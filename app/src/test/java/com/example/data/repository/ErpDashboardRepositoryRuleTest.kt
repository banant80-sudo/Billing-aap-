package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.BusinessProfile
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpModuleConfig
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErpDashboardRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureWorkspaceInitialized_authenticatedOwner_createsProfileAndModules() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = ErpDashboardRepository(firestore, auth)

    val initResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.ensureWorkspaceInitialized("Alice Traders", ALICE_EMAIL)
      }
    assertTrue(initResult.isSuccess)

    val profile =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeBusinessProfile().first { it != null && it.userId == uid }
      }
    assertNotNull(profile)
    assertEquals("Alice Traders", profile?.ownerName)

    val modules =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeModules().first { it.isNotEmpty() }
      }
    assertEquals(6, modules.size)
    assertEquals("dashboard", modules.first().code)
  }

  @Test
  fun addDashboardEntry_andMarkPaid_updatesRealTimeFlow() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = ErpDashboardRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureWorkspaceInitialized("Alice", ALICE_EMAIL)
    }

    val ref = "INV-${UUID.randomUUID().toString().take(6)}"
    val createResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.addDashboardEntry(
          title = "Hardware Batch Sale",
          partyName = "Apex Builders",
          category = DashboardEntry.CATEGORY_SALE,
          amount = 42500.0,
          status = DashboardEntry.STATUS_PENDING,
          referenceCode = ref,
        )
      }
    assertTrue(createResult.isSuccess)
    val entryId = createResult.getOrThrow()

    val entries =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeDashboardEntries().first { list -> list.any { it.id == entryId } }
      }
    assertEquals(DashboardEntry.STATUS_PENDING, entries.first { it.id == entryId }.status)

    val markPaidResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.markEntryPaid(entryId) }
    assertTrue(markPaidResult.isSuccess)

    val updatedEntries =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeDashboardEntries().first { list ->
          list.any { it.id == entryId && it.status == DashboardEntry.STATUS_PAID }
        }
      }
    assertEquals(DashboardEntry.STATUS_PAID, updatedEntries.first { it.id == entryId }.status)
  }

  @Test
  fun setModuleStatus_queuedAndPin_succeedsForOwner() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = ErpDashboardRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureWorkspaceInitialized("Alice", ALICE_EMAIL)
    }

    val modules = withTimeout(FLOW_TIMEOUT_MS) { repo.observeModules().first { it.isNotEmpty() } }
    val inventory = modules.first { it.code == "inventory" }

    val statusResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.setModuleStatus(inventory, ErpModuleConfig.STATUS_QUEUED)
      }
    assertTrue(statusResult.isSuccess)
  }

  @Test
  fun multiBusinessProfiles_twoMainAndOnePlainNonGst_enforcesRulesAndDeletion() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = ErpDashboardRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureWorkspaceInitialized("Alice", ALICE_EMAIL)
    }

    val saveMain2 =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveBusinessProfileAccount(
          slotId = BusinessProfile.SLOT_MAIN_2,
          ownerName = "Alice",
          businessName = "Alice Wholesale Branch 2",
          gstin = "27AABCU9603R1ZM",
          currencySymbol = "₹",
          fiscalYearLabel = "FY 2026-27",
          businessType = "Distribution",
          monthlySalesTarget = 400000.0,
          address = "21 Ring Road, Surat",
          state = "Gujarat",
          pincode = "395002",
        )
      }
    assertTrue(saveMain2.isSuccess)

    val savePlain1 =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveBusinessProfileAccount(
          slotId = BusinessProfile.SLOT_PLAIN_1,
          ownerName = "Alice",
          businessName = "Alice Plain Cash Counter",
          gstin = "27SHOULDBESTRIPPED",
          currencySymbol = "₹",
          fiscalYearLabel = "FY 2026-27",
          businessType = "Retail & Wholesale",
          monthlySalesTarget = 100000.0,
          address = "Main Bazaar Shop 4",
          state = "Maharashtra",
          pincode = "400001",
        )
      }
    assertTrue(savePlain1.isSuccess)

    val profiles =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeBusinessProfiles().first { it.size == 3 }
      }
    assertEquals(3, profiles.size)
    val main2 = profiles.first { it.id == BusinessProfile.SLOT_MAIN_2 }
    assertEquals("21 Ring Road, Surat", main2.address)
    assertEquals("Gujarat", main2.state)
    assertEquals("395002", main2.pincode)
    val plain = profiles.first { it.id == BusinessProfile.SLOT_PLAIN_1 }
    assertEquals(BusinessProfile.MODE_PLAIN_NON_GST, plain.profileMode)
    assertEquals("", plain.gstin)
    assertTrue(plain.isPlainNonGst)

    // Invalid 4th slot is rejected by client & rules
    val fourthSlot =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveBusinessProfileAccount(
          slotId = "main_3",
          ownerName = "Alice",
          businessName = "Fourth Shop",
          gstin = "",
          currencySymbol = "₹",
          fiscalYearLabel = "FY 2026-27",
          businessType = "Services",
          monthlySalesTarget = 50000.0,
        )
      }
    assertTrue(fourthSlot.isFailure)

    // Primary main_1 cannot be deleted; plain_1 can be deleted
    assertTrue(repo.deleteBusinessProfileAccount(BusinessProfile.SLOT_MAIN_1).isFailure)
    assertTrue(repo.deleteBusinessProfileAccount(BusinessProfile.SLOT_PLAIN_1).isSuccess)
  }

  @Test
  fun crossUserRead_aliceDataByBob_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = ErpDashboardRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.ensureWorkspaceInitialized("Alice", ALICE_EMAIL)
    }

    signInTestUser(BOB_EMAIL)
    val bobDirectReadError =
      try {
        withTimeout(DEFAULT_TIMEOUT_MS) {
          firestore.collection("users").document(aliceUid).collection("erp_modules").get().let { task ->
            com.google.android.gms.tasks.Tasks.await(task)
          }
        }
        null
      } catch (e: Exception) {
        e
      }
    assertNotNull(bobDirectReadError)
    val fsErr = bobDirectReadError?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
  }

  @Test
  fun observeModules_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val error =
      try {
        withTimeout(FLOW_TIMEOUT_MS) {
          firestore
            .collection("users")
            .document("some_uid")
            .collection("erp_modules")
            .get()
            .let { com.google.android.gms.tasks.Tasks.await(it) }
        }
        null
      } catch (e: Exception) {
        e
      }
    assertNotNull(error)
    val fsErr = error?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_erp@test.com"
    const val BOB_EMAIL = "bob_erp@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
