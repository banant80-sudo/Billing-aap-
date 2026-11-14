package com.example.data.reference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GstInvoice
import com.example.data.sync.ErpAppSettings
import com.example.data.sync.ErpSettingsStore
import com.example.data.sync.InvoicePrintStyle
import com.example.data.sync.PrintPaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReferenceAndSettingsTest {

  @Test
  fun hsnCodeDirectory_searchByItemNameKeywordAndCode() {
    val printerMatches = HsnCodeDirectory.search("printer")
    assertTrue(printerMatches.any { it.code == "8443" })

    val riceMatches = HsnCodeDirectory.search("basmati")
    assertEquals("1006", riceMatches.first().code)
    assertEquals(5.0, riceMatches.first().gstRatePercent, 0.01)

    val exactCode = HsnCodeDirectory.search("8471")
    assertEquals("8471", exactCode.first().code)
  }

  @Test
  fun indiaStates_searchGstinDetectionAndSameStateCheck() {
    assertEquals(36, IndiaStates.allStates.size)

    val mh = IndiaStates.findByNameOrCode("27")
    assertNotNull(mh)
    assertEquals("Maharashtra", mh?.name)

    val fromGstin = IndiaStates.stateFromGstin("29AAACF1234K1Z1")
    assertEquals("Karnataka", fromGstin?.name)

    assertEquals(true, IndiaStates.areSameState("Maharashtra", "27 - Maharashtra"))
    assertEquals(false, IndiaStates.areSameState("Maharashtra", "Gujarat"))
    assertNull(IndiaStates.areSameState("Maharashtra", ""))
  }

  @Test
  fun erpSettingsStore_persistsUpdatesAndResetsToDefaults() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = ErpSettingsStore(context)
    store.resetToDefaults()

    store.update {
      it.copy(
        defaultPaperSize = PrintPaperSize.THERMAL_80MM,
        defaultPrintStyle = InvoicePrintStyle.SIMPLE,
        invoicePrefix = "FIN-26-",
        defaultGstSlabPercent = 12.0,
        autoDeductStockOnSale = false,
        allowNegativeStockSale = true,
        defaultLowStockThreshold = 15.0,
        defaultPartyCreditLimit = 100000.0,
        defaultPaymentMethod = GstInvoice.PAYMENT_CASH,
      )
    }

    val reloaded = ErpSettingsStore(context)
    reloaded.warmup()
    val current = reloaded.settings.value

    assertEquals(PrintPaperSize.THERMAL_80MM, current.defaultPaperSize)
    assertEquals(InvoicePrintStyle.SIMPLE, current.defaultPrintStyle)
    assertEquals("FIN-26-", current.invoicePrefix)
    assertEquals(12.0, current.defaultGstSlabPercent, 0.01)
    assertFalse(current.autoDeductStockOnSale)
    assertTrue(current.allowNegativeStockSale)
    assertEquals(15.0, current.defaultLowStockThreshold, 0.01)
    assertEquals(100000.0, current.defaultPartyCreditLimit, 0.01)
    assertEquals(GstInvoice.PAYMENT_CASH, current.defaultPaymentMethod)

    reloaded.resetToDefaults()
    assertEquals(ErpAppSettings(), reloaded.settings.value)
  }
}
