package com.mkx.hrttracker.ui.log

import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockState
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.UUID

/**
 * The log sheet's stock preview gives the runway the record it is about to save, so the preview
 * is the state the save leaves. Four tablets, warn at one day. The main plan has one dose time
 * with two rows of the same tablet, one oral and one sublingual (the plan editor allows it: its
 * duplicate key is medicine + route + dose). The sheet logs the oral tablet, so the sublingual
 * one is still owed: with three left, preview and save both say one day, low.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TwoInstructionSlotStockPreviewTest {
    private val today = LocalDate.of(2026, 1, 5)
    private val tracked = MedicineStock(
        trackingEnabled = true,
        unitsRemaining = 4.0,
        unitsLastTotal = 4.0,
        warnAtDaysRemaining = 1,
    )
    private val medicine = testMedicine(uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"), stock = tracked)
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

    // 13 hours early, outside the dose's window.
    @Test
    fun loggedEarly_thePreviewIsTheStateTheSaveLeaves() =
        check(plans = listOf(twoRowPlan(at = 20)), slotHour = 20, loggedAt = today.atTime(7, 0))

    @Test
    fun loggedOnTime_thePreviewIsTheStateTheSaveLeaves() =
        check(plans = listOf(twoRowPlan(at = 20)), slotHour = 20, loggedAt = today.atTime(20, 0))

    // 13 hours late, outside the dose's window.
    @Test
    fun loggedLate_thePreviewIsTheStateTheSaveLeaves() =
        check(plans = listOf(twoRowPlan(at = 7)), slotHour = 7, loggedAt = today.atTime(20, 0))

    // An older record with no slot id covers its slot at the exact time once saved.
    @Test
    fun anOlderRecordWithNoSlotId_thePreviewIsTheStateTheSaveLeaves() =
        check(
            plans = listOf(twoRowPlan(at = 20)),
            slotHour = 20,
            loggedAt = today.atTime(20, 0),
            withSlotId = false,
        )

    // Two plans dose the tablet at 20:00 and the sheet logs plan A's: plan B's dose is still owed.
    @Test
    fun twoPlansAtOneTime_thePreviewCoversOnlyItsOwnPlansDose() =
        check(
            plans = listOf(
                plan(PLAN_A, at = 20, listOf(row(MedicationApplicationType.ORAL))),
                plan(PLAN_B, at = 20, listOf(row(MedicationApplicationType.ORAL))),
            ),
            slotHour = 20,
            loggedAt = today.atTime(20, 0),
            withSlotId = false,
        )

    @Test
    fun noPendingEntry_forALogWithNoPlanSlot() {
        assertNull(sheet(planUuid = null, slotUuid = null, slotAt = null, loggedAt = today.atTime(20, 0)).previewPendingEntry())
    }

    private fun check(
        plans: List<MedicationGroup>,
        slotHour: Int,
        loggedAt: LocalDateTime,
        withSlotId: Boolean = true,
    ) = runTest {
        val plan = plans.first()
        val slotAt = today.atTime(slotHour, 0)
        val clock = Clock.fixed(loggedAt.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val medicineRepository: MedicineRepository = mockk(relaxed = true)
        val groupRepository: MedicationGroupRepository = mockk(relaxed = true)
        val logRepository: MedicationLogRepository = mockk(relaxed = true)
        val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true)
        val medicines = MutableStateFlow(listOf(medicine))
        every { medicineRepository.observeAllActive() } returns medicines
        every { medicineRepository.observeAllActiveOrNull() } returns medicines
        every { groupRepository.observeGroups() } returns MutableStateFlow<List<MedicationGroup>?>(plans)
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

            // The sheet, as MedicationLogEntryScreen builds it: one oral tablet on the plan's slot.
            val slotUuid = plan.schedule.timeSlots.single().uuid.takeIf { withSlotId }
            val pending = sheet(plan.uuid, slotUuid, slotAt, loggedAt).previewPendingEntry()

            // The save: that record, with the tablet deducted.
            val record = testMedicationLogEntry(
                medicine = medicine,
                applicationType = MedicationApplicationType.ORAL,
                sourceGroupUuid = plan.uuid,
                scheduleTimeUuid = slotUuid,
                appliedAt = loggedAt.toInstant(ZoneOffset.UTC),
                appliedAtTimeZoneId = "UTC",
                scheduledFor = slotAt,
            )
            // Three tablets left tell the preview from one that drops the whole slot; four, from
            // one that ignores the record (a day's two tablets are two doses).
            for (unitsLeft in listOf(3.0, 4.0)) {
                val afterStock = tracked.copy(unitsRemaining = unitsLeft)
                val saved = repository.projectAll(
                    medicines = listOf(medicine.copy(stock = afterStock)),
                    activeGroups = plans,
                    logEntries = listOf(record),
                    now = clock.instant(),
                ).single()
                if (unitsLeft == 3.0) assertEquals(MedicineStockState.USER_LOW, saved.state)
                assertEquals(
                    "$unitsLeft left",
                    saved.runway to saved.state,
                    repository.previewRunway(medicine.uuid, afterStock, pending) to
                        repository.previewState(medicine.uuid, afterStock, pending),
                )
            }
        } finally {
            scope.cancel()
        }
    }

    private fun sheet(
        planUuid: UUID?,
        slotUuid: UUID?,
        slotAt: LocalDateTime?,
        loggedAt: LocalDateTime,
    ) = MedicationLogEntryUiState(
        resolvedMedicine = medicine,
        doseInstructionDraft = doseInstructionDraftFromInstruction(
            applicationType = MedicationApplicationType.ORAL,
            preparationType = MedicinePreparationType.PILL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
        ),
        sourceGroupUuid = planUuid,
        scheduleTimeUuid = slotUuid,
        scheduledFor = slotAt,
        countText = "1",
        appliedDate = loggedAt.toLocalDate(),
        appliedTime = loggedAt.toLocalTime(),
        appliedZoneId = ZoneOffset.UTC,
        appliedOffset = ZoneOffset.UTC,
    )

    private fun twoRowPlan(at: Int) = plan(
        PLAN_A,
        at,
        listOf(row(MedicationApplicationType.ORAL), row(MedicationApplicationType.SUBLINGUAL)),
    )

    private fun row(applicationType: MedicationApplicationType) =
        testMedicationGroupMedication(medicine = medicine, applicationType = applicationType)

    private fun plan(uuid: UUID, at: Int, medications: List<MedicationGroupMedication>) = MedicationGroup(
        uuid = uuid,
        name = "Plan",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 1, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(at, 0)),
        ),
        medications = medications,
    )

    private companion object {
        val PLAN_A: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val PLAN_B: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
    }
}
