package com.example.util

import com.example.data.model.GstInvoice
import com.example.data.model.InvoiceLineItem
import com.example.data.sync.ErpAppSettings
import com.example.data.sync.InvoicePrintStyle
import com.example.data.sync.PrintPaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoicePdfPrinterTest {

  @Test
  fun paginateLineItems_splitsLargeInvoicesAcrossMultipleA4Pages() {
    val lines =
      (1..22).map { idx ->
        InvoiceLineItem(
          itemId = "item_$idx",
          name = "Barcode Thermal Label Roll #$idx",
          sku = "SKU-$idx",
          hsnCode = "4811",
          unit = "BOX",
          qty = 2.0,
          unitPrice = 450.0,
          gstRatePercent = 18.0,
          taxableAmount = 900.0,
          gstAmount = 162.0,
          lineTotal = 1062.0,
        )
      }

    val pages = InvoicePdfPrinter.paginateLineItems(lines)
    assertTrue("Expected multi-page split for 22 SKU rows", pages.size >= 2)
    assertEquals(22, pages.sumOf { it.size })
  }

  @Test
  fun buildSafePdfFileName_andFormatters_sanitizesAndFormatsCleanly() {
    assertEquals("FB_2026_088.pdf", InvoicePdfPrinter.buildSafePdfFileName("FB/2026/088"))
    assertEquals("invoice.pdf", InvoicePdfPrinter.buildSafePdfFileName("   "))
    assertEquals("CGST + SGST", InvoicePdfPrinter.formatTaxModeLabel(GstInvoice.TAX_MODE_CGST_SGST))
    assertEquals("IGST", InvoicePdfPrinter.formatTaxModeLabel(GstInvoice.TAX_MODE_IGST))
    assertEquals("No GST", InvoicePdfPrinter.formatTaxModeLabel(GstInvoice.TAX_MODE_NONE))
    assertEquals("₹ 12,450.50", InvoicePdfPrinter.formatPdfMoney("₹", 12450.5))
  }

  @Test
  fun buildAddressLine_andAmountInWords_formatIndianStandards() {
    assertEquals(
      "Plot 18, Maharashtra, PIN 400703",
      InvoicePdfPrinter.buildAddressLine("Plot 18", "Maharashtra", "400703"),
    )
    assertEquals("Maharashtra", InvoicePdfPrinter.buildAddressLine("", "Maharashtra", ""))
    assertEquals("", InvoicePdfPrinter.buildAddressLine("", "", ""))

    assertEquals(
      "Rupees Two Thousand Three Hundred Sixty Only",
      InvoicePdfPrinter.amountInWords(2360.0),
    )
    assertEquals(
      "Rupees One Lakh Twenty Five Thousand Forty Two and Fifty Paise Only",
      InvoicePdfPrinter.amountInWords(125042.50),
    )
    assertEquals("Rupees Zero Only", InvoicePdfPrinter.amountInWords(0.0))
  }

  @Test
  fun printOptionsFromSettings_andPaperSizes_mapAllPrintStylesAndDimensions() {
    assertEquals(595, PrintPaperSize.A4.widthPt)
    assertEquals(842, PrintPaperSize.A4.heightPt)
    assertFalse(PrintPaperSize.A4.isThermal)

    assertEquals(420, PrintPaperSize.A5.widthPt)
    assertEquals(595, PrintPaperSize.A5.heightPt)
    assertFalse(PrintPaperSize.A5.isThermal)

    assertEquals(226, PrintPaperSize.THERMAL_80MM.widthPt)
    assertTrue(PrintPaperSize.THERMAL_80MM.isThermal)

    assertEquals(164, PrintPaperSize.THERMAL_58MM.widthPt)
    assertTrue(PrintPaperSize.THERMAL_58MM.isThermal)

    val settings =
      ErpAppSettings(
        defaultPaperSize = PrintPaperSize.THERMAL_80MM,
        defaultPrintStyle = InvoicePrintStyle.SIMPLE,
        showHsnOnInvoicePrint = false,
        showAddressAndStateOnPrint = true,
        showAmountInWordsOnPrint = false,
        invoiceFooterNote = "Visit Again",
        invoiceTermsText = "No Return",
      )
    val options = InvoicePrintOptions.fromSettings(settings)
    assertEquals(PrintPaperSize.THERMAL_80MM, options.paperSize)
    assertEquals(InvoicePrintStyle.SIMPLE, options.style)
    assertFalse(options.showHsn)
    assertTrue(options.showAddressAndState)
    assertFalse(options.showAmountInWords)
    assertEquals("Visit Again", options.footerNote)
    assertEquals("No Return", options.termsText)
  }
}
