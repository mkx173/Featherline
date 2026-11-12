package com.mkx.hrttracker.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

class LegacySnoozeCleanupTest {
    @Test
    fun eachV142SnoozeTime_isOneAlarm_forEveryRecordThatSharedIt() {
        val a = "aaaaaaaa-0000-4000-8000-000000000001|11111111-0000-4000-8000-000000000001|2026-04-20T09:00"
        val b = "bbbbbbbb-0000-4000-8000-000000000002|22222222-0000-4000-8000-000000000002|2026-04-20T09:00"
        val records = setOf(
            "$a\u001F2026-04-20T09:15\u001F1",
            "$b\u001F2026-04-20T09:15\u001F2",
            "$a\u001F2026-04-20T09:45\u001F3",
            // An unreleased build's four-field record names no v1.4.2 alarm.
            "$b\u001F1776676500000\u001F1\u001Ftrue",
        )

        val alarms = legacySnoozeAlarmData(records)

        // As v1.4.2's snoozeIntentData built them: the sorted slots of the records at that time.
        fun uuidOf(vararg slots: String) =
            UUID.nameUUIDFromBytes(slots.sorted().joinToString(";").toByteArray(StandardCharsets.UTF_8))
        assertEquals(
            setOf(
                "hrttracker://medication-reminder-snooze/2026-04-20T09:15/${uuidOf(b, a)}",
                "hrttracker://medication-reminder-snooze/2026-04-20T09:45/${uuidOf(a)}",
            ),
            alarms.toSet(),
        )
    }
}
