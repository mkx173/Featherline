package com.mkx.hrttracker.ui.journal

import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon

@StringRes
fun anchorIconLabelRes(icon: AnchorIcon): Int = when (icon) {
    AnchorIcon.EVENT -> R.string.journal_anchor_icon_event
    AnchorIcon.MEDICATION -> R.string.journal_anchor_icon_medication
    AnchorIcon.PILL -> R.string.journal_anchor_icon_pill
    AnchorIcon.VACCINES -> R.string.journal_anchor_icon_vaccines
    AnchorIcon.WATER_DROPS -> R.string.journal_anchor_icon_water_drops
    AnchorIcon.LABS -> R.string.journal_anchor_icon_labs
    AnchorIcon.MONITOR_WEIGHT -> R.string.journal_anchor_icon_monitor_weight
    AnchorIcon.MIC -> R.string.journal_anchor_icon_mic
    AnchorIcon.FLAG -> R.string.journal_anchor_icon_flag
    AnchorIcon.PASSPORT -> R.string.journal_anchor_icon_passport
    AnchorIcon.HOME_HEALTH -> R.string.journal_anchor_icon_home_health
    AnchorIcon.FAVORITE -> R.string.journal_anchor_icon_favorite
    AnchorIcon.STETHOSCOPE -> R.string.journal_anchor_icon_stethoscope
    AnchorIcon.TRAVEL -> R.string.journal_anchor_icon_travel
    AnchorIcon.CAKE -> R.string.journal_anchor_icon_cake
    AnchorIcon.STAR -> R.string.journal_anchor_icon_star
}
