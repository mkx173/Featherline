package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationGroupDao
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.toEntity
import com.mkx.hrttracker.data.repository.toMedicationLogEntryModel
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSignature
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.withAppLanguage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A plan's records lock its slots: whatever the editor holds when one arrives, saving keeps every
 * record an occurrence of its own slot. Runs the editor over the real repository and database, so
 * a save that commits halfway shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationGroupEditorRecordLockTest {
    private val dispatcher = StandardTestDispatcher()
    private val databaseHolder: DatabaseHolder = mockk()
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk(relaxed = true)
    private val medicineRepository: MedicineRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk(relaxed = true)
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler =
        mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val entries = MutableStateFlow<List<MedicationLogEntry>?>(emptyList())
    private val now = LocalDateTime.of(2026, 11, 8, 12, 0)
    private val since = LocalDate.of(2026, 11, 1)
    private val medicine: Medicine = testMedicine(
        uuid = UUID.fromString("5a4b3c2d-0000-4000-8000-000000000001"),
    )
    private val secondMedicine: Medicine = testMedicine(
        uuid = UUID.fromString("5a4b3c2d-0000-4000-8000-000000000002"),
        key = MedicationKey.SPIRONOLACTONE,
        preparation = MedicinePreparation.Pill(strengthMgPerTablet = 50.0),
    )
    private val planMedicines = listOf(medicine, secondMedicine)

    private lateinit var room: HrtTrackerDatabase
    private lateinit var groupDao: MedicationGroupDao
    private lateinit var repository: MedicationGroupRepository
    private var failNextUpsert = false

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Inline executors keep Room on the test thread, so advanceUntilIdle waits for it.
        room = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HrtTrackerDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        groupDao = spyk(room.medicationGroupDao())
        // The one failure a test injects: the plan's own write, after anything before it.
        coEvery { groupDao.upsertGroupWithItems(any(), any(), any(), any()) } coAnswers {
            if (failNextUpsert) {
                failNextUpsert = false
                throw IllegalStateException("Injected plan write failure")
            }
            callOriginal()
        }
        val database: HrtTrackerDatabase = mockk {
            every { medicationGroupDao() } returns groupDao
            every { medicationLogDao() } returns room.medicationLogDao()
            every { medicineDao() } returns room.medicineDao()
        }
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        every { databaseHolder.get() } returns database
        coEvery { databaseHolder.withTransaction<Unit>(any()) } coAnswers {
            room.withTransaction { firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database) }
        }
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        repository = MedicationGroupRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            appScope = CoroutineScope(dispatcher),
        )

        val settings = SettingsState(remindersEnabled = true)
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        coEvery { settingsRepository.getCurrentSettings() } returns settings
        coEvery { settingsRepository.peekNextGroupNameIndex() } returns 1
        coEvery { settingsRepository.consumeNextGroupNameIndex() } returns 1
        every { medicationLogRepository.observeEntries() } returns entries
        every { medicineRepository.observeAllActive() } returns MutableStateFlow(planMedicines)
        every { context.getString(R.string.default_group_name_format, any()) } returns "Group 1"
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { context.withAppLanguage() } returns context
    }

    @After
    fun tearDown() {
        room.close()
        Dispatchers.resetMain()
        unmockkStatic("com.mkx.hrttracker.util.LocalizationKt")
    }

    @Test
    fun aRecordArrivingAfterAScheduleTypeChangeStaysOnItsSlot() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val viewModel = editor(groupUuid)
        viewModel.updateGroupName("Mornings")
        viewModel.updateScheduleType(MedicationGroupScheduleType.WEEKLY)

        val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0))
        viewModel.saveGroup()
        advanceUntilIdle()

        assertRecordOnItsSlot(groupUuid, record)
        assertNull(viewModel.uiState.value.saveMedicationGroupResult)
        // The name isn't the schedule's: it is still saved.
        assertEquals("Mornings", repository.getGroup(groupUuid)?.name)
    }

    @Test
    fun aRecordLandingAsTheSaveStartsRefusesAScheduleTypeChangeUntilTheEditorSeesIt() =
        runTest(dispatcher) {
            val groupUuid = createDailyPlan(LocalTime.of(8, 0))
            val viewModel = editor(groupUuid)
            viewModel.updateScheduleType(MedicationGroupScheduleType.WEEKLY)

            // Written, but not yet observed by the editor.
            val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0), observe = false)
            viewModel.saveGroup()
            advanceUntilIdle()

            assertRecordOnItsSlot(groupUuid, record)
            assertEquals(
                SaveMedicationGroupResult.LOCKED_BY_RECORDS,
                viewModel.uiState.value.saveMedicationGroupResult,
            )

            // Once the editor sees the record, the next save goes through.
            observeRecords()
            advanceUntilIdle()
            viewModel.consumeSaveMedicationGroupResult()
            viewModel.saveGroup()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isSaved)
            assertRecordOnItsSlot(groupUuid, record)
        }

    @Test
    fun aRecordArrivingAfterATimeWasAddedStillLetsThePlanSave() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val slotUuid = slotUuids(groupUuid).single()
        val viewModel = editor(groupUuid)
        viewModel.updateGroupName("Mornings")
        viewModel.addDailyTime(LocalTime.of(20, 0))

        val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0))
        viewModel.saveGroup()
        advanceUntilIdle()

        assertNull(
            "The save should go through once the record locks the slots",
            viewModel.uiState.value.saveMedicationGroupResult,
        )
        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(listOf(slotUuid), slotUuids(groupUuid))
        assertRecordOnItsSlot(groupUuid, record)
        assertEquals("Mornings", repository.getGroup(groupUuid)?.name)
    }

    @Test
    fun aRecordArrivingAfterAMedicineWasRemovedStillMatchesThePlan() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), medicines = planMedicines)
        val viewModel = editor(groupUuid)
        viewModel.updateGroupName("Mornings")
        viewModel.removeMedication(
            viewModel.uiState.value.medications.single { it.medicineUuid == medicine.uuid }.localId
        )

        // Logged from the saved plan, which still has the removed medicine.
        val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0))
        viewModel.saveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertRecordMatchesAPlanMedication(groupUuid, record)
        assertEquals(
            planMedicines.map { it.uuid },
            checkNotNull(repository.getGroup(groupUuid)).medications.map { it.medicineUuid },
        )
        assertEquals("Mornings", repository.getGroup(groupUuid)?.name)
    }

    @Test
    fun aLockedRetimeBeforeTheStartIsRefusedWithItsOwnReason() = runTest(dispatcher) {
        // The plan starts at 07:00 on its first day, and that day's 08:00 dose is logged.
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), startAt = since.atTime(7, 0))
        val record = logRecord(groupUuid, since.atTime(8, 0))
        val viewModel = editor(groupUuid)
        assertTrue(viewModel.uiState.value.isLocked)

        // 06:00 would move that dose before the plan's start.
        viewModel.updateDailyTime(viewModel.uiState.value.dailyTimes.single().localId, LocalTime.of(6, 0))
        viewModel.saveGroup()
        advanceUntilIdle()

        assertRecordOnItsSlot(groupUuid, record)
        assertEquals(
            SaveMedicationGroupResult.LOCKED_BY_RECORDS,
            viewModel.uiState.value.saveMedicationGroupResult,
        )
    }

    @Test
    fun aStartMovedPastARecordThatArrivesGoesBackToTheSavedStart() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val viewModel = editor(groupUuid)
        viewModel.updateGroupName("Mornings")
        viewModel.updateSinceDate(now.toLocalDate().plusDays(2))

        val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0))
        assertEquals(since, viewModel.uiState.value.sinceDate)
        viewModel.saveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertRecordOnItsSlot(groupUuid, record)
        assertEquals("Mornings", repository.getGroup(groupUuid)?.name)
    }

    @Test
    fun aStartThatKeepsTheRecordsStaysWhenAnAddedTimeGoesBack() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val viewModel = editor(groupUuid)
        viewModel.updateStartTime(LocalTime.of(7, 0))
        viewModel.addDailyTime(LocalTime.of(20, 0))

        val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0))
        viewModel.saveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertRecordOnItsSlot(groupUuid, record)
        assertEquals(
            listOf(since.atTime(7, 0)),
            checkNotNull(repository.getGroup(groupUuid)).schedule.timeSlots.map { it.effectiveFrom },
        )
    }

    @Test
    fun aLockedTimeChangeRetriedAfterAFailedSaveMovesEachRecordWithItsOwnSlot() =
        runTest(dispatcher) {
            val groupUuid = createDailyPlan(LocalTime.of(8, 0), LocalTime.of(20, 0))
            val (morningSlot, eveningSlot) = slotUuids(groupUuid)
            val yesterday = now.toLocalDate().minusDays(1)
            val morning = logRecord(groupUuid, yesterday.atTime(8, 0))
            val evening = logRecord(groupUuid, yesterday.atTime(20, 0))
            val viewModel = editor(groupUuid)
            assertTrue(viewModel.uiState.value.isLocked)
            val times = viewModel.uiState.value.dailyTimes
            viewModel.updateDailyTime(times.single { it.time == LocalTime.of(8, 0) }.localId, LocalTime.of(21, 0))
            viewModel.updateDailyTime(times.single { it.time == LocalTime.of(20, 0) }.localId, LocalTime.of(7, 0))

            failNextUpsert = true
            viewModel.saveGroup()
            advanceUntilIdle()
            assertEquals(
                SaveMedicationGroupResult.FAILURE,
                viewModel.uiState.value.saveMedicationGroupResult,
            )
            viewModel.consumeSaveMedicationGroupResult()
            viewModel.saveGroup()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isSaved)
            val records = room.medicationLogDao().getEntries().associateBy { it.uuid }
            assertEquals(yesterday.atTime(21, 0).toString(), records.getValue(morning).scheduledForIso)
            assertEquals(morningSlot.toString(), records.getValue(morning).scheduleTimeUuid)
            assertEquals(yesterday.atTime(7, 0).toString(), records.getValue(evening).scheduledForIso)
            assertEquals(eveningSlot.toString(), records.getValue(evening).scheduleTimeUuid)
            assertRecordOnItsSlot(groupUuid, morning)
            assertRecordOnItsSlot(groupUuid, evening)
        }

    @Test
    fun aReplacementThatFailsLeavesThePlanActiveAndARetryReplacesIt() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val viewModel = editor(groupUuid)

        failNextUpsert = true
        viewModel.archiveAndRecreateGroup()
        advanceUntilIdle()

        assertEquals(
            ArchiveAndRecreateMedicationGroupResult.FAILURE,
            viewModel.uiState.value.archiveAndRecreateMedicationGroupResult,
        )
        assertNull(
            "A replacement that failed archived the plan anyway",
            repository.getGroup(groupUuid)?.archivedAt,
        )
        assertFalse(viewModel.uiState.value.isArchived)
        assertEquals(groupUuid.toString(), viewModel.uiState.value.editingGroupId)

        viewModel.consumeArchiveAndRecreateMedicationGroupResult()
        viewModel.archiveAndRecreateGroup()
        advanceUntilIdle()

        assertEquals(
            ArchiveAndRecreateMedicationGroupResult.SUCCESS,
            viewModel.uiState.value.archiveAndRecreateMedicationGroupResult,
        )
        val original = checkNotNull(repository.getGroup(groupUuid))
        val successorUuid = UUID.fromString(viewModel.uiState.value.editingGroupId)
        assertNotNull(original.archivedAt)
        assertEquals(successorUuid, original.replacedByGroupUuid)
        assertEquals(groupUuid, repository.getGroup(successorUuid)?.recreatedFromGroupUuid)
    }

    private suspend fun createDailyPlan(
        vararg times: LocalTime,
        medicines: List<Medicine> = listOf(medicine),
        startAt: LocalDateTime? = null,
    ): UUID {
        medicines.forEach { planMedicine -> room.medicineDao().insert(planMedicine.toEntity()) }
        return repository.saveGroup(
            uuid = null,
            name = "Plan",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = since,
                weeklyDaysOfWeek = emptySet(),
                times = times.toList(),
                startAt = startAt,
            ),
            medications = medicines.map { planMedicine ->
                MedicationGroupMedicationInput(
                    medicineUuid = planMedicine.uuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            },
            includePastScheduledSlots = true,
            now = since.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant(),
        )
    }

    private fun TestScope.editor(groupUuid: UUID): MedicationGroupEditorViewModel {
        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = repository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = FakeAppTimeSource(now),
        )
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoadingGroupForEditing)
        return viewModel
    }

    private suspend fun slotUuids(groupUuid: UUID): List<UUID> =
        checkNotNull(repository.getGroup(groupUuid)).schedule.timeSlots
            .sortedBy { it.time }
            .map { it.uuid }

    /** Logs the slot at [scheduledFor], as a widget or reminder "Log" does; returns the record's uuid. */
    private suspend fun TestScope.logRecord(
        groupUuid: UUID,
        scheduledFor: LocalDateTime,
        observe: Boolean = true,
    ): String {
        val slot = checkNotNull(repository.getGroup(groupUuid)).schedule.timeSlots
            .single { it.time == scheduledFor.toLocalTime() }
        val uuid = UUID.randomUUID().toString()
        room.medicationLogDao().insertEntry(
            MedicationLogEntryEntity(
                uuid = uuid,
                category = medicine.category.name,
                medicineUuid = medicine.uuid.toString(),
                applicationType = MedicationApplicationType.ORAL.name,
                doseInstructionKind = "TABLET_FRACTION",
                tabletFractionNumerator = 1,
                tabletFractionDenominator = 1,
                doseVolumeMl = null,
                doseWeightGrams = null,
                equivalentE2Mg = null,
                sourceGroupUuid = groupUuid.toString(),
                scheduleTimeUuid = slot.uuid.toString(),
                appliedAtEpochMillis = scheduledFor.atZone(ZoneId.systemDefault()).toInstant()
                    .toEpochMilli(),
                scheduledForIso = scheduledFor.toString(),
            )
        )
        if (observe) {
            observeRecords()
            advanceUntilIdle()
        }
        return uuid
    }

    private suspend fun observeRecords() {
        entries.value = room.medicationLogDao().getEntries().map { entry ->
            entry.toMedicationLogEntryModel(planMedicines.associateBy { it.uuid.toString() })
        }
    }

    /** The record's medicine and dose are still one of the plan's, so the plan doesn't call it off-plan. */
    private suspend fun assertRecordMatchesAPlanMedication(groupUuid: UUID, recordUuid: String) {
        val record = checkNotNull(room.medicationLogDao().getEntry(recordUuid))
            .toMedicationLogEntryModel(planMedicines.associateBy { it.uuid.toString() })
        val plan = checkNotNull(repository.getGroup(groupUuid))
        assertTrue(
            "Record of ${record.medicineUuid} matches none of the plan's medications " +
                    "${plan.medications.map { it.medicineUuid }}",
            plan.medications.any { medication ->
                MedicationSignature.fromGroupMedication(medication) ==
                        MedicationSignature.fromLogEntry(record)
            },
        )
    }

    /** The record is still an occurrence of the slot it was logged for, so the plan counts it. */
    private suspend fun assertRecordOnItsSlot(groupUuid: UUID, recordUuid: String) {
        val record = checkNotNull(room.medicationLogDao().getEntry(recordUuid))
        val scheduledFor = LocalDateTime.parse(record.scheduledForIso)
        val plan = checkNotNull(repository.getGroup(groupUuid))
        assertTrue(
            "Record at $scheduledFor lost its slot ${record.scheduleTimeUuid}: " +
                    "the plan's slots are now ${plan.schedule.timeSlots}",
            plan.occurrencesBetweenInPlanWindow(scheduledFor.toLocalDate(), scheduledFor.toLocalDate())
                .any { occurrence ->
                    occurrence.scheduleTimeUuid.toString() == record.scheduleTimeUuid &&
                            occurrence.scheduledFor == scheduledFor
                },
        )
    }
}
