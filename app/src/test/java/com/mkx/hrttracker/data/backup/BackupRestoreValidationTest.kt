package com.mkx.hrttracker.data.backup

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.util.UUID

class BackupRestoreValidationTest {
    @Test
    fun toValidatedSnapshot_maps_current_snapshot_shape_to_restorable_entities() {
        val exportedAt = Instant.parse("2026-04-26T03:04:05Z").toEpochMilli()
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val itemUuid = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val analyteUuid = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val panelUuid = UUID.fromString("00000000-0000-0000-0000-000000000005")
        val builtinResultUuid = UUID.fromString("00000000-0000-0000-0000-000000000006")
        val customResultUuid = UUID.fromString("00000000-0000-0000-0000-000000000007")
        val snapshot = BackupSnapshot(
            exportedAtEpochMillis = exportedAt,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = BackupSettingsSnapshot(
                darkModeOption = "DARK",
                adaptiveColorEnabled = false,
                remindersEnabled = false,
                appLockGracePeriodOption = "FIVE_MINUTES",
                hideScreenContentEnabled = true,
                onboardingCompleted = true,
                appLanguageOption = "SIMPLIFIED_CHINESE",
                calibrationDefaultUnits = mapOf(
                    BloodAnalyteKey.E2.storageValue to BloodUnitKey.PMOL_L.storageValue,
                ),
            ),
            userProfile = BackupUserProfileSnapshot(
                weightKg = 52.16312255,
                weightOriginalValue = 115.0,
                weightOriginalUnit = "POUNDS",
            ),
            medicationGroups = listOf(
                BackupMedicationGroupSnapshot(
                    uuid = groupUuid.toString(),
                    name = "Morning meds",
                    colorKey = "TEAL",
                    notificationsEnabled = true,
                    schedule = BackupMedicationGroupScheduleSnapshot(
                        type = "WEEKLY",
                        interval = 2,
                        sinceEpochDay = 20540,
                        weeklyDaysOfWeek = listOf(1, 4),
                        times = listOf(
                            BackupMedicationGroupScheduleTimeSnapshot(9, 0),
                            BackupMedicationGroupScheduleTimeSnapshot(21, 30),
                        ),
                    ),
                    medications = listOf(
                        BackupMedicationGroupItemSnapshot(
                            uuid = itemUuid.toString(),
                            count = 2,
                            category = "CUSTOM",
                            applicationType = "ORAL",
                            selectionKind = "CUSTOM",
                            medicationKey = null,
                            customMedicationName = "Custom med",
                            doseKind = "MG_AS_MEDICINE",
                            doseValueMg = 0.2,
                            customDoseUnit = "MCG",
                            doseValuePercent = null,
                            doseWeightGrams = null,
                            doseReleaseRateMcgPerDay = null,
                            gelApplicationArea = "DEFAULT",
                        )
                    ),
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 200L,
                    archivedAtEpochMillis = null,
                )
            ),
            medicationLogs = listOf(
                BackupMedicationLogSnapshot(
                    uuid = logUuid.toString(),
                    category = "CUSTOM",
                    applicationType = "ORAL",
                    selectionKind = "CUSTOM",
                    medicationKey = null,
                    customMedicationName = "Custom med",
                    doseKind = "MG_AS_MEDICINE",
                    doseValueMg = 0.2,
                    customDoseUnit = "MCG",
                    doseValuePercent = null,
                    doseWeightGrams = null,
                    doseReleaseRateMcgPerDay = null,
                    gelApplicationArea = "DEFAULT",
                    dosageMgAsEstradiol = 0.2,
                    sourceGroupUuid = groupUuid.toString(),
                    appliedAtEpochMillis = 300L,
                    appliedAtTimeZoneId = "Asia/Tokyo",
                    scheduledForIso = "2026-04-26T10:00",
                    count = 2,
                )
            ),
            customBloodAnalytes = listOf(
                BackupCustomBloodAnalyteSnapshot(
                    uuid = analyteUuid.toString(),
                    abbreviation = "DHT",
                    name = "DHT",
                    unitLabel = "ng/dL",
                    createdAtEpochMillis = 400L,
                    updatedAtEpochMillis = 500L,
                    archivedAtEpochMillis = null,
                )
            ),
            bloodTestPanels = listOf(
                BackupBloodTestPanelSnapshot(
                    uuid = panelUuid.toString(),
                    collectedAtInstantEpochMillis = 600L,
                    collectedAtTimeZoneId = "Asia/Tokyo",
                    notes = "Fasting",
                    timeSinceLastEstradiolDoseMillis = 700L,
                    timeSinceLastTestosteroneDoseMillis = null,
                    createdAtEpochMillis = 800L,
                    updatedAtEpochMillis = 900L,
                    results = listOf(
                        BackupBloodTestResultSnapshot(
                            uuid = builtinResultUuid.toString(),
                            createdAtEpochMillis = 901L,
                            displayOrder = 0,
                            builtinAnalyteKey = BloodAnalyteKey.E2.storageValue,
                            customAnalyteUuid = null,
                            value = 367.1,
                            unitSnapshot = BloodUnitKey.PMOL_L.storageValue,
                            canonicalValue = 100.0,
                        ),
                        BackupBloodTestResultSnapshot(
                            uuid = customResultUuid.toString(),
                            createdAtEpochMillis = 902L,
                            displayOrder = 1,
                            builtinAnalyteKey = null,
                            customAnalyteUuid = analyteUuid.toString(),
                            value = 12.0,
                            unitSnapshot = "ng/dL",
                            canonicalValue = 12.0,
                        ),
                    ),
                )
            ),
        )

        val validatedSnapshot = snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        assertEquals(DarkModeOption.DARK, validatedSnapshot.settings.darkModeOption)
        assertEquals(false, validatedSnapshot.settings.adaptiveColorEnabled)
        assertEquals(false, validatedSnapshot.settings.remindersEnabled)
        assertEquals(AppLockGracePeriodOption.FIVE_MINUTES, validatedSnapshot.settings.appLockGracePeriodOption)
        assertEquals(true, validatedSnapshot.settings.hideScreenContentEnabled)
        assertEquals(true, validatedSnapshot.settings.onboardingCompleted)
        assertEquals(AppLanguageOption.SIMPLIFIED_CHINESE, validatedSnapshot.settings.appLanguageOption)
        assertEquals(
            mapOf(BloodAnalyteKey.E2 to BloodUnitKey.PMOL_L),
            validatedSnapshot.settings.calibrationDefaultUnits,
        )

        checkNotNull(validatedSnapshot.userProfile)
        assertEquals(52.16312255, validatedSnapshot.userProfile.weightKg!!, 1e-9)
        assertEquals("POUNDS", validatedSnapshot.userProfile.weightOriginalUnit)
        assertEquals(exportedAt, validatedSnapshot.userProfile.updatedAtEpochMillis)

        val restoredGroup = validatedSnapshot.medicationGroups.single()
        assertEquals(groupUuid.toString(), restoredGroup.uuid)
        assertEquals("TEAL", restoredGroup.colorKey)
        assertEquals(2, restoredGroup.scheduleInterval)

        val restoredItem = validatedSnapshot.medicationGroupItems.single()
        assertEquals(itemUuid.toString(), restoredItem.uuid)
        assertEquals("MCG", restoredItem.customDoseUnit)
        assertEquals(0.2, restoredItem.doseValueMg!!, 1e-9)

        val restoredLog = validatedSnapshot.medicationLogs.single()
        assertEquals(logUuid.toString(), restoredLog.uuid)
        assertEquals(groupUuid.toString(), restoredLog.sourceGroupUuid)
        assertEquals("MCG", restoredLog.customDoseUnit)

        val restoredAnalyte = validatedSnapshot.customBloodAnalytes.single()
        assertEquals(analyteUuid.toString(), restoredAnalyte.uuid)
        assertEquals("DHT", restoredAnalyte.abbreviation)
        assertEquals("dht", restoredAnalyte.normalizedName)
        assertEquals("ng/dl", restoredAnalyte.normalizedUnitLabel)

        val restoredPanel = validatedSnapshot.bloodTestPanels.single()
        assertEquals(panelUuid.toString(), restoredPanel.uuid)
        assertEquals("Fasting", restoredPanel.notes)
        assertEquals(700L, restoredPanel.timeSinceLastEstradiolDoseMillis)
        assertNull(restoredPanel.timeSinceLastTestosteroneDoseMillis)

        val restoredResults = validatedSnapshot.bloodTestResults.sortedBy { it.displayOrder }
        assertEquals(2, restoredResults.size)
        assertEquals(builtinResultUuid.toString(), restoredResults[0].uuid)
        assertEquals(BloodAnalyteKey.E2.storageValue, restoredResults[0].builtinAnalyteKey)
        assertEquals(100.0, restoredResults[0].canonicalValue, 1e-9)
        assertEquals(customResultUuid.toString(), restoredResults[1].uuid)
        assertEquals(analyteUuid.toString(), restoredResults[1].customAnalyteUuid)
    }

    @Test
    fun toValidatedSnapshot_rejects_backup_for_different_package() {
        val snapshot = BackupSnapshot(
            exportedAtEpochMillis = 1L,
            app = BackupAppSnapshot(packageName = "com.example.other"),
            settings = BackupSettingsSnapshot(
                darkModeOption = "FOLLOW_SYSTEM",
                adaptiveColorEnabled = true,
                remindersEnabled = true,
                appLockGracePeriodOption = "IMMEDIATELY",
                hideScreenContentEnabled = false,
                onboardingCompleted = false,
                appLanguageOption = "ENGLISH",
                calibrationDefaultUnits = emptyMap(),
            ),
            userProfile = BackupUserProfileSnapshot(
                weightKg = null,
                weightOriginalValue = null,
                weightOriginalUnit = "KILOGRAMS",
            ),
            medicationGroups = emptyList(),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        try {
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")
            fail("Expected restore validation to reject a backup for a different package.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
