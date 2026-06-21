package com.mkx.hrttracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A custom group/anchor color scheme must follow the *resolved* app theme, not the
 * raw system night mode. On API < 31 the in-app dark-mode override is applied only
 * through AppCompatDelegate.setDefaultNightMode (UiModeManager.setApplicationNightMode
 * is API 31+), so it is never baked into the Configuration that isSystemInDarkTheme()
 * reads. Resolving custom colors from isSystemInDarkTheme() therefore ignores the
 * override on those devices. Robolectric's default configuration is not night mode, so
 * isSystemInDarkTheme() returns false here regardless of the MaterialTheme supplied,
 * which reproduces that divergence.
 *
 * onPrimaryFixed carries the raw light/dark on-container palette color (no desaturation
 * or harmonization), so it directly reveals which side of the palette was selected.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29])
class MedicationGroupColorSchemeDarkResolutionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun followsResolvedDarkTheme_evenWhenSystemConfigIsLight() {
        var resolved: Color? = null
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                resolved = rememberMedicationGroupColorScheme(
                    colorKey = MedicationGroupColorKey.ROSE,
                ).onPrimaryFixed
            }
        }
        composeRule.runOnIdle {
            assertEquals(
                MedicationGroupPalettes.getValue(MedicationGroupColorKey.ROSE).darkOnContainer,
                resolved,
            )
        }
    }

    @Test
    fun followsResolvedLightTheme_whenSystemConfigIsLight() {
        var resolved: Color? = null
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                resolved = rememberMedicationGroupColorScheme(
                    colorKey = MedicationGroupColorKey.ROSE,
                ).onPrimaryFixed
            }
        }
        composeRule.runOnIdle {
            assertEquals(
                MedicationGroupPalettes.getValue(MedicationGroupColorKey.ROSE).lightOnContainer,
                resolved,
            )
        }
    }
}
