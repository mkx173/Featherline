package com.mkx.hrttracker.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class HrtTrackerNavHostTest {
    @Test
    fun topLevelNavigationTapAction_returnsScrollToTop_for_active_top_level_route() {
        assertEquals(
            TopLevelNavigationTapAction.SCROLL_TO_TOP,
            topLevelNavigationTapAction(
                tappedScreen = Screen.Main,
                selectedBottomScreen = Screen.Main,
                currentRoute = Screen.Main.route,
            )
        )
    }

    @Test
    fun topLevelNavigationTapAction_returnsPopToTopLevel_for_child_of_selected_route() {
        assertEquals(
            TopLevelNavigationTapAction.POP_TO_TOP_LEVEL,
            topLevelNavigationTapAction(
                tappedScreen = Screen.Settings,
                selectedBottomScreen = Screen.Settings,
                currentRoute = Screen.SettingsCalibration.baseRoute,
            )
        )
    }

    @Test
    fun topLevelNavigationTapAction_returnsNavigate_for_different_top_level_route() {
        assertEquals(
            TopLevelNavigationTapAction.NAVIGATE,
            topLevelNavigationTapAction(
                tappedScreen = Screen.Settings,
                selectedBottomScreen = Screen.Plan,
                currentRoute = Screen.Plan.route,
            )
        )
    }

    @Test
    fun topLevelNavigationTapAction_returnsPopToTopLevel_for_plan_history_route() {
        assertEquals(
            TopLevelNavigationTapAction.POP_TO_TOP_LEVEL,
            topLevelNavigationTapAction(
                tappedScreen = Screen.Plan,
                selectedBottomScreen = Screen.Plan,
                currentRoute = Screen.History.route,
            )
        )
    }

    @Test
    fun topLevelNavigationTapAction_returnsPopToTopLevel_for_planBatchAdd_route() {
        assertEquals(
            TopLevelNavigationTapAction.POP_TO_TOP_LEVEL,
            topLevelNavigationTapAction(
                tappedScreen = Screen.Plan,
                selectedBottomScreen = Screen.Plan,
                currentRoute = Screen.PlanBatchAdd.route,
            )
        )
    }

    @Test
    fun topLevelNavigationTapAction_returnsPopToTopLevel_for_planArchivedGroups_route() {
        assertEquals(
            TopLevelNavigationTapAction.POP_TO_TOP_LEVEL,
            topLevelNavigationTapAction(
                tappedScreen = Screen.Plan,
                selectedBottomScreen = Screen.Plan,
                currentRoute = Screen.PlanArchivedGroups.route,
            )
        )
    }
}
