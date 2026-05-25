package com.mkx.hrttracker.ui.catalog.stock

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class StockIntroPromptSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun doneReturnsParsedInitialCountsAndKeepsBlankRowsNull() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstMedicine = medicine(
            uuid = UUID.fromString("11111111-1111-4111-8111-111111111111"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val secondMedicine = medicine(
            uuid = UUID.fromString("22222222-2222-4222-8222-222222222222"),
            key = MedicationKey.ESTRADIOL_VALERATE,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 4.0),
        )
        var submitted: List<StockIntroEntry>? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                StockIntroPromptSheet(
                    medicines = listOf(firstMedicine, secondMedicine),
                    onDone = { entries -> submitted = entries },
                    onDismissRequest = { },
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.stock_intro_prompt_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("stock-intro-count-${firstMedicine.uuid}")
            .performTextInput("60")
        composeRule
            .onNodeWithTag("stock-intro-count-${secondMedicine.uuid}")
            .performTextInput("Infinity")
        composeRule
            .onNodeWithText(context.getString(R.string.stock_intro_prompt_done))
            .performClick()

        val entries = checkNotNull(submitted)
        assertEquals(listOf(firstMedicine.uuid, secondMedicine.uuid), entries.map { it.medicine.uuid })
        assertEquals(60.0, entries[0].initialCount ?: -1.0, 0.0)
        assertNull(entries[1].initialCount)
    }
}

private fun medicine(
    uuid: UUID,
    key: MedicationKey,
    preparation: MedicinePreparation,
): Medicine {
    return Medicine(
        uuid = uuid,
        selection = MedicineSelection.Catalog(key),
        category = MedicationCategory.ESTRADIOL,
        preparation = preparation,
        displayName = null,
        identityKey = MedicineIdentityKey.catalog(key, preparation),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null,
        stock = MedicineStock(),
    )
}
