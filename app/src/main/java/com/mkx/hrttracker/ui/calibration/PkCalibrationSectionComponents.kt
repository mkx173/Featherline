package com.mkx.hrttracker.ui.calibration

import android.icu.text.ListFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationLabIgnoreReason
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtOutlinedButton
import com.mkx.hrttracker.ui.components.HrtPill
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
    val adjusted = ready && uiState.heroKind == PkCalibrationHeroKind.ADJUSTED
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

/** One chip per route, fit-level states only; opens the routes sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PkCalibrationRouteSummaryCard(
    uiState: PkCalibrationUiState,
    onOpen: () -> Unit,
) {
    val adjustedCount = uiState.routeRows.count { row -> row.displayState.isAdjusted }
    val reviewCount = uiState.routeRows.count { row -> row.hasWarning }
    val summary = buildList {
        if (adjustedCount == 0) {
            add(stringResource(R.string.calibration_pk_routes_card_all_population))
        } else {
            add(
                pluralStringResource(
                    R.plurals.calibration_pk_routes_card_adjusted_count,
                    adjustedCount,
                    adjustedCount,
                )
            )
        }
        if (reviewCount > 0) {
            add(
                pluralStringResource(
                    R.plurals.calibration_pk_routes_card_review_count,
                    reviewCount,
                    reviewCount,
                )
            )
        }
    }.joinToString(separator = " · ")

    EditorSegmentedListItem(onClick = onOpen, contentPadding = PaddingValues(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(10.dp))
                val title = stringResource(R.string.calibration_pk_routes_card_title)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .cjkTextOffset(title),
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PkCalibrationRowChevron()
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                uiState.routeRows.forEach { row -> PkCalibrationRouteChip(row) }
            }
        }
    }
}

@Composable
private fun PkCalibrationRouteChip(row: PkCalibrationRouteRowUiState) {
    val routeScheme = rememberMedicationGroupColorScheme(
        colorKey = row.route.medicationGroupColorKey,
    )
    val adjusted = row.displayState.isAdjusted
    // Adjusted chips carry the coarse confidence tier (user decision,
    // 2026-08-12) instead of the former binary "limited" qualifier.
    val stateWord = when {
        row.confidence != null -> stringResource(
            R.string.calibration_pk_chip_adjusted_confidence,
            stringResource(row.confidence.labelRes),
        )

        adjusted -> stringResource(R.string.calibration_pk_chip_adjusted)
        else -> stringResource(R.string.calibration_pk_route_label_population)
    }
    HrtPill(
        containerColor = if (adjusted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (adjusted) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        contentPadding = PaddingValues(start = 6.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(routeScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    medicationApplicationIconRes(row.route.applicationType)
                ),
                contentDescription = null,
                tint = routeScheme.onPrimaryContainer,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = stringResource(row.route.applicationType.labelRes),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = stateWord,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (row.hasWarning) {
            Icon(
                painter = painterResource(R.drawable.ic_error_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/**
 * Review footer under a lab panel row (§4.2 invalid non-positive, §10 outlier
 * review, durable exclusion with explicit re-inclusion). Value correction
 * rides the existing lab-edit path via [onCorrect].
 */
@Composable
fun PkCalibrationLabRowFooter(
    flag: PkCalibrationLabRowFlag,
    onCorrect: () -> Unit,
    onExclude: () -> Unit,
    onReinclude: () -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        when (flag) {
            is PkCalibrationLabRowFlag.Ignored -> when (flag.reason) {
                PkCalibrationLabIgnoreReason.NON_POSITIVE_VALUE -> {
                    PkCalibrationLabFooterText(
                        title = stringResource(R.string.calibration_pk_lab_invalid_title),
                        body = stringResource(R.string.calibration_pk_lab_invalid_body),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        HrtOutlinedButton(
                            text = stringResource(R.string.calibration_pk_lab_invalid_exclude),
                            onClick = onExclude,
                            compact = true,
                        )
                        HrtButton(
                            text = stringResource(R.string.calibration_pk_lab_invalid_correct),
                            onClick = onCorrect,
                            compact = true,
                        )
                    }
                }

                PkCalibrationLabIgnoreReason.BELOW_INFORMATIVE_SIGNAL ->
                    PkCalibrationLabFooterNote(
                        stringResource(R.string.calibration_pk_lab_ignored_signal_note)
                    )

                PkCalibrationLabIgnoreReason.NUMERIC_FAILURE ->
                    PkCalibrationLabFooterNote(
                        stringResource(R.string.calibration_pk_lab_ignored_numeric_note)
                    )
            }

            is PkCalibrationLabRowFlag.UnreviewedOutlier -> {
                val appLocale = rememberAppLocale()
                val names = flag.affectedRoutes
                    .map { route -> stringResource(route.applicationType.labelRes) }
                val joinedNames = remember(names, appLocale) {
                    ListFormatter.getInstance(appLocale).format(names)
                }
                PkCalibrationLabFooterText(
                    title = stringResource(R.string.calibration_pk_lab_outlier_title),
                    body = stringResource(R.string.calibration_pk_lab_outlier_body, joinedNames),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    HrtOutlinedButton(
                        text = stringResource(R.string.calibration_pk_lab_outlier_exclude),
                        onClick = onExclude,
                        compact = true,
                    )
                }
            }

            is PkCalibrationLabRowFlag.Excluded -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    val note = stringResource(R.string.calibration_pk_lab_excluded_note)
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .cjkTextOffset(note),
                    )
                    TextButton(onClick = onReinclude) {
                        Text(text = stringResource(R.string.calibration_pk_lab_reinclude))
                    }
                }
            }
        }
    }
}

@Composable
private fun PkCalibrationLabFooterNote(note: String) {
    Text(
        text = note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 8.dp)
            .cjkTextOffset(note),
    )
}

@Composable
private fun PkCalibrationLabFooterText(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .padding(top = 10.dp)
            .cjkTextOffset(title),
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 4.dp)
            .cjkTextOffset(body),
    )
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

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "PK Route Chip · states", showBackground = true, widthDp = 420)
@Composable
private fun PkCalibrationRouteChipPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            PkCalibrationRouteChip(previewPkRouteRow(PkCalibrationRoute.ORAL))
            PkCalibrationRouteChip(previewPkNumericFailureRows.first())
            PkCalibrationRouteChip(previewPkCalibratedRow)
            PkCalibrationRouteChip(previewPkProvisionalMediumRow)
            PkCalibrationRouteChip(previewPkProvisionalLowRow)
        }
    }
}

/** Every review footer a lab row can carry (§4.2 invalid, §10 outlier, exclusion). */
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
