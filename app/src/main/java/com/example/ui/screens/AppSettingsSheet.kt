package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ErpExpense
import com.example.data.model.GstInvoice
import com.example.data.sync.ErpAppSettings
import com.example.data.sync.InvoicePrintStyle
import com.example.data.sync.PrintPaperSize
import com.example.util.InvoicePrintOptions

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppSettingsSheet(
  settings: ErpAppSettings,
  isPlainNonGst: Boolean,
  onUpdateSettings: ((ErpAppSettings) -> ErpAppSettings) -> Unit,
  onResetDefaults: () -> Unit,
  onDismiss: () -> Unit,
) {
  var prefixDraft by remember(settings.invoicePrefix) { mutableStateOf(settings.invoicePrefix) }
  var footerDraft by remember(settings.invoiceFooterNote) { mutableStateOf(settings.invoiceFooterNote) }
  var termsDraft by remember(settings.invoiceTermsText) { mutableStateOf(settings.invoiceTermsText) }
  var lowStockDraft by remember(settings.defaultLowStockThreshold) {
    mutableStateOf(settings.defaultLowStockThreshold.toInt().toString())
  }
  var creditLimitDraft by remember(settings.defaultPartyCreditLimit) {
    mutableStateOf(settings.defaultPartyCreditLimit.toLong().toString())
  }
  var gstPortalGstinDraft by remember(settings.gstPortalGstin) {
    mutableStateOf(settings.gstPortalGstin)
  }
  var gstPortalUsernameDraft by remember(settings.gstPortalUsername) {
    mutableStateOf(settings.gstPortalUsername)
  }
  var gstPortalClientIdDraft by remember(settings.gstPortalClientId) {
    mutableStateOf(settings.gstPortalClientId)
  }
  var dispatchCityDraft by remember(settings.defaultDispatchCity) {
    mutableStateOf(settings.defaultDispatchCity)
  }
  var transporterNameDraft by remember(settings.defaultTransporterName) {
    mutableStateOf(settings.defaultTransporterName)
  }
  var transporterIdDraft by remember(settings.defaultTransporterId) {
    mutableStateOf(settings.defaultTransporterId)
  }
  var beneficiaryNameDraft by remember(settings.merchantBeneficiaryName) {
    mutableStateOf(settings.merchantBeneficiaryName)
  }
  var merchantUpiDraft by remember(settings.merchantUpiId) {
    mutableStateOf(settings.merchantUpiId)
  }
  var merchantGatewayKeyDraft by remember(settings.merchantGatewayKeyId) {
    mutableStateOf(settings.merchantGatewayKeyId)
  }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Column {
            Text(
              text = "Finboss Settings & Logic",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Customize print sizes, bill styles, GST rules & automation",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // 1. PRINTING & INVOICE STYLE LOGIC
      SettingsSectionCard(
        icon = Icons.Filled.Print,
        title = "Printing & Bill Design Logic",
      ) {
        Text("Default Paper Size", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          PrintPaperSize.entries.forEach { size ->
            FilterChip(
              selected = settings.defaultPaperSize == size,
              onClick = { onUpdateSettings { it.copy(defaultPaperSize = size) } },
              label = { Text(size.label) },
              modifier = Modifier.testTag("settings_paper_${size.key}"),
            )
          }
        }
        Text(
          text = settings.defaultPaperSize.subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()
        Text("Default Invoice Print Style", style = MaterialTheme.typography.labelLarge)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          InvoicePrintStyle.entries.forEach { style ->
            val selected = settings.defaultPrintStyle == style
            Card(
              shape = MaterialTheme.shapes.small,
              colors =
                CardDefaults.cardColors(
                  containerColor =
                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surface,
                ),
              border =
                BorderStroke(
                  width = if (selected) 1.5.dp else 1.dp,
                  color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { onUpdateSettings { it.copy(defaultPrintStyle = style) } }
                  .testTag("settings_style_${style.key}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(style.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                  Text(style.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected) {
                  Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall) {
                    Text(
                      text = "ACTIVE",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onPrimary,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                  }
                }
              }
            }
          }
        }

        HorizontalDivider()
        SettingsSwitchRow(
          title = "Ask Size & Style Before Every Print / PDF",
          subtitle = "Opens quick picker before generating print or PDF",
          checked = settings.askPrintOptionsBeforePrint,
          testTag = "settings_switch_ask_print",
          onCheckedChange = { v -> onUpdateSettings { it.copy(askPrintOptionsBeforePrint = v) } },
        )
        if (!isPlainNonGst) {
          SettingsSwitchRow(
            title = "Show HSN / SAC Code Column on Bills",
            subtitle = "Include item HSN codes in Tally, Modern & Thermal prints",
            checked = settings.showHsnOnInvoicePrint,
            testTag = "settings_switch_show_hsn",
            onCheckedChange = { v -> onUpdateSettings { it.copy(showHsnOnInvoicePrint = v) } },
          )
        }
        SettingsSwitchRow(
          title = "Show Full Address, State & PIN Code",
          subtitle = "Prints business & customer address, state code and PIN",
          checked = settings.showAddressAndStateOnPrint,
          testTag = "settings_switch_show_address",
          onCheckedChange = { v -> onUpdateSettings { it.copy(showAddressAndStateOnPrint = v) } },
        )
        SettingsSwitchRow(
          title = "Show Amount in Words (Rupees Only)",
          subtitle = "Displays Indian numbering total in words on A4/A5 bills",
          checked = settings.showAmountInWordsOnPrint,
          testTag = "settings_switch_show_words",
          onCheckedChange = { v -> onUpdateSettings { it.copy(showAmountInWordsOnPrint = v) } },
        )

        OutlinedTextField(
          value = footerDraft,
          onValueChange = {
            footerDraft = it
            onUpdateSettings { s -> s.copy(invoiceFooterNote = it) }
          },
          label = { Text("Invoice Footer Note") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_footer_input"),
        )
        OutlinedTextField(
          value = termsDraft,
          onValueChange = {
            termsDraft = it
            onUpdateSettings { s -> s.copy(invoiceTermsText = it) }
          },
          label = { Text("Terms / Declaration on Bill") },
          maxLines = 2,
          modifier = Modifier.fillMaxWidth().testTag("settings_terms_input"),
        )
      }

      // 2. BILLING & GST RULES
      SettingsSectionCard(
        icon = Icons.AutoMirrored.Filled.ReceiptLong,
        title = "Billing & GST Logic",
      ) {
        OutlinedTextField(
          value = prefixDraft,
          onValueChange = {
            val up = it.uppercase().take(18)
            prefixDraft = up
            if (up.isNotBlank()) {
              onUpdateSettings { s -> s.copy(invoicePrefix = up) }
            }
          },
          label = { Text("Default Invoice Number Prefix") },
          placeholder = { Text("FB-2026-") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_invoice_prefix_input"),
        )

        if (!isPlainNonGst) {
          Text("Default Bill Format", style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = settings.defaultInvoiceType == GstInvoice.TYPE_TAX_INVOICE,
              onClick = { onUpdateSettings { it.copy(defaultInvoiceType = GstInvoice.TYPE_TAX_INVOICE) } },
              label = { Text("GST Tax Invoice") },
            )
            FilterChip(
              selected = settings.defaultInvoiceType == GstInvoice.TYPE_POS_BILL,
              onClick = { onUpdateSettings { it.copy(defaultInvoiceType = GstInvoice.TYPE_POS_BILL) } },
              label = { Text("POS Quick Bill") },
            )
          }

          Text("Default GST Rate (%)", style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.0, 5.0, 12.0, 18.0, 28.0).forEach { slab ->
              FilterChip(
                selected = settings.defaultGstSlabPercent == slab,
                onClick = { onUpdateSettings { it.copy(defaultGstSlabPercent = slab) } },
                label = { Text("${slab.toInt()}%") },
              )
            }
          }

          SettingsSwitchRow(
            title = "Auto CGST+SGST vs IGST from State",
            subtitle = "Compares Business State & Party State automatically on bills",
            checked = settings.autoDetectGstModeFromState,
            testTag = "settings_switch_auto_state_gst",
            onCheckedChange = { v -> onUpdateSettings { it.copy(autoDetectGstModeFromState = v) } },
          )
        }

        Text("Default Sale Payment Mode", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(
            GstInvoice.PAYMENT_UPI to "UPI",
            GstInvoice.PAYMENT_CASH to "Cash",
            GstInvoice.PAYMENT_CARD to "Card",
            GstInvoice.PAYMENT_CREDIT to "Credit",
          ).forEach { (key, label) ->
            FilterChip(
              selected = settings.defaultPaymentMethod == key,
              onClick = { onUpdateSettings { it.copy(defaultPaymentMethod = key) } },
              label = { Text(label) },
            )
          }
        }
      }

      // 2B. GST PORTAL E-INVOICE (IRP) & E-WAY BILL INTEGRATION
      if (!isPlainNonGst) {
        SettingsSectionCard(
          icon = Icons.Filled.QrCode2,
          title = "GST Portal E-Invoice & E-Way Bill API",
        ) {
          Text(
            text = "NIC GST Portal / GSP Connection Mode",
            style = MaterialTheme.typography.labelLarge,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
              "sandbox" to "Sandbox IRP (Test)",
              "live" to "Live NIC GSP API",
              "offline_json" to "Offline NIC JSON Export",
            ).forEach { (modeKey, modeLabel) ->
              FilterChip(
                selected = settings.gstPortalMode == modeKey,
                onClick = { onUpdateSettings { it.copy(gstPortalMode = modeKey) } },
                label = { Text(modeLabel) },
                modifier = Modifier.testTag("settings_gst_portal_mode_$modeKey"),
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = gstPortalGstinDraft,
              onValueChange = { raw ->
                val clean = raw.uppercase().take(15)
                gstPortalGstinDraft = clean
                onUpdateSettings { it.copy(gstPortalGstin = clean) }
              },
              label = { Text("Seller Portal GSTIN") },
              placeholder = { Text("27AABCF9012R1Z5") },
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("settings_gst_portal_gstin_input"),
            )
            OutlinedTextField(
              value = gstPortalUsernameDraft,
              onValueChange = { raw ->
                val clean = raw.trim().take(50)
                gstPortalUsernameDraft = clean
                onUpdateSettings { it.copy(gstPortalUsername = clean) }
              },
              label = { Text("IRP / EWB API Username") },
              placeholder = { Text("API_USER_01") },
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("settings_gst_portal_user_input"),
            )
          }

          OutlinedTextField(
            value = gstPortalClientIdDraft,
            onValueChange = { raw ->
              val clean = raw.trim().take(60)
              gstPortalClientIdDraft = clean
              onUpdateSettings { it.copy(gstPortalClientId = clean) }
            },
            label = { Text("GSP Client ID / ERP Bridge Key (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("settings_gst_portal_client_id_input"),
          )

          HorizontalDivider()
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("Default E-Way Bill Dispatch & Transporter", style = MaterialTheme.typography.labelLarge)
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = dispatchCityDraft,
              onValueChange = { raw ->
                dispatchCityDraft = raw.take(60)
                onUpdateSettings { it.copy(defaultDispatchCity = raw.trim().take(60)) }
              },
              label = { Text("Default From City") },
              placeholder = { Text("Bhubaneswar / Mumbai") },
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("settings_default_dispatch_city_input"),
            )
            OutlinedTextField(
              value = transporterNameDraft,
              onValueChange = { raw ->
                transporterNameDraft = raw.take(80)
                onUpdateSettings { it.copy(defaultTransporterName = raw.trim().take(80)) }
              },
              label = { Text("Default Transporter") },
              placeholder = { Text("VRL Logistics") },
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("settings_default_transporter_name_input"),
            )
          }

          OutlinedTextField(
            value = transporterIdDraft,
            onValueChange = { raw ->
              val clean = raw.uppercase().take(15)
              transporterIdDraft = clean
              onUpdateSettings { it.copy(defaultTransporterId = clean) }
            },
            label = { Text("Default Transporter ID / GSTIN (15-char)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("settings_default_transporter_id_input"),
          )
        }
      }

      // 3. INVENTORY & PURCHASE AUTOMATION
      SettingsSectionCard(
        icon = Icons.Filled.Inventory2,
        title = "Inventory & Purchase Automation",
      ) {
        OutlinedTextField(
          value = lowStockDraft,
          onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() }
            lowStockDraft = filtered
            filtered.toDoubleOrNull()?.let { v ->
              onUpdateSettings { it.copy(defaultLowStockThreshold = v) }
            }
          },
          label = { Text("Default Low-Stock Alert Quantity") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_low_stock_input"),
        )
        SettingsSwitchRow(
          title = "Auto-Deduct Stock on Invoice Sale",
          subtitle = "Reduces SKU quantity automatically when creating/editing bills",
          checked = settings.autoDeductStockOnSale,
          testTag = "settings_switch_auto_deduct",
          onCheckedChange = { v -> onUpdateSettings { it.copy(autoDeductStockOnSale = v) } },
        )
        SettingsSwitchRow(
          title = "Allow Billing When Stock is Zero",
          subtitle = "Allows selling an SKU even if stock falls to 0",
          checked = settings.allowNegativeStockSale,
          testTag = "settings_switch_allow_zero_stock",
          onCheckedChange = { v -> onUpdateSettings { it.copy(allowNegativeStockSale = v) } },
        )
        SettingsSwitchRow(
          title = "Auto-Log Purchases to Expenses & ITC",
          subtitle = "Each purchase entry creates an expense record with GST Input Credit",
          checked = settings.autoRecordPurchaseInExpenses,
          testTag = "settings_switch_auto_purchase_exp",
          onCheckedChange = { v -> onUpdateSettings { it.copy(autoRecordPurchaseInExpenses = v) } },
        )
        Text("Default Purchase Payment Mode", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(
            ErpExpense.METHOD_UPI to "UPI",
            ErpExpense.METHOD_CASH to "Cash",
            ErpExpense.METHOD_BANK to "Bank",
            ErpExpense.METHOD_CARD to "Card",
          ).forEach { (key, label) ->
            FilterChip(
              selected = settings.defaultPurchasePaymentMethod == key,
              onClick = { onUpdateSettings { it.copy(defaultPurchasePaymentMethod = key) } },
              label = { Text(label) },
            )
          }
        }
      }

      // 4. PARTIES & SYNC RULES
      SettingsSectionCard(
        icon = Icons.Filled.Groups,
        title = "Parties & Cloud Sync Logic",
      ) {
        OutlinedTextField(
          value = creditLimitDraft,
          onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() }
            creditLimitDraft = filtered
            filtered.toDoubleOrNull()?.let { v ->
              onUpdateSettings { it.copy(defaultPartyCreditLimit = v) }
            }
          },
          label = { Text("Default Party Credit Limit") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_credit_limit_input"),
        )
        SettingsSwitchRow(
          title = "Credit Limit Warning on Billing",
          subtitle = "Highlights warning when customer ledger due exceeds credit limit",
          checked = settings.warnOnCreditLimitExceeded,
          testTag = "settings_switch_warn_credit",
          onCheckedChange = { v -> onUpdateSettings { it.copy(warnOnCreditLimitExceeded = v) } },
        )
        SettingsSwitchRow(
          title = "Auto Cloud Sync When Online",
          subtitle = "Keeps offline Firestore queue synced automatically in real time",
          checked = settings.autoSyncWhenOnline,
          testTag = "settings_switch_auto_sync",
          onCheckedChange = { v -> onUpdateSettings { it.copy(autoSyncWhenOnline = v) } },
        )
      }

      // 5. PRIVATE MERCHANT PAYOUT CONFIGURATION (Admin / Owner Only)
      SettingsSectionCard(
        icon = Icons.Filled.WorkspacePremium,
        title = "Merchant Payout & Subscription Gateway (Admin Only)",
      ) {
        Text(
          text = "Configure merchant receiving account (Strictly private — never displayed to users).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = beneficiaryNameDraft,
          onValueChange = { raw ->
            beneficiaryNameDraft = raw.take(80)
            onUpdateSettings { it.copy(merchantBeneficiaryName = raw.trim().take(80)) }
          },
          label = { Text("Beneficiary Name") },
          placeholder = { Text("Raghav Enterprises") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_merchant_beneficiary_input"),
        )
        OutlinedTextField(
          value = merchantUpiDraft,
          onValueChange = { raw ->
            merchantUpiDraft = raw.trim().take(60)
            onUpdateSettings { it.copy(merchantUpiId = raw.trim().take(60)) }
          },
          label = { Text("Merchant UPI ID") },
          placeholder = { Text("anantbharat@ybl") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_merchant_upi_input"),
        )
        OutlinedTextField(
          value = merchantGatewayKeyDraft,
          onValueChange = { raw ->
            merchantGatewayKeyDraft = raw.trim().take(80)
            onUpdateSettings { it.copy(merchantGatewayKeyId = raw.trim().take(80)) }
          },
          label = { Text("Payment Gateway Key ID (Optional - Razorpay/Cashfree)") },
          placeholder = { Text("rzp_live_xxxxxxxxxx") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_merchant_gateway_key_input"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onResetDefaults,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("settings_reset_defaults_button"),
        ) {
          Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Reset Defaults")
        }
        Button(
          onClick = onDismiss,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("settings_done_button"),
        ) {
          Text("Save & Close")
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun SettingsSectionCard(
  icon: ImageVector,
  title: String,
  content: @Composable () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      }
      content()
    }
  }
}

@Composable
private fun SettingsSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  testTag: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrintOptionsPickerDialog(
  invoiceNumber: String,
  isPdfExport: Boolean,
  isPlainNonGst: Boolean,
  defaultSettings: ErpAppSettings,
  onConfirm: (InvoicePrintOptions) -> Unit,
  onOpenAllSettings: () -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedSize by remember(defaultSettings) { mutableStateOf(defaultSettings.defaultPaperSize) }
  var selectedStyle by remember(defaultSettings) { mutableStateOf(defaultSettings.defaultPrintStyle) }
  var showHsn by remember(defaultSettings) { mutableStateOf(defaultSettings.showHsnOnInvoicePrint) }
  var showAddress by remember(defaultSettings) { mutableStateOf(defaultSettings.showAddressAndStateOnPrint) }
  var showWords by remember(defaultSettings) { mutableStateOf(defaultSettings.showAmountInWordsOnPrint) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
          imageVector = if (isPdfExport) Icons.Filled.PictureAsPdf else Icons.Filled.Print,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(if (isPdfExport) "PDF Format • $invoiceNumber" else "Print Format • $invoiceNumber")
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Paper / Roll Size", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          PrintPaperSize.entries.forEach { size ->
            FilterChip(
              selected = selectedSize == size,
              onClick = {
                selectedSize = size
                if (size.isThermal) selectedStyle = InvoicePrintStyle.THERMAL_POS
              },
              label = { Text(size.label) },
              modifier = Modifier.testTag("print_picker_size_${size.key}"),
            )
          }
        }

        Text("Bill Design Style", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          InvoicePrintStyle.entries.forEach { style ->
            FilterChip(
              selected = selectedStyle == style,
              onClick = { selectedStyle = style },
              label = { Text(style.label) },
              modifier = Modifier.testTag("print_picker_style_${style.key}"),
            )
          }
        }

        if (!isPlainNonGst) {
          SettingsSwitchRow(
            title = "Include HSN / SAC Column",
            subtitle = "Print item HSN numbers",
            checked = showHsn,
            testTag = "print_picker_hsn",
            onCheckedChange = { showHsn = it },
          )
        }
        SettingsSwitchRow(
          title = "Include Address, State & PIN",
          subtitle = "Print business & customer address details",
          checked = showAddress,
          testTag = "print_picker_address",
          onCheckedChange = { showAddress = it },
        )
        if (!selectedSize.isThermal) {
          SettingsSwitchRow(
            title = "Include Amount in Words",
            subtitle = "Show Rupees total in words",
            checked = showWords,
            testTag = "print_picker_words",
            onCheckedChange = { showWords = it },
          )
        }

        TextButton(
          onClick = {
            onDismiss()
            onOpenAllSettings()
          },
          modifier = Modifier.testTag("print_picker_open_settings"),
        ) {
          Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Change Defaults in All App Settings")
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(
            InvoicePrintOptions(
              paperSize = selectedSize,
              style = selectedStyle,
              showHsn = showHsn,
              showAddressAndState = showAddress,
              showAmountInWords = showWords,
              footerNote = defaultSettings.invoiceFooterNote,
              termsText = defaultSettings.invoiceTermsText,
            )
          )
        },
        modifier = Modifier.testTag("print_picker_confirm"),
      ) {
        Text(if (isPdfExport) "Save PDF" else "Print Now")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
