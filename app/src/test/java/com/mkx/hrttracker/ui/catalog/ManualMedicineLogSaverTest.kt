package com.mkx.hrttracker.ui.catalog

import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.PostLogStockWarning
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
    private val medicineStockRepository: MedicineStockRepository = mockk()
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
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns emptyList()
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val result = saveManualMedicineLog(
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineUuid = medicineUuid,
            resolvedApplicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.25),
            count = 2,
            appliedDate = appliedDate,
            appliedTime = appliedTime,
            appliedZoneId = zoneId,
        )

        assertNull(result.saveResult)
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun saveManualMedicineLog_passesDoseAmountDeltaAndReturnsPostLogWarning() = runTest {
        val medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000207")
        val medicine = testMedicine(
            uuid = medicineUuid,
            key = MedicationKey.ESTRADIOL_VALERATE,
        )
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicineUuid,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.VolumeMl(0.5),
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
                appliedAtTimeZoneId = "Asia/Tokyo",
                doseAmountDelta = 0.1,
            )
        } returns Unit
        // Before the log the medicine was healthy (empty projection list); only
        // the post-log drop to IMMINENT should produce a warning.
        coEvery { medicineStockRepository.projectAllOnce(any()) } returnsMany listOf(
            emptyList(),
            listOf(
                stockProjection(
                    medicine = medicine,
                    state = MedicineStockState.IMMINENT,
                )
            ),
        )
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val result = saveManualMedicineLog(
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineUuid = medicineUuid,
            resolvedApplicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.5),
            count = 1,
            doseAmountDelta = 0.1,
            appliedDate = LocalDate.of(2026, 5, 24),
            appliedTime = LocalTime.of(9, 0),
            appliedZoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertNull(result.saveResult)
        assertEquals(
            PostLogStockWarning.Single(medicine, MedicineStockState.IMMINENT),
            result.postLogStockWarning,
        )
    }

    @Test
    fun saveManualMedicineLog_failureReturnsFailureAndDoesNotReschedule() = runTest {
        coEvery { medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            IllegalStateException("save failed")

        val result = saveManualMedicineLog(
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000202"),
            resolvedApplicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            count = 1,
            appliedDate = LocalDate.of(2026, 5, 24),
            appliedTime = LocalTime.of(9, 0),
            appliedZoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertSame(MedicineSlotDraftSaveResult.FAILURE, result.saveResult)
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
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns emptyList()
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val result = saveManualMedicineLog(
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000203"),
            resolvedApplicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.VolumeMl(0.25),
            count = 1,
            appliedDate = LocalDate.of(2026, 5, 24),
            appliedTime = LocalTime.of(9, 0),
            appliedZoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertNull(result.saveResult)
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
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns emptyList()
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val result = saveManualMedicineLog(
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000204"),
            resolvedApplicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 2),
            count = 0,
            appliedDate = LocalDate.of(2026, 5, 24),
            appliedTime = LocalTime.of(9, 0),
            appliedZoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertNull(result.saveResult)
    }

    @Test
    fun saveManualMedicineLog_schedulerFailureStillReturnsSuccess() = runTest {
        coEvery { medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns emptyList()
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } throws IllegalStateException("scheduler failed")

        val result = saveManualMedicineLog(
            medicationLogRepository = medicationLogRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000205"),
            resolvedApplicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            count = 1,
            appliedDate = LocalDate.of(2026, 5, 24),
            appliedTime = LocalTime.of(9, 0),
            appliedZoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertNull(result.saveResult)
    }

    @Test
    fun saveManualMedicineLog_saveCancellationIsRethrownAndDoesNotReschedule() = runTest {
        coEvery { medicationLogRepository.saveEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            CancellationException("save cancelled")

        try {
            saveManualMedicineLog(
                medicationLogRepository = medicationLogRepository,
                medicineStockRepository = medicineStockRepository,
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

private fun stockProjection(
    medicine: Medicine,
    state: MedicineStockState,
): MedicineStockProjection {
    return MedicineStockProjection(
        medicine = medicine,
        dosesPerDayMagnitude = 1.0,
        totalStockUnits = medicine.stock.unitsRemaining ?: 1.0,
        runway = RunwayProjection.NoSchedule,
        intervalDays = null,
        maxPerAdministration = 1.0,
        state = state,
    )
}
