package com.mkx.hrttracker.ui.medicine

import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class NewMedicineSlotViewModelTest {
    private val medicineRepository: MedicineRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
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
    fun saveGroupSlot_validatesInputBeforeCreatingMedicine() = runTest {
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft {
            it.copy(
                applicationType = MedicationApplicationType.INJECTION,
                medicationKey = MedicationKey.ESTRADIOL_VALERATE,
                preparationType = com.mkx.hrttracker.model.medication.MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
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
    fun saveManualLog_whenLogSaveFailsKeepsMedicineCreatedAndReportsLogFailure() = runTest {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000303"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        coEvery { medicineRepository.findOrCreateForCatalog(any(), any(), any()) } returns medicine
        coEvery {
            medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("save failed")
        val viewModel = newViewModel()
        viewModel.updateMedicineDraft { it.copy(pillStrengthMg = "2") }

        viewModel.saveManualLog()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals(MedicineSlotDraftSaveResult.FAILURE, viewModel.uiState.value.manualLogSaveResult)
        coVerify(exactly = 1) { medicineRepository.findOrCreateForCatalog(any(), any(), any()) }
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
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

    private fun newViewModel(
        zoneId: ZoneId = ZoneId.of("Asia/Tokyo"),
    ): NewMedicineSlotViewModel {
        return NewMedicineSlotViewModel(
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            initialAppliedZoneId = zoneId,
        )
    }
}
