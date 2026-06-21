package com.mkx.hrttracker.model.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinOrderTest {
    @Test
    fun appendOrder_isMaxPlusOne_andZeroWhenEmpty() {
        assertEquals(0, PinOrder.appendOrder(existingOrders = emptyList()))
        assertEquals(3, PinOrder.appendOrder(existingOrders = listOf(0, 1, 2)))
        assertEquals(6, PinOrder.appendOrder(existingOrders = listOf(0, 5)))
    }

    @Test
    fun appendOrderAfterMax_isMaxPlusOne_andZeroWhenEmpty() {
        assertEquals(0, PinOrder.appendOrderAfterMax(maxOrder = null))
        assertEquals(6, PinOrder.appendOrderAfterMax(maxOrder = 5))
    }

    @Test
    fun normalize_compactsToContiguousIndices() {
        val result = PinOrder.normalize(pinnedIdsInOrder = listOf("c", "a", "b"))
        assertEquals(mapOf("c" to 0, "a" to 1, "b" to 2), result)
    }

    @Test
    fun hero_isFirstPinned_orNullWhenEmpty() {
        assertEquals("c", PinOrder.hero(pinnedIdsInOrder = listOf("c", "a")))
        assertNull(PinOrder.hero(pinnedIdsInOrder = emptyList()))
    }
}
