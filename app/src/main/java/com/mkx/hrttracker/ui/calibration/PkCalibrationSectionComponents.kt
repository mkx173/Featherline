package com.mkx.hrttracker.ui.calibration

import android.icu.text.ListFormatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationLabIgnoreReason
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.medication.medicationApplicationIconRes
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.labelRes
import com.mkx.hrttracker.util.rememberAppLocale
import java.util.UUID

/**
 * The calibration status section shown at the top of the calibration screen.
 * Only composed while a validated [PkCalibrationUiState] exists — never
 * synthesized from raw fits. The status row opens How it works; the route
 * card lists adjusted routes only and carries the review queue entry.
 */
@Composable
fun PkCalibrationSection(
    uiState: PkCalibrationUiState,
    reviewCount: Int,
    onRetry: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenReview: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
    // Target range summary shown as the last row; null hides it.
    targetRange: String? = null,
) {
    val adjustedRows = if (uiState.globalState == PkCalibrationGlobalState.READY) {
        uiState.routeRows.filter { row -> row.displayState.isAdjusted }
    } else {
        emptyList()
    }
    HrtSection(
        title = stringResource(R.string.calibration_pk_section_title),
        modifier = modifier,
        topPadding = false,
    ) {
        item {
            PkCalibrationStatusRow(uiState = uiState, onInfo = onInfo)
        }
        // Other non-READY states carry their call to action in the body copy
        // ("add an E2 result").
        if (uiState.numericFailure) {
            item {
                PreferenceSegmentedListItem(
                    title = stringResource(R.string.calibration_pk_retry),
                    leadingContent = { PkCalibrationRowIcon(R.drawable.ic_restart_alt) },
                    onClick = onRetry,
                )
            }
        }
        if (adjustedRows.isNotEmpty()) {
            item {
                PkCalibrationRouteSummaryCard(
                    rows = adjustedRows,
                    routeCount = uiState.routeRows.size,
                    reviewCount = reviewCount,
                    onOpen = onOpenRoutes,
                    onOpenReview = onOpenReview,
                )
            }
        } else if (reviewCount > 0) {
            // An invalid value can need review before any route is adjusted.
            item {
                PreferenceSegmentedListItem(
                    title = pluralStringResource(
                        R.plurals.calibration_pk_review_count,
                        reviewCount,
                        reviewCount,
                    ),
                    leadingContent = { PkCalibrationRowIcon(R.drawable.ic_error_outline) },
                    trailingContent = { PkCalibrationRowChevron() },
                    onClick = onOpenReview,
                )
            }
        }
        targetRange?.let { summary ->
            item { CalibrationTargetRangeRow(summary = summary) }
        }
    }
}

@Composable
private fun PkCalibrationRowChevron(tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = tint,
    )
}

@Composable
private fun PkCalibrationRowIcon(
    @DrawableRes iconRes: Int,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = tint,
    )
}

/**
 * Global status row: one row, never five failures. Tapping it opens How it
 * works. Adjusted and population states point there; the other states keep
 * their call to action as the body.
 */
@Composable
private fun PkCalibrationStatusRow(
    uiState: PkCalibrationUiState,
    onInfo: () -> Unit,
) {
    val ready = uiState.globalState == PkCalibrationGlobalState.READY
    val adjusted = ready && uiState.adjusted
    val iconRes: Int
    val title: String
    val body: String
    when {
        !ready -> {
            iconRes = when (uiState.globalState) {
                PkCalibrationGlobalState.NO_DOSE_HISTORY -> R.drawable.ic_medication
                PkCalibrationGlobalState.NO_USABLE_LABS -> R.drawable.ic_labs
                else -> R.drawable.ic_sync_alt
            }
            title = stringResource(requireNotNull(uiState.globalState.statusTitleRes))
            body = stringResource(requireNotNull(uiState.globalState.statusBodyRes))
        }

        // A joint-solve failure keeps READY (so the lab rows survive) but
        // every route is a numeric failure: same row as the global state.
        uiState.numericFailure -> {
            iconRes = R.drawable.ic_sync_alt
            title = stringResource(
                requireNotNull(PkCalibrationGlobalState.NUMERIC_FAILURE.statusTitleRes)
            )
            body = stringResource(
                requireNotNull(PkCalibrationGlobalState.NUMERIC_FAILURE.statusBodyRes)
            )
        }

        !adjusted -> {
            iconRes = R.drawable.ic_labs
            title = stringResource(R.string.calibration_pk_status_population_title)
            body = stringResource(R.string.calibration_pk_status_info_hint)
        }

        else -> {
            iconRes = if (uiState.limitedConfidence) {
                R.drawable.ic_experiment
            } else {
                R.drawable.ic_check_circle
            }
            title = stringResource(R.string.calibration_pk_status_adjusted_title)
            body = stringResource(R.string.calibration_pk_status_info_hint)
        }
    }

    PreferenceSegmentedListItem(
        title = title,
        supportingText = body,
        onClick = onInfo,
        leadingContent = {
            PkCalibrationRowIcon(
                iconRes = iconRes,
                tint = if (adjusted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = stringResource(R.string.calibration_pk_edu_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/**
 * One sub-card per adjusted route (population routes live in the routes
 * sheet), plus the review-queue entry when results need checking.
 */
@Composable
private fun PkCalibrationRouteSummaryCard(
    rows: List<PkCalibrationRouteRowUiState>,
    routeCount: Int,
    reviewCount: Int,
    onOpen: () -> Unit,
    onOpenReview: () -> Unit,
) {
    EditorSegmentedListItem(
        onClick = onOpen,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val title = stringResource(R.string.calibration_pk_routes_card_title)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .cjkTextOffset(title),
                )
                Text(
                    text = stringResource(
                        R.string.calibration_pk_routes_card_adjusted_of,
                        rows.size,
                        routeCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PkCalibrationRowChevron()
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                rows.forEach { row -> PkCalibrationRouteSummaryCell(row = row) }
                if (reviewCount > 0) {
                    PkCalibrationReviewEntry(
                        count = reviewCount,
                        onClick = onOpenReview,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Tonal sub-card inside the route card that opens the review queue. */
@Composable
private fun PkCalibrationReviewEntry(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PkCalibrationRowIcon(R.drawable.ic_error_outline, tint = contentColor)
        val text = pluralStringResource(R.plurals.calibration_pk_review_count, count, count)
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            modifier = Modifier
                .weight(1f)
                .cjkTextOffset(text),
        )
        PkCalibrationRowChevron(tint = contentColor)
    }
}

/** Tonal sub-card for one adjusted route: tile, name, labs and confidence. */
@Composable
private fun PkCalibrationRouteSummaryCell(row: PkCalibrationRouteRowUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PkCalibrationRouteTile(
            route = row.route,
            size = 32.dp,
            iconSize = 18.dp,
            shape = MaterialTheme.shapes.small,
        )
        Column(modifier = Modifier.weight(1f)) {
            val name = stringResource(row.route.applicationType.labelRes)
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.cjkTextOffset(name),
            )
            pkCalibrationRouteMeta(row)?.let { meta ->
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.cjkTextOffset(meta),
                )
            }
        }
        row.confidence?.let { confidence -> PkCalibrationConfidenceBars(confidence) }
    }
}

/** Supporting line for a route: labs and confidence when adjusted, the population tag otherwise. */
@Composable
internal fun pkCalibrationRouteMeta(row: PkCalibrationRouteRowUiState): String? {
    if (!row.displayState.isAdjusted) {
        return row.displayState.tagRes?.let { tag -> stringResource(tag) }
    }
    val labs = pluralStringResource(
        R.plurals.calibration_pk_supporting_lab_count,
        row.supportingLabCount,
        row.supportingLabCount,
    )
    return row.confidence?.let { confidence ->
        stringResource(
            R.string.calibration_pk_route_meta_confidence,
            labs,
            stringResource(confidence.labelRes),
        )
    } ?: labs
}

/**
 * Compact trailing chip on a lab list row. Accepted results carry none: they
 * look like any other included result.
 */
@Composable
internal fun PkCalibrationLabChip(flag: PkCalibrationLabRowFlag) {
    val (textRes, iconRes, container, content) = when {
        flag is PkCalibrationLabRowFlag.UnreviewedOutlier -> PkChipStyle(
            R.string.calibration_pk_lab_chip_check,
            R.drawable.ic_error_outline,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )

        flag.needsReview -> PkChipStyle(
            R.string.calibration_pk_lab_chip_check,
            R.drawable.ic_error_outline,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )

        flag is PkCalibrationLabRowFlag.Excluded -> PkChipStyle(
            R.string.calibration_pk_lab_chip_excluded,
            R.drawable.ic_block,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        flag is PkCalibrationLabRowFlag.Ignored -> PkChipStyle(
            R.string.calibration_pk_lab_chip_not_used,
            R.drawable.ic_info,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> return
    }
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(start = 6.dp, top = 3.dp, end = 8.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
        )
    }
}

private data class PkChipStyle(
    @StringRes val textRes: Int,
    @DrawableRes val iconRes: Int,
    val container: Color,
    val content: Color,
)

/** Route colour tile: the route's medication-group container with its application icon. */
@Composable
internal fun PkCalibrationRouteTile(
    route: PkCalibrationRoute,
    size: Dp,
    iconSize: Dp,
    shape: Shape,
) {
    val routeScheme = rememberMedicationGroupColorScheme(
        colorKey = route.medicationGroupColorKey,
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(routeScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(medicationApplicationIconRes(route.applicationType)),
            contentDescription = null,
            tint = routeScheme.onPrimaryContainer,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Three-bar signal glyph for the coarse confidence tier: LOW fills one bar,
 * MEDIUM two, HIGH three; the rest stay outline-variant. Announced by the
 * tier word so colour is never the sole carrier.
 */
@Composable
internal fun PkCalibrationConfidenceBars(
    confidence: PkCalibrationRouteConfidence,
    modifier: Modifier = Modifier,
) {
    val filled = when (confidence) {
        PkCalibrationRouteConfidence.LOW -> 1
        PkCalibrationRouteConfidence.MEDIUM -> 2
        PkCalibrationRouteConfidence.HIGH -> 3
    }
    val description = stringResource(confidence.labelRes)
    val filledColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier
            .height(10.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        listOf(4.dp, 7.dp, 10.dp).forEachIndexed { index, barHeight ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (index < filled) filledColor else emptyColor),
            )
        }
    }
}

/**
 * Review note for one E2 result, shared by the review queue (under the lab
 * row) and the result editor so both offer the same actions. Value
 * correction rides the existing lab-edit path via [onCorrect]; null hides it
 * where the value field is already on screen.
 */
@Composable
fun PkCalibrationLabRowFooter(
    flag: PkCalibrationLabRowFlag,
    onCorrect: (() -> Unit)?,
    onExclude: () -> Unit,
    onReinclude: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val note = modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceContainer)
    when (flag) {
        is PkCalibrationLabRowFlag.Accepted -> PkCalibrationNoteRow(
            iconRes = R.drawable.ic_check_circle,
            text = stringResource(R.string.calibration_pk_lab_accepted_body),
            modifier = note.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        ) {
            TextButton(onClick = onReinclude, enabled = enabled) {
                Text(text = stringResource(R.string.calibration_pk_lab_show_review))
            }
        }

        is PkCalibrationLabRowFlag.Ignored -> when (flag.reason) {
            PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE -> PkCalibrationLabNote(
                title = stringResource(R.string.calibration_pk_lab_invalid_title),
                body = stringResource(R.string.calibration_pk_lab_invalid_body),
                modifier = note,
            ) {
                TextButton(onClick = onExclude, enabled = enabled) {
                    Text(text = stringResource(R.string.calibration_pk_lab_invalid_exclude))
                }
                if (onCorrect != null) {
                    HrtFilledTonalButton(
                        text = stringResource(R.string.calibration_pk_lab_invalid_correct),
                        onClick = onCorrect,
                        enabled = enabled,
                        compact = true,
                    )
                }
            }

            PkCalibrationLabIgnoreReason.BELOW_INFORMATIVE_SIGNAL -> PkCalibrationNoteRow(
                iconRes = R.drawable.ic_info,
                text = stringResource(R.string.calibration_pk_lab_ignored_signal_note),
                modifier = note.padding(horizontal = 12.dp, vertical = 10.dp),
            )

            PkCalibrationLabIgnoreReason.NUMERIC_FAILURE -> PkCalibrationNoteRow(
                iconRes = R.drawable.ic_info,
                text = stringResource(R.string.calibration_pk_lab_ignored_numeric_note),
                modifier = note.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        is PkCalibrationLabRowFlag.UnreviewedOutlier -> {
            val appLocale = rememberAppLocale()
            val names = flag.affectedRoutes
                .map { route -> stringResource(route.applicationType.labelRes) }
            val joinedNames = remember(names, appLocale) {
                ListFormatter.getInstance(appLocale).format(names)
            }
            PkCalibrationLabNote(
                title = stringResource(R.string.calibration_pk_lab_outlier_title),
                body = stringResource(R.string.calibration_pk_lab_outlier_body, joinedNames),
                modifier = note,
            ) {
                TextButton(onClick = onExclude, enabled = enabled) {
                    Text(text = stringResource(R.string.calibration_pk_lab_outlier_exclude))
                }
                HrtFilledTonalButton(
                    text = stringResource(R.string.calibration_pk_lab_accept),
                    onClick = onAccept,
                    enabled = enabled,
                    compact = true,
                )
            }
        }

        is PkCalibrationLabRowFlag.Excluded -> PkCalibrationNoteRow(
            iconRes = R.drawable.ic_block,
            text = stringResource(R.string.calibration_pk_lab_excluded_note),
            modifier = note.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        ) {
            TextButton(onClick = onReinclude, enabled = enabled) {
                Text(text = stringResource(R.string.calibration_pk_lab_reinclude))
            }
        }
    }
}

/** Titled review note with end-aligned [actions]. */
@Composable
private fun PkCalibrationLabNote(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(end = 8.dp),
        ) {
            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_error_outline),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.cjkTextOffset(title),
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.cjkTextOffset(body),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Icon + one line of note text, with an optional trailing [action]. */
@Composable
internal fun PkCalibrationNoteRow(
    @DrawableRes iconRes: Int,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
        action?.invoke()
    }
}

@Preview(name = "PK Section · Ready", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationSectionReadyPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        PkCalibrationSection(
            uiState = previewPkAdjustedUiState,
            reviewCount = 2,
            onRetry = { },
            onOpenRoutes = { },
            onOpenReview = { },
            onInfo = { },
        )
    }
}

/** Non-READY hides the routes card; pending reviews get their own row. */
@Preview(name = "PK Section · Not ready", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationSectionNotReadyPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        PkCalibrationSection(
            uiState = previewPkUiState(globalState = PkCalibrationGlobalState.NO_USABLE_LABS),
            reviewCount = 2,
            onRetry = { },
            onOpenRoutes = { },
            onOpenReview = { },
            onInfo = { },
        )
    }
}

/**
 * The visually distinct states: plain (all non-READY and population states
 * share it, only copy and icon change), numeric failure, adjusted, adjusted
 * with limited confidence.
 */
@Preview(name = "PK Status Row · states", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationStatusRowPreview() {
    val states = listOf(
        previewPkUiState(globalState = PkCalibrationGlobalState.NO_USABLE_LABS),
        previewPkUiState(globalState = PkCalibrationGlobalState.NUMERIC_FAILURE),
        previewPkAdjustedUiState,
        previewPkProvisionalUiState,
    )
    PkCalibrationPreviewColumn {
        states.forEach { state ->
            PkCalibrationStatusRow(uiState = state, onInfo = { })
        }
    }
}

/** Two clean adjusted routes; three adjusted with results to check. */
@Preview(name = "PK Route Summary Card · states", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationRouteSummaryCardPreview() {
    val states = listOf(previewPkAdjustedUiState to 0, previewPkProvisionalUiState to 2)
    PkCalibrationPreviewColumn {
        states.forEach { (state, reviewCount) ->
            PkCalibrationRouteSummaryCard(
                rows = state.routeRows.filter { row -> row.displayState.isAdjusted },
                routeCount = state.routeRows.size,
                reviewCount = reviewCount,
                onOpen = { },
                onOpenReview = { },
            )
        }
    }
}

/** Every review note a result can carry, plus the list chips. */
@Preview(name = "PK Lab Row Footer · states", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationLabRowFooterPreview() {
    val resultId = UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a")
    val flags = listOf(
        PkCalibrationLabRowFlag.Ignored(resultId, PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE),
        PkCalibrationLabRowFlag.Ignored(resultId, PkCalibrationLabIgnoreReason.BELOW_INFORMATIVE_SIGNAL),
        PkCalibrationLabRowFlag.Ignored(resultId, PkCalibrationLabIgnoreReason.NUMERIC_FAILURE),
        PkCalibrationLabRowFlag.UnreviewedOutlier(
            resultId = resultId,
            affectedRoutes = listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.GEL),
        ),
        PkCalibrationLabRowFlag.Accepted(resultId),
        PkCalibrationLabRowFlag.Excluded(resultId),
    )
    PkCalibrationPreviewColumn {
        flags.forEach { flag ->
            EditorSegmentedListItem(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PkCalibrationLabChip(flag)
                    PkCalibrationLabRowFooter(
                        flag = flag,
                        onCorrect = { },
                        onExclude = { },
                        onReinclude = { },
                        onAccept = { },
                    )
                }
            }
        }
    }
}

@Composable
private fun PkCalibrationPreviewColumn(content: @Composable () -> Unit) {
    HrtTrackerTheme(dynamicColor = false) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            content()
        }
    }
}
