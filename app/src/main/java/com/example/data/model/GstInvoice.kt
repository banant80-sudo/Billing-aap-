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
  val partyName: String = "",
  val partyGstin: String = "",
  val partyAddress: String = "",
  val partyState: String = "",
  val partyPincode: String = "",
  val invoiceType: String = TYPE_TAX_INVOICE,
  val taxMode: String = TAX_MODE_CGST_SGST,
  val paymentMethod: String = PAYMENT_UPI,
  val status: String = STATUS_PAID,
  val subtotal: Double = 0.0,
  val gstTotal: Double = 0.0,
  val discountAmount: Double = 0.0,
  val grandTotal: Double = 0.0,
  val paidAmount: Double = 0.0,
  val notes: String = "",
  val lineItems: List<InvoiceLineItem> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedProfileId(): String = businessProfileId.ifBlank { BusinessProfile.SLOT_MAIN_1 }

  val balanceDue: Double
    get() = (grandTotal - paidAmount).coerceAtLeast(0.0)

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
  }
}
