package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class PlanDayOccurrenceTest {
    @Test
    fun buildPlanDaySchedule_marks_slots_within_strictSubHourGrace_as_dueSoon() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(10, 30))
            )
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 10, 0)
        )

        assertFalse(schedule.scheduledEntries[0].isDueSoon)
        assertTrue(schedule.scheduledEntries[0].isPastDue)
        assertFalse(schedule.scheduledEntries[1].isDueSoon)
        assertTrue(schedule.scheduledEntries[1].isPastDue)
        assertTrue(schedule.scheduledEntries[2].isDueSoon)
        assertFalse(schedule.scheduledEntries[2].isPastDue)
        assertEquals(MedicationGroupColorKey.TEAL, schedule.scheduledEntries[0].groupColorKey)
    }

    @Test
    fun dueSoonAndPastDue_useStrictSubHourGracePeriodAroundScheduledTime() {
        val scheduledAt = LocalDateTime.of(2026, 4, 18, 9, 0)

        assertTrue(isDueSoon(scheduledAt, scheduledAt.minusHours(1).plusSeconds(1)))
        assertTrue(isDueSoon(scheduledAt, scheduledAt.plusHours(1).minusSeconds(1)))
        assertFalse(isDueSoon(scheduledAt, scheduledAt.minusHours(1)))
        assertFalse(isDueSoon(scheduledAt, scheduledAt.plusHours(1)))
        assertFalse(isPastDue(scheduledAt, scheduledAt.plusHours(1).minusSeconds(1)))
        assertTrue(isPastDue(scheduledAt, scheduledAt.plusHours(1)))
    }

    @Test
    fun buildPlanDaySchedule_skips_slots_before_group_creation_time_on_start_day() {
        val zoneId = ZoneId.of("UTC")
        val savedAt = LocalDateTime.of(2026, 4, 18, 10, 0)
        val createdAt = savedAt
            .atZone(zoneId)
            .toInstant()
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 18),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0), LocalTime.of(11, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTime(
                        uuid = UUID.randomUUID(),
                        time = LocalTime.of(9, 0),
                        effectiveFrom = savedAt,
                    ),
                    MedicationGroupScheduleTime(
                        uuid = UUID.randomUUID(),
                        time = LocalTime.of(11, 0),
                        effectiveFrom = savedAt,
                    ),
                ),
            )
        ).copy(
            createdAt = createdAt,
            updatedAt = createdAt,
            includePastScheduledSlots = false,
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 10, 15),
            zoneId = zoneId,
        )

        assertEquals(listOf(LocalTime.of(11, 0)), schedule.scheduledEntries.map { it.scheduledTime })
    }

    @Test
    fun buildPlanDaySchedule_includes_slots_before_group_creation_when_schedule_history_enabled() {
        val zoneId = ZoneId.of("UTC")
        val createdAt = LocalDateTime.of(2026, 4, 18, 10, 0)
            .atZone(zoneId)
            .toInstant()
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 18),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0), LocalTime.of(11, 0))
            )
        ).copy(
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 10, 15),
            zoneId = zoneId,
        )

        assertEquals(
            listOf(LocalTime.of(9, 0), LocalTime.of(11, 0)),
            schedule.scheduledEntries.map { it.scheduledTime },
        )
    }

    @Test
    fun buildPlanDaySchedule_archiveAndRecreate_savedAsIs_doesNotDuplicateTodaysSlots() {
        val zoneId = ZoneId.of("UTC")
        val savedAt = LocalDateTime.of(2026, 4, 18, 10, 0)
        val savedAtInstant = savedAt.atZone(zoneId).toInstant()
        val originalUuid = UUID.fromString("a1c1c10c-3a47-4f64-aa10-0d59ba2a5b21")
        val recreatedUuid = UUID.fromString("b2d2d20d-4b58-5077-bb21-1e6acb3b6c32")
        val baseGroup = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(21, 0))
            )
        )
        val archivedOriginal = baseGroup.copy(
            uuid = originalUuid,
            archivedAt = savedAtInstant,
            archivedAtLocal = savedAt,
        )
        val recreated = baseGroup.copy(
            uuid = recreatedUuid,
            schedule = baseGroup.schedule.copy(
                timeSlots = listOf(
                    MedicationGroupScheduleTime(
                        uuid = UUID.randomUUID(),
                        time = LocalTime.of(8, 0),
                        effectiveFrom = savedAt,
                    ),
                    MedicationGroupScheduleTime(
                        uuid = UUID.randomUUID(),
                        time = LocalTime.of(13, 0),
                        effectiveFrom = savedAt,
                    ),
                    MedicationGroupScheduleTime(
                        uuid = UUID.randomUUID(),
                        time = LocalTime.of(21, 0),
                        effectiveFrom = savedAt,
                    ),
                ),
            ),
            createdAt = savedAtInstant,
            updatedAt = savedAtInstant,
            includePastScheduledSlots = false,
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(archivedOriginal, recreated),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 10, 5),
            zoneId = zoneId,
        )

        val times = schedule.scheduledEntries.map { entry -> entry.scheduledTime }
        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(21, 0)),
            times,
        )
        val ownerByTime = schedule.scheduledEntries.associate { entry ->
            entry.scheduledTime to entry.groupUuid
        }
        assertEquals(originalUuid, ownerByTime.getValue(LocalTime.of(8, 0)))
        assertEquals(recreatedUuid, ownerByTime.getValue(LocalTime.of(13, 0)))
        assertEquals(recreatedUuid, ownerByTime.getValue(LocalTime.of(21, 0)))
    }

    @Test
    fun buildPlanDaySchedule_orders_planned_slots_by_time_groupCreation_andMedicationOrder() {
        val zoneId = ZoneId.of("UTC")
        val schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 4, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(9, 0))
        )
        val olderGroupUuid = UUID.fromString("e93b0560-cf66-4460-b7f3-265cfda912ed")
        val newerGroupUuid = UUID.fromString("f9d93184-9c55-4ca0-a645-a26bdebe1e3f")
        val olderFirstMedicationUuid = UUID.fromString("cc92195a-fd7d-4b45-9190-c088de6fe424")
        val olderSecondMedicationUuid = UUID.fromString("a9b8b10e-f3ab-4f7c-949e-8e67c52f56e9")
        val newerMedicationUuid = UUID.fromString("f75b7cdb-bf8b-4923-9091-baf5bc5de395")
        val olderGroup = medicationGroup(schedule).copy(
            uuid = olderGroupUuid,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = olderFirstMedicationUuid,
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.SPIRONOLACTONE,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(100.0)
                    )
                ),
                testMedicationGroupMedication(
                    uuid = olderSecondMedicationUuid,
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0)
                    )
                )
            )
        )
        val newerGroup = medicationGroup(schedule).copy(
            uuid = newerGroupUuid,
            createdAt = Instant.parse("2026-04-02T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-02T00:00:00Z"),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = newerMedicationUuid,
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.CYPROTERONE_ACETATE,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(12.5)
                    )
                )
            )
        )

        val daySchedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(newerGroup, olderGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 7, 0),
            zoneId = zoneId
        )

        assertEquals(
            listOf(olderGroupUuid, olderGroupUuid, newerGroupUuid),
            daySchedule.scheduledEntries.map { entry -> entry.groupUuid }
        )
        assertEquals(
            listOf(olderFirstMedicationUuid, olderSecondMedicationUuid, newerMedicationUuid),
            daySchedule.scheduledEntries.map { entry -> entry.medication.uuid }
        )
    }

    @Test
    fun buildPlanDaySchedule_orders_manual_entries_by_logged_time() {
        val zoneId = ZoneId.of("UTC")
        val morningEntryUuid = UUID.fromString("81010725-e95a-4055-992d-5bb10e307e98")
        val eveningEntryUuid = UUID.fromString("e566ac73-c1f7-45e6-af71-cf5d8f511456")
        val details = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(2.0)
        )

        val daySchedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = emptyList(),
            entries = listOf(
                com.mkx.hrttracker.model.medication.testMedicationLogEntry(
                    uuid = eveningEntryUuid,
                    details = details,
                    dosageMgAsEstradiol = 2.0,
                    sourceGroupUuid = null,
                    appliedAt = LocalDateTime.of(2026, 4, 18, 21, 0).atZone(zoneId).toInstant(),
                    appliedAtTimeZoneId = zoneId.id,
                ),
                com.mkx.hrttracker.model.medication.testMedicationLogEntry(
                    uuid = morningEntryUuid,
                    details = details,
                    dosageMgAsEstradiol = 2.0,
                    sourceGroupUuid = null,
                    appliedAt = LocalDateTime.of(2026, 4, 18, 8, 0).atZone(zoneId).toInstant(),
                    appliedAtTimeZoneId = zoneId.id,
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 22, 0),
            zoneId = zoneId
        )

        assertEquals(
            listOf(morningEntryUuid, eveningEntryUuid),
            daySchedule.unplannedEntries.map { entry -> entry.uuid }
        )
    }

    @Test
    fun buildPlanDaySchedule_shows_logged_slot_before_group_creation_as_planned() {
        val zoneId = ZoneId.of("UTC")
        val createdAt = LocalDateTime.of(2026, 4, 18, 10, 0)
            .atZone(zoneId)
            .toInstant()
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            )
        ).copy(
            createdAt = createdAt,
            updatedAt = createdAt,
        )
        val loggedSlot = com.mkx.hrttracker.model.medication.testMedicationLogEntry(
            details = group.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = LocalDateTime.of(2026, 4, 17, 9, 5)
                .atZone(zoneId)
                .toInstant(),
            scheduledFor = LocalDateTime.of(2026, 4, 17, 9, 0)
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 17),
            groups = listOf(group),
            entries = listOf(loggedSlot),
            now = LocalDateTime.of(2026, 4, 18, 10, 15),
            zoneId = zoneId,
        )

        assertEquals(1, schedule.scheduledEntries.size)
        assertTrue(schedule.scheduledEntries.single().isFulfilled)
        assertTrue(schedule.unplannedEntries.isEmpty())
    }

    @Test
    fun buildPlanDaySchedule_shows_logged_slot_outside_effective_window_as_planned() {
        val scheduleTimeUuid = UUID.fromString("16d63c4d-10d2-4bc3-9f0a-934fd5aa7c74")
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
            )
        )
        val loggedSlot = com.mkx.hrttracker.model.medication.testMedicationLogEntry(
            details = group.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = LocalDateTime.of(2026, 4, 18, 9, 5)
                .atZone(ZoneId.systemDefault())
                .toInstant(),
            scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0),
        ).copy(scheduleTimeUuid = scheduleTimeUuid)

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = listOf(loggedSlot),
            now = LocalDateTime.of(2026, 4, 18, 10, 15),
        )

        assertEquals(1, schedule.scheduledEntries.size)
        assertEquals(LocalTime.of(9, 0), schedule.scheduledEntries.single().scheduledTime)
        assertTrue(schedule.scheduledEntries.single().isFulfilled)
        assertTrue(schedule.unplannedEntries.isEmpty())
    }

    @Test
    fun buildPlanDaySchedule_uses_scheduleTimeUuid_when_locked_time_shift_keeps_old_scheduledFor() {
        val scheduleTimeUuid = UUID.fromString("9b2fef06-05d9-4c27-8126-114fe09417a6")
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
                        effectiveFrom = LocalDate.of(2026, 4, 1).atStartOfDay(),
                    )
                ),
            )
        )
        val shiftedSlotLog = com.mkx.hrttracker.model.medication.testMedicationLogEntry(
            details = group.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = LocalDateTime.of(2026, 4, 18, 8, 5)
                .atZone(ZoneId.systemDefault())
                .toInstant(),
            scheduledFor = LocalDateTime.of(2026, 4, 18, 8, 0),
        ).copy(scheduleTimeUuid = scheduleTimeUuid)

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = listOf(shiftedSlotLog),
            now = LocalDateTime.of(2026, 4, 18, 10, 0),
        )

        assertEquals(1, schedule.scheduledEntries.size)
        assertEquals(LocalTime.of(9, 0), schedule.scheduledEntries.single().scheduledTime)
        assertEquals(listOf(shiftedSlotLog.uuid), schedule.scheduledEntries.single().fulfillingEntryUuids)
        assertTrue(schedule.scheduledEntries.single().isFulfilled)
        assertTrue(schedule.unplannedEntries.isEmpty())
    }

    @Test
    fun buildPlanDaySchedule_does_not_mark_fulfilled_slot_as_past_due() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            )
        )
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)
        val fulfilledEntry = com.mkx.hrttracker.model.medication.testMedicationLogEntry(
            details = group.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = scheduledFor.plusMinutes(3).toLocalDate().atTime(9, 3)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant(),
            scheduledFor = scheduledFor
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = listOf(fulfilledEntry),
            now = LocalDateTime.of(2026, 4, 18, 10, 0)
        )

        assertTrue(schedule.scheduledEntries.single().isFulfilled)
        assertEquals(LocalDateTime.of(2026, 4, 18, 9, 3), schedule.scheduledEntries.single().loggedAt)
        assertFalse(schedule.scheduledEntries.single().isPastDue)
        assertFalse(schedule.scheduledEntries.single().isDueSoon)
    }

    @Test
    fun buildPlanDaySchedule_matches_planned_record_when_applied_after_midnight() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(23, 0))
            )
        )
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 23, 0)
        val fulfilledEntry = com.mkx.hrttracker.model.medication.testMedicationLogEntry(
            details = group.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = LocalDateTime.of(2026, 4, 19, 0, 15)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant(),
            scheduledFor = scheduledFor
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = listOf(fulfilledEntry),
            now = LocalDateTime.of(2026, 4, 19, 1, 0)
        )

        assertTrue(schedule.scheduledEntries.single().isFulfilled)
        assertEquals(LocalDateTime.of(2026, 4, 19, 0, 15), schedule.scheduledEntries.single().loggedAt)
        assertTrue(schedule.unplannedEntries.isEmpty())
    }

    @Test
    fun buildPlanDaySchedule_shows_archived_linked_record_as_group_logged_entry() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            )
        ).copy(
            archivedAt = Instant.parse("2026-04-20T00:00:00Z")
        )
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)
        val linkedEntry = com.mkx.hrttracker.model.medication.testMedicationLogEntry(
            details = group.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = LocalDateTime.of(2026, 4, 18, 9, 4)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant(),
            scheduledFor = scheduledFor
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = listOf(linkedEntry),
            now = LocalDateTime.of(2026, 4, 18, 10, 0)
        )

        assertTrue(schedule.unplannedEntries.isEmpty())
        assertEquals(1, schedule.scheduledEntries.size)
        assertEquals(group.name, schedule.scheduledEntries.single().groupName)
        assertEquals(group.colorKey, schedule.scheduledEntries.single().groupColorKey)
        assertEquals(listOf(linkedEntry.uuid), schedule.scheduledEntries.single().fulfillingEntryUuids)
        assertTrue(schedule.scheduledEntries.single().isFulfilled)
    }

    @Test
    fun buildPlanDaySchedule_collapses_duplicate_matching_medications_into_one_counted_entry() {
        val sharedDetails = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(2.0)
        )
        val group = MedicationGroup(
            uuid = UUID.fromString("3ff64a14-e1fd-4900-b804-f298d9e5e504"),
            name = "Test group",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("43f8f777-86eb-4193-94b8-cf5a3441bc3f"),
                    details = sharedDetails
                ),
                testMedicationGroupMedication(
                    uuid = UUID.fromString("37d67438-f3a9-42ba-a3b8-57a005063b0f"),
                    details = sharedDetails
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 8, 0)
        )

        assertEquals(1, schedule.scheduledEntries.size)
        assertEquals(2, schedule.scheduledEntries.single().medication.count)
    }

    @Test
    fun buildPlanDaySchedule_uses_single_counted_log_row_for_fulfillment_progress() {
        val group = MedicationGroup(
            uuid = UUID.fromString("77365b1a-aa5d-427e-9313-0c56241ecbaa"),
            name = "Test group",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("258ae865-c7d2-44ef-8cf2-3257451f57d1"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0)
                    ),
                    count = 2
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val countedEntryId = UUID.fromString("5a09f5d0-4f91-44d7-8f51-9ff0e9b1498a")
        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = listOf(
                com.mkx.hrttracker.model.medication.testMedicationLogEntry(
                    uuid = countedEntryId,
                    details = group.medications.single().details,
                    dosageMgAsEstradiol = 2.0,
                    sourceGroupUuid = group.uuid,
                    appliedAt = LocalDateTime.of(2026, 4, 18, 9, 3)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant(),
                    scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0),
                    count = 2
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 10, 0)
        )

        val scheduledEntry = schedule.scheduledEntries.single()
        assertEquals(2, scheduledEntry.loggedCount)
        assertEquals(listOf(countedEntryId), scheduledEntry.fulfillingEntryUuids)
        assertEquals(LocalDateTime.of(2026, 4, 18, 9, 3), scheduledEntry.loggedAt)
        assertTrue(scheduledEntry.isFulfilled)
    }

    @Test
    fun buildPlanDaySchedule_keeps_far_group_entry_on_scheduled_row_without_fulfilling_slot() {
        val group = medicationGroup(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0), LocalTime.of(11, 0))
            )
        )
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)
        val linkedEntry = com.mkx.hrttracker.model.medication.testMedicationLogEntry(
            details = group.medications.single().details,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = LocalDateTime.of(2026, 4, 18, 10, 1)
                .atZone(ZoneId.systemDefault())
                .toInstant(),
            scheduledFor = scheduledFor
        )

        val schedule = buildPlanDaySchedule(
            date = LocalDate.of(2026, 4, 18),
            groups = listOf(group),
            entries = listOf(linkedEntry),
            now = LocalDateTime.of(2026, 4, 18, 10, 30)
        )

        val firstScheduledEntry = schedule.scheduledEntries.first()
        assertFalse(firstScheduledEntry.isFulfilled)
        assertTrue(firstScheduledEntry.fulfillingEntryUuids.isEmpty())
        assertEquals(listOf(linkedEntry.uuid), firstScheduledEntry.outsideScheduleWindowEntryUuids)
        assertEquals(LocalDateTime.of(2026, 4, 18, 10, 1), firstScheduledEntry.outsideScheduleWindowLoggedAt)
        assertTrue(schedule.unplannedEntries.isEmpty())
    }

    @Test
    fun plannedEntryEditorIds_returns_single_backing_id_for_counted_fulfilled_entry() {
        val countedEntryId = UUID.fromString("5a09f5d0-4f91-44d7-8f51-9ff0e9b1498a")
        val scheduledEntry = PlanDayScheduleEntry(
            groupUuid = UUID.fromString("77365b1a-aa5d-427e-9313-0c56241ecbaa"),
            groupName = "Test group",
            groupColorKey = MedicationGroupColorKey.TEAL,
            scheduledTime = LocalTime.of(9, 0),
            medication = testMedicationGroupMedication(
                uuid = UUID.fromString("258ae865-c7d2-44ef-8cf2-3257451f57d1"),
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    dose = MedicationDose.MgAsMedicine(2.0)
                ),
                count = 2
            ),
            fulfillingEntryUuids = listOf(countedEntryId),
            loggedCount = 2,
            isFulfilled = true,
            isDueSoon = false,
            isPastDue = false
        )

        assertEquals(setOf(countedEntryId), plannedEntryEditorIds(scheduledEntry))
    }

    @Test
    fun plannedEntryEditorIds_returns_outside_schedule_window_entry_id() {
        val outsideWindowEntryId = UUID.fromString("6e5b7213-dbb4-443b-9446-e4751c16a941")
        val scheduledEntry = PlanDayScheduleEntry(
            groupUuid = UUID.fromString("77365b1a-aa5d-427e-9313-0c56241ecbaa"),
            groupName = "Test group",
            groupColorKey = MedicationGroupColorKey.TEAL,
            scheduledTime = LocalTime.of(9, 0),
            medication = testMedicationGroupMedication(
                uuid = UUID.fromString("258ae865-c7d2-44ef-8cf2-3257451f57d1"),
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    dose = MedicationDose.MgAsMedicine(2.0)
                )
            ),
            fulfillingEntryUuids = emptyList(),
            outsideScheduleWindowEntryUuids = listOf(outsideWindowEntryId),
            isFulfilled = false,
            isDueSoon = false,
            isPastDue = true
        )

        assertEquals(setOf(outsideWindowEntryId), plannedEntryEditorIds(scheduledEntry))
    }

    private fun medicationGroup(schedule: MedicationGroupSchedule): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString("77365b1a-aa5d-427e-9313-0c56241ecbaa"),
            name = "Test group",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = schedule,
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("258ae865-c7d2-44ef-8cf2-3257451f57d1"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0)
                    )
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
    }
}
