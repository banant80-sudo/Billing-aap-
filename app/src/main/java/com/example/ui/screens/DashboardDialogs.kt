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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessProfile
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpModuleConfig
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.reference.IndiaStates
import com.example.util.WhatsAppShareHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BusinessProfileDialog(
  profile: BusinessProfile?,
  slotId: String = profile?.resolvedSlotId ?: BusinessProfile.SLOT_MAIN_1,
  defaultOwnerName: String = "",
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    ownerName: String,
    businessName: String,
    gstin: String,
    currencySymbol: String,
    fiscalYearLabel: String,
    businessType: String,
    monthlySalesTarget: Double,
    address: String,
    state: String,
    pincode: String,
  ) -> Unit,
) {
  val isPlainNonGst = slotId == BusinessProfile.SLOT_PLAIN_1
  var ownerName by remember(profile, slotId) { mutableStateOf(profile?.ownerName ?: defaultOwnerName) }
  var businessName by remember(profile, slotId) { mutableStateOf(profile?.businessName ?: "") }
  var gstin by remember(profile, slotId) {
    mutableStateOf(if (isPlainNonGst) "" else (profile?.gstin ?: ""))
  }
  var address by remember(profile, slotId) { mutableStateOf(profile?.address ?: "") }
  var state by remember(profile, slotId) { mutableStateOf(profile?.state ?: "") }
  var pincode by remember(profile, slotId) { mutableStateOf(profile?.pincode ?: "") }
  var currencySymbol by remember(profile, slotId) { mutableStateOf(profile?.currencySymbol ?: "₹") }
  var fiscalYear by remember(profile, slotId) {
    mutableStateOf(profile?.fiscalYearLabel ?: "FY 2026-27")
  }
  var businessType by remember(profile, slotId) {
    mutableStateOf(profile?.businessType ?: "Retail & Wholesale")
  }
  var targetText by remember(profile, slotId) {
    mutableStateOf((profile?.monthlySalesTarget ?: 250000.0).toLong().toString())
  }
  var validationError by remember { mutableStateOf<String?>(null) }

  val currencies = listOf("₹", "$", "€", "£", "AED")
  val businessTypes = listOf("Retail & Wholesale", "Services", "Distribution", "Manufacturing")

  val dialogTitle =
    when (slotId) {
      BusinessProfile.SLOT_PLAIN_1 -> "Plain Non-GST Profile"
      BusinessProfile.SLOT_MAIN_2 -> "Main GST Account 2"
      else -> "Main GST Account 1"
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = dialogTitle,
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (isPlainNonGst) {
          Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                "Plain Non-GST mode generates clean bills & PDF receipts with 0% GST and no GSTIN fields.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.padding(10.dp),
            )
          }
        }
        OutlinedTextField(
          value = businessName,
          onValueChange = { businessName = it },
          label = { Text("Company / Store Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("profile_business_name_input"),
        )
        OutlinedTextField(
          value = ownerName,
          onValueChange = { ownerName = it },
          label = { Text("Owner / Admin Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("profile_owner_name_input"),
        )
        if (!isPlainNonGst) {
          OutlinedTextField(
            value = gstin,
            onValueChange = { raw ->
              val upper = raw.uppercase()
              gstin = upper
              if (state.isBlank()) {
                IndiaStates.stateFromGstin(upper)?.let { state = it.name }
              }
            },
            label = { Text("GSTIN / Tax ID (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("profile_gstin_input"),
          )
        }
        OutlinedTextField(
          value = address,
          onValueChange = { address = it },
          label = { Text("Shop / Office Address") },
          maxLines = 2,
          modifier = Modifier.fillMaxWidth().testTag("profile_address_input"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          StatePickerField(
            selectedState = state,
            onStateSelected = { state = it },
            label = "Business State",
            testTag = "profile_state_picker",
            modifier = Modifier.weight(1.3f),
          )
          OutlinedTextField(
            value = pincode,
            onValueChange = { pincode = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text("PIN Code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(0.75f).testTag("profile_pincode_input"),
          )
        }
        OutlinedTextField(
          value = fiscalYear,
          onValueChange = { fiscalYear = it },
          label = { Text("Fiscal Year") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = targetText,
          onValueChange = { targetText = it.filter { ch -> ch.isDigit() || ch == '.' } },
          label = { Text("Monthly Revenue Target ($currencySymbol)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth().testTag("profile_target_input"),
        )

        Text(
          text = "Currency",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          currencies.forEach { symbol ->
            FilterChip(
              selected = currencySymbol == symbol,
              onClick = { currencySymbol = symbol },
              label = { Text(symbol) },
            )
          }
        }

        Text(
          text = "Business Sector",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          businessTypes.forEach { type ->
            FilterChip(
              selected = businessType == type,
              onClick = { businessType = type },
              label = { Text(type) },
            )
          }
        }

        if (validationError != null) {
          Text(
            text = validationError.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedTarget = targetText.toDoubleOrNull()
          if (businessName.isBlank()) {
            validationError = "Please enter a business name"
          } else if (parsedTarget == null || parsedTarget < 0) {
            validationError = "Enter a valid revenue target"
          } else {
            validationError = null
            onSave(
              ownerName,
              businessName,
              if (isPlainNonGst) "" else gstin,
              currencySymbol,
              fiscalYear,
              businessType,
              parsedTarget,
              address,
              state,
              pincode,
            )
          }
        },
        enabled = !isBusy,
        modifier = Modifier.testTag("save_profile_button"),
      ) {
        Text(if (isBusy) "Saving…" else "Save Profile")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessProfilesSheet(
  profiles: List<BusinessProfile>,
  activeSlotId: String,
  isBusy: Boolean,
  onSwitchProfile: (String) -> Unit,
  onCreateOrEditSlot: (slotId: String, existing: BusinessProfile?) -> Unit,
  onDeleteSlot: (String) -> Unit,
  onDismiss: () -> Unit,
  allowedSlots: List<String> = BusinessProfile.ALL_SLOTS,
  canManageProfiles: Boolean = true,
) {
  var slotPendingDelete by remember { mutableStateOf<BusinessProfile?>(null) }
  val visibleSlots = remember(allowedSlots, canManageProfiles, profiles) {
    val base = BusinessProfile.ALL_SLOTS.filter { it in allowedSlots }
    if (canManageProfiles) base
    else base.filter { slotId -> profiles.any { it.resolvedSlotId == slotId } }
  }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text =
            if (canManageProfiles) "Business Profiles (${profiles.size}/3 Accounts)"
            else "Allowed Business Accounts (${visibleSlots.size})",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text =
            if (canManageProfiles)
              "Create up to 2 Main GST accounts with full ERP modules and 1 Plain Non-GST account for simple billing."
            else
              "Switch between the business profiles assigned to your salesman account by the owner.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      visibleSlots.forEach { slotId ->
        val existing = profiles.firstOrNull { it.resolvedSlotId == slotId }
        val isPlain = slotId == BusinessProfile.SLOT_PLAIN_1
        val isActive = existing != null && activeSlotId == slotId
        val slotLabel =
          when (slotId) {
            BusinessProfile.SLOT_MAIN_1 -> "Main GST Account 1"
            BusinessProfile.SLOT_MAIN_2 -> "Main GST Account 2"
            else -> "Plain Non-GST Account"
          }
        val modeBadge = if (isPlain) "PLAIN NON-GST" else "MAIN GST • FULL ERP"

        Card(
          shape = MaterialTheme.shapes.medium,
          colors =
            CardDefaults.cardColors(
              containerColor =
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surface,
            ),
          border =
            BorderStroke(
              width = if (isActive) 1.5.dp else 1.dp,
              color =
                if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .then(
                if (existing != null && !isActive) {
                  Modifier.clickable(enabled = !isBusy) { onSwitchProfile(slotId) }
                } else {
                  Modifier
                }
              )
              .testTag("profile_slot_card_$slotId"),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector =
                    if (isPlain) Icons.AutoMirrored.Filled.ReceiptLong else Icons.Filled.Business,
                  contentDescription = null,
                  tint =
                    if (isPlain) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = slotLabel,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                  color =
                    if (isPlain) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                  shape = RoundedCornerShape(50),
                ) {
                  Text(
                    text = modeBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color =
                      if (isPlain) MaterialTheme.colorScheme.onTertiaryContainer
                      else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
                if (isActive) {
                  Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                  ) {
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

            if (existing != null) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = existing.businessName.ifBlank { slotLabel },
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                val metaParts =
                  listOfNotNull(
                    existing.businessType,
                    existing.fiscalYearLabel,
                    existing.gstin.takeIf { !isPlain && it.isNotBlank() }?.let { "GSTIN: $it" },
                    listOfNotNull(
                      existing.state.takeIf { it.isNotBlank() },
                      existing.pincode.takeIf { it.isNotBlank() }?.let { "PIN $it" },
                    ).joinToString(" ").takeIf { it.isNotBlank() },
                    if (isPlain) "0% GST • Plain Bills Only" else null,
                  )
                Text(
                  text = metaParts.joinToString(" • "),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (isActive) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.CheckCircle,
                      contentDescription = "Active profile",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text = "Selected",
                      style = MaterialTheme.typography.labelLarge,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                } else {
                  Button(
                    onClick = { onSwitchProfile(slotId) },
                    enabled = !isBusy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("switch_profile_$slotId"),
                  ) {
                    Text("Switch Account")
                  }
                }

                if (canManageProfiles) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    OutlinedButton(
                      onClick = { onCreateOrEditSlot(slotId, existing) },
                      enabled = !isBusy,
                      modifier = Modifier.heightIn(min = 48.dp).testTag("edit_profile_$slotId"),
                    ) {
                      Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Edit")
                    }
                    if (slotId != BusinessProfile.SLOT_MAIN_1) {
                      IconButton(
                        onClick = { slotPendingDelete = existing },
                        enabled = !isBusy,
                        modifier = Modifier.size(48.dp).testTag("delete_profile_$slotId"),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.DeleteOutline,
                          contentDescription = "Delete $slotLabel",
                          tint = MaterialTheme.colorScheme.error,
                        )
                      }
                    }
                  }
                }
              }
            } else if (canManageProfiles) {
              Text(
                text =
                  if (isPlain)
                    "Create a dedicated non-GST billing profile for plain cash bills & estimates without GSTIN."
                  else
                    "Add a second Main GST business account with its own invoices, stock, parties & ledger.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              OutlinedButton(
                onClick = { onCreateOrEditSlot(slotId, null) },
                enabled = !isBusy,
                modifier =
                  Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("create_profile_$slotId"),
              ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create $slotLabel")
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }

  slotPendingDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { if (!isBusy) slotPendingDelete = null },
      title = { Text("Remove ${target.businessName}?") },
      text = {
        Text(
          "This deletes the business profile slot from your account. You can recreate it later if needed.",
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteSlot(target.resolvedSlotId)
            slotPendingDelete = null
          },
          enabled = !isBusy,
          modifier = Modifier.testTag("confirm_delete_profile_button"),
        ) {
          Text("Remove")
        }
      },
      dismissButton = {
        TextButton(onClick = { slotPendingDelete = null }, enabled = !isBusy) {
          Text("Cancel")
        }
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddDashboardEntryDialog(
  currencySymbol: String,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSubmit: (
    title: String,
    partyName: String,
    category: String,
    amount: Double,
    status: String,
    referenceCode: String,
  ) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var partyName by remember { mutableStateOf("") }
  var referenceCode by remember { mutableStateOf("") }
  var amountText by remember { mutableStateOf("") }
  var category by remember { mutableStateOf(DashboardEntry.CATEGORY_SALE) }
  var status by remember { mutableStateOf(DashboardEntry.STATUS_PAID) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val categories =
    listOf(
      DashboardEntry.CATEGORY_SALE to "Sale / Billing",
      DashboardEntry.CATEGORY_RECEIVABLE to "Receivable Due",
      DashboardEntry.CATEGORY_EXPENSE to "Expense",
    )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Record Quick Overview Entry",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Category",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          categories.forEach { (key, label) ->
            FilterChip(
              selected = category == key,
              onClick = {
                category = key
                if (key == DashboardEntry.CATEGORY_RECEIVABLE) {
                  status = DashboardEntry.STATUS_PENDING
                }
              },
              label = { Text(label) },
            )
          }
        }

        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Particulars / Summary Title") },
          placeholder = { Text("e.g., Opening Counter Sales") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("entry_title_input"),
        )

        OutlinedTextField(
          value = partyName,
          onValueChange = { partyName = it },
          label = { Text("Party / Account Name (Optional)") },
          placeholder = { Text("e.g., Retail Counter or Supplier") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("entry_party_input"),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
            label = { Text("Amount ($currencySymbol)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).testTag("entry_amount_input"),
          )
          OutlinedTextField(
            value = referenceCode,
            onValueChange = { referenceCode = it.uppercase() },
            label = { Text("Ref #") },
            placeholder = { Text("INV-01") },
            singleLine = true,
            modifier = Modifier.weight(0.75f).testTag("entry_ref_input"),
          )
        }

        Text(
          text = "Settlement Status",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = status == DashboardEntry.STATUS_PAID,
            onClick = { status = DashboardEntry.STATUS_PAID },
            label = { Text("Settled / Paid") },
          )
          FilterChip(
            selected = status == DashboardEntry.STATUS_PENDING,
            onClick = { status = DashboardEntry.STATUS_PENDING },
            label = { Text("Pending") },
          )
        }

        if (validationError != null) {
          Text(
            text = validationError.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedAmount = amountText.toDoubleOrNull()
          if (title.isBlank()) {
            validationError = "Please enter an entry title"
          } else if (parsedAmount == null || parsedAmount <= 0.0) {
            validationError = "Amount must be greater than zero"
          } else {
            validationError = null
            onSubmit(title, partyName, category, parsedAmount, status, referenceCode)
          }
        },
        enabled = !isBusy,
        modifier = Modifier.testTag("submit_entry_button"),
      ) {
        Text(if (isBusy) "Saving…" else "Add Entry")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleDetailSheet(
  module: ErpModuleConfig,
  onDismiss: () -> Unit,
  onTogglePin: () -> Unit,
  onSetQueued: (Boolean) -> Unit,
) {
  val capabilities = remember(module.code) { moduleBlueprintFeatures(module.code) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          ModuleStatusBadge(status = module.status)
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = module.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
        }
        OutlinedButton(
          onClick = onTogglePin,
          modifier = Modifier.testTag("sheet_toggle_pin_${module.code}"),
        ) {
          Icon(
            imageVector = if (module.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            contentDescription = null,
          )
          Spacer(modifier = Modifier.padding(start = 6.dp))
          Text(if (module.isPinned) "Pinned" else "Pin")
        }
      }

      Text(
        text = module.subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Included Capabilities",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          capabilities.forEach { item ->
            Text(
              text = "• $item",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      if (module.code != "dashboard") {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = MaterialTheme.shapes.medium,
          tonalElevation = 1.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Prioritize for Next Rollout",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = "Mark this module as queued next in your Book build plan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = module.status == ErpModuleConfig.STATUS_QUEUED,
              onCheckedChange = onSetQueued,
              modifier = Modifier.testTag("sheet_queue_switch_${module.code}"),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
fun ModuleStatusBadge(status: String) {
  val (label, bg, fg) =
    when (status) {
      ErpModuleConfig.STATUS_ACTIVE ->
        Triple(
          "LIVE MODULE",
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
        )
      ErpModuleConfig.STATUS_QUEUED ->
        Triple(
          "NEXT IN QUEUE",
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
        )
      else ->
        Triple(
          "PLANNED",
          MaterialTheme.colorScheme.surfaceVariant,
          MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

  Surface(color = bg, shape = MaterialTheme.shapes.small) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = fg,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

private fun moduleBlueprintFeatures(code: String): List<String> =
  when (code) {
    "dashboard" ->
      listOf(
        "Real-time revenue, expense, receivables & net cashflow KPIs",
        "Monthly revenue target tracking with progress visualization",
        "Quick overview ledger entries and settlement status tracking",
        "Multi-user isolated Firestore cloud sync",
      )
    "billing" ->
      listOf(
        "GST & non-GST tax invoice builder with HSN/SAC rates",
        "Instant POS checkout, discounts & payment split (Cash/UPI/Card)",
        "PDF invoice sharing & thermal receipt layout",
      )
    "inventory" ->
      listOf(
        "SKU catalog with barcode/QR lookup and unit conversions",
        "Real-time stock-in/stock-out adjustments & low-stock alerts",
        "Batch valuation and warehouse summary",
      )
    "parties" ->
      listOf(
        "Customer & supplier directory with GSTIN verification",
        "Running khata ledger balance and credit limit tracking",
        "Pending dues summary and one-tap payment follow-ups",
      )
    "expenses" ->
      listOf(
        "Categorized operating expenses, rent, utilities & payroll",
        "Petty cashbook and bank account balance tracking",
        "Monthly burn-rate comparison against revenue",
      )
    else ->
      listOf(
        "GSTR-1 & GSTR-3B tax liability summaries",
        "Profit & Loss statement and monthly cashflow reports",
        "Exportable audit-ready ledger summaries",
      )
  }

private data class RecordedPaymentReceipt(
  val payerName: String,
  val payerPhone: String?,
  val message: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReceivePaymentDashboardDialog(
  invoices: List<GstInvoice>,
  parties: List<ErpParty>,
  businessName: String,
  currency: String,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onRecordInvoicePayment: (invoice: GstInvoice, amount: Double, method: String, onDone: () -> Unit) -> Unit,
  onRecordPartyPayment: (party: ErpParty, amount: Double, onDone: () -> Unit) -> Unit,
) {
  val context = LocalContext.current
  val pendingInvoices = remember(invoices) {
    invoices.filter { it.status != GstInvoice.STATUS_PAID && it.balanceDue > 0.0 }
  }
  val customersWithDues = remember(parties) {
    val withDues = parties.filter { it.partyType == ErpParty.TYPE_CUSTOMER && it.outstandingBalance > 0.0 }
    if (withDues.isNotEmpty()) withDues else parties.filter { it.partyType == ErpParty.TYPE_CUSTOMER }
  }

  var mode by remember(pendingInvoices.size, customersWithDues.size) {
    mutableStateOf(if (pendingInvoices.isNotEmpty() || customersWithDues.isEmpty()) "invoice" else "party")
  }
  var selectedInvoiceId by remember(pendingInvoices) {
    mutableStateOf(pendingInvoices.firstOrNull()?.id.orEmpty())
  }
  var selectedPartyId by remember(customersWithDues) {
    mutableStateOf(customersWithDues.firstOrNull()?.id.orEmpty())
  }

  val selectedInvoice = pendingInvoices.firstOrNull { it.id == selectedInvoiceId } ?: pendingInvoices.firstOrNull()
  val selectedParty = customersWithDues.firstOrNull { it.id == selectedPartyId } ?: customersWithDues.firstOrNull()

  var amountText by remember(mode, selectedInvoice?.id, selectedParty?.id) {
    val defaultAmt =
      if (mode == "invoice") selectedInvoice?.balanceDue ?: 0.0
      else selectedParty?.outstandingBalance ?: 0.0
    mutableStateOf(if (defaultAmt > 0.0) defaultAmt.toLong().toString() else "")
  }
  var payMethod by remember { mutableStateOf(GstInvoice.PAYMENT_UPI) }
  var validationError by remember { mutableStateOf<String?>(null) }
  var recordedReceipt by remember { mutableStateOf<RecordedPaymentReceipt?>(null) }

  AlertDialog(
    onDismissRequest = { if (!isBusy) onDismiss() },
    icon = {
      Icon(
        imageVector = if (recordedReceipt != null) Icons.Filled.CheckCircle else Icons.Filled.Payments,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
      )
    },
    title = {
      Text(
        text = if (recordedReceipt != null) "Payment Received" else "Receive Payment",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      val receipt = recordedReceipt
      if (receipt != null) {
        Column(
          modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = receipt.message,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
          Button(
            onClick = {
              WhatsAppShareHelper.shareTextToWhatsApp(
                context = context,
                text = receipt.message,
                rawPhoneNumber = receipt.payerPhone,
                chooserTitle = "Share Payment Receipt on WhatsApp",
              )
            },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
              ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("receive_payment_share_whatsapp_button"),
          ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Receipt on WhatsApp")
          }
        }
      } else {
        Column(
          modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = mode == "invoice",
              onClick = {
                mode = "invoice"
                validationError = null
              },
              label = { Text("Pending Bills (${pendingInvoices.size})") },
              modifier = Modifier.testTag("receive_mode_invoice_chip"),
            )
            FilterChip(
              selected = mode == "party",
              onClick = {
                mode = "party"
                validationError = null
              },
              label = { Text("Customer Khata (${customersWithDues.size})") },
              modifier = Modifier.testTag("receive_mode_party_chip"),
            )
          }

          if (mode == "invoice") {
            if (pendingInvoices.isEmpty()) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "All bills are currently paid! Switch to Customer Khata to record an account payment.",
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(12.dp),
                )
              }
            } else {
              Text(
                text = "Select Invoice to Collect",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pendingInvoices.take(8).forEach { inv ->
                  val isSel = selectedInvoice?.id == inv.id
                  Surface(
                    color =
                      if (isSel) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = MaterialTheme.shapes.small,
                    border =
                      if (isSel) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                      else null,
                    modifier =
                      Modifier.fillMaxWidth()
                        .clickable {
                          selectedInvoiceId = inv.id
                          validationError = null
                        }
                        .testTag("receive_invoice_option_${inv.invoiceNumber}"),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = "${inv.invoiceNumber} • ${inv.partyName}",
                          style = MaterialTheme.typography.labelLarge,
                          fontWeight = FontWeight.SemiBold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          text = "Total ${formatErpMoney(currency, inv.grandTotal)}",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Text(
                        text = "Due ${formatErpMoney(currency, inv.balanceDue)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                  }
                }
              }
            }
          } else {
            if (customersWithDues.isEmpty()) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "No customer parties added yet. Add a customer in Parties & Khata first.",
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(12.dp),
                )
              }
            } else {
              Text(
                text = "Select Customer Khata",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                customersWithDues.take(8).forEach { party ->
                  val isSel = selectedParty?.id == party.id
                  Surface(
                    color =
                      if (isSel) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = MaterialTheme.shapes.small,
                    border =
                      if (isSel) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                      else null,
                    modifier =
                      Modifier.fillMaxWidth()
                        .clickable {
                          selectedPartyId = party.id
                          validationError = null
                        }
                        .testTag("receive_party_option_${party.id}"),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = party.name,
                          style = MaterialTheme.typography.labelLarge,
                          fontWeight = FontWeight.SemiBold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        if (party.phone.isNotBlank()) {
                          Text(
                            text = party.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                          )
                        }
                      }
                      Text(
                        text =
                          if (party.outstandingBalance > 0.0) "Due ${formatErpMoney(currency, party.outstandingBalance)}"
                          else "Settled",
                        style = MaterialTheme.typography.labelLarge,
                        color =
                          if (party.outstandingBalance > 0.0) MaterialTheme.colorScheme.tertiary
                          else MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                  }
                }
              }
            }
          }

          OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
            label = { Text("Amount Received ($currency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("receive_payment_amount_input"),
          )

          if (mode == "invoice") {
            Text(
              text = "Payment Mode",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(
                GstInvoice.PAYMENT_UPI to "UPI",
                GstInvoice.PAYMENT_CASH to "Cash",
                GstInvoice.PAYMENT_CARD to "Card",
              ).forEach { (key, label) ->
                FilterChip(
                  selected = payMethod == key,
                  onClick = { payMethod = key },
                  label = { Text(label) },
                  modifier = Modifier.testTag("receive_method_chip_$key"),
                )
              }
            }
          }

          if (validationError != null) {
            Text(
              text = validationError.orEmpty(),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    },
    confirmButton = {
      if (recordedReceipt != null) {
        Button(
          onClick = onDismiss,
          modifier = Modifier.testTag("receive_payment_done_button"),
        ) {
          Text("Done")
        }
      } else {
        Button(
          onClick = {
            val parsedAmt = amountText.toDoubleOrNull() ?: 0.0
            if (parsedAmt <= 0.0) {
              validationError = "Enter a valid received amount greater than zero"
              return@Button
            }
            if (mode == "invoice") {
              val inv = selectedInvoice
              if (inv == null) {
                validationError = "No pending invoice selected"
                return@Button
              }
              validationError = null
              val targetPhone = parties.firstOrNull {
                (inv.partyId.isNotBlank() && it.id == inv.partyId) ||
                  it.name.equals(inv.partyName, ignoreCase = true)
              }?.phone
              onRecordInvoicePayment(inv, parsedAmt, payMethod) {
                val remaining = (inv.balanceDue - parsedAmt).coerceAtLeast(0.0)
                val msg =
                  WhatsAppShareHelper.formatPaymentReceiptMessage(
                    payerName = inv.partyName,
                    referenceLabel = "Invoice ${inv.invoiceNumber}",
                    amountPaid = parsedAmt,
                    remainingDue = remaining,
                    paymentMethod = payMethod,
                    businessName = businessName,
                    currency = currency,
                  )
                recordedReceipt = RecordedPaymentReceipt(inv.partyName, targetPhone, msg)
              }
            } else {
              val pty = selectedParty
              if (pty == null) {
                validationError = "No customer selected"
                return@Button
              }
              validationError = null
              onRecordPartyPayment(pty, parsedAmt) {
                val remaining = (pty.outstandingBalance - parsedAmt).coerceAtLeast(0.0)
                val msg =
                  WhatsAppShareHelper.formatPaymentReceiptMessage(
                    payerName = pty.name,
                    referenceLabel = "Customer Khata Payment",
                    amountPaid = parsedAmt,
                    remainingDue = remaining,
                    paymentMethod = "KHATA",
                    businessName = businessName,
                    currency = currency,
                  )
                recordedReceipt = RecordedPaymentReceipt(pty.name, pty.phone, msg)
              }
            }
          },
          enabled = !isBusy && ((mode == "invoice" && selectedInvoice != null) || (mode == "party" && selectedParty != null)),
          modifier = Modifier.testTag("confirm_receive_payment_button"),
        ) {
          Text(if (isBusy) "Saving…" else "Save Payment")
        }
      }
    },
    dismissButton = {
      if (recordedReceipt == null) {
        TextButton(onClick = onDismiss, enabled = !isBusy) {
          Text("Cancel")
        }
      }
    },
  )
}
