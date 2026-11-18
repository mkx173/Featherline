package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.data.backup.BackupAppSnapshot
import com.mkx.hrttracker.data.backup.BackupMedicationGroupItemSnapshot
import com.mkx.hrttracker.data.backup.BackupMedicationGroupScheduleSnapshot
import com.mkx.hrttracker.data.backup.BackupMedicationGroupScheduleTimeSnapshot
import com.mkx.hrttracker.data.backup.BackupMedicationGroupSnapshot
import com.mkx.hrttracker.data.backup.BackupMedicationLogSnapshot
import com.mkx.hrttracker.data.backup.BackupMedicineSnapshot
import com.mkx.hrttracker.data.backup.BackupSettingsSnapshot
import com.mkx.hrttracker.data.backup.BackupSnapshot
import com.mkx.hrttracker.data.backup.BackupUserProfileSnapshot
import com.mkx.hrttracker.data.backup.toValidatedSnapshot
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.data.repository.ScheduledRunwayCalculator
import com.mkx.hrttracker.data.repository.toMedicationGroupModel
import com.mkx.hrttracker.data.repository.toMedicationLogEntryModel
import com.mkx.hrttracker.data.repository.toMedicineModel
import com.mkx.hrttracker.model.settings.ReminderRepeats
import com.mkx.hrttracker.reminder.MedicationReminderSlot
import com.mkx.hrttracker.reminder.buildMissingScheduledLogEntries
import com.mkx.hrttracker.reminder.nextReminderAlert
import com.mkx.hrttracker.ui.main.MainTodayDoseStatus
import com.mkx.hrttracker.ui.main.buildMainDoseSections
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A backup with an ordinary one-tablet plan and two on-time records of Int.MAX_VALUE tablets on
 * one dose goes through the real validator (each count only has to be above 0) and the real
 * entity mappers. Added up as an Int, the records would wrap to -2.
 */
class OverflowingLogCountTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 18)
    private val slotAt = today.atTime(8, 0)

    private data class Restored(val plan: MedicationGroup, val records: List<MedicationLogEntry>)

    /** Through toValidatedSnapshot and the mappers Room's rows are read with. */
    private fun restore(vararg counts: Int): Restored {
        val validated = BackupSnapshot(
            exportedAtEpochMillis = 0L,
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
            userProfile = BackupUserProfileSnapshot(weightKg = null, weightOriginalValue = null, weightOriginalUnit = "KILOGRAMS"),
            medicines = listOf(
                BackupMedicineSnapshot(
                    uuid = MEDICINE,
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
                    identityKey = MedicineIdentityKey.catalog(MedicationKey.ESTRADIOL, MedicinePreparation.Pill(strengthMgPerTablet = 2.0)),
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    archivedAtEpochMillis = null,
                )
            ),
            medicationGroups = listOf(
                BackupMedicationGroupSnapshot(
                    uuid = PLAN,
                    name = "Morning",
                    colorKey = "ROSE",
                    notificationsEnabled = true,
                    schedule = BackupMedicationGroupScheduleSnapshot(
                        type = "DAILY",
                        interval = 1,
                        sinceEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
                        weeklyDaysOfWeek = emptyList(),
                        times = listOf(BackupMedicationGroupScheduleTimeSnapshot(hourOfDay = 8, minuteOfHour = 0)),
                    ),
                    medications = listOf(
                        BackupMedicationGroupItemSnapshot(
                            uuid = "00000000-0000-0000-0000-0000000000af",
                            count = 1,
                            medicineUuid = MEDICINE,
                            applicationType = "ORAL",
                            doseInstructionKind = "TABLET_FRACTION",
                            tabletFractionNumerator = 1,
                            tabletFractionDenominator = 1,
                            doseVolumeMl = null,
                            doseWeightGrams = null,
                            gelApplicationArea = "DEFAULT",
                        )
                    ),
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 200L,
                    archivedAtEpochMillis = null,
                )
            ),
            medicationLogs = counts.mapIndexed { index, count ->
                BackupMedicationLogSnapshot(
                    uuid = "00000000-0000-0000-0000-00000000010$index",
                    category = "ESTRADIOL",
                    medicineUuid = MEDICINE,
                    applicationType = "ORAL",
                    doseInstructionKind = "TABLET_FRACTION",
                    tabletFractionNumerator = 1,
                    tabletFractionDenominator = 1,
                    doseVolumeMl = null,
                    doseWeightGrams = null,
                    gelApplicationArea = "DEFAULT",
                    equivalentE2Mg = 2.0,
                    sourceGroupUuid = PLAN,
                    appliedAtEpochMillis = slotAt.plusMinutes(index.toLong()).atZone(zone).toInstant().toEpochMilli(),
                    appliedAtTimeZoneId = "UTC",
                    scheduledForIso = slotAt.toString(),
                    count = count,
                )
            },
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        ).toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")

        val medicines = validated.medicines.associate { it.uuid to it.toMedicineModel() }
        return Restored(
            plan = MedicationGroupWithItemsEntity(
                group = validated.medicationGroups.single(),
                items = validated.medicationGroupItems,
                scheduleTimes = validated.medicationGroupScheduleTimes,
                weeklyDays = validated.medicationGroupWeeklyDays,
            ).toMedicationGroupModel(medicines),
            records = validated.medicationLogs.map { it.toMedicationLogEntryModel(medicines) },
        )
    }

    private fun Restored.slot() = MedicationGroupSlotKey(plan.schedule.timeSlots.single().uuid, slotAt)
    private fun Restored.signature() = MedicationSignature.fromGroupMedication(plan.medications.single())

    @Test
    fun twoRecordsOfIntMax_restore_andTheCoveredDoseReadsDone() {
        val restored = restore(Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals(listOf(Int.MAX_VALUE, Int.MAX_VALUE), restored.records.map { it.count })

        assertEquals(emptyMap<MedicationSignature, Int>(), missingSlotCounts(restored.plan, restored.slot(), restored.records))
        assertEquals(true, isSlotFulfilled(restored.plan, restored.slot(), restored.records, zone))

        // Home at 08:10: the dose is Done.
        val row = buildMainDoseSections(
            groups = listOf(restored.plan),
            entries = restored.records,
            now = today.atTime(8, 10),
            zoneId = zone,
            clockNow = today.atTime(8, 10).atZone(zone).toInstant(),
        ).today.rows.single()
        assertEquals(MainTodayDoseStatus.DONE, row.status)
        assertEquals(Int.MAX_VALUE, row.loggedCount)
        assertEquals(2, row.fulfillingEntryUuids.size)

        // Reminders: the covered dose rings nothing more (the next alert is tomorrow's dose), and
        // Log now writes nothing.
        val alert = restored.plan.nextReminderAlert(
            after = today.atTime(8, 10).atZone(zone).toInstant(),
            entries = restored.records,
            repeats = ReminderRepeats(),
            skippedThrough = null,
            zone = zone,
        )
        assertEquals(slotAt.plusDays(1), alert?.doseAt)
        assertEquals(
            emptyList<Int>(),
            buildMissingScheduledLogEntries(
                group = restored.plan,
                slot = MedicationReminderSlot(restored.plan.uuid, slotAt, restored.slot().scheduleTimeUuid),
                entries = restored.records,
                appliedAt = today.atTime(8, 10).atZone(zone).toInstant(),
                zoneId = zone,
            ).map { it.count },
        )

        // Stock: today's covered dose owes nothing.
        val tracked = restored.plan.medications.single().medicine!!.copy(
            stock = MedicineStock(trackingEnabled = true, unitsRemaining = 10.0),
        )
        val owed = ScheduledRunwayCalculator.enumerateUnfulfilledOccurrencesForMedicine(
            medicine = tracked,
            activeGroups = listOf(restored.plan),
            logEntries = restored.records,
            nowLocal = today.atTime(8, 10),
            horizonEnd = today.plusDays(2),
            zoneId = zone,
        )
        assertEquals(listOf(slotAt.plusDays(1) to 1.0, slotAt.plusDays(2) to 1.0), owed.map { it.scheduledFor to it.perDose })

        // The next day's dose is untouched.
        assertEquals(
            mapOf(restored.signature() to 1),
            missingSlotCounts(restored.plan, restored.slot().copy(scheduledFor = slotAt.plusDays(1)), restored.records),
        )
    }

    // As an Int this pair would wrap to Int.MIN_VALUE and read as not fulfilled.
    @Test
    fun intMaxAndOne_readsAsNothingMissingAndFulfilled() {
        val restored = restore(Int.MAX_VALUE, 1)
        assertEquals(emptyMap<MedicationSignature, Int>(), missingSlotCounts(restored.plan, restored.slot(), restored.records))
        assertEquals(true, isSlotFulfilled(restored.plan, restored.slot(), restored.records, zone))
    }

    // One record of Int.MAX_VALUE, the most the app's own writers can leave on a dose (a save is
    // clamped to what the plan still misses, and a plan count is an Int): no overflow.
    @Test
    fun oneRecordOfIntMax_isFine() {
        val restored = restore(Int.MAX_VALUE)
        assertEquals(emptyMap<MedicationSignature, Int>(), missingSlotCounts(restored.plan, restored.slot(), restored.records))
        assertEquals(true, isSlotFulfilled(restored.plan, restored.slot(), restored.records, zone))
    }

    private companion object {
        const val MEDICINE = "00000000-0000-0000-0000-000000000010"
        const val PLAN = "00000000-0000-0000-0000-0000000000a1"
    }
}
