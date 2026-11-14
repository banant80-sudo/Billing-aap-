package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.data.model.BusinessProfile
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.model.InvoiceLineItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class CollectionSyncMeta(
  val hasPendingWrites: Boolean = false,
  val isFromCache: Boolean = false,
)

data class SyncMetadataSummary(
  val collections: Map<String, CollectionSyncMeta> = emptyMap(),
  val lastServerConfirmedAtMs: Long = 0L,
) {
  val pendingCollectionsCount: Int
    get() = collections.values.count { it.hasPendingWrites }

  val hasPendingWrites: Boolean
    get() = pendingCollectionsCount > 0

  val isFromCacheOnly: Boolean
    get() = collections.isNotEmpty() && collections.values.all { it.isFromCache }
}

class SyncStatusTracker {
  private val _state = MutableStateFlow(SyncMetadataSummary())
  val state: StateFlow<SyncMetadataSummary> = _state.asStateFlow()

  fun updateCollection(
    collectionName: String,
    hasPendingWrites: Boolean,
    isFromCache: Boolean,
  ) {
    _state.update { current ->
      val updatedMap =
        current.collections.toMutableMap().apply {
          put(
            collectionName,
            CollectionSyncMeta(hasPendingWrites = hasPendingWrites, isFromCache = isFromCache),
          )
        }
      val confirmedTime =
        if (!hasPendingWrites && !isFromCache) System.currentTimeMillis()
        else current.lastServerConfirmedAtMs
      current.copy(collections = updatedMap, lastServerConfirmedAtMs = confirmedTime)
    }
  }
}

class SyncPreferences(context: Context) {
  private val appContext = context.applicationContext
  private val prefs: SharedPreferences by lazy {
    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun warmup() {
    prefs.contains("warmup_probe")
  }

  fun getLastCloudSyncMs(userId: String): Long =
    prefs.getLong("last_cloud_sync_$userId", 0L)

  fun setLastCloudSyncMs(userId: String, timeMs: Long) {
    prefs.edit().putLong("last_cloud_sync_$userId", timeMs).apply()
  }

  fun getLastLocalBackupMs(userId: String): Long =
    prefs.getLong("last_local_backup_$userId", 0L)

  fun setLastLocalBackupMs(userId: String, timeMs: Long) {
    prefs.edit().putLong("last_local_backup_$userId", timeMs).apply()
  }

  fun getActiveProfileId(userId: String): String =
    prefs.getString("active_profile_id_$userId", BusinessProfile.SLOT_MAIN_1)
      ?.takeIf { it in BusinessProfile.ALL_SLOTS }
      ?: BusinessProfile.SLOT_MAIN_1

  fun setActiveProfileId(userId: String, profileId: String) {
    val safeId =
      if (profileId in BusinessProfile.ALL_SLOTS) profileId else BusinessProfile.SLOT_MAIN_1
    prefs.edit().putString("active_profile_id_$userId", safeId).apply()
  }

  private companion object {
    const val PREFS_NAME = "finboss_sync_prefs"
  }
}

fun configureFirestoreOfflinePersistence(db: FirebaseFirestore) {
  try {
    val settings =
      FirebaseFirestoreSettings.Builder()
        .setLocalCacheSettings(
          PersistentCacheSettings.newBuilder()
            .setSizeBytes(100L * 1024L * 1024L) // 100 MB offline disk cache
            .build()
        )
        .build()
    db.firestoreSettings = settings
  } catch (_: IllegalStateException) {
    // Firestore instance already active in this process; persistent cache remains active.
  }
}

fun isNetworkCurrentlyConnected(context: Context): Boolean {
  val cm =
    context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return true
  val network = cm.activeNetwork ?: return false
  val caps = cm.getNetworkCapabilities(network) ?: return false
  return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

fun observeNetworkConnectivity(context: Context): Flow<Boolean> = callbackFlow {
  val cm =
    context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
  if (cm == null) {
    trySend(true)
    awaitClose {}
    return@callbackFlow
  }

  fun checkOnline(): Boolean {
    val active = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(active) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  trySend(checkOnline())

  val callback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        trySend(true)
      }

      override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
      }

      override fun onLost(network: Network) {
        trySend(checkOnline())
      }

      override fun onUnavailable() {
        trySend(false)
      }
    }

  cm.registerDefaultNetworkCallback(callback)
  awaitClose {
    try {
      cm.unregisterNetworkCallback(callback)
    } catch (_: Exception) {
    }
  }
}.distinctUntilChanged()

data class FinbossBackupPayload(
  val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
  val exportedAtMs: Long = System.currentTimeMillis(),
  val businessProfile: BusinessProfile? = null,
  val businessProfiles: List<BusinessProfile> = emptyList(),
  val parties: List<ErpParty> = emptyList(),
  val inventory: List<InventoryItem> = emptyList(),
  val invoices: List<GstInvoice> = emptyList(),
  val expenses: List<ErpExpense> = emptyList(),
  val dashboardEntries: List<DashboardEntry> = emptyList(),
) {
  val totalRecordsCount: Int
    get() = parties.size + inventory.size + invoices.size + expenses.size + dashboardEntries.size

  companion object {
    const val BACKUP_SCHEMA_VERSION = 1
  }
}

object FinbossBackupSerializer {

  fun encodeToJson(payload: FinbossBackupPayload): String {
    val root = JSONObject()
    root.put("app", "Finboss Billing No.1 ERP")
    root.put("schemaVersion", payload.schemaVersion)
    root.put("exportedAtMs", payload.exportedAtMs)

    payload.businessProfile?.let { p ->
      val prof =
        JSONObject().apply {
          put("id", p.id)
          put("ownerName", p.ownerName)
          put("businessName", p.businessName)
          put("gstin", p.gstin.orEmpty())
          put("address", p.address)
          put("state", p.state)
          put("pincode", p.pincode)
          put("currencySymbol", p.currencySymbol)
          put("fiscalYearLabel", p.fiscalYearLabel)
          put("businessType", p.businessType)
          put("monthlySalesTarget", p.monthlySalesTarget)
          put("profileMode", p.profileMode)
        }
      root.put("businessProfile", prof)
    }

    if (payload.businessProfiles.isNotEmpty()) {
      val profilesArr = JSONArray()
      payload.businessProfiles.forEach { p ->
        profilesArr.put(
          JSONObject().apply {
            put("id", p.resolvedSlotId)
            put("ownerName", p.ownerName)
            put("businessName", p.businessName)
            put("gstin", p.gstin.orEmpty())
            put("address", p.address)
            put("state", p.state)
            put("pincode", p.pincode)
            put("currencySymbol", p.currencySymbol)
            put("fiscalYearLabel", p.fiscalYearLabel)
            put("businessType", p.businessType)
            put("monthlySalesTarget", p.monthlySalesTarget)
            put("profileMode", p.profileMode)
          }
        )
      }
      root.put("businessProfiles", profilesArr)
    }

    val partiesArr = JSONArray()
    payload.parties.forEach { party ->
      partiesArr.put(
        JSONObject().apply {
          put("id", party.id)
          put("businessProfileId", party.resolvedProfileId())
          put("name", party.name)
          put("partyType", party.partyType)
          put("phone", party.phone)
          put("gstin", party.gstin)
          put("address", party.address)
          put("state", party.state)
          put("pincode", party.pincode)
          put("openingDue", party.openingDue)
          put("totalBilled", party.totalBilled)
          put("totalPaid", party.totalPaid)
          put("creditLimit", party.creditLimit)
          put("notes", party.notes)
        }
      )
    }
    root.put("parties", partiesArr)

    val inventoryArr = JSONArray()
    payload.inventory.forEach { item ->
      inventoryArr.put(
        JSONObject().apply {
          put("id", item.id)
          put("businessProfileId", item.resolvedProfileId())
          put("name", item.name)
          put("sku", item.sku)
          put("hsnCode", item.hsnCode)
          put("category", item.category)
          put("unit", item.unit)
          put("salePrice", item.salePrice)
          put("purchasePrice", item.purchasePrice)
          put("gstRatePercent", item.gstRatePercent)
          put("stockQty", item.stockQty)
          put("lowStockThreshold", item.lowStockThreshold)
        }
      )
    }
    root.put("inventory", inventoryArr)

    val invoicesArr = JSONArray()
    payload.invoices.forEach { inv ->
      val linesArr = JSONArray()
      inv.lineItems.forEach { line ->
        linesArr.put(
          JSONObject().apply {
            put("itemId", line.itemId)
            put("name", line.name)
            put("sku", line.sku)
            put("hsnCode", line.hsnCode)
            put("unit", line.unit)
            put("qty", line.qty)
            put("unitPrice", line.unitPrice)
            put("gstRatePercent", line.gstRatePercent)
            put("taxableAmount", line.taxableAmount)
            put("gstAmount", line.gstAmount)
            put("lineTotal", line.lineTotal)
          }
        )
      }
      invoicesArr.put(
        JSONObject().apply {
          put("id", inv.id)
          put("businessProfileId", inv.resolvedProfileId())
          put("invoiceNumber", inv.invoiceNumber)
          put("partyId", inv.partyId)
          put("partyName", inv.partyName)
          put("partyGstin", inv.partyGstin)
          put("partyAddress", inv.partyAddress)
          put("partyState", inv.partyState)
          put("partyPincode", inv.partyPincode)
          put("invoiceType", inv.invoiceType)
          put("taxMode", inv.taxMode)
          put("paymentMethod", inv.paymentMethod)
          put("status", inv.status)
          put("subtotal", inv.subtotal)
          put("gstTotal", inv.gstTotal)
          put("discountAmount", inv.discountAmount)
          put("grandTotal", inv.grandTotal)
          put("paidAmount", inv.paidAmount)
          put("notes", inv.notes)
          put("lineItems", linesArr)
        }
      )
    }
    root.put("invoices", invoicesArr)

    val expensesArr = JSONArray()
    payload.expenses.forEach { exp ->
      expensesArr.put(
        JSONObject().apply {
          put("id", exp.id)
          put("businessProfileId", exp.resolvedProfileId())
          put("title", exp.title)
          put("category", exp.category)
          put("paymentMethod", exp.paymentMethod)
          put("status", exp.status)
          put("partyId", exp.partyId)
          put("partyName", exp.partyName)
          put("referenceNo", exp.referenceNo)
          put("amount", exp.amount)
          put("gstInputAmount", exp.gstInputAmount)
          put("notes", exp.notes)
        }
      )
    }
    root.put("expenses", expensesArr)

    val entriesArr = JSONArray()
    payload.dashboardEntries.forEach { entry ->
      entriesArr.put(
        JSONObject().apply {
          put("id", entry.id)
          put("businessProfileId", entry.resolvedProfileId())
          put("title", entry.title)
          put("partyName", entry.partyName)
          put("category", entry.category)
          put("amount", entry.amount)
          put("status", entry.status)
          put("referenceCode", entry.referenceCode)
        }
      )
    }
    root.put("dashboardEntries", entriesArr)

    return root.toString(2)
  }

  fun decodeFromJson(rawJson: String): Result<FinbossBackupPayload> {
    return try {
      val root = JSONObject(rawJson)
      val schemaVersion = root.optInt("schemaVersion", -1)
      require(schemaVersion >= 1) { "Unrecognized backup format or missing schemaVersion" }

      val profile =
        root.optJSONObject("businessProfile")?.let { p ->
          val slotId = p.optString("id", BusinessProfile.SLOT_MAIN_1).ifBlank { BusinessProfile.SLOT_MAIN_1 }
          BusinessProfile(
            id = slotId,
            ownerName = p.optString("ownerName", "Business Owner"),
            businessName = p.optString("businessName", "Finboss Enterprise"),
            gstin = p.optString("gstin", ""),
            address = p.optString("address", ""),
            state = p.optString("state", ""),
            pincode = p.optString("pincode", ""),
            currencySymbol = p.optString("currencySymbol", "₹"),
            fiscalYearLabel = p.optString("fiscalYearLabel", "FY 2026-27"),
            businessType = p.optString("businessType", "Retail & Wholesale"),
            monthlySalesTarget = p.optDouble("monthlySalesTarget", 250000.0),
            profileMode = p.optString("profileMode", BusinessProfile.modeForSlot(slotId)),
          )
        }

      val profilesList = mutableListOf<BusinessProfile>()
      val profilesArr = root.optJSONArray("businessProfiles") ?: JSONArray()
      for (i in 0 until profilesArr.length()) {
        val p = profilesArr.getJSONObject(i)
        val slotId = p.optString("id", "").takeIf { it in BusinessProfile.ALL_SLOTS } ?: continue
        profilesList.add(
          BusinessProfile(
            id = slotId,
            ownerName = p.optString("ownerName", "Business Owner"),
            businessName = p.optString("businessName", "Finboss Enterprise"),
            gstin = if (slotId == BusinessProfile.SLOT_PLAIN_1) "" else p.optString("gstin", ""),
            address = p.optString("address", ""),
            state = p.optString("state", ""),
            pincode = p.optString("pincode", ""),
            currencySymbol = p.optString("currencySymbol", "₹"),
            fiscalYearLabel = p.optString("fiscalYearLabel", "FY 2026-27"),
            businessType = p.optString("businessType", "Retail & Wholesale"),
            monthlySalesTarget = p.optDouble("monthlySalesTarget", 250000.0),
            profileMode = BusinessProfile.modeForSlot(slotId),
          )
        )
      }

      val parties = mutableListOf<ErpParty>()
      val partiesArr = root.optJSONArray("parties") ?: JSONArray()
      for (i in 0 until partiesArr.length()) {
        val o = partiesArr.getJSONObject(i)
        parties.add(
          ErpParty(
            id = o.optString("id", ""),
            businessProfileId = o.optString("businessProfileId", ""),
            name = o.optString("name", ""),
            partyType = o.optString("partyType", ErpParty.TYPE_CUSTOMER),
            phone = o.optString("phone", ""),
            gstin = o.optString("gstin", ""),
            address = o.optString("address", ""),
            state = o.optString("state", ""),
            pincode = o.optString("pincode", ""),
            openingDue = o.optDouble("openingDue", 0.0),
            totalBilled = o.optDouble("totalBilled", 0.0),
            totalPaid = o.optDouble("totalPaid", 0.0),
            creditLimit = o.optDouble("creditLimit", 0.0),
            notes = o.optString("notes", ""),
          )
        )
      }

      val inventory = mutableListOf<InventoryItem>()
      val inventoryArr = root.optJSONArray("inventory") ?: JSONArray()
      for (i in 0 until inventoryArr.length()) {
        val o = inventoryArr.getJSONObject(i)
        inventory.add(
          InventoryItem(
            id = o.optString("id", ""),
            businessProfileId = o.optString("businessProfileId", ""),
            name = o.optString("name", ""),
            sku = o.optString("sku", ""),
            hsnCode = o.optString("hsnCode", ""),
            category = o.optString("category", "General"),
            unit = o.optString("unit", "PCS"),
            salePrice = o.optDouble("salePrice", 0.0),
            purchasePrice = o.optDouble("purchasePrice", 0.0),
            gstRatePercent = o.optDouble("gstRatePercent", 18.0),
            stockQty = o.optDouble("stockQty", 0.0),
            lowStockThreshold = o.optDouble("lowStockThreshold", 5.0),
          )
        )
      }

      val invoices = mutableListOf<GstInvoice>()
      val invoicesArr = root.optJSONArray("invoices") ?: JSONArray()
      for (i in 0 until invoicesArr.length()) {
        val o = invoicesArr.getJSONObject(i)
        val linesArr = o.optJSONArray("lineItems") ?: JSONArray()
        val lineItems = mutableListOf<InvoiceLineItem>()
        for (j in 0 until linesArr.length()) {
          val l = linesArr.getJSONObject(j)
          lineItems.add(
            InvoiceLineItem(
              itemId = l.optString("itemId", ""),
              name = l.optString("name", ""),
              sku = l.optString("sku", ""),
              hsnCode = l.optString("hsnCode", ""),
              unit = l.optString("unit", "PCS"),
              qty = l.optDouble("qty", 1.0),
              unitPrice = l.optDouble("unitPrice", 0.0),
              gstRatePercent = l.optDouble("gstRatePercent", 18.0),
              taxableAmount = l.optDouble("taxableAmount", 0.0),
              gstAmount = l.optDouble("gstAmount", 0.0),
              lineTotal = l.optDouble("lineTotal", 0.0),
            )
          )
        }
        invoices.add(
          GstInvoice(
            id = o.optString("id", ""),
            businessProfileId = o.optString("businessProfileId", ""),
            invoiceNumber = o.optString("invoiceNumber", ""),
            partyId = o.optString("partyId", ""),
            partyName = o.optString("partyName", "Walk-in Customer"),
            partyGstin = o.optString("partyGstin", ""),
            partyAddress = o.optString("partyAddress", ""),
            partyState = o.optString("partyState", ""),
            partyPincode = o.optString("partyPincode", ""),
            invoiceType = o.optString("invoiceType", GstInvoice.TYPE_TAX_INVOICE),
            taxMode = o.optString("taxMode", GstInvoice.TAX_MODE_CGST_SGST),
            paymentMethod = o.optString("paymentMethod", GstInvoice.PAYMENT_UPI),
            status = o.optString("status", GstInvoice.STATUS_PAID),
            subtotal = o.optDouble("subtotal", 0.0),
            gstTotal = o.optDouble("gstTotal", 0.0),
            discountAmount = o.optDouble("discountAmount", 0.0),
            grandTotal = o.optDouble("grandTotal", 0.0),
            paidAmount = o.optDouble("paidAmount", 0.0),
            notes = o.optString("notes", ""),
            lineItems = lineItems,
          )
        )
      }

      val expenses = mutableListOf<ErpExpense>()
      val expensesArr = root.optJSONArray("expenses") ?: JSONArray()
      for (i in 0 until expensesArr.length()) {
        val o = expensesArr.getJSONObject(i)
        expenses.add(
          ErpExpense(
            id = o.optString("id", ""),
            businessProfileId = o.optString("businessProfileId", ""),
            title = o.optString("title", ""),
            category = o.optString("category", ErpExpense.CAT_OTHER),
            paymentMethod = o.optString("paymentMethod", ErpExpense.METHOD_UPI),
            status = o.optString("status", ErpExpense.STATUS_SETTLED),
            partyId = o.optString("partyId", ""),
            partyName = o.optString("partyName", ""),
            referenceNo = o.optString("referenceNo", ""),
            amount = o.optDouble("amount", 0.0),
            gstInputAmount = o.optDouble("gstInputAmount", 0.0),
            notes = o.optString("notes", ""),
          )
        )
      }

      val entries = mutableListOf<DashboardEntry>()
      val entriesArr = root.optJSONArray("dashboardEntries") ?: JSONArray()
      for (i in 0 until entriesArr.length()) {
        val o = entriesArr.getJSONObject(i)
        entries.add(
          DashboardEntry(
            id = o.optString("id", ""),
            businessProfileId = o.optString("businessProfileId", ""),
            title = o.optString("title", ""),
            partyName = o.optString("partyName", ""),
            category = o.optString("category", DashboardEntry.CATEGORY_SALE),
            amount = o.optDouble("amount", 0.0),
            status = o.optString("status", DashboardEntry.STATUS_PAID),
            referenceCode = o.optString("referenceCode", ""),
          )
        )
      }

      Result.success(
        FinbossBackupPayload(
          schemaVersion = schemaVersion,
          exportedAtMs = root.optLong("exportedAtMs", System.currentTimeMillis()),
          businessProfile = profile,
          businessProfiles = profilesList,
          parties = parties,
          inventory = inventory,
          invoices = invoices,
          expenses = expenses,
          dashboardEntries = entries,
        )
      )
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
