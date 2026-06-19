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
    AnchorIcon.BLOODTYPE -> R.string.journal_anchor_icon_bloodtype
    AnchorIcon.LABS -> R.string.journal_anchor_icon_labs
    AnchorIcon.MONITOR_WEIGHT -> R.string.journal_anchor_icon_monitor_weight
    AnchorIcon.SCHEDULE -> R.string.journal_anchor_icon_schedule
    AnchorIcon.FLAG -> R.string.journal_anchor_icon_flag
    AnchorIcon.BOOKMARK -> R.string.journal_anchor_icon_bookmark
    AnchorIcon.HOME_HEALTH -> R.string.journal_anchor_icon_home_health
    AnchorIcon.FAVORITE -> R.string.journal_anchor_icon_favorite
}
