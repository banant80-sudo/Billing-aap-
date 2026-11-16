package com.example.util

import com.example.data.model.DashboardEntry
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class LedgerPeriod(val key: String, val label: String) {
  THIS_MONTH("month", "This Month"),
  QUARTER("quarter", "Quarter"),
  HALF_YEAR("half_year", "Half Year"),
  PREVIOUS_YEAR("prev_year", "Previous Year"),
  PRESENT_YEAR("present_year", "Present Year"),
  ALL_TIME("all", "All Time"),
}

data class PeriodDateRange(
  val period: LedgerPeriod,
  val startMs: Long,
  val endMs: Long,
  val rangeLabel: String,
)

data class PartyProfitSummary(
  val partyId: String,
  val partyName: String,
  val partyType: String,
  val phone: String,
  val gstin: String,
  val transactionCount: Int,
  val periodBilledTotal: Double,
  val periodPaidTotal: Double,
  val overallBalanceDue: Double,
  val salesNetRevenue: Double,
  val costOfGoods: Double,
  val purchaseSpend: Double,
  val profitAmount: Double,
  val profitPercent: Double,
  val hasSalesActivity: Boolean,
) {
  val isProfit: Boolean
    get() = profitAmount >= 0.0
}

data class ItemStockProfitSummary(
  val itemId: String,
  val itemName: String,
  val sku: String,
  val hsnCode: String,
  val unit: String,
  val currentStockQty: Double,
  val purchaseUnitPrice: Double,
  val saleUnitPrice: Double,
  val stockValuation: Double,
  val soldQty: Double,
  val salesValue: Double,
  val costOfSold: Double,
  val profitAmount: Double,
  val profitPercent: Double,
  val hasSoldActivity: Boolean,
) {
  val isProfit: Boolean
    get() = profitAmount >= 0.0
}

data class LedgerTransactionRow(
  val id: String,
  val timestampMs: Long,
  val dateLabel: String,
  val voucherType: String,
  val voucherNumber: String,
  val partyName: String,
  val itemsSummary: String,
  val amount: Double,
  val paidOrSettled: Double,
  val profitAmount: Double?,
  val profitPercent: Double?,
)

data class LedgerAnalyticsReport(
  val period: LedgerPeriod,
  val rangeLabel: String,
  val totalSalesRevenue: Double,
  val totalSalesGrand: Double,
  val totalCostOfGoods: Double,
  val totalPurchasesAndExpenses: Double,
  val netProfitAmount: Double,
  val netProfitPercent: Double,
  val totalStockQty: Double,
  val totalStockValue: Double,
  val totalReceivables: Double,
  val totalPayables: Double,
  val invoiceCount: Int,
  val partySummaries: List<PartyProfitSummary>,
  val itemSummaries: List<ItemStockProfitSummary>,
  val transactions: List<LedgerTransactionRow>,
) {
  val isOverallProfit: Boolean
    get() = netProfitAmount >= 0.0
}

object LedgerAnalyticsEngine {

  fun resolveDateRange(
    period: LedgerPeriod,
    nowMs: Long = System.currentTimeMillis(),
  ): PeriodDateRange {
    if (period == LedgerPeriod.ALL_TIME) {
      return PeriodDateRange(
        period = period,
        startMs = 0L,
        endMs = Long.MAX_VALUE,
        rangeLabel = "All Transactions",
      )
    }

    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) // 0 = Jan .. 11 = Dec
    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun startOfDay(y: Int, m: Int, d: Int): Long =
      Calendar.getInstance().apply {
        set(y, m, d, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
      }.timeInMillis

    fun endOfMonthDay(y: Int, m: Int): Long =
      Calendar.getInstance().apply {
        set(y, m, 1, 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
      }.timeInMillis

    // Indian Financial Year starts April (Calendar.APRIL = 3)
    val fyStartYear = if (month >= Calendar.APRIL) year else year - 1

    return when (period) {
      LedgerPeriod.THIS_MONTH -> {
        val start = startOfDay(year, month, 1)
        val end = endOfMonthDay(year, month)
        val mLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(start))
        PeriodDateRange(period, start, end, mLabel)
      }
      LedgerPeriod.QUARTER -> {
        val (qName, qStartYear, qStartMonth, qEndMonth) =
          when (month) {
            in Calendar.APRIL..Calendar.JUNE -> Quad("Q1", year, Calendar.APRIL, Calendar.JUNE)
            in Calendar.JULY..Calendar.SEPTEMBER -> Quad("Q2", year, Calendar.JULY, Calendar.SEPTEMBER)
            in Calendar.OCTOBER..Calendar.DECEMBER -> Quad("Q3", year, Calendar.OCTOBER, Calendar.DECEMBER)
            else -> Quad("Q4", year, Calendar.JANUARY, Calendar.MARCH)
          }
        val start = startOfDay(qStartYear, qStartMonth, 1)
        val end = endOfMonthDay(qStartYear, qEndMonth)
        val label = "$qName (${dateFmt.format(Date(start))} – ${dateFmt.format(Date(end))})"
        PeriodDateRange(period, start, end, label)
      }
      LedgerPeriod.HALF_YEAR -> {
        val isH1 = month in Calendar.APRIL..Calendar.SEPTEMBER
        val startYear = if (isH1) year else fyStartYear
        val startMonth = if (isH1) Calendar.APRIL else Calendar.OCTOBER
        val endYear = if (isH1) year else fyStartYear + 1
        val endMonth = if (isH1) Calendar.SEPTEMBER else Calendar.MARCH
        val start = startOfDay(startYear, startMonth, 1)
        val end = endOfMonthDay(endYear, endMonth)
        val hTag = if (isH1) "H1" else "H2"
        val label = "$hTag (${dateFmt.format(Date(start))} – ${dateFmt.format(Date(end))})"
        PeriodDateRange(period, start, end, label)
      }
      LedgerPeriod.PRESENT_YEAR -> {
        val start = startOfDay(fyStartYear, Calendar.APRIL, 1)
        val end = endOfMonthDay(fyStartYear + 1, Calendar.MARCH)
        val shortNext = ((fyStartYear + 1) % 100).toString().padStart(2, '0')
        val label = "FY $fyStartYear-$shortNext (${dateFmt.format(Date(start))} – ${dateFmt.format(Date(end))})"
        PeriodDateRange(period, start, end, label)
      }
      LedgerPeriod.PREVIOUS_YEAR -> {
        val prevStartYear = fyStartYear - 1
        val start = startOfDay(prevStartYear, Calendar.APRIL, 1)
        val end = endOfMonthDay(prevStartYear + 1, Calendar.MARCH)
        val shortNext = ((prevStartYear + 1) % 100).toString().padStart(2, '0')
        val label = "FY $prevStartYear-$shortNext (${dateFmt.format(Date(start))} – ${dateFmt.format(Date(end))})"
        PeriodDateRange(period, start, end, label)
      }
      LedgerPeriod.ALL_TIME -> PeriodDateRange(period, 0L, Long.MAX_VALUE, "All Transactions")
    }
  }

  private data class Quad(val qName: String, val year: Int, val startMonth: Int, val endMonth: Int)

  fun computeProfitPercent(profitAmount: Double, costBasis: Double, revenueBasis: Double): Double {
    return when {
      costBasis > 0.0001 -> (profitAmount / costBasis) * 100.0
      revenueBasis > 0.0001 -> (profitAmount / revenueBasis) * 100.0
      else -> 0.0
    }
  }

  fun formatProfitPercent(percent: Double): String {
    val sign = if (percent > 0.05) "+" else if (percent < -0.05) "-" else ""
    val formatted = String.format(Locale.US, "%.1f", abs(percent))
    return "$sign$formatted%"
  }

  fun buildReport(
    period: LedgerPeriod,
    parties: List<ErpParty>,
    inventory: List<InventoryItem>,
    invoices: List<GstInvoice>,
    expenses: List<ErpExpense>,
    entries: List<DashboardEntry> = emptyList(),
    nowMs: Long = System.currentTimeMillis(),
    isPlainNonGst: Boolean = false,
  ): LedgerAnalyticsReport {
    val range = resolveDateRange(period, nowMs)
    val rowDateFmt = SimpleDateFormat("dd MMM yy, hh:mm a", Locale.getDefault())

    fun inRange(tsMs: Long): Boolean = tsMs in range.startMs..range.endMs

    val filteredInvoices =
      invoices.filter { inv ->
        val ts = inv.createdAt?.toDate()?.time ?: nowMs
        inRange(ts)
      }
    val filteredExpenses =
      expenses.filter { exp ->
        val ts = exp.createdAt?.toDate()?.time ?: nowMs
        inRange(ts)
      }
    val filteredEntries =
      entries.filter { entry ->
        val ts = entry.createdAt?.toDate()?.time ?: nowMs
        inRange(ts)
      }

    // Inventory lookup maps
    val itemById = inventory.associateBy { it.id }
    val itemBySku =
      inventory.filter { it.sku.isNotBlank() }.associateBy { it.sku.trim().lowercase() }
    val itemByName =
      inventory.filter { it.name.isNotBlank() }.associateBy { it.name.trim().lowercase() }

    fun findInventoryItem(itemId: String, sku: String, name: String): InventoryItem? {
      if (itemId.isNotBlank()) itemById[itemId]?.let { return it }
      if (sku.isNotBlank()) itemBySku[sku.trim().lowercase()]?.let { return it }
      if (name.isNotBlank()) itemByName[name.trim().lowercase()]?.let { return it }
      return null
    }

    // Accumulators for Item-wise P&L
    data class ItemAcc(
      val key: String,
      val invItem: InventoryItem?,
      val fallbackName: String,
      val fallbackSku: String,
      val fallbackHsn: String,
      val fallbackUnit: String,
      var soldQty: Double = 0.0,
      var salesValue: Double = 0.0,
      var costOfSold: Double = 0.0,
    )
    val itemMap = linkedMapOf<String, ItemAcc>()
    inventory.forEach { item ->
      itemMap[item.id] =
        ItemAcc(
          key = item.id,
          invItem = item,
          fallbackName = item.name,
          fallbackSku = item.sku,
          fallbackHsn = item.hsnCode,
          fallbackUnit = item.unit,
        )
    }

    // Accumulators for Party-wise P&L
    data class PartyAcc(
      val key: String,
      val party: ErpParty?,
      val name: String,
      val type: String,
      val phone: String,
      val gstin: String,
      var txCount: Int = 0,
      var billedTotal: Double = 0.0,
      var paidTotal: Double = 0.0,
      var salesNetRevenue: Double = 0.0,
      var costOfGoods: Double = 0.0,
      var purchaseSpend: Double = 0.0,
    )
    val partyMap = linkedMapOf<String, PartyAcc>()
    val partyById = parties.associateBy { it.id }
    val partyByName = parties.associateBy { it.name.trim().lowercase() }

    parties.forEach { p ->
      partyMap[p.id] =
        PartyAcc(
          key = p.id,
          party = p,
          name = p.name,
          type = p.partyType,
          phone = p.phone,
          gstin = p.gstin,
        )
    }

    val txRows = mutableListOf<LedgerTransactionRow>()
    var totalSalesRevenue = 0.0
    var totalSalesGrand = 0.0
    var totalCostOfGoods = 0.0

    filteredInvoices.forEach { inv ->
      val ts = inv.createdAt?.toDate()?.time ?: nowMs
      val rawLineTaxableSum =
        inv.lineItems.sumOf { line ->
          if (line.taxableAmount > 0.0) line.taxableAmount else line.qty * line.unitPrice
        }
      val effectiveSubtotal = if (inv.subtotal > 0.0) inv.subtotal else rawLineTaxableSum
      val netInvoiceRevenue = (effectiveSubtotal - inv.discountAmount).coerceAtLeast(0.0)
      val discountFactor =
        if (effectiveSubtotal > 0.0001) netInvoiceRevenue / effectiveSubtotal else 1.0

      var invCost = 0.0
      inv.lineItems.forEach { line ->
        val matched = findInventoryItem(line.itemId, line.sku, line.name)
        val unitCost = matched?.purchasePrice ?: 0.0
        val lineRawRev =
          if (line.taxableAmount > 0.0) line.taxableAmount else line.qty * line.unitPrice
        val lineNetRev = lineRawRev * discountFactor
        val lineCost = line.qty * unitCost
        invCost += lineCost

        val itemKey = matched?.id ?: "name:${line.name.trim().lowercase()}"
        val acc =
          itemMap.getOrPut(itemKey) {
            ItemAcc(
              key = itemKey,
              invItem = matched,
              fallbackName = line.name.ifBlank { "Unnamed Item" },
              fallbackSku = line.sku,
              fallbackHsn = line.hsnCode,
              fallbackUnit = line.unit,
            )
          }
        acc.soldQty += line.qty
        acc.salesValue += lineNetRev
        acc.costOfSold += lineCost
      }

      val invProfit = netInvoiceRevenue - invCost
      val invProfitPct = computeProfitPercent(invProfit, invCost, netInvoiceRevenue)
      totalSalesRevenue += netInvoiceRevenue
      totalSalesGrand += inv.grandTotal
      totalCostOfGoods += invCost

      // Update party accumulator
      val matchedParty =
        inv.partyId.takeIf { it.isNotBlank() }?.let { partyById[it] }
          ?: partyByName[inv.partyName.trim().lowercase()]
      val partyKey = matchedParty?.id ?: "walkin:${inv.partyName.trim().lowercase()}"
      val pAcc =
        partyMap.getOrPut(partyKey) {
          PartyAcc(
            key = partyKey,
            party = matchedParty,
            name = inv.partyName.ifBlank { "Walk-in Customer" },
            type = ErpParty.TYPE_CUSTOMER,
            phone = "",
            gstin = inv.partyGstin,
          )
        }
      pAcc.txCount += 1
      pAcc.billedTotal += inv.grandTotal
      pAcc.paidTotal += inv.paidAmount
      pAcc.salesNetRevenue += netInvoiceRevenue
      pAcc.costOfGoods += invCost

      val itemsDesc =
        if (inv.lineItems.isEmpty()) "Bill entry"
        else inv.lineItems.joinToString(", ") { "${it.name} (${formatQtyCompact(it.qty)} ${it.unit})" }
      txRows.add(
        LedgerTransactionRow(
          id = "inv_${inv.id}",
          timestampMs = ts,
          dateLabel = rowDateFmt.format(Date(ts)),
          voucherType = if (isPlainNonGst) "BILL" else "SALE",
          voucherNumber = inv.invoiceNumber,
          partyName = inv.partyName,
          itemsSummary = itemsDesc,
          amount = inv.grandTotal,
          paidOrSettled = inv.paidAmount,
          profitAmount = invProfit,
          profitPercent = invProfitPct,
        )
      )
    }

    var totalPurchasesAndExpenses = 0.0
    filteredExpenses.forEach { exp ->
      val ts = exp.createdAt?.toDate()?.time ?: nowMs
      totalPurchasesAndExpenses += exp.amount

      if (exp.partyId.isNotBlank() || exp.partyName.isNotBlank()) {
        val matchedParty =
          exp.partyId.takeIf { it.isNotBlank() }?.let { partyById[it] }
            ?: partyByName[exp.partyName.trim().lowercase()]
        val partyKey = matchedParty?.id ?: "exp_party:${exp.partyName.trim().lowercase()}"
        val pAcc =
          partyMap.getOrPut(partyKey) {
            PartyAcc(
              key = partyKey,
              party = matchedParty,
              name = exp.partyName,
              type = ErpParty.TYPE_SUPPLIER,
              phone = "",
              gstin = "",
            )
          }
        pAcc.txCount += 1
        pAcc.purchaseSpend += exp.amount
      }

      val isPurchase = exp.category == ErpExpense.CAT_PURCHASES
      txRows.add(
        LedgerTransactionRow(
          id = "exp_${exp.id}",
          timestampMs = ts,
          dateLabel = rowDateFmt.format(Date(ts)),
          voucherType = if (isPurchase) "PURCHASE" else "EXPENSE",
          voucherNumber = exp.referenceNo.ifBlank { exp.category.uppercase() },
          partyName = exp.partyName.ifBlank { exp.title },
          itemsSummary = exp.title,
          amount = exp.amount,
          paidOrSettled = if (exp.status == ErpExpense.STATUS_SETTLED) exp.amount else 0.0,
          profitAmount = null,
          profitPercent = null,
        )
      )
    }

    filteredEntries.forEach { entry ->
      val ts = entry.createdAt?.toDate()?.time ?: nowMs
      val isExpenseEntry = entry.category == DashboardEntry.CATEGORY_EXPENSE
      if (isExpenseEntry) {
        totalPurchasesAndExpenses += entry.amount
      } else {
        totalSalesRevenue += entry.amount
        totalSalesGrand += entry.amount
      }
      txRows.add(
        LedgerTransactionRow(
          id = "entry_${entry.id}",
          timestampMs = ts,
          dateLabel = rowDateFmt.format(Date(ts)),
          voucherType = if (isExpenseEntry) "CASH OUT" else "CASH IN",
          voucherNumber = entry.referenceCode.ifBlank { entry.category.uppercase() },
          partyName = entry.partyName.ifBlank { entry.title },
          itemsSummary = entry.title.ifBlank { "Quick ledger entry" },
          amount = entry.amount,
          paidOrSettled = entry.amount,
          profitAmount = null,
          profitPercent = null,
        )
      )
    }

    val partySummaries =
      partyMap.values
        .map { acc ->
          val profit = acc.salesNetRevenue - acc.costOfGoods
          val pct = computeProfitPercent(profit, acc.costOfGoods, acc.salesNetRevenue)
          PartyProfitSummary(
            partyId = acc.key,
            partyName = acc.name,
            partyType = acc.type,
            phone = acc.phone,
            gstin = acc.gstin,
            transactionCount = acc.txCount,
            periodBilledTotal = acc.billedTotal,
            periodPaidTotal = acc.paidTotal,
            overallBalanceDue = acc.party?.outstandingBalance ?: (acc.billedTotal - acc.paidTotal).coerceAtLeast(0.0),
            salesNetRevenue = acc.salesNetRevenue,
            costOfGoods = acc.costOfGoods,
            purchaseSpend = acc.purchaseSpend,
            profitAmount = profit,
            profitPercent = pct,
            hasSalesActivity = acc.salesNetRevenue > 0.0 || acc.costOfGoods > 0.0,
          )
        }
        .sortedWith(
          compareByDescending<PartyProfitSummary> { it.hasSalesActivity }
            .thenByDescending { it.periodBilledTotal + it.purchaseSpend }
            .thenByDescending { it.overallBalanceDue }
        )

    val itemSummaries =
      itemMap.values
        .map { acc ->
          val item = acc.invItem
          val profit = acc.salesValue - acc.costOfSold
          val hasSold = acc.soldQty > 0.0
          val pct =
            if (hasSold) {
              computeProfitPercent(profit, acc.costOfSold, acc.salesValue)
            } else {
              0.0
            }
          ItemStockProfitSummary(
            itemId = acc.key,
            itemName = item?.name ?: acc.fallbackName,
            sku = item?.sku ?: acc.fallbackSku,
            hsnCode = item?.hsnCode ?: acc.fallbackHsn,
            unit = item?.unit ?: acc.fallbackUnit,
            currentStockQty = item?.stockQty ?: 0.0,
            purchaseUnitPrice = item?.purchasePrice ?: 0.0,
            saleUnitPrice = item?.salePrice ?: 0.0,
            stockValuation = item?.stockValuation ?: 0.0,
            soldQty = acc.soldQty,
            salesValue = acc.salesValue,
            costOfSold = acc.costOfSold,
            profitAmount = profit,
            profitPercent = pct,
            hasSoldActivity = hasSold,
          )
        }
        .sortedWith(
          compareByDescending<ItemStockProfitSummary> { it.hasSoldActivity }
            .thenByDescending { it.salesValue }
            .thenByDescending { it.stockValuation }
        )

    // Overall P&L for the period: Gross margin from sales when COGS exists, otherwise Sales - Expenses
    val netProfit =
      if (totalCostOfGoods > 0.0) {
        totalSalesRevenue - totalCostOfGoods
      } else {
        totalSalesRevenue - totalPurchasesAndExpenses
      }
    val overallBasis = if (totalCostOfGoods > 0.0) totalCostOfGoods else totalPurchasesAndExpenses
    val netProfitPct = computeProfitPercent(netProfit, overallBasis, totalSalesRevenue)

    val totalStockQty = inventory.sumOf { it.stockQty.coerceAtLeast(0.0) }
    val totalStockValue = inventory.sumOf { it.stockValuation }
    val totalReceivables =
      parties.filter { it.partyType == ErpParty.TYPE_CUSTOMER }.sumOf { it.outstandingBalance }
    val totalPayables =
      parties.filter { it.partyType == ErpParty.TYPE_SUPPLIER }.sumOf { it.outstandingBalance }

    return LedgerAnalyticsReport(
      period = period,
      rangeLabel = range.rangeLabel,
      totalSalesRevenue = totalSalesRevenue,
      totalSalesGrand = totalSalesGrand,
      totalCostOfGoods = totalCostOfGoods,
      totalPurchasesAndExpenses = totalPurchasesAndExpenses,
      netProfitAmount = netProfit,
      netProfitPercent = netProfitPct,
      totalStockQty = totalStockQty,
      totalStockValue = totalStockValue,
      totalReceivables = totalReceivables,
      totalPayables = totalPayables,
      invoiceCount = filteredInvoices.size,
      partySummaries = partySummaries,
      itemSummaries = itemSummaries,
      transactions = txRows.sortedByDescending { it.timestampMs },
    )
  }

  private fun formatQtyCompact(qty: Double): String =
    if (qty % 1.0 == 0.0) qty.toLong().toString() else String.format(Locale.US, "%.2f", qty)
}
