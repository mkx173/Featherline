package com.mkx.hrttracker.data.backup

import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class BackupRestoreValidationTest {
    @Test
    fun toValidatedSnapshot_maps_current_snapshot_shape_to_restorable_entities() {
        val exportedAt = Instant.parse("2026-04-26T03:04:05Z").toEpochMilli()
        val profileUpdatedAt = Instant.parse("2026-04-25T00:00:00Z").toEpochMilli()
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val itemUuid = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val analyteUuid = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val panelUuid = UUID.fromString("00000000-0000-0000-0000-000000000005")
        val builtinResultUuid = UUID.fromString("00000000-0000-0000-0000-000000000006")
        val customResultUuid = UUID.fromString("00000000-0000-0000-0000-000000000007")
        val recreatedFromGroupUuid = UUID.fromString("00000000-0000-0000-0000-000000000008")
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
                homeE2DisplayUnit = BloodUnitKey.NG_DL.storageValue,
                homeE2ChartWindow = HomeE2ChartWindowOption.THIRTY_DAYS.name,
                calibrationDefaultUnits = mapOf(
                    BloodAnalyteKey.E2.storageValue to BloodUnitKey.PMOL_L.storageValue,
                ),
                widgetContentScale = 0.8f,
                widgetBackgroundAlpha = 0.6f,
            ),
            userProfile = BackupUserProfileSnapshot(
                weightKg = 52.16312255,
                weightOriginalValue = 115.0,
                weightOriginalUnit = "POUNDS",
                updatedAtEpochMillis = profileUpdatedAt,
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
                    recreatedFromGroupUuid = recreatedFromGroupUuid.toString(),
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
            AllowedAnalyteUnit.of(BloodAnalyteKey.E2, BloodUnitKey.NG_DL),
            validatedSnapshot.settings.homeE2DisplayUnit,
        )
        assertEquals(
            HomeE2ChartWindowOption.THIRTY_DAYS,
            validatedSnapshot.settings.homeE2ChartWindowOption,
        )
        assertEquals(
            setOf(AllowedAnalyteUnit.of(BloodAnalyteKey.E2, BloodUnitKey.PMOL_L)),
            validatedSnapshot.settings.calibrationDefaultUnits,
        )
        assertEquals(0.8f, validatedSnapshot.settings.widgetContentScale, 0f)
        assertEquals(0.6f, validatedSnapshot.settings.widgetBackgroundAlpha, 0f)

        checkNotNull(validatedSnapshot.userProfile)
        assertEquals(52.16312255, validatedSnapshot.userProfile.weightKg!!, 1e-9)
        assertEquals("POUNDS", validatedSnapshot.userProfile.weightOriginalUnit)
        assertEquals(profileUpdatedAt, validatedSnapshot.userProfile.updatedAtEpochMillis)

        val restoredGroup = validatedSnapshot.medicationGroups.single()
        assertEquals(groupUuid.toString(), restoredGroup.uuid)
        assertEquals("TEAL", restoredGroup.colorKey)
        assertEquals(2, restoredGroup.scheduleInterval)
        assertEquals(recreatedFromGroupUuid.toString(), restoredGroup.recreatedFromGroupUuid)

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
    fun toValidatedSnapshot_backfillsLegacyUserProfileUpdatedAtFromExportTime() {
        val exportedAt = Instant.parse("2026-04-26T03:04:05Z").toEpochMilli()
        val snapshot = BackupSnapshot(
            exportedAtEpochMillis = exportedAt,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = BackupSettingsSnapshot(
                darkModeOption = "FOLLOW_SYSTEM",
                adaptiveColorEnabled = true,
                remindersEnabled = true,
                appLockGracePeriodOption = "IMMEDIATELY",
                hideScreenContentEnabled = false,
                onboardingCompleted = true,
                appLanguageOption = "ENGLISH",
                calibrationDefaultUnits = emptyMap(),
            ),
            userProfile = BackupUserProfileSnapshot(
                weightKg = 70.0,
                weightOriginalValue = 70.0,
                weightOriginalUnit = "KILOGRAMS",
            ),
            medicationGroups = emptyList(),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        val validatedSnapshot = snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        checkNotNull(validatedSnapshot.userProfile)
        assertEquals(exportedAt, validatedSnapshot.userProfile.updatedAtEpochMillis)
    }

    @Test
    fun toValidatedSnapshot_legacyBackupWithoutHomeE2ChartWindow_defaultsToSevenDays() {
        val exportedAt = Instant.parse("2026-04-26T03:04:05Z").toEpochMilli()
        val snapshot = BackupSnapshot(
            exportedAtEpochMillis = exportedAt,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = BackupSettingsSnapshot(
                darkModeOption = "FOLLOW_SYSTEM",
                adaptiveColorEnabled = true,
                remindersEnabled = true,
                appLockGracePeriodOption = "IMMEDIATELY",
                hideScreenContentEnabled = false,
                onboardingCompleted = true,
                appLanguageOption = "ENGLISH",
                // homeE2ChartWindow intentionally omitted to simulate a pre-feature backup.
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

        val validatedSnapshot = snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        assertEquals(
            HomeE2ChartWindowOption.SEVEN_DAYS,
            validatedSnapshot.settings.homeE2ChartWindowOption,
        )
    }

    @Test
    fun toValidatedSnapshot_backfillsLegacyScheduleTimeUuidsEffectiveFromAndMatchedLogLink() {
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000102")
        val snapshot = medicationOnlySnapshot(
            groupUuid = groupUuid,
            scheduleTimes = listOf(
                BackupMedicationGroupScheduleTimeSnapshot(hourOfDay = 9, minuteOfHour = 0),
                BackupMedicationGroupScheduleTimeSnapshot(hourOfDay = 21, minuteOfHour = 0),
            ),
            logs = listOf(
                medicationLogSnapshot(
                    uuid = logUuid,
                    sourceGroupUuid = groupUuid,
                    scheduledForIso = "2026-04-26T09:00",
                )
            ),
            includePastScheduledSlots = true,
        )

        val validatedSnapshot = snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        val scheduleTimes = validatedSnapshot.medicationGroupScheduleTimes
        assertEquals(2, scheduleTimes.size)
        assertEquals(2, scheduleTimes.map { scheduleTime -> scheduleTime.uuid }.toSet().size)
        assertEquals(
            listOf("2026-04-01T00:00", "2026-04-01T00:00"),
            scheduleTimes.map { scheduleTime -> scheduleTime.effectiveFromLocalIso },
        )
        assertEquals(scheduleTimes.first().uuid, validatedSnapshot.medicationLogs.single().scheduleTimeUuid)
    }

    @Test
    fun toValidatedSnapshot_keepsLegacyLogScheduleTimeUuidNullWhenMatchIsAmbiguousOrMissing() {
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-000000000201")
        val ambiguousLogUuid = UUID.fromString("00000000-0000-0000-0000-000000000202")
        val missingLogUuid = UUID.fromString("00000000-0000-0000-0000-000000000203")
        val snapshot = medicationOnlySnapshot(
            groupUuid = groupUuid,
            scheduleTimes = listOf(
                BackupMedicationGroupScheduleTimeSnapshot(hourOfDay = 9, minuteOfHour = 0),
                BackupMedicationGroupScheduleTimeSnapshot(hourOfDay = 9, minuteOfHour = 0),
            ),
            logs = listOf(
                medicationLogSnapshot(
                    uuid = ambiguousLogUuid,
                    sourceGroupUuid = groupUuid,
                    scheduledForIso = "2026-04-26T09:00",
                ),
                medicationLogSnapshot(
                    uuid = missingLogUuid,
                    sourceGroupUuid = groupUuid,
                    scheduledForIso = "2026-04-26T10:00",
                )
            ),
            includePastScheduledSlots = true,
        )

        val validatedSnapshot = snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        assertEquals(
            listOf(null, null),
            validatedSnapshot.medicationLogs.sortedBy { log -> log.uuid }.map { log -> log.scheduleTimeUuid },
        )
    }

    @Test
    fun toValidatedSnapshot_usesCreatedAtEffectiveFromForLegacyForwardOnlySuccessor() {
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-000000000301")
        val createdAt = Instant.parse("2026-04-18T01:00:00Z")
        val snapshot = medicationOnlySnapshot(
            groupUuid = groupUuid,
            scheduleTimes = listOf(
                BackupMedicationGroupScheduleTimeSnapshot(hourOfDay = 9, minuteOfHour = 0),
            ),
            logs = emptyList(),
            includePastScheduledSlots = false,
            createdAtEpochMillis = createdAt.toEpochMilli(),
        )

        val validatedSnapshot = snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        assertEquals(
            createdAt.atZone(ZoneId.systemDefault()).toLocalDateTime().toString(),
            validatedSnapshot.medicationGroupScheduleTimes.single().effectiveFromLocalIso,
        )
    }

    @Test
    fun toValidatedSnapshot_derivesLegacySuccessorLineageFromReplacedByLink() {
        val originalGroupUuid = UUID.fromString("00000000-0000-0000-0000-000000000351")
        val successorGroupUuid = UUID.fromString("00000000-0000-0000-0000-000000000352")
        val snapshot = medicationOnlySnapshot(
            groupUuid = originalGroupUuid,
            scheduleTimes = listOf(
                BackupMedicationGroupScheduleTimeSnapshot(hourOfDay = 9, minuteOfHour = 0),
            ),
            logs = emptyList(),
            replacedByGroupUuid = successorGroupUuid.toString(),
            extraGroups = listOf(
                medicationGroupSnapshot(
                    groupUuid = successorGroupUuid,
                    scheduleTimes = listOf(
                        BackupMedicationGroupScheduleTimeSnapshot(hourOfDay = 10, minuteOfHour = 0),
                    ),
                    includePastScheduledSlots = false,
                )
            ),
        )

        val validatedSnapshot = snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        val successor = validatedSnapshot.medicationGroups.single { group ->
            group.uuid == successorGroupUuid.toString()
        }
        assertEquals(originalGroupUuid.toString(), successor.recreatedFromGroupUuid)
    }

    @Test
    fun toValidatedSnapshot_rejectsLogScheduleTimeUuidOutsideSourceGroup() {
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-000000000401")
        val otherGroupUuid = UUID.fromString("00000000-0000-0000-0000-000000000402")
        val scheduleTimeUuid = UUID.fromString("00000000-0000-0000-0000-000000000403")
        val snapshot = medicationOnlySnapshot(
            groupUuid = groupUuid,
            scheduleTimes = listOf(
                BackupMedicationGroupScheduleTimeSnapshot(
                    hourOfDay = 9,
                    minuteOfHour = 0,
                    uuid = scheduleTimeUuid.toString(),
                    effectiveFromLocalIso = "2026-04-01T00:00",
                ),
            ),
            logs = listOf(
                medicationLogSnapshot(
                    uuid = UUID.fromString("00000000-0000-0000-0000-000000000404"),
                    sourceGroupUuid = otherGroupUuid,
                    scheduledForIso = "2026-04-26T09:00",
                    scheduleTimeUuid = scheduleTimeUuid,
                )
            ),
            extraGroups = listOf(
                medicationGroupSnapshot(
                    groupUuid = otherGroupUuid,
                    scheduleTimes = listOf(
                        BackupMedicationGroupScheduleTimeSnapshot(hourOfDay = 9, minuteOfHour = 0),
                    ),
                )
            ),
        )

        try {
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")
            fail("Expected restore validation to reject a log schedule time outside its source group.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
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

    private fun medicationOnlySnapshot(
        groupUuid: UUID,
        scheduleTimes: List<BackupMedicationGroupScheduleTimeSnapshot>,
        logs: List<BackupMedicationLogSnapshot>,
        includePastScheduledSlots: Boolean = true,
        createdAtEpochMillis: Long = Instant.parse("2026-04-18T01:00:00Z").toEpochMilli(),
        replacedByGroupUuid: String? = null,
        recreatedFromGroupUuid: String? = null,
        extraGroups: List<BackupMedicationGroupSnapshot> = emptyList(),
    ): BackupSnapshot {
        return BackupSnapshot(
            exportedAtEpochMillis = Instant.parse("2026-04-26T03:04:05Z").toEpochMilli(),
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = BackupSettingsSnapshot(
                darkModeOption = "FOLLOW_SYSTEM",
                adaptiveColorEnabled = true,
                remindersEnabled = true,
                appLockGracePeriodOption = "IMMEDIATELY",
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
            medicationGroups = listOf(
                medicationGroupSnapshot(
                    groupUuid = groupUuid,
                    scheduleTimes = scheduleTimes,
                    includePastScheduledSlots = includePastScheduledSlots,
                    createdAtEpochMillis = createdAtEpochMillis,
                    replacedByGroupUuid = replacedByGroupUuid,
                    recreatedFromGroupUuid = recreatedFromGroupUuid,
                )
            ) + extraGroups,
            medicationLogs = logs,
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )
    }

    private fun medicationGroupSnapshot(
        groupUuid: UUID,
        scheduleTimes: List<BackupMedicationGroupScheduleTimeSnapshot>,
        includePastScheduledSlots: Boolean = true,
        createdAtEpochMillis: Long = Instant.parse("2026-04-18T01:00:00Z").toEpochMilli(),
        replacedByGroupUuid: String? = null,
        recreatedFromGroupUuid: String? = null,
    ): BackupMedicationGroupSnapshot {
        return BackupMedicationGroupSnapshot(
            uuid = groupUuid.toString(),
            name = "Morning meds",
            colorKey = "TEAL",
            notificationsEnabled = true,
            schedule = BackupMedicationGroupScheduleSnapshot(
                type = "DAILY",
                interval = 1,
                sinceEpochDay = LocalDate.of(2026, 4, 1).toEpochDay(),
                weeklyDaysOfWeek = emptyList(),
                times = scheduleTimes,
            ),
            medications = listOf(
                BackupMedicationGroupItemSnapshot(
                    uuid = UUID.randomUUID().toString(),
                    count = 1,
                    category = "ESTRADIOL",
                    applicationType = "ORAL",
                    selectionKind = "CATALOG",
                    medicationKey = "ESTRADIOL",
                    customMedicationName = null,
                    doseKind = "MG_AS_MEDICINE",
                    doseValueMg = 2.0,
                    customDoseUnit = "MG",
                    doseValuePercent = null,
                    doseWeightGrams = null,
                    doseReleaseRateMcgPerDay = null,
                    gelApplicationArea = "DEFAULT",
                )
            ),
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = createdAtEpochMillis,
            archivedAtEpochMillis = null,
            includePastScheduledSlots = includePastScheduledSlots,
            replacedByGroupUuid = replacedByGroupUuid,
            recreatedFromGroupUuid = recreatedFromGroupUuid,
        )
    }

    private fun medicationLogSnapshot(
        uuid: UUID,
        sourceGroupUuid: UUID,
        scheduledForIso: String,
        scheduleTimeUuid: UUID? = null,
    ): BackupMedicationLogSnapshot {
        return BackupMedicationLogSnapshot(
            uuid = uuid.toString(),
            category = "ESTRADIOL",
            applicationType = "ORAL",
            selectionKind = "CATALOG",
            medicationKey = "ESTRADIOL",
            customMedicationName = null,
            doseKind = "MG_AS_MEDICINE",
            doseValueMg = 2.0,
            customDoseUnit = "MG",
            doseValuePercent = null,
            doseWeightGrams = null,
            doseReleaseRateMcgPerDay = null,
            gelApplicationArea = "DEFAULT",
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = sourceGroupUuid.toString(),
            scheduleTimeUuid = scheduleTimeUuid?.toString(),
            appliedAtEpochMillis = Instant.parse("2026-04-26T00:00:00Z").toEpochMilli(),
            appliedAtTimeZoneId = "Asia/Tokyo",
            scheduledForIso = scheduledForIso,
            count = 1,
        )
    }
}
