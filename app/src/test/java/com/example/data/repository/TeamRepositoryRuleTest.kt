package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.BusinessProfile
import com.example.data.model.SalesmanPermissions
import com.example.data.model.SalesmanSeatConfig
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private fun randomPhoneSuffix(): String =
    (1000000..9999999).random().toString()

  @Test
  fun ownerCanManageUpToFiveSalesmanSeats_andSixthSeatIsRejected() = runBlocking {
    val ownerUid = signInTestUser("team-owner-${UUID.randomUUID().toString().take(6)}@finboss.test")
    val teamRepo = TeamRepository(firestore, auth)

    val phones = SalesmanSeatConfig.SEAT_IDS.indices.map { i -> "+9191${i}${randomPhoneSuffix()}" }

    SalesmanSeatConfig.SEAT_IDS.forEachIndexed { idx, seatId ->
      val existingSeats = withTimeout(FLOW_TIMEOUT_MS) { teamRepo.observeSalesmen(ownerUid).first() }
      val result =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          teamRepo.saveSalesmanSeat(
            ownerUid = ownerUid,
            seatId = seatId,
            existingSeat = null,
            allSeats = existingSeats,
            name = "Salesman ${idx + 1}",
            rawPhoneE164 = phones[idx],
            roleLabel = "Counter Billing",
            isActive = true,
            allowedProfiles = listOf(BusinessProfile.SLOT_MAIN_1),
            permissions = SalesmanPermissions.COUNTER_BILLING,
          )
        }
      assertTrue("Seat $seatId should save", result.isSuccess)
    }

    val allFive =
      withTimeout(FLOW_TIMEOUT_MS) {
        teamRepo.observeSalesmen(ownerUid).first { it.size == 5 }
      }
    assertEquals(5, allFive.size)

    // Attempt 6th seat via repository
    val sixthRepoResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        teamRepo.saveSalesmanSeat(
          ownerUid = ownerUid,
          seatId = "seat_6",
          existingSeat = null,
          allSeats = allFive,
          name = "Extra Salesman 6",
          rawPhoneE164 = "+9199${randomPhoneSuffix()}1",
          roleLabel = "Counter Billing",
          isActive = true,
          allowedProfiles = listOf(BusinessProfile.SLOT_MAIN_1),
          permissions = SalesmanPermissions.COUNTER_BILLING,
        )
      }
    assertTrue(sixthRepoResult.isFailure)

    // Attempt direct Firestore write for seat_6 (must be rejected by firestore.rules)
    var caught: Throwable? = null
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore
          .collection("users")
          .document(ownerUid)
          .collection("salesmen")
          .document("seat_6")
          .set(
            mapOf(
              "userId" to ownerUid,
              "seatId" to "seat_6",
              "name" to "Bypass 6",
              "phoneNumber" to "+9199${randomPhoneSuffix()}2",
              "roleLabel" to "Counter Billing",
              "isActive" to true,
              "allowedProfiles" to listOf(BusinessProfile.SLOT_MAIN_1),
              "permissions" to SalesmanPermissions.COUNTER_BILLING.toFirestoreMap(),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
    } catch (e: Throwable) {
      caught = e
    }
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      caught?.unwrapFirestoreException()?.code,
    )
  }

  @Test
  fun ownerCanEditTogglePhoneSwapAndDeleteSalesmanSeat() = runBlocking {
    val ownerUid = signInTestUser("team-edit-${UUID.randomUUID().toString().take(6)}@finboss.test")
    val teamRepo = TeamRepository(firestore, auth)

    val phone1 = "+9192${randomPhoneSuffix()}1"
    val phone2 = "+9193${randomPhoneSuffix()}2"

    val createRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        teamRepo.saveSalesmanSeat(
          ownerUid = ownerUid,
          seatId = "seat_1",
          existingSeat = null,
          allSeats = emptyList(),
          name = "Ramesh Kumar",
          rawPhoneE164 = phone1,
          roleLabel = "Counter Billing",
          isActive = true,
          allowedProfiles = listOf(BusinessProfile.SLOT_MAIN_1, BusinessProfile.SLOT_PLAIN_1),
          permissions = SalesmanPermissions.COUNTER_BILLING,
        )
      }
    assertTrue(createRes.isSuccess)

    val seat1 =
      withTimeout(FLOW_TIMEOUT_MS) {
        teamRepo.observeSalesmen(ownerUid).first { it.any { s -> s.seatId == "seat_1" } }
      }.first { it.seatId == "seat_1" }

    // Update permissions & phone number (tests createdAt immutability + old phone_access cleanup)
    val customPerms =
      SalesmanPermissions.COUNTER_BILLING.copy(
        canManageParties = true,
        canViewReports = true,
      )
    val updateRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        teamRepo.saveSalesmanSeat(
          ownerUid = ownerUid,
          seatId = "seat_1",
          existingSeat = seat1,
          allSeats = listOf(seat1),
          name = "Ramesh Senior",
          rawPhoneE164 = phone2,
          roleLabel = "Field Sales & Khata",
          isActive = true,
          allowedProfiles = listOf(BusinessProfile.SLOT_PLAIN_1),
          permissions = customPerms,
        )
      }
    assertTrue("Seat update & phone swap should succeed", updateRes.isSuccess)

    // Verify old phone_access doc was deleted and new phone_access doc has updated fields
    val oldGrantSnap = firestore.collection("phone_access").document(phone1).get().await()
    assertFalse("Old phone access doc should be deleted", oldGrantSnap.exists())

    val newGrantSnap = firestore.collection("phone_access").document(phone2).get().await()
    assertTrue("New phone access doc should exist", newGrantSnap.exists())
    assertEquals("Ramesh Senior", newGrantSnap.getString("salesmanName"))
    assertEquals(listOf(BusinessProfile.SLOT_PLAIN_1), newGrantSnap.get("allowedProfiles"))

    // Toggle inactive
    val updatedSeat =
      withTimeout(FLOW_TIMEOUT_MS) {
        teamRepo.observeSalesmen(ownerUid).first { list ->
          list.any { it.seatId == "seat_1" && it.phoneNumber == phone2 }
        }
      }.first { it.seatId == "seat_1" }

    val pauseRes = withTimeout(DEFAULT_TIMEOUT_MS) { teamRepo.toggleSalesmanActive(ownerUid, updatedSeat, false) }
    assertTrue("Toggle inactive should succeed", pauseRes.isSuccess)

    val pausedGrant = firestore.collection("phone_access").document(phone2).get().await()
    assertEquals(false, pausedGrant.getBoolean("isActive"))

    // Delete seat
    val deleteRes = withTimeout(DEFAULT_TIMEOUT_MS) { teamRepo.deleteSalesmanSeat(ownerUid, updatedSeat) }
    assertTrue("Delete seat should succeed", deleteRes.isSuccess)

    val afterDeleteGrant = firestore.collection("phone_access").document(phone2).get().await()
    assertFalse("Phone access grant should be deleted with seat", afterDeleteGrant.exists())
    val remaining = withTimeout(FLOW_TIMEOUT_MS) { teamRepo.observeSalesmen(ownerUid).first() }
    assertTrue(remaining.isEmpty())
  }

  @Test
  fun anotherOwnerCannotOverwriteExistingPhoneAccessOrSalesmanSeat() = runBlocking {
    val ownerAUid = signInTestUser("team-a-${UUID.randomUUID().toString().take(6)}@finboss.test")
    val teamRepo = TeamRepository(firestore, auth)
    val sharedPhone = "+9194${randomPhoneSuffix()}5"

    val createRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        teamRepo.saveSalesmanSeat(
          ownerUid = ownerAUid,
          seatId = "seat_1",
          existingSeat = null,
          allSeats = emptyList(),
          name = "Owner A Salesman",
          rawPhoneE164 = sharedPhone,
          roleLabel = "Counter Billing",
          isActive = true,
          allowedProfiles = listOf(BusinessProfile.SLOT_MAIN_1),
          permissions = SalesmanPermissions.COUNTER_BILLING,
        )
      }
    assertTrue(createRes.isSuccess)
    auth.signOut()

    // Sign in as Owner B
    val ownerBUid = signInTestUser("team-b-${UUID.randomUUID().toString().take(6)}@finboss.test")
    val hijackRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        teamRepo.saveSalesmanSeat(
          ownerUid = ownerBUid,
          seatId = "seat_1",
          existingSeat = null,
          allSeats = emptyList(),
          name = "Hijack Attempt",
          rawPhoneE164 = sharedPhone,
          roleLabel = "Counter Billing",
          isActive = true,
          allowedProfiles = listOf(BusinessProfile.SLOT_MAIN_1),
          permissions = SalesmanPermissions.COUNTER_BILLING,
        )
      }
    assertTrue("Owner B must not hijack Owner A's phone access doc", hijackRes.isFailure)

    // Cross-owner seat read must be denied
    var readCaught: Throwable? = null
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("users").document(ownerAUid).collection("salesmen").get().await()
      }
    } catch (e: Throwable) {
      readCaught = e
    }
    assertNotNull(readCaught)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      readCaught?.unwrapFirestoreException()?.code,
    )
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 6000L
  }
}
