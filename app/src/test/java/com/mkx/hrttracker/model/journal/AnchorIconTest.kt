package com.mkx.hrttracker.model.journal

import org.junit.Assert.assertEquals
import org.junit.Test

class AnchorIconTest {
    @Test
    fun allStorageKeys_roundTrip() {
        AnchorIcon.entries.forEach { icon ->
            assertEquals(icon, AnchorIcon.fromStorageValue(icon.storageKey))
        }
    }

    @Test
    fun storageKeys_areUnique() {
        val storageKeys = AnchorIcon.entries.map { it.storageKey }

        assertEquals(storageKeys.distinct(), storageKeys)
    }

    @Test
    fun unknownKey_fallsBackToEvent() {
        assertEquals(AnchorIcon.EVENT, AnchorIcon.fromStorageValue("not_a_real_icon"))
        assertEquals(AnchorIcon.EVENT, AnchorIcon.fromStorageValue(null))
    }

    @Test
    fun defaultIsEvent() {
        assertEquals(AnchorIcon.EVENT, AnchorIcon.DEFAULT)
    }
}
