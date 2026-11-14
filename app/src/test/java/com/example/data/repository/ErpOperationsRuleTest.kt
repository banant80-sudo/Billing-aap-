package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.model.InvoiceLineItem
import com.example.data.sync.FinbossBackupPayload
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErpOperationsRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveParty_andRecordPayment_updatesBalanceInRealTime() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = ErpOperationsRepository(firestore, auth)

    val partyId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .saveParty(
            name = "Shree Ganesh Traders",
            partyType = ErpParty.TYPE_CUSTOMER,
            phone = "9876543210",
            gstin = "27AABCS1234B1Z5",
            openingDue = 5000.0,
            creditLimit = 50000.0,
            notes = "Regular buyer",
          )
          .getOrThrow()
      }

    val savedParty =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeParties().first { list -> list.any { it.id == partyId } }.first { it.id == partyId }
      }
    assertEquals(5000.0, savedParty.outstandingBalance, 0.01)

    val payResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.recordPartyPayment(savedParty, 2000.0) }
    assertTrue(payResult.isSuccess)

    val updatedParty =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo
          .observeParties()
          .first { list -> list.any { it.id == partyId && it.totalPaid >= 2000.0 } }
          .first { it.id == partyId }
      }
    assertEquals(3000.0, updatedParty.outstandingBalance, 0.01)
  }

  @Test
  fun createInvoice_atomicallyDeductsInventory_andUpdatesPartyKhata() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = ErpOperationsRepository(firestore, auth)

    val partyId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .saveParty(
            name = "Metro Mart",
            partyType = ErpParty.TYPE_CUSTOMER,
            phone = "",
            gstin = "27AABCM1234D1Z9",
            openingDue = 0.0,
            creditLimit = 100000.0,
            notes = "",
          )
          .getOrThrow()
      }

    val itemId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .saveInventoryItem(
            name = "Laser Barcode Scanner",
            sku = "SKU-SCAN-01",
            category = "Hardware",
            unit = "PCS",
            salePrice = 2000.0,
            purchasePrice = 1400.0,
            gstRatePercent = 18.0,
            stockQty = 10.0,
            lowStockThreshold = 3.0,
          )
          .getOrThrow()
      }

    val party = withTimeout(FLOW_TIMEOUT_MS) { repo.observeParties().first { it.any { p -> p.id == partyId } } }.first { it.id == partyId }
    val items = withTimeout(FLOW_TIMEOUT_MS) { repo.observeInventory().first { it.any { i -> i.id == itemId } } }
    val item = items.first { it.id == itemId }

    val invoiceId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createInvoice(
            invoiceNumber = "FB-2026-101",
            party = party,
            walkInPartyName = "",
            walkInPartyGstin = "",
            invoiceType = GstInvoice.TYPE_TAX_INVOICE,
            taxMode = GstInvoice.TAX_MODE_CGST_SGST,
            paymentMethod = GstInvoice.PAYMENT_UPI,
            discountAmount = 0.0,
            paidAmount = 1000.0,
            notes = "Batch test",
            lineItems =
              listOf(
                InvoiceLineItem(
                  itemId = item.id,
                  name = item.name,
                  sku = item.sku,
                  unit = item.unit,
                  qty = 2.0,
                  unitPrice = item.salePrice,
                  gstRatePercent = item.gstRatePercent,
                )
              ),
            inventorySnapshot = items,
          )
          .getOrThrow()
      }

    // Verify stock deducted from 10 -> 8
    val updatedItem =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInventory().first { list -> list.any { it.id == itemId && it.stockQty == 8.0 } }.first { it.id == itemId }
      }
    assertEquals(8.0, updatedItem.stockQty, 0.01)

    // Verify invoice partial status (2*2000 + 18% GST = 4720, paid 1000)
    val createdInvoice =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInvoices().first { list -> list.any { it.id == invoiceId } }.first { it.id == invoiceId }
      }
    assertEquals(4720.0, createdInvoice.grandTotal, 0.01)
    assertEquals(GstInvoice.STATUS_PARTIAL, createdInvoice.status)

    // Verify Party Khata reflects billed 4720, paid 1000 -> due 3720
    val updatedParty =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeParties().first { list -> list.any { it.id == partyId && it.totalBilled >= 4720.0 } }.first { it.id == partyId }
      }
    assertEquals(3720.0, updatedParty.outstandingBalance, 0.01)

    // Settle remaining invoice balance
    val settleResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.recordInvoicePayment(createdInvoice, 3720.0, GstInvoice.PAYMENT_CASH, updatedParty)
      }
    assertTrue(settleResult.isSuccess)
  }

  @Test
  fun crossUserRead_invoices_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    signInTestUser(BOB_EMAIL)

    val err =
      try {
        withTimeout(DEFAULT_TIMEOUT_MS) {
          firestore
            .collection("users")
            .document(aliceUid)
            .collection("invoices")
            .get()
            .let { com.google.android.gms.tasks.Tasks.await(it) }
        }
        null
      } catch (e: Exception) {
        e
      }
    assertNotNull(err)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, err?.unwrapFirestoreException()?.code)
  }

  @Test
  fun saveExpense_andMarkSettled_updatesRealTimeAndIsolatesUser() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = ErpOperationsRepository(firestore, auth)

    val expenseId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .saveExpense(
            title = "Shop Rent May",
            category = ErpExpense.CAT_RENT,
            paymentMethod = ErpExpense.METHOD_BANK,
            status = ErpExpense.STATUS_PENDING,
            party = null,
            partyNameFallback = "Apex Properties",
            referenceNo = "RENT-05",
            amount = 18000.0,
            gstInputAmount = 0.0,
            notes = "Monthly rent",
          )
          .getOrThrow()
      }

    val created =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeExpenses().first { list -> list.any { it.id == expenseId } }.first { it.id == expenseId }
      }
    assertEquals(ErpExpense.STATUS_PENDING, created.status)
    assertEquals(18000.0, created.amount, 0.01)

    val settleRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.markExpenseSettled(expenseId, ErpExpense.METHOD_UPI)
      }
    assertTrue(settleRes.isSuccess)

    val settled =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo
          .observeExpenses()
          .first { list -> list.any { it.id == expenseId && it.status == ErpExpense.STATUS_SETTLED } }
          .first { it.id == expenseId }
      }
    assertEquals(ErpExpense.METHOD_UPI, settled.paymentMethod)

    // Cross-user read denied
    signInTestUser(BOB_EMAIL)
    val bobErr =
      try {
        withTimeout(DEFAULT_TIMEOUT_MS) {
          firestore
            .collection("users")
            .document(aliceUid)
            .collection("expenses")
            .get()
            .let { com.google.android.gms.tasks.Tasks.await(it) }
        }
        null
      } catch (e: Exception) {
        e
      }
    assertNotNull(bobErr)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, bobErr?.unwrapFirestoreException()?.code)
  }

  @Test
  fun restoreFromBackup_writesValidDocumentsThroughRules() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = ErpOperationsRepository(firestore, auth)
    val runTag = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
    val partyDocId = "restored_party_$runTag"
    val itemDocId = "restored_item_$runTag"
    val expDocId = "restored_exp_$runTag"
    val entryDocId = "restored_entry_$runTag"

    val backupPayload =
      FinbossBackupPayload(
        parties =
          listOf(
            ErpParty(
              id = partyDocId,
              name = "Bharat Distributors",
              partyType = ErpParty.TYPE_SUPPLIER,
              phone = "9988776655",
              openingDue = 1500.0,
            )
          ),
        inventory =
          listOf(
            InventoryItem(
              id = itemDocId,
              name = "UPI Soundbox",
              sku = "SKU-SB-01",
              salePrice = 999.0,
              purchasePrice = 650.0,
              gstRatePercent = 18.0,
              stockQty = 5.0,
            )
          ),
        expenses =
          listOf(
            ErpExpense(
              id = expDocId,
              title = "Fiber Internet",
              category = ErpExpense.CAT_UTILITIES,
              paymentMethod = ErpExpense.METHOD_UPI,
              status = ErpExpense.STATUS_SETTLED,
              amount = 1180.0,
              gstInputAmount = 180.0,
            )
          ),
        dashboardEntries =
          listOf(
            DashboardEntry(
              id = entryDocId,
              title = "Misc Cash Receipt",
              partyName = "Walk-in",
              category = DashboardEntry.CATEGORY_SALE,
              amount = 450.0,
              status = DashboardEntry.STATUS_PAID,
            )
          ),
      )

    val restoredCount =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .restoreFromBackup(
            payload = backupPayload,
            existingPartyIds = emptySet(),
            existingInventoryIds = emptySet(),
            existingInvoiceIds = emptySet(),
            existingExpenseIds = emptySet(),
            existingEntryIds = emptySet(),
          )
          .getOrThrow()
      }

    assertEquals(4, restoredCount)

    val parties = withTimeout(FLOW_TIMEOUT_MS) { repo.observeParties().first { it.any { p -> p.id == partyDocId } } }
    assertTrue(parties.any { it.name == "Bharat Distributors" })

    val expenses = withTimeout(FLOW_TIMEOUT_MS) { repo.observeExpenses().first { it.any { e -> e.id == expDocId } } }
    assertTrue(expenses.any { it.title == "Fiber Internet" })

    // Running restore again with existing IDs should skip duplicates cleanly
    val secondRestoreCount =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .restoreFromBackup(
            payload = backupPayload,
            existingPartyIds = setOf(partyDocId),
            existingInventoryIds = setOf(itemDocId),
            existingInvoiceIds = emptySet(),
            existingExpenseIds = setOf(expDocId),
            existingEntryIds = setOf(entryDocId),
          )
          .getOrThrow()
      }
    assertEquals(0, secondRestoreCount)
  }

  @Test
  fun updateInvoice_andDeleteInvoice_reconcileStockAndPartyKhata() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = ErpOperationsRepository(firestore, auth)

    val partyId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .saveParty(
            name = "Neha Retailers",
            partyType = ErpParty.TYPE_CUSTOMER,
            phone = "9123456789",
            gstin = "27AABCN5555B1Z1",
            openingDue = 0.0,
            creditLimit = 100000.0,
            notes = "",
          )
          .getOrThrow()
      }

    val itemId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .saveInventoryItem(
            name = "USB Receipt Printer",
            sku = "SKU-PRN-01",
            category = "Hardware",
            unit = "PCS",
            salePrice = 3000.0,
            purchasePrice = 2100.0,
            gstRatePercent = 18.0,
            stockQty = 10.0,
            lowStockThreshold = 2.0,
          )
          .getOrThrow()
      }

    val party =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeParties().first { list -> list.any { it.id == partyId } }
      }.first { it.id == partyId }
    val inventoryList =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInventory().first { list -> list.any { it.id == itemId } }
      }
    val item = inventoryList.first { it.id == itemId }

    val invoiceId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createInvoice(
            invoiceNumber = "FB-2026-201",
            party = party,
            walkInPartyName = "",
            walkInPartyGstin = "",
            invoiceType = GstInvoice.TYPE_TAX_INVOICE,
            taxMode = GstInvoice.TAX_MODE_CGST_SGST,
            paymentMethod = GstInvoice.PAYMENT_UPI,
            discountAmount = 0.0,
            paidAmount = 1000.0,
            notes = "Initial bill",
            lineItems =
              listOf(
                InvoiceLineItem(
                  itemId = item.id,
                  name = item.name,
                  sku = item.sku,
                  unit = item.unit,
                  qty = 2.0,
                  unitPrice = 3000.0,
                  gstRatePercent = 18.0,
                )
              ),
            inventorySnapshot = inventoryList,
          )
          .getOrThrow()
      }

    // Stock should now be 8.0, Invoice grandTotal = 7080.0
    val createdInvoice =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInvoices().first { list -> list.any { it.id == invoiceId } }.first { it.id == invoiceId }
      }
    val stockAfterCreate =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInventory().first { list -> list.any { it.id == itemId && it.stockQty == 8.0 } }
      }
    val partyAfterCreate =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeParties().first { list -> list.any { it.id == partyId && it.totalBilled >= 7080.0 } }
      }

    // Edit invoice: qty 2 -> 3, paid 1000 -> 5000
    val updatedParty = partyAfterCreate.first { it.id == partyId }
    val updateRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.updateInvoice(
          existingInvoice = createdInvoice,
          invoiceNumber = "FB-2026-201-R1",
          party = updatedParty,
          walkInPartyName = "",
          walkInPartyGstin = "",
          invoiceType = GstInvoice.TYPE_TAX_INVOICE,
          taxMode = GstInvoice.TAX_MODE_CGST_SGST,
          paymentMethod = GstInvoice.PAYMENT_UPI,
          discountAmount = 0.0,
          paidAmount = 5000.0,
          notes = "Edited qty to 3",
          lineItems =
            listOf(
              InvoiceLineItem(
                itemId = item.id,
                name = item.name,
                sku = item.sku,
                unit = item.unit,
                qty = 3.0,
                unitPrice = 3000.0,
                gstRatePercent = 18.0,
              )
            ),
          inventorySnapshot = stockAfterCreate,
          partiesSnapshot = partyAfterCreate,
        )
      }
    assertTrue("updateInvoice failed: ${updateRes.exceptionOrNull()}", updateRes.isSuccess)

    // Stock should reconcile from 8.0 -> 7.0
    val stockAfterEdit =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInventory().first { list -> list.any { it.id == itemId && it.stockQty == 7.0 } }
      }
    // Party totalBilled should be 10620.0 and totalPaid 5000.0
    val partyAfterEdit =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeParties().first { list -> list.any { it.id == partyId && it.totalBilled >= 10620.0 } }
      }
    val editedInvoice =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInvoices().first { list -> list.any { it.id == invoiceId && it.invoiceNumber == "FB-2026-201-R1" } }
          .first { it.id == invoiceId }
      }
    assertEquals(10620.0, editedInvoice.grandTotal, 0.01)

    // Delete invoice: stock should restore 7.0 -> 10.0 and Party Khata billed/paid -> 0.0
    val deleteRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.deleteInvoice(
          invoice = editedInvoice,
          inventorySnapshot = stockAfterEdit,
          partiesSnapshot = partyAfterEdit,
        )
      }
    assertTrue(deleteRes.isSuccess)

    val restoredStock =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInventory().first { list -> list.any { it.id == itemId && it.stockQty == 10.0 } }
          .first { it.id == itemId }
      }
    assertEquals(10.0, restoredStock.stockQty, 0.01)

    val restoredParty =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeParties().first { list -> list.any { it.id == partyId && it.totalBilled == 0.0 } }
          .first { it.id == partyId }
      }
    assertEquals(0.0, restoredParty.totalBilled, 0.01)
    assertEquals(0.0, restoredParty.totalPaid, 0.01)
  }

  @Test
  fun recordPurchase_createsAndRestocksInventoryLogsExpenseItcAndUpdatesSupplier() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = ErpOperationsRepository(firestore, auth)

    val supplierId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .saveParty(
            name = "Apex Electronics Distributor",
            partyType = ErpParty.TYPE_SUPPLIER,
            phone = "9820012345",
            gstin = "27AABCA9999K1Z2",
            openingDue = 0.0,
            creditLimit = 200000.0,
            notes = "Main wholesaler",
            address = "14 Lamington Road",
            state = "Maharashtra",
            pincode = "400007",
          )
          .getOrThrow()
      }
    val supplier =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeParties().first { list -> list.any { it.id == supplierId } }.first { it.id == supplierId }
      }
    assertEquals("Maharashtra", supplier.state)
    assertEquals("400007", supplier.pincode)

    // 1. Purchase creating a brand-new SKU with HSN 8471
    val itemId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .recordPurchase(
            existingItem = null,
            newItemName = "Thermal Barcode Printer",
            newItemSku = "SKU-TBP-8471",
            hsnCode = "8471",
            category = "Hardware",
            unit = "PCS",
            purchasePrice = 4000.0,
            salePrice = 5200.0,
            gstRatePercent = 18.0,
            purchaseQty = 5.0,
            lowStockThreshold = 2.0,
            supplier = supplier,
            supplierNameFallback = "",
            billNumber = "PUR-2026-01",
            paymentMethod = ErpExpense.METHOD_BANK,
            isPaidNow = true,
            recordInExpenses = true,
          )
          .getOrThrow()
      }

    val createdItem =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInventory().first { list -> list.any { it.id == itemId } }.first { it.id == itemId }
      }
    assertEquals("8471", createdItem.hsnCode)
    assertEquals(5.0, createdItem.stockQty, 0.01)

    val expensesAfterFirst =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeExpenses().first { list -> list.any { it.referenceNo == "PUR-2026-01" } }
      }
    val firstExp = expensesAfterFirst.first { it.referenceNo == "PUR-2026-01" }
    assertEquals(ErpExpense.CAT_PURCHASES, firstExp.category)
    assertEquals(23600.0, firstExp.amount, 0.01)
    assertEquals(3600.0, firstExp.gstInputAmount, 0.01)

    // 2. Restock same SKU on supplier credit (unpaid)
    val supplierAfterFirst =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeParties().first { list -> list.any { it.id == supplierId && it.totalBilled >= 23600.0 } }
          .first { it.id == supplierId }
      }
    val restockRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.recordPurchase(
          existingItem = createdItem,
          newItemName = createdItem.name,
          newItemSku = createdItem.sku,
          hsnCode = "847160",
          category = createdItem.category,
          unit = createdItem.unit,
          purchasePrice = 4100.0,
          salePrice = createdItem.salePrice,
          gstRatePercent = 18.0,
          purchaseQty = 3.0,
          lowStockThreshold = createdItem.lowStockThreshold,
          supplier = supplierAfterFirst,
          supplierNameFallback = "",
          billNumber = "PUR-2026-02",
          paymentMethod = ErpExpense.METHOD_UPI,
          isPaidNow = false,
          recordInExpenses = true,
        )
      }
    assertTrue("Restock failed: ${restockRes.exceptionOrNull()}", restockRes.isSuccess)

    val restockedItem =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInventory().first { list -> list.any { it.id == itemId && it.stockQty >= 8.0 } }
          .first { it.id == itemId }
      }
    assertEquals(8.0, restockedItem.stockQty, 0.01)
    assertEquals("847160", restockedItem.hsnCode)
    assertEquals(4100.0, restockedItem.purchasePrice, 0.01)

    val supplierAfterSecond =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeParties().first { list -> list.any { it.id == supplierId && it.totalBilled >= 38114.0 } }
          .first { it.id == supplierId }
      }
    assertTrue(supplierAfterSecond.outstandingBalance >= 14514.0)
  }

  @Test
  fun saveInvoiceEInvoiceAndEWayBill_persistsIrnQrAndTransportDetails() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = ErpOperationsRepository(firestore, auth)

    val partyId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .saveParty(
            name = "Kalinga Industrial Supplies",
            partyType = ErpParty.TYPE_CUSTOMER,
            phone = "9437012345",
            gstin = "21AABCK1234F1Z5",
            openingDue = 0.0,
            creditLimit = 500000.0,
            notes = "B2B GST customer",
            address = "Plot 42, Rasulgarh Industrial Estate",
            state = "Odisha",
            pincode = "751010",
          )
          .getOrThrow()
      }

    val party =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeParties().first { list -> list.any { it.id == partyId } }.first { it.id == partyId }
      }

    val invoiceId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createInvoice(
            invoiceNumber = "FB-EINV-2026-01",
            party = party,
            walkInPartyName = "",
            walkInPartyGstin = "",
            invoiceType = GstInvoice.TYPE_TAX_INVOICE,
            taxMode = GstInvoice.TAX_MODE_IGST,
            paymentMethod = GstInvoice.PAYMENT_UPI,
            discountAmount = 0.0,
            paidAmount = 59000.0,
            notes = "Inter-state B2B Machinery Dispatch",
            lineItems =
              listOf(
                InvoiceLineItem(
                  itemId = "",
                  name = "Industrial CNC Spindle Unit",
                  sku = "CNC-8466",
                  hsnCode = "8466",
                  unit = "PCS",
                  qty = 1.0,
                  unitPrice = 50000.0,
                  gstRatePercent = 18.0,
                )
              ),
            inventorySnapshot = emptyList(),
          )
          .getOrThrow()
      }

    val createdInvoice =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInvoices().first { list -> list.any { it.id == invoiceId } }.first { it.id == invoiceId }
      }

    val sellerProfile =
      com.example.data.model.BusinessProfile(
        id = com.example.data.model.BusinessProfile.SLOT_MAIN_1,
        businessName = "Alice Engineering Works",
        gstin = "27AABCU9603R1ZM",
        state = "Maharashtra",
        address = "MIDC Andheri East, Mumbai",
        pincode = "400093",
      )

    val einvResult =
      com.example.data.reference.GstEInvoiceAndEWayBillEngine.generateEInvoice(
        invoice = createdInvoice,
        sellerProfile = sellerProfile,
      )
    assertEquals(64, einvResult.irn.length)

    val saveEinvRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveInvoiceEInvoiceDetails(
          invoice = createdInvoice,
          result = einvResult,
        )
      }
    assertTrue("saveInvoiceEInvoiceDetails failed: ${saveEinvRes.exceptionOrNull()}", saveEinvRes.isSuccess)

    val invoiceWithIrn =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInvoices().first { list ->
          list.any { it.id == invoiceId && it.irn == einvResult.irn }
        }.first { it.id == invoiceId }
      }
    assertTrue(invoiceWithIrn.hasEInvoice)
    assertEquals(einvResult.ackNo, invoiceWithIrn.ackNo)

    val transportInput =
      com.example.data.reference.EWayBillTransportInput(
        fromPlace = "Mumbai",
        fromPincode = "400093",
        toPlace = "Bhubaneswar",
        toPincode = "751010",
        distanceKm =
          com.example.data.reference.GstEInvoiceAndEWayBillEngine.estimateDistanceKm(
            fromCity = "Mumbai",
            fromPin = "400093",
            toCity = "Bhubaneswar",
            toPin = "751010",
          ),
        transportMode = GstInvoice.TRANSPORT_ROAD,
        vehicleNumber = "OD-02-AB-1234",
        vehicleType = GstInvoice.VEHICLE_REGULAR,
        transporterName = "VRL Logistics Ltd",
        transporterId = "27AABCV1234F1Z2",
        transporterDocNo = "LR-90812",
        transporterDocDate = "15-11-2026",
      )
    val ewbResult =
      com.example.data.reference.GstEInvoiceAndEWayBillEngine.generateEWayBill(
        invoice = invoiceWithIrn,
        sellerProfile = sellerProfile,
        input = transportInput,
      )
    assertEquals(12, ewbResult.ewbNumber.length)

    val saveEwbRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveInvoiceEWayBillDetails(
          invoice = invoiceWithIrn,
          result = ewbResult,
        )
      }
    assertTrue("saveInvoiceEWayBillDetails failed: ${saveEwbRes.exceptionOrNull()}", saveEwbRes.isSuccess)

    val invoiceWithEwb =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInvoices().first { list ->
          list.any { it.id == invoiceId && it.ewbNumber == ewbResult.ewbNumber }
        }.first { it.id == invoiceId }
      }
    assertTrue(invoiceWithEwb.hasEWayBill)
    assertEquals("OD-02-AB-1234", invoiceWithEwb.vehicleNumber)
    assertTrue(invoiceWithEwb.distanceKm > 0)

    val cancelEinvRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.cancelInvoiceEInvoice(invoiceWithEwb)
      }
    assertTrue(cancelEinvRes.isSuccess)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_ops@test.com"
    const val BOB_EMAIL = "bob_ops@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
