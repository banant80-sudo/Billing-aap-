package com.example.ai

import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.model.InvoiceLineItem
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class AiChatMessage(
  val id: String,
  val isUser: Boolean,
  val text: String,
  val isStreaming: Boolean = false,
  val isError: Boolean = false,
)

sealed interface AiChatUiState {
  data object Idle : AiChatUiState
  data object Streaming : AiChatUiState
  data class Error(val message: String) : AiChatUiState
}

data class AiDraftLineItem(
  val matchedInventoryId: String = "",
  val name: String,
  val sku: String = "",
  val hsnCode: String = "",
  val unit: String = "PCS",
  val qty: Double = 1.0,
  val unitPrice: Double = 0.0,
  val gstRatePercent: Double = 18.0,
) {
  fun toInvoiceLineItem(isPlainNonGst: Boolean): InvoiceLineItem {
    val safeQty = qty.coerceAtLeast(0.01)
    val safePrice = unitPrice.coerceAtLeast(0.0)
    val rate = if (isPlainNonGst) 0.0 else gstRatePercent.coerceIn(0.0, 28.0)
    val taxable = safeQty * safePrice
    val gst = taxable * (rate / 100.0)
    return InvoiceLineItem(
      itemId = matchedInventoryId,
      name = name,
      sku = sku,
      hsnCode = hsnCode,
      unit = unit.ifBlank { "PCS" },
      qty = safeQty,
      unitPrice = safePrice,
      gstRatePercent = rate,
      taxableAmount = taxable,
      gstAmount = gst,
      lineTotal = taxable + gst,
    )
  }
}

data class AiParsedDraft(
  val entryType: String, // "INVOICE" or "EXPENSE"
  val matchedPartyId: String = "",
  val partyName: String = "",
  val partyGstin: String = "",
  val paymentMethod: String = GstInvoice.PAYMENT_UPI,
  val paymentStatus: String = GstInvoice.STATUS_PAID,
  val paidAmount: Double = 0.0,
  val discountAmount: Double = 0.0,
  val notes: String = "",
  val transcript: String = "",
  val lineItems: List<AiDraftLineItem> = emptyList(),
  val expenseTitle: String = "",
  val expenseCategory: String = ErpExpense.CAT_OTHER,
  val expenseAmount: Double = 0.0,
  val gstInputAmount: Double = 0.0,
) {
  val isInvoice: Boolean
    get() = entryType.equals("INVOICE", ignoreCase = true)

  fun computedInvoiceGrandTotal(isPlainNonGst: Boolean): Double {
    val subtotal = lineItems.sumOf { it.qty.coerceAtLeast(0.0) * it.unitPrice.coerceAtLeast(0.0) }
    val gst =
      if (isPlainNonGst) 0.0
      else lineItems.sumOf {
        (it.qty.coerceAtLeast(0.0) * it.unitPrice.coerceAtLeast(0.0)) * (it.gstRatePercent.coerceIn(0.0, 28.0) / 100.0)
      }
    return (subtotal - discountAmount + gst).coerceAtLeast(0.0)
  }
}

sealed interface VoiceCommandUiState {
  data object Idle : VoiceCommandUiState
  data class Recording(val elapsedSeconds: Int) : VoiceCommandUiState
  data object Analyzing : VoiceCommandUiState
  data class Ready(val draft: AiParsedDraft) : VoiceCommandUiState
  data class Error(val message: String) : VoiceCommandUiState
}

data class AiExtractedBill(
  val documentKind: String, // "PURCHASE_EXPENSE_BILL" or "SALE_INVOICE"
  val matchedPartyId: String = "",
  val partyName: String = "",
  val partyGstin: String = "",
  val billOrInvoiceNumber: String = "",
  val billDate: String = "",
  val paymentMethod: String = GstInvoice.PAYMENT_UPI,
  val expenseCategory: String = ErpExpense.CAT_PURCHASES,
  val subtotal: Double = 0.0,
  val gstAmount: Double = 0.0,
  val grandTotal: Double = 0.0,
  val notes: String = "",
  val lineItems: List<AiDraftLineItem> = emptyList(),
)

sealed interface BillOcrUiState {
  data object Idle : BillOcrUiState
  data object Scanning : BillOcrUiState
  data class Scanned(val bill: AiExtractedBill) : BillOcrUiState
  data class Error(val message: String) : BillOcrUiState
}

object BookAiJsonParser {

  fun parseVoiceDraftJson(
    rawJson: String,
    inventory: List<InventoryItem>,
    parties: List<ErpParty>,
    isPlainNonGst: Boolean,
  ): AiParsedDraft {
    val json = JSONObject(rawJson)
    val entryType =
      json.optString("entryType", "INVOICE").uppercase().let {
        if (it == "EXPENSE") "EXPENSE" else "INVOICE"
      }
    val rawPartyName = json.optString("partyName", "").trim()
    val matchedParty = matchParty(rawPartyName, parties)

    val rawLines = json.optJSONArray("lineItems") ?: JSONArray()
    val parsedLines = parseLineItemsArray(rawLines, inventory, isPlainNonGst)

    val payMethod = normalizePaymentMethod(json.optString("paymentMethod", "upi"))
    val payStatus = normalizePaymentStatus(json.optString("paymentStatus", "paid"), payMethod)
    val discount = json.optDouble("discountAmount", 0.0).takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0

    val computedTotal =
      parsedLines.sumOf { line ->
        val sub = line.qty * line.unitPrice
        val tax = if (isPlainNonGst) 0.0 else sub * (line.gstRatePercent / 100.0)
        sub + tax
      }.let { (it - discount).coerceAtLeast(0.0) }

    val parsedPaid = json.optDouble("paidAmount", -1.0)
    val resolvedPaid =
      when {
        !parsedPaid.isNaN() && parsedPaid >= 0.0 -> parsedPaid
        payStatus == GstInvoice.STATUS_PAID -> computedTotal
        else -> 0.0
      }

    val expenseAmt = json.optDouble("expenseAmount", 0.0).takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0
    val gstInput =
      if (isPlainNonGst) 0.0
      else (json.optDouble("gstInputAmount", 0.0).takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0)
        .coerceAtMost(expenseAmt)

    return AiParsedDraft(
      entryType = entryType,
      matchedPartyId = matchedParty?.id.orEmpty(),
      partyName = matchedParty?.name ?: rawPartyName.ifBlank { if (entryType == "INVOICE") "Walk-in Customer" else "" },
      partyGstin = json.optString("partyGstin", "").trim().ifBlank { matchedParty?.gstin.orEmpty() },
      paymentMethod = payMethod,
      paymentStatus = payStatus,
      paidAmount = resolvedPaid,
      discountAmount = discount,
      notes = json.optString("notes", "").trim(),
      transcript = json.optString("transcript", "").trim(),
      lineItems = parsedLines,
      expenseTitle = json.optString("expenseTitle", "").trim().ifBlank { rawPartyName.ifBlank { "Voice Expense" } },
      expenseCategory = normalizeExpenseCategory(json.optString("expenseCategory", ErpExpense.CAT_OTHER)),
      expenseAmount = expenseAmt,
      gstInputAmount = gstInput,
    )
  }

  fun parseOcrBillJson(
    rawJson: String,
    inventory: List<InventoryItem>,
    parties: List<ErpParty>,
    isPlainNonGst: Boolean,
  ): AiExtractedBill {
    val json = JSONObject(rawJson)
    val docKind =
      json.optString("documentKind", "PURCHASE_EXPENSE_BILL").uppercase().let {
        if (it == "SALE_INVOICE") "SALE_INVOICE" else "PURCHASE_EXPENSE_BILL"
      }
    val rawPartyName = json.optString("vendorOrPartyName", "").trim()
    val matchedParty = matchParty(rawPartyName, parties)
    val rawLines = json.optJSONArray("lineItems") ?: JSONArray()
    val parsedLines = parseLineItemsArray(rawLines, inventory, isPlainNonGst)

    val lineSubSum = parsedLines.sumOf { it.qty * it.unitPrice }
    val lineGstSum =
      if (isPlainNonGst) 0.0
      else parsedLines.sumOf { (it.qty * it.unitPrice) * (it.gstRatePercent / 100.0) }

    val subtotal =
      json.optDouble("subtotal", -1.0).takeIf { !it.isNaN() && it >= 0.0 } ?: lineSubSum
    val gstAmount =
      if (isPlainNonGst) 0.0
      else json.optDouble("gstAmount", -1.0).takeIf { !it.isNaN() && it >= 0.0 } ?: lineGstSum
    val grandTotal =
      json.optDouble("grandTotal", -1.0).takeIf { !it.isNaN() && it > 0.0 }
        ?: (subtotal + gstAmount).coerceAtLeast(0.0)

    return AiExtractedBill(
      documentKind = docKind,
      matchedPartyId = matchedParty?.id.orEmpty(),
      partyName = matchedParty?.name ?: rawPartyName.ifBlank { "Scanned Vendor" },
      partyGstin = json.optString("vendorGstin", "").trim().uppercase().ifBlank { matchedParty?.gstin.orEmpty() },
      billOrInvoiceNumber = json.optString("invoiceOrBillNumber", "").trim(),
      billDate = json.optString("invoiceDate", "").trim(),
      paymentMethod = normalizePaymentMethod(json.optString("paymentMethod", "upi")),
      expenseCategory = normalizeExpenseCategory(json.optString("expenseCategory", ErpExpense.CAT_PURCHASES)),
      subtotal = subtotal,
      gstAmount = gstAmount,
      grandTotal = grandTotal,
      notes = json.optString("notes", "").trim(),
      lineItems = parsedLines,
    )
  }

  private fun parseLineItemsArray(
    arr: JSONArray,
    inventory: List<InventoryItem>,
    isPlainNonGst: Boolean,
  ): List<AiDraftLineItem> =
    buildList {
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val rawName = obj.optString("itemName", "").trim()
        if (rawName.isBlank()) continue
        val matched = matchInventoryItem(rawName, inventory)
        val qty = obj.optDouble("qty", 1.0).takeIf { !it.isNaN() && it > 0.0 } ?: 1.0
        val rawPrice = obj.optDouble("unitPrice", -1.0)
        val price =
          when {
            !rawPrice.isNaN() && rawPrice > 0.0 -> rawPrice
            matched != null -> matched.salePrice
            else -> 0.0
          }
        val rawGst = obj.optDouble("gstRatePercent", -1.0)
        val gst =
          when {
            isPlainNonGst -> 0.0
            !rawGst.isNaN() && rawGst >= 0.0 -> rawGst.coerceIn(0.0, 28.0)
            matched != null -> matched.gstRatePercent
            else -> 18.0
          }
        val unit = obj.optString("unit", "").trim().ifBlank { matched?.unit ?: "PCS" }
        val hsn = obj.optString("hsnCode", "").trim().ifBlank { matched?.hsnCode.orEmpty() }
        add(
          AiDraftLineItem(
            matchedInventoryId = matched?.id.orEmpty(),
            name = matched?.name ?: rawName,
            sku = matched?.sku.orEmpty(),
            hsnCode = hsn,
            unit = unit,
            qty = qty,
            unitPrice = price,
            gstRatePercent = gst,
          )
        )
      }
    }

  private fun matchInventoryItem(queryName: String, inventory: List<InventoryItem>): InventoryItem? {
    val q = queryName.trim().lowercase()
    if (q.isBlank()) return null
    return inventory.firstOrNull { it.name.trim().lowercase() == q || it.sku.trim().lowercase() == q }
      ?: inventory.firstOrNull {
        val n = it.name.trim().lowercase()
        n.contains(q) || q.contains(n)
      }
  }

  private fun matchParty(queryName: String, parties: List<ErpParty>): ErpParty? {
    val q = queryName.trim().lowercase()
    if (q.isBlank()) return null
    return parties.firstOrNull { it.name.trim().lowercase() == q }
      ?: parties.firstOrNull {
        val n = it.name.trim().lowercase()
        n.contains(q) || q.contains(n)
      }
  }

  private fun normalizePaymentMethod(raw: String): String =
    when (raw.trim().lowercase()) {
      "cash" -> GstInvoice.PAYMENT_CASH
      "card" -> GstInvoice.PAYMENT_CARD
      "credit", "udhar", "unpaid" -> GstInvoice.PAYMENT_CREDIT
      else -> GstInvoice.PAYMENT_UPI
    }

  private fun normalizePaymentStatus(raw: String, method: String): String =
    when {
      method == GstInvoice.PAYMENT_CREDIT -> GstInvoice.STATUS_UNPAID
      raw.trim().lowercase() == "unpaid" -> GstInvoice.STATUS_UNPAID
      raw.trim().lowercase() == "partial" -> GstInvoice.STATUS_PARTIAL
      else -> GstInvoice.STATUS_PAID
    }

  private fun normalizeExpenseCategory(raw: String): String {
    val key = raw.trim().lowercase()
    return ErpExpense.ALL_CATEGORIES.firstOrNull { it.first == key || it.second.lowercase() == key }?.first
      ?: when {
        key.contains("rent") -> ErpExpense.CAT_RENT
        key.contains("sal") || key.contains("pay") -> ErpExpense.CAT_PAYROLL
        key.contains("elec") || key.contains("util") || key.contains("net") -> ErpExpense.CAT_UTILITIES
        key.contains("purch") || key.contains("stock") -> ErpExpense.CAT_PURCHASES
        key.contains("fuel") || key.contains("diesel") || key.contains("trans") -> ErpExpense.CAT_TRANSPORT
        key.contains("ad") || key.contains("mark") -> ErpExpense.CAT_MARKETING
        key.contains("tax") || key.contains("gst") -> ErpExpense.CAT_TAX
        else -> ErpExpense.CAT_OTHER
      }
  }

  fun formatQtyShort(qty: Double): String =
    if (abs(qty - qty.toLong().toDouble()) < 0.001) qty.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", qty)
}
