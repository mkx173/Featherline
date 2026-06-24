package com.mkx.hrttracker.model.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCardLayoutTest {

    @Test
    fun `decode of empty input yields default order and no hidden`() {
        val layout = HomeCardLayout.decode(orderNames = emptyList(), hiddenNames = emptyList())
        assertEquals(DEFAULT_HOME_CARD_ORDER, layout.order)
        assertEquals(emptySet<HomeCardType>(), layout.hidden)
    }

    @Test
    fun `decode preserves stored order and hidden set round-trip`() {
        val storedOrder = listOf("TIMELINE", "E2_CHART", "E2_HERO", "ANTIANDROGEN", "LOW_STOCK")
        val layout = HomeCardLayout.decode(storedOrder, listOf("E2_CHART", "TIMELINE"))
        assertEquals(
            listOf(
                HomeCardType.TIMELINE,
                HomeCardType.E2_CHART,
                HomeCardType.E2_HERO,
                HomeCardType.ANTIANDROGEN,
                HomeCardType.LOW_STOCK,
            ),
            layout.order,
        )
        assertEquals(setOf(HomeCardType.E2_CHART, HomeCardType.TIMELINE), layout.hidden)
    }

    @Test
    fun `decode appends enum values missing from stored order in default position`() {
        // Only two of five stored; the other three must be appended in DEFAULT order.
        val layout = HomeCardLayout.decode(listOf("TIMELINE", "E2_HERO"), emptyList())
        assertEquals(
            listOf(
                HomeCardType.TIMELINE,
                HomeCardType.E2_HERO,
                HomeCardType.LOW_STOCK,
                HomeCardType.E2_CHART,
                HomeCardType.ANTIANDROGEN,
            ),
            layout.order,
        )
    }

    @Test
    fun `decode ignores unknown names in order and hidden`() {
        val layout = HomeCardLayout.decode(
            orderNames = listOf("E2_HERO", "GHOST_CARD", "LOW_STOCK"),
            hiddenNames = listOf("LOW_STOCK", "NOT_A_CARD"),
        )
        // Unknown order name dropped, remaining defaults appended.
        assertEquals(
            listOf(
                HomeCardType.E2_HERO,
                HomeCardType.LOW_STOCK,
                HomeCardType.E2_CHART,
                HomeCardType.ANTIANDROGEN,
                HomeCardType.TIMELINE,
            ),
            layout.order,
        )
        assertEquals(setOf(HomeCardType.LOW_STOCK), layout.hidden)
    }

    @Test
    fun `decode de-dupes duplicate order entries by first occurrence`() {
        val layout = HomeCardLayout.decode(
            orderNames = listOf("E2_HERO", "E2_HERO", "LOW_STOCK", "LOW_STOCK"),
            hiddenNames = emptyList(),
        )
        assertEquals(
            listOf(
                HomeCardType.E2_HERO,
                HomeCardType.LOW_STOCK,
                HomeCardType.E2_CHART,
                HomeCardType.ANTIANDROGEN,
                HomeCardType.TIMELINE,
            ),
            layout.order,
        )
    }
}
