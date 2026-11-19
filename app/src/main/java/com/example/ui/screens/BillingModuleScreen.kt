package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessProfile
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.model.InvoiceLineItem
import com.example.data.reference.IndiaStates
import com.example.data.sync.ErpAppSettings
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.viewmodel.UiState
import com.example.util.InvoicePdfPrinter
import com.example.util.InvoicePrintOptions
import com.example.util.WhatsAppShareHelper
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BillingModuleContent(
  invoicesState: UiState<List<GstInvoice>>,
  parties: List<ErpParty>,
  inventory: List<InventoryItem>,
  profile: BusinessProfile?,
  currency: String,
  isPlainNonGst: Boolean = false,
  appSettings: ErpAppSettings = ErpAppSettings(),
  isBusy: Boolean,
  onCreateInvoice: (
    invoiceNumber: String,
    party: ErpParty?,
    walkInPartyName: String,
    walkInPartyGstin: String,
    invoiceType: String,
    taxMode: String,
    paymentMethod: String,
    discountAmount: Double,
    paidAmount: Double,
    notes: String,
    lineItems: List<InvoiceLineItem>,
    walkInPartyAddress: String,
    walkInPartyState: String,
    walkInPartyPincode: String,
    onDone: () -> Unit,
  ) -> Unit,
  onUpdateInvoice: (
    existingInvoice: GstInvoice,
    invoiceNumber: String,
    party: ErpParty?,
    walkInPartyName: String,
    walkInPartyGstin: String,
    invoiceType: String,
    taxMode: String,
    paymentMethod: String,
    discountAmount: Double,
    paidAmount: Double,
    notes: String,
    lineItems: List<InvoiceLineItem>,
    walkInPartyAddress: String,
    walkInPartyState: String,
    walkInPartyPincode: String,
    onDone: () -> Unit,
  ) -> Unit,
  onRecordPayment: (
    invoice: GstInvoice,
    additionalPayment: Double,
    paymentMethod: String,
    onDone: () -> Unit,
  ) -> Unit,
  onDeleteInvoice: (GstInvoice) -> Unit,
  onNotifyMessage: (String) -> Unit,
  onOpenAppSettings: () -> Unit = {},
  showNewInvoiceDialog: Boolean,
  onDismissNewInvoiceDialog: () -> Unit,
  onOpenNewInvoiceDialog: () -> Unit,
  canCreateInvoices: Boolean = true,
  canEditDeleteInvoices: Boolean = true,
  canPrintInvoices: Boolean = true,
  canRecordPayments: Boolean = true,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var searchQuery by rememberSaveable { mutableStateOf("") }
  var statusFilter by rememberSaveable { mutableStateOf("all") }
  var selectedInvoice by remember { mutableStateOf<GstInvoice?>(null) }
  var editingInvoice by remember { mutableStateOf<GstInvoice?>(null) }
  var invoicePendingDelete by remember { mutableStateOf<GstInvoice?>(null) }
  var invoicePendingPdfExport by remember { mutableStateOf<GstInvoice?>(null) }
  var pendingPdfOptions by remember { mutableStateOf(InvoicePrintOptions.fromSettings(appSettings)) }
  var pendingPrintOrPdfRequest by remember { mutableStateOf<Pair<GstInvoice, Boolean>?>(null) }
  val openPortal: (String) -> Unit = { url ->
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
      .onFailure { onNotifyMessage("No browser is available to open the official portal.") }
  }
  val openEInvoiceFor = { openPortal("https://einvoice1.gst.gov.in/") }
  val openEWayBillFor = { openPortal("https://ewaybillgst.gov.in/") }

  val downloadPdfLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
      val target = invoicePendingPdfExport
      val opts = pendingPdfOptions
      invoicePendingPdfExport = null
      if (uri == null || target == null) return@rememberLauncherForActivityResult
      coroutineScope.launch {
        val result =
          withContext(Dispatchers.IO) {
            runCatching {
              context.contentResolver.openOutputStream(uri)?.use { stream ->
                InvoicePdfPrinter.writeInvoicePdfToStream(target, profile, currency, stream, opts)
              } ?: error("Unable to open destination PDF file")
            }
          }
        result
          .onSuccess { onNotifyMessage("Downloaded ${target.invoiceNumber}.pdf (${opts.paperSize.label} • ${opts.style.label})") }
          .onFailure { err ->
            onNotifyMessage(err.localizedMessage ?: "Failed to save invoice PDF")
          }
      }
    }

  val executeDownloadPdf: (GstInvoice, InvoicePrintOptions) -> Unit = { inv, opts ->
    invoicePendingPdfExport = inv
    pendingPdfOptions = opts
    val fileName = InvoicePdfPrinter.buildSafePdfFileName(inv.invoiceNumber)
    downloadPdfLauncher.launch(fileName)
  }

  val executePrintInvoice: (GstInvoice, InvoicePrintOptions) -> Unit = { inv, opts ->
    runCatching {
      InvoicePdfPrinter.printInvoice(context, inv, profile, currency, opts)
    }.onFailure { err ->
      onNotifyMessage(err.localizedMessage ?: "Unable to start print job")
    }
  }

  val triggerDownloadPdf: (GstInvoice) -> Unit = { inv ->
    if (appSettings.askPrintOptionsBeforePrint) {
      pendingPrintOrPdfRequest = inv to true
    } else {
      executeDownloadPdf(inv, InvoicePrintOptions.fromSettings(appSettings))
    }
  }

  val triggerPrintInvoice: (GstInvoice) -> Unit = { inv ->
    if (appSettings.askPrintOptionsBeforePrint) {
      pendingPrintOrPdfRequest = inv to false
    } else {
      executePrintInvoice(inv, InvoicePrintOptions.fromSettings(appSettings))
    }
  }

  val invoices = (invoicesState as? UiState.Success)?.data.orEmpty()
  val totalBilled = remember(invoices) { invoices.sumOf { it.grandTotal } }
  val totalDue = remember(invoices) { invoices.sumOf { it.balanceDue } }

  val filteredInvoices =
    remember(invoices, statusFilter, searchQuery) {
      invoices.filter { inv ->
        val matchesStatus = statusFilter == "all" || inv.status == statusFilter
        val q = searchQuery.trim().lowercase()
        val matchesQuery =
          q.isEmpty() ||
            inv.invoiceNumber.lowercase().contains(q) ||
            inv.partyName.lowercase().contains(q)
        matchesStatus && matchesQuery
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SummaryStatPill(
          title = "Total Invoiced",
          value = formatErpMoney(currency, totalBilled),
          accent = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.weight(1f),
        )
        SummaryStatPill(
          title = "Unpaid Balance",
          value = formatErpMoney(currency, totalDue),
          accent = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text("Search by invoice # or customer") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("billing_search_input"),
      )
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val filters =
          listOf(
            "all" to "All (${invoices.size})",
            GstInvoice.STATUS_PAID to "Paid",
            GstInvoice.STATUS_PARTIAL to "Partial",
            GstInvoice.STATUS_UNPAID to "Unpaid",
          )
        items(filters) { (key, label) ->
          FilterChip(
            selected = statusFilter == key,
            onClick = { statusFilter = key },
            label = { Text(label) },
          )
        }
      }
    }

    when (invoicesState) {
      is UiState.Loading -> {
        item {
          Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
      }
      is UiState.Error -> {
        item {
          Text(
            text = invoicesState.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
      is UiState.Success -> {
        if (filteredInvoices.isEmpty()) {
          item {
            ModuleEmptyCard(
              icon = Icons.AutoMirrored.Filled.ReceiptLong,
              title =
                if (invoices.isEmpty()) {
                  if (isPlainNonGst) "No Plain Bills Yet" else "No GST Invoices Yet"
                } else "No Matching Invoices",
              subtitle =
                if (invoices.isEmpty()) {
                  if (isPlainNonGst)
                    "Create clean plain non-GST bills with 0% tax, automatic stock deduction, and Party Ledger sync."
                  else
                    "Create your first GST tax invoice or POS bill with automatic stock deduction and Party Ledger sync."
                } else "Try adjusting your search or payment status filter.",
              actionLabel =
                if (canCreateInvoices) "Create Invoice" else null,
              onAction = if (canCreateInvoices) onOpenNewInvoiceDialog else null,
            )
          }
        } else {
          items(filteredInvoices, key = { it.id }) { invoice ->
            val buyerPhone =
              parties.firstOrNull {
                (invoice.partyId.isNotBlank() && it.id == invoice.partyId) ||
                  it.name.equals(invoice.partyName, ignoreCase = true)
              }?.phone
            InvoiceListCard(
              invoice = invoice,
              currency = currency,
              isPlainNonGst = isPlainNonGst,
              canPrint = canPrintInvoices,
              canEditDelete = canEditDeleteInvoices,
              onClick = { selectedInvoice = invoice },
              onPrint = { triggerPrintInvoice(invoice) },
              onDownloadPdf = { triggerDownloadPdf(invoice) },
              onShareWhatsApp = {
                val msg =
                  WhatsAppShareHelper.formatInvoiceSummary(
                    invoice = invoice,
                    profile = profile,
                    currency = currency,
                    isPlainNonGst = isPlainNonGst,
                  )
                WhatsAppShareHelper.shareTextToWhatsApp(
                  context = context,
                  text = msg,
                  rawPhoneNumber = buyerPhone,
                  chooserTitle = "Share Invoice on WhatsApp",
                )
              },
              onEdit = { editingInvoice = invoice },
              onDelete = { invoicePendingDelete = invoice },
              onOpenEInvoice = { openEInvoiceFor() },
              onOpenEWayBill = { openEWayBillFor() },
            )
          }
        }
      }
    }
  }

  if (showNewInvoiceDialog) {
    val nextSequence = invoices.size + 1
    NewInvoiceDialog(
      defaultInvoiceNumber = "${appSettings.invoicePrefix}${nextSequence.toString().padStart(3, '0')}",
      parties = parties,
      inventory = inventory,
      profile = profile,
      appSettings = appSettings,
      currency = currency,
      isPlainNonGst = isPlainNonGst,
      isBusy = isBusy,
      existingInvoice = null,
      onDismiss = onDismissNewInvoiceDialog,
      onCreate = { invNo, party, walkInName, walkInGstin, type, taxMode, payMethod, discount, paid, notes, lines, walkAddr, walkState, walkPin ->
        onCreateInvoice(
          invNo,
          party,
          walkInName,
          walkInGstin,
          type,
          taxMode,
          payMethod,
          discount,
          paid,
          notes,
          lines,
          walkAddr,
          walkState,
          walkPin,
          onDismissNewInvoiceDialog,
        )
      },
    )
  }

  editingInvoice?.let { targetToEdit ->
    NewInvoiceDialog(
      defaultInvoiceNumber = targetToEdit.invoiceNumber,
      parties = parties,
      inventory = inventory,
      profile = profile,
      appSettings = appSettings,
      currency = currency,
      isPlainNonGst = isPlainNonGst,
      isBusy = isBusy,
      existingInvoice = targetToEdit,
      onDismiss = { editingInvoice = null },
      onCreate = { invNo, party, walkInName, walkInGstin, type, taxMode, payMethod, discount, paid, notes, lines, walkAddr, walkState, walkPin ->
        onUpdateInvoice(
          targetToEdit,
          invNo,
          party,
          walkInName,
          walkInGstin,
          type,
          taxMode,
          payMethod,
          discount,
          paid,
          notes,
          lines,
          walkAddr,
          walkState,
          walkPin,
        ) {
          editingInvoice = null
        }
      },
    )
  }

  val liveSelected =
    selectedInvoice?.let { current -> invoices.firstOrNull { it.id == current.id } }
  if (liveSelected != null) {
    val liveBuyerPhone =
      parties.firstOrNull {
        (liveSelected.partyId.isNotBlank() && it.id == liveSelected.partyId) ||
          it.name.equals(liveSelected.partyName, ignoreCase = true)
      }?.phone
    InvoiceDetailSheet(
      invoice = liveSelected,
      profile = profile,
      appSettings = appSettings,
      buyerPhone = liveBuyerPhone,
      currency = currency,
      isPlainNonGst = isPlainNonGst,
      canPrint = canPrintInvoices,
      canEditDelete = canEditDeleteInvoices,
      canRecordPayments = canRecordPayments,
      isBusy = isBusy,
      onDismiss = { selectedInvoice = null },
      onPrint = { triggerPrintInvoice(liveSelected) },
      onDownloadPdf = { triggerDownloadPdf(liveSelected) },
      onEdit = {
        selectedInvoice = null
        editingInvoice = liveSelected
      },
      onRecordPayment = { amount, method ->
        onRecordPayment(liveSelected, amount, method) {}
      },
      onDelete = {
        invoicePendingDelete = liveSelected
      },
      onOpenEInvoice = { openEInvoiceFor() },
      onOpenEWayBill = { openEWayBillFor() },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvoiceListCard(
  invoice: GstInvoice,
  currency: String,
  isPlainNonGst: Boolean,
  canPrint: Boolean = true,
  canEditDelete: Boolean = true,
  onClick: () -> Unit,
  onPrint: () -> Unit,
  onDownloadPdf: () -> Unit,
  onShareWhatsApp: () -> Unit = {},
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onOpenEInvoice: () -> Unit = {},
  onOpenEWayBill: () -> Unit = {},
) {
  val (statusLabel, bg, fg) =
    when (invoice.status) {
      GstInvoice.STATUS_PAID ->
        Triple(
          "PAID",
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
        )
      GstInvoice.STATUS_PARTIAL ->
        Triple(
          "PARTIAL",
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
        )
      else ->
        Triple(
          "UNPAID",
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.error,
        )
    }

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .testTag("invoice_card_${invoice.invoiceNumber}"),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier =
            Modifier.size(42.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = invoice.invoiceNumber,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Surface(color = bg, shape = RoundedCornerShape(50)) {
              Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
          Text(
            text = invoice.partyName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              if (isPlainNonGst) {
                "${invoice.lineItems.size} items • ${invoice.paymentMethod.uppercase()} • Plain Bill"
              } else {
                "${invoice.lineItems.size} items • ${invoice.paymentMethod.uppercase()} • GST ${formatErpMoney(currency, invoice.gstTotal)}"
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (!isPlainNonGst && (invoice.hasEInvoice || invoice.hasEWayBill)) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(top = 2.dp),
            ) {
              if (invoice.hasEInvoice) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = RoundedCornerShape(50),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Verified,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(12.dp),
                    )
                    Text(
                      text = "Legacy IRN · verify on portal",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                }
              }
              if (invoice.hasEWayBill) {
                Surface(
                  color = MaterialTheme.colorScheme.tertiaryContainer,
                  shape = RoundedCornerShape(50),
                ) {
                  Text(
                    text = "Legacy EWB #${invoice.ewbNumber} (${invoice.distanceKm} KM)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  )
                }
              }
            }
          }
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = formatErpMoney(currency, invoice.grandTotal),
            style = MonoMetricMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (invoice.balanceDue > 0.0) {
            Text(
              text = "Due ${formatErpMoney(currency, invoice.balanceDue)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.tertiary,
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.Center,
      ) {
        if (canPrint) {
          TextButton(
            onClick = onPrint,
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_card_print_${invoice.invoiceNumber}"),
          ) {
            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Print")
          }
          TextButton(
            onClick = onDownloadPdf,
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_card_pdf_${invoice.invoiceNumber}"),
          ) {
            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("PDF")
          }
        }
        TextButton(
          onClick = onShareWhatsApp,
          modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_card_whatsapp_${invoice.invoiceNumber}"),
        ) {
          Icon(
            Icons.Filled.Share,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("WhatsApp", color = MaterialTheme.colorScheme.secondary)
        }
        if (!isPlainNonGst && invoice.taxMode != GstInvoice.TAX_MODE_NONE) {
          TextButton(
            onClick = onOpenEInvoice,
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_card_einvoice_${invoice.invoiceNumber}"),
          ) {
            Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("E-Invoice Portal")
          }
          TextButton(
            onClick = onOpenEWayBill,
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_card_ewaybill_${invoice.invoiceNumber}"),
          ) {
            Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("E-Way Bill Portal")
          }
        } else if (canEditDelete) {
          TextButton(
            onClick = onEdit,
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_card_edit_${invoice.invoiceNumber}"),
          ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit")
          }
          TextButton(
            onClick = onDelete,
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_card_delete_${invoice.invoiceNumber}"),
          ) {
            Icon(
              Icons.Filled.DeleteOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Delete", color = MaterialTheme.colorScheme.error)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewInvoiceDialog(
  defaultInvoiceNumber: String,
  parties: List<ErpParty>,
  inventory: List<InventoryItem>,
  profile: BusinessProfile? = null,
  appSettings: ErpAppSettings = ErpAppSettings(),
  currency: String,
  isPlainNonGst: Boolean = false,
  isBusy: Boolean,
  existingInvoice: GstInvoice? = null,
  onDismiss: () -> Unit,
  onCreate: (
    invoiceNumber: String,
    party: ErpParty?,
    walkInPartyName: String,
    walkInPartyGstin: String,
    invoiceType: String,
    taxMode: String,
    paymentMethod: String,
    discountAmount: Double,
    paidAmount: Double,
    notes: String,
    lineItems: List<InvoiceLineItem>,
    walkInPartyAddress: String,
    walkInPartyState: String,
    walkInPartyPincode: String,
  ) -> Unit,
) {
  val isEditing = existingInvoice != null
  var invoiceNumber by remember(existingInvoice?.id) {
    mutableStateOf(existingInvoice?.invoiceNumber ?: defaultInvoiceNumber)
  }
  var invoiceType by remember(existingInvoice?.id, isPlainNonGst, appSettings.defaultInvoiceType) {
    mutableStateOf(
      if (isPlainNonGst) GstInvoice.TYPE_POS_BILL
      else (existingInvoice?.invoiceType ?: appSettings.defaultInvoiceType)
    )
  }
  var taxMode by remember(existingInvoice?.id, isPlainNonGst) {
    mutableStateOf(
      if (isPlainNonGst) GstInvoice.TAX_MODE_NONE
      else (existingInvoice?.taxMode ?: GstInvoice.TAX_MODE_CGST_SGST)
    )
  }
  var selectedParty by remember(existingInvoice?.id) {
    mutableStateOf(
      existingInvoice?.partyId?.takeIf { it.isNotBlank() }?.let { pid ->
        parties.firstOrNull { it.id == pid }
      }
    )
  }
  var walkInName by remember(existingInvoice?.id) {
    mutableStateOf(
      if (existingInvoice != null && existingInvoice.partyId.isBlank()) existingInvoice.partyName
      else "Walk-in Customer"
    )
  }
  var walkInGstin by remember(existingInvoice?.id) {
    mutableStateOf(
      if (existingInvoice != null && existingInvoice.partyId.isBlank()) existingInvoice.partyGstin
      else ""
    )
  }
  var walkInAddress by remember(existingInvoice?.id) {
    mutableStateOf(
      if (existingInvoice != null && existingInvoice.partyId.isBlank()) existingInvoice.partyAddress
      else ""
    )
  }
  var walkInState by remember(existingInvoice?.id) {
    mutableStateOf(
      if (existingInvoice != null && existingInvoice.partyId.isBlank()) existingInvoice.partyState
      else ""
    )
  }
  var walkInPincode by remember(existingInvoice?.id) {
    mutableStateOf(
      if (existingInvoice != null && existingInvoice.partyId.isBlank()) existingInvoice.partyPincode
      else ""
    )
  }
  var paymentMethod by remember(existingInvoice?.id, appSettings.defaultPaymentMethod) {
    mutableStateOf(existingInvoice?.paymentMethod ?: appSettings.defaultPaymentMethod)
  }
  var discountText by remember(existingInvoice?.id) {
    mutableStateOf(
      existingInvoice?.discountAmount?.let { if (it == 0.0) "0" else it.toString().removeSuffix(".0") } ?: "0"
    )
  }
  var paidText by remember(existingInvoice?.id) {
    mutableStateOf(
      existingInvoice?.paidAmount?.let { it.toString().removeSuffix(".0") } ?: ""
    )
  }
  var notes by remember(existingInvoice?.id) {
    mutableStateOf(existingInvoice?.notes ?: "")
  }

  val buyerState = selectedParty?.state?.ifBlank { "" } ?: walkInState
  LaunchedEffect(buyerState, profile?.state, isPlainNonGst, appSettings.autoDetectGstModeFromState) {
    if (
      !isPlainNonGst &&
        appSettings.autoDetectGstModeFromState &&
        !isEditing &&
        buyerState.isNotBlank() &&
        profile?.state.orEmpty().isNotBlank()
    ) {
      IndiaStates.areSameState(profile?.state.orEmpty(), buyerState)?.let { same ->
        taxMode =
          if (same) {
            GstInvoice.TAX_MODE_CGST_SGST
          } else {
            GstInvoice.TAX_MODE_IGST
          }
      }
    }
  }

  // Line item draft inputs
  val lineItems = remember(existingInvoice?.id) {
    mutableStateListOf<InvoiceLineItem>().apply {
      existingInvoice?.lineItems?.let { addAll(it) }
    }
  }
  var itemName by remember { mutableStateOf("") }
  var itemQtyText by remember { mutableStateOf("1") }
  var itemPriceText by remember { mutableStateOf("") }
  var itemGstRate by remember(isPlainNonGst, appSettings.defaultGstSlabPercent) {
    mutableStateOf(if (isPlainNonGst) 0.0 else appSettings.defaultGstSlabPercent)
  }
  var linkedInventoryId by remember { mutableStateOf("") }
  var linkedSku by remember { mutableStateOf("") }
  var linkedHsnCode by remember { mutableStateOf("") }
  var linkedUnit by remember { mutableStateOf("PCS") }

  var errorText by remember { mutableStateOf<String?>(null) }

  val subtotal =
    lineItems.sumOf { it.qty * it.unitPrice }
  val gstTotal =
    if (isPlainNonGst || taxMode == GstInvoice.TAX_MODE_NONE) 0.0
    else lineItems.sumOf { (it.qty * it.unitPrice) * (it.gstRatePercent / 100.0) }
  val discount = (discountText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotal + gstTotal)
  val grandTotal = (subtotal + gstTotal - discount).coerceAtLeast(0.0)
  val projectedPaid =
    if (paidText.isBlank()) {
      if (paymentMethod == GstInvoice.PAYMENT_CREDIT) 0.0 else grandTotal
    } else {
      (paidText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, grandTotal)
    }
  val projectedBalanceDue = (grandTotal - projectedPaid).coerceAtLeast(0.0)
  val creditLimitExceededWarning =
    appSettings.warnOnCreditLimitExceeded &&
      selectedParty != null &&
      selectedParty!!.creditLimit > 0.0 &&
      (selectedParty!!.outstandingBalance + projectedBalanceDue) > selectedParty!!.creditLimit

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        when {
          isEditing && isPlainNonGst -> "Edit Plain Bill ${existingInvoice?.invoiceNumber.orEmpty()}"
          isEditing -> "Edit Invoice ${existingInvoice?.invoiceNumber.orEmpty()}"
          isPlainNonGst -> "Create Plain Non-GST Bill"
          else -> "Create GST Invoice / POS Bill"
        }
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (!isPlainNonGst) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = invoiceType == GstInvoice.TYPE_TAX_INVOICE,
              onClick = { invoiceType = GstInvoice.TYPE_TAX_INVOICE },
              label = { Text("GST Tax Invoice") },
            )
            FilterChip(
              selected = invoiceType == GstInvoice.TYPE_POS_BILL,
              onClick = { invoiceType = GstInvoice.TYPE_POS_BILL },
              label = { Text("POS Quick Bill") },
            )
          }
        }

        OutlinedTextField(
          value = invoiceNumber,
          onValueChange = { invoiceNumber = it.uppercase() },
          label = { Text("Invoice Number") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("invoice_number_input"),
        )

        Text("Customer / Party", style = MaterialTheme.typography.labelLarge)
        if (parties.isNotEmpty()) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
              FilterChip(
                selected = selectedParty == null,
                onClick = { selectedParty = null },
                label = { Text("Walk-in") },
              )
            }
            items(parties.filter { it.partyType == ErpParty.TYPE_CUSTOMER }, key = { it.id }) { party ->
              FilterChip(
                selected = selectedParty?.id == party.id,
                onClick = { selectedParty = party },
                label = { Text(party.name) },
              )
            }
          }
        }
        if (selectedParty != null) {
          val p = selectedParty!!
          val partyAddr = InvoicePdfPrinter.buildAddressLine(p.address, p.state, p.pincode)
          if (partyAddr.isNotBlank()) {
            Text(
              text = "Billing Address: $partyAddr",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          OutlinedTextField(
            value = walkInName,
            onValueChange = { walkInName = it },
            label = { Text("Customer Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          if (!isPlainNonGst && invoiceType == GstInvoice.TYPE_TAX_INVOICE) {
            OutlinedTextField(
              value = walkInGstin,
              onValueChange = { raw ->
                val cleaned = raw.uppercase()
                walkInGstin = cleaned
                if (walkInState.isBlank() && cleaned.length >= 2) {
                  IndiaStates.stateFromGstin(cleaned)?.let { walkInState = it.name }
                }
              },
              label = { Text("Customer GSTIN (Optional)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
          OutlinedTextField(
            value = walkInAddress,
            onValueChange = { walkInAddress = it },
            label = { Text("Customer Address (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("invoice_walkin_address_input"),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatePickerField(
              selectedState = walkInState,
              onStateSelected = { walkInState = it },
              label = "Customer State",
              testTag = "invoice_walkin_state_picker",
              modifier = Modifier.weight(1.35f),
            )
            OutlinedTextField(
              value = walkInPincode,
              onValueChange = { walkInPincode = it.filter { c -> c.isDigit() }.take(6) },
              label = { Text("PIN Code") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(0.85f).testTag("invoice_walkin_pincode_input"),
            )
          }
        }

        if (creditLimitExceededWarning) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Warning: Customer balance will exceed credit limit (${formatErpMoney(currency, selectedParty?.creditLimit ?: 0.0)}).",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(10.dp),
            )
          }
        }

        if (!isPlainNonGst) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Tax Mode", style = MaterialTheme.typography.labelLarge)
            val same = IndiaStates.areSameState(profile?.state.orEmpty(), buyerState)
            if (same != null) {
              Text(
                text = if (same) "Intra-State (${profile?.state})" else "Inter-State (${profile?.state} → $buyerState)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = taxMode == GstInvoice.TAX_MODE_CGST_SGST,
              onClick = { taxMode = GstInvoice.TAX_MODE_CGST_SGST },
              label = { Text("CGST + SGST") },
            )
            FilterChip(
              selected = taxMode == GstInvoice.TAX_MODE_IGST,
              onClick = { taxMode = GstInvoice.TAX_MODE_IGST },
              label = { Text("IGST") },
            )
            FilterChip(
              selected = taxMode == GstInvoice.TAX_MODE_NONE,
              onClick = { taxMode = GstInvoice.TAX_MODE_NONE },
              label = { Text("No GST") },
            )
          }
        }

        HorizontalDivider()

        // Quick pick from Inventory
        if (inventory.isNotEmpty()) {
          Text("Tap SKU to Fill Line Item", style = MaterialTheme.typography.labelLarge)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(inventory, key = { it.id }) { stockItem ->
              FilterChip(
                selected = linkedInventoryId == stockItem.id,
                onClick = {
                  linkedInventoryId = stockItem.id
                  linkedSku = stockItem.sku
                  linkedHsnCode = if (isPlainNonGst) "" else stockItem.hsnCode
                  linkedUnit = stockItem.unit
                  itemName = stockItem.name
                  itemPriceText = stockItem.salePrice.toString().removeSuffix(".0")
                  itemGstRate = if (isPlainNonGst) 0.0 else stockItem.gstRatePercent
                  itemQtyText = "1"
                },
                label = { Text("${stockItem.name} (${formatErpMoney(currency, stockItem.salePrice)})") },
              )
            }
          }
        }

        OutlinedTextField(
          value = itemName,
          onValueChange = {
            itemName = it
            if (it.isBlank()) {
              linkedInventoryId = ""
              linkedHsnCode = ""
            }
          },
          label = { Text("Item Description") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("invoice_item_name_input"),
        )
        if (!isPlainNonGst) {
          HsnSearchSelector(
            hsnCode = linkedHsnCode,
            onHsnCodeChange = { linkedHsnCode = it },
            onSelectEntry = { entry -> itemGstRate = entry.gstRatePercent },
            itemNameHint = itemName,
            isPlainNonGst = isPlainNonGst,
            testTagPrefix = "invoice_hsn",
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = itemQtyText,
            onValueChange = { itemQtyText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Qty") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(0.7f),
          )
          OutlinedTextField(
            value = itemPriceText,
            onValueChange = { itemPriceText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Rate ($currency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("invoice_item_price_input"),
          )
        }
        if (!isPlainNonGst) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.0, 5.0, 12.0, 18.0, 28.0).forEach { slab ->
              FilterChip(
                selected = itemGstRate == slab,
                onClick = { itemGstRate = slab },
                label = { Text("${slab.toInt()}% GST") },
              )
            }
          }
        }
        OutlinedButton(
          onClick = {
            val q = itemQtyText.toDoubleOrNull() ?: 0.0
            val p = itemPriceText.toDoubleOrNull() ?: -1.0
            val stockMatch = linkedInventoryId.takeIf { it.isNotBlank() }?.let { id -> inventory.firstOrNull { it.id == id } }
            val alreadyInBillQty = lineItems.filter { it.itemId == linkedInventoryId && linkedInventoryId.isNotBlank() }.sumOf { it.qty }
            if (itemName.isBlank() || q <= 0.0 || p < 0.0) {
              errorText = "Enter valid item name, qty (>0), and rate"
            } else if (!appSettings.allowNegativeStockSale && stockMatch != null && (alreadyInBillQty + q) > stockMatch.stockQty) {
              errorText = "Insufficient stock for ${stockMatch.name} (Available: ${stockMatch.stockQty.toInt()} ${stockMatch.unit}). Enable zero-stock billing in Settings to override."
            } else if (lineItems.size >= 25) {
              errorText = "Maximum 25 items per invoice"
            } else {
              errorText = null
              lineItems.add(
                InvoiceLineItem(
                  itemId = linkedInventoryId,
                  name = itemName.trim(),
                  sku = linkedSku,
                  hsnCode = if (isPlainNonGst) "" else linkedHsnCode.trim(),
                  unit = linkedUnit,
                  qty = q,
                  unitPrice = p,
                  gstRatePercent = if (isPlainNonGst) 0.0 else itemGstRate,
                )
              )
              itemName = ""
              itemPriceText = ""
              itemQtyText = "1"
              linkedInventoryId = ""
              linkedSku = ""
              linkedHsnCode = ""
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("add_line_item_button"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add Line Item (${lineItems.size}/25)")
        }

        if (lineItems.isNotEmpty()) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              lineItems.forEachIndexed { idx, line ->
                val lineTot = (line.qty * line.unitPrice) * (1.0 + (if (taxMode == GstInvoice.TAX_MODE_NONE) 0.0 else line.gstRatePercent) / 100.0)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  val hsnTag = if (!isPlainNonGst && line.hsnCode.isNotBlank()) " [HSN ${line.hsnCode}]" else ""
                  Text(
                    text = "${line.name}$hsnTag (${line.qty} × ${formatErpMoney(currency, line.unitPrice)})",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                  )
                  Text(
                    text = formatErpMoney(currency, lineTot),
                    style = MaterialTheme.typography.labelLarge,
                  )
                  IconButton(onClick = { lineItems.removeAt(idx) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove line", modifier = Modifier.size(16.dp))
                  }
                }
              }
              HorizontalDivider()
              Text(
                text =
                  if (isPlainNonGst)
                    "Subtotal: ${formatErpMoney(currency, subtotal)} | Total: ${formatErpMoney(currency, grandTotal)}"
                  else
                    "Subtotal: ${formatErpMoney(currency, subtotal)} | GST: ${formatErpMoney(currency, gstTotal)} | Total: ${formatErpMoney(currency, grandTotal)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = discountText,
            onValueChange = { discountText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Discount ($currency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = paidText,
            onValueChange = { paidText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Paid Now") },
            placeholder = { Text(if (paymentMethod == GstInvoice.PAYMENT_CREDIT) "0" else "Full") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }

        Text("Payment Mode", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(
            GstInvoice.PAYMENT_UPI to "UPI",
            GstInvoice.PAYMENT_CASH to "Cash",
            GstInvoice.PAYMENT_CARD to "Card",
            GstInvoice.PAYMENT_CREDIT to "Credit",
          ).forEach { (key, label) ->
            FilterChip(
              selected = paymentMethod == key,
              onClick = {
                paymentMethod = key
                if (key == GstInvoice.PAYMENT_CREDIT) paidText = "0"
              },
              label = { Text(label) },
            )
          }
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        if (errorText != null) {
          Text(errorText.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (lineItems.isEmpty()) {
            errorText = "Add at least one line item"
          } else if (grandTotal <= 0.0) {
            errorText = "Grand total must be greater than zero"
          } else {
            errorText = null
            onCreate(
              invoiceNumber,
              selectedParty,
              walkInName,
              if (isPlainNonGst) "" else walkInGstin,
              invoiceType,
              if (isPlainNonGst) GstInvoice.TAX_MODE_NONE else taxMode,
              paymentMethod,
              discount,
              projectedPaid,
              notes,
              lineItems.toList(),
              if (selectedParty == null) walkInAddress else selectedParty!!.address,
              if (selectedParty == null) walkInState else selectedParty!!.state,
              if (selectedParty == null) walkInPincode else selectedParty!!.pincode,
            )
          }
        },
        enabled = !isBusy,
        modifier = Modifier.testTag("save_invoice_button"),
      ) {
        Text(
          when {
            isBusy -> "Saving…"
            isEditing -> "Save Changes"
            else -> "Generate Bill"
          }
        )
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InvoiceDetailSheet(
  invoice: GstInvoice,
  profile: BusinessProfile?,
  appSettings: ErpAppSettings = ErpAppSettings(),
  buyerPhone: String? = null,
  currency: String,
  isPlainNonGst: Boolean = false,
  canPrint: Boolean = true,
  canEditDelete: Boolean = true,
  canRecordPayments: Boolean = true,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onPrint: () -> Unit,
  onDownloadPdf: () -> Unit,
  onEdit: () -> Unit,
  onRecordPayment: (amount: Double, method: String) -> Unit,
  onDelete: () -> Unit,
  onOpenEInvoice: () -> Unit = {},
  onOpenEWayBill: () -> Unit = {},
) {
  val context = LocalContext.current
  var showPayControls by remember { mutableStateOf(false) }
  var payAmountText by remember(invoice) {
    mutableStateOf(invoice.balanceDue.toLong().toString())
  }
  var payMethod by remember { mutableStateOf(GstInvoice.PAYMENT_UPI) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column {
        Text(
          text = invoice.invoiceNumber,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text =
            "${invoice.partyName}${if (!isPlainNonGst && invoice.partyGstin.isNotBlank()) " • GSTIN: ${invoice.partyGstin}" else ""}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val buyerAddressLine =
          InvoicePdfPrinter.buildAddressLine(
            invoice.partyAddress,
            invoice.partyState,
            invoice.partyPincode,
          )
        if (buyerAddressLine.isNotBlank()) {
          Text(
            text = buyerAddressLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (canPrint) {
          OutlinedButton(
            onClick = onPrint,
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_detail_print_button"),
          ) {
            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Print")
          }
          OutlinedButton(
            onClick = onDownloadPdf,
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_detail_pdf_button"),
          ) {
            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save PDF")
          }
        }
        if (!isPlainNonGst && invoice.taxMode != GstInvoice.TAX_MODE_NONE) {
          OutlinedButton(
            onClick = onOpenEInvoice,
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_detail_einvoice_button"),
          ) {
            Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("E-Invoice Portal")
          }
          OutlinedButton(
            onClick = onOpenEWayBill,
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_detail_ewaybill_button"),
          ) {
            Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("E-Way Bill Portal")
          }
        }
        if (canEditDelete) {
          OutlinedButton(
            onClick = onEdit,
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_detail_edit_button"),
          ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Edit")
          }
        }
        OutlinedButton(
          onClick = {
            val summaryText =
              WhatsAppShareHelper.formatInvoiceSummary(invoice, profile, currency, isPlainNonGst)
            WhatsAppShareHelper.shareTextToWhatsApp(
              context = context,
              text = summaryText,
              rawPhoneNumber = buyerPhone,
              chooserTitle = "Share Invoice on WhatsApp",
            )
          },
          modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_detail_whatsapp_text_button"),
        ) {
          Icon(
            Icons.Filled.Share,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(17.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("WhatsApp", color = MaterialTheme.colorScheme.secondary)
        }
        if (canPrint) {
          OutlinedButton(
            onClick = {
              val summaryText =
                WhatsAppShareHelper.formatInvoiceSummary(invoice, profile, currency, isPlainNonGst)
              WhatsAppShareHelper.shareInvoicePdfToWhatsApp(
                context = context,
                invoice = invoice,
                profile = profile,
                currency = currency,
                appSettings = appSettings,
                isPlainNonGst = isPlainNonGst,
                captionText = summaryText,
              )
            },
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_detail_whatsapp_pdf_button"),
          ) {
            Icon(
              Icons.Filled.PictureAsPdf,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("WhatsApp PDF", color = MaterialTheme.colorScheme.secondary)
          }
          OutlinedButton(
            onClick = {
              val summaryText = buildShareableReceipt(invoice, profile, currency, isPlainNonGst)
              val sendIntent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_SUBJECT, "Invoice ${invoice.invoiceNumber}")
                  putExtra(Intent.EXTRA_TEXT, summaryText)
                }
              context.startActivity(Intent.createChooser(sendIntent, "Share Invoice"))
            },
            modifier = Modifier.heightIn(min = 48.dp).testTag("invoice_detail_share_button"),
          ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Share")
          }
        }
      }

      if (!isPlainNonGst && (invoice.hasEInvoice || invoice.hasEWayBill)) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              if (invoice.hasEInvoice) {
                Text(
                  text = "Legacy acknowledgement #${invoice.ackNo} • ${invoice.ackDate}",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "IRN: ${invoice.irn}",
                  style = MaterialTheme.typography.bodySmall,
                  maxLines = 2,
                )
              }
              if (invoice.hasEWayBill) {
                Text(
                  text = "E-Way Bill #${invoice.ewbNumber} • ${invoice.distanceKm} KM (${invoice.transportMode.uppercase()})",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                  text = "${invoice.fromPlace} (${invoice.fromPincode}) → ${invoice.toPlace} (${invoice.toPincode}) • Vehicle: ${invoice.vehicleNumber}",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }
      }

      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Line Items", style = MaterialTheme.typography.titleSmall)
          invoice.lineItems.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                val hsnPrefix = if (!isPlainNonGst && item.hsnCode.isNotBlank()) "HSN ${item.hsnCode} • " else ""
                Text(
                  if (isPlainNonGst) "${item.qty} ${item.unit} × ${formatErpMoney(currency, item.unitPrice)}"
                  else "${hsnPrefix}${item.qty} ${item.unit} × ${formatErpMoney(currency, item.unitPrice)} (GST ${item.gstRatePercent.toInt()}%)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                formatErpMoney(currency, item.lineTotal),
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
          HorizontalDivider()
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
              if (isPlainNonGst) "Item Subtotal" else "Taxable Subtotal",
              style = MaterialTheme.typography.bodySmall,
            )
            Text(formatErpMoney(currency, invoice.subtotal), style = MaterialTheme.typography.bodySmall)
          }
          if (!isPlainNonGst && invoice.taxMode == GstInvoice.TAX_MODE_CGST_SGST) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("CGST + SGST", style = MaterialTheme.typography.bodySmall)
              Text(
                "${formatErpMoney(currency, invoice.gstTotal / 2)} + ${formatErpMoney(currency, invoice.gstTotal / 2)}",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          } else if (!isPlainNonGst && invoice.taxMode == GstInvoice.TAX_MODE_IGST) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("IGST", style = MaterialTheme.typography.bodySmall)
              Text(formatErpMoney(currency, invoice.gstTotal), style = MaterialTheme.typography.bodySmall)
            }
          }
          if (invoice.discountAmount > 0.0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Discount", style = MaterialTheme.typography.bodySmall)
              Text("- ${formatErpMoney(currency, invoice.discountAmount)}", style = MaterialTheme.typography.bodySmall)
            }
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Grand Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
              formatErpMoney(currency, invoice.grandTotal),
              style = MonoMetricMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Paid / Balance Due", style = MaterialTheme.typography.bodySmall)
            Text(
              "${formatErpMoney(currency, invoice.paidAmount)} / ${formatErpMoney(currency, invoice.balanceDue)}",
              style = MaterialTheme.typography.labelLarge,
              color =
                if (invoice.balanceDue > 0.0) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }

      if (invoice.status != GstInvoice.STATUS_PAID && canRecordPayments) {
        if (!showPayControls) {
          Button(
            onClick = { showPayControls = true },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Filled.Payments, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Record Payment (${formatErpMoney(currency, invoice.balanceDue)} Due)")
          }
        } else {
          OutlinedTextField(
            value = payAmountText,
            onValueChange = { payAmountText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Receive Amount ($currency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
              GstInvoice.PAYMENT_UPI to "UPI",
              GstInvoice.PAYMENT_CASH to "Cash",
              GstInvoice.PAYMENT_CARD to "Card",
            ).forEach { (key, label) ->
              FilterChip(selected = payMethod == key, onClick = { payMethod = key }, label = { Text(label) })
            }
          }
          Button(
            onClick = {
              val amt = payAmountText.toDoubleOrNull() ?: 0.0
              if (amt > 0.0) {
                onRecordPayment(amt, payMethod)
                showPayControls = false
              }
            },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Confirm Settlement")
          }
        }
      }

      if (canEditDelete) {
        TextButton(
          onClick = onDelete,
          modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).testTag("invoice_detail_delete_button"),
        ) {
          Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
          Spacer(modifier = Modifier.width(4.dp))
          Text("Delete Invoice", color = MaterialTheme.colorScheme.error)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun DeleteInvoiceConfirmDialog(
  invoice: GstInvoice,
  currency: String,
  isBusy: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = { if (!isBusy) onDismiss() },
    icon = {
      Icon(
        imageVector = Icons.Filled.DeleteOutline,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
      )
    },
    title = { Text("Delete Invoice ${invoice.invoiceNumber}?") },
    text = {
      Text(
        text =
          "Deleting this ${formatErpMoney(currency, invoice.grandTotal)} invoice for ${invoice.partyName} will automatically restore linked stock quantities and reverse Party Ledger balances.",
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        enabled = !isBusy,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        modifier = Modifier.testTag("confirm_delete_invoice_button"),
      ) {
        Text(if (isBusy) "Deleting…" else "Delete Invoice")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isBusy) {
        Text("Cancel")
      }
    },
  )
}

private fun buildShareableReceipt(
  invoice: GstInvoice,
  profile: BusinessProfile?,
  currency: String,
  isPlainNonGst: Boolean = false,
): String = buildString {
  appendLine(profile?.businessName ?: "Finboss Billing No.1 ERP")
  val sellerAddress =
    InvoicePdfPrinter.buildAddressLine(
      profile?.address.orEmpty(),
      profile?.state.orEmpty(),
      profile?.pincode.orEmpty(),
    )
  if (sellerAddress.isNotBlank()) appendLine(sellerAddress)
  if (!isPlainNonGst && !profile?.gstin.isNullOrBlank()) appendLine("Seller GSTIN: ${profile?.gstin}")
  appendLine("------------------------------")
  appendLine("${if (isPlainNonGst) "Bill" else "Invoice"} #: ${invoice.invoiceNumber}")
  appendLine("Customer: ${invoice.partyName}")
  val buyerAddress =
    InvoicePdfPrinter.buildAddressLine(
      invoice.partyAddress,
      invoice.partyState,
      invoice.partyPincode,
    )
  if (buyerAddress.isNotBlank()) appendLine("Address: $buyerAddress")
  if (!isPlainNonGst && invoice.partyGstin.isNotBlank()) appendLine("Customer GSTIN: ${invoice.partyGstin}")
  appendLine("Status: ${invoice.status.uppercase()} (${invoice.paymentMethod.uppercase()})")
  appendLine("------------------------------")
  invoice.lineItems.forEach { item ->
    val hsnPart = if (!isPlainNonGst && item.hsnCode.isNotBlank()) " [HSN ${item.hsnCode}]" else ""
    appendLine("${item.name}$hsnPart (${item.qty} ${item.unit} × ${formatErpMoney(currency, item.unitPrice)}) = ${formatErpMoney(currency, item.lineTotal)}")
  }
  appendLine("------------------------------")
  appendLine("Subtotal: ${formatErpMoney(currency, invoice.subtotal)}")
  if (!isPlainNonGst) {
    appendLine("GST: ${formatErpMoney(currency, invoice.gstTotal)}")
  }
  if (invoice.discountAmount > 0.0) {
    appendLine("Discount: -${formatErpMoney(currency, invoice.discountAmount)}")
  }
  appendLine("Grand Total: ${formatErpMoney(currency, invoice.grandTotal)}")
  appendLine("Paid: ${formatErpMoney(currency, invoice.paidAmount)} | Balance: ${formatErpMoney(currency, invoice.balanceDue)}")
}
