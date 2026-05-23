package com.mkx.hrttracker.ui.medicine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicinesScreenLayoutTest {
    @Test
    fun medicineManagerAddsSectionTopSpacingOnlyBetweenSections() {
        assertFalse(medicineManagerNeedsSectionTopSpacing(sectionIndex = 0))
        assertTrue(medicineManagerNeedsSectionTopSpacing(sectionIndex = 1))
        assertTrue(medicineManagerNeedsSectionTopSpacing(sectionIndex = 2))
    }

    @Test
    fun medicineManagerAddsListSegmentGapOnlyBetweenRows() {
        assertFalse(medicineManagerNeedsRowBottomGap(index = 0, itemCount = 1))
        assertTrue(medicineManagerNeedsRowBottomGap(index = 0, itemCount = 2))
        assertFalse(medicineManagerNeedsRowBottomGap(index = 1, itemCount = 2))
    }

    @Test
    fun medicineManagerSectionHeaderPaddingMatchesSettingsHeaderPadding() {
        assertEquals(4, MedicineManagerSectionHeaderTopPaddingDp)
        assertEquals(10, MedicineManagerSectionHeaderBottomPaddingDp)
    }
}
