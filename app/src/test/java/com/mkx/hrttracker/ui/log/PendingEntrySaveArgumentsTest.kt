package com.mkx.hrttracker.ui.log

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.UUID

/**
 * The stock preview's pending record is the record the save writes: the real view model's save
 * arguments, for a count, a route and a dose that are not the defaults.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingEntrySaveArgumentsTest {
    private val today = LocalDate.of(2026, 1, 5)
    private val tracked = MedicineStock(trackingEnabled = true, unitsRemaining = 40.0, unitsLastTotal = 40.0, warnAtDaysRemaining = 3)
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
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun thePendingRecordCarriesTheSaveArguments() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val logRepository: MedicationLogRepository = mockk(relaxed = true)
        val groupRepository: MedicationGroupRepository = mockk(relaxed = true)
        val stockRepository: MedicineStockRepository = mockk(relaxed = true)
        val scheduler: MedicationReminderScheduler = mockk(relaxed = true)
        every { stockRepository.observeProjections() } returns flowOf(emptyList())
        every { stockRepository.getCachedProjections() } returns null
        coEvery { stockRepository.projectAllOnce(any()) } returns emptyList()
        val groupId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val slotUuid = UUID.fromString("33333333-3333-3333-3333-333333333333")
        every { groupRepository.getCachedGroup(groupId) } returns
            plan(groupId, listOf(row(MedicationApplicationType.SUBLINGUAL, DoseInstruction.TabletFraction(1, 2), 3)))
        val medicineUuid = slot<UUID>()
        val route = slot<MedicationApplicationType>()
        val dose = slot<DoseInstruction>()
        val group = slot<UUID>()
        val slotId = slot<UUID>()
        val scheduled = slot<LocalDateTime>()
        val count = slot<Int>()
        val applied = slot<Instant>()
        val zoneIdArg = slot<String>()
        coEvery {
            logRepository.saveEntry(
                uuid = null,
                medicineUuid = capture(medicineUuid),
                applicationType = capture(route),
                doseInstruction = capture(dose),
                sourceGroupUuid = capture(group),
                scheduleTimeUuid = capture(slotId),
                appliedAt = capture(applied),
                scheduledFor = capture(scheduled),
                count = capture(count),
                appliedAtTimeZoneId = capture(zoneIdArg),
                doseAmountDelta = any(),
            )
        } returns Unit
        val viewModel = MedicationLogEntryViewModel(logRepository, groupRepository, stockRepository, scheduler)
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduleTimeUuid = slotUuid,
            scheduledFor = today.atTime(20, 0),
            medicine = medicine,
            applicationType = MedicationApplicationType.SUBLINGUAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 2),
            medicationCount = 3,
        )
        advanceUntilIdle()
        val pending = viewModel.uiState.value.previewPendingEntry()
        assertNotNull(pending)
        viewModel.saveEntryAfterFulfillmentWarning()
        advanceUntilIdle()

        assertTrue(medicineUuid.isCaptured)
        assertEquals(medicineUuid.captured, pending!!.medicineUuid)
        assertEquals(route.captured, pending.applicationType)
        assertEquals(dose.captured, pending.doseInstruction)
        assertEquals(group.captured, pending.sourceGroupUuid)
        assertEquals(slotId.captured, pending.scheduleTimeUuid)
        assertEquals(scheduled.captured, pending.scheduledFor)
        assertEquals(count.captured, pending.count)
        assertEquals(applied.captured, pending.appliedAt)
        assertEquals(zoneIdArg.captured, pending.appliedAtTimeZoneId)
        assertEquals(3, pending.count)
    }

    // No pending record for an edit, or a count that does not save.
    @Test
    fun noPendingRecordForAnEditOrAnUnsavableCount() {
        val state = buildQuickLogUiState(
            groupId = UUID.randomUUID(),
            group = null,
            scheduledFor = today.atTime(20, 0),
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            medicationCount = 2,
            appliedAt = today.atTime(20, 0).atZone(ZoneOffset.UTC),
        )
        assertEquals(2, state.previewPendingEntry()?.count)
        assertNull(state.copy(editingEntryIds = listOf(UUID.randomUUID().toString())).previewPendingEntry())
        assertNull(state.copy(countText = "").previewPendingEntry())
        assertNull(state.copy(countText = "0").previewPendingEntry())
    }

    private fun row(route: MedicationApplicationType, dose: DoseInstruction, count: Int): MedicationGroupMedication =
        testMedicationGroupMedication(medicine = medicine, applicationType = route, doseInstruction = dose, count = count)

    private fun plan(uuid: UUID, medications: List<MedicationGroupMedication>) = MedicationGroup(
        uuid = uuid,
        name = "Plan",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 1, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(20, 0)),
        ),
        medications = medications,
    )
}
