package com.mkx.hrttracker.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import kotlinx.coroutines.CoroutineScope
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
        lateinit var sheetState: SheetState
        lateinit var scope: CoroutineScope
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                scope = rememberCoroutineScope()
                var showSheet by remember { mutableStateOf(true) }

                if (showSheet) {
                    ModalBottomSheet(
                        sheetState = sheetState,
                        onDismissRequest = { showSheet = false },
                    ) {
                        Text("Sheet content")
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Two hide requests fired before the first settles must collapse to a single
        // hidden callback (hideBottomSheet dedups the in-flight job). Invoking them
        // synchronously on the UI thread makes the second observe the first job still
        // active, so this exercises the dedup-plus-completion contract directly.
        //
        // It deliberately does NOT freeze the clock to hold the animation mid-flight.
        // Freezing it (the previous approach) orphaned the hide coroutine's dispatch
        // under the test clock — its scope is rememberCoroutineScope's AndroidUiDispatcher,
        // which the test frame clock doesn't govern — so the hide occasionally never
        // started and the callback never fired (~5-7% on some hardware). Here the hide
        // runs under the normal auto-advancing clock, exactly as in production.
        composeRule.runOnUiThread {
            hideBottomSheet(scope, sheetState) { observedHiddenCount += 1 }
            hideBottomSheet(scope, sheetState) { observedHiddenCount += 1 }
        }

        composeRule.waitUntil(timeoutMillis = 3_000) {
            observedHiddenCount == 1
        }
        composeRule.runOnIdle {
            assertEquals(1, observedHiddenCount)
        }
    }
}
