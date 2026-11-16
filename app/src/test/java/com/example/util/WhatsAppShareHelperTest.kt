package com.example.util

import com.example.data.model.BusinessProfile
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.model.InvoiceLineItem
import com.example.ui.viewmodel.DashboardKpiSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppShareHelperTest {

  @Test
  fun normalizeWhatsAppPhone_formatsIndianAndInternationalNumbers() {
    assertEquals("919439772634", WhatsAppShareHelper.normalizeWhatsAppPhone("9439772634"))
    assertEquals("919439772634", WhatsAppShareHelper.normalizeWhatsAppPhone("+91 94397 72634"))
    assertEquals("14155551234", WhatsAppShareHelper.normalizeWhatsAppPhone("+1-415-555-1234"))
    assertNull(WhatsAppShareHelper.normalizeWhatsAppPhone("12345"))
    assertNull(WhatsAppShareHelper.normalizeWhatsAppPhone(null))
  }

  @Test
  fun formatInvoiceSummary_includesIrnEwbForGstAndOmitsForPlain() {
    val profile = BusinessProfile(businessName = "Raghav Enterprises", gstin = "27AABCU9603R1ZM")
    val invoice =
      GstInvoice(
        invoiceNumber = "INV-101",
        partyName = "Bharat Traders",
        partyGstin = "29AABCU9603R1ZN",
        grandTotal = 11800.0,
        paidAmount = 5000.0,
        irn = "irn_hash_12345",
        einvoiceStatus = "generated",
        ewbNumber = "123456789012",
        distanceKm = 240,
        lineItems = listOf(InvoiceLineItem(name = "Laser Printer", unit = "PCS", qty = 1.0, lineTotal = 11800.0)),
      )

    val gstSummary = WhatsAppShareHelper.formatInvoiceSummary(invoice, profile, "₹", isPlainNonGst = false)
    assertTrue(gstSummary.contains("Tax Invoice"))
    assertTrue(gstSummary.contains("INV-101"))
    assertTrue(gstSummary.contains("Buyer GSTIN: 29AABCU9603R1ZN"))
    assertTrue(gstSummary.contains("E-Invoice IRN: irn_hash_12345"))
    assertTrue(gstSummary.contains("E-Way Bill: #123456789012 (240 KM)"))

    val plainSummary = WhatsAppShareHelper.formatInvoiceSummary(invoice, profile, "₹", isPlainNonGst = true)
    assertTrue(plainSummary.contains("Bill: *INV-101*"))
    assertFalse(plainSummary.contains("E-Invoice IRN"))
    assertFalse(plainSummary.contains("Buyer GSTIN"))
  }

  @Test
  fun formatPaymentReceiptAndPartyAndInventoryAndExpenseAndOverview_coverAllErpModules() {
    val paymentMsg =
      WhatsAppShareHelper.formatPaymentReceiptMessage(
        payerName = "Apex Hardware",
        referenceLabel = "Invoice INV-101",
        amountPaid = 2500.0,
        remainingDue = 500.0,
        paymentMethod = "upi",
        businessName = "Raghav Enterprises",
        currency = "₹",
      )
    assertTrue(paymentMsg.contains("Payment Receipt"))
    assertTrue(paymentMsg.contains("Apex Hardware"))
    assertTrue(paymentMsg.contains("(UPI)"))

    val partyMsg =
      WhatsAppShareHelper.formatPartyStatementSummary(
        party = ErpParty(name = "Kalinga Steels", partyType = ErpParty.TYPE_CUSTOMER, totalBilled = 9000.0, totalPaid = 4000.0),
        businessName = "Raghav Enterprises",
        currency = "₹",
      )
    assertTrue(partyMsg.contains("Customer Khata"))
    assertTrue(partyMsg.contains("Kalinga Steels"))

    val stockMsg =
      WhatsAppShareHelper.formatInventoryItemSummary(
        item = InventoryItem(name = "PVC Pipe", sku = "SKU-09", hsnCode = "3917", gstRatePercent = 18.0, salePrice = 250.0, stockQty = 40.0, unit = "PCS"),
        businessName = "Raghav Enterprises",
        currency = "₹",
        isPlainNonGst = false,
      )
    assertTrue(stockMsg.contains("PVC Pipe"))
    assertTrue(stockMsg.contains("HSN: 3917"))

    val expMsg =
      WhatsAppShareHelper.formatExpenseSummary(
        expense = ErpExpense(title = "Warehouse Rent", category = ErpExpense.CAT_RENT, paymentMethod = ErpExpense.METHOD_BANK, amount = 15000.0, gstInputAmount = 2700.0, status = ErpExpense.STATUS_SETTLED),
        businessName = "Raghav Enterprises",
        currency = "₹",
        isPlainNonGst = false,
      )
    assertTrue(expMsg.contains("Warehouse Rent"))
    assertTrue(expMsg.contains("GST Input Credit"))

    val ovMsg =
      WhatsAppShareHelper.formatDashboardOverviewSummary(
        businessName = "Raghav Enterprises",
        isPlainNonGst = false,
        kpi = DashboardKpiSummary(totalSales = 50000.0, pendingReceivables = 12000.0, totalExpenses = 8000.0, netCashflow = 42000.0, invoiceCount = 12, partyCount = 6, stockValuation = 95000.0, netGstPayable = 3400.0),
        currency = "₹",
      )
    assertTrue(ovMsg.contains("Business Summary"))
    assertTrue(ovMsg.contains("Net GST Due"))
  }
}
