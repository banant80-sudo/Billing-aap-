package com.example.data.sync

import com.example.data.model.BusinessProfile
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.model.InvoiceLineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncAndBackupTest {

  @Test
  fun backupSerializer_roundTripsFullErpWorkspace() {
    val payload =
      FinbossBackupPayload(
        exportedAtMs = 1770000000000L,
        businessProfile =
          BusinessProfile(
            id = BusinessProfile.SLOT_MAIN_1,
            ownerName = "Aarav Mehta",
            businessName = "Finboss Wholesale Mart",
            gstin = "27AABCU9603R1ZM",
            currencySymbol = "₹",
            fiscalYearLabel = "FY 2026-27",
            businessType = "Wholesale",
            monthlySalesTarget = 500000.0,
            profileMode = BusinessProfile.MODE_MAIN_GST,
          ),
        businessProfiles =
          listOf(
            BusinessProfile(
              id = BusinessProfile.SLOT_MAIN_1,
              ownerName = "Aarav Mehta",
              businessName = "Finboss Wholesale Mart",
              gstin = "27AABCU9603R1ZM",
              address = "Shop 4, APMC Phase 2",
              state = "Maharashtra",
              pincode = "400703",
              currencySymbol = "₹",
              fiscalYearLabel = "FY 2026-27",
              businessType = "Wholesale",
              monthlySalesTarget = 500000.0,
              profileMode = BusinessProfile.MODE_MAIN_GST,
            ),
            BusinessProfile(
              id = BusinessProfile.SLOT_MAIN_2,
              ownerName = "Aarav Mehta",
              businessName = "Finboss Branch 2",
              gstin = "27AABCU9603R2ZN",
              currencySymbol = "₹",
              fiscalYearLabel = "FY 2026-27",
              businessType = "Distribution",
              monthlySalesTarget = 300000.0,
              profileMode = BusinessProfile.MODE_MAIN_GST,
            ),
            BusinessProfile(
              id = BusinessProfile.SLOT_PLAIN_1,
              ownerName = "Aarav Mehta",
              businessName = "Finboss Plain Counter",
              gstin = "",
              currencySymbol = "₹",
              fiscalYearLabel = "FY 2026-27",
              businessType = "Retail & Wholesale",
              monthlySalesTarget = 120000.0,
              profileMode = BusinessProfile.MODE_PLAIN_NON_GST,
            ),
          ),
        parties =
          listOf(
            ErpParty(
              id = "p1",
              businessProfileId = BusinessProfile.SLOT_PLAIN_1,
              name = "Kiran Stores",
              partyType = ErpParty.TYPE_CUSTOMER,
              phone = "9876543210",
              gstin = "27AAACK1111B1Z2",
              address = "Station Road, Nashik",
              state = "Maharashtra",
              pincode = "422001",
              openingDue = 1200.0,
              totalBilled = 5000.0,
              totalPaid = 3000.0,
              creditLimit = 25000.0,
              notes = "Monthly settlement",
            )
          ),
        inventory =
          listOf(
            InventoryItem(
              id = "i1",
              businessProfileId = BusinessProfile.SLOT_MAIN_2,
              name = "Thermal Printer 80mm",
              sku = "SKU-TP80",
              hsnCode = "8443",
              category = "POS Hardware",
              unit = "PCS",
              salePrice = 3200.0,
              purchasePrice = 2400.0,
              gstRatePercent = 18.0,
              stockQty = 14.0,
              lowStockThreshold = 4.0,
            )
          ),
        invoices =
          listOf(
            GstInvoice(
              id = "inv1",
              businessProfileId = BusinessProfile.SLOT_MAIN_1,
              invoiceNumber = "FB-2026-009",
              partyId = "p1",
              partyName = "Kiran Stores",
              partyGstin = "27AAACK1111B1Z2",
              partyAddress = "Station Road, Nashik",
              partyState = "Maharashtra",
              partyPincode = "422001",
              invoiceType = GstInvoice.TYPE_TAX_INVOICE,
              taxMode = GstInvoice.TAX_MODE_CGST_SGST,
              paymentMethod = GstInvoice.PAYMENT_UPI,
              status = GstInvoice.STATUS_PARTIAL,
              subtotal = 3200.0,
              gstTotal = 576.0,
              discountAmount = 0.0,
              grandTotal = 3776.0,
              paidAmount = 2000.0,
              notes = "Delivered by courier",
              lineItems =
                listOf(
                  InvoiceLineItem(
                    itemId = "i1",
                    name = "Thermal Printer 80mm",
                    sku = "SKU-TP80",
                    hsnCode = "8443",
                    unit = "PCS",
                    qty = 1.0,
                    unitPrice = 3200.0,
                    gstRatePercent = 18.0,
                    taxableAmount = 3200.0,
                    gstAmount = 576.0,
                    lineTotal = 3776.0,
                  )
                ),
            )
          ),
        expenses =
          listOf(
            ErpExpense(
              id = "exp1",
              businessProfileId = BusinessProfile.SLOT_PLAIN_1,
              title = "Warehouse Electricity",
              category = ErpExpense.CAT_UTILITIES,
              paymentMethod = ErpExpense.METHOD_BANK,
              status = ErpExpense.STATUS_SETTLED,
              partyId = "",
              partyName = "MSEDCL",
              referenceNo = "BILL-992",
              amount = 4200.0,
              gstInputAmount = 0.0,
              notes = "May bill",
            )
          ),
        dashboardEntries =
          listOf(
            DashboardEntry(
              id = "ent1",
              businessProfileId = BusinessProfile.SLOT_MAIN_1,
              title = "Counter Cash Sale",
              partyName = "Walk-in",
              category = DashboardEntry.CATEGORY_SALE,
              amount = 950.0,
              status = DashboardEntry.STATUS_PAID,
              referenceCode = "POS-12",
            )
          ),
      )

    val json = FinbossBackupSerializer.encodeToJson(payload)
    val decoded = FinbossBackupSerializer.decodeFromJson(json).getOrThrow()

    assertEquals(1770000000000L, decoded.exportedAtMs)
    assertNotNull(decoded.businessProfile)
    assertEquals("Finboss Wholesale Mart", decoded.businessProfile?.businessName)
    assertEquals(500000.0, decoded.businessProfile?.monthlySalesTarget ?: 0.0, 0.01)
    assertEquals(3, decoded.businessProfiles.size)
    assertEquals("Maharashtra", decoded.businessProfiles.first { it.id == BusinessProfile.SLOT_MAIN_1 }.state)
    assertEquals("400703", decoded.businessProfiles.first { it.id == BusinessProfile.SLOT_MAIN_1 }.pincode)
    assertTrue(decoded.businessProfiles.first { it.id == BusinessProfile.SLOT_PLAIN_1 }.isPlainNonGst)

    assertEquals(1, decoded.parties.size)
    assertEquals(BusinessProfile.SLOT_PLAIN_1, decoded.parties.first().resolvedProfileId())
    assertEquals("Kiran Stores", decoded.parties.first().name)
    assertEquals("422001", decoded.parties.first().pincode)
    assertEquals(1, decoded.inventory.size)
    assertEquals(BusinessProfile.SLOT_MAIN_2, decoded.inventory.first().resolvedProfileId())
    assertEquals("SKU-TP80", decoded.inventory.first().sku)
    assertEquals("8443", decoded.inventory.first().hsnCode)
    assertEquals(1, decoded.invoices.size)
    assertEquals(BusinessProfile.SLOT_MAIN_1, decoded.invoices.first().resolvedProfileId())
    assertEquals("Nashik".let { decoded.invoices.first().partyAddress.contains(it) }, true)
    assertEquals("8443", decoded.invoices.first().lineItems.first().hsnCode)
    assertEquals(1, decoded.invoices.first().lineItems.size)
    assertEquals(3776.0, decoded.invoices.first().grandTotal, 0.01)
    assertEquals(1, decoded.expenses.size)
    assertEquals(BusinessProfile.SLOT_PLAIN_1, decoded.expenses.first().resolvedProfileId())
    assertEquals(4200.0, decoded.expenses.first().amount, 0.01)
    assertEquals(1, decoded.dashboardEntries.size)
    assertEquals("Counter Cash Sale", decoded.dashboardEntries.first().title)
    assertEquals(5, decoded.totalRecordsCount)
  }

  @Test
  fun backupSerializer_rejectsInvalidOrUnrecognizedJson() {
    assertTrue(FinbossBackupSerializer.decodeFromJson("not-json").isFailure)
    assertTrue(FinbossBackupSerializer.decodeFromJson("""{"hello":"world"}""").isFailure)
  }

  @Test
  fun syncStatusTracker_tracksPendingCollectionsAndServerConfirmation() {
    val tracker = SyncStatusTracker()
    tracker.updateCollection("invoices", hasPendingWrites = true, isFromCache = true)
    tracker.updateCollection("parties", hasPendingWrites = false, isFromCache = true)

    assertTrue(tracker.state.value.hasPendingWrites)
    assertEquals(1, tracker.state.value.pendingCollectionsCount)
    assertTrue(tracker.state.value.isFromCacheOnly)

    tracker.updateCollection("invoices", hasPendingWrites = false, isFromCache = false)
    assertFalse(tracker.state.value.hasPendingWrites)
    assertEquals(0, tracker.state.value.pendingCollectionsCount)
    assertFalse(tracker.state.value.isFromCacheOnly)
    assertTrue(tracker.state.value.lastServerConfirmedAtMs > 0L)
  }
}
