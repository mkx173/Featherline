package com.mkx.hrttracker.ui.navigation

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [navigationSuiteTypeFor] must derive the bar layout from the dimensions it is *handed* this frame.
 *
 * The production bug it guards against: on API <= 31 the stock window-metrics size lags one rotation
 * behind, so a phone in landscape kept the compact bar and the next portrait showed the medium bar.
 * Feeding the fresh configuration dimensions, a phone-portrait window must resolve to the compact
 * bar and a phone-landscape window to the medium bar — in the *same* call, with no carry-over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NavigationSuiteTypeTest {
    @Test
    fun phonePortraitUsesCompactBar() {
        assertEquals(
            NavigationSuiteType.ShortNavigationBarCompact,
            navigationSuiteTypeFor(widthDp = 411, heightDp = 891, posture = Posture()),
        )
    }

    @Test
    fun phoneLandscapeUsesMediumBar() {
        assertEquals(
            NavigationSuiteType.ShortNavigationBarMedium,
            navigationSuiteTypeFor(widthDp = 891, heightDp = 411, posture = Posture()),
        )
    }

    @Test
    fun rotatingBackAndForthTracksTheCurrentDimensionsNotThePrevious() {
        // The off-by-one signature: each result must match the dimensions of *this* call, never the
        // orientation that preceded it.
        val portrait = navigationSuiteTypeFor(widthDp = 411, heightDp = 891, posture = Posture())
        val landscape = navigationSuiteTypeFor(widthDp = 891, heightDp = 411, posture = Posture())
        val portraitAgain =
            navigationSuiteTypeFor(widthDp = 411, heightDp = 891, posture = Posture())

        assertEquals(NavigationSuiteType.ShortNavigationBarCompact, portrait)
        assertEquals(NavigationSuiteType.ShortNavigationBarMedium, landscape)
        assertEquals(NavigationSuiteType.ShortNavigationBarCompact, portraitAgain)
    }
}
