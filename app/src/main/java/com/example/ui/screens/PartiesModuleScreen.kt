package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.reference.IndiaStates
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.viewmodel.UiState
import com.example.util.LedgerAnalyticsEngine
import com.example.util.LedgerPeriod
import com.example.util.LedgerReportPdfPrinter
import com.example.util.PartyProfitSummary
import com.example.util.WhatsAppShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PartiesModuleContent(
  partiesState: UiState<List<ErpParty>>,
  currency: String,
  defaultCreditLimit: Double = 50000.0,
  isBusy: Boolean,
  onSaveParty: (
    existingId: String?,
    name: String,
    partyType: String,
    phone: String,
    gstin: String,
    openingDue: Double,
    creditLimit: Double,
    notes: String,
    address: String,
    state: String,
    pincode: String,
    onDone: () -> Unit,
  ) -> Unit,
  onRecordPayment: (party: ErpParty, amount: Double, onDone: () -> Unit) -> Unit,
  onDeleteParty: (String) -> Unit,
  showAddPartyDialog: Boolean,
  onDismissAddPartyDialog: () -> Unit,
  onOpenAddPartyDialog: () -> Unit,
  canManageParties: Boolean = true,
  businessName: String = "Finboss Billing No.1 ERP",
  invoices: List<GstInvoice> = emptyList(),
  inventory: List<InventoryItem> = emptyList(),
  expenses: List<ErpExpense> = emptyList(),
  entries: List<DashboardEntry> = emptyList(),
  profile: BusinessProfile? = null,
  isPlainNonGst: Boolean = false,
  selectedLedgerMode: LedgerPageMode = LedgerPageMode.PARTY_LEDGER,
  navigationNonce: Int = 0,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var typeFilter by rememberSaveable { mutableStateOf("all") }
  var selectedPeriod by rememberSaveable { mutableStateOf(LedgerPeriod.THIS_MONTH) }
  var ledgerViewMode by rememberSaveable { mutableStateOf(selectedLedgerMode.key) } // "parties", "items", "transactions"
  var editingParty by remember { mutableStateOf<ErpParty?>(null) }
  var paymentParty by remember { mutableStateOf<ErpParty?>(null) }

  LaunchedEffect(selectedLedgerMode, navigationNonce) {
    ledgerViewMode = selectedLedgerMode.key
  }

  val parties = (partiesState as? UiState.Success)?.data.orEmpty()
  val ledgerReport =
    remember(selectedPeriod, parties, inventory, invoices, expenses, entries, isPlainNonGst) {
      LedgerAnalyticsEngine.buildReport(
        period = selectedPeriod,
        parties = parties,
        inventory = inventory,
        invoices = invoices,
        expenses = expenses,
        entries = entries,
        isPlainNonGst = isPlainNonGst,
      )
    }

  val partyProfitById =
    remember(ledgerReport.partySummaries) {
      ledgerReport.partySummaries.associateBy { it.partyId }
    }

  val downloadLedgerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { targetUri ->
      if (targetUri == null) return@rememberLauncherForActivityResult
      coroutineScope.launch {
        val result =
          withContext(Dispatchers.IO) {
            runCatching {
              context.contentResolver.openOutputStream(targetUri)?.use { out ->
                LedgerReportPdfPrinter.writeLedgerPdfToStream(
                  report = ledgerReport,
                  profile = profile,
                  currency = currency,
                  isPlainNonGst = isPlainNonGst,
                  outputStream = out,
                )
              } ?: error("Unable to open PDF output destination")
            }
          }
        if (result.isSuccess) {
          Toast.makeText(context, "Ledger PDF downloaded", Toast.LENGTH_SHORT).show()
        } else {
          Toast.makeText(
            context,
            result.exceptionOrNull()?.localizedMessage ?: "Failed to download Ledger PDF",
            Toast.LENGTH_SHORT,
          ).show()
        }
      }
    }

  val totalToCollect =
    remember(parties) {
      parties.filter { it.partyType == ErpParty.TYPE_CUSTOMER }.sumOf { it.outstandingBalance }
    }
  val totalToPay =
    remember(parties) {
      parties.filter { it.partyType == ErpParty.TYPE_SUPPLIER }.sumOf { it.outstandingBalance }
    }

  val filteredParties =
    remember(parties, typeFilter, searchQuery) {
      parties.filter { p ->
        val matchesType = typeFilter == "all" || p.partyType == typeFilter
        val q = searchQuery.trim().lowercase()
        val matchesQuery =
          q.isEmpty() ||
            p.name.lowercase().contains(q) ||
            p.phone.lowercase().contains(q) ||
            p.gstin.lowercase().contains(q)
        matchesType && matchesQuery
      }
    }

  val filteredItems =
    remember(ledgerReport.itemSummaries, searchQuery) {
      val q = searchQuery.trim().lowercase()
      if (q.isEmpty()) ledgerReport.itemSummaries
      else ledgerReport.itemSummaries.filter {
        it.itemName.lowercase().contains(q) ||
          it.sku.lowercase().contains(q) ||
          it.hsnCode.lowercase().contains(q)
      }
    }

  val filteredTransactions =
    remember(ledgerReport.transactions, searchQuery) {
      val q = searchQuery.trim().lowercase()
      if (q.isEmpty()) ledgerReport.transactions
      else ledgerReport.transactions.filter {
        it.partyName.lowercase().contains(q) ||
          it.itemsSummary.lowercase().contains(q) ||
          it.voucherNumber.lowercase().contains(q) ||
          it.voucherType.lowercase().contains(q)
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      LedgerPeriodAndExportCard(
        selectedPeriod = selectedPeriod,
        report = ledgerReport,
        currency = currency,
        onSelectPeriod = { selectedPeriod = it },
        onPrintPdf = {
          runCatching {
            LedgerReportPdfPrinter.printLedgerReport(
              context = context,
              report = ledgerReport,
              profile = profile,
              currency = currency,
              isPlainNonGst = isPlainNonGst,
            )
          }.onFailure { err ->
            Toast.makeText(context, err.localizedMessage ?: "Print failed", Toast.LENGTH_SHORT).show()
          }
        },
        onDownloadPdf = {
          downloadLedgerLauncher.launch(LedgerReportPdfPrinter.buildPdfFileName(selectedPeriod))
        },
        onShareWhatsApp = {
          val msg =
            LedgerReportPdfPrinter.formatLedgerWhatsAppSummary(
              report = ledgerReport,
              businessName = businessName,
              currency = currency,
            )
          WhatsAppShareHelper.shareTextToWhatsApp(
            context = context,
            text = msg,
            chooserTitle = "Share Ledger Summary on WhatsApp",
          )
        },
      )
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SummaryStatPill(
          title = "To Collect",
          value = formatErpMoney(currency, totalToCollect),
          accent = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.weight(1f),
        )
        SummaryStatPill(
          title = "To Pay",
          value = formatErpMoney(currency, totalToPay),
          accent = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val viewTabs =
          listOf(
            "parties" to "Party Ledger (${parties.size})",
            "items" to "Item / Stock Ledger (${ledgerReport.itemSummaries.size})",
            "transactions" to "Transactions (${ledgerReport.transactions.size})",
          )
        items(viewTabs) { (key, label) ->
          FilterChip(
            selected = ledgerViewMode == key,
            onClick = { ledgerViewMode = key },
            label = { Text(label) },
            modifier = Modifier.testTag("ledger_view_tab_$key"),
          )
        }
      }
    }

    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = {
          Text(
            when (ledgerViewMode) {
              "items" -> "Search items, SKU, or HSN code"
              "transactions" -> "Search transactions, ref #, party, or item"
              else -> "Search parties by name, phone, or GSTIN"
            }
          )
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("parties_search_input"),
      )
    }

    if (ledgerViewMode == "parties") {
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val filters =
            listOf(
              "all" to "All (${parties.size})",
              ErpParty.TYPE_CUSTOMER to "Customers",
              ErpParty.TYPE_SUPPLIER to "Suppliers",
            )
          items(filters) { (key, label) ->
            FilterChip(
              selected = typeFilter == key,
              onClick = { typeFilter = key },
              label = { Text(label) },
            )
          }
        }
      }
    }

    when (ledgerViewMode) {
      "items" -> {
        if (filteredItems.isEmpty()) {
          item {
            ModuleEmptyCard(
              icon = Icons.Filled.Groups,
              title = "No Item / Stock Activity",
              subtitle = "Add stock SKUs or create invoices in ${selectedPeriod.label} to view item-wise profit & loss %.",
            )
          }
        } else {
          items(filteredItems, key = { it.itemId }) { itemSummary ->
            ItemStockProfitCard(item = itemSummary, currency = currency)
          }
        }
      }
      "transactions" -> {
        if (filteredTransactions.isEmpty()) {
          item {
            ModuleEmptyCard(
              icon = Icons.Filled.Groups,
              title = "No Transactions in ${selectedPeriod.label}",
              subtitle = "Switch period filter (Quarter, Half Year, Previous Year, Present Year) or create a bill.",
            )
          }
        } else {
          items(filteredTransactions, key = { it.id }) { tx ->
            LedgerTransactionCard(tx = tx, currency = currency)
          }
        }
      }
      else -> {
        when (partiesState) {
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
                text = partiesState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
          is UiState.Success -> {
            if (filteredParties.isEmpty()) {
              item {
                ModuleEmptyCard(
                  icon = Icons.Filled.Groups,
                  title = if (parties.isEmpty()) "No Parties in Ledger Yet" else "No Matching Parties",
                  subtitle =
                    if (parties.isEmpty())
                      "Add customers and suppliers to track GSTIN, credit limits, and party profit/loss %."
                    else "Try adjusting your search or filter.",
                  actionLabel = if (canManageParties) "Add Party" else null,
                  onAction = if (canManageParties) onOpenAddPartyDialog else null,
                )
              }
            } else {
              items(filteredParties, key = { it.id }) { party ->
                PartyLedgerCard(
                  party = party,
                  profitSummary = partyProfitById[party.id],
                  currency = currency,
                  canManageParties = canManageParties,
                  onShareWhatsApp = {
                    val msg =
                      WhatsAppShareHelper.formatPartyStatementSummary(
                        party = party,
                        businessName = businessName,
                        currency = currency,
                      )
                    WhatsAppShareHelper.shareTextToWhatsApp(
                      context = context,
                      text = msg,
                      rawPhoneNumber = party.phone,
                      chooserTitle = "Share Ledger Statement on WhatsApp",
                    )
                  },
                  onRecordPayment = { paymentParty = party },
                  onEdit = { editingParty = party },
                  onDelete = { onDeleteParty(party.id) },
                )
              }
            }
          }
        }
      }
    }
  }

  if (showAddPartyDialog || editingParty != null) {
    PartyFormDialog(
      existing = editingParty,
      currency = currency,
      defaultCreditLimit = defaultCreditLimit,
      isBusy = isBusy,
      onDismiss = {
        editingParty = null
        onDismissAddPartyDialog()
      },
      onSave = { name, type, phone, gstin, openingDue, creditLimit, notes, address, state, pincode ->
        onSaveParty(
          editingParty?.id,
          name,
          type,
          phone,
          gstin,
          openingDue,
          creditLimit,
          notes,
          address,
          state,
          pincode,
        ) {
          editingParty = null
          onDismissAddPartyDialog()
        }
      },
    )
  }

  val activePaymentParty = paymentParty
  if (activePaymentParty != null) {
    PartyPaymentDialog(
      party = activePaymentParty,
      currency = currency,
      isBusy = isBusy,
      onDismiss = { paymentParty = null },
      onSubmit = { amt ->
        onRecordPayment(activePaymentParty, amt) { paymentParty = null }
      },
    )
  }
}

@Composable
private fun PartyLedgerCard(
  party: ErpParty,
  profitSummary: PartyProfitSummary? = null,
  currency: String,
  canManageParties: Boolean = true,
  onShareWhatsApp: () -> Unit,
  onRecordPayment: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val isCustomer = party.partyType == ErpParty.TYPE_CUSTOMER
  val balance = party.outstandingBalance
  val badgeBg =
    if (isCustomer) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.tertiaryContainer
  val badgeFg =
    if (isCustomer) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onTertiaryContainer

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth().testTag("party_card_${party.id}"),
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
              .clip(CircleShape)
              .background(badgeBg),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (isCustomer) Icons.Filled.Groups else Icons.Filled.Storefront,
            contentDescription = null,
            tint = badgeFg,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = party.name,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
              Text(
                text = if (isCustomer) "CUSTOMER" else "SUPPLIER",
                style = MaterialTheme.typography.labelSmall,
                color = badgeFg,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
          val subParts =
            listOfNotNull(
              party.phone.takeIf { it.isNotBlank() },
              party.gstin.takeIf { it.isNotBlank() }?.let { "GST: $it" },
              listOfNotNull(
                party.state.takeIf { it.isNotBlank() },
                party.pincode.takeIf { it.isNotBlank() },
              ).joinToString(" - ").takeIf { it.isNotBlank() },
            )
          if (subParts.isNotEmpty()) {
            Text(
              text = subParts.joinToString(" • "),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = formatErpMoney(currency, balance),
            style = MonoMetricMedium,
            color =
              if (balance > 0.0) {
                if (isCustomer) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
              } else MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = if (balance > 0.0) (if (isCustomer) "To Collect" else "To Pay") else "Settled",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (profitSummary != null && profitSummary.hasSalesActivity) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Period Sales: ${formatErpMoney(currency, profitSummary.salesNetRevenue)} (${profitSummary.transactionCount} bills)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          ProfitLossPercentBadge(
            profitAmount = profitSummary.profitAmount,
            profitPercent = profitSummary.profitPercent,
            currency = currency,
            showAmount = true,
            modifier = Modifier.testTag("party_profit_badge_${party.id}"),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            "Billed: ${formatErpMoney(currency, party.totalBilled)} • Paid: ${formatErpMoney(currency, party.totalPaid)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onShareWhatsApp,
            modifier = Modifier.size(48.dp).testTag("party_card_whatsapp_${party.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.Share,
              contentDescription = "Share Party Statement on WhatsApp",
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(18.dp),
            )
          }
          if (canManageParties) {
            if (balance > 0.0) {
              TextButton(onClick = onRecordPayment) {
                Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Settle")
              }
            }
            IconButton(onClick = onEdit) {
              Icon(Icons.Filled.Edit, contentDescription = "Edit party", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
              Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "Delete party",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartyFormDialog(
  existing: ErpParty?,
  currency: String,
  defaultCreditLimit: Double = 50000.0,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    partyType: String,
    phone: String,
    gstin: String,
    openingDue: Double,
    creditLimit: Double,
    notes: String,
    address: String,
    state: String,
    pincode: String,
  ) -> Unit,
) {
  var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
  var partyType by remember(existing) { mutableStateOf(existing?.partyType ?: ErpParty.TYPE_CUSTOMER) }
  var phone by remember(existing) { mutableStateOf(existing?.phone ?: "") }
  var gstin by remember(existing) { mutableStateOf(existing?.gstin ?: "") }
  var address by remember(existing) { mutableStateOf(existing?.address ?: "") }
  var state by remember(existing) { mutableStateOf(existing?.state ?: "") }
  var pincode by remember(existing) { mutableStateOf(existing?.pincode ?: "") }
  var openingText by remember(existing) {
    mutableStateOf(if (existing == null || existing.openingDue == 0.0) "" else existing.openingDue.toLong().toString())
  }
  var creditText by remember(existing, defaultCreditLimit) {
    mutableStateOf(
      if (existing != null) {
        if (existing.creditLimit == 0.0) "" else existing.creditLimit.toLong().toString()
      } else if (defaultCreditLimit > 0.0) {
        defaultCreditLimit.toLong().toString()
      } else ""
    )
  }
  var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "Add Party to Ledger" else "Edit Party") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = partyType == ErpParty.TYPE_CUSTOMER,
            onClick = { partyType = ErpParty.TYPE_CUSTOMER },
            label = { Text("Customer") },
          )
          FilterChip(
            selected = partyType == ErpParty.TYPE_SUPPLIER,
            onClick = { partyType = ErpParty.TYPE_SUPPLIER },
            label = { Text("Supplier") },
          )
        }
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Party / Business Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("party_name_input"),
        )
        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("Phone Number") },
          leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = gstin,
          onValueChange = { raw ->
            val upper = raw.uppercase()
            gstin = upper
            if (state.isBlank()) {
              IndiaStates.stateFromGstin(upper)?.let { state = it.name }
            }
          },
          label = { Text("GSTIN (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("party_gstin_input"),
        )
        OutlinedTextField(
          value = address,
          onValueChange = { address = it },
          label = { Text("Billing Address") },
          maxLines = 2,
          modifier = Modifier.fillMaxWidth().testTag("party_address_input"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StatePickerField(
            selectedState = state,
            onStateSelected = { state = it },
            label = "State / UT",
            testTag = "party_state_picker",
            modifier = Modifier.weight(1.3f),
          )
          OutlinedTextField(
            value = pincode,
            onValueChange = { pincode = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text("PIN Code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(0.75f).testTag("party_pincode_input"),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = openingText,
            onValueChange = { openingText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Opening Due ($currency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = creditText,
            onValueChange = { creditText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Credit Limit ($currency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes / Terms") },
          maxLines = 2,
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
          if (name.isBlank()) {
            errorText = "Party name is required"
          } else {
            errorText = null
            onSave(
              name,
              partyType,
              phone,
              gstin,
              openingText.toDoubleOrNull() ?: 0.0,
              creditText.toDoubleOrNull() ?: 0.0,
              notes,
              address,
              state,
              pincode,
            )
          }
        },
        enabled = !isBusy,
        modifier = Modifier.testTag("save_party_button"),
      ) {
        Text(if (isBusy) "Saving…" else "Save Party")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun PartyPaymentDialog(
  party: ErpParty,
  currency: String,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSubmit: (Double) -> Unit,
) {
  val outstanding = party.outstandingBalance
  var amountText by remember(party) { mutableStateOf(outstanding.toLong().toString()) }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Record Party Payment") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "${party.name} • Balance Due: ${formatErpMoney(currency, outstanding)}",
          style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
          label = { Text("Payment Amount ($currency)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
          val parsed = amountText.toDoubleOrNull()
          if (parsed == null || parsed <= 0.0) {
            errorText = "Enter a positive payment amount"
          } else {
            errorText = null
            onSubmit(parsed)
          }
        },
        enabled = !isBusy,
      ) {
        Text(if (isBusy) "Saving…" else "Record Payment")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun SummaryStatPill(
  title: String,
  value: String,
  accent: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MonoMetricMedium,
        color = accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun ModuleEmptyCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(40.dp))
      Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (actionLabel != null && onAction != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = onAction) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text(actionLabel)
        }
      }
    }
  }
}

fun formatErpMoney(currency: String, amount: Double): String {
  val formatter =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.US).apply {
      maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
      minimumFractionDigits = 0
    }
  return "$currency${formatter.format(amount)}"
}
