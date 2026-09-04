package com.mkx.hrttracker.ui.calibration

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.pk.PkCalibrationRoute

/*
 * Presentation identity for calibration routes (Phase-2 plan §2.3). Colour is
 * decoration only — never the sole carrier of state. Icons and localized
 * labels reuse the medication application-type resources.
 */

val PkCalibrationRoute.medicationGroupColorKey: MedicationGroupColorKey
    get() = when (this) {
        PkCalibrationRoute.INJECTION -> MedicationGroupColorKey.ROSE
        PkCalibrationRoute.PATCH -> MedicationGroupColorKey.CORAL
        PkCalibrationRoute.GEL -> MedicationGroupColorKey.TEAL
        PkCalibrationRoute.ORAL -> MedicationGroupColorKey.VIOLET
        PkCalibrationRoute.SUBLINGUAL -> MedicationGroupColorKey.SKY
    }

/** Application type carrying the route's icon and localized label. */
val PkCalibrationRoute.applicationType: MedicationApplicationType
    get() = when (this) {
        PkCalibrationRoute.INJECTION -> MedicationApplicationType.INJECTION
        PkCalibrationRoute.PATCH -> MedicationApplicationType.PATCH_ON
        PkCalibrationRoute.GEL -> MedicationApplicationType.GEL
        PkCalibrationRoute.ORAL -> MedicationApplicationType.ORAL
        PkCalibrationRoute.SUBLINGUAL -> MedicationApplicationType.SUBLINGUAL
    }

/**
 * Presentation order for the status surfaces: adjusted routes first, canonical
 * route order within each group (design decision, 2026-09-02).
 */
internal val List<PkCalibrationRouteRowUiState>.adjustedFirst: List<PkCalibrationRouteRowUiState>
    get() = sortedByDescending { row -> row.displayState.isAdjusted }
