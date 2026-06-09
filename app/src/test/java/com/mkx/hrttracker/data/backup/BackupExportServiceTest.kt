package com.mkx.hrttracker.data.backup

import android.content.Context
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.bloodtest.CustomBloodAnalyte
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class BackupExportServiceTest {
    private val context: Context = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()
    private val medicineRepository: MedicineRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val bloodTestRepository: BloodTestRepository = mockk()

    private lateinit var backupCrypto: BackupCrypto
    private lateinit var service: BackupExportService
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        every { context.packageName } returns "com.mkx.hrttracker"
        cacheDir = Files.createTempDirectory("backup-export-service-test-").toFile()
        every { context.cacheDir } returns cacheDir
        every { settingsRepository.stockNudgeEnabledFlow } returns flowOf(true)
        every { settingsRepository.stockNudgeUserEnabledFlow } returns flowOf(false)
        backupCrypto = BackupCrypto(TestBackupArgon2KeyDeriver())
        service = BackupExportService(
            context = context,
            settingsRepository = settingsRepository,
            userProfileRepository = userProfileRepository,
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            bloodTestRepository = bloodTestRepository,
            backupCrypto = backupCrypto,
        )
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun backupExport_staysAtVersion3_doseAmountDeltaIsAdditive() {
        // doseAmountDelta is a nullable additive field, so it ships without a
        // version bump — older readers default it to null. Catches a stale bump.
        assertEquals(3, CURRENT_BACKUP_SNAPSHOT_VERSION)
    }

    @Test
    fun buildBackupSnapshotJson_exportsStockNudgeEnabledFalse() = runTest {
        every { settingsRepository.onboardingCompleted } returns flowOf(false)
        every { settingsRepository.stockNudgeEnabledFlow } returns flowOf(false)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile()
        coEvery { medicineRepository.getAll() } returns emptyList()
        coEvery { medicationGroupRepository.getGroups() } returns emptyList()
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        coEvery { bloodTestRepository.getCustomAnalytes() } returns emptyList()
        coEvery { bloodTestRepository.getPanels() } returns emptyList()

        val snapshot = BackupSnapshotJsonCodec.decode(
            service.buildBackupSnapshotJson(Instant.parse("2026-04-26T03:04:05Z"))
        )!!

        assertEquals(false, snapshot.settings.stockNudgeEnabled)
    }

    @Test
    fun buildBackupSnapshotJson_usesStablePackageNameWhenInstalledPackageHasSuffix() = runTest {
        every { context.packageName } returns "com.mkx.hrttracker.debug"
        every { settingsRepository.onboardingCompleted } returns flowOf(false)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile()
        coEvery { medicineRepository.getAll() } returns emptyList()
        coEvery { medicationGroupRepository.getGroups() } returns emptyList()
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        coEvery { bloodTestRepository.getCustomAnalytes() } returns emptyList()
        coEvery { bloodTestRepository.getPanels() } returns emptyList()

        val snapshot = BackupSnapshotJsonCodec.decode(
            service.buildBackupSnapshotJson(Instant.parse("2026-04-26T03:04:05Z"))
        )!!

        assertEquals("com.mkx.hrttracker", snapshot.app.packageName)
    }

    @Test
    fun buildBackupSnapshotJson_serializesCapsulePreparationUsingPerTabletColumn() = runTest {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000000c0")
        val capsuleMedicine = testCustomMedicine(
            uuid = medicineUuid,
            medicationName = "Progesterone",
            category = MedicationCategory.CUSTOM,
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        every { settingsRepository.onboardingCompleted } returns flowOf(false)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile()
        coEvery { medicineRepository.getAll() } returns listOf(capsuleMedicine)
        coEvery { medicationGroupRepository.getGroups() } returns emptyList()
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        coEvery { bloodTestRepository.getCustomAnalytes() } returns emptyList()
        coEvery { bloodTestRepository.getPanels() } returns emptyList()

        val snapshot = BackupSnapshotJsonCodec.decode(
            service.buildBackupSnapshotJson(Instant.parse("2026-04-26T03:04:05Z"))
        )!!

        val backedUpMedicine = snapshot.medicines.single()
        assertEquals(medicineUuid.toString(), backedUpMedicine.uuid)
        assertEquals("CAPSULE", backedUpMedicine.preparationType)
        assertEquals(100.0, backedUpMedicine.strengthMgPerTablet!!, 1e-9)
        assertEquals(null, backedUpMedicine.strengthMgPerVial)
    }

    @Test
    fun buildBackupSnapshotJson_exportsStockFields() = runTest {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-0000000000d0")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-0000000000d1")
        val medicine = testCustomMedicine(
            uuid = medicineUuid,
            medicationName = "Tracked med",
            category = MedicationCategory.CUSTOM,
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 40.0,
                vialVolumeMl = 1.0,
            ),
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 87.0,
                unitsLastTotal = 120.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 21,
                generation = 5L,
            ),
        )

        every { settingsRepository.onboardingCompleted } returns flowOf(false)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile()
        coEvery { medicineRepository.getAll() } returns listOf(medicine)
        coEvery { medicationGroupRepository.getGroups() } returns emptyList()
        coEvery { medicationLogRepository.getEntries() } returns listOf(
            MedicationLogEntry(
                uuid = logUuid,
                medicine = medicine,
                category = MedicationCategory.CUSTOM,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.VolumeMl(0.05),
                equivalentE2Mg = null,
                doseAmountDelta = 0.1,
                sourceGroupUuid = null,
                appliedAt = Instant.parse("2026-04-26T01:00:00Z"),
                appliedAtTimeZoneId = "Asia/Tokyo",
            )
        )
        coEvery { bloodTestRepository.getCustomAnalytes() } returns emptyList()
        coEvery { bloodTestRepository.getPanels() } returns emptyList()

        val snapshot = BackupSnapshotJsonCodec.decode(
            service.buildBackupSnapshotJson(Instant.parse("2026-04-26T03:04:05Z"))
        )!!

        val stock = snapshot.medicines.single().stock!!
        assertEquals(true, stock.trackingEnabled)
        assertEquals(87.0, stock.unitsRemaining!!, 1e-9)
        assertEquals(120.0, stock.unitsLastTotal!!, 1e-9)
        assertEquals(null, stock.openContainerAmount)
        assertEquals(21, stock.warnAtDaysRemaining)
        assertEquals(5L, stock.stockGeneration)

        val log = snapshot.medicationLogs.single()
        assertEquals(logUuid.toString(), log.uuid)
        assertEquals("INJECTION", log.applicationType)
        assertEquals(0.05, log.doseVolumeMl!!, 1e-9)
        assertEquals(0.1, log.doseAmountDelta!!, 1e-9)
    }

    @Test
    fun buildBackupSnapshotJson_includes_medicines_and_new_medication_fields() = runTest {
        val exportedAt = Instant.parse("2026-04-26T03:04:05Z")
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-000000000010")
        val groupMedicationUuid = UUID.fromString("00000000-0000-0000-0000-000000000011")
        val sourceGroupUuid = UUID.fromString("00000000-0000-0000-0000-000000000012")
        val morningScheduleTimeUuid = UUID.fromString("00000000-0000-0000-0000-000000000013")
        val eveningScheduleTimeUuid = UUID.fromString("00000000-0000-0000-0000-000000000014")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000020")
        val analyteUuid = UUID.fromString("00000000-0000-0000-0000-000000000030")
        val panelUuid = UUID.fromString("00000000-0000-0000-0000-000000000040")
        val builtinResultUuid = UUID.fromString("00000000-0000-0000-0000-000000000041")
        val customResultUuid = UUID.fromString("00000000-0000-0000-0000-000000000042")
        val customMedicine = testCustomMedicine(
            uuid = medicineUuid,
            medicationName = "Custom med",
            // Free-text custom medicines stay in the CUSTOM category so the PK
            // path correctly excludes them from estradiol-equivalent dosing.
            category = MedicationCategory.CUSTOM,
        )

        every { settingsRepository.onboardingCompleted } returns flowOf(true)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(
            darkModeOption = DarkModeOption.DARK,
            adaptiveColorEnabled = false,
            appLanguageOption = AppLanguageOption.SIMPLIFIED_CHINESE,
            calibrationDefaultUnits = mapOf(
                BloodAnalyteKey.E2 to BloodUnitKey.PMOL_L,
                BloodAnalyteKey.T to BloodUnitKey.NMOL_L,
            ),
            homeE2DisplayUnit = BloodUnitKey.NG_DL,
            homeE2ChartWindowOption = HomeE2ChartWindowOption.THIRTY_DAYS,
            remindersEnabled = false,
            screenLockProtectionEnabled = true,
            appLockGracePeriodOption = AppLockGracePeriodOption.FIVE_MINUTES,
            hideScreenContentEnabled = true,
            pureBlackEnabled = true,
            cjkTextOffsetEnabled = true,
            hazeBlurEnabled = false,
            widgetContentScale = 0.8f,
            widgetBackgroundAlpha = 0.6f,
        )
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile(
            weightKg = 52.2,
            weightOriginalValue = 115.0,
            weightOriginalUnit = WeightUnit.POUNDS,
            updatedAt = Instant.parse("2026-04-25T00:00:00Z"),
        )
        coEvery { medicineRepository.getAll() } returns listOf(customMedicine)
        coEvery { medicationGroupRepository.getGroups() } returns listOf(
            MedicationGroup(
                uuid = groupUuid,
                name = "Custom oral med",
                colorKey = MedicationGroupColorKey.TEAL,
                schedule = MedicationGroupSchedule(
                    type = MedicationGroupScheduleType.WEEKLY,
                    interval = 2,
                    since = LocalDate.of(2026, 4, 1),
                    weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                    times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 30)),
                    timeSlots = listOf(
                        MedicationGroupScheduleTime(
                            uuid = morningScheduleTimeUuid,
                            time = LocalTime.of(9, 0),
                            effectiveFrom = LocalDateTime.of(2026, 4, 1, 0, 0),
                        ),
                        MedicationGroupScheduleTime(
                            uuid = eveningScheduleTimeUuid,
                            time = LocalTime.of(21, 30),
                            effectiveFrom = LocalDateTime.of(2026, 4, 2, 0, 0),
                        ),
                    ),
                ),
                medications = listOf(
                    MedicationGroupMedication(
                        uuid = groupMedicationUuid,
                        medicine = customMedicine,
                        applicationType = MedicationApplicationType.ORAL,
                        doseInstruction = DoseInstruction.TabletFraction(
                            numerator = 1,
                            denominator = 2,
                        ),
                        count = 2,
                    )
                ),
                notificationsEnabled = true,
                createdAt = Instant.parse("2026-04-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-04-20T00:00:00Z"),
                archivedAt = Instant.parse("2026-04-21T00:00:00Z"),
                recreatedFromGroupUuid = sourceGroupUuid,
            )
        )
        coEvery { medicationLogRepository.getEntries() } returns listOf(
            MedicationLogEntry(
                uuid = logUuid,
                medicine = customMedicine,
                category = MedicationCategory.CUSTOM,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(
                    numerator = 1,
                    denominator = 2,
                ),
                equivalentE2Mg = null,
                sourceGroupUuid = groupUuid,
                appliedAt = Instant.parse("2026-04-26T01:00:00Z"),
                appliedAtTimeZoneId = "Asia/Tokyo",
                scheduledFor = LocalDateTime.of(2026, 4, 26, 9, 0),
                count = 2,
                scheduleTimeUuid = morningScheduleTimeUuid,
            )
        )
        coEvery { bloodTestRepository.getCustomAnalytes() } returns listOf(
            CustomBloodAnalyte(
                uuid = analyteUuid,
                abbreviation = "DHT",
                name = "DHT",
                unitLabel = "ng/dL",
                createdAt = Instant.parse("2026-04-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-04-15T00:00:00Z"),
                archivedAt = Instant.parse("2026-04-20T00:00:00Z"),
            )
        )
        coEvery { bloodTestRepository.getPanels() } returns listOf(
            BloodTestPanel(
                uuid = panelUuid,
                collectedAt = Instant.parse("2026-04-26T02:00:00Z"),
                collectedAtTimeZoneId = "Asia/Tokyo",
                notes = "Fasting",
                timeSinceLastEstradiolDoseMillis = 12_345L,
                timeSinceLastTestosteroneDoseMillis = null,
                createdAt = Instant.parse("2026-04-26T02:30:00Z"),
                updatedAt = Instant.parse("2026-04-26T02:45:00Z"),
                results = listOf(
                    BloodTestResult(
                        uuid = builtinResultUuid,
                        createdAt = Instant.parse("2026-04-26T02:31:00Z"),
                        displayOrder = 0,
                        analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                        value = 559.5,
                        unitSnapshot = BloodUnitKey.PMOL_L.storageValue,
                        canonicalValue = 152.4,
                    ),
                    BloodTestResult(
                        uuid = customResultUuid,
                        createdAt = Instant.parse("2026-04-26T02:32:00Z"),
                        displayOrder = 1,
                        analyte = BloodTestResultAnalyte.Custom(
                            uuid = analyteUuid,
                            abbreviation = "DHT",
                            name = "DHT",
                        ),
                        value = 12.0,
                        unitSnapshot = "ng/dL",
                        canonicalValue = 12.0,
                    ),
                ),
            )
        )

        val json = service.buildBackupSnapshotJson(exportedAt)
        val snapshot = BackupSnapshotJsonCodec.decode(json)

        assertNotNull(snapshot)
        snapshot!!

        assertEquals(CURRENT_BACKUP_SNAPSHOT_VERSION, snapshot.snapshotVersion)
        assertEquals(3, CURRENT_BACKUP_SNAPSHOT_VERSION) // Catches a stale bump.
        assertEquals(exportedAt.toEpochMilli(), snapshot.exportedAtEpochMillis)
        assertEquals("com.mkx.hrttracker", snapshot.app.packageName)
        assertEquals(true, snapshot.settings.pureBlackEnabled)
        assertEquals(true, snapshot.settings.cjkTextOffsetEnabled)
        assertEquals(false, snapshot.settings.hazeBlurEnabled)

        // Medicine appears in the standalone medicines list — the restore path
        // builds its FK-validation set from this collection before walking
        // groups and logs, so an export that omits the medicine would be
        // unrestorable even though the in-memory model can still reference it.
        val backedUpMedicine = snapshot.medicines.single()
        assertEquals(medicineUuid.toString(), backedUpMedicine.uuid)
        assertEquals("CUSTOM", backedUpMedicine.selectionKind)
        assertEquals("Custom med", backedUpMedicine.customMedicationName)
        assertEquals("custom med", backedUpMedicine.customMedicationNameNormalized)
        assertEquals("CUSTOM", backedUpMedicine.category)
        assertEquals("PILL", backedUpMedicine.preparationType)
        assertEquals(customMedicine.identityKey, backedUpMedicine.identityKey)

        val group = snapshot.medicationGroups.single()
        assertEquals(groupUuid.toString(), group.uuid)
        assertEquals("Custom oral med", group.name)
        assertEquals("TEAL", group.colorKey)
        assertEquals(true, group.notificationsEnabled)
        assertEquals("WEEKLY", group.schedule.type)
        assertEquals(2, group.schedule.interval)
        assertEquals(LocalDate.of(2026, 4, 1).toEpochDay(), group.schedule.sinceEpochDay)
        assertEquals(listOf(1, 3), group.schedule.weeklyDaysOfWeek)
        assertEquals(listOf(9, 21), group.schedule.times.map { it.hourOfDay })
        assertEquals(listOf(0, 30), group.schedule.times.map { it.minuteOfHour })
        assertEquals(
            listOf(morningScheduleTimeUuid.toString(), eveningScheduleTimeUuid.toString()),
            group.schedule.times.map { it.uuid },
        )
        assertEquals(
            listOf("2026-04-01T00:00", "2026-04-02T00:00"),
            group.schedule.times.map { it.effectiveFromLocalIso },
        )
        assertEquals(sourceGroupUuid.toString(), group.recreatedFromGroupUuid)

        val groupMedication = group.medications.single()
        assertEquals(groupMedicationUuid.toString(), groupMedication.uuid)
        assertEquals(2, groupMedication.count)
        assertEquals(medicineUuid.toString(), groupMedication.medicineUuid)
        assertEquals("ORAL", groupMedication.applicationType)
        assertEquals("TABLET_FRACTION", groupMedication.doseInstructionKind)
        assertEquals(1, groupMedication.tabletFractionNumerator)
        assertEquals(2, groupMedication.tabletFractionDenominator)
        assertEquals(null, groupMedication.doseVolumeMl)
        assertEquals(null, groupMedication.doseWeightGrams)
        assertEquals("DEFAULT", groupMedication.gelApplicationArea)

        val log = snapshot.medicationLogs.single()
        assertEquals(logUuid.toString(), log.uuid)
        assertEquals("CUSTOM", log.category)
        assertEquals(medicineUuid.toString(), log.medicineUuid)
        assertEquals("ORAL", log.applicationType)
        assertEquals("TABLET_FRACTION", log.doseInstructionKind)
        assertEquals(1, log.tabletFractionNumerator)
        assertEquals(2, log.tabletFractionDenominator)
        assertEquals(null, log.equivalentE2Mg) // Custom medicines have no ester data.
        assertEquals(groupUuid.toString(), log.sourceGroupUuid)
        assertEquals(morningScheduleTimeUuid.toString(), log.scheduleTimeUuid)
        assertEquals(Instant.parse("2026-04-26T01:00:00Z").toEpochMilli(), log.appliedAtEpochMillis)
        assertEquals("Asia/Tokyo", log.appliedAtTimeZoneId)
        assertEquals("2026-04-26T09:00", log.scheduledForIso)
        assertEquals(2, log.count)

        assertTrue(!json.contains("\"screenLockProtectionEnabled\""))
        assertFalse("Backup JSON should not be pretty-printed.", json.contains('\n'))
    }

    @Test
    fun buildBackupSnapshotJson_serializesPatchOffSlotWithoutMedicineUuid() = runTest {
        // PATCH_OFF is the one application type whose medicineUuid is null
        // by design. The export must round-trip that null so a re-imported
        // backup still parses as PATCH_OFF rather than failing FK validation.
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-0000000001a0")
        val itemUuid = UUID.fromString("00000000-0000-0000-0000-0000000001a1")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-0000000001a2")
        val scheduleTimeUuid = UUID.fromString("00000000-0000-0000-0000-0000000001a3")

        every { settingsRepository.onboardingCompleted } returns flowOf(false)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile()
        coEvery { medicineRepository.getAll() } returns emptyList()
        coEvery { medicationGroupRepository.getGroups() } returns listOf(
            MedicationGroup(
                uuid = groupUuid,
                name = "Patch removals",
                colorKey = MedicationGroupColorKey.ROSE,
                schedule = MedicationGroupSchedule(
                    type = MedicationGroupScheduleType.DAILY,
                    interval = 1,
                    since = LocalDate.of(2026, 4, 1),
                    weeklyDaysOfWeek = emptySet(),
                    times = listOf(LocalTime.of(20, 0)),
                    timeSlots = listOf(
                        MedicationGroupScheduleTime(
                            uuid = scheduleTimeUuid,
                            time = LocalTime.of(20, 0),
                            effectiveFrom = LocalDateTime.of(2026, 4, 1, 0, 0),
                        ),
                    ),
                ),
                medications = listOf(
                    MedicationGroupMedication(
                        uuid = itemUuid,
                        medicine = null,
                        applicationType = MedicationApplicationType.PATCH_OFF,
                        doseInstruction = DoseInstruction.Noop,
                        count = 1,
                    )
                ),
                notificationsEnabled = false,
                createdAt = Instant.parse("2026-04-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
            )
        )
        coEvery { medicationLogRepository.getEntries() } returns listOf(
            MedicationLogEntry(
                uuid = logUuid,
                medicine = null,
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.PATCH_OFF,
                doseInstruction = DoseInstruction.Noop,
                equivalentE2Mg = null,
                sourceGroupUuid = null,
                appliedAt = Instant.parse("2026-04-26T01:00:00Z"),
                appliedAtTimeZoneId = "Asia/Tokyo",
            )
        )
        coEvery { bloodTestRepository.getCustomAnalytes() } returns emptyList()
        coEvery { bloodTestRepository.getPanels() } returns emptyList()

        val snapshot = BackupSnapshotJsonCodec.decode(
            service.buildBackupSnapshotJson(Instant.parse("2026-04-26T03:04:05Z"))
        )!!

        val patchOffItem = snapshot.medicationGroups.single().medications.single()
        assertEquals(null, patchOffItem.medicineUuid)
        assertEquals("PATCH_OFF", patchOffItem.applicationType)
        assertEquals("NOOP", patchOffItem.doseInstructionKind)

        val patchOffLog = snapshot.medicationLogs.single()
        assertEquals(null, patchOffLog.medicineUuid)
        assertEquals("ESTRADIOL", patchOffLog.category)
        assertEquals("PATCH_OFF", patchOffLog.applicationType)
        assertEquals("NOOP", patchOffLog.doseInstructionKind)
        assertEquals(null, patchOffLog.equivalentE2Mg)
    }

    @Test
    fun buildEncryptedBackupBytes_wraps_snapshot_json_in_binary_container() = runTest {
        every { settingsRepository.onboardingCompleted } returns flowOf(false)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile()
        coEvery { medicineRepository.getAll() } returns emptyList()
        coEvery { medicationGroupRepository.getGroups() } returns emptyList()
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        coEvery { bloodTestRepository.getCustomAnalytes() } returns emptyList()
        coEvery { bloodTestRepository.getPanels() } returns emptyList()

        val encryptedBytes = service.buildEncryptedBackupBytes(
            password = "secret",
            exportedAt = Instant.parse("2026-04-26T03:04:05Z"),
        )
        val decryptedJson = backupCrypto.decryptSnapshotJson(
            encryptedBytes = encryptedBytes,
            password = "secret".toCharArray(),
        )

        assertTrue(encryptedBytes.isNotEmpty())
        assertTrue(
            encryptedBytes.copyOfRange(0, BackupCrypto.MAGIC_BYTES.size)
                .contentEquals(BackupCrypto.MAGIC_BYTES)
        )
        assertTrue(!encryptedBytes.toString(Charsets.UTF_8).contains("\"packageName\""))
        assertNotNull(BackupSnapshotJsonCodec.decode(decryptedJson))
    }

    @Test
    fun prepareBackupExport_creates_temp_payload_and_discardPreparedBackup_removes_it() = runTest {
        every { settingsRepository.onboardingCompleted } returns flowOf(false)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile()
        coEvery { medicineRepository.getAll() } returns emptyList()
        coEvery { medicationGroupRepository.getGroups() } returns emptyList()
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        coEvery { bloodTestRepository.getCustomAnalytes() } returns emptyList()
        coEvery { bloodTestRepository.getPanels() } returns emptyList()

        val preparedBackupExport = service.prepareBackupExport(password = "secret123")
        val tempFile = File(preparedBackupExport.tempFilePath)

        assertTrue(tempFile.exists())
        assertTrue(tempFile.length() > 0L)

        service.discardPreparedBackup(preparedBackupExport)

        assertFalse(tempFile.exists())
    }

    @Test
    fun buildBackupFileName_uses_local_time_for_requested_zone() {
        val fileName = BackupExportService.buildBackupFileName(
            exportedAt = Instant.parse("2026-04-26T03:04:05Z"),
            zoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertEquals("featherline-backup-2026-04-26_12-04-05.hrtbackup", fileName)
    }
}
