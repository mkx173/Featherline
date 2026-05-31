package com.mkx.hrttracker.ui.main

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.segmentedListItemShape
import com.mkx.hrttracker.ui.medication.MedicationApplicationIcon
import com.mkx.hrttracker.ui.medication.doseInstructionSummary
import com.mkx.hrttracker.ui.medication.medicationCountIndicatorText
import com.mkx.hrttracker.ui.medication.medicationEntryTitle
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.LocalDateFormatter
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.formatMainE2ConcentrationValue
import com.mkx.hrttracker.util.labelRes
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerDrawingModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.MutableCartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.data.CartesianLayerDrawingModelInterpolator
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val MainPreviewNow = LocalDateTime.of(2026, 5, 5, 10, 30)
private val PreviewEstradiolGroupUuid = UUID.fromString("56c7730e-1273-4de3-8d92-1a77953aa2e4")
private val PreviewPatchGroupUuid = UUID.fromString("4d6ac2b0-1185-4718-91d7-e74d4ec19488")
private val PreviewAntiandrogenGroupUuid = UUID.fromString("f5b5ef28-7364-47b4-9d92-2527e9b7b753")
private val PreviewMorningScheduleUuid = UUID.fromString("f4ea56fc-0856-485e-9af2-d1bc7caa341c")
private val PreviewPatchScheduleUuid = UUID.fromString("99cc2da0-e3ea-4bbd-a3ce-d6532b73f474")
private val PreviewAntiandrogenScheduleUuid = UUID.fromString("35bbf5a3-7ee0-4c93-b8cf-54292964a3d7")
private const val MainScheduleGraceMinutes = 60L
private val MainScheduleGracePeriod = Duration.ofMinutes(MainScheduleGraceMinutes)
private const val MainE2ChartInitialAnimationMillis = 500
private const val MainE2ChartAnimationSettleDelayMillis = 50L
// Process-scoped so the chart's initial fade-in plays exactly once per
// app launch, not on every re-composition (tab switch, scroll re-entry,
// etc.) that would otherwise re-arm a rememberSaveable flag.
private var mainE2ChartInitialAnimationConsumed = false
// SEVEN_DAYS keeps the original fixed 48 h floor — 2 d on a phone-width
// chart, derived empirically from the 0.1 h sampler. THIRTY_DAYS instead
// derives its floor from the measured plot width and budget grid: the
// density factor is the target samples/pixel at maximum zoom-in. At 0.42
// the phone-width floor lands at 3 d (≈2.4 px between dense grid samples
// at deepest zoom) while tablets get ~6 d at the same density. The
// elimination phase's gentle slope and the per-dose offset list pin the
// curve at every interesting point; lower factors let users zoom further
// at the cost of slightly visible linearisation between offsets.
private const val MainE2ChartMaxZoomXRangeHours = 48.0
private const val MainE2ChartZoomDensityFactor = 0.42
private const val MainE2ChartFallbackPixelWidth = 400
private const val MainE2ChartThirtyDayProjectionSpanHours: Double = 40.0 * 24.0
private const val MainE2ChartThirtyDaySegmentCount: Int = 2240

private const val MainE2ChartZoomFloorRoundingHours = 24.0

internal suspend fun runDoseRowHighlightFlashEffect(
    setFlashActive: (Boolean) -> Unit,
    holdMillis: Long,
) {
    try {
        setFlashActive(true)
        delay(holdMillis)
    } finally {
        setFlashActive(false)
    }
}

internal fun mainE2ChartZoomFloorHours(
    projectionSpanHours: Double,
    segmentCount: Int,
    chartPixelWidthPx: Int,
): Double {
    val width = chartPixelWidthPx.takeIf { it > 0 } ?: MainE2ChartFallbackPixelWidth
    val safeSegmentCount = segmentCount.coerceAtLeast(1)
    val raw = MainE2ChartZoomDensityFactor * (projectionSpanHours / safeSegmentCount) * width
    // Snap the max-zoom span down to a whole number of days so the
    // deepest-zoom edge lines up with the chart's day-aligned ticks. Never
    // drop below one full day even on very narrow widths.
    return (floor(raw / MainE2ChartZoomFloorRoundingHours) * MainE2ChartZoomFloorRoundingHours)
        .coerceAtLeast(MainE2ChartZoomFloorRoundingHours)
}

internal fun mainE2ChartMaxZoomXRangeHours(
    option: HomeE2ChartWindowOption,
    chartPixelWidthPx: Int,
): Double = when (option) {
    HomeE2ChartWindowOption.SEVEN_DAYS -> MainE2ChartMaxZoomXRangeHours
    HomeE2ChartWindowOption.THIRTY_DAYS -> mainE2ChartZoomFloorHours(
        projectionSpanHours = MainE2ChartThirtyDayProjectionSpanHours,
        segmentCount = MainE2ChartThirtyDaySegmentCount,
        chartPixelWidthPx = chartPixelWidthPx,
    )
}

private fun mainTodayDoseRowsByTimeRange(
    rows: List<MainTodayDoseRowUiState>,
): List<Map.Entry<MainTodayTimeRange, List<MainTodayDoseRowUiState>>> =
    rows
        .groupBy { row -> mainTodayTimeRange(row.scheduledAt.toLocalTime()) }
        .entries
        .sortedBy { (timeRange, _) -> timeRange.ordinal }

internal fun mainDoseRowHighlightScrollTargetKey(
    uiState: MainUiState,
    highlightRequest: DoseRowHighlightRequest?,
): String? {
    if (highlightRequest == null) return null

    var targetKey: String? = null
    uiState.lastNightSection.rows.forEach { row ->
        if (highlightRequest.matches(row)) {
            targetKey = mainTodayDoseRowCompositionKey(row)
        }
    }
    mainTodayDoseRowsByTimeRange(uiState.todaySection.rows).forEach { (_, rows) ->
        rows.forEach { row ->
            if (highlightRequest.matches(row)) {
                targetKey = mainTodayDoseRowCompositionKey(row)
            }
        }
    }
    uiState.upcomingSection.rows.forEach { row ->
        if (highlightRequest.matches(row)) {
            targetKey = mainUpcomingDoseRowCompositionKey(row)
        }
    }
    return targetKey
}

private const val MainE2ChartMinimapRangeEpsilonHours = 1e-3
// Sentinel coordinates for an empty dose-marker series. The x is placed just
// outside the chart's x range (which starts at 0). The y is a large negative
// value so the circle center is far below the chart canvas — necessary because
// Vico renders the circle at the actual pixel position and the left edge of the
// chart clips only the horizontal overhang, leaving the top-right quarter of a
// y=0 sentinel visible as a partial dot on the x-axis.
private const val OffAxisDoseMarkerSentinelXHours = -1.0
private const val OffAxisDoseMarkerSentinelConcentration = -1_000_000f
private val MainE2ChartContentPadding = 8.dp
private val MainE2ChartMarkerLabelGap = 6.dp
private val MainE2ChartPointSpacing = 32.dp
private val MainE2ChartPlotHeight = 184.dp
private val MainE2ChartMinimapHeight = 48.dp
private val MainE2ChartMinimapHorizontalInset = 5.dp
private val MainE2ChartMinimapResetButtonSize = 32.dp
private val PreviewManualEntryUuid = UUID.fromString("d54fbd94-9631-4c20-b79f-9e431d51a719")

@StringRes
internal fun mainE2EstimateInfoToastRes(): Int =
    R.string.medical_disclaimer_plasma_concentration_estimates

private enum class MainTodayTimeRange(
    val labelRes: Int,
    val startHour: Int,
    val endHour: Int,
) {
    NIGHT(
        labelRes = R.string.main_today_range_night,
        startHour = 0,
        endHour = 6
    ),
    MORNING(
        labelRes = R.string.main_today_range_morning,
        startHour = 6,
        endHour = 12
    ),
    AFTERNOON(
        labelRes = R.string.main_today_range_afternoon,
        startHour = 12,
        endHour = 18
    ),
    EVENING(
        labelRes = R.string.main_today_range_evening,
        startHour = 18,
        endHour = 24
    );
}

internal const val MainE2ChartContentTestTag = "main-e2-chart-content"
internal const val MainE2ChartSkeletonTestTag = "main-e2-chart-skeleton"

private const val SkeletonShimmerPeriodMillis = 1400

@Composable
private fun SkeletonShimmerBlock(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val base = MaterialTheme.colorScheme.surfaceContainer
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SkeletonShimmerPeriodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton-shimmer-progress",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val width = size.width
                val sweep = width * 1.5f
                val travel = sweep + width
                val brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(x = -sweep + progress * travel, y = 0f),
                    end = Offset(x = progress * travel, y = 0f),
                )
                drawRect(brush)
            }
    )
}

// Lets the real content drive layout (intrinsic width/height) while painting
// a shimmer on top. Keeps the skeleton's footprint pinned to whatever the
// loaded content would actually measure to — no width/height tuning needed
// as fonts or locales change.
@Composable
private fun SkeletonOverlay(
    active: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!active) {
        Box(modifier = modifier) { content() }
        return
    }
    Box(modifier = modifier) {
        Box(modifier = Modifier.alpha(0f)) { content() }
        SkeletonShimmerBlock(
            modifier = Modifier.matchParentSize(),
            shape = shape,
        )
    }
}

@Composable
private fun AnimatedEllipsis(
    color: Color,
    modifier: Modifier = Modifier,
    bounceHeight: Dp = 12.dp,
    cycleMillis: Int = 1200,
    staggerMillis: Int = 150,
) {
    val bounceHeightPx = with(LocalDensity.current) { bounceHeight.toPx() }
    val liftEasing = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
    val dropEasing = remember { CubicBezierEasing(0.4f, 0f, 1f, 1f) }

    Row(modifier = modifier) {
        repeat(3) { index ->
            val transition = rememberInfiniteTransition(label = "animated-ellipsis-$index")
            val yOffsetPx by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = cycleMillis
                        0f at 0 using liftEasing
                        -bounceHeightPx at cycleMillis / 4 using dropEasing
                        0f at cycleMillis / 2 using LinearEasing
                        0f at cycleMillis
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * staggerMillis),
                ),
                label = "animated-ellipsis-y-$index",
            )

            Text(
                text = ".",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Medium,
                color = color,
                modifier = Modifier
                    .alignByBaseline()
                    .graphicsLayer { translationY = yOffsetPx },
            )
        }
    }
}

@Composable
internal fun MainE2HeroCard(
    section: MainE2HeroUiState,
    now: LocalDateTime,
    displayUnit: BloodUnitKey,
    modifier: Modifier = Modifier,
    trendReady: Boolean = true,
    hideReferenceRanges: Boolean = false,
) {
    val showSkeleton = !trendReady
    val trendDeltaLabel = mainTrendDeltaLabel(
        changeSinceYesterday = section.changeSinceYesterday,
        unit = section.unit,
        displayUnit = displayUnit,
    )
    val isTrendDeltaDisplayZero = isMainE2TrendDeltaDisplayZero(
        changeSinceYesterday = section.changeSinceYesterday,
        displayUnit = displayUnit,
    )
    val effectiveIsTrendDeltaDisplayZero = showSkeleton || isTrendDeltaDisplayZero
    val trendIcon = when {
        effectiveIsTrendDeltaDisplayZero -> Icons.AutoMirrored.Rounded.TrendingFlat
        section.changeSinceYesterday > 0 -> Icons.AutoMirrored.Rounded.TrendingUp
        section.changeSinceYesterday < 0 -> Icons.AutoMirrored.Rounded.TrendingDown
        else -> Icons.AutoMirrored.Rounded.TrendingFlat
    }
    val lastDoseSummary = mainE2LastDoseSummary(
        section = section,
        now = now
    )
    val hasPreviousRecord = section.lastDoseAt != null
    val titleText = stringResource(R.string.main_e2_title)
    val estimateInfoTooltipText = stringResource(mainE2EstimateInfoToastRes())
    val estimateInfoTooltipState = rememberTooltipState(isPersistent = true)
    val estimateInfoTooltipScope = rememberCoroutineScope()
    BackHandler(enabled = estimateInfoTooltipState.isVisible) {
        estimateInfoTooltipState.dismiss()
    }
    val currentValueText = formatMainE2ConcentrationValue(section.currentValue, displayUnit)
    val unitText = section.unit
    // When the trend isn't ready, we pin the pill to "in range" so the slot
    // stays visually continuous instead of popping below→above as the value
    // arrives.
    val rangeStatusIconDrawableRes = when {
        showSkeleton -> R.drawable.ic_adjust
        section.currentValue > section.targetMax -> R.drawable.ic_expand_circle_up
        section.currentValue < section.targetMin -> R.drawable.ic_expand_circle_down
        else -> R.drawable.ic_adjust
    }
    val rangeStatusLabelRes = when {
        showSkeleton -> R.string.settings_calibration_range_status_in_range
        section.currentValue > section.targetMax -> R.string.settings_calibration_range_status_above
        section.currentValue < section.targetMin -> R.string.settings_calibration_range_status_below
        else -> R.string.settings_calibration_range_status_in_range
    }
    val rangeStatusLabel = stringResource(rangeStatusLabelRes)
    val colorScheme = MaterialTheme.colorScheme
    val heroContentColor = colorScheme.primary
    val heroSupportingColor = colorScheme.onSurfaceVariant
    val heroPillContainerColor = colorScheme.secondaryContainer.copy(alpha = 0.7f)

    Box(modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MonitorHeart,
                            contentDescription = null,
                            tint = heroSupportingColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = heroSupportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.cjkTextOffset(titleText)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                        ) {
                            @OptIn(ExperimentalMaterial3Api::class)
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above
                                ),
                                tooltip = {
                                    RichTooltip {
                                        Text(
                                            text = estimateInfoTooltipText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                },
                                state = estimateInfoTooltipState,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (estimateInfoTooltipState.isVisible) {
                                            estimateInfoTooltipState.dismiss()
                                        } else {
                                            estimateInfoTooltipScope.launch {
                                                estimateInfoTooltipState.show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_info),
                                        contentDescription = stringResource(R.string.main_e2_estimate_info),
                                        tint = heroSupportingColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    ConstraintLayout(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val (valueRef, unitRef, rangeStatusRef) = createRefs()

                        if (showSkeleton) {
                            AnimatedEllipsis(
                                color = heroContentColor,
                                modifier = Modifier.constrainAs(valueRef) {
                                    start.linkTo(parent.start)
                                    top.linkTo(parent.top)
                                },
                            )
                        } else {
                            Text(
                                text = currentValueText,
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Medium,
                                color = heroContentColor,
                                modifier = Modifier.constrainAs(valueRef) {
                                    start.linkTo(parent.start)
                                    top.linkTo(parent.top)
                                },
                            )
                        }
                        Text(
                            text = unitText,
                            modifier = Modifier.constrainAs(unitRef) {
                                start.linkTo(valueRef.end, margin = 8.dp)
                                baseline.linkTo(valueRef.baseline)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = heroSupportingColor
                        )
                        if (!hideReferenceRanges) {
                            SkeletonOverlay(
                                active = showSkeleton,
                                shape = CircleShape,
                                modifier = Modifier.constrainAs(rangeStatusRef) {
                                    start.linkTo(unitRef.end, margin = 8.dp)
                                    top.linkTo(unitRef.top)
                                    bottom.linkTo(unitRef.bottom)
                                },
                            ) {
                                MainE2RangeStatusPill(
                                    iconDrawableRes = rangeStatusIconDrawableRes,
                                    label = rangeStatusLabel,
                                )
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SkeletonOverlay(
                            active = showSkeleton,
                            shape = CircleShape,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = heroPillContainerColor,
                                contentColor = heroSupportingColor
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = trendIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = heroSupportingColor
                                    )

                                    val sinceYesterdayText = if (effectiveIsTrendDeltaDisplayZero) {
                                        stringResource(R.string.main_e2_no_change_since_yesterday)
                                    } else {
                                        stringResource(
                                            R.string.main_e2_change_since_yesterday,
                                            trendDeltaLabel
                                        )
                                    }
                                    Text(
                                        text = sinceYesterdayText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = heroSupportingColor,
                                        modifier = Modifier.padding(end = 2.dp).cjkTextOffset(sinceYesterdayText)
                                    )
                                }
                            }
                        }

                        MainInfoPill(
                            iconDrawableRes = if (hasPreviousRecord) {
                                R.drawable.ic_check_circle
                            } else {
                                R.drawable.ic_info
                            },
                            text = lastDoseSummary,
                            iconTint = heroSupportingColor,
                            containerColor = heroPillContainerColor,
                            contentColor = heroSupportingColor,
                        )
                    }
                }
            }
        }

        Icon(
            painter = painterResource(R.drawable.ic_notification_icon),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(160.dp)
                .alpha(0.1f)
                .offset(x = 20.dp, y = (-20).dp),
            tint = heroSupportingColor
        )
    }
}

@Composable
private fun MainE2RangeStatusPill(
    iconDrawableRes: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(iconDrawableRes),
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(end = 2.dp).cjkTextOffset(label)
            )
        }
    }
}

@Composable
internal fun MainE2ChartCard(
    section: MainE2ChartUiState,
    now: LocalDateTime,
    appLocale: Locale,
    unit: String,
    displayUnit: BloodUnitKey,
    targetRangeLow: Double,
    targetRangeHigh: Double,
    modifier: Modifier = Modifier,
    trendReady: Boolean = true,
    hideReferenceRanges: Boolean = false,
    onChartWindowOptionSelected: (HomeE2ChartWindowOption) -> Unit = { },
) {
    if (!trendReady) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .testTag(MainE2ChartSkeletonTestTag)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .padding(bottom = 6.dp),
            ) {
                MainE2ChartCardHeader(
                    modifier = Modifier.padding(vertical = 4.dp),
                    targetRangeLow = targetRangeLow,
                    targetRangeHigh = targetRangeHigh,
                    displayUnit = displayUnit,
                    unit = unit,
                    hideReferenceRanges = hideReferenceRanges,
                    chartWindowOption = section.chartWindowOption,
                    onChartWindowOptionSelected = onChartWindowOptionSelected,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MainE2ChartPlotHeight)
                    ) {
                        SkeletonShimmerBlock(
                            modifier = Modifier.fillMaxSize(),
                            shape = MaterialTheme.shapes.large,
                        )
                    }
                    // Mirror MainE2ChartMinimap's vertical structure with
                    // transparent stand-ins so the row claims its real
                    // intrinsic height (canvas 48dp + labelMedium date row +
                    // bottom padding), then cover everything with a single
                    // shimmer overlay. Avoids drift from font/locale changes.
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(bottom = 4.dp)) {
                            Spacer(modifier = Modifier.height(MainE2ChartMinimapHeight))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .padding(bottom = 4.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "—",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Transparent,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(
                                    modifier = Modifier.size(
                                        width = MainE2ChartMinimapResetButtonSize,
                                        height = 16.dp,
                                    ),
                                )
                            }
                        }
                        SkeletonShimmerBlock(
                            modifier = Modifier.matchParentSize(),
                            shape = MaterialTheme.shapes.large,
                        )
                    }
                }
            }
        }
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    val chartAnimationsEnabled = remember {
        mutableStateOf(!mainE2ChartInitialAnimationConsumed)
    }
    val pointXHours = remember(section.points, section.pointXHours) {
        section.pointXHours
            .takeIf { xHours -> xHours.size == section.points.size }
            ?: section.points.indices.map { index -> index * section.sampleIntervalHours.toDouble() }
    }
    // Two solid line series, no dashed stroke (which had a phase-shift artifact
    // on zoom because Vico's Dashed is anchored to screen pixels):
    // - Series 1: window-start → now, full alpha, area-filled.
    // - Series 2: window-start → end, alpha-faded via a horizontal gradient
    //   brush. Full alpha through "now"; nonlinear (1−t)² ease-out fade to
    //   floor alpha after "now"; never reaches 0. In the [0, now] range Series
    //   2 sits over Series 1 with full alpha — visually identical to a single
    //   solid line. Past "now", only the faded Series 2 is visible.
    val chartWindowHours = section.windowHours.coerceAtLeast(1)
    val currentTimeXHours = section.predictionStartXHours
        .coerceIn(0.0, chartWindowHours.toDouble())
    val splitChartSeries = remember(
        pointXHours,
        section.points,
        currentTimeXHours,
    ) {
        splitMainE2ChartSeries(
            xHours = pointXHours,
            points = section.points,
            observedEndXHours = currentTimeXHours,
            predictedStartXHours = 0.0,
        )
    }
    // Split markers into logged (real entries, primary color) and planned
    // (synthesized future-schedule slots, secondary color). A real entry that
    // fulfills a planned slot drops it back into the logged bucket via
    // PkDoseMarker.isPlanned, so the marker color flips back to primary as
    // soon as the user records the dose.
    val doseMarkerLoggedXHours = remember(section.doseMarkers) {
        section.doseMarkers.filterNot { it.isPlanned }.map { it.xHours }
    }
    val doseMarkerLoggedConcentrations = remember(section.doseMarkers) {
        section.doseMarkers.filterNot { it.isPlanned }.map { it.concentration }
    }
    val doseMarkerPlannedXHours = remember(section.doseMarkers) {
        section.doseMarkers.filter { it.isPlanned }.map { it.xHours }
    }
    val doseMarkerPlannedConcentrations = remember(section.doseMarkers) {
        section.doseMarkers.filter { it.isPlanned }.map { it.concentration }
    }
    val doseMarkerXHours = remember(section.doseMarkers) {
        section.doseMarkers.map { marker -> marker.xHours }
    }
    val doseMarkerConcentrations = remember(section.doseMarkers) {
        section.doseMarkers.map { marker -> marker.concentration }
    }
    val currentTimeConcentration = remember(currentTimeXHours, pointXHours, section.points) {
        mainE2ChartConcentrationAtX(
            xHours = currentTimeXHours,
            pointXHours = pointXHours,
            points = section.points,
        )
    }
    val chartWindowStart = remember(now, section.pastDays) {
        mainE2ChartWindowStart(now, section.pastDays)
    }
    val chartTimeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val chartDateFormatter = remember(appLocale, now.year) {
        mainE2ChartMarkerDateFormatter(
            locale = appLocale,
            currentYear = now.year,
        )
    }
    val interactiveMarkerXHours = remember { mutableStateOf<Double?>(null) }
    val interactiveMarkerXHoursValue = interactiveMarkerXHours.value
    // The touch marker's x is captured in whatever coord space was active
    // when the user tapped, so it has no meaningful position after the
    // chart-window changes. Clear it on toggle so it doesn't paint at a
    // stale pixel in the new window.
    LaunchedEffect(section.chartWindowOption) {
        interactiveMarkerXHours.value = null
    }
    val interactionMarkerConcentration = remember(
        interactiveMarkerXHoursValue,
        pointXHours,
        section.points,
    ) {
        interactiveMarkerXHoursValue?.let { xHours ->
            mainE2ChartConcentrationAtX(
                xHours = xHours,
                pointXHours = pointXHours,
                points = section.points,
            )
        }
    }
    val interactiveMarkerLabel = remember(
        interactiveMarkerXHoursValue,
        interactionMarkerConcentration,
        chartWindowStart,
        chartWindowHours,
        chartDateFormatter,
        chartTimeFormatter,
        unit,
    ) {
        val xHours = interactiveMarkerXHoursValue
        val concentration = interactionMarkerConcentration
        if (xHours != null && concentration != null) {
            val dateTime = mainE2ChartDisplayDateTimeForXHours(
                chartWindowStart = chartWindowStart,
                xHours = xHours,
                chartWindowHours = chartWindowHours,
            )
            val date = chartDateFormatter(dateTime.toLocalDate())
            val time = dateTime.format(chartTimeFormatter)
            "$date $time\n${formatMainE2ConcentrationValue(concentration.toDouble(), displayUnit)} $unit"
        } else {
            null
        }
    }
    val bottomAxisItemPlacer = remember { ExtraStoreAwareHorizontalAxisItemPlacer() }
    val yAxisSpec = remember(section.points, section.doseMarkers) {
        mainE2ChartYAxisSpec(
            points = section.points,
            doseMarkers = section.doseMarkers,
        )
    }
    val startAxisItemPlacer = remember(yAxisSpec.tickStep) {
        VerticalAxis.ItemPlacer.step(
            step = { yAxisSpec.tickStep },
            shiftTopLines = false,
        )
    }
    // Formatter reads chartWindowStart / chartWindowHours / option from the
    // model's ExtraStore so its output matches the data Vico is currently
    // drawing. During a chart-window toggle this means labels stay on the
    // old format until Vico processes the new transaction; both update
    // atomically when the new model + extras land.
    val bottomAxisValueFormatter = remember(appLocale) {
        CartesianValueFormatter { context, value, _ ->
            val spec = context.model.extraStore.getOrNull(MainE2ChartViewSpecKey)
                ?: return@CartesianValueFormatter ""
            val date = mainE2ChartDisplayDateTimeForXHours(
                chartWindowStart = spec.chartWindowStart,
                xHours = value,
                chartWindowHours = spec.chartWindowHours,
            ).toLocalDate()
            when (spec.chartWindowOption) {
                HomeE2ChartWindowOption.SEVEN_DAYS ->
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, appLocale)
                HomeE2ChartWindowOption.THIRTY_DAYS ->
                    date.format(MainE2ChartAxisDateFormatter)
            }
        }
    }
    // X range is read from the ExtraStore so it matches the data Vico is
    // currently drawing — on a chart-window toggle, the maxX stays on the
    // old value until the new transaction's extras land alongside the new
    // series, so axis labels, decorations, and minimap all stay in
    // lockstep. Y range stays keyed on the live yAxisSpec because the
    // user-reported toggle artifacts are X-axis only.
    val rangeProvider = remember(yAxisSpec) {
        object : CartesianLayerRangeProvider {
            override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = 0.0
            override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double {
                return extraStore.getOrNull(MainE2ChartViewSpecKey)
                    ?.chartWindowHours
                    ?.toDouble()
                    ?: maxX
            }
            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = 0.0
            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
                yAxisSpec.maxY
        }
    }
    // Hoisted up so maxChartZoom can recompute against the measured plot
    // width once .onSizeChanged fires; rememberVicoZoomState is keyed on
    // option + width so the floor reflects the live form factor.
    val chartSize = remember { mutableStateOf(IntSize.Zero) }
    val measuredChartWidthPx = chartSize.value.width
    val maxChartZoom = remember(section.chartWindowOption, measuredChartWidthPx) {
        Zoom.max(
            Zoom.Content,
            Zoom.x(
                mainE2ChartMaxZoomXRangeHours(
                    option = section.chartWindowOption,
                    chartPixelWidthPx = measuredChartWidthPx,
                )
            ),
        )
    }
    val chartViewportResetVersion = rememberSaveable { mutableIntStateOf(0) }
    val (chartScrollState, chartZoomState) = key(
        chartViewportResetVersion.intValue,
        section.chartWindowOption,
        measuredChartWidthPx,
    ) {
        rememberVicoScrollState(scrollEnabled = true) to rememberVicoZoomState(
            zoomEnabled = true,
            initialZoom = Zoom.Content,
            minZoom = Zoom.Content,
            maxZoom = maxChartZoom,
        )
    }
    val chartCoroutineScope = rememberCoroutineScope()
    val chartAnimationSpec = if (chartAnimationsEnabled.value) {
        tween<Float>(durationMillis = MainE2ChartInitialAnimationMillis)
    } else {
        null
    }

    LaunchedEffect(Unit) {
        if (!mainE2ChartInitialAnimationConsumed) {
            mainE2ChartInitialAnimationConsumed = true
            delay(MainE2ChartInitialAnimationMillis + MainE2ChartAnimationSettleDelayMillis)
            chartAnimationsEnabled.value = false
        }
    }

    LaunchedEffect(
        splitChartSeries,
        doseMarkerLoggedXHours,
        doseMarkerLoggedConcentrations,
        doseMarkerPlannedXHours,
        doseMarkerPlannedConcentrations,
        currentTimeXHours,
        currentTimeConcentration,
        section.chartWindowOption,
        chartWindowStart,
        chartWindowHours,
        section.pastDays,
        now,
    ) {
        modelProducer.runTransaction {
            lineSeries {
                // Solid line: window start → today midnight (with area fill).
                series(
                    x = splitChartSeries.observedXHours,
                    y = splitChartSeries.observedPoints,
                )
                // Dashed line: today midnight → window end (no area fill).
                series(
                    x = splitChartSeries.predictedXHours,
                    y = splitChartSeries.predictedPoints,
                )
                // "You are here" dot — a one-point series so it animates in
                // alongside the dose markers instead of popping in flat. Sits
                // in slot 3 (unconditional) because Vico matches series to
                // LineProvider entries by index and rejects empty series, so
                // any conditional series has to come last.
                series(
                    x = listOf(currentTimeXHours),
                    y = listOf(currentTimeConcentration),
                )
                // Logged dose markers (primary color) and planned ones
                // (secondary color) live in separate slots so they can use
                // different point styles. Both series are emitted whenever
                // there is at least one marker — the absent one is padded with
                // an out-of-axis sentinel point so the LineProvider's
                // index-based style mapping stays stable.
                val hasAnyMarker = doseMarkerLoggedXHours.isNotEmpty() ||
                        doseMarkerPlannedXHours.isNotEmpty()
                if (hasAnyMarker) {
                    series(
                        x = doseMarkerLoggedXHours.ifEmpty { listOf(OffAxisDoseMarkerSentinelXHours) },
                        y = doseMarkerLoggedConcentrations.ifEmpty { listOf(OffAxisDoseMarkerSentinelConcentration) },
                    )
                    series(
                        x = doseMarkerPlannedXHours.ifEmpty { listOf(OffAxisDoseMarkerSentinelXHours) },
                        y = doseMarkerPlannedConcentrations.ifEmpty { listOf(OffAxisDoseMarkerSentinelConcentration) },
                    )
                }
            }
            extras { extraStore ->
                extraStore[MainE2ChartViewSpecKey] = MainE2ChartViewSpec(
                    chartWindowOption = section.chartWindowOption,
                    chartWindowStart = chartWindowStart,
                    chartWindowHours = chartWindowHours,
                    currentTimeXHours = currentTimeXHours,
                    pastDays = section.pastDays,
                    now = now,
                )
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).padding(bottom = 6.dp)
        ) {

            MainE2ChartCardHeader(
                modifier = Modifier.padding(vertical = 4.dp),
                targetRangeLow = targetRangeLow,
                targetRangeHigh = targetRangeHigh,
                displayUnit = displayUnit,
                unit = unit,
                hideReferenceRanges = hideReferenceRanges,
                chartWindowOption = section.chartWindowOption,
                onChartWindowOptionSelected = onChartWindowOptionSelected,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val lineColor = MaterialTheme.colorScheme.primary
                val doseMarkerColor = MaterialTheme.colorScheme.primary
                val plannedDoseMarkerColor = MaterialTheme.colorScheme.outlineVariant
                val currentTimeColor = MaterialTheme.colorScheme.tertiary
                val currentTimeLineColor =
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                val interactionMarkerColor = MaterialTheme.colorScheme.onSurfaceVariant
                val markerSurfaceColor = MaterialTheme.colorScheme.surfaceContainer
                val chartCoordinateMapper = remember(chartViewportResetVersion.intValue) {
                    MainE2ChartCoordinateMapper()
                }
                val markerLabelSize = remember { mutableStateOf(IntSize.Zero) }
                val currentTimeDecoration = remember(
                    currentTimeLineColor,
                    chartCoordinateMapper,
                ) {
                    VerticalLineDecoration(
                        xProvider = { ctx ->
                            ctx.model.extraStore
                                .getOrNull(MainE2ChartViewSpecKey)
                                ?.currentTimeXHours
                        },
                        lineColor = currentTimeLineColor,
                        coordinateMapper = chartCoordinateMapper,
                    )
                }
                val interactionMarkerDecoration = remember(
                    interactiveMarkerXHoursValue,
                    interactionMarkerConcentration,
                    interactionMarkerColor,
                    markerSurfaceColor,
                    yAxisSpec.maxY,
                    chartCoordinateMapper,
                ) {
                    val xHours = interactiveMarkerXHoursValue
                    val concentration = interactionMarkerConcentration
                    if (xHours != null && concentration != null) {
                        VerticalLineDecoration(
                            xProvider = { _ -> xHours },
                            lineColor = interactionMarkerColor,
                            dashed = true,
                            coordinateMapper = chartCoordinateMapper,
                            clampToLayerBounds = true,
                            pointY = concentration.toDouble(),
                            pointMaxY = yAxisSpec.maxY,
                            pointFillColor = interactionMarkerColor,
                            pointStrokeColor = markerSurfaceColor,
                        )
                    } else {
                        null
                    }
                }
                val doseMarkerPoint = remember(doseMarkerColor, markerSurfaceColor) {
                    LineCartesianLayer.Point(
                        component = ShapeComponent(
                            fill = Fill(doseMarkerColor),
                            shape = CircleShape,
                            strokeFill = Fill(markerSurfaceColor),
                            strokeThickness = 1.dp,
                        ),
                        size = 7.dp,
                    )
                }
                val plannedDoseMarkerPoint = remember(plannedDoseMarkerColor, markerSurfaceColor) {
                    LineCartesianLayer.Point(
                        component = ShapeComponent(
                            fill = Fill(plannedDoseMarkerColor),
                            shape = CircleShape,
                            strokeFill = Fill(markerSurfaceColor),
                            strokeThickness = 1.dp,
                        ),
                        size = 7.dp,
                    )
                }
                val currentTimePoint = remember(currentTimeColor, markerSurfaceColor) {
                    LineCartesianLayer.Point(
                        component = ShapeComponent(
                            fill = Fill(currentTimeColor),
                            shape = CircleShape,
                            strokeFill = Fill(markerSurfaceColor),
                            strokeThickness = 1.dp,
                        ),
                        size = 8.dp,
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Box(
                            modifier = Modifier
                                .testTag(MainE2ChartContentTestTag)
                                .fillMaxWidth()
                                .height(MainE2ChartPlotHeight)
                                .padding(MainE2ChartContentPadding)
                                .onSizeChanged { chartSize.value = it }
                                .pointerInput(chartCoordinateMapper, chartWindowHours) {
                                    detectMainE2ChartMarkerGestures(
                                        coordinateMapper = chartCoordinateMapper,
                                        chartWindowHours = chartWindowHours,
                                        onMarkerXChanged = { xHours ->
                                            interactiveMarkerXHours.value = xHours
                                        },
                                    )
                                }
                        ) {
                            CartesianChartHost(
                                chart = rememberCartesianChart(
                                    rememberEdgeAlignedLineCartesianLayer(
                                        lineProvider =
                                            LineCartesianLayer.LineProvider.series(
                                                LineCartesianLayer.rememberLine(
                                                    fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
                                                    areaFill =
                                                        LineCartesianLayer.AreaFill.single(
                                                            Fill(
                                                                Brush.verticalGradient(
                                                                    listOf(
                                                                        lineColor.copy(
                                                                            alpha = 0.4f
                                                                        ), Color.Transparent
                                                                    )
                                                                )
                                                            )
                                                        ),
                                                    interpolator = LineCartesianLayer.Interpolator.catmullRom(),
                                                ),
                                                LineCartesianLayer.rememberLine(
                                                    fill = LineCartesianLayer.LineFill.single(
                                                        Fill(lineColor.copy(alpha = MainE2ChartPredictedAlpha))
                                                    ),
                                                    interpolator = LineCartesianLayer.Interpolator.catmullRom(),
                                                ),
                                                LineCartesianLayer.rememberLine(
                                                    fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                                                    stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
                                                    pointProvider = LineCartesianLayer.PointProvider.single(currentTimePoint),
                                                ),
                                                LineCartesianLayer.rememberLine(
                                                    fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                                                    stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
                                                    pointProvider = LineCartesianLayer.PointProvider.single(doseMarkerPoint),
                                                ),
                                                LineCartesianLayer.rememberLine(
                                                    fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                                                    stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
                                                    pointProvider = LineCartesianLayer.PointProvider.single(plannedDoseMarkerPoint),
                                                ),
                                            ),
                                        rangeProvider = rangeProvider,
                                    ),
                                    decorations = listOfNotNull(
                                        currentTimeDecoration,
                                        interactionMarkerDecoration,
                                    ),
                                    startAxis = VerticalAxis.rememberStart(
                                        valueFormatter = CartesianValueFormatter { _, value, _ ->
                                            formatMainE2ConcentrationValue(value, displayUnit)
                                        },
                                        line = null,
                                        tick = null,
                                        itemPlacer = startAxisItemPlacer,
                                    ),
                                    bottomAxis = HorizontalAxis.rememberBottom(
                                        valueFormatter = bottomAxisValueFormatter,
                                        tick = null,
                                        guideline = null,
                                        itemPlacer = bottomAxisItemPlacer,
                                    ),
                                    getXStep = { 1.0 },
                                ),
                                modelProducer = modelProducer,
                                modifier = Modifier.matchParentSize(),
                                animationSpec = chartAnimationSpec,
                                animateIn = chartAnimationsEnabled.value,
                                scrollState = chartScrollState,
                                zoomState = chartZoomState,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = MainE2ChartContentPadding)
                    ) {
                        val chartContentPaddingPx = with(LocalDensity.current) {
                            MainE2ChartContentPadding.toPx()
                        }
                        val markerLabelGapPx = with(LocalDensity.current) {
                            MainE2ChartMarkerLabelGap.toPx()
                        }
                        val markerLabel = interactiveMarkerLabel
                        val markerCanvasX = interactiveMarkerXHours.value
                            ?.let { xHours -> chartCoordinateMapper.canvasXForXHours(xHours) }
                        val markerCanvasTopY = chartCoordinateMapper.layerTopCanvasY()
                        if (markerLabel != null && markerCanvasX != null && markerCanvasTopY != null) {
                            Surface(
                                modifier = Modifier
                                    .onSizeChanged { markerLabelSize.value = it }
                                    .offset {
                                        val labelWidth = markerLabelSize.value.width
                                        val labelHeight = markerLabelSize.value.height
                                        val maxXOffset = (chartSize.value.width - labelWidth).coerceAtLeast(0)
                                        IntOffset(
                                            x = (markerCanvasX.roundToInt() - labelWidth / 2)
                                                .coerceIn(0, maxXOffset),
                                            y = (chartContentPaddingPx + markerCanvasTopY - labelHeight - markerLabelGapPx)
                                                .roundToInt(),
                                        )
                                    },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                shadowElevation = 3.dp
                            ) {
                                Text(
                                    text = markerLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                MainE2ChartMinimap(
                    pointXHours = pointXHours,
                    points = section.points,
                    currentTimeXHours = currentTimeXHours,
                    chartWindowStart = chartWindowStart,
                    dateFormatter = chartDateFormatter,
                    chartWindowHours = chartWindowHours,
                    maxY = yAxisSpec.maxY,
                    coordinateMapper = chartCoordinateMapper,
                    scrollState = chartScrollState,
                    zoomState = chartZoomState,
                    onReset = {
                        chartViewportResetVersion.intValue += 1
                    },
                    onViewportScrollFractionChanged = { scrollFraction ->
                        chartCoroutineScope.launch {
                            chartScrollState.scroll(
                                Scroll.Absolute.pixels(chartScrollState.maxValue * scrollFraction)
                            )
                        }
                    },
                )
            }
        }
    }
}

internal data class MainE2ChartVisibleXRange(
    val start: Double,
    val endInclusive: Double,
)

internal fun canResetMainE2ChartMinimap(
    visibleRange: MainE2ChartVisibleXRange,
    chartWindowHours: Int,
    hasPendingReset: Boolean,
): Boolean {
    return !hasPendingReset &&
            mainE2ChartMinimapIsZoomed(
                visibleRange = visibleRange,
                chartWindowHours = chartWindowHours,
            )
}

internal fun resolveMainE2ChartMinimapDateLabelRange(
    visibleRange: MainE2ChartVisibleXRange,
    pendingResetRange: MainE2ChartVisibleXRange?,
): MainE2ChartVisibleXRange {
    return pendingResetRange ?: visibleRange
}

internal fun mainE2ChartDisplayDateTimeForXHours(
    chartWindowStart: LocalDateTime,
    xHours: Double,
    chartWindowHours: Int,
): LocalDateTime {
    val fullHours = chartWindowHours.toDouble().coerceAtLeast(1.0)
    val fullMinutes = (fullHours * 60).roundToLong().coerceAtLeast(1L)
    val safeXHours = if (xHours.isFinite()) xHours else 0.0
    val displayMinutes = (safeXHours.coerceIn(0.0, fullHours) * 60)
        .roundToLong()
        .coerceIn(0L, fullMinutes - 1L)
    return chartWindowStart.plusMinutes(displayMinutes)
}

private fun mainE2ChartMinimapIsZoomed(
    visibleRange: MainE2ChartVisibleXRange,
    chartWindowHours: Int,
): Boolean {
    val fullHours = chartWindowHours.toDouble().coerceAtLeast(1.0)
    val start = visibleRange.start.coerceIn(0.0, fullHours)
    val end = visibleRange.endInclusive.coerceIn(0.0, fullHours)
    return start > MainE2ChartMinimapRangeEpsilonHours ||
            end < fullHours - MainE2ChartMinimapRangeEpsilonHours
}

private fun mainE2ChartMinimapFullVisibleRange(chartWindowHours: Int): MainE2ChartVisibleXRange {
    return MainE2ChartVisibleXRange(
        start = 0.0,
        endInclusive = chartWindowHours.toDouble().coerceAtLeast(1.0),
    )
}

private data class MainE2ChartViewportSnapshot(
    val rawVisibleRange: MainE2ChartVisibleXRange,
    val visibleRange: MainE2ChartVisibleXRange,
    val spec: MainE2ChartViewSpec?,
)

// View-side spec for the E2 chart, dispatched into the model's ExtraStore
// alongside the series so Vico applies them atomically. Axis placer, label
// formatter, range provider, decoration anchor, and the minimap all read this
// from `context.model.extraStore` — so the visible spec always reflects the
// data Vico is currently drawing. Without this, composition state changes
// (e.g. on chart-window toggle) race ahead of Vico's model update and the
// view spec lands one frame too early on the old data.
private data class MainE2ChartViewSpec(
    val chartWindowOption: HomeE2ChartWindowOption,
    val chartWindowStart: LocalDateTime,
    val chartWindowHours: Int,
    val currentTimeXHours: Double,
    val pastDays: Long,
    val now: LocalDateTime,
)

private val MainE2ChartViewSpecKey = ExtraStore.Key<MainE2ChartViewSpec>()

@Composable
private fun MainE2ChartMinimap(
    pointXHours: List<Double>,
    points: List<Float>,
    currentTimeXHours: Double,
    chartWindowStart: LocalDateTime,
    dateFormatter: LocalDateFormatter,
    chartWindowHours: Int,
    maxY: Double,
    coordinateMapper: MainE2ChartCoordinateMapper,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    onReset: () -> Unit,
    onViewportScrollFractionChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overviewDescription = stringResource(R.string.main_e2_chart_overview)
    val resetDescription = stringResource(R.string.main_e2_chart_reset_view)
    val colorScheme = MaterialTheme.colorScheme
    val lineColor = colorScheme.primary
    val currentTimeLineColor = colorScheme.tertiary.copy(alpha = 0.5f)
    val trackColor = colorScheme.surfaceContainerHigh
    val viewportColor = colorScheme.surfaceContainerHighest
    val viewportStrokeColor = colorScheme.outlineVariant
    val subtleLineColor = colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val minimapHorizontalInsetPx = with(LocalDensity.current) {
        MainE2ChartMinimapHorizontalInset.toPx()
    }
    val minimapCanvasSize = remember { mutableStateOf(IntSize.Zero) }
    val startDateLabelSize = remember { mutableStateOf(IntSize.Zero) }
    val endDateLabelSize = remember { mutableStateOf(IntSize.Zero) }
    val viewportSnapshot = coordinateMapper.viewportSnapshot.collectAsState().value
    // chartWindowStart and chartWindowHours come from MainE2ChartCard's
    // LIVE values, which are deterministic from `now` and pastDays, so
    // date labels and reset state can update in the same frame as the
    // minimap canvas — no need to wait for Vico's draw. The snapshot is
    // still useful for the *visible range* (zoom/scroll), but its spec
    // lags Vico's render by a frame, so on a chart-window toggle the
    // snapshot's visible range is briefly stale and would label the new
    // window through the old visible portion. Treat the snapshot as
    // stale whenever its spec disagrees with the LIVE window and fall
    // back to the full new range, which is also where a toggle resets
    // scroll/zoom to anyway.
    val snapshotMatchesLiveWindow =
        viewportSnapshot?.spec?.chartWindowHours == chartWindowHours
    val fullHours = chartWindowHours.toDouble().coerceAtLeast(1.0)
    val fullVisibleRange = remember(chartWindowHours) {
        mainE2ChartMinimapFullVisibleRange(chartWindowHours)
    }
    val pendingResetDateLabelRange = remember(chartWindowHours) {
        mutableStateOf<MainE2ChartVisibleXRange?>(null)
    }
    val rawVisibleRange = viewportSnapshot
        ?.takeIf { snapshotMatchesLiveWindow }
        ?.rawVisibleRange
        ?: MainE2ChartVisibleXRange(0.0, fullHours)
    val visibleRange = viewportSnapshot
        ?.takeIf { snapshotMatchesLiveWindow }
        ?.visibleRange
        ?: MainE2ChartVisibleXRange(0.0, fullHours)
    val pendingResetRange = pendingResetDateLabelRange.value
    LaunchedEffect(visibleRange, pendingResetRange) {
        if (
            pendingResetRange != null &&
            !mainE2ChartMinimapIsZoomed(
                visibleRange = visibleRange,
                chartWindowHours = chartWindowHours,
            )
        ) {
            pendingResetDateLabelRange.value = null
        }
    }
    val dateLabelVisibleRange = resolveMainE2ChartMinimapDateLabelRange(
        visibleRange = visibleRange,
        pendingResetRange = pendingResetRange,
    )
    val resetEnabled = canResetMainE2ChartMinimap(
        visibleRange = visibleRange,
        chartWindowHours = chartWindowHours,
        hasPendingReset = pendingResetRange != null,
    )
    val startDateLabel = remember(
        chartWindowStart,
        chartWindowHours,
        dateFormatter,
        dateLabelVisibleRange.start,
    ) {
        dateFormatter(
            mainE2ChartDisplayDateTimeForXHours(
                chartWindowStart = chartWindowStart,
                xHours = dateLabelVisibleRange.start,
                chartWindowHours = chartWindowHours,
            )
                .toLocalDate()
        )
    }
    val endDateLabel = remember(
        chartWindowStart,
        chartWindowHours,
        dateFormatter,
        dateLabelVisibleRange.endInclusive,
    ) {
        dateFormatter(
            mainE2ChartDisplayDateTimeForXHours(
                chartWindowStart = chartWindowStart,
                xHours = dateLabelVisibleRange.endInclusive,
                chartWindowHours = chartWindowHours,
            )
                .toLocalDate()
        )
    }
    val minimapMinViewportWidthPx = with(LocalDensity.current) { 4.dp.toPx() }

    fun viewportMetrics(canvasWidth: Float): Pair<Float, Float> {
        val plotWidth = (canvasWidth - minimapHorizontalInsetPx * 2).coerceAtLeast(1f)
        val visibleSpan = (rawVisibleRange.endInclusive - rawVisibleRange.start)
            .coerceIn(0.0, fullHours)
        val rawViewportWidth = (visibleSpan / fullHours).toFloat() * plotWidth
        val viewportWidth = if (scrollState.maxValue <= 0f) {
            plotWidth
        } else {
            rawViewportWidth.coerceAtLeast(minimapMinViewportWidthPx)
        }.coerceAtMost(plotWidth)
        val scrollablePlotWidth = (plotWidth - viewportWidth).coerceAtLeast(0f)
        val viewportLeft = minimapHorizontalInsetPx + if (scrollState.maxValue <= 0f) {
            0f
        } else {
            (scrollState.value / scrollState.maxValue).coerceIn(0f, 1f) * scrollablePlotWidth
        }
        return viewportLeft to viewportWidth
    }

    fun viewportMetricsForRange(
        canvasWidth: Float,
        visibleRange: MainE2ChartVisibleXRange,
    ): Pair<Float, Float> {
        val plotWidth = (canvasWidth - minimapHorizontalInsetPx * 2).coerceAtLeast(1f)
        val startFraction = (visibleRange.start.coerceIn(0.0, fullHours) / fullHours)
            .toFloat()
        val endFraction = (visibleRange.endInclusive.coerceIn(0.0, fullHours) / fullHours)
            .toFloat()
        val leftFraction = minOf(startFraction, endFraction)
        val rightFraction = maxOf(startFraction, endFraction)
        return minimapHorizontalInsetPx + leftFraction * plotWidth to
                ((rightFraction - leftFraction) * plotWidth).coerceIn(0f, plotWidth)
    }

    fun dateLabelViewportMetrics(canvasWidth: Float): Pair<Float, Float> {
        return pendingResetRange
            ?.let { range -> viewportMetricsForRange(canvasWidth, range) }
            ?: viewportMetrics(canvasWidth)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surfaceContainer,
        contentColor = colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MainE2ChartMinimapHeight)
                    .padding(horizontal = 8.dp)
                    .padding(top = 4.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    Spacer(
                        modifier = Modifier
                            .matchParentSize()
                            .onSizeChanged { minimapCanvasSize.value = it }
                            .pointerInput(
                                chartWindowHours,
                                minimapHorizontalInsetPx,
                                onViewportScrollFractionChanged,
                            ) {
                                var dragPointerOffsetFromViewportStartPx = 0f
                                var dragViewportWidthPx = 0f

                                fun viewportMetrics(): Pair<Float, Float> {
                                    val fullHours = chartWindowHours.toDouble().coerceAtLeast(1.0)
                                    val plotWidth =
                                        (size.width.toFloat() - minimapHorizontalInsetPx * 2)
                                            .coerceAtLeast(1f)
                                    val maxScroll = scrollState.maxValue
                                    if (maxScroll <= 0f) return 0f to plotWidth
                                    val visibleRange = coordinateMapper.rawVisibleXRange()
                                        ?: MainE2ChartVisibleXRange(0.0, fullHours)
                                    val visibleSpan = (visibleRange.endInclusive - visibleRange.start)
                                        .coerceIn(0.0, fullHours)
                                    val viewportWidth = (visibleSpan / fullHours).toFloat()
                                        .times(plotWidth)
                                        .coerceIn(0f, plotWidth)
                                    val scrollablePlotWidth = (plotWidth - viewportWidth).coerceAtLeast(0f)
                                    val viewportLeft = (scrollState.value / maxScroll)
                                        .coerceIn(0f, 1f) * scrollablePlotWidth
                                    return viewportLeft to viewportWidth
                                }

                                fun scrollFractionFor(pointerX: Float): Float {
                                    val plotWidth =
                                        (size.width.toFloat() - minimapHorizontalInsetPx * 2)
                                            .coerceAtLeast(1f)
                                    val scrollablePlotWidth =
                                        (plotWidth - dragViewportWidthPx).coerceAtLeast(0f)
                                    if (scrollablePlotWidth <= 0f) return 0f
                                    val pointerPlotX =
                                        (pointerX - minimapHorizontalInsetPx).coerceIn(0f, plotWidth)
                                    return ((pointerPlotX - dragPointerOffsetFromViewportStartPx) /
                                            scrollablePlotWidth).coerceIn(0f, 1f)
                                }

                                // Horizontal-only drag detection mirrors Vico's chart, which scrolls
                                // via Modifier.scrollable(Orientation.Horizontal); vertical drags fall
                                // through to the page scroll instead of being consumed here.
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        val (viewportLeft, viewportWidth) = viewportMetrics()
                                        dragViewportWidthPx = viewportWidth
                                        val plotWidth =
                                            (size.width.toFloat() - minimapHorizontalInsetPx * 2)
                                                .coerceAtLeast(1f)
                                        val pointerPlotX =
                                            (offset.x - minimapHorizontalInsetPx)
                                                .coerceIn(0f, plotWidth)
                                        dragPointerOffsetFromViewportStartPx =
                                            if (pointerPlotX in viewportLeft..(viewportLeft + viewportWidth)) {
                                                pointerPlotX - viewportLeft
                                            } else {
                                                viewportWidth / 2f
                                            }
                                        onViewportScrollFractionChanged(scrollFractionFor(offset.x))
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        onViewportScrollFractionChanged(
                                            scrollFractionFor(change.position.x)
                                        )
                                    },
                                )
                            }
                            .drawWithCache {
                                // Pre-compute everything that depends only on size, points,
                                // currentTimeXHours, maxY, chartWindowHours and colors.
                                // The polyline collapses from N drawLine calls into a single
                                // Path so the per-frame draw is one drawPath instead of ~84
                                // separate draw ops at our default sampling rate.
                                val fullHours = chartWindowHours.toDouble().coerceAtLeast(1.0)
                                val yMax = maxY.coerceAtLeast(1.0)
                                val horizontalInset = minimapHorizontalInsetPx
                                val verticalInset = 6.dp.toPx()
                                val plotWidth = (size.width - horizontalInset * 2).coerceAtLeast(1f)
                                val plotHeight = (size.height - verticalInset * 2).coerceAtLeast(1f)
                                val plotTop = verticalInset
                                val plotBottom = plotTop + plotHeight
                                val plotTopLeft = Offset(horizontalInset, plotTop)
                                val plotSize = Size(plotWidth, plotHeight)
                                val cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                val trackPath = Path().apply {
                                    addRoundRect(
                                        RoundRect(
                                            rect = Rect(offset = plotTopLeft, size = plotSize),
                                            cornerRadius = cornerRadius,
                                        )
                                    )
                                }

                                fun xToCanvas(xHours: Double): Float {
                                    return horizontalInset +
                                            (xHours.coerceIn(0.0, fullHours) / fullHours).toFloat() * plotWidth
                                }

                                fun yToCanvas(value: Float): Float {
                                    return plotBottom -
                                            (value.toDouble().coerceIn(0.0, yMax) / yMax).toFloat() * plotHeight
                                }

                                val finitePoints = pointXHours
                                    .zip(points)
                                    .filter { (x, y) -> x.isFinite() && y.isFinite() }
                                    .sortedBy { (x, _) -> x }
                                val seriesPath: Path?
                                val singlePointCenter: Offset?
                                when {
                                    finitePoints.isEmpty() -> {
                                        seriesPath = null
                                        singlePointCenter = null
                                    }
                                    finitePoints.size == 1 -> {
                                        val (x, y) = finitePoints.first()
                                        seriesPath = null
                                        singlePointCenter = Offset(xToCanvas(x), yToCanvas(y))
                                    }
                                    else -> {
                                        seriesPath = Path().apply {
                                            finitePoints.forEachIndexed { index, (x, y) ->
                                                val cx = xToCanvas(x)
                                                val cy = yToCanvas(y)
                                                if (index == 0) moveTo(cx, cy) else lineTo(cx, cy)
                                            }
                                        }
                                        singlePointCenter = null
                                    }
                                }
                                val seriesColor = lineColor.copy(alpha = 0.72f)
                                val currentX = xToCanvas(currentTimeXHours)
                                val seriesStrokeWidth = 1.5.dp.toPx()
                                val pointRadius = 2.dp.toPx()
                                val currentTimeStrokeWidth = 1.dp.toPx()
                                val baselineStrokeWidth = 1.dp.toPx()
                                val viewportStrokeWidth = 1.25.dp.toPx()
                                val viewportStrokeInset = viewportStrokeWidth / 2f
                                val viewportStrokeCornerRadius = CornerRadius(
                                    x = (cornerRadius.x - viewportStrokeInset).coerceAtLeast(0f),
                                    y = (cornerRadius.y - viewportStrokeInset).coerceAtLeast(0f),
                                )
                                val minViewportWidth = 4.dp.toPx()

                                onDrawBehind {
                                    val scrollValue = scrollState.value
                                    val zoomValue = zoomState.value
                                    if (!scrollValue.isFinite() || !zoomValue.isFinite()) {
                                        return@onDrawBehind
                                    }

                                    drawRoundRect(
                                        color = trackColor,
                                        topLeft = plotTopLeft,
                                        size = plotSize,
                                        cornerRadius = cornerRadius,
                                    )

                                    val rawVisibleRange = coordinateMapper.rawVisibleXRange()
                                        ?: MainE2ChartVisibleXRange(0.0, fullHours)
                                    val visibleSpan = (rawVisibleRange.endInclusive - rawVisibleRange.start)
                                        .coerceIn(0.0, fullHours)
                                    val rawViewportWidth = (visibleSpan / fullHours).toFloat() * plotWidth
                                    val viewportWidth = if (scrollState.maxValue <= 0f) {
                                        plotWidth
                                    } else if (rawViewportWidth < minViewportWidth) {
                                        minViewportWidth
                                    } else {
                                        rawViewportWidth
                                    }
                                    val scrollablePlotWidth = (plotWidth - viewportWidth).coerceAtLeast(0f)
                                    val viewportLeft = if (scrollState.maxValue <= 0f) {
                                        horizontalInset
                                    } else {
                                        horizontalInset +
                                                (scrollValue / scrollState.maxValue).coerceIn(0f, 1f) *
                                                scrollablePlotWidth
                                    }

                                    clipPath(trackPath) {
                                        drawRoundRect(
                                            color = viewportColor,
                                            topLeft = Offset(viewportLeft, plotTop),
                                            size = Size(viewportWidth, plotHeight),
                                            cornerRadius = cornerRadius,
                                        )

                                        if (singlePointCenter != null) {
                                            drawCircle(
                                                color = seriesColor,
                                                radius = pointRadius,
                                                center = singlePointCenter,
                                            )
                                        } else if (seriesPath != null) {
                                            drawPath(
                                                path = seriesPath,
                                                color = seriesColor,
                                                style = Stroke(width = seriesStrokeWidth),
                                            )
                                        }

                                        drawLine(
                                            color = currentTimeLineColor,
                                            start = Offset(currentX, plotTop),
                                            end = Offset(currentX, plotBottom),
                                            strokeWidth = currentTimeStrokeWidth,
                                        )

                                        drawRoundRect(
                                            color = viewportStrokeColor,
                                            topLeft = Offset(
                                                x = viewportLeft + viewportStrokeInset,
                                                y = plotTop + viewportStrokeInset,
                                            ),
                                            size = Size(
                                                width = (viewportWidth - viewportStrokeWidth).coerceAtLeast(0f),
                                                height = (plotHeight - viewportStrokeWidth).coerceAtLeast(0f),
                                            ),
                                            cornerRadius = viewportStrokeCornerRadius,
                                            style = Stroke(width = viewportStrokeWidth),
                                        )
                                        drawLine(
                                            color = subtleLineColor,
                                            start = Offset(horizontalInset, plotBottom),
                                            end = Offset(horizontalInset + plotWidth, plotBottom),
                                            strokeWidth = baselineStrokeWidth,
                                        )
                                    }
                                }
                            }
                            .semantics { contentDescription = overviewDescription }
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(MainE2ChartMinimapResetButtonSize)
                        .clip(CircleShape)
                        .clickable(
                            enabled = resetEnabled,
                            role = Role.Button,
                            onClick = {
                                pendingResetDateLabelRange.value = fullVisibleRange
                                onReset()
                            },
                        )
                        .semantics { contentDescription = resetDescription },
                    shape = CircleShape,
                    color = if (resetEnabled) {
                        colorScheme.secondaryContainer.copy(alpha = 0.72f)
                    } else {
                        colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                    },
                    contentColor = if (resetEnabled) {
                        colorScheme.onSecondaryContainer
                    } else {
                        colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_restart_alt),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = startDateLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .onSizeChanged { startDateLabelSize.value = it }
                            .offset {
                                val (viewportLeft, _) = dateLabelViewportMetrics(
                                    minimapCanvasSize.value.width.toFloat()
                                )
                                val maxX = (
                                        minimapCanvasSize.value.width -
                                                startDateLabelSize.value.width
                                        ).coerceAtLeast(0)
                                IntOffset(
                                    x = viewportLeft.roundToInt().coerceIn(0, maxX),
                                    y = 0,
                                )
                            },
                    )
                    Text(
                        text = endDateLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .onSizeChanged { endDateLabelSize.value = it }
                            .offset {
                                val (viewportLeft, viewportWidth) = dateLabelViewportMetrics(
                                    minimapCanvasSize.value.width.toFloat()
                                )
                                val maxX = (
                                        minimapCanvasSize.value.width -
                                                endDateLabelSize.value.width
                                        ).coerceAtLeast(0)
                                val x = (viewportLeft + viewportWidth).roundToInt() -
                                        endDateLabelSize.value.width
                                IntOffset(
                                    x = x.coerceIn(0, maxX),
                                    y = 0,
                                )
                            },
                    )
                }
                Spacer(
                    modifier = Modifier.size(
                        width = MainE2ChartMinimapResetButtonSize,
                        height = 16.dp,
                    ),
                )
            }
        }
    }
}

// Locale-independent M/d. Keeps labels narrow so the adaptive placer can fit
// more ticks even at deep zoom, and intentionally omits the year so a window
// straddling Dec 31 → Jan 1 doesn't render mixed-year labels. Used by the
// 30-day chart only; the 7-day chart sticks with weekday-narrow labels.
private val MainE2ChartAxisDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M/d")

// Single placer for both chart-window options. The strategy (static noon
// ticks for 7-day, adaptive day ticks for 30-day) and all anchor values are
// read from the model's ExtraStore on every call, so the placer's behavior
// always reflects the data Vico is currently drawing — not the LIVE Compose
// state that may have raced ahead during a chart-window toggle. The 7-day
// branch caches its noon-tick list across calls; the 30-day branch
// re-derives daily because tick density depends on the visible zoom.
private class ExtraStoreAwareHorizontalAxisItemPlacer : HorizontalAxis.ItemPlacer {
    private var cachedNoonKey: Triple<LocalDateTime, Int, Long>? = null
    private var cachedNoonTicks: List<Double> = emptyList()

    private fun noonTicksFor(spec: MainE2ChartViewSpec): List<Double> {
        val key = Triple(spec.now, spec.chartWindowHours, spec.pastDays)
        if (cachedNoonKey != key) {
            cachedNoonTicks = mainE2ChartNoonTickHours(
                now = spec.now,
                windowHours = spec.chartWindowHours,
                pastDays = spec.pastDays,
            )
            cachedNoonKey = key
        }
        return cachedNoonTicks
    }

    override fun getShiftExtremeLines(context: CartesianDrawingContext): Boolean = false

    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        val spec = context.model.extraStore.getOrNull(MainE2ChartViewSpecKey)
            ?: return emptyList()
        return when (spec.chartWindowOption) {
            HomeE2ChartWindowOption.SEVEN_DAYS -> noonTicksFor(spec).filter { value ->
                value >= visibleXRange.start && value <= visibleXRange.endInclusive
            }
            HomeE2ChartWindowOption.THIRTY_DAYS -> {
                val visibleHours = visibleXRange.endInclusive - visibleXRange.start
                mainE2ChartAxisLabelTickHours(
                    chartWindowStart = spec.chartWindowStart,
                    chartWindowHours = spec.chartWindowHours,
                    visibleXRange = visibleXRange,
                    intervalDays = mainE2ChartAxisTickIntervalDays(visibleHours),
                )
            }
        }
    }

    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
    ): List<Double> = measurementValues(context, fullXRange)

    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = measurementValues(context, fullXRange)

    override fun getLineValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = getLabelValues(context, visibleXRange, fullXRange, maxLabelWidth)

    override fun getStartLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = 0f

    override fun getEndLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = 0f

    private fun measurementValues(
        context: CartesianMeasuringContext,
        fullXRange: ClosedFloatingPointRange<Double>,
    ): List<Double> {
        val spec = context.model.extraStore.getOrNull(MainE2ChartViewSpecKey)
            ?: return listOf(fullXRange.start)
        val ticks = when (spec.chartWindowOption) {
            HomeE2ChartWindowOption.SEVEN_DAYS -> noonTicksFor(spec)
            HomeE2ChartWindowOption.THIRTY_DAYS -> mainE2ChartAxisLabelTickHours(
                chartWindowStart = spec.chartWindowStart,
                chartWindowHours = spec.chartWindowHours,
                visibleXRange = fullXRange,
                intervalDays = 1L,
            )
        }
        return ticks.ifEmpty { listOf(fullXRange.start) }
    }
}

@Composable
private fun rememberEdgeAlignedLineCartesianLayer(
    lineProvider: LineCartesianLayer.LineProvider,
    rangeProvider: CartesianLayerRangeProvider,
    pointSpacing: Dp = MainE2ChartPointSpacing,
): LineCartesianLayer {
    // The drawing-model key and interpolator carry the in-flight animation state
    // between the chart's `transform` writes and its `drawInternal` reads. Vico's
    // public LineCartesianLayer ctor mints a fresh ExtraStore.Key() per call, so
    // letting `remember(lineProvider, …)` reconstruct the layer when the predicted-
    // line brush updates would orphan the interpolated frames written under the
    // previous key — the next draw reads null from the new key and snaps to the
    // raw model. Pinning both across recompositions matches Vico's own
    // rememberLineCartesianLayer + copy() wrapper pattern.
    val drawingModelKey = remember { ExtraStore.Key<LineCartesianLayerDrawingModel>() }
    val drawingModelInterpolator = remember {
        CartesianLayerDrawingModelInterpolator.default<
                LineCartesianLayerDrawingModel.Entry,
                LineCartesianLayerDrawingModel,
                >()
    }
    return remember(lineProvider, rangeProvider, pointSpacing) {
        EdgeAlignedLineCartesianLayer(
            lineProvider = lineProvider,
            pointSpacing = pointSpacing,
            rangeProvider = rangeProvider,
            drawingModelInterpolator = drawingModelInterpolator,
            drawingModelKey = drawingModelKey,
        )
    }
}

private class EdgeAlignedLineCartesianLayer(
    lineProvider: LineProvider,
    pointSpacing: Dp,
    rangeProvider: CartesianLayerRangeProvider,
    drawingModelInterpolator: CartesianLayerDrawingModelInterpolator<
            LineCartesianLayerDrawingModel.Entry,
            LineCartesianLayerDrawingModel,
            >,
    drawingModelKey: ExtraStore.Key<LineCartesianLayerDrawingModel>,
) : LineCartesianLayer(
    lineProvider = lineProvider,
    pointSpacing = pointSpacing,
    rangeProvider = rangeProvider,
    verticalAxisPosition = null,
    drawingModelInterpolator = drawingModelInterpolator,
    drawingModelKey = drawingModelKey,
) {
    override fun updateDimensions(
        context: CartesianMeasuringContext,
        dimensions: MutableCartesianLayerDimensions,
        model: LineCartesianLayerModel,
    ) {
        with(context) {
            dimensions.ensureValuesAtLeast(
                xSpacing = pointSpacing.pixels,
                scalableStartPadding = layerPadding.scalableStart.pixels,
                scalableEndPadding = layerPadding.scalableEnd.pixels,
                unscalableStartPadding = layerPadding.unscalableStart.pixels,
                unscalableEndPadding = layerPadding.unscalableEnd.pixels,
            )
        }
    }
}

private suspend fun PointerInputScope.detectMainE2ChartMarkerGestures(
    coordinateMapper: MainE2ChartCoordinateMapper,
    chartWindowHours: Int,
    onMarkerXChanged: (Double?) -> Unit,
) {
    fun markerXFor(pointerX: Float): Double {
        val fallbackX = if (size.width > 0) {
            pointerX / size.width * chartWindowHours
        } else {
            0f
        }
        return coordinateMapper.pointerXToXHours(pointerX)
            ?: fallbackX.toDouble().coerceIn(0.0, chartWindowHours.toDouble())
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress == null) {
            onMarkerXChanged(null)
            return@awaitEachGesture
        }

        longPress.consume()
        onMarkerXChanged(markerXFor(longPress.position.x))

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { change -> change.id == down.id }
                ?: event.changes.firstOrNull()
                ?: continue
            change.consume()
            if (!change.pressed) {
                onMarkerXChanged(null)
                break
            }
            onMarkerXChanged(markerXFor(change.position.x))
        }
    }
}

private class MainE2ChartCoordinateMapper {
    private val _viewportSnapshot = MutableStateFlow<MainE2ChartViewportSnapshot?>(null)
    private var drawingStart: Float = 0f
    private var layerLeft: Float = 0f
    private var layerRight: Float = 0f
    private var layerTop: Float = 0f
    private var layerBottom: Float = 0f
    private var minX: Double = 0.0
    private var maxX: Double = 0.0
    private var xStep: Double = 0.0
    private var xSpacing: Float = 0f
    private var layoutDirectionMultiplier: Int = 1
    private var hasLayerBounds: Boolean = false
    private var spec: MainE2ChartViewSpec? = null

    val viewportSnapshot: StateFlow<MainE2ChartViewportSnapshot?> = _viewportSnapshot.asStateFlow()

    fun update(context: CartesianDrawingContext) {
        with(context) {
            drawingStart = (if (isLtr) layerBounds.left else layerBounds.right) +
                    layoutDirectionMultiplier * layerDimensions.startPadding -
                    scroll
            layerLeft = layerBounds.left
            layerRight = layerBounds.right
            layerTop = layerBounds.top
            layerBottom = layerBounds.bottom
            minX = ranges.minX
            maxX = ranges.maxX
            xStep = ranges.xStep
            xSpacing = layerDimensions.xSpacing
            this@MainE2ChartCoordinateMapper.layoutDirectionMultiplier =
                layoutDirectionMultiplier
            hasLayerBounds = true
            // Snapshotting the spec from the same context that gave us the
            // ranges means the minimap reads the spec that matches Vico's
            // current draw — view config and data stay locked together
            // through a chart-window toggle.
            spec = model.extraStore.getOrNull(MainE2ChartViewSpecKey)
        }
        updateViewportSnapshot()
    }

    fun pointerXToXHours(pointerX: Float): Double? {
        val rawXHours = rawXHoursForCanvasX(pointerX) ?: return null
        val visibleRange = visibleXRange() ?: return rawXHours.coerceIn(minX, maxX)
        return rawXHours.coerceIn(
            visibleRange.start,
            visibleRange.endInclusive,
        )
    }

    fun canvasXForXHours(xHours: Double): Float? {
        if (!hasUsableMapping()) return null
        val canvasX = drawingStart +
                layoutDirectionMultiplier *
                xSpacing *
                ((xHours.coerceIn(minX, maxX) - minX) / xStep).toFloat()
        return canvasX.coerceIn(layerLeft, layerRight)
    }

    fun layerTopCanvasY(): Float? {
        return if (hasLayerBounds && layerTop <= layerBottom) {
            layerTop
        } else {
            null
        }
    }

    fun visibleXRange(): MainE2ChartVisibleXRange? {
        return rawVisibleXRange()?.let { range ->
            MainE2ChartVisibleXRange(
                start = range.start.coerceIn(minX, maxX),
                endInclusive = range.endInclusive.coerceIn(minX, maxX),
            )
        }
    }

    fun rawVisibleXRange(): MainE2ChartVisibleXRange? {
        if (!hasLayerBounds || layerLeft >= layerRight) return null
        val leftX = rawXHoursForCanvasX(layerLeft) ?: return null
        val rightX = rawXHoursForCanvasX(layerRight) ?: return null
        return MainE2ChartVisibleXRange(
            start = minOf(leftX, rightX),
            endInclusive = maxOf(leftX, rightX),
        )
    }

    private fun rawXHoursForCanvasX(canvasX: Float): Double? {
        if (!hasUsableMapping()) return null
        return minX +
                (canvasX - drawingStart) /
                (layoutDirectionMultiplier * xSpacing) *
                xStep
    }

    private fun hasUsableMapping(): Boolean {
        return xStep != 0.0 && xSpacing != 0f && minX <= maxX
    }

    private fun updateViewportSnapshot() {
        val rawVisibleRange = rawVisibleXRange()
        val visibleRange = visibleXRange()
        val nextSnapshot = if (rawVisibleRange != null && visibleRange != null) {
            MainE2ChartViewportSnapshot(
                rawVisibleRange = rawVisibleRange,
                visibleRange = visibleRange,
                spec = spec,
            )
        } else {
            null
        }
        if (_viewportSnapshot.value != nextSnapshot) {
            _viewportSnapshot.value = nextSnapshot
        }
    }
}

private const val MainE2ChartPredictedAlpha = 0.5f

private fun mainE2ChartConcentrationAtX(
    xHours: Double,
    pointXHours: List<Double>,
    points: List<Float>,
): Float {
    if (pointXHours.isEmpty() || points.isEmpty()) {
        return 0f
    }
    val lastIndex = minOf(pointXHours.lastIndex, points.lastIndex)
    if (lastIndex <= 0 || xHours <= pointXHours.first()) {
        return points.first()
    }
    for (index in 1..lastIndex) {
        val rightX = pointXHours[index]
        if (xHours <= rightX) {
            val leftX = pointXHours[index - 1]
            val leftY = points[index - 1]
            val rightY = points[index]
            val span = rightX - leftX
            if (span <= 0.0) {
                return rightY
            }
            val fraction = ((xHours - leftX) / span).coerceIn(0.0, 1.0).toFloat()
            return leftY + (rightY - leftY) * fraction
        }
    }
    return points[lastIndex]
}

private fun mainE2ChartMarkerDateFormatter(
    locale: Locale,
    currentYear: Int,
): LocalDateFormatter {
    val currentYearFormatter = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, "Md"),
        locale,
    )
    val otherYearFormatter = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, "yMd"),
        locale,
    )
    return { date ->
        date.format(
            if (date.year == currentYear) {
                currentYearFormatter
            } else {
                otherYearFormatter
            }
        )
    }
}

// xProvider is invoked per draw so the line's anchor can come from the
// model's ExtraStore (current-time decoration) or live Compose state
// (touch-marker decoration). Pulling currentTimeXHours from extras instead
// of taking it at construction means the anchor moves with the data Vico
// is currently rendering — there's no chart-window-toggle frame where the
// new anchor is mapped through the old ranges, so no separate guard is
// needed. xProvider returning null skips the draw.
private class VerticalLineDecoration(
    private val xProvider: (CartesianDrawingContext) -> Double?,
    private val lineColor: Color,
    private val dashed: Boolean = false,
    private val coordinateMapper: MainE2ChartCoordinateMapper? = null,
    private val lineWidth: Dp = 1.dp,
    private val dashLength: Dp = 4.dp,
    private val dashGap: Dp = 2.dp,
    private val clampToLayerBounds: Boolean = false,
    private val pointY: Double? = null,
    private val pointMaxY: Double = 1.0,
    private val pointFillColor: Color = lineColor,
    private val pointStrokeColor: Color = Color.Transparent,
    private val pointSize: Dp = 8.dp,
    private val pointStrokeThickness: Dp = 1.dp,
) : Decoration {
    override fun drawUnderLayers(context: CartesianDrawingContext) {
        with(context) {
            coordinateMapper?.update(context)
            val x = xProvider(context) ?: return
            val canvasX = canvasXForLine(x) ?: return

            val strokeWidth = lineWidth.pixels
            val paint = Paint().apply {
                this.color = lineColor
                this.strokeWidth = strokeWidth
            }
            val halfStrokeWidth = strokeWidth / 2f
            val lineTop = layerBounds.top + halfStrokeWidth
            val lineBottom = layerBounds.bottom - halfStrokeWidth
            if (!dashed) {
                canvas.drawLine(
                    p1 = Offset(canvasX, lineTop),
                    p2 = Offset(canvasX, lineBottom),
                    paint = paint,
                )
                return
            }

            val dashLengthPx = dashLength.pixels.coerceAtLeast(strokeWidth)
            val dashGapPx = dashGap.pixels.coerceAtLeast(0f)
            var dashTop = lineTop
            while (dashTop < lineBottom) {
                val dashBottom = (dashTop + dashLengthPx).coerceAtMost(lineBottom)
                canvas.drawLine(
                    p1 = Offset(canvasX, dashTop),
                    p2 = Offset(canvasX, dashBottom),
                    paint = paint,
                )
                dashTop = dashBottom + dashGapPx
            }
        }
    }

    override fun drawOverLayers(context: CartesianDrawingContext) {
        val y = pointY ?: return
        with(context) {
            coordinateMapper?.update(context)
            val x = xProvider(context) ?: return
            val canvasX = canvasXForLine(x) ?: return
            if (pointMaxY <= 0.0 || layerBounds.height <= 0f) {
                return
            }

            val normalizedY = (y.coerceIn(0.0, pointMaxY) / pointMaxY).toFloat()
            val canvasY = layerBounds.bottom - normalizedY * layerBounds.height
            if (canvasY < layerBounds.top || canvasY > layerBounds.bottom) {
                return
            }

            val radius = pointSize.pixels / 2f
            if (radius <= 0f) {
                return
            }
            val strokeWidth = pointStrokeThickness.pixels.coerceAtLeast(0f)
            if (strokeWidth > 0f && pointStrokeColor.alpha > 0f) {
                canvas.drawCircle(
                    center = Offset(canvasX, canvasY),
                    radius = radius,
                    paint = Paint().apply { color = pointStrokeColor },
                )
            }
            canvas.drawCircle(
                center = Offset(canvasX, canvasY),
                radius = (radius - strokeWidth).coerceAtLeast(0f),
                paint = Paint().apply { color = pointFillColor },
            )
        }
    }

    private fun CartesianDrawingContext.canvasXForLine(x: Double): Float? {
        if (ranges.xLength <= 0.0 || ranges.xStep == 0.0 || x !in ranges.minX..ranges.maxX) {
            return null
        }

        val drawingStart = (if (isLtr) layerBounds.left else layerBounds.right) +
                layoutDirectionMultiplier * layerDimensions.startPadding -
                scroll
        val canvasX = drawingStart +
                layoutDirectionMultiplier *
                layerDimensions.xSpacing *
                ((x - ranges.minX) / ranges.xStep).toFloat()
        if (clampToLayerBounds) {
            return canvasX.coerceIn(layerBounds.left, layerBounds.right)
        }
        return canvasX.takeIf { value ->
            value >= layerBounds.left && value <= layerBounds.right
        }
    }
}

@Composable
private fun MainE2ChartCardHeader(
    modifier: Modifier = Modifier,
    targetRangeLow: Double,
    targetRangeHigh: Double,
    displayUnit: BloodUnitKey,
    unit: String,
    hideReferenceRanges: Boolean = false,
    chartWindowOption: HomeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
    onChartWindowOptionSelected: (HomeE2ChartWindowOption) -> Unit = { },
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val mainE2ChartTitleText = stringResource(
            when (chartWindowOption) {
                HomeE2ChartWindowOption.SEVEN_DAYS -> R.string.main_e2_chart_title_seven_days
                HomeE2ChartWindowOption.THIRTY_DAYS -> R.string.main_e2_chart_title_thirty_days
            }
        )
        val toggleContentDescription = stringResource(R.string.main_e2_chart_window_toggle_cd)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = mainE2ChartTitleText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.cjkTextOffset(mainE2ChartTitleText)
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    val next = when (chartWindowOption) {
                        HomeE2ChartWindowOption.SEVEN_DAYS -> HomeE2ChartWindowOption.THIRTY_DAYS
                        HomeE2ChartWindowOption.THIRTY_DAYS -> HomeE2ChartWindowOption.SEVEN_DAYS
                    }
                    onChartWindowOptionSelected(next)
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sync_alt),
                    contentDescription = toggleContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (!hideReferenceRanges) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                val targetText = stringResource(
                    R.string.main_e2_chart_target,
                    formatMainE2ConcentrationValue(targetRangeLow, displayUnit),
                    formatMainE2ConcentrationValue(targetRangeHigh, displayUnit),
                    unit
                )
                Text(
                    text = targetText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).cjkTextOffset(targetText),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun MainAntiandrogenCard(
    modifier: Modifier = Modifier,
    cards: List<MainAntiandrogenCardUiState>,
    now: LocalDateTime,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
) {
    if (cards.isEmpty()) return

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).padding(bottom = 6.dp)
        ) {
            MainAntiandrogenCardHeader(
                modifier = Modifier.padding(vertical = 6.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
            ) {
                cards.forEachIndexed { index, card ->
                    MainAntiandrogenMedicationSubCard(
                        card = card,
                        index = index,
                        itemCount = cards.size,
                        now = now,
                        dateFormatter = dateFormatter,
                        timeFormatter = timeFormatter,
                    )
                }
            }
        }
    }

}

@Composable
private fun MainAntiandrogenCardHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_medication),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )

        val antiandrogenTitleText = stringResource(R.string.main_antiandrogen_title)
        Text(
            text = antiandrogenTitleText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.cjkTextOffset(antiandrogenTitleText)
        )
    }
}

@Composable
private fun MainAntiandrogenMedicationSubCard(
    card: MainAntiandrogenCardUiState,
    index: Int,
    itemCount: Int,
    now: LocalDateTime,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    // The last logged dose, if any, is the most accurate; else fall back to the
    // slot definition. A null medicine means PATCH_OFF — no dose line.
    val displayedMedicine = card.lastDose?.medicine ?: card.medication.medicine
    val displayedApplicationType = card.lastDose?.applicationType
        ?: card.medication.applicationType
    val displayedDoseInstruction = card.lastDose?.doseInstruction
        ?: card.medication.doseInstruction
    val groupColorScheme = rememberMedicationGroupColorScheme(colorKey = card.groupColorKey)
    val medicationName = medicationEntryTitle(displayedMedicine, displayedApplicationType)
    val routeLabel = stringResource(displayedApplicationType.labelRes)
    val doseSummary = displayedMedicine?.let { doseInstructionSummary(it, displayedDoseInstruction) }
    val summaryText = listOfNotNull(
        medicationCountIndicatorText(card.medication.count),
        doseSummary,
    ).joinToString(separator = " · ")
    val supportingText = listOfNotNull(
        routeLabel,
        summaryText.takeIf(String::isNotBlank)
    ).joinToString(separator = " · ")
    val takenText = card.lastDoseAt?.let { doseAt ->
        stringResource(
            R.string.main_antiandrogen_last_dose_elapsed,
            mainCompactElapsedDurationLabel(from = doseAt, to = now)
        )
    } ?: stringResource(R.string.main_antiandrogen_no_last_dose)
    val hasPreviousRecord = card.lastDoseAt != null
    val dueText = card.nextDoseAt?.let { nextDoseAt ->
        if (card.isNextDosePastDue) {
            stringResource(
                R.string.main_antiandrogen_next_dose_past_due,
                mainPastDueHoursLabel(
                    from = nextDoseAt,
                    to = now,
                )
            )
        } else {
            stringResource(
                R.string.main_antiandrogen_next_dose,
                mainAntiandrogenDueLabel(
                    target = nextDoseAt,
                    now = now,
                    dateFormatter = dateFormatter,
                    timeFormatter = timeFormatter
                )
            )
        }
    } ?: stringResource(R.string.main_antiandrogen_no_next_dose)

    EditorSegmentedListItem(
        index = index,
        count = itemCount,
        onClick = null,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        cornerShape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MainRouteIconSurface(
                    applicationType = displayedApplicationType,
                    groupColorScheme = groupColorScheme,
                )

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = medicationName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.cjkTextOffset(medicationName),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.cjkTextOffset(supportingText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainInfoPill(
                    iconDrawableRes = if (hasPreviousRecord) {
                        R.drawable.ic_check_circle
                    } else {
                        R.drawable.ic_info
                    },
                    text = takenText,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                MainInfoPill(
                    iconDrawableRes = R.drawable.ic_schedule,
                    text = dueText,
                )
            }
        }
    }
}

@Composable
private fun MainInfoPill(
    modifier: Modifier = Modifier,
    iconDrawableRes: Int? = null,
    text: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (iconDrawableRes != null) {
                Icon(
                    painter = painterResource(iconDrawableRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(13.dp)
                )
            }

            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 2.dp).cjkTextOffset(text)
            )
        }
    }
}

@Composable
internal fun MainTodaySection(
    section: MainTodaySectionUiState,
    now: LocalDateTime,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    highlightRequest: DoseRowHighlightRequest? = null,
    highlightEffectsEnabled: Boolean = true,
    highlightFlashReady: Boolean = true,
    highlightScrollTargetKey: String? = null,
    onQuickLogDoseClick: (MainQuickLogDoseRequest) -> Unit,
    onEntryClick: (MainEditEntryRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedRows = remember(section.rows) { mainTodayDoseRowsByTimeRange(section.rows) }
    val countLabel = mainTodayCountLabel(
        doneCount = section.doneCount,
        totalCount = section.totalCount,
        manualCount = section.manualCount,
        doneLabel = stringResource(R.string.main_today_summary_done_label),
        manualLabel = stringResource(R.string.main_today_summary_manual_label)
    )
    val sectionSummary = listOfNotNull(
        dateFormatter(section.date),
        countLabel
    ).joinToString(separator = " · ")

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        MainSectionHeader(
            title = stringResource(R.string.main_today_title),
            summary = sectionSummary,
            emphasize = true
        )

        if (section.rows.isEmpty()) {
            SupportMessageListItem(
                text = stringResource(R.string.main_today_empty_state),
                painter = painterResource(R.drawable.ic_info),
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))
            ) {
                groupedRows.forEachIndexed { groupIndex, (timeRange, rows) ->
                    val scheduledRows = rows.filterNot { row -> row.isManualRecord }
                    MainTodayTimeRangeHeader(
                        timeRange = timeRange,
                        isCurrent = timeRange == mainTodayTimeRange(now.toLocalTime()),
                        doneCount = scheduledRows.count { row -> row.status == MainTodayDoseStatus.DONE },
                        totalCount = scheduledRows.size,
                        manualCount = rows.count { row -> row.isManualRecord },
                        timeFormatter = timeFormatter,
                        modifier = Modifier.padding(
                            top = if (groupIndex == 0) 0.dp else 2.dp
                        )
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
                    ) {
                        rows.forEachIndexed { index, row ->
                            val rowKey = mainTodayDoseRowCompositionKey(row)
                            val matchesHighlight =
                                highlightRequest?.matches(row) == true && highlightEffectsEnabled
                            key(rowKey) {
                                MainTodayDoseRow(
                                    row = row,
                                    index = index,
                                    itemCount = rows.size,
                                    now = now,
                                    timeFormatter = timeFormatter,
                                    isHighlighted = matchesHighlight,
                                    isHighlightFlashReady = matchesHighlight && highlightFlashReady,
                                    isHighlightScrollTarget = rowKey == highlightScrollTargetKey,
                                    onQuickLogDoseClick = onQuickLogDoseClick,
                                    onEntryClick = onEntryClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MainLastNightSection(
    section: MainLastNightSectionUiState,
    now: LocalDateTime,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    highlightRequest: DoseRowHighlightRequest? = null,
    highlightEffectsEnabled: Boolean = true,
    highlightFlashReady: Boolean = true,
    highlightScrollTargetKey: String? = null,
    onQuickLogDoseClick: (MainQuickLogDoseRequest) -> Unit,
    onEntryClick: (MainEditEntryRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    if (section.rows.isEmpty()) return

    val countLabel = mainTodayCountLabel(
        doneCount = section.doneCount,
        totalCount = section.totalCount,
        manualCount = section.manualCount,
        doneLabel = stringResource(R.string.main_today_summary_done_label),
        manualLabel = stringResource(R.string.main_today_summary_manual_label)
    )
    val sectionSummary = listOfNotNull(
        section.date?.let(dateFormatter),
        countLabel
    ).joinToString(separator = " · ")

    Column(modifier = modifier.fillMaxWidth()) {
        MainSectionHeader(
            title = stringResource(R.string.main_last_night_title),
            summary = sectionSummary
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
        ) {
            section.rows.forEachIndexed { index, row ->
                val rowKey = mainTodayDoseRowCompositionKey(row)
                val matchesHighlight = highlightRequest?.matches(row) == true && highlightEffectsEnabled
                key(rowKey) {
                    MainTodayDoseRow(
                        row = row,
                        index = index,
                        itemCount = section.rows.size,
                        now = now,
                        timeFormatter = timeFormatter,
                        isHighlighted = matchesHighlight,
                        isHighlightFlashReady = matchesHighlight && highlightFlashReady,
                        isHighlightScrollTarget = rowKey == highlightScrollTargetKey,
                        onQuickLogDoseClick = onQuickLogDoseClick,
                        onEntryClick = onEntryClick
                    )
                }
            }
        }
    }
}

@Composable
internal fun MainUpcomingSection(
    section: MainUpcomingSectionUiState,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    highlightRequest: DoseRowHighlightRequest? = null,
    highlightEffectsEnabled: Boolean = true,
    highlightFlashReady: Boolean = true,
    highlightScrollTargetKey: String? = null,
    modifier: Modifier = Modifier
) {
    val title = when (section.title) {
        MainUpcomingSectionTitle.TOMORROW -> stringResource(R.string.main_tomorrow_title)
        MainUpcomingSectionTitle.UPCOMING -> stringResource(R.string.main_upcoming_title)
    }

    val subtitle = section.anchorDate?.let(dateFormatter).orEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        MainSectionHeader(
            title = title,
            summary = subtitle
        )

        if (section.rows.isEmpty()) {
            SupportMessageListItem(
                text = stringResource(R.string.main_upcoming_empty_state),
                painter = painterResource(R.drawable.ic_info),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
            ) {
                section.rows.forEachIndexed { index, row ->
                    val rowKey = mainUpcomingDoseRowCompositionKey(row)
                    val matchesHighlight = highlightRequest?.matches(row) == true && highlightEffectsEnabled
                    key(rowKey) {
                        MainUpcomingDoseRow(
                            row = row,
                            index = index,
                            itemCount = section.rows.size,
                            timeFormatter = timeFormatter,
                            isHighlighted = matchesHighlight,
                            isHighlightFlashReady = matchesHighlight && highlightFlashReady,
                            isHighlightScrollTarget = rowKey == highlightScrollTargetKey,
                        )
                    }
                }
            }
        }
    }
}

internal fun mainTodayDoseRowCompositionKey(row: MainTodayDoseRowUiState): String {
    val entryIdentity = (row.fulfillingEntryUuids + row.outsideScheduleWindowEntryUuids)
        .sorted()
        .joinToString(separator = ",")
    return listOf(
        if (row.isManualRecord) "manual" else "scheduled",
        row.groupUuid,
        row.scheduleTimeUuid,
        row.scheduledAt,
        row.medication.uuid,
        entryIdentity,
    ).joinToString(separator = "|")
}

internal fun mainUpcomingDoseRowCompositionKey(row: MainUpcomingDoseRowUiState): String =
    listOf(
        "upcoming",
        row.groupUuid,
        row.scheduleTimeUuid,
        row.scheduledAt,
        row.medication.uuid,
    ).joinToString(separator = "|")

@Composable
private fun MainTodayDoseRow(
    row: MainTodayDoseRowUiState,
    index: Int,
    itemCount: Int,
    now: LocalDateTime,
    timeFormatter: DateTimeFormatter,
    onQuickLogDoseClick: (MainQuickLogDoseRequest) -> Unit,
    onEntryClick: (MainEditEntryRequest) -> Unit,
    isHighlighted: Boolean = false,
    isHighlightFlashReady: Boolean = isHighlighted,
    isHighlightScrollTarget: Boolean = isHighlighted,
    modifier: Modifier = Modifier
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var flashActive by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (flashActive) 1f else 0f,
        animationSpec = if (flashActive) tween(150) else tween(600),
        label = "dose-row-highlight",
    )
    LaunchedEffect(isHighlightScrollTarget) {
        if (isHighlightScrollTarget) {
            bringIntoViewRequester.bringIntoView()
        }
    }
    LaunchedEffect(isHighlightFlashReady) {
        if (!isHighlightFlashReady) {
            flashActive = false
            return@LaunchedEffect
        }

        runDoseRowHighlightFlashEffect(
            setFlashActive = { active -> flashActive = active },
            holdMillis = 300,
        )
    }
    val medication = row.medication
    val groupColorScheme = rememberMedicationGroupColorScheme(colorKey = row.groupColorKey)
    val headline = medicationEntryTitle(medication.medicine, medication.applicationType)
    val routeLabel = stringResource(medication.applicationType.labelRes)
    val doseText = medication.medicine?.let { doseInstructionSummary(it, medication.doseInstruction) }
    val supportingText = listOfNotNull(
        routeLabel,
        medicationCountIndicatorText(row.medication.count),
        doseText,
    ).joinToString(separator = " · ")
    val entryEditorIds = mainTodayEntryEditorIds(row)
    val hasOnlyOutsideScheduleWindowLog = row.loggedAt == null &&
            row.outsideScheduleWindowLoggedAt != null
    val routeIconOutlined = hasOnlyOutsideScheduleWindowLog ||
            (row.loggedAt == null && row.status == MainTodayDoseStatus.OVERDUE)
    val quickLogGroupUuid = row.groupUuid
    val onQuickLogClick = {
        if (quickLogGroupUuid != null) {
            onQuickLogDoseClick(
                MainQuickLogDoseRequest(
                    groupUuid = quickLogGroupUuid,
                    scheduleTimeUuid = row.scheduleTimeUuid,
                    scheduledAt = row.scheduledAt,
                    medicine = row.medication.medicine,
                    applicationType = row.medication.applicationType,
                    doseInstruction = row.medication.doseInstruction,
                    medicationCount = remainingQuickLogCount(
                        totalCount = row.medication.count,
                        fulfilledCount = row.loggedCount
                    ),
                    sourceGroupName = row.groupName,
                    sourceGroupColorKey = row.groupColorKey,
                    sourceGroupPreviousScheduledFor = row.sourceGroupPreviousScheduledFor,
                    sourceGroupNextScheduledFor = row.sourceGroupNextScheduledFor,
                )
            )
        }
    }
    val onStatusClick = {
        if (entryEditorIds.isNotEmpty()) {
            onEntryClick(row.toMainEditEntryRequest(entryEditorIds))
        } else {
            onQuickLogClick()
        }
    }

    val rowShape = segmentedListItemShape(index = index, count = itemCount)
    Box {
        EditorSegmentedListItem(
            index = index,
            count = itemCount,
            onClick = onStatusClick,
            modifier = modifier.fillMaxWidth().bringIntoViewRequester(bringIntoViewRequester),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MainRouteIconSurface(
                    applicationType = medication.applicationType,
                    groupColorScheme = groupColorScheme,
                    outlinedIcon = routeIconOutlined
                )

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).alignByBaseline().cjkTextOffset(headline),
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.weight(1f).alignByBaseline().cjkTextOffset(supportingText),
                        )
                    }
                }

                MainTodayTrailingContent(
                    row = row,
                    now = now,
                    timeFormatter = timeFormatter,
                    onStatusClick = onStatusClick
                )
            }
        }
        if (flashAlpha > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(rowShape)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                            alpha = 0.55f * flashAlpha,
                        )
                    )
            )
        }
    }
}

@Composable
private fun MainUpcomingDoseRow(
    row: MainUpcomingDoseRowUiState,
    index: Int,
    itemCount: Int,
    timeFormatter: DateTimeFormatter,
    isHighlighted: Boolean = false,
    isHighlightFlashReady: Boolean = isHighlighted,
    isHighlightScrollTarget: Boolean = isHighlighted,
    modifier: Modifier = Modifier
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var flashActive by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (flashActive) 1f else 0f,
        animationSpec = if (flashActive) tween(150) else tween(1000),
        label = "dose-row-highlight",
    )
    LaunchedEffect(isHighlightScrollTarget) {
        if (isHighlightScrollTarget) {
            bringIntoViewRequester.bringIntoView()
        }
    }
    LaunchedEffect(isHighlightFlashReady) {
        if (!isHighlightFlashReady) {
            flashActive = false
            return@LaunchedEffect
        }

        runDoseRowHighlightFlashEffect(
            setFlashActive = { active -> flashActive = active },
            holdMillis = 600,
        )
    }
    val medication = row.medication
    val groupColorScheme = rememberMedicationGroupColorScheme(colorKey = row.groupColorKey)
    val headline = medicationEntryTitle(medication.medicine, medication.applicationType)
    val routeLabel = stringResource(medication.applicationType.labelRes)
    val doseText = medication.medicine?.let { doseInstructionSummary(it, medication.doseInstruction) }
    val supportingText = listOfNotNull(
        routeLabel,
        medicationCountIndicatorText(row.medication.count),
        doseText,
    ).joinToString(separator = " · ")
    val timeLabel = row.scheduledAt.toLocalTime().format(timeFormatter)
    val rowShape = segmentedListItemShape(index = index, count = itemCount)

    Box {
        EditorSegmentedListItem(
            index = index,
            count = itemCount,
            onClick = null,
            modifier = modifier.fillMaxWidth().bringIntoViewRequester(bringIntoViewRequester),
            trailingContent = {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MainRouteIconSurface(
                    applicationType = medication.applicationType,
                    groupColorScheme = groupColorScheme,
                )

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.cjkTextOffset(headline),
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (supportingText.isNotBlank()) {
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.cjkTextOffset(supportingText),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (flashAlpha > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(rowShape)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                            alpha = 0.55f * flashAlpha,
                        )
                    )
            )
        }
    }
}

@Composable
private fun MainTodayTrailingContent(
    row: MainTodayDoseRowUiState,
    now: LocalDateTime,
    timeFormatter: DateTimeFormatter,
    onStatusClick: () -> Unit,
) {
    val loggedAt = row.loggedAt ?: row.outsideScheduleWindowLoggedAt
    val isLogged = loggedAt != null
    val isMutedLogged = row.loggedAt == null && row.outsideScheduleWindowLoggedAt != null
    val isDueSoon = !isLogged && mainTodayIsInGracePeriod(
        scheduledFor = row.scheduledAt,
        now = now
    )
    val textLabel = if (row.isManualRecord) {
        MainTodayTrailingText(
            text = stringResource(R.string.plan_entry_label_manual),
            isDelta = true
        )
    } else {
        when (loggedAt) {
            null -> when {
                mainTodayIsBeforeOrInGracePeriod(
                    scheduledFor = row.scheduledAt,
                    now = now
                ) -> MainTodayTrailingText(
                    text = row.scheduledAt.toLocalTime().format(timeFormatter),
                    isDelta = false
                )

                else -> mainTodayScheduleOffsetText(
                    scheduledFor = row.scheduledAt,
                    comparedAt = now
                )?.let { text ->
                    MainTodayTrailingText(
                        text = text,
                        isDelta = true
                    )
                }
            }

            else -> {
                mainTodayScheduleOffsetText(
                    scheduledFor = row.scheduledAt,
                    comparedAt = loggedAt
                )?.let { text ->
                    MainTodayTrailingText(
                        text = text,
                        isDelta = true
                    )
                }
            }
        }
    }

    val hasTextLabel = textLabel != null && textLabel.text.isNotBlank()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (row.isFromArchivedGroup || hasTextLabel) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (row.isFromArchivedGroup) {
                    Icon(
                        painter = painterResource(R.drawable.ic_archive),
                        contentDescription = stringResource(R.string.archived_group_record_indicator),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (textLabel != null && textLabel.text.isNotBlank()) {
                    Text(
                        text = textLabel.text,
                        style = if (textLabel.isDelta) {
                            MaterialTheme.typography.labelLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        color = if (textLabel.isDelta) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (textLabel.isDelta) {
                            FontWeight.Normal
                        } else {
                            FontWeight.Medium
                        },
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.cjkTextOffset(textLabel.text)
                    )
                }
            }
        }
        MainTodayTrailingStatusButton(
            isLogged = isLogged,
            isMutedLogged = isMutedLogged,
            isDueSoon = isDueSoon,
            onClick = onStatusClick
        )
    }
}

private data class MainTodayTrailingText(
    val text: String,
    val isDelta: Boolean,
)

@Composable
private fun MainTodayTrailingStatusButton(
    isLogged: Boolean,
    isMutedLogged: Boolean,
    isDueSoon: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val actionDescription = stringResource(
        if (isLogged) R.string.edit_entry else R.string.main_today_quick_log
    )
    val containerColor = when {
        isMutedLogged -> colorScheme.surfaceContainerHighest
        isLogged -> colorScheme.secondaryContainer
        isDueSoon -> colorScheme.tertiaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        isLogged -> colorScheme.onSecondaryContainer
        isDueSoon -> colorScheme.onTertiaryContainer
        else -> colorScheme.onSurfaceVariant
    }
    val border = if (!isLogged && !isDueSoon) {
        BorderStroke(1.dp, colorScheme.outlineVariant)
    } else {
        null
    }

    Surface(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = actionDescription
            },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = border
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLogged -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                isDueSoon -> Icon(
                    painter = painterResource(R.drawable.ic_schedule),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun mainTodayScheduleOffsetText(
    scheduledFor: LocalDateTime,
    comparedAt: LocalDateTime,
): String? {
    val deltaMinutes = ChronoUnit.MINUTES.between(scheduledFor, comparedAt)
    val absoluteMinutes = kotlin.math.abs(deltaMinutes)
    if (absoluteMinutes < MainScheduleGraceMinutes) {
        return null
    }
    val isEarly = deltaMinutes < 0
    val absoluteHours = (absoluteMinutes / 60L).coerceAtLeast(1L)
    val quantity = absoluteHours.toInt()
    return pluralStringResource(
        if (isEarly) {
            R.plurals.main_today_schedule_offset_hours_earlier
        } else {
            R.plurals.main_today_schedule_offset_hours_later
        },
        quantity,
        quantity
    )
}

private fun mainTodayIsBeforeOrInGracePeriod(
    scheduledFor: LocalDateTime,
    now: LocalDateTime,
): Boolean {
    return scheduledFor.isAfter(now.minus(MainScheduleGracePeriod))
}

private fun mainTodayIsInGracePeriod(
    scheduledFor: LocalDateTime,
    now: LocalDateTime,
): Boolean {
    return Duration.between(scheduledFor, now).abs() < MainScheduleGracePeriod
}

private fun mainTodayTimeRangeTimeLabel(
    timeRange: MainTodayTimeRange,
    timeFormatter: DateTimeFormatter,
): String {
    return listOf(
        mainTodayTimeRangeBoundaryLabel(timeRange.startHour, timeFormatter),
        mainTodayTimeRangeBoundaryLabel(timeRange.endHour, timeFormatter)
    ).joinToString(separator = "–")
}

private fun mainTodayTimeRangeBoundaryLabel(
    hour: Int,
    timeFormatter: DateTimeFormatter,
): String {
    return LocalTime.of(hour % 24, 0).format(timeFormatter)
}

@Composable
private fun MainRouteIconSurface(
    applicationType: MedicationApplicationType,
    groupColorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    surfaceSize: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    outlinedIcon: Boolean = false,
) {
    val applicationTypeLabel = stringResource(applicationType.labelRes)

    Box(
        modifier = modifier.size(surfaceSize),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.small,
            color = groupColorScheme.primaryContainer,
            contentColor = groupColorScheme.onPrimaryContainer
        ) {
            Box(
                modifier = Modifier.size(surfaceSize),
                contentAlignment = Alignment.Center
            ) {
                MedicationApplicationIcon(
                    applicationType = applicationType,
                    contentDescription = applicationTypeLabel,
                    modifier = Modifier.size(iconSize),
                    outlined = outlinedIcon,
                )
            }
        }
    }
}

@Composable
private fun MainSectionHeader(
    modifier: Modifier = Modifier,
    title: String,
    summary: String? = null,
    emphasize: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = if (emphasize) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = if (emphasize) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.alignByBaseline().cjkTextOffset(title),
            maxLines = 1,
        )
        if (!summary.isNullOrEmpty()) {
            Text(
                text = summary.uppercase(),
                style = if (emphasize) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.labelMedium
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline().cjkTextOffset(summary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun MainTodayTimeRangeHeader(
    timeRange: MainTodayTimeRange,
    isCurrent: Boolean,
    doneCount: Int,
    totalCount: Int,
    manualCount: Int,
    timeFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val timeRangeLabel = stringResource(timeRange.labelRes)
    val timeRangeTimeLabel = mainTodayTimeRangeTimeLabel(
        timeRange = timeRange,
        timeFormatter = timeFormatter
    )
    val iconDrawableRes = R.drawable.ic_schedule
    val countLabel = mainTodayCompactCountLabel(
        doneCount = doneCount,
        totalCount = totalCount,
        manualCount = manualCount
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(iconDrawableRes),
                    contentDescription = null,
                    tint = if (isCurrent) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = timeRangeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.alignByBaseline().cjkTextOffset(timeRangeLabel)
                )
                Text(
                    text = timeRangeTimeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.alignByBaseline().cjkTextOffset(appLocale)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
        if (countLabel != null) {
            Text(
                text = countLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun mainTodayCountLabel(
    doneCount: Int,
    totalCount: Int,
    manualCount: Int,
    doneLabel: String,
    manualLabel: String,
): String? {
    val planLabel = if (totalCount > 0) {
        "$doneCount/$totalCount $doneLabel"
    } else {
        null
    }
    val manualPart = if (manualCount > 0) {
        "$manualCount $manualLabel"
    } else {
        null
    }

    return listOfNotNull(planLabel, manualPart)
        .joinToString(separator = " · ")
        .takeIf { it.isNotEmpty() }
}

internal fun mainTodayCompactCountLabel(
    doneCount: Int,
    totalCount: Int,
    manualCount: Int,
): String? {
    val planLabel = if (totalCount > 0) {
        "$doneCount/$totalCount"
    } else {
        null
    }
    val manualPart = if (manualCount > 0) {
        "($manualCount)"
    } else {
        null
    }

    return listOfNotNull(planLabel, manualPart)
        .joinToString(separator = " ")
        .takeIf { it.isNotEmpty() }
}

private fun mainTodayTimeRange(time: LocalTime): MainTodayTimeRange {
    return when (time.hour) {
        in 0 until 6 -> MainTodayTimeRange.NIGHT
        in 6 until 12 -> MainTodayTimeRange.MORNING
        in 12 until 18 -> MainTodayTimeRange.AFTERNOON
        else -> MainTodayTimeRange.EVENING
    }
}

private fun mainTodayEntryEditorIds(row: MainTodayDoseRowUiState): Set<UUID> {
    return (row.fulfillingEntryUuids + row.outsideScheduleWindowEntryUuids).toSet()
}

private fun MainTodayDoseRowUiState.toMainEditEntryRequest(
    entryEditorIds: Set<UUID>,
): MainEditEntryRequest {
    val rowGroupUuid = groupUuid
    val snapshotEntries = editSnapshotEntries.filter { entry -> entry.uuid in entryEditorIds }
    return MainEditEntryRequest(
        entryUuids = entryEditorIds,
        snapshotEntries = snapshotEntries,
        sourceGroupName = groupName.takeIf { rowGroupUuid != null && it.isNotBlank() },
        sourceGroupColorKey = groupColorKey.takeIf { rowGroupUuid != null },
        sourceGroupPreviousScheduledFor = sourceGroupPreviousScheduledFor
            .takeIf { rowGroupUuid != null },
        sourceGroupNextScheduledFor = sourceGroupNextScheduledFor
            .takeIf { rowGroupUuid != null },
    )
}

internal fun mainCompactElapsedTotalMinutes(
    from: LocalDateTime,
    to: LocalDateTime
): Long {
    return ChronoUnit.MINUTES.between(
        from.truncatedTo(ChronoUnit.MINUTES),
        to.truncatedTo(ChronoUnit.MINUTES)
    ).coerceAtLeast(0)
}

@Composable
private fun mainCompactElapsedDurationLabel(
    from: LocalDateTime,
    to: LocalDateTime
): String {
    val totalMinutes = mainCompactElapsedTotalMinutes(from = from, to = to)
    val days = totalMinutes / (24 * 60)
    val hours = totalMinutes / 60

    return when {
        days > 0 -> stringResource(R.string.main_duration_compact_days, days)
        hours > 0 -> stringResource(R.string.main_duration_compact_hours, hours)
        else -> stringResource(R.string.main_duration_compact_minutes, totalMinutes)
    }
}

@Composable
private fun mainPastDueHoursLabel(
    from: LocalDateTime,
    to: LocalDateTime
): String {
    val totalHours = ChronoUnit.HOURS.between(from, to).coerceAtLeast(1)
    return stringResource(R.string.main_duration_compact_hours, totalHours)
}

@Composable
private fun mainAntiandrogenDueLabel(
    target: LocalDateTime,
    now: LocalDateTime,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter
): String {
    return when (target.toLocalDate()) {
        now.toLocalDate() -> target.toLocalTime().format(timeFormatter)
        now.toLocalDate().plusDays(1) -> stringResource(R.string.main_relative_tomorrow)
        else -> dateFormatter(target.toLocalDate())
    }
}

@Composable
private fun mainE2LastDoseSummary(
    section: MainE2HeroUiState,
    now: LocalDateTime
): String {
    val lastDoseAt = section.lastDoseAt ?: return stringResource(R.string.main_e2_no_last_dose)
    val elapsedLabel = mainCompactElapsedDurationLabel(
        from = lastDoseAt,
        to = now
    )
    return stringResource(
        R.string.main_e2_last_dose_with_time,
        elapsedLabel
    )
}

private fun mainTrendDeltaLabel(
    changeSinceYesterday: Double,
    unit: String,
    displayUnit: BloodUnitKey,
): String {
    return listOf(formatMainE2TrendDeltaValue(changeSinceYesterday, displayUnit), unit)
        .filter(String::isNotBlank)
        .joinToString(separator = " ")
}

private fun remainingQuickLogCount(
    totalCount: Int,
    fulfilledCount: Int
): Int {
    return totalCount - fulfilledCount
}

@Preview(name = "Main E2 Hero Card", showBackground = true, widthDp = 420)
@Composable
private fun MainE2HeroCardPreview() {
    val uiState = buildMainContentPreviewUiState()

    MainContentComponentPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MainE2HeroCard(
                section = uiState.e2Hero,
                now = uiState.now,
                displayUnit = uiState.homeE2DisplayUnit,
            )
            MainE2HeroCard(
                section = uiState.e2Hero,
                now = uiState.now,
                displayUnit = uiState.homeE2DisplayUnit,
                trendReady = false,
            )
        }
    }
}

@Preview(name = "Main E2 Chart Card", showBackground = true, widthDp = 420)
@Composable
private fun MainE2ChartCardPreview() {
    val uiState = buildMainContentPreviewUiState()

    MainContentComponentPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MainE2ChartCard(
                section = uiState.e2Chart,
                now = uiState.now,
                appLocale = Locale.US,
                unit = uiState.e2Hero.unit,
                displayUnit = uiState.homeE2DisplayUnit,
                targetRangeLow = uiState.e2Hero.targetMin,
                targetRangeHigh = uiState.e2Hero.targetMax,
            )
            MainE2ChartCard(
                section = uiState.e2Chart,
                now = uiState.now,
                appLocale = Locale.US,
                unit = uiState.e2Hero.unit,
                displayUnit = uiState.homeE2DisplayUnit,
                targetRangeLow = uiState.e2Hero.targetMin,
                targetRangeHigh = uiState.e2Hero.targetMax,
                trendReady = false,
            )
        }
    }
}

@Preview(name = "Main Antiandrogen Card", showBackground = true, widthDp = 420)
@Composable
private fun MainAntiandrogenCardPreview() {
    val uiState = buildMainContentPreviewUiState()
    val dateFormatter = dateLabelFormatter(Locale.US, uiState.now.toLocalDate())
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainAntiandrogenCard(
            cards = uiState.antiandrogenCards,
            now = uiState.now,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter
        )
    }
}

@Preview(name = "Main Today Section", showBackground = true, widthDp = 420, heightDp = 430)
@Composable
private fun MainTodaySectionPreview() {
    val uiState = buildMainContentPreviewUiState()
    val dateFormatter = medicationGroupScheduleDateFormatter(Locale.US, uiState.now.toLocalDate())
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainTodaySection(
            section = uiState.todaySection,
            now = uiState.now,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            onQuickLogDoseClick = { },
            onEntryClick = { }
        )
    }
}

@Preview(name = "Main Last Night Section", showBackground = true, widthDp = 420)
@Composable
private fun MainLastNightSectionPreview() {
    val uiState = buildMainContentPreviewUiState()
    val dateFormatter = medicationGroupScheduleDateFormatter(Locale.US, uiState.now.toLocalDate())
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainLastNightSection(
            section = uiState.lastNightSection,
            now = uiState.now,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            onQuickLogDoseClick = { },
            onEntryClick = { }
        )
    }
}

@Preview(name = "Main Upcoming Section", showBackground = true, widthDp = 420)
@Composable
private fun MainUpcomingSectionPreview() {
    val uiState = buildMainContentPreviewUiState()
    val dateFormatter = dateLabelFormatter(Locale.US, uiState.now.toLocalDate())
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainUpcomingSection(
            section = uiState.upcomingSection,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter
        )
    }
}

@Preview(name = "Main Today Dose Row", showBackground = true, widthDp = 420)
@Composable
private fun MainTodayDoseRowPreview() {
    val uiState = buildMainContentPreviewUiState()
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainTodayDoseRow(
            row = uiState.todaySection.rows.first { it.status == MainTodayDoseStatus.DUE_SOON },
            index = 0,
            itemCount = 2,
            now = uiState.now,
            timeFormatter = timeFormatter,
            onQuickLogDoseClick = { },
            onEntryClick = { }
        )
    }
}

@Preview(name = "Main Upcoming Dose Row", showBackground = true, widthDp = 420)
@Composable
private fun MainUpcomingDoseRowPreview() {
    val uiState = buildMainContentPreviewUiState()
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainUpcomingDoseRow(
            row = uiState.upcomingSection.rows.first(),
            index = 0,
            itemCount = 3,
            timeFormatter = timeFormatter
        )
    }
}

@Preview(name = "Main Today Trailing Content", showBackground = true, widthDp = 260)
@Composable
private fun MainTodayTrailingContentPreview() {
    val uiState = buildMainContentPreviewUiState()
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            uiState.todaySection.rows.forEach { row ->
                MainTodayTrailingContent(
                    row = row,
                    now = uiState.now,
                    timeFormatter = timeFormatter,
                    onStatusClick = { }
                )
            }
        }
    }
}

@Preview(name = "Main Route Icon Surface", showBackground = true, widthDp = 180)
@Composable
private fun MainRouteIconSurfacePreview() {
    MainContentComponentPreviewContainer {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainRouteIconSurface(
                applicationType = MedicationApplicationType.ORAL,
                groupColorScheme = rememberMedicationGroupColorScheme(colorKey = MedicationGroupColorKey.ROSE),
            )
            MainRouteIconSurface(
                applicationType = MedicationApplicationType.PATCH_ON,
                groupColorScheme = rememberMedicationGroupColorScheme(colorKey = MedicationGroupColorKey.TEAL),
            )
            MainRouteIconSurface(
                applicationType = MedicationApplicationType.INJECTION,
                groupColorScheme = rememberMedicationGroupColorScheme(colorKey = MedicationGroupColorKey.INDIGO),
                iconSize = 18.dp,
                surfaceSize = 30.dp,
                outlinedIcon = true
            )
        }
    }
}

@Preview(name = "Main Section Header", showBackground = true, widthDp = 420)
@Composable
private fun MainSectionHeaderPreview() {
    MainContentComponentPreviewContainer {
        MainSectionHeader(
            title = "Today",
            summary = "May 5 Tue · 1/4 (1) done",
            emphasize = true
        )
    }
}

@Preview(name = "Main Today Time Range Header", showBackground = true, widthDp = 420)
@Composable
private fun MainTodayTimeRangeHeaderPreview() {
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainTodayTimeRangeHeader(
            timeRange = MainTodayTimeRange.MORNING,
            isCurrent = true,
            doneCount = 1,
            totalCount = 2,
            manualCount = 1,
            timeFormatter = timeFormatter
        )
    }
}

@Composable
private fun MainContentComponentPreviewContainer(
    content: @Composable () -> Unit
) {
    HrtTrackerTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

internal fun buildMainContentPreviewUiState(): MainUiState {
    val now = MainPreviewNow
    val estradiolTablet = previewMedication(
        uuid = "e0d4fc52-8a75-4cf5-9ab3-f28fbd9a5ea7",
        key = MedicationKey.ESTRADIOL,
        applicationType = MedicationApplicationType.ORAL,
    )
    val estradiolSublingual = previewMedication(
        uuid = "98f22e3e-ef38-4701-9f73-94a6b3430d9b",
        key = MedicationKey.ESTRADIOL_VALERATE,
        applicationType = MedicationApplicationType.SUBLINGUAL,
    )
    val estradiolPatch = previewMedication(
        uuid = "e483ee93-976d-445a-9b0d-78ee8753d59e",
        key = MedicationKey.ESTRADIOL_PATCH,
        applicationType = MedicationApplicationType.PATCH_ON,
    )
    val spironolactone = previewMedication(
        uuid = "be598126-5d8f-4daa-bdc6-54d45035f3f3",
        key = MedicationKey.SPIRONOLACTONE,
        applicationType = MedicationApplicationType.ORAL,
    )

    return MainUiState(
        now = now,
        e2Hero = MainE2HeroUiState(
            currentValue = 147.0,
            changeSinceYesterday = 8.0,
            targetMin = 100.0,
            targetMax = 200.0,
            lastDose = estradiolTablet.toPreviewLastDose(),
            lastDoseAt = now.minusHours(2).minusMinutes(25)
        ),
        e2Chart = MainE2ChartUiState(
            points = listOf(132f, 148f, 162f, 155f, 176f, 139f, 147f)
        ),
        antiandrogenCards = listOf(
            MainAntiandrogenCardUiState(
                id = "preview-spiro",
                groupUuid = PreviewAntiandrogenGroupUuid,
                groupName = "Antiandrogen",
                groupColorKey = MedicationGroupColorKey.INDIGO,
                medication = spironolactone,
                lastDose = spironolactone.toPreviewLastDose(),
                lastDoseAt = now.minusHours(1).minusMinutes(10),
                nextDoseAt = now.plusHours(11).plusMinutes(30)
            )
        ),
        todaySection = MainTodaySectionUiState(
            date = now.toLocalDate(),
            doneCount = 1,
            totalCount = 4,
            manualCount = 1,
            rows = listOf(
                MainTodayDoseRowUiState(
                    groupUuid = PreviewEstradiolGroupUuid,
                    groupName = "Morning estradiol",
                    groupColorKey = MedicationGroupColorKey.ROSE,
                    scheduleTimeUuid = PreviewMorningScheduleUuid,
                    scheduledAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(8, 0)),
                    medication = estradiolTablet,
                    status = MainTodayDoseStatus.DONE,
                    loggedAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(8, 3)),
                    loggedCount = 1
                ),
                MainTodayDoseRowUiState(
                    groupUuid = PreviewPatchGroupUuid,
                    groupName = "Patch change",
                    groupColorKey = MedicationGroupColorKey.TEAL,
                    scheduleTimeUuid = PreviewPatchScheduleUuid,
                    scheduledAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(9, 0)),
                    medication = estradiolPatch,
                    status = MainTodayDoseStatus.OVERDUE
                ),
                MainTodayDoseRowUiState(
                    groupUuid = null,
                    groupName = "",
                    groupColorKey = null,
                    scheduleTimeUuid = null,
                    scheduledAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(10, 20)),
                    medication = estradiolSublingual,
                    status = MainTodayDoseStatus.DONE,
                    loggedAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(10, 20)),
                    fulfillingEntryUuids = listOf(PreviewManualEntryUuid),
                    loggedCount = 1,
                    isManualRecord = true
                ),
                MainTodayDoseRowUiState(
                    groupUuid = PreviewAntiandrogenGroupUuid,
                    groupName = "Antiandrogen",
                    groupColorKey = MedicationGroupColorKey.INDIGO,
                    scheduleTimeUuid = PreviewAntiandrogenScheduleUuid,
                    scheduledAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(10, 45)),
                    medication = spironolactone,
                    status = MainTodayDoseStatus.DUE_SOON
                ),
                MainTodayDoseRowUiState(
                    groupUuid = PreviewEstradiolGroupUuid,
                    groupName = "Afternoon estradiol",
                    groupColorKey = MedicationGroupColorKey.ROSE,
                    scheduleTimeUuid = null,
                    scheduledAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(14, 0)),
                    medication = estradiolSublingual,
                    status = MainTodayDoseStatus.UPCOMING
                ),
            )
        ),
        lastNightSection = MainLastNightSectionUiState(
            date = now.toLocalDate().minusDays(1),
            doneCount = 1,
            totalCount = 1,
            manualCount = 0,
            rows = listOf(
                MainTodayDoseRowUiState(
                    groupUuid = PreviewEstradiolGroupUuid,
                    groupName = "Evening estradiol",
                    groupColorKey = MedicationGroupColorKey.ROSE,
                    scheduleTimeUuid = PreviewMorningScheduleUuid,
                    scheduledAt = LocalDateTime.of(
                        now.toLocalDate().minusDays(1),
                        LocalTime.of(21, 0)
                    ),
                    medication = estradiolTablet,
                    status = MainTodayDoseStatus.DONE,
                    loggedAt = LocalDateTime.of(
                        now.toLocalDate().minusDays(1),
                        LocalTime.of(21, 4)
                    ),
                    loggedCount = 1
                )
            )
        ),
        upcomingSection = MainUpcomingSectionUiState(
            title = MainUpcomingSectionTitle.TOMORROW,
            anchorDate = now.toLocalDate().plusDays(1),
            rows = listOf(
                MainUpcomingDoseRowUiState(
                    groupUuid = PreviewEstradiolGroupUuid,
                    groupName = "Morning estradiol",
                    groupColorKey = MedicationGroupColorKey.ROSE,
                    scheduleTimeUuid = PreviewMorningScheduleUuid,
                    scheduledAt = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(8, 0)),
                    medication = estradiolTablet
                ),
                MainUpcomingDoseRowUiState(
                    groupUuid = PreviewAntiandrogenGroupUuid,
                    groupName = "Antiandrogen",
                    groupColorKey = MedicationGroupColorKey.INDIGO,
                    scheduleTimeUuid = PreviewAntiandrogenScheduleUuid,
                    scheduledAt = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(22, 0)),
                    medication = spironolactone
                ),
            )
        )
    )
}

private fun previewMedication(
    uuid: String,
    key: MedicationKey,
    applicationType: MedicationApplicationType,
    count: Int = 1
): MedicationGroupMedication {
    val medicine = previewMedicineFor(key)
    val doseInstruction = when (medicine.preparation) {
        is com.mkx.hrttracker.model.medication.MedicinePreparation.Pill -> DoseInstruction.TabletFraction(1, 1)
        is com.mkx.hrttracker.model.medication.MedicinePreparation.Patch -> DoseInstruction.WholeUnit
        else -> DoseInstruction.WholeUnit
    }
    return MedicationGroupMedication(
        uuid = UUID.fromString(uuid),
        medicine = medicine,
        applicationType = applicationType,
        doseInstruction = doseInstruction,
        count = count
    )
}

private fun MedicationGroupMedication.toPreviewLastDose(): LastDoseDisplay {
    return LastDoseDisplay(
        medicine = medicine,
        applicationType = applicationType,
        doseInstruction = doseInstruction,
    )
}

private fun previewMedicineFor(
    key: MedicationKey,
): com.mkx.hrttracker.model.medication.Medicine {
    val selection = com.mkx.hrttracker.model.medication.MedicineSelection.Catalog(key)
    val preparation = when (key) {
        MedicationKey.ESTRADIOL_PATCH -> {
            com.mkx.hrttracker.model.medication.MedicinePreparation.Patch(
                specification = com.mkx.hrttracker.model.medication.MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                    100.0
                )
            )
        }
        else -> {
            com.mkx.hrttracker.model.medication.MedicinePreparation.Pill(
                strengthMgPerTablet = 2.0,
            )
        }
    }
    return com.mkx.hrttracker.model.medication.Medicine(
        uuid = UUID.nameUUIDFromBytes("preview-${key.name}".toByteArray()),
        selection = selection,
        category = key.category,
        preparation = preparation,
        displayName = null,
        identityKey = com.mkx.hrttracker.model.medication.MedicineIdentityKey.catalog(
            key, preparation,
        ),
        createdAt = java.time.Instant.EPOCH,
        updatedAt = java.time.Instant.EPOCH,
        archivedAt = null,
        stock = com.mkx.hrttracker.model.medication.MedicineStock(),
    )
}
