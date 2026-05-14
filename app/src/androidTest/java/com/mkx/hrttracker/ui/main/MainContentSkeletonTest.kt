package com.mkx.hrttracker.ui.main

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        // MainUpcomingSectionUiState defaults to TOMORROW; the loading screen
        // therefore renders the "Tomorrow" header, not "Upcoming".
        val upcomingTitle = context.getString(R.string.main_tomorrow_title)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MainContent(
                    uiState = MainUiState(
                        homeDataReady = false,
                        now = LocalDateTime.of(2026, 5, 5, 10, 30),
                    ),
                    listState = rememberLazyListState(),
                    onQuickLogDoseClick = { },
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

    @Test
    fun e2ChartShowsSkeletonBeforeTrendReady() {
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MainContent(
                    uiState = MainUiState(
                        homeDataReady = false,
                        now = LocalDateTime.of(2026, 5, 5, 10, 30),
                    ),
                    listState = rememberLazyListState(),
                    onQuickLogDoseClick = { },
                    onEntryClick = { },
                )
            }
        }

        composeRule.onNodeWithTag(MainE2ChartSkeletonTestTag, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(MainE2ChartContentTestTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun e2ChartShowsContentAfterTrendReady() {
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MainContent(
                    uiState = buildMainContentPreviewUiState().copy(
                        homeDataReady = true,
                        e2TrendReady = true,
                    ),
                    listState = rememberLazyListState(),
                    onQuickLogDoseClick = { },
                    onEntryClick = { },
                )
            }
        }

        composeRule.onNodeWithTag(MainE2ChartContentTestTag, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(MainE2ChartSkeletonTestTag, useUnmergedTree = true).assertDoesNotExist()
    }
}
