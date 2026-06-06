package com.mkx.hrttracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedStockTest {

    @Test
    fun poolStockSubtractsPerDoseUntilDepleted() {
        val state = SimulatedStock(open = 5.0, sealed = 0.0, containerCapacity = 0.0, isContainer = false)

        val fulfilled = simulateNDoses(state = state, n = 5, perDose = 1.0)

        assertEquals(5, fulfilled)
    }

    @Test
    fun poolStockStopsAtZero() {
        val state = SimulatedStock(open = 1.0, sealed = 0.0, containerCapacity = 0.0, isContainer = false)

        val fulfilled = simulateNDoses(state = state, n = 3, perDose = 1.0)

        assertEquals(1, fulfilled)
    }

    @Test
    fun containerCarriesOpenPartialIntoFreshUnitAllowingAnExtraDose() {
        // Exact-split: the first 0.7 dose consumes the 0.5 open plus 0.2 from a
        // fresh unit, leaving 1.0 - 0.2 = 0.8 (the 0.5 dreg is carried, not
        // discarded). That carried volume lets the second 0.7 dose still be
        // fulfilled from the open vial, where crack-and-discard would have left
        // only 0.3 and stopped at one dose.
        val state = SimulatedStock(open = 0.5, sealed = 1.0, containerCapacity = 1.0, isContainer = true)

        val fulfilled = simulateNDoses(state = state, n = 2, perDose = 0.7)

        assertEquals(2, fulfilled)
    }

    @Test
    fun containerReusesOpenUntilItCannotSatisfyDose() {
        val state = SimulatedStock(open = 5.0, sealed = 0.0, containerCapacity = 5.0, isContainer = true)

        val fulfilled = simulateNDoses(state = state, n = 5, perDose = 1.0)

        assertEquals(5, fulfilled)
    }

    @Test
    fun containerReturnsZeroWhenNeitherOpenNorSealedSatisfies() {
        val state = SimulatedStock(open = 0.0, sealed = 0.0, containerCapacity = 1.0, isContainer = true)

        val fulfilled = simulateNDoses(state = state, n = 2, perDose = 0.5)

        assertEquals(0, fulfilled)
    }

    @Test
    fun applyReturnsNullOnPoolStockout() {
        val state = SimulatedStock(open = 0.5, sealed = 0.0, containerCapacity = 0.0, isContainer = false)

        assertTrue(state.applyDose(perDose = 1.0) == null)
    }

    @Test
    fun applyReturnsNullWhenDoseExceedsFreshContainerCapacity() {
        val state = SimulatedStock(open = 0.0, sealed = 1.0, containerCapacity = 0.5, isContainer = true)

        assertNull(state.applyDose(perDose = 0.7))
    }

    @Test
    fun containerDoseThatExceedsOpenCarriesDregIntoFreshContainer() {
        // Exact-split: 0.1 open + 0.15 from a fresh 10 mL container fulfils the
        // 0.25 dose, leaving 10 - 0.15 = 9.85 (the 0.1 dreg is carried, not
        // discarded, which would leave 9.75).
        val state = SimulatedStock(open = 0.1, sealed = 1.0, containerCapacity = 10.0, isContainer = true)

        val next = requireNotNull(state.applyDose(perDose = 0.25))

        assertEquals(9.85, next.open, 1e-9)
        assertEquals(0.0, next.sealed, 1e-9)
    }
}
