package com.mkx.hrttracker.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TopLevelNavigationItemsTest {

    @Test
    fun `every topLevelNavigationItems entry resolves via Screen_topLevelScreenForRoute`() {
        topLevelNavigationItems.forEach { item ->
            val resolved = Screen.topLevelScreenForRoute(item.screen.route)
            assertNotNull(
                "Route ${item.screen.route} from topLevelNavigationItems must resolve " +
                        "via Screen.topLevelScreenForRoute",
                resolved,
            )
            assertEquals(
                "Route ${item.screen.route} must resolve to the same Screen instance",
                item.screen,
                resolved,
            )
        }
    }

    @Test
    fun `topLevelNavigationItems is non-empty`() {
        assertEquals(
            "Expected three top-level destinations (Main, Plan, Settings)",
            3,
            topLevelNavigationItems.size,
        )
    }
}
