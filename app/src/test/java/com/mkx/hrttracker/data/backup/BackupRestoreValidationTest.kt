package com.mkx.hrttracker.data.backup

import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
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
import java.util.UUID

class BackupRestoreValidationTest {

    @Test
    fun toValidatedSnapshot_mapsCurrentSnapshotToRestorableEntities() {
        val exportedAt = Instant.parse("2026-04-26T03:04:05Z").toEpochMilli()
        val profileUpdatedAt = Instant.parse("2026-04-25T00:00:00Z").toEpochMilli()
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-000000000010")
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
                cjkTextOffsetEnabled = true,
                hazeBlurEnabled = false,
            ),
            userProfile = BackupUserProfileSnapshot(
                weightKg = 52.16312255,
                weightOriginalValue = 115.0,
                weightOriginalUnit = "POUNDS",
                updatedAtEpochMillis = profileUpdatedAt,
            ),
            medicines = listOf(catalogMedicineSnapshot(medicineUuid)),
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
                            medicineUuid = medicineUuid.toString(),
                            applicationType = "ORAL",
                            doseInstructionKind = "TABLET_FRACTION",
                            tabletFractionNumerator = 1,
                            tabletFractionDenominator = 2,
                            doseVolumeMl = null,
                            doseWeightGrams = null,
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
                    category = "ESTRADIOL",
                    medicineUuid = medicineUuid.toString(),
                    applicationType = "ORAL",
                    doseInstructionKind = "TABLET_FRACTION",
                    tabletFractionNumerator = 1,
                    tabletFractionDenominator = 2,
                    doseVolumeMl = null,
                    doseWeightGrams = null,
                    gelApplicationArea = "DEFAULT",
                    equivalentE2Mg = 1.0,
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

        val validatedSnapshot =
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        assertEquals(DarkModeOption.DARK, validatedSnapshot.settings.darkModeOption)
        assertEquals(
            AppLockGracePeriodOption.FIVE_MINUTES,
            validatedSnapshot.settings.appLockGracePeriodOption
        )
        assertEquals(
            AppLanguageOption.SIMPLIFIED_CHINESE,
            validatedSnapshot.settings.appLanguageOption
        )
        assertEquals(true, validatedSnapshot.settings.cjkTextOffsetEnabled)
        assertEquals(false, validatedSnapshot.settings.hazeBlurEnabled)
        assertEquals(
            AllowedAnalyteUnit.of(BloodAnalyteKey.E2, BloodUnitKey.NG_DL),
            validatedSnapshot.settings.homeE2DisplayUnit,
        )
        assertEquals(
            HomeE2ChartWindowOption.THIRTY_DAYS,
            validatedSnapshot.settings.homeE2ChartWindowOption
        )

        checkNotNull(validatedSnapshot.userProfile)
        assertEquals(52.16312255, validatedSnapshot.userProfile.weightKg!!, 1e-9)
        assertEquals(profileUpdatedAt, validatedSnapshot.userProfile.updatedAtEpochMillis)

        // Medicines are validated before groups/logs so the FK-validation set
        // can be built before walking children. Restored entities preserve
        // their original UUIDs verbatim.
        val restoredMedicine = validatedSnapshot.medicines.single()
        assertEquals(medicineUuid.toString(), restoredMedicine.uuid)
        assertEquals("PILL", restoredMedicine.preparationType)
        assertEquals(2.0, restoredMedicine.strengthMgPerTablet!!, 1e-9)

        val restoredGroup = validatedSnapshot.medicationGroups.single()
        assertEquals(groupUuid.toString(), restoredGroup.uuid)
        assertEquals(2, restoredGroup.scheduleInterval)
        assertEquals(recreatedFromGroupUuid.toString(), restoredGroup.recreatedFromGroupUuid)

        val restoredItem = validatedSnapshot.medicationGroupItems.single()
        assertEquals(itemUuid.toString(), restoredItem.uuid)
        assertEquals(medicineUuid.toString(), restoredItem.medicineUuid)
        assertEquals("ORAL", restoredItem.applicationType)
        assertEquals("TABLET_FRACTION", restoredItem.doseInstructionKind)
        assertEquals(1, restoredItem.tabletFractionNumerator)
        assertEquals(2, restoredItem.tabletFractionDenominator)

        val restoredLog = validatedSnapshot.medicationLogs.single()
        assertEquals(logUuid.toString(), restoredLog.uuid)
        assertEquals(groupUuid.toString(), restoredLog.sourceGroupUuid)
        assertEquals(medicineUuid.toString(), restoredLog.medicineUuid)
        assertEquals(1.0, restoredLog.equivalentE2Mg!!, 1e-9)

        val restoredAnalyte = validatedSnapshot.customBloodAnalytes.single()
        assertEquals(analyteUuid.toString(), restoredAnalyte.uuid)
        assertEquals("dht", restoredAnalyte.normalizedName)

        val restoredPanel = validatedSnapshot.bloodTestPanels.single()
        assertEquals(panelUuid.toString(), restoredPanel.uuid)
        assertEquals("Fasting", restoredPanel.notes)
        assertNull(restoredPanel.timeSinceLastTestosteroneDoseMillis)

        val restoredResults = validatedSnapshot.bloodTestResults.sortedBy { it.displayOrder }
        assertEquals(2, restoredResults.size)
        assertEquals(builtinResultUuid.toString(), restoredResults[0].uuid)
        assertEquals(customResultUuid.toString(), restoredResults[1].uuid)
    }

    @Test
    fun toValidatedSnapshot_acceptsVersion2BackupWithoutCapsules() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000006a0")
        val snapshot = BackupSnapshot(
            snapshotVersion = 2,
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = listOf(catalogMedicineSnapshot(medicineUuid)),
            medicationGroups = emptyList(),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        val result = snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        assertEquals(medicineUuid.toString(), result.medicines.single().uuid)
    }

    @Test
    fun toValidatedSnapshot_defaultsCjkTextOffsetOffWhenMissing() {
        val snapshot = BackupSnapshot(
            exportedAtEpochMillis = 1L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = emptyList(),
            medicationGroups = emptyList(),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        val validatedSnapshot =
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        assertEquals(false, validatedSnapshot.settings.cjkTextOffsetEnabled)
    }

    @Test
    fun toValidatedSnapshot_rejectsVersionBelowSupportedFloor() {
        val snapshot = BackupSnapshot(
            snapshotVersion = 1,
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = emptyList(),
            medicationGroups = emptyList(),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        try {
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")
            fail("Expected v1 snapshots to be rejected.")
        } catch (error: IllegalArgumentException) {
            assertEquals("Unsupported backup snapshot version: 1.", error.message)
        }
    }

    @Test
    fun toValidatedSnapshot_restoresCapsulePreparationFromVersion3() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000006b0")
        val snapshot = BackupSnapshot(
            snapshotVersion = 3,
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = listOf(capsuleMedicineSnapshot(medicineUuid)),
            medicationGroups = emptyList(),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        val result = snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        val restoredMedicine = result.medicines.single()
        assertEquals(MedicinePreparationType.CAPSULE.name, restoredMedicine.preparationType)
        assertEquals(100.0, restoredMedicine.strengthMgPerTablet!!, 1e-9)
    }

    @Test
    fun toValidatedSnapshot_restoresStockFieldsForMedicines() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000006b2")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-0000000006b3")
        val snapshot = BackupSnapshot(
            snapshotVersion = 3,
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = listOf(
                catalogMedicineSnapshot(medicineUuid).copy(
                    stock = BackupMedicineStockSnapshot(
                        trackingEnabled = true,
                        unitsRemaining = 87.5,
                        unitsLastTotal = 120.0,
                        openContainerAmount = null,
                        warnAtDaysRemaining = 9,
                        stockGeneration = 7L,
                    )
                )
            ),
            medicationGroups = emptyList(),
            medicationLogs = listOf(
                BackupMedicationLogSnapshot(
                    uuid = logUuid.toString(),
                    category = "ESTRADIOL",
                    medicineUuid = medicineUuid.toString(),
                    applicationType = "ORAL",
                    doseInstructionKind = "TABLET_FRACTION",
                    tabletFractionNumerator = 1,
                    tabletFractionDenominator = 1,
                    doseVolumeMl = null,
                    doseWeightGrams = null,
                    gelApplicationArea = "DEFAULT",
                    equivalentE2Mg = 2.0,
                    sourceGroupUuid = null,
                    appliedAtEpochMillis = 200L,
                    appliedAtTimeZoneId = "Asia/Tokyo",
                    scheduledForIso = null,
                    count = 1,
                )
            ),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        val result = snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        val restoredMedicine = result.medicines.single()
        assertEquals(true, restoredMedicine.trackingEnabled)
        assertEquals(87.5, restoredMedicine.stockUnitsRemaining!!, 1e-9)
        assertEquals(120.0, restoredMedicine.stockUnitsLastTotal!!, 1e-9)
        assertNull(restoredMedicine.openContainerAmount)
        assertEquals(9, restoredMedicine.warnAtDaysRemaining)
        assertEquals(7L, restoredMedicine.stockGeneration)

        val restoredLog = result.medicationLogs.single()
        assertEquals(logUuid.toString(), restoredLog.uuid)
    }

    @Test
    fun toValidatedSnapshot_rejectsCapsuleGroupItemWithSublingualApplicationType() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000006c0")
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-0000000006c1")
        val itemUuid = UUID.fromString("00000000-0000-0000-0000-0000000006c2")
        val snapshot = BackupSnapshot(
            snapshotVersion = 3,
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = listOf(capsuleMedicineSnapshot(medicineUuid)),
            medicationGroups = listOf(
                baselineMedicationGroupSnapshot(
                    groupUuid = groupUuid,
                    item = capsuleWholeUnitGroupItemSnapshot(
                        itemUuid = itemUuid,
                        medicineUuid = medicineUuid,
                        applicationType = "SUBLINGUAL",
                    ),
                )
            ),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        try {
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")
            fail("Expected validation to reject sublingual capsule rows.")
        } catch (error: IllegalArgumentException) {
            assertEquals(
                "medication group item $itemUuid applicationType=SUBLINGUAL is not compatible with preparation=CAPSULE.",
                error.message,
            )
        }
    }

    @Test
    fun toValidatedSnapshot_rejectsCapsuleLogWithSublingualApplicationType() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000006c4")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-0000000006c5")
        val snapshot = BackupSnapshot(
            snapshotVersion = 3,
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = listOf(capsuleMedicineSnapshot(medicineUuid)),
            medicationGroups = emptyList(),
            medicationLogs = listOf(
                capsuleWholeUnitLogSnapshot(
                    logUuid = logUuid,
                    medicineUuid = medicineUuid,
                    applicationType = "SUBLINGUAL",
                )
            ),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        try {
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")
            fail("Expected validation to reject a sublingual capsule log.")
        } catch (error: IllegalArgumentException) {
            assertEquals(
                "medication log $logUuid applicationType=SUBLINGUAL is not compatible with preparation=CAPSULE.",
                error.message,
            )
        }
    }

    @Test
    fun toValidatedSnapshot_normalizesLegacyPillWholeUnitMedicationRowsToTabletFraction() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000006d0")
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-0000000006d1")
        val itemUuid = UUID.fromString("00000000-0000-0000-0000-0000000006d2")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-0000000006d3")
        val snapshot = BackupSnapshot(
            snapshotVersion = 3,
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = listOf(catalogMedicineSnapshot(medicineUuid)),
            medicationGroups = listOf(
                baselineMedicationGroupSnapshot(
                    groupUuid = groupUuid,
                    item = pillWholeUnitGroupItemSnapshot(
                        itemUuid = itemUuid,
                        medicineUuid = medicineUuid,
                    ),
                )
            ),
            medicationLogs = listOf(
                pillWholeUnitLogSnapshot(
                    logUuid = logUuid,
                    medicineUuid = medicineUuid,
                )
            ),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        val validatedSnapshot =
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        val restoredItem = validatedSnapshot.medicationGroupItems.single()
        assertEquals("TABLET_FRACTION", restoredItem.doseInstructionKind)
        assertEquals(1, restoredItem.tabletFractionNumerator)
        assertEquals(1, restoredItem.tabletFractionDenominator)
        val restoredLog = validatedSnapshot.medicationLogs.single()
        assertEquals("TABLET_FRACTION", restoredLog.doseInstructionKind)
        assertEquals(1, restoredLog.tabletFractionNumerator)
        assertEquals(1, restoredLog.tabletFractionDenominator)
    }

    @Test
    fun toValidatedSnapshot_rejectsV1BackupVersion() {
        // v1 backups carry MedicationDetails-shaped fields the new restore
        // path cannot map. Surface the version mismatch instead of silently
        // attempting to import an incomplete record.
        val snapshot = BackupSnapshot(
            snapshotVersion = 1,
            exportedAtEpochMillis = Instant.parse("2026-04-26T03:04:05Z").toEpochMilli(),
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = emptyList(),
            medicationGroups = emptyList(),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        try {
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")
            fail("Expected v1 snapshots to be rejected.")
        } catch (error: IllegalArgumentException) {
            assertEquals("Unsupported backup snapshot version: 1.", error.message)
        }
    }

    // The PATCH_OFF singleton round-trips through backup as a medicine row.
    // Validation rebuilds the PatchOff selection from the preparationType
    // marker even though the stored selectionKind is CATALOG, and the
    // identityKey check uses MedicineIdentityKey.patchOff().
    @Test
    fun toValidatedSnapshot_acceptsPatchOffMedicineSnapshot() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000005a0")
        val snapshot = BackupSnapshot(
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = listOf(patchOffMedicineSnapshot(medicineUuid)),
            medicationGroups = emptyList(),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        val validatedSnapshot = snapshot.toValidatedSnapshot(
            expectedPackageName = "com.mkx.hrttracker",
        )

        val restored = validatedSnapshot.medicines.single()
        assertEquals(medicineUuid.toString(), restored.uuid)
        assertEquals("PATCH_OFF", restored.preparationType)
        assertEquals("ESTRADIOL", restored.category)
        assertEquals(MedicineIdentityKey.patchOff(), restored.identityKey)
        assertNull(restored.medicationKey)
        assertNull(restored.patchTotalMg)
        assertNull(restored.patchReleaseRateMcgPerDay)
        assertNull(restored.strengthMgPerTablet)
    }

    @Test
    fun toValidatedSnapshot_acceptsPatchOffSlotWithNullMedicineUuid() {
        // PATCH_OFF is the one application type whose medicineUuid may be
        // null. Validation must accept that null specifically for PATCH_OFF
        // and not raise the missing-medicine error.
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-0000000001a0")
        val itemUuid = UUID.fromString("00000000-0000-0000-0000-0000000001a1")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-0000000001a2")
        val snapshot = patchOffSnapshot(
            groupUuid = groupUuid,
            itemUuid = itemUuid,
            logUuid = logUuid,
        )

        val validatedSnapshot =
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        val restoredItem = validatedSnapshot.medicationGroupItems.single()
        assertEquals(null, restoredItem.medicineUuid)
        assertEquals("PATCH_OFF", restoredItem.applicationType)
        val restoredLog = validatedSnapshot.medicationLogs.single()
        assertEquals(null, restoredLog.medicineUuid)
        assertEquals("PATCH_OFF", restoredLog.applicationType)
    }

    @Test
    fun toValidatedSnapshot_rejectsNullMedicineUuidOnNonPatchOffItem() {
        // Outside PATCH_OFF every group item must carry a medicine reference.
        // Allowing this through would let the restore write a row that
        // violates the data layer's NOT NULL invariants in practice (only
        // PATCH_OFF tolerates a null medicineUuid).
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-0000000002a0")
        val itemUuid = UUID.fromString("00000000-0000-0000-0000-0000000002a1")
        val snapshot = BackupSnapshot(
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = emptyList(),
            medicationGroups = listOf(
                BackupMedicationGroupSnapshot(
                    uuid = groupUuid.toString(),
                    name = "Group",
                    colorKey = "ROSE",
                    notificationsEnabled = false,
                    schedule = BackupMedicationGroupScheduleSnapshot(
                        type = "DAILY",
                        interval = 1,
                        sinceEpochDay = LocalDate.of(2026, 4, 1).toEpochDay(),
                        weeklyDaysOfWeek = emptyList(),
                        times = listOf(BackupMedicationGroupScheduleTimeSnapshot(9, 0)),
                    ),
                    medications = listOf(
                        BackupMedicationGroupItemSnapshot(
                            uuid = itemUuid.toString(),
                            count = 1,
                            medicineUuid = null,
                            applicationType = "ORAL",
                            doseInstructionKind = "WHOLE_UNIT",
                            tabletFractionNumerator = null,
                            tabletFractionDenominator = null,
                            doseVolumeMl = null,
                            doseWeightGrams = null,
                            gelApplicationArea = "DEFAULT",
                        )
                    ),
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    archivedAtEpochMillis = null,
                )
            ),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        try {
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")
            fail("Expected validation to reject a non-PATCH_OFF item without a medicineUuid.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun toValidatedSnapshot_rejectsMedicineUuidNotInMedicinesList() {
        // The medicines list defines the FK universe for the rest of the
        // snapshot. A group item referencing a UUID outside that set would
        // otherwise be inserted with a dangling FK against an empty
        // medicines table.
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-0000000003a0")
        val itemUuid = UUID.fromString("00000000-0000-0000-0000-0000000003a1")
        val unknownMedicineUuid = UUID.fromString("ffffffff-0000-0000-0000-000000000099")
        val snapshot = BackupSnapshot(
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = emptyList(),
            medicationGroups = listOf(
                BackupMedicationGroupSnapshot(
                    uuid = groupUuid.toString(),
                    name = "Group",
                    colorKey = "ROSE",
                    notificationsEnabled = false,
                    schedule = BackupMedicationGroupScheduleSnapshot(
                        type = "DAILY",
                        interval = 1,
                        sinceEpochDay = LocalDate.of(2026, 4, 1).toEpochDay(),
                        weeklyDaysOfWeek = emptyList(),
                        times = listOf(BackupMedicationGroupScheduleTimeSnapshot(9, 0)),
                    ),
                    medications = listOf(
                        BackupMedicationGroupItemSnapshot(
                            uuid = itemUuid.toString(),
                            count = 1,
                            medicineUuid = unknownMedicineUuid.toString(),
                            applicationType = "ORAL",
                            doseInstructionKind = "WHOLE_UNIT",
                            tabletFractionNumerator = null,
                            tabletFractionDenominator = null,
                            doseVolumeMl = null,
                            doseWeightGrams = null,
                            gelApplicationArea = "DEFAULT",
                        )
                    ),
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    archivedAtEpochMillis = null,
                )
            ),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        try {
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")
            fail("Expected validation to reject an item whose medicineUuid is not in medicines.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun toValidatedSnapshot_rejectsActiveGroupReferencingArchivedMedicine() {
        // An active group can never reference an archived medicine — the
        // archived row would have to be unarchived before re-selection in
        // the picker, so importing one would surface an inconsistent UI.
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000004a0")
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-0000000004b0")
        val itemUuid = UUID.fromString("00000000-0000-0000-0000-0000000004b1")
        val snapshot = BackupSnapshot(
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = listOf(
                catalogMedicineSnapshot(medicineUuid)
                    .copy(archivedAtEpochMillis = 50L)
            ),
            medicationGroups = listOf(
                BackupMedicationGroupSnapshot(
                    uuid = groupUuid.toString(),
                    name = "Active group",
                    colorKey = "ROSE",
                    notificationsEnabled = false,
                    schedule = BackupMedicationGroupScheduleSnapshot(
                        type = "DAILY",
                        interval = 1,
                        sinceEpochDay = LocalDate.of(2026, 4, 1).toEpochDay(),
                        weeklyDaysOfWeek = emptyList(),
                        times = listOf(BackupMedicationGroupScheduleTimeSnapshot(9, 0)),
                    ),
                    medications = listOf(
                        BackupMedicationGroupItemSnapshot(
                            uuid = itemUuid.toString(),
                            count = 1,
                            medicineUuid = medicineUuid.toString(),
                            applicationType = "ORAL",
                            doseInstructionKind = "TABLET_FRACTION",
                            tabletFractionNumerator = 1,
                            tabletFractionDenominator = 1,
                            doseVolumeMl = null,
                            doseWeightGrams = null,
                            gelApplicationArea = "DEFAULT",
                        )
                    ),
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    archivedAtEpochMillis = null,
                )
            ),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        try {
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")
            fail("Expected validation to reject an active group referencing an archived medicine.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun toValidatedSnapshot_rejectsDuplicateMedicineUuid() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000005a0")
        val snapshot = BackupSnapshot(
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = listOf(
                catalogMedicineSnapshot(medicineUuid),
                catalogMedicineSnapshot(medicineUuid),
            ),
            medicationGroups = emptyList(),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )

        try {
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")
            fail("Expected validation to reject duplicate medicine UUIDs.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun toValidatedSnapshot_rejectsBackupForDifferentPackage() {
        val snapshot = BackupSnapshot(
            exportedAtEpochMillis = 1L,
            app = BackupAppSnapshot(packageName = "com.example.other"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = emptyList(),
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

    @Test
    fun toValidatedSnapshot_allowsImportedMedicationLogsForImportedMedicineAndPatchOffWithoutMedicine() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000008a0")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-0000000008a1")
        val patchOffLogUuid = UUID.fromString("00000000-0000-0000-0000-0000000008a2")
        val snapshot = validationSnapshot(
            medicines = listOf(importedPillMedicineSnapshot(medicineUuid)),
            medicationLogs = listOf(
                importedPillLogSnapshot(
                    logUuid = logUuid,
                    medicineUuid = medicineUuid,
                    externalId = "dose-8a1",
                ),
                importedPatchOffLogSnapshot(
                    logUuid = patchOffLogUuid,
                    externalId = "patch-off-8a2",
                ),
            ),
        )

        val result = snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        assertEquals(true, result.medicines.single().importedFromExternalTracker)
        val restoredLogs = result.medicationLogs.sortedBy { it.uuid }
        assertEquals("transmtf", restoredLogs[0].importSourceApp)
        assertEquals("dose-8a1", restoredLogs[0].importExternalId)
        assertEquals(medicineUuid.toString(), restoredLogs[0].medicineUuid)
        assertEquals("transmtf", restoredLogs[1].importSourceApp)
        assertEquals("patch-off-8a2", restoredLogs[1].importExternalId)
        assertNull(restoredLogs[1].medicineUuid)
        assertEquals("PATCH_OFF", restoredLogs[1].applicationType)
    }

    @Test
    fun toValidatedSnapshot_rejectsDuplicateLogImportProvenance() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000008b0")
        val snapshot = validationSnapshot(
            medicines = listOf(importedPillMedicineSnapshot(medicineUuid)),
            medicationLogs = listOf(
                importedPillLogSnapshot(
                    logUuid = UUID.fromString("00000000-0000-0000-0000-0000000008b1"),
                    medicineUuid = medicineUuid,
                    externalId = "dose-duplicate",
                ),
                importedPillLogSnapshot(
                    logUuid = UUID.fromString("00000000-0000-0000-0000-0000000008b2"),
                    medicineUuid = medicineUuid,
                    externalId = "dose-duplicate",
                ),
            ),
        )

        assertRejects(snapshot, "Duplicate medication log import provenance")
    }

    @Test
    fun toValidatedSnapshot_rejectsDuplicatePanelImportProvenance() {
        val snapshot = validationSnapshot(
            bloodTestPanels = listOf(
                importedBloodPanelSnapshot(
                    panelUuid = UUID.fromString("00000000-0000-0000-0000-0000000008c1"),
                    resultUuid = UUID.fromString("00000000-0000-0000-0000-0000000008c2"),
                    panelKey = 81L,
                    resultExternalId = "result-8c2",
                ),
                importedBloodPanelSnapshot(
                    panelUuid = UUID.fromString("00000000-0000-0000-0000-0000000008c3"),
                    resultUuid = UUID.fromString("00000000-0000-0000-0000-0000000008c4"),
                    panelKey = 81L,
                    resultExternalId = "result-8c4",
                ),
            ),
        )

        assertRejects(snapshot, "Duplicate blood test panel import provenance")
    }

    @Test
    fun toValidatedSnapshot_rejectsDuplicateResultImportProvenance() {
        val snapshot = validationSnapshot(
            bloodTestPanels = listOf(
                importedBloodPanelSnapshot(
                    panelUuid = UUID.fromString("00000000-0000-0000-0000-0000000008d1"),
                    resultUuid = UUID.fromString("00000000-0000-0000-0000-0000000008d2"),
                    panelKey = 82L,
                    resultExternalId = "result-duplicate",
                ),
                importedBloodPanelSnapshot(
                    panelUuid = UUID.fromString("00000000-0000-0000-0000-0000000008d3"),
                    resultUuid = UUID.fromString("00000000-0000-0000-0000-0000000008d4"),
                    panelKey = 83L,
                    resultExternalId = "result-duplicate",
                ),
            ),
        )

        assertRejects(snapshot, "Duplicate blood test result import provenance")
    }

    @Test
    fun toValidatedSnapshot_rejectsHalfSetImportProvenancePairs() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000008e0")
        val cases = listOf(
            validationSnapshot(
                medicines = listOf(importedPillMedicineSnapshot(medicineUuid)),
                medicationLogs = listOf(
                    importedPillLogSnapshot(
                        logUuid = UUID.fromString("00000000-0000-0000-0000-0000000008e1"),
                        medicineUuid = medicineUuid,
                        externalId = "dose-8e1",
                    ).copy(importExternalId = null)
                ),
            ),
            validationSnapshot(
                bloodTestPanels = listOf(
                    importedBloodPanelSnapshot(
                        panelUuid = UUID.fromString("00000000-0000-0000-0000-0000000008e2"),
                        resultUuid = UUID.fromString("00000000-0000-0000-0000-0000000008e3"),
                        panelKey = 84L,
                        resultExternalId = "result-8e3",
                    ).copy(importPanelKey = null)
                ),
            ),
            validationSnapshot(
                bloodTestPanels = listOf(
                    importedBloodPanelSnapshot(
                        panelUuid = UUID.fromString("00000000-0000-0000-0000-0000000008e4"),
                        resultUuid = UUID.fromString("00000000-0000-0000-0000-0000000008e5"),
                        panelKey = 85L,
                        resultExternalId = "result-8e5",
                    ).let { panel ->
                        panel.copy(
                            results = listOf(panel.results.single().copy(importExternalId = null))
                        )
                    }
                ),
            ),
        )

        cases.forEach { snapshot ->
            assertRejects(snapshot, "import provenance must be either fully set or fully absent")
        }
    }

    @Test
    fun toValidatedSnapshot_rejectsImportOnlyPreparationWhenMedicineIsNotImported() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000008f0")
        val snapshot = validationSnapshot(
            medicines = listOf(
                importedInjectionMedicineSnapshot(medicineUuid)
                    .copy(importedFromExternalTracker = false)
            ),
        )

        assertRejects(snapshot, "requires importedFromExternalTracker")
    }

    @Test
    fun toValidatedSnapshot_rejectsImportedMedicineWithTrackedStock() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000008f1")
        val snapshot = validationSnapshot(
            medicines = listOf(
                importedPillMedicineSnapshot(medicineUuid).copy(
                    stock = BackupMedicineStockSnapshot(
                        trackingEnabled = true,
                        unitsRemaining = 1.0,
                        unitsLastTotal = 1.0,
                        openContainerAmount = null,
                        warnAtDaysRemaining = 14,
                        stockGeneration = 1L,
                    )
                )
            ),
        )

        assertRejects(snapshot, "Imported external medicines cannot track stock")
    }

    @Test
    fun toValidatedSnapshot_rejectsImportedMedicineWithDisabledStockBlock() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000008f7")
        val snapshot = validationSnapshot(
            medicines = listOf(
                importedPillMedicineSnapshot(medicineUuid).copy(
                    stock = BackupMedicineStockSnapshot(
                        trackingEnabled = false,
                        unitsRemaining = 1.0,
                        unitsLastTotal = 1.0,
                        openContainerAmount = null,
                        warnAtDaysRemaining = 14,
                        stockGeneration = 1L,
                    )
                )
            ),
        )

        assertRejects(snapshot, "Imported external medicines cannot include stock blocks")
    }

    @Test
    fun toValidatedSnapshot_rejectsGroupItemsThatReferenceImportedMedicine() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000008f2")
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-0000000008f3")
        val itemUuid = UUID.fromString("00000000-0000-0000-0000-0000000008f4")
        val snapshot = validationSnapshot(
            medicines = listOf(importedPillMedicineSnapshot(medicineUuid)),
            medicationGroups = listOf(
                baselineMedicationGroupSnapshot(
                    groupUuid = groupUuid,
                    item = pillWholeUnitGroupItemSnapshot(
                        itemUuid = itemUuid,
                        medicineUuid = medicineUuid,
                    ),
                )
            ),
        )

        assertRejects(snapshot, "references imported medicine")
    }

    @Test
    fun toValidatedSnapshot_rejectsImportedMedicationLogReferencingNonImportedMedicine() {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000008f5")
        val snapshot = validationSnapshot(
            medicines = listOf(catalogMedicineSnapshot(medicineUuid)),
            medicationLogs = listOf(
                importedPillLogSnapshot(
                    logUuid = UUID.fromString("00000000-0000-0000-0000-0000000008f6"),
                    medicineUuid = medicineUuid,
                    externalId = "dose-8f6",
                )
            ),
        )

        assertRejects(snapshot, "must reference an imported medicine")
    }

    private fun baselineSettings(): BackupSettingsSnapshot = BackupSettingsSnapshot(
        darkModeOption = "FOLLOW_SYSTEM",
        adaptiveColorEnabled = true,
        remindersEnabled = true,
        appLockGracePeriodOption = "IMMEDIATELY",
        hideScreenContentEnabled = false,
        onboardingCompleted = true,
        appLanguageOption = "ENGLISH",
        calibrationDefaultUnits = emptyMap(),
    )

    private fun baselineUserProfile(): BackupUserProfileSnapshot = BackupUserProfileSnapshot(
        weightKg = null,
        weightOriginalValue = null,
        weightOriginalUnit = "KILOGRAMS",
    )

    private fun assertRejects(
        snapshot: BackupSnapshot,
        expectedMessagePart: String,
    ) {
        try {
            snapshot.toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")
            fail("Expected restore validation to reject the snapshot.")
        } catch (error: IllegalArgumentException) {
            assertEquals(
                true,
                error.message.orEmpty().contains(expectedMessagePart),
            )
        }
    }

    private fun validationSnapshot(
        medicines: List<BackupMedicineSnapshot> = emptyList(),
        medicationGroups: List<BackupMedicationGroupSnapshot> = emptyList(),
        medicationLogs: List<BackupMedicationLogSnapshot> = emptyList(),
        bloodTestPanels: List<BackupBloodTestPanelSnapshot> = emptyList(),
    ): BackupSnapshot {
        return BackupSnapshot(
            exportedAtEpochMillis = 0L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = medicines,
            medicationGroups = medicationGroups,
            medicationLogs = medicationLogs,
            customBloodAnalytes = emptyList(),
            bloodTestPanels = bloodTestPanels,
        )
    }

    private fun importedPillMedicineSnapshot(uuid: UUID): BackupMedicineSnapshot {
        return BackupMedicineSnapshot(
            uuid = uuid.toString(),
            selectionKind = "CATALOG",
            medicationKey = MedicationKey.ESTRADIOL.name,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = "ESTRADIOL",
            preparationType = "PILL",
            strengthMgPerTablet = 2.0,
            strengthMgPerVial = null,
            concentrationMgPerMl = null,
            vialVolumeMl = null,
            concentrationPercent = null,
            sachetWeightGrams = null,
            containerWeightGrams = null,
            patchTotalMg = null,
            patchReleaseRateMcgPerDay = null,
            displayName = null,
            identityKey = MedicineIdentityKey.external(
                sourceApp = "transmtf",
                applicationType = MedicationApplicationType.ORAL,
                compound = "ESTRADIOL",
                doseKey = "2",
            ),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = null,
            importedFromExternalTracker = true,
        )
    }

    private fun importedInjectionMedicineSnapshot(uuid: UUID): BackupMedicineSnapshot {
        return BackupMedicineSnapshot(
            uuid = uuid.toString(),
            selectionKind = "CUSTOM",
            medicationKey = MedicationKey.ESTRADIOL_VALERATE.name,
            customMedicationName = "External tracker",
            customMedicationNameNormalized = "external tracker",
            category = "ESTRADIOL",
            preparationType = "IMPORTED_INJECTION",
            strengthMgPerTablet = null,
            strengthMgPerVial = 5.0,
            concentrationMgPerMl = null,
            vialVolumeMl = null,
            concentrationPercent = null,
            sachetWeightGrams = null,
            containerWeightGrams = null,
            patchTotalMg = null,
            patchReleaseRateMcgPerDay = null,
            displayName = null,
            identityKey = MedicineIdentityKey.external(
                sourceApp = "transmtf",
                applicationType = MedicationApplicationType.INJECTION,
                compound = "EV",
                doseKey = "5",
            ),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = null,
            importedFromExternalTracker = true,
        )
    }

    private fun importedPillLogSnapshot(
        logUuid: UUID,
        medicineUuid: UUID,
        externalId: String,
    ): BackupMedicationLogSnapshot {
        return BackupMedicationLogSnapshot(
            uuid = logUuid.toString(),
            category = "ESTRADIOL",
            medicineUuid = medicineUuid.toString(),
            applicationType = "ORAL",
            doseInstructionKind = "TABLET_FRACTION",
            tabletFractionNumerator = 1,
            tabletFractionDenominator = 1,
            doseVolumeMl = null,
            doseWeightGrams = null,
            gelApplicationArea = "DEFAULT",
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAtEpochMillis = 200L,
            appliedAtTimeZoneId = "Asia/Tokyo",
            scheduledForIso = null,
            count = 1,
            importSourceApp = "transmtf",
            importExternalId = externalId,
        )
    }

    private fun importedPatchOffLogSnapshot(
        logUuid: UUID,
        externalId: String,
    ): BackupMedicationLogSnapshot {
        return BackupMedicationLogSnapshot(
            uuid = logUuid.toString(),
            category = "ESTRADIOL",
            medicineUuid = null,
            applicationType = "PATCH_OFF",
            doseInstructionKind = "NOOP",
            tabletFractionNumerator = null,
            tabletFractionDenominator = null,
            doseVolumeMl = null,
            doseWeightGrams = null,
            gelApplicationArea = "DEFAULT",
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAtEpochMillis = 200L,
            appliedAtTimeZoneId = "Asia/Tokyo",
            scheduledForIso = null,
            count = 1,
            importSourceApp = "transmtf",
            importExternalId = externalId,
        )
    }

    private fun importedBloodPanelSnapshot(
        panelUuid: UUID,
        resultUuid: UUID,
        panelKey: Long,
        resultExternalId: String,
    ): BackupBloodTestPanelSnapshot {
        return BackupBloodTestPanelSnapshot(
            uuid = panelUuid.toString(),
            collectedAtInstantEpochMillis = 300L,
            collectedAtTimeZoneId = "Asia/Tokyo",
            notes = null,
            timeSinceLastEstradiolDoseMillis = null,
            timeSinceLastTestosteroneDoseMillis = null,
            createdAtEpochMillis = 300L,
            updatedAtEpochMillis = 300L,
            importSourceApp = "oyama",
            importPanelKey = panelKey,
            results = listOf(
                BackupBloodTestResultSnapshot(
                    uuid = resultUuid.toString(),
                    createdAtEpochMillis = 301L,
                    displayOrder = 0,
                    builtinAnalyteKey = BloodAnalyteKey.E2.storageValue,
                    customAnalyteUuid = null,
                    value = 100.0,
                    unitSnapshot = BloodUnitKey.PG_ML.storageValue,
                    canonicalValue = 100.0,
                    importSourceApp = "oyama",
                    importExternalId = resultExternalId,
                )
            ),
        )
    }

    private fun patchOffMedicineSnapshot(uuid: UUID): BackupMedicineSnapshot {
        return BackupMedicineSnapshot(
            uuid = uuid.toString(),
            // PATCH_OFF reuses CATALOG selectionKind for storage; the PATCH_OFF
            // preparationType is what makes the restore path rebuild PatchOff.
            selectionKind = "CATALOG",
            medicationKey = null,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = "ESTRADIOL",
            preparationType = "PATCH_OFF",
            strengthMgPerTablet = null,
            strengthMgPerVial = null,
            concentrationMgPerMl = null,
            vialVolumeMl = null,
            concentrationPercent = null,
            sachetWeightGrams = null,
            containerWeightGrams = null,
            patchTotalMg = null,
            patchReleaseRateMcgPerDay = null,
            displayName = null,
            identityKey = MedicineIdentityKey.patchOff(),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = null,
        )
    }

    private fun catalogMedicineSnapshot(uuid: UUID): BackupMedicineSnapshot {
        val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
        return BackupMedicineSnapshot(
            uuid = uuid.toString(),
            selectionKind = "CATALOG",
            medicationKey = "ESTRADIOL",
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = "ESTRADIOL",
            preparationType = "PILL",
            strengthMgPerTablet = 2.0,
            strengthMgPerVial = null,
            concentrationMgPerMl = null,
            vialVolumeMl = null,
            concentrationPercent = null,
            sachetWeightGrams = null,
            containerWeightGrams = null,
            patchTotalMg = null,
            patchReleaseRateMcgPerDay = null,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(
                com.mkx.hrttracker.model.medication.MedicationKey.ESTRADIOL,
                preparation,
            ),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = null,
        )
    }

    private fun capsuleMedicineSnapshot(uuid: UUID): BackupMedicineSnapshot {
        val preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0)
        return BackupMedicineSnapshot(
            uuid = uuid.toString(),
            selectionKind = "CUSTOM",
            medicationKey = null,
            customMedicationName = "Progesterone",
            customMedicationNameNormalized = "progesterone",
            category = "CUSTOM",
            preparationType = "CAPSULE",
            strengthMgPerTablet = 100.0,
            strengthMgPerVial = null,
            concentrationMgPerMl = null,
            vialVolumeMl = null,
            concentrationPercent = null,
            sachetWeightGrams = null,
            containerWeightGrams = null,
            patchTotalMg = null,
            patchReleaseRateMcgPerDay = null,
            displayName = null,
            identityKey = MedicineIdentityKey.custom("Progesterone", preparation),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = null,
        )
    }

    private fun baselineMedicationGroupSnapshot(
        groupUuid: UUID,
        item: BackupMedicationGroupItemSnapshot,
    ): BackupMedicationGroupSnapshot {
        return BackupMedicationGroupSnapshot(
            uuid = groupUuid.toString(),
            name = "Group",
            colorKey = "ROSE",
            notificationsEnabled = false,
            schedule = BackupMedicationGroupScheduleSnapshot(
                type = "DAILY",
                interval = 1,
                sinceEpochDay = LocalDate.of(2026, 4, 1).toEpochDay(),
                weeklyDaysOfWeek = emptyList(),
                times = listOf(BackupMedicationGroupScheduleTimeSnapshot(9, 0)),
            ),
            medications = listOf(item),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = null,
        )
    }

    private fun pillWholeUnitGroupItemSnapshot(
        itemUuid: UUID,
        medicineUuid: UUID,
    ): BackupMedicationGroupItemSnapshot {
        return capsuleWholeUnitGroupItemSnapshot(
            itemUuid = itemUuid,
            medicineUuid = medicineUuid,
            applicationType = "ORAL",
        )
    }

    private fun capsuleWholeUnitGroupItemSnapshot(
        itemUuid: UUID,
        medicineUuid: UUID,
        applicationType: String,
    ): BackupMedicationGroupItemSnapshot {
        return BackupMedicationGroupItemSnapshot(
            uuid = itemUuid.toString(),
            count = 1,
            medicineUuid = medicineUuid.toString(),
            applicationType = applicationType,
            doseInstructionKind = "WHOLE_UNIT",
            tabletFractionNumerator = null,
            tabletFractionDenominator = null,
            doseVolumeMl = null,
            doseWeightGrams = null,
            gelApplicationArea = "DEFAULT",
        )
    }

    private fun pillWholeUnitLogSnapshot(
        logUuid: UUID,
        medicineUuid: UUID,
    ): BackupMedicationLogSnapshot {
        return capsuleWholeUnitLogSnapshot(
            logUuid = logUuid,
            medicineUuid = medicineUuid,
            applicationType = "ORAL",
        )
    }

    private fun capsuleWholeUnitLogSnapshot(
        logUuid: UUID,
        medicineUuid: UUID,
        applicationType: String,
    ): BackupMedicationLogSnapshot {
        return BackupMedicationLogSnapshot(
            uuid = logUuid.toString(),
            category = "CUSTOM",
            medicineUuid = medicineUuid.toString(),
            applicationType = applicationType,
            doseInstructionKind = "WHOLE_UNIT",
            tabletFractionNumerator = null,
            tabletFractionDenominator = null,
            doseVolumeMl = null,
            doseWeightGrams = null,
            gelApplicationArea = "DEFAULT",
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAtEpochMillis = 200L,
            appliedAtTimeZoneId = "Asia/Tokyo",
            scheduledForIso = null,
            count = 1,
        )
    }

    private fun patchOffSnapshot(
        groupUuid: UUID,
        itemUuid: UUID,
        logUuid: UUID,
    ): BackupSnapshot {
        return BackupSnapshot(
            exportedAtEpochMillis = Instant.parse("2026-04-26T03:04:05Z").toEpochMilli(),
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = baselineSettings(),
            userProfile = baselineUserProfile(),
            medicines = emptyList(),
            medicationGroups = listOf(
                BackupMedicationGroupSnapshot(
                    uuid = groupUuid.toString(),
                    name = "Patch removals",
                    colorKey = "ROSE",
                    notificationsEnabled = false,
                    schedule = BackupMedicationGroupScheduleSnapshot(
                        type = "DAILY",
                        interval = 1,
                        sinceEpochDay = LocalDate.of(2026, 4, 1).toEpochDay(),
                        weeklyDaysOfWeek = emptyList(),
                        times = listOf(BackupMedicationGroupScheduleTimeSnapshot(20, 0)),
                    ),
                    medications = listOf(
                        BackupMedicationGroupItemSnapshot(
                            uuid = itemUuid.toString(),
                            count = 1,
                            medicineUuid = null,
                            applicationType = "PATCH_OFF",
                            doseInstructionKind = "NOOP",
                            tabletFractionNumerator = null,
                            tabletFractionDenominator = null,
                            doseVolumeMl = null,
                            doseWeightGrams = null,
                            gelApplicationArea = "DEFAULT",
                        )
                    ),
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    archivedAtEpochMillis = null,
                )
            ),
            medicationLogs = listOf(
                BackupMedicationLogSnapshot(
                    uuid = logUuid.toString(),
                    category = "ESTRADIOL",
                    medicineUuid = null,
                    applicationType = "PATCH_OFF",
                    doseInstructionKind = "NOOP",
                    tabletFractionNumerator = null,
                    tabletFractionDenominator = null,
                    doseVolumeMl = null,
                    doseWeightGrams = null,
                    gelApplicationArea = "DEFAULT",
                    equivalentE2Mg = null,
                    sourceGroupUuid = null,
                    appliedAtEpochMillis = 200L,
                    appliedAtTimeZoneId = "Asia/Tokyo",
                    scheduledForIso = null,
                    count = 1,
                )
            ),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )
    }
}
