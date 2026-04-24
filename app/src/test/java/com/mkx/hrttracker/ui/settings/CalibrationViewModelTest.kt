package com.mkx.hrttracker.ui.settings

import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodTestResultInput
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelTest {
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
    fun parseCalibrationNumericInput_accepts_comma_decimal() {
        assertEquals(123.4, checkNotNull(parseCalibrationNumericInput("123,4")), 1e-9)
        assertEquals(123.4, checkNotNull(parseCalibrationNumericInput(" 123.4 ")), 1e-9)
        assertEquals(null, parseCalibrationNumericInput("abc"))
    }

    @Test
    fun init_loads_panels_from_repository() = runTest {
        val panel = testBloodTestPanel()
        coEvery { repository.getPanels() } returns listOf(panel)

        val viewModel = CalibrationViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf(panel), viewModel.uiState.value.panels)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun addE2Result_saves_panel_and_refreshes_list() = runTest {
        val resultInputSlot = slot<List<BloodTestResultInput>>()
        val panel = testBloodTestPanel()
        coEvery { repository.getPanels() } returnsMany listOf(emptyList(), listOf(panel))
        coEvery {
            repository.savePanel(
                uuid = null,
                collectedAt = Instant.ofEpochMilli(1_700_000_000_000L),
                collectedAtTimeZoneId = "Asia/Tokyo",
                notes = null,
                results = capture(resultInputSlot),
                now = any()
            )
        } returns UUID.randomUUID()

        val viewModel = CalibrationViewModel(repository)
        advanceUntilIdle()

        val saved = viewModel.addE2Result(
            e2ValueText = "123.4",
            collectedAt = Instant.ofEpochMilli(1_700_000_000_000L),
            collectedAtTimeZoneId = "Asia/Tokyo"
        )

        assertTrue(saved)
        assertEquals(listOf(panel), viewModel.uiState.value.panels)
        assertFalse(viewModel.uiState.value.isSaving)

        val builtinResult = resultInputSlot.captured.single() as BloodTestResultInput.Builtin
        assertEquals(BloodAnalyteKey.E2, builtinResult.analyteKey)
        assertEquals(com.mkx.hrttracker.model.bloodtest.BloodUnitKey.PG_ML, builtinResult.unit)
        assertEquals(123.4, builtinResult.value, 1e-9)
        coVerify(exactly = 2) { repository.getPanels() }
    }

    @Test
    fun addE2Result_rejects_invalid_input_without_saving() = runTest {
        coEvery { repository.getPanels() } returns emptyList()

        val viewModel = CalibrationViewModel(repository)
        advanceUntilIdle()

        val saved = viewModel.addE2Result(
            e2ValueText = "abc",
            collectedAt = Instant.ofEpochMilli(1_700_000_000_000L),
            collectedAtTimeZoneId = "Asia/Tokyo"
        )

        assertFalse(saved)
        assertFalse(viewModel.uiState.value.isSaving)
        coVerify(exactly = 0) {
            repository.savePanel(
                uuid = any(),
                collectedAt = any(),
                collectedAtTimeZoneId = any(),
                notes = any(),
                results = any(),
                now = any()
            )
        }
    }
}

private fun testBloodTestPanel(): BloodTestPanel {
    return BloodTestPanel(
        uuid = UUID.fromString("9c95f940-d8c3-4d04-b766-c55f0e014b58"),
        collectedAt = Instant.ofEpochMilli(1_700_000_000_000L),
        collectedAtTimeZoneId = "Asia/Tokyo",
        notes = null,
        results = listOf(
            BloodTestResult(
                uuid = UUID.fromString("b39b8e68-3c42-4c90-bf70-6c8bc486bd98"),
                createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                displayOrder = 0,
                analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                value = 95.0,
                unitSnapshot = "pg_ml",
                canonicalValue = 95.0
            )
        ),
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
        updatedAt = Instant.ofEpochMilli(1_700_000_000_000L)
    )
}
