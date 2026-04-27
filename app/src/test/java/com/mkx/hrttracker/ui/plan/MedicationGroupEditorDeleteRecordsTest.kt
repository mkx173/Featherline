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
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationGroupEditorDeleteRecordsTest {
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val context: Context = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var settingsStateFlow: MutableStateFlow<SettingsState>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settingsStateFlow = MutableStateFlow(SettingsState(remindersEnabled = true))
        every { settingsRepository.settingsState } returns settingsStateFlow
        coEvery { settingsRepository.getCurrentSettings() } returns settingsStateFlow.value
        every {
            context.getString(R.string.default_group_name_format, any())
        } returns "Group 1"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun deleteRelatedEntries_updatesUiStateWithSuccessResult() = runTest {
        val groupUuid = UUID.fromString("1fd2a0a4-6ddd-48e0-bc6d-d5824d8832f8")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    details = testMedicationDetails(),
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
                ),
                testMedicationLogEntry(
                    details = testMedicationDetails(),
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-25T00:00:00Z"),
                ),
                testMedicationLogEntry(
                    details = testMedicationDetails(),
                    sourceGroupUuid = null,
                    appliedAt = Instant.parse("2026-04-24T00:00:00Z"),
                ),
            )
        )
        coEvery { medicationLogRepository.deleteEntriesForGroup(groupUuid) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.relatedEntryCount)

        viewModel.showDeleteRelatedEntriesConfirmation()
        assertEquals(true, viewModel.uiState.value.isDeleteRelatedEntriesConfirmationVisible)

        viewModel.deleteRelatedEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeletingRelatedEntries)
        assertEquals(
            DeleteRelatedEntriesResult.SUCCESS,
            viewModel.uiState.value.deleteRelatedEntriesResult,
        )
        assertEquals(false, viewModel.uiState.value.isDeleteRelatedEntriesConfirmationVisible)

        viewModel.consumeDeleteRelatedEntriesResult()
        assertNull(viewModel.uiState.value.deleteRelatedEntriesResult)
        coVerify(exactly = 1) { medicationLogRepository.deleteEntriesForGroup(groupUuid) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun deleteGroupAndRelatedEntries_deletesGroupRecordsAndMarksEditorDeleted() = runTest {
        val groupUuid = UUID.fromString("9bd82f30-b6eb-495e-b894-48a1779fd5d7")
        val group = testMedicationGroup(groupUuid)

        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        coEvery { medicationGroupRepository.getGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns flowOf(
            listOf(
                testMedicationLogEntry(
                    details = testMedicationDetails(),
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-04-26T00:00:00Z"),
                )
            )
        )
        coEvery { medicationGroupRepository.deleteGroupAndRelatedEntries(groupUuid) } returns Unit
        every { medicationReminderScheduler.cancelReminder(groupUuid) } returns Unit

        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
        )
        advanceUntilIdle()

        viewModel.showDeleteConfirmation()
        assertEquals(true, viewModel.uiState.value.isDeleteConfirmationVisible)

        viewModel.deleteGroupAndRelatedEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleting)
        assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
        assertEquals(true, viewModel.uiState.value.isDeleted)

        coVerify(exactly = 1) { medicationGroupRepository.deleteGroupAndRelatedEntries(groupUuid) }
        verify(exactly = 1) { medicationReminderScheduler.cancelReminder(groupUuid) }
    }
}

private fun testMedicationGroup(groupUuid: UUID): MedicationGroup {
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
        notificationsEnabled = true,
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
