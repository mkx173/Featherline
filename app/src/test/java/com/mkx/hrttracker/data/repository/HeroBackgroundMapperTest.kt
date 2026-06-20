package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.TrackedDateEntity
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.PrideFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroBackgroundMapperTest {
    private fun entity(heroBackgroundKey: String?) = TrackedDateEntity(
        uuid = "id1",
        name = "Started E",
        iconKey = "medication",
        dateIso = "2024-01-01",
        paletteKey = null,
        heroBackgroundKey = heroBackgroundKey,
        pinnedOrder = 0,
        createdAtEpochMillis = 1000L,
        updatedAtEpochMillis = 1000L,
    )

    @Test
    fun toModel_decodesKnownFlag() {
        assertEquals(
            HeroBackground.Flag(PrideFlag.TRANSGENDER),
            entity("TRANSGENDER").toModel().heroBackground,
        )
    }

    @Test
    fun toModel_decodesNullToDateColorDefault() {
        assertEquals(HeroBackground.DateColor, entity(null).toModel().heroBackground)
    }

    @Test
    fun toModel_decodesExplicitNoneAndUnknownToNone() {
        assertEquals(HeroBackground.None, entity("NONE").toModel().heroBackground)
        assertEquals(HeroBackground.None, entity("PROGRESS").toModel().heroBackground)
    }

    @Test
    fun toEntity_encodesFlagName_nullForDateColorDefault_andSentinelForNone() {
        val model = entity("LESBIAN").toModel()
        assertEquals("LESBIAN", model.toEntity(1000L, 1000L).heroBackgroundKey)
        assertNull(
            model.copy(heroBackground = HeroBackground.DateColor)
                .toEntity(1000L, 1000L)
                .heroBackgroundKey,
        )
        assertEquals(
            "NONE",
            model.copy(heroBackground = HeroBackground.None)
                .toEntity(1000L, 1000L)
                .heroBackgroundKey,
        )
    }
}
