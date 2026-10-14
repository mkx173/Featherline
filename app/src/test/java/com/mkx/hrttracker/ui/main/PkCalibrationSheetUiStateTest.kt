package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.data.repository.PkCalibrationLiveResult
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PkCalibrationSheetUiStateTest {
    @Test
    fun loading_isNull_failedEvaluation_isTheNumericFailureSheet() {
        assertNull(pkCalibrationSheetUiState(live = null))

        val failed = requireNotNull(pkCalibrationSheetUiState(PkCalibrationLiveResult(null)))
        assertTrue(failed.numericFailure)
        assertTrue(failed.routeRows.isEmpty())
    }
}
