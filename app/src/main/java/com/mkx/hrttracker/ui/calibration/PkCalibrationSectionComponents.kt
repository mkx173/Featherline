package com.mkx.hrttracker.ui.calibration

import android.icu.text.ListFormatter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * The calibration status section shown at the top of the calibration screen
 * (plan D1). Only composed while a validated [PkCalibrationUiState] exists —
 * never synthesized from raw fits.
 */
@Composable
fun PkCalibrationSection(
    uiState: PkCalibrationUiState,
    onRetry: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenCoaching: () -> Unit,
    onOpenDisclaimer: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = uiState.globalState == PkCalibrationGlobalState.READY
    HrtSection(
        title = stringResource(R.string.calibration_pk_section_title),
        modifier = modifier,
        topPadding = false,
    ) {
        item {
            PkCalibrationStatusCard(
                uiState = uiState,
                onRetry = onRetry,
                onInfo = onInfo,
            )
        }
        if (ready) {
            item {
                PkCalibrationRouteSummaryCard(uiState = uiState, onOpen = onOpenRoutes)
            }
            item {
                PreferenceSegmentedListItem(
                    title = stringResource(R.string.calibration_pk_coaching_row_title),
                    supportingText = stringResource(R.string.calibration_pk_coaching_row_subtitle),
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_help),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = { PkCalibrationRowChevron() },
                    onClick = onOpenCoaching,
                )
            }
            item {
                PreferenceSegmentedListItem(
                    title = stringResource(R.string.calibration_pk_disclaimer_row_title),
                    supportingText = stringResource(R.string.calibration_pk_disclaimer_row_subtitle),
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_privacy_tip),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = { PkCalibrationRowChevron() },
                    onClick = onOpenDisclaimer,
                )
            }
        }
    }
}

@Composable
private fun PkCalibrationRowChevron() {
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Global status card (handoff §4/§5.1): one card, never five failures. */
@Composable
private fun PkCalibrationStatusCard(
    uiState: PkCalibrationUiState,
    onRetry: () -> Unit,
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
        // every route is a numeric failure: same card as the global state.
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
            body = stringResource(R.string.calibration_pk_status_population_body)
        }

        else -> {
            iconRes = if (uiState.limitedConfidence) {
                R.drawable.ic_experiment
            } else {
                R.drawable.ic_check_circle
            }
            title = stringResource(R.string.calibration_pk_status_adjusted_title)
            val appLocale = rememberAppLocale()
            val names = uiState.effectivePromotedRoutes
                .map { route -> stringResource(route.applicationType.labelRes) }
            val joinedNames = remember(names, appLocale) {
                ListFormatter.getInstance(appLocale).format(names)
            }
            body = stringResource(R.string.calibration_pk_status_adjusted_body, joinedNames)
        }
    }

    EditorSegmentedListItem(contentPadding = PaddingValues(16.dp)) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (adjusted) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (adjusted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .cjkTextOffset(title),
                )
                IconButton(onClick = onInfo) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = stringResource(R.string.calibration_pk_edu_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .cjkTextOffset(body),
            )
            // Other non-READY states carry their call to action in the body
            // copy ("add an E2 result").
            if (uiState.numericFailure) {
                HrtFilledTonalButton(
                    text = stringResource(R.string.calibration_pk_retry),
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 12.dp),
                    compact = true,
                )
            }
        }
    }
}

/**
 * Two-column route ledger, adjusted routes first: name plus a confidence glyph
 * (bars) or the population glyph (group). Fit-level states only; opens the
 * routes sheet.
 */
@Composable
private fun PkCalibrationRouteSummaryCard(
    uiState: PkCalibrationUiState,
    onOpen: () -> Unit,
) {
    val rows = uiState.routeRows.adjustedFirst
    val adjustedCount = rows.count { row -> row.displayState.isAdjusted }

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
                        adjustedCount,
                        rows.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PkCalibrationRowChevron()
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                // Aligns the ledger with the title (icon 24 + gap 10).
                modifier = Modifier.padding(start = 34.dp, top = 8.dp),
            ) {
                rows.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        pair.forEach { row ->
                            PkCalibrationRouteSummaryCell(row = row, modifier = Modifier.weight(1f))
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PkCalibrationRouteSummaryCell(
    row: PkCalibrationRouteRowUiState,
    modifier: Modifier = Modifier,
) {
    val adjusted = row.displayState.isAdjusted
    Row(
        modifier = modifier.heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PkCalibrationRouteTile(
            route = row.route,
            size = 20.dp,
            iconSize = 14.dp,
            shape = RoundedCornerShape(6.dp),
        )
        Text(
            text = stringResource(row.route.applicationType.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = if (adjusted) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        if (adjusted) {
            if (row.hasWarning) {
                Icon(
                    painter = painterResource(R.drawable.ic_error_outline),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
            row.confidence?.let { confidence -> PkCalibrationConfidenceBars(confidence) }
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_group),
                contentDescription = stringResource(R.string.calibration_pk_route_label_population),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp),
            )
        }
    }
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
 * Review note under a lab panel row (§4.2 invalid non-positive, §10 outlier
 * review, durable exclusion with explicit re-inclusion), nested inside the
 * card like its testosterone sub-row. Value correction rides the existing
 * lab-edit path via [onCorrect].
 */
@Composable
fun PkCalibrationLabRowFooter(
    flag: PkCalibrationLabRowFlag,
    onCorrect: () -> Unit,
    onExclude: () -> Unit,
    onReinclude: () -> Unit,
) {
    val note = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp)
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceContainer)
    when (flag) {
        is PkCalibrationLabRowFlag.Ignored -> when (flag.reason) {
            PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE -> PkCalibrationLabNote(
                title = stringResource(R.string.calibration_pk_lab_invalid_title),
                body = stringResource(R.string.calibration_pk_lab_invalid_body),
                modifier = note,
            ) {
                TextButton(onClick = onExclude) {
                    Text(text = stringResource(R.string.calibration_pk_lab_invalid_exclude))
                }
                HrtFilledTonalButton(
                    text = stringResource(R.string.calibration_pk_lab_invalid_correct),
                    onClick = onCorrect,
                    compact = true,
                )
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
                TextButton(onClick = onExclude) {
                    Text(text = stringResource(R.string.calibration_pk_lab_outlier_exclude))
                }
            }
        }

        is PkCalibrationLabRowFlag.Excluded -> PkCalibrationNoteRow(
            iconRes = R.drawable.ic_block,
            text = stringResource(R.string.calibration_pk_lab_excluded_note),
            modifier = note.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        ) {
            TextButton(onClick = onReinclude) {
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
            onRetry = { },
            onOpenRoutes = { },
            onOpenCoaching = { },
            onOpenDisclaimer = { },
            onInfo = { },
        )
    }
}

/** Non-READY hides the routes card and the coaching/disclaimer rows. */
@Preview(name = "PK Section · Not ready", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationSectionNotReadyPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        PkCalibrationSection(
            uiState = previewPkUiState(globalState = PkCalibrationGlobalState.NO_USABLE_LABS),
            onRetry = { },
            onOpenRoutes = { },
            onOpenCoaching = { },
            onOpenDisclaimer = { },
            onInfo = { },
        )
    }
}

/**
 * The visually distinct layouts: plain (all non-READY and population states
 * share it, only copy and icon change), retry action, adjusted, adjusted with
 * limited confidence.
 */
@Preview(name = "PK Status Card · states", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationStatusCardPreview() {
    val states = listOf(
        previewPkUiState(globalState = PkCalibrationGlobalState.NO_USABLE_LABS),
        previewPkUiState(globalState = PkCalibrationGlobalState.NUMERIC_FAILURE),
        previewPkAdjustedUiState,
        previewPkProvisionalUiState,
    )
    PkCalibrationPreviewColumn {
        states.forEach { state ->
            PkCalibrationStatusCard(uiState = state, onRetry = { }, onInfo = { })
        }
    }
}

/** All population, numeric failure, two clean adjusted, three adjusted with warnings. */
@Preview(name = "PK Route Summary Card · states", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationRouteSummaryCardPreview() {
    val states = listOf(
        previewPkUiState(),
        previewPkUiState(routeRows = previewPkNumericFailureRows),
        previewPkAdjustedUiState,
        previewPkProvisionalUiState,
    )
    PkCalibrationPreviewColumn {
        states.forEach { state ->
            PkCalibrationRouteSummaryCard(uiState = state, onOpen = { })
        }
    }
}

/** Every review note a lab row can carry (§4.2 invalid, §10 outlier, exclusion). */
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
        PkCalibrationLabRowFlag.Excluded(resultId),
    )
    PkCalibrationPreviewColumn {
        flags.forEach { flag ->
            EditorSegmentedListItem(contentPadding = PaddingValues(16.dp)) {
                PkCalibrationLabRowFooter(
                    flag = flag,
                    onCorrect = { },
                    onExclude = { },
                    onReinclude = { },
                )
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
