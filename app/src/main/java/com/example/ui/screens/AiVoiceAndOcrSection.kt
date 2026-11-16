package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ai.AiExtractedBill
import com.example.ai.AiParsedDraft
import com.example.ai.BillOcrUiState
import com.example.ai.BookAiJsonParser
import com.example.ai.VoiceCommandUiState
import com.example.data.model.ErpParty
import com.example.data.model.InventoryItem
import com.example.ui.theme.MonoMetricMedium
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceToInvoiceAndExpenseSection(
  voiceState: VoiceCommandUiState,
  currency: String,
  isPlainNonGst: Boolean,
  isBusy: Boolean,
  canCreateInvoices: Boolean,
  canManageExpenses: Boolean,
  onStartRecording: () -> Unit,
  onStopAndAnalyze: () -> Unit,
  onCancelRecording: () -> Unit,
  onAnalyzeTextCommand: (String) -> Unit,
  onResetVoice: () -> Unit,
  onSaveDraft: (AiParsedDraft) -> Unit,
) {
  val context = LocalContext.current
  var typedCommand by rememberSaveable { mutableStateOf("") }
  var micPermissionDenied by rememberSaveable { mutableStateOf(false) }

  val audioPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        micPermissionDenied = false
        onStartRecording()
      } else {
        micPermissionDenied = true
      }
    }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Card(
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Voice-to-Invoice & Expense",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Speak in English, Hindi, or Hinglish to auto-fill a GST Bill or Expense entry using Book AI.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (micPermissionDenied) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(
              text = "Microphone permission was denied. Grant RECORD_AUDIO in Android Settings or type your voice command below.",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        when (voiceState) {
          is VoiceCommandUiState.Recording -> {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Box(
                    modifier =
                      Modifier.size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                  )
                  Text(
                    text = "Listening... ${voiceState.elapsedSeconds}s",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                  )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  TextButton(
                    onClick = onCancelRecording,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("voice_cancel_btn"),
                  ) {
                    Text("Cancel")
                  }
                  Button(
                    onClick = onStopAndAnalyze,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("voice_stop_analyze_btn"),
                  ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop & Parse")
                  }
                }
              }
            }
          }

          is VoiceCommandUiState.Analyzing -> {
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
              Text(
                text = "Analyzing audio with Book AI...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
              )
            }
          }

          else -> {
            Button(
              onClick = {
                val granted =
                  ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) onStartRecording()
                else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              },
              modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("voice_start_mic_btn"),
            ) {
              Icon(Icons.Filled.Mic, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text("Tap & Speak Invoice or Expense")
            }
          }
        }

        HorizontalDivider()

        OutlinedTextField(
          value = typedCommand,
          onValueChange = { typedCommand = it },
          label = { Text("Or type / dictate command (Hinglish / English)") },
          placeholder = { Text("e.g. Sharma Traders ko 2 Copper Cable 1250 rate UPI bill banao") },
          modifier = Modifier.fillMaxWidth().testTag("voice_text_input"),
          minLines = 2,
          maxLines = 3,
        )

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf(
            "Bill Sharma Traders 2 Laptop Stand @ 850 UPI",
            "Add expense Office Diesel 1500 cash",
            "Create credit bill Mehta Mart 5 Carton Box @ 420 GST 18%",
          ).forEachIndexed { idx, sample ->
            AssistChip(
              onClick = { typedCommand = sample },
              label = { Text(sample, maxLines = 1) },
              modifier = Modifier.testTag("voice_sample_chip_$idx"),
            )
          }
        }

        Button(
          onClick = { onAnalyzeTextCommand(typedCommand) },
          enabled = typedCommand.isNotBlank() && voiceState !is VoiceCommandUiState.Analyzing,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("voice_parse_text_btn"),
        ) {
          Text("Parse Command into Draft")
        }
      }
    }

    if (voiceState is VoiceCommandUiState.Error) {
      AiErrorBanner(message = voiceState.message, onDismiss = onResetVoice)
    }

    if (voiceState is VoiceCommandUiState.Ready) {
      val draft = voiceState.draft
      val canSave = if (draft.isInvoice) canCreateInvoices else canManageExpenses
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("voice_draft_review_card"),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (draft.isInvoice) "AI Draft: Sale Invoice" else "AI Draft: Expense Entry",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onResetVoice, modifier = Modifier.testTag("voice_draft_clear_btn")) {
              Text("Discard")
            }
          }

          if (draft.transcript.isNotBlank()) {
            Text(
              text = "\"${draft.transcript}\"",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }

          if (draft.isInvoice) {
            Text(
              text = "Customer: ${draft.partyName}${if (draft.matchedPartyId.isNotBlank()) " (Matched Khata)" else " (Walk-in)"}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "Payment: ${draft.paymentMethod.uppercase()} • Status: ${draft.paymentStatus.uppercase()}",
              style = MaterialTheme.typography.bodySmall,
            )
            draft.lineItems.forEach { item ->
              Text(
                text = "• ${item.name}: ${BookAiJsonParser.formatQtyShort(item.qty)} ${item.unit} × ${formatErpMoney(currency, item.unitPrice)}${if (!isPlainNonGst) " (+${item.gstRatePercent.toInt()}% GST)" else ""}",
                style = MaterialTheme.typography.bodySmall,
              )
            }
            Text(
              text = "Estimated Total: ${formatErpMoney(currency, draft.computedInvoiceGrandTotal(isPlainNonGst))}",
              style = MonoMetricMedium,
              fontWeight = FontWeight.Bold,
            )
          } else {
            Text(
              text = "Title: ${draft.expenseTitle}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "Category: ${draft.expenseCategory.uppercase()} • Mode: ${draft.paymentMethod.uppercase()}",
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              text = "Amount: ${formatErpMoney(currency, draft.expenseAmount)}${if (!isPlainNonGst && draft.gstInputAmount > 0) " (ITC ${formatErpMoney(currency, draft.gstInputAmount)})" else ""}",
              style = MonoMetricMedium,
              fontWeight = FontWeight.Bold,
            )
          }

          if (!canSave) {
            Text(
              text = "Your salesman role does not have permission to save this record type.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          } else {
            Button(
              onClick = { onSaveDraft(draft) },
              enabled = !isBusy && (!draft.isInvoice || draft.lineItems.isNotEmpty()) && (draft.isInvoice || draft.expenseAmount > 0.0),
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("voice_confirm_save_btn"),
            ) {
              Icon(Icons.Filled.CheckCircle, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(if (draft.isInvoice) "Confirm & Create Invoice" else "Confirm & Save Expense")
            }
          }
        }
      }
    }
  }
}

@Composable
fun BillPhotoOcrSection(
  ocrState: BillOcrUiState,
  scannedBitmap: Bitmap?,
  currency: String,
  isPlainNonGst: Boolean,
  isBusy: Boolean,
  canCreateInvoices: Boolean,
  canManageExpenses: Boolean,
  onScanImageUri: (Uri) -> Unit,
  onResetOcr: () -> Unit,
  onSaveBillAsExpense: (AiExtractedBill) -> Unit,
  onSaveBillAsInvoice: (AiExtractedBill) -> Unit,
) {
  val context = LocalContext.current
  var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

  // Zero-permission Photo Picker (compliant with Google Play policy)
  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        onScanImageUri(uri)
      }
    }

  val cameraCaptureLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
      val uri = pendingCameraUri
      if (saved && uri != null) {
        onScanImageUri(uri)
      }
    }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Card(
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.DocumentScanner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Column {
            Text(
              text = "Bill Photo OCR Scanner",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Snap or pick any supplier bill, purchase receipt, or invoice photo to extract totals, GSTIN & items.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = {
              val uri = createTempBillImageUri(context)
              pendingCameraUri = uri
              cameraCaptureLauncher.launch(uri)
            },
            enabled = ocrState !is BillOcrUiState.Scanning,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ocr_camera_btn"),
          ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Camera")
          }

          OutlinedButton(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            enabled = ocrState !is BillOcrUiState.Scanning,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ocr_gallery_btn"),
          ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Pick Photo")
          }
        }

        if (scannedBitmap != null) {
          Image(
            bitmap = scannedBitmap.asImageBitmap(),
            contentDescription = "Scanned bill preview",
            modifier =
              Modifier.fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
          )
        }

        if (ocrState is BillOcrUiState.Scanning) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            Text("Scanning bill lines & GST values with Book AI...", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    }

    if (ocrState is BillOcrUiState.Error) {
      AiErrorBanner(message = ocrState.message, onDismiss = onResetOcr)
    }

    if (ocrState is BillOcrUiState.Scanned) {
      val bill = ocrState.bill
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("ocr_result_card"),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Extracted Bill Details",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onResetOcr, modifier = Modifier.testTag("ocr_clear_btn")) {
              Text("Clear")
            }
          }

          Text("Party/Vendor: ${bill.partyName}", fontWeight = FontWeight.SemiBold)
          if (bill.partyGstin.isNotBlank()) {
            Text("GSTIN: ${bill.partyGstin}", style = MaterialTheme.typography.bodySmall)
          }
          if (bill.billOrInvoiceNumber.isNotBlank() || bill.billDate.isNotBlank()) {
            Text(
              text = listOfNotNull(
                bill.billOrInvoiceNumber.takeIf { it.isNotBlank() }?.let { "Bill #$it" },
                bill.billDate.takeIf { it.isNotBlank() },
              ).joinToString(" • "),
              style = MaterialTheme.typography.bodySmall,
            )
          }

          if (bill.lineItems.isNotEmpty()) {
            HorizontalDivider()
            bill.lineItems.forEach { item ->
              Text(
                text = "• ${item.name} (${BookAiJsonParser.formatQtyShort(item.qty)} ${item.unit} × ${formatErpMoney(currency, item.unitPrice)})",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }

          HorizontalDivider()
          Text(
            text = "Subtotal: ${formatErpMoney(currency, bill.subtotal)} • GST: ${formatErpMoney(currency, if (isPlainNonGst) 0.0 else bill.gstAmount)}",
            style = MaterialTheme.typography.bodySmall,
          )
          Text(
            text = "Grand Total: ${formatErpMoney(currency, bill.grandTotal)}",
            style = MonoMetricMedium,
            fontWeight = FontWeight.Bold,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (canManageExpenses) {
              Button(
                onClick = { onSaveBillAsExpense(bill) },
                enabled = !isBusy && bill.grandTotal > 0.0,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ocr_save_expense_btn"),
              ) {
                Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Expense", maxLines = 1)
              }
            }
            if (canCreateInvoices && bill.lineItems.isNotEmpty()) {
              OutlinedButton(
                onClick = { onSaveBillAsInvoice(bill) },
                enabled = !isBusy,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ocr_save_invoice_btn"),
              ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Invoice", maxLines = 1)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun AiErrorBanner(message: String, onDismiss: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("ai_error_banner"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(Icons.Filled.ErrorOutline, contentDescription = "Error")
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onDismiss) {
        Icon(Icons.Filled.Close, contentDescription = "Dismiss")
      }
    }
  }
}

private fun createTempBillImageUri(context: Context): Uri {
  val dir = File(context.cacheDir, "shared").apply { mkdirs() }
  val file = File(dir, "ocr_bill_${System.currentTimeMillis()}.jpg")
  return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
