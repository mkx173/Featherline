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
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class MedicationGroupRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val medicationGroupDao: MedicationGroupDao = mockk(relaxed = true)
    private val medicationLogDao: MedicationLogDao = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true)

    private lateinit var repository: MedicationGroupRepository

    @Before
    fun setUp() {
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        every { databaseHolder.get() } returns database
        every { database.medicationGroupDao() } returns medicationGroupDao
        every { database.medicationLogDao() } returns medicationLogDao

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
        coVerify(exactly = 1) { homeSnapshotRepository.invalidateHomeSnapshot() }
        verify(exactly = 1) { homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = true) }
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
        coVerify(exactly = 1) { homeSnapshotRepository.invalidateHomeSnapshot() }
        verify(exactly = 1) { homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = true) }
    }

    @Test
    fun archiveGroup_setsArchivedAtMinuteWithoutTouchingNotificationsInSingleTransaction() = runTest {
        val groupUuid = UUID.fromString("38789ce3-9978-402c-8fd5-e660d436b8c4")
        val now = Instant.parse("2026-04-30T08:00:45Z")
        val expectedArchivedAtLocal = now.atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .truncatedTo(ChronoUnit.MINUTES)
            .toString()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = now.toEpochMilli(),
                archivedAtLocalIso = expectedArchivedAtLocal,
                updatedAtEpochMillis = now.toEpochMilli(),
            )
        } returns Unit

        repository.archiveGroup(groupUuid, now)

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerify(exactly = 1) {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = now.toEpochMilli(),
                archivedAtLocalIso = expectedArchivedAtLocal,
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
                    details = testMedicationDetails(),
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
    fun saveGroup_forExistingFreshBackfilledGroupWithRecords_preservesEffectiveFromWhenStartDateChanges() =
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
                listOf("2026-04-10T00:00", "2026-04-10T00:00"),
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
    fun saveGroup_forExistingGroupPreservesUnchangedScheduleTimeAndResetsChangedTime() = runTest {
        val groupUuid = UUID.fromString("d301984f-47c4-4617-8d4d-0f18c66f306d")
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
            listOf("2026-04-01T00:00", expectedEffectiveFrom),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun saveGroup_forExistingGroupPreservesEffectiveFromWhenRowsAreReordered() = runTest {
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
            listOf("2026-04-02T00:00", "2026-04-01T00:00"),
            savedTimes.captured.map(MedicationGroupScheduleTimeEntity::effectiveFromLocalIso),
        )
    }

    @Test
    fun saveGroup_whenReplacingEightWithExistingNine_preservesUnrelatedNineEffectiveFrom() = runTest {
        val groupUuid = UUID.fromString("c34fc9ce-2870-4198-9d54-ae1d91e96f41")
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
            listOf(expectedEffectiveFrom, "2026-04-02T00:00"),
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

    private fun testGroupEntity(
        groupUuid: UUID,
        times: List<LocalTime>,
        items: List<MedicationGroupItemEntity> = emptyList(),
        weeklyDays: List<MedicationGroupWeeklyDayEntity> = emptyList(),
        archivedAtEpochMillis: Long? = null,
        includePastScheduledSlots: Boolean = true,
        scheduleSinceEpochDay: Long = LocalDate.of(2026, 4, 1).toEpochDay(),
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
                createdAtEpochMillis = 0,
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

    private fun testScheduleTimeUuid(index: Int): UUID {
        return UUID.nameUUIDFromBytes("schedule-time-$index".toByteArray())
    }

    private fun testGroupItemEntity(
        uuid: String,
        groupUuid: String,
    ): MedicationGroupItemEntity {
        return MedicationGroupItemEntity(
            uuid = uuid,
            groupUuid = groupUuid,
            sortOrder = 0,
            count = 1,
            category = "ESTRADIOL",
            applicationType = "ORAL",
            selectionKind = "CATALOG",
            medicationKey = "ESTRADIOL",
            customMedicationName = null,
            doseKind = "MG_AS_MEDICINE",
            doseValueMg = 2.0,
            customDoseUnit = "MG",
            doseValuePercent = null,
            doseWeightGrams = null,
            doseReleaseRateMcgPerDay = null,
            gelApplicationArea = "DEFAULT",
        )
    }

    private fun testMedicationDetails(): MedicationDetails {
        return MedicationDetails(
            category = MedicationKey.ESTRADIOL.category,
            applicationType = MedicationApplicationType.ORAL,
            selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL),
            dose = MedicationDose.MgAsMedicine(2.0),
        )
    }
}
