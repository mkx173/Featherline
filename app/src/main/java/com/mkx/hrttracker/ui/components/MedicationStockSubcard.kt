package com.mkx.hrttracker.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.FLOAT_EPSILON
import com.mkx.hrttracker.data.repository.deductInsertedDoseStock
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.text.NumberFormat
import java.util.Locale

internal enum class MedicationStockSubcardTone {
    HEALTHY,
    WARNING,
    ERROR,
    NEUTRAL,
}

internal enum class MedicationStockSubcardRowKind(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val contentDescriptionRes: Int,
    @param:StringRes val labelRes: Int,
    val iconSize: Dp
) {
    STOCK_POOL(
        iconRes = R.drawable.ic_inventory_2,
        contentDescriptionRes = R.string.stock_subcard_cd_stock_pool,
        labelRes = R.string.stock_row_label_stock,
        iconSize = 20.dp
    ),
    OPEN_VIAL(
        iconRes = R.drawable.ic_humidity_mid,
        contentDescriptionRes = R.string.stock_subcard_cd_open_vial,
        labelRes = R.string.stock_row_label_current_vial,
        iconSize = 24.dp
    ),
    OPEN_CONTAINER(
        iconRes = R.drawable.ic_humidity_mid,
        contentDescriptionRes = R.string.stock_subcard_cd_open_container,
        labelRes = R.string.stock_row_label_current_container,
        iconSize = 24.dp
    ),
}

internal data class MedicationStockSubcardSealedSupplement(
    val countText: String,
    val previewCountText: String? = null,
)

internal data class MedicationStockSubcardText(
    @param:StringRes val resId: Int? = null,
    @param:PluralsRes val pluralResId: Int? = null,
    val intArg: Int? = null,
)

internal data class MedicationStockSubcardRowModel(
    val kind: MedicationStockSubcardRowKind,
    val valueText: String,
    val previewValueText: String? = null,
    @param:StringRes val valueUnitRes: Int? = null,
    val valuePluralCount: Double? = null,
    val previewPluralCount: Double? = null,
    val progress: Float,
    val sealedSupplement: MedicationStockSubcardSealedSupplement? = null,
    val opensNewContainer: Boolean = false,
) {
    @get:DrawableRes
    val iconRes: Int get() = kind.iconRes

    @get:StringRes
    val contentDescriptionRes: Int get() = kind.contentDescriptionRes

    @get:StringRes
    val labelRes: Int get() = kind.labelRes

    val iconSize: Dp get() = kind.iconSize
}

internal data class MedicationStockSubcardModel(
    @param:StringRes val chipLabelRes: Int?,
    val tone: MedicationStockSubcardTone,
    val currentValueTone: MedicationStockSubcardTone,
    val previewValueTone: MedicationStockSubcardTone?,
    val blinkPreview: Boolean,
    val runwayText: MedicationStockSubcardText?,
    val rows: List<MedicationStockSubcardRowModel>,
) {
    val showsHeader: Boolean get() = chipLabelRes != null || runwayText != null
}

internal fun medicationStockSubcardModel(
    projection: MedicineStockProjection?,
    mutationPreviewDoseMagnitude: Double? = null,
    previewPostMutationState: ((MedicineStock) -> MedicineStockState?)? = null,
): MedicationStockSubcardModel? {
    projection ?: return null
    val medicine = projection.medicine
    val stock = medicine.stock
    if (!stock.trackingEnabled || projection.state == MedicineStockState.UNTRACKED) return null
    if (medicine.preparation is MedicinePreparation.PatchOff) return null

    val preview = stockMutationPreview(
        medicine = medicine,
        requestedDose = mutationPreviewDoseMagnitude,
    )
    val rows = stockSubcardRows(
        projection = projection,
        mutationPreview = preview,
    )
    if (rows.isEmpty()) return null

    val currentState = projection.state
    val currentValueTone = stockSubcardTone(currentState)
    val postMutationState = preview?.afterStock?.let { after ->
        previewPostMutationState?.invoke(after)
    }
    val hasPreview = rows.any { it.previewValueText != null }
    val previewValueTone = when {
        !hasPreview -> null
        postMutationState != null -> stockSubcardTone(postMutationState)
        else -> currentValueTone
    }
    val blinkPreview = hasPreview &&
        postMutationState != null &&
        postMutationState != currentState

    return MedicationStockSubcardModel(
        chipLabelRes = stockSubcardChipLabelRes(currentState),
        tone = stockSubcardTone(currentState),
        currentValueTone = currentValueTone,
        previewValueTone = previewValueTone,
        blinkPreview = blinkPreview,
        runwayText = stockSubcardRunwayText(projection.runway),
        rows = rows,
    )
}

@Composable
internal fun MedicationStockSubcard(
    projection: MedicineStockProjection,
    containerColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    mutationPreviewDoseMagnitude: Double? = null,
    previewPostMutationState: ((MedicineStock) -> MedicineStockState?)? = null,
) {
    val model = remember(projection, mutationPreviewDoseMagnitude, previewPostMutationState) {
        medicationStockSubcardModel(
            projection = projection,
            mutationPreviewDoseMagnitude = mutationPreviewDoseMagnitude,
            previewPostMutationState = previewPostMutationState,
        )
    } ?: return
    val chipColors = stockSubcardChipColors(model.tone)
    val iconContainerColor = stockSubcardIconContainerColor(
        containerColor = containerColor,
        surfaceContainer = MaterialTheme.colorScheme.surfaceContainer,
        surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh,
        surfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    val progressIndicatorColors = stockSubcardProgressIndicatorColors(
        tone = model.tone,
        primary = MaterialTheme.colorScheme.primary,
        tertiary = MaterialTheme.colorScheme.tertiary,
        error = MaterialTheme.colorScheme.error,
        secondary = MaterialTheme.colorScheme.secondary,
        secondaryContainer = MaterialTheme.colorScheme.secondaryContainer,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (model.showsHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    model.chipLabelRes?.let { chipLabelRes ->
                        StockSubcardChip(
                            chipLabelRes = chipLabelRes,
                            colors = chipColors,
                        )
                    }
                    model.runwayText?.let { runwayText ->
                        if (model.chipLabelRes != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        val runwayTextString = runwayText.resolve()
                        Text(
                            text = runwayTextString,
                            modifier = Modifier.weight(1f).cjkTextOffset(runwayTextString),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            StockSubcardMetrics(
                rows = model.rows,
                iconContainerColor = iconContainerColor,
                progressIndicatorColors = progressIndicatorColors,
                currentValueTone = model.currentValueTone,
                previewValueTone = model.previewValueTone,
                blinkPreview = model.blinkPreview,
            )
        }
    }
}

@Composable
private fun StockSubcardChip(
    @StringRes chipLabelRes: Int,
    colors: StockSubcardChipColors,
) {
    HrtPill(
        label = stringResource(chipLabelRes),
        containerColor = colors.container,
        contentColor = colors.content,
        size = HrtPillSize.XSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun StockSubcardMetrics(
    rows: List<MedicationStockSubcardRowModel>,
    iconContainerColor: Color,
    progressIndicatorColors: StockSubcardProgressIndicatorColors,
    currentValueTone: MedicationStockSubcardTone,
    previewValueTone: MedicationStockSubcardTone?,
    blinkPreview: Boolean,
) {
    rows.firstOrNull()?.let { row ->
        StockSubcardMetricCell(
            row = row,
            iconContainerColor = iconContainerColor,
            progressIndicatorColors = progressIndicatorColors,
            currentValueTone = currentValueTone,
            previewValueTone = previewValueTone,
            blinkPreview = blinkPreview,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StockSubcardMetricCell(
    row: MedicationStockSubcardRowModel,
    iconContainerColor: Color,
    progressIndicatorColors: StockSubcardProgressIndicatorColors,
    currentValueTone: MedicationStockSubcardTone,
    previewValueTone: MedicationStockSubcardTone?,
    blinkPreview: Boolean,
    modifier: Modifier = Modifier,
) {
    val metricTextStyle = MaterialTheme.typography.labelMedium

    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            color = iconContainerColor,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.aspectRatio(1f).width(24.dp).fillMaxHeight()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(row.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(row.iconSize),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val progressModifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(6.dp)
            // When the previewed dose opens a new container, show two bars: the
            // fresh container, full, on the left; the current container on the
            // right, blinking as the dose drains it away into the new one. The
            // left bar's width is animated by its weight so dragging the slider
            // past the unseal boundary opens/collapses it smoothly instead of a
            // hard swap to a single bar.
            val newContainerBarWeight = animateFloatAsState(
                targetValue = if (row.opensNewContainer) 1f else 0f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "stockNewContainerBarWeight",
            ).value
            val pulseAlpha = if (row.opensNewContainer || blinkPreview) {
                rememberStockSubcardPulseAlpha()
            } else {
                1f
            }
            if (newContainerBarWeight > 0.005f) {
                Row(
                    modifier = progressModifier,
                    horizontalArrangement = Arrangement.spacedBy(6.dp * newContainerBarWeight),
                ) {
                    LinearProgressIndicator(
                        progress = { 1f },
                        color = progressIndicatorColors.color,
                        trackColor = progressIndicatorColors.trackColor,
                        modifier = Modifier.weight(newContainerBarWeight).height(6.dp),
                    )
                    PulsingStockSubcardProgressIndicator(
                        progress = row.progress,
                        colors = progressIndicatorColors,
                        // Blink only while the dose actually opens a container; as
                        // the bar collapses back the current container settles solid.
                        pulsing = row.opensNewContainer,
                        pulseAlpha = pulseAlpha,
                        modifier = Modifier.weight(1f).height(6.dp),
                    )
                }
            } else {
                LinearProgressIndicator(
                    progress = { row.progress },
                    color = progressIndicatorColors.color,
                    trackColor = progressIndicatorColors.trackColor,
                    modifier = progressModifier,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val labelText = stringResource(row.labelRes)
                val sealedSuffix = row.sealedSupplement?.labelSuffix().orEmpty()
                Text(
                    text = labelText + sealedSuffix,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                    style = metricTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StockSubcardValueText(
                    row = row,
                    textStyle = metricTextStyle,
                    currentValueTone = currentValueTone,
                    previewValueTone = previewValueTone,
                    blinkPreview = blinkPreview,
                    pulseAlpha = pulseAlpha,
                )
            }
        }
    }
}

@Composable
private fun rememberStockSubcardPulseAlpha(): Float {
    // Callers create this only when a pulse is active, then share the same alpha
    // across every blinking element in the metric row.
    val transition = rememberInfiniteTransition(label = "stockSubcardPulse")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stockSubcardPulseAlpha",
    ).value
}

@Composable
private fun PulsingStockSubcardProgressIndicator(
    progress: Float,
    colors: StockSubcardProgressIndicatorColors,
    pulsing: Boolean,
    pulseAlpha: Float,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { progress },
        color = colors.color,
        trackColor = colors.trackColor,
        modifier = modifier.alpha(if (pulsing) pulseAlpha else 1f),
    )
}

@Composable
private fun StockSubcardValueText(
    row: MedicationStockSubcardRowModel,
    textStyle: androidx.compose.ui.text.TextStyle,
    currentValueTone: MedicationStockSubcardTone,
    previewValueTone: MedicationStockSubcardTone?,
    blinkPreview: Boolean,
    pulseAlpha: Float,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val tertiary = MaterialTheme.colorScheme.tertiary
    val error = MaterialTheme.colorScheme.error
    val currentColor = stockSubcardValueTextColor(currentValueTone, onSurface, tertiary, error)
    val previewColor = stockSubcardValueTextColor(
        previewValueTone ?: currentValueTone,
        onSurface,
        tertiary,
        error,
    )

    val previewValueText = row.previewValueText
    if (previewValueText == null) {
        Text(
            text = resolvedStockValueText(
                valueText = row.valueText,
                unitRes = row.valueUnitRes,
                pluralCount = row.valuePluralCount,
            ),
            style = textStyle,
            color = currentColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    val blinkAlpha = if (blinkPreview) pulseAlpha else 1f

    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Opening a new container skips the "before" volume: the current dreg is
        // shown on the pulsing bar instead, so the value reads "→ <fresh unit>".
        if (!row.opensNewContainer) {
            Text(
                text = row.valueText,
                style = textStyle,
                color = currentColor,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Text(
            text = " → ",
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = resolvedStockValueText(
                valueText = previewValueText,
                unitRes = row.valueUnitRes,
                pluralCount = row.previewPluralCount,
            ),
            style = textStyle,
            color = previewColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alignByBaseline().alpha(blinkAlpha),
        )
    }
}

@Composable
private fun resolvedStockValueText(
    valueText: String,
    @StringRes unitRes: Int?,
    pluralCount: Double?,
): String {
    unitRes ?: return valueText
    val pluralRes = stockUnitNounPluralForUnitRes(unitRes)
    val unit = if (pluralRes != null && pluralCount != null) {
        pluralStringResource(pluralRes, stockCountPluralQuantity(pluralCount))
    } else {
        stringResource(unitRes)
    }
    return stringResource(
        R.string.stock_row_count_with_unit,
        valueText,
        unit,
    )
}

@Composable
private fun MedicationStockSubcardText.resolve(): String {
    return when {
        pluralResId != null && intArg != null ->
            pluralStringResource(pluralResId, intArg, intArg)
        resId != null -> stringResource(resId)
        else -> ""
    }
}

internal data class StockSubcardChipColors(
    val container: Color,
    val content: Color,
)

internal data class StockSubcardProgressIndicatorColors(
    val color: Color,
    val trackColor: Color,
)

internal fun stockSubcardIconContainerColor(
    containerColor: Color,
    surfaceContainer: Color,
    surfaceContainerHigh: Color,
    surfaceContainerHighest: Color,
): Color {
    return when (containerColor) {
        surfaceContainer -> surfaceContainerHigh
        surfaceContainerHigh -> surfaceContainerHighest
        else -> surfaceContainerHighest
    }
}

internal fun stockSubcardProgressIndicatorColors(
    tone: MedicationStockSubcardTone,
    primary: Color,
    tertiary: Color,
    error: Color,
    secondary: Color,
    secondaryContainer: Color,
): StockSubcardProgressIndicatorColors {
    return when (tone) {
        MedicationStockSubcardTone.HEALTHY -> StockSubcardProgressIndicatorColors(
            color = primary,
            trackColor = secondaryContainer,
        )
        MedicationStockSubcardTone.WARNING -> StockSubcardProgressIndicatorColors(
            color = tertiary,
            trackColor = secondaryContainer,
        )
        MedicationStockSubcardTone.ERROR -> StockSubcardProgressIndicatorColors(
            color = error,
            trackColor = secondaryContainer,
        )
        MedicationStockSubcardTone.NEUTRAL -> StockSubcardProgressIndicatorColors(
            color = secondary,
            trackColor = secondaryContainer,
        )
    }
}

internal fun stockSubcardValueTextColor(
    tone: MedicationStockSubcardTone,
    onSurface: Color,
    tertiary: Color,
    error: Color,
): Color = when (tone) {
    MedicationStockSubcardTone.WARNING -> tertiary
    MedicationStockSubcardTone.ERROR -> error
    MedicationStockSubcardTone.HEALTHY,
    MedicationStockSubcardTone.NEUTRAL,
    -> onSurface
}

@Composable
internal fun stockSubcardChipColors(
    tone: MedicationStockSubcardTone,
): StockSubcardChipColors {
    return when (tone) {
        MedicationStockSubcardTone.HEALTHY -> StockSubcardChipColors(
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        MedicationStockSubcardTone.WARNING -> StockSubcardChipColors(
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        MedicationStockSubcardTone.ERROR -> StockSubcardChipColors(
            container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
        MedicationStockSubcardTone.NEUTRAL -> StockSubcardChipColors(
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun stockSubcardRows(
    projection: MedicineStockProjection,
    mutationPreview: StockSubcardMutationPreview?,
): List<MedicationStockSubcardRowModel> {
    val stock = projection.medicine.stock
    return when (val preparation = projection.medicine.preparation) {
        is MedicinePreparation.InjectionMultiUseVial -> listOf(
            stock.openContainerAmount?.let { openAmount ->
                openContainerStockSubcardRow(
                    kind = MedicationStockSubcardRowKind.OPEN_VIAL,
                    openAmount = openAmount,
                    capacity = preparation.vialVolumeMl,
                    valueUnitRes = R.string.stock_unit_ml,
                    sealedCount = stock.unitsRemaining,
                    mutationPreviewOpenAmount = mutationPreview?.openContainerAmountAfter,
                    mutationPreviewSealedCount = mutationPreview?.unitsRemainingAfter,
                )
            } ?: stockPoolSubcardRow(stock, preparation, mutationPreview),
        )

        is MedicinePreparation.GelContainer -> listOf(
            stock.openContainerAmount?.let { openAmount ->
                openContainerStockSubcardRow(
                    kind = MedicationStockSubcardRowKind.OPEN_CONTAINER,
                    openAmount = openAmount,
                    capacity = preparation.containerWeightGrams,
                    valueUnitRes = R.string.stock_unit_g,
                    sealedCount = stock.unitsRemaining,
                    mutationPreviewOpenAmount = mutationPreview?.openContainerAmountAfter,
                    mutationPreviewSealedCount = mutationPreview?.unitsRemainingAfter,
                )
            } ?: stockPoolSubcardRow(stock, preparation, mutationPreview),
        )

        is MedicinePreparation.PatchOff -> emptyList()

        else -> listOf(stockPoolSubcardRow(stock, preparation, mutationPreview))
    }
}

private fun openContainerStockSubcardRow(
    kind: MedicationStockSubcardRowKind,
    openAmount: Double,
    capacity: Double,
    @StringRes valueUnitRes: Int,
    sealedCount: Double?,
    mutationPreviewOpenAmount: Double?,
    mutationPreviewSealedCount: Double?,
): MedicationStockSubcardRowModel {
    val valueText = stockSubcardValueText(
        numerator = openAmount,
        denominator = capacity,
        mutationPreviewNumerator = mutationPreviewOpenAmount,
    )
    val opensNewContainer = sealedCountDecreased(
        current = sealedCount,
        preview = mutationPreviewSealedCount,
    )
    return MedicationStockSubcardRowModel(
        kind = kind,
        valueText = valueText.currentText,
        previewValueText = valueText.previewText,
        valueUnitRes = valueUnitRes,
        valuePluralCount = capacity,
        previewPluralCount = capacity,
        progress = stockSubcardProgress(
            numerator = openAmount,
            denominator = capacity,
        ),
        sealedSupplement = stockSubcardSealedSupplement(
            sealedCount = sealedCount,
            previewSealedCount = if (opensNewContainer) mutationPreviewSealedCount else null,
        ),
        opensNewContainer = opensNewContainer,
    )
}

private fun sealedCountDecreased(current: Double?, preview: Double?): Boolean {
    val resolvedCurrent = current?.takeIf { it.isFinite() } ?: return false
    val resolvedPreview = preview?.takeIf { it.isFinite() } ?: return false
    return resolvedPreview < resolvedCurrent - FLOAT_EPSILON
}

// Formats the sealed-reserve count appended to the row label, e.g. " (+2)", or
// " (+2 → 1)" when a previewed dose is about to crack one of the reserve units.
private fun MedicationStockSubcardSealedSupplement.labelSuffix(): String {
    val preview = previewCountText
    return if (preview != null) {
        " (+$countText → $preview)"
    } else {
        " (+$countText)"
    }
}

private fun stockPoolSubcardRow(
    stock: MedicineStock,
    preparation: MedicinePreparation,
    mutationPreview: StockSubcardMutationPreview?,
): MedicationStockSubcardRowModel {
    val valueText = stockSubcardValueText(
        numerator = stock.unitsRemaining,
        denominator = stock.unitsLastTotal,
        mutationPreviewNumerator = mutationPreview?.unitsRemainingAfter,
    )
    return MedicationStockSubcardRowModel(
        kind = MedicationStockSubcardRowKind.STOCK_POOL,
        valueText = valueText.currentText,
        previewValueText = valueText.previewText,
        valueUnitRes = stockInventoryUnitRes(preparation),
        valuePluralCount = stock.unitsLastTotal ?: stock.unitsRemaining,
        previewPluralCount = stock.unitsLastTotal ?: mutationPreview?.unitsRemainingAfter,
        progress = stockSubcardProgress(
            numerator = stock.unitsRemaining,
            denominator = stock.unitsLastTotal,
        ),
    )
}

private fun stockSubcardSealedSupplement(
    sealedCount: Double?,
    previewSealedCount: Double? = null,
): MedicationStockSubcardSealedSupplement? {
    val resolvedCount = sealedCount?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    return MedicationStockSubcardSealedSupplement(
        countText = formatStockSubcardCount(resolvedCount),
        previewCountText = previewSealedCount
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { formatStockSubcardCount(it) },
    )
}

@StringRes
internal fun stockSubcardChipLabelRes(state: MedicineStockState): Int? {
    return when (state) {
        MedicineStockState.HEALTHY -> R.string.stock_subcard_chip_in_stock
        MedicineStockState.USER_LOW -> R.string.stock_subcard_chip_low
        MedicineStockState.IMMINENT -> R.string.stock_subcard_chip_almost_out
        MedicineStockState.OUT -> R.string.stock_subcard_chip_out
        MedicineStockState.NO_RUNWAY -> null
        MedicineStockState.UNTRACKED -> null
    }
}

internal fun stockSubcardTone(state: MedicineStockState): MedicationStockSubcardTone {
    return when (state) {
        MedicineStockState.HEALTHY -> MedicationStockSubcardTone.HEALTHY
        MedicineStockState.USER_LOW -> MedicationStockSubcardTone.WARNING
        MedicineStockState.IMMINENT,
        MedicineStockState.OUT,
        -> MedicationStockSubcardTone.ERROR
        MedicineStockState.NO_RUNWAY,
        MedicineStockState.UNTRACKED,
        -> MedicationStockSubcardTone.NEUTRAL
    }
}

internal fun stockSubcardRunwayText(
    runway: RunwayProjection,
): MedicationStockSubcardText? {
    return when (runway) {
        is RunwayProjection.Days -> MedicationStockSubcardText(
            pluralResId = R.plurals.stock_subcard_runway_days,
            intArg = runway.days,
        )
        RunwayProjection.BeyondHorizon -> MedicationStockSubcardText(
            resId = R.string.stock_subcard_runway_more_than_one_year,
        )
        RunwayProjection.NoSchedule -> null
    }
}

internal fun stockSubcardProgress(
    numerator: Double?,
    denominator: Double?,
): Float {
    val resolvedNumerator = numerator?.takeIf { it.isFinite() } ?: return 0f
    val resolvedDenominator = denominator?.takeIf { it.isFinite() } ?: return 0f
    if (resolvedDenominator <= 0.0) return 0f
    return (resolvedNumerator / resolvedDenominator).toFloat().coerceIn(0f, 1f)
}

private data class StockSubcardValueText(
    val currentText: String,
    val previewText: String?,
)

private fun stockSubcardValueText(
    numerator: Double?,
    denominator: Double?,
    mutationPreviewNumerator: Double?,
): StockSubcardValueText {
    return if (mutationPreviewNumerator?.isFinite() == true) {
        StockSubcardValueText(
            currentText = formatStockSubcardCount(numerator),
            previewText = compactStockValueText(
                numerator = mutationPreviewNumerator,
                denominator = denominator,
            ),
        )
    } else {
        StockSubcardValueText(
            currentText = compactStockValueText(
                numerator = numerator,
                denominator = denominator,
            ),
            previewText = null,
        )
    }
}

internal fun compactStockValueText(
    numerator: Double?,
    denominator: Double?,
): String {
    val numeratorText = formatStockSubcardCount(numerator)
    if (denominator == null || !denominator.isFinite() || denominator <= 0.0) {
        return numeratorText
    }
    return "$numeratorText / ${formatStockSubcardCount(denominator)}"
}

private fun formatStockSubcardCount(value: Double?): String {
    val resolved = value?.takeIf { it.isFinite() } ?: return "-"
    return NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(resolved)
}

private data class StockSubcardMutationPreview(
    val unitsRemainingAfter: Double?,
    val openContainerAmountAfter: Double?,
    val afterStock: MedicineStock,
)

private fun stockMutationPreview(
    medicine: Medicine,
    requestedDose: Double?,
): StockSubcardMutationPreview? {
    val dose = requestedDose?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    val stock = medicine.stock
    if (!stock.trackingEnabled) return null
    if (medicine.preparation is MedicinePreparation.PatchOff) return null

    // Mirror the exact deduction the write will apply (carry-over, sealed crack,
    // and eager promotion of an emptied container) so the preview never diverges
    // from the committed stock.
    val after = deductInsertedDoseStock(
        preparation = medicine.preparation,
        stock = stock,
        requestedDose = dose,
    )
    return StockSubcardMutationPreview(
        unitsRemainingAfter = after.unitsRemaining,
        openContainerAmountAfter = after.openContainerAmount,
        afterStock = after,
    )
}

@Preview(
    name = "Medication Stock Subcard",
    showBackground = true,
    widthDp = 420
)
@Composable
private fun MedicationStockSubcardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MedicationStockSubcard(
                projection = stockSubcardPreviewProjection(
                    preparation = MedicinePreparation.GelContainer(
                        concentrationPercent = 0.06,
                        containerWeightGrams = 80.0,
                    ),
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 3.0,
                        unitsLastTotal = 4.0,
                        openContainerAmount = 41.26,
                    ),
                    totalStockUnits = 281.26,
                    runwayDays = 111,
                    state = MedicineStockState.HEALTHY,
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
            MedicationStockSubcard(
                projection = stockSubcardPreviewProjection(
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 84.0,
                        unitsLastTotal = 84.0,
                    ),
                    totalStockUnits = 84.0,
                    runwayDays = null,
                    state = MedicineStockState.NO_RUNWAY,
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
        }
    }
}

@Preview(
    name = "Medication Stock Subcard Alert States",
    showBackground = true,
    widthDp = 420
)
@Composable
private fun MedicationStockSubcardAlertStatesPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MedicationStockSubcard(
                projection = stockSubcardPreviewProjection(
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 8.0,
                        unitsLastTotal = 84.0,
                    ),
                    totalStockUnits = 8.0,
                    runwayDays = 21,
                    state = MedicineStockState.USER_LOW,
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
            MedicationStockSubcard(
                projection = stockSubcardPreviewProjection(
                    preparation = MedicinePreparation.GelContainer(
                        concentrationPercent = 0.06,
                        containerWeightGrams = 80.0,
                    ),
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 0.0,
                        unitsLastTotal = 2.0,
                        openContainerAmount = 4.0,
                    ),
                    totalStockUnits = 4.0,
                    runwayDays = 3,
                    state = MedicineStockState.IMMINENT,
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
            MedicationStockSubcard(
                projection = stockSubcardPreviewProjection(
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 0.0,
                        unitsLastTotal = 84.0,
                    ),
                    totalStockUnits = 0.0,
                    runwayDays = 0,
                    state = MedicineStockState.OUT,
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
        }
    }
}

private fun stockSubcardPreviewProjection(
    preparation: MedicinePreparation,
    stock: MedicineStock,
    totalStockUnits: Double,
    runwayDays: Int?,
    state: MedicineStockState,
): MedicineStockProjection {
    val medicine = stockSubcardPreviewMedicine(preparation = preparation, stock = stock)
    return MedicineStockProjection(
        medicine = medicine,
        dosesPerDayMagnitude = 1.0,
        totalStockUnits = totalStockUnits,
        runway = if (runwayDays == null) {
            RunwayProjection.NoSchedule
        } else {
            RunwayProjection.Days(
                days = runwayDays,
                lastFulfillable = java.time.LocalDate.of(2026, 5, 27).plusDays(runwayDays.toLong()),
            )
        },
        intervalDays = null,
        maxPerAdministration = 1.0,
        state = state,
    )
}

private fun stockSubcardPreviewMedicine(
    preparation: MedicinePreparation,
    stock: MedicineStock,
): Medicine {
    val medicationKey = when (preparation) {
        is MedicinePreparation.GelContainer -> MedicationKey.ESTRADIOL_GEL
        else -> MedicationKey.ESTRADIOL
    }
    return Medicine(
        uuid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000101"),
        selection = MedicineSelection.Catalog(medicationKey),
        category = medicationKey.category,
        preparation = preparation,
        displayName = null,
        identityKey = MedicineIdentityKey.catalog(medicationKey, preparation),
        createdAt = java.time.Instant.EPOCH,
        updatedAt = java.time.Instant.EPOCH,
        archivedAt = null,
        stock = stock,
    )
}
