package com.mkx.hrttracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BackupSnapshotJsonCodecTest {

    // A real v1 backup lacks the top-level `medicines` array that v2 added as
    // a required field. Full decode would throw on the missing field; the peek
    // path must still extract `snapshotVersion` so the restore flow can raise
    // the intended unsupported-version error.
    @Test
    fun peekSnapshotVersion_extractsVersionFromV1ShapeMissingMedicines() {
        val v1Json = """
            {
              "snapshotVersion": 1,
              "exportedAtEpochMillis": 0,
              "app": { "packageName": "com.mkx.hrttracker" }
            }
        """.trimIndent()

        assertEquals(1, BackupSnapshotJsonCodec.peekSnapshotVersion(v1Json))
    }

    @Test
    fun peekSnapshotVersion_returnsZeroWhenFieldAbsent() {
        assertEquals(
            0,
            BackupSnapshotJsonCodec.peekSnapshotVersion("""{"exportedAtEpochMillis":0}""")
        )
    }

    @Test
    fun peekSnapshotVersion_returnsNullForJsonNullLiteral() {
        assertNull(BackupSnapshotJsonCodec.peekSnapshotVersion("null"))
    }

    @Test
    fun roundTrip_medicineWithStockBlock_preservesAllFields() {
        val snapshot = makeSnapshotWithSingleMedicine(
            stock = BackupMedicineStockSnapshot(
                trackingEnabled = true,
                unitsRemaining = 87.0,
                unitsLastTotal = 120.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                stockGeneration = 5L,
            )
        )

        val json = BackupSnapshotJsonCodec.encode(snapshot)
        val decoded = BackupSnapshotJsonCodec.decode(json)
        val medicine = decoded!!.medicines.single()
        val stock = medicine.stock!!

        assertEquals(true, stock.trackingEnabled)
        assertEquals(87.0, stock.unitsRemaining!!, 0.0)
        assertEquals(120.0, stock.unitsLastTotal!!, 0.0)
        assertNull(stock.openContainerAmount)
        assertEquals(14, stock.warnAtDaysRemaining)
        assertEquals(5L, stock.stockGeneration)
    }

    @Test
    fun roundTrip_logEntry_preservesCoreFields() {
        val snapshot = makeSnapshotWithSingleLog()

        val json = BackupSnapshotJsonCodec.encode(snapshot)
        val decoded = BackupSnapshotJsonCodec.decode(json)
        val log = decoded!!.medicationLogs.single()

        assertEquals("22222222-2222-2222-2222-222222222222", log.uuid)
        assertEquals("ORAL", log.applicationType)
    }

    @Test
    fun roundTrip_logEntry_preservesDoseAmountDelta() {
        val snapshot = makeSnapshotWithSingleLog()

        val json = BackupSnapshotJsonCodec.encode(snapshot)
        val decoded = BackupSnapshotJsonCodec.decode(json)
        val log = decoded!!.medicationLogs.single()

        assertEquals(0.1, log.doseAmountDelta!!, 1e-9)
    }

    @Test
    fun roundTrip_importedMedicineAndProvenanceFields_preservesAllFields() {
        val snapshot = baselineSnapshot(
            medicines = listOf(
                BackupMedicineSnapshot(
                    uuid = "33333333-3333-3333-3333-333333333331",
                    selectionKind = "CUSTOM",
                    medicationKey = "ESTRADIOL_VALERATE",
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
                    identityKey = "E|transmtf|INJECTION|EV|5",
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    archivedAtEpochMillis = null,
                    importedFromExternalTracker = true,
                    stock = null,
                ),
                BackupMedicineSnapshot(
                    uuid = "33333333-3333-3333-3333-333333333332",
                    selectionKind = "CUSTOM",
                    medicationKey = "ESTRADIOL",
                    customMedicationName = "External tracker",
                    customMedicationNameNormalized = "external tracker",
                    category = "ESTRADIOL",
                    preparationType = "IMPORTED_GEL",
                    strengthMgPerTablet = null,
                    strengthMgPerVial = 0.75,
                    concentrationMgPerMl = null,
                    vialVolumeMl = null,
                    concentrationPercent = null,
                    sachetWeightGrams = null,
                    containerWeightGrams = null,
                    patchTotalMg = null,
                    patchReleaseRateMcgPerDay = null,
                    displayName = null,
                    identityKey = "E|oyama|GEL|ESTRADIOL|0.75",
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    archivedAtEpochMillis = null,
                    importedFromExternalTracker = true,
                    stock = null,
                ),
            ),
            medicationLogs = listOf(
                BackupMedicationLogSnapshot(
                    uuid = "44444444-4444-4444-4444-444444444444",
                    category = "ESTRADIOL",
                    medicineUuid = "33333333-3333-3333-3333-333333333331",
                    applicationType = "INJECTION",
                    doseInstructionKind = "WHOLE_UNIT",
                    tabletFractionNumerator = null,
                    tabletFractionDenominator = null,
                    doseVolumeMl = null,
                    doseWeightGrams = null,
                    gelApplicationArea = "DEFAULT",
                    equivalentE2Mg = 5.0,
                    doseAmountDelta = null,
                    sourceGroupUuid = null,
                    scheduleTimeUuid = null,
                    appliedAtEpochMillis = 1L,
                    appliedAtTimeZoneId = "UTC",
                    scheduledForIso = null,
                    count = 1,
                    importSourceApp = "transmtf",
                    importExternalId = "dose-1",
                )
            ),
            bloodTestPanels = listOf(
                BackupBloodTestPanelSnapshot(
                    uuid = "55555555-5555-5555-5555-555555555555",
                    collectedAtInstantEpochMillis = 2L,
                    collectedAtTimeZoneId = "UTC",
                    notes = null,
                    timeSinceLastEstradiolDoseMillis = null,
                    timeSinceLastTestosteroneDoseMillis = null,
                    createdAtEpochMillis = 2L,
                    updatedAtEpochMillis = 2L,
                    importSourceApp = "oyama",
                    importPanelKey = 42L,
                    results = listOf(
                        BackupBloodTestResultSnapshot(
                            uuid = "66666666-6666-6666-6666-666666666666",
                            createdAtEpochMillis = 3L,
                            displayOrder = 0,
                            builtinAnalyteKey = "e2",
                            customAnalyteUuid = null,
                            value = 367.1,
                            unitSnapshot = "pmol_l",
                            canonicalValue = 100.0,
                            importSourceApp = "oyama",
                            importExternalId = "result-1",
                        )
                    ),
                )
            ),
        )

        val json = BackupSnapshotJsonCodec.encode(snapshot)
        val decoded = BackupSnapshotJsonCodec.decode(json)!!

        assertEquals(true, decoded.medicines[0].importedFromExternalTracker)
        assertEquals("IMPORTED_INJECTION", decoded.medicines[0].preparationType)
        assertEquals("IMPORTED_GEL", decoded.medicines[1].preparationType)
        assertEquals("transmtf", decoded.medicationLogs.single().importSourceApp)
        assertEquals("dose-1", decoded.medicationLogs.single().importExternalId)
        assertEquals("oyama", decoded.bloodTestPanels.single().importSourceApp)
        assertEquals(42L, decoded.bloodTestPanels.single().importPanelKey)
        assertEquals("oyama", decoded.bloodTestPanels.single().results.single().importSourceApp)
        assertEquals("result-1", decoded.bloodTestPanels.single().results.single().importExternalId)
    }

    @Test
    fun decodingLogEntryWithoutDoseAmountDelta_yieldsNullDoseAmountDelta() {
        val json = BackupSnapshotJsonCodec.encode(makeSnapshotWithSingleLog())
            .replace(""","doseAmountDelta":0.1""", "")

        val decoded = BackupSnapshotJsonCodec.decode(json)!!
        val log = decoded.medicationLogs.single()

        assertNull(log.doseAmountDelta)
    }

    @Test
    fun decodingMedicineWithoutStockBlock_yieldsNullStock() {
        val json = BackupSnapshotJsonCodec.encode(makeSnapshotWithSingleMedicine(stock = null))
            .replace(""","stock":null""", "")

        val decoded = BackupSnapshotJsonCodec.decode(json)!!
        val medicine = decoded.medicines.single()

        assertNull(medicine.stock)
    }

    @Test
    fun roundTrip_trackedDatesAndNotes_preservesAllFields() {
        val trackedDates = listOf(
            BackupTrackedDateSnapshot(
                uuid = "77777777-7777-7777-7777-777777777777",
                name = "Started estradiol",
                iconKey = "medication",
                dateIso = "2024-04-01",
                paletteKey = "ROSE",
                heroBackgroundKey = "TRANSGENDER",
                pinnedOrder = 0,
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 2_000L,
            ),
            BackupTrackedDateSnapshot(
                uuid = "88888888-8888-8888-8888-888888888888",
                name = "Follow-up appointment",
                iconKey = "calendar",
                dateIso = "2026-06-16",
                paletteKey = null,
                pinnedOrder = null,
                createdAtEpochMillis = 3_000L,
                updatedAtEpochMillis = 4_000L,
            ),
        )
        val notes = listOf(
            BackupNoteSnapshot(
                uuid = "99999999-9999-9999-9999-999999999999",
                dateIso = "2026-06-16",
                text = "Slept well.",
                createdAtEpochMillis = 5_000L,
                updatedAtEpochMillis = 6_000L,
            )
        )
        val snapshot = baselineSnapshot().copy(
            trackedDates = trackedDates,
            notes = notes,
        )

        val decoded = BackupSnapshotJsonCodec.decode(BackupSnapshotJsonCodec.encode(snapshot))

        assertEquals(trackedDates, decoded?.trackedDates)
        assertEquals(notes, decoded?.notes)
    }

    @Test
    fun decodingBackupWithoutTrackedDatesAndNotes_defaultsToEmptyLists() {
        val json = legacyV5JsonWithoutJournalFields()

        assertFalse(json.contains(""""trackedDates""""))
        assertFalse(json.contains(""""notes""""))

        val decoded = BackupSnapshotJsonCodec.decode(json)

        assertEquals(emptyList<BackupTrackedDateSnapshot>(), decoded?.trackedDates)
        assertEquals(emptyList<BackupNoteSnapshot>(), decoded?.notes)
    }

    @Test
    fun currentBackupSnapshotVersion_isNine() {
        assertEquals(9, CURRENT_BACKUP_SNAPSHOT_VERSION)
    }

    private fun makeSnapshotWithSingleMedicine(
        stock: BackupMedicineStockSnapshot?,
    ): BackupSnapshot {
        return baselineSnapshot(
            medicines = listOf(
                BackupMedicineSnapshot(
                    uuid = "11111111-1111-1111-1111-111111111111",
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
                    identityKey = "C|ESTRADIOL|PILL|s=2",
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    archivedAtEpochMillis = null,
                    displayDoseUnit = "MG",
                    stock = stock,
                )
            )
        )
    }

    private fun makeSnapshotWithSingleLog(): BackupSnapshot {
        return baselineSnapshot(
            medicationLogs = listOf(
                BackupMedicationLogSnapshot(
                    uuid = "22222222-2222-2222-2222-222222222222",
                    category = "ESTRADIOL",
                    medicineUuid = "11111111-1111-1111-1111-111111111111",
                    applicationType = "ORAL",
                    doseInstructionKind = "TABLET_FRACTION",
                    tabletFractionNumerator = 1,
                    tabletFractionDenominator = 1,
                    doseVolumeMl = null,
                    doseWeightGrams = null,
                    gelApplicationArea = "DEFAULT",
                    equivalentE2Mg = 2.0,
                    doseAmountDelta = 0.1,
                    sourceGroupUuid = null,
                    scheduleTimeUuid = null,
                    appliedAtEpochMillis = 0L,
                    appliedAtTimeZoneId = "UTC",
                    scheduledForIso = null,
                    count = 1,
                )
            )
        )
    }

    private fun baselineSnapshot(
        medicines: List<BackupMedicineSnapshot> = emptyList(),
        medicationLogs: List<BackupMedicationLogSnapshot> = emptyList(),
        bloodTestPanels: List<BackupBloodTestPanelSnapshot> = emptyList(),
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
            medicines = medicines,
            medicationGroups = emptyList(),
            medicationLogs = medicationLogs,
            customBloodAnalytes = emptyList(),
            bloodTestPanels = bloodTestPanels,
        )
    }

    private fun legacyV5JsonWithoutJournalFields(): String {
        return """
            {
              "snapshotVersion": 5,
              "exportedAtEpochMillis": 0,
              "app": {
                "packageName": "com.mkx.hrttracker"
              },
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
                "weightKg": null,
                "weightOriginalValue": null,
                "weightOriginalUnit": "KILOGRAMS"
              },
              "medicines": [],
              "medicationGroups": [],
              "medicationLogs": [],
              "customBloodAnalytes": [],
              "bloodTestPanels": []
            }
        """.trimIndent()
    }
}
