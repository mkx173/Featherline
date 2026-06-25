package com.mkx.hrttracker.ui.journal

import androidx.annotation.DrawableRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon

@DrawableRes
fun anchorIconRes(icon: AnchorIcon): Int = when (icon) {
    AnchorIcon.EVENT -> R.drawable.ic_event
    AnchorIcon.MEDICATION -> R.drawable.ic_medication
    AnchorIcon.PILL -> R.drawable.ic_pill
    AnchorIcon.VACCINES -> R.drawable.ic_vaccines
    AnchorIcon.WATER_DROPS -> R.drawable.ic_water_drops
    AnchorIcon.LABS -> R.drawable.ic_labs
    AnchorIcon.MONITOR_WEIGHT -> R.drawable.ic_monitor_weight
    AnchorIcon.MIC -> R.drawable.ic_mic
    AnchorIcon.FLAG -> R.drawable.ic_flag
    AnchorIcon.PASSPORT -> R.drawable.ic_passport
    AnchorIcon.HOME_HEALTH -> R.drawable.ic_local_hospital
    AnchorIcon.FAVORITE -> R.drawable.ic_favorite
    AnchorIcon.STETHOSCOPE -> R.drawable.ic_stethoscope
    AnchorIcon.TRAVEL -> R.drawable.ic_travel
    AnchorIcon.CAKE -> R.drawable.ic_cake
    AnchorIcon.STAR -> R.drawable.ic_kid_star
}
