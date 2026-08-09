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
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtOutlinedButton
import com.mkx.hrttracker.ui.components.HrtPill
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.medication.medicationApplicationIconRes
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.labelRes
import com.mkx.hrttracker.util.rememberAppLocale

/**
 * The Phase-2 calibration status section shown at the top of the calibration
 * screen (plan D1). Only composed while [pkCalibrationSurfaceEnabled] holds and
 * a validated [PkCalibrationUiState] exists — never synthesized from raw fits.
 */
@Composable
fun PkCalibrationSection(
    uiState: PkCalibrationUiState,
    onRetry: () -> Unit,
    onAttest: () -> Unit,
    onManageAttestation: () -> Unit,
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
                onAttest = onAttest,
                onManageAttestation = onManageAttestation,
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
    onAttest: () -> Unit,
    onManageAttestation: () -> Unit,
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
                PkCalibrationGlobalState.SCOPE_NOT_CONFIRMED -> R.drawable.ic_privacy_tip
                PkCalibrationGlobalState.NO_USABLE_LABS -> R.drawable.ic_labs
                PkCalibrationGlobalState.SHARED_INPUT_INVALID -> R.drawable.ic_error_outline
                else -> R.drawable.ic_sync_alt
            }
            title = stringResource(requireNotNull(uiState.globalState.statusTitleRes))
            body = stringResource(requireNotNull(uiState.globalState.statusBodyRes))
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
            when (uiState.globalState) {
                PkCalibrationGlobalState.SHARED_NUMERIC_FAILURE -> HrtFilledTonalButton(
                    text = stringResource(R.string.calibration_pk_retry),
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 12.dp),
                    compact = true,
                )

                PkCalibrationGlobalState.SCOPE_NOT_CONFIRMED -> HrtButton(
                    text = stringResource(R.string.calibration_pk_attest_action),
                    onClick = onAttest,
                    modifier = Modifier.padding(top = 12.dp),
                    compact = true,
                )

                PkCalibrationGlobalState.READY -> {
                    val reminder =
                        stringResource(R.string.calibration_pk_status_attested_reminder)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(
                            text = reminder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .cjkTextOffset(reminder),
                        )
                        TextButton(onClick = onManageAttestation) {
                            Text(text = stringResource(R.string.calibration_pk_status_manage))
                        }
                    }
                }

                // The body copy is the call to action ("add an E2 result");
                // Phase 3.1 decides any richer affordance.
                PkCalibrationGlobalState.NO_USABLE_LABS,
                PkCalibrationGlobalState.SHARED_INPUT_INVALID,
                -> Unit
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
    val reviewCount = uiState.routeRows.count { row -> row.displayState.needsReview }
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

/** Caution states surfaced with a non-color cue (icon + word), handoff §7. */
private val PkRouteCalibrationDisplayState.needsReview: Boolean
    get() = this == PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE ||
        this == PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED

@Composable
private fun PkCalibrationRouteChip(row: PkCalibrationRouteRowUiState) {
    val routeScheme = rememberMedicationGroupColorScheme(
        colorKey = row.route.medicationGroupColorKey,
    )
    val adjusted = row.displayState.isAdjusted
    val stateWord = stringResource(
        when {
            row.displayState == PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL ->
                R.string.calibration_pk_chip_adjusted_limited

            adjusted -> R.string.calibration_pk_chip_adjusted
            else -> R.string.calibration_pk_route_label_population
        }
    )
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
        if (row.displayState.needsReview) {
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
    onKeep: () -> Unit,
    onReinclude: () -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        when (flag) {
            is PkCalibrationLabRowFlag.InvalidNonPositive -> {
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

            is PkCalibrationLabRowFlag.UnreviewedOutlier -> {
                val appLocale = rememberAppLocale()
                val names = flag.blockedRoutes
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
                    HrtButton(
                        text = stringResource(R.string.calibration_pk_lab_outlier_keep),
                        onClick = onKeep,
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
