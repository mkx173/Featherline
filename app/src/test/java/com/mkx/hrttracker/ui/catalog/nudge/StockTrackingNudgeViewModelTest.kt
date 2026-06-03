package com.mkx.hrttracker.ui.catalog.nudge

import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class StockTrackingNudgeViewModelTest {

    private val gate: StockNudgeGate = mockk()
    private val medicineRepository: MedicineRepository = mockk()
    private val stockRepository: MedicineStockRepository = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { gate.enabled } returns flowOf(true)
        every { stockRepository.getCachedProjection(any()) } returns null
        every { stockRepository.observeProjections() } returns flowOf(emptyList())
        every { medicineRepository.observeByUuid(any()) } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun actionWaitsForMatchingProjectionWhenFirstLiveEmissionDoesNotContainMedicine() = runTest {
        val medicineId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
        val medicine = testMedicine(uuid = medicineId)
        val otherProjection = testProjection(
            testMedicine(uuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001")),
        )
        val projection = testProjection(medicine)
        val projections = MutableSharedFlow<List<MedicineStockProjection>>()
        every { medicineRepository.observeByUuid(medicineId) } returns flowOf(medicine)
        every { stockRepository.observeProjections() } returns projections

        val viewModel = createViewModel()
        viewModel.onNewMedicineCreated(medicineId)
        advanceUntilIdle()

        viewModel.onNudgeActionTapped()
        runCurrent()
        assertNull(viewModel.pendingNudge.value)

        projections.emit(listOf(otherProjection))
        runCurrent()
        assertNull(viewModel.optInTarget.value)

        projections.emit(listOf(otherProjection, projection))
        advanceUntilIdle()

        assertEquals(projection, viewModel.optInTarget.value)
    }

    @Test
    fun submitOptInReceivedUsesCapturedTargetWhenSheetStateChangesBeforeCoroutineRuns() = runTest {
        val medicineId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002")
        val medicine = testMedicine(
            uuid = medicineId,
            stock = MedicineStock(unitsRemaining = 10.0),
        )
        val projection = testProjection(medicine)
        every { medicineRepository.observeByUuid(medicineId) } returns flowOf(medicine)
        every { stockRepository.getCachedProjection(medicineId) } returns projection
        coEvery {
            medicineRepository.enableTracking(any(), any(), any(), any(), any())
        } just Runs

        val viewModel = createViewModel()
        viewModel.onNewMedicineCreated(medicineId)
        advanceUntilIdle()
        viewModel.onNudgeActionTapped()
        advanceUntilIdle()

        viewModel.submitOptInReceived(medicineId = medicineId, unitsReceived = 5.0)
        viewModel.dismissOptInSheet()
        advanceUntilIdle()

        coVerify {
            medicineRepository.enableTracking(
                uuid = medicineId,
                initialUnitsRemaining = 15.0,
                initialOpenContainerAmount = null,
                initialUnitsLastTotal = 15.0,
                now = any(),
            )
        }
    }

    @Test
    fun submitOptInReceivedFailureKeepsSheetOpenAndEmitsFailureEvent() = runTest {
        val medicineId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003")
        val medicine = testMedicine(
            uuid = medicineId,
            stock = MedicineStock(unitsRemaining = 10.0),
        )
        val projection = testProjection(medicine)
        every { medicineRepository.observeByUuid(medicineId) } returns flowOf(medicine)
        every { stockRepository.getCachedProjection(medicineId) } returns projection
        coEvery {
            medicineRepository.enableTracking(any(), any(), any(), any(), any())
        } throws IllegalStateException("failed")

        val viewModel = createViewModel()
        viewModel.onNewMedicineCreated(medicineId)
        advanceUntilIdle()
        viewModel.onNudgeActionTapped()
        advanceUntilIdle()

        viewModel.submitOptInReceived(medicineId = medicineId, unitsReceived = 5.0)
        advanceUntilIdle()

        assertEquals(Unit, viewModel.optInFailureEvents.first())
        assertEquals(projection, viewModel.optInTarget.value)
    }

    @Test
    fun submitOptInReceivedSuccessClearsSheetAndEmitsAddedConfirmation() = runTest {
        val medicineId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005")
        val medicine = testMedicine(
            uuid = medicineId,
            stock = MedicineStock(unitsRemaining = 10.0),
        )
        val projection = testProjection(medicine)
        every { medicineRepository.observeByUuid(medicineId) } returns flowOf(medicine)
        every { stockRepository.getCachedProjection(medicineId) } returns projection
        coEvery {
            medicineRepository.enableTracking(any(), any(), any(), any(), any())
        } just Runs

        val viewModel = createViewModel()
        viewModel.onNewMedicineCreated(medicineId)
        advanceUntilIdle()
        viewModel.onNudgeActionTapped()
        advanceUntilIdle()

        viewModel.submitOptInReceived(medicineId = medicineId, unitsReceived = 2.0)
        advanceUntilIdle()

        // The confirmation carries the received amount and preparation so the host
        // can render "Added 2 <unit> to stock"; the sheet closes on success.
        val confirmation = viewModel.optInAddedEvents.first()
        assertEquals(2.0, confirmation.amount, 0.0)
        assertEquals(medicine.preparation, confirmation.preparation)
        assertNull(viewModel.optInTarget.value)
    }

    @Test
    fun submitOptInReceivedIgnoresDuplicateSubmitWhileMutationIsActive() = runTest {
        val medicineId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000004")
        val medicine = testMedicine(
            uuid = medicineId,
            stock = MedicineStock(unitsRemaining = 10.0),
        )
        val projection = testProjection(medicine)
        val mutationCanFinish = CompletableDeferred<Unit>()
        every { medicineRepository.observeByUuid(medicineId) } returns flowOf(medicine)
        every { stockRepository.getCachedProjection(medicineId) } returns projection
        coEvery {
            medicineRepository.enableTracking(any(), any(), any(), any(), any())
        } coAnswers {
            mutationCanFinish.await()
        }

        val viewModel = createViewModel()
        viewModel.onNewMedicineCreated(medicineId)
        advanceUntilIdle()
        viewModel.onNudgeActionTapped()
        advanceUntilIdle()

        viewModel.submitOptInReceived(medicineId = medicineId, unitsReceived = 5.0)
        runCurrent()
        viewModel.submitOptInReceived(medicineId = medicineId, unitsReceived = 5.0)
        runCurrent()

        coVerify(exactly = 1) {
            medicineRepository.enableTracking(any(), any(), any(), any(), any())
        }

        mutationCanFinish.complete(Unit)
        advanceUntilIdle()
    }

    private fun createViewModel(): StockTrackingNudgeViewModel {
        return StockTrackingNudgeViewModel(
            gate = gate,
            medicineRepository = medicineRepository,
            stockRepository = stockRepository,
        )
    }

    private fun testProjection(medicine: Medicine): MedicineStockProjection {
        return MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = 1.0,
            totalStockUnits = 10.0,
            runway = RunwayProjection.Days(
                days = 10,
                lastFulfillable = LocalDate.of(2026, 1, 11),
            ),
            intervalDays = null,
            maxPerAdministration = 1.0,
            state = MedicineStockState.HEALTHY,
        )
    }
}
