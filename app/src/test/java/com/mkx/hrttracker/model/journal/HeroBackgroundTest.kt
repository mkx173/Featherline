package com.mkx.hrttracker.model.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroBackgroundTest {
    @Test
    fun fromStorageValue_mapsMissingValueToNoneDefault() {
        assertEquals(HeroBackground.None, HeroBackground.fromStorageValue(null))
    }

    @Test
    fun fromStorageValue_decodesDateColorLegacyNoneAndFlags() {
        assertEquals(HeroBackground.DateColor, HeroBackground.fromStorageValue("DATE_COLOR"))
        // Legacy rows persisted before None became the null-backed default.
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
    fun storageKey_encodesNoneAsNullAndDateColorAsSentinel() {
        assertNull(HeroBackground.None.storageKey)
        assertEquals("DATE_COLOR", HeroBackground.DateColor.storageKey)
        assertEquals(PrideFlag.LESBIAN.name, HeroBackground.Flag(PrideFlag.LESBIAN).storageKey)
    }
}
