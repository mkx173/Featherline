package com.mkx.hrttracker.data.backup

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.UserProfileDao
import com.mkx.hrttracker.data.local.UserProfileEntity
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.personalization.WeightUnit
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Health Connect weight-sync fields are additive: they came without a snapshot version bump. */
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

        assertEquals(CURRENT_BACKUP_SNAPSHOT_VERSION, decoded.snapshotVersion)
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
        // An undated weight, valid until the next typed or synced one.
        assertNull(profile.weightMeasuredAtEpochMillis)
        assertEquals(2_000L, profile.updatedAtEpochMillis)
    }

    @Test
    fun aWeightSwitchedUnitWithSyncOn_passesTheBackupValidator() = runTest {
        // The real unit switch, on a row in memory.
        var row: UserProfileEntity? = UserProfileEntity(
            weightKg = 61.8,
            weightOriginalValue = 61.8,
            weightOriginalUnit = WeightUnit.KILOGRAMS.name,
            updatedAtEpochMillis = 1_000L,
            weightMeasuredAtEpochMillis = 1_500L,
        )
        val dao: UserProfileDao = mockk {
            coEvery { getProfile(any()) } answers { row }
            coEvery { upsertProfile(any()) } answers { row = firstArg() }
        }
        val databaseHolder: DatabaseHolder = mockk {
            every { databaseFlow } returns MutableStateFlow(null)
            every { get() } returns mockk<HrtTrackerDatabase> { every { userProfileDao() } returns dao }
        }
        val home: HomeSnapshotRepository = mockk(relaxed = true)
        coEvery { home.runHomeDataMutation<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
        val repository = UserProfileRepository(databaseHolder, home, CoroutineScope(StandardTestDispatcher()))

        for (unit in listOf(WeightUnit.POUNDS, WeightUnit.KILOGRAMS, WeightUnit.POUNDS)) {
            repository.setWeightUnit(unit)
            val switched = row!!
            val restored = snapshot(
                syncEnabled = true,
                userProfile = BackupUserProfileSnapshot(
                    weightKg = switched.weightKg,
                    weightOriginalValue = switched.weightOriginalValue,
                    weightOriginalUnit = switched.weightOriginalUnit!!,
                    updatedAtEpochMillis = switched.updatedAtEpochMillis,
                    weightMeasuredAtEpochMillis = switched.weightMeasuredAtEpochMillis,
                ),
            ).toValidatedSnapshot(expectedPackageName = PACKAGE).userProfile!!
            assertEquals(unit.name, restored.weightOriginalUnit)
            assertEquals(1_500L, restored.weightMeasuredAtEpochMillis)
        }
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
