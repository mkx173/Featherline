package com.mkx.hrttracker.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
    fun sharedAxisXEnterOffset_movesForwardTargetsInFromTrailingEdge() {
        assertEquals(
            30,
            sharedAxisXEnterOffset(
                slideDistancePx = 30,
                forward = true,
                layoutDirection = LayoutDirection.Ltr,
            )
        )
    }

    @Test
    fun sharedAxisXSlideDistancePx_matchesMaterialDefaultAtMdpi() {
        assertEquals(30, sharedAxisXSlideDistancePx(Density(density = 1f)))
    }

    @Test
    fun sharedAxisXExitOffset_movesForwardInitialContentTowardLeadingEdge() {
        assertEquals(
            -30,
            sharedAxisXExitOffset(
                slideDistancePx = 30,
                forward = true,
                layoutDirection = LayoutDirection.Ltr,
            )
        )
    }

    @Test
    fun sharedAxisXOffsets_reverseForBackwardNavigation() {
        assertEquals(
            -30,
            sharedAxisXEnterOffset(
                slideDistancePx = 30,
                forward = false,
                layoutDirection = LayoutDirection.Ltr,
            )
        )
        assertEquals(
            30,
            sharedAxisXExitOffset(
                slideDistancePx = 30,
                forward = false,
                layoutDirection = LayoutDirection.Ltr,
            )
        )
    }

    @Test
    fun sharedAxisXOffsets_mirrorForRtlNavigation() {
        assertEquals(
            -30,
            sharedAxisXEnterOffset(
                slideDistancePx = 30,
                forward = true,
                layoutDirection = LayoutDirection.Rtl,
            )
        )
        assertEquals(
            30,
            sharedAxisXExitOffset(
                slideDistancePx = 30,
                forward = true,
                layoutDirection = LayoutDirection.Rtl,
            )
        )
    }

    // Regression guards for the three medicine routes: they were missing from
    // navigationRouteContextFor so transitions to/from them resolved to NONE
    // (no animation). Each test uses the full route template, which is what
    // Compose Navigation supplies as destination.route.
    @Test
    fun resolveNavigationMotionPattern_returnsNestedForward_for_medicines_navigation() {
        assertEquals(
            NavigationMotionPattern.NESTED_FORWARD,
            resolveNavigationMotionPattern(
                initialRoute = Screen.Plan.route,
                targetRoute = Screen.Medicines.route,
                isPop = false,
            )
        )
    }

    @Test
    fun resolveNavigationMotionPattern_returnsNestedForward_for_home_medicines_navigation() {
        assertEquals(
            NavigationMotionPattern.NESTED_FORWARD,
            resolveNavigationMotionPattern(
                initialRoute = Screen.Main.route,
                targetRoute = Screen.Medicines.route,
                isPop = false,
                targetTopLevelParentRoute = Screen.Main.route,
            )
        )
    }

    @Test
    fun resolveNavigationMotionPattern_returnsNestedForward_for_medicineDetail_navigation() {
        assertEquals(
            NavigationMotionPattern.NESTED_FORWARD,
            resolveNavigationMotionPattern(
                initialRoute = Screen.Medicines.route,
                targetRoute = Screen.MedicineDetail.route,
                isPop = false,
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
