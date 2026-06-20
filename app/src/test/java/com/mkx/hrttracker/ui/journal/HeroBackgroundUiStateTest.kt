package com.mkx.hrttracker.ui.journal

import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.PrideFlag
import com.mkx.hrttracker.model.journal.TrackedDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HeroBackgroundUiStateTest {
    private fun trackedDate(heroBackground: PrideFlag?) = TrackedDate(
        id = "id1",
        name = "Started E",
        icon = AnchorIcon.MEDICATION,
        date = LocalDate.parse("2024-01-01"),
        palette = null,
        heroBackground = heroBackground,
        pinnedOrder = 0,
    )

    @Test
    fun toAnchorRowUiState_propagatesHeroBackground() {
        val row = trackedDate(PrideFlag.RAINBOW).toAnchorRowUiState(LocalDate.parse("2024-06-01"))
        assertEquals(PrideFlag.RAINBOW, row.heroBackground)
    }

    @Test
    fun toAnchorRowUiState_propagatesNoneAsNull() {
        assertNull(trackedDate(null).toAnchorRowUiState(LocalDate.parse("2024-06-01")).heroBackground)
    }
}
