package com.mkx.hrttracker.ui.navigation

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.CalibrationEstimate
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.E2State
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.ObservedEstradiolEntryLookup
import com.mkx.hrttracker.data.repository.PkCalibrationStorageRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.calibration.CalibrationEditorViewModel
import com.mkx.hrttracker.ui.components.UnsavedChangesState
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * A calibration draft under milestones, which a widget opened over the editor, then process
 * death: the back stack comes back with only milestones composed, and the editor's ViewModel isn't
 * created to report the draft it saved. A pop to the pager must still ask before losing it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoveredCalibrationEditorTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository: BloodTestRepository = mockk(relaxed = true)
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val pkStorageRepository: PkCalibrationStorageRepository = mockk(relaxed = true)
    private val e2EstimateRepository: E2EstimateRepository = mockk {
        every { observeCalibration(any()) } returns MutableStateFlow(E2State<CalibrationEstimate>(null))
    }
    private val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 5, 20, 10, 0))
    private val lifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle get() = registry
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every {
            medicationLogRepository.getObservedLatestEstradiolEntryOnOrBefore(any())
        } returns ObservedEstradiolEntryLookup.NotLoaded
        coEvery { medicationLogRepository.getLatestEstradiolEntryOnOrBefore(any()) } returns null
        coEvery { repository.getActiveCustomAnalytes() } returns emptyList()
        every { repository.getCachedPanel(any()) } returns null
        every { repository.getCachedActiveCustomAnalytes() } returns null
        every { settingsRepository.settingsState } returns MutableStateFlow(SettingsState())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun restoredDraftUnderMilestones_asksBeforeAPopToThePager() = runTest {
        val restored = processDeathUnderMilestones { editor ->
            editor.updateAnalyteValue(BloodAnalyteKey.E2, "152.4")
        }

        var popped = false
        val leave = restored.leaveBackStack { popped = true; true }
        assertFalse("The restored calibration draft was popped without asking", popped)
        assertNotNull(leave)
    }

    @Test
    fun untouchedRestoredForm_isPoppedAtOnce() = runTest {
        // Its draft is saved too, as the form settles: the key alone is no edit to lose.
        val restored = processDeathUnderMilestones { }

        var popped = false
        assertNull(restored.leaveBackStack { popped = true; true })
        assertTrue(popped)
    }

    /**
     * Opens a new calibration entry, applies [edit], lets milestones cover it, then restores the
     * back stack in a new process, hosting its editors as the NavHost does, and returns the new
     * process's unsaved-changes state.
     */
    private fun TestScope.processDeathUnderMilestones(
        edit: (CalibrationEditorViewModel) -> Unit,
    ): UnsavedChangesState {
        val first = navController()
        first.navigate(Screen.SettingsCalibrationEntry.createRoute(Screen.Settings.route))
        lateinit var editorState: SavedStateHandle
        val editor = calibrationEditor(first.currentBackStackEntry!!, UnsavedChangesState()) {
            editorState = it
        }
        advanceUntilIdle()
        edit(editor)
        advanceUntilIdle()
        assertNotNull(editorState.get<String>("calibrationDraft"))
        first.navigate(MILESTONES)
        val savedState = first.saveState()

        val unsavedChanges = UnsavedChangesState()
        navController(savedState).coveredCalibrationEditorEntry()?.let { entry ->
            unsavedChanges.hostInBackStack(calibrationEditor(entry, unsavedChanges))
        }
        advanceUntilIdle()
        return unsavedChanges
    }

    // A new process's: its own ViewModel store, the back stack restored from [savedState].
    private fun navController(savedState: Bundle? = null) =
        NavHostController(ApplicationProvider.getApplicationContext()).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setLifecycleOwner(lifecycleOwner)
            setViewModelStore(ViewModelStore())
            restoreState(savedState)
            graph = createGraph(startDestination = Screen.Main.route) {
                composable(Screen.Main.route) {}
                composable(
                    route = Screen.SettingsCalibrationEntry.route,
                    arguments = listOf(
                        navArgument(CalibrationEditorViewModel.PANEL_ID_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Settings.route
                        },
                    ),
                ) {}
                composable(MILESTONES) {}
            }
        }

    // The editor's ViewModel as hiltViewModel(entry) creates it, its state from the entry's.
    private fun calibrationEditor(
        entry: NavBackStackEntry,
        unsavedChanges: UnsavedChangesState,
        onState: (SavedStateHandle) -> Unit = {},
    ): CalibrationEditorViewModel = ViewModelProvider.create(
        entry,
        viewModelFactory {
            initializer {
                CalibrationEditorViewModel(
                    repository,
                    medicationLogRepository,
                    settingsRepository,
                    createSavedStateHandle().also(onState),
                    pkStorageRepository,
                    e2EstimateRepository,
                    appTimeSource,
                    unsavedChanges,
                )
            }
        },
    )[CalibrationEditorViewModel::class]

    private companion object {
        const val MILESTONES = "milestones"
    }
}
