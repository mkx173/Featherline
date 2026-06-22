package com.mkx.hrttracker.ui.navigation

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass

/**
 * The [NavigationSuiteType] for the current window, derived from the configuration's reported size
 * rather than [currentWindowAdaptiveInfoV2]'s window-metrics size.
 *
 * [currentWindowAdaptiveInfoV2] reads `LocalWindowInfo.containerSize`, which Compose recomputes
 * from `WindowMetricsCalculator.computeCurrentWindowMetrics()` inside its `onConfigurationChanged`
 * callback. On API <= 31 those window metrics still report the *pre-rotation* bounds at callback
 * time, so when [com.mkx.hrttracker.MainActivity] handles `orientation|screenSize` in place (no
 * recreate) the size class lags one rotation behind: landscape keeps the compact bar and the
 * following portrait shows the medium bar, intermittently corrected by a later layout pass.
 *
 * [LocalConfiguration] is updated with the fresh configuration in that same callback, so its
 * `screenWidthDp`/`screenHeightDp` are correct on every API level. Posture is still taken from
 * [currentWindowAdaptiveInfoV2] — tabletop detection comes from folding features, not the laggy
 * window metrics.
 */
@Composable
fun rememberNavigationSuiteType(): NavigationSuiteType {
    val configuration = LocalConfiguration.current
    val posture = currentWindowAdaptiveInfoV2().windowPosture
    return navigationSuiteTypeFor(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
        posture = posture,
    )
}

/**
 * Pure mapping from window dimensions + [posture] to a [NavigationSuiteType], delegating the
 * breakpoint decision to [NavigationSuiteScaffoldDefaults.navigationSuiteType].
 *
 * [WindowSizeClass.BREAKPOINTS_V2] uses the same width-V2 / height-V1 breakpoints that
 * [currentWindowAdaptiveInfoV2] computes internally, so feeding it the configuration dimensions
 * yields the same size class the stock path would — only without the API <= 31 rotation lag.
 */
internal fun navigationSuiteTypeFor(
    widthDp: Int,
    heightDp: Int,
    posture: Posture,
): NavigationSuiteType =
    NavigationSuiteScaffoldDefaults.navigationSuiteType(
        WindowAdaptiveInfo(
            windowSizeClass =
                WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(
                    widthDp = widthDp.toFloat(),
                    heightDp = heightDp.toFloat(),
                ),
            windowPosture = posture,
        )
    )
