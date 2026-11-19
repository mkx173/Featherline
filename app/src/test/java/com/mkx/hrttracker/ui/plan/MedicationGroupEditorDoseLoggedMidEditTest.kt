package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
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
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.withAppLanguage
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * A count or past-option edit on a plan with no record, then one of its doses logged before Save.
 * The plan editor's rule (product decisions, round 2): nothing is reverted, and Save is refused.
 * The harness is MedicationGroupEditorRecordLockTest's: the real editor ViewModel, the real
 * MedicationGroupRepository and an in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationGroupEditorDoseLoggedMidEditTest {
    private val dispatcher = StandardTestDispatcher()
    private val databaseHolder: DatabaseHolder = mockk()
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk(relaxed = true)
    private val medicineRepository: MedicineRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val entries = MutableStateFlow<List<MedicationLogEntry>?>(emptyList())
    private val now = LocalDateTime.of(2026, 11, 8, 12, 0)
    private val since = LocalDate.of(2026, 11, 1)
    private val medicine: Medicine = testMedicine(uuid = UUID.fromString("5a4b3c2d-0000-4000-8000-000000000001"))

    private lateinit var room: HrtTrackerDatabase
    private lateinit var repository: MedicationGroupRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        room = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HrtTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        every { databaseHolder.get() } returns room
        coEvery { databaseHolder.withTransaction<Unit>(any()) } coAnswers {
            room.withTransaction { firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(room) }
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
        every { medicineRepository.observeAllActive() } returns MutableStateFlow(listOf(medicine))
        every { context.getString(R.string.default_group_name_format, any()) } returns "Group 1"
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { context.withAppLanguage() } returns context
    }

    @After
    fun tearDown() {
        clearAllMocks()
        room.close()
        Dispatchers.resetMain()
        unmockkStatic("com.mkx.hrttracker.util.LocalizationKt")
    }

    // 1 tablet -> 2 on a plan with no record; then its 08:00 dose is logged.
    @Test
    fun aCountEdit_thenADoseLoggedAndSeen_isRefused() = runTest(dispatcher) {
        aCountEditThenADoseLogged(seen = true)
    }

    @Test
    fun aCountEdit_thenADoseLoggedUnseen_isRefusedByTheRepository() = runTest(dispatcher) {
        aCountEditThenADoseLogged(seen = false)
    }

    private suspend fun TestScope.aCountEditThenADoseLogged(seen: Boolean) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val viewModel = editor(groupUuid)
        viewModel.showMedicationEditor(viewModel.uiState.value.medications.single().localId)
        viewModel.increaseEditingMedicationCount()
        viewModel.saveEditingMedication()
        assertEquals("the form shows the edit", 2, viewModel.uiState.value.medications.single().count)

        logRecord(groupUuid, now.toLocalDate().atTime(8, 0), observe = seen)
        assertEquals("nothing reverted in the form", 2, viewModel.uiState.value.medications.single().count)
        viewModel.saveGroup()
        advanceUntilIdle()

        assertRefused(viewModel)
        assertEquals(1, checkNotNull(repository.getGroup(groupUuid)).medications.single().count)
        assertEquals("the edit stays in the form", 2, viewModel.uiState.value.medications.single().count)
    }

    // "Hide past" picked on a plan with no record; then yesterday's dose is logged.
    @Test
    fun hidingThePast_thenADoseLogged_isRefused() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val viewModel = editor(groupUuid)
        viewModel.updateIncludePastScheduledSlots(false)
        assertFalse("the form shows the edit", viewModel.uiState.value.includePastScheduledSlots)

        logRecord(groupUuid, now.toLocalDate().minusDays(1).atTime(8, 0))
        assertFalse("nothing reverted in the form", viewModel.uiState.value.includePastScheduledSlots)
        viewModel.saveGroup()
        advanceUntilIdle()

        assertRefused(viewModel)
        assertTrue(checkNotNull(repository.getGroup(groupUuid)).includePastScheduledSlots)
        assertFalse("the edit stays in the form", viewModel.uiState.value.includePastScheduledSlots)
    }

    private fun assertRefused(viewModel: MedicationGroupEditorViewModel) {
        val state = viewModel.uiState.value
        assertFalse(state.isSaved)
        assertEquals(SaveMedicationGroupResult.LOCKED_BY_RECORDS, state.saveMedicationGroupResult)
    }

    private suspend fun createDailyPlan(vararg times: LocalTime): UUID {
        room.medicineDao().insert(medicine.toEntity())
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
            ),
            medications = listOf(
                MedicationGroupMedicationInput(
                    medicineUuid = medicine.uuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                ),
            ),
            includePastScheduledSlots = true,
            now = since.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant(),
        )
    }

    private fun TestScope.editor(
        groupUuid: UUID,
        clock: FakeAppTimeSource = FakeAppTimeSource(now),
    ): MedicationGroupEditorViewModel {
        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = repository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())),
            appTimeSource = clock,
        )
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoadingGroupForEditing)
        return viewModel
    }

    /** Logs the slot at [scheduledFor], as a widget or reminder "Log" does. */
    private suspend fun TestScope.logRecord(groupUuid: UUID, scheduledFor: LocalDateTime, observe: Boolean = true) {
        val slot = checkNotNull(repository.getGroup(groupUuid)).schedule.timeSlots
            .single { it.time == scheduledFor.toLocalTime() }
        room.medicationLogDao().insertEntry(
            MedicationLogEntryEntity(
                uuid = UUID.randomUUID().toString(),
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
                appliedAtEpochMillis = scheduledFor.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                scheduledForIso = scheduledFor.toString(),
            )
        )
        if (observe) {
            observeRecords()
            advanceUntilIdle()
        }
    }

    private suspend fun observeRecords() {
        entries.value = room.medicationLogDao().getEntries().map { entry ->
            entry.toMedicationLogEntryModel(mapOf(medicine.uuid.toString() to medicine))
        }
    }
}
