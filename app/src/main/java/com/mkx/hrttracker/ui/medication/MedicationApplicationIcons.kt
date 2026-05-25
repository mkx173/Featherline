package com.mkx.hrttracker.ui.medication

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationForm
import com.mkx.hrttracker.model.medication.form

@Composable
internal fun rememberMedicationApplicationIcons(
    outlined: Boolean = false
): Map<MedicationApplicationType, ImageVector> {
    val iconRes: (MedicationApplicationType) -> Int = if (outlined) {
        ::medicationApplicationOutlinedIconRes
    } else {
        ::medicationApplicationIconRes
    }
    val pillIcon = ImageVector.vectorResource(iconRes(MedicationApplicationType.ORAL))
    val sublingualIcon = ImageVector.vectorResource(iconRes(MedicationApplicationType.SUBLINGUAL))
    val injectionIcon = ImageVector.vectorResource(iconRes(MedicationApplicationType.INJECTION))
    val gelIcon = ImageVector.vectorResource(iconRes(MedicationApplicationType.GEL))
    val patchOnIcon = ImageVector.vectorResource(iconRes(MedicationApplicationType.PATCH_ON))
    val patchOffIcon = ImageVector.vectorResource(iconRes(MedicationApplicationType.PATCH_OFF))
    return remember(pillIcon, sublingualIcon, injectionIcon, gelIcon, patchOnIcon, patchOffIcon) {
        mapOf(
            MedicationApplicationType.ORAL to pillIcon,
            MedicationApplicationType.SUBLINGUAL to sublingualIcon,
            MedicationApplicationType.INJECTION to injectionIcon,
            MedicationApplicationType.GEL to gelIcon,
            MedicationApplicationType.PATCH_ON to patchOnIcon,
            MedicationApplicationType.PATCH_OFF to patchOffIcon,
        )
    }
}

@Composable
internal fun MedicationApplicationIcon(
    applicationType: MedicationApplicationType,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
) {
    val applicationTypeIcon = rememberMedicationApplicationIcons(outlined = outlined)
        .getValue(applicationType)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = applicationTypeIcon,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@DrawableRes
internal fun medicationApplicationIconRes(applicationType: MedicationApplicationType): Int {
    return when (applicationType) {
        MedicationApplicationType.ORAL -> R.drawable.ic_oral
        MedicationApplicationType.SUBLINGUAL -> R.drawable.ic_sublingual
        MedicationApplicationType.INJECTION -> R.drawable.ic_syringe
        MedicationApplicationType.GEL -> R.drawable.ic_water_drops
        MedicationApplicationType.PATCH_ON -> R.drawable.ic_sticker_add
        MedicationApplicationType.PATCH_OFF -> R.drawable.ic_tab_close_inactive
    }
}

// Preparation-form icons describe the physical medicine (tablet, capsule, …)
// rather than the per-dose route. Medicine-identity surfaces (the medicine
// manager card, the medicine detail header, the editor summary) use these
// instead of route icons so the glyph stays stable across oral/sublingual
// usage of the same tablet.
@DrawableRes
internal fun medicinePreparationFormIconRes(form: MedicinePreparationForm): Int {
    return when (form) {
        MedicinePreparationForm.TABLET -> R.drawable.ic_control_point_duplicate
        MedicinePreparationForm.CAPSULE -> R.drawable.ic_pill
        MedicinePreparationForm.INJECTION -> R.drawable.ic_syringe
        MedicinePreparationForm.GEL -> R.drawable.ic_water_drops
        // Preparation form is the medicine identity, not the "applying"
        // action — the plus-decorated sticker is reserved for PATCH_ON.
        MedicinePreparationForm.PATCH -> R.drawable.ic_sticker
    }
}

// Preparation-keyed glyph used by the medicine card's leading icon. Mirrors
// medicinePreparationFormIconRes for ordinary preparations and routes the
// PATCH_OFF singleton to its own close-tab glyph so the medicine manager
// distinguishes it from a real patch medicine.
@DrawableRes
internal fun medicinePreparationIconRes(preparation: MedicinePreparation): Int {
    return when (preparation) {
        is MedicinePreparation.PatchOff -> R.drawable.ic_tab_close_inactive
        else -> medicinePreparationFormIconRes(preparation.type.form())
    }
}

@DrawableRes
internal fun medicationApplicationOutlinedIconRes(applicationType: MedicationApplicationType): Int {
    return when (applicationType) {
        MedicationApplicationType.ORAL -> R.drawable.ic_oral_alt
        MedicationApplicationType.SUBLINGUAL -> R.drawable.ic_sublingual_alt
        MedicationApplicationType.INJECTION -> R.drawable.ic_syringe_alt
        MedicationApplicationType.GEL -> R.drawable.ic_water_drops_alt
        MedicationApplicationType.PATCH_ON -> R.drawable.ic_sticker_add_alt
        MedicationApplicationType.PATCH_OFF -> R.drawable.ic_tab_close_inactive_alt
    }
}
