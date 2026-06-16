package com.mkx.hrttracker.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry

// Nested Shared Axis duration. Matches MaterialSharedAxis motionDurationLong1; tune
// together with slide distance.
internal const val sharedAxisXTransitionDurationMillis = 300

// Nested Shared Axis X slide distance. Matches R.dimen.mtrl_transition_shared_axis_slide_distance.
private val sharedAxisXSlideDistance: Dp = 30.dp

// FadeThroughProvider threshold. Outgoing content fades during 0..threshold;
// incoming fades during threshold..1.
internal const val navigationFadeThroughProgressThreshold = 0.35f

internal val sharedAxisXEnterFadeEasing = Easing { fraction ->
    navigationFadeThroughAppearingAlphaProgress(fraction)
}

internal val sharedAxisXExitFadeEasing = Easing { fraction ->
    navigationFadeThroughDisappearingAlphaProgress(fraction)
}

internal val sharedAxisXSlideEasing: Easing = FastOutSlowInEasing

private data class NavigationRouteContext(
    val topLevelScreen: Screen,
)

internal enum class NavigationMotionPattern {
    NONE,
    NESTED_FORWARD,
    NESTED_BACKWARD,
}

internal fun resolveNavigationMotionPattern(
    initialRoute: String?,
    targetRoute: String?,
    isPop: Boolean,
    initialTopLevelParentRoute: String? = null,
    targetTopLevelParentRoute: String? = null,
): NavigationMotionPattern {
    val normalizedInitialRoute =
        normalizeNavigationRoute(initialRoute) ?: return NavigationMotionPattern.NONE
    val normalizedTargetRoute =
        normalizeNavigationRoute(targetRoute) ?: return NavigationMotionPattern.NONE
    if (normalizedInitialRoute == normalizedTargetRoute) {
        return NavigationMotionPattern.NONE
    }

    val initialContext = navigationRouteContextFor(
        route = normalizedInitialRoute,
        topLevelParentRoute = initialTopLevelParentRoute,
    )
        ?: return NavigationMotionPattern.NONE
    val targetContext = navigationRouteContextFor(
        route = normalizedTargetRoute,
        topLevelParentRoute = targetTopLevelParentRoute,
    )
        ?: return NavigationMotionPattern.NONE

    if (initialContext.topLevelScreen != targetContext.topLevelScreen) {
        return NavigationMotionPattern.NONE
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
            initialTopLevelParentRoute = initialState.topLevelParentRoute,
            targetTopLevelParentRoute = targetState.topLevelParentRoute,
        )
    ) {
        NavigationMotionPattern.NESTED_FORWARD -> sharedAxisXEnterTransition(
            density = density,
            layoutDirection = layoutDirection,
            forward = true,
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
            initialTopLevelParentRoute = initialState.topLevelParentRoute,
            targetTopLevelParentRoute = targetState.topLevelParentRoute,
        )
    ) {
        NavigationMotionPattern.NESTED_FORWARD -> sharedAxisXExitTransition(
            density = density,
            layoutDirection = layoutDirection,
            forward = true,
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
            initialTopLevelParentRoute = initialState.topLevelParentRoute,
            targetTopLevelParentRoute = targetState.topLevelParentRoute,
        )
    ) {
        NavigationMotionPattern.NESTED_BACKWARD -> sharedAxisXEnterTransition(
            density = density,
            layoutDirection = layoutDirection,
            forward = false,
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
            initialTopLevelParentRoute = initialState.topLevelParentRoute,
            targetTopLevelParentRoute = targetState.topLevelParentRoute,
        )
    ) {
        NavigationMotionPattern.NESTED_BACKWARD -> sharedAxisXExitTransition(
            density = density,
            layoutDirection = layoutDirection,
            forward = false,
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

internal fun sharedAxisXEnterTransition(
    density: Density,
    layoutDirection: LayoutDirection,
    forward: Boolean,
): EnterTransition {
    return nestedEnterTransition(
        forward = forward,
        slideDistancePx = sharedAxisXSlideDistancePx(density),
        layoutDirection = layoutDirection,
    )
}

internal fun sharedAxisXExitTransition(
    density: Density,
    layoutDirection: LayoutDirection,
    forward: Boolean,
): ExitTransition {
    return nestedExitTransition(
        forward = forward,
        slideDistancePx = sharedAxisXSlideDistancePx(density),
        layoutDirection = layoutDirection,
    )
}

private val NavBackStackEntry.topLevelParentRoute: String?
    get() = arguments?.getString(TOP_LEVEL_PARENT_ARG)

private fun navigationRouteContextFor(
    route: String,
    topLevelParentRoute: String? = null,
): NavigationRouteContext? {
    fun childContext(defaultTopLevelScreen: Screen): NavigationRouteContext {
        return NavigationRouteContext(
            topLevelScreen = Screen.topLevelScreenForRoute(topLevelParentRoute)
                ?: defaultTopLevelScreen,
        )
    }

    return when (route) {
        Screen.Main.route -> NavigationRouteContext(topLevelScreen = Screen.Main)
        Screen.Plan.route -> NavigationRouteContext(topLevelScreen = Screen.Plan)
        Screen.PlanBatchAdd.baseRoute -> childContext(defaultTopLevelScreen = Screen.Plan)
        Screen.PlanArchivedGroups.baseRoute -> childContext(defaultTopLevelScreen = Screen.Plan)
        Screen.History.baseRoute -> childContext(defaultTopLevelScreen = Screen.Plan)
        Screen.Journal.route -> NavigationRouteContext(topLevelScreen = Screen.Journal)
        Screen.JournalMilestones.baseRoute -> childContext(defaultTopLevelScreen = Screen.Journal)
        Screen.JournalAllNotes.baseRoute -> childContext(defaultTopLevelScreen = Screen.Journal)
        Screen.Settings.route -> NavigationRouteContext(topLevelScreen = Screen.Settings)
        Screen.EditMedicationGroup.baseRoute -> childContext(defaultTopLevelScreen = Screen.Plan)
        Screen.Medicines.motionRoute -> childContext(defaultTopLevelScreen = Screen.Plan)
        Screen.MedicineDetail.motionRoute -> childContext(defaultTopLevelScreen = Screen.Plan)
        Screen.SettingsCalibration.baseRoute -> childContext(defaultTopLevelScreen = Screen.Settings)
        Screen.SettingsCalibrationUnits.baseRoute -> childContext(defaultTopLevelScreen = Screen.Settings)
        Screen.SettingsCalibrationEntry.baseRoute -> childContext(defaultTopLevelScreen = Screen.Settings)
        else -> null
    }
}

// Child pages within one section use directional motion to reinforce forward/back navigation.
private fun nestedEnterTransition(
    forward: Boolean,
    slideDistancePx: Int,
    layoutDirection: LayoutDirection,
): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = sharedAxisXTransitionDurationMillis,
            easing = sharedAxisXSlideEasing,
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
            durationMillis = sharedAxisXTransitionDurationMillis,
            easing = sharedAxisXEnterFadeEasing,
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
            durationMillis = sharedAxisXTransitionDurationMillis,
            easing = sharedAxisXSlideEasing,
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
            durationMillis = sharedAxisXTransitionDurationMillis,
            easing = sharedAxisXExitFadeEasing,
        )
    )
}
