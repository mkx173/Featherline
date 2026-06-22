package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ColorPaletteSwatchGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingSwatchReportsKeyInAssignmentOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var picked: MedicationGroupColorKey? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                // Edge-to-edge is enforced on API 35+, so keep the grid out from
                // under the status bar where touch injection is blocked.
                Box(modifier = Modifier.statusBarsPadding()) {
                    ColorPaletteSwatchGrid(
                        selectedColorKey = null,
                        onColorSelected = { picked = it },
                    )
                }
            }
        }

        // assignmentOrder[2] == CORAL -> a11y index 3 -> "Color 3"
        val coralDescription = context.getString(
            R.string.group_color_picker_swatch_content_description,
            MedicationGroupColorKey.assignmentOrder.indexOf(MedicationGroupColorKey.CORAL) + 1,
        )
        composeRule.onNodeWithContentDescription(coralDescription).performClick()

        composeRule.runOnIdle {
            assertEquals(MedicationGroupColorKey.CORAL, picked)
        }
    }
}
