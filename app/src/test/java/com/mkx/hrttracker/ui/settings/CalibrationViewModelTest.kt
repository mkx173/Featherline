package com.mkx.hrttracker.ui.settings

import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
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

    @Test
    fun formatCalibrationPanelValueSummary_usesCanonicalUnitsForBuiltinResults() {
        val panel = testBloodTestPanel(
            results = listOf(
                BloodTestResult(
                    uuid = UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a"),
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.T),
                    value = 1.1,
                    unitSnapshot = BloodUnitKey.NMOL_L.storageValue,
                    canonicalValue = 31.7,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("2c35207b-c771-4c11-b6f2-f35f485542cd"),
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    displayOrder = 1,
                    analyte = BloodTestResultAnalyte.Custom(
                        uuid = UUID.fromString("387e4ffb-6ddb-4b69-a6d9-95353d2e1f55"),
                        name = "Marker",
                    ),
                    value = 8.5,
                    unitSnapshot = "ratio",
                    canonicalValue = 8.5,
                )
            )
        )

        assertEquals(
            "T 31.7 ng/dL · Marker 8.5 ratio",
            formatCalibrationPanelValueSummary(panel)
        )
    }
}

internal fun testBloodTestPanel(
    uuid: UUID = UUID.fromString("9c95f940-d8c3-4d04-b766-c55f0e014b58"),
    collectedAt: Instant = Instant.ofEpochMilli(1_700_000_000_000L),
    collectedAtTimeZoneId: String = "Asia/Tokyo",
    notes: String? = null,
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
        notes = notes,
        results = results,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
        updatedAt = Instant.ofEpochMilli(1_700_000_000_000L)
    )
}
