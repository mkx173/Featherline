package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()
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
    fun clockTickAcrossMidnight_updatesTodaySectionDate() = runTest {
        val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 30, 23, 59))
        every { medicationGroupRepository.observeGroups() } returns flowOf(
            listOf(medicationGroup())
        )
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { userProfileRepository.observeProfile() } returns flowOf(UserProfile(weightKg = 60.0))

        val viewModel = MainViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            userProfileRepository = userProfileRepository,
            appTimeSource = appTimeSource,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 4, 30), viewModel.uiState.value.todaySection.date)

        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 5, 1, 0, 0))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 5, 1), viewModel.uiState.value.todaySection.date)
    }

    private fun TestScope.startUiStateCollection(viewModel: MainViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    private fun medicationGroup(): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString("0961b1af-d7db-43f7-b66a-8b82b9faefaf"),
            name = "Estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("bf3e810c-0ade-4db7-8ca6-ef85b93b2c3f"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0),
                    )
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
    }
}
