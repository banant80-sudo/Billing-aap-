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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ErpParty
import com.example.data.model.InventoryItem
import com.example.data.sync.ErpAppSettings
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.viewmodel.UiState

@Composable
fun InventoryModuleContent(
  inventoryState: UiState<List<InventoryItem>>,
  parties: List<ErpParty> = emptyList(),
  currency: String,
  isPlainNonGst: Boolean = false,
  appSettings: ErpAppSettings = ErpAppSettings(),
  isBusy: Boolean,
  onSaveItem: (
    existingId: String?,
    name: String,
    sku: String,
    category: String,
    unit: String,
    salePrice: Double,
    purchasePrice: Double,
    gstRatePercent: Double,
    stockQty: Double,
    lowStockThreshold: Double,
    hsnCode: String,
    onDone: () -> Unit,
  ) -> Unit,
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
  ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, onDone -> onDone() },
  onAdjustStock: (item: InventoryItem, deltaQty: Double, onDone: () -> Unit) -> Unit,
  onDeleteItem: (String) -> Unit,
  showAddItemDialog: Boolean,
  onDismissAddItemDialog: () -> Unit,
  onOpenAddItemDialog: () -> Unit,
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var stockFilter by rememberSaveable { mutableStateOf("all") }
  var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
  var adjustItem by remember { mutableStateOf<InventoryItem?>(null) }
  var showAddPurchaseDialog by remember { mutableStateOf(false) }

  val items = (inventoryState as? UiState.Success)?.data.orEmpty()
  val totalValuation = remember(items) { items.sumOf { it.stockValuation } }
  val lowCount = remember(items) { items.count { it.isLowStock || it.isOutOfStock } }

  val filteredItems =
    remember(items, stockFilter, searchQuery) {
      items.filter { item ->
        val matchesFilter =
          when (stockFilter) {
            "in_stock" -> !item.isLowStock && !item.isOutOfStock
            "low_stock" -> item.isLowStock
            "out_of_stock" -> item.isOutOfStock
            else -> true
          }
        val q = searchQuery.trim().lowercase()
        val matchesQuery =
          q.isEmpty() ||
            item.name.lowercase().contains(q) ||
            item.sku.lowercase().contains(q) ||
            item.hsnCode.lowercase().contains(q) ||
            item.category.lowercase().contains(q)
        matchesFilter && matchesQuery
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
          title = "Stock Value",
          value = formatErpMoney(currency, totalValuation),
          accent = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        SummaryStatPill(
          title = "Low / Out Alerts",
          value = "$lowCount SKUs",
          accent =
            if (lowCount > 0) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.secondary,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = { showAddPurchaseDialog = true },
          modifier = Modifier.weight(1f).testTag("inventory_add_purchase_button"),
        ) {
          Icon(Icons.Filled.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add Purchase")
        }
        OutlinedButton(
          onClick = onOpenAddItemDialog,
          modifier = Modifier.weight(1f).testTag("inventory_quick_add_sku_button"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add Item SKU")
        }
      }
    }

    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text("Search SKUs by name, code, or category") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("inventory_search_input"),
      )
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val filters =
          listOf(
            "all" to "All (${items.size})",
            "in_stock" to "Healthy",
            "low_stock" to "Low Stock",
            "out_of_stock" to "Out of Stock",
          )
        items(filters) { (key, label) ->
          FilterChip(
            selected = stockFilter == key,
            onClick = { stockFilter = key },
            label = { Text(label) },
          )
        }
      }
    }

    when (inventoryState) {
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
            text = inventoryState.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
      is UiState.Success -> {
        if (filteredItems.isEmpty()) {
          item {
            ModuleEmptyCard(
              icon = Icons.Filled.Inventory2,
              title = if (items.isEmpty()) "No Products in Inventory" else "No Matching SKUs",
              subtitle =
                if (items.isEmpty()) {
                  if (isPlainNonGst)
                    "Add your products with sale price and stock levels for instant billing auto-deduction."
                  else
                    "Add your products with sale price, GST %, and stock levels for instant billing auto-deduction."
                } else "Try adjusting your search or stock filter.",
              actionLabel = "Add SKU Item",
              onAction = onOpenAddItemDialog,
            )
          }
        } else {
          items(filteredItems, key = { it.id }) { item ->
            InventoryItemCard(
              item = item,
              currency = currency,
              isPlainNonGst = isPlainNonGst,
              onQuickIncrement = { onAdjustStock(item, 1.0) {} },
              onQuickDecrement = { onAdjustStock(item, -1.0) {} },
              onAdjustCustom = { adjustItem = item },
              onEdit = { editingItem = item },
              onDelete = { onDeleteItem(item.id) },
            )
          }
        }
      }
    }
  }

  if (showAddItemDialog || editingItem != null) {
    InventoryFormDialog(
      existing = editingItem,
      currency = currency,
      isPlainNonGst = isPlainNonGst,
      defaultGstRate = appSettings.defaultGstSlabPercent,
      defaultLowThreshold = appSettings.defaultLowStockThreshold,
      isBusy = isBusy,
      onDismiss = {
        editingItem = null
        onDismissAddItemDialog()
      },
      onSave = { name, sku, category, unit, sale, purchase, gst, qty, threshold, hsnCode ->
        onSaveItem(
          editingItem?.id,
          name,
          sku,
          category,
          unit,
          sale,
          purchase,
          gst,
          qty,
          threshold,
          hsnCode,
        ) {
          editingItem = null
          onDismissAddItemDialog()
        }
      },
    )
  }

  if (showAddPurchaseDialog) {
    AddPurchaseDialog(
      inventory = items,
      parties = parties,
      currency = currency,
      isPlainNonGst = isPlainNonGst,
      appSettings = appSettings,
      isBusy = isBusy,
      onDismiss = { showAddPurchaseDialog = false },
      onRecordPurchase = onRecordPurchase,
    )
  }

  val activeAdjust = adjustItem
  if (activeAdjust != null) {
    StockAdjustDialog(
      item = activeAdjust,
      isBusy = isBusy,
      onDismiss = { adjustItem = null },
      onSubmit = { delta ->
        onAdjustStock(activeAdjust, delta) { adjustItem = null }
      },
    )
  }
}

@Composable
private fun InventoryItemCard(
  item: InventoryItem,
  currency: String,
  isPlainNonGst: Boolean,
  onQuickIncrement: () -> Unit,
  onQuickDecrement: () -> Unit,
  onAdjustCustom: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val (stockLabel, badgeBg, badgeFg) =
    when {
      item.isOutOfStock ->
        Triple(
          "OUT OF STOCK",
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.error,
        )
      item.isLowStock ->
        Triple(
          "LOW: ${formatQty(item.stockQty)} ${item.unit}",
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
        )
      else ->
        Triple(
          "${formatQty(item.stockQty)} ${item.unit}",
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth().testTag("inventory_card_${item.id}"),
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
          Icon(Icons.Filled.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = item.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              buildString {
                append("${item.sku} • ${item.category}")
                if (!isPlainNonGst) {
                  if (item.hsnCode.isNotBlank()) append(" • HSN ${item.hsnCode}")
                  append(" • GST ${item.gstRatePercent.toInt()}%")
                }
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = formatErpMoney(currency, item.salePrice),
            style = MonoMetricMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
            Text(
              text = stockLabel,
              style = MaterialTheme.typography.labelSmall,
              color = badgeFg,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          IconButton(onClick = onQuickDecrement, enabled = item.stockQty > 0.0) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease stock")
          }
          OutlinedButton(onClick = onAdjustCustom) {
            Text("Adjust")
          }
          IconButton(onClick = onQuickIncrement) {
            Icon(Icons.Filled.Add, contentDescription = "Increase stock")
          }
        }
        Row {
          IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit SKU", modifier = Modifier.size(18.dp))
          }
          IconButton(onClick = onDelete) {
            Icon(
              Icons.Filled.DeleteOutline,
              contentDescription = "Delete SKU",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InventoryFormDialog(
  existing: InventoryItem?,
  currency: String,
  isPlainNonGst: Boolean = false,
  defaultGstRate: Double = 18.0,
  defaultLowThreshold: Double = 5.0,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    sku: String,
    category: String,
    unit: String,
    salePrice: Double,
    purchasePrice: Double,
    gstRatePercent: Double,
    stockQty: Double,
    lowStockThreshold: Double,
    hsnCode: String,
  ) -> Unit,
) {
  var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
  var sku by remember(existing) { mutableStateOf(existing?.sku ?: "") }
  var hsnCode by remember(existing) { mutableStateOf(existing?.hsnCode ?: "") }
  var category by remember(existing) { mutableStateOf(existing?.category ?: "General") }
  var unit by remember(existing) { mutableStateOf(existing?.unit ?: "PCS") }
  var salePriceText by remember(existing) {
    mutableStateOf(if (existing == null) "" else existing.salePrice.toString().removeSuffix(".0"))
  }
  var purchasePriceText by remember(existing) {
    mutableStateOf(if (existing == null) "" else existing.purchasePrice.toString().removeSuffix(".0"))
  }
  var gstRate by remember(existing, isPlainNonGst, defaultGstRate) {
    mutableStateOf(if (isPlainNonGst) 0.0 else (existing?.gstRatePercent ?: defaultGstRate))
  }
  var stockQtyText by remember(existing) {
    mutableStateOf(if (existing == null) "10" else existing.stockQty.toString().removeSuffix(".0"))
  }
  var thresholdText by remember(existing, defaultLowThreshold) {
    mutableStateOf(
      if (existing == null) defaultLowThreshold.toInt().toString()
      else existing.lowStockThreshold.toString().removeSuffix(".0")
    )
  }
  var errorText by remember { mutableStateOf<String?>(null) }

  val units = listOf("PCS", "BOX", "KG", "LTR", "MTR", "SET")
  val gstSlabs = listOf(0.0, 5.0, 12.0, 18.0, 28.0)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "Add Inventory SKU" else "Edit Inventory SKU") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Item Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("inventory_name_input"),
        )
        if (!isPlainNonGst) {
          HsnSearchSelector(
            hsnCode = hsnCode,
            onHsnCodeChange = { hsnCode = it },
            onSelectEntry = { entry ->
              gstRate = entry.gstRatePercent
              if (category == "General") category = entry.category
            },
            itemNameHint = name,
            isPlainNonGst = isPlainNonGst,
            testTagPrefix = "inventory_hsn",
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = sku,
            onValueChange = { sku = it.uppercase() },
            label = { Text("SKU Code") },
            placeholder = { Text("SKU-01") },
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = salePriceText,
            onValueChange = { salePriceText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Sale Price ($currency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("inventory_sale_price_input"),
          )
          OutlinedTextField(
            value = purchasePriceText,
            onValueChange = { purchasePriceText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Cost ($currency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = stockQtyText,
            onValueChange = { stockQtyText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Current Stock") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = thresholdText,
            onValueChange = { thresholdText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Low Alert At") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        Text("Unit", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          units.forEach { u ->
            FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) })
          }
        }
        if (!isPlainNonGst) {
          Text("GST Slab", style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            gstSlabs.forEach { rate ->
              FilterChip(
                selected = gstRate == rate,
                onClick = { gstRate = rate },
                label = { Text("${rate.toInt()}%") },
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
          val sale = salePriceText.toDoubleOrNull()
          if (name.isBlank()) {
            errorText = "Item name is required"
          } else if (sale == null || sale < 0.0) {
            errorText = "Enter a valid sale price"
          } else {
            errorText = null
            onSave(
              name,
              sku,
              category,
              unit,
              sale,
              purchasePriceText.toDoubleOrNull() ?: 0.0,
              if (isPlainNonGst) 0.0 else gstRate,
              stockQtyText.toDoubleOrNull() ?: 0.0,
              thresholdText.toDoubleOrNull() ?: defaultLowThreshold,
              if (isPlainNonGst) "" else hsnCode,
            )
          }
        },
        enabled = !isBusy,
        modifier = Modifier.testTag("save_inventory_button"),
      ) {
        Text(if (isBusy) "Saving…" else "Save SKU")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun StockAdjustDialog(
  item: InventoryItem,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSubmit: (Double) -> Unit,
) {
  var isAdd by remember { mutableStateOf(true) }
  var qtyText by remember { mutableStateOf("5") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Adjust Stock • ${item.name}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Current stock: ${formatQty(item.stockQty)} ${item.unit}",
          style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(selected = isAdd, onClick = { isAdd = true }, label = { Text("+ Stock In") })
          FilterChip(selected = !isAdd, onClick = { isAdd = false }, label = { Text("- Stock Out") })
        }
        OutlinedTextField(
          value = qtyText,
          onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } },
          label = { Text("Quantity (${item.unit})") },
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
          val qty = qtyText.toDoubleOrNull()
          if (qty == null || qty <= 0.0) {
            errorText = "Enter a positive quantity"
          } else {
            errorText = null
            onSubmit(if (isAdd) qty else -qty)
          }
        },
        enabled = !isBusy,
      ) {
        Text("Apply")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

private fun formatQty(qty: Double): String {
  return if (qty % 1.0 == 0.0) qty.toLong().toString() else String.format(java.util.Locale.US, "%.2f", qty)
}
