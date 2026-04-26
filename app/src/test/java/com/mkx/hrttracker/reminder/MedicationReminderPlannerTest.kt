package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class MedicationReminderPlannerTest {
    @Test
    fun buildNextMedicationReminderPlans_returns_next_plan_for_enabled_groups() {
        val firstGroup = medicationGroup(
            uuid = UUID.fromString("0d03cb47-f36c-4dce-853b-d2427dc8069b"),
            name = "Morning estradiol",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true
        )
        val secondGroup = medicationGroup(
            uuid = UUID.fromString("419f894f-d432-4e1f-b23e-bdc92048d6e1"),
            name = "Evening spiro",
            time = LocalTime.of(20, 0),
            notificationsEnabled = true
        )

        val plans = buildNextMedicationReminderPlans(
            groups = listOf(secondGroup, firstGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 8, 0)
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 20, 9, 0),
                LocalDateTime.of(2026, 4, 20, 20, 0)
            ),
            plans.map { it.scheduledAt }
        )
        assertEquals(
            listOf(firstGroup.uuid, secondGroup.uuid),
            plans.map { it.groupUuid }
        )
    }

    @Test
    fun buildNextMedicationReminderPlans_ignores_disabled_groups() {
        val group = medicationGroup(
            uuid = UUID.fromString("8509fd6e-51d1-4f58-b3e0-4283357ad0bb"),
            name = "Disabled group",
            time = LocalTime.of(9, 0),
            notificationsEnabled = false
        )

        val plans = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 8, 0)
        )

        assertEquals(emptyList<MedicationReminderPlan>(), plans)
    }

    @Test
    fun buildNextMedicationReminderPlans_skips_fulfilled_occurrence_and_uses_next_one() {
        val group = medicationGroup(
            uuid = UUID.fromString("d7bdfbf8-722d-4a81-b921-9169257873aa"),
            name = "Daily estradiol",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true
        )

        val plans = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = listOf(
                scheduledEntry(
                    groupUuid = group.uuid,
                    appliedAt = LocalDateTime.of(2026, 4, 20, 9, 5),
                    scheduledFor = LocalDateTime.of(2026, 4, 20, 9, 0)
                )
            ),
            now = LocalDateTime.of(2026, 4, 20, 8, 30)
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 4, 21, 9, 0)),
            plans.map { it.scheduledAt }
        )
    }

    @Test
    fun buildNextMedicationReminderPlans_returns_empty_when_no_future_unfulfilled_occurrence_exists() {
        val group = medicationGroup(
            uuid = UUID.fromString("8d49d0f2-7daf-458f-9995-9a2b5db32ce0"),
            name = "Weekly estradiol",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true
        )

        val plans = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 10, 0),
            lookaheadDays = 0
        )

        assertEquals(emptyList<MedicationReminderPlan>(), plans)
    }

    @Test
    fun buildNextMedicationReminderPlans_uses_next_selected_weekday_for_weekly_multi_day_group() {
        val group = MedicationGroup(
            uuid = UUID.fromString("548ee741-c2db-4126-a79d-a761131f8be9"),
            name = "Twice weekly estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = LocalDate.of(2026, 4, 14),
                weeklyDaysOfWeek = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("56f1a6b4-dcdb-448b-8f08-9cb5746206d5"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0)
                    )
                )
            ),
            notificationsEnabled = true,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )

        val plans = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 21, 8, 0)
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 4, 23, 9, 0)),
            plans.map { it.scheduledAt }
        )
    }

    private fun medicationGroup(
        uuid: UUID,
        name: String,
        time: LocalTime,
        notificationsEnabled: Boolean
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = name,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(time)
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("56f1a6b4-dcdb-448b-8f08-9cb5746206d5"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0)
                    )
                )
            ),
            notificationsEnabled = notificationsEnabled,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
    }

    private fun scheduledEntry(
        groupUuid: UUID,
        appliedAt: LocalDateTime,
        scheduledFor: LocalDateTime
    ): MedicationLogEntry {
        return testMedicationLogEntry(
            uuid = UUID.randomUUID(),
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor
        )
    }
}
