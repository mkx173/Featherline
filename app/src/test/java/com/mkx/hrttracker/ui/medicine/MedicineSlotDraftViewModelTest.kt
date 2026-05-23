package com.mkx.hrttracker.ui.medicine

import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
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
}
