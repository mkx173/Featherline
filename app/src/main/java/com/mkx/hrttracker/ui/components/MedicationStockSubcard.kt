package com.mkx.hrttracker.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
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
) {
    STOCK_POOL(
        iconRes = R.drawable.ic_inventory_2,
        contentDescriptionRes = R.string.stock_subcard_cd_stock_pool,
    ),
    OPEN_VIAL(
        iconRes = R.drawable.ic_humidity_mid,
        contentDescriptionRes = R.string.stock_subcard_cd_open_vial,
    ),
    OPEN_CONTAINER(
        iconRes = R.drawable.ic_humidity_mid,
        contentDescriptionRes = R.string.stock_subcard_cd_open_container,
    ),
    SEALED_VIALS(
        iconRes = R.drawable.ic_inventory_2,
        contentDescriptionRes = R.string.stock_subcard_cd_sealed_vials,
    ),
    SEALED_CONTAINERS(
        iconRes = R.drawable.ic_inventory_2,
        contentDescriptionRes = R.string.stock_subcard_cd_sealed_containers,
    ),
}

internal data class MedicationStockSubcardText(
    @param:StringRes val resId: Int,
    val intArg: Int? = null,
)

internal data class MedicationStockSubcardRowModel(
    val kind: MedicationStockSubcardRowKind,
    val valueText: String,
    val progress: Float,
) {
    @get:DrawableRes
    val iconRes: Int get() = kind.iconRes

    @get:StringRes
    val contentDescriptionRes: Int get() = kind.contentDescriptionRes
}

internal data class MedicationStockSubcardModel(
    @param:StringRes val chipLabelRes: Int,
    val tone: MedicationStockSubcardTone,
    val runwayText: MedicationStockSubcardText,
    val rows: List<MedicationStockSubcardRowModel>,
)

internal fun medicationStockSubcardModel(
    projection: MedicineStockProjection?,
): MedicationStockSubcardModel? {
    projection ?: return null
    val medicine = projection.medicine
    val stock = medicine.stock
    if (!stock.trackingEnabled || projection.state == MedicineStockState.UNTRACKED) return null
    if (medicine.preparation is MedicinePreparation.PatchOff) return null

    val rows = stockSubcardRows(projection)
    if (rows.isEmpty()) return null

    return MedicationStockSubcardModel(
        chipLabelRes = stockSubcardChipLabelRes(projection.state),
        tone = stockSubcardTone(projection.state),
        runwayText = stockSubcardRunwayText(projection.runway),
        rows = rows,
    )
}

private fun stockSubcardRows(
    projection: MedicineStockProjection,
): List<MedicationStockSubcardRowModel> {
    val stock = projection.medicine.stock
    return when (val preparation = projection.medicine.preparation) {
        is MedicinePreparation.InjectionMultiUseVial -> buildList {
            stock.openContainerAmount?.let { openAmount ->
                add(
                    MedicationStockSubcardRowModel(
                        kind = MedicationStockSubcardRowKind.OPEN_VIAL,
                        valueText = compactStockValueText(
                            numerator = openAmount,
                            denominator = preparation.vialVolumeMl,
                            suffix = " mL",
                        ),
                        progress = stockSubcardProgress(
                            numerator = openAmount,
                            denominator = preparation.vialVolumeMl,
                        ),
                    ),
                )
            }
            add(
                MedicationStockSubcardRowModel(
                    kind = MedicationStockSubcardRowKind.SEALED_VIALS,
                    valueText = compactStockValueText(
                        numerator = stock.unitsRemaining,
                        denominator = stock.unitsLastTotal,
                    ),
                    progress = stockSubcardProgress(
                        numerator = stock.unitsRemaining,
                        denominator = stock.unitsLastTotal,
                    ),
                ),
            )
        }

        is MedicinePreparation.GelContainer -> buildList {
            stock.openContainerAmount?.let { openAmount ->
                add(
                    MedicationStockSubcardRowModel(
                        kind = MedicationStockSubcardRowKind.OPEN_CONTAINER,
                        valueText = compactStockValueText(
                            numerator = openAmount,
                            denominator = preparation.containerWeightGrams,
                            suffix = " g",
                        ),
                        progress = stockSubcardProgress(
                            numerator = openAmount,
                            denominator = preparation.containerWeightGrams,
                        ),
                    ),
                )
            }
            add(
                MedicationStockSubcardRowModel(
                    kind = MedicationStockSubcardRowKind.SEALED_CONTAINERS,
                    valueText = compactStockValueText(
                        numerator = stock.unitsRemaining,
                        denominator = stock.unitsLastTotal,
                    ),
                    progress = stockSubcardProgress(
                        numerator = stock.unitsRemaining,
                        denominator = stock.unitsLastTotal,
                    ),
                ),
            )
        }

        is MedicinePreparation.PatchOff -> emptyList()

        else -> listOf(
            MedicationStockSubcardRowModel(
                kind = MedicationStockSubcardRowKind.STOCK_POOL,
                valueText = compactStockValueText(
                    numerator = stock.unitsRemaining,
                    denominator = stock.unitsLastTotal,
                ),
                progress = stockSubcardProgress(
                    numerator = stock.unitsRemaining,
                    denominator = stock.unitsLastTotal,
                ),
            ),
        )
    }
}

@StringRes
private fun stockSubcardChipLabelRes(state: MedicineStockState): Int {
    return when (state) {
        MedicineStockState.HEALTHY -> R.string.stock_subcard_chip_in_stock
        MedicineStockState.USER_LOW -> R.string.stock_subcard_chip_low
        MedicineStockState.IMMINENT -> R.string.stock_subcard_chip_almost_out
        MedicineStockState.OUT -> R.string.stock_subcard_chip_out
        MedicineStockState.NO_RUNWAY -> R.string.stock_subcard_chip_unknown
        MedicineStockState.UNTRACKED -> R.string.stock_subcard_chip_unknown
    }
}

private fun stockSubcardTone(state: MedicineStockState): MedicationStockSubcardTone {
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

private fun stockSubcardRunwayText(
    runway: RunwayProjection,
): MedicationStockSubcardText {
    return when (runway) {
        is RunwayProjection.Days -> MedicationStockSubcardText(
            resId = R.string.stock_subcard_runway_days,
            intArg = runway.days,
        )
        RunwayProjection.BeyondHorizon -> MedicationStockSubcardText(
            resId = R.string.stock_subcard_runway_plenty,
        )
        RunwayProjection.NoSchedule -> MedicationStockSubcardText(
            resId = R.string.stock_subcard_runway_unknown,
        )
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

internal fun compactStockValueText(
    numerator: Double?,
    denominator: Double?,
    suffix: String = "",
): String {
    val numeratorText = formatStockSubcardCount(numerator)
    if (denominator == null || !denominator.isFinite() || denominator <= 0.0) {
        return "$numeratorText$suffix"
    }
    return "$numeratorText / ${formatStockSubcardCount(denominator)}$suffix"
}

private fun formatStockSubcardCount(value: Double?): String {
    val resolved = value?.takeIf { it.isFinite() } ?: return "-"
    return NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(resolved)
}
