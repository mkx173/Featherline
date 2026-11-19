package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.PlanDayScheduleEntry
import com.mkx.hrttracker.model.medication.buildPlanDaySchedule
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.ui.medication.MedicationLogScheduleOffset
import com.mkx.hrttracker.ui.medication.sourceGroupScheduleOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** O6: the Plan screen and the log editor count real minutes from the slot, as Home does. */
class PlanScheduleOffsetDstTest {
    private val newYork = ZoneId.of("America/New_York")
    private val medicine = testMedicine()

    // Spring forward: 03:10 EDT is 40 minutes after 01:30 EST; the wall clock says 100.
    private val springSlot = LocalDateTime.of(2026, 3, 8, 1, 30)
    private val springLoggedAt = LocalDateTime.of(2026, 3, 8, 3, 10)
    private val springAppliedAt = springLoggedAt.atOffset(ZoneOffset.ofHours(-4)).toInstant()

    // Fall back: 01:40 EST, the repeated hour's second pass, is 130 minutes after 00:30 EDT;
    // the wall clock says 70.
    private val fallSlot = LocalDateTime.of(2026, 11, 1, 0, 30)
    private val fallAppliedAt = LocalDateTime.of(2026, 11, 1, 1, 40).atOffset(ZoneOffset.ofHours(-5)).toInstant()

    @Test
    fun springForward_a0130DoseLoggedAt0310_isFortyMinutesLate_soNoLabel() {
        assertEquals(40L, planEntry(springSlot, springAppliedAt).loggedOffsetMinutes)
        assertNull(selectedDayScheduleOffset(40))
        assertEquals(minutesLater(40), sourceGroupScheduleOffset(springSlot, springLoggedAt, newYork))
    }

    @Test
    fun fallBack_a0030DoseLoggedAt0140OnTheSecondPass_isTwoHoursLate() {
        assertEquals(130L, planEntry(fallSlot, fallAppliedAt).loggedOffsetMinutes)
        assertEquals(hoursLater(2), selectedDayScheduleOffset(130))
        // The editor's draft at 02:40 (EST, no overlap): 3 h 10 min after the slot in real time.
        assertEquals(hoursLater(3), sourceGroupScheduleOffset(fallSlot, LocalDateTime.of(2026, 11, 1, 2, 40), newYork))
    }

    // The Plan row's own label code (SelectedDayRow calls it), on both changes.
    @Test
    fun thePlanRowsLabel_countsRealMinutes_acrossBothChanges() {
        val spring = planEntry(springSlot, springAppliedAt)
        assertNull(selectedDayRowScheduleOffset(SelectedDayRowModel.Scheduled(spring), spring.shownLoggedAt()))
        val fall = planEntry(fallSlot, fallAppliedAt)
        assertEquals(hoursLater(2), selectedDayRowScheduleOffset(SelectedDayRowModel.Scheduled(fall), fall.shownLoggedAt()))
    }

    // As SelectedDayRow's displayedLoggedAt.
    private fun PlanDayScheduleEntry.shownLoggedAt(): LocalDateTime? =
        loggedAt ?: outsideScheduleWindowLoggedAt.takeIf { !isFulfilled }

    private fun planEntry(slot: LocalDateTime, appliedAt: Instant): PlanDayScheduleEntry {
        val plan = group(slot.toLocalDate().minusDays(7), slot.toLocalTime())
        val entry = testMedicationLogEntry(
            medicine = medicine,
            sourceGroupUuid = plan.uuid,
            scheduleTimeUuid = plan.schedule.timeSlots.single().uuid,
            appliedAt = appliedAt,
            appliedAtTimeZoneId = newYork.id,
            scheduledFor = slot,
        )
        return buildPlanDaySchedule(
            date = slot.toLocalDate(),
            groups = listOf(plan),
            entries = listOf(entry),
            now = slot.plusHours(6),
            zoneId = newYork,
            groupsByUuid = mapOf(plan.uuid to plan),
            clockNow = slot.plusHours(6).atZone(newYork).toInstant(),
        ).scheduledEntries.single()
    }

    private fun minutesLater(value: Long) =
        MedicationLogScheduleOffset(labelRes = R.string.medication_editor_schedule_offset_minutes_later, value = value)

    private fun hoursLater(value: Long) =
        MedicationLogScheduleOffset(labelRes = R.string.medication_editor_schedule_offset_hours_later, value = value)

    private fun group(since: LocalDate, time: LocalTime) = MedicationGroup(
        uuid = UUID.randomUUID(),
        name = "Plan",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = since,
            weeklyDaysOfWeek = emptySet(),
            times = listOf(time),
        ),
        medications = listOf(testMedicationGroupMedication(medicine = medicine)),
    )
}
