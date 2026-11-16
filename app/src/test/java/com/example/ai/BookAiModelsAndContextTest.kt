package com.example.ai

import com.example.data.model.BusinessProfile
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.model.InvoiceLineItem
import com.google.firebase.Timestamp
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookAiModelsAndContextTest {

  private val sampleParties =
    listOf(
      ErpParty(id = "p1", name = "Sharma Traders", partyType = ErpParty.TYPE_CUSTOMER, gstin = "27AABCS1429B1ZS"),
    )

  private val sampleInventory =
    listOf(
      InventoryItem(
        id = "i1",
        name = "Copper Cable",
        sku = "CC-100",
        hsnCode = "8544",
        unit = "PCS",
        purchasePrice = 100.0,
        salePrice = 125.0,
        gstRatePercent = 18.0,
        stockQty = 3.0,
        lowStockThreshold = 5.0,
      )
    )

  @Test
  fun parseVoiceDraftJson_matchesInventoryAndPartyAndComputesTotals() {
    val rawJson =
      """
      {
        "entryType": "INVOICE",
        "transcript": "Sharma Traders ko 2 Copper Cable 1250 rate UPI bill banao",
        "partyName": "Sharma Traders",
        "partyGstin": "",
        "paymentMethod": "upi",
        "paymentStatus": "paid",
        "paidAmount": -1,
        "discountAmount": 0,
        "notes": "",
        "lineItems": [
          {
            "itemName": "copper cable",
            "qty": 2,
            "unit": "",
            "unitPrice": 1250,
            "gstRatePercent": 18,
            "hsnCode": ""
          }
        ],
        "expenseTitle": "",
        "expenseCategory": "other",
        "expenseAmount": 0,
        "gstInputAmount": 0
      }
      """.trimIndent()

    val draft = BookAiJsonParser.parseVoiceDraftJson(rawJson, sampleInventory, sampleParties, isPlainNonGst = false)
    assertTrue(draft.isInvoice)
    assertEquals("p1", draft.matchedPartyId)
    assertEquals("Sharma Traders", draft.partyName)
    assertEquals("27AABCS1429B1ZS", draft.partyGstin)
    assertEquals(1, draft.lineItems.size)
    assertEquals("i1", draft.lineItems.first().matchedInventoryId)
    assertEquals("Copper Cable", draft.lineItems.first().name)
    assertEquals("8544", draft.lineItems.first().hsnCode)
    // 2 * 1250 = 2500 + 18% (450) = 2950
    assertEquals(2950.0, draft.computedInvoiceGrandTotal(isPlainNonGst = false), 0.01)
    assertEquals(2950.0, draft.paidAmount, 0.01)
  }

  @Test
  fun parseVoiceDraftJson_parsesExpenseInPlainModeWithZeroGst() {
    val rawJson =
      """
      {
        "entryType": "EXPENSE",
        "transcript": "Office Diesel 1500 cash paid",
        "partyName": "HP Fuel Pump",
        "paymentMethod": "cash",
        "paymentStatus": "paid",
        "lineItems": [],
        "expenseTitle": "Office Diesel",
        "expenseCategory": "transport",
        "expenseAmount": 1500,
        "gstInputAmount": 200
      }
      """.trimIndent()

    val draft = BookAiJsonParser.parseVoiceDraftJson(rawJson, sampleInventory, sampleParties, isPlainNonGst = true)
    assertFalse(draft.isInvoice)
    assertEquals("Office Diesel", draft.expenseTitle)
    assertEquals(ErpExpense.CAT_TRANSPORT, draft.expenseCategory)
    assertEquals(1500.0, draft.expenseAmount, 0.01)
    assertEquals(0.0, draft.gstInputAmount, 0.01)
  }

  @Test
  fun parseOcrBillJson_extractsVendorGstinAndLineItems() {
    val rawJson =
      """
      {
        "documentKind": "PURCHASE_EXPENSE_BILL",
        "vendorOrPartyName": "Apex Electronics",
        "vendorGstin": "27AAACA1234A1Z5",
        "invoiceOrBillNumber": "AE-992",
        "invoiceDate": "15/11/2026",
        "paymentMethod": "upi",
        "expenseCategory": "purchases",
        "subtotal": 2000,
        "gstAmount": 360,
        "grandTotal": 2360,
        "notes": "Purchase stock",
        "lineItems": [
          {
            "itemName": "Copper Cable",
            "hsnCode": "8544",
            "qty": 20,
            "unit": "PCS",
            "unitPrice": 100,
            "gstRatePercent": 18
          }
        ]
      }
      """.trimIndent()

    val bill = BookAiJsonParser.parseOcrBillJson(rawJson, sampleInventory, sampleParties, isPlainNonGst = false)
    assertEquals("PURCHASE_EXPENSE_BILL", bill.documentKind)
    assertEquals("Apex Electronics", bill.partyName)
    assertEquals("27AAACA1234A1Z5", bill.partyGstin)
    assertEquals("AE-992", bill.billOrInvoiceNumber)
    assertEquals(2360.0, bill.grandTotal, 0.01)
    assertEquals(1, bill.lineItems.size)
    assertEquals("i1", bill.lineItems.first().matchedInventoryId)
  }

  @Test
  fun buildAnalyticsContextPrompt_includesProfitPercentAndLowStockAlerts() {
    val now = Timestamp(Date())
    val invoices =
      listOf(
        GstInvoice(
          id = "inv1",
          invoiceNumber = "INV-1",
          partyId = "p1",
          partyName = "Sharma Traders",
          subtotal = 250.0,
          gstTotal = 45.0,
          grandTotal = 295.0,
          paidAmount = 295.0,
          createdAt = now,
          lineItems =
            listOf(
              InvoiceLineItem(
                itemId = "i1",
                name = "Copper Cable",
                qty = 2.0,
                unitPrice = 125.0,
                taxableAmount = 250.0,
                gstAmount = 45.0,
                lineTotal = 295.0,
              )
            ),
        )
      )

    val contextText =
      BookAiContextBuilder.buildAnalyticsContextPrompt(
        profile = BusinessProfile(businessName = "Book Enterprise"),
        currency = "₹",
        isPlainNonGst = false,
        parties = sampleParties,
        inventory = sampleInventory,
        invoices = invoices,
        expenses = emptyList(),
        entries = emptyList(),
      )

    assertTrue(contextText.contains("Book Enterprise"))
    assertTrue(contextText.contains("Sharma Traders"))
    assertTrue(contextText.contains("+25.0%"))
    assertTrue(contextText.contains("LOW / OUT OF STOCK ALERTS"))
    assertTrue(contextText.contains("Copper Cable"))
  }
}
