package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class InventoryItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val businessProfileId: String = "",
  val name: String = "",
  val sku: String = "",
  val hsnCode: String = "",
  val category: String = "General",
  val unit: String = "PCS",
  val salePrice: Double = 0.0,
  val purchasePrice: Double = 0.0,
  val gstRatePercent: Double = 18.0,
  val stockQty: Double = 0.0,
  val lowStockThreshold: Double = 5.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedProfileId(): String = businessProfileId.ifBlank { BusinessProfile.SLOT_MAIN_1 }

  val isOutOfStock: Boolean
    get() = stockQty <= 0.0

  val isLowStock: Boolean
    get() = stockQty > 0.0 && stockQty <= lowStockThreshold

  val stockValuation: Double
    get() = (stockQty * purchasePrice).coerceAtLeast(0.0)
}
