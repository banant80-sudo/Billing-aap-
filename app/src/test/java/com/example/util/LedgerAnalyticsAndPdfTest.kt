package com.example.util

import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.model.InvoiceLineItem
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerAnalyticsAndPdfTest {

  private fun calMs(year: Int, month: Int, day: Int, hour: Int = 12): Long =
    Calendar.getInstance().apply {
      set(year, month, day, hour, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

  @Test
  fun resolveDateRange_computesIndianFinancialYearPeriodsCorrectly() {
    val nov15_2026 = calMs(2026, Calendar.NOVEMBER, 15)

    val monthRange = LedgerAnalyticsEngine.resolveDateRange(LedgerPeriod.THIS_MONTH, nov15_2026)
    assertTrue(monthRange.startMs <= calMs(2026, Calendar.NOVEMBER, 1))
    assertTrue(monthRange.endMs >= calMs(2026, Calendar.NOVEMBER, 30))

    val quarterRange = LedgerAnalyticsEngine.resolveDateRange(LedgerPeriod.QUARTER, nov15_2026)
    assertTrue(quarterRange.rangeLabel.startsWith("Q3"))
    assertTrue(nov15_2026 in quarterRange.startMs..quarterRange.endMs)
    assertFalse(calMs(2026, Calendar.SEPTEMBER, 30) in quarterRange.startMs..quarterRange.endMs)

    val halfYearRange = LedgerAnalyticsEngine.resolveDateRange(LedgerPeriod.HALF_YEAR, nov15_2026)
    assertTrue(halfYearRange.rangeLabel.startsWith("H2"))
    assertTrue(calMs(2026, Calendar.OCTOBER, 1) in halfYearRange.startMs..halfYearRange.endMs)
    assertTrue(calMs(2027, Calendar.MARCH, 31) in halfYearRange.startMs..halfYearRange.endMs)

    val presentYearRange = LedgerAnalyticsEngine.resolveDateRange(LedgerPeriod.PRESENT_YEAR, nov15_2026)
    assertTrue(presentYearRange.rangeLabel.contains("FY 2026-27"))
    assertTrue(calMs(2026, Calendar.APRIL, 1) in presentYearRange.startMs..presentYearRange.endMs)
    assertFalse(calMs(2026, Calendar.MARCH, 31) in presentYearRange.startMs..presentYearRange.endMs)

    val prevYearRange = LedgerAnalyticsEngine.resolveDateRange(LedgerPeriod.PREVIOUS_YEAR, nov15_2026)
    assertTrue(prevYearRange.rangeLabel.contains("FY 2025-26"))
    assertTrue(calMs(2026, Calendar.FEBRUARY, 10) in prevYearRange.startMs..prevYearRange.endMs)
    assertFalse(nov15_2026 in prevYearRange.startMs..prevYearRange.endMs)
  }

  @Test
  fun buildReport_computesItemAndPartyProfitAndLossPercentCorrectly() {
    val nowMs = calMs(2026, Calendar.NOVEMBER, 15)
    val prevFyMs = calMs(2026, Calendar.FEBRUARY, 10)

    val partyProfit = ErpParty(id = "p1", name = "Sharma Traders", partyType = ErpParty.TYPE_CUSTOMER)
    val partyLoss = ErpParty(id = "p2", name = "Clearance Mart", partyType = ErpParty.TYPE_CUSTOMER)

    val itemA =
      InventoryItem(
        id = "i1",
        name = "Copper Cable",
        sku = "CC-100",
        unit = "PCS",
        purchasePrice = 100.0,
        salePrice = 125.0,
        stockQty = 50.0,
      )
    val itemB =
      InventoryItem(
        id = "i2",
        name = "Old Router",
        sku = "OR-200",
        unit = "PCS",
        purchasePrice = 100.0,
        salePrice = 100.0,
        stockQty = 10.0,
      )

    // Profitable invoice: Sold 2x Copper Cable @ 125 = 250 vs cost 200 -> +50 (+25.0%)
    val inv1 =
      GstInvoice(
        id = "inv1",
        invoiceNumber = "INV-101",
        partyId = "p1",
        partyName = "Sharma Traders",
        subtotal = 250.0,
        gstTotal = 45.0,
        grandTotal = 295.0,
        paidAmount = 295.0,
        createdAt = Timestamp(Date(nowMs)),
        lineItems =
          listOf(
            InvoiceLineItem(
              itemId = "i1",
              name = "Copper Cable",
              sku = "CC-100",
              qty = 2.0,
              unitPrice = 125.0,
              taxableAmount = 250.0,
              gstAmount = 45.0,
              lineTotal = 295.0,
            )
          ),
      )

    // Loss invoice via discount: Sold 1x Old Router @ 100 with ₹20 discount -> net 80 vs cost 100 -> -20 (-20.0%)
    val inv2 =
      GstInvoice(
        id = "inv2",
        invoiceNumber = "INV-102",
        partyId = "p2",
        partyName = "Clearance Mart",
        subtotal = 100.0,
        discountAmount = 20.0,
        gstTotal = 0.0,
        grandTotal = 80.0,
        paidAmount = 80.0,
        createdAt = Timestamp(Date(nowMs)),
        lineItems =
          listOf(
            InvoiceLineItem(
              itemId = "i2",
              name = "Old Router",
              sku = "OR-200",
              qty = 1.0,
              unitPrice = 100.0,
              taxableAmount = 100.0,
              gstAmount = 0.0,
              lineTotal = 100.0,
            )
          ),
      )

    // Old FY invoice should NOT appear in THIS_MONTH
    val oldInv =
      GstInvoice(
        id = "inv_old",
        invoiceNumber = "INV-050",
        partyId = "p1",
        partyName = "Sharma Traders",
        subtotal = 500.0,
        grandTotal = 500.0,
        createdAt = Timestamp(Date(prevFyMs)),
        lineItems =
          listOf(
            InvoiceLineItem(itemId = "i1", name = "Copper Cable", qty = 4.0, unitPrice = 125.0, taxableAmount = 500.0)
          ),
      )

    val monthReport =
      LedgerAnalyticsEngine.buildReport(
        period = LedgerPeriod.THIS_MONTH,
        parties = listOf(partyProfit, partyLoss),
        inventory = listOf(itemA, itemB),
        invoices = listOf(inv1, inv2, oldInv),
        expenses = emptyList(),
        nowMs = nowMs,
      )

    assertEquals(2, monthReport.invoiceCount)

    val sharma = monthReport.partySummaries.first { it.partyId == "p1" }
    assertTrue(sharma.isProfit)
    assertEquals(50.0, sharma.profitAmount, 0.01)
    assertEquals(25.0, sharma.profitPercent, 0.01)

    val clearance = monthReport.partySummaries.first { it.partyId == "p2" }
    assertFalse(clearance.isProfit)
    assertEquals(-20.0, clearance.profitAmount, 0.01)
    assertEquals(-20.0, clearance.profitPercent, 0.01)

    val copperItem = monthReport.itemSummaries.first { it.itemId == "i1" }
    assertTrue(copperItem.isProfit)
    assertEquals(25.0, copperItem.profitPercent, 0.01)

    val routerItem = monthReport.itemSummaries.first { it.itemId == "i2" }
    assertFalse(routerItem.isProfit)
    assertEquals(-20.0, routerItem.profitPercent, 0.01)

    val prevReport =
      LedgerAnalyticsEngine.buildReport(
        period = LedgerPeriod.PREVIOUS_YEAR,
        parties = listOf(partyProfit, partyLoss),
        inventory = listOf(itemA, itemB),
        invoices = listOf(inv1, inv2, oldInv),
        expenses =
          listOf(
            ErpExpense(id = "e1", title = "Freight", amount = 50.0, createdAt = Timestamp(Date(prevFyMs)))
          ),
        nowMs = nowMs,
      )
    assertEquals(1, prevReport.invoiceCount)
    assertEquals(500.0, prevReport.totalSalesRevenue, 0.01)
  }

  @Test
  fun ledgerReportPdfPrinter_formatsFileNamePercentAndWhatsAppSummaryForBook() {
    assertTrue(LedgerReportPdfPrinter.buildPdfFileName(LedgerPeriod.QUARTER).startsWith("Book_Ledger_quarter_"))
    assertTrue(LedgerReportPdfPrinter.buildPdfFileName(LedgerPeriod.QUARTER).endsWith(".pdf"))
    assertEquals("+25.0%", LedgerAnalyticsEngine.formatProfitPercent(25.0))
    assertEquals("-20.0%", LedgerAnalyticsEngine.formatProfitPercent(-20.0))

    val nowMs = calMs(2026, Calendar.NOVEMBER, 15)
    val report =
      LedgerAnalyticsEngine.buildReport(
        period = LedgerPeriod.PRESENT_YEAR,
        parties = listOf(ErpParty(id = "p1", name = "Alpha Corp")),
        inventory = listOf(InventoryItem(id = "i1", name = "Widget", purchasePrice = 100.0, salePrice = 140.0, stockQty = 5.0)),
        invoices =
          listOf(
            GstInvoice(
              id = "inv1",
              invoiceNumber = "INV-1",
              partyId = "p1",
              partyName = "Alpha Corp",
              subtotal = 140.0,
              grandTotal = 140.0,
              createdAt = Timestamp(Date(nowMs)),
              lineItems = listOf(InvoiceLineItem(itemId = "i1", name = "Widget", qty = 1.0, unitPrice = 140.0, taxableAmount = 140.0)),
            )
          ),
        expenses = emptyList(),
        nowMs = nowMs,
      )

    val summary = LedgerReportPdfPrinter.formatLedgerWhatsAppSummary(report, "Book Enterprise", "₹")
    assertTrue(summary.contains("Book Enterprise"))
    assertTrue(summary.contains("+40.0%"))
    assertTrue(summary.contains("_Shared via Book_"))
  }
}
