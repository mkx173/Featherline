package com.mkx.hrttracker.ui.calibration

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HazeModalBottomSheet
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.MedicalDisclaimerKind
import com.mkx.hrttracker.ui.components.MedicalDisclaimerText
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.labelRes
import kotlinx.coroutines.CoroutineScope

/*
 * The four Phase-2 calibration sheets (routes detail, coaching, disclaimer,
 * how-it-works). All ride HazeModalBottomSheet
 * with hideBottomSheet-driven dismissal, matching every other sheet in the app.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PkCalibrationSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope: CoroutineScope = rememberCoroutineScope()
    HazeModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(
                    start = dimensionResource(R.dimen.padding_large),
                    end = dimensionResource(R.dimen.padding_large),
                    bottom = dimensionResource(R.dimen.padding_large),
                ),
        ) {
            content { hideBottomSheet(scope, sheetState, onDismissRequest) }
        }
    }
}

@Composable
private fun PkCalibrationSheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.cjkTextOffset(text),
    )
}

/** Icon-box + title + body row shared by the coaching/edu sheets. */
@Composable
private fun PkCalibrationSheetItem(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
) {
    Row(modifier = Modifier.padding(top = 12.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            val title = stringResource(titleRes)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.cjkTextOffset(title),
            )
            val body = stringResource(bodyRes)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .cjkTextOffset(body),
            )
        }
    }
}

@Composable
private fun PkCalibrationSheetNote(@StringRes textRes: Int) {
    val text = stringResource(textRes)
    Row(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_privacy_tip),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.cjkTextOffset(text),
        )
    }
}

// ---------------------------------------------------------------------------
// Routes detail sheet
// ---------------------------------------------------------------------------

@Composable
fun PkCalibrationRoutesSheet(
    uiState: PkCalibrationUiState,
    onDismissRequest: () -> Unit,
) {
    PkCalibrationSheet(onDismissRequest = onDismissRequest) { dismiss ->
        PkCalibrationSheetTitle(stringResource(R.string.calibration_pk_routes_card_title))
        val intro = stringResource(R.string.calibration_pk_routes_sheet_intro)
        Text(
            text = intro,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 8.dp)
                .cjkTextOffset(intro),
        )
        Spacer(modifier = Modifier.height(20.dp))
        PkCalibrationRouteCards(uiState.routeRows.adjustedFirst)
        Spacer(modifier = Modifier.height(24.dp))
        HrtButton(
            text = stringResource(R.string.calibration_pk_got_it),
            onClick = dismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One segmented card per route, grouped by [HrtSection] without a header. */
@Composable
private fun PkCalibrationRouteCards(rows: List<PkCalibrationRouteRowUiState>) {
    HrtSection(title = null) {
        rows.forEach { row ->
            item { PkCalibrationRouteCard(row) }
        }
    }
}

/**
 * Route card: tile, name, supporting line (labs + confidence, or the
 * population tag), trailing state. Adjusted routes carry a note with every
 * warning the fit raised (warn-only: the adjustment applies regardless) and
 * the suggested next step.
 */
@Composable
private fun PkCalibrationRouteCard(row: PkCalibrationRouteRowUiState) {
    val adjusted = row.displayState.isAdjusted
    EditorSegmentedListItem(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PkCalibrationRouteTile(
                    route = row.route,
                    size = 34.dp,
                    iconSize = 20.dp,
                    shape = MaterialTheme.shapes.medium,
                )
                Column(modifier = Modifier.weight(1f)) {
                    val name = stringResource(row.route.applicationType.labelRes)
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.cjkTextOffset(name),
                    )
                    val labs = pluralStringResource(
                        R.plurals.calibration_pk_supporting_lab_count,
                        row.supportingLabCount,
                        row.supportingLabCount,
                    )
                    val meta = when {
                        !adjusted -> row.displayState.tagRes?.let { tag -> stringResource(tag) }
                        row.confidence != null -> stringResource(
                            R.string.calibration_pk_route_meta_confidence,
                            labs,
                            stringResource(row.confidence.labelRes),
                        )

                        else -> labs
                    }
                    meta?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.cjkTextOffset(it),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (adjusted) {
                        Text(
                            text = stringResource(R.string.calibration_pk_chip_adjusted),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        row.confidence?.let { confidence -> PkCalibrationConfidenceBars(confidence) }
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_group),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.calibration_pk_route_label_population),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (adjusted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.reasons.forEach { reason ->
                        PkCalibrationRouteNoteLine(
                            iconRes = R.drawable.ic_error_outline,
                            tint = MaterialTheme.colorScheme.tertiary,
                            text = stringResource(reason.detailRes),
                        )
                    }
                    row.displayState.nextStepRes?.let { nextStep ->
                        PkCalibrationRouteNoteLine(
                            iconRes = R.drawable.ic_check_circle,
                            tint = MaterialTheme.colorScheme.primary,
                            text = stringResource(nextStep),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PkCalibrationRouteNoteLine(
    @DrawableRes iconRes: Int,
    tint: androidx.compose.ui.graphics.Color,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .cjkTextOffset(text),
        )
    }
}

// ---------------------------------------------------------------------------
// Coaching sheet (§9)
// ---------------------------------------------------------------------------

@Composable
fun PkCalibrationCoachingSheet(onDismissRequest: () -> Unit) {
    PkCalibrationSheet(onDismissRequest = onDismissRequest) { dismiss ->
        PkCalibrationSheetTitle(stringResource(R.string.calibration_pk_coaching_row_title))
        PkCalibrationSheetItem(
            iconRes = R.drawable.ic_schedule,
            titleRes = R.string.calibration_pk_coaching_time_title,
            bodyRes = R.string.calibration_pk_coaching_time_body,
        )
        PkCalibrationSheetItem(
            iconRes = R.drawable.ic_history,
            titleRes = R.string.calibration_pk_coaching_history_title,
            bodyRes = R.string.calibration_pk_coaching_history_body,
        )
        PkCalibrationSheetItem(
            iconRes = R.drawable.ic_experiment,
            titleRes = R.string.calibration_pk_coaching_assay_title,
            bodyRes = R.string.calibration_pk_coaching_assay_body,
        )
        PkCalibrationSheetItem(
            iconRes = R.drawable.ic_labs,
            titleRes = R.string.calibration_pk_coaching_variety_title,
            bodyRes = R.string.calibration_pk_coaching_variety_body,
        )
        PkCalibrationSheetNote(R.string.calibration_pk_coaching_safety_note)
        Spacer(modifier = Modifier.height(20.dp))
        HrtButton(
            text = stringResource(R.string.calibration_pk_got_it),
            onClick = dismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// Disclaimer sheet ("About these estimates")
// ---------------------------------------------------------------------------

@Composable
fun PkCalibrationDisclaimerSheet(onDismissRequest: () -> Unit) {
    val lines = listOf(
        R.string.calibration_pk_disclaimer_line_treatment_derived,
        R.string.calibration_pk_disclaimer_line_absorbs,
        R.string.calibration_pk_disclaimer_line_not_verified,
        R.string.calibration_pk_disclaimer_line_shifts,
    )
    PkCalibrationSheet(onDismissRequest = onDismissRequest) { dismiss ->
        PkCalibrationSheetTitle(stringResource(R.string.calibration_pk_disclaimer_row_title))
        Spacer(modifier = Modifier.height(4.dp))
        lines.forEach { lineRes ->
            val line = stringResource(lineRes)
            Row(modifier = Modifier.padding(top = 12.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_privacy_tip),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.cjkTextOffset(line),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        MedicalDisclaimerText(kinds = listOf(MedicalDisclaimerKind.LAB_ADJUSTMENT))
        Spacer(modifier = Modifier.height(16.dp))
        HrtButton(
            text = stringResource(R.string.calibration_pk_got_it),
            onClick = dismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// How-it-works sheet (§8.7)
// ---------------------------------------------------------------------------

@Composable
fun PkCalibrationEduSheet(onDismissRequest: () -> Unit) {
    PkCalibrationSheet(onDismissRequest = onDismissRequest) { dismiss ->
        PkCalibrationSheetTitle(stringResource(R.string.calibration_pk_edu_title))
        val intro = stringResource(R.string.calibration_pk_edu_intro)
        Text(
            text = intro,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 8.dp)
                .cjkTextOffset(intro),
        )
        PkCalibrationEduHeader(R.string.calibration_pk_edu_does_header)
        PkCalibrationSheetItem(
            iconRes = R.drawable.ic_tune,
            titleRes = R.string.calibration_pk_edu_does_routes_title,
            bodyRes = R.string.calibration_pk_edu_does_routes_body,
        )
        PkCalibrationSheetItem(
            iconRes = R.drawable.ic_labs,
            titleRes = R.string.calibration_pk_edu_does_band_title,
            bodyRes = R.string.calibration_pk_edu_does_band_body,
        )
        PkCalibrationEduHeader(R.string.calibration_pk_edu_doesnt_header)
        PkCalibrationSheetItem(
            iconRes = R.drawable.ic_block,
            titleRes = R.string.calibration_pk_edu_doesnt_measure_title,
            bodyRes = R.string.calibration_pk_edu_doesnt_measure_body,
        )
        PkCalibrationSheetItem(
            iconRes = R.drawable.ic_block,
            titleRes = R.string.calibration_pk_edu_doesnt_verify_title,
            bodyRes = R.string.calibration_pk_edu_doesnt_verify_body,
        )
        PkCalibrationSheetItem(
            iconRes = R.drawable.ic_block,
            titleRes = R.string.calibration_pk_edu_doesnt_learn_title,
            bodyRes = R.string.calibration_pk_edu_doesnt_learn_body,
        )
        PkCalibrationSheetItem(
            iconRes = R.drawable.ic_block,
            titleRes = R.string.calibration_pk_edu_doesnt_advise_title,
            bodyRes = R.string.calibration_pk_edu_doesnt_advise_body,
        )
        Spacer(modifier = Modifier.height(16.dp))
        MedicalDisclaimerText(kinds = listOf(MedicalDisclaimerKind.LAB_ADJUSTMENT))
        Spacer(modifier = Modifier.height(16.dp))
        HrtButton(
            text = stringResource(R.string.calibration_pk_got_it),
            onClick = dismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PkCalibrationEduHeader(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes).uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp),
    )
}

/** Calibrated, provisional MEDIUM, provisional LOW with every reason, then population and numeric failure. */
@Preview(name = "PK Route Card · states", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationRouteCardPreview() {
    val rows = listOf(
        previewPkCalibratedRow,
        previewPkProvisionalMediumRow,
        previewPkProvisionalLowRow,
        previewPkRouteRow(PkCalibrationRoute.ORAL),
        previewPkNumericFailureRows.last(),
    )
    HrtTrackerTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            PkCalibrationRouteCards(rows)
        }
    }
}
