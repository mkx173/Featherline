package com.mkx.hrttracker.ui.medicine

import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class ManualMedicineLogSaverTest {
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()

    @Test
    fun saveManualMedicineLog_usesAppliedDateTimeZoneAndReschedulesOnSuccess() = runTest {
        val medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000201")
        val appliedDate = LocalDate.of(2026, 5, 24)
        val appliedTime = LocalTime.of(21, 15)
        val zoneId = ZoneId.of("Asia/Tokyo")
        val appliedAt = LocalDateTime.of(appliedDate, appliedTime).atZone(zoneId).toInstant()
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicineUuid,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.VolumeMl(0.25),
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = appliedAt,
                scheduledFor = null,
                count = 2,
                appliedAtTimeZoneId = zoneId.id,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val result = saveManualMedicineLog(
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineUuid = medicineUuid,
            resolvedApplicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.25),
            count = 2,
            appliedDate = appliedDate,
            appliedTime = appliedTime,
            appliedZoneId = zoneId,
        )

        assertNull(result)
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun saveManualMedicineLog_failureReturnsFailureAndDoesNotReschedule() = runTest {
        coEvery { medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            IllegalStateException("save failed")

        val result = saveManualMedicineLog(
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000202"),
            resolvedApplicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            count = 1,
            appliedDate = LocalDate.of(2026, 5, 24),
            appliedTime = LocalTime.of(9, 0),
            appliedZoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertSame(MedicineSlotDraftSaveResult.FAILURE, result)
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun saveManualMedicineLog_patchOffSavesNoopDose() = runTest {
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000203"),
                applicationType = MedicationApplicationType.PATCH_OFF,
                doseInstruction = DoseInstruction.Noop,
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
                appliedAtTimeZoneId = "Asia/Tokyo",
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val result = saveManualMedicineLog(
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000203"),
            resolvedApplicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.VolumeMl(0.25),
            count = 1,
            appliedDate = LocalDate.of(2026, 5, 24),
            appliedTime = LocalTime.of(9, 0),
            appliedZoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertNull(result)
    }

    @Test
    fun saveManualMedicineLog_countBelowOneSavesCountOne() = runTest {
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000204"),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 2),
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
                appliedAtTimeZoneId = "Asia/Tokyo",
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val result = saveManualMedicineLog(
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000204"),
            resolvedApplicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 2),
            count = 0,
            appliedDate = LocalDate.of(2026, 5, 24),
            appliedTime = LocalTime.of(9, 0),
            appliedZoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertNull(result)
    }

    @Test
    fun saveManualMedicineLog_schedulerFailureStillReturnsSuccess() = runTest {
        coEvery { medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } throws IllegalStateException("scheduler failed")

        val result = saveManualMedicineLog(
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000205"),
            resolvedApplicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            count = 1,
            appliedDate = LocalDate.of(2026, 5, 24),
            appliedTime = LocalTime.of(9, 0),
            appliedZoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertNull(result)
    }

    @Test
    fun saveManualMedicineLog_saveCancellationIsRethrownAndDoesNotReschedule() = runTest {
        coEvery { medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            CancellationException("save cancelled")

        try {
            saveManualMedicineLog(
                medicationLogRepository = medicationLogRepository,
                medicationReminderScheduler = medicationReminderScheduler,
                medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000206"),
                resolvedApplicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                count = 1,
                appliedDate = LocalDate.of(2026, 5, 24),
                appliedTime = LocalTime.of(9, 0),
                appliedZoneId = ZoneId.of("Asia/Tokyo"),
            )
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("save cancelled", exception.message)
        }
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleAll(any()) }
    }
}
