package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiChatMessage
import com.example.ai.AiChatUiState
import com.example.ai.AiExtractedBill
import com.example.ai.AiParsedDraft
import com.example.ai.BookAiViewModel
import com.example.data.model.BusinessProfile
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.util.WhatsAppShareHelper

enum class AiCopilotMode(val label: String) {
  CHAT("AI Analyst"),
  VOICE("Voice Entry"),
  OCR("Bill Scan"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCopilotScreenContent(
  aiViewModel: BookAiViewModel,
  initialMode: AiCopilotMode,
  profile: BusinessProfile?,
  currency: String,
  isPlainNonGst: Boolean,
  parties: List<ErpParty>,
  inventory: List<InventoryItem>,
  invoices: List<GstInvoice>,
  expenses: List<ErpExpense>,
  entries: List<DashboardEntry>,
  isBusy: Boolean,
  canCreateInvoices: Boolean,
  canManageExpenses: Boolean,
  onSaveVoiceDraft: (AiParsedDraft) -> Unit,
  onSaveOcrExpense: (AiExtractedBill) -> Unit,
  onSaveOcrInvoice: (AiExtractedBill) -> Unit,
) {
  val context = LocalContext.current
  var selectedMode by rememberSaveable(initialMode) { mutableStateOf(initialMode) }

  val chatMessages by aiViewModel.chatMessages.collectAsStateWithLifecycle()
  val chatState by aiViewModel.chatUiState.collectAsStateWithLifecycle()
  val voiceState by aiViewModel.voiceUiState.collectAsStateWithLifecycle()
  val ocrState by aiViewModel.ocrUiState.collectAsStateWithLifecycle()
  val scannedBitmap by aiViewModel.scannedBitmap.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Live ERP snapshot header pill
    Surface(
      color = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
          Text(
            text = "Book AI • ${invoices.size} Bills • ${parties.size} Parties • ${inventory.size} SKUs",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (selectedMode == AiCopilotMode.CHAT) {
          TextButton(
            onClick = { aiViewModel.clearChat() },
            modifier = Modifier.testTag("ai_chat_reset_btn"),
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Reset chat", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Reset")
          }
        }
      }
    }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      AiCopilotMode.entries.forEachIndexed { index, mode ->
        SegmentedButton(
          selected = selectedMode == mode,
          onClick = { selectedMode = mode },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = AiCopilotMode.entries.size),
          icon = {
            val icon =
              when (mode) {
                AiCopilotMode.CHAT -> Icons.Filled.AutoAwesome
                AiCopilotMode.VOICE -> Icons.Filled.Mic
                AiCopilotMode.OCR -> Icons.Filled.DocumentScanner
              }
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          modifier = Modifier.testTag("ai_mode_${mode.name.lowercase()}"),
        ) {
          Text(mode.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }

    when (selectedMode) {
      AiCopilotMode.CHAT -> {
        AiAnalystChatPane(
          messages = chatMessages,
          chatState = chatState,
          onSendQuestion = { q ->
            aiViewModel.sendChatQuestion(
              question = q,
              profile = profile,
              currency = currency,
              isPlainNonGst = isPlainNonGst,
              parties = parties,
              inventory = inventory,
              invoices = invoices,
              expenses = expenses,
              entries = entries,
            )
          },
          onShareAnswerWhatsApp = { answerText ->
            WhatsAppShareHelper.shareTextToWhatsApp(
              context = context,
              text = "*Book AI Business Analysis*\n\n$answerText\n\n_Shared via Book_",
              chooserTitle = "Share AI Analysis on WhatsApp",
            )
          },
          modifier = Modifier.weight(1f),
        )
      }

      AiCopilotMode.VOICE -> {
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentPadding = PaddingValues(bottom = 24.dp),
        ) {
          item {
            VoiceToInvoiceAndExpenseSection(
              voiceState = voiceState,
              currency = currency,
              isPlainNonGst = isPlainNonGst,
              isBusy = isBusy,
              canCreateInvoices = canCreateInvoices,
              canManageExpenses = canManageExpenses,
              onStartRecording = { aiViewModel.startVoiceRecording(context) },
              onStopAndAnalyze = {
                aiViewModel.stopAndAnalyzeVoiceRecording(inventory, parties, isPlainNonGst)
              },
              onCancelRecording = { aiViewModel.cancelVoiceRecording() },
              onAnalyzeTextCommand = { cmd ->
                aiViewModel.analyzeTextVoiceCommand(cmd, inventory, parties, isPlainNonGst)
              },
              onResetVoice = { aiViewModel.resetVoiceState() },
              onSaveDraft = onSaveVoiceDraft,
            )
          }
        }
      }

      AiCopilotMode.OCR -> {
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentPadding = PaddingValues(bottom = 24.dp),
        ) {
          item {
            BillPhotoOcrSection(
              ocrState = ocrState,
              scannedBitmap = scannedBitmap,
              currency = currency,
              isPlainNonGst = isPlainNonGst,
              isBusy = isBusy,
              canCreateInvoices = canCreateInvoices,
              canManageExpenses = canManageExpenses,
              onScanImageUri = { uri ->
                aiViewModel.scanBillPhotoUri(context, uri, inventory, parties, isPlainNonGst)
              },
              onResetOcr = { aiViewModel.resetOcrState() },
              onSaveBillAsExpense = onSaveOcrExpense,
              onSaveBillAsInvoice = onSaveOcrInvoice,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AiAnalystChatPane(
  messages: List<AiChatMessage>,
  chatState: AiChatUiState,
  onSendQuestion: (String) -> Unit,
  onShareAnswerWhatsApp: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var inputText by rememberSaveable { mutableStateOf("") }
  val listState = rememberLazyListState()

  LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      val prompts =
        listOf(
          "Sales & Profit/Loss % breakdown batao",
          "Kis party me kitna profit % aur balance due hai?",
          "Low stock items aur fast-moving SKUs dikhao",
          "GST Output vs ITC aur expenses summary do",
        )
      items(prompts) { prompt ->
        AssistChip(
          onClick = { onSendQuestion(prompt) },
          enabled = chatState !is AiChatUiState.Streaming,
          label = { Text(prompt, maxLines = 1) },
          modifier = Modifier.testTag("ai_quick_prompt_chip"),
        )
      }
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      contentPadding = PaddingValues(vertical = 4.dp),
    ) {
      items(messages, key = { it.id }) { msg ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
        ) {
          Card(
            shape =
              RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (msg.isUser) 16.dp else 4.dp,
                bottomEnd = if (msg.isUser) 4.dp else 16.dp,
              ),
            colors =
              CardDefaults.cardColors(
                containerColor =
                  when {
                    msg.isError -> MaterialTheme.colorScheme.errorContainer
                    msg.isUser -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surface
                  },
                contentColor =
                  when {
                    msg.isError -> MaterialTheme.colorScheme.onErrorContainer
                    msg.isUser -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                  },
              ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (msg.isUser) 0.dp else 2.dp),
            modifier = Modifier.widthIn(max = 560.dp),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              if (msg.text.isBlank() && msg.isStreaming) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                  Text("Analyzing Book ledger & P&L...", style = MaterialTheme.typography.bodySmall)
                }
              } else {
                Text(text = msg.text, style = MaterialTheme.typography.bodyMedium)
              }

              if (!msg.isUser && !msg.isStreaming && !msg.isError && msg.text.isNotBlank()) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End,
                ) {
                  IconButton(
                    onClick = { onShareAnswerWhatsApp(msg.text) },
                    modifier = Modifier.size(36.dp).testTag("ai_msg_share_wa_btn"),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Share,
                      contentDescription = "Share AI reply on WhatsApp",
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = inputText,
        onValueChange = { inputText = it },
        placeholder = { Text("Ask Book AI about sales, party P&L, stock...") },
        modifier = Modifier.weight(1f).testTag("ai_chat_input"),
        maxLines = 3,
      )
      FilledIconButton(
        onClick = {
          val q = inputText.trim()
          if (q.isNotBlank()) {
            inputText = ""
            onSendQuestion(q)
          }
        },
        enabled = inputText.isNotBlank() && chatState !is AiChatUiState.Streaming,
        modifier = Modifier.size(52.dp).testTag("ai_chat_send_btn"),
      ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send AI Question")
      }
    }
  }
}
