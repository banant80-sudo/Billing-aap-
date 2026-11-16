package com.example.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.SubscriptionTier
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class SubscriptionPaymentReceipt(
  val receiptNumber: String,
  val paidAtMs: Long,
  val validUntilMs: Long,
  val tier: SubscriptionTier,
  val billingCycle: String,
  val amountPaidInr: Int,
  val taxableValueInr: Double,
  val gstAmountInr: Double,
  val paymentMethodLabel: String,
  val transactionRef: String,
  val merchantName: String,
  val merchantUpiId: String,
  val subscriberBusinessName: String,
  val subscriberEmail: String,
  val ccEmail: String? = null,
)

object SubscriptionReceiptPdfPrinter {
  const val DEFAULT_MERCHANT_BENEFICIARY = "Raghav Enterprises"
  const val DEFAULT_MERCHANT_UPI = "anantbharat@ybl"
  const val DEFAULT_SUBSCRIPTION_NOTIFICATION_EMAIL = "banant80@gmail.com"
  const val SUBSCRIPTION_SAC_CODE = "998314"

  private const val A4_WIDTH = 595
  private const val A4_HEIGHT = 842
  private const val MARGIN_X = 34f
  private const val CONTENT_RIGHT = A4_WIDTH - MARGIN_X

  fun buildUpiPaymentUri(
    merchantUpiId: String,
    beneficiaryName: String,
    amountInr: Int,
    planNote: String,
  ): String {
    val pa = urlEncodeComponent(merchantUpiId.trim().ifBlank { DEFAULT_MERCHANT_UPI })
    val pn = urlEncodeComponent(beneficiaryName.trim().ifBlank { DEFAULT_MERCHANT_BENEFICIARY })
    val tn = urlEncodeComponent(planNote.trim().ifBlank { "Finboss Subscription" })
    val safeAmount = amountInr.coerceAtLeast(1)
    return "upi://pay?pa=$pa&pn=$pn&am=$safeAmount&cu=INR&tn=$tn"
  }

  private fun urlEncodeComponent(raw: String): String =
    URLEncoder.encode(raw, StandardCharsets.UTF_8.name()).replace("+", "%20")

  fun generateQrBitmap(payload: String, sizePx: Int = 512): Bitmap? {
    if (payload.isBlank() || sizePx <= 0) return null
    return runCatching {
      val hints =
        mapOf(
          EncodeHintType.MARGIN to 1,
          EncodeHintType.CHARACTER_SET to "UTF-8",
        )
      val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val width = matrix.width
      val height = matrix.height
      val pixels = IntArray(width * height)
      for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
          pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
      }
      Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }.getOrNull()
  }

  fun isValidUpiUtr(utr: String): Boolean {
    val trimmed = utr.trim()
    return trimmed.matches(Regex("^[0-9]{12}$"))
  }

  fun isValidCardNumber(rawNumber: String): Boolean {
    val digits = rawNumber.filter { it.isDigit() }
    if (digits.length !in 13..19) return false
    if (digits.all { it == digits.first() }) return false
    var sum = 0
    var alternate = false
    for (i in digits.length - 1 downTo 0) {
      var n = digits[i] - '0'
      if (alternate) {
        n *= 2
        if (n > 9) n = (n % 10) + 1
      }
      sum += n
      alternate = !alternate
    }
    return sum % 10 == 0
  }

  fun isValidCardExpiry(
    rawExpiry: String,
    referenceCalendar: Calendar = Calendar.getInstance(),
  ): Boolean {
    val trimmed = rawExpiry.trim()
    if (!trimmed.matches(Regex("^(0[1-9]|1[0-2])/[0-9]{2}$"))) return false
    val parts = trimmed.split("/")
    val month = parts[0].toIntOrNull() ?: return false
    val yearTwoDigit = parts[1].toIntOrNull() ?: return false
    val currentYy = referenceCalendar.get(Calendar.YEAR) % 100
    val currentMm = referenceCalendar.get(Calendar.MONTH) + 1
    return yearTwoDigit > currentYy || (yearTwoDigit == currentYy && month >= currentMm)
  }

  fun isValidCardCvv(rawCvv: String): Boolean =
    rawCvv.trim().matches(Regex("^[0-9]{3,4}$"))

  fun isValidCardHolderName(rawName: String): Boolean {
    val cleaned = rawName.trim()
    return cleaned.length >= 2 && cleaned.any { it.isLetter() }
  }

  fun resolveReceiptRecipientEmails(signedInUserEmail: String?): Pair<String, String?> {
    val trimmed = signedInUserEmail?.trim().orEmpty()
    val hasValidEmail =
      trimmed.contains("@") && trimmed.substringAfter("@").contains(".") && !trimmed.contains(" ")
    val primary =
      if (hasValidEmail) trimmed else DEFAULT_SUBSCRIPTION_NOTIFICATION_EMAIL
    val cc =
      if (!primary.equals(DEFAULT_SUBSCRIPTION_NOTIFICATION_EMAIL, ignoreCase = true)) {
        DEFAULT_SUBSCRIPTION_NOTIFICATION_EMAIL
      } else {
        null
      }
    return primary to cc
  }

  fun createReceipt(
    tier: SubscriptionTier,
    billingCycle: String,
    amountPaidInr: Int,
    paymentMethodLabel: String,
    transactionRef: String,
    merchantName: String,
    merchantUpiId: String,
    subscriberBusinessName: String,
    signedInUserEmail: String?,
    nowMs: Long = System.currentTimeMillis(),
  ): SubscriptionPaymentReceipt {
    val isYearly = billingCycle.equals(SubscriptionTier.CYCLE_YEARLY, ignoreCase = true)
    val durationDays = if (isYearly) 365L else 30L
    val validUntilMs = nowMs + durationDays * 24L * 60L * 60L * 1000L
    val taxable = ((amountPaidInr / 1.18) * 100.0).roundToInt() / 100.0
    val gst = ((amountPaidInr - taxable) * 100.0).roundToInt() / 100.0
    val dayCode = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMs))
    val suffix = ((nowMs % 9000L) + 1000L).toInt()
    val (primaryEmail, ccEmail) = resolveReceiptRecipientEmails(signedInUserEmail)
    val safeMerchantName =
      merchantName.trim().ifBlank { DEFAULT_MERCHANT_BENEFICIARY }
    val safeMerchantUpi =
      merchantUpiId.trim().ifBlank { DEFAULT_MERCHANT_UPI }

    return SubscriptionPaymentReceipt(
      receiptNumber = "FB-SUB-$dayCode-$suffix",
      paidAtMs = nowMs,
      validUntilMs = validUntilMs,
      tier = tier,
      billingCycle = if (isYearly) SubscriptionTier.CYCLE_YEARLY else SubscriptionTier.CYCLE_MONTHLY,
      amountPaidInr = amountPaidInr,
      taxableValueInr = taxable,
      gstAmountInr = gst,
      paymentMethodLabel = paymentMethodLabel,
      transactionRef = transactionRef,
      merchantName = safeMerchantName,
      merchantUpiId = safeMerchantUpi,
      subscriberBusinessName = subscriberBusinessName.trim().ifBlank { "Finboss Enterprise" },
      subscriberEmail = primaryEmail,
      ccEmail = ccEmail,
    )
  }

  fun buildReceiptPdfDocument(receipt: SubscriptionPaymentReceipt): PdfDocument {
    val pdf = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
    val page = pdf.startPage(pageInfo)
    drawReceiptPage(page.canvas, receipt)
    pdf.finishPage(page)
    return pdf
  }

  fun writeReceiptPdfToStream(receipt: SubscriptionPaymentReceipt, outputStream: OutputStream) {
    val pdf = buildReceiptPdfDocument(receipt)
    try {
      pdf.writeTo(outputStream)
      outputStream.flush()
    } finally {
      pdf.close()
    }
  }

  fun saveReceiptPdfToCacheAndGetUri(
    context: Context,
    receipt: SubscriptionPaymentReceipt,
  ): Pair<File, Uri> {
    val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val safeFileName = "${receipt.receiptNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")}.pdf"
    val outFile = File(receiptsDir, safeFileName)
    FileOutputStream(outFile).use { stream ->
      writeReceiptPdfToStream(receipt, stream)
    }
    val uri =
      FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        outFile,
      )
    return outFile to uri
  }

  fun buildSubscriptionEmailIntent(
    context: Context,
    receipt: SubscriptionPaymentReceipt,
    pdfUri: Uri,
  ): Intent {
    val dateFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val validFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val cycleLabel = if (receipt.billingCycle == SubscriptionTier.CYCLE_YEARLY) "Yearly" else "Monthly"
    val inrFmt =
      NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
      }
    val body = buildString {
      appendLine("Congratulations! Your Finboss ${receipt.tier.displayName} ($cycleLabel) plan is now ACTIVE.")
      appendLine()
      appendLine("Receipt No.: ${receipt.receiptNumber}")
      appendLine("Subscriber: ${receipt.subscriberBusinessName}")
      appendLine("Plan Activated: ${receipt.tier.displayName} ($cycleLabel)")
      appendLine("Amount Paid (Incl. 18% GST): ${inrFmt.format(receipt.amountPaidInr)}")
      appendLine("Taxable Value: ${inrFmt.format(receipt.taxableValueInr)} | GST (18%): ${inrFmt.format(receipt.gstAmountInr)}")
      appendLine("Payment Method: ${receipt.paymentMethodLabel}")
      appendLine("Reference / UTR: ${receipt.transactionRef}")
      appendLine("Merchant: ${receipt.merchantName} (Verified UPI: ${receipt.merchantUpiId})")
      appendLine("Paid On: ${dateFmt.format(Date(receipt.paidAtMs))}")
      appendLine("Valid Until: ${validFmt.format(Date(receipt.validUntilMs))}")
      appendLine()
      appendLine("Your GST Subscription Tax Receipt PDF is attached to this email.")
      appendLine("Thank you for choosing Finboss Billing No.1 ERP!")
    }

    val sendIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(receipt.subscriberEmail))
        if (!receipt.ccEmail.isNullOrBlank()) {
          putExtra(Intent.EXTRA_CC, arrayOf(receipt.ccEmail))
        }
        putExtra(
          Intent.EXTRA_SUBJECT,
          "Congratulations! Your Finboss ${receipt.tier.displayName} Plan is Now Active (${receipt.receiptNumber})",
        )
        putExtra(Intent.EXTRA_TEXT, body)
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        clipData = ClipData.newUri(context.contentResolver, "Finboss GST Subscription Receipt", pdfUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    return Intent.createChooser(sendIntent, "Send Congratulations & Receipt PDF")
  }

  private fun drawReceiptPage(canvas: Canvas, receipt: SubscriptionPaymentReceipt) {
    val navyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B253C") }
    val emeraldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0D9488") }
    val emeraldTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#ECFDF5") }
    val lightBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
    val borderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
      }
    val whiteTitlePaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val whiteSubPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = 9.5f
      }
    val headingPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val bodyPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        textSize = 9.5f
      }
    val mutedPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        textSize = 8.5f
      }
    val rightBodyPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
    val rightBoldPaint = Paint(headingPaint).apply { textAlign = Paint.Align.RIGHT }

    // 1. Navy Header Banner
    canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), 96f, navyPaint)
    canvas.drawText("FINBOSS BILLING NO.1 ERP", MARGIN_X, 38f, whiteTitlePaint)
    canvas.drawText("GST SUBSCRIPTION TAX RECEIPT • CLOUD SAAS LICENSE", MARGIN_X, 58f, whiteSubPaint)
    canvas.drawText("SAC $SUBSCRIPTION_SAC_CODE • Verified Digital Tax Invoice", MARGIN_X, 74f, whiteSubPaint)

    val badgeRect = RectF(CONTENT_RIGHT - 122f, 28f, CONTENT_RIGHT, 58f)
    canvas.drawRoundRect(badgeRect, 6f, 6f, emeraldPaint)
    val badgePaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    canvas.drawText("PAID & ACTIVE", badgeRect.centerX(), badgeRect.centerY() + 3.5f, badgePaint)

    var y = 116f

    // 2. Congratulations Highlight Strip
    val congratsRect = RectF(MARGIN_X, y, CONTENT_RIGHT, y + 46f)
    canvas.drawRoundRect(congratsRect, 8f, 8f, emeraldTintPaint)
    canvas.drawRoundRect(congratsRect, 8f, 8f, borderPaint)
    val congratsPaint =
      Paint(headingPaint).apply {
        color = Color.parseColor("#065F46")
        textSize = 11.5f
      }
    canvas.drawText(
      "Congratulations! Your Finboss ${receipt.tier.displayName} Plan is Now Active.",
      MARGIN_X + 14f,
      y + 20f,
      congratsPaint,
    )
    val cycleLabel = if (receipt.billingCycle == SubscriptionTier.CYCLE_YEARLY) "Yearly" else "Monthly"
    val validFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val paidFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    canvas.drawText(
      "Plan validity extends until ${validFmt.format(Date(receipt.validUntilMs))} • Receipt emailed to ${receipt.subscriberEmail}",
      MARGIN_X + 14f,
      y + 36f,
      mutedPaint,
    )

    y += 64f

    // 3. Two-Column Subscriber & Merchant Cards (Zero Bank A/C or IFSC!)
    val colMid = A4_WIDTH / 2f
    val leftBox = RectF(MARGIN_X, y, colMid - 8f, y + 106f)
    val rightBox = RectF(colMid + 8f, y, CONTENT_RIGHT, y + 106f)
    canvas.drawRoundRect(leftBox, 6f, 6f, lightBgPaint)
    canvas.drawRoundRect(leftBox, 6f, 6f, borderPaint)
    canvas.drawRoundRect(rightBox, 6f, 6f, lightBgPaint)
    canvas.drawRoundRect(rightBox, 6f, 6f, borderPaint)

    canvas.drawText("BILLED TO (SUBSCRIBER)", leftBox.left + 12f, leftBox.top + 18f, mutedPaint)
    canvas.drawText(receipt.subscriberBusinessName, leftBox.left + 12f, leftBox.top + 36f, headingPaint)
    canvas.drawText("Email: ${receipt.subscriberEmail}", leftBox.left + 12f, leftBox.top + 52f, bodyPaint)
    if (!receipt.ccEmail.isNullOrBlank()) {
      canvas.drawText("Admin Copy: ${receipt.ccEmail}", leftBox.left + 12f, leftBox.top + 68f, bodyPaint)
    }
    canvas.drawText("Receipt No.: ${receipt.receiptNumber}", leftBox.left + 12f, leftBox.top + 86f, mutedPaint)

    canvas.drawText("ISSUED BY (VERIFIED MERCHANT)", rightBox.left + 12f, rightBox.top + 18f, mutedPaint)
    canvas.drawText(receipt.merchantName, rightBox.left + 12f, rightBox.top + 36f, headingPaint)
    canvas.drawText("Verified Merchant UPI: ${receipt.merchantUpiId}", rightBox.left + 12f, rightBox.top + 52f, bodyPaint)
    canvas.drawText("Payment Date: ${paidFmt.format(Date(receipt.paidAtMs))}", rightBox.left + 12f, rightBox.top + 68f, bodyPaint)
    canvas.drawText("Ref / UTR: ${receipt.transactionRef}", rightBox.left + 12f, rightBox.top + 86f, mutedPaint)

    y += 128f

    // 4. Subscription Line Item & Tax Breakup Table
    val tableTop = y
    val headerRect = RectF(MARGIN_X, tableTop, CONTENT_RIGHT, tableTop + 26f)
    canvas.drawRect(headerRect, navyPaint)
    val tableHeadPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 9.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val tableHeadRight = Paint(tableHeadPaint).apply { textAlign = Paint.Align.RIGHT }
    canvas.drawText("DESCRIPTION", MARGIN_X + 10f, tableTop + 17f, tableHeadPaint)
    canvas.drawText("SAC", MARGIN_X + 275f, tableTop + 17f, tableHeadPaint)
    canvas.drawText("TAXABLE", MARGIN_X + 380f, tableTop + 17f, tableHeadRight)
    canvas.drawText("GST (18%)", MARGIN_X + 455f, tableTop + 17f, tableHeadRight)
    canvas.drawText("TOTAL PAID", CONTENT_RIGHT - 10f, tableTop + 17f, tableHeadRight)

    val rowTop = tableTop + 26f
    val rowRect = RectF(MARGIN_X, rowTop, CONTENT_RIGHT, rowTop + 56f)
    canvas.drawRect(rowRect, lightBgPaint)
    canvas.drawRect(rowRect, borderPaint)

    val inrFmt = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }
    canvas.drawText(
      "Finboss ${receipt.tier.displayName} Subscription ($cycleLabel)",
      MARGIN_X + 10f,
      rowTop + 21f,
      headingPaint,
    )
    canvas.drawText(
      "Method: ${receipt.paymentMethodLabel} • Valid until ${validFmt.format(Date(receipt.validUntilMs))}",
      MARGIN_X + 10f,
      rowTop + 39f,
      mutedPaint,
    )
    canvas.drawText(SUBSCRIPTION_SAC_CODE, MARGIN_X + 275f, rowTop + 25f, bodyPaint)
    canvas.drawText(inrFmt.format(receipt.taxableValueInr), MARGIN_X + 380f, rowTop + 25f, rightBodyPaint)
    canvas.drawText(inrFmt.format(receipt.gstAmountInr), MARGIN_X + 455f, rowTop + 25f, rightBodyPaint)
    canvas.drawText(inrFmt.format(receipt.amountPaidInr), CONTENT_RIGHT - 10f, rowTop + 25f, rightBoldPaint)

    y = rowTop + 76f

    // 5. Totals Summary Card
    val summaryRect = RectF(CONTENT_RIGHT - 250f, y, CONTENT_RIGHT, y + 86f)
    canvas.drawRoundRect(summaryRect, 8f, 8f, lightBgPaint)
    canvas.drawRoundRect(summaryRect, 8f, 8f, borderPaint)
    canvas.drawText("Taxable Value", summaryRect.left + 12f, summaryRect.top + 22f, bodyPaint)
    canvas.drawText(inrFmt.format(receipt.taxableValueInr), summaryRect.right - 12f, summaryRect.top + 22f, rightBodyPaint)
    canvas.drawText("GST (18% Included)", summaryRect.left + 12f, summaryRect.top + 42f, bodyPaint)
    canvas.drawText(inrFmt.format(receipt.gstAmountInr), summaryRect.right - 12f, summaryRect.top + 42f, rightBodyPaint)
    canvas.drawLine(summaryRect.left + 12f, summaryRect.top + 54f, summaryRect.right - 12f, summaryRect.top + 54f, borderPaint)
    val grandPaint = Paint(headingPaint).apply { color = Color.parseColor("#0D9488"); textSize = 12f }
    val grandRightPaint = Paint(grandPaint).apply { textAlign = Paint.Align.RIGHT }
    canvas.drawText("Total Paid (INR)", summaryRect.left + 12f, summaryRect.top + 74f, grandPaint)
    canvas.drawText(inrFmt.format(receipt.amountPaidInr), summaryRect.right - 12f, summaryRect.top + 74f, grandRightPaint)

    // 6. Unlocked Plan Features on left side next to summary
    canvas.drawText("UNLOCKED PLAN ENTITLEMENTS", MARGIN_X, y + 14f, headingPaint)
    var featY = y + 32f
    receipt.tier.featureHighlights.take(4).forEach { feature ->
      canvas.drawText("• $feature", MARGIN_X, featY, bodyPaint)
      featY += 16f
    }

    // 7. Footer
    val footerY = A4_HEIGHT - 44f
    canvas.drawLine(MARGIN_X, footerY - 14f, CONTENT_RIGHT, footerY - 14f, borderPaint)
    canvas.drawText(
      "This is a computer-generated GST Subscription Receipt from Finboss Billing No.1 ERP.",
      MARGIN_X,
      footerY,
      mutedPaint,
    )
    canvas.drawText(
      "Support & Billing Inquiries: ${DEFAULT_SUBSCRIPTION_NOTIFICATION_EMAIL}",
      MARGIN_X,
      footerY + 14f,
      mutedPaint,
    )
  }
}
