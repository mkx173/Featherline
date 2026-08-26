package com.mkx.hrttracker.ui.main

import android.icu.text.ListFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkPredictiveBandKnot
import com.mkx.hrttracker.ui.calibration.PkCalibrationHeroKind
import com.mkx.hrttracker.ui.calibration.PkCalibrationUiState
import com.mkx.hrttracker.ui.calibration.applicationType
import com.mkx.hrttracker.ui.components.HrtPill
import com.mkx.hrttracker.ui.components.HrtPillSize
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.util.labelRes
import com.mkx.hrttracker.util.rememberAppLocale
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * Home-surface calibration overlay (Phase-2 plan §2.4): the hero pill, the
 * 68/95% observation-predictive band decoration, and the render/band failure
 * presentations. Lives beside MainContentComponents.kt on purpose (plan R5).
 */

/**
 * View-safe calibration state for Home. Carries display states and the
 * validated, display-unit band geometry only — never raw fit parameters.
 */
data class MainPkCalibrationUiState(
    val ui: PkCalibrationUiState,
    val band: MainE2CalibrationBand?,
)

/** Band knots mapped to chart coordinates (xHours + display-unit values). */
data class MainE2CalibrationBand(
    val xHours: List<Double>,
    val p025: List<Float>,
    val p158655254: List<Float>,
    val p841344746: List<Float>,
    val p975: List<Float>,
)

/**
 * Converts validated band knots into the chart's coordinate space: hours since
 * the chart window start, values in the selected display unit (§7: line,
 * bands, card, labs, and axes share one unit). Returns null when no knot lands
 * inside the visible window ([0, windowHours]): a READY band with no in-window
 * geometry must not surface a legend row (Phase-3 #9).
 */
fun buildMainE2CalibrationBand(
    bandKnots: List<PkPredictiveBandKnot>,
    now: LocalDateTime,
    zoneId: ZoneId,
    pastDays: Long,
    windowHours: Long,
    displayUnit: BloodUnitKey,
): MainE2CalibrationBand? {
    if (bandKnots.isEmpty()) {
        return null
    }
    val windowStartEpochMillis = mainE2ChartWindowStart(now, pastDays)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
    val xHours = bandKnots.map { knot ->
        (knot.epochMillis - windowStartEpochMillis) / 3_600_000.0
    }
    if (xHours.none { x -> x in 0.0..windowHours.toDouble() }) {
        return null
    }

    fun display(canonicalPgml: Double): Float = BloodTestCatalog.fromCanonical(
        analyteKey = BloodAnalyteKey.E2,
        canonicalValue = canonicalPgml,
        unit = displayUnit,
    ).toFloat()

    return MainE2CalibrationBand(
        xHours = xHours,
        p025 = bandKnots.map { knot -> display(knot.p025Pgml) },
        p158655254 = bandKnots.map { knot -> display(knot.p158655254Pgml) },
        p841344746 = bandKnots.map { knot -> display(knot.p841344746Pgml) },
        p975 = bandKnots.map { knot -> display(knot.p975Pgml) },
    )
}

/**
 * Draws the nested 95% and 68% predictive envelopes under the chart layers.
 * Knots are engine-supplied at the right x positions; spans without knots stay
 * unshaded, so the fill is segmented on x gaps instead of interpolating across
 * a PK discontinuity (§7).
 */
internal class MainE2CalibrationBandDecoration(
    private val band: MainE2CalibrationBand,
    private val maxY: Double,
    private val outerColor: Color,
    private val innerColor: Color,
    private val coordinateMapper: MainE2ChartCoordinateMapper? = null,
) : Decoration {
    private val segments: List<IntRange> = bandGapSegments(band.xHours)

    override fun drawUnderLayers(context: CartesianDrawingContext) {
        coordinateMapper?.update(context)
        if (maxY <= 0.0 || context.layerBounds.height <= 0f) {
            return
        }
        segments.forEach { segment ->
            drawEnvelope(context, segment, band.p025, band.p975, outerColor)
            drawEnvelope(context, segment, band.p158655254, band.p841344746, innerColor)
        }
    }

    private fun drawEnvelope(
        context: CartesianDrawingContext,
        segment: IntRange,
        lower: List<Float>,
        upper: List<Float>,
        color: Color,
    ) {
        val path = Path()
        var pointCount = 0
        for (index in segment) {
            val x = context.mainE2ChartCanvasXForLine(
                x = band.xHours[index],
                clampToLayerBounds = true,
            ) ?: continue
            val y = context.canvasYFor(upper[index])
            if (pointCount == 0) path.moveTo(x, y) else path.lineTo(x, y)
            pointCount++
        }
        if (pointCount < 2) {
            return
        }
        for (index in segment.reversed()) {
            val x = context.mainE2ChartCanvasXForLine(
                x = band.xHours[index],
                clampToLayerBounds = true,
            ) ?: continue
            path.lineTo(x, context.canvasYFor(lower[index]))
        }
        path.close()
        context.canvas.drawPath(path, Paint().apply { this.color = color })
    }

    // Same normalization as the chart's point overlay: y in [0, maxY] maps
    // linearly onto layerBounds. Values above the axis maximum clamp to the
    // plot edge rather than escaping the layer.
    private fun CartesianDrawingContext.canvasYFor(value: Float): Float {
        val normalized = (value.toDouble().coerceIn(0.0, maxY) / maxY).toFloat()
        return layerBounds.bottom - normalized * layerBounds.height
    }
}

/**
 * Splits knot indices into contiguous runs. A spacing jump far above the
 * median marks an engine-left gap (a span with no promoted contribution);
 * shading must not bridge it.
 */
internal fun bandGapSegments(xHours: List<Double>): List<IntRange> {
    if (xHours.size < 2) {
        return if (xHours.isEmpty()) emptyList() else listOf(0..0)
    }
    val spacings = xHours.zipWithNext { a, b -> b - a }.sorted()
    val medianSpacing = spacings[spacings.size / 2]
    val gapThreshold = medianSpacing * 3.0
    val segments = mutableListOf<IntRange>()
    var start = 0
    for (index in 1 until xHours.size) {
        if (xHours[index] - xHours[index - 1] > gapThreshold) {
            segments += start until index
            start = index
        }
    }
    segments += start until xHours.size
    return segments.map { range -> range.first..range.last }
}

/** Shared x mapper for chart decorations (extracted from VerticalLineDecoration). */
internal fun CartesianDrawingContext.mainE2ChartCanvasXForLine(
    x: Double,
    clampToLayerBounds: Boolean,
): Float? {
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

// ---------------------------------------------------------------------------
// Hero pill (§5.1)
// ---------------------------------------------------------------------------

@Composable
internal fun MainPkCalibrationHeroPills(pk: PkCalibrationUiState) {
    val colorScheme = MaterialTheme.colorScheme
    if (pk.heroKind == PkCalibrationHeroKind.ADJUSTED) {
        val appLocale = rememberAppLocale()
        val names = pk.effectivePromotedRoutes
            .map { route -> stringResource(route.applicationType.labelRes) }
        val joinedNames = remember(names, appLocale) {
            ListFormatter.getInstance(appLocale).format(names)
        }
        HrtPill(
            label = stringResource(R.string.calibration_pk_hero_adjusted, joinedNames),
            containerColor = colorScheme.primaryContainer.copy(alpha = 0.7f),
            contentColor = colorScheme.onPrimaryContainer,
            size = HrtPillSize.Small,
            fontWeight = FontWeight.SemiBold,
            icon = {
                Icon(
                    painter = painterResource(
                        if (pk.limitedConfidence) {
                            R.drawable.ic_experiment
                        } else {
                            R.drawable.ic_check_circle_heavy
                        }
                    ),
                    contentDescription = null,
                    modifier = iconModifier,
                    tint = colorScheme.onPrimaryContainer,
                )
            },
        )
        if (pk.limitedConfidence) {
            HrtPill(
                label = stringResource(R.string.calibration_pk_hero_limited_confidence),
                containerColor = colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                contentColor = colorScheme.onTertiaryContainer,
                size = HrtPillSize.Small,
                fontWeight = FontWeight.SemiBold,
            )
        }
    } else {
        HrtPill(
            label = stringResource(R.string.calibration_pk_hero_population),
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurfaceVariant,
            size = HrtPillSize.Small,
            fontWeight = FontWeight.SemiBold,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_labs),
                    contentDescription = null,
                    modifier = iconModifier,
                    tint = colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Chart failure presentations (§6, §7)
// ---------------------------------------------------------------------------

/** Shown instead of the plot when the shared central render is unavailable. */
@Composable
internal fun MainPkCalibrationChartUnavailableCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sync_alt),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            val title = stringResource(R.string.calibration_pk_chart_unavailable_title)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.cjkTextOffset(title),
            )
            val body = stringResource(R.string.calibration_pk_chart_unavailable_body)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .cjkTextOffset(body),
            )
        }
    }
}

/**
 * Legend + informational note under the chart: the accessible band summary
 * and the band-only-failure warning (central line kept, §7).
 */
@Composable
internal fun MainPkCalibrationChartNote(pk: MainPkCalibrationUiState) {
    val bandUnavailable = pk.ui.bandState == PkCalibrationBandState.NUMERIC_UNAVAILABLE
    val bandSummary = stringResource(R.string.calibration_pk_band_a11y_summary)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (pk.band != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .semantics { contentDescription = bandSummary },
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 8.dp)
                        .padding(end = 0.dp)
                ) {
                    Row {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.size(9.dp, 8.dp),
                        ) { }
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.size(9.dp, 8.dp),
                        ) { }
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.calibration_pk_band_legend),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (bandUnavailable) {
            MainPkCalibrationNoteLine(
                text = stringResource(R.string.calibration_pk_band_unavailable_note),
            )
        }
    }
}

@Composable
private fun MainPkCalibrationNoteLine(text: String) {
    Row(modifier = Modifier.padding(top = 6.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.cjkTextOffset(text),
        )
    }
}
