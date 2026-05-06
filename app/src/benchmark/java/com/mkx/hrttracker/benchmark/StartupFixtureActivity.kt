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
import com.mkx.hrttracker.data.local.UserProfileEntity
import com.mkx.hrttracker.data.repository.EstradiolEquivalentCalculator
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
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
        val groups = benchmarkGroups(today = today, createdAt = createdAt, archivedAt = archivedAt)
        val entries = benchmarkEntries(
            today = today,
            zoneId = zoneId,
            groups = groups,
        )

        settingsRepository.restoreSettings(
            darkModeOption = DarkModeOption.FOLLOW_SYSTEM,
            adaptiveColorEnabled = false,
            remindersEnabled = false,
            showArchivedGroupRecords = true,
            appLockGracePeriodOption = AppLockGracePeriodOption.ONE_MINUTE,
            hideScreenContentEnabled = false,
            onboardingCompleted = true,
            appLanguageOption = AppLanguageOption.ENGLISH,
            calibrationDefaultUnits = emptyMap(),
            homeE2DisplayUnit = BloodUnitKey.PG_ML,
        )
        settingsRepository.setScreenLockProtectionEnabled(false)

        databaseHolder.runTransaction { database ->
            database.medicationLogDao().deleteAllEntries()
            database.medicationGroupDao().deleteAllGroups()
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

    private fun benchmarkGroups(
        today: LocalDate,
        createdAt: Instant,
        archivedAt: Instant,
    ): List<BenchmarkGroup> {
        return listOf(
            benchmarkGroup(
                key = "oral-e2",
                name = "Daily estradiol",
                colorKey = MedicationGroupColorKey.ORCHID,
                scheduleType = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = today.minusDays(180),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                medications = listOf(
                    MedicationDetails(
                        category = MedicationCategory.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL),
                        dose = MedicationDose.MgAsMedicine(2.0),
                    )
                ),
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
                medications = listOf(
                    MedicationDetails(
                        category = MedicationCategory.ESTRADIOL,
                        applicationType = MedicationApplicationType.INJECTION,
                        selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL_VALERATE),
                        dose = MedicationDose.MgAsMedicine(5.0),
                    )
                ),
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
                medications = listOf(
                    MedicationDetails(
                        category = MedicationCategory.ESTRADIOL,
                        applicationType = MedicationApplicationType.PATCH_ON,
                        selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL_PATCH),
                        dose = MedicationDose.PatchReleaseRateMcgPerDay(100.0),
                    )
                ),
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
                medications = listOf(
                    MedicationDetails(
                        category = MedicationCategory.ANTIANDROGEN,
                        applicationType = MedicationApplicationType.ORAL,
                        selection = MedicationSelection.Catalog(MedicationKey.SPIRONOLACTONE),
                        dose = MedicationDose.MgAsMedicine(100.0),
                    )
                ),
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
                medications = listOf(
                    MedicationDetails(
                        category = MedicationCategory.ANTIANDROGEN,
                        applicationType = MedicationApplicationType.ORAL,
                        selection = MedicationSelection.Catalog(MedicationKey.BICALUTAMIDE),
                        dose = MedicationDose.MgAsMedicine(25.0),
                    )
                ),
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
        medications: List<MedicationDetails>,
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
            items = medications.mapIndexed { index, medication ->
                medication.toGroupItemEntity(
                    uuid = uuid("group-item:$key:$index"),
                    groupUuid = groupUuid,
                    sortOrder = index,
                )
            },
            scheduleTimes = timeEntities,
            weeklyDays = weeklyDaysOfWeek.map { dayOfWeek ->
                MedicationGroupWeeklyDayEntity(
                    groupUuid = groupUuid.toString(),
                    dayOfWeek = dayOfWeek.value,
                )
            },
        )
    }

    private fun benchmarkEntries(
        today: LocalDate,
        zoneId: ZoneId,
        groups: List<BenchmarkGroup>,
    ): List<MedicationLogEntryEntity> {
        val groupsByKey = groups.associateBy(BenchmarkGroup::key)
        val entries = mutableListOf<MedicationLogEntryEntity>()
        val startDate = today.minusDays(179)
        generateSequence(startDate) { date -> date.plusDays(1) }
            .takeWhile { date -> !date.isAfter(today) }
            .forEach { date ->
                entries += scheduledEntriesForDate(groupsByKey.getValue("oral-e2"), date, zoneId)
                entries += scheduledEntriesForDate(groupsByKey.getValue("spiro"), date, zoneId)
                if (daysBetween(groupsByKey.getValue("patch-e2"), date) % 4L == 0L) {
                    entries += scheduledEntriesForDate(groupsByKey.getValue("patch-e2"), date, zoneId)
                }
                if (date.dayOfWeek == today.dayOfWeek) {
                    entries += scheduledEntriesForDate(groupsByKey.getValue("injection-e2"), date, zoneId)
                }
            }

        entries += manualEntry(
            key = "manual-yesterday-e2",
            details = MedicationDetails(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.GEL,
                selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL_GEL),
                dose = MedicationDose.GelEquivalentEstradiolMg(0.75),
            ),
            appliedAt = today.minusDays(1).atTime(12, 20).atZone(zoneId).toInstant(),
            zoneId = zoneId,
        )
        entries += manualEntry(
            key = "manual-today-spiro",
            details = MedicationDetails(
                category = MedicationCategory.ANTIANDROGEN,
                applicationType = MedicationApplicationType.ORAL,
                selection = MedicationSelection.Catalog(MedicationKey.SPIRONOLACTONE),
                dose = MedicationDose.MgAsMedicine(50.0),
            ),
            appliedAt = today.atTime(7, 45).atZone(zoneId).toInstant(),
            zoneId = zoneId,
        )
        entries += scheduledEntriesForDate(groupsByKey.getValue("oral-e2"), today.plusDays(1), zoneId)

        return entries.distinctBy(MedicationLogEntryEntity::uuid)
    }

    private fun scheduledEntriesForDate(
        group: BenchmarkGroup,
        date: LocalDate,
        zoneId: ZoneId,
    ): List<MedicationLogEntryEntity> {
        return group.scheduleTimes.flatMapIndexed { timeIndex, time ->
            val scheduledFor = LocalDateTime.of(date, LocalTime.of(time.hourOfDay, time.minuteOfHour))
            group.items.mapIndexed { medicationIndex, item ->
                item.toLogEntryEntity(
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
        details: MedicationDetails,
        appliedAt: Instant,
        zoneId: ZoneId,
    ): MedicationLogEntryEntity {
        return details.toLogEntryEntity(
            uuid = uuid("entry:$key"),
            sourceGroupUuid = null,
            scheduleTimeUuid = null,
            scheduledFor = null,
            appliedAt = appliedAt,
            zoneId = zoneId,
        )
    }

    private fun MedicationDetails.toGroupItemEntity(
        uuid: UUID,
        groupUuid: UUID,
        sortOrder: Int,
    ): MedicationGroupItemEntity {
        return MedicationGroupItemEntity(
            uuid = uuid.toString(),
            groupUuid = groupUuid.toString(),
            sortOrder = sortOrder,
            count = 1,
            category = category.name,
            applicationType = applicationType.name,
            selectionKind = selection.kind.name,
            medicationKey = (selection as? MedicationSelection.Catalog)?.medicationKey?.name,
            customMedicationName = (selection as? MedicationSelection.Custom)?.medicationName,
            doseKind = dose.kind.name,
            doseValueMg = doseValueMg,
            customDoseUnit = resolvedCustomDoseUnit,
            doseValuePercent = (dose as? MedicationDose.GelPercentAndWeight)?.percent,
            doseWeightGrams = (dose as? MedicationDose.GelPercentAndWeight)?.weightGrams,
            doseReleaseRateMcgPerDay = (dose as? MedicationDose.PatchReleaseRateMcgPerDay)?.valueMcgPerDay,
            gelApplicationArea = gelApplicationArea.name,
        )
    }

    private fun MedicationDetails.toLogEntryEntity(
        uuid: UUID,
        sourceGroupUuid: String?,
        scheduleTimeUuid: String?,
        scheduledFor: LocalDateTime?,
        appliedAt: Instant,
        zoneId: ZoneId,
    ): MedicationLogEntryEntity {
        return MedicationLogEntryEntity(
            uuid = uuid.toString(),
            category = category.name,
            applicationType = applicationType.name,
            selectionKind = selection.kind.name,
            medicationKey = (selection as? MedicationSelection.Catalog)?.medicationKey?.name,
            customMedicationName = (selection as? MedicationSelection.Custom)?.medicationName,
            doseKind = dose.kind.name,
            doseValueMg = doseValueMg,
            customDoseUnit = resolvedCustomDoseUnit,
            doseValuePercent = (dose as? MedicationDose.GelPercentAndWeight)?.percent,
            doseWeightGrams = (dose as? MedicationDose.GelPercentAndWeight)?.weightGrams,
            doseReleaseRateMcgPerDay = (dose as? MedicationDose.PatchReleaseRateMcgPerDay)?.valueMcgPerDay,
            dosageMgAsEstradiol = EstradiolEquivalentCalculator.calculate(this),
            sourceGroupUuid = sourceGroupUuid,
            scheduleTimeUuid = scheduleTimeUuid,
            appliedAtEpochMillis = appliedAt.toEpochMilli(),
            appliedAtTimeZoneId = zoneId.id,
            scheduledForIso = scheduledFor?.toString(),
            count = 1,
            gelApplicationArea = gelApplicationArea.name,
        )
    }

    private fun MedicationGroupItemEntity.toLogEntryEntity(
        uuid: UUID,
        sourceGroupUuid: String,
        scheduleTimeUuid: String,
        scheduledFor: LocalDateTime,
        appliedAt: Instant,
        zoneId: ZoneId,
    ): MedicationLogEntryEntity {
        return MedicationLogEntryEntity(
            uuid = uuid.toString(),
            category = category,
            applicationType = applicationType,
            selectionKind = selectionKind,
            medicationKey = medicationKey,
            customMedicationName = customMedicationName,
            doseKind = doseKind,
            doseValueMg = doseValueMg,
            customDoseUnit = customDoseUnit,
            doseValuePercent = doseValuePercent,
            doseWeightGrams = doseWeightGrams,
            doseReleaseRateMcgPerDay = doseReleaseRateMcgPerDay,
            dosageMgAsEstradiol = EstradiolEquivalentCalculator.calculate(toMedicationDetails()),
            sourceGroupUuid = sourceGroupUuid,
            scheduleTimeUuid = scheduleTimeUuid,
            appliedAtEpochMillis = appliedAt.toEpochMilli(),
            appliedAtTimeZoneId = zoneId.id,
            scheduledForIso = scheduledFor.toString(),
            count = count,
            gelApplicationArea = gelApplicationArea,
        )
    }

    private fun MedicationGroupItemEntity.toMedicationDetails(): MedicationDetails {
        val selection = when (selectionKind) {
            "CATALOG" -> MedicationSelection.Catalog(
                medicationKey = requireNotNull(MedicationKey.fromStorageValue(medicationKey))
            )
            else -> MedicationSelection.Custom(customMedicationName.orEmpty())
        }
        val dose = when (doseKind) {
            "MG_AS_MEDICINE" -> MedicationDose.MgAsMedicine(requireNotNull(doseValueMg))
            "GEL_EQUIVALENT_ESTRADIOL_MG" -> MedicationDose.GelEquivalentEstradiolMg(requireNotNull(doseValueMg))
            "GEL_PERCENT_AND_WEIGHT" -> MedicationDose.GelPercentAndWeight(
                percent = requireNotNull(doseValuePercent),
                weightGrams = requireNotNull(doseWeightGrams),
            )
            "PATCH_TOTAL_MG" -> MedicationDose.PatchTotalMg(requireNotNull(doseValueMg))
            "PATCH_RELEASE_RATE_MCG_DAY" -> MedicationDose.PatchReleaseRateMcgPerDay(
                valueMcgPerDay = requireNotNull(doseReleaseRateMcgPerDay)
            )
            else -> MedicationDose.None
        }
        return MedicationDetails(
            category = MedicationCategory.fromStorageValue(category),
            applicationType = MedicationApplicationType.fromStorageValue(applicationType),
            selection = selection,
            dose = dose,
            gelApplicationArea = MedicationGelApplicationArea.fromStorageValue(gelApplicationArea),
            customDoseUnit = MedicationDoseUnit.fromStorageValue(customDoseUnit),
        )
    }

    private val MedicationDetails.doseValueMg: Double?
        get() = when (val resolvedDose = dose) {
            is MedicationDose.MgAsMedicine -> resolvedDose.valueMg
            is MedicationDose.GelEquivalentEstradiolMg -> resolvedDose.valueMg
            is MedicationDose.PatchTotalMg -> resolvedDose.valueMg
            else -> null
        }

    private val MedicationDetails.resolvedCustomDoseUnit: String
        get() = if (selection is MedicationSelection.Custom && dose is MedicationDose.MgAsMedicine) {
            customDoseUnit.storageValue
        } else {
            MedicationDoseUnit.MG.storageValue
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
