package com.mkx.hrttracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroBackgroundBackupRoundTripTest {
    private fun snapshot(heroBackgroundKey: String?) = BackupTrackedDateSnapshot(
        uuid = "00000000-0000-0000-0000-000000000001",
        name = "Started E",
        iconKey = "medication",
        dateIso = "2024-01-01",
        paletteKey = null,
        heroBackgroundKey = heroBackgroundKey,
        pinnedOrder = 0,
        createdAtEpochMillis = 1000L,
        updatedAtEpochMillis = 1000L,
    )

    @Test
    fun snapshotVersionWasBumpedForTheNewField() {
        assertEquals(6, CURRENT_BACKUP_SNAPSHOT_VERSION)
    }

    @Test
    fun heroBackgroundKey_survivesJsonRoundTrip() {
        val json = BackupSnapshotJsonCodec.encode(
            baselineSnapshot(trackedDates = listOf(snapshot("TRANSGENDER")))
        )
        val decoded = BackupSnapshotJsonCodec.decode(json)!!
        assertEquals("TRANSGENDER", decoded.trackedDates.single().heroBackgroundKey)
    }

    @Test
    fun missingHeroBackgroundKey_decodesToNull_forOlderBackups() {
        val v5Json = """
            {
              "snapshotVersion": 5,
              "exportedAtEpochMillis": 0,
              "app": { "packageName": "com.mkx.hrttracker" },
              "settings": {
                "darkModeOption": "FOLLOW_SYSTEM",
                "adaptiveColorEnabled": false,
                "remindersEnabled": false,
                "appLockGracePeriodOption": "IMMEDIATE",
                "hideScreenContentEnabled": false,
                "onboardingCompleted": true,
                "appLanguageOption": "FOLLOW_SYSTEM",
                "calibrationDefaultUnits": {}
              },
              "userProfile": {
                "weightKg": null,
                "weightOriginalValue": null,
                "weightOriginalUnit": "KILOGRAMS"
              },
              "medicines": [],
              "medicationGroups": [],
              "medicationLogs": [],
              "customBloodAnalytes": [],
              "bloodTestPanels": [],
              "trackedDates": [
                {
                  "uuid": "00000000-0000-0000-0000-000000000001",
                  "name": "Started E",
                  "iconKey": "medication",
                  "dateIso": "2024-01-01",
                  "paletteKey": null,
                  "pinnedOrder": 0,
                  "createdAtEpochMillis": 1000,
                  "updatedAtEpochMillis": 1000
                }
              ]
            }
        """.trimIndent()
        assertNull(BackupSnapshotJsonCodec.decode(v5Json)!!.trackedDates.single().heroBackgroundKey)
    }

    private fun baselineSnapshot(
        trackedDates: List<BackupTrackedDateSnapshot> = emptyList(),
    ): BackupSnapshot {
        return BackupSnapshot(
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = BackupSettingsSnapshot(
                darkModeOption = "FOLLOW_SYSTEM",
                adaptiveColorEnabled = true,
                remindersEnabled = false,
                appLockGracePeriodOption = "ONE_MINUTE",
                hideScreenContentEnabled = false,
                onboardingCompleted = true,
                appLanguageOption = "ENGLISH",
                calibrationDefaultUnits = emptyMap(),
            ),
            userProfile = BackupUserProfileSnapshot(
                weightKg = null,
                weightOriginalValue = null,
                weightOriginalUnit = "KILOGRAMS",
            ),
            medicines = emptyList(),
            medicationGroups = emptyList(),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
            trackedDates = trackedDates,
        )
    }
}
