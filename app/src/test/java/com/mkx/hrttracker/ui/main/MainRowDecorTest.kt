package com.mkx.hrttracker.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class MainRowDecorTest {
    @Test
    fun mainTodayRowTone_matches_today_status() {
        assertEquals(MainTodayRowTone.DONE, mainTodayRowTone(MainTodayDoseStatus.DONE))
        assertEquals(MainTodayRowTone.DUE_SOON, mainTodayRowTone(MainTodayDoseStatus.DUE_SOON))
        assertEquals(MainTodayRowTone.DEFAULT, mainTodayRowTone(MainTodayDoseStatus.UPCOMING))
        assertEquals(MainTodayRowTone.OVERDUE, mainTodayRowTone(MainTodayDoseStatus.OVERDUE))
    }
}
