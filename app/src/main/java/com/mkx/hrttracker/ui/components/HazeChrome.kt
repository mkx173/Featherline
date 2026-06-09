@file:OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3Api::class)

package com.mkx.hrttracker.ui.components

import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

val LocalHazeBlurEnabled = staticCompositionLocalOf { isHazeBlurSupported() }
val LocalChromeHazeState = staticCompositionLocalOf<HazeState?> { null }

internal const val TopAppBarScrolledOverlapThreshold = 0.01f

@Composable
fun rememberChromeHazeState(): HazeState = rememberHazeState()

fun isHazeBlurSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    return sdkInt >= Build.VERSION_CODES.S
}

fun effectiveHazeBlurEnabled(
    preferenceEnabled: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean {
    return preferenceEnabled && isHazeBlurSupported(sdkInt)
}

@Composable
fun Modifier.hazeSourceArea(state: HazeState?): Modifier {
    return if (!LocalHazeBlurEnabled.current || state == null) this else hazeSource(state)
}

@Composable
fun Modifier.hazeChrome(
    state: HazeState? = LocalChromeHazeState.current,
    enabled: Boolean = true,
): Modifier {
    if (!LocalHazeBlurEnabled.current || !enabled || state == null) return this

    return hazeEffect(
        state = state,
        style = HazeMaterials.thin(),
    )
}

@Composable
fun topAppBarHazeEnabled(scrollBehavior: TopAppBarScrollBehavior): Boolean {
    return remember(scrollBehavior) {
        derivedStateOf {
            scrollBehavior.state.overlappedFraction > TopAppBarScrolledOverlapThreshold
        }
    }.value
}

@Composable
fun hazeTopAppBarColors(enabled: Boolean = LocalHazeBlurEnabled.current): TopAppBarColors {
    if (!enabled) return TopAppBarDefaults.topAppBarColors()

    return TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
    )
}

@Composable
fun hazeNavigationSuiteColors(
    enabled: Boolean = LocalHazeBlurEnabled.current,
): NavigationSuiteColors {
    if (!enabled) return NavigationSuiteDefaults.colors()

    return NavigationSuiteDefaults.colors(
        shortNavigationBarContainerColor = Color.Transparent,
        navigationBarContainerColor = Color.Transparent,
    )
}
