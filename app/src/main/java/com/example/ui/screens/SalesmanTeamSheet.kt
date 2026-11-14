package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessProfile
import com.example.data.model.SalesmanPermissions
import com.example.data.model.SalesmanRolePreset
import com.example.data.model.SalesmanSeat
import com.example.data.model.SalesmanSeatConfig

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SalesmanTeamSheet(
  salesmen: List<SalesmanSeat>,
  profiles: List<BusinessProfile>,
  isBusy: Boolean,
  onSaveSeat: (
    seatId: String,
    existingSeat: SalesmanSeat?,
    name: String,
    phoneE164: String,
    roleLabel: String,
    isActive: Boolean,
    allowedProfiles: List<String>,
    permissions: SalesmanPermissions,
    onDone: () -> Unit,
  ) -> Unit,
  onToggleActive: (SalesmanSeat, Boolean) -> Unit,
  onDeleteSeat: (SalesmanSeat) -> Unit,
  onDismiss: () -> Unit,
) {
  var editingTarget by remember { mutableStateOf<Pair<String, SalesmanSeat?>?>(null) }
  var seatPendingDelete by remember { mutableStateOf<SalesmanSeat?>(null) }

  val seatsBySlot =
    remember(salesmen) {
      salesmen.associateBy { it.seatId.ifBlank { it.id } }
    }
  val activeCount = remember(salesmen) { salesmen.count { it.isActive } }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(
            modifier =
              Modifier.size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Badge,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          Column {
            Text(
              text = "Salesman Team & OTP Access",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "${salesmen.size}/5 Seats Assigned • $activeCount Active via Mobile OTP",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
          )
          Text(
            text =
              "Add up to 5 salesmen by mobile number. They sign in with SMS OTP on the login screen and only see the modules & business accounts you allow.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      SalesmanSeatConfig.SEAT_IDS.forEachIndexed { index, seatId ->
        val assigned = seatsBySlot[seatId]
        val seatNumber = index + 1
        if (assigned != null) {
          SalesmanSeatCard(
            seatNumber = seatNumber,
            seat = assigned,
            isBusy = isBusy,
            onEdit = { editingTarget = seatId to assigned },
            onToggleActive = { active -> onToggleActive(assigned, active) },
            onDelete = { seatPendingDelete = assigned },
          )
        } else {
          EmptySalesmanSlotCard(
            seatNumber = seatNumber,
            seatId = seatId,
            onAssign = { editingTarget = seatId to null },
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }

  editingTarget?.let { (seatId, existingSeat) ->
    SalesmanSeatEditorDialog(
      seatId = seatId,
      existingSeat = existingSeat,
      profiles = profiles,
      isBusy = isBusy,
      onDismiss = { editingTarget = null },
      onSave = { name, phoneE164, roleLabel, isActive, allowedProfiles, permissions ->
        onSaveSeat(
          seatId,
          existingSeat,
          name,
          phoneE164,
          roleLabel,
          isActive,
          allowedProfiles,
          permissions,
        ) {
          editingTarget = null
        }
      },
    )
  }

  seatPendingDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { seatPendingDelete = null },
      title = { Text("Remove ${target.name}?") },
      text = {
        Text(
          "Removing ${target.name} (${target.phoneNumber}) immediately revokes their Mobile OTP access to your ERP workspace."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteSeat(target)
            seatPendingDelete = null
          },
          modifier = Modifier.testTag("confirm_delete_salesman_button"),
        ) {
          Text("Remove Salesman")
        }
      },
      dismissButton = {
        TextButton(onClick = { seatPendingDelete = null }) {
          Text("Cancel")
        }
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalesmanSeatCard(
  seatNumber: Int,
  seat: SalesmanSeat,
  isBusy: Boolean,
  onEdit: () -> Unit,
  onToggleActive: (Boolean) -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (seat.isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
          else MaterialTheme.colorScheme.outlineVariant,
      ),
    modifier = Modifier.fillMaxWidth().testTag("salesman_card_${seat.seatId.ifBlank { seat.id }}"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier =
              Modifier.size(40.dp)
                .clip(CircleShape)
                .background(
                  if (seat.isActive) MaterialTheme.colorScheme.secondaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "#$seatNumber",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color =
                if (seat.isActive) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = seat.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Surface(
                color =
                  if (seat.isActive) MaterialTheme.colorScheme.secondaryContainer
                  else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(50),
              ) {
                Text(
                  text = if (seat.isActive) "ACTIVE" else "PAUSED",
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (seat.isActive) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.error,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
            }
            val instantOtp =
              remember(seat.phoneNumber) {
                com.example.auth.generateInstantSalesmanOtp(seat.phoneNumber)
              }
            Text(
              text = "${seat.phoneNumber} • ${seat.roleLabel} • 6-Digit OTP: $instantOtp",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Switch(
          checked = seat.isActive,
          onCheckedChange = onToggleActive,
          enabled = !isBusy,
          modifier = Modifier.testTag("salesman_active_switch_${seat.seatId.ifBlank { seat.id }}"),
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        val p = seat.permissions
        val activeBadges = buildList {
          if (p.canCreateInvoices) add("Create Bills")
          else if (p.canViewBilling) add("View Bills")
          if (p.canEditDeleteInvoices) add("Edit/Delete Bills")
          if (p.canPrintInvoices) add("Print/PDF")
          if (p.canManageStock) add("Stock & Purchase")
          else if (p.canViewStock) add("View Stock")
          if (p.canManageParties) add("Manage Khata")
          else if (p.canViewParties) add("View Parties")
          if (p.canManageExpenses) add("Manage Expenses")
          else if (p.canViewExpenses) add("View Cashbook")
          if (p.canViewReports) add("GST & P&L Reports")
        }
        activeBadges.forEach { badge ->
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            shape = RoundedCornerShape(50),
          ) {
            Text(
              text = badge,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val profileLabels =
          seat.allowedProfiles.map { slot ->
            when (slot) {
              BusinessProfile.SLOT_MAIN_2 -> "Main GST #2"
              BusinessProfile.SLOT_PLAIN_1 -> "Plain Bill"
              else -> "Main GST #1"
            }
          }
        Text(
          text = "Profiles: ${profileLabels.joinToString(", ")}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          TextButton(
            onClick = onEdit,
            modifier = Modifier.heightIn(min = 48.dp).testTag("edit_salesman_${seat.seatId.ifBlank { seat.id }}"),
          ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Permissions")
          }
          IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("delete_salesman_${seat.seatId.ifBlank { seat.id }}"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Remove Salesman",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EmptySalesmanSlotCard(
  seatNumber: Int,
  seatId: String,
  onAssign: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onAssign)
        .testTag("empty_salesman_slot_$seatId"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f),
      ) {
        Box(
          modifier =
            Modifier.size(38.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surface),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.PhoneAndroid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
        }
        Column {
          Text(
            text = "Salesman Slot #$seatNumber (Available)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Assign mobile number for OTP login & configure module access",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      OutlinedButton(
        onClick = onAssign,
        modifier = Modifier.heightIn(min = 48.dp).testTag("assign_salesman_button_$seatId"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add")
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalesmanSeatEditorDialog(
  seatId: String,
  existingSeat: SalesmanSeat?,
  profiles: List<BusinessProfile>,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    phoneE164: String,
    roleLabel: String,
    isActive: Boolean,
    allowedProfiles: List<String>,
    permissions: SalesmanPermissions,
  ) -> Unit,
) {
  val initialPhone = existingSeat?.phoneNumber.orEmpty()
  val initialCc =
    if (initialPhone.startsWith("+91")) "+91"
    else if (initialPhone.startsWith("+1")) "+1"
    else "+91"
  val initialLocal =
    if (initialPhone.startsWith(initialCc)) initialPhone.removePrefix(initialCc)
    else initialPhone

  var name by remember(existingSeat) { mutableStateOf(existingSeat?.name.orEmpty()) }
  var countryCode by remember(existingSeat) { mutableStateOf(initialCc) }
  var phoneLocal by remember(existingSeat) { mutableStateOf(initialLocal) }
  var roleLabel by remember(existingSeat) {
    mutableStateOf(existingSeat?.roleLabel ?: SalesmanRolePreset.COUNTER_BILLING.label)
  }
  var isActive by remember(existingSeat) { mutableStateOf(existingSeat?.isActive ?: true) }
  var allowedProfiles by remember(existingSeat) {
    mutableStateOf(
      existingSeat?.allowedProfiles?.ifEmpty { listOf(BusinessProfile.SLOT_MAIN_1) }
        ?: listOf(BusinessProfile.SLOT_MAIN_1)
    )
  }
  var perms by remember(existingSeat) {
    mutableStateOf(existingSeat?.permissions ?: SalesmanPermissions.COUNTER_BILLING)
  }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text =
          if (existingSeat == null) "Add ${SalesmanSeatConfig.seatLabel(seatId)}"
          else "Edit ${existingSeat.name} (${SalesmanSeatConfig.seatLabel(seatId)})",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Salesman Full Name") },
          placeholder = { Text("e.g. Rahul Verma") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("salesman_name_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = countryCode,
            onValueChange = { raw ->
              val digits = raw.filter { it.isDigit() }.take(4)
              countryCode = if (digits.isEmpty()) "+" else "+$digits"
            },
            label = { Text("Code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.width(88.dp).testTag("seat_country_code_input"),
          )
          OutlinedTextField(
            value = phoneLocal,
            onValueChange = { raw ->
              phoneLocal = raw.filter { it.isDigit() || it == '+' }.take(15)
            },
            label = { Text("Mobile Number (for OTP)") },
            placeholder = { Text("9876543210") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.weight(1f).testTag("seat_phone_input"),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Allow Mobile OTP Login", style = MaterialTheme.typography.titleSmall)
            Text(
              "Turn off anytime to pause salesman access immediately",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = isActive,
            onCheckedChange = { isActive = it },
            modifier = Modifier.testTag("seat_active_toggle"),
          )
        }

        HorizontalDivider()

        Text(
          text = "Quick Role Presets (Owner Can Customize Below)",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          SalesmanRolePreset.entries.forEach { preset ->
            FilterChip(
              selected = roleLabel == preset.label && perms == preset.permissions,
              onClick = {
                roleLabel = preset.label
                perms = preset.permissions
              },
              label = { Text(preset.label) },
              modifier = Modifier.testTag("preset_chip_${preset.name.lowercase()}"),
            )
          }
        }

        HorizontalDivider()

        Text(
          text = "Allowed Business Accounts",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "Select which of your 3 business profiles this salesman can view & bill from:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val slotSpecs =
          listOf(
            BusinessProfile.SLOT_MAIN_1 to
              (profiles.firstOrNull { it.id == BusinessProfile.SLOT_MAIN_1 }?.businessName
                ?: "Main GST Account 1"),
            BusinessProfile.SLOT_MAIN_2 to
              (profiles.firstOrNull { it.id == BusinessProfile.SLOT_MAIN_2 }?.businessName
                ?: "Main GST Account 2"),
            BusinessProfile.SLOT_PLAIN_1 to
              (profiles.firstOrNull { it.id == BusinessProfile.SLOT_PLAIN_1 }?.businessName
                ?: "Plain Non-GST Account"),
          )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          slotSpecs.forEach { (slotId, title) ->
            val selected = slotId in allowedProfiles
            FilterChip(
              selected = selected,
              onClick = {
                allowedProfiles =
                  if (selected && allowedProfiles.size > 1) {
                    allowedProfiles - slotId
                  } else if (!selected) {
                    allowedProfiles + slotId
                  } else {
                    allowedProfiles
                  }
              },
              label = { Text(title) },
              modifier = Modifier.testTag("allow_profile_chip_$slotId"),
            )
          }
        }

        HorizontalDivider()

        Text(
          text = "Granular Module Access (Owner Decides)",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )

        PermissionToggleRow(
          title = "Billing: View Invoices & Bills",
          subtitle = "See invoice list and payment statuses",
          checked = perms.canViewBilling,
          tag = "perm_view_billing",
          onCheckedChange = { checked ->
            perms =
              perms.copy(
                canViewBilling = checked,
                canCreateInvoices = if (!checked) false else perms.canCreateInvoices,
                canEditDeleteInvoices = if (!checked) false else perms.canEditDeleteInvoices,
              )
          },
        )
        PermissionToggleRow(
          title = "Billing: Create New Bills / POS Invoices",
          subtitle = "Generate GST or Plain bills (auto-deducts stock)",
          checked = perms.canCreateInvoices,
          tag = "perm_create_invoices",
          onCheckedChange = { checked ->
            perms =
              perms.copy(
                canCreateInvoices = checked,
                canViewBilling = if (checked) true else perms.canViewBilling,
              )
          },
        )
        PermissionToggleRow(
          title = "Billing: Edit, Settle & Delete Bills",
          subtitle = "Modify existing invoices, record bill payments, or delete bills",
          checked = perms.canEditDeleteInvoices,
          tag = "perm_edit_delete_invoices",
          onCheckedChange = { checked ->
            perms =
              perms.copy(
                canEditDeleteInvoices = checked,
                canViewBilling = if (checked) true else perms.canViewBilling,
              )
          },
        )
        PermissionToggleRow(
          title = "Billing: Print & Download PDF",
          subtitle = "Print thermal/A4 receipts and share invoice PDFs",
          checked = perms.canPrintInvoices,
          tag = "perm_print_invoices",
          onCheckedChange = { perms = perms.copy(canPrintInvoices = it) },
        )

        PermissionToggleRow(
          title = "Inventory: View Stock Catalog",
          subtitle = "View SKU stock quantities and sale prices",
          checked = perms.canViewStock,
          tag = "perm_view_stock",
          onCheckedChange = { checked ->
            perms =
              perms.copy(
                canViewStock = checked,
                canManageStock = if (!checked) false else perms.canManageStock,
              )
          },
        )
        PermissionToggleRow(
          title = "Inventory: Add SKU, Stock-In Purchase & Adjust",
          subtitle = "Add items, record supplier purchases, and edit stock",
          checked = perms.canManageStock,
          tag = "perm_manage_stock",
          onCheckedChange = { checked ->
            perms =
              perms.copy(
                canManageStock = checked,
                canViewStock = if (checked) true else perms.canViewStock,
              )
          },
        )

        PermissionToggleRow(
          title = "Parties: View Customer & Supplier Khata",
          subtitle = "View party directory and outstanding balances",
          checked = perms.canViewParties,
          tag = "perm_view_parties",
          onCheckedChange = { checked ->
            perms =
              perms.copy(
                canViewParties = checked,
                canManageParties = if (!checked) false else perms.canManageParties,
              )
          },
        )
        PermissionToggleRow(
          title = "Parties: Add Party & Settle Khata Dues",
          subtitle = "Add/edit customers & suppliers and record Khata payments",
          checked = perms.canManageParties,
          tag = "perm_manage_parties",
          onCheckedChange = { checked ->
            perms =
              perms.copy(
                canManageParties = checked,
                canViewParties = if (checked) true else perms.canViewParties,
              )
          },
        )

        PermissionToggleRow(
          title = "Expenses: View Expenses & Cashbook",
          subtitle = "View operating expenses and cash-in-hand ledger",
          checked = perms.canViewExpenses,
          tag = "perm_view_expenses",
          onCheckedChange = { checked ->
            perms =
              perms.copy(
                canViewExpenses = checked,
                canManageExpenses = if (!checked) false else perms.canManageExpenses,
              )
          },
        )
        PermissionToggleRow(
          title = "Expenses: Record & Edit Expenses",
          subtitle = "Add, settle, or delete operating expenses",
          checked = perms.canManageExpenses,
          tag = "perm_manage_expenses",
          onCheckedChange = { checked ->
            perms =
              perms.copy(
                canManageExpenses = checked,
                canViewExpenses = if (checked) true else perms.canViewExpenses,
              )
          },
        )

        PermissionToggleRow(
          title = "Dashboard: View Executive Overview KPIs",
          subtitle = "See revenue, receivables, and target summary",
          checked = perms.canViewOverview,
          tag = "perm_view_overview",
          onCheckedChange = { checked ->
            perms =
              perms.copy(
                canViewOverview = checked,
                canAddQuickEntry = if (!checked) false else perms.canAddQuickEntry,
              )
          },
        )
        PermissionToggleRow(
          title = "Dashboard: Add Quick Cashbook Entries",
          subtitle = "Create or settle quick cash entries from Overview",
          checked = perms.canAddQuickEntry,
          tag = "perm_add_quick_entry",
          onCheckedChange = { checked ->
            perms =
              perms.copy(
                canAddQuickEntry = checked,
                canViewOverview = if (checked) true else perms.canViewOverview,
              )
          },
        )

        PermissionToggleRow(
          title = "Reports: View GST & P&L Tax Reports",
          subtitle = "View GSTR-1/3B summaries and net profit reports",
          checked = perms.canViewReports,
          tag = "perm_view_reports",
          onCheckedChange = { perms = perms.copy(canViewReports = it) },
        )

        if (errorText != null) {
          Text(
            text = errorText.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val cleanName = name.trim()
          val e164 = SalesmanSeatConfig.normalizePhoneToE164(countryCode, phoneLocal)
          if (cleanName.isBlank()) {
            errorText = "Please enter the salesman's name"
          } else if (e164 == null) {
            errorText = "Enter a valid 10-digit mobile number with country code"
          } else if (perms.enabledCount() == 0) {
            errorText = "Enable at least one module permission for this salesman"
          } else {
            errorText = null
            onSave(cleanName, e164, roleLabel, isActive, allowedProfiles, perms)
          }
        },
        enabled = !isBusy,
        modifier = Modifier.testTag("save_salesman_seat_button"),
      ) {
        Text(if (isBusy) "Saving…" else "Save Salesman Access")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isBusy) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun PermissionToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  tag: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
      Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(tag),
    )
  }
}
