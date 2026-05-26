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
    fun containerDiscardsOpenPartialWhenOpeningSealedUnit() {
        val state = SimulatedStock(open = 0.5, sealed = 1.0, containerCapacity = 1.0, isContainer = true)

        val fulfilled = simulateNDoses(state = state, n = 2, perDose = 0.7)

        assertEquals(1, fulfilled)
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
}
