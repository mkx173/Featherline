package com.mkx.hrttracker.ui.calibration

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PkCalibrationRoutesSheetEmptyTextTest {
    @Test
    fun loadingAndNonReadyStates_showAnEmptyLine_readyRowsDoNot() {
        assertEquals(
            R.string.calibration_pk_routes_sheet_loading,
            pkCalibrationRoutesSheetEmptyTextRes(null),
        )
        val noLabs = pkCalibrationUiState(PkCalibrationResult(PkCalibrationGlobalState.NO_USABLE_LABS), null)
        assertEquals(
            R.string.calibration_pk_global_no_usable_labs_body,
            pkCalibrationRoutesSheetEmptyTextRes(noLabs),
        )
        val ready = pkCalibrationUiState(
            PkCalibrationResult(
                globalState = PkCalibrationGlobalState.READY,
                routeResults = PkCalibrationRoute.entries.map { route ->
                    PkRouteCalibrationResult(
                        route = route,
                        displayState = PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL,
                    )
                },
            ),
            null,
        )
        assertNull(pkCalibrationRoutesSheetEmptyTextRes(ready))
    }
}
