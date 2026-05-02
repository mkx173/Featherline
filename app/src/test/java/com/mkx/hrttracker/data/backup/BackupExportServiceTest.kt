package com.mkx.hrttracker.data.backup

import android.content.Context
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.bloodtest.CustomBloodAnalyte
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
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
        backupCrypto = BackupCrypto(TestBackupArgon2KeyDeriver())
        service = BackupExportService(
            context = context,
            settingsRepository = settingsRepository,
            userProfileRepository = userProfileRepository,
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
    fun buildBackupSnapshotJson_includes_current_snapshot_shape_and_new_medication_fields() = runTest {
        val exportedAt = Instant.parse("2026-04-26T03:04:05Z")
        val groupUuid = UUID.fromString("00000000-0000-0000-0000-000000000010")
        val groupMedicationUuid = UUID.fromString("00000000-0000-0000-0000-000000000011")
        val sourceGroupUuid = UUID.fromString("00000000-0000-0000-0000-000000000012")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000020")
        val analyteUuid = UUID.fromString("00000000-0000-0000-0000-000000000030")
        val panelUuid = UUID.fromString("00000000-0000-0000-0000-000000000040")
        val builtinResultUuid = UUID.fromString("00000000-0000-0000-0000-000000000041")
        val customResultUuid = UUID.fromString("00000000-0000-0000-0000-000000000042")

        every { settingsRepository.onboardingCompleted } returns flowOf(true)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(
            darkModeOption = DarkModeOption.DARK,
            adaptiveColorEnabled = false,
            appLanguageOption = AppLanguageOption.SIMPLIFIED_CHINESE,
            calibrationDefaultUnits = mapOf(
                BloodAnalyteKey.E2 to BloodUnitKey.PMOL_L,
                BloodAnalyteKey.T to BloodUnitKey.NMOL_L,
            ),
            remindersEnabled = false,
            screenLockProtectionEnabled = true,
            appLockGracePeriodOption = AppLockGracePeriodOption.FIVE_MINUTES,
            hideScreenContentEnabled = true,
        )
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile(
            weightKg = 52.2,
            weightOriginalValue = 115.0,
            weightOriginalUnit = WeightUnit.POUNDS,
            updatedAt = Instant.parse("2026-04-25T00:00:00Z"),
        )
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
                ),
                medications = listOf(
                    MedicationGroupMedication(
                        uuid = groupMedicationUuid,
                        details = MedicationDetails(
                            category = MedicationCategory.CUSTOM,
                            applicationType = MedicationApplicationType.ORAL,
                            selection = MedicationSelection.Custom("Custom med"),
                            dose = MedicationDose.MgAsMedicine(0.2),
                            gelApplicationArea = MedicationGelApplicationArea.DEFAULT,
                            customDoseUnit = MedicationDoseUnit.MCG,
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
                details = MedicationDetails(
                    category = MedicationCategory.CUSTOM,
                    applicationType = MedicationApplicationType.ORAL,
                    selection = MedicationSelection.Custom("Custom med"),
                    dose = MedicationDose.MgAsMedicine(0.2),
                    customDoseUnit = MedicationDoseUnit.MCG,
                ),
                dosageMgAsEstradiol = 0.2,
                sourceGroupUuid = groupUuid,
                appliedAt = Instant.parse("2026-04-26T01:00:00Z"),
                appliedAtTimeZoneId = "Asia/Tokyo",
                scheduledFor = LocalDateTime.of(2026, 4, 26, 10, 0),
                count = 2,
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
        assertEquals(exportedAt.toEpochMilli(), snapshot.exportedAtEpochMillis)
        assertEquals("com.mkx.hrttracker", snapshot.app.packageName)

        assertEquals("DARK", snapshot.settings.darkModeOption)
        assertEquals(false, snapshot.settings.adaptiveColorEnabled)
        assertEquals(false, snapshot.settings.remindersEnabled)
        assertEquals("FIVE_MINUTES", snapshot.settings.appLockGracePeriodOption)
        assertEquals(true, snapshot.settings.hideScreenContentEnabled)
        assertEquals(true, snapshot.settings.onboardingCompleted)
        assertEquals("SIMPLIFIED_CHINESE", snapshot.settings.appLanguageOption)
        assertEquals(
            mapOf("e2" to "pmol_l", "t" to "nmol_l"),
            snapshot.settings.calibrationDefaultUnits,
        )

        assertEquals(52.2, snapshot.userProfile.weightKg!!, 1e-9)
        assertEquals(115.0, snapshot.userProfile.weightOriginalValue!!, 1e-9)
        assertEquals("POUNDS", snapshot.userProfile.weightOriginalUnit)

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
        assertTrue(group.schedule.times.all { it.uuid != null })
        assertEquals(
            listOf("2026-04-01T00:00", "2026-04-01T00:00"),
            group.schedule.times.map { it.effectiveFromLocalIso },
        )
        assertEquals(
            Instant.parse("2026-04-01T00:00:00Z").toEpochMilli(),
            group.createdAtEpochMillis,
        )
        assertEquals(
            Instant.parse("2026-04-20T00:00:00Z").toEpochMilli(),
            group.updatedAtEpochMillis,
        )
        assertEquals(
            Instant.parse("2026-04-21T00:00:00Z").toEpochMilli(),
            group.archivedAtEpochMillis,
        )
        assertEquals(true, group.includePastScheduledSlots)
        assertEquals(sourceGroupUuid.toString(), group.recreatedFromGroupUuid)

        val groupMedication = group.medications.single()
        assertEquals(groupMedicationUuid.toString(), groupMedication.uuid)
        assertEquals(2, groupMedication.count)
        assertEquals("CUSTOM", groupMedication.category)
        assertEquals("ORAL", groupMedication.applicationType)
        assertEquals("CUSTOM", groupMedication.selectionKind)
        assertEquals(null, groupMedication.medicationKey)
        assertEquals("Custom med", groupMedication.customMedicationName)
        assertEquals("MG_AS_MEDICINE", groupMedication.doseKind)
        assertEquals(0.2, groupMedication.doseValueMg!!, 1e-9)
        assertEquals("MCG", groupMedication.customDoseUnit)
        assertEquals(null, groupMedication.doseValuePercent)
        assertEquals(null, groupMedication.doseWeightGrams)
        assertEquals(null, groupMedication.doseReleaseRateMcgPerDay)
        assertEquals("DEFAULT", groupMedication.gelApplicationArea)

        val log = snapshot.medicationLogs.single()
        assertEquals(logUuid.toString(), log.uuid)
        assertEquals("CUSTOM", log.category)
        assertEquals("ORAL", log.applicationType)
        assertEquals("CUSTOM", log.selectionKind)
        assertEquals(null, log.medicationKey)
        assertEquals("Custom med", log.customMedicationName)
        assertEquals("MG_AS_MEDICINE", log.doseKind)
        assertEquals(0.2, log.doseValueMg!!, 1e-9)
        assertEquals("MCG", log.customDoseUnit)
        assertEquals(null, log.doseValuePercent)
        assertEquals(null, log.doseWeightGrams)
        assertEquals(null, log.doseReleaseRateMcgPerDay)
        assertEquals("DEFAULT", log.gelApplicationArea)
        assertEquals(0.2, log.dosageMgAsEstradiol!!, 1e-9)
        assertEquals(groupUuid.toString(), log.sourceGroupUuid)
        assertEquals(Instant.parse("2026-04-26T01:00:00Z").toEpochMilli(), log.appliedAtEpochMillis)
        assertEquals("Asia/Tokyo", log.appliedAtTimeZoneId)
        assertEquals("2026-04-26T10:00", log.scheduledForIso)
        assertEquals(2, log.count)

        val analyte = snapshot.customBloodAnalytes.single()
        assertEquals(analyteUuid.toString(), analyte.uuid)
        assertEquals("DHT", analyte.abbreviation)
        assertEquals("DHT", analyte.name)
        assertEquals("ng/dL", analyte.unitLabel)
        assertEquals(
            Instant.parse("2026-04-01T00:00:00Z").toEpochMilli(),
            analyte.createdAtEpochMillis,
        )
        assertEquals(
            Instant.parse("2026-04-15T00:00:00Z").toEpochMilli(),
            analyte.updatedAtEpochMillis,
        )
        assertEquals(
            Instant.parse("2026-04-20T00:00:00Z").toEpochMilli(),
            analyte.archivedAtEpochMillis,
        )

        val panel = snapshot.bloodTestPanels.single()
        assertEquals(panelUuid.toString(), panel.uuid)
        assertEquals(Instant.parse("2026-04-26T02:00:00Z").toEpochMilli(), panel.collectedAtInstantEpochMillis)
        assertEquals("Asia/Tokyo", panel.collectedAtTimeZoneId)
        assertEquals("Fasting", panel.notes)
        assertEquals(12_345L, panel.timeSinceLastEstradiolDoseMillis)
        assertEquals(null, panel.timeSinceLastTestosteroneDoseMillis)
        assertEquals(Instant.parse("2026-04-26T02:30:00Z").toEpochMilli(), panel.createdAtEpochMillis)
        assertEquals(Instant.parse("2026-04-26T02:45:00Z").toEpochMilli(), panel.updatedAtEpochMillis)

        val builtinResult = panel.results[0]
        assertEquals(builtinResultUuid.toString(), builtinResult.uuid)
        assertEquals(Instant.parse("2026-04-26T02:31:00Z").toEpochMilli(), builtinResult.createdAtEpochMillis)
        assertEquals(0, builtinResult.displayOrder)
        assertEquals("e2", builtinResult.builtinAnalyteKey)
        assertEquals(null, builtinResult.customAnalyteUuid)
        assertEquals(559.5, builtinResult.value, 1e-9)
        assertEquals("pmol_l", builtinResult.unitSnapshot)
        assertEquals(152.4, builtinResult.canonicalValue, 1e-9)

        val customResult = panel.results[1]
        assertEquals(customResultUuid.toString(), customResult.uuid)
        assertEquals(Instant.parse("2026-04-26T02:32:00Z").toEpochMilli(), customResult.createdAtEpochMillis)
        assertEquals(1, customResult.displayOrder)
        assertEquals(null, customResult.builtinAnalyteKey)
        assertEquals(analyteUuid.toString(), customResult.customAnalyteUuid)
        assertEquals(12.0, customResult.value, 1e-9)
        assertEquals("ng/dL", customResult.unitSnapshot)
        assertEquals(12.0, customResult.canonicalValue, 1e-9)

        assertContains(json, "\"medicationKey\": null")
        assertContains(json, "\"doseValuePercent\": null")
        assertContains(json, "\"doseWeightGrams\": null")
        assertContains(json, "\"doseReleaseRateMcgPerDay\": null")
        assertContains(json, "\"timeSinceLastTestosteroneDoseMillis\": null")
        assertContains(json, "\"customAnalyteUuid\": null")
        assertTrue(!json.contains("\"screenLockProtectionEnabled\""))
    }

    @Test
    fun buildEncryptedBackupBytes_wraps_snapshot_json_in_binary_container() = runTest {
        every { settingsRepository.onboardingCompleted } returns flowOf(false)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile()
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
        assertTrue(encryptedBytes.copyOfRange(0, BackupCrypto.MAGIC_BYTES.size).contentEquals(BackupCrypto.MAGIC_BYTES))
        assertTrue(!encryptedBytes.toString(Charsets.UTF_8).contains("\"packageName\""))
        assertNotNull(BackupSnapshotJsonCodec.decode(decryptedJson))
    }

    @Test
    fun prepareBackupExport_creates_temp_payload_and_discardPreparedBackup_removes_it() = runTest {
        every { settingsRepository.onboardingCompleted } returns flowOf(false)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile()
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

        assertEquals("hrttracker-backup-2026-04-26_12-04-05.hrtbackup", fileName)
    }

    private fun assertContains(
        text: String,
        expected: String,
    ) {
        assertTrue("Expected JSON to contain: $expected\n$text", text.contains(expected))
    }
}
