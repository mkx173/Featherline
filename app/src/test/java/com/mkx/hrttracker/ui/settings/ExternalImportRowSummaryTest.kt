package com.mkx.hrttracker.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalImportRowSummaryTest {
    @Test
    fun showsAllThreeSegmentsWhenEveryCountIsPositive() {
        assertEquals(
            "5 new · 3 updated · 2 existing",
            externalImportRowSummary(
                newText = "5 new",
                updatedText = "3 updated",
                updatedCount = 3,
                existingText = "2 existing",
                existingCount = 2,
            ),
        )
    }

    @Test
    fun alwaysShowsNewEvenWhenItIsZero() {
        // An import that creates nothing must still report "0 new" so the user
        // can see at a glance that no rows were added.
        assertEquals(
            "0 new",
            externalImportRowSummary(
                newText = "0 new",
                updatedText = "0 updated",
                updatedCount = 0,
                existingText = "0 existing",
                existingCount = 0,
            ),
        )
    }

    @Test
    fun dropsUpdatedSegmentWhenZeroButKeepsExisting() {
        assertEquals(
            "4 new · 2 existing",
            externalImportRowSummary(
                newText = "4 new",
                updatedText = "0 updated",
                updatedCount = 0,
                existingText = "2 existing",
                existingCount = 2,
            ),
        )
    }

    @Test
    fun dropsExistingSegmentWhenZeroButKeepsUpdated() {
        assertEquals(
            "4 new · 1 updated",
            externalImportRowSummary(
                newText = "4 new",
                updatedText = "1 updated",
                updatedCount = 1,
                existingText = "0 existing",
                existingCount = 0,
            ),
        )
    }
}
