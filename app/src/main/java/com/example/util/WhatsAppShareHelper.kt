package com.example.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.BusinessProfile
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.sync.ErpAppSettings
import com.example.ui.screens.formatErpMoney
import com.example.ui.viewmodel.DashboardKpiSummary
import java.io.File
import java.io.FileOutputStream

object WhatsAppShareHelper {

  fun normalizeWhatsAppPhone(rawPhone: String?): String? {
    val digits = rawPhone.orEmpty().filter { it.isDigit() }
    return when {
      digits.length == 10 -> "91$digits"
      digits.length in 11..15 -> digits
      else -> null
    }
  }

  fun shareTextToWhatsApp(
    context: Context,
    text: String,
    rawPhoneNumber: String? = null,
    chooserTitle: String = "Share via WhatsApp",
  ) {
    val normalizedPhone = normalizeWhatsAppPhone(rawPhoneNumber)
    if (normalizedPhone != null) {
      val waUri = Uri.parse("https://wa.me/$normalizedPhone?text=${Uri.encode(text)}")
      val directChatIntent = Intent(Intent.ACTION_VIEW, waUri)
      if (runCatching { context.startActivity(directChatIntent) }.isSuccess) {
        return
      }
    }

    val whatsAppIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage("com.whatsapp")
      }
    if (runCatching { context.startActivity(whatsAppIntent) }.isSuccess) {
      return
    }

    val businessWhatsAppIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage("com.whatsapp.w4b")
      }
    if (runCatching { context.startActivity(businessWhatsAppIntent) }.isSuccess) {
      return
    }

    val fallbackIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
      }
    val launched = runCatching {
      context.startActivity(Intent.createChooser(fallbackIntent, chooserTitle))
    }
    if (launched.isFailure) {
      Toast.makeText(context, "No sharing app found on device", Toast.LENGTH_SHORT).show()
    }
  }

  fun shareInvoicePdfToWhatsApp(
    context: Context,
    invoice: GstInvoice,
    profile: BusinessProfile?,
    currency: String,
    appSettings: ErpAppSettings,
    isPlainNonGst: Boolean,
    captionText: String,
  ) {
    val result = runCatching {
      val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
      val safeName = InvoicePdfPrinter.buildSafePdfFileName(invoice.invoiceNumber)
      val outFile = File(sharedDir, safeName)
      FileOutputStream(outFile).use { stream ->
        InvoicePdfPrinter.writeInvoicePdfToStream(
          invoice = invoice,
          profile = profile,
          currency = currency,
          options = InvoicePrintOptions.fromSettings(appSettings),
          outputStream = stream,
        )
      }
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    val pdfUri = result.getOrNull()
    if (pdfUri == null) {
      shareTextToWhatsApp(context, captionText)
      return
    }

    val baseSendIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        putExtra(Intent.EXTRA_TEXT, captionText)
        putExtra(
          Intent.EXTRA_SUBJECT,
          if (isPlainNonGst) "Bill ${invoice.invoiceNumber}" else "GST Invoice ${invoice.invoiceNumber}",
        )
        clipData = ClipData.newUri(context.contentResolver, safePdfLabel(invoice), pdfUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

    val waIntent = Intent(baseSendIntent).apply { setPackage("com.whatsapp") }
    if (runCatching { context.startActivity(waIntent) }.isSuccess) return

    val waBizIntent = Intent(baseSendIntent).apply { setPackage("com.whatsapp.w4b") }
    if (runCatching { context.startActivity(waBizIntent) }.isSuccess) return

    val fallback = runCatching {
      context.startActivity(Intent.createChooser(baseSendIntent, "Share Invoice PDF"))
    }
    if (fallback.isFailure) {
      shareTextToWhatsApp(context, captionText)
    }
  }

  private fun safePdfLabel(invoice: GstInvoice): String =
    "Invoice_${invoice.invoiceNumber}"

  fun formatInvoiceSummary(
    invoice: GstInvoice,
    profile: BusinessProfile?,
    currency: String,
    isPlainNonGst: Boolean,
  ): String =
    buildString {
      val sender = profile?.businessName?.ifBlank { null } ?: "Finboss Billing No.1 ERP"
      appendLine("*$sender*")
      appendLine("${if (isPlainNonGst) "Bill" else "Tax Invoice"}: *${invoice.invoiceNumber}*")
      appendLine("Customer: ${invoice.partyName}")
      if (!isPlainNonGst && invoice.partyGstin.isNotBlank()) {
        appendLine("Buyer GSTIN: ${invoice.partyGstin}")
      }
      appendLine("Items:")
      invoice.lineItems.forEachIndexed { idx, line ->
        appendLine("${idx + 1}. ${line.name} (${line.qty} ${line.unit}) = ${formatErpMoney(currency, line.lineTotal)}")
      }
      appendLine("Grand Total: *${formatErpMoney(currency, invoice.grandTotal)}*")
      appendLine("Paid: ${formatErpMoney(currency, invoice.paidAmount)} • Due: ${formatErpMoney(currency, invoice.balanceDue)}")
      if (!isPlainNonGst && invoice.hasEInvoice) {
        appendLine("E-Invoice IRN: ${invoice.irn}")
      }
      if (!isPlainNonGst && invoice.hasEWayBill) {
        appendLine("E-Way Bill: #${invoice.ewbNumber} (${invoice.distanceKm} KM)")
      }
      appendLine("_Shared via Finboss Billing No.1 ERP_")
    }

  fun formatPaymentReceiptMessage(
    payerName: String,
    referenceLabel: String,
    amountPaid: Double,
    remainingDue: Double,
    paymentMethod: String,
    businessName: String,
    currency: String,
  ): String =
    buildString {
      appendLine("*Payment Receipt • $businessName*")
      appendLine("Received From: *$payerName*")
      appendLine("Reference: $referenceLabel")
      appendLine("Amount Received: *${formatErpMoney(currency, amountPaid)}* (${paymentMethod.uppercase()})")
      appendLine("Remaining Balance: ${formatErpMoney(currency, remainingDue.coerceAtLeast(0.0))}")
      appendLine("Thank you for your payment!")
      appendLine("_Finboss Billing No.1 ERP_")
    }

  fun formatPartyStatementSummary(
    party: ErpParty,
    businessName: String,
    currency: String,
  ): String =
    buildString {
      val typeLabel = if (party.partyType == ErpParty.TYPE_CUSTOMER) "Customer Ledger" else "Supplier Ledger"
      appendLine("*$typeLabel Statement • $businessName*")
      appendLine("Party: *${party.name}*")
      if (party.gstin.isNotBlank()) appendLine("GSTIN: ${party.gstin}")
      appendLine("Total Billed: ${formatErpMoney(currency, party.totalBilled)}")
      appendLine("Total Paid: ${formatErpMoney(currency, party.totalPaid)}")
      appendLine("Opening Balance: ${formatErpMoney(currency, party.openingDue)}")
      appendLine("Net Balance Due: *${formatErpMoney(currency, party.outstandingBalance)}*")
      appendLine("_Shared via Finboss Billing No.1 ERP_")
    }

  fun formatInventoryItemSummary(
    item: InventoryItem,
    businessName: String,
    currency: String,
    isPlainNonGst: Boolean,
  ): String =
    buildString {
      appendLine("*Product & Stock Details • $businessName*")
      appendLine("Item: *${item.name}* (${item.sku})")
      if (!isPlainNonGst && item.hsnCode.isNotBlank()) {
        appendLine("HSN: ${item.hsnCode} • GST: ${item.gstRatePercent.toInt()}%")
      }
      appendLine("Sale Rate: *${formatErpMoney(currency, item.salePrice)}* / ${item.unit}")
      appendLine("Available Stock: ${item.stockQty} ${item.unit}")
      appendLine("_Shared via Finboss Billing No.1 ERP_")
    }

  fun formatExpenseSummary(
    expense: ErpExpense,
    businessName: String,
    currency: String,
    isPlainNonGst: Boolean,
  ): String =
    buildString {
      appendLine("*Expense Voucher • $businessName*")
      appendLine("Title: *${expense.title}*")
      appendLine("Category: ${expense.category.uppercase()} • Mode: ${expense.paymentMethod.uppercase()}")
      if (expense.partyName.isNotBlank()) appendLine("Payee: ${expense.partyName}")
      appendLine("Amount: *${formatErpMoney(currency, expense.amount)}* (${expense.status.uppercase()})")
      if (!isPlainNonGst && expense.gstInputAmount > 0.0) {
        appendLine("GST Input Credit: ${formatErpMoney(currency, expense.gstInputAmount)}")
      }
      appendLine("_Shared via Finboss Billing No.1 ERP_")
    }

  fun formatDashboardOverviewSummary(
    businessName: String,
    isPlainNonGst: Boolean,
    kpi: DashboardKpiSummary,
    currency: String,
  ): String =
    buildString {
      appendLine("*$businessName — Business Summary*")
      appendLine("Collected Revenue: *${formatErpMoney(currency, kpi.totalSales)}*")
      appendLine("Pending Receivables: ${formatErpMoney(currency, kpi.pendingReceivables)} (${kpi.pendingCount} pending)")
      appendLine("Total Expenses: ${formatErpMoney(currency, kpi.totalExpenses)}")
      appendLine("Net Cashflow: *${formatErpMoney(currency, kpi.netCashflow)}*")
      appendLine("Invoices Count: ${kpi.invoiceCount} • Parties: ${kpi.partyCount}")
      appendLine("Stock Valuation: ${formatErpMoney(currency, kpi.stockValuation)}")
      if (!isPlainNonGst) {
        appendLine("Net GST Due: ${formatErpMoney(currency, kpi.netGstPayable)}")
      }
      appendLine("_Shared via Finboss Billing No.1 ERP_")
    }
}
