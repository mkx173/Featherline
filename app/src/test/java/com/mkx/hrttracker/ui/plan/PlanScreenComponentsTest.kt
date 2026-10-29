package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.PlanDaySchedule
import com.mkx.hrttracker.model.medication.PlanDayScheduleEntry
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.ui.medication.MedicationLogScheduleOffset
import com.mkx.hrttracker.util.MedicationDayBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class PlanScreenComponentsTest {

    @Test
    fun selectedDayRows_sortAfterMidnightSlotLast() {
        val dawn = PlanDayScheduleEntry(
            groupUuid = UUID.fromString("33333333-3333-3333-3333-333333333333"),
            groupName = "Night",
            groupColorKey = MedicationGroupColorKey.ROSE,
            scheduledTime = LocalTime.of(2, 0),
            scheduledFor = LocalDateTime.of(2026, 5, 19, 2, 0),
            medication = testMedicationGroupMedication(),
            fulfillingEntryUuids = emptyList(),
            isFulfilled = false,
            isDueSoon = false,
            isPastDue = false,
        )
        val evening = testMedicationLogEntry(
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 5, 18, 23, 0)),
        )

        val rows = selectedDayRows(
            PlanDaySchedule(
                date = LocalDate.of(2026, 5, 18),
                scheduledEntries = listOf(dawn),
                unplannedEntries = listOf(evening),
            ),
            groupsByUuid = emptyMap(),
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 5, 18, 23, 0),
                LocalDateTime.of(2026, 5, 19, 2, 0),
            ),
            rows.map { row -> row.sortTime },
        )
    }

    @Test
    fun selectedDayRows_takeEachRowsOwnPlanEnding() {
        val nightOwl = MedicationGroup(
            uuid = UUID.fromString("44444444-4444-4444-4444-444444444444"),
            name = "Night",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 10, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(22, 0)),
                dayBoundary = MedicationDayBoundary.FourAm,
            ),
            medications = listOf(testMedicationGroupMedication()),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val scheduled = PlanDayScheduleEntry(
            groupUuid = nightOwl.uuid,
            groupName = nightOwl.name,
            groupColorKey = MedicationGroupColorKey.ROSE,
            scheduledTime = LocalTime.of(22, 0),
            scheduledFor = LocalDateTime.of(2026, 10, 28, 22, 0),
            medication = testMedicationGroupMedication(),
            fulfillingEntryUuids = emptyList(),
            isFulfilled = false,
            isDueSoon = false,
            isPastDue = false,
            dayBoundary = MedicationDayBoundary.FourAm,
        )
        val offPlan = testMedicationLogEntry(
            sourceGroupUuid = nightOwl.uuid,
            appliedAt = testInstant(LocalDateTime.of(2026, 10, 29, 1, 0)),
        )
        val manual = testMedicationLogEntry(
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 10, 29, 1, 30)),
        )

        val rows = selectedDayRows(
            PlanDaySchedule(
                date = LocalDate.of(2026, 10, 28),
                scheduledEntries = listOf(scheduled),
                unplannedEntries = listOf(offPlan, manual),
            ),
            groupsByUuid = mapOf(nightOwl.uuid to nightOwl),
        )

        // The moon and day label follow each row's own plan; a record not from a plan uses midnight.
        assertEquals(
            listOf(MedicationDayBoundary.FourAm, MedicationDayBoundary.FourAm, MedicationDayBoundary.Midnight),
            rows.map { row -> row.dayBoundary },
        )
    }

    @Test
    fun stockPreviewNumberUsesProvidedLocaleWithoutFixedTrailingZeros() {
        assertEquals("1,5", stockPreviewNumber(1.5, Locale.GERMANY))
        assertEquals("1", stockPreviewNumber(1.0, Locale.GERMANY))
    }

    @Test
    fun selectedDayLoggedDayOffsetDays_returnsNullForSameDayLog() {
        assertNull(
            selectedDayLoggedDayOffsetDays(
                scheduledDate = LocalDate.of(2026, 4, 18),
                loggedAt = LocalDateTime.of(2026, 4, 18, 23, 30),
                boundary = MedicationDayBoundary.Midnight,
            )
        )
    }

    @Test
    fun selectedDayLoggedDayOffsetDays_returnsSignedDayOffsetForDifferentDayLog() {
        assertEquals(
            1L,
            selectedDayLoggedDayOffsetDays(
                scheduledDate = LocalDate.of(2026, 4, 18),
                loggedAt = LocalDateTime.of(2026, 4, 19, 0, 15),
                boundary = MedicationDayBoundary.Midnight,
            )
        )
        assertEquals(
            -1L,
            selectedDayLoggedDayOffsetDays(
                scheduledDate = LocalDate.of(2026, 4, 18),
                loggedAt = LocalDateTime.of(2026, 4, 17, 23, 45),
                boundary = MedicationDayBoundary.Midnight,
            )
        )
    }

    @Test
    fun selectedDayLoggedDayOffsetDays_fourAmKeepsAPreDawnLogOnItsNight() {
        val scheduledDate = LocalDate.of(2026, 4, 18)
        assertNull(
            selectedDayLoggedDayOffsetDays(
                scheduledDate = scheduledDate,
                loggedAt = LocalDateTime.of(2026, 4, 19, 1, 5),
                boundary = MedicationDayBoundary.FourAm,
            )
        )
        assertEquals(
            1L,
            selectedDayLoggedDayOffsetDays(
                scheduledDate = scheduledDate,
                loggedAt = LocalDateTime.of(2026, 4, 19, 9, 0),
                boundary = MedicationDayBoundary.FourAm,
            )
        )
    }

    @Test
    fun selectedDayLoggedTimeLabel_appendsDayOffsetAfterLoggedTime() {
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

        assertEquals(
            "01:05 AM (+1)",
            selectedDayLoggedTimeLabel(
                loggedAt = LocalDateTime.of(2026, 4, 19, 1, 5),
                fallbackScheduledTime = LocalTime.of(23, 0),
                timeFormatter = timeFormatter,
                loggedDayOffsetText = "+1"
            )
        )
    }

    @Test
    fun selectedDayLoggedTimeLabel_omitsDayOffsetWhenSameDay() {
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

        assertEquals(
            "09:30 PM",
            selectedDayLoggedTimeLabel(
                loggedAt = LocalDateTime.of(2026, 4, 18, 21, 30),
                fallbackScheduledTime = LocalTime.of(21, 0),
                timeFormatter = timeFormatter,
                loggedDayOffsetText = null
            )
        )
    }

    @Test
    fun selectedDayHeaderCountLabel_omits_empty_count() {
        assertNull(
            selectedDayHeaderCountLabel(
                date = LocalDate.of(2026, 4, 18),
                today = LocalDate.of(2026, 4, 18),
                completedScheduledCount = 0,
                scheduledCount = 0,
                offPlanCount = 0
            )
        )
    }

    @Test
    fun selectedDayHeaderCountLabel_formats_planned_count() {
        assertEquals(
            "1/4",
            selectedDayHeaderCountLabel(
                date = LocalDate.of(2026, 4, 18),
                today = LocalDate.of(2026, 4, 18),
                completedScheduledCount = 1,
                scheduledCount = 4,
                offPlanCount = 0
            )
        )
    }

    @Test
    fun selectedDayHeaderCountLabel_formats_manual_only_count() {
        assertEquals(
            "(2)",
            selectedDayHeaderCountLabel(
                date = LocalDate.of(2026, 4, 18),
                today = LocalDate.of(2026, 4, 18),
                completedScheduledCount = 0,
                scheduledCount = 0,
                offPlanCount = 2
            )
        )
    }

    @Test
    fun selectedDayHeaderCountLabel_formats_planned_and_manual_count() {
        assertEquals(
            "1/4 (2)",
            selectedDayHeaderCountLabel(
                date = LocalDate.of(2026, 4, 18),
                today = LocalDate.of(2026, 4, 18),
                completedScheduledCount = 1,
                scheduledCount = 4,
                offPlanCount = 2
            )
        )
    }

    @Test
    fun selectedDayScheduleOffset_omitsSubHourDelta() {
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)

        assertNull(
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusMinutes(59),
            )
        )
        assertNull(
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusMinutes(59),
            )
        )
        assertNull(
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusHours(1).minusSeconds(1),
            )
        )
        assertNull(
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusHours(1).plusSeconds(1),
            )
        )
    }

    @Test
    fun selectedDayScheduleOffset_keepsExactHourDelta() {
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)

        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_hours_later,
                value = 1,
            ),
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusHours(1),
            )
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_hours_earlier,
                value = 1,
            ),
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusHours(1),
            )
        )
    }
}
