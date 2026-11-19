package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.ui.plan.buildNextOccurrencesByGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * An early record counts as taken in Home's Upcoming list too, as on the Plan screen's list and
 * in reminders (product decisions, Fulfilment): a dose with any attached record is left out,
 * in or out of its window.
 */
class HomeUpcomingEarlyRecordTest {
    private val zone = ZoneOffset.UTC
    private val monday = LocalDateTime.of(2026, 1, 5, 9, 0)
    private val medicine = testMedicine()

    private fun plan(since: LocalDate, interval: Int) = MedicationGroup(
        uuid = UUID.randomUUID(),
        name = "Morning",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = interval,
            since = since,
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(8, 0)),
        ),
        medications = listOf(testMedicationGroupMedication(medicine = medicine)),
    )

    private fun record(plan: MedicationGroup, slot: LocalDateTime, appliedAt: LocalDateTime) =
        testMedicationLogEntry(
            medicine = medicine,
            sourceGroupUuid = plan.uuid,
            scheduleTimeUuid = plan.schedule.timeSlots.single().uuid,
            appliedAt = appliedAt.toInstant(zone),
            appliedAtTimeZoneId = "UTC",
            scheduledFor = slot,
        )

    // A weekly dose due Friday, taken Wednesday and logged on Friday's slot from the Plan
    // calendar, outside its 24-hour window: Home names the next Friday, as the Plan list does.
    @Test
    fun aDoseLoggedEarlyFromThePlanCalendar_leavesHomesUpcomingList() {
        val friday = LocalDate.of(2026, 1, 9)
        val plan = plan(since = friday, interval = 7)
        val wednesday = monday.plusDays(2)
        val entries = listOf(record(plan, friday.atTime(8, 0), appliedAt = wednesday))
        val wednesdayNoon = wednesday.withHour(12)

        val home = buildMainUpcomingSection(listOf(plan), entries, wednesdayNoon, zone)
        assertEquals(MainUpcomingSectionTitle.UPCOMING, home.title)
        assertEquals(friday.plusDays(7), home.anchorDate)
        assertEquals(
            buildNextOccurrencesByGroup(listOf(plan), entries, wednesdayNoon, limit = 1).getValue(plan.uuid).single(),
            home.rows.single().scheduledAt,
        )
    }

    // Tomorrow's dose logged this morning, a day early: Tomorrow skips it.
    @Test
    fun tomorrowsDoseLoggedEarly_leavesTomorrow() {
        val plan = plan(since = LocalDate.of(2026, 1, 1), interval = 1)
        val tomorrow = monday.toLocalDate().plusDays(1)
        val entries = listOf(record(plan, tomorrow.atTime(8, 0), appliedAt = monday))

        val home = buildMainUpcomingSection(listOf(plan), entries, monday, zone)
        assertEquals(tomorrow.plusDays(1).atTime(8, 0), home.rows.single().scheduledAt)
    }

    private val tomorrow = monday.toLocalDate().plusDays(1)
    private val tablet = testMedicine(uuid = UUID(1, 1))
    private val other = testMedicine(uuid = UUID(1, 2), key = MedicationKey.SPIRONOLACTONE)

    private fun planOf(uuid: UUID, vararg rows: MedicationGroupMedication, at: Int = 8) = MedicationGroup(
        uuid = uuid, name = "Morning", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY, interval = 1, since = LocalDate.of(2026, 1, 1),
            weeklyDaysOfWeek = emptySet(), times = listOf(LocalTime.of(at, 0)),
        ),
        medications = rows.toList(),
    )

    private fun recordOf(plan: MedicationGroup, row: MedicationGroupMedication, count: Int = 1, slotId: Boolean = true, slotAt: LocalDateTime = tomorrow.atTime(8, 0), appliedAt: LocalDateTime = monday) =
        testMedicationLogEntry(
            medicine = row.medicine, applicationType = row.applicationType, doseInstruction = row.doseInstruction,
            sourceGroupUuid = plan.uuid, scheduleTimeUuid = plan.schedule.timeSlots.single().uuid.takeIf { slotId },
            appliedAt = appliedAt.toInstant(zone), appliedAtTimeZoneId = "UTC", scheduledFor = slotAt, count = count,
        )

    private fun rows(plans: List<MedicationGroup>, entries: List<com.mkx.hrttracker.model.medication.MedicationLogEntry>) =
        buildMainUpcomingSection(plans, entries, monday, zone).let { section ->
            section.title to section.rows.map { Triple(it.scheduledAt, it.medication.medicine?.uuid to it.medication.applicationType, it.medication.count) }
        }

    // Each row leaves when its own signature has what the plan asks, by records early, late or on time.
    @Test
    fun aRowLeavesWhenItsOwnSignatureIsLogged() {
        val a = testMedicationGroupMedication(medicine = tablet, count = 2)
        val b = testMedicationGroupMedication(medicine = other)
        val sub = testMedicationGroupMedication(medicine = tablet, applicationType = MedicationApplicationType.SUBLINGUAL)
        val p = planOf(UUID(2, 1), a, b, sub)
        val slot = tomorrow.atTime(8, 0)
        val all = listOf(
            Triple(slot, tablet.uuid to MedicationApplicationType.ORAL, 2),
            Triple(slot, other.uuid to MedicationApplicationType.ORAL, 1),
            Triple(slot, tablet.uuid to MedicationApplicationType.SUBLINGUAL, 1),
        )
        // Nothing logged.
        assertEquals(MainUpcomingSectionTitle.TOMORROW to all, rows(listOf(p), emptyList()))
        // One of two medicines, 23 h early (outside the 12 h window): only its row leaves.
        assertEquals(MainUpcomingSectionTitle.TOMORROW to listOf(all[0], all[2]), rows(listOf(p), listOf(recordOf(p, b))))
        // One of two tablets: the row stays (it shows the plan's two).
        assertEquals(MainUpcomingSectionTitle.TOMORROW to all, rows(listOf(p), listOf(recordOf(p, a, count = 1))))
        // Both tablets, in two records, one with no slot id: the row leaves.
        assertEquals(
            MainUpcomingSectionTitle.TOMORROW to listOf(all[1], all[2]),
            rows(listOf(p), listOf(recordOf(p, a, count = 1), recordOf(p, a, count = 1, slotId = false))),
        )
        // The oral tablet's record doesn't cover the sublingual row.
        assertEquals(MainUpcomingSectionTitle.TOMORROW to listOf(all[1], all[2]), rows(listOf(p), listOf(recordOf(p, a, count = 2))))
        // A record inside the window (10 h early) leaves, as before.
        assertEquals(
            MainUpcomingSectionTitle.TOMORROW to listOf(all[0], all[2]),
            rows(listOf(p), listOf(recordOf(p, b, appliedAt = monday.withHour(22)))),
        )
        // Everything logged early: Tomorrow is empty, so the day after shows as Upcoming.
        val everything = listOf(recordOf(p, a, count = 2), recordOf(p, b), recordOf(p, sub))
        val after = rows(listOf(p), everything)
        assertEquals(MainUpcomingSectionTitle.UPCOMING, after.first)
        assertEquals(tomorrow.plusDays(1).atTime(8, 0), after.second.first().first)
        // Another plan at the same time keeps its own row.
        val q = planOf(UUID(2, 2), testMedicationGroupMedication(medicine = tablet, count = 2))
        assertEquals(
            listOf(q.uuid),
            buildMainUpcomingSection(listOf(p, q), everything, monday, zone).rows.map { it.groupUuid },
        )
        // A record whose time of day the plan has since moved: same slot id, same date.
        val moved = p.copy(
            schedule = p.schedule.copy(
                times = listOf(LocalTime.of(9, 0)),
                timeSlots = p.schedule.timeSlots.map { it.copy(time = LocalTime.of(9, 0)) },
            ),
            medications = listOf(b),
        )
        assertEquals(
            tomorrow.plusDays(1).atTime(9, 0),
            buildMainUpcomingSection(listOf(moved), listOf(recordOf(p, b)), monday, zone).rows.single().scheduledAt,
        )
    }
}
