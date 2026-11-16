package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MonoMetricMedium
import com.example.util.ItemStockProfitSummary
import com.example.util.LedgerAnalyticsEngine
import com.example.util.LedgerAnalyticsReport
import com.example.util.LedgerPeriod
import com.example.util.LedgerTransactionRow
import java.util.Locale

val ProfitGreenText = Color(0xFF15803D)
val ProfitGreenBg = Color(0xFFDCFCE7)
val LossRedText = Color(0xFFDC2626)
val LossRedBg = Color(0xFFFEE2E2)

@Composable
fun ProfitLossPercentBadge(
  profitAmount: Double,
  profitPercent: Double,
  currency: String,
  showAmount: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val isProfit = profitAmount >= 0.0
  val fg = if (isProfit) ProfitGreenText else LossRedText
  val bg = if (isProfit) ProfitGreenBg else LossRedBg
  val pctText = LedgerAnalyticsEngine.formatProfitPercent(profitPercent)
  val label =
    if (showAmount) {
      val tag = if (isProfit) "Profit" else "Loss"
      "$tag $pctText (${formatErpMoney(currency, kotlin.math.abs(profitAmount))})"
    } else {
      val tag = if (isProfit) "Profit" else "Loss"
      "$tag $pctText"
    }

  Surface(
    color = bg,
    shape = RoundedCornerShape(999.dp),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = if (isProfit) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun LedgerPeriodAndExportCard(
  selectedPeriod: LedgerPeriod,
  report: LedgerAnalyticsReport,
  currency: String,
  onSelectPeriod: (LedgerPeriod) -> Unit,
  onPrintPdf: () -> Unit,
  onDownloadPdf: () -> Unit,
  onShareWhatsApp: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth().testTag("ledger_period_export_card"),
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
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Ledger Period & P&L",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = report.rangeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        ProfitLossPercentBadge(
          profitAmount = report.netProfitAmount,
          profitPercent = report.netProfitPercent,
          currency = currency,
          showAmount = false,
          modifier = Modifier.testTag("ledger_overall_pl_badge"),
        )
      }

      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(LedgerPeriod.entries) { period ->
          FilterChip(
            selected = selectedPeriod == period,
            onClick = { onSelectPeriod(period) },
            label = { Text(period.label) },
            modifier = Modifier.testTag("ledger_period_chip_${period.key}"),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.weight(1f),
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Period Sales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatErpMoney(currency, report.totalSalesRevenue), style = MonoMetricMedium)
          }
        }
        Surface(
          color = if (report.isOverallProfit) ProfitGreenBg.copy(alpha = 0.65f) else LossRedBg.copy(alpha = 0.65f),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.weight(1f),
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = if (report.isOverallProfit) "Net Profit" else "Net Loss",
              style = MaterialTheme.typography.labelSmall,
              color = if (report.isOverallProfit) ProfitGreenText else LossRedText,
            )
            Text(
              text = "${formatErpMoney(currency, report.netProfitAmount)} (${LedgerAnalyticsEngine.formatProfitPercent(report.netProfitPercent)})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = if (report.isOverallProfit) ProfitGreenText else LossRedText,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onPrintPdf,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ledger_print_pdf_button"),
        ) {
          Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Print")
        }
        OutlinedButton(
          onClick = onDownloadPdf,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ledger_download_pdf_button"),
        ) {
          Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Download")
        }
        OutlinedButton(
          onClick = onShareWhatsApp,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ledger_whatsapp_share_button"),
        ) {
          Icon(Icons.Filled.Share, contentDescription = null, tint = ProfitGreenText, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("WhatsApp", color = ProfitGreenText)
        }
      }
    }
  }
}

@Composable
fun ItemStockProfitCard(
  item: ItemStockProfitSummary,
  currency: String,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth().testTag("ledger_item_card_${item.itemId}"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(38.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Inventory2,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(8.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = item.itemName,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "Stock: ${formatQtyLabel(item.currentStockQty)} ${item.unit} • Val: ${formatErpMoney(currency, item.stockValuation)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        if (item.hasSoldActivity) {
          ProfitLossPercentBadge(
            profitAmount = item.profitAmount,
            profitPercent = item.profitPercent,
            currency = currency,
            showAmount = false,
          )
        } else {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(999.dp),
          ) {
            Text(
              text = "No Sales",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Sold: ${formatQtyLabel(item.soldQty)} ${item.unit} (${formatErpMoney(currency, item.salesValue)})",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text =
            if (item.hasSoldActivity) {
              val prefix = if (item.isProfit) "Profit" else "Loss"
              "$prefix: ${formatErpMoney(currency, item.profitAmount)}"
            } else {
              "Buy ${formatErpMoney(currency, item.purchaseUnitPrice)} • Sell ${formatErpMoney(currency, item.saleUnitPrice)}"
            },
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color =
            if (!item.hasSoldActivity) MaterialTheme.colorScheme.onSurfaceVariant
            else if (item.isProfit) ProfitGreenText
            else LossRedText,
        )
      }
    }
  }
}

@Composable
fun LedgerTransactionCard(
  tx: LedgerTransactionRow,
  currency: String,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth().testTag("ledger_tx_card_${tx.id}"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.ReceiptLong,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(6.dp),
          ) {
            Text(
              text = tx.voucherType,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
          Text(
            text = tx.voucherNumber,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = formatErpMoney(currency, tx.amount),
          style = MonoMetricMedium,
        )
      }

      Text(
        text = "${tx.partyName} — ${tx.itemsSummary}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = tx.dateLabel,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val pAmt = tx.profitAmount
        val pPct = tx.profitPercent
        if (pAmt != null && pPct != null) {
          ProfitLossPercentBadge(
            profitAmount = pAmt,
            profitPercent = pPct,
            currency = currency,
            showAmount = true,
          )
        }
      }
    }
  }
}

private fun formatQtyLabel(qty: Double): String =
  if (qty % 1.0 == 0.0) qty.toLong().toString() else String.format(Locale.US, "%.2f", qty)
