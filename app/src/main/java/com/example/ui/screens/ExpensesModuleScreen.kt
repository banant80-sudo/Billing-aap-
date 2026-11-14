package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.viewmodel.UiState

private data class CashbookItem(
  val id: String,
  val title: String,
  val subtitle: String,
  val mode: String,
  val amount: Double,
  val isInflow: Boolean,
  val timestampMs: Long,
)

@Composable
fun ExpensesModuleContent(
  expensesState: UiState<List<ErpExpense>>,
  invoices: List<GstInvoice>,
  entries: List<DashboardEntry>,
  parties: List<ErpParty>,
  currency: String,
  isPlainNonGst: Boolean = false,
  isBusy: Boolean,
  onSaveExpense: (
    existingId: String?,
    title: String,
    category: String,
    paymentMethod: String,
    status: String,
    party: ErpParty?,
    partyNameFallback: String,
    referenceNo: String,
    amount: Double,
    gstInputAmount: Double,
    notes: String,
    onDone: () -> Unit,
  ) -> Unit,
  onMarkSettled: (expenseId: String, paymentMethod: String) -> Unit,
  onDeleteExpense: (String) -> Unit,
  showAddExpenseDialog: Boolean,
  onDismissAddExpenseDialog: () -> Unit,
  onOpenAddExpenseDialog: () -> Unit,
  canManageExpenses: Boolean = true,
) {
  var viewMode by rememberSaveable { mutableStateOf("expenses") } // "expenses" or "cashbook"
  var categoryFilter by rememberSaveable { mutableStateOf("all") }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var editingExpense by remember { mutableStateOf<ErpExpense?>(null) }

  val expenses = (expensesState as? UiState.Success)?.data.orEmpty()
  val totalExpenses =
    remember(expenses, entries) {
      expenses.sumOf { it.amount } +
        entries.filter { it.category == DashboardEntry.CATEGORY_EXPENSE }.sumOf { it.amount }
    }
  val totalGstItc = remember(expenses) { expenses.sumOf { it.gstInputAmount } }

  // Cashbook balances: Cash vs Bank/UPI/Card
  val (cashInHand, bankUpiNet, cashbookFeed) =
    remember(expenses, invoices, entries) {
      val feed = mutableListOf<CashbookItem>()
      var cashNet = 0.0
      var digitalNet = 0.0

      invoices.filter { it.paidAmount > 0.0 }.forEach { inv ->
        val isCash = inv.paymentMethod == GstInvoice.PAYMENT_CASH
        if (isCash) cashNet += inv.paidAmount else digitalNet += inv.paidAmount
        feed.add(
          CashbookItem(
            id = "inv_${inv.id}",
            title = "Bill ${inv.invoiceNumber}",
            subtitle = inv.partyName,
            mode = inv.paymentMethod.uppercase(),
            amount = inv.paidAmount,
            isInflow = true,
            timestampMs = inv.createdAt?.toDate()?.time ?: 0L,
          )
        )
      }

      entries.filter { it.status == DashboardEntry.STATUS_PAID || it.category == DashboardEntry.CATEGORY_EXPENSE }.forEach { entry ->
        val isOut = entry.category == DashboardEntry.CATEGORY_EXPENSE
        if (isOut) cashNet -= entry.amount else cashNet += entry.amount
        feed.add(
          CashbookItem(
            id = "ent_${entry.id}",
            title = entry.title,
            subtitle = entry.partyName.ifBlank { "Quick Entry" },
            mode = "CASHBOOK",
            amount = entry.amount,
            isInflow = !isOut,
            timestampMs = entry.createdAt?.toDate()?.time ?: 0L,
          )
        )
      }

      expenses.filter { it.status == ErpExpense.STATUS_SETTLED }.forEach { exp ->
        val isCash = exp.paymentMethod == ErpExpense.METHOD_CASH
        if (isCash) cashNet -= exp.amount else digitalNet -= exp.amount
        feed.add(
          CashbookItem(
            id = "exp_${exp.id}",
            title = exp.title,
            subtitle = exp.partyName.ifBlank { exp.category.replaceFirstChar { c -> c.uppercase() } },
            mode = exp.paymentMethod.uppercase(),
            amount = exp.amount,
            isInflow = false,
            timestampMs = exp.createdAt?.toDate()?.time ?: 0L,
          )
        )
      }

      Triple(cashNet, digitalNet, feed.sortedByDescending { it.timestampMs })
    }

  val filteredExpenses =
    remember(expenses, categoryFilter, searchQuery) {
      expenses.filter { exp ->
        val matchesCat = categoryFilter == "all" || exp.category == categoryFilter
        val q = searchQuery.trim().lowercase()
        val matchesQuery =
          q.isEmpty() ||
            exp.title.lowercase().contains(q) ||
            exp.partyName.lowercase().contains(q) ||
            exp.referenceNo.lowercase().contains(q)
        matchesCat && matchesQuery
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
          selected = viewMode == "expenses",
          onClick = { viewMode = "expenses" },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
          Text("Operating Expenses")
        }
        SegmentedButton(
          selected = viewMode == "cashbook",
          onClick = { viewMode = "cashbook" },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
          Text("Cashbook Ledger")
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (viewMode == "expenses") {
          SummaryStatPill(
            title = "Total Expenses",
            value = formatErpMoney(currency, totalExpenses),
            accent = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
          )
          if (isPlainNonGst) {
            val pendingOutflow =
              remember(expenses) {
                expenses.filter { it.status == ErpExpense.STATUS_PENDING }.sumOf { it.amount }
              }
            SummaryStatPill(
              title = "Pending Payables",
              value = formatErpMoney(currency, pendingOutflow),
              accent = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.weight(1f),
            )
          } else {
            SummaryStatPill(
              title = "GST Input Credit",
              value = formatErpMoney(currency, totalGstItc),
              accent = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.weight(1f),
            )
          }
        } else {
          SummaryStatPill(
            title = "Cash-in-Hand",
            value = formatErpMoney(currency, cashInHand),
            accent =
              if (cashInHand >= 0) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
          )
          SummaryStatPill(
            title = "Bank / UPI / Card",
            value = formatErpMoney(currency, bankUpiNet),
            accent =
              if (bankUpiNet >= 0) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    if (viewMode == "expenses") {
      item {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          placeholder = { Text("Search expenses by title, payee, or ref #") },
          singleLine = true,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().testTag("expenses_search_input"),
        )
      }

      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(
              selected = categoryFilter == "all",
              onClick = { categoryFilter = "all" },
              label = { Text("All (${expenses.size})") },
            )
          }
          items(ErpExpense.ALL_CATEGORIES) { (key, label) ->
            FilterChip(
              selected = categoryFilter == key,
              onClick = { categoryFilter = key },
              label = { Text(label) },
            )
          }
        }
      }

      when (expensesState) {
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
              text = expensesState.message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        is UiState.Success -> {
          if (filteredExpenses.isEmpty()) {
            item {
              ModuleEmptyCard(
                icon = Icons.Filled.Payments,
                title = if (expenses.isEmpty()) "No Expenses Recorded Yet" else "No Matching Expenses",
                subtitle =
                  if (expenses.isEmpty()) {
                    if (isPlainNonGst)
                      "Log rent, payroll, utilities, or supplier purchases in your cashbook."
                    else
                      "Log rent, payroll, utilities, or supplier purchases with GST input tax credit."
                  } else "Try adjusting your search or category filter.",
                actionLabel = if (canManageExpenses) "Record Expense" else null,
                onAction = if (canManageExpenses) onOpenAddExpenseDialog else null,
              )
            }
          } else {
            items(filteredExpenses, key = { it.id }) { exp ->
              ExpenseCard(
                expense = exp,
                currency = currency,
                isPlainNonGst = isPlainNonGst,
                canManageExpenses = canManageExpenses,
                onSettle = { onMarkSettled(exp.id, exp.paymentMethod) },
                onEdit = { editingExpense = exp },
                onDelete = { onDeleteExpense(exp.id) },
              )
            }
          }
        }
      }
    } else {
      // Cashbook Unified Feed
      if (cashbookFeed.isEmpty()) {
        item {
          ModuleEmptyCard(
            icon = Icons.Filled.AccountBalance,
            title = "Cashbook Is Empty",
            subtitle = "Settled bills, expenses, and cashbook entries automatically reconcile here.",
            actionLabel = if (canManageExpenses) "Record Expense" else null,
            onAction = if (canManageExpenses) onOpenAddExpenseDialog else null,
          )
        }
      } else {
        items(cashbookFeed, key = { it.id }) { item ->
          CashbookRowCard(item = item, currency = currency)
        }
      }
    }
  }

  if (showAddExpenseDialog || editingExpense != null) {
    ExpenseFormDialog(
      existing = editingExpense,
      parties = parties,
      currency = currency,
      isPlainNonGst = isPlainNonGst,
      isBusy = isBusy,
      onDismiss = {
        editingExpense = null
        onDismissAddExpenseDialog()
      },
      onSave = { title, cat, method, status, party, fallbackParty, refNo, amount, gstItc, notes ->
        onSaveExpense(
          editingExpense?.id,
          title,
          cat,
          method,
          status,
          party,
          fallbackParty,
          refNo,
          amount,
          gstItc,
          notes,
        ) {
          editingExpense = null
          onDismissAddExpenseDialog()
        }
      },
    )
  }
}

@Composable
private fun ExpenseCard(
  expense: ErpExpense,
  currency: String,
  isPlainNonGst: Boolean,
  canManageExpenses: Boolean = true,
  onSettle: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val isPending = expense.status == ErpExpense.STATUS_PENDING
  val catLabel =
    ErpExpense.ALL_CATEGORIES.firstOrNull { it.first == expense.category }?.second ?: "Other"

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth().testTag("expense_card_${expense.id}"),
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
              .background(MaterialTheme.colorScheme.errorContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.Payments,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = expense.title,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            Surface(
              color =
                if (isPending) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(50),
            ) {
              Text(
                text = if (isPending) "DUE" else "PAID",
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (isPending) MaterialTheme.colorScheme.onTertiaryContainer
                  else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
          val meta =
            listOfNotNull(
              catLabel,
              expense.partyName.takeIf { it.isNotBlank() },
              expense.paymentMethod.uppercase(),
              if (!isPlainNonGst && expense.gstInputAmount > 0.0)
                "ITC ${formatErpMoney(currency, expense.gstInputAmount)}"
              else null,
            )
          Text(
            text = meta.joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = "- ${formatErpMoney(currency, expense.amount)}",
          style = MonoMetricMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }

      if (canManageExpenses) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (isPending) {
            TextButton(onClick = onSettle) {
              Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Mark Paid")
            }
          }
          IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit expense", modifier = Modifier.size(18.dp))
          }
          IconButton(onClick = onDelete) {
            Icon(
              Icons.Filled.DeleteOutline,
              contentDescription = "Delete expense",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CashbookRowCard(item: CashbookItem, currency: String) {
  val accent = if (item.isInflow) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector =
            if (item.isInflow) Icons.AutoMirrored.Filled.ReceiptLong else Icons.Filled.Payments,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(19.dp),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(text = item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          text = "${item.subtitle} • ${item.mode}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = (if (item.isInflow) "+ " else "- ") + formatErpMoney(currency, item.amount),
        style = MonoMetricMedium,
        color = accent,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseFormDialog(
  existing: ErpExpense?,
  parties: List<ErpParty>,
  currency: String,
  isPlainNonGst: Boolean = false,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    title: String,
    category: String,
    paymentMethod: String,
    status: String,
    party: ErpParty?,
    partyNameFallback: String,
    referenceNo: String,
    amount: Double,
    gstInputAmount: Double,
    notes: String,
  ) -> Unit,
) {
  var title by remember(existing) { mutableStateOf(existing?.title ?: "") }
  var category by remember(existing) { mutableStateOf(existing?.category ?: ErpExpense.CAT_UTILITIES) }
  var paymentMethod by remember(existing) { mutableStateOf(existing?.paymentMethod ?: ErpExpense.METHOD_UPI) }
  var status by remember(existing) { mutableStateOf(existing?.status ?: ErpExpense.STATUS_SETTLED) }
  var selectedParty by remember(existing, parties) {
    mutableStateOf(
      existing?.partyId?.takeIf { it.isNotBlank() }?.let { pid -> parties.firstOrNull { it.id == pid } }
    )
  }
  var payeeFallback by remember(existing) { mutableStateOf(existing?.partyName ?: "") }
  var referenceNo by remember(existing) { mutableStateOf(existing?.referenceNo ?: "") }
  var amountText by remember(existing) {
    mutableStateOf(if (existing == null) "" else existing.amount.toString().removeSuffix(".0"))
  }
  var gstItcText by remember(existing) {
    mutableStateOf(if (existing == null || existing.gstInputAmount == 0.0) "" else existing.gstInputAmount.toString().removeSuffix(".0"))
  }
  var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "Record Business Expense" else "Edit Expense") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Expense Title *") },
          placeholder = { Text("e.g. Shop Rent, Staff Salary") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("expense_title_input"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Amount ($currency) *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("expense_amount_input"),
          )
          if (!isPlainNonGst) {
            OutlinedTextField(
              value = gstItcText,
              onValueChange = { gstItcText = it.filter { c -> c.isDigit() || c == '.' } },
              label = { Text("GST ITC ($currency)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }
        }
        Text("Category", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ErpExpense.ALL_CATEGORIES.forEach { (key, label) ->
            FilterChip(
              selected = category == key,
              onClick = { category = key },
              label = { Text(label) },
            )
          }
        }
        Text("Payment Mode & Status", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(
            ErpExpense.METHOD_UPI to "UPI",
            ErpExpense.METHOD_CASH to "Cash",
            ErpExpense.METHOD_BANK to "Bank",
            ErpExpense.METHOD_CARD to "Card",
          ).forEach { (key, label) ->
            FilterChip(selected = paymentMethod == key, onClick = { paymentMethod = key }, label = { Text(label) })
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = status == ErpExpense.STATUS_SETTLED,
            onClick = { status = ErpExpense.STATUS_SETTLED },
            label = { Text("Paid Now") },
          )
          FilterChip(
            selected = status == ErpExpense.STATUS_PENDING,
            onClick = { status = ErpExpense.STATUS_PENDING },
            label = { Text("Payable Later") },
          )
        }
        val suppliers = parties.filter { it.partyType == ErpParty.TYPE_SUPPLIER }
        if (suppliers.isNotEmpty()) {
          Text("Supplier (Optional)", style = MaterialTheme.typography.labelLarge)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
              FilterChip(
                selected = selectedParty == null,
                onClick = { selectedParty = null },
                label = { Text("Other Vendor") },
              )
            }
            items(suppliers, key = { it.id }) { sup ->
              FilterChip(
                selected = selectedParty?.id == sup.id,
                onClick = { selectedParty = sup },
                label = { Text(sup.name) },
              )
            }
          }
        }
        if (selectedParty == null) {
          OutlinedTextField(
            value = payeeFallback,
            onValueChange = { payeeFallback = it },
            label = { Text("Vendor / Payee Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        OutlinedTextField(
          value = referenceNo,
          onValueChange = { referenceNo = it.uppercase() },
          label = { Text("Voucher / Bill Ref #") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes") },
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
          val amt = amountText.toDoubleOrNull()
          val gst = if (isPlainNonGst) 0.0 else (gstItcText.toDoubleOrNull() ?: 0.0)
          if (title.isBlank()) {
            errorText = "Expense title is required"
          } else if (amt == null || amt <= 0.0) {
            errorText = "Enter a positive amount"
          } else if (gst > amt) {
            errorText = "GST input credit cannot exceed total amount"
          } else {
            errorText = null
            onSave(title, category, paymentMethod, status, selectedParty, payeeFallback, referenceNo, amt, gst, notes)
          }
        },
        enabled = !isBusy,
        modifier = Modifier.testTag("save_expense_button"),
      ) {
        Text(if (isBusy) "Saving…" else "Save Expense")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
