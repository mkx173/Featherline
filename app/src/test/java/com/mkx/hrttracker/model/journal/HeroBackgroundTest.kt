package com.mkx.hrttracker.model.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroBackgroundTest {
    @Test
    fun fromStorageValue_mapsMissingValueToDateColorDefault() {
        assertEquals(HeroBackground.DateColor, HeroBackground.fromStorageValue(null))
    }

    @Test
    fun fromStorageValue_decodesExplicitNoneAndFlags() {
        assertEquals(HeroBackground.None, HeroBackground.fromStorageValue("NONE"))
        assertEquals(
            HeroBackground.Flag(PrideFlag.TRANSGENDER),
            HeroBackground.fromStorageValue(PrideFlag.TRANSGENDER.name),
        )
    }

    @Test
    fun fromStorageValue_mapsUnknownValuesToNone() {
        assertEquals(HeroBackground.None, HeroBackground.fromStorageValue("PROGRESS"))
        assertEquals(HeroBackground.None, HeroBackground.fromStorageValue(""))
    }

    @Test
    fun storageKey_encodesDateColorAsNullAndNoneAsSentinel() {
        assertNull(HeroBackground.DateColor.storageKey)
        assertEquals("NONE", HeroBackground.None.storageKey)
        assertEquals(PrideFlag.LESBIAN.name, HeroBackground.Flag(PrideFlag.LESBIAN).storageKey)
    }
}
