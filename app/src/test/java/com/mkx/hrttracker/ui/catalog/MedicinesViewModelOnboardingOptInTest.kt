package com.mkx.hrttracker.ui.catalog

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.data.repository.StockReceived
import com.mkx.hrttracker.model.medication.MedicineStock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicinesViewModelOnboardingOptInTest {

    private val medicineRepository: MedicineRepository = mockk(relaxed = true)
    private val medicationGroupRepository: MedicationGroupRepository = mockk(relaxed = true)
    private val stockRepository: MedicineStockRepository = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { stockRepository.observeProjections() } returns flowOf(emptyList())
        every { stockRepository.getCachedProjections() } returns null
        every { medicineRepository.observeAllActive() } returns flowOf(emptyList())
        every { medicineRepository.getCachedActiveMedicines() } returns null
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { medicationGroupRepository.getCachedGroups() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun enableTrackingFromReceivedSumsCurrentUnitsAndReceivedUnits() = runTest {
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000030")
        val viewModel = MedicinesViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
        )

        viewModel.enableTrackingFromReceived(
            medicineUuid = uuid,
            currentUnitsRemaining = 4.0,
            received = StockReceived(unitsReceived = 30.0),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            medicineRepository.enableTracking(
                uuid,
                34.0,
                null,
                34.0,
                any(),
            )
        }
    }

    @Test
    fun enableTrackingFromReceivedEmitsSuccessResult() = runTest {
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000032")
        val viewModel = MedicinesViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
        )

        viewModel.enableTrackingFromReceived(
            medicineUuid = uuid,
            currentUnitsRemaining = 0.0,
            received = StockReceived(unitsReceived = 10.0),
        )
        advanceUntilIdle()

        assertEquals(MedicineStockMutationResult.SUCCESS, viewModel.stockOptInResult.value)
    }

    @Test
    fun enableTrackingFromReceivedEmitsFailureResultWhenRepositoryThrows() = runTest {
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000033")
        coEvery {
            medicineRepository.enableTracking(any(), any(), any(), any(), any())
        } throws IllegalStateException("enable failed")
        val viewModel = MedicinesViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
        )

        viewModel.enableTrackingFromReceived(
            medicineUuid = uuid,
            currentUnitsRemaining = 0.0,
            received = StockReceived(unitsReceived = 10.0),
        )
        advanceUntilIdle()

        assertEquals(MedicineStockMutationResult.FAILURE, viewModel.stockOptInResult.value)
    }

    @Test
    fun previewRunwayForDelegatesToStockRepository() {
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000031")
        val stock = MedicineStock(
            trackingEnabled = true,
            unitsRemaining = 12.0,
            unitsLastTotal = 12.0,
        )
        val projection = RunwayProjection.Days(
            days = 12,
            lastFulfillable = LocalDate.of(2026, 6, 9),
        )
        every { stockRepository.previewRunway(uuid, stock) } returns projection
        val viewModel = MedicinesViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
        )

        assertEquals(projection, viewModel.previewRunwayFor(uuid, stock))
    }
}
