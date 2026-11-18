package com.mkx.hrttracker.ui.log

import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.scheduleOffsetMinutes
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.ui.medication.MedicationLogScheduleOffset
import com.mkx.hrttracker.ui.medication.medicationLogScheduleOffset
import com.mkx.hrttracker.ui.medication.sourceGroupScheduleOffset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.UUID

/**
 * The log editor in a repeated fall-back hour keeps the pass it was opened on. New York,
 * 2026-11-01: 01:00-02:00 happens twice. A 00:30 dose logged at 01:40 EST, the second pass, is
 * 06:40Z: 130 real minutes late.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationLogEntryFallBackHourTest {
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicineStockRepository: MedicineStockRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val dispatcher = StandardTestDispatcher()

    private val newYork = ZoneId.of("America/New_York")
    private val medicine = testMedicine(
        uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000001"),
        key = MedicationKey.ESTRADIOL,
    )
    private val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
    private val slot = LocalDateTime.of(2026, 11, 1, 0, 30)
    private val secondPass0140: Instant = Instant.parse("2026-11-01T06:40:00Z")
    private val firstPass0140: Instant = Instant.parse("2026-11-01T05:40:00Z")
    private val savedAppliedAt = slot<Instant>()
    private lateinit var deviceZone: TimeZone

    @Before
    fun setUp() {
        deviceZone = TimeZone.getDefault()
        Dispatchers.setMain(dispatcher)
        every { medicineStockRepository.observeProjections() } returns flowOf(emptyList())
        every { medicineStockRepository.getCachedProjections() } returns null
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns emptyList()
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = any(),
                medicineUuid = any(),
                applicationType = any(),
                doseInstruction = any(),
                sourceGroupUuid = any(),
                scheduleTimeUuid = any(),
                appliedAt = capture(savedAppliedAt),
                scheduledFor = any(),
                count = any(),
                appliedAtTimeZoneId = any(),
                doseAmountDelta = any(),
            )
        } returns Unit
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(deviceZone)
        Dispatchers.resetMain()
    }

    private fun viewModel(clock: Clock = Clock.systemUTC()) = MedicationLogEntryViewModel(
        medicationLogRepository = medicationLogRepository,
        medicationGroupRepository = medicationGroupRepository,
        medicineStockRepository = medicineStockRepository,
        medicationReminderScheduler = medicationReminderScheduler,
        clock = clock,
    )

    private fun secondPassRecord(entryId: UUID, appliedAt: Instant = secondPass0140) = testMedicationLogEntry(
        uuid = entryId,
        medicine = medicine,
        applicationType = MedicationApplicationType.ORAL,
        sourceGroupUuid = null,
        appliedAt = appliedAt,
        appliedAtTimeZoneId = newYork.id,
    )

    private fun TestScope.editorFor(record: MedicationLogEntry): MedicationLogEntryViewModel {
        coEvery { medicationLogRepository.getEntries(listOf(record.uuid)) } returns listOf(record)
        val viewModel = viewModel()
        viewModel.initialize(listOf(record.uuid.toString()))
        advanceUntilIdle()
        return viewModel
    }

    // The offset the sheet shows, from its state, as MedicationLogEntryEditorSheet computes it.
    private fun MedicationLogEntryUiState.sheetOffset(slot: LocalDateTime) = sourceGroupScheduleOffset(
        slot,
        LocalDateTime.of(appliedDate, appliedTime),
        appliedZoneId,
        appliedOffset,
    )

    // What the editor has to work around: java.time resolves a repeated wall time to its first pass.
    @Test
    fun control_atZoneResolvesARepeatedWallTimeToItsFirstPass() {
        assertEquals(firstPass0140, LocalDateTime.of(2026, 11, 1, 1, 40).atZone(newYork).toInstant())
        assertEquals(LocalDateTime.of(2026, 11, 1, 1, 40), secondPass0140.atZone(newYork).toLocalDateTime())
    }

    @Test
    fun editingASecondPassRecord_andSavingWithoutTouchingTheTime_keepsItsInstant() = runTest(dispatcher) {
        val viewModel = editorFor(secondPassRecord(UUID.randomUUID()))
        assertEquals(LocalTime.of(1, 40), viewModel.uiState.value.appliedTime)
        assertEquals(newYork, viewModel.uiState.value.appliedZoneId)

        viewModel.saveEntry()
        advanceUntilIdle()

        // Not 05:40Z, an hour earlier.
        assertEquals(secondPass0140, savedAppliedAt.captured)
    }

    @Test
    fun theEditSheetsOffset_forASecondPassRecord_isThePlanRowsOffset() = runTest(dispatcher) {
        val state = editorFor(secondPassRecord(UUID.randomUUID())).uiState.value

        // What Home and the Plan row show for the stored record: 130 real minutes, "2 h later".
        val stored = medicationLogScheduleOffset(scheduleOffsetMinutes(slot, secondPass0140, newYork))
        assertEquals(
            MedicationLogScheduleOffset(R.string.medication_editor_schedule_offset_hours_later, 2),
            stored,
        )
        // Not "1 h later".
        assertEquals(stored, state.sheetOffset(slot))
    }

    @Test
    fun aQuickLogOpenedOnTheSecondPass_savesNow() = runTest(dispatcher) {
        TimeZone.setDefault(TimeZone.getTimeZone(newYork))
        every { medicationGroupRepository.getCachedGroup(groupId) } returns plan()
        val viewModel = viewModel(clock = Clock.fixed(secondPass0140, ZoneOffset.UTC))
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = slot,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            medicationCount = 1,
        )
        advanceUntilIdle()
        assertEquals(LocalTime.of(1, 40), viewModel.uiState.value.appliedTime)
        assertEquals(newYork, viewModel.uiState.value.appliedZoneId)

        viewModel.saveEntry()
        advanceUntilIdle()

        // Not 05:40Z, an hour before the tap.
        assertEquals(secondPass0140, savedAppliedAt.captured)
    }

    // A slot inside the repeated hour: a 01:30 slot resolves to 05:30Z, so a dose at the second
    // 01:30 (06:30Z) is "1 h later" on Plan, not on time.
    @Test
    fun aSlotInsideTheRepeatedHour_loggedOnItsSecondPass_showsThePlanRowsOffset() = runTest(dispatcher) {
        val slot0130 = LocalDateTime.of(2026, 11, 1, 1, 30)
        val secondPass0130 = Instant.parse("2026-11-01T06:30:00Z")
        val state = editorFor(secondPassRecord(UUID.randomUUID(), appliedAt = secondPass0130)).uiState.value

        val stored = medicationLogScheduleOffset(scheduleOffsetMinutes(slot0130, secondPass0130, newYork))
        assertEquals(MedicationLogScheduleOffset(R.string.medication_editor_schedule_offset_hours_later, 1), stored)
        assertEquals(stored, state.sheetOffset(slot0130))
    }

    @Test
    fun aNewLogOpenedOnTheSecondPass_meansThatPass() = runTest(dispatcher) {
        TimeZone.setDefault(TimeZone.getTimeZone(newYork))
        val viewModel = viewModel(clock = Clock.fixed(secondPass0140, ZoneOffset.UTC))
        viewModel.initialize(emptyList())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalTime.of(1, 40), state.appliedTime)
        assertEquals(secondPass0140, state.appliedAt().toInstant())
    }

    // 00:30 and 02:30 doses are 3 real hours apart, so 90 minutes each side: the first 01:40
    // is 70 minutes late, the second 130.
    @Test
    fun theFulfillmentWarning_readsThePassTheDraftIsOn() {
        fun draftAt(offset: ZoneOffset) = MedicationLogEntryUiState(
            sourceGroupUuid = groupId,
            scheduledFor = slot,
            sourceGroupNextScheduledFor = LocalDateTime.of(2026, 11, 1, 2, 30),
            appliedDate = LocalDate.of(2026, 11, 1),
            appliedTime = LocalTime.of(1, 40),
            appliedZoneId = newYork,
            appliedOffset = offset,
        )

        assertFalse(draftAt(ZoneOffset.ofHours(-4)).shouldWarnScheduleWillNotBeFulfilled())
        assertTrue(draftAt(ZoneOffset.ofHours(-5)).shouldWarnScheduleWillNotBeFulfilled())
    }

    // A time drafted on another day has one pass: the record's offset doesn't move it.
    @Test
    fun aTimeMovedToAnotherDay_resolvesInThatDaysOffset() = runTest(dispatcher) {
        val viewModel = editorFor(secondPassRecord(UUID.randomUUID()))
        viewModel.updateAppliedDate(LocalDate.of(2026, 10, 31))

        viewModel.saveEntry()
        advanceUntilIdle()

        assertEquals(Instant.parse("2026-10-31T05:40:00Z"), savedAppliedAt.captured)
    }

    private fun plan() = MedicationGroup(
        uuid = groupId,
        name = "Nightly",
        colorKey = MedicationGroupColorKey.INDIGO,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 10, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(0, 30)),
        ),
        medications = emptyList(),
        createdAt = Instant.parse("2026-10-01T12:00:00Z"),
        updatedAt = Instant.parse("2026-10-01T12:00:00Z"),
    )
}
