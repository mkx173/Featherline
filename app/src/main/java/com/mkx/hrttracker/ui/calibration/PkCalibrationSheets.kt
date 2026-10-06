package com.mkx.hrttracker.ui.calibration

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.mkx.hrttracker.ui.components.MedicalDisclaimerText
import androidx.compose.ui.res.dimensionResource
import com.mkx.hrttracker.ui.components.HrtPillSize
import com.mkx.hrttracker.ui.components.HrtPill
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
    title: String?,
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
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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

// ---------------------------------------------------------------------------
// Routes detail sheet
// ---------------------------------------------------------------------------

/**
 * Adjusted routes first, each with a short warning line; population routes
 * follow in their own group. Results to check live on the section's banner.
 */
@Composable
fun PkCalibrationRoutesSheet(
    uiState: PkCalibrationUiState,
    onDismissRequest: () -> Unit,
) {
    PkCalibrationSheet(
        title = stringResource(R.string.calibration_pk_routes_card_title),
        onDismissRequest = onDismissRequest,
    ) {
        val (adjusted, population) = uiState.routeRows.partition { row -> row.displayState.isAdjusted }
        if (adjusted.isNotEmpty()) {
            HrtSection(title = stringResource(R.string.calibration_pk_hero_adjusted)) {
                adjusted.forEach { row ->
                    item { PkCalibrationRouteCard(row) }
                }
            }
        }
        if (population.isNotEmpty()) {
            HrtSection(
                title = stringResource(R.string.calibration_pk_hero_population),
            ) {
                population.forEach { row ->
                    item { PkCalibrationRouteCard(row) }
                }
            }
        }
    }
}

/**
 * Route card: tile, name, supporting line (results + confidence). Adjusted
 * routes add short warning pills (the adjustment applies regardless);
 * outliers are left to the review banner. Population routes are a bare name
 * unless the fit failed for them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PkCalibrationRouteCard(row: PkCalibrationRouteRowUiState) {
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
                    // "No lab signal" on every population row is noise under its own
                    // header; only a failed fit is worth calling out.
                    pkCalibrationRouteMeta(row)
                        ?.takeIf { row.displayState != PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL }
                        ?.let {
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
            // Low confidence already says "uncertain"; outliers live on the review banner.
            val warnings = row.reasons.filterNot { reason ->
                reason == PkCalibrationReason.UNREVIEWED_OUTLIER ||
                    (reason == PkCalibrationReason.UNCERTAIN &&
                        row.confidence == PkCalibrationRouteConfidence.LOW)
            }
            if (adjusted && warnings.isNotEmpty()) {
                FlowRow(
                    // Aligns under the name (tile 34 + gap 12).
                    modifier = Modifier.padding(start = 46.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    warnings.forEach { reason ->
                        HrtPill(
                            label = stringResource(reason.labelRes),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            size = HrtPillSize.Small,
                        )
                    }
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
 * Two pages: what lab adjustment is, then how to help a route calibrate.
 * Next advances, Back returns, Finish (last page) dismisses. Pages slide in
 * the direction of travel; the sheet wraps and animates between their heights.
 */
@Composable
fun PkCalibrationEduSheet(onDismissRequest: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val lastPage = page == 1
    PkCalibrationSheet(
        // The title slides with its page below.
        title = null,
        onDismissRequest = onDismissRequest,
        confirmButtonText = stringResource(
            if (lastPage) R.string.calibration_pk_edu_finish else R.string.calibration_pk_edu_next
        ),
        onConfirm = if (lastPage) null else ({ page = 1 }),
        secondaryButtonText = if (lastPage) stringResource(R.string.calibration_pk_edu_back) else null,
        onSecondary = if (lastPage) ({ page = 0 }) else null,
    ) {
        // Each page carries its own disclaimer so it slides with the page.
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally { width -> if (forward) width else -width } + fadeIn()) togetherWith
                    (slideOutHorizontally { width -> if (forward) -width else width } + fadeOut()) using
                    SizeTransform()
            },
            label = "pkEduPage",
        ) { shownPage ->
            Column {
                Text(
                    text = stringResource(
                        if (shownPage == 1) R.string.calibration_pk_coaching_row_title
                        else R.string.calibration_pk_edu_title
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
                if (shownPage == 1) PkCalibrationCoachingPage() else PkCalibrationEduPage()
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                MedicalDisclaimerText(
                    kinds = listOf(
                        if (shownPage == 1) MedicalDisclaimerKind.LAB_ADJUSTMENT_COACHING
                        else MedicalDisclaimerKind.LAB_ADJUSTMENT
                    )
                )
            }
        }
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
    // "Not a measurement" and "not dosing advice" are the sheet's disclaimer.
    PkCalibrationEduHeader(R.string.calibration_pk_edu_doesnt_header)
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
                rows.forEach { row ->
                    item { PkCalibrationRouteCard(row) }
                }
            }
        }
    }
}
