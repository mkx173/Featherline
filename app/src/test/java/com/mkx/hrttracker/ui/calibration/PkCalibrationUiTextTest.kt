package com.mkx.hrttracker.ui.calibration

import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The copy tables are exhaustive `when`s, so a new enum value already fails to
 * compile. These tests pin the deliberate nullability pattern: which states
 * carry no copy of their own, so a UI regression cannot silently blank a card.
 */
class PkCalibrationUiTextTest {

    @Test
    fun globalStatusCopy_existsExactlyForNonReadyStates() {
        for (state in PkCalibrationGlobalState.entries) {
            val expectNull = state == PkCalibrationGlobalState.READY
            assertEquals("title for $state", expectNull, state.statusTitleRes == null)
            assertEquals("body for $state", expectNull, state.statusBodyRes == null)
        }
    }

    @Test
    fun routeStateCopy_isTotal_andOnlyCleanCalibratedLacksATag() {
        for (state in PkRouteCalibrationDisplayState.entries) {
            assertNotEquals("label for $state", 0, state.labelRes)
            assertNotEquals("reason for $state", 0, state.reasonRes)
            assertNotEquals("next step for $state", 0, state.nextStepRes)
            assertEquals(
                "tag for $state",
                state == PkRouteCalibrationDisplayState.LAB_CALIBRATED,
                state.tagRes == null,
            )
        }
    }

    @Test
    fun reasonDetailCopy_coversEveryReasonWithoutThrowing() {
        // Reasons whose copy lives at the state or card level deliberately map
        // to null; the accessor must still be total over the enum.
        for (reason in PkCalibrationReason.entries) {
            reason.detailRes?.let { res -> assertNotEquals("detail for $reason", 0, res) }
        }
        // Warn-only classification: a provisional row's warnings are its whole
        // story, so every warning reason must carry its own detail line.
        for (reason in listOf(
            PkCalibrationReason.DISPLAY_SCALE_EXCEEDED,
            PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_SUPPORTING_LABS,
            PkCalibrationReason.RESIDUAL_FIT_POOR,
            PkCalibrationReason.UNREVIEWED_OUTLIER,
            PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS,
        )) {
            assertNotNull("detail for $reason", reason.detailRes)
        }
    }
}
