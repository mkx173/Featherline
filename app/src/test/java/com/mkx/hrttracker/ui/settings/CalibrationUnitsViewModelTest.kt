package com.mkx.hrttracker.ui.settings

import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.bloodtest.CustomBloodAnalyte
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.calibration.CalibrationArchiveCustomAnalyteResult
import com.mkx.hrttracker.ui.calibration.CalibrationUnitsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationUnitsViewModelTest {
    private val settingsRepository: SettingsRepository = mockk()
    private val bloodTestRepository: BloodTestRepository = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var settingsStateFlow: MutableStateFlow<SettingsState>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settingsStateFlow = MutableStateFlow(
            SettingsState(
                calibrationDefaultUnits = mapOf(BloodAnalyteKey.E2 to BloodUnitKey.PMOL_L)
            )
        )
        every { settingsRepository.settingsState } returns settingsStateFlow
        every { bloodTestRepository.getCachedActiveCustomAnalytes() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsActiveCustomAnalytes() = runTest {
        val customAnalytes = listOf(
            testCustomBloodAnalyte(
                uuid = UUID.fromString("2bb92630-21ba-4703-a9b9-2994db8f411a"),
                name = "DHT",
                unitLabel = "ng/dL",
            )
        )
        coEvery { bloodTestRepository.getActiveCustomAnalytes() } returns customAnalytes

        val viewModel = CalibrationUnitsViewModel(settingsRepository, bloodTestRepository)
        advanceUntilIdle()

        assertEquals(customAnalytes, viewModel.uiState.value.customAnalytes)
        assertEquals(settingsStateFlow.value, viewModel.uiState.value.settingsState)
        assertFalse(viewModel.uiState.value.isLoadingCustomAnalytes)
    }

    @Test
    fun init_usesCachedCustomAnalytesWithoutLoadingState() = runTest {
        val cachedAnalytes = listOf(
            testCustomBloodAnalyte(
                uuid = UUID.fromString("fc515834-5b90-4354-b268-f68c861b2b1d"),
                name = "DHT",
                unitLabel = "ng/dL",
            )
        )
        every { bloodTestRepository.getCachedActiveCustomAnalytes() } returns cachedAnalytes

        val viewModel = CalibrationUnitsViewModel(settingsRepository, bloodTestRepository)
        advanceUntilIdle()

        assertEquals(cachedAnalytes, viewModel.uiState.value.customAnalytes)
        assertFalse(viewModel.uiState.value.isLoadingCustomAnalytes)
        coVerify(exactly = 0) { bloodTestRepository.getActiveCustomAnalytes() }
    }

    @Test
    fun saveCustomAnalyte_refreshesActiveAnalytes() = runTest {
        val analyteUuid = UUID.fromString("9401767e-c4c7-4f06-8c78-73a2610ce4aa")
        val refreshedAnalytes = listOf(
            testCustomBloodAnalyte(
                uuid = analyteUuid,
                name = "SHBG",
                unitLabel = "nmol/L",
            )
        )
        coEvery { bloodTestRepository.getActiveCustomAnalytes() } returns emptyList()
        coEvery {
            bloodTestRepository.saveCustomAnalyte(
                uuid = null,
                abbreviation = "SHBG",
                name = "SHBG",
                unitLabel = "nmol/L",
                now = any(),
            )
        } returns analyteUuid
        coEvery { bloodTestRepository.getActiveCustomAnalytes() } returnsMany listOf(
            emptyList(),
            refreshedAnalytes,
        )

        val viewModel = CalibrationUnitsViewModel(settingsRepository, bloodTestRepository)
        advanceUntilIdle()

        val error = viewModel.saveCustomAnalyte(
            uuid = null,
            abbreviation = "SHBG",
            name = "SHBG",
            unitLabel = "nmol/L",
        )
        advanceUntilIdle()

        assertNull(error)
        assertEquals(refreshedAnalytes, viewModel.uiState.value.customAnalytes)
        coVerify(exactly = 1) {
            bloodTestRepository.saveCustomAnalyte(
                uuid = null,
                abbreviation = "SHBG",
                name = "SHBG",
                unitLabel = "nmol/L",
                now = any(),
            )
        }
    }

    @Test
    fun saveCustomAnalyte_whenRefreshFails_returnsNoErrorAndKeepsCurrentAnalytes() = runTest {
        val analyteUuid = UUID.fromString("a6d8dc40-9421-4a8e-b3c2-f884f13b1512")
        coEvery { bloodTestRepository.getActiveCustomAnalytes() } returns emptyList()
        coEvery {
            bloodTestRepository.saveCustomAnalyte(
                uuid = null,
                abbreviation = "DHT",
                name = "DHT",
                unitLabel = "ng/dL",
                now = any(),
            )
        } returns analyteUuid

        val viewModel = CalibrationUnitsViewModel(settingsRepository, bloodTestRepository)
        advanceUntilIdle()
        coEvery { bloodTestRepository.getActiveCustomAnalytes() } throws RuntimeException("refresh failed")

        val error = viewModel.saveCustomAnalyte(
            uuid = null,
            abbreviation = "DHT",
            name = "DHT",
            unitLabel = "ng/dL",
        )
        advanceUntilIdle()

        assertNull(error)
        assertEquals(emptyList<CustomBloodAnalyte>(), viewModel.uiState.value.customAnalytes)
        coVerify(exactly = 1) {
            bloodTestRepository.saveCustomAnalyte(
                uuid = null,
                abbreviation = "DHT",
                name = "DHT",
                unitLabel = "ng/dL",
                now = any(),
            )
        }
    }

    @Test
    fun archiveCustomAnalyte_refreshesActiveAnalytes() = runTest {
        val analyteUuid = UUID.fromString("9401767e-c4c7-4f06-8c78-73a2610ce4aa")
        val existingAnalytes = listOf(
            testCustomBloodAnalyte(
                uuid = analyteUuid,
                name = "SHBG",
                unitLabel = "nmol/L",
            )
        )
        coEvery { bloodTestRepository.getActiveCustomAnalytes() } returnsMany listOf(
            existingAnalytes,
            emptyList(),
        )
        coEvery {
            bloodTestRepository.archiveCustomAnalyte(
                uuid = analyteUuid,
                now = any(),
            )
        } returns Unit

        val viewModel = CalibrationUnitsViewModel(settingsRepository, bloodTestRepository)
        advanceUntilIdle()

        viewModel.archiveCustomAnalyte(analyteUuid)
        advanceUntilIdle()

        assertEquals(
            CalibrationArchiveCustomAnalyteResult.SUCCESS,
            viewModel.uiState.value.archiveCustomAnalyteResult
        )
        assertFalse(viewModel.uiState.value.isArchivingCustomAnalyte)
        assertEquals(emptyList<CustomBloodAnalyte>(), viewModel.uiState.value.customAnalytes)

        viewModel.consumeArchiveCustomAnalyteResult()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.archiveCustomAnalyteResult)
        coVerify(exactly = 1) {
            bloodTestRepository.archiveCustomAnalyte(
                uuid = analyteUuid,
                now = any(),
            )
        }
    }

    @Test
    fun archiveCustomAnalyte_whenRefreshFails_reportsSuccessAndRemovesAnalyte() = runTest {
        val analyteUuid = UUID.fromString("6d18a7ad-818c-4fab-9744-8ebbe78149da")
        val existingAnalytes = listOf(
            testCustomBloodAnalyte(
                uuid = analyteUuid,
                name = "DHT",
                unitLabel = "ng/dL",
            )
        )
        coEvery { bloodTestRepository.getActiveCustomAnalytes() } returns existingAnalytes
        coEvery {
            bloodTestRepository.archiveCustomAnalyte(
                uuid = analyteUuid,
                now = any(),
            )
        } returns Unit

        val viewModel = CalibrationUnitsViewModel(settingsRepository, bloodTestRepository)
        advanceUntilIdle()
        coEvery { bloodTestRepository.getActiveCustomAnalytes() } throws RuntimeException("refresh failed")

        viewModel.archiveCustomAnalyte(analyteUuid)
        advanceUntilIdle()

        assertEquals(
            CalibrationArchiveCustomAnalyteResult.SUCCESS,
            viewModel.uiState.value.archiveCustomAnalyteResult
        )
        assertFalse(viewModel.uiState.value.isArchivingCustomAnalyte)
        assertEquals(emptyList<CustomBloodAnalyte>(), viewModel.uiState.value.customAnalytes)
    }

    @Test
    fun archiveCustomAnalyte_whenRepositoryFails_updatesUiStateWithFailureResult() = runTest {
        val analyteUuid = UUID.fromString("56967f39-311b-43cc-943a-b7cb21f8a804")
        val existingAnalytes = listOf(
            testCustomBloodAnalyte(
                uuid = analyteUuid,
                name = "SHBG",
                unitLabel = "nmol/L",
            )
        )
        coEvery { bloodTestRepository.getActiveCustomAnalytes() } returns existingAnalytes
        coEvery {
            bloodTestRepository.archiveCustomAnalyte(
                uuid = analyteUuid,
                now = any(),
            )
        } throws RuntimeException("archive failed")

        val viewModel = CalibrationUnitsViewModel(settingsRepository, bloodTestRepository)
        advanceUntilIdle()

        viewModel.archiveCustomAnalyte(analyteUuid)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isArchivingCustomAnalyte)
        assertEquals(
            CalibrationArchiveCustomAnalyteResult.FAILURE,
            viewModel.uiState.value.archiveCustomAnalyteResult,
        )
        assertEquals(existingAnalytes, viewModel.uiState.value.customAnalytes)

        viewModel.consumeArchiveCustomAnalyteResult()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.archiveCustomAnalyteResult)
        coVerify(exactly = 1) {
            bloodTestRepository.archiveCustomAnalyte(
                uuid = analyteUuid,
                now = any(),
            )
        }
    }

    @Test
    fun hasResultsForCustomAnalyte_whenRepositoryFails_returnsTrue() = runTest {
        val analyteUuid = UUID.fromString("2ed03461-f8f7-42a9-85a0-20fd9398ef81")
        coEvery { bloodTestRepository.getActiveCustomAnalytes() } returns emptyList()
        coEvery {
            bloodTestRepository.hasResultsForCustomAnalyte(analyteUuid)
        } throws RuntimeException("check failed")

        val viewModel = CalibrationUnitsViewModel(settingsRepository, bloodTestRepository)
        advanceUntilIdle()

        assertTrue(viewModel.hasResultsForCustomAnalyte(analyteUuid))
    }

}

private fun testCustomBloodAnalyte(
    uuid: UUID,
    name: String,
    unitLabel: String,
    abbreviation: String = name,
): CustomBloodAnalyte {
    return CustomBloodAnalyte(
        uuid = uuid,
        abbreviation = abbreviation,
        name = name,
        unitLabel = unitLabel,
        createdAt = Instant.parse("2026-04-24T00:30:00Z"),
        updatedAt = Instant.parse("2026-04-24T00:30:00Z"),
        archivedAt = null,
    )
}
