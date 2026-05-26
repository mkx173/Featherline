package com.mkx.hrttracker.ui.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineDetailScreenStockWarningTest {

    @Test
    fun warnAtBelowIntervalShowsWarningOnlyWhenEnabledAndShorterThanInterval() {
        assertTrue(showWarnAtBelowIntervalWarning(warnAtDays = 7, intervalDays = 14))
        assertFalse(showWarnAtBelowIntervalWarning(warnAtDays = 14, intervalDays = 14))
        assertFalse(showWarnAtBelowIntervalWarning(warnAtDays = 21, intervalDays = 14))
        assertFalse(showWarnAtBelowIntervalWarning(warnAtDays = 0, intervalDays = 14))
        assertFalse(showWarnAtBelowIntervalWarning(warnAtDays = 7, intervalDays = null))
    }
}
