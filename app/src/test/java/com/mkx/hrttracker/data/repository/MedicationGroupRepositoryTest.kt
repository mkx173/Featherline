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
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDetails
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
    fun archiveGroup_setsArchivedAtWithoutTouchingNotificationsInSingleTransaction() = runTest {
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
            )
        } returns Unit

        repository.archiveGroup(groupUuid, now)

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerify(exactly = 1) {
            medicationGroupDao.updateGroupArchiveState(
                uuid = groupUuid.toString(),
                archivedAtEpochMillis = now.toEpochMilli(),
                updatedAtEpochMillis = now.toEpochMilli(),
            )
        }
    }

    @Test
    fun unarchiveGroup_clearsArchivedAtWithoutTouchingNotificationsInSingleTransaction() = runTest {
        val groupUuid = UUID.fromString("810b59ca-af49-45ee-a9fe-f9fe268a94dc")
        val now = Instant.parse("2026-04-30T09:00:00Z")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0)),
            archivedAtEpochMillis = 1L,
        )
        coEvery {
            medicationGroupDao.clearGroupArchive(
                uuid = groupUuid.toString(),
                updatedAtEpochMillis = now.toEpochMilli(),
            )
        } returns Unit

        repository.unarchiveGroup(groupUuid, now)

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerify(exactly = 1) {
            medicationGroupDao.clearGroupArchive(
                uuid = groupUuid.toString(),
                updatedAtEpochMillis = now.toEpochMilli(),
            )
        }
        coVerify(exactly = 0) {
            medicationGroupDao.updateGroupArchiveState(
                uuid = any(),
                archivedAtEpochMillis = any(),
                updatedAtEpochMillis = any(),
            )
        }
    }

    @Test
    fun unarchiveGroup_throwsWhenReplacementGroupIsStillActive() = runTest {
        val groupUuid = UUID.fromString("a3a16c10-e9c3-4f4a-9ddf-1e8b4d3aa01d")
        val replacementUuid = UUID.fromString("be3eaa20-6e2c-4cc4-8a5b-5b9a8a8e0c10")
        val now = Instant.parse("2026-04-30T09:00:00Z")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationGroupDao.getGroup(groupUuid.toString()) } returns testGroupEntity(
            groupUuid = groupUuid,
            times = listOf(LocalTime.of(8, 0)),
            archivedAtEpochMillis = 1L,
            replacedByGroupUuid = replacementUuid.toString(),
        )
        coEvery { medicationGroupDao.getGroup(replacementUuid.toString()) } returns testGroupEntity(
            groupUuid = replacementUuid,
            times = listOf(LocalTime.of(8, 0)),
        )

        assertThrows(MedicationGroupReplacedByActiveSuccessorException::class.java) {
            kotlinx.coroutines.runBlocking { repository.unarchiveGroup(groupUuid, now) }
        }
        coVerify(exactly = 0) {
            medicationGroupDao.clearGroupArchive(any(), any())
        }
    }

    @Test
    fun saveGroup_withReplacement_createsActiveCopyAndLinksArchivedOriginal() = runTest {
        val originalGroupUuid = UUID.fromString("893f1577-5d7f-447f-b626-45cd7dc69e33")
        val savedGroup = slot<MedicationGroupEntity>()
        val savedItems = slot<List<MedicationGroupItemEntity>>()
        val savedTimes = slot<List<MedicationGroupScheduleTimeEntity>>()
        val savedWeeklyDays = slot<List<MedicationGroupWeeklyDayEntity>>()
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
        assertEquals(savedGroupUuid.toString(), savedItems.captured.single().groupUuid)
        assertEquals(savedGroupUuid.toString(), savedTimes.captured.single().groupUuid)
        assertEquals(savedGroupUuid.toString(), savedWeeklyDays.captured.single().groupUuid)
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
        archivedAtEpochMillis: Long? = null,
        replacedByGroupUuid: String? = null,
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
                archivedAtEpochMillis = archivedAtEpochMillis,
                replacedByGroupUuid = replacedByGroupUuid,
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

    private fun testMedicationDetails(): MedicationDetails {
        return MedicationDetails(
            category = MedicationKey.ESTRADIOL.category,
            applicationType = MedicationApplicationType.ORAL,
            selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL),
            dose = MedicationDose.MgAsMedicine(2.0),
        )
    }
}
