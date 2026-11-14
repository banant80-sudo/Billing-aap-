package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.SubscriptionStatus
import com.example.data.model.SubscriptionTier
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionPlansSheet(
    status: SubscriptionStatus,
    isOwnerSession: Boolean,
    onActivatePlan: (tier: SubscriptionTier, billingCycle: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedBillingCycle by remember {
        mutableStateOf(if (status.billingCycle == SubscriptionTier.CYCLE_MONTHLY) "monthly" else "yearly")
    }
    
    // Checkout Modal State
    var checkoutTier by remember { mutableStateOf<SubscriptionTier?>(null) }
    var checkoutCycle by remember { mutableStateOf("monthly") }
    var checkoutPaymentTab by remember { mutableStateOf("upi") } // "upi" or "card"
    var utrInput by remember { mutableStateOf("") }
    var cardNumberInput by remember { mutableStateOf("") }
    var cardExpiryInput by remember { mutableStateOf("") }
    var cardCvvInput by remember { mutableStateOf("") }
    var cardHolderInput by remember { mutableStateOf("Raghav Enterprises") }
    var checkoutSuccessReceipt by remember { mutableStateOf<String?>(null) }
    var checkoutErrorMessage by remember { mutableStateOf<String?>(null) }

    val inrFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
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
                                    checkoutErrorMessage = null
                                },
                                enabled = !isCurrentTier,
                                modifier = Modifier
                                    .fillMaxWidth()
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
                    onClick = { onActivatePlan(SubscriptionTier.TRIAL, "trial") },
                    modifier = Modifier
                        .fillMaxWidth()
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

    // Subscription Payment Checkout Modal Dialog
    val currentCheckoutTier = checkoutTier
    if (currentCheckoutTier != null) {
        val isYearly = checkoutCycle == "yearly"
        val totalAmount = if (isYearly) currentCheckoutTier.yearlyTotalInr else currentCheckoutTier.monthlyPriceInr

        AlertDialog(
            onDismissRequest = { checkoutTier = null },
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                text = "Amount Payable: ${inrFormatter.format(totalAmount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Beneficiary: Raghav Enterprises • Verified Merchant",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Payment Method Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { checkoutPaymentTab = "upi" },
                            modifier = Modifier.weight(1f),
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
                            onClick = { checkoutPaymentTab = "card" },
                            modifier = Modifier.weight(1f),
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
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode2,
                                        contentDescription = "UPI QR Code",
                                        modifier = Modifier.size(96.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Scan & Pay ${inrFormatter.format(totalAmount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "UPI ID: anantbharat@ybl\nBeneficiary: Raghav Enterprises",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    try {
                                        val upiUri = Uri.parse(
                                            "upi://pay?pa=anantbharat@ybl&pn=Raghav%20Enterprises&am=$totalAmount&cu=INR"
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW, upiUri)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Fallback if no UPI app
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Open UPI App to Pay ${inrFormatter.format(totalAmount)}")
                            }

                            OutlinedTextField(
                                value = utrInput,
                                onValueChange = { utrInput = it.filter { ch -> ch.isDigit() || ch.isLetter() }.take(20) },
                                label = { Text("12-Digit UPI UTR / Transaction Reference") },
                                placeholder = { Text("e.g. 432198765432") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("upi_utr_input"),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = cardNumberInput,
                                onValueChange = { cardNumberInput = it.filter { ch -> ch.isDigit() }.take(16) },
                                label = { Text("Debit / Credit Card Number") },
                                placeholder = { Text("4242 4242 4242 4242") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = cardExpiryInput,
                                    onValueChange = { cardExpiryInput = it.take(5) },
                                    label = { Text("Expiry (MM/YY)") },
                                    placeholder = { Text("12/28") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = cardCvvInput,
                                    onValueChange = { cardCvvInput = it.filter { ch -> ch.isDigit() }.take(4) },
                                    label = { Text("CVV") },
                                    placeholder = { Text("123") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            OutlinedTextField(
                                value = cardHolderInput,
                                onValueChange = { cardHolderInput = it.take(60) },
                                label = { Text("Cardholder Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
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
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (checkoutPaymentTab == "upi" && utrInput.trim().length < 6) {
                            checkoutErrorMessage = "Please enter a valid 12-digit UPI UTR / Transaction Reference."
                            return@Button
                        }
                        if (checkoutPaymentTab == "card" && cardNumberInput.length < 12) {
                            checkoutErrorMessage = "Please enter a valid card number."
                            return@Button
                        }

                        val tier = currentCheckoutTier
                        val cycle = checkoutCycle
                        checkoutTier = null
                        onActivatePlan(tier, cycle)
                        checkoutSuccessReceipt = "GST Subscription Receipt • ${tier.displayName} (${cycle.uppercase()})\n" +
                            "Amount Paid: ${inrFormatter.format(totalAmount)}\n" +
                            "Merchant: Raghav Enterprises (UPI: anantbharat@ybl)\n" +
                            "Confirmation Email auto-sent to: banant80@gmail.com\n" +
                            "Status: SUCCESSFUL"
                    },
                    modifier = Modifier.testTag("confirm_payment_checkout_button")
                ) {
                    Text("Verify & Activate Plan (${inrFormatter.format(totalAmount)})")
                }
            },
            dismissButton = {
                TextButton(onClick = { checkoutTier = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Success Receipt & Email Confirmation Dialog
    val receiptText = checkoutSuccessReceipt
    if (receiptText != null) {
        AlertDialog(
            onDismissRequest = { checkoutSuccessReceipt = null },
            icon = {
                Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("Subscription Activated Successfully!", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Congratulations! Your plan is now active.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = receiptText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { checkoutSuccessReceipt = null }) {
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
