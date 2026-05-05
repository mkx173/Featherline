package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
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
    private val testZoneId: ZoneId = ZoneId.systemDefault()

    @Test
    fun buildMainE2Hero_keeps_mock_value_and_uses_latest_actual_estradiol_dose() {
        val latestEstradiolDoseTime = LocalDateTime.of(2026, 4, 18, 20, 5)
        val latestEstradiolDoseDetails = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL_VALERATE,
            applicationType = MedicationApplicationType.INJECTION,
            dose = MedicationDose.MgAsMedicine(5.0)
        )

        val hero = buildMainE2Hero(
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.SPIRONOLACTONE,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(100.0)
                    ),
                    dosageMgAsEstradiol = null,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(LocalDateTime.of(2026, 4, 18, 8, 0))
                ),
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    details = latestEstradiolDoseDetails,
                    dosageMgAsEstradiol = 3.82,
                    sourceGroupUuid = UUID.randomUUID(),
                    appliedAt = testInstant(latestEstradiolDoseTime)
                )
            ),
            zoneId = testZoneId
        )

        assertEquals(1145, hero.currentValue)
        assertEquals(14, hero.changeSinceYesterday)
        assertEquals(latestEstradiolDoseTime, hero.lastDoseAt)
        assertEquals(latestEstradiolDoseDetails, hero.lastDoseDetails)
    }

    @Test
    fun buildMainE2Chart_returns_placeholder_curve_that_ends_at_mock_value() {
        val chart = buildMainE2Chart()

        assertEquals(7, chart.points.size)
        assertEquals(100f, chart.points.last())
    }

    @Test
    fun buildMainAntiandrogenCards_returns_one_card_per_antiandrogen_with_real_last_and_next_values() {
        val antiandrogenGroup = MedicationGroup(
            uuid = UUID.fromString("efea64bc-0f6d-4813-a39d-428bdce6fd6a"),
            name = "Daily antiandrogen",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("96f31e44-2059-4f89-a3f4-5477cbbce4b3"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.SPIRONOLACTONE,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(100.0)
                    )
                ),
                testMedicationGroupMedication(
                    uuid = UUID.fromString("e1a7c6d6-6ef2-4c8b-9a56-3a4f4f1298ce"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.CYPROTERONE_ACETATE,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(12.5)
                    )
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val now = LocalDateTime.of(2026, 4, 18, 11, 0)
        val spiroLastDoseTime = LocalDateTime.of(2026, 4, 18, 8, 4)
        val cyproLastDoseTime = LocalDateTime.of(2026, 4, 17, 20, 2)
        val spiroActualDose = testCatalogMedicationDetails(
            key = MedicationKey.SPIRONOLACTONE,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(50.0)
        )

        val cards = buildMainAntiandrogenCards(
            groups = listOf(antiandrogenGroup),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    details = spiroActualDose,
                    dosageMgAsEstradiol = null,
                    sourceGroupUuid = antiandrogenGroup.uuid,
                    appliedAt = testInstant(spiroLastDoseTime),
                    scheduledFor = LocalDateTime.of(2026, 4, 18, 8, 0)
                ),
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.CYPROTERONE_ACETATE,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(12.5)
                    ),
                    dosageMgAsEstradiol = null,
                    sourceGroupUuid = antiandrogenGroup.uuid,
                    appliedAt = testInstant(cyproLastDoseTime),
                    scheduledFor = LocalDateTime.of(2026, 4, 17, 20, 0)
                )
            ),
            now = now,
            zoneId = testZoneId
        )

        assertEquals(2, cards.size)
        assertEquals(
            listOf(MedicationSelection.Catalog(MedicationKey.SPIRONOLACTONE), MedicationSelection.Catalog(MedicationKey.CYPROTERONE_ACETATE)),
            cards.map { it.medication.selection }
        )
        assertEquals(listOf(spiroLastDoseTime, cyproLastDoseTime), cards.map { it.lastDoseAt })
        assertEquals(
            listOf(
                MedicationDose.MgAsMedicine(50.0),
                MedicationDose.MgAsMedicine(12.5)
            ),
            cards.map { it.lastDoseDetails?.dose }
        )
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 18, 8, 0),
                LocalDateTime.of(2026, 4, 18, 8, 0)
            ),
            cards.map { it.nextDoseAt }
        )
        assertEquals(listOf(true, true), cards.map { it.isNextDosePastDue })
    }

    @Test
    fun buildMainAntiandrogenCards_skips_fulfilled_upcoming_due_slot() {
        val antiandrogenGroup = antiandrogenGroup(
            uuid = UUID.fromString("d4d0d2d5-3201-4bf1-84e0-64d95d37599d"),
            times = listOf(LocalTime.of(19, 30))
        )
        val now = LocalDateTime.of(2026, 4, 18, 19, 18)
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 19, 30)

        val cards = buildMainAntiandrogenCards(
            groups = listOf(antiandrogenGroup),
            entries = listOf(
                scheduledAntiandrogenEntry(
                    group = antiandrogenGroup,
                    appliedAt = now,
                    scheduledFor = scheduledFor
                )
            ),
            now = now,
            zoneId = testZoneId
        )

        assertEquals(LocalDateTime.of(2026, 4, 19, 19, 30), cards.single().nextDoseAt)
        assertEquals(false, cards.single().isNextDosePastDue)
    }

    @Test
    fun buildMainAntiandrogenCards_marks_due_slot_past_due_after_display_grace() {
        val antiandrogenGroup = antiandrogenGroup(
            uuid = UUID.fromString("1e0e149c-28bb-48e7-a037-64a57f571e57"),
            times = listOf(LocalTime.of(19, 30))
        )

        val cards = buildMainAntiandrogenCards(
            groups = listOf(antiandrogenGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 20, 31),
            zoneId = testZoneId
        )

        assertEquals(LocalDateTime.of(2026, 4, 18, 19, 30), cards.single().nextDoseAt)
        assertEquals(true, cards.single().isNextDosePastDue)
    }

    @Test
    fun buildMainAntiandrogenCards_returns_next_scheduled_slot_after_past_due_window_expires() {
        val antiandrogenGroup = antiandrogenGroup(
            uuid = UUID.fromString("c240fd51-2797-47dc-a6ff-ad2e22309641"),
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
        )

        val cards = buildMainAntiandrogenCards(
            groups = listOf(antiandrogenGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 15, 0),
            zoneId = testZoneId
        )

        assertEquals(LocalDateTime.of(2026, 4, 18, 20, 0), cards.single().nextDoseAt)
        assertEquals(false, cards.single().isNextDosePastDue)
    }

    @Test
    fun mainCompactElapsedTotalMinutes_counts_elapsed_minute_boundaries() {
        val appliedAt = LocalDateTime.of(2026, 4, 18, 19, 18, 50)

        assertEquals(0L, mainCompactElapsedTotalMinutes(appliedAt, LocalDateTime.of(2026, 4, 18, 19, 18)))
        assertEquals(1L, mainCompactElapsedTotalMinutes(appliedAt, LocalDateTime.of(2026, 4, 18, 19, 19)))
        assertEquals(2L, mainCompactElapsedTotalMinutes(appliedAt, LocalDateTime.of(2026, 4, 18, 19, 20)))
        assertEquals(3L, mainCompactElapsedTotalMinutes(appliedAt, LocalDateTime.of(2026, 4, 18, 19, 21)))
    }

    @Test
    fun buildMainAntiandrogenCards_ignores_non_antiandrogen_groups() {
        val estradiolGroup = medicationGroup(
            uuid = UUID.fromString("b0fd2bf8-91f8-4cf8-a1ec-5d9f666c25db"),
            name = "Daily estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0))
            )
        )

        assertEquals(
            emptyList<MainAntiandrogenCardUiState>(),
            buildMainAntiandrogenCards(
                groups = listOf(estradiolGroup),
                entries = emptyList(),
                now = LocalDateTime.of(2026, 4, 18, 11, 0)
            )
        )
    }

    @Test
    fun buildMainAntiandrogenCards_collapses_duplicate_matching_rows_into_one_counted_card() {
        val sharedDetails = testCatalogMedicationDetails(
            key = MedicationKey.SPIRONOLACTONE,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(50.0)
        )
        val antiandrogenGroup = MedicationGroup(
            uuid = UUID.fromString("0f32c3be-a63a-4a2f-aebd-f9c9bc2df942"),
            name = "Night blocker",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(22, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("725d4c60-1684-4c8f-b786-4d7df50a6400"),
                    details = sharedDetails
                ),
                testMedicationGroupMedication(
                    uuid = UUID.fromString("74c29e3e-724d-4f3c-996f-6f4195ae7904"),
                    details = sharedDetails
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )

        val cards = buildMainAntiandrogenCards(
            groups = listOf(antiandrogenGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 11, 0),
            zoneId = testZoneId
        )

        assertEquals(1, cards.size)
        assertEquals(2, cards.single().medication.count)
    }

    @Test
    fun buildMainAntiandrogenCards_orders_groups_by_oldest_creation_time_first() {
        val olderGroup = medicationGroup(
            uuid = UUID.fromString("c806d3bb-61a6-4efa-883a-99b03cc503b8"),
            name = "Older blocker",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("bc722321-7c11-4318-a7d5-cbd712ad1621"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.SPIRONOLACTONE,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(100.0)
                    )
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val newerGroup = medicationGroup(
            uuid = UUID.fromString("d1e9086f-a911-46d6-a941-fb390dc09f9a"),
            name = "Newer blocker",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("c6d2a3e6-5f5f-4cb7-9e5b-3e6634745f1b"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.CYPROTERONE_ACETATE,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(12.5)
                    )
                )
            ),
            createdAt = Instant.parse("2026-04-10T00:00:00Z")
        )

        val cards = buildMainAntiandrogenCards(
            groups = listOf(newerGroup, olderGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 11, 0),
            zoneId = testZoneId
        )

        assertEquals(listOf("Older blocker", "Newer blocker"), cards.map { it.groupName })
    }

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
        assertEquals(
            listOf(
                MedicationGroupColorKey.ORCHID,
                MedicationGroupColorKey.ORCHID,
                MedicationGroupColorKey.ORCHID,
                MedicationGroupColorKey.ORCHID,
            ),
            todaySection.rows.map { it.groupColorKey }
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
        assertEquals(
            listOf(MedicationGroupColorKey.ORCHID, MedicationGroupColorKey.ORCHID),
            upcomingSection.rows.map { it.groupColorKey }
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
        schedule: MedicationGroupSchedule,
        medications: List<com.mkx.hrttracker.model.medication.MedicationGroupMedication> = listOf(
            testMedicationGroupMedication(
                uuid = UUID.randomUUID(),
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    dose = MedicationDose.MgAsMedicine(2.0)
                )
            )
        ),
        createdAt: Instant = Instant.parse("2026-04-01T00:00:00Z")
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = name,
            colorKey = MedicationGroupColorKey.ORCHID,
            schedule = schedule,
            medications = medications,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    private fun antiandrogenGroup(
        uuid: UUID,
        times: List<LocalTime>
    ): MedicationGroup {
        return medicationGroup(
            uuid = uuid,
            name = "Daily antiandrogen",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = times
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.randomUUID(),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.SPIRONOLACTONE,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(100.0)
                    )
                )
            )
        )
    }

    private fun scheduledAntiandrogenEntry(
        group: MedicationGroup,
        appliedAt: LocalDateTime,
        scheduledFor: LocalDateTime
    ): MedicationLogEntry {
        return testMedicationLogEntry(
            uuid = UUID.randomUUID(),
            details = group.medications.single().details,
            dosageMgAsEstradiol = null,
            sourceGroupUuid = group.uuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor
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
