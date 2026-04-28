package com.mkx.hrttracker.ui.settings

import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.calibration.CalibrationDeleteAllEntriesResult
import com.mkx.hrttracker.ui.calibration.CalibrationPanelResultSummary
import com.mkx.hrttracker.ui.calibration.CalibrationRangeStatus
import com.mkx.hrttracker.ui.calibration.CalibrationViewModel
import com.mkx.hrttracker.ui.calibration.calibrationRangeStatus
import com.mkx.hrttracker.ui.calibration.calibrationValueInPreferredUnitLabel
import com.mkx.hrttracker.ui.calibration.formatCalibrationPanelValueSummary
import com.mkx.hrttracker.ui.calibration.groupCalibrationPanelsByMonth
import com.mkx.hrttracker.ui.calibration.parseCalibrationNumericInput
import com.mkx.hrttracker.util.CalibrationPanelDateTimeFormatters
import com.mkx.hrttracker.util.calibrationMonthHeaderFormatter
import com.mkx.hrttracker.util.formatCalibrationPanelDateTimeLabels
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelTest {
    private val repository: BloodTestRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var settingsStateFlow: MutableStateFlow<SettingsState>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settingsStateFlow = MutableStateFlow(SettingsState())
        every { settingsRepository.settingsState } returns settingsStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun parseCalibrationNumericInput_accepts_comma_decimal() {
        assertEquals(123.4, checkNotNull(parseCalibrationNumericInput("123,4")), 1e-9)
        assertEquals(123.4, checkNotNull(parseCalibrationNumericInput(" 123.4 ")), 1e-9)
        assertEquals(0.0, checkNotNull(parseCalibrationNumericInput("0")), 1e-9)
        assertEquals(null, parseCalibrationNumericInput("abc"))
        assertEquals(null, parseCalibrationNumericInput("-1"))
        assertEquals(null, parseCalibrationNumericInput("-0.5"))
    }

    @Test
    fun init_loads_panels_from_repository() = runTest {
        val panel = testBloodTestPanel()
        every { repository.observePanels() } returns flowOf(listOf(panel))

        val viewModel = CalibrationViewModel(repository, settingsRepository)
        advanceUntilIdle()

        assertEquals(listOf(panel), viewModel.uiState.value.panels)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun ui_state_updates_when_repository_emits_new_panels() = runTest {
        val initialPanel = testBloodTestPanel(
            uuid = UUID.fromString("9c95f940-d8c3-4d04-b766-c55f0e014b58")
        )
        val updatedPanel = testBloodTestPanel(
            uuid = UUID.fromString("5d802712-bfc0-4af9-96f9-ae9c050b6af8")
        )
        val flow = MutableStateFlow(listOf(initialPanel))
        every { repository.observePanels() } returns flow

        val viewModel = CalibrationViewModel(repository, settingsRepository)
        advanceUntilIdle()
        assertEquals(listOf(initialPanel), viewModel.uiState.value.panels)

        flow.value = listOf(updatedPanel)
        advanceUntilIdle()

        assertEquals(listOf(updatedPanel), viewModel.uiState.value.panels)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun deleteAllCalibrationEntries_updatesUiStateWithSuccessResult() = runTest {
        every { repository.observePanels() } returns flowOf(emptyList())
        coEvery { repository.deleteAllPanels() } returns Unit

        val viewModel = CalibrationViewModel(repository, settingsRepository)
        advanceUntilIdle()

        viewModel.deleteAllCalibrationEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeletingAllEntries)
        assertEquals(
            CalibrationDeleteAllEntriesResult.SUCCESS,
            viewModel.uiState.value.deleteAllEntriesResult,
        )

        viewModel.consumeDeleteAllEntriesResult()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deleteAllEntriesResult)
        coVerify(exactly = 1) { repository.deleteAllPanels() }
    }

    @Test
    fun formatCalibrationPanelValueSummary_showsEstradiolTestosteroneAndRemainingEntries() {
        val panel = testBloodTestPanel(
            results = listOf(
                BloodTestResult(
                    uuid = UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a"),
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    displayOrder = 1,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                    value = 95.0,
                    unitSnapshot = BloodUnitKey.PG_ML.storageValue,
                    canonicalValue = 95.0,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("df0d4beb-21a2-4138-a52e-260306d35da5"),
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
                    displayOrder = 2,
                    analyte = BloodTestResultAnalyte.Custom(
                        uuid = UUID.fromString("387e4ffb-6ddb-4b69-a6d9-95353d2e1f55"),
                        abbreviation = "Marker",
                        name = "Marker",
                    ),
                    value = 8.5,
                    unitSnapshot = "ratio",
                    canonicalValue = 8.5,
                ),
            )
        )

        val summary = formatCalibrationPanelValueSummary(panel)

        assertEquals(
            CalibrationPanelResultSummary.Builtin(
                analyteKey = BloodAnalyteKey.E2,
                value = "95",
                unit = "pg/mL",
                rangeStatus = CalibrationRangeStatus.BELOW,
            ),
            summary.mainResultSummary,
        )
        assertEquals(
            CalibrationPanelResultSummary.Builtin(
                analyteKey = BloodAnalyteKey.T,
                value = "31.7",
                unit = "ng/dL",
                rangeStatus = CalibrationRangeStatus.IN_RANGE,
            ),
            summary.testosteroneResultSummary,
        )
        assertEquals(1, summary.remainingResultCount)
    }

    @Test
    fun formatCalibrationPanelValueSummary_usesStoredDefaultUnitsForBuiltins() {
        val panel = testBloodTestPanel(
            results = listOf(
                BloodTestResult(
                    uuid = UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a"),
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                    value = 95.0,
                    unitSnapshot = BloodUnitKey.PG_ML.storageValue,
                    canonicalValue = 95.0,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("df0d4beb-21a2-4138-a52e-260306d35da5"),
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    displayOrder = 1,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.T),
                    value = 1.1,
                    unitSnapshot = BloodUnitKey.NMOL_L.storageValue,
                    canonicalValue = 31.7,
                ),
            )
        )

        val summary = formatCalibrationPanelValueSummary(
            panel = panel,
            settingsState = SettingsState(
                calibrationDefaultUnits = mapOf(
                    BloodAnalyteKey.E2 to BloodUnitKey.PMOL_L,
                    BloodAnalyteKey.T to BloodUnitKey.NMOL_L,
                )
            ),
        )

        assertEquals(
            CalibrationPanelResultSummary.Builtin(
                analyteKey = BloodAnalyteKey.E2,
                value = "348.7",
                unit = "pmol/L",
                rangeStatus = CalibrationRangeStatus.BELOW,
            ),
            summary.mainResultSummary,
        )
        assertEquals(
            CalibrationPanelResultSummary.Builtin(
                analyteKey = BloodAnalyteKey.T,
                value = "1.1",
                unit = "nmol/L",
                rangeStatus = CalibrationRangeStatus.IN_RANGE,
            ),
            summary.testosteroneResultSummary,
        )
    }

    @Test
    fun calibrationValueInPreferredUnitLabel_convertsWhenUnitsDiffer() {
        assertEquals(
            "348.7 pmol/L",
            calibrationValueInPreferredUnitLabel(
                analyteKey = BloodAnalyteKey.E2,
                valueText = "95",
                unit = BloodUnitKey.PG_ML,
                preferredUnit = BloodUnitKey.PMOL_L,
            ),
        )
        assertEquals(
            null,
            calibrationValueInPreferredUnitLabel(
                analyteKey = BloodAnalyteKey.E2,
                valueText = "95",
                unit = BloodUnitKey.PG_ML,
                preferredUnit = BloodUnitKey.PG_ML,
            ),
        )
        assertEquals(
            null,
            calibrationValueInPreferredUnitLabel(
                analyteKey = BloodAnalyteKey.E2,
                valueText = "",
                unit = BloodUnitKey.PG_ML,
                preferredUnit = BloodUnitKey.PMOL_L,
            ),
        )
    }

    @Test
    fun calibrationRangeStatus_respectsSelectedUnit() {
        assertEquals(
            CalibrationRangeStatus.IN_RANGE,
            calibrationRangeStatus(
                analyteKey = BloodAnalyteKey.T,
                valueText = "40",
                unit = BloodUnitKey.NG_DL,
            ),
        )
        assertEquals(
            CalibrationRangeStatus.ABOVE,
            calibrationRangeStatus(
                analyteKey = BloodAnalyteKey.T,
                valueText = "40",
                unit = BloodUnitKey.NMOL_L,
            ),
        )
    }

    @Test
    fun formatCalibrationPanelValueSummary_withoutEstradiolShowsFirstResultOnly() {
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
                        abbreviation = "Marker",
                        name = "Marker",
                    ),
                    value = 8.5,
                    unitSnapshot = "ratio",
                    canonicalValue = 8.5,
                )
            )
        )

        val summary = formatCalibrationPanelValueSummary(panel)

        assertEquals(
            CalibrationPanelResultSummary.Builtin(
                analyteKey = BloodAnalyteKey.T,
                value = "31.7",
                unit = "ng/dL",
                rangeStatus = CalibrationRangeStatus.IN_RANGE,
            ),
            summary.mainResultSummary,
        )
        assertEquals(null, summary.testosteroneResultSummary)
        assertEquals(1, summary.remainingResultCount)
    }

    @Test
    fun groupCalibrationPanelsByMonth_usesLocalDateAndPreservesPanelOrder() {
        val formatter = calibrationMonthHeaderFormatter(
            locale = Locale.ENGLISH,
            currentYear = 2026,
        )
        val localZoneId = ZoneId.of("UTC")
        val localAprilPanel = testBloodTestPanel(
            uuid = UUID.fromString("fdc5a64f-3b29-4676-8d51-37976eb07b1d"),
            collectedAt = Instant.parse("2026-04-01T06:30:00Z"),
            collectedAtTimeZoneId = "America/Los_Angeles",
        )
        val localMarchPanel = testBloodTestPanel(
            uuid = UUID.fromString("1bd6b492-7670-4912-b625-7f06006e3797"),
            collectedAt = Instant.parse("2026-03-31T15:30:00Z"),
            collectedAtTimeZoneId = "Asia/Tokyo",
        )
        val utcMarchPanel = testBloodTestPanel(
            uuid = UUID.fromString("3981dbac-4620-478e-af2f-60e352ba4319"),
            collectedAt = Instant.parse("2026-03-15T00:00:00Z"),
            collectedAtTimeZoneId = "UTC",
        )

        val groups = groupCalibrationPanelsByMonth(
            panels = listOf(localAprilPanel, localMarchPanel, utcMarchPanel),
            monthFormatter = formatter,
            zoneId = localZoneId,
        )

        assertEquals(2, groups.size)
        assertEquals(YearMonth.of(2026, 4), groups[0].yearMonth)
        assertEquals("April", groups[0].monthLabel)
        assertEquals(listOf(localAprilPanel.uuid), groups[0].panels.map(BloodTestPanel::uuid))
        assertEquals(YearMonth.of(2026, 3), groups[1].yearMonth)
        assertEquals("March", groups[1].monthLabel)
        assertEquals(
            listOf(localMarchPanel.uuid, utcMarchPanel.uuid),
            groups[1].panels.map(BloodTestPanel::uuid),
        )
    }

    @Test
    fun formatCalibrationPanelDateTimeLabels_usesLocalDateTime() {
        val formatters = CalibrationPanelDateTimeFormatters(
            monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH),
            dayFormatter = DateTimeFormatter.ofPattern("d", Locale.ENGLISH),
            timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH),
        )
        val panel = testBloodTestPanel(
            collectedAt = Instant.parse("2026-04-01T06:30:00Z"),
            collectedAtTimeZoneId = "America/Los_Angeles",
        )

        val labels = formatCalibrationPanelDateTimeLabels(
            collectedAt = panel.collectedAt,
            dateTimeFormatters = formatters,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals("Apr", labels.monthLabel)
        assertEquals("1", labels.dayLabel)
        assertEquals("06:30", labels.timeLabel)
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
        timeSinceLastEstradiolDoseMillis = null,
        timeSinceLastTestosteroneDoseMillis = null,
        results = results,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
        updatedAt = Instant.ofEpochMilli(1_700_000_000_000L)
    )
}
