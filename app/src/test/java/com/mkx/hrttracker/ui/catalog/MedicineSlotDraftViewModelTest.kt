package com.mkx.hrttracker.ui.catalog

import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicineSlotDraftViewModelTest {
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
    fun saveManualLog_usesExplicitAppliedDateTimeAndResolvedSlotFields() = runTest {
        val medicineUuid = UUID.fromString("7f3c6db0-6589-48fb-929d-84d7e0c3f032")
        val appliedDate = LocalDate.of(2026, 5, 18)
        val appliedTime = LocalTime.of(22, 45)
        val zoneId = ZoneId.of("Asia/Tokyo")
        val expectedAppliedAt = LocalDateTime.of(appliedDate, appliedTime)
            .atZone(zoneId)
            .toInstant()
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicineUuid,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.VolumeMl(0.25),
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = expectedAppliedAt,
                scheduledFor = null,
                count = 2,
                appliedAtTimeZoneId = zoneId.id,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicineSlotDraftViewModel(
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            initialAppliedZoneId = zoneId,
        )

        viewModel.updateAppliedDate(appliedDate)
        viewModel.updateAppliedTime(appliedTime)
        viewModel.saveManualLog(
            medicineUuid = medicineUuid,
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.25),
            count = 2,
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicineUuid,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.VolumeMl(0.25),
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = expectedAppliedAt,
                scheduledFor = null,
                count = 2,
                appliedAtTimeZoneId = zoneId.id,
            )
        }
        coVerify(exactly = 1) {
            medicationReminderScheduler.rescheduleAll(any())
        }
    }

    @Test
    fun saveManualLog_passesDoseAmountDelta() = runTest {
        val medicineUuid = UUID.fromString("7f3c6db0-6589-48fb-929d-84d7e0c3f035")
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicineUuid,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.VolumeMl(0.25),
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
                appliedAtTimeZoneId = "Asia/Tokyo",
                doseAmountDelta = 0.1,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit
        val viewModel = MedicineSlotDraftViewModel(
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            initialAppliedZoneId = ZoneId.of("Asia/Tokyo"),
        )

        viewModel.saveManualLog(
            medicineUuid = medicineUuid,
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.25),
            count = 1,
            doseAmountDelta = 0.1,
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicineUuid,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.VolumeMl(0.25),
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
                appliedAtTimeZoneId = "Asia/Tokyo",
                doseAmountDelta = 0.1,
            )
        }
    }

    @Test
    fun updateAppliedDateTime_afterSuccessfulSaveBeforeConsumptionAreIgnored() = runTest {
        val medicineUuid = UUID.fromString("7f3c6db0-6589-48fb-929d-84d7e0c3f033")
        val appliedDate = LocalDate.of(2026, 5, 18)
        val appliedTime = LocalTime.of(22, 45)
        val zoneId = ZoneId.of("Asia/Tokyo")
        coEvery { medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit
        val viewModel = MedicineSlotDraftViewModel(
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            initialAppliedZoneId = zoneId,
        )
        viewModel.updateAppliedDate(appliedDate)
        viewModel.updateAppliedTime(appliedTime)

        viewModel.saveManualLog(
            medicineUuid = medicineUuid,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            count = 1,
        )
        advanceUntilIdle()
        viewModel.updateAppliedDate(LocalDate.of(2026, 5, 19))
        viewModel.updateAppliedTime(LocalTime.of(8, 15))

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(appliedDate, viewModel.uiState.value.appliedDate)
        assertEquals(appliedTime, viewModel.uiState.value.appliedTime)
    }

    @Test
    fun saveManualLog_whenCancelledClearsSavingState() = runTest {
        val medicineUuid = UUID.fromString("7f3c6db0-6589-48fb-929d-84d7e0c3f034")
        coEvery {
            medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws CancellationException("cancelled")
        val viewModel = MedicineSlotDraftViewModel(
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            initialAppliedZoneId = ZoneId.of("Asia/Tokyo"),
        )

        viewModel.saveManualLog(
            medicineUuid = medicineUuid,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            count = 1,
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isSaved)
    }
}
