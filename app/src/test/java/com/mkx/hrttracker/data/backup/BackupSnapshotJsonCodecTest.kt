package com.mkx.hrttracker.data.backup

import org.junit.Assert.assertEquals
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
        assertEquals(0, BackupSnapshotJsonCodec.peekSnapshotVersion("""{"exportedAtEpochMillis":0}"""))
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
    fun decodingMedicineWithoutStockBlock_yieldsNullStock() {
        val json = BackupSnapshotJsonCodec.encode(makeSnapshotWithSingleMedicine(stock = null))
            .replace(""","stock":null""", "")

        val decoded = BackupSnapshotJsonCodec.decode(json)!!
        val medicine = decoded.medicines.single()

        assertNull(medicine.stock)
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
            bloodTestPanels = emptyList(),
        )
    }
}
