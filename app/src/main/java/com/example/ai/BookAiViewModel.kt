package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BusinessProfile
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "BookAiViewModel"
private const val AI_TIMEOUT_MS = 60_000L
private const val MAX_INLINE_MEDIA_BYTES = 18 * 1024 * 1024 // < 20MB payload safety cap

class BookAiViewModel : ViewModel() {

  private val chatModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      systemInstruction =
        content {
          text(
            "You are Finboss AI, a sharp Indian Chartered Accountant & Business Operations Analyst inside the Finboss Billing No.1 ERP app. " +
              "Analyze the live business snapshot provided with each user query (Sales, GST Output/ITC, Party Ledger balances & Profit/Loss %, Inventory stock levels & item margins, Cashbook/Expenses). " +
              "Respond concisely in clear bullet points with exact INR amounts and percentages. " +
              "If the user writes in Hinglish or Hindi, match their language naturally while keeping financial terms clear. " +
              "Never invent numbers not present in the snapshot; if data is empty or zero, explain what is missing."
          )
        },
    )
  }

  private val voiceSchema by lazy {
    Schema.obj(
      properties =
        mapOf(
          "entryType" to Schema.enumeration(
            values = listOf("INVOICE", "EXPENSE"),
            description = "Whether the command creates a customer sale INVOICE/bill or an operating EXPENSE.",
          ),
          "transcript" to Schema.string(description = "Clean transcription/interpretation of the user voice or text command."),
          "partyName" to Schema.string(description = "Customer or vendor name mentioned, empty string if none."),
          "partyGstin" to Schema.string(description = "Optional GSTIN if spoken."),
          "paymentMethod" to Schema.enumeration(
            values = listOf("upi", "cash", "card", "credit"),
            description = "Payment mode; use credit for udhar/unpaid.",
          ),
          "paymentStatus" to Schema.enumeration(
            values = listOf("paid", "partial", "unpaid"),
            description = "Settlement status.",
          ),
          "paidAmount" to Schema.double(description = "Paid amount if partial/specific, otherwise -1."),
          "discountAmount" to Schema.double(description = "Flat discount amount on invoice if mentioned, else 0."),
          "notes" to Schema.string(description = "Any extra note or reference."),
          "lineItems" to Schema.array(
            items =
              Schema.obj(
                properties =
                  mapOf(
                    "itemName" to Schema.string(description = "Product or service name."),
                    "qty" to Schema.double(description = "Numeric quantity, default 1."),
                    "unit" to Schema.string(description = "Unit e.g. PCS, KG, BOX."),
                    "unitPrice" to Schema.double(description = "Rate per unit before GST; 0 if unknown."),
                    "gstRatePercent" to Schema.double(description = "GST slab 0, 5, 12, 18, or 28; -1 if unknown."),
                    "hsnCode" to Schema.string(description = "HSN code if spoken, else empty."),
                  ),
                optionalProperties = listOf("unit", "hsnCode"),
              ),
            description = "Line items for INVOICE entries; empty list for EXPENSE.",
          ),
          "expenseTitle" to Schema.string(description = "Expense description for EXPENSE entries."),
          "expenseCategory" to Schema.enumeration(
            values = listOf("rent", "payroll", "utilities", "purchases", "transport", "marketing", "tax", "other"),
            description = "Category for EXPENSE entries.",
          ),
          "expenseAmount" to Schema.double(description = "Total amount for EXPENSE entries."),
          "gstInputAmount" to Schema.double(description = "GST ITC portion if mentioned on expense, else 0."),
        ),
      optionalProperties = listOf("partyGstin", "notes", "paidAmount", "discountAmount", "gstInputAmount"),
    )
  }

  private val voiceModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = voiceSchema
        },
    )
  }

  private val billOcrSchema by lazy {
    Schema.obj(
      properties =
        mapOf(
          "documentKind" to Schema.enumeration(
            values = listOf("PURCHASE_EXPENSE_BILL", "SALE_INVOICE"),
            description = "PURCHASE_EXPENSE_BILL for vendor receipts/supplier bills; SALE_INVOICE for customer sales bills.",
          ),
          "vendorOrPartyName" to Schema.string(description = "Supplier, store, vendor, or billed party name."),
          "vendorGstin" to Schema.string(description = "15-char GSTIN if printed on bill, else empty string."),
          "invoiceOrBillNumber" to Schema.string(description = "Invoice or bill/receipt number."),
          "invoiceDate" to Schema.string(description = "Date printed on the bill."),
          "paymentMethod" to Schema.enumeration(
            values = listOf("upi", "cash", "card", "credit"),
            description = "Inferred payment mode, default upi.",
          ),
          "expenseCategory" to Schema.enumeration(
            values = listOf("purchases", "rent", "payroll", "utilities", "transport", "marketing", "tax", "other"),
            description = "Most relevant Finboss ERP expense category.",
          ),
          "subtotal" to Schema.double(description = "Taxable subtotal before GST/tax."),
          "gstAmount" to Schema.double(description = "Total GST/CGST+SGST/IGST tax amount on bill."),
          "grandTotal" to Schema.double(description = "Final payable/total bill amount."),
          "notes" to Schema.string(description = "Concise summary of items or bill remarks."),
          "lineItems" to Schema.array(
            items =
              Schema.obj(
                properties =
                  mapOf(
                    "itemName" to Schema.string(description = "Line item name/description."),
                    "hsnCode" to Schema.string(description = "HSN/SAC code if shown."),
                    "qty" to Schema.double(description = "Quantity."),
                    "unit" to Schema.string(description = "Unit e.g. PCS, KG, BOX."),
                    "unitPrice" to Schema.double(description = "Unit rate before tax."),
                    "gstRatePercent" to Schema.double(description = "GST percent e.g. 0, 5, 12, 18, 28."),
                  ),
                optionalProperties = listOf("hsnCode", "unit"),
              ),
            description = "Extracted line items from the bill photo.",
          ),
        ),
      optionalProperties = listOf("vendorGstin", "invoiceOrBillNumber", "invoiceDate", "notes"),
    )
  }

  private val billOcrModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = billOcrSchema
        },
    )
  }

  private val _chatMessages =
    MutableStateFlow(
      listOf(
        AiChatMessage(
          id = "welcome",
          isUser = false,
          text =
            "Namaste! Main Finboss Billing No.1 ERP AI Analyst hoon. Aap mujhse Sales, Party Ledger Profit/Loss %, Stock alerts, ya GST ITC ke baare me koi bhi sawaal pooch sakte hain.",
        )
      )
    )
  val chatMessages: StateFlow<List<AiChatMessage>> = _chatMessages.asStateFlow()

  private val _chatUiState = MutableStateFlow<AiChatUiState>(AiChatUiState.Idle)
  val chatUiState: StateFlow<AiChatUiState> = _chatUiState.asStateFlow()

  private val _voiceUiState = MutableStateFlow<VoiceCommandUiState>(VoiceCommandUiState.Idle)
  val voiceUiState: StateFlow<VoiceCommandUiState> = _voiceUiState.asStateFlow()

  private val _ocrUiState = MutableStateFlow<BillOcrUiState>(BillOcrUiState.Idle)
  val ocrUiState: StateFlow<BillOcrUiState> = _ocrUiState.asStateFlow()

  private val _scannedBitmap = MutableStateFlow<Bitmap?>(null)
  val scannedBitmap: StateFlow<Bitmap?> = _scannedBitmap.asStateFlow()

  private var mediaRecorder: MediaRecorder? = null
  private var activeAudioFile: File? = null
  private var recordingTickerJob: Job? = null

  fun clearChat() {
    _chatUiState.value = AiChatUiState.Idle
    _chatMessages.value =
      listOf(
        AiChatMessage(
          id = "welcome_${System.currentTimeMillis()}",
          isUser = false,
          text =
            "Chat reset! Ask me anything about your Sales, Party Profit/Loss %, Low Stock, or GST summary.",
        )
      )
  }

  fun sendChatQuestion(
    question: String,
    profile: BusinessProfile?,
    currency: String,
    isPlainNonGst: Boolean,
    parties: List<ErpParty>,
    inventory: List<InventoryItem>,
    invoices: List<GstInvoice>,
    expenses: List<ErpExpense>,
    entries: List<DashboardEntry>,
  ) {
    val cleanQ = question.trim()
    if (cleanQ.isBlank() || _chatUiState.value is AiChatUiState.Streaming) return

    val userMsgId = UUID.randomUUID().toString()
    val modelMsgId = UUID.randomUUID().toString()
    val priorHistory =
      _chatMessages.value
        .filter { !it.isError && it.id != "welcome" && !it.id.startsWith("welcome_") && it.text.isNotBlank() }
        .takeLast(8)
        .map { msg -> content(if (msg.isUser) "user" else "model") { text(msg.text) } }

    _chatMessages.value =
      _chatMessages.value +
        AiChatMessage(id = userMsgId, isUser = true, text = cleanQ) +
        AiChatMessage(id = modelMsgId, isUser = false, text = "", isStreaming = true)

    viewModelScope.launch {
      _chatUiState.value = AiChatUiState.Streaming
      val accumulated = StringBuilder()
      try {
        val snapshot =
          withContext(Dispatchers.Default) {
            BookAiContextBuilder.buildAnalyticsContextPrompt(
              profile = profile,
              currency = currency,
              isPlainNonGst = isPlainNonGst,
              parties = parties,
              inventory = inventory,
              invoices = invoices,
              expenses = expenses,
              entries = entries,
            )
          }

        val chatSession = chatModel.startChat(history = priorHistory)
        val groundedPrompt = "$snapshot\n\nUSER QUESTION: $cleanQ"

        withTimeout(AI_TIMEOUT_MS) {
          chatSession.sendMessageStream(groundedPrompt).collect { chunk ->
            val piece = chunk.text.orEmpty()
            if (piece.isNotEmpty()) {
              accumulated.append(piece)
              updateModelMessage(modelMsgId, accumulated.toString(), isStreaming = true, isError = false)
            }
          }
        }

        val finalAnswer = accumulated.toString().trim()
        if (finalAnswer.isBlank()) {
          val msg = "AI returned an empty response. Please try rephrasing your question."
          updateModelMessage(modelMsgId, msg, isStreaming = false, isError = true)
          _chatUiState.value = AiChatUiState.Error(msg)
        } else {
          updateModelMessage(modelMsgId, finalAnswer, isStreaming = false, isError = false)
          _chatUiState.value = AiChatUiState.Idle
        }
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "AI chat timed out", e)
        val msg = "AI response timed out after 60 seconds. Check your connection and try again."
        updateModelMessage(modelMsgId, msg, isStreaming = false, isError = true)
        _chatUiState.value = AiChatUiState.Error(msg)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Firebase AI chat failed", e)
        val msg = e.localizedMessage ?: "Failed to connect to Finboss AI service."
        updateModelMessage(modelMsgId, msg, isStreaming = false, isError = true)
        _chatUiState.value = AiChatUiState.Error(msg)
      }
    }
  }

  private fun updateModelMessage(
    id: String,
    text: String,
    isStreaming: Boolean,
    isError: Boolean,
  ) {
    _chatMessages.value =
      _chatMessages.value.map { m ->
        if (m.id == id) m.copy(text = text, isStreaming = isStreaming, isError = isError) else m
      }
  }

  // --- Voice-to-Invoice & Expense ---

  fun startVoiceRecording(context: Context) {
    if (_voiceUiState.value is VoiceCommandUiState.Recording || _voiceUiState.value is VoiceCommandUiState.Analyzing) {
      return
    }
    releaseRecorder()
    try {
      val outFile = File(context.cacheDir, "voice_cmd_${System.currentTimeMillis()}.m4a")
      val recorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          MediaRecorder(context)
        } else {
          @Suppress("DEPRECATION")
          MediaRecorder()
        }
      recorder.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioSamplingRate(16_000)
        setAudioEncodingBitRate(32_000)
        setOutputFile(outFile.absolutePath)
        prepare()
        start()
      }
      mediaRecorder = recorder
      activeAudioFile = outFile
      _voiceUiState.value = VoiceCommandUiState.Recording(elapsedSeconds = 0)
      recordingTickerJob?.cancel()
      recordingTickerJob =
        viewModelScope.launch {
          var sec = 0
          while (isActive && _voiceUiState.value is VoiceCommandUiState.Recording) {
            delay(1_000L)
            sec += 1
            _voiceUiState.value = VoiceCommandUiState.Recording(elapsedSeconds = sec)
          }
        }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start voice recording", e)
      releaseRecorder()
      _voiceUiState.value =
        VoiceCommandUiState.Error(
          "Microphone could not start (${e.localizedMessage ?: "hardware unavailable"}). You can type or dictate your command below."
        )
    }
  }

  fun cancelVoiceRecording() {
    releaseRecorder()
    activeAudioFile?.delete()
    activeAudioFile = null
    _voiceUiState.value = VoiceCommandUiState.Idle
  }

  fun stopAndAnalyzeVoiceRecording(
    inventory: List<InventoryItem>,
    parties: List<ErpParty>,
    isPlainNonGst: Boolean,
  ) {
    val file = activeAudioFile
    stopRecorderSafely()
    if (file == null || !file.exists() || file.length() <= 0L) {
      _voiceUiState.value = VoiceCommandUiState.Error("No audio captured. Hold or tap Speak and try again.")
      return
    }

    viewModelScope.launch {
      _voiceUiState.value = VoiceCommandUiState.Analyzing
      try {
        val audioBytes = withContext(Dispatchers.IO) { file.readBytes() }
        withContext(Dispatchers.IO) { file.delete() }
        activeAudioFile = null

        if (audioBytes.size > MAX_INLINE_MEDIA_BYTES) {
          _voiceUiState.value = VoiceCommandUiState.Error("Recording exceeds 18 MB limit. Please record a shorter command.")
          return@launch
        }

        val catalogHint = BookAiContextBuilder.buildCatalogHintPrompt(inventory, parties, isPlainNonGst)
        val promptContent =
          content {
            inlineData(audioBytes, "audio/mp4")
            text(
              "Listen carefully to this voice command in English, Hindi, or Hinglish for Finboss Billing No.1 ERP. " +
                "Determine whether the user wants to create a customer INVOICE/bill or record an EXPENSE, and extract every field into JSON.\n" +
                catalogHint
            )
          }

        val response = withTimeout(AI_TIMEOUT_MS) { voiceModel.generateContent(promptContent) }
        val jsonText = response.text ?: throw IllegalStateException("Empty JSON response from AI.")
        val draft = BookAiJsonParser.parseVoiceDraftJson(jsonText, inventory, parties, isPlainNonGst)
        _voiceUiState.value = VoiceCommandUiState.Ready(draft)
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Voice analysis timed out", e)
        _voiceUiState.value = VoiceCommandUiState.Error("Voice analysis timed out after 60 seconds.")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Voice analysis failed", e)
        _voiceUiState.value = VoiceCommandUiState.Error(e.localizedMessage ?: "Failed to analyze voice command.")
      }
    }
  }

  fun analyzeTextVoiceCommand(
    commandText: String,
    inventory: List<InventoryItem>,
    parties: List<ErpParty>,
    isPlainNonGst: Boolean,
  ) {
    val clean = commandText.trim()
    if (clean.isBlank()) {
      _voiceUiState.value = VoiceCommandUiState.Error("Please enter or speak an invoice or expense command.")
      return
    }

    viewModelScope.launch {
      _voiceUiState.value = VoiceCommandUiState.Analyzing
      try {
        val catalogHint = BookAiContextBuilder.buildCatalogHintPrompt(inventory, parties, isPlainNonGst)
        val promptContent =
          content {
            text(
              "Parse this spoken/dictated command in English, Hindi, or Hinglish for Finboss Billing No.1 ERP into a structured INVOICE or EXPENSE JSON.\n" +
                "Command: \"$clean\"\n\n$catalogHint"
            )
          }
        val response = withTimeout(AI_TIMEOUT_MS) { voiceModel.generateContent(promptContent) }
        val jsonText = response.text ?: throw IllegalStateException("Empty JSON response from AI.")
        val draft = BookAiJsonParser.parseVoiceDraftJson(jsonText, inventory, parties, isPlainNonGst)
        _voiceUiState.value = VoiceCommandUiState.Ready(draft)
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Text command parsing timed out", e)
        _voiceUiState.value = VoiceCommandUiState.Error("AI parsing timed out after 60 seconds.")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Text command parsing failed", e)
        _voiceUiState.value = VoiceCommandUiState.Error(e.localizedMessage ?: "Failed to parse command.")
      }
    }
  }

  fun resetVoiceState() {
    _voiceUiState.value = VoiceCommandUiState.Idle
  }

  // --- Bill Photo OCR Scanner ---

  fun scanBillPhotoUri(
    context: Context,
    imageUri: Uri,
    inventory: List<InventoryItem>,
    parties: List<ErpParty>,
    isPlainNonGst: Boolean,
  ) {
    viewModelScope.launch {
      _ocrUiState.value = BillOcrUiState.Scanning
      try {
        val bitmap =
          withContext(Dispatchers.IO) {
            decodeAndScaleBitmap(context, imageUri, maxDimension = 1600)
          } ?: throw IllegalStateException("Could not decode the selected image.")
        _scannedBitmap.value = bitmap

        val catalogHint = BookAiContextBuilder.buildCatalogHintPrompt(inventory, parties, isPlainNonGst)
        val promptContent =
          content {
            image(bitmap)
            text(
              "Perform high-accuracy OCR on this printed or handwritten bill, receipt, or tax invoice photo for Finboss Billing No.1 ERP. " +
                "Extract vendor/party name, GSTIN, bill number, date, taxable subtotal, GST tax amount, grand total, and all line items into JSON.\n" +
                catalogHint
            )
          }

        val response = withTimeout(AI_TIMEOUT_MS) { billOcrModel.generateContent(promptContent) }
        val jsonText = response.text ?: throw IllegalStateException("Empty OCR response from AI.")
        val extracted = BookAiJsonParser.parseOcrBillJson(jsonText, inventory, parties, isPlainNonGst)
        _ocrUiState.value = BillOcrUiState.Scanned(extracted)
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Bill OCR timed out", e)
        _ocrUiState.value = BillOcrUiState.Error("Bill scan timed out after 60 seconds. Try a clearer photo.")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Bill OCR failed", e)
        _ocrUiState.value = BillOcrUiState.Error(e.localizedMessage ?: "Failed to scan bill photo.")
      }
    }
  }

  fun resetOcrState() {
    _ocrUiState.value = BillOcrUiState.Idle
    _scannedBitmap.value = null
  }

  private fun stopRecorderSafely() {
    recordingTickerJob?.cancel()
    recordingTickerJob = null
    try {
      mediaRecorder?.stop()
    } catch (e: Exception) {
      Log.w(TAG, "MediaRecorder stop warning", e)
    }
    releaseRecorder()
  }

  private fun releaseRecorder() {
    recordingTickerJob?.cancel()
    recordingTickerJob = null
    try {
      mediaRecorder?.reset()
      mediaRecorder?.release()
    } catch (_: Exception) {
    } finally {
      mediaRecorder = null
    }
  }

  private fun decodeAndScaleBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxDimension * 2 || bounds.outHeight / sampleSize > maxDimension * 2) {
      sampleSize *= 2
    }

    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    var bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return null

    // Handle EXIF rotation if present
    runCatching {
      rawBytes.inputStream().use { stream ->
        val exif = ExifInterface(stream)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees =
          when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
          }
        if (degrees != 0f) {
          val matrix = Matrix().apply { postRotate(degrees) }
          val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
          if (rotated != bmp) {
            bmp.recycle()
            bmp = rotated
          }
        }
      }
    }

    // Scale strictly within maxDimension and ensure compressed size << 20MB
    val longest = maxOf(bmp.width, bmp.height)
    if (longest > maxDimension) {
      val ratio = maxDimension.toFloat() / longest.toFloat()
      val scaled =
        Bitmap.createScaledBitmap(
          bmp,
          (bmp.width * ratio).toInt().coerceAtLeast(1),
          (bmp.height * ratio).toInt().coerceAtLeast(1),
          true,
        )
      if (scaled != bmp) {
        bmp.recycle()
        bmp = scaled
      }
    }

    // Recompress to JPEG stream <= 2MB to guarantee clean payload delivery
    val baos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 82, baos)
    val compressed = baos.toByteArray()
    return if (compressed.size < MAX_INLINE_MEDIA_BYTES) {
      BitmapFactory.decodeByteArray(compressed, 0, compressed.size) ?: bmp
    } else {
      bmp
    }
  }

  override fun onCleared() {
    releaseRecorder()
    super.onCleared()
  }
}
