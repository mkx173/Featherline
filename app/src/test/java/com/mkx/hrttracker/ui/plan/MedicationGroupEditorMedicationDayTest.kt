package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.withAppLanguage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationGroupEditorMedicationDayTest {
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicineRepository: MedicineRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler =
        mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private val now = LocalDateTime.of(2026, 10, 19, 1, 32)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val settings = SettingsState(remindersEnabled = true, medicationDayEndsAtFourAm = true)
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        coEvery { settingsRepository.getCurrentSettings() } returns settings
        coEvery { settingsRepository.peekNextGroupNameIndex() } returns 1
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicationGroupRepository.getCachedGroup(any()) } returns null
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { medicineRepository.observeAllActive() } returns
                MutableStateFlow<List<Medicine>>(emptyList())
        every {
            context.getString(R.string.default_group_name_format, any())
        } answers {
            val formatArgs = invocation.args[1] as Array<*>
            "Group ${formatArgs.first()}"
        }
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { context.withAppLanguage() } returns context
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("com.mkx.hrttracker.util.LocalizationKt")
    }

    @Test
    fun fourAmNewPlanDefaultsToStartMedicationDayAndItsWeekday() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalDate.of(2026, 10, 18), state.sinceDate)
        assertEquals(setOf(DayOfWeek.SUNDAY), state.weeklyDaysOfWeek)
        assertEquals(LocalTime.of(2, 0), state.weeklyTime)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 10, 19, 2, 0),
                LocalDateTime.of(2026, 10, 26, 2, 0),
            ),
            buildMedicationGroupEditorUpcomingOccurrences(
                uiState = state.copy(scheduleType = MedicationGroupScheduleType.WEEKLY),
                start = now,
            ),
        )
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 10, 19, 2, 0),
                LocalDateTime.of(2026, 10, 20, 2, 0),
            ),
            buildMedicationGroupEditorUpcomingOccurrences(
                uiState = state.copy(scheduleType = MedicationGroupScheduleType.DAILY),
                start = now,
            ),
        )
    }

    private fun newViewModel(): MedicationGroupEditorViewModel {
        return MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(),
            appTimeSource = FakeAppTimeSource(now),
        )
    }
}
