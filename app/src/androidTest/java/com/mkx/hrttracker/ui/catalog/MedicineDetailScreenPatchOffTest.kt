package com.mkx.hrttracker.ui.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MedicineDetailScreenPatchOffTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun patchOffSummaryMovesFromPreparationSectionToHeaderTooltip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val patchOff = patchOffMedicine()
        val medicineRepository: MedicineRepository = mockk()
        val medicationGroupRepository: MedicationGroupRepository = mockk()
        val stockRepository: MedicineStockRepository = mockk()
        val settingsRepository: SettingsRepository = mockk()
        every { medicineRepository.getCachedActiveMedicine(patchOff.uuid) } returns patchOff
        every { medicineRepository.observeByUuid(patchOff.uuid) } returns flowOf(patchOff)
        every { medicationGroupRepository.getCachedGroups() } returns emptyList()
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { stockRepository.getCachedProjection(patchOff.uuid) } returns null
        every { stockRepository.observeProjections() } returns flowOf(emptyList())
        every { settingsRepository.settingsState } returns MutableStateFlow(SettingsState())
        coEvery { medicineRepository.isLocked(patchOff.uuid) } returns false

        // Construct the view model outside setContent: building one inside a
        // composable trips the ViewModelConstructorInComposable lint check.
        val viewModel = MedicineDetailViewModel(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            stockRepository = stockRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicineDetailViewModel.MEDICINE_ID_ARG to patchOff.uuid.toString()),
            ),
        )

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MedicineDetailScreen(
                    onNavigateBack = { },
                    onArchiveExit = { true },
                    onGroupClick = { },
                    viewModel = viewModel,
                )
            }
        }

        val patchOffName = context.getString(R.string.medicine_patch_off_name)
        val preparationHeading = context.getString(R.string.medicine_preparation)
        val summary = context.getString(R.string.medicine_patch_off_detail_summary)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText(patchOffName, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText(preparationHeading, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText(summary, useUnmergedTree = true)
            .assertDoesNotExist()

        composeRule.onNodeWithContentDescription(summary, useUnmergedTree = true)
            .performClick()

        composeRule.onNodeWithText(summary, useUnmergedTree = true)
            .assertIsDisplayed()
    }
}

private fun patchOffMedicine(): Medicine {
    return Medicine(
        uuid = UUID.fromString("dddddddd-0000-0000-0000-000000000000"),
        selection = MedicineSelection.PatchOff,
        category = MedicationCategory.ESTRADIOL,
        preparation = MedicinePreparation.PatchOff,
        displayName = null,
        identityKey = MedicineIdentityKey.patchOff(),
        displayDoseUnit = MedicineDisplayDoseUnit.MG,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null,
        stock = MedicineStock(),
    )
}
