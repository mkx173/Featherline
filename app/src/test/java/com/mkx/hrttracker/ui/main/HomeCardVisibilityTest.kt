package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.home.HomeCardLayout
import com.mkx.hrttracker.model.home.HomeCardType
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCardVisibilityTest {

    private fun stateWith(
        layout: HomeCardLayout,
        stockWarnings: List<MedicineStockProjection> = emptyList(),
        antiandrogenCards: List<MainAntiandrogenCardUiState> = emptyList(),
        homeAnchor: com.mkx.hrttracker.ui.journal.AnchorRowUiState? = null,
    ): MainUiState = MainUiState(
        homeCardLayout = layout,
        stockWarnings = stockWarnings,
        antiandrogenCards = antiandrogenCards,
        homeAnchor = homeAnchor,
    )

    @Test
    fun `default layout with no data shows only the always-on cards in order`() {
        val visible = visibleHomeCards(HomeCardLayout(), stateWith(HomeCardLayout()))
        // LOW_STOCK (no warnings), ANTIANDROGEN (no cards), TIMELINE (no anchor) drop out.
        assertEquals(listOf(HomeCardType.E2_HERO, HomeCardType.E2_CHART), visible)
    }

    @Test
    fun `visible cards follow the configured order`() {
        val layout = HomeCardLayout(
            order = listOf(
                HomeCardType.E2_CHART,
                HomeCardType.E2_HERO,
                HomeCardType.TIMELINE,
                HomeCardType.ANTIANDROGEN,
                HomeCardType.LOW_STOCK,
            ),
            hidden = emptySet(),
        )
        val visible = visibleHomeCards(
            layout,
            stateWith(layout, homeAnchor = mockk()),
        )
        // E2_CHART, E2_HERO always on; TIMELINE on (anchor present); ANTIANDROGEN/LOW_STOCK off.
        assertEquals(
            listOf(HomeCardType.E2_CHART, HomeCardType.E2_HERO, HomeCardType.TIMELINE),
            visible,
        )
    }

    @Test
    fun `hidden cards are excluded even when they have data`() {
        val layout = HomeCardLayout(hidden = setOf(HomeCardType.E2_HERO))
        val visible = visibleHomeCards(layout, stateWith(layout))
        assertEquals(listOf(HomeCardType.E2_CHART), visible)
    }
}
