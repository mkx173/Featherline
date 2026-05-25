package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class MedicationGroupScheduleOccurrencesTest {
    @Test
    fun isScheduledOn_returns_true_for_each_selected_weekday() {
        val schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.WEEKLY,
            interval = 1,
            since = LocalDate.of(2026, 4, 14),
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            times = listOf(LocalTime.of(9, 0))
        )

        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 4, 16)))
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 4, 20)))
        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 4, 21)))
    }

    @Test
    fun isScheduledOn_groups_selected_days_within_same_since_anchored_week() {
        // since = Tue 2026-04-14, interval = 2 weeks, days = {Mon, Thu}.
        // Cadence is anchored on `since`, so week 0 spans Apr 14..Apr 20.
        // Both Thu Apr 16 (3 days after since) and Mon Apr 20 (6 days after)
        // belong to week 0 and fire; week 1 (Apr 21..Apr 27) is off; week 2
        // (Apr 28..May 4) fires again. With the old Monday-bucket anchor,
        // Mon Apr 20 would have fallen into a different week than its
        // preceding Thu — that coupling is gone now.
        val schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.WEEKLY,
            interval = 2,
            since = LocalDate.of(2026, 4, 14),
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            times = listOf(LocalTime.of(9, 0))
        )

        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 4, 13)))
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 4, 16)))
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 4, 20)))

        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 4, 23)))
        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 4, 27)))

        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 4, 30)))
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 5, 4)))

        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 5, 7)))
    }

    @Test
    fun occurrencesBetween_returns_all_selected_weekdays_at_shared_time() {
        val schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.WEEKLY,
            interval = 1,
            since = LocalDate.of(2026, 4, 14),
            weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            times = listOf(LocalTime.of(9, 0))
        )

        val occurrences = schedule.occurrencesBetween(
            startDate = LocalDate.of(2026, 4, 16),
            endDate = LocalDate.of(2026, 4, 23)
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 16, 9, 0),
                LocalDateTime.of(2026, 4, 20, 9, 0),
                LocalDateTime.of(2026, 4, 23, 9, 0),
            ),
            occurrences
        )
    }

    @Test
    fun occurrencesBetweenInPlanWindow_excludes_archived_slots_at_or_after_archive_time() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0)),
            ),
        ).copy(
            archivedAtLocal = LocalDateTime.of(2026, 4, 18, 10, 0),
        )

        val occurrences = group.occurrencesBetweenInPlanWindow(
            startDate = LocalDate.of(2026, 4, 18),
            endDate = LocalDate.of(2026, 4, 18),
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 4, 18, 9, 0)),
            occurrences.map(MedicationGroupSlotOccurrence::scheduledFor),
        )
    }

    @Test
    fun occurrencesBetweenInPlanWindow_excludes_successor_slots_before_effectiveFrom() {
        val scheduleTimeUuid = UUID.fromString("08b67f8d-3534-4c95-aa12-1691132e508f")
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTime(
                        uuid = scheduleTimeUuid,
                        time = LocalTime.of(9, 0),
                        effectiveFrom = LocalDateTime.of(2026, 4, 18, 10, 0),
                    )
                ),
            ),
        )

        val occurrences = group.occurrencesBetweenInPlanWindow(
            startDate = LocalDate.of(2026, 4, 17),
            endDate = LocalDate.of(2026, 4, 19),
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 4, 19, 9, 0)),
            occurrences.map(MedicationGroupSlotOccurrence::scheduledFor),
        )
        assertEquals(listOf(scheduleTimeUuid), occurrences.map(MedicationGroupSlotOccurrence::scheduleTimeUuid))
    }

    @Test
    fun occurrencesBetweenInPlanWindow_excludes_added_past_same_day_time_until_next_day() {
        val addedSlotUuid = UUID.fromString("c032724d-ce9c-4564-a5ca-49807a308aef")
        val existingSlotUuid = UUID.fromString("fe13529d-0e06-4d3c-9424-49a14935350e")
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(11, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTime(
                        uuid = addedSlotUuid,
                        time = LocalTime.of(8, 0),
                        effectiveFrom = LocalDateTime.of(2026, 4, 18, 10, 0),
                    ),
                    MedicationGroupScheduleTime(
                        uuid = existingSlotUuid,
                        time = LocalTime.of(11, 0),
                        effectiveFrom = LocalDate.of(2026, 4, 1).atStartOfDay(),
                    )
                ),
            ),
        )

        val occurrences = group.occurrencesBetweenInPlanWindow(
            startDate = LocalDate.of(2026, 4, 18),
            endDate = LocalDate.of(2026, 4, 19),
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 18, 11, 0),
                LocalDateTime.of(2026, 4, 19, 8, 0),
                LocalDateTime.of(2026, 4, 19, 11, 0),
            ),
            occurrences.map(MedicationGroupSlotOccurrence::scheduledFor),
        )
    }

    @Test
    fun occurrencesBetweenInPlanWindow_applies_midnight_cutover_against_save_time() {
        val lateSaveGroup = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.MIDNIGHT),
                timeSlots = listOf(
                    MedicationGroupScheduleTime(
                        uuid = UUID.fromString("2ed090f4-6580-48fb-bf07-7e3febcff861"),
                        time = LocalTime.MIDNIGHT,
                        effectiveFrom = LocalDateTime.of(2026, 4, 19, 0, 1),
                    )
                ),
            ),
        )
        val earlySaveGroup = lateSaveGroup.copy(
            schedule = lateSaveGroup.schedule.copy(
                timeSlots = listOf(
                    lateSaveGroup.schedule.timeSlots.single().copy(
                        effectiveFrom = LocalDateTime.of(2026, 4, 18, 23, 59),
                    )
                )
            )
        )

        assertEquals(
            emptyList<LocalDateTime>(),
            lateSaveGroup.occurrencesBetweenInPlanWindow(
                startDate = LocalDate.of(2026, 4, 19),
                endDate = LocalDate.of(2026, 4, 19),
            ).map(MedicationGroupSlotOccurrence::scheduledFor),
        )
        assertEquals(
            listOf(LocalDateTime.of(2026, 4, 19, 0, 0)),
            earlySaveGroup.occurrencesBetweenInPlanWindow(
                startDate = LocalDate.of(2026, 4, 19),
                endDate = LocalDate.of(2026, 4, 19),
            ).map(MedicationGroupSlotOccurrence::scheduledFor),
        )
    }

    private fun medicationGroup(schedule: MedicationGroupSchedule): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString("435c592a-cc90-4f70-9266-b0960a42f46d"),
            name = "Group",
            schedule = schedule,
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("b163399f-2eb2-4d5f-b1ad-b516de344c4e"),
                    medicine = testMedicine(key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
    }
}
