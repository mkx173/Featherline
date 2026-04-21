package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class MainUiModelsTest {
    @Test
    fun buildMainTodaySection_marks_done_overdue_dueSoon_and_upcoming_rows() {
        val group = medicationGroup(
            uuid = UUID.fromString("7b53f876-8809-4f64-91b0-c6c6a59b87c0"),
            name = "Daily estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(
                    LocalTime.of(8, 0),
                    LocalTime.of(9, 0),
                    LocalTime.of(11, 30),
                    LocalTime.of(20, 0)
                )
            )
        )

        val todaySection = buildMainTodaySection(
            groups = listOf(group),
            entries = listOf(
                scheduledEntry(
                    groupUuid = group.uuid,
                    appliedAt = LocalDateTime.of(2026, 4, 18, 8, 5),
                    scheduledFor = LocalDateTime.of(2026, 4, 18, 8, 0)
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 11, 0)
        )

        assertEquals(1, todaySection.doneCount)
        assertEquals(4, todaySection.totalCount)
        assertEquals(
            listOf(
                MainTodayDoseStatus.DONE,
                MainTodayDoseStatus.OVERDUE,
                MainTodayDoseStatus.DUE_SOON,
                MainTodayDoseStatus.UPCOMING
            ),
            todaySection.rows.map { it.status }
        )
        assertNotNull(todaySection.rows.first().loggedAt)
    }

    @Test
    fun buildMainUpcomingSection_returns_tomorrow_rows_when_tomorrow_has_unfulfilled_slots() {
        val group = medicationGroup(
            uuid = UUID.fromString("1ec1b1bf-f3ff-4104-9ed5-68d5aa7e5a47"),
            name = "Daily spiro",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
            )
        )

        val upcomingSection = buildMainUpcomingSection(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 11, 0)
        )

        assertEquals(MainUpcomingSectionTitle.TOMORROW, upcomingSection.title)
        assertEquals(LocalDate.of(2026, 4, 19), upcomingSection.anchorDate)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 19, 8, 0),
                LocalDateTime.of(2026, 4, 19, 20, 0)
            ),
            upcomingSection.rows.map { it.scheduledAt }
        )
    }

    @Test
    fun buildMainUpcomingSection_falls_back_to_future_upcoming_when_tomorrow_is_empty() {
        val group = medicationGroup(
            uuid = UUID.fromString("efea64bc-0f6d-4813-a39d-428bdce6fd6a"),
            name = "Weekly estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = setOf(LocalDate.of(2026, 4, 20).dayOfWeek),
                times = listOf(LocalTime.of(13, 30))
            )
        )

        val upcomingSection = buildMainUpcomingSection(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 11, 0)
        )

        assertEquals(MainUpcomingSectionTitle.UPCOMING, upcomingSection.title)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 20, 13, 30),
                LocalDateTime.of(2026, 4, 27, 13, 30),
                LocalDateTime.of(2026, 5, 4, 13, 30)
            ),
            upcomingSection.rows.map { it.scheduledAt }
        )
    }

    @Test
    fun buildMainUpcomingSection_excludes_future_slots_that_are_already_fulfilled() {
        val group = medicationGroup(
            uuid = UUID.fromString("ec938925-8b3c-46dd-9912-79244a13f813"),
            name = "Daily estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0))
            )
        )

        val upcomingSection = buildMainUpcomingSection(
            groups = listOf(group),
            entries = listOf(
                scheduledEntry(
                    groupUuid = group.uuid,
                    appliedAt = LocalDateTime.of(2026, 4, 19, 8, 5),
                    scheduledFor = LocalDateTime.of(2026, 4, 19, 8, 0)
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 11, 0)
        )

        assertEquals(MainUpcomingSectionTitle.UPCOMING, upcomingSection.title)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 21, 8, 0),
                LocalDateTime.of(2026, 4, 22, 8, 0)
            ),
            upcomingSection.rows.map { it.scheduledAt }
        )
    }

    private fun medicationGroup(
        uuid: UUID,
        name: String,
        schedule: MedicationGroupSchedule
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = name,
            schedule = schedule,
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.randomUUID(),
                    routeOfAdministration = RouteOfAdministration.ORAL,
                    medicineName = "Estradiol",
                    dosageMgAsMedicine = 2.0
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
    }

    private fun scheduledEntry(
        groupUuid: UUID,
        appliedAt: LocalDateTime,
        scheduledFor: LocalDateTime
    ): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = UUID.randomUUID(),
            routeOfAdministration = RouteOfAdministration.ORAL,
            medicineName = "Estradiol",
            dosageMgAsMedicine = 2.0,
            dosageMgAsEstradiol = 2.0,
            sourceType = MedicationLogEntrySourceType.GROUP_MANUAL,
            sourceGroupUuid = groupUuid,
            appliedAt = appliedAt.atZone(ZoneId.systemDefault()).toInstant(),
            scheduledFor = scheduledFor
        )
    }
}
