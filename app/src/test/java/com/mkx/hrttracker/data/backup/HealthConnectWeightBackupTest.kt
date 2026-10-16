package com.mkx.hrttracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Health Connect weight-sync fields are additive: snapshot version stays 7. */
class HealthConnectWeightBackupTest {

    @Test
    fun roundTrip_preservesSyncFieldsAndFlag() {
        val snapshot = snapshot(
            syncEnabled = true,
            userProfile = BackupUserProfileSnapshot(
                weightKg = 62.4,
                weightOriginalValue = 62.4,
                weightOriginalUnit = "KILOGRAMS",
                updatedAtEpochMillis = 2_000L,
                weightMeasuredAtEpochMillis = 1_500L,
            ),
        )

        val decoded = BackupSnapshotJsonCodec.decode(BackupSnapshotJsonCodec.encode(snapshot))!!
        val validated = decoded.toValidatedSnapshot(expectedPackageName = PACKAGE)

        assertEquals(7, decoded.snapshotVersion)
        assertTrue(validated.settings.healthConnectWeightSyncEnabled)
        val profile = validated.userProfile!!
        assertEquals(1_500L, profile.weightMeasuredAtEpochMillis)
    }

    @Test
    fun v7BackupWithoutSyncFields_restoresWithFallbacks() {
        val json = """
            {
              "snapshotVersion": 7,
              "exportedAtEpochMillis": 9000,
              "app": { "packageName": "$PACKAGE" },
              "settings": {
                "darkModeOption": "FOLLOW_SYSTEM",
                "adaptiveColorEnabled": true,
                "remindersEnabled": false,
                "appLockGracePeriodOption": "ONE_MINUTE",
                "hideScreenContentEnabled": false,
                "onboardingCompleted": true,
                "appLanguageOption": "ENGLISH",
                "calibrationDefaultUnits": {}
              },
              "userProfile": {
                "weightKg": 62.4,
                "weightOriginalValue": 62.4,
                "weightOriginalUnit": "KILOGRAMS",
                "updatedAtEpochMillis": 2000
              },
              "medicines": [],
              "medicationGroups": [],
              "medicationLogs": [],
              "customBloodAnalytes": [],
              "bloodTestPanels": []
            }
        """.trimIndent()

        val validated = BackupSnapshotJsonCodec.decode(json)!!
            .toValidatedSnapshot(expectedPackageName = PACKAGE)

        assertFalse(validated.settings.healthConnectWeightSyncEnabled)
        val profile = validated.userProfile!!
        // Only blanks the "Measured" time until the next sync.
        assertNull(profile.weightMeasuredAtEpochMillis)
        assertEquals(2_000L, profile.updatedAtEpochMillis)
    }

    private fun snapshot(
        syncEnabled: Boolean,
        userProfile: BackupUserProfileSnapshot,
    ) = BackupSnapshot(
        exportedAtEpochMillis = 9_000L,
        app = BackupAppSnapshot(packageName = PACKAGE),
        settings = BackupSettingsSnapshot(
            darkModeOption = "FOLLOW_SYSTEM",
            adaptiveColorEnabled = true,
            remindersEnabled = false,
            healthConnectWeightSyncEnabled = syncEnabled,
            appLockGracePeriodOption = "ONE_MINUTE",
            hideScreenContentEnabled = false,
            onboardingCompleted = true,
            appLanguageOption = "ENGLISH",
            calibrationDefaultUnits = emptyMap(),
        ),
        userProfile = userProfile,
        medicines = emptyList(),
        medicationGroups = emptyList(),
        medicationLogs = emptyList(),
        customBloodAnalytes = emptyList(),
        bloodTestPanels = emptyList(),
    )

    private companion object {
        const val PACKAGE = "com.mkx.hrttracker"
    }
}
