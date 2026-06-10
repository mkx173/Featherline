package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.ObservedEstradiolEntryLookup
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodTestResultInput
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.bloodtest.CustomBloodAnalyte
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.calibration.CalibrationAddAnalyteOption
import com.mkx.hrttracker.ui.calibration.CalibrationDeleteEntryResult
import com.mkx.hrttracker.ui.calibration.CalibrationEditorUiState
import com.mkx.hrttracker.ui.calibration.CalibrationEditorViewModel
import com.mkx.hrttracker.ui.calibration.CalibrationResultDraftUiState
import com.mkx.hrttracker.ui.calibration.CalibrationSaveEntryResult
import com.mkx.hrttracker.ui.calibration.calibrationAddAnalyteOptions
import com.mkx.hrttracker.ui.calibration.calibrationAnalyteOptions
import com.mkx.hrttracker.ui.calibration.canSaveCalibrationEditorState
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationEditorViewModelTest {
    private val repository: BloodTestRepository = mockk(relaxed = true)
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var settingsStateFlow: MutableStateFlow<SettingsState>

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
        settingsStateFlow = MutableStateFlow(SettingsState())
        every { settingsRepository.settingsState } returns settingsStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cachedPanelForEditing_initializesWithoutLoadingState() = runTest {
        val panelUuid = UUID.fromString("2a45018d-864f-402f-9376-1cd167a46ab6")
        val panel = testBloodTestPanel(
            uuid = panelUuid,
            collectedAt = Instant.parse("2026-04-24T00:30:00Z"),
            notes = "Visible from history",
            timeSinceLastEstradiolDoseMillis = 7_200_000L,
        )
        every { repository.getCachedPanel(panelUuid) } returns panel
        coEvery { repository.getPanel(panelUuid) } returns panel

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle(
                mapOf(CalibrationEditorViewModel.PANEL_ID_ARG to panelUuid.toString())
            )
        )

        val initialState = viewModel.uiState.value
        assertTrue(initialState.isEditing)
        assertFalse(initialState.isLoading)
        assertEquals("Visible from history", initialState.notes)
        assertEquals(panelUuid.toString(), initialState.panelUuid)
        assertEquals(7_200_000L, initialState.timeSinceLastEstradiolDoseMillis)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(7_200_000L, viewModel.uiState.value.timeSinceLastEstradiolDoseMillis)
        coVerify(exactly = 0) { repository.getPanel(panelUuid) }
        coVerify(exactly = 0) { medicationLogRepository.getLatestEstradiolEntryOnOrBefore(any()) }
    }

    @Test
    fun init_usesCachedCustomAnalytesForAddOptions() = runTest {
        val customAnalyte = CustomBloodAnalyte(
            uuid = UUID.fromString("61d5fc71-5533-4d5d-aa61-7333077c65c2"),
            abbreviation = "DHT",
            name = "DHT",
            unitLabel = "ng/dL",
            createdAt = Instant.parse("2026-04-24T00:30:00Z"),
            updatedAt = Instant.parse("2026-04-24T00:30:00Z"),
            archivedAt = null,
        )
        every { repository.getCachedPanel(any()) } returns null
        every { repository.getCachedActiveCustomAnalytes() } returns listOf(customAnalyte)

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle()
        )

        assertEquals(listOf(customAnalyte), viewModel.uiState.value.customAnalytes)
        assertTrue(
            calibrationAddAnalyteOptions(viewModel.uiState.value)
                .contains(CalibrationAddAnalyteOption.Custom(customAnalyte))
        )
        coVerify(exactly = 0) { repository.getActiveCustomAnalytes() }
    }

    @Test
    fun init_usesObservedMedicationEntriesForInitialElapsedDose() = runTest {
        val observedEntry = testMedicationLogEntry(
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAt = Instant.EPOCH,
        )
        every {
            medicationLogRepository.getObservedLatestEstradiolEntryOnOrBefore(any())
        } returns ObservedEstradiolEntryLookup.Loaded(observedEntry)

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle()
        )

        val initialState = viewModel.uiState.value
        val targetCollectedAt = LocalDateTime.of(
            initialState.collectedDate,
            initialState.collectedTime,
        ).atZone(ZoneId.systemDefault()).toInstant()
        assertEquals(
            targetCollectedAt.toEpochMilli() - observedEntry.appliedAt.toEpochMilli(),
            initialState.timeSinceLastEstradiolDoseMillis,
        )
        coVerify(exactly = 0) { medicationLogRepository.getLatestEstradiolEntryOnOrBefore(any()) }
    }

    @Test
    fun init_doesNotQueryDatabaseWhenObservedMedicationEntriesLoadedWithoutMatch() = runTest {
        every {
            medicationLogRepository.getObservedLatestEstradiolEntryOnOrBefore(any())
        } returns ObservedEstradiolEntryLookup.Loaded(null)

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle()
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.timeSinceLastEstradiolDoseMillis)
        coVerify(exactly = 0) { medicationLogRepository.getLatestEstradiolEntryOnOrBefore(any()) }
    }

    @Test
    fun loadPanelForEditing_mapsExistingBuiltinsIntoDrafts() = runTest {
        val panelUuid = UUID.fromString("f791a95e-f0e0-495d-a1ce-0f41150eed2d")
        coEvery { repository.getPanel(panelUuid) } returns testBloodTestPanel(
            uuid = panelUuid,
            collectedAt = Instant.parse("2026-04-24T00:30:00Z"),
            notes = "Lab draw before morning dose",
            results = listOf(
                BloodTestResult(
                    uuid = UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a"),
                    createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                    value = 559.5,
                    unitSnapshot = BloodUnitKey.PMOL_L.storageValue,
                    canonicalValue = 152.4,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("d6cf4bf5-f47e-41a1-97ce-96f818e63888"),
                    createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                    displayOrder = 1,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.T),
                    value = 1.1,
                    unitSnapshot = BloodUnitKey.NMOL_L.storageValue,
                    canonicalValue = 31.7,
                ),
            )
        )

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle(
                mapOf(CalibrationEditorViewModel.PANEL_ID_ARG to panelUuid.toString())
            )
        )
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        val expectedCollectedDateTime = Instant.parse("2026-04-24T00:30:00Z")
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        assertTrue(uiState.isEditing)
        assertFalse(uiState.isLoading)
        assertEquals(expectedCollectedDateTime.toLocalDate(), uiState.collectedDate)
        assertEquals(
            expectedCollectedDateTime.toLocalTime().withSecond(0).withNano(0),
            uiState.collectedTime
        )
        assertEquals("Lab draw before morning dose", uiState.notes)
        assertEquals(
            listOf(BloodAnalyteKey.E2, BloodAnalyteKey.T),
            uiState.drafts.mapNotNull(CalibrationResultDraftUiState::analyteKey),
        )
        val e2Draft = uiState.drafts.first { it.analyteKey == BloodAnalyteKey.E2 }
        assertEquals("559.5", e2Draft.valueText)
        assertEquals(BloodUnitKey.PMOL_L, e2Draft.unit)
        assertEquals(BloodUnitKey.PMOL_L, e2Draft.originalUnit)
        val tDraft = uiState.drafts.first { it.analyteKey == BloodAnalyteKey.T }
        assertEquals("1.1", tDraft.valueText)
        assertEquals(BloodUnitKey.NMOL_L, tDraft.unit)
        assertEquals(BloodUnitKey.NMOL_L, tDraft.originalUnit)
        assertNull(uiState.timeSinceLastEstradiolDoseMillis)
    }

    @Test
    fun loadPanelForEditing_mapsExistingCustomResultsIntoDrafts() = runTest {
        val panelUuid = UUID.fromString("f791a95e-f0e0-495d-a1ce-0f41150eed2d")
        val customAnalyteUuid = UUID.fromString("7e4edcb1-baa1-47ba-b1a9-0548f7f194ba")
        coEvery { repository.getPanel(panelUuid) } returns testBloodTestPanel(
            uuid = panelUuid,
            collectedAt = Instant.parse("2026-04-24T00:30:00Z"),
            results = listOf(
                BloodTestResult(
                    uuid = UUID.fromString("1893720a-819e-4a47-85fe-3cb546411ee8"),
                    createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Custom(
                        uuid = customAnalyteUuid,
                        abbreviation = "DHT",
                        name = "DHT",
                    ),
                    value = 18.4,
                    unitSnapshot = "ng/dL",
                    canonicalValue = 18.4,
                ),
            )
        )

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle(
                mapOf(CalibrationEditorViewModel.PANEL_ID_ARG to panelUuid.toString())
            )
        )
        advanceUntilIdle()

        val customDraft = viewModel.uiState.value.drafts.single()
        assertNull(customDraft.analyteKey)
        assertEquals(customAnalyteUuid, customDraft.customAnalyteUuid)
        assertEquals("DHT", customDraft.customAnalyteAbbreviation)
        assertEquals("DHT", customDraft.customAnalyteName)
        assertEquals("ng/dL", customDraft.customUnitLabel)
        assertEquals("18.4", customDraft.valueText)
        assertNull(customDraft.unit)
        assertNull(customDraft.defaultUnit)
    }

    @Test
    fun processDeath_restoresInProgressNewPanelForm() = runTest {
        // The calibration editor is the one form where almost everything is external
        // input, so its in-progress draft must survive process death: collected
        // date/time, selected analytes, their order, value, unit, and note.
        val savedStateHandle = SavedStateHandle()
        val firstSession = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            savedStateHandle,
        )
        advanceUntilIdle()
        firstSession.updateCollectedDate(LocalDate.of(2026, 4, 24))
        firstSession.updateCollectedTime(LocalTime.of(9, 30))
        firstSession.updateNotes("Taken fasting")
        firstSession.removeAnalyte(BloodAnalyteKey.T)
        firstSession.addAnalyte(BloodAnalyteKey.PROG)
        firstSession.updateAnalyteValue(BloodAnalyteKey.E2, "152.4")
        firstSession.updateAnalyteUnit(BloodAnalyteKey.E2, BloodUnitKey.PMOL_L)
        firstSession.updateAnalyteValue(BloodAnalyteKey.PROG, "1.2")
        advanceUntilIdle()

        // Process death: a fresh ViewModel restores from the same SavedStateHandle.
        val restoredSession = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            savedStateHandle,
        )
        advanceUntilIdle()

        val restored = restoredSession.uiState.value
        assertFalse(restored.isLoading)
        assertEquals(LocalDate.of(2026, 4, 24), restored.collectedDate)
        assertEquals(LocalTime.of(9, 30), restored.collectedTime)
        assertEquals("Taken fasting", restored.notes)
        // Selected analytes and their order survive.
        assertEquals(
            listOf(BloodAnalyteKey.E2, BloodAnalyteKey.PROG),
            restored.drafts.mapNotNull(CalibrationResultDraftUiState::analyteKey),
        )
        val e2 = restored.drafts.first { it.analyteKey == BloodAnalyteKey.E2 }
        assertEquals("152.4", e2.valueText)
        assertEquals(BloodUnitKey.PMOL_L, e2.unit)
        assertTrue(e2.isUnitUserSelected)
        val prog = restored.drafts.first { it.analyteKey == BloodAnalyteKey.PROG }
        assertEquals("1.2", prog.valueText)
    }

    @Test
    fun processDeath_existingPanel_restoresEditsOverDbLoadAndKeepsResultUuids() = runTest {
        val panelUuid = UUID.fromString("4a3e2f1d-0c9b-4a87-8d6e-1f2a3b4c5d6e")
        val e2ResultUuid = UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a")
        val panel = testBloodTestPanel(
            uuid = panelUuid,
            collectedAt = Instant.parse("2026-04-24T00:30:00Z"),
            notes = "Original note",
            results = listOf(
                BloodTestResult(
                    uuid = e2ResultUuid,
                    createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                    value = 100.0,
                    unitSnapshot = BloodUnitKey.PMOL_L.storageValue,
                    canonicalValue = 27.2,
                ),
            ),
        )
        coEvery { repository.getPanel(panelUuid) } returns panel
        val resultInputSlot = slot<List<BloodTestResultInput>>()
        coEvery {
            repository.savePanel(
                uuid = panelUuid,
                collectedAt = any(),
                collectedAtTimeZoneId = any(),
                notes = any(),
                results = capture(resultInputSlot),
                now = any(),
            )
        } returns panelUuid

        val savedStateHandle = SavedStateHandle(
            mapOf(CalibrationEditorViewModel.PANEL_ID_ARG to panelUuid.toString())
        )
        val firstSession = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            savedStateHandle,
        )
        advanceUntilIdle()
        firstSession.updateNotes("Edited note")
        firstSession.updateAnalyteValue(BloodAnalyteKey.E2, "250")
        advanceUntilIdle()

        // Process death: restored draft must win over the persisted DB values.
        val restoredSession = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            savedStateHandle,
        )
        advanceUntilIdle()

        val restored = restoredSession.uiState.value
        assertFalse(restored.isLoading)
        assertTrue(restored.isEditing)
        assertEquals("Edited note", restored.notes)
        assertEquals("250", restored.drafts.first { it.analyteKey == BloodAnalyteKey.E2 }.valueText)
        // The restored session must not re-read the DB (snapshot is the source of truth).
        coVerify(exactly = 1) { repository.getPanel(panelUuid) }

        // Saving the restored draft updates the same result row, not a duplicate.
        restoredSession.save()
        advanceUntilIdle()
        val savedResult = resultInputSlot.captured.single() as BloodTestResultInput.Builtin
        assertEquals(e2ResultUuid, savedResult.uuid)
        assertEquals(250.0, savedResult.value, 1e-9)
    }

    @Test
    fun savingNewPanel_clearsPersistedDraftSoProcessDeathDoesNotRestoreSavedAsDuplicate() =
        runTest {
            val savedPanelUuid = UUID.fromString("3a7d2c9e-1b44-4f0e-9a2c-7d6e5f4c3b2a")
            coEvery {
                repository.savePanel(
                    uuid = null,
                    collectedAt = any(),
                    collectedAtTimeZoneId = any(),
                    notes = any(),
                    results = any(),
                    now = any(),
                )
            } returns savedPanelUuid

            val savedStateHandle = SavedStateHandle()
            val firstSession = CalibrationEditorViewModel(
                repository,
                medicationLogRepository,
                settingsRepository,
                savedStateHandle,
            )
            advanceUntilIdle()
            firstSession.updateAnalyteValue(BloodAnalyteKey.E2, "152.4")
            firstSession.updateAnalyteUnit(BloodAnalyteKey.E2, BloodUnitKey.PMOL_L)
            firstSession.updateAnalyteValue(BloodAnalyteKey.T, "31.7")
            firstSession.updateAnalyteUnit(BloodAnalyteKey.T, BloodUnitKey.NMOL_L)
            advanceUntilIdle()
            // The in-progress draft is persisted while editing.
            assertNotNull(savedStateHandle.get<String>("calibrationDraft"))

            firstSession.save()
            advanceUntilIdle()
            assertTrue(firstSession.uiState.value.isSaved)

            // A successful save clears the draft snapshot, so process death in the
            // post-save/pre-navigation window cannot restore the just-saved panel as a new
            // unsaved draft (which would create a duplicate when saved again).
            assertNull(savedStateHandle.get<String>("calibrationDraft"))

            val restoredSession = CalibrationEditorViewModel(
                repository,
                medicationLogRepository,
                settingsRepository,
                savedStateHandle,
            )
            advanceUntilIdle()
            assertNull(restoredSession.uiState.value.panelUuid)
            assertTrue(restoredSession.uiState.value.drafts.all { it.valueText.isEmpty() })
        }

    @Test
    fun save_persistsE2AndAdditionalBuiltins() = runTest {
        val resultInputSlot = slot<List<BloodTestResultInput>>()
        val panelUuid = UUID.fromString("35ab5226-f26d-4c22-918d-785e6687e4e2")
        coEvery {
            repository.savePanel(
                uuid = null,
                collectedAt = any(),
                collectedAtTimeZoneId = any(),
                notes = any(),
                results = capture(resultInputSlot),
                now = any()
            )
        } returns panelUuid

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle()
        )
        advanceUntilIdle()
        viewModel.updateCollectedDate(LocalDate.of(2026, 4, 24))
        viewModel.updateCollectedTime(LocalTime.of(9, 30))
        viewModel.updateNotes("Taken fasting")
        viewModel.updateAnalyteValue(BloodAnalyteKey.E2, "152.4")
        viewModel.updateAnalyteUnit(BloodAnalyteKey.E2, BloodUnitKey.PMOL_L)
        viewModel.updateAnalyteValue(BloodAnalyteKey.T, "31.7")
        viewModel.updateAnalyteUnit(BloodAnalyteKey.T, BloodUnitKey.NMOL_L)

        val expectedZoneId = ZoneId.systemDefault()
        val expectedInstant = LocalDateTime.of(2026, 4, 24, 9, 30)
            .atZone(expectedZoneId)
            .toInstant()

        viewModel.save()
        advanceUntilIdle()

        val savedResults = resultInputSlot.captured
        assertTrue(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(2, savedResults.size)

        val e2Result = savedResults[0] as BloodTestResultInput.Builtin
        val tResult = savedResults[1] as BloodTestResultInput.Builtin
        assertEquals(BloodAnalyteKey.E2, e2Result.analyteKey)
        assertEquals(BloodUnitKey.PMOL_L, e2Result.unit)
        assertEquals(152.4, e2Result.value, 1e-9)
        assertEquals(BloodAnalyteKey.T, tResult.analyteKey)
        assertEquals(BloodUnitKey.NMOL_L, tResult.unit)
        assertEquals(31.7, tResult.value, 1e-9)

        coVerify {
            repository.savePanel(
                uuid = null,
                collectedAt = expectedInstant,
                collectedAtTimeZoneId = expectedZoneId.id,
                notes = "Taken fasting",
                results = any(),
                now = any()
            )
        }
    }

    @Test
    fun save_whenRepositoryFails_updatesUiStateWithFailureResult() = runTest {
        coEvery {
            repository.savePanel(
                uuid = null,
                collectedAt = any(),
                collectedAtTimeZoneId = any(),
                notes = any(),
                results = any(),
                now = any()
            )
        } throws RuntimeException("save failed")

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle()
        )
        advanceUntilIdle()
        viewModel.updateAnalyteValue(BloodAnalyteKey.E2, "152.4")
        viewModel.updateAnalyteUnit(BloodAnalyteKey.E2, BloodUnitKey.PMOL_L)
        viewModel.updateAnalyteValue(BloodAnalyteKey.T, "31.7")
        viewModel.updateAnalyteUnit(BloodAnalyteKey.T, BloodUnitKey.NMOL_L)

        viewModel.save()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(
            CalibrationSaveEntryResult.FAILURE,
            viewModel.uiState.value.saveEntryResult,
        )

        viewModel.consumeSaveEntryResult()
        assertNull(viewModel.uiState.value.saveEntryResult)
        coVerify(exactly = 1) {
            repository.savePanel(
                uuid = null,
                collectedAt = any(),
                collectedAtTimeZoneId = any(),
                notes = any(),
                results = any(),
                now = any()
            )
        }
    }

    @Test
    fun save_writeCompletes_evenWhenViewModelScopeIsCancelledMidWrite() = runTest {
        // Entry teardown (back press racing the 1-frame nav-lock gap) cancels
        // viewModelScope mid-write; a user-confirmed save must not be torn in
        // half (PR #60 finding 11).
        val writeGate = CompletableDeferred<Unit>()
        var writeCompleted = false
        coEvery {
            repository.savePanel(
                uuid = null,
                collectedAt = any(),
                collectedAtTimeZoneId = any(),
                notes = any(),
                results = any(),
                now = any()
            )
        } coAnswers {
            writeGate.await()
            writeCompleted = true
            UUID.randomUUID()
        }

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle()
        )
        advanceUntilIdle()
        viewModel.updateAnalyteValue(BloodAnalyteKey.E2, "152.4")
        viewModel.updateAnalyteUnit(BloodAnalyteKey.E2, BloodUnitKey.PMOL_L)
        viewModel.updateAnalyteValue(BloodAnalyteKey.T, "31.7")
        viewModel.updateAnalyteUnit(BloodAnalyteKey.T, BloodUnitKey.NMOL_L)

        viewModel.save()
        runCurrent()

        viewModel.viewModelScope.cancel()
        writeGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(writeCompleted)
    }

    @Test
    fun delete_writeCompletes_evenWhenViewModelScopeIsCancelledMidWrite() = runTest {
        val panelUuid = UUID.fromString("2a45018d-864f-402f-9376-1cd167a46ab6")
        val panel = testBloodTestPanel(
            uuid = panelUuid,
            collectedAt = Instant.parse("2026-04-24T00:30:00Z"),
            notes = "",
            timeSinceLastEstradiolDoseMillis = null,
        )
        every { repository.getCachedPanel(panelUuid) } returns panel
        coEvery { repository.getPanel(panelUuid) } returns panel
        val writeGate = CompletableDeferred<Unit>()
        var writeCompleted = false
        coEvery { repository.deletePanel(panelUuid) } coAnswers {
            writeGate.await()
            writeCompleted = true
        }

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle(
                mapOf(CalibrationEditorViewModel.PANEL_ID_ARG to panelUuid.toString())
            )
        )
        advanceUntilIdle()

        viewModel.delete()
        runCurrent()

        viewModel.viewModelScope.cancel()
        writeGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(writeCompleted)
    }

    @Test
    fun save_omitsDeletedDefaultAnalytes() = runTest {
        val resultInputSlot = slot<List<BloodTestResultInput>>()
        coEvery {
            repository.savePanel(
                uuid = null,
                collectedAt = any(),
                collectedAtTimeZoneId = any(),
                notes = any(),
                results = capture(resultInputSlot),
                now = any()
            )
        } returns UUID.randomUUID()

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle()
        )
        advanceUntilIdle()
        viewModel.updateAnalyteValue(BloodAnalyteKey.E2, "95")
        viewModel.removeAnalyte(BloodAnalyteKey.T)

        viewModel.save()
        advanceUntilIdle()

        val savedResults = resultInputSlot.captured
        assertEquals(1, savedResults.size)
        assertEquals(
            BloodAnalyteKey.E2,
            (savedResults.single() as BloodTestResultInput.Builtin).analyteKey
        )
    }

    @Test
    fun save_persistsCustomAnalytes() = runTest {
        val resultInputSlot = slot<List<BloodTestResultInput>>()
        val panelUuid = UUID.fromString("35ab5226-f26d-4c22-918d-785e6687e4e2")
        val customAnalyteUuid = UUID.fromString("7e4edcb1-baa1-47ba-b1a9-0548f7f194ba")
        coEvery {
            repository.savePanel(
                uuid = null,
                collectedAt = any(),
                collectedAtTimeZoneId = any(),
                notes = any(),
                results = capture(resultInputSlot),
                now = any()
            )
        } returns panelUuid

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle()
        )
        advanceUntilIdle()
        viewModel.updateAnalyteValue(BloodAnalyteKey.E2, "152.4")
        viewModel.removeAnalyte(BloodAnalyteKey.T)
        viewModel.addCustomAnalyte(
            CustomBloodAnalyte(
                uuid = customAnalyteUuid,
                abbreviation = "DHT",
                name = "DHT",
                unitLabel = "ng/dL",
                createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                updatedAt = Instant.parse("2026-04-24T00:30:00Z"),
                archivedAt = null,
            )
        )
        viewModel.updateCustomAnalyteValue(customAnalyteUuid, "18.4")

        viewModel.save()
        advanceUntilIdle()

        val savedResults = resultInputSlot.captured
        assertEquals(2, savedResults.size)
        assertEquals(
            BloodAnalyteKey.E2,
            (savedResults[0] as BloodTestResultInput.Builtin).analyteKey
        )
        val customResult = savedResults[1] as BloodTestResultInput.Custom
        assertEquals(customAnalyteUuid, customResult.customAnalyteUuid)
        assertEquals(18.4, customResult.value, 1e-9)
    }

    @Test
    fun delete_existingPanel_marksEntryDeleted() = runTest {
        val panelUuid = UUID.fromString("9f8a2bcc-5b67-41e1-81f4-adfe3f0bcf8e")
        coEvery { repository.getPanel(panelUuid) } returns testBloodTestPanel(uuid = panelUuid)
        coEvery { repository.deletePanel(panelUuid) } returns Unit

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle(
                mapOf(CalibrationEditorViewModel.PANEL_ID_ARG to panelUuid.toString())
            )
        )
        advanceUntilIdle()

        viewModel.delete()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDeleted)
        assertFalse(viewModel.uiState.value.isDeleting)
        coVerify(exactly = 1) { repository.deletePanel(panelUuid) }
    }

    @Test
    fun delete_whenRepositoryFails_updatesUiStateWithFailureResult() = runTest {
        val panelUuid = UUID.fromString("32e9a133-51d1-41ed-b9b0-08af6fbb9b65")
        coEvery { repository.getPanel(panelUuid) } returns testBloodTestPanel(uuid = panelUuid)
        coEvery { repository.deletePanel(panelUuid) } throws RuntimeException("delete failed")

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle(
                mapOf(CalibrationEditorViewModel.PANEL_ID_ARG to panelUuid.toString())
            )
        )
        advanceUntilIdle()

        viewModel.delete()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleted)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertEquals(
            CalibrationDeleteEntryResult.FAILURE,
            viewModel.uiState.value.deleteEntryResult,
        )

        viewModel.consumeDeleteEntryResult()
        assertNull(viewModel.uiState.value.deleteEntryResult)
        coVerify(exactly = 1) { repository.deletePanel(panelUuid) }
    }

    @Test
    fun updateCollectedDateAndTime_recomputesTimeSinceLastEstradiolDose() = runTest {
        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle()
        )
        advanceUntilIdle()

        val zoneId = ZoneId.systemDefault()
        val selectedCollectedAt = LocalDateTime.of(2026, 4, 24, 9, 30)
            .atZone(zoneId)
            .toInstant()
        coEvery {
            medicationLogRepository.getLatestEstradiolEntryOnOrBefore(selectedCollectedAt)
        } returns testMedicationLogEntry(
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAt = selectedCollectedAt.minus(Duration.ofHours(9).plusMinutes(30)),
        )

        viewModel.updateCollectedDate(LocalDate.of(2026, 4, 24))
        viewModel.updateCollectedTime(LocalTime.of(9, 30))
        advanceUntilIdle()

        assertEquals(
            Duration.ofHours(9).plusMinutes(30).toMillis(),
            viewModel.uiState.value.timeSinceLastEstradiolDoseMillis
        )
    }

    @Test
    fun init_appliesStoredDefaultUnitsToNewDrafts() = runTest {
        settingsStateFlow.value = SettingsState(
            calibrationDefaultUnits = mapOf(
                BloodAnalyteKey.E2 to BloodUnitKey.PMOL_L,
                BloodAnalyteKey.T to BloodUnitKey.NMOL_L,
            )
        )

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle()
        )

        assertEquals(BloodUnitKey.PMOL_L, viewModel.uiState.value.drafts[0].unit)
        assertEquals(BloodUnitKey.PMOL_L, viewModel.uiState.value.drafts[0].defaultUnit)
        assertNull(viewModel.uiState.value.drafts[0].originalUnit)
        assertEquals(BloodUnitKey.NMOL_L, viewModel.uiState.value.drafts[1].unit)
        assertEquals(BloodUnitKey.NMOL_L, viewModel.uiState.value.drafts[1].defaultUnit)
        assertNull(viewModel.uiState.value.drafts[1].originalUnit)

        advanceUntilIdle()

        assertEquals(BloodUnitKey.PMOL_L, viewModel.uiState.value.drafts[0].unit)
        assertEquals(BloodUnitKey.PMOL_L, viewModel.uiState.value.drafts[0].defaultUnit)
        assertNull(viewModel.uiState.value.drafts[0].originalUnit)
        assertEquals(BloodUnitKey.NMOL_L, viewModel.uiState.value.drafts[1].unit)
        assertEquals(BloodUnitKey.NMOL_L, viewModel.uiState.value.drafts[1].defaultUnit)
        assertNull(viewModel.uiState.value.drafts[1].originalUnit)
    }

    @Test
    fun canSaveCalibrationEditorState_requiresAllDraftsNonEmpty() {
        val emptyState = CalibrationEditorUiState()
        val e2Draft = emptyState.drafts.first { it.analyteKey == BloodAnalyteKey.E2 }
        val tDraft = emptyState.drafts.first { it.analyteKey == BloodAnalyteKey.T }

        assertFalse(canSaveCalibrationEditorState(emptyState))

        val partialState = emptyState.copy(
            drafts = listOf(e2Draft.copy(valueText = "95"), tDraft),
        )
        assertFalse(canSaveCalibrationEditorState(partialState))

        val validState = emptyState.copy(
            drafts = listOf(
                e2Draft.copy(valueText = "95"),
                tDraft.copy(valueText = "42"),
            ),
        )
        assertTrue(canSaveCalibrationEditorState(validState))

        val invalidState = emptyState.copy(
            drafts = listOf(
                e2Draft.copy(valueText = "abc"),
                tDraft.copy(valueText = "42"),
            ),
        )
        assertTrue(canSaveCalibrationEditorState(invalidState))

        val oneRemainingValidState = emptyState.copy(
            drafts = listOf(e2Draft.copy(valueText = "95")),
        )
        assertTrue(canSaveCalibrationEditorState(oneRemainingValidState))

        val noDraftsState = emptyState.copy(drafts = emptyList())
        assertFalse(canSaveCalibrationEditorState(noDraftsState))
    }

    @Test
    fun save_withInvalidNumericInput_marksDraftErrorAndClearsItOnNextEdit() = runTest {
        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            settingsRepository,
            SavedStateHandle()
        )
        advanceUntilIdle()

        viewModel.updateAnalyteValue(BloodAnalyteKey.E2, "abc")
        viewModel.updateAnalyteValue(BloodAnalyteKey.T, "42")

        assertTrue(canSaveCalibrationEditorState(viewModel.uiState.value))

        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            setOf(BloodAnalyteKey.E2.storageValue),
            viewModel.uiState.value.invalidDraftKeys,
        )
        coVerify(exactly = 0) {
            repository.savePanel(
                uuid = any(),
                collectedAt = any(),
                collectedAtTimeZoneId = any(),
                notes = any(),
                results = any(),
                now = any(),
            )
        }

        viewModel.updateAnalyteValue(BloodAnalyteKey.E2, "152.4")

        assertTrue(viewModel.uiState.value.invalidDraftKeys.isEmpty())
    }

    @Test
    fun calibrationAnalyteOptions_excludesAlreadyAddedAnalytes() {
        val state = CalibrationEditorUiState(
            drafts = listOf(
                CalibrationResultDraftUiState(analyteKey = BloodAnalyteKey.T),
                CalibrationResultDraftUiState(analyteKey = BloodAnalyteKey.FSH),
            )
        )

        assertEquals(
            listOf(
                BloodAnalyteKey.E2,
                BloodAnalyteKey.PROG,
                BloodAnalyteKey.PRL,
                BloodAnalyteKey.LH
            ),
            calibrationAnalyteOptions(state)
        )
    }

    @Test
    fun calibrationAddAnalyteOptions_includeRemainingBuiltinsAndCustomAnalytes() {
        val firstCustomAnalyte = CustomBloodAnalyte(
            uuid = UUID.fromString("7e4edcb1-baa1-47ba-b1a9-0548f7f194ba"),
            abbreviation = "DHT",
            name = "DHT",
            unitLabel = "ng/dL",
            createdAt = Instant.parse("2026-04-24T00:30:00Z"),
            updatedAt = Instant.parse("2026-04-24T00:30:00Z"),
            archivedAt = null,
        )
        val secondCustomAnalyte = firstCustomAnalyte.copy(
            uuid = UUID.fromString("973e0580-6aea-44ff-9964-0d1e0f43f8c9"),
            name = "SHBG",
            unitLabel = "nmol/L",
        )
        val state = CalibrationEditorUiState(
            customAnalytes = listOf(firstCustomAnalyte, secondCustomAnalyte),
            drafts = listOf(
                CalibrationResultDraftUiState(analyteKey = BloodAnalyteKey.T),
                CalibrationResultDraftUiState(
                    customAnalyteUuid = firstCustomAnalyte.uuid,
                    customAnalyteName = firstCustomAnalyte.name,
                    customUnitLabel = firstCustomAnalyte.unitLabel,
                ),
            )
        )

        val options = calibrationAddAnalyteOptions(state)

        assertEquals(
            listOf(
                CalibrationAddAnalyteOption.Builtin(BloodAnalyteKey.E2),
                CalibrationAddAnalyteOption.Builtin(BloodAnalyteKey.PROG),
                CalibrationAddAnalyteOption.Builtin(BloodAnalyteKey.PRL),
                CalibrationAddAnalyteOption.Builtin(BloodAnalyteKey.FSH),
                CalibrationAddAnalyteOption.Builtin(BloodAnalyteKey.LH),
                CalibrationAddAnalyteOption.Custom(secondCustomAnalyte),
            ),
            options,
        )
    }
}
