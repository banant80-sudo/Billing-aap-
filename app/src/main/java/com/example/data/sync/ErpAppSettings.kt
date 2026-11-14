package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
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
) {
  A4("A4", "A4 Full Page", "210 × 297 mm • Office / Tally", 595, 842),
  A5("A5", "A5 Half Page", "148 × 210 mm • Compact Bill", 420, 595),
  THERMAL_80MM("THERMAL_80MM", "Thermal 80mm (3\")", "80 mm POS Roll • Standard Receipt", 226, 720),
  THERMAL_58MM("THERMAL_58MM", "Thermal 58mm (2\")", "58 mm Mini POS Roll", 164, 720);

  val isThermal: Boolean
    get() = this == THERMAL_80MM || this == THERMAL_58MM

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
  TALLY("TALLY", "Tally Classic", "Bordered ERP grid, HSN, tax breakup & words"),
  MODERN("MODERN", "Modern Executive", "Navy & Emerald banner + rounded cards"),
  SIMPLE("SIMPLE", "Simple Bill", "Clean ink-saving monochrome bill"),
  THERMAL_POS("THERMAL_POS", "Thermal POS Receipt", "Compact receipt strip with dashed lines");

  companion object {
    fun fromKey(key: String?): InvoicePrintStyle =
      entries.firstOrNull { it.key == key } ?: TALLY
  }
}

data class ErpAppSettings(
  // Printing & Design Logic
  val defaultPaperSize: PrintPaperSize = PrintPaperSize.A4,
  val defaultPrintStyle: InvoicePrintStyle = InvoicePrintStyle.TALLY,
  val askPrintOptionsBeforePrint: Boolean = true,
  val showHsnOnInvoicePrint: Boolean = true,
  val showAddressAndStateOnPrint: Boolean = true,
  val showAmountInWordsOnPrint: Boolean = true,
  val invoiceFooterNote: String = "Thank you for your business!",
  val invoiceTermsText: String = "Goods once sold will not be taken back. Subject to local jurisdiction.",

  // Billing & GST Logic
  val invoicePrefix: String = "FB-2026-",
  val defaultInvoiceType: String = GstInvoice.TYPE_TAX_INVOICE,
  val defaultGstSlabPercent: Double = 18.0,
  val autoDetectGstModeFromState: Boolean = true,
  val defaultPaymentMethod: String = GstInvoice.PAYMENT_UPI,

  // Inventory & Purchase Logic
  val defaultLowStockThreshold: Double = 5.0,
  val autoDeductStockOnSale: Boolean = true,
  val allowNegativeStockSale: Boolean = false,
  val autoRecordPurchaseInExpenses: Boolean = true,
  val defaultPurchasePaymentMethod: String = "upi",

  // Parties & Sync Logic
  val defaultPartyCreditLimit: Double = 50000.0,
  val warnOnCreditLimitExceeded: Boolean = true,
  val autoSyncWhenOnline: Boolean = true,
)

class ErpSettingsStore(context: Context) {
  private val appContext = context.applicationContext
  private val prefs: SharedPreferences by lazy {
    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  private val _settings = MutableStateFlow(ErpAppSettings())
  val settings: StateFlow<ErpAppSettings> = _settings.asStateFlow()

  fun warmup() {
    _settings.value = loadFromPrefs()
  }

  fun update(transform: (ErpAppSettings) -> ErpAppSettings) {
    val next = sanitize(transform(_settings.value))
    _settings.value = next
    saveToPrefs(next)
  }

  fun replaceAll(newSettings: ErpAppSettings) {
    val clean = sanitize(newSettings)
    _settings.value = clean
    saveToPrefs(clean)
  }

  fun resetToDefaults() {
    val defaults = ErpAppSettings()
    _settings.value = defaults
    prefs.edit().clear().apply()
    saveToPrefs(defaults)
  }

  private fun loadFromPrefs(): ErpAppSettings {
    val defaults = ErpAppSettings()
    return sanitize(
      ErpAppSettings(
        defaultPaperSize = PrintPaperSize.fromKey(prefs.getString(KEY_PAPER_SIZE, defaults.defaultPaperSize.key)),
        defaultPrintStyle = InvoicePrintStyle.fromKey(prefs.getString(KEY_PRINT_STYLE, defaults.defaultPrintStyle.key)),
        askPrintOptionsBeforePrint = prefs.getBoolean(KEY_ASK_BEFORE_PRINT, defaults.askPrintOptionsBeforePrint),
        showHsnOnInvoicePrint = prefs.getBoolean(KEY_SHOW_HSN, defaults.showHsnOnInvoicePrint),
        showAddressAndStateOnPrint = prefs.getBoolean(KEY_SHOW_ADDRESS, defaults.showAddressAndStateOnPrint),
        showAmountInWordsOnPrint = prefs.getBoolean(KEY_SHOW_WORDS, defaults.showAmountInWordsOnPrint),
        invoiceFooterNote = prefs.getString(KEY_FOOTER_NOTE, defaults.invoiceFooterNote) ?: defaults.invoiceFooterNote,
        invoiceTermsText = prefs.getString(KEY_TERMS_TEXT, defaults.invoiceTermsText) ?: defaults.invoiceTermsText,
        invoicePrefix = prefs.getString(KEY_INV_PREFIX, defaults.invoicePrefix) ?: defaults.invoicePrefix,
        defaultInvoiceType = prefs.getString(KEY_INV_TYPE, defaults.defaultInvoiceType) ?: defaults.defaultInvoiceType,
        defaultGstSlabPercent = prefs.getFloat(KEY_DEFAULT_GST, defaults.defaultGstSlabPercent.toFloat()).toDouble(),
        autoDetectGstModeFromState = prefs.getBoolean(KEY_AUTO_STATE_TAX, defaults.autoDetectGstModeFromState),
        defaultPaymentMethod = prefs.getString(KEY_DEFAULT_PAY_METHOD, defaults.defaultPaymentMethod) ?: defaults.defaultPaymentMethod,
        defaultLowStockThreshold = prefs.getFloat(KEY_LOW_STOCK_THRESH, defaults.defaultLowStockThreshold.toFloat()).toDouble(),
        autoDeductStockOnSale = prefs.getBoolean(KEY_AUTO_DEDUCT_STOCK, defaults.autoDeductStockOnSale),
        allowNegativeStockSale = prefs.getBoolean(KEY_ALLOW_NEG_STOCK, defaults.allowNegativeStockSale),
        autoRecordPurchaseInExpenses = prefs.getBoolean(KEY_AUTO_PURCHASE_EXPENSE, defaults.autoRecordPurchaseInExpenses),
        defaultPurchasePaymentMethod = prefs.getString(KEY_PURCHASE_PAY_METHOD, defaults.defaultPurchasePaymentMethod) ?: defaults.defaultPurchasePaymentMethod,
        defaultPartyCreditLimit = prefs.getFloat(KEY_PARTY_CREDIT_LIMIT, defaults.defaultPartyCreditLimit.toFloat()).toDouble(),
        warnOnCreditLimitExceeded = prefs.getBoolean(KEY_WARN_CREDIT_LIMIT, defaults.warnOnCreditLimitExceeded),
        autoSyncWhenOnline = prefs.getBoolean(KEY_AUTO_SYNC, defaults.autoSyncWhenOnline),
      )
    )
  }

  private fun saveToPrefs(s: ErpAppSettings) {
    prefs.edit()
      .putString(KEY_PAPER_SIZE, s.defaultPaperSize.key)
      .putString(KEY_PRINT_STYLE, s.defaultPrintStyle.key)
      .putBoolean(KEY_ASK_BEFORE_PRINT, s.askPrintOptionsBeforePrint)
      .putBoolean(KEY_SHOW_HSN, s.showHsnOnInvoicePrint)
      .putBoolean(KEY_SHOW_ADDRESS, s.showAddressAndStateOnPrint)
      .putBoolean(KEY_SHOW_WORDS, s.showAmountInWordsOnPrint)
      .putString(KEY_FOOTER_NOTE, s.invoiceFooterNote)
      .putString(KEY_TERMS_TEXT, s.invoiceTermsText)
      .putString(KEY_INV_PREFIX, s.invoicePrefix)
      .putString(KEY_INV_TYPE, s.defaultInvoiceType)
      .putFloat(KEY_DEFAULT_GST, s.defaultGstSlabPercent.toFloat())
      .putBoolean(KEY_AUTO_STATE_TAX, s.autoDetectGstModeFromState)
      .putString(KEY_DEFAULT_PAY_METHOD, s.defaultPaymentMethod)
      .putFloat(KEY_LOW_STOCK_THRESH, s.defaultLowStockThreshold.toFloat())
      .putBoolean(KEY_AUTO_DEDUCT_STOCK, s.autoDeductStockOnSale)
      .putBoolean(KEY_ALLOW_NEG_STOCK, s.allowNegativeStockSale)
      .putBoolean(KEY_AUTO_PURCHASE_EXPENSE, s.autoRecordPurchaseInExpenses)
      .putString(KEY_PURCHASE_PAY_METHOD, s.defaultPurchasePaymentMethod)
      .putFloat(KEY_PARTY_CREDIT_LIMIT, s.defaultPartyCreditLimit.toFloat())
      .putBoolean(KEY_WARN_CREDIT_LIMIT, s.warnOnCreditLimitExceeded)
      .putBoolean(KEY_AUTO_SYNC, s.autoSyncWhenOnline)
      .apply()
  }

  private fun sanitize(s: ErpAppSettings): ErpAppSettings =
    s.copy(
      invoicePrefix = s.invoicePrefix.trim().ifBlank { "FB-" }.take(18),
      invoiceFooterNote = s.invoiceFooterNote.take(120),
      invoiceTermsText = s.invoiceTermsText.take(160),
      defaultGstSlabPercent = if (s.defaultGstSlabPercent in listOf(0.0, 5.0, 12.0, 18.0, 28.0)) s.defaultGstSlabPercent else 18.0,
      defaultLowStockThreshold = s.defaultLowStockThreshold.coerceIn(0.0, 100000.0),
      defaultPartyCreditLimit = s.defaultPartyCreditLimit.coerceIn(0.0, 100_000_000.0),
    )

  private companion object {
    const val PREFS_NAME = "finboss_app_settings"
    const val KEY_PAPER_SIZE = "paper_size"
    const val KEY_PRINT_STYLE = "print_style"
    const val KEY_ASK_BEFORE_PRINT = "ask_before_print"
    const val KEY_SHOW_HSN = "show_hsn"
    const val KEY_SHOW_ADDRESS = "show_address"
    const val KEY_SHOW_WORDS = "show_words"
    const val KEY_FOOTER_NOTE = "footer_note"
    const val KEY_TERMS_TEXT = "terms_text"
    const val KEY_INV_PREFIX = "inv_prefix"
    const val KEY_INV_TYPE = "inv_type"
    const val KEY_DEFAULT_GST = "default_gst"
    const val KEY_AUTO_STATE_TAX = "auto_state_tax"
    const val KEY_DEFAULT_PAY_METHOD = "default_pay_method"
    const val KEY_LOW_STOCK_THRESH = "low_stock_thresh"
    const val KEY_AUTO_DEDUCT_STOCK = "auto_deduct_stock"
    const val KEY_ALLOW_NEG_STOCK = "allow_neg_stock"
    const val KEY_AUTO_PURCHASE_EXPENSE = "auto_purchase_expense"
    const val KEY_PURCHASE_PAY_METHOD = "purchase_pay_method"
    const val KEY_PARTY_CREDIT_LIMIT = "party_credit_limit"
    const val KEY_WARN_CREDIT_LIMIT = "warn_credit_limit"
    const val KEY_AUTO_SYNC = "auto_sync"
  }
}
