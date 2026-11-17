package com.example.data.model

import kotlin.math.ceil

enum class SubscriptionTier(
  val key: String,
  val displayName: String,
  val badgeLabel: String,
  val tagline: String,
  val monthlyPriceInr: Int,
  val yearlyDiscountPercent: Int,
  val maxProfiles: Int,
  val maxSalesmen: Int,
  val monthlyInvoiceLimit: Int,
  val allowsAllPrintStyles: Boolean,
  val allowsEInvoiceAndEWayBill: Boolean,
  val allowsFullGstReports: Boolean,
  val featureHighlights: List<String>,
) {
  TRIAL(
    key = "trial",
    displayName = "1-Month Free Trial",
    badgeLabel = "FREE TRIAL",
    tagline = "Full Diamond ERP Access for 30 Days on New Signup",
    monthlyPriceInr = 0,
    yearlyDiscountPercent = 0,
    maxProfiles = 3,
    maxSalesmen = 5,
    monthlyInvoiceLimit = Int.MAX_VALUE,
    allowsAllPrintStyles = true,
    allowsEInvoiceAndEWayBill = true,
    allowsFullGstReports = true,
    featureHighlights =
      listOf(
        "Full Access to all 3 Business Profiles (2 Main GST + 1 Plain Non-GST)",
        "All 5 Salesman Mobile OTP Login Seats with Owner Access Control",
        "GST Portal E-Invoice (IRN + Signed QR) & E-Way Bill Generation",
        "Unlimited GST & Plain Invoices + All Print Sizes & Tally/Executive Styles",
        "Full GSTR-1, GSTR-3B, HSN Summary & Real-time Cloud Sync",
      ),
  ),
  SILVER(
    key = "silver",
    displayName = "Silver Plan",
    badgeLabel = "SILVER",
    tagline = "Essential Single-Business Billing & Inventory",
    monthlyPriceInr = 300,
    yearlyDiscountPercent = 0,
    maxProfiles = 1,
    maxSalesmen = 0,
    monthlyInvoiceLimit = 50,
    allowsAllPrintStyles = false,
    allowsEInvoiceAndEWayBill = false,
    allowsFullGstReports = false,
    featureHighlights =
      listOf(
        "1 Main GST Business Profile (Primary Account Only)",
        "Up to 50 Invoices / Month with Standard Print Layout",
        "Inventory SKU Catalog, Purchase Stock-In & Party Ledger",
        "No Salesman OTP Seats (Owner Login Only)",
        "E-Invoice & E-Way Bill Locked (Upgrade to Diamond)",
      ),
  ),
  GOLD(
    key = "gold",
    displayName = "Gold Plan",
    badgeLabel = "GOLD",
    tagline = "Multi-Profile Billing, 2 Salesmen & GST Reports (10% Off Yearly)",
    monthlyPriceInr = 500,
    yearlyDiscountPercent = 10,
    maxProfiles = 2,
    maxSalesmen = 2,
    monthlyInvoiceLimit = 500,
    allowsAllPrintStyles = true,
    allowsEInvoiceAndEWayBill = false,
    allowsFullGstReports = true,
    featureHighlights =
      listOf(
        "Up to 2 Business Profiles (Main GST #1 & #2)",
        "Up to 2 Salesman Mobile OTP Seats (Seat #1 & #2)",
        "Up to 500 Invoices / Month + All Thermal/A4/Tally Print Styles",
        "GSTR-1, GSTR-3B & P&L Tax Reports Included",
        "E-Invoice & E-Way Bill Locked (Available in Diamond)",
      ),
  ),
  DIAMOND(
    key = "diamond",
    displayName = "Diamond Plan",
    badgeLabel = "DIAMOND",
    tagline = "Full Enterprise ERP + GST E-Invoice & E-Way Bill (20% Off Yearly)",
    monthlyPriceInr = 700,
    yearlyDiscountPercent = 20,
    maxProfiles = 3,
    maxSalesmen = 5,
    monthlyInvoiceLimit = Int.MAX_VALUE,
    allowsAllPrintStyles = true,
    allowsEInvoiceAndEWayBill = true,
    allowsFullGstReports = true,
    featureHighlights =
      listOf(
        "All 3 Business Profiles (2 Main GST + 1 Plain Non-GST Account)",
        "All 5 Salesman Mobile OTP Login Seats with Granular Permissions",
        "1-Click GST Portal E-Invoice (IRN + Signed QR Code)",
        "Full E-Way Bill Generation with Transport, Vehicle & Distance (KM)",
        "Unlimited Invoices, All Print Styles, GSTR Reports & Cloud Backup",
      ),
  );

  val yearlyUndiscountedInr: Int
    get() = monthlyPriceInr * 12

  val yearlyTotalInr: Int
    get() =
      if (yearlyDiscountPercent <= 0) {
        yearlyUndiscountedInr
      } else {
        (yearlyUndiscountedInr * (100 - yearlyDiscountPercent)) / 100
      }

  val yearlySavingsInr: Int
    get() = (yearlyUndiscountedInr - yearlyTotalInr).coerceAtLeast(0)

  val effectiveMonthlyInYearlyInr: Int
    get() = if (yearlyTotalInr == 0) 0 else yearlyTotalInr / 12

  fun allowedProfileSlots(): List<String> =
    when (this) {
      TRIAL,
      DIAMOND -> BusinessProfile.ALL_SLOTS
      GOLD -> listOf(BusinessProfile.SLOT_MAIN_1, BusinessProfile.SLOT_MAIN_2)
      SILVER -> listOf(BusinessProfile.SLOT_MAIN_1)
    }

  fun allowedSalesmanSeatIds(): List<String> =
    when (this) {
      TRIAL,
      DIAMOND -> SalesmanSeatConfig.SEAT_IDS
      GOLD -> listOf("seat_1", "seat_2")
      SILVER -> emptyList()
    }

  fun canUseProfileSlot(slotId: String): Boolean = slotId in allowedProfileSlots()

  fun canUseSalesmanSeat(seatId: String): Boolean = seatId in allowedSalesmanSeatIds()

  companion object {
    const val CYCLE_TRIAL = "trial"
    const val CYCLE_MONTHLY = "monthly"
    const val CYCLE_YEARLY = "yearly"

    const val TRIAL_DURATION_DAYS = 30L
    const val TRIAL_DURATION_MS = TRIAL_DURATION_DAYS * 24L * 60L * 60L * 1000L
    const val MONTHLY_DURATION_MS = 30L * 24L * 60L * 60L * 1000L
    const val YEARLY_DURATION_MS = 365L * 24L * 60L * 60L * 1000L

    val PAID_TIERS: List<SubscriptionTier> = listOf(SILVER, GOLD, DIAMOND)
    val ALL_KEYS: Set<String> = entries.map { it.key }.toSet()

    fun fromKey(rawKey: String?): SubscriptionTier =
      entries.firstOrNull { it.key.equals(rawKey?.trim(), ignoreCase = true) } ?: TRIAL
  }
}

data class SubscriptionStatus(
  val configuredTier: SubscriptionTier = SubscriptionTier.TRIAL,
  val billingCycle: String = SubscriptionTier.CYCLE_TRIAL,
  val trialStartedAtMs: Long = 0L,
  val planExpiresAtMs: Long = 0L,
  val daysRemaining: Int = 30,
  val isExpired: Boolean = false,
  val planAmountPaid: Double = 0.0,
) {
  val effectiveTier: SubscriptionTier
    get() =
      if (isExpired && configuredTier == SubscriptionTier.TRIAL) {
        SubscriptionTier.SILVER
      } else {
        configuredTier
      }

  val isTrial: Boolean
    get() = configuredTier == SubscriptionTier.TRIAL

  val topBarBadgeText: String
    get() =
      when {
        isTrial && !isExpired -> "FREE TRIAL • ${daysRemaining}d left"
        isTrial && isExpired -> "TRIAL EXPIRED • Upgrade"
        billingCycle == SubscriptionTier.CYCLE_YEARLY -> "${configuredTier.badgeLabel} YEARLY"
        else -> "${configuredTier.badgeLabel} PLAN"
      }

  val statusSummaryText: String
    get() =
      when {
        isTrial && !isExpired ->
          "1-Month Free Trial Active ($daysRemaining of 30 days left) • Full Diamond ERP Access Unlocked"
        isTrial && isExpired ->
          "Your 30-day Free Trial has ended. Upgrade to Silver, Gold, or Diamond to unlock full features."
        billingCycle == SubscriptionTier.CYCLE_YEARLY ->
          "${configuredTier.displayName} (Yearly Billing • $daysRemaining days remaining)"
        else ->
          "${configuredTier.displayName} (Monthly Billing • $daysRemaining days remaining)"
      }

  fun canUseProfileSlot(slotId: String): Boolean = effectiveTier.canUseProfileSlot(slotId)

  fun canUseSalesmanSeat(seatId: String): Boolean = effectiveTier.canUseSalesmanSeat(seatId)

  val allowsEInvoiceAndEWayBill: Boolean
    get() = effectiveTier.allowsEInvoiceAndEWayBill

  val allowsAllPrintStyles: Boolean
    get() = effectiveTier.allowsAllPrintStyles

  val maxProfiles: Int
    get() = effectiveTier.maxProfiles

  val maxSalesmen: Int
    get() = effectiveTier.maxSalesmen

  companion object {
    fun fromProfile(
      profile: BusinessProfile?,
      nowMs: Long = System.currentTimeMillis(),
    ): SubscriptionStatus {
      if (profile == null) {
        return SubscriptionStatus(
          configuredTier = SubscriptionTier.TRIAL,
          billingCycle = SubscriptionTier.CYCLE_TRIAL,
          trialStartedAtMs = nowMs,
          planExpiresAtMs = nowMs + SubscriptionTier.TRIAL_DURATION_MS,
          daysRemaining = 30,
          isExpired = false,
        )
      }
      val tier = SubscriptionTier.fromKey(profile.planTier)
      val createdFallbackMs =
        profile.createdAt?.toDate()?.time?.takeIf { it > 0L } ?: nowMs
      val startMs =
        if (profile.trialStartedAtMs > 0L) profile.trialStartedAtMs else createdFallbackMs
      val expiresMs =
        if (profile.planExpiresAtMs > 0L) {
          profile.planExpiresAtMs
        } else {
          startMs + SubscriptionTier.TRIAL_DURATION_MS
        }
      val remainingMs = expiresMs - nowMs
      val daysLeft =
        if (remainingMs <= 0L) 0
        else ceil(remainingMs.toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)).toInt().coerceAtLeast(1)
      val expired = remainingMs <= 0L
      val cycle =
        profile.billingCycle.ifBlank {
          if (tier == SubscriptionTier.TRIAL) SubscriptionTier.CYCLE_TRIAL
          else SubscriptionTier.CYCLE_MONTHLY
        }
      return SubscriptionStatus(
        configuredTier = tier,
        billingCycle = cycle,
        trialStartedAtMs = startMs,
        planExpiresAtMs = expiresMs,
        daysRemaining = daysLeft,
        isExpired = expired,
        planAmountPaid = profile.planAmountPaid,
      )
    }
  }
}
