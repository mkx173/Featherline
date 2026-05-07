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
import androidx.navigation.NavBackStackEntry
import kotlin.math.roundToInt

private const val topLevelTransitionDurationMillis = 300
private const val topLevelFadeThroughInitialScale = 0.92f
private const val nestedTransitionDurationMillis = 300
private const val nestedSlideDistanceFraction = 0.1f
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

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hrtNavHostEnterTransition(): EnterTransition {
    return when (
        resolveNavigationMotionPattern(
            initialRoute = initialState.destination.route,
            targetRoute = targetState.destination.route,
            isPop = false,
        )
    ) {
        NavigationMotionPattern.TOP_LEVEL -> topLevelEnterTransition()
        NavigationMotionPattern.NESTED_FORWARD -> nestedEnterTransition(forward = true)
        else -> EnterTransition.None
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hrtNavHostExitTransition(): ExitTransition {
    return when (
        resolveNavigationMotionPattern(
            initialRoute = initialState.destination.route,
            targetRoute = targetState.destination.route,
            isPop = false,
        )
    ) {
        NavigationMotionPattern.TOP_LEVEL -> topLevelExitTransition()
        NavigationMotionPattern.NESTED_FORWARD -> nestedExitTransition(forward = true)
        else -> ExitTransition.None
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hrtNavHostPopEnterTransition(): EnterTransition {
    return when (
        resolveNavigationMotionPattern(
            initialRoute = initialState.destination.route,
            targetRoute = targetState.destination.route,
            isPop = true,
        )
    ) {
        NavigationMotionPattern.TOP_LEVEL -> topLevelEnterTransition()
        NavigationMotionPattern.NESTED_BACKWARD -> nestedEnterTransition(forward = false)
        else -> EnterTransition.None
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hrtNavHostPopExitTransition(): ExitTransition {
    return when (
        resolveNavigationMotionPattern(
            initialRoute = initialState.destination.route,
            targetRoute = targetState.destination.route,
            isPop = true,
        )
    ) {
        NavigationMotionPattern.TOP_LEVEL -> topLevelExitTransition()
        NavigationMotionPattern.NESTED_BACKWARD -> nestedExitTransition(forward = false)
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
private fun nestedEnterTransition(forward: Boolean): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = nestedTransitionDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        initialOffsetX = { fullWidth ->
            val distance = (fullWidth * nestedSlideDistanceFraction).roundToInt()
            if (forward) distance else -distance
        }
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = nestedTransitionDurationMillis,
            easing = navigationFadeThroughEnterEasing,
        )
    )
}

private fun nestedExitTransition(forward: Boolean): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = nestedTransitionDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        targetOffsetX = { fullWidth ->
            val distance = (fullWidth * nestedSlideDistanceFraction).roundToInt()
            if (forward) -distance else distance
        }
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = nestedTransitionDurationMillis,
            easing = navigationFadeThroughExitEasing,
        )
    )
}
