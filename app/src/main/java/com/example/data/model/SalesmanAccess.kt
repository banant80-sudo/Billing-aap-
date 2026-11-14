package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class SalesmanPermissions(
  val canViewOverview: Boolean = true,
  val canAddQuickEntry: Boolean = false,
  val canViewBilling: Boolean = true,
  val canCreateInvoices: Boolean = true,
  val canEditDeleteInvoices: Boolean = false,
  val canPrintInvoices: Boolean = true,
  val canViewStock: Boolean = true,
  val canManageStock: Boolean = false,
  val canViewParties: Boolean = true,
  val canManageParties: Boolean = false,
  val canViewExpenses: Boolean = false,
  val canManageExpenses: Boolean = false,
  val canViewReports: Boolean = false,
) {
  fun toFirestoreMap(): Map<String, Boolean> =
    mapOf(
      "canViewOverview" to canViewOverview,
      "canAddQuickEntry" to canAddQuickEntry,
      "canViewBilling" to canViewBilling,
      "canCreateInvoices" to canCreateInvoices,
      "canEditDeleteInvoices" to canEditDeleteInvoices,
      "canPrintInvoices" to canPrintInvoices,
      "canViewStock" to canViewStock,
      "canManageStock" to canManageStock,
      "canViewParties" to canViewParties,
      "canManageParties" to canManageParties,
      "canViewExpenses" to canViewExpenses,
      "canManageExpenses" to canManageExpenses,
      "canViewReports" to canViewReports,
    )

  fun enabledCount(): Int = toFirestoreMap().values.count { it }

  companion object {
    val OWNER_FULL =
      SalesmanPermissions(
        canViewOverview = true,
        canAddQuickEntry = true,
        canViewBilling = true,
        canCreateInvoices = true,
        canEditDeleteInvoices = true,
        canPrintInvoices = true,
        canViewStock = true,
        canManageStock = true,
        canViewParties = true,
        canManageParties = true,
        canViewExpenses = true,
        canManageExpenses = true,
        canViewReports = true,
      )

    val COUNTER_BILLING =
      SalesmanPermissions(
        canViewOverview = true,
        canAddQuickEntry = false,
        canViewBilling = true,
        canCreateInvoices = true,
        canEditDeleteInvoices = false,
        canPrintInvoices = true,
        canViewStock = true,
        canManageStock = false,
        canViewParties = true,
        canManageParties = false,
        canViewExpenses = false,
        canManageExpenses = false,
        canViewReports = false,
      )

    val FIELD_SALES_KHATA =
      SalesmanPermissions(
        canViewOverview = true,
        canAddQuickEntry = true,
        canViewBilling = true,
        canCreateInvoices = true,
        canEditDeleteInvoices = false,
        canPrintInvoices = true,
        canViewStock = true,
        canManageStock = false,
        canViewParties = true,
        canManageParties = true,
        canViewExpenses = true,
        canManageExpenses = false,
        canViewReports = false,
      )

    val STORE_MANAGER =
      SalesmanPermissions(
        canViewOverview = true,
        canAddQuickEntry = true,
        canViewBilling = true,
        canCreateInvoices = true,
        canEditDeleteInvoices = true,
        canPrintInvoices = true,
        canViewStock = true,
        canManageStock = true,
        canViewParties = true,
        canManageParties = true,
        canViewExpenses = true,
        canManageExpenses = true,
        canViewReports = true,
      )
  }
}

enum class SalesmanRolePreset(
  val label: String,
  val description: String,
  val permissions: SalesmanPermissions,
) {
  COUNTER_BILLING(
    label = "Counter Billing",
    description = "Create & print bills, view stock and customer dues",
    permissions = SalesmanPermissions.COUNTER_BILLING,
  ),
  FIELD_SALES(
    label = "Field Sales & Khata",
    description = "Create bills, manage parties & collect Khata payments",
    permissions = SalesmanPermissions.FIELD_SALES_KHATA,
  ),
  STORE_MANAGER(
    label = "Store Manager",
    description = "Full ERP access across bills, stock, purchases & reports",
    permissions = SalesmanPermissions.STORE_MANAGER,
  ),
}

data class SalesmanSeat(
  @DocumentId val id: String = "",
  val userId: String = "",
  val seatId: String = "",
  val name: String = "",
  val phoneNumber: String = "",
  val roleLabel: String = SalesmanRolePreset.COUNTER_BILLING.label,
  @get:PropertyName("isActive")
  @set:PropertyName("isActive")
  var isActive: Boolean = true,
  val allowedProfiles: List<String> = listOf(BusinessProfile.SLOT_MAIN_1),
  val permissions: SalesmanPermissions = SalesmanPermissions.COUNTER_BILLING,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

data class PhoneAccessGrant(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val seatId: String = "",
  val phoneNumber: String = "",
  val salesmanName: String = "",
  val roleLabel: String = SalesmanRolePreset.COUNTER_BILLING.label,
  @get:PropertyName("isActive")
  @set:PropertyName("isActive")
  var isActive: Boolean = true,
  val allowedProfiles: List<String> = listOf(BusinessProfile.SLOT_MAIN_1),
  val permissions: SalesmanPermissions = SalesmanPermissions.COUNTER_BILLING,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

object SalesmanSeatConfig {
  const val MAX_SALESMEN = 5
  val SEAT_IDS = listOf("seat_1", "seat_2", "seat_3", "seat_4", "seat_5")
  private val E164_REGEX = Regex("^\\+[1-9][0-9]{7,14}$")

  fun seatLabel(seatId: String): String {
    val num = seatId.removePrefix("seat_").toIntOrNull()
    return if (num != null) "Salesman $num" else seatId
  }

  fun nextAvailableSeatId(existingSeats: List<SalesmanSeat>): String? {
    val used = existingSeats.map { it.seatId.ifBlank { it.id } }.toSet()
    return SEAT_IDS.firstOrNull { it !in used }
  }

  fun isValidE164(phone: String): Boolean = E164_REGEX.matches(phone.trim())

  fun normalizePhoneToE164(countryCode: String, rawLocalOrFull: String): String? {
    val trimmed = rawLocalOrFull.trim().replace(" ", "").replace("-", "")
    if (trimmed.isEmpty()) return null
    val candidate =
      if (trimmed.startsWith("+")) {
        "+" + trimmed.drop(1).filter { it.isDigit() }
      } else {
        val cleanCc =
          countryCode.trim().let { cc ->
            val digits = cc.filter { it.isDigit() }
            if (digits.isEmpty()) "+91" else "+$digits"
          }
        val localDigits = trimmed.filter { it.isDigit() }.trimStart('0')
        "$cleanCc$localDigits"
      }
    return candidate.takeIf { E164_REGEX.matches(it) }
  }
}
