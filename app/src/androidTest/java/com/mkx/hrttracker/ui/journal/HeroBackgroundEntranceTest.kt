package com.mkx.hrttracker.ui.journal

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HeroBackgroundEntranceTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun hero() = AnchorRowUiState(
        id = "id1",
        name = "Started E",
        icon = AnchorIcon.MEDICATION,
        palette = null,
        date = LocalDate.parse("2024-01-01"),
        dayMagnitude = 500,
        isFuture = false,
    )

    @Test
    fun wandIsShownAndClickable_whenHeroExists() {
        var opened = false
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesScreenContent(
                    uiState = MilestonesUiState(
                        isLoading = false,
                        hero = hero(),
                        pinnedTray = listOf(hero()),
                    ),
                    onNavigateBack = {},
                    onToggleEdit = {},
                    onSetPinned = { _, _ -> },
                    onReorder = {},
                    onAddDate = {},
                    onUpdateDate = {},
                    onOpenHeroBackground = { opened = true },
                )
            }
        }
        val wand = context.getString(R.string.journal_hero_background_action)
        composeRule.onNodeWithContentDescription(wand).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(wand).performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun wandTouchTargetIsAtLeast48Dp_whenHeroExists() {
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesScreenContent(
                    uiState = MilestonesUiState(
                        isLoading = false,
                        hero = hero(),
                        pinnedTray = listOf(hero()),
                    ),
                    onNavigateBack = {},
                    onToggleEdit = {},
                    onSetPinned = { _, _ -> },
                    onReorder = {},
                    onAddDate = {},
                    onUpdateDate = {},
                    onOpenHeroBackground = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.journal_hero_background_action))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun wandIsHidden_whenThereIsNoHero() {
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesScreenContent(
                    uiState = MilestonesUiState(isLoading = false, hero = null),
                    onNavigateBack = {},
                    onToggleEdit = {},
                    onSetPinned = { _, _ -> },
                    onReorder = {},
                    onAddDate = {},
                    onUpdateDate = {},
                    onOpenHeroBackground = {},
                )
            }
        }
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.journal_hero_background_action))
            .assertDoesNotExist()
    }

    @Test
    fun dialogHostClearsTarget_whenHeroNoLongerMatches() {
        val firstHero = hero()
        val currentHero = mutableStateOf<AnchorRowUiState?>(firstHero)
        val targetHeroId = mutableStateOf<String?>(firstHero.id)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                HeroBackgroundDialogHost(
                    hero = currentHero.value,
                    targetHeroId = targetHeroId.value,
                    onTargetHeroIdChange = { targetHeroId.value = it },
                    onSetHeroBackground = { _, _ -> },
                )
            }
        }

        val title = context.getString(R.string.journal_hero_background_title)
        composeRule.onNodeWithText(title).assertIsDisplayed()

        composeRule.runOnUiThread {
            currentHero.value = null
        }

        composeRule.onNodeWithText(title).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(null, targetHeroId.value)
        }

        composeRule.runOnUiThread {
            currentHero.value = hero().copy(id = "id2")
        }

        composeRule.onNodeWithText(title).assertDoesNotExist()
    }
}
