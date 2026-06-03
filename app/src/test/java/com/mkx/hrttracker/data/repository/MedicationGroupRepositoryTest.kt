package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationGroupDao
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWeeklyDayEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.data.local.MedicationLogDao
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class MedicationGroupRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val medicationGroupDao: MedicationGroupDao = mockk(relaxed = true)
    private val medicationLogDao: MedicationLogDao = mockk(relaxed = true)
    private val medicineDao: MedicineDao = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true)

    private lateinit var repository: MedicationGroupRepository

    @Before
    fun setUp() {
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        every { databaseHolder.get() } returns database
        every { database.medicationGroupDao() } returns medicationGroupDao
        every { database.medicationLogDao() } returns medicationLogDao
        every { database.medicineDao() } returns medicineDao
        coEvery { medicineDao.getByUuids(any()) } answers {
            firstArg<List<String>>().map { uuid ->
                testMedicineEntity(
                    uuid = uuid,
                    medicationKey = MedicationKey.ESTRADIOL,
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                )
            }
        }
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }

        repository = MedicationGroupRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            appScope = CoroutineScope(StandardTestDispatcher()),
        )
    }

    @Test
    fun deleteGroup_reclassifiesEntriesAndDeletesGroupInSingleTransaction() = runTest {
        val groupUuid = UUID.fromString("14f6c652-a26d-4b68-ac54-c70cbec929d9")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationLogDao.reclassifyEntriesForDeletedGroup(groupUuid.toString()) } returns Unit
        coEvery { medicationGroupDao.deleteGroup(groupUuid.toString()) } returns Unit

        repository.deleteGroup(groupUuid)

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerifyOrder {
            medicationLogDao.reclassifyEntriesForDeletedGroup(groupUuid.toString())
            medicationGroupDao.deleteGroup(groupUuid.toString())
        }
        coVerify(exactly = 1) { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) }
    }

    @Test
    fun deleteGroup_runsWritesInsideHomeDataMutation() = runTest {
        val groupUuid = UUID.fromString("14f6c652-a26d-4b68-ac54-c70cbec929d9")
        val events = mutableListOf<String>()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            events += "mutation-start"
            firstArg<suspend () -> Unit>().invoke()
            events += "mutation-end"
        }
        coEvery { medicationLogDao.reclassifyEntriesForDeletedGroup(groupUuid.toString()) } coAnswers {
            events += "write-log"
        }
        coEvery { medicationGroupDao.deleteGroup(groupUuid.toString()) } coAnswers {
            events += "write-group"
        }

        repository.deleteGroup(groupUuid)

        assertEquals(listOf("mutation-start", "write-log", "write-group", "mutation-end"), events)
    }

    @Test
    fun deleteGroupAndRelatedEntries_deletesEntriesAndGroupInSingleTransaction() = runTest {
        val groupUuid = UUID.fromString("f2f8890f-09ab-4775-85cb-cf4aa896f0b7")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationLogDao.deleteEntriesForGroup(groupUuid.toString()) } returns Unit
        coEvery { medicationGroupDao.deleteGroup(groupUuid.toString()) } returns Unit

        repository.deleteGroupAndRelatedEntries(groupUuid)

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerifyOrder {
            medicationLogDao.deleteEntriesForGroup(groupUuid.toString())
            medicationGroupDao.deleteGroup(groupUuid.toString())
        }
        coVerify(exactly = 1) { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) }
    }

    @Test
    fun archiveGroup_setsEndOfDayCutoffWithoutTouchingNotificationsInSingleTransaction() = runTest {
        val groupUuid = UUID.fromString("38789ce3-9978-402c-8fd5-e660d436b8c4")
        val now = Instant.parse("2026-04-30T08:00:45Z")
        val archivedThroughDate = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val expectedCutoffLocalIso = archivedThroughDate.atTime(LocalTime.MAX).toString()
        val expectedCutoffEpochMillis = archivedThroughDate.atTime(LocalTime.MAX)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(9, 0)),
        )
        coEvery { medicationLogDao.getEntriesForGroup(groupUuid.toString()) } returns emptyList()
        coEvery {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = expectedCutoffEpochMillis,
                archivedAtLocalIso = expectedCutoffLocalIso,
                updatedAtEpochMillis = now.toEpochMilli(),
            )
        } returns Unit

        repository.archiveGroup(
            uuid = groupUuid,
            archivedThroughDate = archivedThroughDate,
            now = now,
        )

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerify(exactly = 1) {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = expectedCutoffEpochMillis,
                archivedAtLocalIso = expectedCutoffLocalIso,
                updatedAtEpochMillis = now.toEpochMilli(),
            )
        }
    }

    @Test
    fun archiveGroup_blocksCurrentOrFuturePlannedSlots() = runTest {
        val groupUuid = UUID.fromString("81811bc8-ee30-439f-817b-2297bbeac486")
        val now = Instant.parse("2026-04-30T08:00:45Z")
        val expectedArchiveAttemptLocal = now.atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .truncatedTo(ChronoUnit.MINUTES)
            .toString()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(9, 0)),
        )
        coEvery {
            medicationLogDao.getCurrentOrFuturePlannedSlotCountForGroup(
                groupUuid = groupUuid.toString(),
                onOrAfterScheduledForIso = expectedArchiveAttemptLocal,
            )
        } returns 1

        try {
            repository.archiveGroup(
                uuid = groupUuid,
                archivedThroughDate = now.atZone(ZoneId.systemDefault()).toLocalDate(),
                now = now,
            )
            fail("Expected current or future planned slots to block archiving")
        } catch (_: CurrentOrFuturePlannedSlotsBlockArchiveException) {
        }

        coVerify(exactly = 0) {
            medicationGroupDao.updateGroupArchiveState(any(), any(), any(), any())
        }
    }

    @Test
    fun archiveGroup_withBackdatedDateWritesEndOfDayCutoffAndMutationNow() = runTest {
        val groupUuid = UUID.fromString("38789ce3-9978-402c-8fd5-e660d436b8c4")
        val now = Instant.parse("2026-04-30T08:00:45Z")
        val archivedThroughDate = LocalDate.of(2026, 4, 25)
        val systemZone = ZoneId.systemDefault()
        val archiveCutoffLocal = LocalDateTime.of(2026, 4, 25, 23, 59, 59, 999_999_999)
        val archiveCutoffEpochMillis = archiveCutoffLocal.atZone(systemZone).toInstant().toEpochMilli()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(9, 0)),
        )
        coEvery { medicationLogDao.getEntriesForGroup(groupUuid.toString()) } returns emptyList()
        coEvery {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = archiveCutoffEpochMillis,
                archivedAtLocalIso = archiveCutoffLocal.toString(),
                updatedAtEpochMillis = now.toEpochMilli(),
            )
        } returns Unit

        repository.archiveGroup(
            uuid = groupUuid,
            archivedThroughDate = archivedThroughDate,
            now = now,
        )

        coVerify(exactly = 1) {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = archiveCutoffEpochMillis,
                archivedAtLocalIso = archiveCutoffLocal.toString(),
                updatedAtEpochMillis = now.toEpochMilli(),
            )
        }
    }

    @Test
    fun archiveGroup_withNullDateArchivesAsOfNowMinute() = runTest {
        // The default (no explicit date) archives as of `now` at minute
        // granularity — the original archive behavior — not end of day. This
        // is the "Now" option in the dialog; the cutoff is bound to the
        // confirm-time instant, independent of any calendar day.
        val groupUuid = UUID.fromString("d1ffabcd-0000-4000-8000-000000000002")
        val now = Instant.parse("2026-06-03T08:00:45Z")
        val expectedNowLocalIso = now.atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .truncatedTo(ChronoUnit.MINUTES)
            .toString()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(9, 0)),
        )
        coEvery { medicationLogDao.getEntriesForGroup(groupUuid.toString()) } returns emptyList()

        repository.archiveGroup(uuid = groupUuid, archivedThroughDate = null, now = now)

        coVerify(exactly = 1) {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = now.toEpochMilli(),
                archivedAtLocalIso = expectedNowLocalIso,
                updatedAtEpochMillis = now.toEpochMilli(),
            )
        }
    }

    @Test
    fun archiveGroup_rejectsDateBeforeScheduleStartWhenNoEntriesExist() = runTest {
        val groupUuid = UUID.fromString("8ec6339d-3b83-464c-ac61-e36f7dc9ee3f")
        val now = Instant.parse("2026-04-30T08:00:45Z")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(9, 0)),
            scheduleSinceEpochDay = LocalDate.of(2026, 4, 20).toEpochDay(),
        )
        coEvery { medicationLogDao.getEntriesForGroup(groupUuid.toString()) } returns emptyList()

        try {
            repository.archiveGroup(
                uuid = groupUuid,
                archivedThroughDate = LocalDate.of(2026, 4, 19),
                now = now,
            )
            fail("Expected archive date before schedule start to be rejected")
        } catch (expected: ArchiveDateBeforeRecordedDoseException) {
            assertTrue(expected.message.orEmpty().contains("before minimum archive date"))
        }

        coVerify(exactly = 0) {
            medicationGroupDao.updateGroupArchiveState(any(), any(), any(), any())
        }
    }

    @Test
    fun archiveGroup_allowsBackdateToScheduleStartWhenGroupCreatedAfterStart() = runTest {
        // Backfilled plan: the schedule starts Jun 1 but the group row was
        // created today (Jun 3, e.g. backdated start at creation). Archiving
        // through the plan's start day must be allowed — the floor is the
        // schedule start, not the wall-clock creation timestamp. Regression for
        // "cannot select days other than today" on a backdated-start group.
        val groupUuid = UUID.fromString("c0ffee00-0000-4000-8000-000000000001")
        val now = Instant.parse("2026-06-03T08:00:45Z")
        val scheduleSince = LocalDate.of(2026, 6, 1)
        val createdToday = LocalDate.of(2026, 6, 3)
        val selectedDate = LocalDate.of(2026, 6, 1)
        val expectedCutoffLocalIso = selectedDate.atTime(LocalTime.MAX).toString()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(9, 0)),
            scheduleSinceEpochDay = scheduleSince.toEpochDay(),
            createdAtEpochMillis = createdToday.atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli(),
        )
        coEvery { medicationLogDao.getEntriesForGroup(groupUuid.toString()) } returns listOf(
            testMedicationLogEntryEntity(
                groupUuid = groupUuid,
                appliedAt = Instant.parse("2026-06-01T00:30:00Z"),
                appliedAtTimeZoneId = ZoneId.systemDefault().id,
                scheduledForIso = "2026-06-01T09:00",
            )
        )

        repository.archiveGroup(
            uuid = groupUuid,
            archivedThroughDate = selectedDate,
            now = now,
        )

        coVerify(exactly = 1) {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = any(),
                archivedAtLocalIso = expectedCutoffLocalIso,
                updatedAtEpochMillis = now.toEpochMilli(),
            )
        }
    }

    @Test
    fun archiveGroup_rejectsBackdateBeforeAdHocEntryStoredZoneDate() = runTest {
        val groupUuid = UUID.fromString("944e6041-c249-401f-abaf-5e6795c88967")
        val now = Instant.parse("2026-05-10T08:00:45Z")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(9, 0)),
        )
        coEvery { medicationLogDao.getEntriesForGroup(groupUuid.toString()) } returns listOf(
            testMedicationLogEntryEntity(
                groupUuid = groupUuid,
                appliedAt = Instant.parse("2026-05-08T15:30:00Z"),
                appliedAtTimeZoneId = "Asia/Tokyo",
                scheduledForIso = null,
            )
        )

        try {
            repository.archiveGroup(
                uuid = groupUuid,
                archivedThroughDate = LocalDate.of(2026, 5, 8),
                now = now,
            )
            fail("Expected backdate before ad-hoc entry date to be rejected")
        } catch (_: ArchiveDateBeforeRecordedDoseException) {
        }

        coVerify(exactly = 0) {
            medicationGroupDao.updateGroupArchiveState(any(), any(), any(), any())
        }
    }

    @Test
    fun archiveGroup_rejectsBackdateBeforePastPlannedDoseScheduledAfterSelectedDate() = runTest {
        val groupUuid = UUID.fromString("b3c1e0f2-6a4d-4f1c-9b2e-1d3a5c7e9f01")
        val now = Instant.parse("2026-05-10T08:00:45Z")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(9, 0)),
        )
        coEvery { medicationLogDao.getEntriesForGroup(groupUuid.toString()) } returns listOf(
            testMedicationLogEntryEntity(
                groupUuid = groupUuid,
                appliedAt = Instant.parse("2026-05-09T00:00:00Z"),
                appliedAtTimeZoneId = ZoneId.systemDefault().id,
                scheduledForIso = "2026-05-09T09:00",
            )
        )

        try {
            repository.archiveGroup(
                uuid = groupUuid,
                archivedThroughDate = LocalDate.of(2026, 5, 8),
                now = now,
            )
            fail("Expected backdate before a logged scheduled dose date to be rejected")
        } catch (_: ArchiveDateBeforeRecordedDoseException) {
        }

        coVerify(exactly = 0) {
            medicationGroupDao.updateGroupArchiveState(any(), any(), any(), any())
        }
    }

    @Test
    fun archiveGroup_allows2359SlotOnSelectedDateBeforeEndOfDayCutoff() = runTest {
        val groupUuid = UUID.fromString("cb22a623-f098-47e0-b1b5-a6ef7358e6bc")
        val now = Instant.parse("2026-05-10T08:00:45Z")
        val selectedDate = LocalDate.of(2026, 5, 8)
        val persistedCutoff = selectedDate.atTime(LocalTime.MAX).toString()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(23, 59)),
        )
        coEvery { medicationLogDao.getEntriesForGroup(groupUuid.toString()) } returns listOf(
            testMedicationLogEntryEntity(
                groupUuid = groupUuid,
                appliedAt = Instant.parse("2026-05-08T14:59:00Z"),
                appliedAtTimeZoneId = ZoneId.systemDefault().id,
                scheduledForIso = "2026-05-08T23:59",
            )
        )

        repository.archiveGroup(
            uuid = groupUuid,
            archivedThroughDate = selectedDate,
            now = now,
        )

        assertFalse(LocalDateTime.parse("2026-05-08T23:59").isAfter(LocalDateTime.parse(persistedCutoff)))
        coVerify(exactly = 1) {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = any(),
                archivedAtLocalIso = persistedCutoff,
                updatedAtEpochMillis = now.toEpochMilli(),
            )
        }
    }

    @Test
    fun saveGroup_withReplacement_createsActiveCopyAndLinksArchivedOriginal() = runTest {
        val originalGroupUuid = UUID.fromString("893f1577-5d7f-447f-b626-45cd7dc69e33")
        val savedGroup = slot<MedicationGroupEntity>()
        val savedItems = slot<List<MedicationGroupItemEntity>>()
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        val savedWeeklyDays = slot<List<MedicationGroupWeeklyDayEntity>>()
        val now = Instant.parse("2026-04-30T08:15:00Z")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }

        val savedGroupUuid = repository.saveGroup(
            uuid = null,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = LocalDate.of(2026, 4, 30),
                weeklyDaysOfWeek = setOf(java.time.DayOfWeek.THURSDAY),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(
                MedicationGroupMedicationInput(
                    uuid = null,
                    medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    count = 1,
                )
            ),
            notificationsEnabled = true,
            replacesGroupUuid = originalGroupUuid,
            now = now,
        )

        coVerifyOrder {
            medicationGroupDao.upsertGroupWithItems(
                group = capture(savedGroup),
                items = capture(savedItems),
                scheduleTimes = capture(savedTimes),
                weeklyDays = capture(savedWeeklyDays),
            )
            medicationGroupDao.updateGroupReplacedBy(
                uuid = originalGroupUuid.toString(),
                replacedByGroupUuid = savedGroupUuid.toString(),
                updatedAtEpochMillis = any(),
            )
        }
        assertEquals(savedGroupUuid.toString(), savedGroup.captured.uuid)
        assertNotEquals(originalGroupUuid.toString(), savedGroup.captured.uuid)
        assertEquals(LocalDate.of(2026, 4, 30).toEpochDay(), savedGroup.captured.scheduleSinceEpochDay)
        assertEquals(true, savedGroup.captured.notificationsEnabled)
        assertNull(savedGroup.captured.archivedAtEpochMillis)
        assertEquals(false, savedGroup.captured.includePastScheduledSlots)
        assertNull(savedGroup.captured.replacedByGroupUuid)
        assertEquals(originalGroupUuid.toString(), savedGroup.captured.recreatedFromGroupUuid)
        assertEquals(savedGroupUuid.toString(), savedItems.captured.single().groupUuid)
        assertEquals(savedGroupUuid.toString(), savedTimes.captured.single().groupUuid)
        assertEquals(now.atZone(ZoneId.systemDefault()).toLocalDateTime().toString(), savedTimes.captured.single().effectiveFromLocalIso)
        assertEquals(savedGroupUuid.toString(), savedWeeklyDays.captured.single().groupUuid)
    }

    @Test
    fun saveGroup_rejectsIncompatibleApplicationTypeBeforeUpsert() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        coEvery { medicineDao.getByUuids(listOf(medicineUuid.toString())) } returns listOf(
            testMedicineEntity(
                uuid = medicineUuid.toString(),
                medicationKey = MedicationKey.ESTRADIOL,
                preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
            )
        )
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }

        val thrown = runCatching {
            repository.saveGroup(
                uuid = null,
                name = "Group",
                colorKey = MedicationGroupColorKey.ROSE,
                schedule = MedicationGroupScheduleInput(
                    type = MedicationGroupScheduleType.DAILY,
                    interval = 1,
                    since = LocalDate.of(2026, 4, 1),
                    weeklyDaysOfWeek = emptySet(),
                    times = listOf(LocalTime.of(8, 0)),
                ),
                medications = listOf(
                    MedicationGroupMedicationInput(
                        medicineUuid = medicineUuid,
                        applicationType = MedicationApplicationType.SUBLINGUAL,
                        doseInstruction = DoseInstruction.WholeUnit,
                    )
                ),
            )
        }.exceptionOrNull()

        assertEquals(
            "medication group item $medicineUuid applicationType=SUBLINGUAL is not compatible with preparation=CAPSULE.",
            thrown?.message,
        )
        coVerify(exactly = 0) { medicationGroupDao.upsertGroupWithItems(any(), any(), any(), any()) }
    }

    @Test
    fun saveGroup_rejectsPillWholeUnitBeforeUpsert() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        coEvery { medicineDao.getByUuids(listOf(medicineUuid.toString())) } returns listOf(
            testMedicineEntity(
                uuid = medicineUuid.toString(),
                medicationKey = MedicationKey.ESTRADIOL,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            )
        )
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }

        val thrown = runCatching {
            repository.saveGroup(
                uuid = null,
                name = "Group",
                colorKey = MedicationGroupColorKey.ROSE,
                schedule = MedicationGroupScheduleInput(
                    type = MedicationGroupScheduleType.DAILY,
                    interval = 1,
                    since = LocalDate.of(2026, 4, 1),
                    weeklyDaysOfWeek = emptySet(),
                    times = listOf(LocalTime.of(8, 0)),
                ),
                medications = listOf(
                    MedicationGroupMedicationInput(
                        medicineUuid = medicineUuid,
                        applicationType = MedicationApplicationType.ORAL,
                        doseInstruction = DoseInstruction.WholeUnit,
                    )
                ),
            )
        }.exceptionOrNull()

        assertEquals(
            "medication group item $medicineUuid doseInstruction=WHOLE_UNIT is not compatible with preparation=PILL.",
            thrown?.message,
        )
        coVerify(exactly = 0) { medicationGroupDao.upsertGroupWithItems(any(), any(), any(), any()) }
    }

    @Test
    fun saveGroup_forFreshGroupWithBackfillOn_setsInitialScheduleTimesEffectiveFromSince() = runTest {
        val savedGroup = slot<MedicationGroupEntity>()
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        val now = Instant.parse("2026-04-30T08:15:00Z")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }

        repository.saveGroup(
            uuid = null,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            ),
            medications = emptyList(),
            includePastScheduledSlots = true,
            now = now,
        )

        coVerify {
            medicationGroupDao.upsertGroupWithItems(
                group = capture(savedGroup),
                items = any(),
                scheduleTimes = capture(savedTimes),
                weeklyDays = any(),
            )
        }
        assertEquals(true, savedGroup.captured.includePastScheduledSlots)
        assertNull(savedGroup.captured.recreatedFromGroupUuid)
        assertEquals(
            listOf("2026-04-01T00:00", "2026-04-01T00:00"),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun saveGroup_forFreshGroupWithBackfillOff_setsInitialScheduleTimesEffectiveFromCurrentMinute() = runTest {
        val savedGroup = slot<MedicationGroupEntity>()
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        val now = Instant.parse("2026-04-30T08:15:45.123Z")
        val expectedEffectiveFrom = now.atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .truncatedTo(ChronoUnit.MINUTES)
            .toString()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }

        repository.saveGroup(
            uuid = null,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            ),
            medications = emptyList(),
            includePastScheduledSlots = false,
            now = now,
        )

        coVerify {
            medicationGroupDao.upsertGroupWithItems(
                group = capture(savedGroup),
                items = any(),
                scheduleTimes = capture(savedTimes),
                weeklyDays = any(),
            )
        }
        assertEquals(false, savedGroup.captured.includePastScheduledSlots)
        assertNull(savedGroup.captured.recreatedFromGroupUuid)
        assertEquals(now.toEpochMilli(), savedGroup.captured.createdAtEpochMillis)
        assertEquals(now.toEpochMilli(), savedGroup.captured.updatedAtEpochMillis)
        assertEquals(
            listOf(expectedEffectiveFrom, expectedEffectiveFrom),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun saveGroup_newNoBackfillGroupWithFutureSinceIsEffectiveFromStartOfThatDay() = runTest {
        // Defense in depth: a no-backfill plan whose start is in the future (e.g.
        // a recreate beginning the day after an end-of-day archive) pins each
        // slot's effectiveFrom to that start day, never `now`, so it can never
        // own a day the archived plan still owns. (`since` already gates
        // generation, so today-start groups keep effectiveFrom = now.)
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        val now = Instant.parse("2026-04-25T10:00:00Z")
        val futureSince = LocalDate.of(2026, 5, 1)
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }

        repository.saveGroup(
            uuid = null,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = futureSince,
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = emptyList(),
            includePastScheduledSlots = false,
            now = now,
        )

        coVerify {
            medicationGroupDao.upsertGroupWithItems(
                group = any(),
                items = any(),
                scheduleTimes = capture(savedTimes),
                weeklyDays = any(),
            )
        }
        assertEquals(
            futureSince.atStartOfDay().toString(),
            savedTimes.captured.single().effectiveFromLocalIso,
        )
    }

    @Test
    fun saveGroup_forExistingFreshGroupWhenBackfillEnabled_movesCurrentRowsToSinceStart() = runTest {
        val groupUuid = UUID.fromString("30c4a905-64d5-4ef0-8096-60b7d1edc4c4")
        val savedGroup = slot<MedicationGroupEntity>()
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            includePastScheduledSlots = false,
            effectiveFromLocalIso = listOf("2026-04-18T10:00", "2026-04-18T10:00"),
        )

        repository.saveGroup(
            uuid = groupUuid,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(0),
                        time = LocalTime.of(8, 0),
                    ),
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(1),
                        time = LocalTime.of(20, 0),
                    ),
                ),
            ),
            medications = emptyList(),
            includePastScheduledSlots = true,
            now = Instant.parse("2026-04-30T08:15:00Z"),
        )

        coVerify {
            medicationGroupDao.upsertGroupWithItems(
                group = capture(savedGroup),
                items = any(),
                scheduleTimes = capture(savedTimes),
                weeklyDays = any(),
            )
        }
        assertEquals(true, savedGroup.captured.includePastScheduledSlots)
        assertNull(savedGroup.captured.recreatedFromGroupUuid)
        assertEquals(
            listOf("2026-04-01T00:00", "2026-04-01T00:00"),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun saveGroup_forExistingFreshBackfilledGroupWhenStartDateChanges_movesCurrentRowsToNewSinceStart() =
        runTest {
            val groupUuid = UUID.fromString("3c4e6213-dadf-409f-afee-c3d862a01ea0")
            val savedGroup = slot<MedicationGroupEntity>()
            val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
            coEvery {
                databaseHolder.withTransaction<Unit>(any())
            } coAnswers {
                firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
            }
            coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
                groupUuid = groupUuid,
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                includePastScheduledSlots = true,
                scheduleSinceEpochDay = LocalDate.of(2026, 4, 10).toEpochDay(),
                effectiveFromLocalIso = listOf("2026-04-10T00:00", "2026-04-10T00:00"),
            )

            repository.saveGroup(
                uuid = groupUuid,
                name = "Group",
                colorKey = MedicationGroupColorKey.ROSE,
                schedule = MedicationGroupScheduleInput(
                    type = MedicationGroupScheduleType.DAILY,
                    interval = 1,
                    since = LocalDate.of(2026, 4, 1),
                    weeklyDaysOfWeek = emptySet(),
                    times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                    timeSlots = listOf(
                        MedicationGroupScheduleTimeInput(
                            uuid = testScheduleTimeUuid(0),
                            time = LocalTime.of(8, 0),
                        ),
                        MedicationGroupScheduleTimeInput(
                            uuid = testScheduleTimeUuid(1),
                            time = LocalTime.of(20, 0),
                        ),
                    ),
                ),
                medications = emptyList(),
                includePastScheduledSlots = true,
                now = Instant.parse("2026-04-30T08:15:00Z"),
            )

            coVerify {
                medicationGroupDao.upsertGroupWithItems(
                    group = capture(savedGroup),
                    items = any(),
                    scheduleTimes = capture(savedTimes),
                    weeklyDays = any(),
                )
            }
            assertEquals(LocalDate.of(2026, 4, 1).toEpochDay(), savedGroup.captured.scheduleSinceEpochDay)
            assertEquals(true, savedGroup.captured.includePastScheduledSlots)
            assertEquals(
                listOf("2026-04-01T00:00", "2026-04-01T00:00"),
                savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
            )
        }

    @Test
    fun saveGroup_forExistingFreshBackfilledGroupWithRecords_movesCurrentRowsToNewSinceStart() =
        runTest {
            val groupUuid = UUID.fromString("94d89db6-c7b7-41b5-95a2-8be9dc3e375d")
            val savedGroup = slot<MedicationGroupEntity>()
            val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
            coEvery {
                databaseHolder.withTransaction<Unit>(any())
            } coAnswers {
                firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
            }
            coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
                groupUuid = groupUuid,
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                includePastScheduledSlots = true,
                scheduleSinceEpochDay = LocalDate.of(2026, 4, 10).toEpochDay(),
                effectiveFromLocalIso = listOf("2026-04-10T00:00", "2026-04-10T00:00"),
            )
            coEvery { medicationLogDao.getEntryCountForGroup(groupUuid.toString()) } returns 1

            repository.saveGroup(
                uuid = groupUuid,
                name = "Group",
                colorKey = MedicationGroupColorKey.ROSE,
                schedule = MedicationGroupScheduleInput(
                    type = MedicationGroupScheduleType.DAILY,
                    interval = 1,
                    since = LocalDate.of(2026, 4, 1),
                    weeklyDaysOfWeek = emptySet(),
                    times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                    timeSlots = listOf(
                        MedicationGroupScheduleTimeInput(
                            uuid = testScheduleTimeUuid(0),
                            time = LocalTime.of(8, 0),
                        ),
                        MedicationGroupScheduleTimeInput(
                            uuid = testScheduleTimeUuid(1),
                            time = LocalTime.of(20, 0),
                        ),
                    ),
                ),
                medications = emptyList(),
                includePastScheduledSlots = true,
                now = Instant.parse("2026-04-30T08:15:00Z"),
            )

            coVerify {
                medicationGroupDao.upsertGroupWithItems(
                    group = capture(savedGroup),
                    items = any(),
                    scheduleTimes = capture(savedTimes),
                    weeklyDays = any(),
                )
            }
            assertEquals(LocalDate.of(2026, 4, 1).toEpochDay(), savedGroup.captured.scheduleSinceEpochDay)
            assertEquals(
                listOf("2026-04-01T00:00", "2026-04-01T00:00"),
                savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
            )
        }

    @Test
    fun saveGroup_forExistingFreshGroupWhenBackfillDisabled_movesCurrentRowsToSaveTime() = runTest {
        val groupUuid = UUID.fromString("d20ad4f2-e8bb-43f7-aab8-72dfb270f66a")
        val savedGroup = slot<MedicationGroupEntity>()
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        val now = Instant.parse("2026-04-30T08:15:00Z")
        val expectedEffectiveFrom = now.atZone(ZoneId.systemDefault()).toLocalDateTime().toString()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            includePastScheduledSlots = true,
            effectiveFromLocalIso = listOf("2026-04-01T00:00", "2026-04-01T00:00"),
        )

        repository.saveGroup(
            uuid = groupUuid,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(0),
                        time = LocalTime.of(8, 0),
                    ),
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(1),
                        time = LocalTime.of(20, 0),
                    ),
                ),
            ),
            medications = emptyList(),
            includePastScheduledSlots = false,
            now = now,
        )

        coVerify {
            medicationGroupDao.upsertGroupWithItems(
                group = capture(savedGroup),
                items = any(),
                scheduleTimes = capture(savedTimes),
                weeklyDays = any(),
            )
        }
        assertEquals(false, savedGroup.captured.includePastScheduledSlots)
        assertNull(savedGroup.captured.recreatedFromGroupUuid)
        assertEquals(
            listOf(expectedEffectiveFrom, expectedEffectiveFrom),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun saveGroup_forExistingFreshGroupWithRecords_ignoresBackfillToggle() = runTest {
        val groupUuid = UUID.fromString("08fa3c50-f431-4d29-a0b5-d4dbf22e7095")
        val savedGroup = slot<MedicationGroupEntity>()
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            includePastScheduledSlots = false,
            effectiveFromLocalIso = listOf("2026-04-18T10:00", "2026-04-18T10:00"),
        )
        coEvery { medicationLogDao.getEntryCountForGroup(groupUuid.toString()) } returns 1

        repository.saveGroup(
            uuid = groupUuid,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(0),
                        time = LocalTime.of(8, 0),
                    ),
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(1),
                        time = LocalTime.of(20, 0),
                    ),
                ),
            ),
            medications = emptyList(),
            includePastScheduledSlots = true,
            now = Instant.parse("2026-04-30T08:15:00Z"),
        )

        coVerify {
            medicationGroupDao.upsertGroupWithItems(
                group = capture(savedGroup),
                items = any(),
                scheduleTimes = capture(savedTimes),
                weeklyDays = any(),
            )
        }
        assertEquals(false, savedGroup.captured.includePastScheduledSlots)
        assertEquals(
            listOf("2026-04-18T10:00", "2026-04-18T10:00"),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun saveGroup_forExistingRecreatedGroup_ignoresBackfillToggle() = runTest {
        val originalGroupUuid = UUID.fromString("aefc640e-c100-4498-b2d0-39d25da81182")
        val groupUuid = UUID.fromString("c26d46fc-5000-4c58-8522-2fc765f4c7db")
        val savedGroup = slot<MedicationGroupEntity>()
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0)),
            includePastScheduledSlots = false,
            recreatedFromGroupUuid = originalGroupUuid.toString(),
            effectiveFromLocalIso = listOf("2026-04-18T10:00"),
        )

        repository.saveGroup(
            uuid = groupUuid,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(0),
                        time = LocalTime.of(8, 0),
                    ),
                ),
            ),
            medications = emptyList(),
            includePastScheduledSlots = true,
            now = Instant.parse("2026-04-30T08:15:00Z"),
        )

        coVerify {
            medicationGroupDao.upsertGroupWithItems(
                group = capture(savedGroup),
                items = any(),
                scheduleTimes = capture(savedTimes),
                weeklyDays = any(),
            )
        }
        assertEquals(false, savedGroup.captured.includePastScheduledSlots)
        assertEquals(originalGroupUuid.toString(), savedGroup.captured.recreatedFromGroupUuid)
        assertEquals(
            listOf("2026-04-18T10:00"),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun saveGroup_forRecordlessExistingRecreatedGroupWhenStartDateChangesToToday_movesRowsToCurrentMinute() = runTest {
        val originalGroupUuid = UUID.fromString("bb802fd3-2b84-4be0-98a1-526e2fafb286")
        val groupUuid = UUID.fromString("51fb3ef2-68ec-4ad9-a78b-df25681a48c9")
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        val now = Instant.parse("2026-04-30T08:15:45Z")
        val nowLocal = now.atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .truncatedTo(ChronoUnit.MINUTES)
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationLogDao.getEntryCountForGroup(groupUuid.toString()) } returns 0
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0)),
            includePastScheduledSlots = false,
            scheduleSinceEpochDay = LocalDate.of(2026, 4, 18).toEpochDay(),
            recreatedFromGroupUuid = originalGroupUuid.toString(),
            effectiveFromLocalIso = listOf("2026-04-18T10:00"),
        )

        repository.saveGroup(
            uuid = groupUuid,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = nowLocal.toLocalDate(),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(0),
                        time = LocalTime.of(8, 0),
                    ),
                ),
            ),
            medications = emptyList(),
            includePastScheduledSlots = true,
            now = now,
        )

        coVerify {
            medicationGroupDao.upsertGroupWithItems(
                group = any(),
                items = any(),
                scheduleTimes = capture(savedTimes),
                weeklyDays = any(),
            )
        }
        assertEquals(
            listOf(nowLocal.toString()),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun saveGroup_forRecordlessExistingRecreatedGroupWhenStartDateChangesToFuture_movesRowsToFutureStart() = runTest {
        val originalGroupUuid = UUID.fromString("f5603821-326a-4af8-a95f-4e63def955a2")
        val groupUuid = UUID.fromString("736ae5a8-d328-4730-aa3c-fe06b5e6a4bc")
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        val now = Instant.parse("2026-04-30T08:15:45Z")
        val futureDate = now.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1)
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationLogDao.getEntryCountForGroup(groupUuid.toString()) } returns 0
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0)),
            includePastScheduledSlots = false,
            scheduleSinceEpochDay = LocalDate.of(2026, 4, 18).toEpochDay(),
            recreatedFromGroupUuid = originalGroupUuid.toString(),
            effectiveFromLocalIso = listOf("2026-04-18T10:00"),
        )

        repository.saveGroup(
            uuid = groupUuid,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = futureDate,
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(0),
                        time = LocalTime.of(8, 0),
                    ),
                ),
            ),
            medications = emptyList(),
            includePastScheduledSlots = true,
            now = now,
        )

        coVerify {
            medicationGroupDao.upsertGroupWithItems(
                group = any(),
                items = any(),
                scheduleTimes = capture(savedTimes),
                weeklyDays = any(),
            )
        }
        assertEquals(
            listOf(futureDate.atStartOfDay().toString()),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun saveGroup_forExistingFreshBackfilledGroup_resetsAllScheduleTimesEffectiveFromToSinceStart() = runTest {
        val groupUuid = UUID.fromString("d301984f-47c4-4617-8d4d-0f18c66f306d")
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        val now = Instant.parse("2026-04-30T08:15:00Z")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            effectiveFromLocalIso = listOf("2026-04-01T00:00", "2026-04-02T00:00"),
        )

        repository.saveGroup(
            uuid = groupUuid,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(21, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(0),
                        time = LocalTime.of(8, 0),
                    ),
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(1),
                        time = LocalTime.of(21, 0),
                    ),
                ),
            ),
            medications = emptyList(),
            now = now,
        )

        coVerify {
            medicationGroupDao.upsertGroupWithItems(
                group = any(),
                items = any(),
                scheduleTimes = capture(savedTimes),
                weeklyDays = any(),
            )
        }
        assertEquals(
            listOf("2026-04-01T00:00", "2026-04-01T00:00"),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun saveGroup_forExistingFreshBackfilledGroup_resetsEffectiveFromAndPreservesUuidOrderWhenRowsAreReordered() = runTest {
        val groupUuid = UUID.fromString("04d776b5-294a-4135-bf59-9cad0b0cb893")
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            effectiveFromLocalIso = listOf("2026-04-01T00:00", "2026-04-02T00:00"),
        )

        repository.saveGroup(
            uuid = groupUuid,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0), LocalTime.of(8, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(1),
                        time = LocalTime.of(20, 0),
                    ),
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(0),
                        time = LocalTime.of(8, 0),
                    ),
                ),
            ),
            medications = emptyList(),
            now = Instant.parse("2026-04-30T08:15:00Z"),
        )

        coVerify {
            medicationGroupDao.upsertGroupWithItems(
                group = any(),
                items = any(),
                scheduleTimes = capture(savedTimes),
                weeklyDays = any(),
            )
        }
        assertEquals(
            listOf(testScheduleTimeUuid(1).toString(), testScheduleTimeUuid(0).toString()),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::uuid),
        )
        assertEquals(
            listOf("2026-04-01T00:00", "2026-04-01T00:00"),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun saveGroup_forExistingFreshBackfilledGroup_resetsAllScheduleTimesEffectiveFromWhenReplacingSlotTime() = runTest {
        val groupUuid = UUID.fromString("c34fc9ce-2870-4198-9d54-ae1d91e96f41")
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        val now = Instant.parse("2026-04-30T08:15:00Z")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(9, 0)),
            effectiveFromLocalIso = listOf("2026-04-01T00:00", "2026-04-02T00:00"),
        )

        repository.saveGroup(
            uuid = groupUuid,
            name = "Group",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0), LocalTime.of(9, 0)),
                timeSlots = listOf(
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(0),
                        time = LocalTime.of(9, 0),
                    ),
                    MedicationGroupScheduleTimeInput(
                        uuid = testScheduleTimeUuid(1),
                        time = LocalTime.of(9, 0),
                    ),
                ),
            ),
            medications = emptyList(),
            now = now,
        )

        coVerify {
            medicationGroupDao.upsertGroupWithItems(
                group = any(),
                items = any(),
                scheduleTimes = capture(savedTimes),
                weeklyDays = any(),
            )
        }
        assertEquals(
            listOf(testScheduleTimeUuid(0).toString(), testScheduleTimeUuid(1).toString()),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::uuid),
        )
        assertEquals(
            listOf("2026-04-01T00:00", "2026-04-01T00:00"),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun updateScheduleTimes_updatesMatchingEntriesFromOriginalSnapshot() = runTest {
        val groupUuid = UUID.fromString("d5037093-0f98-4f24-a0d4-f4fb93a8d210")
        val now = Instant.parse("2026-04-30T09:00:00Z")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(9, 0))
        )
        coEvery {
            medicationLogDao.getPlannedEntryIdsForGroupSlotTime(
                groupUuid.toString(),
                testScheduleTimeUuid(0).toString(),
                "08:00",
            )
        } returns listOf("entry-8")
        coEvery {
            medicationLogDao.getPlannedEntryIdsForGroupSlotTime(
                groupUuid.toString(),
                testScheduleTimeUuid(1).toString(),
                "09:00",
            )
        } returns listOf("entry-9")

        repository.updateScheduleTimes(
            groupUuid = groupUuid,
            newTimes = listOf(LocalTime.of(9, 30), LocalTime.of(8, 30)),
            now = now,
        )

        coVerifyOrder {
            medicationLogDao.getPlannedEntryIdsForGroupSlotTime(
                groupUuid.toString(),
                testScheduleTimeUuid(0).toString(),
                "08:00",
            )
            medicationLogDao.getPlannedEntryIdsForGroupSlotTime(
                groupUuid.toString(),
                testScheduleTimeUuid(1).toString(),
                "09:00",
            )
            medicationGroupDao.updateGroupUpdatedAt(groupUuid.toString(), now.toEpochMilli())
            medicationGroupDao.deleteScheduleTimesForGroup(groupUuid.toString())
            medicationGroupDao.insertScheduleTimes(
                listOf(
                    MedicationGroupScheduleTimeEntity(
                        groupUuid = groupUuid.toString(),
                        sortOrder = 0,
                        hourOfDay = 8,
                        minuteOfHour = 30,
                        uuid = testScheduleTimeUuid(1).toString(),
                    ),
                    MedicationGroupScheduleTimeEntity(
                        groupUuid = groupUuid.toString(),
                        sortOrder = 1,
                        hourOfDay = 9,
                        minuteOfHour = 30,
                        uuid = testScheduleTimeUuid(0).toString(),
                    ),
                )
            )
            medicationLogDao.updateScheduledForTimeForEntries(listOf("entry-8"), "09:30")
            medicationLogDao.updateScheduledForTimeForEntries(listOf("entry-9"), "08:30")
        }
    }

    @Test
    fun validateScheduleTimeMigration_rejectsCountChange() {
        assertThrows(ScheduleTimeCountMismatchException::class.java) {
            validateScheduleTimeMigration(
                oldTimes = listOf(LocalTime.of(8, 0)),
                newTimes = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            )
        }
    }

    @Test
    fun validateScheduleTimeMigration_allowsReorder() {
        validateScheduleTimeMigration(
            oldTimes = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            newTimes = listOf(LocalTime.of(21, 0), LocalTime.of(20, 0)),
        )
    }

    @Test
    fun validateScheduleTimeMigration_rejectsDuplicateTimes() {
        assertThrows(ScheduleTimeDuplicateException::class.java) {
            validateScheduleTimeMigration(
                oldTimes = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                newTimes = listOf(LocalTime.of(8, 0), LocalTime.of(8, 0)),
            )
        }
    }

    @Test
    fun saveGroup_rejectsMedicineUuidChangeWhenGroupHasLogs() = runTest {
        val groupUuid = UUID.fromString("cccccccc-0000-0000-0000-000000000000")
        val itemUuid = UUID.fromString("dddddddd-0000-0000-0000-000000000000")
        val originalMedicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val replacementMedicineUuid = UUID.fromString("eeeeeeee-0000-0000-0000-000000000000")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupWithItem(
            groupUuid = groupUuid,
            itemUuid = itemUuid,
            medicineUuid = originalMedicineUuid,
        )
        coEvery { medicationLogDao.getEntryCountForGroup(groupUuid.toString()) } returns 1

        // The outer `runTest` already drives this coroutine; using
        // `assertThrows { runTest { ... } }` here would start a *nested*
        // TestScope and immediately fail with the "Only a single call to
        // runTest" guard before the repository code can throw its
        // LockedMedicationGroupSlotRepointException.
        val thrown = runCatching {
            repository.saveGroup(
                uuid = groupUuid,
                name = "Locked group",
                colorKey = MedicationGroupColorKey.ROSE,
                schedule = MedicationGroupScheduleInput(
                    type = MedicationGroupScheduleType.WEEKLY,
                    interval = 1,
                    since = LocalDate.of(2026, 5, 22),
                    weeklyDaysOfWeek = setOf(java.time.DayOfWeek.FRIDAY),
                    times = listOf(LocalTime.of(8, 0)),
                ),
                medications = listOf(
                    MedicationGroupMedicationInput(
                        uuid = itemUuid,
                        medicineUuid = replacementMedicineUuid,
                        applicationType = MedicationApplicationType.ORAL,
                        doseInstruction = DoseInstruction.TabletFraction(1, 1),
                        count = 1,
                    )
                ),
            )
        }.exceptionOrNull()
        assertTrue(
            "Expected LockedMedicationGroupSlotRepointException but got $thrown",
            thrown is LockedMedicationGroupSlotRepointException,
        )
    }

    private fun testGroupEntity(
        groupUuid: UUID,
        times: List<LocalTime>,
        items: List<MedicationGroupItemEntity> = emptyList(),
        weeklyDays: List<MedicationGroupWeeklyDayEntity> = emptyList(),
        archivedAtEpochMillis: Long? = null,
        includePastScheduledSlots: Boolean = true,
        scheduleSinceEpochDay: Long = LocalDate.of(2026, 4, 1).toEpochDay(),
        createdAtEpochMillis: Long = 0,
        replacedByGroupUuid: String? = null,
        recreatedFromGroupUuid: String? = null,
        effectiveFromLocalIso: List<String> = List(times.size) {
            LocalDate.EPOCH.atStartOfDay().toString()
        },
    ): MedicationGroupWithItemsEntity {
        return MedicationGroupWithItemsEntity(
            group = MedicationGroupEntity(
                uuid = groupUuid.toString(),
                name = "Group",
                colorKey = "ROSE",
                notificationsEnabled = true,
                scheduleType = "DAILY",
                scheduleInterval = 1,
                scheduleSinceEpochDay = scheduleSinceEpochDay,
                createdAtEpochMillis = createdAtEpochMillis,
                updatedAtEpochMillis = 0,
                archivedAtEpochMillis = archivedAtEpochMillis,
                includePastScheduledSlots = includePastScheduledSlots,
                replacedByGroupUuid = replacedByGroupUuid,
                recreatedFromGroupUuid = recreatedFromGroupUuid,
            ),
            items = items,
            scheduleTimes = times.mapIndexed { index, time ->
                MedicationGroupScheduleTimeEntity(
                    groupUuid = groupUuid.toString(),
                    sortOrder = index,
                    hourOfDay = time.hour,
                    minuteOfHour = time.minute,
                    uuid = testScheduleTimeUuid(index).toString(),
                    effectiveFromLocalIso = effectiveFromLocalIso[index],
                )
            },
            weeklyDays = weeklyDays,
        )
    }

    private fun testMedicationLogEntryEntity(
        groupUuid: UUID,
        appliedAt: Instant,
        appliedAtTimeZoneId: String,
        scheduledForIso: String?,
    ): MedicationLogEntryEntity {
        return MedicationLogEntryEntity(
            uuid = UUID.randomUUID().toString(),
            category = "ESTRADIOL",
            medicineUuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000001").toString(),
            applicationType = "ORAL",
            doseInstructionKind = "TABLET_FRACTION",
            tabletFractionNumerator = 1,
            tabletFractionDenominator = 1,
            doseVolumeMl = null,
            doseWeightGrams = null,
            equivalentE2Mg = null,
            sourceGroupUuid = groupUuid.toString(),
            scheduleTimeUuid = null,
            appliedAtEpochMillis = appliedAt.toEpochMilli(),
            appliedAtTimeZoneId = appliedAtTimeZoneId,
            scheduledForIso = scheduledForIso,
            count = 1,
        )
    }

    private fun testScheduleTimeUuid(index: Int): UUID {
        return UUID.nameUUIDFromBytes("schedule-time-$index".toByteArray())
    }

    private fun testGroupItemEntity(
        uuid: String,
        groupUuid: String,
        medicineUuid: String? = "aaaaaaaa-0000-0000-0000-000000000000",
    ): MedicationGroupItemEntity {
        return MedicationGroupItemEntity(
            uuid = uuid,
            groupUuid = groupUuid,
            sortOrder = 0,
            count = 1,
            medicineUuid = medicineUuid,
            applicationType = "ORAL",
            doseInstructionKind = DoseInstructionKind.TABLET_FRACTION.name,
            tabletFractionNumerator = 1,
            tabletFractionDenominator = 1,
            doseVolumeMl = null,
            doseWeightGrams = null,
            gelApplicationArea = "DEFAULT",
        )
    }

    private fun testGroupWithItem(
        groupUuid: UUID,
        itemUuid: UUID,
        medicineUuid: UUID,
    ): MedicationGroupWithItemsEntity {
        return testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0)),
            items = listOf(
                testGroupItemEntity(
                    uuid = itemUuid.toString(),
                    groupUuid = groupUuid.toString(),
                    medicineUuid = medicineUuid.toString(),
                ),
            ),
        )
    }

    private fun testMedicineEntity(
        uuid: String,
        medicationKey: MedicationKey,
        preparation: MedicinePreparation,
    ): MedicineEntity {
        val fields = preparation.toStorageFields()
        return MedicineEntity(
            uuid = uuid,
            selectionKind = MedicationSelectionKind.CATALOG.name,
            medicationKey = medicationKey.name,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = medicationKey.category.name,
            preparationType = fields.preparationType,
            strengthMgPerTablet = fields.strengthMgPerTablet,
            strengthMgPerVial = fields.strengthMgPerVial,
            concentrationMgPerMl = fields.concentrationMgPerMl,
            vialVolumeMl = fields.vialVolumeMl,
            concentrationPercent = fields.concentrationPercent,
            sachetWeightGrams = fields.sachetWeightGrams,
            containerWeightGrams = fields.containerWeightGrams,
            patchTotalMg = fields.patchTotalMg,
            patchReleaseRateMcgPerDay = fields.patchReleaseRateMcgPerDay,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(medicationKey, preparation),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = null,
        )
    }

    // Regression: observeGroups() resolves Medicine references via a separate
    // point-in-time fetch off the groups Flow, so a medicines-table change
    // (displayName, archive, etc.) used to not re-emit until the groups table
    // also changed — i.e., effectively only on app relaunch. The combined
    // medicineDao.observeMedicineChangeVersion() signal forces re-resolution
    // on any medicines mutation.
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun observeGroups_reEmitsWhenMedicineTableSignalChanges() = runTest {
        val groupUuid = UUID.fromString("dddddddd-0000-0000-0000-000000000001")
        val itemUuid = UUID.fromString("eeeeeeee-0000-0000-0000-000000000002")
        val medicineUuid = UUID.fromString("ffffffff-0000-0000-0000-000000000003")

        val medicineDao = mockk<MedicineDao>()
        val groupsSource = MutableStateFlow(listOf(testGroupWithItem(groupUuid, itemUuid, medicineUuid)))
        val medicineChangeVersion = MutableStateFlow(0)
        val medicineState = MutableStateFlow(
            MedicineEntity(
                uuid = medicineUuid.toString(),
                selectionKind = "CATALOG",
                medicationKey = "ESTRADIOL",
                customMedicationName = null,
                customMedicationNameNormalized = null,
                category = com.mkx.hrttracker.model.medication.MedicationCategory.ESTRADIOL.name,
                preparationType = "PILL",
                strengthMgPerTablet = 2.0,
                strengthMgPerVial = null,
                concentrationMgPerMl = null,
                vialVolumeMl = null,
                concentrationPercent = null,
                sachetWeightGrams = null,
                containerWeightGrams = null,
                patchTotalMg = null,
                patchReleaseRateMcgPerDay = null,
                displayName = "Original",
                identityKey = "C|ESTRADIOL|PILL|strengthMgPerTablet=2",
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0,
                archivedAtEpochMillis = null,
            )
        )

        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        every { database.medicineDao() } returns medicineDao
        every { medicationGroupDao.observeGroups() } returns groupsSource
        every { medicineDao.observeMedicineChangeVersion() } returns medicineChangeVersion
        coEvery { medicineDao.getByUuids(any()) } answers { listOf(medicineState.value) }

        val freshRepository = MedicationGroupRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            appScope = CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            ),
        )

        advanceUntilIdle()
        val before = freshRepository.observeGroups().first { it != null }
        assertEquals(
            "Original",
            before?.first()?.medications?.first()?.medicine?.displayName,
        )

        medicineState.value = medicineState.value.copy(
            displayName = "Renamed",
            updatedAtEpochMillis = 500,
        )
        medicineChangeVersion.value = medicineChangeVersion.value + 1
        advanceUntilIdle()

        val after = freshRepository.observeGroups().first {
            it?.firstOrNull()?.medications?.firstOrNull()?.medicine?.displayName == "Renamed"
        }
        assertEquals(
            "Renamed",
            after?.first()?.medications?.first()?.medicine?.displayName,
        )
    }
}
