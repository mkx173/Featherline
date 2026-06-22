package com.mkx.hrttracker.model.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrideFlagTest {
    @Test
    fun fromStorageValueOrNull_roundTripsKnownNames() {
        PrideFlag.entries.forEach { flag ->
            assertEquals(flag, PrideFlag.fromStorageValueOrNull(flag.name))
        }
    }

    @Test
    fun fromStorageValueOrNull_returnsNullForUnknownOrMissing() {
        assertNull(PrideFlag.fromStorageValueOrNull(null))
        assertNull(PrideFlag.fromStorageValueOrNull("PROGRESS"))
        assertNull(PrideFlag.fromStorageValueOrNull(""))
    }

    @Test
    fun everyFlagHasAtLeastTwoDistinctSeeds() {
        // The bloom fans >= 2 blooms and the swatch draws >= 2 strips; a single-seed
        // flag would degrade both. This also guards an accidental empty seed list.
        PrideFlag.entries.forEach { flag ->
            assertTrue(flag.name, flag.seeds.distinct().size >= 2)
        }
    }

    @Test
    fun flagSetIsExactlyTheNineSpecFlags() {
        assertEquals(
            listOf(
                "TRANSGENDER", "RAINBOW", "BISEXUAL", "PANSEXUAL", "NONBINARY",
                "LESBIAN", "ASEXUAL", "GENDERFLUID", "AGENDER",
            ),
            PrideFlag.entries.map { it.name },
        )
    }
}
