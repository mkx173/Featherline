package com.mkx.hrttracker.ui.components

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.util.currentAppLocale
import java.text.NumberFormat

@PluralsRes
internal fun stockUnitNounPluralRes(preparation: MedicinePreparation): Int? = when (preparation) {
    is MedicinePreparation.Pill -> R.plurals.stock_count_tablets
    is MedicinePreparation.Capsule -> R.plurals.stock_count_capsules
    is MedicinePreparation.Patch -> R.plurals.stock_count_patches
    is MedicinePreparation.GelSachet -> R.plurals.stock_count_sachets
    is MedicinePreparation.InjectionSingleUseVial -> R.plurals.stock_count_vials
    is MedicinePreparation.InjectionMultiUseVial -> R.plurals.stock_count_vials
    is MedicinePreparation.GelContainer -> R.plurals.stock_count_containers
    is MedicinePreparation.PatchOff -> null
}

@PluralsRes
internal fun stockUnitNounPluralForUnitRes(@StringRes unitRes: Int): Int? = when (unitRes) {
    R.string.stock_unit_tablets -> R.plurals.stock_count_tablets
    R.string.stock_unit_capsules -> R.plurals.stock_count_capsules
    R.string.stock_unit_patches -> R.plurals.stock_count_patches
    R.string.stock_unit_sachets -> R.plurals.stock_count_sachets
    R.string.stock_unit_vials -> R.plurals.stock_count_vials
    R.string.stock_unit_containers -> R.plurals.stock_count_containers
    else -> null
}

internal fun stockCountPluralQuantity(count: Double): Int = if (count == 1.0) 1 else 2

/**
 * Localized "count + unit" phrase for an inventory amount (e.g. "2 tablets",
 * "1 vial"). The count is formatted with the active locale's number format
 * (correct decimal separator, trailing zeros trimmed); the noun is pluralized
 * via Android plurals. Returns null for preparations without an inventory unit.
 */
internal fun stockInventoryCountText(
    context: Context,
    preparation: MedicinePreparation,
    count: Double,
): String? {
    val pluralRes = stockUnitNounPluralRes(preparation) ?: return null
    val noun = context.resources.getQuantityString(
        pluralRes,
        stockCountPluralQuantity(count),
    )
    val formattedCount = NumberFormat.getNumberInstance(context.currentAppLocale()).apply {
        maximumFractionDigits = 2
    }.format(count)
    return context.getString(R.string.stock_row_count_with_unit, formattedCount, noun)
}

@StringRes
internal fun stockInventoryUnitRes(preparation: MedicinePreparation): Int? = when (preparation) {
    is MedicinePreparation.Pill -> R.string.stock_unit_tablets
    is MedicinePreparation.Capsule -> R.string.stock_unit_capsules
    is MedicinePreparation.Patch -> R.string.stock_unit_patches
    is MedicinePreparation.GelSachet -> R.string.stock_unit_sachets
    is MedicinePreparation.InjectionSingleUseVial -> R.string.stock_unit_vials
    is MedicinePreparation.InjectionMultiUseVial -> R.string.stock_unit_vials
    is MedicinePreparation.GelContainer -> R.string.stock_unit_containers
    is MedicinePreparation.PatchOff -> null
}

@StringRes
internal fun stockRateUnitRes(preparation: MedicinePreparation): Int? = when (preparation) {
    is MedicinePreparation.Pill -> R.string.stock_unit_tablets
    is MedicinePreparation.Capsule -> R.string.stock_unit_capsules
    is MedicinePreparation.Patch -> R.string.stock_unit_patches
    is MedicinePreparation.GelSachet -> R.string.stock_unit_sachets
    is MedicinePreparation.InjectionSingleUseVial -> R.string.stock_unit_vials
    is MedicinePreparation.InjectionMultiUseVial -> R.string.stock_unit_ml
    is MedicinePreparation.GelContainer -> R.string.stock_unit_g
    is MedicinePreparation.PatchOff -> null
}
