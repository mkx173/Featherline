package com.mkx.hrttracker.widget

import android.content.Context
import android.content.Intent
import com.mkx.hrttracker.MainActivity

// Set on a MainActivity launch intent to route to the "Since you started" (Milestones)
// screen. MainActivity parses it into MainViewModel.requestMilestonesDeepLink(); the nav
// host consumes the resulting signal. Shared by the pinned shortcut and the configured
// widget card so both land on the same screen.
const val EXTRA_OPEN_MILESTONES = "open_milestones"

fun anchorOpenMilestonesIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_OPEN_MILESTONES, true)
    }
