package com.example.ai

import com.example.data.model.BusinessProfile
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.ui.screens.formatErpMoney
import com.example.util.LedgerAnalyticsEngine
import com.example.util.LedgerPeriod

object BookAiContextBuilder {

  fun buildAnalyticsContextPrompt(
    profile: BusinessProfile?,
    currency: String,
    isPlainNonGst: Boolean,
    parties: List<ErpParty>,
    inventory: List<InventoryItem>,
    invoices: List<GstInvoice>,
    expenses: List<ErpExpense>,
    entries: List<DashboardEntry>,
  ): String = buildString {
    val monthReport =
      LedgerAnalyticsEngine.buildReport(
        period = LedgerPeriod.THIS_MONTH,
        parties = parties,
        inventory = inventory,
        invoices = invoices,
        expenses = expenses,
        entries = entries,
        isPlainNonGst = isPlainNonGst,
      )
    val yearReport =
      LedgerAnalyticsEngine.buildReport(
        period = LedgerPeriod.PRESENT_YEAR,
        parties = parties,
        inventory = inventory,
        invoices = invoices,
        expenses = expenses,
        entries = entries,
        isPlainNonGst = isPlainNonGst,
      )

    appendLine("=== BOOK ERP LIVE BUSINESS SNAPSHOT ===")
    appendLine("Business: ${profile?.businessName?.ifBlank { "Book Enterprise" } ?: "Book Enterprise"}")
    appendLine("Mode: ${if (isPlainNonGst) "Plain Non-GST Counter" else "Main GST Business"}")
    if (!isPlainNonGst && !profile?.gstin.isNullOrBlank()) {
      appendLine("GSTIN: ${profile?.gstin}")
    }
    appendLine("Currency: $currency")
    appendLine()

    appendLine("--- THIS MONTH (${monthReport.rangeLabel}) ---")
    appendLine("Sales Revenue (Excl GST): ${formatErpMoney(currency, monthReport.totalSalesRevenue)} | Billed Grand Total: ${formatErpMoney(currency, monthReport.totalSalesGrand)} (${monthReport.invoiceCount} bills)")
    appendLine("Net Profit/Loss: ${formatErpMoney(currency, monthReport.netProfitAmount)} (${LedgerAnalyticsEngine.formatProfitPercent(monthReport.netProfitPercent)})")
    appendLine()

    appendLine("--- PRESENT FINANCIAL YEAR (${yearReport.rangeLabel}) ---")
    appendLine("FY Sales Revenue: ${formatErpMoney(currency, yearReport.totalSalesRevenue)} | FY Grand Total: ${formatErpMoney(currency, yearReport.totalSalesGrand)} (${yearReport.invoiceCount} bills)")
    appendLine("FY Cost of Goods Sold (COGS): ${formatErpMoney(currency, yearReport.totalCostOfGoods)} | FY Operating Expenses: ${formatErpMoney(currency, yearReport.totalPurchasesAndExpenses)}")
    appendLine("FY Net Profit/Loss: ${formatErpMoney(currency, yearReport.netProfitAmount)} (${LedgerAnalyticsEngine.formatProfitPercent(yearReport.netProfitPercent)})")
    appendLine("Receivables (To Collect): ${formatErpMoney(currency, yearReport.totalReceivables)} | Payables (To Pay): ${formatErpMoney(currency, yearReport.totalPayables)}")
    appendLine("Total Stock Qty: ${BookAiJsonParser.formatQtyShort(yearReport.totalStockQty)} | Stock Valuation: ${formatErpMoney(currency, yearReport.totalStockValue)}")
    appendLine()

    val outGst = invoices.sumOf { it.gstTotal }
    val inGst = expenses.sumOf { it.gstInputAmount }
    if (!isPlainNonGst) {
      appendLine("--- GST SUMMARY ---")
      appendLine("Output GST: ${formatErpMoney(currency, outGst)} | Input Tax Credit (ITC): ${formatErpMoney(currency, inGst)} | Net GST Payable: ${formatErpMoney(currency, (outGst - inGst).coerceAtLeast(0.0))}")
      appendLine()
    }

    appendLine("--- PARTY LEDGER & P&L % (Top 15) ---")
    if (yearReport.partySummaries.isEmpty()) {
      appendLine("No parties recorded yet.")
    } else {
      yearReport.partySummaries.take(15).forEach { p ->
        val plText =
          if (p.hasSalesActivity) {
            "P&L=${formatErpMoney(currency, p.profitAmount)} (${LedgerAnalyticsEngine.formatProfitPercent(p.profitPercent)})"
          } else {
            "No sales in FY"
          }
        appendLine("• ${p.partyName} [${p.partyType}]: Billed=${formatErpMoney(currency, p.periodBilledTotal)}, Due=${formatErpMoney(currency, p.overallBalanceDue)}, $plText")
      }
    }
    appendLine()

    appendLine("--- ITEM & STOCK MARGIN % (Top 20) ---")
    if (yearReport.itemSummaries.isEmpty()) {
      appendLine("No stock items recorded yet.")
    } else {
      yearReport.itemSummaries.take(20).forEach { it ->
        val marginInfo =
          if (it.hasSoldActivity) {
            "Sold=${BookAiJsonParser.formatQtyShort(it.soldQty)}, P&L=${formatErpMoney(currency, it.profitAmount)} (${LedgerAnalyticsEngine.formatProfitPercent(it.profitPercent)})"
          } else {
            "Not sold in FY"
          }
        appendLine("• ${it.itemName} (${it.sku}): Stock=${BookAiJsonParser.formatQtyShort(it.currentStockQty)} ${it.unit}, Buy=${formatErpMoney(currency, it.purchaseUnitPrice)}, Sale=${formatErpMoney(currency, it.saleUnitPrice)}, $marginInfo")
      }
    }

    val lowStock = inventory.filter { it.isOutOfStock || it.isLowStock }
    if (lowStock.isNotEmpty()) {
      appendLine()
      appendLine("--- LOW / OUT OF STOCK ALERTS ---")
      lowStock.take(12).forEach {
        appendLine("• ${it.name}: ${BookAiJsonParser.formatQtyShort(it.stockQty)} ${it.unit} (Alert <= ${BookAiJsonParser.formatQtyShort(it.lowStockThreshold)})")
      }
    }
  }

  fun buildCatalogHintPrompt(
    inventory: List<InventoryItem>,
    parties: List<ErpParty>,
    isPlainNonGst: Boolean,
  ): String = buildString {
    appendLine("Business Tax Mode: ${if (isPlainNonGst) "Plain Non-GST (always 0% GST)" else "GST Registered (use matching item GST % or 0/5/12/18/28)"}")
    if (parties.isNotEmpty()) {
      appendLine("Known Parties: " + parties.take(25).joinToString("; ") { "${it.name}${if (it.gstin.isNotBlank()) " [GSTIN:${it.gstin}]" else ""}" })
    }
    if (inventory.isNotEmpty()) {
      appendLine("Known Inventory Items: " + inventory.take(30).joinToString("; ") { "${it.name} (SKU:${it.sku}, Unit:${it.unit}, Sale:${it.salePrice}, Buy:${it.purchasePrice}, GST:${it.gstRatePercent}%)" })
    }
  }
}
