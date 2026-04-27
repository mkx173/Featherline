package com.mkx.hrttracker.ui.medication

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType

@Composable
internal fun rememberMedicationApplicationIcons(): Map<MedicationApplicationType, ImageVector> {
    val pillIcon = ImageVector.vectorResource(medicationApplicationIconRes(MedicationApplicationType.ORAL))
    val pillAltIcon = ImageVector.vectorResource(medicationApplicationIconRes(MedicationApplicationType.SUBLINGUAL))
    val injectionIcon = ImageVector.vectorResource(medicationApplicationIconRes(MedicationApplicationType.INJECTION))
    val gelIcon = ImageVector.vectorResource(medicationApplicationIconRes(MedicationApplicationType.GEL))
    val patchOnIcon = ImageVector.vectorResource(medicationApplicationIconRes(MedicationApplicationType.PATCH_ON))
    val patchOffIcon = ImageVector.vectorResource(medicationApplicationIconRes(MedicationApplicationType.PATCH_OFF))
    return remember(pillIcon, pillAltIcon, injectionIcon, gelIcon, patchOnIcon, patchOffIcon) {
        mapOf(
            MedicationApplicationType.ORAL to pillIcon,
            MedicationApplicationType.SUBLINGUAL to pillAltIcon,
            MedicationApplicationType.INJECTION to injectionIcon,
            MedicationApplicationType.GEL to gelIcon,
            MedicationApplicationType.PATCH_ON to patchOnIcon,
            MedicationApplicationType.PATCH_OFF to patchOffIcon,
        )
    }
}

@DrawableRes
internal fun medicationApplicationIconRes(applicationType: MedicationApplicationType): Int {
    return when (applicationType) {
        MedicationApplicationType.ORAL -> R.drawable.ic_pill
        MedicationApplicationType.SUBLINGUAL -> R.drawable.ic_pill_alt
        MedicationApplicationType.INJECTION -> R.drawable.ic_syringe
        MedicationApplicationType.GEL -> R.drawable.ic_water_drops
        MedicationApplicationType.PATCH_ON -> R.drawable.ic_sticker_add
        MedicationApplicationType.PATCH_OFF -> R.drawable.ic_tab_close_inactive
    }
}
