package com.mkx.hrttracker.data.backup

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.repository.HomeRepository
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockMutator
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.ReminderCapabilityReconciler
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import com.mkx.hrttracker.reminder.ReminderScheduleStore
import com.mkx.hrttracker.ui.settings.BackupRestoreEvent
import com.mkx.hrttracker.ui.settings.messageRes
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * A hand-edited, re-encrypted backup with an impossible dose time or start date. Once restored, an
 * hour of 24 fails every read of the plans (Home keeps the splash, no reminder pass runs) and a
 * start in the year 547 million overflows every reminder pass. The validator refuses both as not
 * a compatible backup, so the user's data stays. End to end: the real BackupRestoreService
 * (decrypt, decode, toValidatedSnapshot, the Room transaction, the capability reconcile), an
 * in-memory Room database, and the real MedicationGroupRepository, HomeRepository and
 * MedicationReminderScheduler with the real ReminderScheduleStore.
 *
 * Mocked: the Home mutation gate (runs its block; no current cache, as after the failed
 * rebuild), SettingsRepository, the notification manager, and the three Home side inputs
 * (tracked medicines, the anchor, stock projection).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreImpossibleScheduleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseHolder: DatabaseHolder = mockk()
    private val gate: HomeSnapshotRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val notificationManager: ReminderNotificationManager = mockk(relaxed = true)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val crypto = BackupCrypto(TestBackupArgon2KeyDeriver())
    private val zone: ZoneId = ZoneId.systemDefault()
    private val now: Instant = LocalDateTime.of(2026, 11, 18, 12, 0).atZone(zone).toInstant()

    private lateinit var database: HrtTrackerDatabase
    private lateinit var scheduleStore: ReminderScheduleStore
    private lateinit var groups: MedicationGroupRepository
    private lateinit var logs: MedicationLogRepository
    private lateinit var scheduler: MedicationReminderScheduler
    private lateinit var service: BackupRestoreService
    private lateinit var home: HomeRepository

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        database = Room.inMemoryDatabaseBuilder(context, HrtTrackerDatabase::class.java).build()
        every { databaseHolder.get() } returns database
        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        coEvery { databaseHolder.withTransaction<Any?>(any()) } coAnswers {
            database.withTransaction { firstArg<suspend (HrtTrackerDatabase) -> Any?>().invoke(database) }
        }
        coEvery { databaseHolder.runTransaction(any()) } coAnswers {
            database.withTransaction { firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database) }
        }
        coEvery { gate.runHomeDataMutation<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
        every { gate.generation } returns MutableStateFlow(1L)
        // A cold start after the restore: its rebuild failed (HomeSnapshotRepository.build maps the
        // same rows), so no part is current and Home waits for Room.
        every { gate.currentCache } returns flowOf(null)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = true)
        every { settingsRepository.settingsState } returns MutableStateFlow(SettingsState())
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns flowOf(HomeE2ChartWindowOption.SEVEN_DAYS)
        every { notificationManager.activeDoseReminders() } returns emptyMap()

        scheduleStore = ReminderScheduleStore(
            PreferenceDataStoreFactory.create(scope = appScope) {
                File(context.cacheDir, "reminder_schedule_${UUID.randomUUID()}.preferences_pb")
            }
        )
        groups = MedicationGroupRepository(databaseHolder, gate, appScope)
        logs = MedicationLogRepository(databaseHolder, gate, MedicineStockMutator(), appScope)
        scheduler = MedicationReminderScheduler(
            context = context,
            medicationGroupRepository = groups,
            medicationLogRepository = logs,
            settingsRepository = settingsRepository,
            reminderScheduleStore = scheduleStore,
            reminderNotificationManager = notificationManager,
            homeSnapshotRepository = gate,
            sdkInt = 34,
            instantNow = { now },
        )
        service = BackupRestoreService(
            context = context,
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = gate,
            medicationReminderScheduler = scheduler,
            reminderCapabilityReconciler = ReminderCapabilityReconciler(
                context = context,
                appScope = appScope,
                settingsRepository = settingsRepository,
                medicationReminderScheduler = scheduler,
            ),
            widgetAppearanceRepository = mockk(relaxed = true),
            backupCrypto = crypto,
            healthConnectCapabilityReconciler = mockk(relaxed = true),
        )
        home = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = gate,
            medicineStockRepository = mockk<MedicineStockRepository> {
                every { projectAll(any(), any(), any(), any(), any()) } returns emptyList()
            },
            medicineRepository = mockk<MedicineRepository> {
                every { observeAllActiveTracked() } returns flowOf(emptyList())
            },
            medicationLogRepository = logs,
            journalRepository = mockk<JournalRepository> {
                every { observePinnedTrackedDates() } returns flowOf(emptyList())
            },
        )
    }

    @After
    fun tearDown() {
        appScope.cancel()
        database.close()
    }

    @Test
    fun anImpossibleDoseTime_isRefusedAsNotACompatibleBackup_andKeepsTheUsersPlan() = runBlocking {
        restoreTheUsersPlan()

        for ((hour, minute) in listOf(24 to 0, -1 to 0, 8 to 60, 8 to -1)) {
            assertRefusedAsIncompatible(backup(plan(BAD_PLAN, hour = hour, minute = minute)))
        }
        assertTheUsersPlanStands()
    }

    @Test
    fun aStartDatePastTheYear9999_isRefusedAsNotACompatibleBackup_andKeepsTheUsersPlan() = runBlocking {
        restoreTheUsersPlan()

        // The year 547 million: its alert's epoch millis overflow a Long in every reminder pass.
        assertRefusedAsIncompatible(
            backup(plan(GOOD_PLAN, hour = 8), plan(BAD_PLAN, hour = 8, sinceEpochDay = 200_000_000_000L)),
        )
        assertRefusedAsIncompatible(
            backup(plan(BAD_PLAN, hour = 8, sinceEpochDay = LocalDate.of(10_000, 1, 1).toEpochDay())),
        )
        assertTheUsersPlanStands()
    }

    @Test
    fun anImpossibleSlotStartOrArchiveTime_isRefusedAsNotACompatibleBackup_andKeepsTheUsersPlan() = runBlocking {
        restoreTheUsersPlan()

        // The year 300 million overflows every reminder pass; the end of time throws in it, and
        // in Home's and Plan's dose lists at every launch.
        for (iso in listOf("+300000000-01-01T08:00:00", "+999999999-12-31T23:59:00")) {
            assertRefusedAsIncompatible(backup(plan(GOOD_PLAN, hour = 8), plan(BAD_PLAN, hour = 8).effectiveFrom(iso)))
        }
        assertRefusedAsIncompatible(
            backup(plan(BAD_PLAN, hour = 8).copy(archivedAtEpochMillis = 1L, archivedAtLocalIso = "+999999999-12-31T23:59:00")),
        )
        assertTheUsersPlanStands()
    }

    @Test
    fun aStartDateInTheYear0_isRefused_andTheYear1Restores() = runBlocking {
        restoreTheUsersPlan()
        assertRefusedAsIncompatible(backup(plan(BAD_PLAN, hour = 8, sinceEpochDay = LocalDate.of(0, 12, 31).toEpochDay())))
        assertTheUsersPlanStands()

        assertEquals(
            BackupRestoreOutcome.RESTORED,
            service.restoreBackupBytes(
                backup(plan(BAD_PLAN, hour = 8, sinceEpochDay = LocalDate.of(1, 1, 1).toEpochDay())),
                "password",
            ),
        )
        assertEquals(listOf(UUID.fromString(BAD_PLAN)), groups.getGroups().map { it.uuid })
    }

    private fun BackupMedicationGroupSnapshot.effectiveFrom(iso: String) = copy(
        schedule = schedule.copy(times = schedule.times.map { it.copy(effectiveFromLocalIso = iso) }),
    )

    @Test
    fun theLastAllowedStartDate_restoresAndArmsTheOtherPlans() = runBlocking {
        val outcome = service.restoreBackupBytes(
            backup(
                plan(GOOD_PLAN, hour = 8),
                plan(BAD_PLAN, hour = 8, sinceEpochDay = LocalDate.of(9999, 12, 31).toEpochDay()),
            ),
            "password",
        )

        assertEquals(BackupRestoreOutcome.RESTORED, outcome)
        assertEquals(2, groups.getGroups().size)
        assertTrue(UUID.fromString(GOOD_PLAN) in scheduleStore.getScheduledReminders().keys)
    }

    private suspend fun restoreTheUsersPlan() {
        assertEquals(
            BackupRestoreOutcome.RESTORED,
            service.restoreBackupBytes(backup(plan(GOOD_PLAN, hour = 8)), "password"),
        )
        assertTheUsersPlanStands()
    }

    private suspend fun assertRefusedAsIncompatible(bytes: ByteArray) {
        val error = runCatching { service.restoreBackupBytes(bytes, "password") }.exceptionOrNull()
        assertTrue("restore: $error", error is IllegalArgumentException)
        assertEquals(
            R.string.settings_backup_restore_incompatible_file,
            BackupRestoreEvent.Failure(error!!).messageRes(),
        )
    }

    /** Nothing was replaced: the plan reads, Home gets its data and the plan stays armed. */
    private suspend fun assertTheUsersPlanStands() {
        assertEquals(listOf(UUID.fromString(GOOD_PLAN)), groups.getGroups().map { it.uuid })
        assertNotNull("Home gets its data", firstHomeInputs())
        scheduler.rescheduleAll()
        assertEquals(setOf(UUID.fromString(GOOD_PLAN)), scheduleStore.getScheduledReminders().keys)
    }

    private suspend fun firstHomeInputs() = withTimeoutOrNull(8_000) {
        home.observeHomeInputs(
            date = LocalDate.of(2026, 11, 18),
            nowFlow = MutableStateFlow(LocalDateTime.of(2026, 11, 18, 12, 0)),
            zoneId = zone,
        ).first()
    }

    private fun backup(vararg plans: BackupMedicationGroupSnapshot): ByteArray = crypto.encryptSnapshotJson(
        json = BackupSnapshotJsonCodec.encode(
            BackupSnapshot(
                exportedAtEpochMillis = 0L,
                app = BackupAppSnapshot(packageName = BACKUP_APP_PACKAGE_NAME),
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
                medicines = listOf(estradiolTablet()),
                medicationGroups = plans.toList(),
                medicationLogs = emptyList(),
                customBloodAnalytes = emptyList(),
                bloodTestPanels = emptyList(),
            )
        ),
        password = "password".toCharArray(),
    )

    private fun plan(
        uuid: String,
        hour: Int,
        minute: Int = 0,
        sinceEpochDay: Long = LocalDate.of(2026, 11, 1).toEpochDay(),
    ) = BackupMedicationGroupSnapshot(
        uuid = uuid,
        name = "Plan ${uuid.takeLast(1)}",
        colorKey = "ROSE",
        notificationsEnabled = true,
        schedule = BackupMedicationGroupScheduleSnapshot(
            type = "DAILY",
            interval = 1,
            sinceEpochDay = sinceEpochDay,
            weeklyDaysOfWeek = emptyList(),
            times = listOf(BackupMedicationGroupScheduleTimeSnapshot(hourOfDay = hour, minuteOfHour = minute)),
        ),
        medications = listOf(
            BackupMedicationGroupItemSnapshot(
                uuid = uuid.dropLast(1) + "f",
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

    private fun estradiolTablet() = BackupMedicineSnapshot(
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

    private companion object {
        const val MEDICINE = "00000000-0000-0000-0000-000000000010"
        const val GOOD_PLAN = "00000000-0000-0000-0000-0000000000a1"
        const val BAD_PLAN = "00000000-0000-0000-0000-0000000000b1"
    }
}
