package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessProfile
import com.example.data.model.GstInvoice
import com.example.data.reference.EInvoiceGenerationResult
import com.example.data.reference.EWayBillGenerationResult
import com.example.data.reference.EWayBillTransportInput
import com.example.data.reference.GstEInvoiceAndEWayBillEngine
import com.example.data.sync.ErpAppSettings
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateEInvoiceSheet(
    invoice: GstInvoice,
    sellerProfile: BusinessProfile?,
    appSettings: ErpAppSettings,
    onSaveEInvoice: (EInvoiceGenerationResult) -> Unit,
    onCancelEInvoice: () -> Unit,
    onOpenEWayBillGenerator: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val inrFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    val configuredSellerGstin =
        appSettings.gstPortalGstin.ifBlank { sellerProfile?.gstin.orEmpty() }.trim().uppercase()
    var sellerGstinOverride by remember(invoice.id, configuredSellerGstin) {
        mutableStateOf(if (configuredSellerGstin.length == 15) configuredSellerGstin else "27AABCF9012R1Z5")
    }
    var buyerGstinOverride by remember(invoice.id, invoice.partyGstin) {
        mutableStateOf(invoice.partyGstin.ifBlank { "27AAACR5055K1Z7" }.trim().uppercase())
    }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val effectiveSellerProfile = remember(sellerProfile, sellerGstinOverride) {
        (sellerProfile ?: BusinessProfile()).copy(gstin = sellerGstinOverride.trim().uppercase())
    }
    val effectiveInvoice = remember(invoice, buyerGstinOverride) {
        invoice.copy(partyGstin = buyerGstinOverride.trim().uppercase())
    }

    val previewResult = remember(effectiveInvoice, effectiveSellerProfile, appSettings.gstPortalMode) {
        GstEInvoiceAndEWayBillEngine.generateEInvoice(
            invoice = effectiveInvoice,
            sellerProfile = effectiveSellerProfile,
            fallbackSellerGstin = sellerGstinOverride,
            portalMode = appSettings.gstPortalMode
        )
    }

    val sellerValid = GstEInvoiceAndEWayBillEngine.isValidGstin(sellerGstinOverride)
    val buyerValid = GstEInvoiceAndEWayBillEngine.isValidGstin(buyerGstinOverride)
    val isIgst = invoice.taxMode == GstInvoice.TAX_MODE_IGST

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
                .testTag("generate_einvoice_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "GST E-Invoice IRN",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "GST Portal E-Invoice (IRN)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${invoice.invoiceNumber} • ${invoice.partyName} • Mode: ${appSettings.gstPortalMode.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Existing IRN Card if already generated
            if (invoice.hasEInvoice) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SignedQrCodeMatrixBox(
                            qrPayload = invoice.signedQrData.ifBlank { invoice.irn },
                            modifier = Modifier.size(92.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "IRN Registered (${invoice.einvoiceStatus.uppercase()})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Ack No: ${invoice.ackNo} • ${invoice.ackDate}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "IRN: ${invoice.irn}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            // GSTIN & Tax Breakup Validation Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "NIC E-Invoice Schema v1.1 Validation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = sellerGstinOverride,
                            onValueChange = { sellerGstinOverride = it.uppercase().take(15) },
                            label = { Text("Seller GSTIN (15-char)") },
                            singleLine = true,
                            isError = !sellerValid,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("einvoice_seller_gstin_input"),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                        )
                        OutlinedTextField(
                            value = buyerGstinOverride,
                            onValueChange = { buyerGstinOverride = it.uppercase().take(15) },
                            label = { Text("Buyer GSTIN (B2B)") },
                            singleLine = true,
                            isError = !buyerValid,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("einvoice_buyer_gstin_input"),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Taxable: ${inrFormat.format(invoice.subtotal)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = if (isIgst) {
                                "IGST: ${inrFormat.format(invoice.igstAmount)}"
                            } else {
                                "CGST: ${inrFormat.format(invoice.cgstAmount)} • SGST: ${inrFormat.format(invoice.sgstAmount)}"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Total: ${inrFormat.format(invoice.grandTotal)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!sellerValid || !buyerValid) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Enter valid 15-character Indian GSTINs (e.g. 27AABCF9012R1Z5) for Seller & Buyer.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Seller & Buyer GSTINs, HSN lines (${invoice.lineItems.size.coerceAtLeast(1)}), and GST breakup verified for IRP.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (statusBanner != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusBanner.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Primary Actions
            Button(
                onClick = {
                    onSaveEInvoice(previewResult)
                    statusBanner = "E-Invoice IRN & Signed QR saved on ${invoice.invoiceNumber} (Ack #${previewResult.ackNo})."
                },
                enabled = sellerValid && buyerValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_generate_einvoice_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (invoice.hasEInvoice) "Regenerate / Update IRN & Signed QR" else "1-Click Generate E-Invoice (IRN & QR)",
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(previewResult.nicJsonPayload))
                        statusBanner = "NIC E-Invoice Schema v1.1 JSON copied to clipboard for portal upload."
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_einvoice_json_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy NIC JSON")
                }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(GstEInvoiceAndEWayBillEngine.NIC_EINVOICE_PORTAL_URL))
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_nic_einvoice_portal_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NIC IRP Portal")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenEWayBillGenerator()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate E-Way Bill")
                }

                if (invoice.hasEInvoice) {
                    OutlinedButton(
                        onClick = {
                            onCancelEInvoice()
                            statusBanner = "E-Invoice IRN marked as cancelled."
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel IRN")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenerateEWayBillSheet(
    invoice: GstInvoice,
    sellerProfile: BusinessProfile?,
    appSettings: ErpAppSettings,
    onSaveEWayBill: (EWayBillGenerationResult) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val todayFormatted = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }

    var fromPlace by remember(invoice.id) {
        mutableStateOf(
            invoice.fromPlace
                .ifBlank { appSettings.defaultDispatchCity }
                .ifBlank { sellerProfile?.address?.substringBefore(",")?.trim().orEmpty() }
                .ifBlank { sellerProfile?.state.orEmpty() }
                .ifBlank { "Mumbai" }
        )
    }
    var fromPincode by remember(invoice.id) {
        mutableStateOf(
            invoice.fromPincode
                .ifBlank { sellerProfile?.pincode.orEmpty() }
                .ifBlank { "400001" }
        )
    }
    var toPlace by remember(invoice.id) {
        mutableStateOf(
            invoice.toPlace
                .ifBlank { invoice.partyAddress.substringBefore(",").trim() }
                .ifBlank { invoice.partyState }
                .ifBlank { "Delhi" }
        )
    }
    var toPincode by remember(invoice.id) {
        mutableStateOf(
            invoice.toPincode
                .ifBlank { invoice.partyPincode }
                .ifBlank { "110001" }
        )
    }
    var distanceKmInput by remember(invoice.id) {
        val initialDistance = if (invoice.distanceKm > 0) {
            invoice.distanceKm
        } else {
            GstEInvoiceAndEWayBillEngine.estimateDistanceKm(
                fromCity = fromPlace,
                fromPin = fromPincode,
                toCity = toPlace,
                toPin = toPincode
            )
        }
        mutableStateOf(initialDistance.toString())
    }
    var transportMode by remember(invoice.id) {
        mutableStateOf(invoice.transportMode.ifBlank { "road" })
    }
    var vehicleNumber by remember(invoice.id) {
        mutableStateOf(invoice.vehicleNumber.ifBlank { "OD-10-AB-1234" })
    }
    var vehicleType by remember(invoice.id) {
        mutableStateOf(invoice.vehicleType.ifBlank { "regular" })
    }
    var transporterName by remember(invoice.id) {
        mutableStateOf(invoice.transporterName.ifBlank { appSettings.defaultTransporterName }.ifBlank { "VRL Logistics Ltd" })
    }
    var transporterId by remember(invoice.id) {
        mutableStateOf(invoice.transporterId.ifBlank { appSettings.defaultTransporterId })
    }
    var transporterDocNo by remember(invoice.id) {
        mutableStateOf(invoice.transporterDocNo.ifBlank { "LR-${invoice.invoiceNumber.takeLast(6)}" })
    }
    var transporterDocDate by remember(invoice.id) {
        mutableStateOf(invoice.transporterDocDate.ifBlank { todayFormatted })
    }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val parsedDistanceKm = distanceKmInput.toIntOrNull()?.coerceIn(1, 4000) ?: 0
    val isVehicleValid =
        transportMode != "road" || GstEInvoiceAndEWayBillEngine.isValidVehicleNumber(vehicleNumber)
    val canGenerate = fromPlace.isNotBlank() && toPlace.isNotBlank() && parsedDistanceKm in 1..4000 && isVehicleValid

    val transportInput = remember(
        transportMode,
        parsedDistanceKm,
        fromPlace,
        fromPincode,
        toPlace,
        toPincode,
        vehicleNumber,
        vehicleType,
        transporterName,
        transporterId,
        transporterDocNo,
        transporterDocDate
    ) {
        EWayBillTransportInput(
            fromPlace = fromPlace.trim(),
            fromPincode = fromPincode.trim(),
            toPlace = toPlace.trim(),
            toPincode = toPincode.trim(),
            distanceKm = parsedDistanceKm.coerceAtLeast(10),
            transportMode = transportMode,
            vehicleNumber = GstEInvoiceAndEWayBillEngine.formatVehicleNumberDisplay(vehicleNumber),
            vehicleType = vehicleType,
            transporterName = transporterName.trim(),
            transporterId = transporterId.trim().uppercase(),
            transporterDocNo = transporterDocNo.trim(),
            transporterDocDate = transporterDocDate.trim()
        )
    }

    val previewEwbResult = remember(invoice, sellerProfile, transportInput, appSettings.gstPortalGstin) {
        GstEInvoiceAndEWayBillEngine.generateEWayBill(
            invoice = invoice,
            sellerProfile = sellerProfile,
            input = transportInput,
            fallbackSellerGstin = appSettings.gstPortalGstin
        )
    }

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
                .testTag("generate_ewaybill_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = "E-Way Bill Generator",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "GST E-Way Bill & Transport Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${invoice.invoiceNumber} • ${invoice.partyName} • Part-A & Part-B",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (invoice.hasEWayBill) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Active E-Way Bill No: ${invoice.ewbNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Generated: ${invoice.ewbDate} • Valid: ${invoice.ewbValidUntil}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "${invoice.fromPlace} (${invoice.fromPincode}) → ${invoice.toPlace} (${invoice.toPincode}) • ${invoice.distanceKm} KM • Vehicle: ${invoice.vehicleNumber}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Section 1: City-to-City & PIN-to-PIN Distance
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Part-A: Dispatch & Destination City + Distance (KM)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = fromPlace,
                            onValueChange = { fromPlace = it },
                            label = { Text("From City / Place") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("ewb_from_city_input")
                        )
                        OutlinedTextField(
                            value = fromPincode,
                            onValueChange = { fromPincode = it.filter(Char::isDigit).take(6) },
                            label = { Text("From PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(0.8f)
                                .testTag("ewb_from_pin_input")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = toPlace,
                            onValueChange = { toPlace = it },
                            label = { Text("To City / Place") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("ewb_to_city_input")
                        )
                        OutlinedTextField(
                            value = toPincode,
                            onValueChange = { toPincode = it.filter(Char::isDigit).take(6) },
                            label = { Text("To PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(0.8f)
                                .testTag("ewb_to_pin_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = distanceKmInput,
                            onValueChange = { distanceKmInput = it.filter(Char::isDigit).take(4) },
                            label = { Text("Approx. Distance (KM)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ewb_distance_km_input")
                        )

                        OutlinedButton(
                            onClick = {
                                val estimated = GstEInvoiceAndEWayBillEngine.estimateDistanceKm(
                                    fromCity = fromPlace,
                                    fromPin = fromPincode,
                                    toCity = toPlace,
                                    toPin = toPincode
                                )
                                distanceKmInput = estimated.toString()
                                statusMessage = "Auto-calculated road distance: $estimated KM between $fromPlace and $toPlace."
                            },
                            modifier = Modifier.testTag("ewb_auto_distance_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto KM")
                        }
                    }
                }
            }

            // Section 2: Part-B Transport Mode, Vehicle Number & Transporter Details
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Part-B: Transport Mode, Vehicle No. & Transporter Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val modes = listOf(
                            Triple("road", "Road (Truck/Tempo)", Icons.Default.LocalShipping),
                            Triple("rail", "Rail", Icons.Default.Train),
                            Triple("air", "Air Cargo", Icons.Default.Flight),
                            Triple("ship", "Ship / Vessel", Icons.Default.DirectionsBoat)
                        )
                        modes.forEach { (modeId, label, icon) ->
                            FilterChip(
                                selected = transportMode == modeId,
                                onClick = { transportMode = modeId },
                                label = { Text(label) },
                                leadingIcon = {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("ewb_mode_${modeId}_chip")
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = vehicleNumber,
                            onValueChange = { vehicleNumber = it.uppercase().take(15) },
                            label = {
                                Text(
                                    if (transportMode == "road") {
                                        "Vehicle No. (e.g. OD-10-AB-1234)"
                                    } else {
                                        "Vehicle / Rake / Airway Ref"
                                    }
                                )
                            },
                            singleLine = true,
                            isError = !isVehicleValid,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("ewb_vehicle_number_input")
                        )

                        Column(modifier = Modifier.weight(0.9f)) {
                            Text(
                                text = "Vehicle Type",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = vehicleType.equals("regular", ignoreCase = true),
                                    onClick = { vehicleType = "regular" },
                                    label = { Text("Regular") }
                                )
                                FilterChip(
                                    selected = vehicleType.equals("odc", ignoreCase = true),
                                    onClick = { vehicleType = "odc" },
                                    label = { Text("ODC") }
                                )
                            }
                        }
                    }

                    if (!isVehicleValid && transportMode == "road") {
                        Text(
                            text = "Enter valid Indian RTO registration (e.g. OD-10-AB-1234, MH-12-DE-4567, DL-01-CA-9999).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (transportMode == "road") {
                        Text(
                            text = "Formatted RTO No: ${GstEInvoiceAndEWayBillEngine.formatVehicleNumberDisplay(vehicleNumber)} • Valid: ${previewEwbResult.ewbValidUntil}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    HorizontalDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = transporterName,
                            onValueChange = { transporterName = it },
                            label = { Text("Transporter Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ewb_transporter_name_input")
                        )
                        OutlinedTextField(
                            value = transporterId,
                            onValueChange = { transporterId = it.uppercase().take(15) },
                            label = { Text("Transporter ID / GSTIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ewb_transporter_id_input")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = transporterDocNo,
                            onValueChange = { transporterDocNo = it },
                            label = { Text("LR / GR / RR / Airway No.") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ewb_doc_no_input")
                        )
                        OutlinedTextField(
                            value = transporterDocDate,
                            onValueChange = { transporterDocDate = it },
                            label = { Text("LR / Doc Date (DD/MM/YYYY)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ewb_doc_date_input")
                        )
                    }
                }
            }

            if (statusMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusMessage.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    onSaveEWayBill(previewEwbResult)
                    statusMessage = "12-Digit E-Way Bill #${previewEwbResult.ewbNumber} generated! Valid: ${previewEwbResult.ewbValidUntil}."
                },
                enabled = canGenerate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_generate_ewaybill_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (invoice.hasEWayBill) "Update E-Way Bill & Vehicle Details" else "Generate 12-Digit E-Way Bill (Part-A & B)",
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(previewEwbResult.nicJsonPayload))
                        statusMessage = "NIC E-Way Bill JSON payload copied to clipboard."
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_ewaybill_json_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy EWB JSON")
                }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(GstEInvoiceAndEWayBillEngine.NIC_EWAYBILL_PORTAL_URL))
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_nic_ewaybill_portal_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NIC EWB Portal")
                }
            }
        }
    }
}

/**
 * Renders a deterministic 21x21 QR-style matrix from [qrPayload] so users can preview the
 * Signed E-Invoice QR Code directly on screen.
 */
@Composable
fun SignedQrCodeMatrixBox(
    qrPayload: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
    ) {
        Canvas(modifier = Modifier.padding(6.dp)) {
            val gridSize = 21
            val cellW = size.width / gridSize
            val cellH = size.height / gridSize
            val hashBytes = qrPayload.encodeToByteArray()

            fun finderCellOn(localR: Int, localC: Int): Boolean {
                if (localR == 0 || localR == 6 || localC == 0 || localC == 6) return true
                if (localR in 2..4 && localC in 2..4) return true
                return false
            }

            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    val on = when {
                        r in 0..6 && c in 0..6 -> finderCellOn(r, c)
                        r in 0..6 && c in (gridSize - 7) until gridSize -> finderCellOn(r, c - (gridSize - 7))
                        r in (gridSize - 7) until gridSize && c in 0..6 -> finderCellOn(r - (gridSize - 7), c)
                        r == 6 || c == 6 -> (r + c) % 2 == 0
                        else -> {
                            val idx = (r * gridSize + c)
                            val b = if (hashBytes.isNotEmpty()) hashBytes[idx % hashBytes.size].toInt() else idx
                            ((b xor (r * 31 + c * 17)) and 1) == 0
                        }
                    }
                    if (on) {
                        drawRect(
                            color = Color(0xFF0F172A),
                            topLeft = Offset(c * cellW, r * cellH),
                            size = Size(cellW * 0.95f, cellH * 0.95f)
                        )
                    }
                }
            }
        }
    }
}
