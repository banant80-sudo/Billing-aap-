package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
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
import com.example.data.model.GstInvoice
import com.example.data.model.InvoiceLineItem
import com.example.data.reference.IndiaStates
import com.example.data.sync.ErpAppSettings
import com.example.data.sync.InvoicePrintStyle
import com.example.data.sync.PrintPaperSize
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class InvoicePrintOptions(
  val paperSize: PrintPaperSize = PrintPaperSize.A4,
  val style: InvoicePrintStyle = InvoicePrintStyle.TALLY,
  val showHsn: Boolean = true,
  val showAddressAndState: Boolean = true,
  val showAmountInWords: Boolean = true,
  val footerNote: String = "Thank you for your business!",
  val termsText: String = "Goods once sold will not be taken back. Subject to local jurisdiction.",
) {
  companion object {
    fun fromSettings(settings: ErpAppSettings): InvoicePrintOptions =
      InvoicePrintOptions(
        paperSize = settings.defaultPaperSize,
        style = settings.defaultPrintStyle,
        showHsn = settings.showHsnOnInvoicePrint,
        showAddressAndState = settings.showAddressAndStateOnPrint,
        showAmountInWords = settings.showAmountInWordsOnPrint,
        footerNote = settings.invoiceFooterNote,
        termsText = settings.invoiceTermsText,
      )
  }
}

object InvoicePdfPrinter {
  private const val A4_WIDTH = 595
  private const val A4_HEIGHT = 842
  private const val MARGIN_X = 32f
  private const val CONTENT_RIGHT = A4_WIDTH - MARGIN_X

  fun buildInvoicePdfDocument(
    invoice: GstInvoice,
    profile: BusinessProfile?,
    currency: String,
    options: InvoicePrintOptions = InvoicePrintOptions(),
  ): PdfDocument {
    val isPlainNonGst =
      profile?.isPlainNonGst == true || invoice.resolvedProfileId() == BusinessProfile.SLOT_PLAIN_1
    val effectiveShowHsn = options.showHsn && !isPlainNonGst

    if (options.paperSize.isThermal) {
      return buildThermalRollPdf(invoice, profile, currency, options, isPlainNonGst, effectiveShowHsn)
    }

    if (options.style == InvoicePrintStyle.THERMAL_POS) {
      return buildThermalStripOnSheetPdf(invoice, profile, currency, options, isPlainNonGst, effectiveShowHsn)
    }

    return buildScaledSheetPdf(invoice, profile, currency, options, isPlainNonGst, effectiveShowHsn)
  }

  private fun buildScaledSheetPdf(
    invoice: GstInvoice,
    profile: BusinessProfile?,
    currency: String,
    options: InvoicePrintOptions,
    isPlainNonGst: Boolean,
    effectiveShowHsn: Boolean,
  ): PdfDocument {
    val pdf = PdfDocument()
    val targetWidth = options.paperSize.widthPt
    val targetHeight = options.paperSize.heightPt
    val scale = targetWidth.toFloat() / A4_WIDTH.toFloat()

    val isTally = options.style == InvoicePrintStyle.TALLY
    val isSimple = options.style == InvoicePrintStyle.SIMPLE

    val primaryBannerPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isSimple) Color.WHITE else Color.parseColor("#0B253C")
      }
    val emeraldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0D9488") }
    val lightBgPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isSimple) Color.parseColor("#F8FAFC") else Color.parseColor("#F1F5F9")
      }
    val borderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isTally) Color.parseColor("#334155") else Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeWidth = if (isTally) 1.0f else 0.9f
      }
    val whiteTitlePaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 17f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val whiteSubPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = 9f
      }
    val titleDarkPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val headingPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = 10f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val bodyPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        textSize = 9f
      }
    val mutedPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        textSize = 8f
      }
    val rightBodyPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
    val rightBoldPaint =
      Paint(headingPaint).apply {
        textSize = 9.5f
        textAlign = Paint.Align.RIGHT
      }

    val sellerName = profile?.businessName?.ifBlank { null } ?: "Book"
    val sellerAddressLine = buildAddressLine(profile?.address.orEmpty(), profile?.state.orEmpty(), profile?.pincode.orEmpty())
    val buyerAddressLine = buildAddressLine(invoice.partyAddress, invoice.partyState, invoice.partyPincode)
    val badgeLabel =
      when {
        isPlainNonGst -> "PLAIN BILL"
        invoice.invoiceType == GstInvoice.TYPE_POS_BILL -> "POS BILL"
        else -> "TAX INVOICE"
      }
    val dateStr =
      SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        .format(invoice.createdAt?.toDate() ?: Date())

    var pageNumber = 1
    var page = pdf.startPage(PdfDocument.PageInfo.Builder(targetWidth, targetHeight, pageNumber).create())
    var canvas = page.canvas
    canvas.save()
    canvas.scale(scale, scale)

    var y = 0f
    if (isTally) {
      // Classic Tally ERP outer border
      canvas.drawRect(MARGIN_X, 26f, CONTENT_RIGHT, A4_HEIGHT - 26f, borderPaint)

      val centeredTitlePaint = Paint(titleDarkPaint).apply { textAlign = Paint.Align.CENTER }
      val centeredMutedPaint = Paint(mutedPaint).apply { textAlign = Paint.Align.CENTER }
      canvas.drawText(badgeLabel, A4_WIDTH / 2f, 44f, centeredTitlePaint)
      canvas.drawText("(Original for Recipient)", A4_WIDTH / 2f, 56f, centeredMutedPaint)

      y = 64f
      canvas.drawLine(MARGIN_X, y, CONTENT_RIGHT, y, borderPaint)

      val midX = A4_WIDTH / 2f + 16f
      val headerBoxHeight = if (options.showAddressAndState) 82f else 62f
      canvas.drawLine(midX, y, midX, y + headerBoxHeight, borderPaint)

      // Seller column (Left)
      canvas.drawText(sellerName.take(36), MARGIN_X + 8f, y + 16f, headingPaint)
      var sy = y + 29f
      if (options.showAddressAndState && sellerAddressLine.isNotBlank()) {
        canvas.drawText(sellerAddressLine.take(48), MARGIN_X + 8f, sy, bodyPaint)
        sy += 13f
      }
      if (!profile?.state.isNullOrBlank()) {
        val code = IndiaStates.findByNameOrCode(profile?.state.orEmpty())?.gstCode
        val stateLabel = if (code != null) "State: ${profile?.state} ($code)" else "State: ${profile?.state}"
        canvas.drawText(stateLabel.take(44), MARGIN_X + 8f, sy, bodyPaint)
        sy += 13f
      }
      if (!isPlainNonGst && !profile?.gstin.isNullOrBlank()) {
        canvas.drawText("GSTIN/UIN: ${profile?.gstin}", MARGIN_X + 8f, sy, bodyPaint)
      } else if (!profile?.ownerName.isNullOrBlank()) {
        canvas.drawText("Prop: ${profile?.ownerName}", MARGIN_X + 8f, sy, mutedPaint)
      }

      // Metadata grid (Right)
      canvas.drawText("Invoice No.: ${invoice.invoiceNumber}", midX + 8f, y + 16f, headingPaint)
      canvas.drawText("Dated: $dateStr", midX + 8f, y + 30f, bodyPaint)
      canvas.drawText("Pay Mode: ${invoice.paymentMethod.uppercase()}", midX + 8f, y + 44f, bodyPaint)
      canvas.drawText("Status: ${invoice.status.uppercase()}", midX + 8f, y + 58f, bodyPaint)
      if (!isPlainNonGst) {
        canvas.drawText("Tax: ${formatTaxModeLabel(invoice.taxMode)}", midX + 8f, y + 72f, mutedPaint)
      }

      y += headerBoxHeight
      canvas.drawLine(MARGIN_X, y, CONTENT_RIGHT, y, borderPaint)

      // Buyer block
      val buyerBoxHeight = if (options.showAddressAndState && buyerAddressLine.isNotBlank()) 54f else 42f
      canvas.drawText("Buyer (Bill To):", MARGIN_X + 8f, y + 14f, mutedPaint)
      canvas.drawText(invoice.partyName.take(42), MARGIN_X + 8f, y + 27f, headingPaint)
      var by = y + 39f
      if (options.showAddressAndState && buyerAddressLine.isNotBlank()) {
        canvas.drawText(buyerAddressLine.take(65), MARGIN_X + 8f, by, bodyPaint)
        by += 12f
      }
      val rightBuyerInfo =
        if (isPlainNonGst) "Billing Mode: Plain Non-GST"
        else if (invoice.partyGstin.isNotBlank()) "GSTIN: ${invoice.partyGstin}"
        else "Unregistered / Consumer"
      canvas.drawText(rightBuyerInfo, CONTENT_RIGHT - 8f, y + 27f, rightBodyPaint)
      if (!invoice.partyState.isNullOrBlank()) {
        val code = IndiaStates.findByNameOrCode(invoice.partyState)?.gstCode
        val st = if (code != null) "Place of Supply: ${invoice.partyState} ($code)" else "State: ${invoice.partyState}"
        canvas.drawText(st, CONTENT_RIGHT - 8f, y + 39f, Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT })
      }
      y += buyerBoxHeight
    } else if (isSimple) {
      // Minimal ink-saving Simple Bill header
      y = 32f
      canvas.drawText(sellerName.take(40), MARGIN_X, y + 14f, titleDarkPaint)
      canvas.drawText(badgeLabel, CONTENT_RIGHT, y + 14f, Paint(titleDarkPaint).apply { textAlign = Paint.Align.RIGHT })
      y += 28f
      if (options.showAddressAndState && sellerAddressLine.isNotBlank()) {
        canvas.drawText(sellerAddressLine.take(75), MARGIN_X, y, mutedPaint)
        y += 12f
      }
      val sellerMeta =
        buildList {
            if (!isPlainNonGst && !profile?.gstin.isNullOrBlank()) add("GSTIN: ${profile?.gstin}")
            if (!profile?.ownerName.isNullOrBlank()) add("Prop: ${profile?.ownerName}")
          }
          .joinToString("  •  ")
      if (sellerMeta.isNotBlank()) {
        canvas.drawText(sellerMeta, MARGIN_X, y, bodyPaint)
        y += 12f
      }
      canvas.drawLine(MARGIN_X, y + 4f, CONTENT_RIGHT, y + 4f, borderPaint)
      y += 18f

      canvas.drawText("Bill To: ${invoice.partyName.take(36)}", MARGIN_X, y, headingPaint)
      canvas.drawText("# ${invoice.invoiceNumber}", CONTENT_RIGHT, y, rightBoldPaint)
      y += 13f
      if (options.showAddressAndState && buyerAddressLine.isNotBlank()) {
        canvas.drawText(buyerAddressLine.take(52), MARGIN_X, y, bodyPaint)
      } else {
        canvas.drawText(
          if (!isPlainNonGst && invoice.partyGstin.isNotBlank()) "GSTIN: ${invoice.partyGstin}" else "Customer",
          MARGIN_X,
          y,
          mutedPaint,
        )
      }
      canvas.drawText("Date: $dateStr", CONTENT_RIGHT, y, rightBodyPaint)
      y += 13f
      if (!isPlainNonGst && invoice.partyGstin.isNotBlank() && buyerAddressLine.isNotBlank()) {
        canvas.drawText("GSTIN: ${invoice.partyGstin} • ${formatTaxModeLabel(invoice.taxMode)}", MARGIN_X, y, mutedPaint)
      }
      canvas.drawText("Pay: ${invoice.paymentMethod.uppercase()} (${invoice.status.uppercase()})", CONTENT_RIGHT, y, rightBodyPaint)
      y += 16f
    } else {
      // MODERN Executive Header Banner
      canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), 102f, primaryBannerPaint)
      canvas.drawText(sellerName.take(34), MARGIN_X, 34f, whiteTitlePaint)
      val sellerSub =
        buildList {
            if (!isPlainNonGst && !profile?.gstin.isNullOrBlank()) add("GSTIN: ${profile?.gstin}")
            if (!profile?.businessType.isNullOrBlank()) add(profile!!.businessType)
            if (!profile?.fiscalYearLabel.isNullOrBlank()) add(profile!!.fiscalYearLabel)
          }
          .joinToString("  •  ")
      if (sellerSub.isNotBlank()) {
        canvas.drawText(sellerSub, MARGIN_X, 52f, whiteSubPaint)
      }
      if (options.showAddressAndState && sellerAddressLine.isNotBlank()) {
        canvas.drawText(sellerAddressLine.take(68), MARGIN_X, 68f, whiteSubPaint)
      } else if (!profile?.ownerName.isNullOrBlank()) {
        canvas.drawText("Proprietor: ${profile?.ownerName}", MARGIN_X, 68f, whiteSubPaint)
      }
      if (!profile?.ownerName.isNullOrBlank() && options.showAddressAndState && sellerAddressLine.isNotBlank()) {
        canvas.drawText("Proprietor: ${profile?.ownerName}", MARGIN_X, 84f, whiteSubPaint)
      }

      val badgeRect = RectF(CONTENT_RIGHT - 108f, 22f, CONTENT_RIGHT, 50f)
      canvas.drawRoundRect(badgeRect, 6f, 6f, emeraldPaint)
      val badgePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          textSize = 10.5f
          textAlign = Paint.Align.CENTER
          typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
      canvas.drawText(badgeLabel, badgeRect.centerX(), badgeRect.centerY() + 4f, badgePaint)

      y = 116f
      val boxHeight = if (options.showAddressAndState && buyerAddressLine.isNotBlank()) 82f else 72f
      val leftBox = RectF(MARGIN_X, y, A4_WIDTH / 2f - 6f, y + boxHeight)
      val rightBox = RectF(A4_WIDTH / 2f + 6f, y, CONTENT_RIGHT, y + boxHeight)
      canvas.drawRoundRect(leftBox, 6f, 6f, lightBgPaint)
      canvas.drawRoundRect(rightBox, 6f, 6f, lightBgPaint)

      canvas.drawText("BILL TO", leftBox.left + 12f, leftBox.top + 16f, mutedPaint)
      canvas.drawText(invoice.partyName.take(32), leftBox.left + 12f, leftBox.top + 32f, headingPaint)
      var lby = leftBox.top + 46f
      if (options.showAddressAndState && buyerAddressLine.isNotBlank()) {
        canvas.drawText(buyerAddressLine.take(40), leftBox.left + 12f, lby, bodyPaint)
        lby += 13f
      }
      if (isPlainNonGst) {
        canvas.drawText("Plain Non-GST Counter Bill", leftBox.left + 12f, lby, mutedPaint)
      } else {
        canvas.drawText(
          if (invoice.partyGstin.isNotBlank()) "GSTIN: ${invoice.partyGstin}" else "Unregistered / Walk-in",
          leftBox.left + 12f,
          lby,
          mutedPaint,
        )
      }

      canvas.drawText("INVOICE DETAILS", rightBox.left + 12f, rightBox.top + 16f, mutedPaint)
      canvas.drawText("# ${invoice.invoiceNumber}", rightBox.left + 12f, rightBox.top + 32f, headingPaint)
      canvas.drawText("Date: $dateStr", rightBox.left + 12f, rightBox.top + 46f, bodyPaint)
      canvas.drawText(
        "Status: ${invoice.status.uppercase()} (${invoice.paymentMethod.uppercase()})",
        rightBox.left + 12f,
        rightBox.top + 60f,
        bodyPaint,
      )
      y += boxHeight + 12f
    }

    // Column X coordinates (595 A4 coordinate space)
    val colSrX = MARGIN_X + 6f
    val colNameX = MARGIN_X + 26f
    val colHsnRight = if (effectiveShowHsn) MARGIN_X + 248f else MARGIN_X + 248f
    val colQtyRight = if (isPlainNonGst) MARGIN_X + 335f else if (effectiveShowHsn) MARGIN_X + 312f else MARGIN_X + 290f
    val colRateRight = if (isPlainNonGst) MARGIN_X + 420f else MARGIN_X + 378f
    val colGstRight = MARGIN_X + 425f

    fun drawTableHeader(c: Canvas, topY: Float): Float {
      val headerRect = RectF(MARGIN_X, topY, CONTENT_RIGHT, topY + 22f)
      if (isSimple || isTally) {
        c.drawRect(headerRect, lightBgPaint)
        c.drawRect(headerRect, borderPaint)
      } else {
        c.drawRect(headerRect, primaryBannerPaint)
      }
      val hPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = if (isSimple || isTally) Color.parseColor("#0F172A") else Color.WHITE
          textSize = 8.5f
          typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
      val hRightPaint = Paint(hPaint).apply { textAlign = Paint.Align.RIGHT }
      val baseline = topY + 14.5f
      c.drawText("#", colSrX, baseline, hPaint)
      c.drawText("Description of Goods", colNameX, baseline, hPaint)
      if (effectiveShowHsn) {
        c.drawText("HSN/SAC", colHsnRight, baseline, hRightPaint)
      }
      c.drawText("Qty", colQtyRight, baseline, hRightPaint)
      c.drawText("Rate", colRateRight, baseline, hRightPaint)
      if (!isPlainNonGst) {
        c.drawText("GST", colGstRight, baseline, hRightPaint)
      }
      c.drawText("Amount", CONTENT_RIGHT - 6f, baseline, hRightPaint)
      return topY + 22f
    }

    y = drawTableHeader(canvas, y)
    val pages = paginateLineItems(invoice.lineItems, startY = y)
    var globalIndex = 0

    pages.forEachIndexed { pageIdx, pageItems ->
      if (pageIdx > 0) {
        canvas.restore()
        pdf.finishPage(page)
        pageNumber++
        page = pdf.startPage(PdfDocument.PageInfo.Builder(targetWidth, targetHeight, pageNumber).create())
        canvas = page.canvas
        canvas.save()
        canvas.scale(scale, scale)
        if (isTally) {
          canvas.drawRect(MARGIN_X, 26f, CONTENT_RIGHT, A4_HEIGHT - 26f, borderPaint)
        }
        y = 44f
        canvas.drawText("${invoice.invoiceNumber} (Continued — Page $pageNumber)", MARGIN_X + 6f, y, headingPaint)
        y += 10f
        y = drawTableHeader(canvas, y)
      }

      pageItems.forEach { item ->
        val rowHeight = if (item.sku.isNotBlank()) 28f else 22f
        if (!isTally && !isSimple && globalIndex % 2 == 1) {
          canvas.drawRect(MARGIN_X, y, CONTENT_RIGHT, y + rowHeight, lightBgPaint)
        }
        if (isTally) {
          canvas.drawRect(MARGIN_X, y, CONTENT_RIGHT, y + rowHeight, borderPaint)
        } else {
          canvas.drawLine(MARGIN_X, y + rowHeight, CONTENT_RIGHT, y + rowHeight, borderPaint)
        }

        val lineY = y + 14f
        canvas.drawText("${globalIndex + 1}", colSrX, lineY, bodyPaint)
        val maxNameChars = if (effectiveShowHsn) 27 else 34
        canvas.drawText(item.name.take(maxNameChars), colNameX, lineY, bodyPaint)
        if (item.sku.isNotBlank()) {
          canvas.drawText("SKU: ${item.sku.take(24)}", colNameX, lineY + 10f, mutedPaint)
        }
        if (effectiveShowHsn) {
          canvas.drawText(item.hsnCode.ifBlank { "-" }.take(10), colHsnRight, lineY, rightBodyPaint)
        }
        canvas.drawText("${formatQty(item.qty)} ${item.unit}", colQtyRight, lineY, rightBodyPaint)
        canvas.drawText(formatPdfMoney(currency, item.unitPrice), colRateRight, lineY, rightBodyPaint)
        if (!isPlainNonGst) {
          canvas.drawText("${item.gstRatePercent.toInt()}%", colGstRight, lineY, rightBodyPaint)
        }
        canvas.drawText(formatPdfMoney(currency, item.lineTotal), CONTENT_RIGHT - 6f, lineY, rightBoldPaint)
        y += rowHeight
        globalIndex++
      }
    }

    if (y > A4_HEIGHT - 205f) {
      canvas.restore()
      pdf.finishPage(page)
      pageNumber++
      page = pdf.startPage(PdfDocument.PageInfo.Builder(targetWidth, targetHeight, pageNumber).create())
      canvas = page.canvas
      canvas.save()
      canvas.scale(scale, scale)
      if (isTally) {
        canvas.drawRect(MARGIN_X, 26f, CONTENT_RIGHT, A4_HEIGHT - 26f, borderPaint)
      }
      y = 44f
    }

    y += 12f
    val summaryLeft = A4_WIDTH - 246f
    val summaryHeight = 120f
    val summaryRect = RectF(summaryLeft, y, CONTENT_RIGHT, y + summaryHeight)
    if (!isSimple) {
      canvas.drawRoundRect(summaryRect, if (isTally) 0f else 6f, if (isTally) 0f else 6f, lightBgPaint)
    }
    canvas.drawRoundRect(summaryRect, if (isTally) 0f else 6f, if (isTally) 0f else 6f, borderPaint)

    var sy = y + 18f
    canvas.drawText(if (isPlainNonGst) "Item Subtotal" else "Taxable Subtotal", summaryLeft + 10f, sy, bodyPaint)
    canvas.drawText(formatPdfMoney(currency, invoice.subtotal), CONTENT_RIGHT - 10f, sy, rightBodyPaint)

    if (!isPlainNonGst) {
      sy += 16f
      when (invoice.taxMode) {
        GstInvoice.TAX_MODE_CGST_SGST -> {
          canvas.drawText("CGST + SGST", summaryLeft + 10f, sy, bodyPaint)
          canvas.drawText(
            "${formatPdfMoney(currency, invoice.gstTotal / 2)} + ${formatPdfMoney(currency, invoice.gstTotal / 2)}",
            CONTENT_RIGHT - 10f,
            sy,
            rightBodyPaint,
          )
        }
        GstInvoice.TAX_MODE_IGST -> {
          canvas.drawText("IGST", summaryLeft + 10f, sy, bodyPaint)
          canvas.drawText(formatPdfMoney(currency, invoice.gstTotal), CONTENT_RIGHT - 10f, sy, rightBodyPaint)
        }
        else -> {
          canvas.drawText("GST", summaryLeft + 10f, sy, bodyPaint)
          canvas.drawText(formatPdfMoney(currency, 0.0), CONTENT_RIGHT - 10f, sy, rightBodyPaint)
        }
      }
    }

    if (invoice.discountAmount > 0.0) {
      sy += 16f
      canvas.drawText("Discount", summaryLeft + 10f, sy, bodyPaint)
      canvas.drawText("- ${formatPdfMoney(currency, invoice.discountAmount)}", CONTENT_RIGHT - 10f, sy, rightBodyPaint)
    }

    sy += 9f
    canvas.drawLine(summaryLeft + 8f, sy, CONTENT_RIGHT - 8f, sy, borderPaint)
    sy += 16f
    canvas.drawText("Grand Total", summaryLeft + 10f, sy, headingPaint)
    canvas.drawText(formatPdfMoney(currency, invoice.grandTotal), CONTENT_RIGHT - 10f, sy, rightBoldPaint)

    sy += 16f
    canvas.drawText("Paid Amount", summaryLeft + 10f, sy, bodyPaint)
    canvas.drawText(formatPdfMoney(currency, invoice.paidAmount), CONTENT_RIGHT - 10f, sy, rightBodyPaint)

    sy += 15f
    canvas.drawText("Balance Due", summaryLeft + 10f, sy, headingPaint)
    canvas.drawText(formatPdfMoney(currency, invoice.balanceDue), CONTENT_RIGHT - 10f, sy, rightBoldPaint)

    // Left column beside summary: Amount in words, notes, terms
    var leftNoteY = y + 16f
    if (options.showAmountInWords) {
      canvas.drawText("Amount Chargeable (in words):", MARGIN_X + 6f, leftNoteY, mutedPaint)
      leftNoteY += 12f
      val words = amountInWords(invoice.grandTotal)
      wrapLines(words, 46).take(2).forEach { line ->
        canvas.drawText(line, MARGIN_X + 6f, leftNoteY, headingPaint)
        leftNoteY += 12f
      }
      leftNoteY += 6f
    }
    if (invoice.notes.isNotBlank()) {
      canvas.drawText("Notes:", MARGIN_X + 6f, leftNoteY, mutedPaint)
      leftNoteY += 12f
      wrapLines(invoice.notes, 46).take(2).forEach { line ->
        canvas.drawText(line, MARGIN_X + 6f, leftNoteY, bodyPaint)
        leftNoteY += 11f
      }
    }

    // GST Portal E-Invoice (IRN + Signed QR) & E-Way Bill + Transport Box
    if (!isPlainNonGst && (invoice.hasEInvoice || invoice.hasEWayBill)) {
      val gstBoxTop = leftNoteY + 4f
      val gstBoxRight = summaryLeft - 10f
      val gstBoxBottom = (gstBoxTop + 92f).coerceAtMost(A4_HEIGHT - 64f)
      if (gstBoxBottom - gstBoxTop >= 52f) {
        val gstBoxRect = RectF(MARGIN_X + 4f, gstBoxTop, gstBoxRight, gstBoxBottom)
        canvas.drawRoundRect(gstBoxRect, 5f, 5f, lightBgPaint)
        canvas.drawRoundRect(gstBoxRect, 5f, 5f, borderPaint)

        var textLeft = gstBoxRect.left + 8f
        if (invoice.hasEInvoice) {
          val qrSize = (gstBoxBottom - gstBoxTop - 14f).coerceIn(40f, 68f)
          drawPdfQrMatrix(
            canvas = canvas,
            left = gstBoxRect.left + 7f,
            top = gstBoxTop + 7f,
            boxSize = qrSize,
            payload = invoice.signedQrData.ifBlank { invoice.irn },
          )
          textLeft = gstBoxRect.left + qrSize + 14f
        }

        var gy = gstBoxTop + 14f
        if (invoice.hasEInvoice) {
          canvas.drawText("GST E-INVOICE (IRN VERIFIED)", textLeft, gy, headingPaint)
          gy += 11f
          canvas.drawText("Ack No: ${invoice.ackNo}  •  Dt: ${invoice.ackDate.take(16)}", textLeft, gy, bodyPaint)
          gy += 11f
          canvas.drawText("IRN: ${invoice.irn.take(32)}", textLeft, gy, mutedPaint)
          if (invoice.irn.length > 32) {
            gy += 9f
            canvas.drawText(invoice.irn.substring(32).take(32), textLeft, gy, mutedPaint)
          }
          gy += 12f
        }
        if (invoice.hasEWayBill && gy < gstBoxBottom - 6f) {
          canvas.drawText(
            "E-Way Bill No: ${invoice.ewbNumber} (${invoice.transportMode.uppercase()} • ${invoice.distanceKm} KM)",
            textLeft,
            gy,
            headingPaint,
          )
          gy += 11f
          val routeLine =
            "${invoice.fromPlace} (${invoice.fromPincode}) -> ${invoice.toPlace} (${invoice.toPincode}) | Veh: ${invoice.vehicleNumber.ifBlank { "N/A" }}"
          canvas.drawText(routeLine.take(44), textLeft, gy, bodyPaint)
        }
        leftNoteY = gstBoxBottom + 4f
      }
    }

    if (options.termsText.isNotBlank()) {
      val termsY = maxOf(leftNoteY + 8f, y + summaryHeight + 14f)
      if (termsY < A4_HEIGHT - 56f) {
        canvas.drawText("Declaration / Terms:", MARGIN_X + 6f, termsY, mutedPaint)
        canvas.drawText(options.termsText.take(92), MARGIN_X + 6f, termsY + 12f, mutedPaint)
      }
    }

    // Footer
    val footerLineY = A4_HEIGHT - 48f
    canvas.drawLine(MARGIN_X, footerLineY, CONTENT_RIGHT, footerLineY, borderPaint)
    val footerMsg = options.footerNote.ifBlank { "Generated by Book" }
    canvas.drawText(footerMsg.take(64), MARGIN_X + 6f, A4_HEIGHT - 32f, mutedPaint)
    canvas.drawText(
      "For ${sellerName.take(26)} • Authorized Signatory",
      CONTENT_RIGHT - 6f,
      A4_HEIGHT - 32f,
      Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT },
    )

    canvas.restore()
    pdf.finishPage(page)
    return pdf
  }

  private fun buildThermalRollPdf(
    invoice: GstInvoice,
    profile: BusinessProfile?,
    currency: String,
    options: InvoicePrintOptions,
    isPlainNonGst: Boolean,
    effectiveShowHsn: Boolean,
  ): PdfDocument {
    val width = options.paperSize.widthPt
    val lineCount = invoice.lineItems.size.coerceAtLeast(1)
    val einvoiceExtra = if (!isPlainNonGst && (invoice.hasEInvoice || invoice.hasEWayBill)) 85 else 0
    val estimatedHeight =
      (290 + lineCount * 36 + if (options.showAddressAndState) 30 else 0 + einvoiceExtra).coerceAtLeast(360)
    val pdf = PdfDocument()
    val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, estimatedHeight, 1).create())
    drawThermalContent(
      canvas = page.canvas,
      originX = 0f,
      receiptWidth = width.toFloat(),
      invoice = invoice,
      profile = profile,
      currency = currency,
      options = options,
      isPlainNonGst = isPlainNonGst,
      effectiveShowHsn = effectiveShowHsn,
    )
    pdf.finishPage(page)
    return pdf
  }

  private fun buildThermalStripOnSheetPdf(
    invoice: GstInvoice,
    profile: BusinessProfile?,
    currency: String,
    options: InvoicePrintOptions,
    isPlainNonGst: Boolean,
    effectiveShowHsn: Boolean,
  ): PdfDocument {
    val width = options.paperSize.widthPt
    val height = options.paperSize.heightPt
    val stripWidth = 226f
    val originX = (width - stripWidth) / 2f
    val pdf = PdfDocument()
    val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
    val border =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
      }
    page.canvas.drawRect(originX - 4f, 16f, originX + stripWidth + 4f, height - 16f, border)
    drawThermalContent(
      canvas = page.canvas,
      originX = originX,
      receiptWidth = stripWidth,
      invoice = invoice,
      profile = profile,
      currency = currency,
      options = options,
      isPlainNonGst = isPlainNonGst,
      effectiveShowHsn = effectiveShowHsn,
      startY = 30f,
    )
    pdf.finishPage(page)
    return pdf
  }

  private fun drawThermalContent(
    canvas: Canvas,
    originX: Float,
    receiptWidth: Float,
    invoice: GstInvoice,
    profile: BusinessProfile?,
    currency: String,
    options: InvoicePrintOptions,
    isPlainNonGst: Boolean,
    effectiveShowHsn: Boolean,
    startY: Float = 20f,
  ) {
    val isMini58 = receiptWidth <= 175f
    val pad = if (isMini58) 6f else 8f
    val left = originX + pad
    val right = originX + receiptWidth - pad
    val centerX = originX + receiptWidth / 2f

    val boldCenter =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = if (isMini58) 9.5f else 11f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
      }
    val regularCenter =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = if (isMini58) 7f else 8f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
      }
    val regularLeft =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = if (isMini58) 7f else 8f
        typeface = Typeface.MONOSPACE
      }
    val boldLeft =
      Paint(regularLeft).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
      }
    val regularRight = Paint(regularLeft).apply { textAlign = Paint.Align.RIGHT }
    val boldRight = Paint(boldLeft).apply { textAlign = Paint.Align.RIGHT }
    val dashPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        pathEffect = DashPathEffect(floatArrayOf(3f, 2f), 0f)
      }

    var y = startY
    val sellerName = profile?.businessName?.ifBlank { null } ?: "Book"
    canvas.drawText(sellerName.take(if (isMini58) 24 else 30), centerX, y, boldCenter)
    y += 11f

    if (options.showAddressAndState) {
      val addr = buildAddressLine(profile?.address.orEmpty(), profile?.state.orEmpty(), profile?.pincode.orEmpty())
      if (addr.isNotBlank()) {
        wrapLines(addr, if (isMini58) 26 else 34).take(2).forEach { line ->
          canvas.drawText(line, centerX, y, regularCenter)
          y += 10f
        }
      }
    }
    if (!isPlainNonGst && !profile?.gstin.isNullOrBlank()) {
      canvas.drawText("GSTIN: ${profile?.gstin}", centerX, y, regularCenter)
      y += 10f
    }

    canvas.drawLine(left, y, right, y, dashPaint)
    y += 11f

    val billTitle = if (isPlainNonGst) "PLAIN CASH BILL" else "TAX INVOICE RECEIPT"
    canvas.drawText(billTitle, centerX, y, boldCenter)
    y += 11f

    val dateStr =
      SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault())
        .format(invoice.createdAt?.toDate() ?: Date())
    canvas.drawText("No: ${invoice.invoiceNumber}", left, y, boldLeft)
    canvas.drawText(dateStr, right, y, regularRight)
    y += 10f
    canvas.drawText("To: ${invoice.partyName.take(if (isMini58) 22 else 30)}", left, y, regularLeft)
    y += 10f
    if (!isPlainNonGst && invoice.partyGstin.isNotBlank()) {
      canvas.drawText("GSTIN: ${invoice.partyGstin}", left, y, regularLeft)
      y += 10f
    }

    canvas.drawLine(left, y, right, y, dashPaint)
    y += 10f
    canvas.drawText("ITEM / QTY x RATE", left, y, boldLeft)
    canvas.drawText("AMT", right, y, boldRight)
    y += 5f
    canvas.drawLine(left, y, right, y, dashPaint)
    y += 10f

    invoice.lineItems.forEach { item ->
      canvas.drawText(item.name.take(if (isMini58) 24 else 32), left, y, boldLeft)
      y += 10f
      val qtyRate =
        buildString {
          append("${formatQty(item.qty)} ${item.unit} x ${formatCompactMoney(item.unitPrice)}")
          if (effectiveShowHsn && item.hsnCode.isNotBlank()) {
            append(" [${item.hsnCode}]")
          } else if (!isPlainNonGst && item.gstRatePercent > 0) {
            append(" (${item.gstRatePercent.toInt()}%)")
          }
        }
      canvas.drawText(qtyRate.take(if (isMini58) 22 else 28), left, y, regularLeft)
      canvas.drawText(formatPdfMoney(currency, item.lineTotal), right, y, boldRight)
      y += 12f
    }

    canvas.drawLine(left, y, right, y, dashPaint)
    y += 11f

    canvas.drawText("Subtotal", left, y, regularLeft)
    canvas.drawText(formatPdfMoney(currency, invoice.subtotal), right, y, regularRight)
    y += 10f

    if (!isPlainNonGst && invoice.gstTotal > 0.0) {
      val taxLbl = if (invoice.taxMode == GstInvoice.TAX_MODE_IGST) "IGST" else "CGST+SGST"
      canvas.drawText(taxLbl, left, y, regularLeft)
      canvas.drawText(formatPdfMoney(currency, invoice.gstTotal), right, y, regularRight)
      y += 10f
    }
    if (invoice.discountAmount > 0.0) {
      canvas.drawText("Discount", left, y, regularLeft)
      canvas.drawText("-${formatPdfMoney(currency, invoice.discountAmount)}", right, y, regularRight)
      y += 10f
    }

    canvas.drawLine(left, y, right, y, dashPaint)
    y += 12f
    canvas.drawText("TOTAL", left, y, boldLeft)
    canvas.drawText(formatPdfMoney(currency, invoice.grandTotal), right, y, boldRight)
    y += 11f
    canvas.drawText("Paid (${invoice.paymentMethod.uppercase()})", left, y, regularLeft)
    canvas.drawText(formatPdfMoney(currency, invoice.paidAmount), right, y, regularRight)
    y += 10f
    if (invoice.balanceDue > 0.01) {
      canvas.drawText("Balance Due", left, y, boldLeft)
      canvas.drawText(formatPdfMoney(currency, invoice.balanceDue), right, y, boldRight)
      y += 10f
    }

    if (!isPlainNonGst && (invoice.hasEInvoice || invoice.hasEWayBill)) {
      canvas.drawLine(left, y, right, y, dashPaint)
      y += 10f
      if (invoice.hasEInvoice) {
        canvas.drawText("E-INVOICE Ack: ${invoice.ackNo}", left, y, boldLeft)
        y += 9f
        canvas.drawText("IRN: ${invoice.irn.take(24)}...", left, y, regularLeft)
        y += 10f
      }
      if (invoice.hasEWayBill) {
        canvas.drawText("EWB: ${invoice.ewbNumber} (${invoice.distanceKm}KM)", left, y, boldLeft)
        y += 9f
        canvas.drawText(
          "${invoice.fromPlace.take(10)}->${invoice.toPlace.take(10)} ${invoice.vehicleNumber}",
          left,
          y,
          regularLeft,
        )
        y += 10f
      }
    }

    canvas.drawLine(left, y, right, y, dashPaint)
    y += 12f
    val footer = options.footerNote.ifBlank { "Thank you! Visit again." }
    wrapLines(footer, if (isMini58) 24 else 32).take(2).forEach { line ->
      canvas.drawText(line, centerX, y, regularCenter)
      y += 10f
    }
  }

  private fun drawPdfQrMatrix(
    canvas: Canvas,
    left: Float,
    top: Float,
    boxSize: Float,
    payload: String,
  ) {
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }
    canvas.drawRect(left, top, left + boxSize, top + boxSize, whitePaint)
    val gridSize = 21
    val cell = boxSize / gridSize
    val bytes = payload.encodeToByteArray()

    fun finderOn(r: Int, c: Int): Boolean {
      if (r == 0 || r == 6 || c == 0 || c == 6) return true
      if (r in 2..4 && c in 2..4) return true
      return false
    }

    for (r in 0 until gridSize) {
      for (c in 0 until gridSize) {
        val on =
          when {
            r in 0..6 && c in 0..6 -> finderOn(r, c)
            r in 0..6 && c in (gridSize - 7) until gridSize -> finderOn(r, c - (gridSize - 7))
            r in (gridSize - 7) until gridSize && c in 0..6 -> finderOn(r - (gridSize - 7), c)
            r == 6 || c == 6 -> (r + c) % 2 == 0
            else -> {
              val idx = r * gridSize + c
              val b = if (bytes.isNotEmpty()) bytes[idx % bytes.size].toInt() else idx
              ((b xor (r * 31 + c * 17)) and 1) == 0
            }
          }
        if (on) {
          val cl = left + c * cell
          val ct = top + r * cell
          canvas.drawRect(cl, ct, cl + cell * 0.94f, ct + cell * 0.94f, darkPaint)
        }
      }
    }
  }

  fun writeInvoicePdfToStream(
    invoice: GstInvoice,
    profile: BusinessProfile?,
    currency: String,
    outputStream: OutputStream,
    options: InvoicePrintOptions = InvoicePrintOptions(),
  ) {
    val pdf = buildInvoicePdfDocument(invoice, profile, currency, options)
    try {
      pdf.writeTo(outputStream)
      outputStream.flush()
    } finally {
      pdf.close()
    }
  }

  fun printInvoice(
    context: Context,
    invoice: GstInvoice,
    profile: BusinessProfile?,
    currency: String,
    options: InvoicePrintOptions = InvoicePrintOptions(),
  ) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
      ?: error("Print service unavailable on this device")

    val jobName = "Book_${options.paperSize.key}_${invoice.invoiceNumber}"
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
            PrintDocumentInfo.Builder("${invoice.invoiceNumber}.pdf")
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
          val pdf = buildInvoicePdfDocument(invoice, profile, currency, options)
          try {
            if (cancellationSignal?.isCanceled == true) {
              callback.onWriteCancelled()
              return
            }
            FileOutputStream(destination.fileDescriptor).use { out ->
              pdf.writeTo(out)
              out.flush()
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
          } catch (e: Exception) {
            callback.onWriteFailed(e.localizedMessage ?: "Failed to print invoice")
          } finally {
            pdf.close()
          }
        }
      }

    val mediaSize =
      when (options.paperSize) {
        PrintPaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4
        PrintPaperSize.A5 -> PrintAttributes.MediaSize.ISO_A5
        PrintPaperSize.THERMAL_80MM ->
          PrintAttributes.MediaSize("FINBOSS_THERMAL_80MM", "Thermal 80mm (3 in)", 3150, 11000)
        PrintPaperSize.THERMAL_58MM ->
          PrintAttributes.MediaSize("FINBOSS_THERMAL_58MM", "Thermal 58mm (2 in)", 2280, 11000)
      }

    val colorMode =
      if (options.paperSize.isThermal || options.style == InvoicePrintStyle.SIMPLE) {
        PrintAttributes.COLOR_MODE_MONOCHROME
      } else {
        PrintAttributes.COLOR_MODE_COLOR
      }

    val attrs =
      PrintAttributes.Builder()
        .setMediaSize(mediaSize)
        .setColorMode(colorMode)
        .build()

    printManager.print(jobName, adapter, attrs)
  }

  internal fun paginateLineItems(
    lineItems: List<InvoiceLineItem>,
    startY: Float = 234f,
  ): List<List<InvoiceLineItem>> {
    if (lineItems.isEmpty()) return listOf(emptyList())
    val pages = mutableListOf<List<InvoiceLineItem>>()
    var current = mutableListOf<InvoiceLineItem>()
    var simY = startY
    lineItems.forEach { item ->
      if (simY > A4_HEIGHT - 170f && current.isNotEmpty()) {
        pages.add(current)
        current = mutableListOf()
        simY = 76f
      }
      current.add(item)
      simY += if (item.sku.isNotBlank()) 28f else 22f
    }
    if (current.isNotEmpty()) pages.add(current)
    return pages
  }

  fun buildSafePdfFileName(invoiceNumber: String): String {
    val sanitized = invoiceNumber.trim().replace(Regex("[^A-Za-z0-9_\\-]"), "_").ifBlank { "invoice" }
    return "$sanitized.pdf"
  }

  internal fun formatTaxModeLabel(taxMode: String): String =
    when (taxMode) {
      GstInvoice.TAX_MODE_CGST_SGST -> "CGST + SGST"
      GstInvoice.TAX_MODE_IGST -> "IGST"
      else -> "No GST"
    }

  internal fun buildAddressLine(address: String, state: String, pincode: String): String =
    listOfNotNull(
        address.trim().takeIf { it.isNotBlank() },
        state.trim().takeIf { it.isNotBlank() },
        pincode.trim().takeIf { it.isNotBlank() }?.let { "PIN $it" },
      )
      .joinToString(", ")

  internal fun amountInWords(amount: Double): String {
    val rupees = amount.toLong().coerceAtLeast(0L)
    val paise = ((amount - rupees) * 100.0).roundToInt().coerceIn(0, 99)
    val rupeeWords = if (rupees == 0L) "Zero" else convertIndianNumber(rupees)
    return if (paise > 0) {
      "Rupees $rupeeWords and ${convertIndianNumber(paise.toLong())} Paise Only"
    } else {
      "Rupees $rupeeWords Only"
    }
  }

  private fun convertIndianNumber(n: Long): String {
    if (n == 0L) return ""
    val units =
      arrayOf(
        "",
        "One",
        "Two",
        "Three",
        "Four",
        "Five",
        "Six",
        "Seven",
        "Eight",
        "Nine",
        "Ten",
        "Eleven",
        "Twelve",
        "Thirteen",
        "Fourteen",
        "Fifteen",
        "Sixteen",
        "SevenTeen",
        "Eighteen",
        "Nineteen",
      ).also { it[17] = "Seventeen" }
    val tens =
      arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    fun twoDigits(num: Int): String =
      when {
        num < 20 -> units[num]
        num % 10 == 0 -> tens[num / 10]
        else -> "${tens[num / 10]} ${units[num % 10]}"
      }

    fun threeDigits(num: Int): String =
      when {
        num >= 100 -> {
          val h = units[num / 100]
          val rem = num % 100
          if (rem == 0) "$h Hundred" else "$h Hundred ${twoDigits(rem)}"
        }
        else -> twoDigits(num)
      }

    val parts = mutableListOf<String>()
    val crore = (n / 10_000_000L).toInt()
    val lakh = ((n / 100_000L) % 100L).toInt()
    val thousand = ((n / 1000L) % 100L).toInt()
    val remainder = (n % 1000L).toInt()

    if (crore > 0) parts.add("${twoDigits(crore)} Crore")
    if (lakh > 0) parts.add("${twoDigits(lakh)} Lakh")
    if (thousand > 0) parts.add("${twoDigits(thousand)} Thousand")
    if (remainder > 0) parts.add(threeDigits(remainder))
    return parts.joinToString(" ").trim()
  }

  private fun wrapLines(text: String, maxChars: Int): List<String> {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    words.forEach { w ->
      if (current.length + w.length + 1 > maxChars && current.isNotEmpty()) {
        lines.add(current.toString())
        current = StringBuilder(w)
      } else {
        if (current.isNotEmpty()) current.append(' ')
        current.append(w)
      }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines
  }

  private fun formatQty(qty: Double): String =
    if (qty % 1.0 == 0.0) qty.toInt().toString() else String.format(Locale.US, "%.2f", qty)

  private fun formatCompactMoney(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toLong().toString() else String.format(Locale.US, "%.2f", amount)

  internal fun formatPdfMoney(currency: String, amount: Double): String =
    "$currency ${String.format(Locale.US, "%,.2f", amount)}"
}
