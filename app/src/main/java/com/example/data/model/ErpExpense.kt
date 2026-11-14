package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ErpExpense(
  @DocumentId val id: String = "",
  val userId: String = "",
  val businessProfileId: String = "",
  val title: String = "",
  val category: String = CAT_OTHER,
  val paymentMethod: String = METHOD_UPI,
  val status: String = STATUS_SETTLED,
  val partyId: String = "",
  val partyName: String = "",
  val referenceNo: String = "",
  val amount: Double = 0.0,
  val gstInputAmount: Double = 0.0,
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedProfileId(): String = businessProfileId.ifBlank { BusinessProfile.SLOT_MAIN_1 }

  companion object {
    const val CAT_RENT = "rent"
    const val CAT_PAYROLL = "payroll"
    const val CAT_UTILITIES = "utilities"
    const val CAT_PURCHASES = "purchases"
    const val CAT_TRANSPORT = "transport"
    const val CAT_MARKETING = "marketing"
    const val CAT_TAX = "tax"
    const val CAT_OTHER = "other"

    val ALL_CATEGORIES =
      listOf(
        CAT_RENT to "Rent",
        CAT_PAYROLL to "Payroll",
        CAT_UTILITIES to "Utilities",
        CAT_PURCHASES to "Purchases",
        CAT_TRANSPORT to "Transport",
        CAT_MARKETING to "Marketing",
        CAT_TAX to "Taxes & Fees",
        CAT_OTHER to "Other",
      )

    const val METHOD_CASH = "cash"
    const val METHOD_UPI = "upi"
    const val METHOD_BANK = "bank"
    const val METHOD_CARD = "card"

    const val STATUS_SETTLED = "settled"
    const val STATUS_PENDING = "pending"
  }
}
