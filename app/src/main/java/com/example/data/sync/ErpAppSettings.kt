package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ErpExpense
import com.example.data.model.GstInvoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PrintPaperSize(
  val key: String,
  val label: String,
  val subtitle: String,
  val widthPt: Int,
  val heightPt: Int,
  val isThermal: Boolean,
) {
  A4(
    key = "a4",
    label = "A4 Full Sheet",
    subtitle = "210 × 297 mm • Standard Office & Tally Laser Print",
    widthPt = 595,
    heightPt = 842,
    isThermal = false,
  ),
  A5(
    key = "a5",
    label = "A5 Half Sheet",
    subtitle = "148 × 210 mm • Compact Wholesale Counter Bill",
    widthPt = 420,
    heightPt = 595,
    isThermal = false,
  ),
  THERMAL_80MM(
    key = "thermal_80mm",
    label = "Thermal 80mm (3-Inch POS)",
    subtitle = "80mm Roll • Supermarket & Restaurant POS Terminal",
    widthPt = 226,
    heightPt = 640,
    isThermal = true,
  ),
  THERMAL_58MM(
    key = "thermal_58mm",
    label = "Thermal 58mm (2-Inch Mini)",
    subtitle = "58mm Roll • Handheld Bluetooth Terminal Receipt",
    widthPt = 164,
    heightPt = 560,
    isThermal = true,
  );

  val pageWidthPt: Int
    get() = widthPt

  val pageHeightPt: Int
    get() = heightPt

  companion object {
    fun fromKey(key: String?): PrintPaperSize =
      entries.firstOrNull { it.key == key } ?: A4
  }
}

enum class InvoicePrintStyle(
  val key: String,
  val label: String,
  val subtitle: String,
) {
  TALLY(
    key = "tally_classic",
    label = "Tally Classic Boxed",
    subtitle = "Ruled grid borders, HSN/SAC columns, GST breakup table & signatory box",
  ),
  SIMPLE(
    key = "simple_bill",
    label = "Simple Clean Bill",
    subtitle = "Minimal clutter-free layout for fast counter receipts & plain bills",
  ),
  MODERN_EXEC(
    key = "modern_executive",
    label = "Modern Executive",
    subtitle = "Branded navy header banner, color accents & executive summary block",
  ),
  THERMAL_POS(
    key = "thermal_pos",
    label = "Thermal POS Receipt",
    subtitle = "High-density item rows optimized for 80mm/58mm thermal rolls & counter strips",
  );

  companion object {
    fun fromKey(key: String?): InvoicePrintStyle =
      entries.firstOrNull { it.key == key } ?: TALLY
  }
}

typealias PrintLayoutStyle = InvoicePrintStyle

data class ErpAppSettings(
  // Printing & Layout Settings
  val defaultPaperSize: PrintPaperSize = PrintPaperSize.A4,
  val defaultPrintStyle: InvoicePrintStyle = InvoicePrintStyle.TALLY,
  val askPrintOptionsBeforePrint: Boolean = true,
  val showHsnOnInvoicePrint: Boolean = true,
  val showAddressAndStateOnPrint: Boolean = true,
  val showAmountInWordsOnPrint: Boolean = true,
  val showGstBreakupTableOnPrint: Boolean = true,
  val showBankAndUpiOnPrint: Boolean = true,
  val showSignatureBoxOnPrint: Boolean = true,
  val bankAndUpiDetails: String = "UPI: finboss@upi | A/C: Finboss Current A/C • IFSC: HDFC0001234",
  val invoiceFooterNote: String = "Thank you for your business!",
  val invoiceTermsText: String =
    "Goods once sold will not be taken back. Subject to local jurisdiction.",
  // Billing & Tax Logic Settings
  val invoicePrefix: String = "FB-2026-",
  val defaultInvoiceType: String = GstInvoice.TYPE_TAX_INVOICE,
  val defaultTaxMode: String = GstInvoice.TAX_MODE_CGST_SGST,
  val defaultPaymentMethod: String = GstInvoice.PAYMENT_UPI,
  val autoDetectGstModeFromState: Boolean = true,
  val roundOffGrandTotal: Boolean = false,
  // Inventory, Purchase & Khata Logic Settings
  val defaultGstSlabPercent: Double = 18.0,
  val defaultItemUnit: String = "PCS",
  val defaultLowStockThreshold: Double = 5.0,
  val autoDeductStockOnSale: Boolean = true,
  val allowNegativeStockSale: Boolean = false,
  val autoRecordPurchaseInExpenses: Boolean = true,
  val defaultPurchasePaymentMethod: String = ErpExpense.METHOD_UPI,
  val defaultPartyCreditLimit: Double = 50000.0,
  val warnOnCreditLimitExceeded: Boolean = true,
  val autoSyncWhenOnline: Boolean = true,
  // GST Portal (IRP) E-Invoice & E-Way Bill API Integration Settings
  val gstPortalMode: String = GST_PORTAL_MODE_SANDBOX, // sandbox, live, offline_json
  val gstPortalGstin: String = "",
  val gstPortalUsername: String = "",
  val gstPortalClientId: String = "",
  val gstPortalClientSecret: String = "",
  val defaultDispatchCity: String = "",
  val defaultTransporterName: String = "",
  val defaultTransporterId: String = "",
) {
  val showHsnOnPrint: Boolean
    get() = showHsnOnInvoicePrint

  val showPartyAddressOnPrint: Boolean
    get() = showAddressAndStateOnPrint

  val autoDetectIgstByState: Boolean
    get() = autoDetectGstModeFromState

  companion object {
    const val GST_PORTAL_MODE_SANDBOX = "sandbox"
    const val GST_PORTAL_MODE_LIVE = "live"
    const val GST_PORTAL_MODE_OFFLINE_JSON = "offline_json"
  }
}

class ErpSettingsStore(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

  private val _settings = MutableStateFlow(loadFromPrefs())
  val settings: StateFlow<ErpAppSettings> = _settings.asStateFlow()

  fun warmup() {
    _settings.value = loadFromPrefs()
  }

  fun update(transform: (ErpAppSettings) -> ErpAppSettings) {
    val updated = transform(_settings.value)
    saveToPrefs(updated)
    _settings.value = updated
  }

  fun resetToDefaults() {
    val defaults = ErpAppSettings()
    saveToPrefs(defaults)
    _settings.value = defaults
  }

  private fun loadFromPrefs(): ErpAppSettings {
    val defaults = ErpAppSettings()
    return ErpAppSettings(
      defaultPaperSize =
        PrintPaperSize.fromKey(prefs.getString("defaultPaperSize", defaults.defaultPaperSize.key)),
      defaultPrintStyle =
        InvoicePrintStyle.fromKey(
          prefs.getString("defaultPrintStyle", defaults.defaultPrintStyle.key)
        ),
      askPrintOptionsBeforePrint =
        prefs.getBoolean("askPrintOptionsBeforePrint", defaults.askPrintOptionsBeforePrint),
      showHsnOnInvoicePrint =
        prefs.getBoolean("showHsnOnInvoicePrint", defaults.showHsnOnInvoicePrint),
      showAddressAndStateOnPrint =
        prefs.getBoolean("showAddressAndStateOnPrint", defaults.showAddressAndStateOnPrint),
      showAmountInWordsOnPrint =
        prefs.getBoolean("showAmountInWordsOnPrint", defaults.showAmountInWordsOnPrint),
      showGstBreakupTableOnPrint =
        prefs.getBoolean("showGstBreakupTableOnPrint", defaults.showGstBreakupTableOnPrint),
      showBankAndUpiOnPrint =
        prefs.getBoolean("showBankAndUpiOnPrint", defaults.showBankAndUpiOnPrint),
      showSignatureBoxOnPrint =
        prefs.getBoolean("showSignatureBoxOnPrint", defaults.showSignatureBoxOnPrint),
      bankAndUpiDetails =
        prefs.getString("bankAndUpiDetails", defaults.bankAndUpiDetails)
          ?: defaults.bankAndUpiDetails,
      invoiceFooterNote =
        prefs.getString("invoiceFooterNote", defaults.invoiceFooterNote)
          ?: defaults.invoiceFooterNote,
      invoiceTermsText =
        prefs.getString("invoiceTermsText", defaults.invoiceTermsText)
          ?: defaults.invoiceTermsText,
      invoicePrefix =
        prefs.getString("invoicePrefix", defaults.invoicePrefix)?.ifBlank { "FB-2026-" }
          ?: defaults.invoicePrefix,
      defaultInvoiceType =
        prefs.getString("defaultInvoiceType", defaults.defaultInvoiceType)
          ?: defaults.defaultInvoiceType,
      defaultTaxMode =
        prefs.getString("defaultTaxMode", defaults.defaultTaxMode) ?: defaults.defaultTaxMode,
      defaultPaymentMethod =
        prefs.getString("defaultPaymentMethod", defaults.defaultPaymentMethod)
          ?: defaults.defaultPaymentMethod,
      autoDetectGstModeFromState =
        prefs.getBoolean("autoDetectGstModeFromState", defaults.autoDetectGstModeFromState),
      roundOffGrandTotal = prefs.getBoolean("roundOffGrandTotal", defaults.roundOffGrandTotal),
      defaultGstSlabPercent =
        prefs.getFloat("defaultGstSlabPercent", defaults.defaultGstSlabPercent.toFloat()).toDouble(),
      defaultItemUnit =
        prefs.getString("defaultItemUnit", defaults.defaultItemUnit) ?: defaults.defaultItemUnit,
      defaultLowStockThreshold =
        prefs
          .getFloat("defaultLowStockThreshold", defaults.defaultLowStockThreshold.toFloat())
          .toDouble(),
      autoDeductStockOnSale =
        prefs.getBoolean("autoDeductStockOnSale", defaults.autoDeductStockOnSale),
      allowNegativeStockSale =
        prefs.getBoolean("allowNegativeStockSale", defaults.allowNegativeStockSale),
      autoRecordPurchaseInExpenses =
        prefs.getBoolean("autoRecordPurchaseInExpenses", defaults.autoRecordPurchaseInExpenses),
      defaultPurchasePaymentMethod =
        prefs.getString("defaultPurchasePaymentMethod", defaults.defaultPurchasePaymentMethod)
          ?: defaults.defaultPurchasePaymentMethod,
      defaultPartyCreditLimit =
        prefs
          .getFloat("defaultPartyCreditLimit", defaults.defaultPartyCreditLimit.toFloat())
          .toDouble(),
      warnOnCreditLimitExceeded =
        prefs.getBoolean("warnOnCreditLimitExceeded", defaults.warnOnCreditLimitExceeded),
      autoSyncWhenOnline =
        prefs.getBoolean("autoSyncWhenOnline", defaults.autoSyncWhenOnline),
      gstPortalMode =
        prefs.getString("gstPortalMode", defaults.gstPortalMode) ?: defaults.gstPortalMode,
      gstPortalGstin =
        prefs.getString("gstPortalGstin", defaults.gstPortalGstin) ?: defaults.gstPortalGstin,
      gstPortalUsername =
        prefs.getString("gstPortalUsername", defaults.gstPortalUsername)
          ?: defaults.gstPortalUsername,
      gstPortalClientId =
        prefs.getString("gstPortalClientId", defaults.gstPortalClientId)
          ?: defaults.gstPortalClientId,
      gstPortalClientSecret =
        prefs.getString("gstPortalClientSecret", defaults.gstPortalClientSecret)
          ?: defaults.gstPortalClientSecret,
      defaultDispatchCity =
        prefs.getString("defaultDispatchCity", defaults.defaultDispatchCity)
          ?: defaults.defaultDispatchCity,
      defaultTransporterName =
        prefs.getString("defaultTransporterName", defaults.defaultTransporterName)
          ?: defaults.defaultTransporterName,
      defaultTransporterId =
        prefs.getString("defaultTransporterId", defaults.defaultTransporterId)
          ?: defaults.defaultTransporterId,
    )
  }

  private fun saveToPrefs(s: ErpAppSettings) {
    prefs
      .edit()
      .putString("defaultPaperSize", s.defaultPaperSize.key)
      .putString("defaultPrintStyle", s.defaultPrintStyle.key)
      .putBoolean("askPrintOptionsBeforePrint", s.askPrintOptionsBeforePrint)
      .putBoolean("showHsnOnInvoicePrint", s.showHsnOnInvoicePrint)
      .putBoolean("showAddressAndStateOnPrint", s.showAddressAndStateOnPrint)
      .putBoolean("showAmountInWordsOnPrint", s.showAmountInWordsOnPrint)
      .putBoolean("showGstBreakupTableOnPrint", s.showGstBreakupTableOnPrint)
      .putBoolean("showBankAndUpiOnPrint", s.showBankAndUpiOnPrint)
      .putBoolean("showSignatureBoxOnPrint", s.showSignatureBoxOnPrint)
      .putString("bankAndUpiDetails", s.bankAndUpiDetails.take(180))
      .putString("invoiceFooterNote", s.invoiceFooterNote.take(160))
      .putString("invoiceTermsText", s.invoiceTermsText.take(260))
      .putString("invoicePrefix", s.invoicePrefix.trim().take(18).ifBlank { "FB-2026-" })
      .putString("defaultInvoiceType", s.defaultInvoiceType)
      .putString("defaultTaxMode", s.defaultTaxMode)
      .putString("defaultPaymentMethod", s.defaultPaymentMethod)
      .putBoolean("autoDetectGstModeFromState", s.autoDetectGstModeFromState)
      .putBoolean("roundOffGrandTotal", s.roundOffGrandTotal)
      .putFloat("defaultGstSlabPercent", s.defaultGstSlabPercent.toFloat())
      .putString("defaultItemUnit", s.defaultItemUnit.trim().take(10).ifBlank { "PCS" })
      .putFloat("defaultLowStockThreshold", s.defaultLowStockThreshold.toFloat())
      .putBoolean("autoDeductStockOnSale", s.autoDeductStockOnSale)
      .putBoolean("allowNegativeStockSale", s.allowNegativeStockSale)
      .putBoolean("autoRecordPurchaseInExpenses", s.autoRecordPurchaseInExpenses)
      .putString("defaultPurchasePaymentMethod", s.defaultPurchasePaymentMethod)
      .putFloat("defaultPartyCreditLimit", s.defaultPartyCreditLimit.toFloat())
      .putBoolean("warnOnCreditLimitExceeded", s.warnOnCreditLimitExceeded)
      .putBoolean("autoSyncWhenOnline", s.autoSyncWhenOnline)
      .putString("gstPortalMode", s.gstPortalMode)
      .putString("gstPortalGstin", s.gstPortalGstin.trim().uppercase().take(15))
      .putString("gstPortalUsername", s.gstPortalUsername.trim().take(60))
      .putString("gstPortalClientId", s.gstPortalClientId.trim().take(80))
      .putString("gstPortalClientSecret", s.gstPortalClientSecret.trim().take(80))
      .putString("defaultDispatchCity", s.defaultDispatchCity.trim().take(60))
      .putString("defaultTransporterName", s.defaultTransporterName.trim().take(80))
      .putString("defaultTransporterId", s.defaultTransporterId.trim().uppercase().take(20))
      .apply()
  }

  private companion object {
    const val PREFS_FILE = "finboss_erp_logic_settings"
  }
}
