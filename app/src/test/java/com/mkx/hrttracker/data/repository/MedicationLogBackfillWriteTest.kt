package com.mkx.hrttracker.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.reminder.MedicationReminderSlot
import com.mkx.hrttracker.reminder.buildMissingScheduledLogEntries
import com.mkx.hrttracker.ui.plan.buildPlanBatchAddEntries
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * The slot rule is "at most the plan's count per slot and medication", decided under the write
 * gate. Batch Add and the plan editor's past records build their entries from a read made before
 * it, so [MedicationLogRepository.saveBackfillEntries] trims them as the quick-log writers are
 * trimmed ([MedicationLogSlotWriteTest]); real repositories, in-memory Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicationLogBackfillWriteTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val gate: HomeSnapshotRepository = mockk()
    private lateinit var database: HrtTrackerDatabase
    private lateinit var groupRepository: MedicationGroupRepository
    private lateinit var logRepository: MedicationLogRepository

    private val estradiol = testMedicine(
        uuid = UUID.fromString("5a1e0000-0000-0000-0000-000000000001"),
        key = MedicationKey.ESTRADIOL,
        stock = MedicineStock(trackingEnabled = true, unitsRemaining = 10.0, unitsLastTotal = 10.0),
    )
    private val wholeTablet = DoseInstruction.TabletFraction(1, 1)
    private val scheduledAt = LocalDateTime.of(2026, 11, 8, 8, 0)
    private val now = LocalDateTime.of(2026, 11, 8, 12, 0)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HrtTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        every { databaseHolder.get() } returns database
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        coEvery { databaseHolder.withTransaction<Any?>(any()) } coAnswers {
            database.withTransaction { firstArg<suspend (HrtTrackerDatabase) -> Any?>().invoke(database) }
        }
        coEvery { gate.runHomeDataMutation<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
        val appScope = CoroutineScope(StandardTestDispatcher())
        groupRepository = MedicationGroupRepository(databaseHolder, gate, appScope)
        logRepository = MedicationLogRepository(databaseHolder, gate, MedicineStockMutator(), appScope)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aBatchAddSavedAfterAQuickLogOfItsSlot_logsTheDoseOnce() = runTest {
        val plan = seedPlan()
        // The batch add screen lists today's unlogged 08:00 dose; nothing has been written yet.
        val batch = buildPlanBatchAddEntries(
            group = plan,
            existingEntries = logRepository.getEntries(),
            startDate = scheduledAt.toLocalDate(),
            endDate = scheduledAt.toLocalDate(),
            now = now,
        )
        assertEquals("the batch holds the slot's one dose", 1, batch.size)

        // "Log" on the 08:00 reminder commits first, then the batch's Save.
        logRepository.saveNewEntries(quickLogAsReadBeforeTheGate(plan))
        val written = logRepository.saveBackfillEntries(batch, deductStock = true)

        assertEquals("nothing left to write", emptyList<MedicationLogEntryInput>(), written)
        assertEquals("the plan asks for 1 tablet at 08:00", 1, loggedCount(plan))
        assertEquals("deducted once", 9.0, stock(), 0.0)
    }

    @Test
    fun theEditorsPastRecordsSavedAfterAQuickLogOfASlot_logTheDoseOnce() = runTest {
        val plan = seedPlan()
        // MedicationGroupEditorViewModel.savePastScheduledSlotRecords: getEntries, then the write.
        val pastRecords = buildPlanBatchAddEntries(
            group = plan,
            existingEntries = logRepository.getEntries(),
            startDate = scheduledAt.toLocalDate(),
            endDate = scheduledAt.toLocalDate(),
            now = now,
        )

        logRepository.saveNewEntries(quickLogAsReadBeforeTheGate(plan))
        logRepository.saveBackfillEntries(pastRecords)

        assertEquals("the plan asks for 1 tablet at 08:00", 1, loggedCount(plan))
    }

    // F9: a pre-start occurrence is saved as a manual row, which the trim passes through.
    @Test
    fun preStartManualRows_areWrittenAgainOnARerun() = runTest {
        val plan = seedPlan()
        val day = LocalDate.of(2026, 11, 1)
        val rerun = {
            buildPlanBatchAddEntries(group = plan, startDate = day, endDate = day, now = now)
        }
        assertEquals(1, logRepository.saveBackfillEntries(rerun()).size)
        assertEquals(1, logRepository.saveBackfillEntries(rerun()).size)
        assertEquals(2, database.medicationLogDao().getEntries().count { entry -> entry.sourceGroupUuid == null })
    }

    // One estradiol tablet at 08:00, daily since Nov 8.
    private suspend fun seedPlan(): MedicationGroup {
        database.medicineDao().insert(estradiol.toEntity())
        val time = LocalTime.of(8, 0)
        val uuid = groupRepository.saveGroup(
            uuid = null,
            name = "Morning",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 11, 8),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(time),
                timeSlots = listOf(MedicationGroupScheduleTimeInput(uuid = null, time = time)),
            ),
            medications = listOf(
                MedicationGroupMedicationInput(
                    medicineUuid = estradiol.uuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = wholeTablet,
                    count = 1,
                ),
            ),
            now = testInstant(LocalDateTime.of(2026, 11, 8, 7, 0)),
        )
        return checkNotNull(groupRepository.getGroup(uuid))
    }

    private suspend fun quickLogAsReadBeforeTheGate(plan: MedicationGroup): List<MedicationLogEntryInput> {
        return buildMissingScheduledLogEntries(
            group = plan,
            slot = MedicationReminderSlot(
                groupUuid = plan.uuid,
                scheduledAt = scheduledAt,
                scheduleTimeUuid = plan.schedule.timeSlots.single().uuid,
            ),
            entries = logRepository.getScheduledGroupEntriesSince(scheduledAt),
            appliedAt = scheduledAt.plusMinutes(10),
        )
    }

    private suspend fun loggedCount(plan: MedicationGroup): Int {
        return database.medicationLogDao().getEntries()
            .filter { entry -> entry.sourceGroupUuid == plan.uuid.toString() && entry.scheduledForIso == scheduledAt.toString() }
            .sumOf { entry -> entry.count }
    }

    private suspend fun stock(): Double {
        return checkNotNull(database.medicineDao().getByUuid(estradiol.uuid.toString())?.stockUnitsRemaining)
    }
}
