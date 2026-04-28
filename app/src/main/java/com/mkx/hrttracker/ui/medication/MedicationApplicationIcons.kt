package com.mkx.hrttracker.ui.medication

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType

@Composable
internal fun rememberMedicationApplicationIcons(): Map<MedicationApplicationType, ImageVector> {
    val pillIcon = ImageVector.vectorResource(medicationApplicationIconRes(MedicationApplicationType.ORAL))
    val injectionIcon = ImageVector.vectorResource(medicationApplicationIconRes(MedicationApplicationType.INJECTION))
    val gelIcon = ImageVector.vectorResource(medicationApplicationIconRes(MedicationApplicationType.GEL))
    val patchOnIcon = ImageVector.vectorResource(medicationApplicationIconRes(MedicationApplicationType.PATCH_ON))
    val patchOffIcon = ImageVector.vectorResource(medicationApplicationIconRes(MedicationApplicationType.PATCH_OFF))
    return remember(pillIcon, injectionIcon, gelIcon, patchOnIcon, patchOffIcon) {
        mapOf(
            MedicationApplicationType.ORAL to pillIcon,
            MedicationApplicationType.SUBLINGUAL to pillIcon,
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
    scheduleIconSize: Dp = 9.dp,
) {
    val applicationTypeIcon = rememberMedicationApplicationIcons().getValue(applicationType)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = applicationTypeIcon,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize()
        )
        if (applicationType == MedicationApplicationType.SUBLINGUAL) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(scheduleIconSize)
            )
        }
    }
}

@DrawableRes
internal fun medicationApplicationIconRes(applicationType: MedicationApplicationType): Int {
    return when (applicationType) {
        MedicationApplicationType.ORAL -> R.drawable.ic_pill
        MedicationApplicationType.SUBLINGUAL -> R.drawable.ic_pill
        MedicationApplicationType.INJECTION -> R.drawable.ic_syringe
        MedicationApplicationType.GEL -> R.drawable.ic_water_drops
        MedicationApplicationType.PATCH_ON -> R.drawable.ic_sticker_add
        MedicationApplicationType.PATCH_OFF -> R.drawable.ic_tab_close_inactive
    }
}
