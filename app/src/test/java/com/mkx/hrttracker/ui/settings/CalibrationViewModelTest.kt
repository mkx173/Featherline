package com.mkx.hrttracker.ui.settings

import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import io.mockk.coEvery
import io.mockk.mockk
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
}

internal fun testBloodTestPanel(
    uuid: UUID = UUID.fromString("9c95f940-d8c3-4d04-b766-c55f0e014b58"),
    collectedAt: Instant = Instant.ofEpochMilli(1_700_000_000_000L),
    collectedAtTimeZoneId: String = "Asia/Tokyo",
    results: List<BloodTestResult> = listOf(
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
): BloodTestPanel {
    return BloodTestPanel(
        uuid = uuid,
        collectedAt = collectedAt,
        collectedAtTimeZoneId = collectedAtTimeZoneId,
        notes = null,
        results = results,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
        updatedAt = Instant.ofEpochMilli(1_700_000_000_000L)
    )
}
