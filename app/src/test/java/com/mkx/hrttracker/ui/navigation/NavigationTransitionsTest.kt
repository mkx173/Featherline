package com.mkx.hrttracker.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTransitionsTest {
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
