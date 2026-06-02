package com.mkx.hrttracker.ui.components

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicinePreparation
import java.text.NumberFormat

@PluralsRes
internal fun stockInventoryCountPluralRes(preparation: MedicinePreparation): Int? = when (preparation) {
    is MedicinePreparation.Pill -> R.plurals.stock_count_tablets
    is MedicinePreparation.Capsule -> R.plurals.stock_count_capsules
    is MedicinePreparation.Patch -> R.plurals.stock_count_patches
    is MedicinePreparation.GelSachet -> R.plurals.stock_count_sachets
    is MedicinePreparation.InjectionSingleUseVial -> R.plurals.stock_count_vials
    is MedicinePreparation.InjectionMultiUseVial -> R.plurals.stock_count_vials
    is MedicinePreparation.GelContainer -> R.plurals.stock_count_containers
    is MedicinePreparation.PatchOff -> null
}

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
    val pluralRes = stockInventoryCountPluralRes(preparation) ?: return null
    // Only "one" (count == 1) vs "other" is distinguished in the supported
    // locales (en, zh-Hans); fractional amounts select "other".
    val quantity = if (count == 1.0) 1 else 2
    val formattedCount = NumberFormat.getInstance().apply {
        maximumFractionDigits = 2
    }.format(count)
    return context.resources.getQuantityString(pluralRes, quantity, formattedCount)
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
