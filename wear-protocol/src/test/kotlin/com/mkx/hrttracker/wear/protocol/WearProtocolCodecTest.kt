package com.mkx.hrttracker.wear.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WearProtocolCodecTest {
    @Test
    fun snapshot_roundTripsEveryField() {
        val snapshot = WearDoseSnapshot(
            generatedAtEpochMillis = 1234L,
            zoneId = "Asia/Shanghai",
            anchorDateEpochDay = 20_000L,
            doneCount = 1,
            totalCount = 2,
            hideMedicationDetails = false,
            appLanguageTag = "zh-Hans",
            rows = listOf(
                WearDoseRow(
                    medicationName = "Estradiol",
                    groupName = "Evening",
                    routeLabel = "Oral",
                    doseText = "2 mg",
                    status = WearDoseStatus.DUE_SOON,
                    scheduledAt = "2026-07-26T21:00",
                    trailingText = "21:00",
                    groupUuid = "group",
                    scheduleTimeUuid = "slot",
                )
            ),
            estradiol = WearEstradiolSnapshot(
                currentValueText = "182",
                unitLabel = "pg/mL",
                samples = listOf(110.0, 140.0, 182.0),
                sampleIntervalMinutes = 120,
            ),
            recentDose = WearRecentDose(
                groupName = "Morning",
                medicationSummary = "Estradiol · 2 mg · Oral",
                recordedAt = "2026-07-26T08:03",
                entryUuids = listOf("entry-a", "entry-b"),
            ),
        )

        assertEquals(
            snapshot,
            WearProtocolCodec.decodeSnapshot(WearProtocolCodec.encodeSnapshot(snapshot)),
        )
    }

    @Test
    fun logCommand_roundTripsEveryField() {
        val command = WearLogDoseCommand(
            requestId = "request",
            groupUuid = "group",
            scheduleTimeUuid = null,
            scheduledAt = "2026-07-26T21:00",
        )

        assertEquals(
            command,
            WearProtocolCodec.decodeLogDoseCommand(
                WearProtocolCodec.encodeLogDoseCommand(command)
            ),
        )
    }

    @Test
    fun undoCommand_roundTripsEveryEntryUuid() {
        val command = WearUndoDoseCommand(
            requestId = "undo-request",
            entryUuids = listOf("entry-a", "entry-b"),
        )

        assertEquals(
            command,
            WearProtocolCodec.decodeUndoDoseCommand(
                WearProtocolCodec.encodeUndoDoseCommand(command)
            ),
        )
    }

    @Test
    fun snapshot_rejectsTrailingData() {
        val payload = WearProtocolCodec.encodeSnapshot(
            WearDoseSnapshot(
                generatedAtEpochMillis = 1L,
                zoneId = "UTC",
                anchorDateEpochDay = 1L,
                doneCount = 0,
                totalCount = 0,
                hideMedicationDetails = true,
                appLanguageTag = "en",
                rows = emptyList(),
            )
        ) + byteArrayOf(1)

        assertThrows(IllegalArgumentException::class.java) {
            WearProtocolCodec.decodeSnapshot(payload)
        }
    }
}
