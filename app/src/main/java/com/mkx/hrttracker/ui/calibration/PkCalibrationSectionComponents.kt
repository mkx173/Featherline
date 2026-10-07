package com.mkx.hrttracker.ui.calibration

import android.icu.text.ListFormatter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
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
    // A snapshot rebuild is pending: the routes card is what can change, so
    // it carries the progress line while the previous rows stay up.
    isRefreshing: Boolean = false,
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
                    trailingContent = { PkCalibrationRowChevron() },
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
                    isRefreshing = isRefreshing,
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
                    leadingContent = {
                        PkCalibrationRowIcon(
                            R.drawable.ic_error_outline,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    },
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
                PkCalibrationGlobalState.NO_USABLE_LABS -> R.drawable.ic_add_chart
                else -> R.drawable.ic_help
            }
            title = stringResource(requireNotNull(uiState.globalState.statusTitleRes))
            body = stringResource(requireNotNull(uiState.globalState.statusBodyRes))
        }

        // A joint-solve failure keeps READY (so the lab rows survive) but
        // every route is a numeric failure: same row as the global state.
        uiState.numericFailure -> {
            iconRes = R.drawable.ic_help
            title = stringResource(
                requireNotNull(PkCalibrationGlobalState.NUMERIC_FAILURE.statusTitleRes)
            )
            body = stringResource(
                requireNotNull(PkCalibrationGlobalState.NUMERIC_FAILURE.statusBodyRes)
            )
        }

        !adjusted -> {
            iconRes = R.drawable.ic_add_chart
            title = stringResource(R.string.calibration_pk_status_population_title)
            body = stringResource(R.string.calibration_pk_status_info_hint)
        }

        else -> {
            iconRes = R.drawable.ic_experiment
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
    isRefreshing: Boolean = false,
) {
    // While a rebuild is pending the rows, the count and the chevron give
    // way to one progress line; the card is not clickable until the new
    // rows land. The height change animates both ways.
    EditorSegmentedListItem(
        onClick = if (isRefreshing) null else onOpen,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_route),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val title = stringResource(R.string.calibration_pk_routes_card_title)
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .cjkTextOffset(title),
                )
                if (!isRefreshing) {
                    val trailingText = stringResource(
                        R.string.calibration_pk_routes_card_adjusted_of,
                        rows.size,
                        routeCount,
                    )
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.cjkTextOffset(trailingText),
                    )
                    PkCalibrationRowChevron()
                }
            }
            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            } else {
                HrtSection(title = null, modifier = Modifier.padding(top = 12.dp)) {
                    rows.forEach { row -> item { PkCalibrationRouteSummaryCell(row = row) } }
                    if (reviewCount > 0) {
                        item { PkCalibrationReviewEntry(count = reviewCount, onClick = onOpenReview) }
                    }
                }
            }
        }
    }
}

/** Route-card sub-card that opens the review queue; same shape as a route. */
@Composable
private fun PkCalibrationReviewEntry(
    count: Int,
    onClick: () -> Unit,
) {
    PkCalibrationSubCard(
        title = pluralStringResource(R.plurals.calibration_pk_review_count, count, count),
        onClick = onClick,
        leading = {
            // 32dp slot keeps the title in line with the route tiles above.
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                PkCalibrationRowIcon(
                    R.drawable.ic_error_outline,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        },
        trailing = { PkCalibrationRowChevron() },
    )
}

/** Sub-card for one adjusted route: tile, name, labs and confidence. */
@Composable
private fun PkCalibrationRouteSummaryCell(row: PkCalibrationRouteRowUiState) {
    PkCalibrationSubCard(
        title = stringResource(row.route.applicationType.labelRes),
        supportingText = pkCalibrationRouteMeta(row),
        leading = {
            PkCalibrationRouteTile(
                route = row.route,
                size = 32.dp,
                iconSize = 18.dp,
                shape = MaterialTheme.shapes.small,
            )
        },
        trailing = row.confidence?.let { confidence ->
            {
                // Center in an icon-sized box so the bars line up with the
                // review entry's chevron, whose glyph sits inset in its 24dp box.
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    PkCalibrationConfidenceBars(confidence)
                }
            }
        },
    )
}

/** Tonal segmented row inside the route card: 32dp leading tile, title, optional supporting line. */
@Composable
private fun PkCalibrationSubCard(
    title: String,
    leading: @Composable () -> Unit,
    supportingText: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    PreferenceSegmentedListItem(
        title = title,
        supportingText = supportingText,
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        leadingContent = leading,
        trailingContent = trailing,
        titleTextStyle = MaterialTheme.typography.bodyMedium,
        supportingTextStyle = MaterialTheme.typography.bodySmall,
    )
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
 * Compact trailing icon on a lab list row; the label rides on the content
 * description. Kept outliers get a check so the result still reads as
 * decided.
 */
@Composable
internal fun PkCalibrationLabChip(flag: PkCalibrationLabRowFlag) {
    val (iconRes, descriptionRes, tint) = when {
        flag is PkCalibrationLabRowFlag.UnreviewedOutlier -> Triple(
            R.drawable.ic_error_outline,
            R.string.calibration_pk_lab_chip_check,
            MaterialTheme.colorScheme.tertiary,
        )

        flag.needsReview -> Triple(
            R.drawable.ic_error_outline,
            R.string.calibration_pk_lab_chip_check,
            MaterialTheme.colorScheme.error,
        )

        flag is PkCalibrationLabRowFlag.Ignored ||
            (flag is PkCalibrationLabRowFlag.Excluded && flag.dismissed) -> Triple(
            R.drawable.ic_info,
            R.string.calibration_pk_lab_chip_not_used,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        flag is PkCalibrationLabRowFlag.Excluded -> Triple(
            R.drawable.ic_block,
            R.string.calibration_pk_lab_chip_excluded,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        flag is PkCalibrationLabRowFlag.Accepted -> Triple(
            R.drawable.ic_check_circle,
            R.string.calibration_pk_lab_chip_dismissed,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> return
    }
    Icon(
        painter = painterResource(iconRes),
        contentDescription = stringResource(descriptionRes),
        tint = tint,
        modifier = Modifier.size(18.dp),
    )
}

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
 * One-line review state under a queue row: why it needs a look, or what the
 * user did in the editor. Null [flag] means an edit cleared the warning.
 */
@Composable
internal fun PkCalibrationReviewSummary(
    flag: PkCalibrationLabRowFlag?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (iconRes, text) = when (flag) {
        null -> R.drawable.ic_check_circle to stringResource(R.string.calibration_pk_review_summary_updated)
        is PkCalibrationLabRowFlag.Accepted ->
            R.drawable.ic_check_circle to stringResource(R.string.calibration_pk_review_summary_accepted)
        is PkCalibrationLabRowFlag.Excluded -> if (flag.dismissed) {
            R.drawable.ic_check_circle to stringResource(R.string.calibration_pk_review_summary_dismissed)
        } else {
            R.drawable.ic_block to stringResource(R.string.calibration_pk_review_summary_excluded)
        }
        is PkCalibrationLabRowFlag.UnreviewedOutlier -> R.drawable.ic_error_outline to stringResource(
            R.string.calibration_pk_review_summary_outlier,
            rememberPkRouteNames(flag.affectedRoutes),
        )
        is PkCalibrationLabRowFlag.Ignored -> when (flag.reason) {
            PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE ->
                R.drawable.ic_error_outline to stringResource(R.string.calibration_pk_review_summary_non_positive)
            PkCalibrationLabIgnoreReason.BELOW_INFORMATIVE_SIGNAL ->
                R.drawable.ic_info to stringResource(R.string.calibration_pk_lab_ignored_signal_note)
            PkCalibrationLabIgnoreReason.NUMERIC_FAILURE ->
                R.drawable.ic_info to stringResource(R.string.calibration_pk_lab_ignored_numeric_note)
        }
    }
    PkCalibrationNoteRow(
        iconRes = iconRes,
        text = text,
        tint = if (flag?.needsReview == true) colors.tertiary else colors.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/** Route labels joined for the app locale ("Injection and gel"). */
@Composable
private fun rememberPkRouteNames(routes: List<PkCalibrationRoute>): String {
    val appLocale = rememberAppLocale()
    val names = routes.map { route -> stringResource(route.applicationType.labelRes) }
    return remember(names, appLocale) { ListFormatter.getInstance(appLocale).format(names) }
}

/**
 * Review note for one E2 result in the result editor. The value field there
 * is the correction path, so the note only offers keep / dismiss / exclude / undo.
 */
@Composable
fun PkCalibrationLabRowFooter(
    flag: PkCalibrationLabRowFlag,
    onExclude: () -> Unit,
    onReinclude: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Transparent where the caller's card is already the surface (result editor).
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val note = modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(containerColor)
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
                body = stringResource(R.string.calibration_pk_lab_invalid_body),
                modifier = note,
            ) {
                TextButton(onClick = onExclude, enabled = enabled) {
                    Text(text = stringResource(R.string.calibration_pk_lab_dismiss))
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
            PkCalibrationLabNote(
                body = stringResource(
                    R.string.calibration_pk_lab_outlier_body,
                    rememberPkRouteNames(flag.affectedRoutes),
                ),
                modifier = note,
            ) {
                // Keep leaves the result at its reduced weight; Exclude drops it.
                TextButton(onClick = onAccept, enabled = enabled) {
                    Text(text = stringResource(R.string.calibration_pk_lab_keep))
                }
                HrtFilledTonalButton(
                    text = stringResource(R.string.calibration_pk_lab_outlier_exclude),
                    onClick = onExclude,
                    enabled = enabled,
                    compact = true,
                )
            }
        }

        is PkCalibrationLabRowFlag.Excluded -> PkCalibrationNoteRow(
            iconRes = if (flag.dismissed) R.drawable.ic_info else R.drawable.ic_block,
            text = stringResource(
                if (flag.dismissed) R.string.calibration_pk_lab_dismissed_note
                else R.string.calibration_pk_lab_excluded_note
            ),
            modifier = note.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        ) {
            TextButton(onClick = onReinclude, enabled = enabled) {
                Text(
                    text = stringResource(
                        if (flag.dismissed) R.string.calibration_pk_lab_show_review
                        else R.string.calibration_pk_lab_reinclude
                    )
                )
            }
        }
    }
}

/** Review note with end-aligned [actions]. */
@Composable
private fun PkCalibrationLabNote(
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    val bodyStyle = MaterialTheme.typography.bodyMedium
    // Center the icon on the first text line; tracks the user's font scale.
    val firstLineHeight = with(LocalDensity.current) {
        bodyStyle.lineHeight.takeOrElse { bodyStyle.fontSize }.toDp()
    }
    Column(
        modifier = modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(end = 8.dp),
        ) {
            Box(modifier = Modifier.height(firstLineHeight), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_error_outline),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = body,
                style = bodyStyle,
                modifier = Modifier
                    .weight(1f)
                    .cjkTextOffset(body),
            )
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
            targetRange = previewPkTargetRange,
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
            targetRange = previewPkTargetRange,
        )
    }
}

/** The live evaluation failed: the numeric-failure status row plus Try again. */
@Preview(name = "PK Section · Unavailable", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationSectionUnavailablePreview() {
    HrtTrackerTheme(dynamicColor = false) {
        PkCalibrationSection(
            uiState = pkCalibrationUnavailableScreenState().ui,
            reviewCount = 0,
            onRetry = { },
            onOpenRoutes = { },
            onOpenReview = { },
            onInfo = { },
            targetRange = previewPkTargetRange,
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

/** The card while a snapshot rebuild is pending: title only, one progress line in place of the rows. */
@Preview(name = "PK Route Summary Card · updating", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationRouteSummaryCardUpdatingPreview() {
    val state = previewPkAdjustedUiState
    PkCalibrationPreviewColumn {
        PkCalibrationRouteSummaryCard(
            rows = state.routeRows.filter { row -> row.displayState.isAdjusted },
            routeCount = state.routeRows.size,
            reviewCount = 0,
            isRefreshing = true,
            onOpen = { },
            onOpenReview = { },
        )
    }
}

/** Every review note the result editor can show, one card per state. */
@Preview(name = "PK Lab Note · editor states", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationLabRowFooterPreview() {
    PkCalibrationPreviewColumn {
        previewPkLabFlags.forEach { flag ->
            // Same surface as the editor: the card is the note's background.
            EditorSegmentedListItem(contentPadding = PaddingValues(4.dp)) {
                PkCalibrationLabRowFooter(
                    flag = flag,
                    onExclude = { },
                    onReinclude = { },
                    onAccept = { },
                    containerColor = Color.Transparent,
                )
            }
        }
    }
}

/** Review sheet subcards: open reasons, then what the user did; null = fixed in the editor. */
@Preview(name = "PK Review Summary · states", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationReviewSummaryPreview() {
    PkCalibrationPreviewColumn {
        (previewPkLabFlags + null).forEach { flag ->
            EditorSegmentedListItem(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                PkCalibrationReviewSummary(flag = flag)
            }
        }
    }
}

/** Trailing chips on calibration list rows, one per flag. */
@Preview(name = "PK Lab Chip · states", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationLabChipPreview() {
    PkCalibrationPreviewColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            previewPkLabFlags.forEach { flag -> PkCalibrationLabChip(flag) }
        }
    }
}

private val previewPkLabResultId = UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a")

private val previewPkLabFlags = listOf(
    PkCalibrationLabRowFlag.UnreviewedOutlier(
        resultId = previewPkLabResultId,
        affectedRoutes = listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.GEL),
    ),
    PkCalibrationLabRowFlag.Ignored(previewPkLabResultId, PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE),
    PkCalibrationLabRowFlag.Ignored(previewPkLabResultId, PkCalibrationLabIgnoreReason.BELOW_INFORMATIVE_SIGNAL),
    PkCalibrationLabRowFlag.Ignored(previewPkLabResultId, PkCalibrationLabIgnoreReason.NUMERIC_FAILURE),
    PkCalibrationLabRowFlag.Accepted(previewPkLabResultId),
    PkCalibrationLabRowFlag.Excluded(previewPkLabResultId),
    PkCalibrationLabRowFlag.Excluded(previewPkLabResultId, dismissed = true),
)

private const val previewPkTargetRange = "Estradiol: 100–200 pg/mL\nTestosterone: <50 ng/dL"

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
