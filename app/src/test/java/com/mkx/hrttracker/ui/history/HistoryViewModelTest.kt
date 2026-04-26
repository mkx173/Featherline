package com.mkx.hrttracker.ui.history

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun deleteAllEntries_updatesUiStateWithSuccessResult() = runTest {
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0),
                    ),
                    sourceGroupUuid = null,
                    appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
                )
            )
        )
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        coEvery { medicationLogRepository.deleteAllEntries() } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = HistoryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
        )
        advanceUntilIdle()

        viewModel.deleteAllEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeletingAllEntries)
        assertEquals(
            HistoryDeleteAllEntriesResult.SUCCESS,
            viewModel.uiState.value.deleteAllEntriesResult,
        )

        viewModel.consumeDeleteAllEntriesResult()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deleteAllEntriesResult)
        coVerify(exactly = 1) { medicationLogRepository.deleteAllEntries() }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }
}
