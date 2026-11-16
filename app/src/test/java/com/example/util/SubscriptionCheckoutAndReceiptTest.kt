package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SubscriptionTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubscriptionCheckoutAndReceiptTest {

  @Test
  fun buildUpiPaymentUri_encodesMerchantDetailsAndExactAmountWithoutBankInfo() {
    val uri =
      SubscriptionReceiptPdfPrinter.buildUpiPaymentUri(
        merchantUpiId = "anantbharat@ybl",
        beneficiaryName = "Raghav Enterprises",
        amountInr = 6720,
        planNote = "Book Diamond YEARLY",
      )

    assertTrue(uri.startsWith("upi://pay?"))
    assertTrue(uri.contains("pa=anantbharat%40ybl"))
    assertTrue(uri.contains("pn=Raghav%20Enterprises"))
    assertTrue(uri.contains("am=6720"))
    assertTrue(uri.contains("cu=INR"))
    assertFalse(uri.contains("IFSC", ignoreCase = true))
    assertFalse(uri.contains("HDFC", ignoreCase = true))
  }

  @Test
  fun validationHelpers_enforce12DigitUtrAndValidCardDetails() {
    assertTrue(SubscriptionReceiptPdfPrinter.isValidUpiUtr("432198765432"))
    assertFalse(SubscriptionReceiptPdfPrinter.isValidUpiUtr("123456"))
    assertFalse(SubscriptionReceiptPdfPrinter.isValidUpiUtr("43219876543A"))

    // Standard Visa test card passes Luhn check
    assertTrue(SubscriptionReceiptPdfPrinter.isValidCardNumber("4242424242424242"))
    assertFalse(SubscriptionReceiptPdfPrinter.isValidCardNumber("4242424242424243"))
    assertFalse(SubscriptionReceiptPdfPrinter.isValidCardNumber("0000000000000000"))

    val refCal =
      Calendar.getInstance().apply {
        set(Calendar.YEAR, 2026)
        set(Calendar.MONTH, Calendar.OCTOBER)
      }
    assertTrue(SubscriptionReceiptPdfPrinter.isValidCardExpiry("12/28", refCal))
    assertTrue(SubscriptionReceiptPdfPrinter.isValidCardExpiry("10/26", refCal))
    assertFalse(SubscriptionReceiptPdfPrinter.isValidCardExpiry("09/26", refCal))
    assertFalse(SubscriptionReceiptPdfPrinter.isValidCardExpiry("13/28", refCal))

    assertTrue(SubscriptionReceiptPdfPrinter.isValidCardCvv("123"))
    assertTrue(SubscriptionReceiptPdfPrinter.isValidCardCvv("1234"))
    assertFalse(SubscriptionReceiptPdfPrinter.isValidCardCvv("12"))

    assertTrue(SubscriptionReceiptPdfPrinter.isValidCardHolderName("Anant Bharat"))
    assertFalse(SubscriptionReceiptPdfPrinter.isValidCardHolderName(" "))
  }

  @Test
  fun resolveReceiptRecipientEmails_alwaysIncludesBanantAdminEmail() {
    val (primaryWithOtherUser, ccWithOtherUser) =
      SubscriptionReceiptPdfPrinter.resolveReceiptRecipientEmails("buyer@company.in")
    assertEquals("buyer@company.in", primaryWithOtherUser)
    assertEquals("banant80@gmail.com", ccWithOtherUser)

    val (primaryDirect, ccDirect) =
      SubscriptionReceiptPdfPrinter.resolveReceiptRecipientEmails("banant80@gmail.com")
    assertEquals("banant80@gmail.com", primaryDirect)
    assertNull(ccDirect)

    val (primaryFallback, ccFallback) =
      SubscriptionReceiptPdfPrinter.resolveReceiptRecipientEmails(null)
    assertEquals("banant80@gmail.com", primaryFallback)
    assertNull(ccFallback)
  }

  @Test
  fun generateQrBitmap_andSavePdfAndBuildEmailIntent_produceValidArtifacts() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val upiUri =
      SubscriptionReceiptPdfPrinter.buildUpiPaymentUri(
        merchantUpiId = "anantbharat@ybl",
        beneficiaryName = "Raghav Enterprises",
        amountInr = 5400,
        planNote = "Book Gold YEARLY",
      )

    val qrBitmap = SubscriptionReceiptPdfPrinter.generateQrBitmap(upiUri, sizePx = 256)
    assertNotNull(qrBitmap)
    assertEquals(256, qrBitmap!!.width)
    assertEquals(256, qrBitmap.height)
    // Verify finder pattern contains both black and white pixels (not a blank square)
    var hasBlack = false
    var hasWhite = false
    for (x in 0 until qrBitmap.width step 8) {
      val px = qrBitmap.getPixel(x, x)
      if (px == Color.BLACK) hasBlack = true
      if (px == Color.WHITE) hasWhite = true
    }
    assertTrue(hasBlack && hasWhite)

    val receipt =
      SubscriptionReceiptPdfPrinter.createReceipt(
        tier = SubscriptionTier.GOLD,
        billingCycle = SubscriptionTier.CYCLE_YEARLY,
        amountPaidInr = 5400,
        paymentMethodLabel = "UPI (UTR: 432198765432)",
        transactionRef = "432198765432",
        merchantName = "Raghav Enterprises",
        merchantUpiId = "anantbharat@ybl",
        subscriberBusinessName = "Shree Ganesh Traders",
        signedInUserEmail = "owner@traders.in",
        nowMs = 1_794_000_000_000L,
      )

    assertEquals(5400, receipt.amountPaidInr)
    assertEquals(5400.0, receipt.taxableValueInr + receipt.gstAmountInr, 0.02)
    assertEquals("owner@traders.in", receipt.subscriberEmail)
    assertEquals("banant80@gmail.com", receipt.ccEmail)

    val receiptsDir = java.io.File(context.cacheDir, "receipts").apply { mkdirs() }
    val cachedFile = java.io.File(receiptsDir, "${receipt.receiptNumber}.pdf")
    cachedFile.writeBytes("%PDF-1.4\n%%EOF\n".encodeToByteArray())
    val pdfUri =
      androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        cachedFile,
      )
    assertTrue(cachedFile.exists())
    assertEquals("${context.packageName}.fileprovider", pdfUri.authority)

    val chooserIntent =
      SubscriptionReceiptPdfPrinter.buildSubscriptionEmailIntent(context, receipt, pdfUri)
    assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
    @Suppress("DEPRECATION")
    val targetIntent = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
    assertNotNull(targetIntent)
    assertEquals(Intent.ACTION_SEND, targetIntent!!.action)
    assertEquals("owner@traders.in", targetIntent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.first())
    assertEquals("banant80@gmail.com", targetIntent.getStringArrayExtra(Intent.EXTRA_CC)?.first())
    assertTrue(
      targetIntent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().contains("Congratulations!"),
    )
    @Suppress("DEPRECATION")
    val attachedUri = targetIntent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
    assertEquals(pdfUri, attachedUri)
    assertTrue((targetIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
  }
}
