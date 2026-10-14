package com.mkx.hrttracker.ui.main

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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkPredictiveBandKnot
import com.mkx.hrttracker.ui.calibration.PkCalibrationUiState
import com.mkx.hrttracker.ui.calibration.pkCalibrationSmallShareRoutes
import com.mkx.hrttracker.ui.calibration.applicationType
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.desaturateHct
import com.mkx.hrttracker.util.labelRes
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerDrawingModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * Home-surface calibration overlay: the hero pill, the 68/95%
 * observation-predictive band decoration, and the render/band failure
 * presentations.
 */

/**
 * Snapshot-sourced calibration presentation for Home; nothing here waits for
 * the live evaluation. Carries display flags and display-unit band geometry
 * only, never raw fit parameters.
 */
data class MainPkCalibrationUiState(
    val adjusted: Boolean,
    val limitedConfidence: Boolean,
    val renderUnavailable: Boolean,
    val bandUnavailable: Boolean,
    val band: MainE2CalibrationBand?,
    /** The routes behind [adjusted], over the snapshot's chart range. */
    val effectivePromotedRoutes: List<PkCalibrationRoute> = emptyList(),
) {
    /** The estimate sheet's "small share" rows, judged over the same range as the hero. */
    fun smallShareRoutes(sheet: PkCalibrationUiState?): Set<PkCalibrationRoute> =
        pkCalibrationSmallShareRoutes(
            routeRows = sheet?.routeRows.orEmpty(),
            effectivePromotedRoutes = effectivePromotedRoutes,
            renderUnavailable = renderUnavailable,
        )

    /** The legend/note row under the chart is shown. */
    val showsChartNote: Boolean get() = band != null || bandUnavailable
}

/** Primary-color alphas of the 95% and 68% bands; the 68% band is drawn over the 95% one. */
internal const val MainE2CalibrationBandOuterAlpha = 0.06f
internal const val MainE2CalibrationBandInnerAlpha = 0.10f

/** Desaturated primary the bands and their legend swatches are tinted with. */
@Composable
internal fun mainE2CalibrationBandColor(): Color {
    val primary = MaterialTheme.colorScheme.primary
    return remember(primary) { primary.desaturateHct() }
}

/**
 * Band knots mapped to chart coordinates (xHours + display-unit values).
 * [segments] are the engine's contiguous runs as index ranges; the fill
 * breaks between them.
 */
data class MainE2CalibrationBand(
    val xHours: List<Double>,
    val p025: List<Float>,
    val p158655254: List<Float>,
    val p841344746: List<Float>,
    val p975: List<Float>,
    val segments: List<IntRange> = listOf(xHours.indices),
)

/**
 * Upper band edge restricted to knots inside [0, windowHours]. The snapshot's
 * forecast buffer extends past the visible range; a peak out there must not
 * size the y-axis.
 */
fun MainE2CalibrationBand.visibleUpper(windowHours: Int): List<Float> {
    return p975.filterIndexed { index, _ -> xHours[index] in 0.0..windowHours.toDouble() }
}

/**
 * Converts validated band knots into the chart's coordinate space: hours since
 * the chart window start, values in the selected display unit (line, bands,
 * card, labs, and axes share one unit). Returns null when no knot lands
 * inside the visible window ([0, windowHours]): a READY band with no in-window
 * geometry must not surface a legend row.
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
        segments = bandSegments(bandKnots.map(PkPredictiveBandKnot::segment)),
    )
}

/**
 * Draws the nested 95% and 68% predictive envelopes under the chart layers.
 * Knots are engine-supplied at the right x positions; spans the engine left
 * out stay unshaded, so the fill is drawn per engine segment instead of
 * interpolating across a PK discontinuity.
 */
internal class MainE2CalibrationBandDecoration(
    private val band: MainE2CalibrationBand,
    private val maxY: Double,
    private val outerColor: Color,
    private val innerColor: Color,
    private val coordinateMapper: MainE2ChartCoordinateMapper? = null,
    private val lineDrawingModelKey: ExtraStore.Key<LineCartesianLayerDrawingModel>? = null,
) : Decoration {
    private val segments: List<IntRange> = band.segments

    override fun drawUnderLayers(context: CartesianDrawingContext) {
        coordinateMapper?.update(context)
        if (maxY <= 0.0 || context.layerBounds.height <= 0f) {
            return
        }
        // Vico animates only layer drawing models, never decorations. During
        // the intro the line layer's drawing model carries opacity == tween
        // fraction (it lerps 0 -> 1 from a null "old" model), so reusing it
        // makes the band rise and fade with the line. Absent model (the
        // synchronous post-intro host, or a later data update) draws at rest.
        val progress = lineDrawingModelKey
            ?.let { key -> context.extraStore.getOrNull(key)?.opacity }
            ?: 1f
        segments.forEach { segment ->
            drawEnvelope(context, segment, band.p025, band.p975, outerColor, progress)
            drawEnvelope(context, segment, band.p158655254, band.p841344746, innerColor, progress)
        }
    }

    private fun drawEnvelope(
        context: CartesianDrawingContext,
        segment: IntRange,
        lower: List<Float>,
        upper: List<Float>,
        color: Color,
        progress: Float,
    ) {
        val path = Path()
        var pointCount = 0
        for (index in segment) {
            val x = context.mainE2ChartCanvasXForLine(
                x = band.xHours[index],
                clampToLayerBounds = true,
            ) ?: continue
            val y = context.canvasYFor(upper[index], progress)
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
            path.lineTo(x, context.canvasYFor(lower[index], progress))
        }
        path.close()
        context.canvas.drawPath(
            path,
            Paint().apply { this.color = color.copy(alpha = color.alpha * progress) },
        )
    }

    // Same normalization as the chart's point overlay: y in [0, maxY] maps
    // linearly onto layerBounds. Values above the axis maximum clamp to the
    // plot edge rather than escaping the layer. `progress` scales the height
    // the same way Vico lerps the line's entries from the baseline.
    private fun CartesianDrawingContext.canvasYFor(value: Float, progress: Float): Float {
        val normalized = (value.toDouble().coerceIn(0.0, maxY) / maxY).toFloat()
        return layerBounds.bottom - normalized * progress * layerBounds.height
    }
}

/**
 * Groups knot indices by the engine's run tag ([PkPredictiveBandKnot.segment])
 * into contiguous index ranges. The engine leaves out every knot no calibrated
 * route shapes, and spacing is nonuniform, so a hole of one or two knots is
 * only visible through the tag; shading must not bridge it.
 */
internal fun bandSegments(segmentTags: List<Int>): List<IntRange> {
    val segments = mutableListOf<IntRange>()
    var start = 0
    for (index in 1 until segmentTags.size) {
        if (segmentTags[index] != segmentTags[index - 1]) {
            segments += start until index
            start = index
        }
    }
    if (segmentTags.isNotEmpty()) segments += start until segmentTags.size
    return segments
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
// Chart failure presentations
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
                painter = painterResource(R.drawable.ic_help),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            val title = stringResource(R.string.calibration_pk_chart_unavailable_title)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            val body = stringResource(R.string.calibration_pk_chart_unavailable_body)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp),
            )
        }
    }
}

/**
 * Legend + informational note under the chart: the accessible band summary
 * and the band-only-failure warning (central line kept).
 */
@Composable
internal fun MainPkCalibrationChartNote(pk: MainPkCalibrationUiState) {
    val bandUnavailable = pk.bandUnavailable
    // An empty note would still take the parent column's spacing.
    if (!pk.showsChartNote) {
        return
    }
    val bandSummary = stringResource(R.string.calibration_pk_band_a11y_summary)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (pk.band != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .semantics { contentDescription = bandSummary },
            ) {
                Spacer(modifier = Modifier.weight(1f))
                val pkBandLegendText = stringResource(R.string.calibration_pk_band_legend)
                // Swatches sit on the card, but the bands blend over the chart's
                // surfaceContainer plot (the 68% band over the 95% one), so paint
                // the opaque result the plot shows.
                val bandColor = mainE2CalibrationBandColor()
                val plotColor = MaterialTheme.colorScheme.surfaceContainer
                val outerSwatchColor = bandColor.copy(alpha = MainE2CalibrationBandOuterAlpha)
                    .compositeOver(plotColor)
                val innerSwatchColor = bandColor.copy(alpha = MainE2CalibrationBandInnerAlpha)
                    .compositeOver(outerSwatchColor)
                Box {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            color = innerSwatchColor,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.size(9.dp),
                        ) { }
                        Surface(
                            color = outerSwatchColor,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.size(9.dp),
                        ) { }
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = pkBandLegendText,
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
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val previewPkCalibration = MainPkCalibrationUiState(
    adjusted = true,
    limitedConfidence = false,
    renderUnavailable = false,
    bandUnavailable = false,
    band = MainE2CalibrationBand(
        xHours = listOf(0.0, 1.0),
        p025 = listOf(0f, 0f),
        p158655254 = listOf(0f, 0f),
        p841344746 = listOf(0f, 0f),
        p975 = listOf(0f, 0f),
    ),
)

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MainPkCalibrationChartNotePreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                MainPkCalibrationChartNote(previewPkCalibration)
                MainPkCalibrationChartNote(previewPkCalibration.copy(band = null, bandUnavailable = true))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MainPkCalibrationChartUnavailableCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Surface {
            MainPkCalibrationChartUnavailableCard(modifier = Modifier.padding(16.dp))
        }
    }
}
