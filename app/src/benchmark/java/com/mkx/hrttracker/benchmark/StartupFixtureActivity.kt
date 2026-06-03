package com.mkx.hrttracker.benchmark

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWeeklyDayEntity
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.data.local.UserProfileEntity
import com.mkx.hrttracker.model.medication.DoseInstructionCalculator
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.toEntity
import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class StartupFixtureActivity : AppCompatActivity() {
    @Inject
    lateinit var databaseHolder: DatabaseHolder

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var homeSnapshotRepository: HomeSnapshotRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    seedBenchmarkFixture()
                }
            }
            setResult(if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED)
            showFixtureResult(
                textRes = if (result.isSuccess) {
                    R.string.benchmark_fixture_ready
                } else {
                    R.string.benchmark_fixture_failed
                },
                contentDescription = if (result.isSuccess) {
                    FIXTURE_READY_CONTENT_DESCRIPTION
                } else {
                    FIXTURE_FAILED_CONTENT_DESCRIPTION
                },
            )
        }
    }

    private fun showFixtureResult(
        textRes: Int,
        contentDescription: String,
    ) {
        setContentView(
            TextView(this).apply {
                text = getString(textRes)
                this.contentDescription = contentDescription
                gravity = Gravity.CENTER
            }
        )
    }

    private suspend fun seedBenchmarkFixture() {
        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val createdAt = today.minusDays(220).atStartOfDay(zoneId).toInstant()
        val archivedAt = today.minusDays(30).atTime(12, 0).atZone(zoneId).toInstant()
        val medicines = benchmarkMedicines(createdAt = createdAt)
        val groups = benchmarkGroups(
            today = today,
            createdAt = createdAt,
            archivedAt = archivedAt,
            medicines = medicines,
        )
        val entries = benchmarkEntries(
            today = today,
            zoneId = zoneId,
            groups = groups,
            medicines = medicines,
        )

        settingsRepository.restoreSettings(
            darkModeOption = DarkModeOption.FOLLOW_SYSTEM,
            adaptiveColorEnabled = false,
            pureBlackEnabled = false,
            remindersEnabled = false,
            showArchivedGroupRecords = true,
            hideReferenceRanges = false,
            appLockGracePeriodOption = AppLockGracePeriodOption.ONE_MINUTE,
            hideScreenContentEnabled = false,
            onboardingCompleted = true,
            appLanguageOption = AppLanguageOption.ENGLISH,
            calibrationDefaultUnits = emptySet(),
            homeE2DisplayUnit = AllowedAnalyteUnit.of(BloodAnalyteKey.E2, BloodUnitKey.PG_ML),
            homeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
        )
        settingsRepository.setScreenLockProtectionEnabled(false)

        databaseHolder.runTransaction { database ->
            database.medicationLogDao().deleteAllEntries()
            database.medicationGroupDao().deleteAllGroups()
            database.medicineDao().deleteAll()
            database.userProfileDao().deleteProfile()
            database.bloodTestDao().deleteAllResults()
            database.bloodTestDao().deleteAllPanels()
            database.bloodTestDao().deleteAllCustomAnalytes()

            database.userProfileDao().upsertProfile(
                UserProfileEntity(
                    weightKg = 68.0,
                    weightOriginalValue = 68.0,
                    weightOriginalUnit = "KILOGRAMS",
                    updatedAtEpochMillis = createdAt.toEpochMilli(),
                )
            )
            database.medicineDao().insertAll(medicines.values.map { it.toEntity() })
            database.medicationGroupDao().insertGroups(groups.map(BenchmarkGroup::group))
            database.medicationGroupDao().insertItems(groups.flatMap(BenchmarkGroup::items))
            database.medicationGroupDao().insertScheduleTimes(groups.flatMap(BenchmarkGroup::scheduleTimes))
            database.medicationGroupDao().insertWeeklyDays(groups.flatMap(BenchmarkGroup::weeklyDays))
            database.medicationLogDao().insertEntries(entries)
        }
        homeSnapshotRepository.invalidateHomeSnapshot()
        homeSnapshotRepository.refreshHomeSnapshotIfNeeded(
            now = LocalDateTime.now(),
            force = true,
        )
    }

    /** One representative medicine per benchmark group; keyed by group key. */
    private fun benchmarkMedicines(createdAt: Instant): Map<String, Medicine> {
        return mapOf(
            "oral-e2" to catalogMedicine(
                key = "oral-e2",
                medicationKey = MedicationKey.ESTRADIOL,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                createdAt = createdAt,
            ),
            "injection-e2" to catalogMedicine(
                key = "injection-e2",
                medicationKey = MedicationKey.ESTRADIOL_VALERATE,
                preparation = MedicinePreparation.InjectionMultiUseVial(
                    concentrationMgPerMl = 10.0,
                    vialVolumeMl = 20.0,
                ),
                createdAt = createdAt.plusSeconds(30),
            ),
            "patch-e2" to catalogMedicine(
                key = "patch-e2",
                medicationKey = MedicationKey.ESTRADIOL_PATCH,
                preparation = MedicinePreparation.Patch(
                    specification = MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                        valueMcgPerDay = 100.0,
                    ),
                ),
                createdAt = createdAt.plusSeconds(60),
            ),
            "spiro" to catalogMedicine(
                key = "spiro",
                medicationKey = MedicationKey.SPIRONOLACTONE,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 100.0),
                createdAt = createdAt.plusSeconds(90),
            ),
            "archived" to catalogMedicine(
                key = "archived",
                medicationKey = MedicationKey.BICALUTAMIDE,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 25.0),
                createdAt = createdAt.plusSeconds(120),
            ),
            "gel" to catalogMedicine(
                key = "gel",
                medicationKey = MedicationKey.ESTRADIOL_GEL,
                preparation = MedicinePreparation.GelContainer(
                    concentrationPercent = 0.06,
                    containerWeightGrams = 80.0,
                ),
                createdAt = createdAt.plusSeconds(150),
            ),
        )
    }

    private fun catalogMedicine(
        key: String,
        medicationKey: MedicationKey,
        preparation: MedicinePreparation,
        createdAt: Instant,
    ): Medicine {
        return Medicine(
            uuid = uuid("medicine:$key"),
            selection = MedicineSelection.Catalog(medicationKey),
            category = medicationKey.category,
            preparation = preparation,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(medicationKey, preparation),
            createdAt = createdAt,
            updatedAt = createdAt,
            archivedAt = null,
        )
    }

    private fun benchmarkGroups(
        today: LocalDate,
        createdAt: Instant,
        archivedAt: Instant,
        medicines: Map<String, Medicine>,
    ): List<BenchmarkGroup> {
        return listOf(
            benchmarkGroup(
                key = "oral-e2",
                name = "Daily estradiol",
                colorKey = MedicationGroupColorKey.PLUM,
                scheduleType = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = today.minusDays(180),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                medicine = medicines.getValue("oral-e2"),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 1),
                createdAt = createdAt,
            ),
            benchmarkGroup(
                key = "injection-e2",
                name = "Weekly injection",
                colorKey = MedicationGroupColorKey.ROSE,
                scheduleType = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = today.minusDays(175),
                weeklyDaysOfWeek = setOf(today.dayOfWeek),
                times = listOf(LocalTime.of(21, 0)),
                medicine = medicines.getValue("injection-e2"),
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.VolumeMl(valueMl = 0.5),
                createdAt = createdAt.plusSeconds(60),
            ),
            benchmarkGroup(
                key = "patch-e2",
                name = "Patch cycle",
                colorKey = MedicationGroupColorKey.INDIGO,
                scheduleType = MedicationGroupScheduleType.DAILY,
                interval = 4,
                since = today.minusDays(180),
                times = listOf(LocalTime.of(9, 0)),
                medicine = medicines.getValue("patch-e2"),
                applicationType = MedicationApplicationType.PATCH_ON,
                doseInstruction = DoseInstruction.WholeUnit,
                createdAt = createdAt.plusSeconds(120),
            ),
            benchmarkGroup(
                key = "spiro",
                name = "Night blocker",
                colorKey = MedicationGroupColorKey.TEAL,
                scheduleType = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = today.minusDays(180),
                times = listOf(LocalTime.of(22, 0)),
                medicine = medicines.getValue("spiro"),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 1),
                createdAt = createdAt.plusSeconds(180),
            ),
            benchmarkGroup(
                key = "archived",
                name = "Archived blocker",
                colorKey = MedicationGroupColorKey.AMBER,
                scheduleType = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = today.minusDays(210),
                times = listOf(LocalTime.of(7, 30)),
                medicine = medicines.getValue("archived"),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 1),
                createdAt = createdAt.minusSeconds(60),
                archivedAt = archivedAt,
            ),
        )
    }

    private fun benchmarkGroup(
        key: String,
        name: String,
        colorKey: MedicationGroupColorKey,
        scheduleType: MedicationGroupScheduleType,
        interval: Int,
        since: LocalDate,
        weeklyDaysOfWeek: Set<DayOfWeek> = emptySet(),
        times: List<LocalTime>,
        medicine: Medicine,
        applicationType: MedicationApplicationType,
        doseInstruction: DoseInstruction,
        createdAt: Instant,
        archivedAt: Instant? = null,
    ): BenchmarkGroup {
        val groupUuid = uuid("group:$key")
        val timeEntities = times.mapIndexed { index, time ->
            MedicationGroupScheduleTimeEntity(
                uuid = uuid("schedule-time:$key:$index").toString(),
                groupUuid = groupUuid.toString(),
                sortOrder = index,
                hourOfDay = time.hour,
                minuteOfHour = time.minute,
                effectiveFromLocalIso = since.atStartOfDay().toString(),
            )
        }
        return BenchmarkGroup(
            key = key,
            group = MedicationGroupEntity(
                uuid = groupUuid.toString(),
                name = name,
                colorKey = colorKey.name,
                notificationsEnabled = false,
                scheduleType = scheduleType.name,
                scheduleInterval = interval,
                scheduleSinceEpochDay = since.toEpochDay(),
                createdAtEpochMillis = createdAt.toEpochMilli(),
                updatedAtEpochMillis = (archivedAt ?: createdAt).toEpochMilli(),
                archivedAtEpochMillis = archivedAt?.toEpochMilli(),
                archivedAtLocalIso = archivedAt?.atZone(ZoneId.systemDefault())?.toLocalDateTime()?.toString(),
                includePastScheduledSlots = true,
            ),
            items = listOf(
                groupItemEntity(
                    uuid = uuid("group-item:$key:0"),
                    groupUuid = groupUuid,
                    sortOrder = 0,
                    medicine = medicine,
                    applicationType = applicationType,
                    doseInstruction = doseInstruction,
                ),
            ),
            scheduleTimes = timeEntities,
            weeklyDays = weeklyDaysOfWeek.map { dayOfWeek ->
                MedicationGroupWeeklyDayEntity(
                    groupUuid = groupUuid.toString(),
                    dayOfWeek = dayOfWeek.value,
                )
            },
        )
    }

    private fun groupItemEntity(
        uuid: UUID,
        groupUuid: UUID,
        sortOrder: Int,
        medicine: Medicine,
        applicationType: MedicationApplicationType,
        doseInstruction: DoseInstruction,
    ): MedicationGroupItemEntity {
        return MedicationGroupItemEntity(
            uuid = uuid.toString(),
            groupUuid = groupUuid.toString(),
            sortOrder = sortOrder,
            count = 1,
            medicineUuid = medicine.uuid.toString(),
            applicationType = applicationType.name,
            doseInstructionKind = doseInstruction.kind.name,
            tabletFractionNumerator = (doseInstruction as? DoseInstruction.TabletFraction)?.numerator,
            tabletFractionDenominator = (doseInstruction as? DoseInstruction.TabletFraction)?.denominator,
            doseVolumeMl = (doseInstruction as? DoseInstruction.VolumeMl)?.valueMl,
            doseWeightGrams = (doseInstruction as? DoseInstruction.WeightGrams)?.valueGrams,
        )
    }

    private fun benchmarkEntries(
        today: LocalDate,
        zoneId: ZoneId,
        groups: List<BenchmarkGroup>,
        medicines: Map<String, Medicine>,
    ): List<MedicationLogEntryEntity> {
        val groupsByKey = groups.associateBy(BenchmarkGroup::key)
        val entries = mutableListOf<MedicationLogEntryEntity>()
        val startDate = today.minusDays(179)
        generateSequence(startDate) { date -> date.plusDays(1) }
            .takeWhile { date -> !date.isAfter(today) }
            .forEach { date ->
                entries += scheduledEntriesForDate(groupsByKey.getValue("oral-e2"), medicines.getValue("oral-e2"), date, zoneId)
                entries += scheduledEntriesForDate(groupsByKey.getValue("spiro"), medicines.getValue("spiro"), date, zoneId)
                if (daysBetween(groupsByKey.getValue("patch-e2"), date) % 4L == 0L) {
                    entries += scheduledEntriesForDate(groupsByKey.getValue("patch-e2"), medicines.getValue("patch-e2"), date, zoneId)
                }
                if (date.dayOfWeek == today.dayOfWeek) {
                    entries += scheduledEntriesForDate(groupsByKey.getValue("injection-e2"), medicines.getValue("injection-e2"), date, zoneId)
                }
            }

        entries += manualEntry(
            key = "manual-yesterday-e2",
            medicine = medicines.getValue("gel"),
            applicationType = MedicationApplicationType.GEL,
            doseInstruction = DoseInstruction.WeightGrams(valueGrams = 1.25),
            appliedAt = today.minusDays(1).atTime(12, 20).atZone(zoneId).toInstant(),
            zoneId = zoneId,
        )
        entries += manualEntry(
            key = "manual-today-spiro",
            medicine = medicines.getValue("spiro"),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 2),
            appliedAt = today.atTime(7, 45).atZone(zoneId).toInstant(),
            zoneId = zoneId,
        )
        entries += scheduledEntriesForDate(
            group = groupsByKey.getValue("oral-e2"),
            medicine = medicines.getValue("oral-e2"),
            date = today.plusDays(1),
            zoneId = zoneId,
        )

        return entries.distinctBy(MedicationLogEntryEntity::uuid)
    }

    private fun scheduledEntriesForDate(
        group: BenchmarkGroup,
        medicine: Medicine,
        date: LocalDate,
        zoneId: ZoneId,
    ): List<MedicationLogEntryEntity> {
        return group.scheduleTimes.flatMapIndexed { timeIndex, time ->
            val scheduledFor = LocalDateTime.of(date, LocalTime.of(time.hourOfDay, time.minuteOfHour))
            group.items.mapIndexed { medicationIndex, item ->
                logEntryFromGroupItem(
                    item = item,
                    medicine = medicine,
                    uuid = uuid("entry:${group.key}:${date}:$timeIndex:$medicationIndex"),
                    sourceGroupUuid = group.group.uuid,
                    scheduleTimeUuid = time.uuid,
                    scheduledFor = scheduledFor,
                    appliedAt = scheduledFor.plusMinutes(3).atZone(zoneId).toInstant(),
                    zoneId = zoneId,
                )
            }
        }
    }

    private fun manualEntry(
        key: String,
        medicine: Medicine,
        applicationType: MedicationApplicationType,
        doseInstruction: DoseInstruction,
        appliedAt: Instant,
        zoneId: ZoneId,
    ): MedicationLogEntryEntity {
        return logEntry(
            uuid = uuid("entry:$key"),
            medicine = medicine,
            applicationType = applicationType,
            doseInstruction = doseInstruction,
            count = 1,
            sourceGroupUuid = null,
            scheduleTimeUuid = null,
            scheduledFor = null,
            appliedAt = appliedAt,
            zoneId = zoneId,
        )
    }

    private fun logEntryFromGroupItem(
        item: MedicationGroupItemEntity,
        medicine: Medicine,
        uuid: UUID,
        sourceGroupUuid: String,
        scheduleTimeUuid: String,
        scheduledFor: LocalDateTime,
        appliedAt: Instant,
        zoneId: ZoneId,
    ): MedicationLogEntryEntity {
        val doseInstruction = item.toDoseInstruction()
        return logEntry(
            uuid = uuid,
            medicine = medicine,
            applicationType = MedicationApplicationType.fromStorageValue(item.applicationType),
            doseInstruction = doseInstruction,
            count = item.count,
            sourceGroupUuid = sourceGroupUuid,
            scheduleTimeUuid = scheduleTimeUuid,
            scheduledFor = scheduledFor,
            appliedAt = appliedAt,
            zoneId = zoneId,
        )
    }

    private fun logEntry(
        uuid: UUID,
        medicine: Medicine,
        applicationType: MedicationApplicationType,
        doseInstruction: DoseInstruction,
        count: Int,
        sourceGroupUuid: String?,
        scheduleTimeUuid: String?,
        scheduledFor: LocalDateTime?,
        appliedAt: Instant,
        zoneId: ZoneId,
    ): MedicationLogEntryEntity {
        return MedicationLogEntryEntity(
            uuid = uuid.toString(),
            category = medicine.category.name,
            medicineUuid = medicine.uuid.toString(),
            applicationType = applicationType.name,
            doseInstructionKind = doseInstruction.kind.name,
            tabletFractionNumerator = (doseInstruction as? DoseInstruction.TabletFraction)?.numerator,
            tabletFractionDenominator = (doseInstruction as? DoseInstruction.TabletFraction)?.denominator,
            doseVolumeMl = (doseInstruction as? DoseInstruction.VolumeMl)?.valueMl,
            doseWeightGrams = (doseInstruction as? DoseInstruction.WeightGrams)?.valueGrams,
            equivalentE2Mg = DoseInstructionCalculator.perUnitEquivalentE2Mg(medicine, doseInstruction),
            sourceGroupUuid = sourceGroupUuid,
            scheduleTimeUuid = scheduleTimeUuid,
            appliedAtEpochMillis = appliedAt.toEpochMilli(),
            appliedAtTimeZoneId = zoneId.id,
            scheduledForIso = scheduledFor?.toString(),
            count = count,
        )
    }

    private fun MedicationGroupItemEntity.toDoseInstruction(): DoseInstruction {
        return when (DoseInstructionKind.fromStorageValue(doseInstructionKind)) {
            DoseInstructionKind.TABLET_FRACTION -> DoseInstruction.TabletFraction(
                numerator = requireNotNull(tabletFractionNumerator),
                denominator = requireNotNull(tabletFractionDenominator),
            )
            DoseInstructionKind.VOLUME_ML -> DoseInstruction.VolumeMl(
                valueMl = requireNotNull(doseVolumeMl),
            )
            DoseInstructionKind.WEIGHT_GRAMS -> DoseInstruction.WeightGrams(
                valueGrams = requireNotNull(doseWeightGrams),
            )
            DoseInstructionKind.NOOP -> DoseInstruction.Noop
            DoseInstructionKind.WHOLE_UNIT -> DoseInstruction.WholeUnit
        }
    }

    private fun daysBetween(group: BenchmarkGroup, date: LocalDate): Long {
        return date.toEpochDay() - group.group.scheduleSinceEpochDay
    }

    private fun uuid(seed: String): UUID {
        return UUID.nameUUIDFromBytes("benchmark:$seed".toByteArray(StandardCharsets.UTF_8))
    }
}

private data class BenchmarkGroup(
    val key: String,
    val group: MedicationGroupEntity,
    val items: List<MedicationGroupItemEntity>,
    val scheduleTimes: List<MedicationGroupScheduleTimeEntity>,
    val weeklyDays: List<MedicationGroupWeeklyDayEntity>,
)

private const val FIXTURE_READY_CONTENT_DESCRIPTION = "benchmark-fixture-ready"
private const val FIXTURE_FAILED_CONTENT_DESCRIPTION = "benchmark-fixture-failed"
