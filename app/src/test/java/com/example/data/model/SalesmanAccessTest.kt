package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesmanAccessTest {

  @Test
  fun normalizePhoneToE164_formatsValidNumbersAndRejectsInvalid() {
    assertEquals("+919876543210", SalesmanSeatConfig.normalizePhoneToE164("+91", "9876543210"))
    assertEquals("+919876543210", SalesmanSeatConfig.normalizePhoneToE164("91", "098765-43210"))
    assertEquals("+14155550199", SalesmanSeatConfig.normalizePhoneToE164("+91", "+1 415 555 0199"))
    assertNull(SalesmanSeatConfig.normalizePhoneToE164("+91", "12345"))
    assertNull(SalesmanSeatConfig.normalizePhoneToE164("+91", ""))
  }

  @Test
  fun nextAvailableSeatId_enforcesMaxFiveSeats() {
    assertEquals("seat_1", SalesmanSeatConfig.nextAvailableSeatId(emptyList()))

    val threeSeats =
      listOf("seat_1", "seat_2", "seat_4").map { SalesmanSeat(seatId = it, name = it) }
    assertEquals("seat_3", SalesmanSeatConfig.nextAvailableSeatId(threeSeats))

    val fullSeats =
      SalesmanSeatConfig.SEAT_IDS.map { SalesmanSeat(seatId = it, name = it) }
    assertNull(SalesmanSeatConfig.nextAvailableSeatId(fullSeats))
  }

  @Test
  fun rolePresets_haveExpectedGranularFlags() {
    val counter = SalesmanPermissions.COUNTER_BILLING
    assertTrue(counter.canCreateInvoices)
    assertTrue(counter.canPrintInvoices)
    assertFalse(counter.canEditDeleteInvoices)
    assertFalse(counter.canManageStock)
    assertFalse(counter.canViewReports)

    val field = SalesmanPermissions.FIELD_SALES_KHATA
    assertTrue(field.canManageParties)
    assertTrue(field.canViewExpenses)
    assertFalse(field.canManageExpenses)

    assertEquals(13, SalesmanPermissions.OWNER_FULL.enabledCount())
  }
}
