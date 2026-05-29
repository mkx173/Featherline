package com.mkx.hrttracker.ui.components

import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicinePreparation

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
