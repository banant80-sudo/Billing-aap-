package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.sync.FinbossBackupPayload
import com.example.ui.viewmodel.WorkspaceSyncState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncStatusPill(
  syncState: WorkspaceSyncState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (containerColor, contentColor, icon, statusLabel) =
    when {
      !syncState.isOnline && syncState.hasPendingWrites ->
        Quad(
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
          Icons.Filled.CloudOff,
          stringResource(R.string.sync_status_offline_pending, syncState.pendingCollectionsCount),
        )
      !syncState.isOnline ->
        Quad(
          MaterialTheme.colorScheme.surfaceVariant,
          MaterialTheme.colorScheme.onSurfaceVariant,
          Icons.Filled.CloudOff,
          stringResource(R.string.sync_status_offline_cached),
        )
      syncState.isSyncingNow || syncState.hasPendingWrites ->
        Quad(
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.onPrimaryContainer,
          Icons.Filled.CloudSync,
          stringResource(R.string.sync_status_syncing),
        )
      else ->
        Quad(
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
          Icons.Filled.CloudDone,
          stringResource(R.string.sync_status_online_synced),
        )
    }

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(50),
    color = containerColor,
    contentColor = contentColor,
    border = BorderStroke(0.5.dp, contentColor.copy(alpha = 0.2f)),
    modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sync_status_pill"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f),
      ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
          text = statusLabel,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = "Backup & Sync",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}

@Composable
fun BackupSyncCard(
  syncState: WorkspaceSyncState,
  isBusy: Boolean,
  onSyncNow: () -> Unit,
  onExportBackup: () -> Unit,
  onRestoreBackup: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
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
            imageVector = Icons.Filled.Backup,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = stringResource(R.string.backup_section_title),
            style = MaterialTheme.typography.titleMedium,
          )
        }
        Surface(
          color =
            if (syncState.isOnline) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.tertiaryContainer,
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = if (syncState.isOnline) "ONLINE" else "OFFLINE",
            style = MaterialTheme.typography.labelSmall,
            color =
              if (syncState.isOnline) MaterialTheme.colorScheme.onSecondaryContainer
              else MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      Text(
        text =
          if (syncState.isOnline) {
            "Offline disk cache (100 MB) is active. New bills, stock, parties & expenses work offline and auto-sync to cloud."
          } else {
            "No internet connection. All edits are safely saved on device and will sync automatically when you reconnect."
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      SyncMetaRow(
        label = "Offline Queue",
        value =
          if (syncState.hasPendingWrites) "${syncState.pendingCollectionsCount} collection(s) pending"
          else "All changes synced",
      )
      SyncMetaRow(
        label = "Last Cloud Sync",
        value = formatSyncTimestamp(syncState.lastCloudSyncMs, fallback = "Waiting for sync"),
      )
      SyncMetaRow(
        label = "Last Local JSON Backup",
        value = formatSyncTimestamp(syncState.lastLocalBackupMs, fallback = "Not exported yet"),
      )

      Button(
        onClick = onSyncNow,
        enabled = !syncState.isSyncingNow && !isBusy,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sync_now_button"),
      ) {
        if (syncState.isSyncingNow) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Syncing…")
        } else {
          Icon(imageVector = Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.action_sync_now))
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onExportBackup,
          enabled = !isBusy,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("export_backup_button"),
        ) {
          Icon(imageVector = Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Export .JSON", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
          onClick = onRestoreBackup,
          enabled = !isBusy,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("restore_backup_button"),
        ) {
          Icon(imageVector = Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Restore .JSON", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSyncModalSheet(
  syncState: WorkspaceSyncState,
  isBusy: Boolean,
  onSyncNow: () -> Unit,
  onExportBackup: () -> Unit,
  onRestoreBackup: () -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      BackupSyncCard(
        syncState = syncState,
        isBusy = isBusy,
        onSyncNow = onSyncNow,
        onExportBackup = onExportBackup,
        onRestoreBackup = onRestoreBackup,
      )
    }
  }
}

@Composable
fun RestoreBackupConfirmDialog(
  payload: FinbossBackupPayload,
  isBusy: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = { if (!isBusy) onDismiss() },
    icon = { Icon(imageVector = Icons.Filled.Restore, contentDescription = null) },
    title = { Text("Restore Offline Backup?") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text =
            "Exported on ${formatSyncTimestamp(payload.exportedAtMs, "Unknown date")}. Missing records will be restored safely without overwriting existing entries:",
          style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("• Parties & Khata: ${payload.parties.size}", style = MaterialTheme.typography.bodyMedium)
        Text("• Inventory SKUs: ${payload.inventory.size}", style = MaterialTheme.typography.bodyMedium)
        Text("• GST Invoices: ${payload.invoices.size}", style = MaterialTheme.typography.bodyMedium)
        Text("• Expenses: ${payload.expenses.size}", style = MaterialTheme.typography.bodyMedium)
        Text(
          "• Quick Cashbook Entries: ${payload.dashboardEntries.size}",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        enabled = !isBusy,
        modifier = Modifier.testTag("confirm_restore_backup_button"),
      ) {
        Text(if (isBusy) "Restoring…" else "Restore Missing Data")
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
private fun SyncMetaRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

private fun formatSyncTimestamp(timeMs: Long, fallback: String): String {
  if (timeMs <= 0L) return fallback
  val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
  return formatter.format(Date(timeMs))
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
