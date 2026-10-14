package com.mkx.hrttracker.ui.main

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hero's calibration icon opens the estimate sheet through its callback,
 * and is absent until there is an evaluated value to describe.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MainE2HeroCalibrationIconTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun calibratedIcon_invokesTheCallback() {
        assertEquals(1, clicksOn(adjusted = true, descriptionRes = R.string.calibration_pk_hero_adjusted))
    }

    @Test
    fun populationIcon_invokesTheCallback() {
        assertEquals(1, clicksOn(adjusted = false, descriptionRes = R.string.calibration_pk_hero_population))
    }

    @Test
    fun beforeTheFirstEvaluation_thereIsNoIcon() {
        // An un-evaluated hero is not "population"; announcing it would also
        // flip to the calibrated icon a moment later.
        setHero(adjusted = null)
        assertNoIcon()
    }

    @Test
    fun whileTheValueIsASkeleton_thereIsNoIcon() {
        setHero(adjusted = false, trendReady = false)
        assertNoIcon()
    }

    private fun assertNoIcon() {
        composeRule.onNodeWithContentDescription(context.getString(R.string.calibration_pk_hero_population))
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.calibration_pk_hero_adjusted))
            .assertDoesNotExist()
    }

    private fun clicksOn(adjusted: Boolean?, descriptionRes: Int): Int {
        val clicks = setHero(adjusted)
        composeRule.onNodeWithContentDescription(context.getString(descriptionRes)).performClick()
        return clicks()
    }

    /** Sets the hero and returns a click counter. */
    private fun setHero(adjusted: Boolean?, trendReady: Boolean = true): () -> Int {
        var clicks = 0
        composeRule.setContent {
            MainE2HeroCard(
                section = MainE2HeroUiState(currentValue = 120.0),
                now = LocalDateTime.of(2026, 10, 11, 12, 0),
                displayUnit = BloodUnitKey.PG_ML,
                trendReady = trendReady,
                pkCalibration = adjusted?.let {
                    MainPkCalibrationUiState(
                        adjusted = it,
                        limitedConfidence = false,
                        renderUnavailable = false,
                        bandUnavailable = false,
                        band = null,
                    )
                },
                onCalibrationClick = { clicks += 1 },
            )
        }
        return { clicks }
    }
}
