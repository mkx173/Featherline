package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationGroupEditorLoadStateTest {
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val context: Context = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 25, 10, 0))
    private lateinit var settingsStateFlow: MutableStateFlow<SettingsState>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settingsStateFlow = MutableStateFlow(SettingsState(remindersEnabled = true))
        every { settingsRepository.settingsState } returns settingsStateFlow
        coEvery { settingsRepository.getCurrentSettings() } returns settingsStateFlow.value
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicationGroupRepository.getCachedGroup(any()) } returns null
        every {
            context.getString(R.string.default_group_name_format, any())
        } returns "Group 1"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun editingGroup_startsInLoadingStateUntilPersistedGroupLoads() = runTest {
        val groupUuid = UUID.fromString("3c13fbf7-95f5-4c20-8f2d-f902fd82afd2")
        val group = testMedicationGroup(groupUuid)
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )

        assertEquals(groupUuid.toString(), viewModel.uiState.value.editingGroupId)
        assertTrue(viewModel.uiState.value.isLoadingGroupForEditing)
        assertTrue(viewModel.uiState.value.medications.isEmpty())

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingGroupForEditing)
        assertEquals(group.name, viewModel.uiState.value.groupName)
        assertEquals(1, viewModel.uiState.value.medications.size)
    }

    @Test
    fun editingGroup_usesCachedGroupWithoutLoadingState() = runTest {
        val groupUuid = UUID.fromString("64df58d1-b7c7-45bf-bc6d-a7c99d4ff96f")
        val group = testMedicationGroup(groupUuid)
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )

        assertEquals(groupUuid.toString(), viewModel.uiState.value.editingGroupId)
        assertFalse(viewModel.uiState.value.isLoadingGroupForEditing)
        assertEquals(group.name, viewModel.uiState.value.groupName)
        assertEquals(1, viewModel.uiState.value.medications.size)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingGroupForEditing)
        assertEquals(group.name, viewModel.uiState.value.groupName)
    }

    @Test
    fun unarchiveGroup_confirmsThenMarksEditorSavedWithoutEditableBlink() = runTest {
        val groupUuid = UUID.fromString("8c9c40fd-4487-4a90-b75a-4f04032519fd")
        val group = testMedicationGroup(groupUuid).copy(
            archivedAt = Instant.parse("2026-04-20T00:00:00Z"),
        )
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.unarchiveGroup(groupUuid, any()) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleGroup(groupUuid, any()) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isArchived)

        viewModel.showUnarchiveConfirmation()
        assertTrue(viewModel.uiState.value.isUnarchiveConfirmationVisible)

        viewModel.unarchiveGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUnarchiving)
        assertFalse(viewModel.uiState.value.isUnarchiveConfirmationVisible)
        assertTrue(viewModel.uiState.value.isArchived)
        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) { medicationGroupRepository.unarchiveGroup(groupUuid, any()) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(groupUuid, any()) }
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun unarchiveGroup_whenNotificationsWereDisabled_doesNotReschedule() = runTest {
        val groupUuid = UUID.fromString("b5522c59-4f9d-427b-a8b9-09a21c5328e3")
        val group = testMedicationGroup(
            groupUuid = groupUuid,
            notificationsEnabled = false,
        ).copy(
            archivedAt = Instant.parse("2026-04-20T00:00:00Z"),
        )
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.unarchiveGroup(groupUuid, any()) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        viewModel.showUnarchiveConfirmation()
        viewModel.unarchiveGroup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUnarchiving)
        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) { medicationGroupRepository.unarchiveGroup(groupUuid, any()) }
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleGroup(any(), any()) }
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun newGroup_defaultsScheduleToNextHalfHour() = runTest {
        val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 25, 23, 31))
        val expectedDefaultDate = LocalDate.of(2026, 4, 26)
        val expectedDefaultTime = LocalTime.of(0, 0)
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(),
            appTimeSource = appTimeSource,
        )
        advanceUntilIdle()

        assertEquals(expectedDefaultDate, viewModel.uiState.value.sinceDate)
        assertEquals(setOf(expectedDefaultDate.dayOfWeek), viewModel.uiState.value.weeklyDaysOfWeek)
        assertEquals(expectedDefaultTime, viewModel.uiState.value.weeklyTime)
        assertEquals(
            listOf(expectedDefaultTime),
            viewModel.uiState.value.dailyTimes.map(MedicationGroupScheduleTimeUiState::time),
        )
    }
}

private fun testMedicationGroup(
    groupUuid: UUID,
    notificationsEnabled: Boolean = true,
): MedicationGroup {
    return MedicationGroup(
        uuid = groupUuid,
        name = "Group",
        colorKey = MedicationGroupColorKey.ROSE,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 4, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(9, 0)),
        ),
        medications = listOf(
            MedicationGroupMedication(
                uuid = UUID.fromString("2fd98ab6-f411-43bc-9a87-d943b42ff54b"),
                details = testMedicationDetails(),
                count = 1,
            )
        ),
        notificationsEnabled = notificationsEnabled,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-04-02T00:00:00Z"),
    )
}

private fun testMedicationDetails(): MedicationDetails {
    return MedicationDetails(
        category = MedicationKey.ESTRADIOL.category,
        applicationType = MedicationApplicationType.ORAL,
        selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL),
        dose = MedicationDose.MgAsMedicine(2.0),
    )
}
