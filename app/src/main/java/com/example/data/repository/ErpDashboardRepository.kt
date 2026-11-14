package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BusinessProfile
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpModuleConfig
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
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

class ErpDashboardRepository(
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

  fun observeBusinessProfiles(): Flow<List<BusinessProfile>> = flow {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("business_profiles")
    emitAll(
      colRef
        .snapshots(MetadataChanges.INCLUDE)
        .map { snapshot ->
          syncTracker?.updateCollection(
            collectionName = "business_profiles",
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
            isFromCache = snapshot.metadata.isFromCache(),
          )
          val list =
            snapshot.toObjects(
              BusinessProfile::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          val orderMap = BusinessProfile.ALL_SLOTS.withIndex().associate { it.value to it.index }
          list.sortedBy { orderMap[it.id] ?: 99 }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, colRef.path, uid)
          throw error
        }
    )
  }

  fun observeBusinessProfile(): Flow<BusinessProfile?> = flow {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    emitAll(
      docRef
        .snapshots(MetadataChanges.INCLUDE)
        .map { snapshot ->
          syncTracker?.updateCollection(
            collectionName = "profile",
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
            isFromCache = snapshot.metadata.isFromCache(),
          )
          if (snapshot.exists()) {
            snapshot.toObject(
              BusinessProfile::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          } else {
            null
          }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.GET, docRef.path, uid)
          throw error
        }
    )
  }

  fun observeModules(): Flow<List<ErpModuleConfig>> = flow {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("erp_modules")
    emitAll(
      colRef
        .orderBy("sortOrder", Query.Direction.ASCENDING)
        .snapshots(MetadataChanges.INCLUDE)
        .map { snapshot ->
          syncTracker?.updateCollection(
            collectionName = "erp_modules",
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
            isFromCache = snapshot.metadata.isFromCache(),
          )
          snapshot.toObjects(
            ErpModuleConfig::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, colRef.path, uid)
          throw error
        }
    )
  }

  fun observeDashboardEntries(): Flow<List<DashboardEntry>> = flow {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("dashboard_entries")
    emitAll(
      colRef
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .snapshots(MetadataChanges.INCLUDE)
        .map { snapshot ->
          syncTracker?.updateCollection(
            collectionName = "dashboard_entries",
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
            isFromCache = snapshot.metadata.isFromCache(),
          )
          snapshot.toObjects(
            DashboardEntry::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, colRef.path, uid)
          throw error
        }
    )
  }

  suspend fun ensureWorkspaceInitialized(
    ownerName: String,
    ownerEmail: String?,
  ): Result<Unit> {
    val uid = requireUserId()
    val userDocRef = db.collection("users").document(uid)
    val profilesColRef = userDocRef.collection("business_profiles")
    val main1Ref = profilesColRef.document(BusinessProfile.SLOT_MAIN_1)
    val modulesColRef = userDocRef.collection("erp_modules")

    return try {
      val existingProfile = userDocRef.get().await()
      val safeName = ownerName.trim().ifBlank { "Business Owner" }.take(80)
      if (!existingProfile.exists()) {
        val profilePayload =
          buildMap<String, Any> {
            put("userId", uid)
            put("ownerName", safeName)
            if (!ownerEmail.isNullOrBlank()) {
              put("ownerEmail", ownerEmail.trim().take(150))
            }
            put("businessName", "$safeName Enterprise".take(100))
            put("gstin", "")
            put("currencySymbol", "₹")
            put("fiscalYearLabel", "FY 2026-27")
            put("businessType", "Retail & Wholesale")
            put("monthlySalesTarget", 250000.0)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
          }
        userDocRef.set(profilePayload).await()
      }

      val existingMain1 = main1Ref.get().await()
      if (!existingMain1.exists()) {
        val rootSnap = if (existingProfile.exists()) existingProfile else userDocRef.get().await()
        val seedOwner = rootSnap.getString("ownerName")?.ifBlank { safeName } ?: safeName
        val seedEmail = rootSnap.getString("ownerEmail") ?: ownerEmail
        val seedBiz = rootSnap.getString("businessName")?.ifBlank { "$seedOwner Enterprise" }
          ?: "$seedOwner Enterprise"
        val seedGstin = rootSnap.getString("gstin") ?: ""
        val seedCurrency = rootSnap.getString("currencySymbol")?.ifBlank { "₹" } ?: "₹"
        val seedFy = rootSnap.getString("fiscalYearLabel")?.ifBlank { "FY 2026-27" } ?: "FY 2026-27"
        val seedType = rootSnap.getString("businessType")?.ifBlank { "Retail & Wholesale" }
          ?: "Retail & Wholesale"
        val seedTarget = rootSnap.getDouble("monthlySalesTarget") ?: 250000.0

        val slotPayload =
          buildMap<String, Any> {
            put("userId", uid)
            put("ownerName", seedOwner.take(80))
            if (!seedEmail.isNullOrBlank()) {
              put("ownerEmail", seedEmail.trim().take(150))
            }
            put("businessName", seedBiz.take(100))
            put("gstin", seedGstin.take(30))
            put("currencySymbol", seedCurrency.take(5))
            put("fiscalYearLabel", seedFy.take(20))
            put("businessType", seedType.take(40))
            put("monthlySalesTarget", seedTarget.coerceIn(0.0, 1_000_000_000.0))
            put("profileMode", BusinessProfile.MODE_MAIN_GST)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
          }
        main1Ref.set(slotPayload).await()
      }

      val existingModules = modulesColRef.get().await()
      if (existingModules.isEmpty) {
        val batch = db.batch()
        ErpModuleConfig.defaultRoadmap(uid).forEach { module ->
          val docRef = modulesColRef.document(module.id)
          val payload =
            mapOf(
              "userId" to uid,
              "code" to module.code,
              "title" to module.title,
              "subtitle" to module.subtitle,
              "status" to module.status,
              "isPinned" to module.isPinned,
              "sortOrder" to module.sortOrder,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          batch.set(docRef, payload)
        }
        batch.commit().await()
      } else {
        val liveCodes = setOf("billing", "inventory", "parties", "expenses", "gst_reports")
        val docsToActivate =
          existingModules.documents.filter { doc ->
            val code = doc.getString("code")
            val status = doc.getString("status")
            code in liveCodes && status != ErpModuleConfig.STATUS_ACTIVE
          }
        if (docsToActivate.isNotEmpty()) {
          val batch = db.batch()
          docsToActivate.forEach { doc ->
            batch.update(
              doc.reference,
              mapOf(
                "status" to ErpModuleConfig.STATUS_ACTIVE,
                "isPinned" to true,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
          batch.commit().await()
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userDocRef.path)
      Result.failure(e)
    }
  }

  suspend fun saveBusinessProfile(
    ownerName: String,
    businessName: String,
    gstin: String,
    currencySymbol: String,
    fiscalYearLabel: String,
    businessType: String,
    monthlySalesTarget: Double,
    address: String = "",
    state: String = "",
    pincode: String = "",
  ): Result<Unit> =
    saveBusinessProfileAccount(
      slotId = BusinessProfile.SLOT_MAIN_1,
      ownerName = ownerName,
      businessName = businessName,
      gstin = gstin,
      currencySymbol = currencySymbol,
      fiscalYearLabel = fiscalYearLabel,
      businessType = businessType,
      monthlySalesTarget = monthlySalesTarget,
      address = address,
      state = state,
      pincode = pincode,
    )

  suspend fun saveBusinessProfileAccount(
    slotId: String,
    ownerName: String,
    businessName: String,
    gstin: String,
    currencySymbol: String,
    fiscalYearLabel: String,
    businessType: String,
    monthlySalesTarget: Double,
    address: String = "",
    state: String = "",
    pincode: String = "",
  ): Result<Unit> {
    val uid = requireUserId()
    if (slotId !in BusinessProfile.ALL_SLOTS) {
      return Result.failure(
        IllegalArgumentException("Maximum 3 business profiles allowed (2 Main GST + 1 Plain Non-GST)")
      )
    }
    val slotRef =
      db.collection("users").document(uid).collection("business_profiles").document(slotId)
    val userDocRef = db.collection("users").document(uid)
    return try {
      val cleanOwner = ownerName.trim().ifBlank { "Business Owner" }.take(80)
      val cleanBusiness = businessName.trim().ifBlank { "My Enterprise" }.take(100)
      val mode = BusinessProfile.modeForSlot(slotId)
      val cleanGstin =
        if (slotId == BusinessProfile.SLOT_PLAIN_1) "" else gstin.trim().uppercase().take(30)
      val cleanAddress = address.trim().take(200)
      val cleanState = state.trim().take(60)
      val cleanPincode = pincode.trim().filter { it.isDigit() || it == '-' }.take(12)
      val cleanCurrency = currencySymbol.trim().ifBlank { "₹" }.take(5)
      val cleanFy = fiscalYearLabel.trim().ifBlank { "FY 2026-27" }.take(20)
      val cleanType = businessType.trim().ifBlank { "Retail & Wholesale" }.take(40)
      val clampedTarget = monthlySalesTarget.coerceIn(0.0, 1_000_000_000.0)
      val email = auth.currentUser?.email

      val slotSnapshot = slotRef.get().await()
      if (slotSnapshot.exists()) {
        val updates =
          buildMap<String, Any> {
            put("ownerName", cleanOwner)
            if (!email.isNullOrBlank()) {
              put("ownerEmail", email.trim().take(150))
            }
            put("businessName", cleanBusiness)
            put("gstin", cleanGstin)
            put("address", cleanAddress)
            put("state", cleanState)
            put("pincode", cleanPincode)
            put("currencySymbol", cleanCurrency)
            put("fiscalYearLabel", cleanFy)
            put("businessType", cleanType)
            put("monthlySalesTarget", clampedTarget)
            put("updatedAt", FieldValue.serverTimestamp())
          }
        slotRef.update(updates).awaitOfflineAware()
      } else {
        val payload =
          buildMap<String, Any> {
            put("userId", uid)
            put("ownerName", cleanOwner)
            if (!email.isNullOrBlank()) {
              put("ownerEmail", email.trim().take(150))
            }
            put("businessName", cleanBusiness)
            put("gstin", cleanGstin)
            put("address", cleanAddress)
            put("state", cleanState)
            put("pincode", cleanPincode)
            put("currencySymbol", cleanCurrency)
            put("fiscalYearLabel", cleanFy)
            put("businessType", cleanType)
            put("monthlySalesTarget", clampedTarget)
            put("profileMode", mode)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
          }
        slotRef.set(payload).awaitOfflineAware()
      }

      if (slotId == BusinessProfile.SLOT_MAIN_1) {
        val rootSnapshot = userDocRef.get().await()
        if (rootSnapshot.exists()) {
          userDocRef
            .update(
              mapOf(
                "ownerName" to cleanOwner,
                "businessName" to cleanBusiness,
                "gstin" to cleanGstin,
                "address" to cleanAddress,
                "state" to cleanState,
                "pincode" to cleanPincode,
                "currencySymbol" to cleanCurrency,
                "fiscalYearLabel" to cleanFy,
                "businessType" to cleanType,
                "monthlySalesTarget" to clampedTarget,
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .awaitOfflineAware()
        } else {
          val rootPayload =
            buildMap<String, Any> {
              put("userId", uid)
              put("ownerName", cleanOwner)
              if (!email.isNullOrBlank()) {
                put("ownerEmail", email.trim().take(150))
              }
              put("businessName", cleanBusiness)
              put("gstin", cleanGstin)
              put("address", cleanAddress)
              put("state", cleanState)
              put("pincode", cleanPincode)
              put("currencySymbol", cleanCurrency)
              put("fiscalYearLabel", cleanFy)
              put("businessType", cleanType)
              put("monthlySalesTarget", clampedTarget)
              put("createdAt", FieldValue.serverTimestamp())
              put("updatedAt", FieldValue.serverTimestamp())
            }
          userDocRef.set(rootPayload).awaitOfflineAware()
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, slotRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteBusinessProfileAccount(slotId: String): Result<Unit> {
    val uid = requireUserId()
    if (slotId != BusinessProfile.SLOT_MAIN_2 && slotId != BusinessProfile.SLOT_PLAIN_1) {
      return Result.failure(
        IllegalArgumentException("Primary Main GST account cannot be deleted")
      )
    }
    val slotRef =
      db.collection("users").document(uid).collection("business_profiles").document(slotId)
    return try {
      slotRef.delete().awaitOfflineAware()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, slotRef.path)
      Result.failure(e)
    }
  }

  suspend fun toggleModulePin(module: ErpModuleConfig): Result<Unit> {
    val uid = requireUserId()
    require(module.id.isNotBlank()) { "Module ID must not be blank" }
    val docRef = db.collection("users").document(uid).collection("erp_modules").document(module.id)
    return try {
      docRef
        .update(
          mapOf(
            "isPinned" to !module.isPinned,
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

  suspend fun setModuleStatus(module: ErpModuleConfig, newStatus: String): Result<Unit> {
    val uid = requireUserId()
    require(module.id.isNotBlank()) { "Module ID must not be blank" }
    if (module.code == "dashboard") return Result.success(Unit)
    val docRef = db.collection("users").document(uid).collection("erp_modules").document(module.id)
    return try {
      docRef
        .update(
          mapOf(
            "status" to newStatus,
            "isPinned" to (newStatus == ErpModuleConfig.STATUS_QUEUED || module.isPinned),
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

  suspend fun addDashboardEntry(
    title: String,
    partyName: String,
    category: String,
    amount: Double,
    status: String,
    referenceCode: String,
    businessProfileId: String = BusinessProfile.SLOT_MAIN_1,
  ): Result<String> {
    val uid = requireUserId()
    val safeProfileId =
      if (businessProfileId in BusinessProfile.ALL_SLOTS) businessProfileId
      else BusinessProfile.SLOT_MAIN_1
    val docRef = db.collection("users").document(uid).collection("dashboard_entries").document()
    return try {
      val cleanTitle = title.trim().take(100)
      require(cleanTitle.isNotEmpty()) { "Entry title must not be blank" }
      require(amount > 0.0 && amount <= 1_000_000_000.0) { "Amount must be positive" }

      val payload =
        mapOf(
          "userId" to uid,
          "businessProfileId" to safeProfileId,
          "title" to cleanTitle,
          "partyName" to partyName.trim().take(100),
          "category" to category,
          "amount" to amount,
          "status" to status,
          "referenceCode" to referenceCode.trim().take(30),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      docRef.set(payload).awaitOfflineAware()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun markEntryPaid(entryId: String): Result<Unit> {
    val uid = requireUserId()
    require(entryId.isNotBlank()) { "Entry ID must not be blank" }
    val docRef = db.collection("users").document(uid).collection("dashboard_entries").document(entryId)
    return try {
      docRef
        .update(
          mapOf(
            "status" to DashboardEntry.STATUS_PAID,
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

  suspend fun deleteDashboardEntry(entryId: String): Result<Unit> {
    val uid = requireUserId()
    require(entryId.isNotBlank()) { "Entry ID must not be blank" }
    val docRef = db.collection("users").document(uid).collection("dashboard_entries").document(entryId)
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

  suspend fun syncPendingWritesNow(): Result<Unit> {
    requireUserId()
    return try {
      withTimeoutOrNull(8000L) { db.waitForPendingWrites().await(); true }
        ?: throw IllegalStateException("Sync timed out; changes remain safely queued offline")
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private suspend fun Task<Void>.awaitOfflineAware() {
    if (!isOnlineProvider()) {
      // Write already saved to Firestore persistent local cache and emitted to snapshot listeners.
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
