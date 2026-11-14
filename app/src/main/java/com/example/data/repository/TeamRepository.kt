package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BusinessProfile
import com.example.data.model.PhoneAccessGrant
import com.example.data.model.SalesmanPermissions
import com.example.data.model.SalesmanSeat
import com.example.data.model.SalesmanSeatConfig
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TeamRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    db =
      FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
      ),
    auth = Firebase.auth,
  )

  fun observeSalesmen(ownerUid: String): Flow<List<SalesmanSeat>> = flow {
    val colRef = db.collection("users").document(ownerUid).collection("salesmen")
    emitAll(
      colRef
        .snapshots(MetadataChanges.INCLUDE)
        .map { snapshot ->
          val list =
            snapshot.toObjects(
              SalesmanSeat::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          val orderMap = SalesmanSeatConfig.SEAT_IDS.withIndex().associate { it.value to it.index }
          list.sortedBy { orderMap[it.seatId.ifBlank { it.id }] ?: 99 }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == ownerUid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
          }
          throw error
        }
    )
  }

  fun observeMyPhoneAccess(phoneNumber: String): Flow<PhoneAccessGrant?> = flow {
    val cleanPhone = phoneNumber.trim()
    if (!SalesmanSeatConfig.isValidE164(cleanPhone)) {
      emit(null)
      return@flow
    }
    val docRef = db.collection("phone_access").document(cleanPhone)
    emitAll(
      docRef
        .snapshots(MetadataChanges.INCLUDE)
        .map { snapshot ->
          if (snapshot.exists()) {
            val grant =
              snapshot.toObject(
                PhoneAccessGrant::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
              )
            val currentUser = auth.currentUser
            val currentUid = currentUser?.uid
            if (
              grant != null &&
                grant.isActive &&
                grant.ownerUid.isNotBlank() &&
                !currentUid.isNullOrBlank()
            ) {
              val hasPhoneToken =
                currentUser.providerData.any {
                  it.providerId == com.google.firebase.auth.PhoneAuthProvider.PROVIDER_ID
                } && !currentUser.phoneNumber.isNullOrBlank()
              if (!hasPhoneToken) {
                try {
                  db.collection("salesman_sessions")
                    .document(currentUid)
                    .set(
                      mapOf(
                        "uid" to currentUid,
                        "phoneNumber" to cleanPhone,
                        "ownerUid" to grant.ownerUid,
                        "seatId" to grant.seatId.ifBlank { "seat_1" },
                        "updatedAt" to FieldValue.serverTimestamp(),
                      )
                    )
                    .await()
                } catch (_: Exception) {
                  // Offline or transient; cached session or next snapshot will retry.
                }
              }
            }
            grant
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            emit(null)
          } else {
            throw error
          }
        }
    )
  }

  suspend fun saveSalesmanSeat(
    ownerUid: String,
    seatId: String,
    existingSeat: SalesmanSeat?,
    allSeats: List<SalesmanSeat>,
    name: String,
    rawPhoneE164: String,
    roleLabel: String,
    isActive: Boolean,
    allowedProfiles: List<String>,
    permissions: SalesmanPermissions,
  ): Result<Unit> {
    if (seatId !in SalesmanSeatConfig.SEAT_IDS) {
      return Result.failure(IllegalArgumentException("Maximum 5 salesman accounts allowed"))
    }
    val cleanName = name.trim().take(60)
    if (cleanName.isEmpty()) {
      return Result.failure(IllegalArgumentException("Salesman name is required"))
    }
    val cleanPhone = rawPhoneE164.trim()
    if (!SalesmanSeatConfig.isValidE164(cleanPhone)) {
      return Result.failure(
        IllegalArgumentException("Enter a valid mobile number with country code (e.g. +919876543210)")
      )
    }
    val duplicateSeat =
      allSeats.firstOrNull { seat ->
        val id = seat.seatId.ifBlank { seat.id }
        id != seatId && seat.phoneNumber.trim() == cleanPhone
      }
    if (duplicateSeat != null) {
      return Result.failure(
        IllegalArgumentException("Mobile number is already assigned to ${duplicateSeat.name}")
      )
    }
    val cleanProfiles =
      allowedProfiles.filter { it in BusinessProfile.ALL_SLOTS }.distinct().ifEmpty {
        listOf(BusinessProfile.SLOT_MAIN_1)
      }
    val cleanRole = roleLabel.trim().ifBlank { "Salesman" }.take(40)
    val permsMap = permissions.toFirestoreMap()

    val seatRef = db.collection("users").document(ownerUid).collection("salesmen").document(seatId)
    val grantRef = db.collection("phone_access").document(cleanPhone)
    val previousPhone = existingSeat?.phoneNumber?.trim()?.takeIf { it.isNotBlank() }

    return try {
      val isSamePhoneUpdate = existingSeat != null && previousPhone == cleanPhone
      val batch = db.batch()
      if (existingSeat != null) {
        batch.update(
          seatRef,
          mapOf(
            "name" to cleanName,
            "phoneNumber" to cleanPhone,
            "roleLabel" to cleanRole,
            "isActive" to isActive,
            "allowedProfiles" to cleanProfiles,
            "permissions" to permsMap,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        batch.set(
          seatRef,
          mapOf(
            "userId" to ownerUid,
            "seatId" to seatId,
            "name" to cleanName,
            "phoneNumber" to cleanPhone,
            "roleLabel" to cleanRole,
            "isActive" to isActive,
            "allowedProfiles" to cleanProfiles,
            "permissions" to permsMap,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      if (previousPhone != null && previousPhone != cleanPhone) {
        val oldGrantRef = db.collection("phone_access").document(previousPhone)
        batch.delete(oldGrantRef)
      }

      if (isSamePhoneUpdate) {
        batch.update(
          grantRef,
          mapOf(
            "seatId" to seatId,
            "salesmanName" to cleanName,
            "roleLabel" to cleanRole,
            "isActive" to isActive,
            "allowedProfiles" to cleanProfiles,
            "permissions" to permsMap,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        batch.set(
          grantRef,
          mapOf(
            "ownerUid" to ownerUid,
            "seatId" to seatId,
            "phoneNumber" to cleanPhone,
            "salesmanName" to cleanName,
            "roleLabel" to cleanRole,
            "isActive" to isActive,
            "allowedProfiles" to cleanProfiles,
            "permissions" to permsMap,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      try {
        batch.commit().await()
      } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && !isSamePhoneUpdate) {
          val conflictErr =
            IllegalArgumentException("This mobile number is already registered with another business")
          return Result.failure(conflictErr)
        }
        throw e
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, seatRef.path)
      Result.failure(e)
    }
  }

  suspend fun toggleSalesmanActive(
    ownerUid: String,
    seat: SalesmanSeat,
    isActive: Boolean,
  ): Result<Unit> {
    val targetSeatId = seat.seatId.ifBlank { seat.id }
    if (targetSeatId !in SalesmanSeatConfig.SEAT_IDS) {
      return Result.failure(IllegalArgumentException("Invalid seat ID"))
    }
    val seatRef =
      db.collection("users").document(ownerUid).collection("salesmen").document(targetSeatId)
    val cleanPhone = seat.phoneNumber.trim()
    return try {
      val batch = db.batch()
      batch.update(
        seatRef,
        mapOf(
          "isActive" to isActive,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      if (SalesmanSeatConfig.isValidE164(cleanPhone)) {
        val grantRef = db.collection("phone_access").document(cleanPhone)
        batch.update(
          grantRef,
          mapOf(
            "isActive" to isActive,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, seatRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteSalesmanSeat(ownerUid: String, seat: SalesmanSeat): Result<Unit> {
    val targetSeatId = seat.seatId.ifBlank { seat.id }
    if (targetSeatId !in SalesmanSeatConfig.SEAT_IDS) {
      return Result.failure(IllegalArgumentException("Invalid seat ID"))
    }
    val seatRef =
      db.collection("users").document(ownerUid).collection("salesmen").document(targetSeatId)
    val cleanPhone = seat.phoneNumber.trim()
    return try {
      val batch = db.batch()
      batch.delete(seatRef)
      if (SalesmanSeatConfig.isValidE164(cleanPhone)) {
        val grantRef = db.collection("phone_access").document(cleanPhone)
        batch.delete(grantRef)
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, seatRef.path)
      Result.failure(e)
    }
  }
}
