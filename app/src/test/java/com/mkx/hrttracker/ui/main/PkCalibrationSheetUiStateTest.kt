package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.data.repository.CalibrationEstimate
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PkCalibrationSheetUiStateTest {
    @Test
    fun loading_isNull_failedFitOrRender_isTheNumericFailureSheet() {
        assertNull(pkCalibrationSheetUiState(estimate = null))

        val failed = requireNotNull(pkCalibrationSheetUiState(estimate(result = null)))
        assertTrue(failed.numericFailure)
        assertTrue(failed.routeRows.isEmpty())

        val noLabs = requireNotNull(
            pkCalibrationSheetUiState(estimate(PkCalibrationResult(PkCalibrationGlobalState.NO_USABLE_LABS)))
        )
        assertEquals(PkCalibrationGlobalState.NO_USABLE_LABS, noLabs.globalState)
        assertFalse(noLabs.numericFailure)
    }

    private fun estimate(result: PkCalibrationResult?) = CalibrationEstimate(
        result = result,
        render = null,
        acceptedLabIds = emptySet(),
        excludedLabIds = emptySet(),
        nonPositiveLabIds = emptySet(),
    )
}
