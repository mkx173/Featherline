package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.R
import org.junit.Assert.assertEquals
import org.junit.Test

class MainContentComponentsTest {
    @Test
    fun mainE2EstimateInfoToastRes_uses_plasma_concentration_estimate_disclaimer() {
        assertEquals(
            R.string.medical_disclaimer_plasma_concentration_estimates,
            mainE2EstimateInfoToastRes()
        )
    }

    @Test
    fun mainTodayCountLabel_omits_manual_count_when_absent() {
        assertEquals(
            "1/4",
            mainTodayCountLabel(
                doneCount = 1,
                totalCount = 4,
                manualCount = 0
            )
        )
    }

    @Test
    fun mainTodayCountLabel_appends_manual_count_when_present() {
        assertEquals(
            "1/4 (2)",
            mainTodayCountLabel(
                doneCount = 1,
                totalCount = 4,
                manualCount = 2
            )
        )
    }

    @Test
    fun mainTodayCountLabel_keeps_fraction_when_only_manual_records_are_present() {
        assertEquals(
            "0/0 (2)",
            mainTodayCountLabel(
                doneCount = 0,
                totalCount = 0,
                manualCount = 2
            )
        )
    }
}
