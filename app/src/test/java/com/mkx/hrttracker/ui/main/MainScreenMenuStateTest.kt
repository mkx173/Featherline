package com.mkx.hrttracker.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenMenuStateTest {
    @Test
    fun selectingDisplayUnit_replacesOptionsMenuWithDisplayUnitMenu() {
        val nextState = reduceHomeMoreOptionsMenuState(
            state = HomeMoreOptionsMenuState.OPTIONS,
            action = HomeMoreOptionsMenuAction.OPEN_DISPLAY_UNIT,
        )

        assertEquals(HomeMoreOptionsMenuState.DISPLAY_UNIT, nextState)
        assertEquals(
            HomeMoreOptionsMenuExpansion(
                optionsExpanded = false,
                displayUnitExpanded = true,
            ),
            nextState.toHomeMoreOptionsMenuExpansion(),
        )
    }

    @Test
    fun dismissingDisplayUnitMenu_closesAllMenus() {
        val nextState = reduceHomeMoreOptionsMenuState(
            state = HomeMoreOptionsMenuState.DISPLAY_UNIT,
            action = HomeMoreOptionsMenuAction.DISMISS,
        )

        assertEquals(HomeMoreOptionsMenuState.CLOSED, nextState)
        assertEquals(
            HomeMoreOptionsMenuExpansion(
                optionsExpanded = false,
                displayUnitExpanded = false,
            ),
            nextState.toHomeMoreOptionsMenuExpansion(),
        )
    }
}
