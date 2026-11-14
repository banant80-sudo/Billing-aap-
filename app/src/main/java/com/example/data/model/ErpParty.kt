package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ErpParty(
  @DocumentId val id: String = "",
  val userId: String = "",
  val businessProfileId: String = "",
  val name: String = "",
  val partyType: String = TYPE_CUSTOMER,
  val phone: String = "",
  val gstin: String = "",
  val address: String = "",
  val state: String = "",
  val pincode: String = "",
  val openingDue: Double = 0.0,
  val totalBilled: Double = 0.0,
  val totalPaid: Double = 0.0,
  val creditLimit: Double = 0.0,
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedProfileId(): String = businessProfileId.ifBlank { BusinessProfile.SLOT_MAIN_1 }

  val outstandingBalance: Double
    get() = (openingDue + totalBilled - totalPaid).coerceAtLeast(0.0)

  val advanceCredit: Double
    get() = (totalPaid - (openingDue + totalBilled)).coerceAtLeast(0.0)

  companion object {
    const val TYPE_CUSTOMER = "customer"
    const val TYPE_SUPPLIER = "supplier"
  }
}
