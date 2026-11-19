package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private enum class BankingMethod(val title: String) {
  NEFT("NEFT"), RTGS("RTGS"), UPI("UPI"), NET_BANKING("Net Banking"),
}

private fun paymentPaise(input: String): Long? {
  val match = Regex("([0-9]{1,12})(?:\\.([0-9]{1,2}))?").matchEntire(input.trim()) ?: return null
  val rupees = match.groupValues[1].toLong()
  val fraction = match.groupValues[2].padEnd(2, '0').toLong()
  return (rupees * 100 + fraction).takeIf { it > 0 }
}

@Composable
internal fun BankingPortalActions() {
  val context = LocalContext.current
  var showBanking by remember { mutableStateOf(false) }
  var launchError by remember { mutableStateOf("") }
  val openExternal: (Uri) -> Unit = { uri ->
    launchError = if (runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.isSuccess) {
      ""
    } else {
      "No compatible browser or payment app was found."
    }
  }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("GST & Banking", style = MaterialTheme.typography.titleLarge)
      Text("On the GST Portal, use Services → Payments → Create Challan. Pay through the options shown on your GST challan. Check the CIN and Electronic Cash Ledger there after payment.")
      Button(onClick = { openExternal(Uri.parse("https://www.gst.gov.in/")) }) {
        Text("Open official GST Portal")
      }
      OutlinedButton(onClick = { launchError = ""; showBanking = true }) {
        Text("Business Banking")
      }
      Text("GST Filing in Finboss · Coming Soon", style = MaterialTheme.typography.titleSmall)
      Text("Income Tax Portal", style = MaterialTheme.typography.titleSmall)
      OutlinedButton(onClick = { openExternal(Uri.parse("https://www.incometax.gov.in/iec/foportal/")) }) {
        Text("Open Income Tax Portal")
      }
      if (!showBanking && launchError.isNotEmpty()) {
        Text(launchError, color = MaterialTheme.colorScheme.error)
      }
    }
  }

  if (showBanking) {
    BusinessBankingDialog(
      launchError = launchError,
      onOpen = openExternal,
      onDismiss = { showBanking = false; launchError = "" },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BusinessBankingDialog(
  launchError: String,
  onOpen: (Uri) -> Unit,
  onDismiss: () -> Unit,
) {
  var method by remember { mutableStateOf(BankingMethod.NEFT) }
  var amount by remember { mutableStateOf("") }
  var upiId by remember { mutableStateOf("") }
  val paise = paymentPaise(amount)
  val address = upiId.trim()
  val upiReady = paise != null && address.matches(Regex("[a-zA-Z0-9._-]+@[a-zA-Z0-9]+"))
  val showBankLinks = method != BankingMethod.UPI && (method != BankingMethod.RTGS || (paise ?: 0) >= 20_000_000L)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Business Banking") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          BankingMethod.entries.forEach { option ->
            FilterChip(selected = method == option, onClick = { method = option }, label = { Text(option.title) })
          }
        }
        Text(
          when (method) {
            BankingMethod.NEFT -> "NEFT has no RBI minimum or maximum for account transfers. Check your bank's limits."
            BankingMethod.RTGS -> "RTGS requires at least ₹2,00,000. Review the transfer amount in your bank."
            BankingMethod.UPI -> "Verify the recipient name in your UPI app before approving payment."
            BankingMethod.NET_BANKING -> "Sign in and authorize the payment on your bank's website."
          }
        )
        if (method == BankingMethod.RTGS || method == BankingMethod.UPI) {
          OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.take(15) },
            label = { Text("Amount (₹)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          )
        }
        if (method == BankingMethod.UPI) {
          OutlinedTextField(
            value = upiId,
            onValueChange = { upiId = it.take(255) },
            label = { Text("Recipient UPI ID") },
            singleLine = true,
          )
          Button(
            enabled = upiReady,
            onClick = {
              val value = paise ?: return@Button
              val fraction = (value % 100).toString().padStart(2, '0')
              val uri = Uri.Builder().scheme("upi").authority("pay")
                .appendQueryParameter("pa", address)
                .appendQueryParameter("am", "${value / 100}.$fraction")
                .appendQueryParameter("cu", "INR")
                .build()
              onOpen(uri)
            },
          ) {
            Text("Open UPI app")
          }
        }
        if (showBankLinks) {
          OutlinedButton(onClick = { onOpen(Uri.parse("https://onlinesbi.sbi.bank.in/")) }) { Text("SBI Net Banking") }
          OutlinedButton(onClick = { onOpen(Uri.parse("https://bobibanking.bankofbaroda.bank.in/")) }) { Text("Bank of Baroda") }
          OutlinedButton(
            onClick = { onOpen(Uri.parse("https://www.unionbankofindia.bank.in/en/details/internet-banking")) },
          ) { Text("Union Bank") }
        }
        Text("For GST tax payments, use the official challan. Finboss does not receive your bank transfer result.")
        if (launchError.isNotEmpty()) Text(launchError, color = MaterialTheme.colorScheme.error)
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}
