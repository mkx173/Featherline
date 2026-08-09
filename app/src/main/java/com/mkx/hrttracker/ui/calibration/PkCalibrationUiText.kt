package com.mkx.hrttracker.ui.calibration

import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState

/*
 * Copy tables for the route-isolated E2 calibration surface. Every `when` is
 * exhaustive with no `else` so a new enum value is a compile error, never a
 * silent blank (Phase-2 plan §2.2). Copy follows UI handoff v9.0 as amended by
 * the v10.0 amendment (supporting-lab wording, attestation scope).
 */

/** Status-card title for a non-READY global state; READY composes §5 instead. */
@get:StringRes
val PkCalibrationGlobalState.statusTitleRes: Int?
    get() = when (this) {
        PkCalibrationGlobalState.READY -> null
        PkCalibrationGlobalState.SCOPE_NOT_CONFIRMED ->
            R.string.calibration_pk_global_scope_not_confirmed_title
        PkCalibrationGlobalState.SHARED_INPUT_INVALID ->
            R.string.calibration_pk_global_input_invalid_title
        PkCalibrationGlobalState.SHARED_NUMERIC_FAILURE ->
            R.string.calibration_pk_global_numeric_failure_title
    }

@get:StringRes
val PkCalibrationGlobalState.statusBodyRes: Int?
    get() = when (this) {
        PkCalibrationGlobalState.READY -> null
        PkCalibrationGlobalState.SCOPE_NOT_CONFIRMED ->
            R.string.calibration_pk_global_scope_not_confirmed_body
        PkCalibrationGlobalState.SHARED_INPUT_INVALID ->
            R.string.calibration_pk_global_input_invalid_body
        PkCalibrationGlobalState.SHARED_NUMERIC_FAILURE ->
            R.string.calibration_pk_global_numeric_failure_body
    }

/** "Population" / "Route-adjusted" group label (handoff §5 table). */
@get:StringRes
val PkRouteCalibrationDisplayState.labelRes: Int
    get() = when (this) {
        PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS,
        PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_SUPPORTING_LABS,
        PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
        PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED,
        PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
        -> R.string.calibration_pk_route_label_population

        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
        PkRouteCalibrationDisplayState.LAB_CALIBRATED,
        -> R.string.calibration_pk_route_label_adjusted
    }

/** Short qualifier appended after the label; null for a clean LAB_CALIBRATED. */
@get:StringRes
val PkRouteCalibrationDisplayState.tagRes: Int?
    get() = when (this) {
        PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS ->
            R.string.calibration_pk_route_tag_no_supporting_labs
        PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_SUPPORTING_LABS ->
            R.string.calibration_pk_route_tag_insufficient_supporting_labs
        PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE ->
            R.string.calibration_pk_route_tag_low_confidence
        PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED ->
            R.string.calibration_pk_route_tag_cap_exceeded
        PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE ->
            R.string.calibration_pk_route_tag_numeric_failure
        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL ->
            R.string.calibration_pk_route_tag_provisional
        PkRouteCalibrationDisplayState.LAB_CALIBRATED -> null
    }

/** Plain-language explanation of the state (details surface). */
@get:StringRes
val PkRouteCalibrationDisplayState.reasonRes: Int
    get() = when (this) {
        PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS ->
            R.string.calibration_pk_route_reason_no_supporting_labs
        PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_SUPPORTING_LABS ->
            R.string.calibration_pk_route_reason_insufficient_supporting_labs
        PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE ->
            R.string.calibration_pk_route_reason_low_confidence
        PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED ->
            R.string.calibration_pk_route_reason_cap_exceeded
        PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE ->
            R.string.calibration_pk_route_reason_numeric_failure
        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL ->
            R.string.calibration_pk_route_reason_provisional
        PkRouteCalibrationDisplayState.LAB_CALIBRATED ->
            R.string.calibration_pk_route_reason_calibrated
    }

/** Suggested next step for the state (handoff §5 table). */
@get:StringRes
val PkRouteCalibrationDisplayState.nextStepRes: Int
    get() = when (this) {
        PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS ->
            R.string.calibration_pk_route_next_no_supporting_labs
        PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_SUPPORTING_LABS ->
            R.string.calibration_pk_route_next_insufficient_supporting_labs
        PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE ->
            R.string.calibration_pk_route_next_low_confidence
        PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED ->
            R.string.calibration_pk_route_next_cap_exceeded
        PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE ->
            R.string.calibration_pk_route_next_numeric_failure
        PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL ->
            R.string.calibration_pk_route_next_provisional
        PkRouteCalibrationDisplayState.LAB_CALIBRATED ->
            R.string.calibration_pk_route_next_calibrated
    }

/**
 * Optional per-reason detail line (handoff §5.4). Null where the state-level
 * copy already says everything a product surface should.
 */
@get:StringRes
val PkCalibrationReason.detailRes: Int?
    get() = when (this) {
        PkCalibrationReason.INSUFFICIENT_DRUG_SIGNAL_CONTRAST ->
            R.string.calibration_pk_reason_insufficient_contrast
        PkCalibrationReason.POSTERIOR_SD_TOO_WIDE ->
            R.string.calibration_pk_reason_posterior_sd_too_wide
        PkCalibrationReason.RESIDUAL_FIT_POOR ->
            R.string.calibration_pk_reason_residual_fit_poor
        PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS ->
            R.string.calibration_pk_reason_posterior_mode_ambiguous
        PkCalibrationReason.UNREVIEWED_OUTLIER ->
            R.string.calibration_pk_reason_unreviewed_outlier
        PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_SUPPORTING_LABS ->
            R.string.calibration_pk_reason_extreme_scale_three_labs
        PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY ->
            R.string.calibration_pk_route_boundary_notice

        PkCalibrationReason.SCOPE_NOT_CONFIRMED,
        PkCalibrationReason.SHARED_INPUT_INVALID,
        PkCalibrationReason.INVALID_NONPOSITIVE_E2,
        PkCalibrationReason.NO_SUPPORTING_LABS,
        PkCalibrationReason.INSUFFICIENT_SUPPORTING_LABS,
        PkCalibrationReason.DISPLAY_SCALE_EXCEEDED,
        PkCalibrationReason.BAND_NUMERIC_FAILURE,
        PkCalibrationReason.NUMERIC_FAILURE,
        -> null
    }
