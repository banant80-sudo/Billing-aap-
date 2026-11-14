package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BusinessProfile
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.model.InvoiceLineItem
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.data.sync.FinbossBackupPayload
import com.example.data.sync.SyncStatusTracker
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class ErpOperationsRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
  private val syncTracker: SyncStatusTracker? = null,
  private val isOnlineProvider: () -> Boolean = { true },
  private val workspaceOwnerUidOverride: String? = null,
) {
  constructor(
    context: Context,
    syncTracker: SyncStatusTracker? = null,
    isOnlineProvider: () -> Boolean = { true },
    workspaceOwnerUidOverride: String? = null,
  ) : this(
    db =
      FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
      ),
    auth = Firebase.auth,
    syncTracker = syncTracker,
    isOnlineProvider = isOnlineProvider,
    workspaceOwnerUidOverride = workspaceOwnerUidOverride,
  )

  private fun requireUserId(): String {
    val authUid =
      auth.currentUser?.uid
        ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    return workspaceOwnerUidOverride?.takeIf { it.isNotBlank() } ?: authUid
  }

  // --- PARTIES & KHATA ---

  fun observeParties(): Flow<List<ErpParty>> = flow {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("parties")
    emitAll(
      colRef
        .orderBy("name", Query.Direction.ASCENDING)
        .snapshots(MetadataChanges.INCLUDE)
        .map { snapshot ->
          syncTracker?.updateCollection(
            collectionName = "parties",
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
            isFromCache = snapshot.metadata.isFromCache(),
          )
          snapshot.toObjects(
            ErpParty::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, colRef.path, uid)
          throw error
        }
    )
  }

  suspend fun saveParty(
    existingId: String? = null,
    name: String,
    partyType: String,
    phone: String,
    gstin: String,
    openingDue: Double,
    creditLimit: Double,
    notes: String,
    businessProfileId: String = BusinessProfile.SLOT_MAIN_1,
    address: String = "",
    state: String = "",
    pincode: String = "",
  ): Result<String> {
    val uid = requireUserId()
    val safeProfileId =
      if (businessProfileId in BusinessProfile.ALL_SLOTS) businessProfileId
      else BusinessProfile.SLOT_MAIN_1
    val colRef = db.collection("users").document(uid).collection("parties")
    val docRef = if (existingId.isNullOrBlank()) colRef.document() else colRef.document(existingId)
    return try {
      val cleanName = name.trim().take(100)
      require(cleanName.isNotEmpty()) { "Party name is required" }
      val cleanType =
        if (partyType == ErpParty.TYPE_SUPPLIER) ErpParty.TYPE_SUPPLIER else ErpParty.TYPE_CUSTOMER
      val cleanOpening = openingDue.coerceIn(0.0, 1_000_000_000.0)
      val cleanCredit = creditLimit.coerceIn(0.0, 1_000_000_000.0)
      val cleanAddress = address.trim().take(200)
      val cleanState = state.trim().take(60)
      val cleanPincode = pincode.trim().filter { it.isDigit() || it == '-' }.take(12)

      if (existingId.isNullOrBlank()) {
        val payload =
          mapOf(
            "userId" to uid,
            "businessProfileId" to safeProfileId,
            "name" to cleanName,
            "partyType" to cleanType,
            "phone" to phone.trim().take(25),
            "gstin" to gstin.trim().uppercase().take(30),
            "address" to cleanAddress,
            "state" to cleanState,
            "pincode" to cleanPincode,
            "openingDue" to cleanOpening,
            "totalBilled" to 0.0,
            "totalPaid" to 0.0,
            "creditLimit" to cleanCredit,
            "notes" to notes.trim().take(200),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(payload).awaitOfflineAware()
      } else {
        val updates =
          mapOf(
            "name" to cleanName,
            "partyType" to cleanType,
            "phone" to phone.trim().take(25),
            "gstin" to gstin.trim().uppercase().take(30),
            "address" to cleanAddress,
            "state" to cleanState,
            "pincode" to cleanPincode,
            "openingDue" to cleanOpening,
            "creditLimit" to cleanCredit,
            "notes" to notes.trim().take(200),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.update(updates).awaitOfflineAware()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun recordPartyPayment(party: ErpParty, paymentAmount: Double): Result<Unit> {
    val uid = requireUserId()
    require(party.id.isNotBlank()) { "Party ID required" }
    require(paymentAmount > 0.0) { "Payment must be positive" }
    val docRef = db.collection("users").document(uid).collection("parties").document(party.id)
    return try {
      val updatedPaid = (party.totalPaid + paymentAmount).coerceAtMost(1_000_000_000.0)
      docRef
        .update(
          mapOf(
            "totalPaid" to updatedPaid,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteParty(partyId: String): Result<Unit> {
    val uid = requireUserId()
    require(partyId.isNotBlank())
    val docRef = db.collection("users").document(uid).collection("parties").document(partyId)
    return try {
      docRef.delete().awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- INVENTORY & STOCK ---

  fun observeInventory(): Flow<List<InventoryItem>> = flow {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("inventory_items")
    emitAll(
      colRef
        .orderBy("name", Query.Direction.ASCENDING)
        .snapshots(MetadataChanges.INCLUDE)
        .map { snapshot ->
          syncTracker?.updateCollection(
            collectionName = "inventory_items",
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
            isFromCache = snapshot.metadata.isFromCache(),
          )
          snapshot.toObjects(
            InventoryItem::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, colRef.path, uid)
          throw error
        }
    )
  }

  suspend fun saveInventoryItem(
    existingId: String? = null,
    name: String,
    sku: String,
    category: String,
    unit: String,
    salePrice: Double,
    purchasePrice: Double,
    gstRatePercent: Double,
    stockQty: Double,
    lowStockThreshold: Double,
    businessProfileId: String = BusinessProfile.SLOT_MAIN_1,
    hsnCode: String = "",
  ): Result<String> {
    val uid = requireUserId()
    val safeProfileId =
      if (businessProfileId in BusinessProfile.ALL_SLOTS) businessProfileId
      else BusinessProfile.SLOT_MAIN_1
    val colRef = db.collection("users").document(uid).collection("inventory_items")
    val docRef = if (existingId.isNullOrBlank()) colRef.document() else colRef.document(existingId)
    return try {
      val cleanName = name.trim().take(100)
      require(cleanName.isNotEmpty()) { "Item name is required" }
      val cleanSku = sku.trim().uppercase().ifBlank { "SKU-${System.currentTimeMillis() % 10000}" }.take(40)
      val cleanHsn = hsnCode.trim().uppercase().take(16)
      val cleanCategory = category.trim().ifBlank { "General" }.take(40)
      val cleanUnit = unit.trim().uppercase().ifBlank { "PCS" }.take(15)

      val payload =
        buildMap<String, Any> {
          put("name", cleanName)
          put("sku", cleanSku)
          put("hsnCode", cleanHsn)
          put("category", cleanCategory)
          put("unit", cleanUnit)
          put("salePrice", salePrice.coerceIn(0.0, 1_000_000_000.0))
          put("purchasePrice", purchasePrice.coerceIn(0.0, 1_000_000_000.0))
          put("gstRatePercent", gstRatePercent.coerceIn(0.0, 28.0))
          put("stockQty", stockQty.coerceIn(0.0, 1_000_000_000.0))
          put("lowStockThreshold", lowStockThreshold.coerceIn(0.0, 1_000_000_000.0))
          put("updatedAt", FieldValue.serverTimestamp())
        }

      if (existingId.isNullOrBlank()) {
        val createMap = payload.toMutableMap().apply {
          put("userId", uid)
          put("businessProfileId", safeProfileId)
          put("createdAt", FieldValue.serverTimestamp())
        }
        docRef.set(createMap).awaitOfflineAware()
      } else {
        docRef.update(payload).awaitOfflineAware()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun recordPurchase(
    existingItem: InventoryItem?,
    newItemName: String,
    newItemSku: String,
    hsnCode: String,
    category: String,
    unit: String,
    purchasePrice: Double,
    salePrice: Double,
    gstRatePercent: Double,
    purchaseQty: Double,
    lowStockThreshold: Double,
    supplier: ErpParty?,
    supplierNameFallback: String,
    billNumber: String,
    paymentMethod: String,
    isPaidNow: Boolean,
    recordInExpenses: Boolean,
    businessProfileId: String = BusinessProfile.SLOT_MAIN_1,
  ): Result<String> {
    val uid = requireUserId()
    val safeProfileId =
      if (businessProfileId in BusinessProfile.ALL_SLOTS) businessProfileId
      else BusinessProfile.SLOT_MAIN_1
    val isPlain = safeProfileId == BusinessProfile.SLOT_PLAIN_1
    val userRef = db.collection("users").document(uid)
    return try {
      val cleanQty = purchaseQty.coerceAtLeast(0.01)
      val cleanCost = purchasePrice.coerceIn(0.0, 1_000_000_000.0)
      val effectiveGstRate = if (isPlain) 0.0 else gstRatePercent.coerceIn(0.0, 28.0)
      val cleanHsn = hsnCode.trim().uppercase().take(16)
      val targetName = (existingItem?.name ?: newItemName).trim().take(100)
      require(targetName.isNotEmpty()) { "Item name is required for purchase" }
      require(cleanCost > 0.0) { "Purchase price must be greater than zero" }

      val batch = db.batch()
      val itemsCol = userRef.collection("inventory_items")
      val itemRef =
        if (existingItem != null && existingItem.id.isNotBlank()) itemsCol.document(existingItem.id)
        else itemsCol.document()

      if (existingItem != null && existingItem.id.isNotBlank()) {
        val updatedQty = (existingItem.stockQty + cleanQty).coerceAtMost(1_000_000_000.0)
        batch.update(
          itemRef,
          mapOf(
            "stockQty" to updatedQty,
            "purchasePrice" to cleanCost,
            "hsnCode" to cleanHsn.ifBlank { existingItem.hsnCode },
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        val cleanSku =
          newItemSku.trim().uppercase().ifBlank { "SKU-${System.currentTimeMillis() % 10000}" }.take(40)
        val cleanSale = salePrice.coerceAtLeast(cleanCost).coerceAtMost(1_000_000_000.0)
        batch.set(
          itemRef,
          mapOf(
            "userId" to uid,
            "businessProfileId" to safeProfileId,
            "name" to targetName,
            "sku" to cleanSku,
            "hsnCode" to cleanHsn,
            "category" to category.trim().ifBlank { "General" }.take(40),
            "unit" to unit.trim().uppercase().ifBlank { "PCS" }.take(15),
            "salePrice" to cleanSale,
            "purchasePrice" to cleanCost,
            "gstRatePercent" to effectiveGstRate,
            "stockQty" to cleanQty.coerceAtMost(1_000_000_000.0),
            "lowStockThreshold" to lowStockThreshold.coerceIn(0.0, 1_000_000_000.0),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      val taxableTotal = (cleanQty * cleanCost).coerceAtLeast(0.01)
      val gstItcAmount = if (isPlain) 0.0 else taxableTotal * (effectiveGstRate / 100.0)
      val grandPurchaseTotal = (taxableTotal + gstItcAmount).coerceIn(0.01, 1_000_000_000.0)

      if (recordInExpenses) {
        val expRef = userRef.collection("expenses").document()
        val expStatus = if (isPaidNow) ErpExpense.STATUS_SETTLED else ErpExpense.STATUS_PENDING
        val cleanMethod =
          if (
            paymentMethod in
              listOf(
                ErpExpense.METHOD_CASH,
                ErpExpense.METHOD_UPI,
                ErpExpense.METHOD_BANK,
                ErpExpense.METHOD_CARD,
              )
          ) {
            paymentMethod
          } else {
            ErpExpense.METHOD_UPI
          }
        val cleanSupplierName = (supplier?.name ?: supplierNameFallback).trim().take(100)
        batch.set(
          expRef,
          mapOf(
            "userId" to uid,
            "businessProfileId" to safeProfileId,
            "title" to "Purchase: $targetName (${if (cleanQty % 1.0 == 0.0) cleanQty.toInt() else cleanQty})".take(100),
            "category" to ErpExpense.CAT_PURCHASES,
            "paymentMethod" to cleanMethod,
            "status" to expStatus,
            "partyId" to (supplier?.id ?: ""),
            "partyName" to cleanSupplierName,
            "referenceNo" to billNumber.trim().uppercase().take(40),
            "amount" to grandPurchaseTotal,
            "gstInputAmount" to gstItcAmount.coerceIn(0.0, grandPurchaseTotal),
            "notes" to
              buildString {
                if (cleanHsn.isNotBlank()) append("HSN: $cleanHsn • ")
                append("Rate $cleanCost × $cleanQty")
              }.take(240),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      if (supplier != null && supplier.id.isNotBlank()) {
        val supplierRef = userRef.collection("parties").document(supplier.id)
        val paidNowAmt = if (isPaidNow) grandPurchaseTotal else 0.0
        batch.update(
          supplierRef,
          mapOf(
            "totalBilled" to (supplier.totalBilled + grandPurchaseTotal).coerceAtMost(1_000_000_000.0),
            "totalPaid" to (supplier.totalPaid + paidNowAmt).coerceAtMost(1_000_000_000.0),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.commit().awaitOfflineAware()
      Result.success(itemRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userRef.path)
      Result.failure(e)
    }
  }

  suspend fun adjustInventoryStock(item: InventoryItem, deltaQty: Double): Result<Unit> {
    val uid = requireUserId()
    require(item.id.isNotBlank()) { "Item ID required" }
    val docRef =
      db.collection("users").document(uid).collection("inventory_items").document(item.id)
    return try {
      val newQty = (item.stockQty + deltaQty).coerceIn(0.0, 1_000_000_000.0)
      docRef
        .update(
          mapOf(
            "stockQty" to newQty,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteInventoryItem(itemId: String): Result<Unit> {
    val uid = requireUserId()
    require(itemId.isNotBlank())
    val docRef =
      db.collection("users").document(uid).collection("inventory_items").document(itemId)
    return try {
      docRef.delete().awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- BILLING & GST INVOICES ---

  fun observeInvoices(): Flow<List<GstInvoice>> = flow {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("invoices")
    emitAll(
      colRef
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .snapshots(MetadataChanges.INCLUDE)
        .map { snapshot ->
          syncTracker?.updateCollection(
            collectionName = "invoices",
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
            isFromCache = snapshot.metadata.isFromCache(),
          )
          snapshot.toObjects(
            GstInvoice::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, colRef.path, uid)
          throw error
        }
    )
  }

  suspend fun createInvoice(
    invoiceNumber: String,
    party: ErpParty?,
    walkInPartyName: String,
    walkInPartyGstin: String,
    invoiceType: String,
    taxMode: String,
    paymentMethod: String,
    discountAmount: Double,
    paidAmount: Double,
    notes: String,
    lineItems: List<InvoiceLineItem>,
    inventorySnapshot: List<InventoryItem>,
    businessProfileId: String = BusinessProfile.SLOT_MAIN_1,
    walkInPartyAddress: String = "",
    walkInPartyState: String = "",
    walkInPartyPincode: String = "",
    autoDeductStock: Boolean = true,
  ): Result<String> {
    val uid = requireUserId()
    val safeProfileId =
      if (businessProfileId in BusinessProfile.ALL_SLOTS) businessProfileId
      else BusinessProfile.SLOT_MAIN_1
    val isPlain = safeProfileId == BusinessProfile.SLOT_PLAIN_1
    val effectiveTaxMode = if (isPlain) GstInvoice.TAX_MODE_NONE else taxMode
    val invoiceRef = db.collection("users").document(uid).collection("invoices").document()
    return try {
      require(lineItems.isNotEmpty() && lineItems.size <= 25) {
        "Invoice must contain 1 to 25 line items"
      }
      val normalizedLines =
        lineItems.map { line ->
          val qty = line.qty.coerceAtLeast(0.01)
          val price = line.unitPrice.coerceAtLeast(0.0)
          val taxable = qty * price
          val rate = if (effectiveTaxMode == GstInvoice.TAX_MODE_NONE) 0.0 else line.gstRatePercent
          val gst = taxable * (rate / 100.0)
          line.copy(
            qty = qty,
            unitPrice = price,
            gstRatePercent = rate,
            taxableAmount = taxable,
            gstAmount = gst,
            lineTotal = taxable + gst,
          )
        }

      val subtotal = normalizedLines.sumOf { it.taxableAmount }
      val gstTotal = normalizedLines.sumOf { it.gstAmount }
      val safeDiscount = discountAmount.coerceIn(0.0, subtotal + gstTotal)
      val grandTotal = (subtotal + gstTotal - safeDiscount).coerceAtLeast(0.01)
      val actualPaid = paidAmount.coerceIn(0.0, grandTotal)
      val status =
        when {
          actualPaid >= grandTotal - 0.01 -> GstInvoice.STATUS_PAID
          actualPaid > 0.0 -> GstInvoice.STATUS_PARTIAL
          else -> GstInvoice.STATUS_UNPAID
        }

      val finalPartyName =
        (party?.name ?: walkInPartyName).trim().ifBlank { "Walk-in Customer" }.take(100)
      val finalPartyGstin =
        if (isPlain) "" else (party?.gstin ?: walkInPartyGstin).trim().uppercase().take(30)
      val finalPartyAddress = (party?.address ?: walkInPartyAddress).trim().take(200)
      val finalPartyState = (party?.state ?: walkInPartyState).trim().take(60)
      val finalPartyPincode =
        (party?.pincode ?: walkInPartyPincode).trim().filter { it.isDigit() || it == '-' }.take(12)
      val cleanInvNo = invoiceNumber.trim().ifBlank { "FB-${System.currentTimeMillis() % 100000}" }.take(40)

      val batch = db.batch()
      val invoicePayload =
        mapOf(
          "userId" to uid,
          "businessProfileId" to safeProfileId,
          "invoiceNumber" to cleanInvNo,
          "partyId" to (party?.id ?: ""),
          "partyName" to finalPartyName,
          "partyGstin" to finalPartyGstin,
          "partyAddress" to finalPartyAddress,
          "partyState" to finalPartyState,
          "partyPincode" to finalPartyPincode,
          "invoiceType" to invoiceType,
          "taxMode" to effectiveTaxMode,
          "paymentMethod" to paymentMethod,
          "status" to status,
          "subtotal" to subtotal,
          "gstTotal" to gstTotal,
          "discountAmount" to safeDiscount,
          "grandTotal" to grandTotal,
          "paidAmount" to actualPaid,
          "notes" to notes.trim().take(240),
          "lineItems" to normalizedLines.map { it.toFirestoreMap() },
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      batch.set(invoiceRef, invoicePayload)

      // Deduct stock quantities in the same atomic batch when autoDeductStock is enabled
      if (autoDeductStock) {
        val qtyByItemId =
          normalizedLines.filter { it.itemId.isNotBlank() }.groupBy { it.itemId }.mapValues { entry ->
            entry.value.sumOf { it.qty }
          }
        val inventoryMap = inventorySnapshot.associateBy { it.id }
        qtyByItemId.forEach { (itemId, usedQty) ->
          val existingItem = inventoryMap[itemId] ?: return@forEach
          val newStock = (existingItem.stockQty - usedQty).coerceAtLeast(0.0)
          val itemRef =
            db.collection("users").document(uid).collection("inventory_items").document(itemId)
          batch.update(
            itemRef,
            mapOf(
              "stockQty" to newStock,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }

      // Update linked Party Khata totals atomically
      if (party != null && party.id.isNotBlank()) {
        val partyRef = db.collection("users").document(uid).collection("parties").document(party.id)
        batch.update(
          partyRef,
          mapOf(
            "totalBilled" to (party.totalBilled + grandTotal).coerceAtMost(1_000_000_000.0),
            "totalPaid" to (party.totalPaid + actualPaid).coerceAtMost(1_000_000_000.0),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.commit().awaitOfflineAware()
      Result.success(invoiceRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, invoiceRef.path)
      Result.failure(e)
    }
  }

  suspend fun recordInvoicePayment(
    invoice: GstInvoice,
    additionalPayment: Double,
    paymentMethod: String,
    linkedParty: ErpParty?,
  ): Result<Unit> {
    val uid = requireUserId()
    require(invoice.id.isNotBlank()) { "Invoice ID required" }
    require(invoice.status != GstInvoice.STATUS_PAID) { "Invoice is already fully paid" }
    require(additionalPayment > 0.0) { "Payment must be positive" }

    val invoiceRef = db.collection("users").document(uid).collection("invoices").document(invoice.id)
    return try {
      val newPaid = (invoice.paidAmount + additionalPayment).coerceAtMost(invoice.grandTotal)
      val deltaPaid = (newPaid - invoice.paidAmount).coerceAtLeast(0.0)
      val newStatus =
        if (newPaid >= invoice.grandTotal - 0.01) GstInvoice.STATUS_PAID
        else GstInvoice.STATUS_PARTIAL

      val batch = db.batch()
      batch.update(
        invoiceRef,
        mapOf(
          "paidAmount" to newPaid,
          "status" to newStatus,
          "paymentMethod" to paymentMethod,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      if (linkedParty != null && linkedParty.id.isNotBlank() && deltaPaid > 0.0) {
        val partyRef =
          db.collection("users").document(uid).collection("parties").document(linkedParty.id)
        batch.update(
          partyRef,
          mapOf(
            "totalBilled" to linkedParty.totalBilled,
            "totalPaid" to (linkedParty.totalPaid + deltaPaid).coerceAtMost(1_000_000_000.0),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.commit().awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, invoiceRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateInvoice(
    existingInvoice: GstInvoice,
    invoiceNumber: String,
    party: ErpParty?,
    walkInPartyName: String,
    walkInPartyGstin: String,
    invoiceType: String,
    taxMode: String,
    paymentMethod: String,
    discountAmount: Double,
    paidAmount: Double,
    notes: String,
    lineItems: List<InvoiceLineItem>,
    inventorySnapshot: List<InventoryItem>,
    partiesSnapshot: List<ErpParty>,
    walkInPartyAddress: String = "",
    walkInPartyState: String = "",
    walkInPartyPincode: String = "",
    autoDeductStock: Boolean = true,
  ): Result<Unit> {
    val uid = requireUserId()
    require(existingInvoice.id.isNotBlank()) { "Invoice ID required" }
    val invoiceRef =
      db.collection("users").document(uid).collection("invoices").document(existingInvoice.id)
    val isPlain = existingInvoice.resolvedProfileId() == BusinessProfile.SLOT_PLAIN_1
    val effectiveTaxMode = if (isPlain) GstInvoice.TAX_MODE_NONE else taxMode
    return try {
      require(lineItems.isNotEmpty() && lineItems.size <= 25) {
        "Invoice must contain 1 to 25 line items"
      }
      val normalizedLines =
        lineItems.map { line ->
          val qty = line.qty.coerceAtLeast(0.01)
          val price = line.unitPrice.coerceAtLeast(0.0)
          val taxable = qty * price
          val rate = if (effectiveTaxMode == GstInvoice.TAX_MODE_NONE) 0.0 else line.gstRatePercent
          val gst = taxable * (rate / 100.0)
          line.copy(
            qty = qty,
            unitPrice = price,
            gstRatePercent = rate,
            taxableAmount = taxable,
            gstAmount = gst,
            lineTotal = taxable + gst,
          )
        }

      val subtotal = normalizedLines.sumOf { it.taxableAmount }
      val gstTotal = normalizedLines.sumOf { it.gstAmount }
      val safeDiscount = discountAmount.coerceIn(0.0, subtotal + gstTotal)
      val grandTotal = (subtotal + gstTotal - safeDiscount).coerceAtLeast(0.01)
      val actualPaid = paidAmount.coerceIn(0.0, grandTotal)
      val status =
        when {
          actualPaid >= grandTotal - 0.01 -> GstInvoice.STATUS_PAID
          actualPaid > 0.0 -> GstInvoice.STATUS_PARTIAL
          else -> GstInvoice.STATUS_UNPAID
        }

      val finalPartyName =
        (party?.name ?: walkInPartyName).trim().ifBlank { "Walk-in Customer" }.take(100)
      val finalPartyGstin =
        if (isPlain) "" else (party?.gstin ?: walkInPartyGstin).trim().uppercase().take(30)
      val finalPartyAddress = (party?.address ?: walkInPartyAddress).trim().take(200)
      val finalPartyState = (party?.state ?: walkInPartyState).trim().take(60)
      val finalPartyPincode =
        (party?.pincode ?: walkInPartyPincode).trim().filter { it.isDigit() || it == '-' }.take(12)
      val cleanInvNo =
        invoiceNumber.trim().ifBlank { existingInvoice.invoiceNumber }.take(40)

      val batch = db.batch()
      val updates =
        mapOf(
          "invoiceNumber" to cleanInvNo,
          "partyId" to (party?.id ?: ""),
          "partyName" to finalPartyName,
          "partyGstin" to finalPartyGstin,
          "partyAddress" to finalPartyAddress,
          "partyState" to finalPartyState,
          "partyPincode" to finalPartyPincode,
          "invoiceType" to invoiceType,
          "taxMode" to effectiveTaxMode,
          "paymentMethod" to paymentMethod,
          "status" to status,
          "subtotal" to subtotal,
          "gstTotal" to gstTotal,
          "discountAmount" to safeDiscount,
          "grandTotal" to grandTotal,
          "paidAmount" to actualPaid,
          "notes" to notes.trim().take(240),
          "lineItems" to normalizedLines.map { it.toFirestoreMap() },
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      batch.update(invoiceRef, updates)

      // Reconcile stock delta between previous and edited line items
      if (autoDeductStock) {
        val oldQtyByItemId =
          existingInvoice.lineItems
            .filter { it.itemId.isNotBlank() }
            .groupBy { it.itemId }
            .mapValues { it.value.sumOf { l -> l.qty } }
        val newQtyByItemId =
          normalizedLines
            .filter { it.itemId.isNotBlank() }
            .groupBy { it.itemId }
            .mapValues { it.value.sumOf { l -> l.qty } }
        val inventoryMap = inventorySnapshot.associateBy { it.id }

        (oldQtyByItemId.keys + newQtyByItemId.keys).forEach { itemId ->
          val existingItem = inventoryMap[itemId] ?: return@forEach
          val deltaUsed = (newQtyByItemId[itemId] ?: 0.0) - (oldQtyByItemId[itemId] ?: 0.0)
          if (deltaUsed != 0.0) {
            val newStock = (existingItem.stockQty - deltaUsed).coerceIn(0.0, 1_000_000_000.0)
            val itemRef =
              db.collection("users").document(uid).collection("inventory_items").document(itemId)
            batch.update(
              itemRef,
              mapOf(
                "stockQty" to newStock,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
        }
      }

      // Reconcile linked Party Khata totals atomically
      val partyMap = partiesSnapshot.associateBy { it.id }
      val oldPartyId = existingInvoice.partyId
      val newPartyId = party?.id ?: ""

      if (oldPartyId.isNotBlank() && oldPartyId == newPartyId) {
        partyMap[oldPartyId]?.let { existingParty ->
          val partyRef =
            db.collection("users").document(uid).collection("parties").document(oldPartyId)
          batch.update(
            partyRef,
            mapOf(
              "totalBilled" to
                (existingParty.totalBilled - existingInvoice.grandTotal + grandTotal)
                  .coerceIn(0.0, 1_000_000_000.0),
              "totalPaid" to
                (existingParty.totalPaid - existingInvoice.paidAmount + actualPaid)
                  .coerceIn(0.0, 1_000_000_000.0),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      } else {
        if (oldPartyId.isNotBlank()) {
          partyMap[oldPartyId]?.let { oldParty ->
            val oldRef =
              db.collection("users").document(uid).collection("parties").document(oldPartyId)
            batch.update(
              oldRef,
              mapOf(
                "totalBilled" to
                  (oldParty.totalBilled - existingInvoice.grandTotal).coerceIn(0.0, 1_000_000_000.0),
                "totalPaid" to
                  (oldParty.totalPaid - existingInvoice.paidAmount).coerceIn(0.0, 1_000_000_000.0),
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
        }
        if (newPartyId.isNotBlank()) {
          val targetParty = partyMap[newPartyId] ?: party
          if (targetParty != null) {
            val newRef =
              db.collection("users").document(uid).collection("parties").document(newPartyId)
            batch.update(
              newRef,
              mapOf(
                "totalBilled" to
                  (targetParty.totalBilled + grandTotal).coerceIn(0.0, 1_000_000_000.0),
                "totalPaid" to
                  (targetParty.totalPaid + actualPaid).coerceIn(0.0, 1_000_000_000.0),
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
        }
      }

      batch.commit().awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, invoiceRef.path)
      Result.failure(e)
    }
  }

  suspend fun saveInvoiceEInvoiceDetails(
    invoice: GstInvoice,
    result: com.example.data.reference.EInvoiceGenerationResult,
  ): Result<Unit> {
    val uid = requireUserId()
    require(invoice.id.isNotBlank()) { "Invoice ID required for E-Invoice generation" }
    val invoiceRef =
      db.collection("users").document(uid).collection("invoices").document(invoice.id)
    return try {
      require(invoice.taxMode != GstInvoice.TAX_MODE_NONE) {
        "Plain Non-GST bills cannot generate GST Portal E-Invoices"
      }
      val updates =
        mapOf(
          "irn" to result.irn.trim().take(64),
          "ackNo" to result.ackNo.trim().take(30),
          "ackDate" to result.ackDate.trim().take(40),
          "signedQrData" to result.signedQrData.trim().take(600),
          "einvoiceStatus" to "generated",
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      invoiceRef.update(updates).awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, invoiceRef.path)
      Result.failure(e)
    }
  }

  suspend fun cancelInvoiceEInvoice(invoice: GstInvoice): Result<Unit> {
    val uid = requireUserId()
    require(invoice.id.isNotBlank()) { "Invoice ID required" }
    val invoiceRef =
      db.collection("users").document(uid).collection("invoices").document(invoice.id)
    return try {
      val updates =
        mapOf(
          "einvoiceStatus" to "cancelled",
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      invoiceRef.update(updates).awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, invoiceRef.path)
      Result.failure(e)
    }
  }

  suspend fun saveInvoiceEWayBillDetails(
    invoice: GstInvoice,
    result: com.example.data.reference.EWayBillGenerationResult,
  ): Result<Unit> {
    val uid = requireUserId()
    require(invoice.id.isNotBlank()) { "Invoice ID required for E-Way Bill generation" }
    val invoiceRef =
      db.collection("users").document(uid).collection("invoices").document(invoice.id)
    return try {
      require(invoice.taxMode != GstInvoice.TAX_MODE_NONE) {
        "Plain Non-GST bills cannot generate GST E-Way Bills"
      }
      val input = result.transportInput
      val cleanMode =
        input.transportMode.lowercase().takeIf { it in listOf("road", "rail", "air", "ship") }
          ?: "road"
      val cleanVehType =
        input.vehicleType.lowercase().takeIf { it in listOf("regular", "odc") } ?: "regular"
      val updates =
        mapOf(
          "ewbNumber" to result.ewbNumber.trim().take(20),
          "ewbDate" to result.ewbDate.trim().take(40),
          "ewbValidUntil" to result.ewbValidUntil.trim().take(60),
          "transportMode" to cleanMode,
          "distanceKm" to input.distanceKm.coerceIn(1, 4000),
          "fromPlace" to input.fromPlace.trim().take(80),
          "fromPincode" to input.fromPincode.trim().take(12),
          "toPlace" to input.toPlace.trim().take(80),
          "toPincode" to input.toPincode.trim().take(12),
          "vehicleNumber" to input.vehicleNumber.trim().uppercase().take(25),
          "vehicleType" to cleanVehType,
          "transporterName" to input.transporterName.trim().take(100),
          "transporterId" to input.transporterId.trim().uppercase().take(30),
          "transporterDocNo" to input.transporterDocNo.trim().take(40),
          "transporterDocDate" to input.transporterDocDate.trim().take(30),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      invoiceRef.update(updates).awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, invoiceRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteInvoice(
    invoice: GstInvoice,
    inventorySnapshot: List<InventoryItem> = emptyList(),
    partiesSnapshot: List<ErpParty> = emptyList(),
  ): Result<Unit> {
    val uid = requireUserId()
    require(invoice.id.isNotBlank()) { "Invoice ID required" }
    val docRef = db.collection("users").document(uid).collection("invoices").document(invoice.id)
    return try {
      val batch = db.batch()

      // Restore deducted stock for linked inventory items
      val inventoryMap = inventorySnapshot.associateBy { it.id }
      invoice.lineItems
        .filter { it.itemId.isNotBlank() }
        .groupBy { it.itemId }
        .mapValues { it.value.sumOf { l -> l.qty } }
        .forEach { (itemId, usedQty) ->
          val existingItem = inventoryMap[itemId] ?: return@forEach
          val itemRef =
            db.collection("users").document(uid).collection("inventory_items").document(itemId)
          batch.update(
            itemRef,
            mapOf(
              "stockQty" to (existingItem.stockQty + usedQty).coerceIn(0.0, 1_000_000_000.0),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }

      // Reverse billed/paid totals from linked Party Khata
      if (invoice.partyId.isNotBlank()) {
        val linkedParty = partiesSnapshot.firstOrNull { it.id == invoice.partyId }
        if (linkedParty != null) {
          val partyRef =
            db.collection("users").document(uid).collection("parties").document(linkedParty.id)
          batch.update(
            partyRef,
            mapOf(
              "totalBilled" to
                (linkedParty.totalBilled - invoice.grandTotal).coerceIn(0.0, 1_000_000_000.0),
              "totalPaid" to
                (linkedParty.totalPaid - invoice.paidAmount).coerceIn(0.0, 1_000_000_000.0),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }

      batch.delete(docRef)
      batch.commit().awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- EXPENSES & CASHBOOK ---

  fun observeExpenses(): Flow<List<ErpExpense>> = flow {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("expenses")
    emitAll(
      colRef
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .snapshots(MetadataChanges.INCLUDE)
        .map { snapshot ->
          syncTracker?.updateCollection(
            collectionName = "expenses",
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
            isFromCache = snapshot.metadata.isFromCache(),
          )
          snapshot.toObjects(
            ErpExpense::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, colRef.path, uid)
          throw error
        }
    )
  }

  suspend fun saveExpense(
    existingId: String? = null,
    title: String,
    category: String,
    paymentMethod: String,
    status: String,
    party: ErpParty?,
    partyNameFallback: String,
    referenceNo: String,
    amount: Double,
    gstInputAmount: Double,
    notes: String,
    businessProfileId: String = BusinessProfile.SLOT_MAIN_1,
  ): Result<String> {
    val uid = requireUserId()
    val safeProfileId =
      if (businessProfileId in BusinessProfile.ALL_SLOTS) businessProfileId
      else BusinessProfile.SLOT_MAIN_1
    val colRef = db.collection("users").document(uid).collection("expenses")
    val docRef = if (existingId.isNullOrBlank()) colRef.document() else colRef.document(existingId)
    return try {
      val cleanTitle = title.trim().take(100)
      require(cleanTitle.isNotEmpty()) { "Expense title is required" }
      val cleanAmount = amount.coerceIn(0.01, 1_000_000_000.0)
      val cleanGst = gstInputAmount.coerceIn(0.0, cleanAmount)
      val cleanCategory =
        if (ErpExpense.ALL_CATEGORIES.any { it.first == category }) category else ErpExpense.CAT_OTHER
      val cleanMethod =
        if (
          paymentMethod in
            listOf(
              ErpExpense.METHOD_CASH,
              ErpExpense.METHOD_UPI,
              ErpExpense.METHOD_BANK,
              ErpExpense.METHOD_CARD,
            )
        ) {
          paymentMethod
        } else {
          ErpExpense.METHOD_UPI
        }
      val cleanStatus =
        if (status == ErpExpense.STATUS_PENDING) ErpExpense.STATUS_PENDING else ErpExpense.STATUS_SETTLED
      val cleanPartyName = (party?.name ?: partyNameFallback).trim().take(100)

      val commonFields =
        buildMap<String, Any> {
          put("title", cleanTitle)
          put("category", cleanCategory)
          put("paymentMethod", cleanMethod)
          put("status", cleanStatus)
          put("partyId", party?.id ?: "")
          put("partyName", cleanPartyName)
          put("referenceNo", referenceNo.trim().uppercase().take(40))
          put("amount", cleanAmount)
          put("gstInputAmount", cleanGst)
          put("notes", notes.trim().take(240))
          put("updatedAt", FieldValue.serverTimestamp())
        }

      if (existingId.isNullOrBlank()) {
        val createPayload =
          commonFields.toMutableMap().apply {
            put("userId", uid)
            put("businessProfileId", safeProfileId)
            put("createdAt", FieldValue.serverTimestamp())
          }
        docRef.set(createPayload).awaitOfflineAware()
      } else {
        docRef.update(commonFields).awaitOfflineAware()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun markExpenseSettled(expenseId: String, paymentMethod: String): Result<Unit> {
    val uid = requireUserId()
    require(expenseId.isNotBlank()) { "Expense ID required" }
    val docRef = db.collection("users").document(uid).collection("expenses").document(expenseId)
    return try {
      val cleanMethod =
        if (
          paymentMethod in
            listOf(
              ErpExpense.METHOD_CASH,
              ErpExpense.METHOD_UPI,
              ErpExpense.METHOD_BANK,
              ErpExpense.METHOD_CARD,
            )
        ) {
          paymentMethod
        } else {
          ErpExpense.METHOD_UPI
        }
      docRef
        .update(
          mapOf(
            "status" to ErpExpense.STATUS_SETTLED,
            "paymentMethod" to cleanMethod,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteExpense(expenseId: String): Result<Unit> {
    val uid = requireUserId()
    require(expenseId.isNotBlank())
    val docRef = db.collection("users").document(uid).collection("expenses").document(expenseId)
    return try {
      docRef.delete().awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun restoreFromBackup(
    payload: FinbossBackupPayload,
    existingPartyIds: Set<String>,
    existingInventoryIds: Set<String>,
    existingInvoiceIds: Set<String>,
    existingExpenseIds: Set<String>,
    existingEntryIds: Set<String>,
  ): Result<Int> {
    val uid = requireUserId()
    val userRef = db.collection("users").document(uid)
    return try {
      val pendingOps = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()
      val validIdRegex = Regex("^[a-zA-Z0-9_\\-]+$")

      payload.parties.forEach { party ->
        val cleanName = party.name.trim().take(100)
        if (cleanName.isNotEmpty() && !existingPartyIds.contains(party.id)) {
          val col = userRef.collection("parties")
          val docRef =
            if (party.id.isNotBlank() && validIdRegex.matches(party.id)) col.document(party.id)
            else col.document()
          pendingOps.add(
            docRef to
              mapOf(
                "userId" to uid,
                "businessProfileId" to party.resolvedProfileId(),
                "name" to cleanName,
                "partyType" to
                  if (party.partyType == ErpParty.TYPE_SUPPLIER) ErpParty.TYPE_SUPPLIER
                  else ErpParty.TYPE_CUSTOMER,
                "phone" to party.phone.trim().take(25),
                "gstin" to party.gstin.trim().uppercase().take(30),
                "address" to party.address.trim().take(200),
                "state" to party.state.trim().take(60),
                "pincode" to party.pincode.trim().filter { it.isDigit() || it == '-' }.take(12),
                "openingDue" to party.openingDue.coerceIn(0.0, 1_000_000_000.0),
                "totalBilled" to party.totalBilled.coerceIn(0.0, 1_000_000_000.0),
                "totalPaid" to party.totalPaid.coerceIn(0.0, 1_000_000_000.0),
                "creditLimit" to party.creditLimit.coerceIn(0.0, 1_000_000_000.0),
                "notes" to party.notes.trim().take(200),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
          )
        }
      }

      payload.inventory.forEach { item ->
        val cleanName = item.name.trim().take(100)
        if (cleanName.isNotEmpty() && !existingInventoryIds.contains(item.id)) {
          val col = userRef.collection("inventory_items")
          val docRef =
            if (item.id.isNotBlank() && validIdRegex.matches(item.id)) col.document(item.id)
            else col.document()
          pendingOps.add(
            docRef to
              mapOf(
                "userId" to uid,
                "businessProfileId" to item.resolvedProfileId(),
                "name" to cleanName,
                "sku" to item.sku.trim().ifBlank { "SKU-RESTORE" }.take(40),
                "hsnCode" to item.hsnCode.trim().uppercase().take(16),
                "category" to item.category.trim().ifBlank { "General" }.take(40),
                "unit" to item.unit.trim().ifBlank { "PCS" }.take(15),
                "salePrice" to item.salePrice.coerceIn(0.0, 1_000_000_000.0),
                "purchasePrice" to item.purchasePrice.coerceIn(0.0, 1_000_000_000.0),
                "gstRatePercent" to item.gstRatePercent.coerceIn(0.0, 28.0),
                "stockQty" to item.stockQty.coerceIn(0.0, 1_000_000_000.0),
                "lowStockThreshold" to item.lowStockThreshold.coerceIn(0.0, 1_000_000_000.0),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
          )
        }
      }

      payload.invoices.forEach { inv ->
        val validLines = inv.lineItems.take(25)
        if (inv.invoiceNumber.isNotBlank() && validLines.isNotEmpty() && inv.grandTotal > 0.0 && !existingInvoiceIds.contains(inv.id)) {
          val col = userRef.collection("invoices")
          val docRef =
            if (inv.id.isNotBlank() && validIdRegex.matches(inv.id)) col.document(inv.id)
            else col.document()
          val safeGrand = inv.grandTotal.coerceIn(0.01, 1_000_000_000.0)
          pendingOps.add(
            docRef to
              mapOf(
                "userId" to uid,
                "businessProfileId" to inv.resolvedProfileId(),
                "invoiceNumber" to inv.invoiceNumber.trim().take(40),
                "partyId" to inv.partyId.take(128),
                "partyName" to inv.partyName.trim().ifBlank { "Walk-in Customer" }.take(100),
                "partyGstin" to inv.partyGstin.trim().uppercase().take(30),
                "partyAddress" to inv.partyAddress.trim().take(200),
                "partyState" to inv.partyState.trim().take(60),
                "partyPincode" to inv.partyPincode.trim().filter { it.isDigit() || it == '-' }.take(12),
                "invoiceType" to
                  if (inv.invoiceType == GstInvoice.TYPE_POS_BILL) GstInvoice.TYPE_POS_BILL
                  else GstInvoice.TYPE_TAX_INVOICE,
                "taxMode" to
                  if (inv.taxMode in listOf(GstInvoice.TAX_MODE_CGST_SGST, GstInvoice.TAX_MODE_IGST, GstInvoice.TAX_MODE_NONE)) inv.taxMode
                  else GstInvoice.TAX_MODE_CGST_SGST,
                "paymentMethod" to
                  if (inv.paymentMethod in listOf(GstInvoice.PAYMENT_CASH, GstInvoice.PAYMENT_UPI, GstInvoice.PAYMENT_CARD, GstInvoice.PAYMENT_CREDIT)) inv.paymentMethod
                  else GstInvoice.PAYMENT_UPI,
                "status" to
                  if (inv.status in listOf(GstInvoice.STATUS_PAID, GstInvoice.STATUS_PARTIAL, GstInvoice.STATUS_UNPAID)) inv.status
                  else GstInvoice.STATUS_PAID,
                "subtotal" to inv.subtotal.coerceIn(0.0, 1_000_000_000.0),
                "gstTotal" to inv.gstTotal.coerceIn(0.0, 1_000_000_000.0),
                "discountAmount" to inv.discountAmount.coerceIn(0.0, 1_000_000_000.0),
                "grandTotal" to safeGrand,
                "paidAmount" to inv.paidAmount.coerceIn(0.0, safeGrand),
                "notes" to inv.notes.trim().take(240),
                "lineItems" to validLines.map { it.toFirestoreMap() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
          )
        }
      }

      payload.expenses.forEach { exp ->
        val cleanTitle = exp.title.trim().take(100)
        if (cleanTitle.isNotEmpty() && exp.amount > 0.0 && !existingExpenseIds.contains(exp.id)) {
          val col = userRef.collection("expenses")
          val docRef =
            if (exp.id.isNotBlank() && validIdRegex.matches(exp.id)) col.document(exp.id)
            else col.document()
          val cleanAmt = exp.amount.coerceIn(0.01, 1_000_000_000.0)
          pendingOps.add(
            docRef to
              mapOf(
                "userId" to uid,
                "businessProfileId" to exp.resolvedProfileId(),
                "title" to cleanTitle,
                "category" to
                  if (ErpExpense.ALL_CATEGORIES.any { it.first == exp.category }) exp.category
                  else ErpExpense.CAT_OTHER,
                "paymentMethod" to
                  if (exp.paymentMethod in listOf(ErpExpense.METHOD_CASH, ErpExpense.METHOD_UPI, ErpExpense.METHOD_BANK, ErpExpense.METHOD_CARD)) exp.paymentMethod
                  else ErpExpense.METHOD_UPI,
                "status" to
                  if (exp.status == ErpExpense.STATUS_PENDING) ErpExpense.STATUS_PENDING
                  else ErpExpense.STATUS_SETTLED,
                "partyId" to exp.partyId.take(128),
                "partyName" to exp.partyName.trim().take(100),
                "referenceNo" to exp.referenceNo.trim().uppercase().take(40),
                "amount" to cleanAmt,
                "gstInputAmount" to exp.gstInputAmount.coerceIn(0.0, cleanAmt),
                "notes" to exp.notes.trim().take(240),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
          )
        }
      }

      payload.dashboardEntries.forEach { entry ->
        val cleanTitle = entry.title.trim().take(100)
        if (cleanTitle.isNotEmpty() && entry.amount > 0.0 && !existingEntryIds.contains(entry.id)) {
          val col = userRef.collection("dashboard_entries")
          val docRef =
            if (entry.id.isNotBlank() && validIdRegex.matches(entry.id)) col.document(entry.id)
            else col.document()
          pendingOps.add(
            docRef to
              mapOf(
                "userId" to uid,
                "businessProfileId" to entry.resolvedProfileId(),
                "title" to cleanTitle,
                "partyName" to entry.partyName.trim().take(100),
                "category" to
                  if (entry.category in listOf("sale", "expense", "receivable")) entry.category
                  else "sale",
                "amount" to entry.amount.coerceIn(0.01, 1_000_000_000.0),
                "status" to if (entry.status == "pending") "pending" else "paid",
                "referenceCode" to entry.referenceCode.trim().take(30),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
          )
        }
      }

      // Commit in Firestore batch chunks of 400
      pendingOps.chunked(400).forEach { chunk ->
        val batch = db.batch()
        chunk.forEach { (ref, data) -> batch.set(ref, data) }
        batch.commit().awaitOfflineAware()
      }

      Result.success(pendingOps.size)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userRef.path)
      Result.failure(e)
    }
  }

  private suspend fun Task<Void>.awaitOfflineAware() {
    if (!isOnlineProvider()) {
      delay(40)
      return
    }
    withTimeoutOrNull(4500L) { await(); true }
  }

  private fun logStreamErrorIfActive(
    error: Throwable,
    operationType: OperationType,
    path: String,
    uid: String,
  ) {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && (auth.currentUser?.uid == uid || workspaceOwnerUidOverride == uid)) {
      handleFirestoreError(exceptionToLog, operationType, path)
    }
  }
}
