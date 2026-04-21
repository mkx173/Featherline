package com.mkx.hrttracker.ui.main

internal enum class MainTodayRowTone {
    DEFAULT,
    DUE_SOON,
    OVERDUE,
    DONE,
}

internal fun mainTodayRowTone(status: MainTodayDoseStatus): MainTodayRowTone {
    return when (status) {
        MainTodayDoseStatus.DONE -> MainTodayRowTone.DONE
        MainTodayDoseStatus.DUE_SOON -> MainTodayRowTone.DUE_SOON
        MainTodayDoseStatus.UPCOMING -> MainTodayRowTone.DEFAULT
        MainTodayDoseStatus.OVERDUE -> MainTodayRowTone.OVERDUE
    }
}
