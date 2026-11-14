package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class BusinessProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val ownerName: String = "",
  val ownerEmail: String = "",
  val businessName: String = "",
  val gstin: String = "",
  val address: String = "",
  val state: String = "",
  val pincode: String = "",
  val currencySymbol: String = "₹",
  val fiscalYearLabel: String = "FY 2026-27",
  val businessType: String = "Retail & Wholesale",
  val monthlySalesTarget: Double = 250000.0,
  val profileMode: String = MODE_MAIN_GST,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val resolvedSlotId: String
    get() = id.ifBlank { SLOT_MAIN_1 }

  val isPlainNonGst: Boolean
    get() = profileMode == MODE_PLAIN_NON_GST || id == SLOT_PLAIN_1

  companion object {
    const val SLOT_MAIN_1 = "main_1"
    const val SLOT_MAIN_2 = "main_2"
    const val SLOT_PLAIN_1 = "plain_1"
    val ALL_SLOTS = listOf(SLOT_MAIN_1, SLOT_MAIN_2, SLOT_PLAIN_1)

    const val MODE_MAIN_GST = "main_gst"
    const val MODE_PLAIN_NON_GST = "plain_non_gst"

    fun modeForSlot(slotId: String): String =
      if (slotId == SLOT_PLAIN_1) MODE_PLAIN_NON_GST else MODE_MAIN_GST
  }
}
