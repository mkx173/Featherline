package com.mkx.hrttracker.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTransitionsTest {
    @Test
    fun fadeThroughAlphaProgress_keepsIncomingTransparent_untilThreshold() {
        assertEquals(0f, fadeThroughAppearingAlphaProgress(0f), 0.0001f)
        assertEquals(
            0f,
            fadeThroughAppearingAlphaProgress(navigationFadeThroughProgressThreshold),
            0.0001f,
        )
    }

    @Test
    fun fadeThroughAlphaProgress_fadesIncomingAfterThreshold() {
        val halfwayThroughIncomingRange = navigationFadeThroughProgressThreshold +
            (1f - navigationFadeThroughProgressThreshold) / 2f

        assertEquals(
            0.5f,
            fadeThroughAppearingAlphaProgress(halfwayThroughIncomingRange),
            0.0001f,
        )
        assertEquals(1f, fadeThroughAppearingAlphaProgress(1f), 0.0001f)
    }

    @Test
    fun fadeThroughAlphaProgress_fadesOutgoingBeforeThreshold() {
        assertEquals(0f, fadeThroughDisappearingAlphaProgress(0f), 0.0001f)
        assertEquals(
            0.5f,
            fadeThroughDisappearingAlphaProgress(navigationFadeThroughProgressThreshold / 2f),
            0.0001f,
        )
        assertEquals(
            1f,
            fadeThroughDisappearingAlphaProgress(navigationFadeThroughProgressThreshold),
            0.0001f,
        )
        assertEquals(1f, fadeThroughDisappearingAlphaProgress(1f), 0.0001f)
    }

    @Test
    fun navigationFadeThroughAlphaProgress_appliesMotionEasingBeforeFadeSplit() {
        val fraction = 0.5f

        assertEquals(
            fadeThroughAppearingAlphaProgress(FastOutSlowInEasing.transform(fraction)),
            navigationFadeThroughAppearingAlphaProgress(fraction),
            0.0001f,
        )
        assertEquals(
            fadeThroughDisappearingAlphaProgress(FastOutSlowInEasing.transform(fraction)),
            navigationFadeThroughDisappearingAlphaProgress(fraction),
            0.0001f,
        )
    }

    @Test
    fun resolveNavigationMotionPattern_returnsTopLevel_for_top_level_section_switch() {
        assertEquals(
            NavigationMotionPattern.TOP_LEVEL,
            resolveNavigationMotionPattern(
                initialRoute = Screen.Main.route,
                targetRoute = Screen.Plan.route,
                isPop = false,
            )
        )
    }

    @Test
    fun resolveNavigationMotionPattern_returnsTopLevel_when_switching_from_plan_nested_route_to_settings() {
        assertEquals(
            NavigationMotionPattern.TOP_LEVEL,
            resolveNavigationMotionPattern(
                initialRoute = Screen.History.route,
                targetRoute = Screen.SettingsCalibration.createRoute(Screen.Settings.route),
                isPop = false,
            )
        )
    }

    @Test
    fun resolveNavigationMotionPattern_returnsNestedForward_for_plan_history_navigation() {
        assertEquals(
            NavigationMotionPattern.NESTED_FORWARD,
            resolveNavigationMotionPattern(
                initialRoute = Screen.Plan.route,
                targetRoute = Screen.History.createRoute(Screen.Plan.route),
                isPop = false,
            )
        )
    }

    @Test
    fun resolveNavigationMotionPattern_returnsNestedForward_for_planBatchAdd_navigation() {
        assertEquals(
            NavigationMotionPattern.NESTED_FORWARD,
            resolveNavigationMotionPattern(
                initialRoute = Screen.Plan.route,
                targetRoute = Screen.PlanBatchAdd.createRoute(Screen.Plan.route),
                isPop = false,
            )
        )
    }

    @Test
    fun resolveNavigationMotionPattern_returnsNestedForward_for_planArchivedGroups_navigation() {
        assertEquals(
            NavigationMotionPattern.NESTED_FORWARD,
            resolveNavigationMotionPattern(
                initialRoute = Screen.Plan.route,
                targetRoute = Screen.PlanArchivedGroups.createRoute(Screen.Plan.route),
                isPop = false,
            )
        )
    }

    @Test
    fun resolveNavigationMotionPattern_returnsNestedForward_for_in_section_navigation() {
        assertEquals(
            NavigationMotionPattern.NESTED_FORWARD,
            resolveNavigationMotionPattern(
                initialRoute = Screen.Settings.route,
                targetRoute = Screen.SettingsCalibration.createRoute(Screen.Settings.route),
                isPop = false,
            )
        )
    }

    @Test
    fun resolveNavigationMotionPattern_returnsNestedBackward_for_pop_within_section() {
        assertEquals(
            NavigationMotionPattern.NESTED_BACKWARD,
            resolveNavigationMotionPattern(
                initialRoute = Screen.SettingsCalibrationEntry.baseRoute,
                targetRoute = Screen.SettingsCalibration.baseRoute,
                isPop = true,
            )
        )
    }

    @Test
    fun resolveNavigationMotionPattern_returnsNone_for_unknown_destination() {
        assertEquals(
            NavigationMotionPattern.NONE,
            resolveNavigationMotionPattern(
                initialRoute = Screen.Main.route,
                targetRoute = "unknown_route",
                isPop = false,
            )
        )
    }
}
