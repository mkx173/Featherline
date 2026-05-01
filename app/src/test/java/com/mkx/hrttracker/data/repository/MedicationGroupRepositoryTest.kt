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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import java.util.UUID

class MedicationGroupRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val medicationGroupDao: MedicationGroupDao = mockk(relaxed = true)
    private val medicationLogDao: MedicationLogDao = mockk(relaxed = true)

    private lateinit var repository: MedicationGroupRepository

    @Before
    fun setUp() {
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        every { databaseHolder.get() } returns database
        every { database.medicationGroupDao() } returns medicationGroupDao
        every { database.medicationLogDao() } returns medicationLogDao

        repository = MedicationGroupRepository(
            databaseHolder = databaseHolder,
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
    }

    @Test
    fun archiveGroup_setsArchivedAtAndDisablesNotificationsInSingleTransaction() = runTest {
        val groupUuid = UUID.fromString("38789ce3-9978-402c-8fd5-e660d436b8c4")
        val now = Instant.parse("2026-04-30T08:00:00Z")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = now.toEpochMilli(),
                updatedAtEpochMillis = now.toEpochMilli(),
                notificationsEnabled = false,
            )
        } returns Unit

        repository.archiveGroup(groupUuid, now)

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerify(exactly = 1) {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = now.toEpochMilli(),
                updatedAtEpochMillis = now.toEpochMilli(),
                notificationsEnabled = false,
            )
        }
    }

    @Test
    fun unarchiveGroup_clearsArchivedAtAndKeepsNotificationsDisabledInSingleTransaction() = runTest {
        val groupUuid = UUID.fromString("810b59ca-af49-45ee-a9fe-f9fe268a94dc")
        val now = Instant.parse("2026-04-30T09:00:00Z")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = null,
                updatedAtEpochMillis = now.toEpochMilli(),
                notificationsEnabled = false,
            )
        } returns Unit

        repository.unarchiveGroup(groupUuid, now)

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerify(exactly = 1) {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = null,
                updatedAtEpochMillis = now.toEpochMilli(),
                notificationsEnabled = false,
            )
        }
    }

    @Test
    fun archiveAndRecreateGroup_archivesOriginalAndCreatesActiveCopyStartingToday() = runTest {
        val groupUuid = UUID.fromString("893f1577-5d7f-447f-b626-45cd7dc69e33")
        val medicationUuid = "932e8b35-7dd4-4202-92f4-5efb9a8a4f0e"
        val now = Instant.parse("2026-04-30T10:00:00Z")
        val today = LocalDate.of(2026, 4, 30)
        val copiedGroup = slot<MedicationGroupEntity>()
        val copiedItems = slot<List<MedicationGroupItemEntity>>()
        val copiedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        val copiedWeeklyDays = slot<List<MedicationGroupWeeklyDayEntity>>()
        coEvery {
            databaseHolder.withTransaction<UUID>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> UUID>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0)),
            items = listOf(
                testGroupItemEntity(
                    uuid = medicationUuid,
                    groupUuid = groupUuid.toString(),
                )
            ),
            weeklyDays = listOf(
                MedicationGroupWeeklyDayEntity(
                    groupUuid = groupUuid.toString(),
                    dayOfWeek = 4,
                )
            ),
        )
        coEvery {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = now.toEpochMilli(),
                updatedAtEpochMillis = now.toEpochMilli(),
                notificationsEnabled = false,
            )
        } returns Unit

        val recreatedGroupUuid = repository.archiveAndRecreateGroup(groupUuid, now, today)

        coVerifyOrder {
            medicationGroupDao.getGroup(groupUuid.toString())
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = now.toEpochMilli(),
                updatedAtEpochMillis = now.toEpochMilli(),
                notificationsEnabled = false,
            )
            medicationGroupDao.upsertGroupWithItems(
                group = capture(copiedGroup),
                items = capture(copiedItems),
                scheduleTimes = capture(copiedTimes),
                weeklyDays = capture(copiedWeeklyDays),
            )
        }
        assertEquals(recreatedGroupUuid.toString(), copiedGroup.captured.uuid)
        assertNotEquals(groupUuid.toString(), copiedGroup.captured.uuid)
        assertEquals(today.toEpochDay(), copiedGroup.captured.scheduleSinceEpochDay)
        assertEquals(now.toEpochMilli(), copiedGroup.captured.createdAtEpochMillis)
        assertEquals(now.toEpochMilli(), copiedGroup.captured.updatedAtEpochMillis)
        assertEquals(true, copiedGroup.captured.notificationsEnabled)
        assertNull(copiedGroup.captured.archivedAtEpochMillis)
        assertEquals(recreatedGroupUuid.toString(), copiedItems.captured.single().groupUuid)
        assertNotEquals(medicationUuid, copiedItems.captured.single().uuid)
        assertEquals(recreatedGroupUuid.toString(), copiedTimes.captured.single().groupUuid)
        assertEquals(recreatedGroupUuid.toString(), copiedWeeklyDays.captured.single().groupUuid)
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
            medicationLogDao.getPlannedEntryIdsForGroupSlotTime(groupUuid.toString(), "08:00")
        } returns listOf("entry-8")
        coEvery {
            medicationLogDao.getPlannedEntryIdsForGroupSlotTime(groupUuid.toString(), "09:00")
        } returns listOf("entry-9")

        repository.updateScheduleTimes(
            groupUuid = groupUuid,
            newTimes = listOf(LocalTime.of(8, 30), LocalTime.of(9, 30)),
            now = now,
        )

        coVerifyOrder {
            medicationLogDao.getPlannedEntryIdsForGroupSlotTime(groupUuid.toString(), "08:00")
            medicationLogDao.getPlannedEntryIdsForGroupSlotTime(groupUuid.toString(), "09:00")
            medicationGroupDao.updateGroupUpdatedAt(groupUuid.toString(), now.toEpochMilli())
            medicationGroupDao.deleteScheduleTimesForGroup(groupUuid.toString())
            medicationGroupDao.insertScheduleTimes(
                listOf(
                    MedicationGroupScheduleTimeEntity(groupUuid.toString(), 0, 8, 30),
                    MedicationGroupScheduleTimeEntity(groupUuid.toString(), 1, 9, 30),
                )
            )
            medicationLogDao.updateScheduledForTimeForEntries(listOf("entry-8"), "08:30")
            medicationLogDao.updateScheduledForTimeForEntries(listOf("entry-9"), "09:30")
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
    fun validateScheduleTimeMigration_rejectsReorder() {
        assertThrows(ScheduleTimeReorderNotAllowedException::class.java) {
            validateScheduleTimeMigration(
                oldTimes = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                newTimes = listOf(LocalTime.of(21, 0), LocalTime.of(20, 0)),
            )
        }
    }

    private fun testGroupEntity(
        groupUuid: UUID,
        times: List<LocalTime>,
        items: List<MedicationGroupItemEntity> = emptyList(),
        weeklyDays: List<MedicationGroupWeeklyDayEntity> = emptyList(),
    ): MedicationGroupWithItemsEntity {
        return MedicationGroupWithItemsEntity(
            group = MedicationGroupEntity(
                uuid = groupUuid.toString(),
                name = "Group",
                colorKey = "ROSE",
                notificationsEnabled = true,
                scheduleType = "DAILY",
                scheduleInterval = 1,
                scheduleSinceEpochDay = 0,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0,
                archivedAtEpochMillis = null,
            ),
            items = items,
            scheduleTimes = times.mapIndexed { index, time ->
                MedicationGroupScheduleTimeEntity(
                    groupUuid = groupUuid.toString(),
                    sortOrder = index,
                    hourOfDay = time.hour,
                    minuteOfHour = time.minute,
                )
            },
            weeklyDays = weeklyDays,
        )
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
}
