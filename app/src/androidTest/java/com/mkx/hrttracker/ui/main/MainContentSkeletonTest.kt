package com.mkx.hrttracker.ui.main

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
                    scrollState = rememberScrollState(),
                    onQuickLogDoseClick = { },
                    onEntryClick = { },
                )
            }
        }

        composeRule.onNodeWithText(estradiolTitle, ignoreCase = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(todayTitle, ignoreCase = true, useUnmergedTree = true).assertExists()
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
                    scrollState = rememberScrollState(),
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
                    scrollState = rememberScrollState(),
                    onQuickLogDoseClick = { },
                    onEntryClick = { },
                )
            }
        }

        composeRule.onNodeWithTag(MainE2ChartContentTestTag, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(MainE2ChartSkeletonTestTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun manualTodayRowUsesIconIndicatorInsteadOfTextLabel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manualLabel = context.getString(R.string.plan_entry_label_manual)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MainContent(
                    uiState = buildMainContentPreviewUiState(),
                    scrollState = rememberScrollState(),
                    onQuickLogDoseClick = { },
                    onEntryClick = { },
                )
            }
        }

        composeRule.onNodeWithText(manualLabel, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(manualLabel, useUnmergedTree = true).assertExists()
    }
}
