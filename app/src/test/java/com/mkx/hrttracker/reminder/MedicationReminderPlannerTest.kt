package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class MedicationReminderPlannerTest {
    // Shared so the planner's fulfillment signature matches between scheduled
    // group slots and the seeded log entries — random per-call UUIDs would
    // always mismatch and the test would never exercise the "skip fulfilled"
    // or "scan further" branches.
    private val estradiolMedicineUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")

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
    fun buildNextMedicationReminderPlans_skips_explicitly_skipped_occurrence() {
        val group = medicationGroup(
            uuid = UUID.fromString("f5db58bc-3e77-45be-a787-5f7ec8784180"),
            name = "Daily estradiol",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true,
        )
        val skippedAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val firstPlan = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 8, 0),
        ).single()

        val plans = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 8, 0),
            skippedSlots = setOf(
                MedicationReminderSlot(
                    groupUuid = group.uuid,
                    scheduledAt = skippedAt,
                    scheduleTimeUuid = firstPlan.scheduleTimeUuid,
                )
            ),
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 4, 21, 9, 0)),
            plans.map(MedicationReminderPlan::scheduledAt),
        )
    }

    @Test
    fun buildNextMedicationReminderPlans_skips_occurrence_at_exactly_now_to_avoid_past_alarm() {
        // The startup reschedule passes a minute-truncated `now`. When the app
        // is reopened within the same minute a dose fired, the occurrence at
        // exactly `now` would otherwise be re-armed as an exact alarm in the
        // past, which AlarmManager fires immediately -> the reminder re-fires.
        val group = medicationGroup(
            uuid = UUID.fromString("b1f0c2d3-4e5f-4a6b-8c7d-9e0f1a2b3c4d"),
            name = "Daily estradiol",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true
        )

        val plans = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 9, 0)
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 4, 21, 9, 0)),
            plans.map { it.scheduledAt }
        )
    }

    @Test
    fun buildNextMedicationReminderPlans_scans_entire_90_day_window_for_next_unfulfilled_occurrence() {
        val group = medicationGroup(
            uuid = UUID.fromString("c873b736-521a-4f35-b207-258fd697c5fd"),
            name = "Daily estradiol",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true
        )
        val now = LocalDateTime.of(2026, 4, 20, 8, 30)
        val fulfilledEntries = (0 until 32).map { dayOffset ->
            val scheduledFor = LocalDateTime.of(
                LocalDate.of(2026, 4, 20).plusDays(dayOffset.toLong()),
                LocalTime.of(9, 0)
            )
            scheduledEntry(
                groupUuid = group.uuid,
                appliedAt = scheduledFor.plusMinutes(5),
                scheduledFor = scheduledFor
            )
        }

        val plans = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = fulfilledEntries,
            now = now
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 5, 22, 9, 0)),
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
                    medicine = testMedicine(
                        uuid = estradiolMedicineUuid,
                        key = MedicationKey.ESTRADIOL,
                    ),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
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
                    medicine = testMedicine(
                        uuid = estradiolMedicineUuid,
                        key = MedicationKey.ESTRADIOL,
                    ),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
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
            medicine = testMedicine(
                uuid = estradiolMedicineUuid,
                key = MedicationKey.ESTRADIOL,
            ),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor
        )
    }
}
