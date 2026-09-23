package com.mkx.hrttracker.ui.calibration

import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState

/*
 * Copy tables for the route-isolated E2 calibration surface. Every `when` is
 * exhaustive with no `else` so a new enum value is a compile error, never a
 * silent blank.
 */

/** Coarse confidence tier word. */
@get:StringRes
val PkCalibrationRouteConfidence.labelRes: Int
    get() = when (this) {
        PkCalibrationRouteConfidence.LOW -> R.string.calibration_pk_confidence_low
        PkCalibrationRouteConfidence.MEDIUM -> R.string.calibration_pk_confidence_medium
        PkCalibrationRouteConfidence.HIGH -> R.string.calibration_pk_confidence_high
    }

/** Status-card title for a non-READY global state; READY composes the route summary instead. */
@get:StringRes
val PkCalibrationGlobalState.statusTitleRes: Int?
    get() = when (this) {
        PkCalibrationGlobalState.READY -> null
        PkCalibrationGlobalState.NO_DOSE_HISTORY ->
            R.string.calibration_pk_global_no_dose_history_title
        PkCalibrationGlobalState.NO_USABLE_LABS ->
            R.string.calibration_pk_global_no_usable_labs_title
        PkCalibrationGlobalState.NUMERIC_FAILURE ->
            R.string.calibration_pk_global_numeric_failure_title
    }

@get:StringRes
val PkCalibrationGlobalState.statusBodyRes: Int?
    get() = when (this) {
        PkCalibrationGlobalState.READY -> null
        PkCalibrationGlobalState.NO_DOSE_HISTORY ->
            R.string.calibration_pk_global_no_dose_history_body
        PkCalibrationGlobalState.NO_USABLE_LABS ->
            R.string.calibration_pk_global_no_usable_labs_body
        PkCalibrationGlobalState.NUMERIC_FAILURE ->
            R.string.calibration_pk_global_numeric_failure_body
    }

/** Why a population row has no adjustment; adjusted rows show confidence instead. */
@get:StringRes
val PkRouteCalibrationDisplayState.tagRes: Int?
    get() = when (this) {
        PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL ->
            R.string.calibration_pk_route_tag_no_supporting_labs
        PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE ->
            R.string.calibration_pk_route_tag_numeric_failure
        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
        PkRouteCalibrationDisplayState.LAB_CALIBRATED,
        -> null
    }

/** Short per-reason label for the routes sheet. */
@get:StringRes
val PkCalibrationReason.labelRes: Int
    get() = when (this) {
        PkCalibrationReason.NO_SUPPORTING_LABS ->
            R.string.calibration_pk_route_tag_no_supporting_labs
        PkCalibrationReason.SCALE_OUTSIDE_USUAL_RANGE ->
            R.string.calibration_pk_reason_short_scale
        PkCalibrationReason.UNCERTAIN ->
            R.string.calibration_pk_reason_short_uncertain
        PkCalibrationReason.RESIDUAL_FIT_POOR ->
            R.string.calibration_pk_reason_short_fit
        PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS ->
            R.string.calibration_pk_reason_short_ambiguous
        PkCalibrationReason.UNREVIEWED_OUTLIER ->
            R.string.calibration_pk_reason_short_outlier
    }
