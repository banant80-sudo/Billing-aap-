package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class DashboardEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val businessProfileId: String = "",
  val title: String = "",
  val partyName: String = "",
  val category: String = CATEGORY_SALE,
  val amount: Double = 0.0,
  val status: String = STATUS_PAID,
  val referenceCode: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedProfileId(): String = businessProfileId.ifBlank { BusinessProfile.SLOT_MAIN_1 }

  companion object {
    const val CATEGORY_SALE = "sale"
    const val CATEGORY_EXPENSE = "expense"
    const val CATEGORY_RECEIVABLE = "receivable"

    const val STATUS_PAID = "paid"
    const val STATUS_PENDING = "pending"
  }
}
