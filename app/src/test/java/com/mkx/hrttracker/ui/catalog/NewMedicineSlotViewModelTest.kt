package com.mkx.hrttracker.ui.catalog

import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationForm
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.ui.medication.changeForm
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class NewMedicineSlotViewModelTest {
    private val medicineRepository: MedicineRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicineStockRepository: MedicineStockRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveGroupSlot_createsMedicineAndPublishesCompletedSlot() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000301"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        coEvery {
            medicineRepository.findOrCreateForCatalog(
                MedicationKey.ESTRADIOL,
                MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                any(),
            )
        } returns medicine
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft {
            it.copy(
                medicationKey = MedicationKey.ESTRADIOL,
                pillStrengthMg = "2",
            )
        }

        viewModel.saveGroupSlot()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(
            MedicineSlotResult(
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                count = 1,
            ),
            viewModel.uiState.value.slotResult,
        )
    }

    @Test
    fun saveGroupSlot_usesDoseDraftRouteForNewTabletMedicine() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000310"),
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        coEvery {
            medicineRepository.findOrCreateForCustom(
                customMedicationName = "Custom E2",
                displayName = any(),
                category = MedicationCategory.CUSTOM,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                displayDoseUnit = any(),
                now = any(),
            )
        } returns medicine
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft {
            defaultMedicineDraft(category = MedicationCategory.CUSTOM)
                .copy(customMedicationName = "Custom E2", pillStrengthMg = "2")
        }
        viewModel.updateDoseInstructionDraft {
            it.copy(
                applicationType = MedicationApplicationType.SUBLINGUAL,
            )
        }

        viewModel.saveGroupSlot()?.join()

        val result = viewModel.uiState.value.slotResult!!
        assertEquals(MedicationApplicationType.SUBLINGUAL, result.applicationType)
    }

    @Test
    fun saveGroupSlot_usesOralForNewCapsuleRegardlessOfDraftRoute() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000311"),
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )
        coEvery {
            medicineRepository.findOrCreateForCustom(
                customMedicationName = "Progesterone",
                displayName = any(),
                category = MedicationCategory.CUSTOM,
                preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
                displayDoseUnit = any(),
                now = any(),
            )
        } returns medicine
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft {
            defaultMedicineDraft(category = MedicationCategory.CUSTOM)
                .changeForm(MedicinePreparationForm.CAPSULE)
                .copy(customMedicationName = "Progesterone", pillStrengthMg = "100")
        }
        viewModel.updateDoseInstructionDraft {
            it.copy(
                applicationType = MedicationApplicationType.SUBLINGUAL,
            )
        }

        viewModel.saveGroupSlot()?.join()

        val result = viewModel.uiState.value.slotResult!!
        assertEquals(MedicationApplicationType.ORAL, result.applicationType)
    }

    @Test
    fun consumeSavedState_afterGroupSaveClearsPublishedSlotResult() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000308"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        coEvery { medicineRepository.findOrCreateForCatalog(any(), any(), any()) } returns medicine
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "2") }

        viewModel.saveGroupSlot()
        advanceUntilIdle()
        viewModel.consumeSavedState()

        assertFalse(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.slotResult)
    }

    @Test
    fun saveGroupSlot_validatesInputBeforeCreatingMedicine() = runTest {
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft {
            it.changeForm(MedicinePreparationForm.INJECTION).copy(
                medicationKey = MedicationKey.ESTRADIOL_VALERATE,
                preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
                concentrationMgPerMl = "20",
                vialVolumeMl = "5",
            )
        }

        viewModel.saveGroupSlot()
        advanceUntilIdle()

        assertEquals(R.string.validation_dose_volume_required, viewModel.uiState.value.errorMessageRes)
        assertNull(viewModel.uiState.value.slotResult)
        coVerify(exactly = 0) { medicineRepository.findOrCreateForCatalog(any(), any(), any()) }
    }

    @Test
    fun saveManualLog_createsMedicineThenSavesLogAtAppliedDateTime() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000302"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val zoneId = ZoneId.of("Asia/Tokyo")
        val appliedDate = LocalDate.of(2026, 5, 24)
        val appliedTime = LocalTime.of(22, 30)
        val appliedAt = LocalDateTime.of(appliedDate, appliedTime).atZone(zoneId).toInstant()
        coEvery { medicineRepository.findOrCreateForCatalog(any(), any(), any()) } returns medicine
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = appliedAt,
                scheduledFor = null,
                count = 1,
                appliedAtTimeZoneId = zoneId.id,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit
        val viewModel = newViewModel(zoneId)
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "2") }
        viewModel.updateAppliedDate(appliedDate)
        viewModel.updateAppliedTime(appliedTime)

        viewModel.saveManualLog()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.manualLogSaveResult)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = appliedAt,
                scheduledFor = null,
                count = 1,
                appliedAtTimeZoneId = zoneId.id,
            )
        }
    }

    @Test
    fun saveManualLog_forActualDoseDeltaFormPassesDeltaToLogSave() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-00000000030a"),
            key = MedicationKey.ESTRADIOL_VALERATE,
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 20.0,
                vialVolumeMl = 5.0,
            ),
        )
        coEvery {
            medicineRepository.findOrCreateForCatalog(
                MedicationKey.ESTRADIOL_VALERATE,
                MedicinePreparation.InjectionMultiUseVial(
                    concentrationMgPerMl = 20.0,
                    vialVolumeMl = 5.0,
                ),
                any(),
            )
        } returns medicine
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.VolumeMl(0.5),
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
                appliedAtTimeZoneId = "Asia/Tokyo",
                doseAmountDelta = match { abs(it - 0.1) < 1e-12 },
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft {
            it.changeForm(MedicinePreparationForm.INJECTION).copy(
                medicationKey = MedicationKey.ESTRADIOL_VALERATE,
                preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
                concentrationMgPerMl = "20",
                vialVolumeMl = "5",
            )
        }
        viewModel.updateDoseInstructionDraft { it.copy(volumeMl = "0.5") }
        viewModel.adjustDoseAmountDelta(0.1)

        viewModel.saveManualLog()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.VolumeMl(0.5),
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
                appliedAtTimeZoneId = "Asia/Tokyo",
                doseAmountDelta = match { abs(it - 0.1) < 1e-12 },
            )
        }
    }

    @Test
    fun saveManualLog_whenLogSaveFailsKeepsMedicineCreatedAndReportsLogFailure() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000303"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        coEvery { medicineRepository.findOrCreateForCatalog(any(), any(), any()) } returns medicine
        coEvery {
            medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("save failed")
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "2") }

        viewModel.saveManualLog()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals(MedicineSlotDraftSaveResult.FAILURE, viewModel.uiState.value.manualLogSaveResult)
        coVerify(exactly = 1) { medicineRepository.findOrCreateForCatalog(any(), any(), any()) }
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun resetClearsMedicineDoseAppliedAndMessagesForNextOpen() = runTest {
        val zoneId = ZoneId.of("Asia/Tokyo")
        val viewModel = newViewModel(zoneId)
        viewModel.updateCountText("3")
        viewModel.updateAppliedDate(LocalDate.of(2026, 5, 1))
        viewModel.updateAppliedTime(LocalTime.of(7, 45))
        viewModel.saveGroupSlot()
        advanceUntilIdle()
        assertEquals(R.string.validation_pill_strength_required, viewModel.uiState.value.errorMessageRes)

        viewModel.reset()

        assertEquals("", viewModel.uiState.value.medicineDraft.pillStrengthMg)
        assertEquals("1", viewModel.uiState.value.countText)
        assertNull(viewModel.uiState.value.errorMessageRes)
        assertNull(viewModel.uiState.value.createSaveResult)
        assertNull(viewModel.uiState.value.manualLogSaveResult)
        assertNull(viewModel.uiState.value.slotResult)
        assertEquals(zoneId, viewModel.uiState.value.appliedZoneId)
    }

    @Test
    fun resetWhileSaveIsSuspendedPreventsOldCompletionFromMutatingResetState() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000304"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val saveStarted = CompletableDeferred<Unit>()
        val allowSaveToComplete = CompletableDeferred<Unit>()
        coEvery { medicineRepository.findOrCreateForCatalog(any(), any(), any()) } coAnswers {
            saveStarted.complete(Unit)
            allowSaveToComplete.await()
            medicine
        }
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "2") }

        viewModel.saveGroupSlot()
        advanceUntilIdle()
        saveStarted.await()
        viewModel.reset()
        allowSaveToComplete.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.slotResult)
        assertEquals("", viewModel.uiState.value.medicineDraft.pillStrengthMg)
    }

    @Test
    fun saveManualLog_whenReturnedJobIsCancelledClearsSavingState() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000305"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val logSaveStarted = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()
        coEvery { medicineRepository.findOrCreateForCatalog(any(), any(), any()) } returns medicine
        coEvery {
            medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            logSaveStarted.complete(Unit)
            neverCompletes.await()
        }
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "2") }

        val job = checkNotNull(viewModel.saveManualLog())
        advanceUntilIdle()
        logSaveStarted.await()
        job.cancelAndJoin()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.manualLogSaveResult)
    }

    @Test
    fun saveGroupSlot_whenReturnedJobIsCancelledDuringMedicineCreateClearsSavingState() = runTest {
        val createStarted = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<com.mkx.hrttracker.model.medication.Medicine>()
        coEvery { medicineRepository.findOrCreateForCatalog(any(), any(), any()) } coAnswers {
            createStarted.complete(Unit)
            neverCompletes.await()
        }
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "2") }

        val job = checkNotNull(viewModel.saveGroupSlot())
        advanceUntilIdle()
        createStarted.await()
        job.cancelAndJoin()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.slotResult)
        assertNull(viewModel.uiState.value.createSaveResult)
    }

    @Test
    fun saveManualLog_whenReturnedJobIsCancelledDuringMedicineCreateClearsSavingState() = runTest {
        val createStarted = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<com.mkx.hrttracker.model.medication.Medicine>()
        coEvery { medicineRepository.findOrCreateForCatalog(any(), any(), any()) } coAnswers {
            createStarted.complete(Unit)
            neverCompletes.await()
        }
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "2") }

        val job = checkNotNull(viewModel.saveManualLog())
        advanceUntilIdle()
        createStarted.await()
        job.cancelAndJoin()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.slotResult)
        assertNull(viewModel.uiState.value.createSaveResult)
        assertNull(viewModel.uiState.value.manualLogSaveResult)
        coVerify(exactly = 0) {
            medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun saveGroupSlot_whenSaveAlreadyInFlightDoesNotStartSecondRepositoryCall() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000306"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val saveStarted = CompletableDeferred<Unit>()
        val allowSaveToComplete = CompletableDeferred<Unit>()
        coEvery { medicineRepository.findOrCreateForCatalog(any(), any(), any()) } coAnswers {
            saveStarted.complete(Unit)
            allowSaveToComplete.await()
            medicine
        }
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "2") }

        viewModel.saveGroupSlot()
        advanceUntilIdle()
        saveStarted.await()
        val secondJob = viewModel.saveGroupSlot()
        allowSaveToComplete.complete(Unit)
        advanceUntilIdle()

        assertNull(secondJob)
        coVerify(exactly = 1) { medicineRepository.findOrCreateForCatalog(any(), any(), any()) }
    }

    @Test
    fun updateMethodsWhileSavingAreIgnored() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000307"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val saveStarted = CompletableDeferred<Unit>()
        val allowSaveToComplete = CompletableDeferred<Unit>()
        coEvery { medicineRepository.findOrCreateForCatalog(any(), any(), any()) } coAnswers {
            saveStarted.complete(Unit)
            allowSaveToComplete.await()
            medicine
        }
        val viewModel = newViewModel()
        val originalDate = viewModel.uiState.value.appliedDate
        val originalTime = viewModel.uiState.value.appliedTime
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "2") }

        viewModel.saveGroupSlot()
        advanceUntilIdle()
        saveStarted.await()
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "4") }
        viewModel.updateDoseInstructionDraft { it.copy(tabletFractionNumerator = 2) }
        viewModel.updateCountText("3")
        viewModel.updateAppliedDate(LocalDate.of(2026, 5, 2))
        viewModel.updateAppliedTime(LocalTime.of(8, 15))
        allowSaveToComplete.complete(Unit)
        advanceUntilIdle()

        assertEquals("2", viewModel.uiState.value.medicineDraft.pillStrengthMg)
        assertEquals(DoseInstruction.TabletFraction(1, 1), viewModel.uiState.value.slotResult?.doseInstruction)
        assertEquals("1", viewModel.uiState.value.countText)
        assertEquals(originalDate, viewModel.uiState.value.appliedDate)
        assertEquals(originalTime, viewModel.uiState.value.appliedTime)
    }

    @Test
    fun updateMethodsAfterGroupSaveBeforeConsumptionAreIgnoredAndKeepSlotResult() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000309"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        coEvery { medicineRepository.findOrCreateForCatalog(any(), any(), any()) } returns medicine
        val viewModel = newViewModel()
        val originalDate = viewModel.uiState.value.appliedDate
        val originalTime = viewModel.uiState.value.appliedTime
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "2") }

        viewModel.saveGroupSlot()
        advanceUntilIdle()
        val savedSlotResult = viewModel.uiState.value.slotResult
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "4") }
        viewModel.updateDoseInstructionDraft { it.copy(tabletFractionNumerator = 2) }
        viewModel.updateCountText("3")
        viewModel.updateAppliedDate(LocalDate.of(2026, 5, 2))
        viewModel.updateAppliedTime(LocalTime.of(8, 15))

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals("2", viewModel.uiState.value.medicineDraft.pillStrengthMg)
        assertEquals(DoseInstruction.TabletFraction(1, 1), viewModel.uiState.value.slotResult?.doseInstruction)
        assertEquals(savedSlotResult, viewModel.uiState.value.slotResult)
        assertEquals("1", viewModel.uiState.value.countText)
        assertEquals(originalDate, viewModel.uiState.value.appliedDate)
        assertEquals(originalTime, viewModel.uiState.value.appliedTime)
    }

    private fun newViewModel(
        zoneId: ZoneId = ZoneId.of("Asia/Tokyo"),
    ): NewMedicineSlotViewModel {
        return NewMedicineSlotViewModel(
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            initialAppliedZoneId = zoneId,
        )
    }
}
