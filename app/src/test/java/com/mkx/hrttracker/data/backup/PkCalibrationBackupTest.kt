package com.mkx.hrttracker.data.backup

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            .replace(",\"acceptedModelVersion\":null", "")
            .replace(",\"acceptedSourceValueBits\":null", "")
            .replace(",\"acceptedCollectedAtEpochMillis\":null", "")
            .replace(",\"calibrationMetadataUpdatedAtEpochMillis\":null", "")

        assertFalse(legacyJson.contains("calibrationDisposition"))
        val decoded = checkNotNull(BackupSnapshotJsonCodec.decode(legacyJson))
        val validated = decoded.toValidatedSnapshot(BACKUP_APP_PACKAGE_NAME)

        assertEquals(6, decoded.snapshotVersion)
        assertEquals(1, validated.bloodTestResults.size)
        assertEquals(emptyList<Any>(), validated.e2CalibrationMetadata)
    }

    @Test
    fun version7AcceptedAndExcludedMetadata_validateWithExactRecordContract() {
        val accepted = result(
            uuid = "00000000-0000-0000-0000-000000000911",
            disposition = "ACCEPTED",
            acceptedModelVersion = "pk-calibration:test/v9",
            acceptedSourceValueBits = "4059000000000000",
            acceptedCollectedAtEpochMillis = 600L,
            updatedAt = 2_000L,
        )
        val excluded = result(
            uuid = "00000000-0000-0000-0000-000000000912",
            disposition = "EXCLUDED",
            updatedAt = 3_000L,
        )

        val validated = snapshot(results = listOf(accepted, excluded))
            .toValidatedSnapshot(BACKUP_APP_PACKAGE_NAME)
        val metadata = validated.e2CalibrationMetadata.associateBy { it.resultUuid }

        assertEquals("ACCEPTED", metadata.getValue(accepted.uuid).disposition)
        assertEquals(
            "pk-calibration:test/v9",
            metadata.getValue(accepted.uuid).acceptedModelVersion,
        )
        assertEquals("EXCLUDED", metadata.getValue(excluded.uuid).disposition)
        assertNull(metadata.getValue(excluded.uuid).acceptedSourceValueBits)
    }

    @Test
    fun acceptedMetadataWithNonContractValueBits_isRejected() {
        val invalid = result(
            uuid = "00000000-0000-0000-0000-000000000921",
            disposition = "ACCEPTED",
            acceptedModelVersion = "pk-calibration:test/v9",
            acceptedSourceValueBits = "not-binary64-bits",
            acceptedCollectedAtEpochMillis = 600L,
            updatedAt = 4_000L,
        )

        try {
            snapshot(results = listOf(invalid)).toValidatedSnapshot(BACKUP_APP_PACKAGE_NAME)
            fail("Expected the non-contract acceptance value bits to be rejected.")
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
        acceptedModelVersion: String? = null,
        acceptedSourceValueBits: String? = null,
        acceptedCollectedAtEpochMillis: Long? = null,
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
            acceptedModelVersion = acceptedModelVersion,
            acceptedSourceValueBits = acceptedSourceValueBits,
            acceptedCollectedAtEpochMillis = acceptedCollectedAtEpochMillis,
            calibrationMetadataUpdatedAtEpochMillis = updatedAt,
        )
    }
}
