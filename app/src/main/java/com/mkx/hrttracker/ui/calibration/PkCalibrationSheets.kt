package com.mkx.hrttracker.ui.calibration

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HazeModalBottomSheet
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.MedicalDisclaimerKind
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.labelRes
import kotlinx.coroutines.CoroutineScope

/*
 * The calibration sheets (routes detail, review queue, how-it-works). All
 * ride MedicationEditorSheetScaffold, matching every other sheet in the app.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PkCalibrationSheet(
    title: String,
    onDismissRequest: () -> Unit,
    disclaimerKinds: List<MedicalDisclaimerKind> = emptyList(),
    fillAvailableHeight: Boolean = false,
    confirmButtonText: String = stringResource(R.string.calibration_pk_got_it),
    // Null confirm action dismisses the sheet.
    onConfirm: (() -> Unit)? = null,
    secondaryButtonText: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope: CoroutineScope = rememberCoroutineScope()
    val dismiss = { hideBottomSheet(scope, sheetState, onDismissRequest) }
    MedicationEditorSheetScaffold(
        title = title,
        sheetState = sheetState,
        confirmButtonText = confirmButtonText,
        onDismissRequest = onDismissRequest,
        onCloseClick = null,
        fillAvailableHeight = fillAvailableHeight,
        isSaving = false,
        destructiveButtonText = secondaryButtonText,
        onDestructiveAction = onSecondary,
        disclaimerKinds = disclaimerKinds,
        onConfirm = onConfirm ?: dismiss,
        content = content,
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

/**
 * Adjusted routes first, each with a short warning line and a link to the
 * results it is waiting on; population routes follow in their own group.
 */
@Composable
fun PkCalibrationRoutesSheet(
    uiState: PkCalibrationUiState,
    reviewCounts: Map<PkCalibrationRoute, Int>,
    onOpenReview: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    PkCalibrationSheet(
        title = stringResource(R.string.calibration_pk_routes_card_title),
        onDismissRequest = onDismissRequest,
    ) {
        val (adjusted, population) = uiState.routeRows.partition { row -> row.displayState.isAdjusted }
        if (adjusted.isNotEmpty()) {
            HrtSection(title = stringResource(R.string.calibration_pk_hero_adjusted), topPadding = false) {
                adjusted.forEach { row ->
                    item {
                        PkCalibrationRouteCard(
                            row = row,
                            reviewCount = reviewCounts[row.route] ?: 0,
                            onOpenReview = onOpenReview,
                        )
                    }
                }
            }
        }
        if (population.isNotEmpty()) {
            HrtSection(
                title = stringResource(R.string.calibration_pk_hero_population),
                topPadding = adjusted.isNotEmpty(),
            ) {
                population.forEach { row ->
                    item { PkCalibrationRouteCard(row = row, reviewCount = 0, onOpenReview = { }) }
                }
            }
        }
    }
}

/**
 * Route card: tile, name, supporting line (labs + confidence, or the
 * population tag). Adjusted routes add one short warning line (the
 * adjustment applies regardless) and a link to results to check; an outlier
 * is represented by that link, not by a warning.
 */
@Composable
private fun PkCalibrationRouteCard(
    row: PkCalibrationRouteRowUiState,
    reviewCount: Int,
    onOpenReview: () -> Unit,
) {
    val adjusted = row.displayState.isAdjusted
    EditorSegmentedListItem(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                    pkCalibrationRouteMeta(row)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.cjkTextOffset(it),
                        )
                    }
                }
                row.confidence?.let { confidence -> PkCalibrationConfidenceBars(confidence) }
            }
            // Aligns under the name (tile 34 + gap 12).
            val indent = Modifier.padding(start = 46.dp)
            val warnings = row.reasons
                .filterNot { reason -> reason == PkCalibrationReason.UNREVIEWED_OUTLIER }
                .map { reason -> stringResource(reason.labelRes) }
            if (adjusted && warnings.isNotEmpty()) {
                PkCalibrationNoteRow(
                    iconRes = R.drawable.ic_error_outline,
                    text = warnings.joinToString(" · "),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = indent.padding(top = 8.dp),
                )
            }
            if (adjusted && reviewCount > 0) {
                val link = pluralStringResource(R.plurals.calibration_pk_review_count, reviewCount, reviewCount)
                Row(
                    modifier = indent
                        .padding(top = 4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onOpenReview)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = link,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.cjkTextOffset(link),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Review queue sheet
// ---------------------------------------------------------------------------

/** One card per lab that needs review; the caller renders each lab row. */
@Composable
fun PkCalibrationReviewSheet(
    count: Int,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    PkCalibrationSheet(
        title = pluralStringResource(R.plurals.calibration_pk_review_count, count, count),
        onDismissRequest = onDismissRequest,
        confirmButtonText = stringResource(R.string.journal_done),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// How-it-works sheet
// ---------------------------------------------------------------------------

/**
 * Two full-height pages: what lab adjustment is, then how to help a route
 * calibrate. Next advances, Back returns, Finish (last page) dismisses.
 */
@Composable
fun PkCalibrationEduSheet(onDismissRequest: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val lastPage = page == 1
    PkCalibrationSheet(
        title = stringResource(
            if (lastPage) R.string.calibration_pk_coaching_row_title else R.string.calibration_pk_edu_title
        ),
        onDismissRequest = onDismissRequest,
        disclaimerKinds = if (lastPage) emptyList() else listOf(MedicalDisclaimerKind.LAB_ADJUSTMENT),
        fillAvailableHeight = true,
        confirmButtonText = stringResource(
            if (lastPage) R.string.calibration_pk_edu_finish else R.string.calibration_pk_edu_next
        ),
        onConfirm = if (lastPage) null else ({ page = 1 }),
        secondaryButtonText = if (lastPage) stringResource(R.string.calibration_pk_edu_back) else null,
        onSecondary = if (lastPage) ({ page = 0 }) else null,
    ) {
        if (lastPage) PkCalibrationCoachingPage() else PkCalibrationEduPage()
    }
}

@Composable
private fun PkCalibrationEduPage() {
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
}

@Composable
private fun PkCalibrationCoachingPage() {
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
            HrtSection(title = null) {
                rows.forEachIndexed { index, row ->
                    item { PkCalibrationRouteCard(row = row, reviewCount = index, onOpenReview = { }) }
                }
            }
        }
    }
}
