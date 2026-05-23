package com.mkx.hrttracker.ui.medicine

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testDoseInstruction
import com.mkx.hrttracker.model.medication.testMedicine
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicinesViewModelTest {

    private val medicineRepository: MedicineRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Why this matters: the medicines list partitions by category so callers
    // browse meaningfully — losing the partition (or breaking deterministic
    // ordering across category buckets) silently regresses navigation. The
    // assertion pins both the partition AND the enum-declaration order.
    @Test
    fun listGroupsActiveMedicinesByCategoryAndKeepsArchivedCollapsed() = runTest {
        val estradiol = testMedicine(
            uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"),
            key = MedicationKey.ESTRADIOL,
        )
        val blocker = testMedicine(
            uuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000"),
            key = MedicationKey.SPIRONOLACTONE,
        )
        val archived = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000000"),
            key = MedicationKey.ESTRADIOL_VALERATE,
            archivedAt = Instant.parse("2026-05-21T00:00:00Z"),
        )
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(estradiol, blocker))
        every { medicineRepository.observeAllArchived() } returns flowOf(listOf(archived))
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())

        val viewModel = MedicinesViewModel(medicineRepository, medicationGroupRepository)
        val collectJob = startUiStateCollection(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            listOf(MedicationCategory.ESTRADIOL, MedicationCategory.ANTIANDROGEN),
            state.activeSections.map { it.category },
        )
        assertEquals(
            listOf(estradiol.uuid),
            state.activeSections
                .first { it.category == MedicationCategory.ESTRADIOL }
                .medicines.map { it.medicine.uuid },
        )
        assertEquals(
            listOf(blocker.uuid),
            state.activeSections
                .first { it.category == MedicationCategory.ANTIANDROGEN }
                .medicines.map { it.medicine.uuid },
        )
        assertEquals(listOf(archived.uuid), state.archivedMedicines.map { it.uuid })
        assertFalse(state.archivedExpanded)
        collectJob.cancel()
    }

    // Why this matters: the active-group reference count is the input to
    // `MedicineDetailViewModel`'s archive guard; computing it from a join in
    // the list view-model lets the list show a chip BEFORE the detail screen
    // opens. Asserting the count surfaces a regression in the join keyed on
    // medicineUuid.
    @Test
    fun activeGroupReferenceCountReflectsLiveGroups() = runTest {
        val medicine = testMedicine(uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"))
        val groupReferencing = testGroupReferencingMedicine(
            groupUuid = UUID.fromString("11111111-0000-0000-0000-000000000000"),
            medicine = medicine,
        )
        // A second group references the same medicine through two slots — the
        // count is over groups, not slots.
        val groupReferencingTwice = testGroupReferencingMedicine(
            groupUuid = UUID.fromString("22222222-0000-0000-0000-000000000000"),
            medicine = medicine,
            slotMultiplicity = 2,
        )
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(
            listOf(groupReferencing, groupReferencingTwice),
        )

        val viewModel = MedicinesViewModel(medicineRepository, medicationGroupRepository)
        val collectJob = startUiStateCollection(viewModel)
        advanceUntilIdle()

        val items = viewModel.uiState.value.activeSections.flatMap { it.medicines }
        assertEquals(1, items.size)
        assertEquals(2, items.first().activeGroupReferenceCount)
        collectJob.cancel()
    }

    // Why this matters: archived groups should not contribute to the
    // reference count — they are "linked active groups" by definition for the
    // archive guard.
    @Test
    fun archivedGroupsDoNotContributeToReferenceCount() = runTest {
        val medicine = testMedicine(uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"))
        val archivedGroup = testGroupReferencingMedicine(
            groupUuid = UUID.fromString("33333333-0000-0000-0000-000000000000"),
            medicine = medicine,
            archivedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
        every { medicineRepository.observeAllActive() } returns flowOf(listOf(medicine))
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(archivedGroup))

        val viewModel = MedicinesViewModel(medicineRepository, medicationGroupRepository)
        val collectJob = startUiStateCollection(viewModel)
        advanceUntilIdle()

        val items = viewModel.uiState.value.activeSections.flatMap { it.medicines }
        assertEquals(0, items.first().activeGroupReferenceCount)
        collectJob.cancel()
    }

    // Why this matters: the archived list is collapsed by default; the
    // expand toggle must flip in the state so the UI can render the rows.
    @Test
    fun toggleArchivedExpandedFlipsState() = runTest {
        every { medicineRepository.observeAllActive() } returns flowOf(emptyList())
        every { medicineRepository.observeAllArchived() } returns flowOf(emptyList())
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())

        val viewModel = MedicinesViewModel(medicineRepository, medicationGroupRepository)
        val collectJob = startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.archivedExpanded)
        viewModel.toggleArchivedExpanded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.archivedExpanded)
        viewModel.toggleArchivedExpanded()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.archivedExpanded)
        collectJob.cancel()
    }

    private fun TestScope.startUiStateCollection(viewModel: MedicinesViewModel): Job {
        return backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }
}

internal fun testGroupReferencingMedicine(
    groupUuid: UUID = UUID.randomUUID(),
    medicine: com.mkx.hrttracker.model.medication.Medicine = testMedicine(),
    slotMultiplicity: Int = 1,
    archivedAt: Instant? = null,
): MedicationGroup {
    val slots = (1..slotMultiplicity).map { slotIndex ->
        MedicationGroupMedication(
            uuid = UUID.fromString("00000000-0000-0000-0000-00000000000$slotIndex"),
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = testDoseInstruction(),
        )
    }
    return MedicationGroup(
        uuid = groupUuid,
        name = "Group",
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 1, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(8, 0)),
        ),
        medications = slots,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = archivedAt,
    )
}

