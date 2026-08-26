package com.mkx.hrttracker.data.backup

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class PkCalibrationBackupTest {
    @Test
    fun version6BackupWithoutReviewFields_remainsReadableAsNoMetadata() {
        val snapshot = snapshot(
            version = 6,
            results = listOf(result("00000000-0000-0000-0000-000000000901")),
        )
        val legacyJson = BackupSnapshotJsonCodec.encode(snapshot)
            .replace(",\"calibrationDisposition\":null", "")
            .replace(",\"calibrationMetadataUpdatedAtEpochMillis\":null", "")

        assertFalse(legacyJson.contains("calibrationDisposition"))
        val decoded = checkNotNull(BackupSnapshotJsonCodec.decode(legacyJson))
        val validated = decoded.toValidatedSnapshot(BACKUP_APP_PACKAGE_NAME)

        assertEquals(6, decoded.snapshotVersion)
        assertEquals(1, validated.bloodTestResults.size)
        assertEquals(emptyList<Any>(), validated.e2CalibrationMetadata)
    }

    @Test
    fun version7ExcludedMetadata_roundTripsAndLegacyAcceptedReadsAsAuto() {
        // "ACCEPTED" was a pre-release disposition; restore maps it to AUTO
        // rather than rejecting the whole backup.
        val legacyAccepted = result(
            uuid = "00000000-0000-0000-0000-000000000911",
            disposition = "ACCEPTED",
            updatedAt = 2_000L,
        )
        val excluded = result(
            uuid = "00000000-0000-0000-0000-000000000912",
            disposition = "EXCLUDED",
            updatedAt = 3_000L,
        )

        val validated = snapshot(results = listOf(legacyAccepted, excluded))
            .toValidatedSnapshot(BACKUP_APP_PACKAGE_NAME)
        val metadata = validated.e2CalibrationMetadata.associateBy { it.resultUuid }

        assertEquals("AUTO", metadata.getValue(legacyAccepted.uuid).disposition)
        assertEquals(2_000L, metadata.getValue(legacyAccepted.uuid).updatedAtEpochMillis)
        assertEquals("EXCLUDED", metadata.getValue(excluded.uuid).disposition)
        assertEquals(3_000L, metadata.getValue(excluded.uuid).updatedAtEpochMillis)
    }

    @Test
    fun unknownDisposition_isRejected() {
        val invalid = result(
            uuid = "00000000-0000-0000-0000-000000000921",
            disposition = "KEPT",
            updatedAt = 4_000L,
        )

        try {
            snapshot(results = listOf(invalid)).toValidatedSnapshot(BACKUP_APP_PACKAGE_NAME)
            fail("Expected the unknown disposition to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun snapshotVersionAboveCurrent_isRejected() {
        try {
            snapshot(version = 8, results = emptyList()).toValidatedSnapshot(BACKUP_APP_PACKAGE_NAME)
            fail("Expected snapshotVersion 8 to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun snapshot(
        version: Int = CURRENT_BACKUP_SNAPSHOT_VERSION,
        results: List<BackupBloodTestResultSnapshot>,
    ): BackupSnapshot {
        return BackupSnapshot(
            snapshotVersion = version,
            exportedAtEpochMillis = 1_000L,
            app = BackupAppSnapshot(BACKUP_APP_PACKAGE_NAME),
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
            bloodTestPanels = results.mapIndexed { index, result ->
                BackupBloodTestPanelSnapshot(
                    uuid = "00000000-0000-0000-0000-${(990 + index).toString().padStart(12, '0')}",
                    collectedAtInstantEpochMillis = 1_000L + index,
                    collectedAtTimeZoneId = "UTC",
                    notes = null,
                    timeSinceLastEstradiolDoseMillis = null,
                    timeSinceLastTestosteroneDoseMillis = null,
                    createdAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 1_000L,
                    results = listOf(result),
                )
            },
        )
    }

    private fun result(
        uuid: String,
        disposition: String? = null,
        updatedAt: Long? = null,
    ): BackupBloodTestResultSnapshot {
        return BackupBloodTestResultSnapshot(
            uuid = uuid,
            createdAtEpochMillis = 1_000L,
            displayOrder = 0,
            builtinAnalyteKey = BloodAnalyteKey.E2.storageValue,
            customAnalyteUuid = null,
            value = 100.0,
            unitSnapshot = BloodUnitKey.PG_ML.storageValue,
            canonicalValue = 100.0,
            calibrationDisposition = disposition,
            calibrationMetadataUpdatedAtEpochMillis = updatedAt,
        )
    }
}
