package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.reminder.MedicationReminderPlan
import com.mkx.hrttracker.reminder.buildNextMedicationReminderPlans
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorUiState
import com.mkx.hrttracker.ui.plan.buildNextOccurrencesByGroup
import com.mkx.hrttracker.ui.plan.buildPlanBatchAddOccurrences
import com.mkx.hrttracker.ui.plan.editorStateWithResetWeeklyDaysOfWeek
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.timeOfDayOrder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationDayCadenceTest {
    private val zone = ZoneId.of("UTC")
    private val fourAm = MedicationDayBoundary.FourAm
    private val midnight = MedicationDayBoundary.Midnight
    private val since = LocalDate.of(2026, 10, 18)

    @Test
    fun onlyPlansThatSkipDaysWithADawnTimeMoveWithTheSwitch() {
        val dawnAndEvening = listOf(LocalTime.of(2, 0), LocalTime.of(22, 0))
        fun moves(type: MedicationGroupScheduleType, interval: Int, times: List<LocalTime>) =
            group(type, interval = interval, times = times, weeklyDays = setOf(DayOfWeek.MONDAY))
                .schedule.movesWithMedicationDayBoundary

        assertEquals(false, moves(MedicationGroupScheduleType.DAILY, 1, dawnAndEvening))
        assertEquals(true, moves(MedicationGroupScheduleType.DAILY, 2, dawnAndEvening))
        assertEquals(true, moves(MedicationGroupScheduleType.WEEKLY, 1, dawnAndEvening))
        assertEquals(false, moves(MedicationGroupScheduleType.DAILY, 2, listOf(LocalTime.of(4, 0))))
    }

    @Test
    fun fourAmOrdersDawnTimesAfterTheEvening() {
        val times = listOf(LocalTime.of(22, 0), LocalTime.of(2, 0), LocalTime.of(4, 0))

        assertEquals(
            listOf(LocalTime.of(4, 0), LocalTime.of(22, 0), LocalTime.of(2, 0)),
            times.sortedBy(fourAm::timeOfDayOrder),
        )
        assertEquals(times.sorted(), times.sortedBy(midnight::timeOfDayOrder))
    }

    @Test
    fun fourAmDailyPlanStartsWithItsDawnSlotOnTheStartMedicationDay() {
        val group = group(MedicationGroupScheduleType.DAILY, interval = 1, times = listOf(LocalTime.of(2, 0)))

        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 19, 2, 0)),
            slots(group, since, fourAm),
        )
    }

    @Test
    fun fourAmWeeklyDawnSlotFiresInItsMedicationDaysEarlyHours() {
        val group = group(
            type = MedicationGroupScheduleType.WEEKLY,
            interval = 1,
            times = listOf(LocalTime.of(2, 0)),
            weeklyDays = setOf(DayOfWeek.MONDAY),
        )

        assertEquals(emptyList<LocalDateTime>(), slots(group, since, fourAm))
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 20, 2, 0)),
            slots(group, since.plusDays(1), fourAm),
        )
    }

    @Test
    fun fourAmDailyIntervalCountsMedicationDays() {
        val group = group(MedicationGroupScheduleType.DAILY, interval = 2, times = listOf(LocalTime.of(2, 0)))

        assertEquals(listOf(LocalDateTime.of(2026, 10, 19, 2, 0)), slots(group, since, fourAm))
        assertEquals(emptyList<LocalDateTime>(), slots(group, since.plusDays(1), fourAm))
        assertEquals(listOf(LocalDateTime.of(2026, 10, 21, 2, 0)), slots(group, since.plusDays(2), fourAm))
    }

    @Test
    fun fourAmEachScheduledMedicationDayCarriesItsEveningAndNextDawnTimes() {
        val group = group(
            MedicationGroupScheduleType.DAILY,
            interval = 2,
            times = listOf(LocalTime.of(1, 0), LocalTime.of(20, 0)),
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 10, 18, 20, 0),
                LocalDateTime.of(2026, 10, 19, 1, 0),
            ),
            slots(group, since, fourAm),
        )
        assertEquals(emptyList<LocalDateTime>(), slots(group, since.plusDays(1), fourAm))
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 10, 20, 20, 0),
                LocalDateTime.of(2026, 10, 21, 1, 0),
            ),
            slots(group, since.plusDays(2), fourAm),
        )
    }

    @Test
    fun midnightCadenceStaysOnCivilDates() {
        val group = group(MedicationGroupScheduleType.DAILY, interval = 2, times = listOf(LocalTime.of(2, 0)))

        assertEquals(listOf(LocalDateTime.of(2026, 10, 18, 2, 0)), slots(group, since, midnight))
        assertEquals(emptyList<LocalDateTime>(), slots(group, since.plusDays(1), midnight))
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 20, 2, 0)),
            slots(group, since.plusDays(2), midnight),
        )
    }

    @Test
    fun fourAmDaytimeCadenceIsUnchanged() {
        val group = group(MedicationGroupScheduleType.DAILY, interval = 2, times = listOf(LocalTime.of(9, 0)))

        assertEquals(listOf(LocalDateTime.of(2026, 10, 18, 9, 0)), slots(group, since, fourAm))
        assertEquals(emptyList<LocalDateTime>(), slots(group, since.plusDays(1), fourAm))
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 20, 9, 0)),
            slots(group, since.plusDays(2), fourAm),
        )
    }

    @Test
    fun fourAmReminderAgreesWithTheStartMedicationDaysDawn() {
        val group = group(MedicationGroupScheduleType.DAILY, interval = 2, times = listOf(LocalTime.of(2, 0)))
            .copy(notificationsEnabled = true)

        val plans = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 10, 19, 1, 32),
            boundary = fourAm,
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 19, 2, 0)),
            plans.map(MedicationReminderPlan::scheduledAt),
        )
    }

    @Test
    fun fourAmPlanNextOccurrencesAgreeWithTheStartMedicationDaysDawn() {
        val group = group(MedicationGroupScheduleType.DAILY, interval = 2, times = listOf(LocalTime.of(2, 0)))

        val next = buildNextOccurrencesByGroup(
            groups = listOf(group),
            entries = emptyList(),
            start = LocalDateTime.of(2026, 10, 19, 1, 32),
            limit = 1,
            boundary = fourAm,
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 19, 2, 0)),
            next.getValue(group.uuid),
        )
    }

    @Test
    fun fourAmEditorPreviewStartsAtTheStartMedicationDaysDawn() {
        val schedule = schedule(
            MedicationGroupScheduleType.DAILY,
            interval = 1,
            times = listOf(LocalTime.of(2, 0)),
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 19, 2, 0)),
            schedule.nextOccurrencesFrom(
                start = LocalDateTime.of(2026, 10, 19, 1, 32),
                limit = 1,
                boundary = fourAm,
            ),
        )
    }

    @Test
    fun fourAmWeeklyResetSelectsTheStartMedicationDaysWeekday() {
        val state = MedicationGroupEditorUiState(
            sinceDate = since,
            medicationDayBoundary = fourAm,
            weeklyTime = LocalTime.of(2, 0),
        )

        assertEquals(
            setOf(DayOfWeek.SUNDAY),
            editorStateWithResetWeeklyDaysOfWeek(state).weeklyDaysOfWeek,
        )
    }

    @Test
    fun fourAmBatchAddFollowsMedicationDayCadence() {
        val group = group(MedicationGroupScheduleType.DAILY, interval = 2, times = listOf(LocalTime.of(2, 0)))

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 10, 19, 2, 0),
                LocalDateTime.of(2026, 10, 21, 2, 0),
            ),
            buildPlanBatchAddOccurrences(
                schedule = group.schedule,
                startDate = since,
                endDate = since.plusDays(2),
                boundary = fourAm,
            ),
        )
    }

    private fun slots(
        group: MedicationGroup,
        date: LocalDate,
        boundary: MedicationDayBoundary,
    ): List<LocalDateTime> {
        return group.scheduledSlotsInPlanWindow(date, boundary, zone)
            .map(MedicationGroupSlotKey::scheduledFor)
    }

    private fun group(
        type: MedicationGroupScheduleType,
        interval: Int,
        times: List<LocalTime>,
        weeklyDays: Set<DayOfWeek> = emptySet(),
    ): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString("1c7a4a6c-6c1d-4c7d-9d2a-6f7b5a3e8c11"),
            name = "Dawn",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            schedule = schedule(type, interval, times, weeklyDays),
            medications = listOf(testMedicationGroupMedication(medicine = testMedicine())),
        )
    }

    private fun schedule(
        type: MedicationGroupScheduleType,
        interval: Int,
        times: List<LocalTime>,
        weeklyDays: Set<DayOfWeek> = emptySet(),
    ): MedicationGroupSchedule {
        return MedicationGroupSchedule(
            type = type,
            interval = interval,
            since = since,
            weeklyDaysOfWeek = weeklyDays,
            times = times,
        )
    }
}
