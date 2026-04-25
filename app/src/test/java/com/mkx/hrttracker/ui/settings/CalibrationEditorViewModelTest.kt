package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodTestResultInput
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationEditorViewModelTest {
    private val repository: BloodTestRepository = mockk(relaxed = true)
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
        assertEquals(expectedCollectedDateTime.toLocalTime().withSecond(0).withNano(0), uiState.collectedTime)
        assertEquals("Lab draw before morning dose", uiState.notes)
        assertEquals(
            listOf(BloodAnalyteKey.E2, BloodAnalyteKey.T),
            uiState.drafts.map(CalibrationResultDraftUiState::analyteKey),
        )
        val e2Draft = uiState.drafts.first { it.analyteKey == BloodAnalyteKey.E2 }
        assertEquals("559.5", e2Draft.valueText)
        assertEquals(BloodUnitKey.PMOL_L, e2Draft.unit)
        val tDraft = uiState.drafts.first { it.analyteKey == BloodAnalyteKey.T }
        assertEquals("1.1", tDraft.valueText)
        assertEquals(BloodUnitKey.NMOL_L, tDraft.unit)
        assertNull(uiState.timeSinceLastEstradiolDoseMillis)
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
            SavedStateHandle()
        )
        advanceUntilIdle()
        viewModel.updateAnalyteValue(BloodAnalyteKey.E2, "95")
        viewModel.removeAnalyte(BloodAnalyteKey.T)

        viewModel.save()
        advanceUntilIdle()

        val savedResults = resultInputSlot.captured
        assertEquals(1, savedResults.size)
        assertEquals(BloodAnalyteKey.E2, (savedResults.single() as BloodTestResultInput.Builtin).analyteKey)
    }

    @Test
    fun delete_existingPanel_marksEntryDeleted() = runTest {
        val panelUuid = UUID.fromString("9f8a2bcc-5b67-41e1-81f4-adfe3f0bcf8e")
        coEvery { repository.getPanel(panelUuid) } returns testBloodTestPanel(uuid = panelUuid)
        coEvery { repository.deletePanel(panelUuid) } returns Unit

        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
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
    fun updateCollectedDateAndTime_recomputesTimeSinceLastEstradiolDose() = runTest {
        val viewModel = CalibrationEditorViewModel(
            repository,
            medicationLogRepository,
            SavedStateHandle()
        )
        advanceUntilIdle()

        val zoneId = ZoneId.systemDefault()
        val selectedCollectedAt = LocalDateTime.of(2026, 4, 24, 9, 30)
            .atZone(zoneId)
            .toInstant()
        coEvery { medicationLogRepository.getEntries() } returns listOf(
            testMedicationLogEntry(
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    dose = MedicationDose.MgAsMedicine(2.0),
                ),
                sourceType = MedicationLogEntrySourceType.MANUAL,
                sourceGroupUuid = null,
                appliedAt = selectedCollectedAt.minus(Duration.ofHours(9).plusMinutes(30)),
            )
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
    fun canSaveCalibrationEditorState_requiresAllDraftsValidAndNonEmpty() {
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
        assertFalse(canSaveCalibrationEditorState(invalidState))

        val oneRemainingValidState = emptyState.copy(
            drafts = listOf(e2Draft.copy(valueText = "95")),
        )
        assertTrue(canSaveCalibrationEditorState(oneRemainingValidState))

        val noDraftsState = emptyState.copy(drafts = emptyList())
        assertFalse(canSaveCalibrationEditorState(noDraftsState))
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
            listOf(BloodAnalyteKey.E2, BloodAnalyteKey.PROG, BloodAnalyteKey.PRL, BloodAnalyteKey.LH),
            calibrationAnalyteOptions(state)
        )
    }
}
