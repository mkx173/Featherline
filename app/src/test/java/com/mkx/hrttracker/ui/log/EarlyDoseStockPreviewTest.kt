package com.mkx.hrttracker.ui.log

import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.nextScheduledForAfter
import com.mkx.hrttracker.model.medication.previousScheduledForBefore
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.ui.medication.doseInstructionDraftFromInstruction
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.UUID

/**
 * Four tablets, one a day at 20:00, warn at two days. At 07:00 the sheet logs today's 20:00 dose,
 * 13 hours early, outside its 12-hour window. The saved record still covers the slot (the
 * committed runway counts any attached record), so the sheet's stock preview must show the state
 * the save leaves: three days, healthy, not "low at two days". The preview is computed as
 * MedicationLogEntryScreen does, with the real MedicineStockRepository, runway calculator and
 * state resolver.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EarlyDoseStockPreviewTest {
    private val clock = Clock.fixed(Instant.parse("2026-01-05T07:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 1, 5)
    private val slotAt = today.atTime(20, 0)
    private val loggedAt = today.atTime(7, 0)
    private val tracked = MedicineStock(
        trackingEnabled = true,
        unitsRemaining = 4.0,
        unitsLastTotal = 4.0,
        warnAtDaysRemaining = 2,
    )
    private val medicine = testMedicine(uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"), stock = tracked)
    private val plan = MedicationGroup(
        uuid = UUID.fromString("22222222-2222-2222-2222-222222222222"),
        name = "Evening",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 1, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(20, 0)),
        ),
        medications = listOf(testMedicationGroupMedication(medicine = medicine)),
    )
    private lateinit var deviceZone: TimeZone

    @Before
    fun setUp() {
        deviceZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(deviceZone)
    }

    @Test
    fun theSheetsPreviewState_forADoseLoggedOutsideItsWindow_isTheStateTheSaveLeaves() = runTest {
        val medicineRepository: MedicineRepository = mockk(relaxed = true)
        val groupRepository: MedicationGroupRepository = mockk(relaxed = true)
        val logRepository: MedicationLogRepository = mockk(relaxed = true)
        val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true)
        val medicines = MutableStateFlow(listOf(medicine))
        every { medicineRepository.observeAllActive() } returns medicines
        every { medicineRepository.observeAllActiveOrNull() } returns medicines
        every { groupRepository.observeGroups() } returns MutableStateFlow<List<MedicationGroup>?>(listOf(plan))
        every { logRepository.observeEntries() } returns MutableStateFlow(emptyList())
        every { homeSnapshotRepository.currentCache } returns MutableStateFlow(null)
        every { homeSnapshotRepository.cache } returns MutableStateFlow(null)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = MedicineStockRepository(
                medicineRepository = medicineRepository,
                medicationGroupRepository = groupRepository,
                medicationLogRepository = logRepository,
                homeSnapshotRepository = homeSnapshotRepository,
                appScope = scope,
                clock = clock,
            )
            advanceUntilIdle()

            // Before the log: 4 tablets cover Jan 5-8, three days of runway, healthy.
            val before = repository.projectAll(listOf(medicine), listOf(plan), emptyList(), clock.instant()).single()
            assertEquals(RunwayProjection.Days(days = 3, lastFulfillable = today.plusDays(3)), before.runway)
            assertEquals(MedicineStockState.HEALTHY, before.state)

            // The sheet: 13 h early is outside the window, and the record still goes to the preview.
            val slotUuid = plan.schedule.timeSlots.single().uuid
            val outsideWindow = shouldWarnScheduleWillNotBeFulfilled(
                sourceGroupUuid = plan.uuid,
                scheduledFor = slotAt,
                sourceGroupPreviousScheduledFor = plan.previousScheduledForBefore(slotAt),
                sourceGroupNextScheduledFor = plan.nextScheduledForAfter(slotAt),
                appliedAt = loggedAt.atZone(ZoneOffset.UTC),
            )
            assertTrue(outsideWindow)
            val pending = MedicationLogEntryUiState(
                resolvedMedicine = medicine,
                doseInstructionDraft = doseInstructionDraftFromInstruction(
                    applicationType = MedicationApplicationType.ORAL,
                    preparationType = MedicinePreparationType.PILL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                ),
                sourceGroupUuid = plan.uuid,
                scheduleTimeUuid = slotUuid,
                scheduledFor = slotAt,
                appliedDate = today,
                appliedTime = loggedAt.toLocalTime(),
                appliedZoneId = ZoneOffset.UTC,
                appliedOffset = ZoneOffset.UTC,
            ).previewPendingEntry()
            assertNotNull(pending)
            val afterStock = tracked.copy(unitsRemaining = 3.0)
            val preview = repository.previewState(medicine.uuid, afterStock, pending)

            // The save: one tablet deducted, the record attached to the 20:00 slot.
            val record = testMedicationLogEntry(
                medicine = medicine,
                sourceGroupUuid = plan.uuid,
                scheduleTimeUuid = slotUuid,
                appliedAt = loggedAt.toInstant(ZoneOffset.UTC),
                appliedAtTimeZoneId = "UTC",
                scheduledFor = slotAt,
            )
            val saved = repository.projectAll(
                medicines = listOf(medicine.copy(stock = afterStock)),
                activeGroups = listOf(plan),
                logEntries = listOf(record),
                now = clock.instant(),
            ).single()
            assertEquals(RunwayProjection.Days(days = 3, lastFulfillable = today.plusDays(3)), saved.runway)
            assertEquals(MedicineStockState.HEALTHY, saved.state)

            val previewRunway = repository.previewRunway(medicine.uuid, afterStock, pending)

            assertEquals(saved.runway to saved.state, previewRunway to preview)
        } finally {
            scope.cancel()
        }
    }
}
