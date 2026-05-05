package com.mkx.hrttracker.ui.main

import android.text.format.DateFormat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.medication.MedicationApplicationIcon
import com.mkx.hrttracker.ui.medication.medicationCountIndicatorText
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationDoseSupportingText
import com.mkx.hrttracker.ui.medication.medicationDoseText
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.LocalDateFormatter
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
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
private const val MainE2ChartInitialAnimationMillis = 500
private const val MainE2ChartAnimationSettleDelayMillis = 50L

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

@Composable
internal fun MainE2HeroCard(
    section: MainE2HeroUiState,
    now: LocalDateTime,
    modifier: Modifier = Modifier
) {
    val trendDeltaLabel = mainTrendDeltaLabel(
        changeSinceYesterday = section.changeSinceYesterday,
        unit = section.unit
    )
    val trendIcon = when {
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
    val unitText = section.unit
    val rangeStatusIconDrawableRes = when {
        section.currentValue > section.targetMax -> R.drawable.ic_expand_circle_up
        section.currentValue < section.targetMin -> R.drawable.ic_expand_circle_down
        else -> R.drawable.ic_adjust
    }
    val rangeStatusLabelRes = when {
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
        EditorSegmentedListItem(
            index = 0,
            count = 1,
            onClick = { },
            modifier = Modifier.fillMaxWidth(),
            containerColor = colorScheme.surfaceContainerLow,
            cornerShape = MaterialTheme.shapes.extraLarge
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MonitorHeart,
                                contentDescription = null,
                                tint = heroSupportingColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = heroSupportingColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.cjkTextOffset(titleText)
                            )
                        }
                    }

                    ConstraintLayout(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val (valueRef, unitRef, rangeStatusRef) = createRefs()

                        Text(
                            text = section.currentValue.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Medium,
                            color = heroContentColor,
                            modifier = Modifier.constrainAs(valueRef) {
                                start.linkTo(parent.start)
                                top.linkTo(parent.top)
                            },
                        )
                        Text(
                            text = unitText,
                            modifier = Modifier.constrainAs(unitRef) {
                                start.linkTo(valueRef.end, margin = 8.dp)
                                baseline.linkTo(valueRef.baseline)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = heroSupportingColor
                        )
                        MainE2RangeStatusPill(
                            iconDrawableRes = rangeStatusIconDrawableRes,
                            label = rangeStatusLabel,
                            modifier = Modifier.constrainAs(rangeStatusRef) {
                                start.linkTo(unitRef.end, margin = 8.dp)
                                top.linkTo(unitRef.top)
                                bottom.linkTo(unitRef.bottom)
                            }
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
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

                                val sinceYesterdayTextString = stringResource(
                                    R.string.main_e2_change_since_yesterday,
                                    trendDeltaLabel
                                )
                                Text(
                                    text = sinceYesterdayTextString,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = heroSupportingColor,
                                    modifier = Modifier.padding(end = 2.dp).cjkTextOffset(sinceYesterdayTextString)
                                )
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
            imageVector = Icons.Rounded.WaterDrop,
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
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val hasConsumedInitialChartAnimation = rememberSaveable { mutableStateOf(false) }
    val chartAnimationsEnabled = remember {
        mutableStateOf(!hasConsumedInitialChartAnimation.value)
    }
    val pointXHours = remember(section.points, section.pointXHours) {
        section.pointXHours
            .takeIf { xHours -> xHours.size == section.points.size }
            ?: section.points.indices.map { index -> index * section.sampleIntervalHours.toDouble() }
    }
    val splitChartSeries = remember(pointXHours, section.points, section.predictionStartXHours) {
        splitMainE2ChartSeries(
            xHours = pointXHours,
            points = section.points,
            predictionStartXHours = section.predictionStartXHours,
        )
    }
    val chartWindowHours = section.windowHours.coerceAtLeast(1)
    val doseMarkerXHours = remember(section.doseMarkers) {
        section.doseMarkers.map { marker -> marker.xHours }
    }
    val doseMarkerConcentrations = remember(section.doseMarkers) {
        section.doseMarkers.map { marker -> marker.concentration }
    }
    val currentTimeXHours = section.predictionStartXHours
        .coerceIn(0.0, chartWindowHours.toDouble())
    val currentTimeConcentration = splitChartSeries.predictedPoints.firstOrNull()
        ?: splitChartSeries.observedPoints.lastOrNull()
        ?: 0f
    val chartWindowStart = remember(now) { mainE2ChartWindowStart(now) }
    val chartTimeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val chartDateFormatter = remember(appLocale, now.year) {
        mainE2ChartMarkerDateFormatter(
            locale = appLocale,
            currentYear = now.year,
        )
    }
    val interactiveMarkerXHours = remember { mutableStateOf<Double?>(null) }
    val isLongPressMarkerActive = interactiveMarkerXHours.value != null
    val displayedMarkerXHours = interactiveMarkerXHours.value ?: currentTimeXHours
    val displayedMarkerConcentration = remember(
        interactiveMarkerXHours.value,
        pointXHours,
        section.points,
        displayedMarkerXHours,
        currentTimeXHours,
        currentTimeConcentration,
    ) {
        if (interactiveMarkerXHours.value == null) {
            currentTimeConcentration
        } else {
            mainE2ChartConcentrationAtX(
                xHours = displayedMarkerXHours,
                pointXHours = pointXHours,
                points = section.points,
            )
        }
    }
    val interactiveMarkerLabel = remember(
        interactiveMarkerXHours.value,
        displayedMarkerConcentration,
        chartWindowStart,
        chartDateFormatter,
        chartTimeFormatter,
        unit,
    ) {
        interactiveMarkerXHours.value?.let { xHours ->
            val dateTime = chartWindowStart.plusMinutes((xHours * 60).roundToLong())
            val date = chartDateFormatter(dateTime.toLocalDate())
            val time = dateTime.format(chartTimeFormatter)
            "$date $time\n${displayedMarkerConcentration.roundToInt()} $unit"
        }
    }
    val noonTickHours = remember(chartWindowHours, now) {
        mainE2ChartNoonTickHours(
            now = now,
            windowHours = chartWindowHours,
        )
    }
    val bottomAxisItemPlacer = remember(noonTickHours) {
        FixedHorizontalAxisItemPlacer(noonTickHours)
    }
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
    val bottomAxisValueFormatter = remember(chartWindowHours, now, appLocale) {
        CartesianValueFormatter { _, value, _ ->
            val hoursFromWindowStart = value.coerceIn(0.0, chartWindowHours.toDouble())
            chartWindowStart
                .plusMinutes((hoursFromWindowStart * 60).roundToLong())
                .toLocalDate()
                .dayOfWeek
                .getDisplayName(TextStyle.NARROW, appLocale)
        }
    }
    val rangeProvider = remember(chartWindowHours, yAxisSpec) {
        CartesianLayerRangeProvider.fixed(
            minX = 0.0,
            maxX = chartWindowHours.toDouble(),
            minY = 0.0,
            maxY = yAxisSpec.maxY,
        )
    }
    val chartAnimationSpec = if (chartAnimationsEnabled.value) {
        tween<Float>(durationMillis = MainE2ChartInitialAnimationMillis)
    } else {
        null
    }

    LaunchedEffect(Unit) {
        if (!hasConsumedInitialChartAnimation.value) {
            hasConsumedInitialChartAnimation.value = true
            delay(MainE2ChartInitialAnimationMillis + MainE2ChartAnimationSettleDelayMillis)
            chartAnimationsEnabled.value = false
        }
    }

    LaunchedEffect(
        splitChartSeries,
        doseMarkerXHours,
        doseMarkerConcentrations,
        displayedMarkerXHours,
        displayedMarkerConcentration,
    ) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = splitChartSeries.observedXHours,
                    y = splitChartSeries.observedPoints,
                )
                series(
                    x = splitChartSeries.predictedXHours,
                    y = splitChartSeries.predictedPoints,
                )
                series(
                    x = listOf(displayedMarkerXHours),
                    y = listOf(displayedMarkerConcentration),
                )
                if (doseMarkerXHours.isNotEmpty()) {
                    series(
                        x = doseMarkerXHours,
                        y = doseMarkerConcentrations,
                    )
                }
            }
        }
    }

    EditorSegmentedListItem(
        index = 0,
        count = 1,
        onClick = {},
        cornerShape = MaterialTheme.shapes.extraLarge,
        pressedShape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {

            MainE2ChartCardHeader(
                modifier = Modifier.padding(vertical = 4.dp),
                targetRangeLow = 100,
                targetRangeHigh = 200,
                unit = unit
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                val lineColor = MaterialTheme.colorScheme.primary
                val doseMarkerColor = MaterialTheme.colorScheme.primary
                val currentTimeColor = MaterialTheme.colorScheme.tertiary
                val currentTimeLineColor =
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                val markerSurfaceColor = MaterialTheme.colorScheme.surfaceContainer
                val chartCoordinateMapper = remember { MainE2ChartCoordinateMapper() }
                val chartSize = remember { mutableStateOf(IntSize.Zero) }
                val markerLabelSize = remember { mutableStateOf(IntSize.Zero) }
                val currentTimeDecoration = remember(
                    displayedMarkerXHours,
                    currentTimeLineColor,
                    isLongPressMarkerActive,
                    chartCoordinateMapper,
                ) {
                    VerticalLineDecoration(
                        x = displayedMarkerXHours,
                        lineColor = currentTimeLineColor,
                        dashed = isLongPressMarkerActive,
                        coordinateMapper = chartCoordinateMapper,
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

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(184.dp)
                            .padding(8.dp)
                            .onSizeChanged { chartSize.value = it }
                    ) {
                        CartesianChartHost(
                            chart = rememberCartesianChart(
                                rememberLineCartesianLayer(
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
                                                fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
                                                stroke = LineCartesianLayer.LineStroke.Dashed(),
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
                                        ),
                                    rangeProvider = rangeProvider,
                                ),
                                decorations = listOf(currentTimeDecoration),
                                startAxis = VerticalAxis.rememberStart(
                                    valueFormatter = CartesianValueFormatter { _, value, _ ->
                                        value.toInt().toString()
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
                            scrollState = rememberVicoScrollState(scrollEnabled = false),
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(chartCoordinateMapper, chartWindowHours) {
                                    detectMainE2ChartMarkerGestures(
                                        coordinateMapper = chartCoordinateMapper,
                                        chartWindowHours = chartWindowHours,
                                        onMarkerXChanged = { xHours ->
                                            interactiveMarkerXHours.value = xHours
                                        },
                                    )
                                }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 8.dp)
                ) {
                    val markerLabel = interactiveMarkerLabel
                    val markerCanvasX = interactiveMarkerXHours.value
                        ?.let { xHours -> chartCoordinateMapper.canvasXForXHours(xHours) }
                    if (markerLabel != null && markerCanvasX != null) {
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
                                        y = -labelHeight - 6,
                                    )
                                }
                            ,
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
        }
    }
}

private class FixedHorizontalAxisItemPlacer(
    private val labelValues: List<Double>,
) : HorizontalAxis.ItemPlacer {
    override fun getShiftExtremeLines(context: CartesianDrawingContext): Boolean = false

    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        return labelValues.filter { value ->
            value >= visibleXRange.start && value <= visibleXRange.endInclusive
        }
    }

    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
    ): List<Double> {
        return measurementValues(fullXRange)
    }

    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        return measurementValues(fullXRange)
    }

    override fun getLineValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        return getLabelValues(
            context = context,
            visibleXRange = visibleXRange,
            fullXRange = fullXRange,
            maxLabelWidth = maxLabelWidth,
        )
    }

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
        fullXRange: ClosedFloatingPointRange<Double>,
    ): List<Double> {
        return labelValues.ifEmpty { listOf(fullXRange.start) }
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
        down.consume()
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress == null) {
            onMarkerXChanged(null)
            return@awaitEachGesture
        }

        longPress.consume()
        onMarkerXChanged(markerXFor(longPress.position.x))

        while (true) {
            val event = awaitPointerEvent()
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
    private var drawingStart: Float = 0f
    private var layerLeft: Float = 0f
    private var layerRight: Float = 0f
    private var minX: Double = 0.0
    private var maxX: Double = 0.0
    private var xStep: Double = 0.0
    private var xSpacing: Float = 0f
    private var layoutDirectionMultiplier: Int = 1

    fun update(context: CartesianDrawingContext) {
        with(context) {
            drawingStart = (if (isLtr) layerBounds.left else layerBounds.right) +
                layoutDirectionMultiplier * layerDimensions.startPadding -
                scroll
            layerLeft = layerBounds.left
            layerRight = layerBounds.right
            minX = ranges.minX
            maxX = ranges.maxX
            xStep = ranges.xStep
            xSpacing = layerDimensions.xSpacing
            this@MainE2ChartCoordinateMapper.layoutDirectionMultiplier =
                layoutDirectionMultiplier
        }
    }

    fun pointerXToXHours(pointerX: Float): Double? {
        if (!hasUsableMapping()) return null
        val rawX = minX +
            (pointerX - drawingStart) /
            (layoutDirectionMultiplier * xSpacing) *
            xStep
        return rawX.coerceIn(minX, maxX)
    }

    fun canvasXForXHours(xHours: Double): Float? {
        if (!hasUsableMapping()) return null
        val canvasX = drawingStart +
            layoutDirectionMultiplier *
            xSpacing *
            ((xHours.coerceIn(minX, maxX) - minX) / xStep).toFloat()
        return canvasX.coerceIn(layerLeft, layerRight)
    }

    private fun hasUsableMapping(): Boolean {
        return xStep != 0.0 && xSpacing != 0f && minX <= maxX
    }
}

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

private class VerticalLineDecoration(
    private val x: Double,
    private val lineColor: Color,
    private val dashed: Boolean = false,
    private val coordinateMapper: MainE2ChartCoordinateMapper? = null,
    private val lineWidth: Dp = 1.dp,
    private val dashLength: Dp = 4.dp,
    private val dashGap: Dp = 2.dp,
) : Decoration {
    override fun drawUnderLayers(context: CartesianDrawingContext) {
        with(context) {
            coordinateMapper?.update(context)
            if (ranges.xLength <= 0.0 || ranges.xStep == 0.0 || x !in ranges.minX..ranges.maxX) {
                return
            }

            val drawingStart = (if (isLtr) layerBounds.left else layerBounds.right) +
                layoutDirectionMultiplier * layerDimensions.startPadding -
                scroll
            val canvasX = drawingStart +
                layoutDirectionMultiplier *
                layerDimensions.xSpacing *
                ((x - ranges.minX) / ranges.xStep).toFloat()
            if (canvasX < layerBounds.left || canvasX > layerBounds.right) {
                return
            }

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
}

@Composable
private fun MainE2ChartCardHeader(
    modifier: Modifier = Modifier,
    targetRangeLow: Int,
    targetRangeHigh: Int,
    unit: String,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )

            val mainE2ChartTitleText = stringResource(R.string.main_e2_chart_title)
            Text(
                text = mainE2ChartTitleText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.cjkTextOffset(mainE2ChartTitleText)
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                text = stringResource(R.string.main_e2_chart_target, targetRangeLow, targetRangeHigh, unit),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
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

    EditorSegmentedListItem(
        index = 0,
        count = 1,
        onClick = {},
        cornerShape = MaterialTheme.shapes.extraLarge,
        pressedShape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            MainAntiandrogenCardHeader(
                modifier = Modifier.padding(vertical = 4.dp)
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
    val displayedDetails = card.lastDoseDetails ?: card.medication.details
    val groupColorScheme = rememberMedicationGroupColorScheme(card.groupColorKey)
    val medicationName = medicationDisplayName(displayedDetails)
    val routeLabel = stringResource(displayedDetails.applicationType.labelRes)
    val summaryText = medicationDoseSupportingText(
        details = displayedDetails,
        medicationCount = card.medication.count,
    )
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
        onClick = { },
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
                    applicationType = displayedDetails.applicationType,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    modifier = Modifier.weight(1f, fill = false)
                )

                MainInfoPill(
                    iconDrawableRes = R.drawable.ic_schedule,
                    text = dueText,
                    modifier = Modifier.weight(1f, fill = false)
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
    onQuickLogDoseClick: (UUID, UUID?, LocalDateTime, MedicationDetails, Int) -> Unit,
    onEntryClick: (Set<UUID>) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedRows = remember(section.rows) {
        section.rows
            .groupBy { row -> mainTodayTimeRange(row.scheduledAt.toLocalTime()) }
            .entries
            .sortedBy { (timeRange, _) -> timeRange.ordinal }
    }
    val sectionSummary = listOf(
        dateFormatter(section.date),
        stringResource(
            R.string.main_today_progress,
            section.doneCount,
            section.totalCount
        )
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
                    MainTodayTimeRangeHeader(
                        timeRange = timeRange,
                        isCurrent = timeRange == mainTodayTimeRange(now.toLocalTime()),
                        doneCount = rows.count { row -> row.status == MainTodayDoseStatus.DONE },
                        totalCount = rows.size,
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
                            MainTodayDoseRow(
                                row = row,
                                index = index,
                                itemCount = rows.size,
                                now = now,
                                timeFormatter = timeFormatter,
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

@Composable
internal fun MainUpcomingSection(
    section: MainUpcomingSectionUiState,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
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
                    MainUpcomingDoseRow(
                        row = row,
                        index = index,
                        itemCount = section.rows.size,
                        dateFormatter = dateFormatter,
                        timeFormatter = timeFormatter,
                        showDate = section.title == MainUpcomingSectionTitle.UPCOMING
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTodayDoseRow(
    row: MainTodayDoseRowUiState,
    index: Int,
    itemCount: Int,
    now: LocalDateTime,
    timeFormatter: DateTimeFormatter,
    onQuickLogDoseClick: (UUID, UUID?, LocalDateTime, MedicationDetails, Int) -> Unit,
    onEntryClick: (Set<UUID>) -> Unit,
    modifier: Modifier = Modifier
) {
    val details = row.medication.details
    val groupColorScheme = rememberMedicationGroupColorScheme(row.groupColorKey)
    val headline = medicationDisplayName(details)
    val routeLabel = stringResource(details.applicationType.labelRes)
    val doseText = medicationDoseText(details)
    val supportingText = listOfNotNull(
        routeLabel,
        doseText,
    ).joinToString(separator = " · ")
    val entryEditorIds = mainTodayEntryEditorIds(row)
    val hasOnlyOutsideScheduleWindowLog = row.loggedAt == null &&
        row.outsideScheduleWindowLoggedAt != null
    val routeIconOutlined = hasOnlyOutsideScheduleWindowLog ||
        (row.loggedAt == null && row.status == MainTodayDoseStatus.OVERDUE)
    val onQuickLogClick = {
        onQuickLogDoseClick(
            row.groupUuid,
            row.scheduleTimeUuid,
            row.scheduledAt,
            row.medication.details,
            remainingQuickLogCount(
                totalCount = row.medication.count,
                fulfilledCount = row.loggedCount
            )
        )
    }
    val onStatusClick = {
        if (entryEditorIds.isNotEmpty()) {
            onEntryClick(entryEditorIds)
        } else {
            onQuickLogClick()
        }
    }

    EditorSegmentedListItem(
        index = index,
        count = itemCount,
        onClick = onStatusClick,
        modifier = modifier.fillMaxWidth(),
        trailingContent = {
            MainTodayTrailingContent(
                row = row,
                now = now,
                timeFormatter = timeFormatter,
                onStatusClick = onStatusClick
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MainRouteIconSurface(
                applicationType = details.applicationType,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MainUpcomingDoseRow(
    row: MainUpcomingDoseRowUiState,
    index: Int,
    itemCount: Int,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    showDate: Boolean,
    modifier: Modifier = Modifier
) {
    val details = row.medication.details
    val groupColorScheme = rememberMedicationGroupColorScheme(row.groupColorKey)
    val headline = medicationDisplayName(details)
    val routeLabel = stringResource(details.applicationType.labelRes)
    val doseText = medicationDoseText(details)
    val extraSupportingText = if (showDate) {
        dateFormatter(row.scheduledAt.toLocalDate())
    } else {
        null
    }
    val supportingText = listOfNotNull(
        routeLabel,
        doseText,
        medicationCountIndicatorText(row.medication.count).takeIf { row.medication.count > 1 },
        extraSupportingText
    ).joinToString(separator = " · ")
    val timeLabel = row.scheduledAt.toLocalTime().format(timeFormatter)

    EditorSegmentedListItem(
        index = index,
        count = itemCount,
        onClick = { },
        modifier = modifier.fillMaxWidth(),
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
                applicationType = details.applicationType,
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
    val textLabel = when (loggedAt) {
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                maxLines = 1
            )
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
    return !scheduledFor.isBefore(now.minusMinutes(MainScheduleGraceMinutes))
}

private fun mainTodayIsInGracePeriod(
    scheduledFor: LocalDateTime,
    now: LocalDateTime,
): Boolean {
    return kotlin.math.abs(
        ChronoUnit.MINUTES.between(scheduledFor, now)
    ) <= MainScheduleGraceMinutes
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
            color = groupColorScheme.secondaryContainer,
            contentColor = groupColorScheme.onSecondaryContainer
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
    timeFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val timeRangeLabel = stringResource(timeRange.labelRes)
    val timeRangeTimeLabel = mainTodayTimeRangeTimeLabel(
        timeRange = timeRange,
        timeFormatter = timeFormatter
    )
    val countLabel = "$doneCount/$totalCount"

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
                    painter = painterResource(R.drawable.ic_schedule),
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
        Text(
            text = countLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
    changeSinceYesterday: Int,
    unit: String
): String {
    val valueLabel = when {
        changeSinceYesterday > 0 -> "+$changeSinceYesterday"
        changeSinceYesterday < 0 -> changeSinceYesterday.toString()
        else -> "0"
    }
    return listOf(valueLabel, unit)
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
        MainE2HeroCard(
            section = uiState.e2Hero,
            now = uiState.now
        )
    }
}

@Preview(name = "Main E2 Chart Card", showBackground = true, widthDp = 420)
@Composable
private fun MainE2ChartCardPreview() {
    val uiState = buildMainContentPreviewUiState()

    MainContentComponentPreviewContainer {
        MainE2ChartCard(
            section = uiState.e2Chart,
            now = uiState.now,
            appLocale = Locale.US,
            unit = uiState.e2Hero.unit
        )
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
    val dateFormatter = dateLabelFormatter(Locale.US, uiState.now.toLocalDate())
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainTodaySection(
            section = uiState.todaySection,
            now = uiState.now,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            onQuickLogDoseClick = { _, _, _, _, _ -> },
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
            onQuickLogDoseClick = { _, _, _, _, _ -> },
            onEntryClick = { }
        )
    }
}

@Preview(name = "Main Upcoming Dose Row", showBackground = true, widthDp = 420)
@Composable
private fun MainUpcomingDoseRowPreview() {
    val uiState = buildMainContentPreviewUiState()
    val dateFormatter = dateLabelFormatter(Locale.US, uiState.now.toLocalDate())
    val timeFormatter = localizedShortTimeFormatter(Locale.US, uses24HourFormat = false)

    MainContentComponentPreviewContainer {
        MainUpcomingDoseRow(
            row = uiState.upcomingSection.rows.first(),
            index = 0,
            itemCount = 3,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            showDate = true
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
                groupColorScheme = rememberMedicationGroupColorScheme(MedicationGroupColorKey.ROSE),
            )
            MainRouteIconSurface(
                applicationType = MedicationApplicationType.PATCH_ON,
                groupColorScheme = rememberMedicationGroupColorScheme(MedicationGroupColorKey.TEAL),
            )
            MainRouteIconSurface(
                applicationType = MedicationApplicationType.INJECTION,
                groupColorScheme = rememberMedicationGroupColorScheme(MedicationGroupColorKey.INDIGO),
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
            summary = "May 5 · 1/4 done",
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
            totalCount = 3,
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
        dose = MedicationDose.MgAsMedicine(2.0)
    )
    val estradiolSublingual = previewMedication(
        uuid = "98f22e3e-ef38-4701-9f73-94a6b3430d9b",
        key = MedicationKey.ESTRADIOL_VALERATE,
        applicationType = MedicationApplicationType.SUBLINGUAL,
        dose = MedicationDose.MgAsMedicine(1.0)
    )
    val estradiolPatch = previewMedication(
        uuid = "e483ee93-976d-445a-9b0d-78ee8753d59e",
        key = MedicationKey.ESTRADIOL_PATCH,
        applicationType = MedicationApplicationType.PATCH_ON,
        dose = MedicationDose.PatchReleaseRateMcgPerDay(100.0)
    )
    val spironolactone = previewMedication(
        uuid = "be598126-5d8f-4daa-bdc6-54d45035f3f3",
        key = MedicationKey.SPIRONOLACTONE,
        applicationType = MedicationApplicationType.ORAL,
        dose = MedicationDose.MgAsMedicine(100.0)
    )

    return MainUiState(
        isLoading = false,
        now = now,
        e2Hero = MainE2HeroUiState(
            currentValue = 147,
            changeSinceYesterday = 8,
            targetMin = 100,
            targetMax = 200,
            lastDoseDetails = estradiolTablet.details,
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
                lastDoseDetails = spironolactone.details,
                lastDoseAt = now.minusHours(1).minusMinutes(10),
                nextDoseAt = now.plusHours(11).plusMinutes(30)
            )
        ),
        todaySection = MainTodaySectionUiState(
            date = now.toLocalDate(),
            doneCount = 1,
            totalCount = 4,
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
    dose: MedicationDose,
    count: Int = 1
): MedicationGroupMedication {
    return MedicationGroupMedication(
        uuid = UUID.fromString(uuid),
        details = MedicationDetails(
            category = key.category,
            applicationType = applicationType,
            selection = MedicationSelection.Catalog(key),
            dose = dose
        ),
        count = count
    )
}
