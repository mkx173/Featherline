package com.mkx.hrttracker.ui

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BottomSheetUtilsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun hideBottomSheetRunsHiddenCallbackAfterRepeatedHideRequests() {
        var observedHiddenCount = 0
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()
                var showSheet by remember { mutableStateOf(true) }

                if (showSheet) {
                    ModalBottomSheet(
                        sheetState = sheetState,
                        onDismissRequest = {
                            observedHiddenCount += 1
                            showSheet = false
                        },
                    ) {
                        Button(
                            onClick = {
                                hideBottomSheet(scope, sheetState) {
                                    observedHiddenCount += 1
                                    showSheet = false
                                }
                            },
                        ) {
                            Text("Close test sheet")
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithText("Close test sheet").performClick()
            composeRule.onNodeWithText("Close test sheet").performClick()
            composeRule.mainClock.advanceTimeBy(1_000)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 3_000) {
            observedHiddenCount == 1
        }
        composeRule.runOnIdle {
            assertEquals(1, observedHiddenCount)
        }
    }
}
