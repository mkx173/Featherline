package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Home's due tests in the repeated hour of 2026-11-01, New York, count real time as the widget does. */
class MainHomeFallBackHourTest {
    private val newYork = ZoneId.of("America/New_York")

    private fun dailyAt(time: LocalTime, key: MedicationKey = MedicationKey.ESTRADIOL) = MedicationGroup(
        uuid = UUID.fromString("5b0e1c2a-0000-4000-8000-000000000001"),
        name = "Daily",
        colorKey = MedicationGroupColorKey.TEAL,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 10, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(time),
        ),
        medications = listOf(testMedicationGroupMedication(medicine = testMedicine(key = key))),
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
    )

    /** Wall-clock [hour]:[minute] on the second pass, EST. */
    private fun secondPass(hour: Int, minute: Int): Pair<LocalDateTime, Instant> {
        val wallClock = LocalDateTime.of(2026, 11, 1, hour, minute)
        return wallClock to wallClock.atOffset(ZoneOffset.ofHours(-5)).toInstant()
    }

    private fun todayRow(group: MedicationGroup, now: LocalDateTime, clockNow: Instant) =
        buildMainDoseSections(groups = listOf(group), entries = emptyList(), now = now, zoneId = newYork, clockNow = clockNow)
            .today.rows.single()

    @Test
    fun aMissedDoseInTheRepeatedHour_isOverdueAnHourAfter_notUpcoming() {
        // A 01:30 dose (EDT, the first pass) at 01:30 EST: an hour later in real time.
        val (now, clockNow) = secondPass(1, 30)

        assertEquals(MainTodayDoseStatus.OVERDUE, todayRow(dailyAt(LocalTime.of(1, 30)), now, clockNow).status)
    }

    @Test
    fun theLateOffset_countsRealMinutes() {
        // A missed 00:30 dose at 01:10 EST is 100 minutes late, not the wall clock's 40.
        val (now, clockNow) = secondPass(1, 10)

        val row = todayRow(dailyAt(LocalTime.of(0, 30)), now, clockNow)

        assertEquals(MainTodayDoseStatus.OVERDUE, row.status)
        assertEquals(100L, row.scheduleOffsetMinutes)
    }

    @Test
    fun aDoseLoggedInAnotherZone_isLateByItsOwnSlot_inBothDirections() {
        // Logged at 08:05 for an 08:00 slot, then viewed in another zone: 5 minutes, no label.
        val date = LocalDate.of(2026, 10, 20)
        val tokyo = ZoneId.of("Asia/Tokyo")
        for ((loggedIn, viewedIn) in listOf(tokyo to newYork, newYork to tokyo)) {
            val medicine = testMedicine(uuid = UUID.fromString("5b0e1c2a-0000-4000-8000-0000000000aa"))
            val group = dailyAt(LocalTime.of(8, 0)).copy(
                medications = listOf(testMedicationGroupMedication(medicine = medicine)),
            )
            val loggedAt = date.atTime(8, 5).atZone(loggedIn).toInstant()
            val entry = testMedicationLogEntry(
                medicine = medicine,
                sourceGroupUuid = group.uuid,
                appliedAt = loggedAt,
                appliedAtTimeZoneId = loggedIn.id,
                scheduledFor = date.atTime(8, 0),
                scheduleTimeUuid = group.schedule.timeSlots.single().uuid,
            )
            // Late that evening where it's viewed: the same calendar day, after the log.
            val now = date.atTime(23, 0)

            val row = buildMainDoseSections(
                groups = listOf(group),
                entries = listOf(entry),
                now = now,
                zoneId = viewedIn,
                clockNow = now.atZone(viewedIn).toInstant(),
            ).today.rows.single()

            assertEquals(MainTodayDoseStatus.DONE, row.status)
            assertEquals("logged in ${loggedIn.id}, viewed in ${viewedIn.id}", 5L, row.scheduleOffsetMinutes)
        }
    }

    @Test
    fun aDoseLoggedOnTheSecondPass_isTheLastDose_onTheCardAndTheHero() {
        // A 01:00 dose logged at 01:20 EST, seen at 01:30 EST: the first pass would put the cutoff
        // at 01:31 EDT, an hour before the dose.
        val (now, clockNow) = secondPass(1, 30)
        val (loggedWallClock, loggedAt) = secondPass(1, 20)
        val spiro = testMedicine(
            uuid = UUID.fromString("5b0e1c2a-0000-4000-8000-0000000000bb"),
            key = MedicationKey.SPIRONOLACTONE,
        )
        val group = dailyAt(LocalTime.of(1, 0)).copy(
            medications = listOf(testMedicationGroupMedication(medicine = spiro)),
        )
        val spiroDose = testMedicationLogEntry(
            medicine = spiro,
            sourceGroupUuid = group.uuid,
            appliedAt = loggedAt,
            appliedAtTimeZoneId = newYork.id,
        )
        val card = buildMainAntiandrogenCards(
            groups = listOf(group),
            entries = listOf(spiroDose),
            now = now,
            zoneId = newYork,
            clockNow = clockNow,
        ).single()
        assertEquals(loggedWallClock, card.lastDoseAt)

        val estradiolDose = testMedicationLogEntry(sourceGroupUuid = null, appliedAt = loggedAt, appliedAtTimeZoneId = newYork.id)
        val hero = buildMainE2Hero(entries = listOf(estradiolDose), zoneId = newYork, now = now, clockNow = clockNow)
        assertEquals(loggedWallClock, hero.lastDoseAt)
    }

    @Test
    fun anAntiandrogenDose_isPastDue_beside_itsOverdueRow() {
        val (now, clockNow) = secondPass(1, 10)

        val card = buildMainAntiandrogenCards(
            groups = listOf(dailyAt(LocalTime.of(0, 30), MedicationKey.SPIRONOLACTONE)),
            entries = emptyList(),
            now = now,
            zoneId = newYork,
            clockNow = clockNow,
        ).single()

        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 30), card.nextDoseAt)
        assertTrue(card.isNextDosePastDue)
    }
}
