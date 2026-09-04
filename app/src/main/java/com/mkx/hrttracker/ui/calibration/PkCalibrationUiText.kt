package com.mkx.hrttracker.ui.calibration

import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState

/*
 * Copy tables for the route-isolated E2 calibration surface. Every `when` is
 * exhaustive with no `else` so a new enum value is a compile error, never a
 * silent blank (Phase-2 plan §2.2).
 */

/** Coarse confidence tier word (user decision, 2026-08-12). */
@get:StringRes
val PkCalibrationRouteConfidence.labelRes: Int
    get() = when (this) {
        PkCalibrationRouteConfidence.LOW -> R.string.calibration_pk_confidence_low
        PkCalibrationRouteConfidence.MEDIUM -> R.string.calibration_pk_confidence_medium
        PkCalibrationRouteConfidence.HIGH -> R.string.calibration_pk_confidence_high
    }

/** Status-card title for a non-READY global state; READY composes §5 instead. */
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

/** Suggested next step for an adjusted row; population rows carry none. */
@get:StringRes
val PkRouteCalibrationDisplayState.nextStepRes: Int?
    get() = when (this) {
        PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL,
        PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
        -> null
        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL ->
            R.string.calibration_pk_route_next_provisional
        PkRouteCalibrationDisplayState.LAB_CALIBRATED ->
            R.string.calibration_pk_route_next_calibrated
    }

/** Per-reason detail line: under warn-only classification these are the whole story of a provisional row. */
@get:StringRes
val PkCalibrationReason.detailRes: Int
    get() = when (this) {
        PkCalibrationReason.NO_SUPPORTING_LABS ->
            R.string.calibration_pk_reason_no_supporting_labs
        PkCalibrationReason.SCALE_OUTSIDE_USUAL_RANGE ->
            R.string.calibration_pk_reason_scale_outside_usual_range
        PkCalibrationReason.UNCERTAIN ->
            R.string.calibration_pk_reason_uncertain
        PkCalibrationReason.RESIDUAL_FIT_POOR ->
            R.string.calibration_pk_reason_residual_fit_poor
        PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS ->
            R.string.calibration_pk_reason_posterior_mode_ambiguous
        PkCalibrationReason.UNREVIEWED_OUTLIER ->
            R.string.calibration_pk_reason_unreviewed_outlier
    }
