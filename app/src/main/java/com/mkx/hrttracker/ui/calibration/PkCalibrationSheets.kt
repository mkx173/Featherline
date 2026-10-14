package com.mkx.hrttracker.ui.calibration

import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.heightIn
import com.mkx.hrttracker.ui.components.SupportMessageListItem
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.MedicalDisclaimerKind
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.labelRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

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
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
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

/** Icon-box + title + body card shared by the edu sheet's pages; lives in an HrtSection. */
@Composable
private fun PkCalibrationSheetItem(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
) {
    PreferenceSegmentedListItem(
        title = stringResource(titleRes),
        supportingText = stringResource(bodyRes),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        titleTextStyle = MaterialTheme.typography.bodyMedium,
        supportingTextStyle = MaterialTheme.typography.bodySmall,
        leadingContent = { PkCalibrationSheetIcon(iconRes) },
    )
}

/** The edu cards' tinted 36dp icon box. */
@Composable
private fun PkCalibrationSheetIcon(@DrawableRes iconRes: Int) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(MaterialTheme.shapes.small)
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
}

// ---------------------------------------------------------------------------
// Routes detail sheet
// ---------------------------------------------------------------------------

/**
 * Adjusted routes first, each with a short warning line; population routes
 * follow in their own group. Results to check live on the section's banner.
 * Grouping is the fit's: a calibrated route stays under Adjusted even where
 * its share of the charted estimate is too small to shape it (so the hero
 * may say "population"); that row says so with a pill instead of being
 * relabeled. [smallShareRoutes] names those rows. Home passes the set judged
 * over its own chart range (the snapshot's), so icon and pill never disagree.
 * A null [uiState] (Home, before the first evaluation) shows a loading
 * indicator, then fades to the rows while the sheet grows to their height; a
 * state with no rows shows the app's one-line empty state instead.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PkCalibrationRoutesSheet(
    uiState: PkCalibrationUiState?,
    onDismissRequest: () -> Unit,
    title: String = stringResource(R.string.calibration_pk_routes_card_title),
    disclaimerKinds: List<MedicalDisclaimerKind> = emptyList(),
    smallShareRoutes: Set<PkCalibrationRoute> = uiState?.smallShareRoutes.orEmpty(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Rows that land while the sheet is still opening wait for it to settle:
    // a size change mid-open restarts the open animation every frame, so the
    // growing content outruns the sheet and pushes its bottom off screen.
    // Latched, so the hide animation never drops back to loading.
    var sheetSettled by remember { mutableStateOf(uiState != null) }
    LaunchedEffect(sheetState) {
        snapshotFlow {
            sheetState.currentValue == SheetValue.Expanded && !sheetState.isAnimationRunning
        }.first { it }
        sheetSettled = true
    }
    PkCalibrationSheet(
        title = title,
        onDismissRequest = onDismissRequest,
        disclaimerKinds = disclaimerKinds,
        sheetState = sheetState,
    ) {
        // Keyed on loading only, so a re-evaluation swaps rows in place
        // instead of cross-fading the whole list.
        AnimatedContent(
            targetState = uiState.takeIf { sheetSettled },
            contentKey = { state -> state == null },
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform() },
            label = "pkRoutesSheetLoading",
        ) { state ->
            if (state == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = dimensionResource(R.dimen.padding_large)),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            } else {
                Column { PkCalibrationRoutesSheetContent(state, smallShareRoutes) }
            }
        }
    }
}

@Composable
private fun PkCalibrationRoutesSheetContent(
    state: PkCalibrationUiState,
    smallShareRoutes: Set<PkCalibrationRoute>,
) {
    val emptyTextRes = pkCalibrationRoutesSheetEmptyTextRes(state)
    if (emptyTextRes != null) {
        SupportMessageListItem(
            text = stringResource(emptyTextRes),
            painter = painterResource(R.drawable.ic_info),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
        return
    }
    val (adjusted, population) = state.routeRows.partition { row -> row.displayState.isAdjusted }
    // Sections spaced like every page's consecutive sections; the first
    // drops its header's top padding so it sits on the scaffold's title gap.
    if (adjusted.isNotEmpty()) {
        HrtSection(
            title = stringResource(R.string.calibration_pk_hero_adjusted),
            topPadding = false,
        ) {
            adjusted.forEach { row ->
                item {
                    PkCalibrationRouteCard(
                        row = row,
                        shapesEstimate = row.route !in smallShareRoutes,
                    )
                }
            }
        }
    }
    if (population.isNotEmpty()) {
        if (adjusted.isNotEmpty()) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
        }
        HrtSection(
            title = stringResource(R.string.calibration_pk_hero_population),
            topPadding = adjusted.isNotEmpty(),
        ) {
            population.forEach { row ->
                item { PkCalibrationRouteCard(row) }
            }
        }
    }
}

/**
 * Route card: tile, name, supporting line (results + confidence). Adjusted
 * routes add short warning pills (the adjustment applies regardless), and a
 * "small share" pill when [shapesEstimate] is false: the route is calibrated
 * but is under the support share of the estimate across the chart range, so
 * the hero does not count it. Outliers are left to the review banner.
 * Population routes are a bare name unless the fit failed for them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PkCalibrationRouteCard(
    row: PkCalibrationRouteRowUiState,
    shapesEstimate: Boolean = true,
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
                    size = 36.dp,
                    iconSize = 20.dp,
                    shape = MaterialTheme.shapes.small,
                )
                Column(modifier = Modifier.weight(1f)) {
                    val name = stringResource(row.route.applicationType.labelRes)
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                    )
                    // "No lab signal" on every population row is noise under its own
                    // header; only a failed fit is worth calling out.
                    pkCalibrationRouteMeta(row)
                        ?.takeIf { row.displayState != PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL }
                        ?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
                row.confidence?.let {
                    confidence ->
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        PkCalibrationConfidenceBars(confidence)
                    }
                }
            }
            // Low confidence already says "uncertain"; outliers live on the review banner.
            val warnings = row.reasons.filterNot { reason ->
                reason == PkCalibrationReason.UNREVIEWED_OUTLIER ||
                    (reason == PkCalibrationReason.UNCERTAIN &&
                        row.confidence == PkCalibrationRouteConfidence.LOW)
            }
            val pills = warnings.map { reason -> stringResource(reason.labelRes) } +
                listOfNotNull(
                    stringResource(R.string.calibration_pk_route_pill_small_share)
                        .takeIf { !shapesEstimate }
                )
            if (adjusted && pills.isNotEmpty()) {
                FlowRow(
                    // Aligns under the name (tile 34 + gap 12).
                    modifier = Modifier.padding(start = 46.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pills.forEach { label ->
                        HrtPill(
                            label = label,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            size = HrtPillSize.Small,
                            fontWeight = FontWeight.SemiBold,
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

/**
 * One card per lab that needs review; the caller renders each lab row and
 * owns [sheetState] so a row tap can hide the sheet before navigating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PkCalibrationReviewSheet(
    count: Int,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    PkCalibrationSheet(
        title = pluralStringResource(R.plurals.calibration_pk_review_count, count, count),
        onDismissRequest = onDismissRequest,
        confirmButtonText = stringResource(R.string.journal_done),
        sheetState = sheetState,
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PkCalibrationEduSheet(
    onDismissRequest: () -> Unit,
    // Previews open straight onto the second page.
    initialPage: Int = 0,
) {
    var page by rememberSaveable { mutableIntStateOf(initialPage) }
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
                // The scaffold header's box, so this page title sits where
                // every other sheet title does.
                Box(
                    modifier = Modifier.heightIn(min = ButtonDefaults.MinHeight),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(
                            if (shownPage == 1) R.string.calibration_pk_coaching_row_title
                            else R.string.calibration_pk_edu_title
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
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
    // Sits on the scaffold's title gap, so its header drops the top padding.
    HrtSection(
        title = stringResource(R.string.calibration_pk_edu_does_header),
        topPadding = false,
    ) {
        item {
            PkCalibrationSheetItem(
                iconRes = R.drawable.ic_experiment,
                titleRes = R.string.calibration_pk_edu_intro_title,
                bodyRes = R.string.calibration_pk_edu_intro,
            )
        }
        item {
            PkCalibrationSheetItem(
                iconRes = R.drawable.ic_route,
                titleRes = R.string.calibration_pk_edu_does_routes_title,
                bodyRes = R.string.calibration_pk_edu_does_routes_body,
            )
        }
        item {
            PkCalibrationSheetItem(
                iconRes = R.drawable.ic_arrow_range,
                titleRes = R.string.calibration_pk_edu_does_band_title,
                bodyRes = R.string.calibration_pk_edu_does_band_body,
            )
        }
    }
    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
    // "Not a measurement" and "not dosing advice" are the sheet's disclaimer.
    HrtSection(title = stringResource(R.string.calibration_pk_edu_doesnt_header)) {
        item {
            PkCalibrationSheetItem(
                iconRes = R.drawable.ic_block,
                titleRes = R.string.calibration_pk_edu_doesnt_verify_title,
                bodyRes = R.string.calibration_pk_edu_doesnt_verify_body,
            )
        }
        item {
            PkCalibrationSheetItem(
                iconRes = R.drawable.ic_block,
                titleRes = R.string.calibration_pk_edu_doesnt_learn_title,
                bodyRes = R.string.calibration_pk_edu_doesnt_learn_body,
            )
        }
    }
}

@Composable
private fun PkCalibrationCoachingPage() {
    HrtSection(title = null) {
        item {
            PkCalibrationSheetItem(
                iconRes = R.drawable.ic_schedule,
                titleRes = R.string.calibration_pk_coaching_time_title,
                bodyRes = R.string.calibration_pk_coaching_time_body,
            )
        }
        item {
            PkCalibrationSheetItem(
                iconRes = R.drawable.ic_history,
                titleRes = R.string.calibration_pk_coaching_history_title,
                bodyRes = R.string.calibration_pk_coaching_history_body,
            )
        }
        item {
            PkCalibrationSheetItem(
                iconRes = R.drawable.ic_biotech,
                titleRes = R.string.calibration_pk_coaching_assay_title,
                bodyRes = R.string.calibration_pk_coaching_assay_body,
            )
        }
        item {
            PkCalibrationSheetItem(
                iconRes = R.drawable.ic_multiple_stop,
                titleRes = R.string.calibration_pk_coaching_variety_title,
                bodyRes = R.string.calibration_pk_coaching_variety_body,
            )
        }
    }
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

/** Home's variant (estimate title + disclaimer) over a mixed fit: both groups, every warning pill. */
@Preview(name = "PK Routes Sheet · Home", showBackground = true, widthDp = 420, heightDp = 920)
@Composable
private fun PkCalibrationRoutesSheetPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        PkCalibrationRoutesSheet(
            uiState = previewPkProvisionalUiState,
            onDismissRequest = {},
            title = stringResource(R.string.main_e2_estimate_sheet_title),
            disclaimerKinds = listOf(MedicalDisclaimerKind.PLASMA_CONCENTRATION_ESTIMATES),
        )
    }
}

@Preview(name = "PK Edu Sheet · what it is", showBackground = true, widthDp = 420, heightDp = 920)
@Composable
private fun PkCalibrationEduSheetPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        PkCalibrationEduSheet(onDismissRequest = {})
    }
}

@Preview(name = "PK Edu Sheet · helping it calibrate", showBackground = true, widthDp = 420, heightDp = 920)
@Composable
private fun PkCalibrationEduSheetCoachingPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        PkCalibrationEduSheet(onDismissRequest = {}, initialPage = 1)
    }
}
