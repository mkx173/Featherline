package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodTestResultInput
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationEditorViewModelTest {
    private val repository: BloodTestRepository = mockk(relaxed = true)
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
    fun loadPanelForEditing_mapsExistingBuiltinsIntoDrafts() = runTest {
        val panelUuid = UUID.fromString("f791a95e-f0e0-495d-a1ce-0f41150eed2d")
        coEvery { repository.getPanel(panelUuid) } returns testBloodTestPanel(
            uuid = panelUuid,
            collectedAt = Instant.parse("2026-04-24T00:30:00Z"),
            results = listOf(
                BloodTestResult(
                    uuid = UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a"),
                    createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                    value = 152.4,
                    unitSnapshot = BloodUnitKey.PG_ML.storageValue,
                    canonicalValue = 152.4,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("d6cf4bf5-f47e-41a1-97ce-96f818e63888"),
                    createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                    displayOrder = 1,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.T),
                    value = 31.7,
                    unitSnapshot = BloodUnitKey.NG_DL.storageValue,
                    canonicalValue = 31.7,
                ),
            )
        )

        val viewModel = CalibrationEditorViewModel(
            repository,
            SavedStateHandle(
                mapOf(CalibrationEditorViewModel.PANEL_ID_ARG to panelUuid.toString())
            )
        )
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue(uiState.isEditing)
        assertFalse(uiState.isLoading)
        assertEquals(LocalDate.of(2026, 4, 24), uiState.collectedDate)
        assertEquals(LocalTime.of(9, 30), uiState.collectedTime)
        assertEquals("152.4", uiState.e2Draft.valueText)
        assertEquals(listOf(BloodAnalyteKey.T), uiState.additionalDrafts.map { it.analyteKey })
        assertEquals("31.7", uiState.additionalDrafts.single().valueText)
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
                notes = null,
                results = capture(resultInputSlot),
                now = any()
            )
        } returns panelUuid

        val viewModel = CalibrationEditorViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        viewModel.updateCollectedDate(LocalDate.of(2026, 4, 24))
        viewModel.updateCollectedTime(LocalTime.of(9, 30))
        viewModel.updateE2Value("152.4")
        viewModel.addAnalyte(BloodAnalyteKey.T)
        viewModel.updateAnalyteValue(BloodAnalyteKey.T, "31.7")

        val expectedZoneId = ZoneId.of(viewModel.uiState.value.timeZoneId)
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
        assertEquals(BloodUnitKey.PG_ML, e2Result.unit)
        assertEquals(152.4, e2Result.value, 1e-9)
        assertEquals(BloodAnalyteKey.T, tResult.analyteKey)
        assertEquals(BloodUnitKey.NG_DL, tResult.unit)
        assertEquals(31.7, tResult.value, 1e-9)

        coVerify {
            repository.savePanel(
                uuid = null,
                collectedAt = expectedInstant,
                collectedAtTimeZoneId = viewModel.uiState.value.timeZoneId,
                notes = null,
                results = any(),
                now = any()
            )
        }
    }

    @Test
    fun save_ignoresBlankAdditionalAnalytes() = runTest {
        val resultInputSlot = slot<List<BloodTestResultInput>>()
        coEvery {
            repository.savePanel(
                uuid = null,
                collectedAt = any(),
                collectedAtTimeZoneId = any(),
                notes = null,
                results = capture(resultInputSlot),
                now = any()
            )
        } returns UUID.randomUUID()

        val viewModel = CalibrationEditorViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        viewModel.updateE2Value("95")
        viewModel.addAnalyte(BloodAnalyteKey.FSH)

        viewModel.save()
        advanceUntilIdle()

        val savedResults = resultInputSlot.captured
        assertEquals(1, savedResults.size)
        assertEquals(BloodAnalyteKey.E2, (savedResults.single() as BloodTestResultInput.Builtin).analyteKey)
    }

    @Test
    fun canSaveCalibrationEditorState_requiresAtLeastOneValidValue() {
        val emptyState = CalibrationEditorUiState()
        val validState = emptyState.copy(
            e2Draft = emptyState.e2Draft.copy(valueText = "95")
        )
        val invalidState = emptyState.copy(
            e2Draft = emptyState.e2Draft.copy(valueText = "abc")
        )

        assertFalse(canSaveCalibrationEditorState(emptyState))
        assertTrue(canSaveCalibrationEditorState(validState))
        assertFalse(canSaveCalibrationEditorState(invalidState))
    }
}
