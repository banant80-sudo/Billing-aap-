package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class InvoiceLineItem(
  val itemId: String = "",
  val name: String = "",
  val sku: String = "",
  val hsnCode: String = "",
  val unit: String = "PCS",
  val qty: Double = 1.0,
  val unitPrice: Double = 0.0,
  val gstRatePercent: Double = 18.0,
  val taxableAmount: Double = 0.0,
  val gstAmount: Double = 0.0,
  val lineTotal: Double = 0.0,
) {
  fun toFirestoreMap(): Map<String, Any> =
    mapOf(
      "itemId" to itemId,
      "name" to name,
      "sku" to sku,
      "hsnCode" to hsnCode,
      "unit" to unit,
      "qty" to qty,
      "unitPrice" to unitPrice,
      "gstRatePercent" to gstRatePercent,
      "taxableAmount" to taxableAmount,
      "gstAmount" to gstAmount,
      "lineTotal" to lineTotal,
    )
}

@IgnoreExtraProperties
data class GstInvoice(
  @DocumentId val id: String = "",
  val userId: String = "",
  val businessProfileId: String = "",
  val invoiceNumber: String = "",
  val partyId: String = "",
  val partyName: String = "Walk-in Customer",
  val partyGstin: String = "",
  val partyAddress: String = "",
  val partyState: String = "",
  val partyPincode: String = "",
  val invoiceType: String = TYPE_TAX_INVOICE, // tax_invoice, pos_bill
  val taxMode: String = TAX_MODE_CGST_SGST, // cgst_sgst, igst, none
  val paymentMethod: String = PAYMENT_UPI, // cash, upi, card, credit
  val status: String = STATUS_PAID, // paid, partial, unpaid
  val subtotal: Double = 0.0,
  val gstTotal: Double = 0.0,
  val discountAmount: Double = 0.0,
  val grandTotal: Double = 0.0,
  val paidAmount: Double = 0.0,
  val notes: String = "",
  val lineItems: List<InvoiceLineItem> = emptyList(),
  // GST Portal E-Invoice (IRP) fields
  val irn: String = "",
  val ackNo: String = "",
  val ackDate: String = "",
  val signedQrData: String = "",
  val einvoiceStatus: String = "", // "", "generated", "cancelled"
  // GST E-Way Bill & Transport details
  val ewbNumber: String = "",
  val ewbDate: String = "",
  val ewbValidUntil: String = "",
  val transportMode: String = "", // "", "road", "rail", "air", "ship"
  val distanceKm: Int = 0,
  val fromPlace: String = "",
  val fromPincode: String = "",
  val toPlace: String = "",
  val toPincode: String = "",
  val vehicleNumber: String = "",
  val vehicleType: String = "", // "", "regular", "odc"
  val transporterName: String = "",
  val transporterId: String = "",
  val transporterDocNo: String = "",
  val transporterDocDate: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedProfileId(): String =
    businessProfileId.ifBlank { BusinessProfile.SLOT_MAIN_1 }

  val balanceDue: Double
    get() = (grandTotal - paidAmount).coerceAtLeast(0.0)

  val cgstAmount: Double
    get() = if (taxMode == TAX_MODE_CGST_SGST) gstTotal / 2.0 else 0.0

  val sgstAmount: Double
    get() = if (taxMode == TAX_MODE_CGST_SGST) gstTotal / 2.0 else 0.0

  val igstAmount: Double
    get() = if (taxMode == TAX_MODE_IGST) gstTotal else 0.0

  val hasEInvoice: Boolean
    get() = irn.isNotBlank() && einvoiceStatus != "cancelled"

  val hasEWayBill: Boolean
    get() = ewbNumber.isNotBlank()

  companion object {
    const val TYPE_TAX_INVOICE = "tax_invoice"
    const val TYPE_POS_BILL = "pos_bill"

    const val TAX_MODE_CGST_SGST = "cgst_sgst"
    const val TAX_MODE_IGST = "igst"
    const val TAX_MODE_NONE = "none"

    const val PAYMENT_CASH = "cash"
    const val PAYMENT_UPI = "upi"
    const val PAYMENT_CARD = "card"
    const val PAYMENT_CREDIT = "credit"

    const val STATUS_PAID = "paid"
    const val STATUS_PARTIAL = "partial"
    const val STATUS_UNPAID = "unpaid"

    const val TRANSPORT_ROAD = "road"
    const val TRANSPORT_RAIL = "rail"
    const val TRANSPORT_AIR = "air"
    const val TRANSPORT_SHIP = "ship"

    const val VEHICLE_REGULAR = "regular"
    const val VEHICLE_ODC = "odc"
  }
}
