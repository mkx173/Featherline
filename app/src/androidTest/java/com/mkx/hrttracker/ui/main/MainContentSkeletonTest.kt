package com.mkx.hrttracker.ui.main

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

class MainContentSkeletonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mainContentShowsStableSectionsBeforeHomeDataReady() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val estradiolTitle = context.getString(R.string.main_e2_title)
        val todayTitle = context.getString(R.string.main_today_title)
        val upcomingTitle = context.getString(R.string.main_upcoming_title)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MainContent(
                    uiState = MainUiState(
                        homeDataReady = false,
                        now = LocalDateTime.of(2026, 5, 5, 10, 30),
                    ),
                    listState = rememberLazyListState(),
                    onQuickLogDoseClick = { _, _, _, _, _ -> },
                    onEntryClick = { },
                )
            }
        }

        val homeList = composeRule.onNode(hasScrollAction())
        composeRule.onNodeWithText(estradiolTitle, ignoreCase = true, useUnmergedTree = true).assertExists()
        homeList.performScrollToIndex(2)
        composeRule.onNodeWithText(todayTitle, ignoreCase = true, useUnmergedTree = true).assertExists()
        homeList.performScrollToIndex(3)
        composeRule.onNodeWithText(upcomingTitle, ignoreCase = true, useUnmergedTree = true).assertExists()
    }
}
