package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.InventoryItem
import com.example.data.reference.HsnCodeDirectory
import com.example.data.reference.HsnCodeEntry
import com.example.data.reference.IndiaStates
import com.example.data.sync.ErpAppSettings
import com.example.ui.theme.MonoMetricMedium

@Composable
fun HsnSearchSelector(
  hsnCode: String,
  onHsnCodeChange: (String) -> Unit,
  onSelectEntry: (HsnCodeEntry) -> Unit,
  itemNameHint: String = "",
  isPlainNonGst: Boolean = false,
  testTagPrefix: String = "hsn",
) {
  var searchQuery by remember { mutableStateOf("") }
  var showList by remember { mutableStateOf(false) }

  val effectiveQuery =
    when {
      searchQuery.isNotBlank() -> searchQuery
      showList && itemNameHint.isNotBlank() -> itemNameHint
      else -> ""
    }
  val matches =
    remember(effectiveQuery, searchQuery, showList) {
      if (searchQuery.isNotBlank() || showList) HsnCodeDirectory.search(effectiveQuery, limit = 10)
      else emptyList()
    }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = {
          searchQuery = it
          showList = true
        },
        label = { Text("Search HSN by Item") },
        placeholder = { Text("Type item e.g. Rice, Printer") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
          if (searchQuery.isNotEmpty() || showList) {
            IconButton(
              onClick = {
                searchQuery = ""
                showList = false
              }
            ) {
              Icon(Icons.Filled.Clear, contentDescription = "Close HSN list", modifier = Modifier.size(18.dp))
            }
          }
        },
        singleLine = true,
        modifier = Modifier.weight(1.25f).testTag("${testTagPrefix}_search_input"),
      )
      OutlinedTextField(
        value = hsnCode,
        onValueChange = { onHsnCodeChange(it.uppercase().take(12)) },
        label = { Text("HSN Code") },
        placeholder = { Text("8471") },
        leadingIcon = { Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp)) },
        singleLine = true,
        modifier = Modifier.weight(0.8f).testTag("${testTagPrefix}_code_input"),
      )
    }

    if (!showList) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            if (hsnCode.isNotBlank()) "HSN Selected: $hsnCode"
            else "Type an item in Search HSN to view matching codes",
          style = MaterialTheme.typography.labelSmall,
          color =
            if (hsnCode.isNotBlank()) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
          onClick = { showList = true },
          modifier = Modifier.testTag("${testTagPrefix}_browse_button"),
        ) {
          Text("Browse HSN List")
        }
      }
    }

    if (showList) {
      Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_suggestions_card"),
      ) {
        if (matches.isEmpty()) {
          Text(
            text = "No matching HSN found. You can enter the HSN code directly on the right.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
          )
        } else {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Tap an item to fill HSN Code (${matches.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
              )
              TextButton(onClick = { showList = false }) { Text("Hide") }
            }
            HorizontalDivider()
            matches.take(6).forEachIndexed { idx, entry ->
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable {
                      onHsnCodeChange(entry.code)
                      onSelectEntry(entry)
                      searchQuery = ""
                      showList = false
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("${testTagPrefix}_suggestion_${entry.code}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = RoundedCornerShape(6.dp),
                ) {
                  Text(
                    text = entry.code,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = entry.itemName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = entry.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                if (!isPlainNonGst) {
                  Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(50),
                  ) {
                    Text(
                      text = "${entry.gstRatePercent.toInt()}% GST",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                  }
                }
              }
              if (idx < matches.take(6).lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun StatePickerField(
  selectedState: String,
  onStateSelected: (String) -> Unit,
  label: String = "State / UT",
  testTag: String = "state_picker_field",
  modifier: Modifier = Modifier,
) {
  var showDialog by remember { mutableStateOf(false) }
  val matched = remember(selectedState) { IndiaStates.findByNameOrCode(selectedState) }
  val displayText = matched?.displayLabel ?: selectedState

  Box(modifier = modifier) {
    OutlinedTextField(
      value = displayText,
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      placeholder = { Text("Select State") },
      leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp)) },
      trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose State") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Box(
      modifier =
        Modifier.matchParentSize()
          .clickable { showDialog = true }
          .testTag(testTag)
    )
  }

  if (showDialog) {
    StatePickerDialog(
      currentSelection = selectedState,
      onDismiss = { showDialog = false },
      onSelect = { info ->
        onStateSelected(info.name)
        showDialog = false
      },
    )
  }
}

@Composable
private fun StatePickerDialog(
  currentSelection: String,
  onDismiss: () -> Unit,
  onSelect: (com.example.data.reference.IndianStateInfo) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val states = remember(query) { IndiaStates.search(query) }
  val currentInfo = remember(currentSelection) { IndiaStates.findByNameOrCode(currentSelection) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Select State / Union Territory") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          placeholder = { Text("Search state name or GST code (e.g. 27)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("state_search_dialog_input"),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
          items(states, key = { it.gstCode }) { state ->
            val isSelected = currentInfo?.gstCode == state.gstCode
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .clickable { onSelect(state) }
                  .padding(horizontal = 8.dp, vertical = 10.dp)
                  .testTag("state_option_${state.gstCode}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Surface(
                color =
                  if (isSelected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp),
              ) {
                Text(
                  text = state.gstCode,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color =
                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
              Text(
                text = state.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
              )
              if (isSelected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
              }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPurchaseDialog(
  inventory: List<InventoryItem>,
  parties: List<ErpParty>,
  currency: String,
  isPlainNonGst: Boolean,
  appSettings: ErpAppSettings,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onRecordPurchase: (
    existingItem: InventoryItem?,
    newItemName: String,
    newItemSku: String,
    hsnCode: String,
    category: String,
    unit: String,
    purchasePrice: Double,
    salePrice: Double,
    gstRatePercent: Double,
    purchaseQty: Double,
    lowStockThreshold: Double,
    supplier: ErpParty?,
    supplierNameFallback: String,
    billNumber: String,
    paymentMethod: String,
    isPaidNow: Boolean,
    onDone: () -> Unit,
  ) -> Unit,
) {
  var isNewItemMode by remember(inventory) { mutableStateOf(inventory.isEmpty()) }
  var selectedItem by remember(inventory) { mutableStateOf(inventory.firstOrNull()) }

  var itemName by remember { mutableStateOf("") }
  var sku by remember { mutableStateOf("") }
  var hsnCode by remember(selectedItem, isNewItemMode) {
    mutableStateOf(if (!isNewItemMode) (selectedItem?.hsnCode ?: "") else "")
  }
  var category by remember(selectedItem, isNewItemMode) {
    mutableStateOf(if (!isNewItemMode) (selectedItem?.category ?: "General") else "General")
  }
  var unit by remember(selectedItem, isNewItemMode) {
    mutableStateOf(if (!isNewItemMode) (selectedItem?.unit ?: "PCS") else "PCS")
  }
  var purchasePriceText by remember(selectedItem, isNewItemMode) {
    mutableStateOf(
      if (!isNewItemMode && selectedItem != null && selectedItem!!.purchasePrice > 0) {
        selectedItem!!.purchasePrice.toString().removeSuffix(".0")
      } else ""
    )
  }
  var salePriceText by remember(selectedItem, isNewItemMode) {
    mutableStateOf(
      if (!isNewItemMode && selectedItem != null) {
        selectedItem!!.salePrice.toString().removeSuffix(".0")
      } else ""
    )
  }
  var gstRate by remember(selectedItem, isNewItemMode, isPlainNonGst, appSettings) {
    mutableStateOf(
      when {
        isPlainNonGst -> 0.0
        !isNewItemMode && selectedItem != null -> selectedItem!!.gstRatePercent
        else -> appSettings.defaultGstSlabPercent
      }
    )
  }
  var qtyText by remember { mutableStateOf("10") }
  var lowThresholdText by remember {
    mutableStateOf(appSettings.defaultLowStockThreshold.toInt().toString())
  }

  val suppliers = remember(parties) { parties.filter { it.partyType == ErpParty.TYPE_SUPPLIER } }
  var selectedSupplier by remember { mutableStateOf<ErpParty?>(null) }
  var walkInSupplierName by remember { mutableStateOf("") }
  var billNumber by remember { mutableStateOf("PUR-${System.currentTimeMillis() % 10000}") }
  var paymentMethod by remember { mutableStateOf(appSettings.defaultPurchasePaymentMethod) }
  var isPaidNow by remember { mutableStateOf(true) }
  var errorText by remember { mutableStateOf<String?>(null) }

  val parsedQty = qtyText.toDoubleOrNull() ?: 0.0
  val parsedCost = purchasePriceText.toDoubleOrNull() ?: 0.0
  val taxable = (parsedQty * parsedCost).coerceAtLeast(0.0)
  val gstItc = if (isPlainNonGst) 0.0 else taxable * (gstRate / 100.0)
  val grandTotal = taxable + gstItc

  val units = listOf("PCS", "BOX", "KG", "LTR", "MTR", "SET")
  val gstSlabs = listOf(0.0, 5.0, 12.0, 18.0, 28.0)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.ShoppingCartCheckout, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Text("Add Purchase / Stock-In")
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (inventory.isNotEmpty()) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = !isNewItemMode,
              onClick = { isNewItemMode = false },
              label = { Text("Existing SKU") },
              modifier = Modifier.testTag("purchase_mode_existing"),
            )
            FilterChip(
              selected = isNewItemMode,
              onClick = { isNewItemMode = true },
              label = { Text("+ New Item") },
              modifier = Modifier.testTag("purchase_mode_new"),
            )
          }
        }

        if (!isNewItemMode && inventory.isNotEmpty()) {
          Text("Select Product SKU", style = MaterialTheme.typography.labelLarge)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(inventory, key = { it.id }) { item ->
              FilterChip(
                selected = selectedItem?.id == item.id,
                onClick = { selectedItem = item },
                label = { Text("${item.name} (${item.stockQty.toInt()} ${item.unit})") },
              )
            }
          }
        } else {
          OutlinedTextField(
            value = itemName,
            onValueChange = { itemName = it },
            label = { Text("New Item Name *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("purchase_new_item_name"),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = sku,
              onValueChange = { sku = it.uppercase() },
              label = { Text("SKU Code") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = category,
              onValueChange = { category = it },
              label = { Text("Category") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }
        }

        if (!isPlainNonGst) {
          HsnSearchSelector(
            hsnCode = hsnCode,
            onHsnCodeChange = { hsnCode = it },
            onSelectEntry = { entry ->
              gstRate = entry.gstRatePercent
              if (category == "General") category = entry.category
            },
            itemNameHint = if (isNewItemMode) itemName else (selectedItem?.name ?: ""),
            isPlainNonGst = isPlainNonGst,
            testTagPrefix = "purchase_hsn",
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = qtyText,
            onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Purchase Qty *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("purchase_qty_input"),
          )
          OutlinedTextField(
            value = purchasePriceText,
            onValueChange = { purchasePriceText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Buy Rate ($currency) *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("purchase_rate_input"),
          )
        }

        if (isNewItemMode) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = salePriceText,
              onValueChange = { salePriceText = it.filter { c -> c.isDigit() || c == '.' } },
              label = { Text("Sale Price ($currency)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("purchase_sale_price_input"),
            )
            OutlinedTextField(
              value = lowThresholdText,
              onValueChange = { lowThresholdText = it.filter { c -> c.isDigit() || c == '.' } },
              label = { Text("Low Alert At") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            units.forEach { u ->
              FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) })
            }
          }
        }

        if (!isPlainNonGst) {
          Text("Purchase GST Slab", style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            gstSlabs.forEach { slab ->
              FilterChip(
                selected = gstRate == slab,
                onClick = { gstRate = slab },
                label = { Text("${slab.toInt()}%") },
              )
            }
          }
        }

        HorizontalDivider()
        Text("Supplier & Bill Details", style = MaterialTheme.typography.labelLarge)
        if (suppliers.isNotEmpty()) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
              FilterChip(
                selected = selectedSupplier == null,
                onClick = { selectedSupplier = null },
                label = { Text("Other Vendor") },
              )
            }
            items(suppliers, key = { it.id }) { sup ->
              FilterChip(
                selected = selectedSupplier?.id == sup.id,
                onClick = { selectedSupplier = sup },
                label = { Text(sup.name) },
              )
            }
          }
        }
        if (selectedSupplier == null) {
          OutlinedTextField(
            value = walkInSupplierName,
            onValueChange = { walkInSupplierName = it },
            label = { Text("Supplier / Vendor Name") },
            placeholder = { Text("e.g. Wholesale Hub") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("purchase_supplier_input"),
          )
        }
        OutlinedTextField(
          value = billNumber,
          onValueChange = { billNumber = it.uppercase() },
          label = { Text("Purchase Bill / Ref #") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = isPaidNow,
            onClick = { isPaidNow = true },
            label = { Text("Paid Now") },
          )
          FilterChip(
            selected = !isPaidNow,
            onClick = { isPaidNow = false },
            label = { Text("Credit / Due") },
          )
        }

        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Stock Purchase Total", style = MaterialTheme.typography.labelLarge)
              Text(formatErpMoney(currency, grandTotal), style = MonoMetricMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (!isPlainNonGst && gstItc > 0.0) {
              Text(
                text = "Includes ${formatErpMoney(currency, gstItc)} GST Input Credit (ITC)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
            if (appSettings.autoRecordPurchaseInExpenses) {
              Text(
                text = "Auto-logs into Expenses & Cashbook (configurable in Settings)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        if (errorText != null) {
          Text(errorText.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val activeExisting = if (isNewItemMode) null else selectedItem
          if (activeExisting == null && itemName.isBlank()) {
            errorText = "Enter item name or choose an existing SKU"
          } else if (parsedQty <= 0.0) {
            errorText = "Purchase quantity must be positive"
          } else if (parsedCost <= 0.0) {
            errorText = "Purchase rate must be greater than zero"
          } else {
            errorText = null
            val resolvedSale = salePriceText.toDoubleOrNull() ?: (parsedCost * 1.2)
            onRecordPurchase(
              activeExisting,
              itemName,
              sku,
              hsnCode,
              category,
              unit,
              parsedCost,
              resolvedSale.coerceAtLeast(parsedCost),
              if (isPlainNonGst) 0.0 else gstRate,
              parsedQty,
              lowThresholdText.toDoubleOrNull() ?: appSettings.defaultLowStockThreshold,
              selectedSupplier,
              walkInSupplierName,
              billNumber,
              paymentMethod,
              isPaidNow,
              onDismiss,
            )
          }
        },
        enabled = !isBusy,
        modifier = Modifier.testTag("confirm_purchase_button"),
      ) {
        Text(if (isBusy) "Saving…" else "Save Purchase")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
