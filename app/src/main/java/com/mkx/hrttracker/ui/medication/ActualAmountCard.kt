package com.mkx.hrttracker.ui.medication

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollSource.Companion.UserInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.DoseInstructionCalculator
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Actual administered-amount cards: interactive ruler (new logs) and the
// frozen read-only summary (editing an already-logged adjusted dose).
// ---------------------------------------------------------------------------

@Composable
internal fun ActualAmountRulerCard(
    modifier: Modifier = Modifier,
    preparationType: MedicinePreparationType?,
    allowsActualDoseDelta: Boolean,
    plannedAmount: Double?,
    doseAmountDelta: Double?,
    isSaving: Boolean,
    onDoseAmountDeltaChange: (Double?) -> Unit,
    onLiveActualAmountChange: (Double) -> Unit = {},
    onScrollingChange: (Boolean) -> Unit = {},
) {
    if (!allowsActualDoseDelta ||
        plannedAmount == null ||
        !plannedAmount.isFinite() ||
        plannedAmount <= 0.0
    ) {
        return
    }
    val params = actualDoseDeltaFormParams(preparationType) ?: return
    val unitText = actualAmountUnitText(preparationType) ?: return
    val range = remember(plannedAmount, params) {
        actualDoseDeltaRange(plannedAmount, params.fraction, params.step, params.underDrawOnly)
    }
    val deltas = remember(range) {
        val count = (((range.max - range.min) / range.step).roundToInt() + 1).coerceAtLeast(1)
        List(count) { i -> range.min + i * range.step }
    }
    val selectedIndex = remember(deltas, doseAmountDelta) {
        val target = doseAmountDelta ?: 0.0
        deltas.indices.minByOrNull { abs(deltas[it] - target) } ?: 0
    }
    val resetIndex = remember(deltas) {
        deltas.indices.minByOrNull { abs(deltas[it]) } ?: selectedIndex
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center,
    )
    val tickSpacing = 14.dp
    val density = LocalDensity.current
    val overscrollPullPx = remember { mutableFloatStateOf(0f) }
    val maxOverscrollPx = with(density) { 64.dp.toPx() }
    val rulerOverscrollEffect = remember(maxOverscrollPx) {
        ActualAmountRulerOverscrollEffect(
            overscrollPx = overscrollPullPx,
            maxOverscrollPx = maxOverscrollPx,
        )
    }
    val currentDoseAmountDelta by rememberUpdatedState(doseAmountDelta)
    val currentOnDoseAmountDeltaChange by rememberUpdatedState(onDoseAmountDeltaChange)
    val currentOnLiveActualAmountChange by rememberUpdatedState(onLiveActualAmountChange)
    val currentOnScrollingChange by rememberUpdatedState(onScrollingChange)
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var lastHapticIndex by remember { mutableStateOf(selectedIndex) }
    var programmaticScrollInProgress by remember { mutableStateOf(false) }

    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) {
                selectedIndex
            } else {
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                info.visibleItemsInfo
                    .minByOrNull { abs((it.offset + it.size / 2f) - center) }
                    ?.index ?: selectedIndex
            }
        }
    }

    LaunchedEffect(
        centeredIndex,
        listState.isScrollInProgress,
        isSaving,
        programmaticScrollInProgress,
    ) {
        if (centeredIndex == lastHapticIndex) return@LaunchedEffect

        lastHapticIndex = centeredIndex
        if (!isSaving && listState.isScrollInProgress && !programmaticScrollInProgress) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    // Keep the ruler aligned with caller-owned state. Reopening the sheet can
    // reset the external delta while this composable instance is reused.
    LaunchedEffect(selectedIndex) {
        if (centeredIndex == selectedIndex) return@LaunchedEffect

        programmaticScrollInProgress = true
        try {
            listState.scrollToItem(selectedIndex)
        } finally {
            programmaticScrollInProgress = false
        }
    }

    // Emit only after the row stays idle; drag/fling/snap can briefly report
    // false between phases, and reacting to that gap causes duplicate snaps.
    LaunchedEffect(listState, deltas, plannedAmount) {
        snapshotFlow { listState.isScrollInProgress to centeredIndex }
            .distinctUntilChanged()
            .collectLatest { (isScrollInProgress, settledIndex) ->
                if (isScrollInProgress) return@collectLatest
                delay(ACTUAL_AMOUNT_RULER_SETTLE_DEBOUNCE_MILLIS)
                if (listState.isScrollInProgress) return@collectLatest

                val tickDelta = deltas.getOrElse(settledIndex) { 0.0 }
                val nextDelta = doseAmountDeltaForActual(plannedAmount, plannedAmount + tickDelta)
                if (!actualAmountDeltasEquivalent(nextDelta, currentDoseAmountDelta)) {
                    currentOnDoseAmountDeltaChange(nextDelta)
                }
            }
    }

    val liveDelta = deltas.getOrElse(centeredIndex) { 0.0 }
    val liveActual = plannedAmount + liveDelta

    // Surface the live centered amount immediately so dependent UI (e.g. the
    // stock subcard's after-mutation amount) tracks the scrub in real time,
    // independent of the debounced commit on `onDoseAmountDeltaChange`.
    LaunchedEffect(liveActual) { currentOnLiveActualAmountChange(liveActual) }

    // Surface scroll state so callers can swallow a Save tapped mid-scroll: the
    // committed delta only lands after the row settles (see the settle-debounce
    // above), so saving during a fling/reset would persist the pre-scroll value
    // even though the header already shows the live one. Reset to false on
    // dispose so a card that leaves composition mid-scroll never wedges saves.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { currentOnScrollingChange(it) }
    }
    DisposableEffect(Unit) {
        onDispose { currentOnScrollingChange(false) }
    }
    // Track the live centered delta so the enabled state flips the instant the
    // row settles (no settle-debounce / state round-trip lag), and hold it
    // enabled while the *user* is scrolling: scrubbing across the planned point
    // drives the live delta through ~0, and without the hold that blinks the
    // button off for a tick. A programmatic scroll (the reset animation itself)
    // force-disables instead, so the button greys out the instant reset is
    // tapped rather than at the end of its animation.
    // isSaving is intentionally excluded: like the sheet's other controls the
    // button stays visually enabled during an in-flight save (the onClick is
    // guarded instead), so it doesn't grey-flicker while saving.
    val resetEnabled = !programmaticScrollInProgress &&
        (listState.isScrollInProgress ||
            abs(liveDelta) >= DoseInstructionCalculator.MIN_EFFECTIVE_DOSE_EPSILON)
    val resetContentDescription = stringResource(R.string.medication_log_actual_amount_reset)

    EditorSegmentedListItem(
        modifier = modifier,
        index = 0,
        count = 1,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: label + signed delta on the left; reset then the live
            // amount on the right. The ruler below carries its own delta labels,
            // so the value is shown exactly once, here.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.medication_log_actual_amount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val isAdjusted =
                        abs(liveDelta) >= DoseInstructionCalculator.MIN_EFFECTIVE_DOSE_EPSILON
                    Text(
                        text = if (isAdjusted) {
                            "${formatSignedActualAmountDelta(liveDelta)} $unitText"
                        } else {
                            "+0 $unitText"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isAdjusted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${formatActualAmount(liveActual)} $unitText",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                    ) {
                        IconButton(
                            enabled = resetEnabled,
                            onClick = {
                                // `enabled` gates only the at-rest zero state, so
                                // guard the in-flight save and re-entrant taps here.
                                if (isSaving || programmaticScrollInProgress) return@IconButton
                                programmaticScrollInProgress = true
                                coroutineScope.launch {
                                    try {
                                        listState.animateScrollToItem(resetIndex)
                                    } finally {
                                        programmaticScrollInProgress = false
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp).offset(x = 4.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_restart_alt),
                                contentDescription = resetContentDescription,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val sidePadding = (maxWidth - tickSpacing) / 2
                    val viewportWidthPx = with(density) { maxWidth.toPx() }
                    val overscrollScale by animateFloatAsState(
                        targetValue = actualAmountRulerOverscrollScale(
                            overscrollPx = overscrollPullPx.floatValue,
                            viewportWidthPx = viewportWidthPx,
                        ),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "ActualAmountRulerOverscrollScale",
                    )
                    // Tick marks occupy the top strip; signed-delta labels sit below.
                    // The selection indicator spans only the tick strip so it never
                    // crosses the labels.
                    val tickAreaHeight = 28.dp
                    LazyRow(
                        state = listState,
                        flingBehavior = flingBehavior,
                        userScrollEnabled = !isSaving,
                        overscrollEffect = rulerOverscrollEffect,
                        contentPadding = PaddingValues(horizontal = sidePadding),
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .graphicsLayer {
                                scaleX = overscrollScale
                                transformOrigin = TransformOrigin.Center
                            },
                    ) {
                        items(deltas.size) { i ->
                            val isEndpoint = i == 0 || i == deltas.size - 1
                            val isMajor = isActualDoseDeltaMajorTick(
                                delta = deltas[i],
                                step = range.step,
                                isEndpoint = isEndpoint,
                            )
                            Column(
                                modifier = Modifier.width(tickSpacing),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier.height(tickAreaHeight).fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .fillMaxHeight(if (isMajor) 0.6f else 0.35f)
                                            .background(
                                                MaterialTheme.colorScheme.outlineVariant,
                                                CircleShape,
                                            ),
                                    )
                                }
                                if (isMajor) {
                                    Text(
                                        text = formatActualDoseDeltaTickLabel(deltas[i]),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        // Labels are wider than the 14.dp item; let them
                                        // spill into the neighbouring minor-tick gaps.
                                        modifier = Modifier.wrapContentWidth(unbounded = true),
                                    )
                                }
                            }
                        }
                    }
                    // Fixed selection indicator - centered on the tick strip, a
                    // touch taller than a major tick without spilling into labels.
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .align(Alignment.TopCenter)
                            .height(20.dp)
                            .width(2.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Preview(name = "Actual amount ruler", showBackground = true, widthDp = 400)
@Composable
private fun ActualAmountRulerCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            ActualAmountRulerCard(
                preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
                allowsActualDoseDelta = true,
                plannedAmount = 0.25,
                doseAmountDelta = 0.05,
                isSaving = false,
                onDoseAmountDeltaChange = {},
            )
        }
    }
}

@Preview(name = "Actual amount read-only", showBackground = true, widthDp = 400)
@Composable
private fun ActualAmountReadOnlyCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            ActualAmountReadOnlyCard(
                preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
                showActualDoseDeltaReadOnly = true,
                scheduledDoseAmount = 0.25,
                doseAmountDelta = 0.1,
                effectiveActualAmount = 0.35,
            )
        }
    }
}

internal fun medicationLogEntrySummaryDoseAmountDelta(
    allowsActualDoseDelta: Boolean,
    showActualDoseDeltaReadOnly: Boolean,
    doseAmountDelta: Double?,
): Double? {
    if (allowsActualDoseDelta) return null
    return doseAmountDelta.takeIf { showActualDoseDeltaReadOnly }
}

@Composable
internal fun ActualAmountReadOnlyCard(
    modifier: Modifier = Modifier,
    preparationType: MedicinePreparationType?,
    showActualDoseDeltaReadOnly: Boolean,
    scheduledDoseAmount: Double?,
    doseAmountDelta: Double?,
    effectiveActualAmount: Double?,
) {
    if (!showActualDoseDeltaReadOnly ||
        scheduledDoseAmount == null ||
        doseAmountDelta == null ||
        effectiveActualAmount == null
    ) {
        return
    }
    val unitText = actualAmountUnitText(preparationType) ?: return

    // Mirrors ActualAmountRulerCard's header (label + amount right) but frozen:
    // no ruler, no reset. The left line shows the planned dose and the recorded
    // adjustment, e.g. "0.25 mL (-0.1 mL)".
    EditorSegmentedListItem(
        modifier = modifier,
        index = 0,
        count = 1,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.medication_log_actual_amount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${formatActualAmount(scheduledDoseAmount)} $unitText " +
                        "(${formatSignedActualAmountDelta(doseAmountDelta)} $unitText)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${formatActualAmount(effectiveActualAmount)} $unitText",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun actualAmountUnitText(preparationType: MedicinePreparationType?): String? {
    val unitRes = when (preparationType) {
        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL -> R.string.unit_mg
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> R.string.unit_ml
        MedicinePreparationType.GEL_CONTAINER -> R.string.unit_grams
        else -> return null
    }
    return stringResource(unitRes)
}

private fun formatSignedActualAmountDelta(value: Double): String {
    val sign = if (value >= 0.0) "+" else "-"
    return sign + formatActualAmount(kotlin.math.abs(value))
}

private fun formatActualDoseDeltaTickLabel(delta: Double): String {
    return if (abs(delta) < DoseInstructionCalculator.MIN_EFFECTIVE_DOSE_EPSILON) {
        "0"
    } else {
        formatSignedActualAmountDelta(delta)
    }
}

private fun formatActualAmount(value: Double): String {
    return BigDecimal.valueOf(value)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

private fun actualAmountDeltasEquivalent(first: Double?, second: Double?): Boolean {
    return abs((first ?: 0.0) - (second ?: 0.0)) <
        DoseInstructionCalculator.MIN_EFFECTIVE_DOSE_EPSILON
}

internal fun actualAmountRulerOverscrollScale(
    overscrollPx: Float,
    viewportWidthPx: Float,
): Float {
    if (viewportWidthPx <= 0f) return 1f
    return 1f + (abs(overscrollPx) / viewportWidthPx).coerceAtMost(
        ACTUAL_AMOUNT_RULER_MAX_OVERSCROLL_SCALE - 1f,
    )
}

private class ActualAmountRulerOverscrollEffect(
    private val overscrollPx: MutableFloatState,
    private val maxOverscrollPx: Float,
) : OverscrollEffect {
    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val consumed = performScroll(delta)
        val unconsumedX = delta.x - consumed.x

        if (source == UserInput && unconsumedX != 0f) {
            overscrollPx.floatValue = (
                overscrollPx.floatValue +
                    unconsumedX * ACTUAL_AMOUNT_RULER_OVERSCROLL_PULL_DAMPING
                ).coerceIn(-maxOverscrollPx, maxOverscrollPx)
            return consumed + Offset(x = unconsumedX, y = 0f)
        }

        if (consumed.x != 0f && overscrollPx.floatValue != 0f) {
            overscrollPx.floatValue = 0f
        }
        return consumed
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        try {
            performFling(velocity)
        } finally {
            overscrollPx.floatValue = 0f
        }
    }

    override val isInProgress: Boolean
        get() = abs(overscrollPx.floatValue) > 0.5f
}

private const val ACTUAL_AMOUNT_RULER_SETTLE_DEBOUNCE_MILLIS = 48L
private const val ACTUAL_AMOUNT_RULER_OVERSCROLL_PULL_DAMPING = 0.35f
private const val ACTUAL_AMOUNT_RULER_MAX_OVERSCROLL_SCALE = 1.08f
