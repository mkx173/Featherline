package com.mkx.hrttracker.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry

// Bottom-tab Fade Through duration. Matches Material's motionDurationLong1 default;
// raise/lower to tune section-swap speed.
private const val topLevelTransitionDurationMillis = 300

// Bottom-tab Fade Through secondary scale. Lower values make incoming sections feel
// farther away before settling.
private const val topLevelFadeThroughInitialScale = 0.92f

// Nested Shared Axis duration. Matches MaterialSharedAxis motionDurationLong1; tune
// together with slide distance.
private const val nestedTransitionDurationMillis = 300

// Nested Shared Axis X slide distance. Matches R.dimen.mtrl_transition_shared_axis_slide_distance.
private val sharedAxisXSlideDistance: Dp = 30.dp

// FadeThroughProvider threshold. Outgoing content fades during 0..threshold;
// incoming fades during threshold..1.
internal const val navigationFadeThroughProgressThreshold = 0.35f

private val navigationFadeThroughEnterEasing = Easing { fraction ->
    navigationFadeThroughAppearingAlphaProgress(fraction)
}

private val navigationFadeThroughExitEasing = Easing { fraction ->
    navigationFadeThroughDisappearingAlphaProgress(fraction)
}

private data class NavigationRouteContext(
    val topLevelScreen: Screen,
)

internal enum class NavigationMotionPattern {
    NONE,
    TOP_LEVEL,
    NESTED_FORWARD,
    NESTED_BACKWARD,
}

internal fun resolveNavigationMotionPattern(
    initialRoute: String?,
    targetRoute: String?,
    isPop: Boolean,
): NavigationMotionPattern {
    val normalizedInitialRoute = normalizeNavigationRoute(initialRoute) ?: return NavigationMotionPattern.NONE
    val normalizedTargetRoute = normalizeNavigationRoute(targetRoute) ?: return NavigationMotionPattern.NONE
    if (normalizedInitialRoute == normalizedTargetRoute) {
        return NavigationMotionPattern.NONE
    }

    val initialContext = navigationRouteContextFor(normalizedInitialRoute)
        ?: return NavigationMotionPattern.NONE
    val targetContext = navigationRouteContextFor(normalizedTargetRoute)
        ?: return NavigationMotionPattern.NONE

    if (initialContext.topLevelScreen != targetContext.topLevelScreen) {
        return NavigationMotionPattern.TOP_LEVEL
    }

    return if (isPop) {
        NavigationMotionPattern.NESTED_BACKWARD
    } else {
        NavigationMotionPattern.NESTED_FORWARD
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hrtNavHostEnterTransition(
    density: Density,
    layoutDirection: LayoutDirection,
): EnterTransition {
    return when (
        resolveNavigationMotionPattern(
            initialRoute = initialState.destination.route,
            targetRoute = targetState.destination.route,
            isPop = false,
        )
    ) {
        NavigationMotionPattern.TOP_LEVEL -> topLevelEnterTransition()
        NavigationMotionPattern.NESTED_FORWARD -> nestedEnterTransition(
            forward = true,
            slideDistancePx = sharedAxisXSlideDistancePx(density),
            layoutDirection = layoutDirection,
        )
        else -> EnterTransition.None
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hrtNavHostExitTransition(
    density: Density,
    layoutDirection: LayoutDirection,
): ExitTransition {
    return when (
        resolveNavigationMotionPattern(
            initialRoute = initialState.destination.route,
            targetRoute = targetState.destination.route,
            isPop = false,
        )
    ) {
        NavigationMotionPattern.TOP_LEVEL -> topLevelExitTransition()
        NavigationMotionPattern.NESTED_FORWARD -> nestedExitTransition(
            forward = true,
            slideDistancePx = sharedAxisXSlideDistancePx(density),
            layoutDirection = layoutDirection,
        )
        else -> ExitTransition.None
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hrtNavHostPopEnterTransition(
    density: Density,
    layoutDirection: LayoutDirection,
): EnterTransition {
    return when (
        resolveNavigationMotionPattern(
            initialRoute = initialState.destination.route,
            targetRoute = targetState.destination.route,
            isPop = true,
        )
    ) {
        NavigationMotionPattern.TOP_LEVEL -> topLevelEnterTransition()
        NavigationMotionPattern.NESTED_BACKWARD -> nestedEnterTransition(
            forward = false,
            slideDistancePx = sharedAxisXSlideDistancePx(density),
            layoutDirection = layoutDirection,
        )
        else -> EnterTransition.None
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hrtNavHostPopExitTransition(
    density: Density,
    layoutDirection: LayoutDirection,
): ExitTransition {
    return when (
        resolveNavigationMotionPattern(
            initialRoute = initialState.destination.route,
            targetRoute = targetState.destination.route,
            isPop = true,
        )
    ) {
        NavigationMotionPattern.TOP_LEVEL -> topLevelExitTransition()
        NavigationMotionPattern.NESTED_BACKWARD -> nestedExitTransition(
            forward = false,
            slideDistancePx = sharedAxisXSlideDistancePx(density),
            layoutDirection = layoutDirection,
        )
        else -> ExitTransition.None
    }
}

internal fun normalizeNavigationRoute(route: String?): String? {
    return route?.substringBefore("?")
}

// Mirrors MaterialFadeThrough's alpha split: outgoing finishes before the
// threshold, incoming starts after it, both using one shared motion progress.
internal fun fadeThroughAppearingAlphaProgress(progress: Float): Float {
    val coercedProgress = progress.coerceIn(0f, 1f)
    return ((coercedProgress - navigationFadeThroughProgressThreshold) /
        (1f - navigationFadeThroughProgressThreshold)).coerceIn(0f, 1f)
}

internal fun fadeThroughDisappearingAlphaProgress(progress: Float): Float {
    val coercedProgress = progress.coerceIn(0f, 1f)
    return (coercedProgress / navigationFadeThroughProgressThreshold).coerceIn(0f, 1f)
}

internal fun navigationFadeThroughAppearingAlphaProgress(fraction: Float): Float {
    return fadeThroughAppearingAlphaProgress(FastOutSlowInEasing.transform(fraction))
}

internal fun navigationFadeThroughDisappearingAlphaProgress(fraction: Float): Float {
    return fadeThroughDisappearingAlphaProgress(FastOutSlowInEasing.transform(fraction))
}

internal fun sharedAxisXSlideDistancePx(density: Density): Int {
    return with(density) { sharedAxisXSlideDistance.roundToPx() }
}

// slideDistancePx is the density-resolved 30dp Material distance; forward mirrors
// MaterialSharedAxis' forward flag, where X-forward moves content left in LTR.
internal fun sharedAxisXEnterOffset(
    slideDistancePx: Int,
    forward: Boolean,
    layoutDirection: LayoutDirection,
): Int {
    val trailingEdgeSign = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    return if (forward) {
        trailingEdgeSign * slideDistancePx
    } else {
        -trailingEdgeSign * slideDistancePx
    }
}

// slideDistancePx is the density-resolved 30dp Material distance; forward mirrors
// MaterialSharedAxis' forward flag, where X-forward moves content left in LTR.
internal fun sharedAxisXExitOffset(
    slideDistancePx: Int,
    forward: Boolean,
    layoutDirection: LayoutDirection,
): Int {
    return -sharedAxisXEnterOffset(
        slideDistancePx = slideDistancePx,
        forward = forward,
        layoutDirection = layoutDirection,
    )
}

private fun navigationRouteContextFor(route: String): NavigationRouteContext? {
    return when (route) {
        Screen.Main.route -> NavigationRouteContext(topLevelScreen = Screen.Main)
        Screen.Plan.route -> NavigationRouteContext(topLevelScreen = Screen.Plan)
        Screen.PlanBatchAdd.baseRoute -> NavigationRouteContext(topLevelScreen = Screen.Plan)
        Screen.PlanArchivedGroups.baseRoute -> NavigationRouteContext(topLevelScreen = Screen.Plan)
        Screen.History.baseRoute -> NavigationRouteContext(topLevelScreen = Screen.Plan)
        Screen.Settings.route -> NavigationRouteContext(topLevelScreen = Screen.Settings)
        Screen.EditMedicationGroup.baseRoute -> NavigationRouteContext(topLevelScreen = Screen.Plan)
        Screen.SettingsCalibration.baseRoute -> NavigationRouteContext(topLevelScreen = Screen.Settings)
        Screen.SettingsCalibrationUnits.baseRoute -> NavigationRouteContext(topLevelScreen = Screen.Settings)
        Screen.SettingsCalibrationEntry.baseRoute -> NavigationRouteContext(topLevelScreen = Screen.Settings)
        else -> null
    }
}

// Switching sections should not imply a spatial relationship, so use a fade through.
private fun topLevelEnterTransition(): EnterTransition {
    return fadeIn(
        animationSpec = tween(
            durationMillis = topLevelTransitionDurationMillis,
            easing = navigationFadeThroughEnterEasing,
        )
    ) + scaleIn(
        initialScale = topLevelFadeThroughInitialScale,
        animationSpec = tween(
            durationMillis = topLevelTransitionDurationMillis,
            easing = FastOutSlowInEasing,
        )
    )
}

private fun topLevelExitTransition(): ExitTransition {
    return fadeOut(
        animationSpec = tween(
            durationMillis = topLevelTransitionDurationMillis,
            easing = navigationFadeThroughExitEasing,
        )
    )
}

// Child pages within one section use directional motion to reinforce forward/back navigation.
private fun nestedEnterTransition(
    forward: Boolean,
    slideDistancePx: Int,
    layoutDirection: LayoutDirection,
): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = nestedTransitionDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        initialOffsetX = {
            sharedAxisXEnterOffset(
                slideDistancePx = slideDistancePx,
                forward = forward,
                layoutDirection = layoutDirection,
            )
        }
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = nestedTransitionDurationMillis,
            easing = navigationFadeThroughEnterEasing,
        )
    )
}

private fun nestedExitTransition(
    forward: Boolean,
    slideDistancePx: Int,
    layoutDirection: LayoutDirection,
): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = nestedTransitionDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        targetOffsetX = {
            sharedAxisXExitOffset(
                slideDistancePx = slideDistancePx,
                forward = forward,
                layoutDirection = layoutDirection,
            )
        }
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = nestedTransitionDurationMillis,
            easing = navigationFadeThroughExitEasing,
        )
    )
}
