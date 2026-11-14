package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ErpModuleConfig(
  @DocumentId val id: String = "",
  val userId: String = "",
  val code: String = "",
  val title: String = "",
  val subtitle: String = "",
  val status: String = STATUS_PLANNED,
  val isPinned: Boolean = false,
  val sortOrder: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  companion object {
    const val STATUS_ACTIVE = "active"
    const val STATUS_QUEUED = "queued"
    const val STATUS_PLANNED = "planned"

    fun defaultRoadmap(userId: String): List<ErpModuleConfig> =
      listOf(
        ErpModuleConfig(
          id = "dashboard",
          userId = userId,
          code = "dashboard",
          title = "Dashboard Overview",
          subtitle = "Live KPIs, cashflow pulse & workspace summary",
          status = STATUS_ACTIVE,
          isPinned = true,
          sortOrder = 0L,
        ),
        ErpModuleConfig(
          id = "billing",
          userId = userId,
          code = "billing",
          title = "Billing & GST Invoices",
          subtitle = "Fast POS billing, GST tax invoices & thermal receipts",
          status = STATUS_ACTIVE,
          isPinned = true,
          sortOrder = 1L,
        ),
        ErpModuleConfig(
          id = "inventory",
          userId = userId,
          code = "inventory",
          title = "Inventory & Stock",
          subtitle = "SKU catalog, low-stock alerts & batch valuation",
          status = STATUS_ACTIVE,
          isPinned = true,
          sortOrder = 2L,
        ),
        ErpModuleConfig(
          id = "parties",
          userId = userId,
          code = "parties",
          title = "Parties & Khata Ledger",
          subtitle = "Customers, suppliers, credit limits & payment reminders",
          status = STATUS_ACTIVE,
          isPinned = true,
          sortOrder = 3L,
        ),
        ErpModuleConfig(
          id = "expenses",
          userId = userId,
          code = "expenses",
          title = "Expenses & Cashbook",
          subtitle = "Operating costs, petty cash & bank reconciliation",
          status = STATUS_ACTIVE,
          isPinned = true,
          sortOrder = 4L,
        ),
        ErpModuleConfig(
          id = "gst_reports",
          userId = userId,
          code = "gst_reports",
          title = "GST & Tax Reports",
          subtitle = "GSTR-1/3B summaries, profit & loss and audit exports",
          status = STATUS_ACTIVE,
          isPinned = true,
          sortOrder = 5L,
        ),
      )
  }
}
