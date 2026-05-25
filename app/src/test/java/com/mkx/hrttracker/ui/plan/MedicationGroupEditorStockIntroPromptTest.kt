package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.medication.testPatchOffMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.ui.catalog.MedicineSlotResult
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationGroupEditorStockIntroPromptTest {
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicineRepository: MedicineRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk(relaxed = true)
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler =
        mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 5, 25, 10, 0))
    private val stockIntroPromptShown = MutableStateFlow(false)
    private lateinit var settingsStateFlow: MutableStateFlow<SettingsState>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settingsStateFlow = MutableStateFlow(SettingsState(remindersEnabled = true))
        every { settingsRepository.settingsState } returns settingsStateFlow
        every { settingsRepository.stockIntroPromptShownFlow } returns stockIntroPromptShown
        coEvery { settingsRepository.getCurrentSettings() } returns settingsStateFlow.value
        coEvery { settingsRepository.peekNextGroupNameIndex() } returns 1
        coEvery { settingsRepository.consumeNextGroupNameIndex() } returns 1
        coEvery { settingsRepository.markStockIntroPromptShown() } coAnswers {
            stockIntroPromptShown.value = true
        }
        every { medicationGroupRepository.getCachedGroup(any()) } returns null
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicineRepository.observeAllActive() } returns flowOf(emptyList())
        every {
            context.getString(R.string.default_group_name_format, any())
        } returns "Group 1"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveFirstGroup_emitsStockIntroPromptForDistinctNonPatchOffMedicines() = runTest {
        val firstMedicine = testMedicine(
            uuid = UUID.fromString("11111111-1111-4111-8111-111111111111"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val secondMedicine = testMedicine(
            uuid = UUID.fromString("22222222-2222-4222-8222-222222222222"),
            key = MedicationKey.ESTRADIOL_VALERATE,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 4.0),
        )
        val patchOff = testPatchOffMedicine(
            uuid = UUID.fromString("33333333-3333-4333-8333-333333333333"),
        )
        val savedGroupUuid = UUID.fromString("44444444-4444-4444-8444-444444444444")

        every { medicineRepository.observeAllActive() } returns MutableStateFlow(
            listOf(firstMedicine, secondMedicine, patchOff)
        )
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        } returns savedGroupUuid

        val viewModel = viewModel()
        val events = mutableListOf<MedicationGroupEditorEvent>()
        backgroundScope.launch {
            viewModel.events.collect { event -> events += event }
        }
        advanceUntilIdle()

        viewModel.updateGroupName("First group")
        viewModel.addCompletedMedicationSlot(
            localId = UUID.randomUUID().toString(),
            slot = slot(
                medicine = firstMedicine,
                applicationType = MedicationApplicationType.SUBLINGUAL,
            ),
        )
        viewModel.addCompletedMedicationSlot(
            localId = UUID.randomUUID().toString(),
            slot = slot(firstMedicine),
        )
        viewModel.addCompletedMedicationSlot(
            localId = UUID.randomUUID().toString(),
            slot = slot(secondMedicine),
        )
        viewModel.addCompletedMedicationSlot(
            localId = UUID.randomUUID().toString(),
            slot = MedicineSlotResult(
                medicineUuid = patchOff.uuid,
                applicationType = MedicationApplicationType.PATCH_OFF,
                doseInstruction = DoseInstruction.Noop,
                count = 1,
            ),
        )
        advanceUntilIdle()

        viewModel.saveGroup()
        advanceUntilIdle()

        assertEquals(2, events.size)
        val event = events[0] as MedicationGroupEditorEvent.ShowStockIntroPrompt
        assertEquals(
            listOf(firstMedicine.uuid, secondMedicine.uuid),
            event.medicines.map(Medicine::uuid),
        )
        assertEquals(MedicationGroupEditorEvent.SaveCompleted, events[1])
        assertTrue(stockIntroPromptShown.value)
        coVerify(exactly = 1) { settingsRepository.markStockIntroPromptShown() }
    }

    @Test
    fun stockIntroPromptFailure_doesNotBlockSaveCompletionOrScheduling() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("55555555-5555-4555-8555-555555555555"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val savedGroupUuid = UUID.fromString("66666666-6666-4666-8666-666666666666")
        val completionEvents = mutableListOf<MedicationGroupEditorCompletionEvent>()

        every { medicineRepository.observeAllActive() } returns MutableStateFlow(listOf(medicine))
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = null,
                name = any(),
                colorKey = any(),
                schedule = any(),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                replacesGroupUuid = any(),
                now = any(),
            )
        } returns savedGroupUuid
        coEvery { settingsRepository.markStockIntroPromptShown() } throws
            RuntimeException("preferences unavailable")

        val viewModel = viewModel()
        backgroundScope.launch {
            viewModel.completionEvents.collect { event -> completionEvents += event }
        }
        advanceUntilIdle()

        viewModel.updateGroupName("First group")
        viewModel.addCompletedMedicationSlot(
            localId = UUID.randomUUID().toString(),
            slot = slot(medicine),
        )
        advanceUntilIdle()

        viewModel.saveGroup()
        advanceUntilIdle()

        assertEquals(
            listOf(MedicationGroupEditorCompletionEvent.SAVE_COMPLETED),
            completionEvents,
        )
        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(savedGroupUuid, any()) }
    }

    @Test
    fun enableStockTrackingFromIntro_enablesTrackingWithInitialCount() = runTest {
        val medicineUuid = UUID.fromString("77777777-7777-4777-8777-777777777777")
        coEvery {
            medicineRepository.enableTracking(
                uuid = medicineUuid,
                initialUnitsRemaining = 42.0,
                initialOpenContainerAmount = null,
                initialUnitsLastTotal = 42.0,
                now = any(),
            )
        } returns Unit

        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.enableStockTrackingFromIntro(
            medicineUuid = medicineUuid,
            initialCount = 42.0,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            medicineRepository.enableTracking(
                uuid = medicineUuid,
                initialUnitsRemaining = 42.0,
                initialOpenContainerAmount = null,
                initialUnitsLastTotal = 42.0,
                now = any(),
            )
        }
    }

    private fun viewModel(): MedicationGroupEditorViewModel {
        return MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(),
            appTimeSource = appTimeSource,
        )
    }

    private fun slot(
        medicine: Medicine,
        applicationType: MedicationApplicationType = MedicationApplicationType.ORAL,
    ): MedicineSlotResult {
        return MedicineSlotResult(
            medicineUuid = medicine.uuid,
            applicationType = applicationType,
            doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 1),
            count = 1,
        )
    }
}
