package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessProfile
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpExpense
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import com.example.util.LedgerAnalyticsEngine
import com.example.util.LedgerPeriod
import com.example.util.LedgerReportPdfPrinter
import com.example.util.WhatsAppShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TaxSlabSummary(
  val ratePercent: Int,
  val taxableValue: Double,
  val taxAmount: Double,
)

@Composable
fun ReportsModuleContent(
  profile: BusinessProfile?,
  invoices: List<GstInvoice>,
  expenses: List<ErpExpense>,
  entries: List<DashboardEntry>,
  parties: List<ErpParty> = emptyList(),
  inventory: List<InventoryItem> = emptyList(),
  currency: String,
  onSwitchProfileClick: () -> Unit = {},
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var period by rememberSaveable { mutableStateOf(LedgerPeriod.THIS_MONTH) }
  val isPlainNonGst = profile?.isPlainNonGst == true

  val dateRange = remember(period) { LedgerAnalyticsEngine.resolveDateRange(period) }

  val filteredInvoices =
    remember(invoices, dateRange) {
      invoices.filter { (it.createdAt?.toDate()?.time ?: System.currentTimeMillis()) in dateRange.startMs..dateRange.endMs }
    }
  val filteredExpenses =
    remember(expenses, dateRange) {
      expenses.filter { (it.createdAt?.toDate()?.time ?: System.currentTimeMillis()) in dateRange.startMs..dateRange.endMs }
    }
  val filteredEntries =
    remember(entries, dateRange) {
      entries.filter { (it.createdAt?.toDate()?.time ?: System.currentTimeMillis()) in dateRange.startMs..dateRange.endMs }
    }

  val ledgerAnalytics =
    remember(period, parties, inventory, invoices, expenses, entries, isPlainNonGst) {
      LedgerAnalyticsEngine.buildReport(
        period = period,
        parties = parties,
        inventory = inventory,
        invoices = invoices,
        expenses = expenses,
        entries = entries,
        isPlainNonGst = isPlainNonGst,
      )
    }

  val downloadLedgerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { targetUri ->
      if (targetUri == null) return@rememberLauncherForActivityResult
      coroutineScope.launch {
        val res =
          withContext(Dispatchers.IO) {
            runCatching {
              context.contentResolver.openOutputStream(targetUri)?.use { out ->
                LedgerReportPdfPrinter.writeLedgerPdfToStream(
                  report = ledgerAnalytics,
                  profile = profile,
                  currency = currency,
                  isPlainNonGst = isPlainNonGst,
                  outputStream = out,
                )
              } ?: error("Unable to open destination PDF file")
            }
          }
        Toast.makeText(
          context,
          if (res.isSuccess) "Report PDF downloaded" else (res.exceptionOrNull()?.localizedMessage ?: "Download failed"),
          Toast.LENGTH_SHORT,
        ).show()
      }
    }

  val report =
    remember(filteredInvoices, filteredExpenses, filteredEntries) {
      val taxableOutward = filteredInvoices.sumOf { it.subtotal }
      val outputGst = filteredInvoices.sumOf { it.gstTotal }
      var cgstTotal = 0.0
      var sgstTotal = 0.0
      var igstTotal = 0.0
      var b2bCount = 0
      var b2bTotal = 0.0
      var b2cTotal = 0.0
      val slabBuckets = mutableMapOf<Int, Pair<Double, Double>>() // rate -> (taxable, tax)

      filteredInvoices.forEach { inv ->
        when (inv.taxMode) {
          GstInvoice.TAX_MODE_CGST_SGST -> {
            cgstTotal += inv.gstTotal / 2.0
            sgstTotal += inv.gstTotal / 2.0
          }
          GstInvoice.TAX_MODE_IGST -> igstTotal += inv.gstTotal
        }
        if (inv.partyGstin.isNotBlank()) {
          b2bCount++
          b2bTotal += inv.grandTotal
        } else {
          b2cTotal += inv.grandTotal
        }
        inv.lineItems.forEach { line ->
          val rateInt = line.gstRatePercent.toInt()
          val prev = slabBuckets[rateInt] ?: (0.0 to 0.0)
          slabBuckets[rateInt] = (prev.first + line.taxableAmount) to (prev.second + line.gstAmount)
        }
      }

      val slabList =
        slabBuckets.entries
          .sortedBy { it.key }
          .map { (rate, pair) -> TaxSlabSummary(rate, pair.first, pair.second) }

      val inputGstCredit = filteredExpenses.sumOf { it.gstInputAmount }
      val netGstPayable = (outputGst - inputGstCredit).coerceAtLeast(0.0)
      val itcCarryForward = (inputGstCredit - outputGst).coerceAtLeast(0.0)

      val quickSales =
        filteredEntries
          .filter { it.category != DashboardEntry.CATEGORY_EXPENSE }
          .sumOf { it.amount }
      val quickExpenses =
        filteredEntries
          .filter { it.category == DashboardEntry.CATEGORY_EXPENSE }
          .sumOf { it.amount }
      val totalRevenue = taxableOutward + quickSales
      val totalOperatingExpenses = filteredExpenses.sumOf { it.amount } + quickExpenses
      val netProfit = totalRevenue - totalOperatingExpenses

      GstReportMetrics(
        taxableOutward = taxableOutward,
        outputGst = outputGst,
        cgstTotal = cgstTotal,
        sgstTotal = sgstTotal,
        igstTotal = igstTotal,
        b2bCount = b2bCount,
        b2bTotal = b2bTotal,
        b2cTotal = b2cTotal,
        inputGstCredit = inputGstCredit,
        netGstPayable = netGstPayable,
        itcCarryForward = itcCarryForward,
        totalRevenue = totalRevenue,
        totalOperatingExpenses = totalOperatingExpenses,
        netProfit = netProfit,
        slabs = slabList,
      )
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(LedgerPeriod.entries) { p ->
            FilterChip(
              selected = period == p,
              onClick = { period = p },
              label = { Text(p.label) },
              modifier = Modifier.testTag("reports_period_chip_${p.key}"),
            )
          }
        }
        Text(
          text = dateRange.rangeLabel,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (isPlainNonGst) {
      item {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Plain Non-GST Account Active",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
              text =
                "GSTR-1, GSTR-3B, and GST slab tax filings are disabled for Plain Non-GST profiles. Your Profit & Loss statement below stays active, or switch to a Main GST account for tax reports.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Button(
              onClick = onSwitchProfileClick,
              modifier = Modifier.heightIn(min = 48.dp).testTag("reports_switch_profile_button"),
            ) {
              Text("Switch Business Profile")
            }
          }
        }
      }
    } else {
      // GSTR-3B Hero Card
      item {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .clip(MaterialTheme.shapes.large)
              .background(
                Brush.linearGradient(
                  colors = listOf(Color(0xFF0B253C), Color(0xFF144266), Color(0xFF0D4A4E))
                )
              )
              .padding(20.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(color = Color(0x3310B981), shape = RoundedCornerShape(50)) {
                Text(
                  text = "GSTR-3B SUMMARY • ${period.label.uppercase()}",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFFA7F3D0),
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
              if (!profile?.gstin.isNullOrBlank()) {
                Text(
                  text = "GSTIN: ${profile?.gstin}",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFFCBD5E1),
                )
              }
            }
            Text(
              text =
                if (report.netGstPayable > 0.0 || report.itcCarryForward == 0.0) "Net GST Payable"
                else "Net ITC Credit Carry Forward",
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFFCBD5E1),
            )
            Text(
              text =
                formatErpMoney(
                  currency,
                  if (report.netGstPayable > 0.0 || report.itcCarryForward == 0.0) report.netGstPayable
                  else report.itcCarryForward,
                ),
              style = MonoMetricLarge,
              color = Color.White,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Column {
                Text("Output GST", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                Text(
                  formatErpMoney(currency, report.outputGst),
                  style = MaterialTheme.typography.titleMedium,
                  color = Color.White,
                  fontWeight = FontWeight.SemiBold,
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                Text("Input Tax Credit (ITC)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                Text(
                  formatErpMoney(currency, report.inputGstCredit),
                  style = MaterialTheme.typography.titleMedium,
                  color = Color(0xFFA7F3D0),
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }
      }

      // GSTR-1 Outward Supplies
      item {
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("GSTR-1 Outward Supplies (${filteredInvoices.size} Bills)", style = MaterialTheme.typography.titleMedium)
            ReportMetricRow("Taxable Value", formatErpMoney(currency, report.taxableOutward))
            ReportMetricRow("CGST Output", formatErpMoney(currency, report.cgstTotal))
            ReportMetricRow("SGST Output", formatErpMoney(currency, report.sgstTotal))
            ReportMetricRow("IGST Output", formatErpMoney(currency, report.igstTotal))
            HorizontalDivider()
            ReportMetricRow("B2B Registered (${report.b2bCount} bills)", formatErpMoney(currency, report.b2bTotal))
            ReportMetricRow("B2C / Walk-in Sales", formatErpMoney(currency, report.b2cTotal))
          }
        }
      }

      // Slab-wise Breakdown
      item {
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("GST Slab Breakdown", style = MaterialTheme.typography.titleMedium)
            if (report.slabs.isEmpty()) {
              Text(
                text = "No GST invoices recorded in ${period.label.lowercase()} yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              val maxTaxable = report.slabs.maxOf { it.taxableValue }.coerceAtLeast(1.0)
              val barColor = MaterialTheme.colorScheme.secondary
              val trackColor = MaterialTheme.colorScheme.surfaceVariant
              report.slabs.forEach { slab ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${slab.ratePercent}% GST Slab", style = MaterialTheme.typography.labelLarge)
                    Text(
                      "Taxable ${formatErpMoney(currency, slab.taxableValue)} • Tax ${formatErpMoney(currency, slab.taxAmount)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  val fraction = (slab.taxableValue / maxTaxable).toFloat().coerceIn(0.05f, 1f)
                  Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                    drawRoundRect(
                      color = trackColor,
                      size = size,
                      cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    )
                    drawRoundRect(
                      color = barColor,
                      topLeft = Offset.Zero,
                      size = Size(size.width * fraction, size.height),
                      cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // Profit & Loss Summary
    item {
      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Profit & Loss Summary", style = MaterialTheme.typography.titleMedium)
          }
          ReportMetricRow(
            if (isPlainNonGst) "Total Sales Revenue" else "Net Revenue (Excl. GST)",
            formatErpMoney(currency, report.totalRevenue),
          )
          ReportMetricRow("Operating Expenses", formatErpMoney(currency, report.totalOperatingExpenses))
          HorizontalDivider()
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Estimated Net Profit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ProfitLossPercentBadge(
                profitAmount = ledgerAnalytics.netProfitAmount,
                profitPercent = ledgerAnalytics.netProfitPercent,
                currency = currency,
                showAmount = false,
              )
              Text(
                text = formatErpMoney(currency, report.netProfit),
                style = MonoMetricMedium,
                color = if (report.netProfit >= 0.0) ProfitGreenText else LossRedText,
              )
            }
          }
        }
      }
    }

    // Item-wise Profit / Loss (%) Card
    item {
      val activeItems = ledgerAnalytics.itemSummaries.filter { it.hasSoldActivity }
      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().testTag("reports_item_profit_card"),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Item-Wise Profit / Loss (%)", style = MaterialTheme.typography.titleMedium)
          if (activeItems.isEmpty()) {
            Text(
              text = "No item sales in ${period.label.lowercase()} yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            activeItems.take(8).forEach { item ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(item.itemName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    text = "Sales ${formatErpMoney(currency, item.salesValue)} • Cost ${formatErpMoney(currency, item.costOfSold)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                ProfitLossPercentBadge(
                  profitAmount = item.profitAmount,
                  profitPercent = item.profitPercent,
                  currency = currency,
                  showAmount = true,
                )
              }
            }
          }
        }
      }
    }

    // Party-wise Profit / Loss (%) Card
    item {
      val activeParties = ledgerAnalytics.partySummaries.filter { it.hasSalesActivity }
      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().testTag("reports_party_profit_card"),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Party-Wise Profit / Loss (%)", style = MaterialTheme.typography.titleMedium)
          if (activeParties.isEmpty()) {
            Text(
              text = "No party sales in ${period.label.lowercase()} yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            activeParties.take(8).forEach { party ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(party.partyName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    text = "${party.transactionCount} bills • Sales ${formatErpMoney(currency, party.salesNetRevenue)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                ProfitLossPercentBadge(
                  profitAmount = party.profitAmount,
                  profitPercent = party.profitPercent,
                  currency = currency,
                  showAmount = true,
                )
              }
            }
          }
        }
      }
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Button(
            onClick = {
              runCatching {
                LedgerReportPdfPrinter.printLedgerReport(
                  context = context,
                  report = ledgerAnalytics,
                  profile = profile,
                  currency = currency,
                  isPlainNonGst = isPlainNonGst,
                )
              }.onFailure { err ->
                Toast.makeText(context, err.localizedMessage ?: "Print failed", Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier.weight(1f).heightIn(min = 50.dp).testTag("reports_print_pdf_button"),
          ) {
            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Print PDF")
          }
          OutlinedButton(
            onClick = { downloadLedgerLauncher.launch(LedgerReportPdfPrinter.buildPdfFileName(period)) },
            modifier = Modifier.weight(1f).heightIn(min = 50.dp).testTag("reports_download_pdf_button"),
          ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Download PDF")
          }
        }

        Button(
          onClick = {
            val text = buildShareableTaxReport(profile, period, report, currency, isPlainNonGst)
            val shareIntent =
              Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                  Intent.EXTRA_SUBJECT,
                  if (isPlainNonGst) "Book Business P&L Summary (${period.label})"
                  else "Book GST & P&L Report (${period.label})",
                )
                putExtra(Intent.EXTRA_TEXT, text)
              }
            context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
          },
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("share_gst_report_button"),
        ) {
          Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(if (isPlainNonGst) "Share Business P&L Summary" else "Share CA / Tax Filing Summary")
        }

        OutlinedButton(
          onClick = {
            val text = buildShareableTaxReport(profile, period, report, currency, isPlainNonGst)
            WhatsAppShareHelper.shareTextToWhatsApp(
              context = context,
              text = text,
              chooserTitle = "Share Report on WhatsApp",
            )
          },
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("share_gst_report_whatsapp_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Share Report on WhatsApp", color = MaterialTheme.colorScheme.secondary)
        }
      }
    }
  }
}

private data class GstReportMetrics(
  val taxableOutward: Double,
  val outputGst: Double,
  val cgstTotal: Double,
  val sgstTotal: Double,
  val igstTotal: Double,
  val b2bCount: Int,
  val b2bTotal: Double,
  val b2cTotal: Double,
  val inputGstCredit: Double,
  val netGstPayable: Double,
  val itcCarryForward: Double,
  val totalRevenue: Double,
  val totalOperatingExpenses: Double,
  val netProfit: Double,
  val slabs: List<TaxSlabSummary>,
)

@Composable
private fun ReportMetricRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
  }
}

private fun buildShareableTaxReport(
  profile: BusinessProfile?,
  period: LedgerPeriod,
  report: GstReportMetrics,
  currency: String,
  isPlainNonGst: Boolean = false,
): String = buildString {
  appendLine(profile?.businessName ?: "Book")
  if (!isPlainNonGst && !profile?.gstin.isNullOrBlank()) appendLine("GSTIN: ${profile?.gstin}")
  appendLine(
    if (isPlainNonGst) "Business P&L Summary (${period.label})"
    else "GST & Tax Summary (${period.label})"
  )
  appendLine("------------------------------")
  if (!isPlainNonGst) {
    appendLine("Taxable Outward Sales: ${formatErpMoney(currency, report.taxableOutward)}")
    appendLine("Output GST: ${formatErpMoney(currency, report.outputGst)} (CGST ${formatErpMoney(currency, report.cgstTotal)}, SGST ${formatErpMoney(currency, report.sgstTotal)}, IGST ${formatErpMoney(currency, report.igstTotal)})")
    appendLine("Input Tax Credit (ITC): ${formatErpMoney(currency, report.inputGstCredit)}")
    appendLine("Net GST Payable: ${formatErpMoney(currency, report.netGstPayable)}")
    appendLine("------------------------------")
  }
  appendLine("Net Revenue: ${formatErpMoney(currency, report.totalRevenue)}")
  appendLine("Operating Expenses: ${formatErpMoney(currency, report.totalOperatingExpenses)}")
  appendLine("Net Profit: ${formatErpMoney(currency, report.netProfit)}")
  appendLine("_Shared via Book_")
}
