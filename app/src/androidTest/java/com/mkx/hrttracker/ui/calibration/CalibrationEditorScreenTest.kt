package com.mkx.hrttracker.ui.calibration

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalibrationEditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun addAnalyteCancelDoesNotClearSheetSynchronously() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var dismissCount = 0

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                CalibrationAddAnalyteSheet(
                    availableAnalytes = listOf(
                        CalibrationAddAnalyteOption.Builtin(BloodAnalyteKey.PROG),
                    ),
                    onDismissRequest = { dismissCount += 1 },
                    onAnalyteClick = { },
                )
            }
        }
        composeRule
            .onNodeWithText(context.getString(R.string.settings_calibration_analyte_prog))
            .assertExists()
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule
                .onNodeWithText(context.getString(R.string.cancel))
                .performClick()

            assertEquals(0, dismissCount)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }
}
