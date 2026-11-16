package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.SubscriptionStatus
import com.example.data.model.SubscriptionTier
import com.example.data.sync.ErpAppSettings
import com.example.util.SubscriptionPaymentReceipt
import com.example.util.SubscriptionReceiptPdfPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionPlansSheet(
    status: SubscriptionStatus,
    isOwnerSession: Boolean,
    appSettings: ErpAppSettings = ErpAppSettings(),
    userEmail: String? = null,
    businessName: String = "Finboss Enterprise",
    isBusy: Boolean = false,
    onActivatePlan: (tier: SubscriptionTier, billingCycle: String, onSuccess: () -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedBillingCycle by remember {
        mutableStateOf(if (status.billingCycle == SubscriptionTier.CYCLE_MONTHLY) "monthly" else "yearly")
    }

    val merchantName = remember(appSettings.merchantBeneficiaryName) {
        appSettings.merchantBeneficiaryName.trim().ifBlank { SubscriptionReceiptPdfPrinter.DEFAULT_MERCHANT_BENEFICIARY }
    }
    val merchantUpiId = remember(appSettings.merchantUpiId) {
        appSettings.merchantUpiId.trim().ifBlank { SubscriptionReceiptPdfPrinter.DEFAULT_MERCHANT_UPI }
    }
    val gatewayKeyId = remember(appSettings.merchantGatewayKeyId) {
        appSettings.merchantGatewayKeyId.trim()
    }
    val (resolvedUserEmail, resolvedCcEmail) = remember(userEmail) {
        SubscriptionReceiptPdfPrinter.resolveReceiptRecipientEmails(userEmail)
    }

    // Checkout Modal State
    var checkoutTier by remember { mutableStateOf<SubscriptionTier?>(null) }
    var checkoutCycle by remember { mutableStateOf("monthly") }
    var checkoutPaymentTab by remember { mutableStateOf("upi") } // "upi" or "card"
    var utrInput by remember { mutableStateOf("") }
    var cardNumberInput by remember { mutableStateOf("") }
    var cardExpiryInput by remember { mutableStateOf("") }
    var cardCvvInput by remember { mutableStateOf("") }
    var cardHolderInput by remember { mutableStateOf("") }
    var checkoutErrorMessage by remember { mutableStateOf<String?>(null) }
    var activeReceipt by remember { mutableStateOf<SubscriptionPaymentReceipt?>(null) }
    var activeReceiptPdfUri by remember { mutableStateOf<Uri?>(null) }
    var emailDispatchNotice by remember { mutableStateOf<String?>(null) }

    val saveReceiptPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        val receiptToSave = activeReceipt ?: return@rememberLauncherForActivityResult
        if (targetUri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(targetUri)?.use { out ->
                        SubscriptionReceiptPdfPrinter.writeReceiptPdfToStream(receiptToSave, out)
                    } ?: error("Unable to open destination PDF file")
                }
            }
            if (result.isSuccess) {
                Toast.makeText(context, "Subscription PDF receipt saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to save PDF receipt",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val inrFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
    }
    val decimalInrFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("subscription_plans_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "Subscription & Plan Access",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Finboss Subscription & Plans",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "30-Day Free Trial Active • Upgrade anytime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_subscription_sheet_button")) {
                    Icon(Icons.Default.Close, contentDescription = "Close Subscription Sheet")
                }
            }

            // Current Active Status & 30-Day Free Trial Countdown Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_subscription_status_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (status.isTrial) Icons.Default.AutoAwesome else Icons.Default.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = status.topBarBadgeText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Valid until ${dateFormat.format(Date(status.planExpiresAtMs))} • ${status.daysRemaining} days left",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = if (status.isExpired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                            contentColor = if (status.isExpired) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = if (status.isExpired) "EXPIRED" else "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (status.isTrial) {
                        val progress = (status.daysRemaining.coerceIn(0, 30) / 30f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = status.statusSummaryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "Included: Up to ${status.maxProfiles} Business Profile(s) • ${status.maxSalesmen} Salesman Seat(s) • " +
                                if (status.allowsEInvoiceAndEWayBill) "GST E-Invoice & E-Way Bill Enabled" else "Standard Billing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Monthly vs Yearly Billing Cycle Switch with 10% / 20% Savings Callout
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isMonthly = selectedBillingCycle == "monthly"
                    val isYearly = selectedBillingCycle == "yearly"

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedBillingCycle = "monthly" }
                            .testTag("billing_cycle_monthly_tab"),
                        color = if (isMonthly) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (isMonthly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Monthly Plan",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pay month-to-month",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedBillingCycle = "yearly" }
                            .testTag("billing_cycle_yearly_tab"),
                        color = if (isYearly) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (isYearly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Yearly Plan",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = if (isYearly) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (isYearly) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "SAVE 10%–20%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "10% off Gold • 20% off Diamond",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // Plan Cards: Silver, Gold, Diamond
            SubscriptionTier.PAID_TIERS.forEach { tier ->
                val isCurrentTier = status.configuredTier == tier && status.billingCycle == selectedBillingCycle && !status.isExpired
                val isYearly = selectedBillingCycle == "yearly"
                val displayPrice = if (isYearly) tier.yearlyTotalInr else tier.monthlyPriceInr
                val effectiveMonthly = if (isYearly) tier.effectiveMonthlyInYearlyInr else tier.monthlyPriceInr

                val borderColor by animateColorAsState(
                    targetValue = when {
                        isCurrentTier -> MaterialTheme.colorScheme.primary
                        tier == SubscriptionTier.DIAMOND -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                    label = "planCardBorder"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plan_card_${tier.key}"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (tier == SubscriptionTier.DIAMOND) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ),
                    border = BorderStroke(if (tier == SubscriptionTier.DIAMOND || isCurrentTier) 2.dp else 1.dp, borderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (tier) {
                                            SubscriptionTier.SILVER -> Icons.Default.Shield
                                            SubscriptionTier.GOLD -> Icons.Default.Star
                                            SubscriptionTier.DIAMOND -> Icons.Default.Diamond
                                            SubscriptionTier.TRIAL -> Icons.Default.AutoAwesome
                                        },
                                        contentDescription = null,
                                        tint = when (tier) {
                                            SubscriptionTier.DIAMOND -> MaterialTheme.colorScheme.tertiary
                                            SubscriptionTier.GOLD -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.secondary
                                        }
                                    )
                                    Text(
                                        text = tier.displayName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    if (tier == SubscriptionTier.DIAMOND) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiary,
                                            contentColor = MaterialTheme.colorScheme.onTertiary,
                                            shape = RoundedCornerShape(999.dp)
                                        ) {
                                            Text(
                                                text = "BEST VALUE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = tier.tagline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isYearly && tier.yearlyDiscountPercent > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Savings,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${tier.yearlyDiscountPercent}% OFF",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }

                        // Price Block
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = inrFormatter.format(displayPrice),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isYearly) "/ year" else "/ month",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            if (isYearly && tier.yearlyDiscountPercent > 0) {
                                Text(
                                    text = inrFormatter.format(tier.yearlyUndiscountedInr),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = TextDecoration.LineThrough
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider()

                        // Feature bullets
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tier.featureHighlights.forEach { feature ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = feature,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (isOwnerSession) {
                            Button(
                                onClick = {
                                    checkoutTier = tier
                                    checkoutCycle = selectedBillingCycle
                                    utrInput = ""
                                    cardNumberInput = ""
                                    cardExpiryInput = ""
                                    cardCvvInput = ""
                                    cardHolderInput = ""
                                    checkoutErrorMessage = null
                                },
                                enabled = !isCurrentTier && !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("activate_plan_${tier.key}_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = if (tier == SubscriptionTier.DIAMOND) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    )
                                } else {
                                    ButtonDefaults.buttonColors()
                                }
                            ) {
                                Text(
                                    text = if (isCurrentTier) {
                                        "Current Active Plan (${selectedBillingCycle.replaceFirstChar { it.uppercase() }})"
                                    } else {
                                        "Subscribe to ${tier.displayName} (${inrFormatter.format(displayPrice)})"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (isOwnerSession && !status.isTrial) {
                OutlinedButton(
                    onClick = { onActivatePlan(SubscriptionTier.TRIAL, SubscriptionTier.CYCLE_TRIAL) { onDismiss() } },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("restore_free_trial_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch Back to 30-Day Free Trial (Full Access)")
                }
            }
        }
    }

    // Subscription Payment Checkout Modal Dialog (Strictly NO bank account numbers or IFSC codes)
    val currentCheckoutTier = checkoutTier
    if (currentCheckoutTier != null) {
        val isYearly = checkoutCycle == "yearly"
        val totalAmount = if (isYearly) currentCheckoutTier.yearlyTotalInr else currentCheckoutTier.monthlyPriceInr
        val upiPaymentUri = remember(merchantUpiId, merchantName, totalAmount, currentCheckoutTier, checkoutCycle) {
            SubscriptionReceiptPdfPrinter.buildUpiPaymentUri(
                merchantUpiId = merchantUpiId,
                beneficiaryName = merchantName,
                amountInr = totalAmount,
                planNote = "Finboss ${currentCheckoutTier.displayName} ${checkoutCycle.uppercase()}"
            )
        }
        val upiQrBitmap = remember(upiPaymentUri) {
            SubscriptionReceiptPdfPrinter.generateQrBitmap(upiPaymentUri, sizePx = 512)
        }

        AlertDialog(
            onDismissRequest = { if (!isBusy) checkoutTier = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Secure Checkout • ${currentCheckoutTier.displayName} (${checkoutCycle.replaceFirstChar { it.uppercase() }})")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Amount Payable: ${inrFormatter.format(totalAmount)} (Incl. 18% GST)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Merchant: $merchantName • Verified UPI: $merchantUpiId",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Activation email & PDF receipt auto-send to: $resolvedUserEmail",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Payment Method Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                checkoutPaymentTab = "upi"
                                checkoutErrorMessage = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("checkout_tab_upi"),
                            shape = RoundedCornerShape(10.dp),
                            colors = if (checkoutPaymentTab == "upi") {
                                ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pay via UPI")
                        }
                        OutlinedButton(
                            onClick = {
                                checkoutPaymentTab = "card"
                                checkoutErrorMessage = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("checkout_tab_card"),
                            shape = RoundedCornerShape(10.dp),
                            colors = if (checkoutPaymentTab == "card") {
                                ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Debit / Card")
                        }
                    }

                    if (checkoutPaymentTab == "upi") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (upiQrBitmap != null) {
                                        Surface(
                                            color = Color.White,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Image(
                                                bitmap = upiQrBitmap.asImageBitmap(),
                                                contentDescription = "Scannable UPI Payment QR Code for $merchantName",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .padding(10.dp)
                                                    .size(160.dp)
                                                    .testTag("checkout_upi_qr_image")
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.size(168.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "QR unavailable — use Pay with UPI App below",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Scan & Pay ${inrFormatter.format(totalAmount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "UPI ID: $merchantUpiId\nVerified Merchant: $merchantName",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    checkoutErrorMessage = null
                                    val launched = runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiPaymentUri))
                                        context.startActivity(intent)
                                    }
                                    if (launched.isFailure) {
                                        checkoutErrorMessage =
                                            "No UPI app installed on this device. Scan the QR code above from another UPI phone or use Debit/Credit Card."
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("open_upi_app_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pay with UPI App (${inrFormatter.format(totalAmount)})")
                            }

                            OutlinedTextField(
                                value = utrInput,
                                onValueChange = { raw ->
                                    utrInput = raw.filter { ch -> ch.isDigit() }.take(12)
                                    checkoutErrorMessage = null
                                },
                                label = { Text("12-Digit UPI UTR / Transaction Reference") },
                                placeholder = { Text("e.g. 432198765432") },
                                supportingText = {
                                    Text("${utrInput.length}/12 digits entered from your UPI payment receipt")
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("upi_utr_input"),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                color = if (gatewayKeyId.isNotBlank()) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (gatewayKeyId.isNotBlank()) {
                                        "Live Gateway Connected (${gatewayKeyId.take(14)}...) • Card checkout enabled"
                                    } else {
                                        "Sandbox / Test Card Checkout Active (Configure Payment Gateway Key in App Settings for live capture)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            OutlinedTextField(
                                value = cardNumberInput,
                                onValueChange = { raw ->
                                    cardNumberInput = raw.filter { ch -> ch.isDigit() }.take(16)
                                    checkoutErrorMessage = null
                                },
                                label = { Text("Debit / Credit Card Number") },
                                placeholder = { Text("4242 4242 4242 4242") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_number_input"),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = cardExpiryInput,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(4)
                                        cardExpiryInput = when {
                                            digits.length > 2 -> "${digits.substring(0, 2)}/${digits.substring(2)}"
                                            else -> digits
                                        }
                                        checkoutErrorMessage = null
                                    },
                                    label = { Text("Expiry (MM/YY)") },
                                    placeholder = { Text("12/28") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("card_expiry_input"),
                                )
                                OutlinedTextField(
                                    value = cardCvvInput,
                                    onValueChange = { raw ->
                                        cardCvvInput = raw.filter { ch -> ch.isDigit() }.take(4)
                                        checkoutErrorMessage = null
                                    },
                                    label = { Text("CVV") },
                                    placeholder = { Text("123") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("card_cvv_input"),
                                )
                            }
                            OutlinedTextField(
                                value = cardHolderInput,
                                onValueChange = { raw ->
                                    cardHolderInput = raw.take(60)
                                    checkoutErrorMessage = null
                                },
                                label = { Text("Cardholder Name") },
                                placeholder = { Text("Full Name on Card") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_holder_input"),
                            )
                        }
                    }

                    if (checkoutErrorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = checkoutErrorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        if (checkoutPaymentTab == "upi") {
                            if (!SubscriptionReceiptPdfPrinter.isValidUpiUtr(utrInput)) {
                                checkoutErrorMessage = "Enter the exact 12-digit UPI UTR / Transaction Reference number."
                                return@Button
                            }
                        } else {
                            if (!SubscriptionReceiptPdfPrinter.isValidCardNumber(cardNumberInput)) {
                                checkoutErrorMessage = "Please enter a valid 13–19 digit debit or credit card number."
                                return@Button
                            }
                            if (!SubscriptionReceiptPdfPrinter.isValidCardExpiry(cardExpiryInput)) {
                                checkoutErrorMessage = "Enter a valid unexpired expiry date in MM/YY format."
                                return@Button
                            }
                            if (!SubscriptionReceiptPdfPrinter.isValidCardCvv(cardCvvInput)) {
                                checkoutErrorMessage = "Please enter a valid 3 or 4 digit CVV."
                                return@Button
                            }
                            if (!SubscriptionReceiptPdfPrinter.isValidCardHolderName(cardHolderInput)) {
                                checkoutErrorMessage = "Please enter the cardholder's full name."
                                return@Button
                            }
                        }

                        val tier = currentCheckoutTier
                        val cycle = checkoutCycle
                        val paymentLabel = if (checkoutPaymentTab == "upi") {
                            "UPI (UTR: ${utrInput.trim()})"
                        } else {
                            val modeTag = if (gatewayKeyId.isNotBlank()) "Gateway" else "Sandbox Card"
                            "$modeTag (•••• ${cardNumberInput.takeLast(4)})"
                        }
                        val txRef = if (checkoutPaymentTab == "upi") {
                            utrInput.trim()
                        } else {
                            "CARD-${System.currentTimeMillis().toString().takeLast(8)}"
                        }

                        checkoutErrorMessage = null
                        onActivatePlan(tier, cycle) {
                            val generatedReceipt = SubscriptionReceiptPdfPrinter.createReceipt(
                                tier = tier,
                                billingCycle = cycle,
                                amountPaidInr = totalAmount,
                                paymentMethodLabel = paymentLabel,
                                transactionRef = txRef,
                                merchantName = merchantName,
                                merchantUpiId = merchantUpiId,
                                subscriberBusinessName = businessName,
                                signedInUserEmail = userEmail
                            )
                            checkoutTier = null
                            activeReceipt = generatedReceipt

                            coroutineScope.launch {
                                val cachedPdfResult = withContext(Dispatchers.IO) {
                                    runCatching {
                                        SubscriptionReceiptPdfPrinter.saveReceiptPdfToCacheAndGetUri(
                                            context = context,
                                            receipt = generatedReceipt
                                        ).second
                                    }
                                }
                                val pdfUri = cachedPdfResult.getOrNull()
                                activeReceiptPdfUri = pdfUri
                                if (pdfUri != null) {
                                    val emailLaunched = runCatching {
                                        val emailIntent = SubscriptionReceiptPdfPrinter.buildSubscriptionEmailIntent(
                                            context = context,
                                            receipt = generatedReceipt,
                                            pdfUri = pdfUri
                                        )
                                        context.startActivity(emailIntent)
                                    }
                                    emailDispatchNotice = if (emailLaunched.isSuccess) {
                                        "Congratulations! GST Receipt PDF generated & auto-dispatched to ${generatedReceipt.subscriberEmail}" +
                                            (if (!generatedReceipt.ccEmail.isNullOrBlank()) " & CC ${generatedReceipt.ccEmail}" else "")
                                    } else {
                                        "PDF receipt is ready! No email client found on this device — tap 'Download PDF' below to save it."
                                    }
                                } else {
                                    emailDispatchNotice = "Plan activated! Tap 'Download PDF' below to export your GST Subscription Receipt."
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_payment_checkout_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Activating...")
                    } else {
                        Text("Verify & Activate Plan (${inrFormatter.format(totalAmount)})")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = { checkoutTier = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Congratulations & GST Subscription Tax Receipt Dialog (Strictly NO bank account or IFSC shown)
    val receipt = activeReceipt
    if (receipt != null) {
        val cycleDisplay = if (receipt.billingCycle == SubscriptionTier.CYCLE_YEARLY) "Yearly" else "Monthly"
        AlertDialog(
            onDismissRequest = {
                activeReceipt = null
                activeReceiptPdfUri = null
                emailDispatchNotice = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Congratulations! Plan Now Active",
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("subscription_receipt_dialog_title")
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Your Finboss ${receipt.tier.displayName} ($cycleDisplay) plan is now active until ${dateFormat.format(Date(receipt.validUntilMs))}.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (emailDispatchNotice != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = emailDispatchNotice.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Receipt No.: ${receipt.receiptNumber}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Amount Paid: ${inrFormatter.format(receipt.amountPaidInr)} (Taxable ${decimalInrFormatter.format(receipt.taxableValueInr)} + 18% GST ${decimalInrFormatter.format(receipt.gstAmountInr)})",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Method: ${receipt.paymentMethodLabel}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Verified Merchant: ${receipt.merchantName} (UPI: ${receipt.merchantUpiId})",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Subscriber: ${receipt.subscriberBusinessName} (${receipt.subscriberEmail})",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (!receipt.ccEmail.isNullOrBlank()) {
                                Text(
                                    text = "Admin Receipt Copy: ${receipt.ccEmail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                saveReceiptPdfLauncher.launch("${receipt.receiptNumber}.pdf")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("download_subscription_receipt_pdf_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download PDF")
                        }

                        OutlinedButton(
                            onClick = {
                                val pdfUri = activeReceiptPdfUri
                                if (pdfUri != null) {
                                    val resendResult = runCatching {
                                        val intent = SubscriptionReceiptPdfPrinter.buildSubscriptionEmailIntent(
                                            context = context,
                                            receipt = receipt,
                                            pdfUri = pdfUri
                                        )
                                        context.startActivity(intent)
                                    }
                                    if (resendResult.isFailure) {
                                        Toast.makeText(
                                            context,
                                            "No email app configured on this device",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            enabled = activeReceiptPdfUri != null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("resend_subscription_receipt_email_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Email")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeReceipt = null
                        activeReceiptPdfUri = null
                        emailDispatchNotice = null
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("subscription_receipt_done_button")
                ) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun UpgradePlanRequiredDialog(
    featureTitle: String,
    featureDescription: String,
    currentPlanLabel: String,
    recommendedTier: SubscriptionTier = SubscriptionTier.DIAMOND,
    onViewPlans: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Feature Locked",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        },
        title = {
            Text(
                text = "Upgrade Required: $featureTitle",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = featureDescription,
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Current Active Plan: $currentPlanLabel",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Recommended: ${recommendedTier.displayName} (₹${recommendedTier.monthlyPriceInr}/mo or ₹${recommendedTier.yearlyTotalInr}/yr with ${recommendedTier.yearlyDiscountPercent}% Yearly Discount)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onViewPlans()
                },
                modifier = Modifier.testTag("upgrade_dialog_view_plans_button")
            ) {
                Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("View Plans & Upgrade")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later")
            }
        }
    )
}
