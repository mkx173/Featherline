package com.mkx.hrttracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.EntryPointAccessors
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class QuickLogActionCallbackTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun onAction_showsStockWarningToastAfterSavingWidgetQuickLog() = runTest {
        mockkStatic(EntryPointAccessors::class)
        val context: Context = mockk()
        val appContext: Context = mockk()
        val glanceId: GlanceId = mockk()
        val entryPoint: WidgetEntryPoint = mockk()
        val groupRepository: MedicationGroupRepository = mockk()
        val logRepository: MedicationLogRepository = mockk()
        val medicineStockRepository: MedicineStockRepository = mockk()
        val notificationManager: ReminderNotificationManager = mockk(relaxed = true)
        val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("1246f246-6e7d-4fac-a16c-0a557dd0c7fd"),
            scheduledTime = scheduledAt.toLocalTime(),
            medicationKey = MedicationKey.ESTRADIOL,
        )
        val medicine = group.medications.single().medicine!!
        val parameters = actionParametersOf(
            GroupUuidKey to group.uuid.toString(),
            ScheduleTimeUuidKey to group.schedule.timeSlots.single().uuid.toString(),
            ScheduledAtKey to scheduledAt.toString(),
        )
        every { context.applicationContext } returns appContext
        every {
            EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        } returns entryPoint
        every { entryPoint.medicationGroupRepository() } returns groupRepository
        every { entryPoint.medicationLogRepository() } returns logRepository
        every { entryPoint.medicineStockRepository() } returns medicineStockRepository
        every { entryPoint.reminderNotificationManager() } returns notificationManager
        every { entryPoint.diagnosticsLogger() } returns diagnosticsLogger
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(any()) } just Runs
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns listOf(
            stockProjection(medicine, MedicineStockState.USER_LOW)
        )

        QuickLogActionCallback().onAction(context, glanceId, parameters)

        coVerify { logRepository.saveNewEntries(any()) }
        verify { notificationManager.showStockUserLowToast(medicine) }
        verify(exactly = 0) { notificationManager.showDoseReminderLoggedToast(any()) }
    }

    private fun medicationGroup(
        uuid: UUID,
        scheduledTime: LocalTime,
        medicationKey: MedicationKey,
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = "Morning",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(scheduledTime),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    medicine = testMedicine(key = medicationKey),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    count = 1,
                )
            ),
            notificationsEnabled = true,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
    }

    private fun stockProjection(
        medicine: Medicine,
        state: MedicineStockState,
    ): MedicineStockProjection {
        return MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = 1.0,
            totalStockUnits = 4.0,
            runway = RunwayProjection.NoSchedule,
            intervalDays = null,
            maxPerAdministration = 1.0,
            state = state,
        )
    }
}
