package com.mkx.hrttracker.ui.settings

import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.bloodtest.CustomBloodAnalyte
import com.mkx.hrttracker.model.settings.SettingsState
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

        val error = viewModel.archiveCustomAnalyte(analyteUuid)
        advanceUntilIdle()

        assertNull(error)
        assertEquals(emptyList<CustomBloodAnalyte>(), viewModel.uiState.value.customAnalytes)
        coVerify(exactly = 1) {
            bloodTestRepository.archiveCustomAnalyte(
                uuid = analyteUuid,
                now = any(),
            )
        }
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
