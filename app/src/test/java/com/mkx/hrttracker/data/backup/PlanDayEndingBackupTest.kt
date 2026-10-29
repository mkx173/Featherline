package com.mkx.hrttracker.data.backup

import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A plan's own day ending (night owl) is new in snapshot v8. */
class PlanDayEndingBackupTest {

    @Test
    fun roundTrip_preservesNightOwlPlan() {
        val decoded = BackupSnapshotJsonCodec.decode(
            BackupSnapshotJsonCodec.encode(snapshot(dayEndsAtFourAm = true))
        )!!
        val validated = decoded.toValidatedSnapshot(expectedPackageName = PACKAGE)

        assertEquals(8, decoded.snapshotVersion)
        assertTrue(validated.medicationGroups.single().dayEndsAtFourAm)
    }

    @Test
    fun v7BackupWithoutDayEnding_restoresPlanAtMidnight() {
        val v8Json = BackupSnapshotJsonCodec.encode(snapshot(dayEndsAtFourAm = true))
        val v7Json = v8Json
            .replace(""","dayEndsAtFourAm":true""", "")
            .replace(""""snapshotVersion":8""", """"snapshotVersion":7""")
        assertFalse(v7Json.contains("dayEndsAtFourAm"))
        assertTrue(v7Json.contains(""""snapshotVersion":7"""))

        val validated = BackupSnapshotJsonCodec.decode(v7Json)!!
            .toValidatedSnapshot(expectedPackageName = PACKAGE)

        assertFalse(validated.medicationGroups.single().dayEndsAtFourAm)
    }

    private fun snapshot(dayEndsAtFourAm: Boolean) = BackupSnapshot(
        exportedAtEpochMillis = 9_000L,
        app = BackupAppSnapshot(packageName = PACKAGE),
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
        medicines = listOf(medicine()),
        medicationGroups = listOf(
            BackupMedicationGroupSnapshot(
                uuid = GROUP_UUID,
                name = "Evening",
                colorKey = "ROSE",
                notificationsEnabled = false,
                schedule = BackupMedicationGroupScheduleSnapshot(
                    type = "DAILY",
                    interval = 1,
                    sinceEpochDay = LocalDate.of(2026, 4, 1).toEpochDay(),
                    weeklyDaysOfWeek = emptyList(),
                    times = listOf(BackupMedicationGroupScheduleTimeSnapshot(1, 0)),
                ),
                medications = listOf(
                    BackupMedicationGroupItemSnapshot(
                        uuid = ITEM_UUID,
                        count = 1,
                        medicineUuid = MEDICINE_UUID,
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
                dayEndsAtFourAm = dayEndsAtFourAm,
            )
        ),
        medicationLogs = emptyList(),
        customBloodAnalytes = emptyList(),
        bloodTestPanels = emptyList(),
    )

    private fun medicine(): BackupMedicineSnapshot {
        val preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0)
        return BackupMedicineSnapshot(
            uuid = MEDICINE_UUID,
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

    private companion object {
        const val PACKAGE = "com.mkx.hrttracker"
        const val GROUP_UUID = "00000000-0000-0000-0000-000000000001"
        const val ITEM_UUID = "00000000-0000-0000-0000-000000000002"
        const val MEDICINE_UUID = "00000000-0000-0000-0000-000000000010"
    }
}
