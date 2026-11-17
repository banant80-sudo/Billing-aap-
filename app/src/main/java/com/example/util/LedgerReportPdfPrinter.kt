package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.data.model.BusinessProfile
import com.example.ui.screens.formatErpMoney
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LedgerReportPdfPrinter {

  private const val A4_WIDTH = 595
  private const val A4_HEIGHT = 842
  private const val MARGIN_X = 28f
  private const val CONTENT_RIGHT = A4_WIDTH - MARGIN_X
  private const val BOTTOM_LIMIT = A4_HEIGHT - 44f

  fun buildPdfFileName(period: LedgerPeriod): String =
    "Finboss_Ledger_${period.key}_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.pdf"

  fun writeLedgerPdfToStream(
    report: LedgerAnalyticsReport,
    profile: BusinessProfile?,
    currency: String,
    isPlainNonGst: Boolean,
    outputStream: OutputStream,
  ) {
    val pdf = buildLedgerPdfDocument(report, profile, currency, isPlainNonGst)
    try {
      pdf.writeTo(outputStream)
      outputStream.flush()
    } finally {
      pdf.close()
    }
  }

  fun printLedgerReport(
    context: Context,
    report: LedgerAnalyticsReport,
    profile: BusinessProfile?,
    currency: String,
    isPlainNonGst: Boolean,
  ) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
      ?: error("Print service unavailable on this device")
    val jobName = "Finboss_Ledger_${report.period.key}"
    val adapter =
      object : PrintDocumentAdapter() {
        override fun onLayout(
          oldAttributes: PrintAttributes?,
          newAttributes: PrintAttributes,
          cancellationSignal: CancellationSignal?,
          callback: LayoutResultCallback,
          extras: Bundle?,
        ) {
          if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
          }
          val info =
            PrintDocumentInfo.Builder(buildPdfFileName(report.period))
              .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
              .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
              .build()
          callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
          pages: Array<out PageRange>,
          destination: ParcelFileDescriptor,
          cancellationSignal: CancellationSignal?,
          callback: WriteResultCallback,
        ) {
          val pdf = buildLedgerPdfDocument(report, profile, currency, isPlainNonGst)
          try {
            if (cancellationSignal?.isCanceled == true) {
              callback.onWriteCancelled()
              return
            }
            FileOutputStream(destination.fileDescriptor).use { out ->
              pdf.writeTo(out)
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
          } catch (e: Exception) {
            callback.onWriteFailed(e.message)
          } finally {
            pdf.close()
          }
        }
      }
    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
  }

  fun buildLedgerPdfDocument(
    report: LedgerAnalyticsReport,
    profile: BusinessProfile?,
    currency: String,
    isPlainNonGst: Boolean,
  ): PdfDocument {
    val pdf = PdfDocument()

    val navyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B253C") }
    val lightBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
    val headerRowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2E8F0") }
    val profitBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DCFCE7") }
    val lossBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FEE2E2") }
    val borderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
      }
    val whiteTitlePaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val whiteSubPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = 8.5f
      }
    val sectionPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val thPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = 8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val thRight = Paint(thPaint).apply { textAlign = Paint.Align.RIGHT }
    val bodyPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        textSize = 8f
      }
    val bodyRight = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
    val mutedPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        textSize = 7.5f
      }
    val profitPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#15803D")
        textSize = 8f
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val lossPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DC2626")
        textSize = 8f
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }

    var pageNumber = 1
    var page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create())
    var canvas = page.canvas
    var y = 0f

    fun drawPageFooter(c: Canvas, num: Int) {
      c.drawLine(MARGIN_X, A4_HEIGHT - 28f, CONTENT_RIGHT, A4_HEIGHT - 28f, borderPaint)
      c.drawText("Generated by Finboss Billing No.1 ERP • Party, Item & Stock Ledger Statement", MARGIN_X, A4_HEIGHT - 16f, mutedPaint)
      val rightMuted = Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT }
      c.drawText("Page $num", CONTENT_RIGHT, A4_HEIGHT - 16f, rightMuted)
    }

    fun startContinuationPage(continuedTitle: String) {
      drawPageFooter(canvas, pageNumber)
      pdf.finishPage(page)
      pageNumber += 1
      page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create())
      canvas = page.canvas
      canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), 38f, navyPaint)
      canvas.drawText(continuedTitle, MARGIN_X, 24f, whiteTitlePaint)
      y = 56f
    }

    fun ensureSpace(neededHeight: Float, headerTitle: String): Boolean {
      if (y + neededHeight > BOTTOM_LIMIT) {
        startContinuationPage(headerTitle)
        return true
      }
      return false
    }

    // Page 1 Header Banner
    val bizName = profile?.businessName?.ifBlank { null } ?: "Finboss Billing No.1 ERP"
    canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), 76f, navyPaint)
    canvas.drawText("$bizName — Ledger & P&L Statement", MARGIN_X, 28f, whiteTitlePaint)
    canvas.drawText("Period: ${report.period.label} (${report.rangeLabel})", MARGIN_X, 46f, whiteSubPaint)
    val subLine = buildString {
      append("Generated: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())}")
      if (!isPlainNonGst && !profile?.gstin.isNullOrBlank()) {
        append("  •  GSTIN: ${profile?.gstin}")
      }
    }
    canvas.drawText(subLine, MARGIN_X, 62f, whiteSubPaint)

    y = 92f

    // Summary KPI Cards Row
    val cardW = (CONTENT_RIGHT - MARGIN_X - 18f) / 4f
    val cards =
      listOf(
        Triple("Total Sales", formatErpMoney(currency, report.totalSalesRevenue), lightBgPaint to sectionPaint),
        Triple("Purchases / Cost", formatErpMoney(currency, if (report.totalCostOfGoods > 0.0) report.totalCostOfGoods else report.totalPurchasesAndExpenses), lightBgPaint to sectionPaint),
        Triple(
          if (report.isOverallProfit) "Net Profit (${LedgerAnalyticsEngine.formatProfitPercent(report.netProfitPercent)})"
          else "Net Loss (${LedgerAnalyticsEngine.formatProfitPercent(report.netProfitPercent)})",
          formatErpMoney(currency, report.netProfitAmount),
          (if (report.isOverallProfit) profitBgPaint else lossBgPaint) to
            Paint(sectionPaint).apply {
              color = if (report.isOverallProfit) Color.parseColor("#15803D") else Color.parseColor("#DC2626")
            },
        ),
        Triple("Stock Value", formatErpMoney(currency, report.totalStockValue), lightBgPaint to sectionPaint),
      )

    cards.forEachIndexed { idx, (title, value, paints) ->
      val left = MARGIN_X + idx * (cardW + 6f)
      val rect = RectF(left, y, left + cardW, y + 48f)
      canvas.drawRoundRect(rect, 6f, 6f, paints.first)
      canvas.drawRoundRect(rect, 6f, 6f, borderPaint)
      canvas.drawText(title.take(24), left + 8f, y + 16f, mutedPaint)
      canvas.drawText(value.take(18), left + 8f, y + 36f, paints.second)
    }
    y += 66f

    // Section 1: Party-Wise Ledger & Profit/Loss (%)
    canvas.drawText("1. Party-Wise Ledger & Profit / Loss (%)", MARGIN_X, y, sectionPaint)
    y += 10f

    fun drawPartyHeader() {
      val rect = RectF(MARGIN_X, y, CONTENT_RIGHT, y + 20f)
      canvas.drawRect(rect, headerRowBgPaint)
      canvas.drawText("PARTY NAME", MARGIN_X + 6f, y + 13f, thPaint)
      canvas.drawText("BILLS", MARGIN_X + 200f, y + 13f, thRight)
      canvas.drawText("SALES", MARGIN_X + 275f, y + 13f, thRight)
      canvas.drawText("COST", MARGIN_X + 345f, y + 13f, thRight)
      canvas.drawText("PROFIT / LOSS (%)", MARGIN_X + 462f, y + 13f, thRight)
      canvas.drawText("DUE BAL", CONTENT_RIGHT - 6f, y + 13f, thRight)
      y += 20f
    }

    drawPartyHeader()
    if (report.partySummaries.isEmpty()) {
      canvas.drawText("No party records in this period.", MARGIN_X + 6f, y + 14f, mutedPaint)
      y += 22f
    } else {
      report.partySummaries.forEachIndexed { idx, p ->
        if (ensureSpace(20f, "$bizName — Party Ledger (Cont.)")) {
          drawPartyHeader()
        }
        val rowRect = RectF(MARGIN_X, y, CONTENT_RIGHT, y + 19f)
        if (idx % 2 == 1) canvas.drawRect(rowRect, lightBgPaint)
        canvas.drawRect(rowRect, borderPaint)

        canvas.drawText(p.partyName.take(28), MARGIN_X + 6f, y + 13f, bodyPaint)
        canvas.drawText(p.transactionCount.toString(), MARGIN_X + 200f, y + 13f, bodyRight)
        canvas.drawText(formatErpMoney(currency, p.salesNetRevenue), MARGIN_X + 275f, y + 13f, bodyRight)
        canvas.drawText(formatErpMoney(currency, p.costOfGoods), MARGIN_X + 345f, y + 13f, bodyRight)
        if (p.hasSalesActivity) {
          val pStr = "${formatErpMoney(currency, p.profitAmount)} (${LedgerAnalyticsEngine.formatProfitPercent(p.profitPercent)})"
          canvas.drawText(pStr.take(22), MARGIN_X + 462f, y + 13f, if (p.isProfit) profitPaint else lossPaint)
        } else {
          canvas.drawText("—", MARGIN_X + 462f, y + 13f, bodyRight)
        }
        canvas.drawText(formatErpMoney(currency, p.overallBalanceDue), CONTENT_RIGHT - 6f, y + 13f, bodyRight)
        y += 19f
      }
    }

    y += 16f

    // Section 2: Item & Stock Profit/Loss (%)
    ensureSpace(46f, "$bizName — Item & Stock Ledger")
    canvas.drawText("2. Item & Stock Profit / Loss (%)", MARGIN_X, y, sectionPaint)
    y += 10f

    fun drawItemHeader() {
      val rect = RectF(MARGIN_X, y, CONTENT_RIGHT, y + 20f)
      canvas.drawRect(rect, headerRowBgPaint)
      canvas.drawText("ITEM / SKU", MARGIN_X + 6f, y + 13f, thPaint)
      canvas.drawText("STOCK", MARGIN_X + 195f, y + 13f, thRight)
      canvas.drawText("SOLD", MARGIN_X + 248f, y + 13f, thRight)
      canvas.drawText("SALES", MARGIN_X + 320f, y + 13f, thRight)
      canvas.drawText("COST", MARGIN_X + 390f, y + 13f, thRight)
      canvas.drawText("PROFIT / LOSS (%)", CONTENT_RIGHT - 6f, y + 13f, thRight)
      y += 20f
    }

    drawItemHeader()
    if (report.itemSummaries.isEmpty()) {
      canvas.drawText("No item or stock records in this period.", MARGIN_X + 6f, y + 14f, mutedPaint)
      y += 22f
    } else {
      report.itemSummaries.forEachIndexed { idx, it ->
        if (ensureSpace(20f, "$bizName — Item & Stock Ledger (Cont.)")) {
          drawItemHeader()
        }
        val rowRect = RectF(MARGIN_X, y, CONTENT_RIGHT, y + 19f)
        if (idx % 2 == 1) canvas.drawRect(rowRect, lightBgPaint)
        canvas.drawRect(rowRect, borderPaint)

        canvas.drawText(it.itemName.take(26), MARGIN_X + 6f, y + 13f, bodyPaint)
        canvas.drawText("${formatQty(it.currentStockQty)} ${it.unit}", MARGIN_X + 195f, y + 13f, bodyRight)
        canvas.drawText(formatQty(it.soldQty), MARGIN_X + 248f, y + 13f, bodyRight)
        canvas.drawText(formatErpMoney(currency, it.salesValue), MARGIN_X + 320f, y + 13f, bodyRight)
        canvas.drawText(formatErpMoney(currency, it.costOfSold), MARGIN_X + 390f, y + 13f, bodyRight)
        if (it.hasSoldActivity) {
          val pStr = "${formatErpMoney(currency, it.profitAmount)} (${LedgerAnalyticsEngine.formatProfitPercent(it.profitPercent)})"
          canvas.drawText(pStr.take(24), CONTENT_RIGHT - 6f, y + 13f, if (it.isProfit) profitPaint else lossPaint)
        } else {
          canvas.drawText("Val ${formatErpMoney(currency, it.stockValuation)}", CONTENT_RIGHT - 6f, y + 13f, bodyRight)
        }
        y += 19f
      }
    }

    y += 16f

    // Section 3: Transactions Log
    ensureSpace(46f, "$bizName — Ledger Transactions")
    canvas.drawText("3. Period Transactions (${report.transactions.size} entries)", MARGIN_X, y, sectionPaint)
    y += 10f

    fun drawTxHeader() {
      val rect = RectF(MARGIN_X, y, CONTENT_RIGHT, y + 20f)
      canvas.drawRect(rect, headerRowBgPaint)
      canvas.drawText("DATE / TYPE", MARGIN_X + 6f, y + 13f, thPaint)
      canvas.drawText("REF #", MARGIN_X + 125f, y + 13f, thPaint)
      canvas.drawText("PARTY & ITEMS", MARGIN_X + 195f, y + 13f, thPaint)
      canvas.drawText("AMOUNT", MARGIN_X + 445f, y + 13f, thRight)
      canvas.drawText("P&L %", CONTENT_RIGHT - 6f, y + 13f, thRight)
      y += 20f
    }

    drawTxHeader()
    if (report.transactions.isEmpty()) {
      canvas.drawText("No transactions recorded in this period.", MARGIN_X + 6f, y + 14f, mutedPaint)
      y += 22f
    } else {
      report.transactions.forEachIndexed { idx, tx ->
        if (ensureSpace(20f, "$bizName — Ledger Transactions (Cont.)")) {
          drawTxHeader()
        }
        val rowRect = RectF(MARGIN_X, y, CONTENT_RIGHT, y + 19f)
        if (idx % 2 == 1) canvas.drawRect(rowRect, lightBgPaint)
        canvas.drawRect(rowRect, borderPaint)

        canvas.drawText("${tx.voucherType} • ${tx.dateLabel.take(9)}", MARGIN_X + 6f, y + 13f, bodyPaint)
        canvas.drawText(tx.voucherNumber.take(12), MARGIN_X + 125f, y + 13f, bodyPaint)
        canvas.drawText("${tx.partyName}: ${tx.itemsSummary}".take(32), MARGIN_X + 195f, y + 13f, bodyPaint)
        canvas.drawText(formatErpMoney(currency, tx.amount), MARGIN_X + 445f, y + 13f, bodyRight)
        val pct = tx.profitPercent
        val pAmt = tx.profitAmount
        if (pct != null && pAmt != null) {
          canvas.drawText(
            LedgerAnalyticsEngine.formatProfitPercent(pct),
            CONTENT_RIGHT - 6f,
            y + 13f,
            if (pAmt >= 0.0) profitPaint else lossPaint,
          )
        } else {
          canvas.drawText("—", CONTENT_RIGHT - 6f, y + 13f, bodyRight)
        }
        y += 19f
      }
    }

    drawPageFooter(canvas, pageNumber)
    pdf.finishPage(page)
    return pdf
  }

  fun formatLedgerWhatsAppSummary(
    report: LedgerAnalyticsReport,
    businessName: String,
    currency: String,
  ): String = buildString {
    val plLabel = if (report.isOverallProfit) "Profit" else "Loss"
    appendLine("*$businessName — Ledger & P&L (${report.period.label})*")
    appendLine("Period: ${report.rangeLabel}")
    appendLine("Total Sales: ${formatErpMoney(currency, report.totalSalesRevenue)} (${report.invoiceCount} bills)")
    appendLine("Net $plLabel: *${formatErpMoney(currency, report.netProfitAmount)}* (${LedgerAnalyticsEngine.formatProfitPercent(report.netProfitPercent)})")
    appendLine("Stock Valuation: ${formatErpMoney(currency, report.totalStockValue)}")
    appendLine("To Collect: ${formatErpMoney(currency, report.totalReceivables)} • To Pay: ${formatErpMoney(currency, report.totalPayables)}")
    val topItems = report.itemSummaries.filter { it.hasSoldActivity }.take(3)
    if (topItems.isNotEmpty()) {
      appendLine("Top Items:")
      topItems.forEach {
        appendLine("• ${it.itemName}: ${formatErpMoney(currency, it.profitAmount)} (${LedgerAnalyticsEngine.formatProfitPercent(it.profitPercent)})")
      }
    }
    appendLine("_Shared via Finboss Billing No.1 ERP_")
  }

  private fun formatQty(qty: Double): String =
    if (qty % 1.0 == 0.0) qty.toLong().toString() else String.format(Locale.US, "%.1f", qty)
}
