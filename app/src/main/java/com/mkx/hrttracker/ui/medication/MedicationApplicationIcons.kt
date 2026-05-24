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
