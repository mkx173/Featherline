package com.mkx.hrttracker.data.importer

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.local.BloodTestPanelEntity
import com.mkx.hrttracker.data.local.BloodTestResultEntity
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.toEntity
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalImportServiceTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()

    private lateinit var database: HrtTrackerDatabase
    private lateinit var service: ExternalImportService

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HrtTrackerDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        every { databaseHolder.get() } returns database
        coEvery {
            databaseHolder.withTransaction<ExternalImportCommitResult>(any())
        } coAnswers {
            database.withTransaction {
                firstArg<suspend (HrtTrackerDatabase) -> ExternalImportCommitResult>()
                    .invoke(database)
            }
        }
        coEvery {
            homeSnapshotRepository.runHomeDataMutation<ExternalImportCommitResult>(any())
        } coAnswers {
            firstArg<suspend () -> ExternalImportCommitResult>().invoke()
        }

        service = ExternalImportService(
            parser = ExternalImportParser(),
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun firstImportCreatesImportedMedicinesAndRowsWithProvenance() = runTest {
        val preview = service.buildPreview(
            json = transmtfJson(
                events = """
                    { "id": "dose-a", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 2 }
                """.trimIndent(),
                labs = """
                    { "id": "lab-a", "timeH": 2, "unit": "pg/ml", "value": 120 }
                """.trimIndent(),
            ),
            now = NOW,
        )

        assertEquals(1, preview.medicationRowsToCreate)
        assertEquals(0, preview.medicationRowsToUpdate)
        assertEquals(1, preview.labRowsToCreate)
        assertEquals(0, preview.labRowsToUpdate)
        assertEquals(1, preview.importedMedicinesToCreate.size)
        assertEquals(emptyList<ImportedMedicineIdentity>(), preview.importedMedicinesToReuse)
        assertEquals(emptyList<MedicationLogEntryEntity>(), database.medicationLogDao().getEntries())

        val result = service.commit(preview, now = NOW)

        assertEquals(1, result.medicationRowsCreated)
        assertEquals(0, result.medicationRowsUpdated)
        assertEquals(1, result.labRowsCreated)
        assertEquals(0, result.labRowsUpdated)
        assertEquals(1, result.importedMedicinesCreated)
        assertEquals(0, result.importedMedicinesReused)

        val importedMedicine = database.medicineDao().getAll().single { medicine ->
            medicine.importedFromExternalTracker
        }
        assertEquals("E|transmtf|ORAL|ESTRADIOL|mg:2", importedMedicine.identityKey)
        assertEquals("CATALOG", importedMedicine.selectionKind)
        assertEquals("ESTRADIOL", importedMedicine.medicationKey)
        assertNull(importedMedicine.archivedAtEpochMillis)
        assertEquals("MG", importedMedicine.displayDoseUnit)

        val log = database.medicationLogDao().getImportedEntry("transmtf", "dose-a")
        checkNotNull(log)
        assertEquals(importedMedicine.uuid, log.medicineUuid)
        assertEquals("transmtf", log.importSourceApp)
        assertEquals("dose-a", log.importExternalId)
        assertEquals(null, log.sourceGroupUuid)
        assertEquals(null, log.scheduleTimeUuid)
        assertEquals(null, log.scheduledForIso)
        assertEquals(1, log.count)
        assertEquals(3_600_000L, log.appliedAtEpochMillis)

        val panel = database.bloodTestDao().getImportedPanel("transmtf", 7_200_000L)
        checkNotNull(panel)
        assertEquals("transmtf", panel.panel.importSourceApp)
        assertEquals(7_200_000L, panel.panel.importPanelKey)
        assertEquals(1, panel.results.size)
        assertEquals("transmtf", panel.results.single().importSourceApp)
        assertEquals("lab-a", panel.results.single().importExternalId)
        assertEquals("e2", panel.results.single().builtinAnalyteKey)
    }

    @Test
    fun buildPreviewPerformsNoWritesAcrossImportedTables() = runTest {
        val existingMedicine = testMedicine(
            uuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        ).toEntity()
        val existingLog = MedicationLogEntryEntity(
            uuid = "00000000-0000-0000-0000-000000000002",
            category = MedicationCategory.ESTRADIOL.name,
            medicineUuid = null,
            applicationType = MedicationApplicationType.PATCH_OFF.name,
            doseInstructionKind = DoseInstructionKind.NOOP.name,
            tabletFractionNumerator = null,
            tabletFractionDenominator = null,
            doseVolumeMl = null,
            doseWeightGrams = null,
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAtEpochMillis = 1_000L,
            appliedAtTimeZoneId = ZoneId.systemDefault().id,
        )
        val existingPanel = BloodTestPanelEntity(
            uuid = "00000000-0000-0000-0000-000000000003",
            collectedAtInstantEpochMillis = 2_000L,
            collectedAtTimeZoneId = ZoneId.systemDefault().id,
            notes = "manual",
            timeSinceLastEstradiolDoseMillis = null,
            timeSinceLastTestosteroneDoseMillis = null,
            createdAtEpochMillis = 2_000L,
            updatedAtEpochMillis = 2_000L,
        )
        val existingResult = BloodTestResultEntity(
            uuid = "00000000-0000-0000-0000-000000000004",
            panelUuid = existingPanel.uuid,
            createdAtEpochMillis = 2_000L,
            displayOrder = 0,
            builtinAnalyteKey = "e2",
            customAnalyteUuid = null,
            value = 95.0,
            unitSnapshot = "pg_ml",
            canonicalValue = 95.0,
        )
        database.medicineDao().insert(existingMedicine)
        database.medicationLogDao().insertEntry(existingLog)
        database.bloodTestDao().insertPanel(existingPanel)
        database.bloodTestDao().insertResults(listOf(existingResult))

        val preview = service.buildPreview(
            json = transmtfJson(
                events = """
                    { "id": "dose-a", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 2 }
                """.trimIndent(),
                labs = """
                    { "id": "lab-a", "timeH": 2, "unit": "pg/ml", "value": 120 }
                """.trimIndent(),
            ),
            now = NOW,
        )

        assertEquals(1, preview.medicationRowsToCreate)
        assertEquals(1, preview.labRowsToCreate)
        assertEquals(listOf(existingMedicine), database.medicineDao().getAll())
        assertEquals(listOf(existingLog), database.medicationLogDao().getEntries())
        val panels = database.bloodTestDao().getPanels()
        assertEquals(listOf(existingPanel), panels.map { panel -> panel.panel })
        assertEquals(listOf(existingResult), panels.flatMap { panel -> panel.results })
    }

    @Test
    fun secondImportWithSameExternalIdsUpdatesExistingRowsAndKeepsMissingRows() = runTest {
        service.commit(
            service.buildPreview(
                transmtfJson(
                    events = """
                        { "id": "dose-a", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 2 },
                        { "id": "dose-b", "timeH": 2, "route": "oral", "ester": "E2", "doseMG": 1 }
                    """.trimIndent(),
                    labs = """
                        { "id": "lab-a", "timeH": 3, "unit": "pg/ml", "value": 120 },
                        { "id": "lab-b", "timeH": 4, "unit": "pg/ml", "value": 140 }
                    """.trimIndent(),
                )
            ),
            now = NOW,
        )
        val originalDoseA = checkNotNull(database.medicationLogDao().getImportedEntry("transmtf", "dose-a"))
        val originalLabA = checkNotNull(database.bloodTestDao().getImportedResult("transmtf", "lab-a"))

        val preview = service.buildPreview(
            transmtfJson(
                events = """
                    { "id": "dose-a", "timeH": 5, "route": "oral", "ester": "E2", "doseMG": 1 }
                """.trimIndent(),
                labs = """
                    { "id": "lab-a", "timeH": 3, "unit": "pg/ml", "value": 200 }
                """.trimIndent(),
            ),
            now = NOW.plusSeconds(60),
        )

        assertEquals(0, preview.medicationRowsToCreate)
        assertEquals(1, preview.medicationRowsToUpdate)
        assertEquals(0, preview.labRowsToCreate)
        assertEquals(1, preview.labRowsToUpdate)

        val result = service.commit(preview, now = NOW.plusSeconds(60))

        assertEquals(0, result.medicationRowsCreated)
        assertEquals(1, result.medicationRowsUpdated)
        assertEquals(0, result.labRowsCreated)
        assertEquals(1, result.labRowsUpdated)

        val updatedDoseA = checkNotNull(database.medicationLogDao().getImportedEntry("transmtf", "dose-a"))
        assertEquals(originalDoseA.uuid, updatedDoseA.uuid)
        assertEquals(18_000_000L, updatedDoseA.appliedAtEpochMillis)
        assertNotEquals(originalDoseA.medicineUuid, updatedDoseA.medicineUuid)
        assertEquals(2, database.medicationLogDao().getEntries().size)
        assertTrue(
            database.medicationLogDao().getEntries().any { entry ->
                entry.importExternalId == "dose-b"
            }
        )

        val updatedLabA = checkNotNull(database.bloodTestDao().getImportedResult("transmtf", "lab-a"))
        assertEquals(originalLabA.uuid, updatedLabA.uuid)
        assertEquals(200.0, updatedLabA.value, 1e-9)
        assertEquals(2, database.bloodTestDao().getPanels().sumOf { panel -> panel.results.size })
        assertTrue(
            database.bloodTestDao().getPanels()
                .flatMap { panel -> panel.results }
                .any { resultEntity -> resultEntity.importExternalId == "lab-b" }
        )
    }

    @Test
    fun deletingImportedRowsAllowsLaterReimportToRecreateThem() = runTest {
        service.commit(
            service.buildPreview(
                transmtfJson(
                    events = """
                        { "id": "dose-a", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 2 }
                    """.trimIndent(),
                    labs = """
                        { "id": "lab-a", "timeH": 2, "unit": "pg/ml", "value": 120 }
                    """.trimIndent(),
                )
            ),
            now = NOW,
        )
        val originalLog = checkNotNull(database.medicationLogDao().getImportedEntry("transmtf", "dose-a"))
        val originalPanel = checkNotNull(database.bloodTestDao().getImportedPanel("transmtf", 7_200_000L))
        val originalResult = originalPanel.results.single()

        database.medicationLogDao().deleteEntries(listOf(originalLog.uuid))
        database.bloodTestDao().deletePanel(originalPanel.panel.uuid)

        service.commit(
            service.buildPreview(
                transmtfJson(
                    events = """
                        { "id": "dose-a", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 2 }
                    """.trimIndent(),
                    labs = """
                        { "id": "lab-a", "timeH": 2, "unit": "pg/ml", "value": 120 }
                    """.trimIndent(),
                )
            ),
            now = NOW.plusSeconds(60),
        )

        val recreatedLog = checkNotNull(database.medicationLogDao().getImportedEntry("transmtf", "dose-a"))
        val recreatedPanel = checkNotNull(database.bloodTestDao().getImportedPanel("transmtf", 7_200_000L))
        val recreatedResult = recreatedPanel.results.single()
        assertNotEquals(originalLog.uuid, recreatedLog.uuid)
        assertNotEquals(originalPanel.panel.uuid, recreatedPanel.panel.uuid)
        assertNotEquals(originalResult.uuid, recreatedResult.uuid)
        assertEquals("dose-a", recreatedLog.importExternalId)
        assertEquals("lab-a", recreatedResult.importExternalId)
    }

    @Test
    fun reimportPreservesUserOwnedResultInImportedPanelWhenAnalyteConflicts() = runTest {
        service.commit(
            service.buildPreview(
                transmtfJson(
                    events = "",
                    labs = """
                        { "id": "lab-a", "timeH": 2, "unit": "pg/ml", "value": 120 }
                    """.trimIndent(),
                )
            ),
            now = NOW,
        )
        val importedPanel = checkNotNull(database.bloodTestDao().getImportedPanel("transmtf", 7_200_000L))
        val userOwnedResult = BloodTestResultEntity(
            uuid = "00000000-0000-0000-0000-000000000150",
            panelUuid = importedPanel.panel.uuid,
            createdAtEpochMillis = NOW.plusSeconds(30).toEpochMilli(),
            displayOrder = 0,
            builtinAnalyteKey = "e2",
            customAnalyteUuid = null,
            value = 999.0,
            unitSnapshot = "pg_ml",
            canonicalValue = 999.0,
            importSourceApp = null,
            importExternalId = null,
        )
        database.bloodTestDao().deleteResultsForPanel(importedPanel.panel.uuid)
        database.bloodTestDao().insertResults(listOf(userOwnedResult))

        val preview = service.buildPreview(
            transmtfJson(
                events = "",
                labs = """
                    { "id": "lab-a", "timeH": 2, "unit": "pg/ml", "value": 125 }
                """.trimIndent(),
            ),
            now = NOW.plusSeconds(60),
        )

        assertEquals(0, preview.labRowsToCreate)
        assertEquals(0, preview.labRowsToUpdate)
        assertEquals(listOf(ExternalImportWarningReason.LAB_USER_CONFLICT), preview.warningReasons())
        assertEquals(listOf("lab-a"), preview.warnings.map { warning -> warning.externalId })

        val result = service.commit(preview, now = NOW.plusSeconds(60))

        assertEquals(0, result.labRowsCreated)
        assertEquals(0, result.labRowsUpdated)
        assertEquals(listOf(ExternalImportWarningReason.LAB_USER_CONFLICT), result.warningReasons())
        val panels = database.bloodTestDao().getPanels()
        assertEquals(1, panels.size)
        assertEquals(importedPanel.panel.uuid, panels.single().panel.uuid)
        assertEquals(listOf(userOwnedResult), panels.single().results)
        assertNull(database.bloodTestDao().getImportedResult("transmtf", "lab-a"))
    }

    @Test
    fun changedLabTimeIntoConflictingImportedPanelLeavesOldImportedResultAndWarns() = runTest {
        service.commit(
            service.buildPreview(
                transmtfJson(
                    events = "",
                    labs = """
                        { "id": "lab-a", "timeH": 1, "unit": "pg/ml", "value": 120 },
                        { "id": "lab-b", "timeH": 2, "unit": "pg/ml", "value": 140 }
                    """.trimIndent(),
                )
            ),
            now = NOW,
        )
        val oldPanel = checkNotNull(database.bloodTestDao().getImportedPanel("transmtf", 3_600_000L))
        val oldImportedResult = oldPanel.results.single()
        val targetPanel = checkNotNull(database.bloodTestDao().getImportedPanel("transmtf", 7_200_000L))
        val userOwnedResult = BloodTestResultEntity(
            uuid = "00000000-0000-0000-0000-000000000151",
            panelUuid = targetPanel.panel.uuid,
            createdAtEpochMillis = NOW.plusSeconds(30).toEpochMilli(),
            displayOrder = 0,
            builtinAnalyteKey = "e2",
            customAnalyteUuid = null,
            value = 999.0,
            unitSnapshot = "pg_ml",
            canonicalValue = 999.0,
            importSourceApp = null,
            importExternalId = null,
        )
        database.bloodTestDao().deleteResultsForPanel(targetPanel.panel.uuid)
        database.bloodTestDao().insertResults(listOf(userOwnedResult))

        val preview = service.buildPreview(
            transmtfJson(
                events = "",
                labs = """
                    { "id": "lab-a", "timeH": 2, "unit": "pg/ml", "value": 125 }
                """.trimIndent(),
            ),
            now = NOW.plusSeconds(60),
        )

        assertEquals(0, preview.labRowsToCreate)
        assertEquals(0, preview.labRowsToUpdate)
        assertEquals(listOf(ExternalImportWarningReason.LAB_USER_CONFLICT), preview.warningReasons())
        assertEquals(listOf("lab-a"), preview.warnings.map { warning -> warning.externalId })

        val result = service.commit(preview, now = NOW.plusSeconds(60))

        assertEquals(0, result.labRowsCreated)
        assertEquals(0, result.labRowsUpdated)
        assertEquals(listOf(ExternalImportWarningReason.LAB_USER_CONFLICT), result.warningReasons())

        val unchangedImportedResult = checkNotNull(database.bloodTestDao().getImportedResult("transmtf", "lab-a"))
        assertEquals(oldImportedResult.uuid, unchangedImportedResult.uuid)
        assertEquals(oldPanel.panel.uuid, unchangedImportedResult.panelUuid)
        assertEquals(120.0, unchangedImportedResult.value, 1e-9)

        val targetPanelAfterImport = checkNotNull(database.bloodTestDao().getImportedPanel("transmtf", 7_200_000L))
        assertEquals(listOf(userOwnedResult), targetPanelAfterImport.results)
        assertNull(database.bloodTestDao().getImportedResult("transmtf", "lab-b"))
        val allResults = database.bloodTestDao().getPanels().flatMap { panel -> panel.results }
        assertEquals(setOf(oldImportedResult.uuid, userOwnedResult.uuid), allResults.map { result -> result.uuid }.toSet())
    }

    @Test
    fun patchRemoveImportsPatchOffLogWithNullMedicineUuid() = runTest {
        service.commit(
            service.buildPreview(
                transmtfJson(
                    events = """
                        { "id": "patch-remove", "timeH": 12, "route": "patchRemove", "ester": "E2", "doseMG": 0 }
                    """.trimIndent(),
                    labs = "",
                )
            ),
            now = NOW,
        )

        val log = checkNotNull(database.medicationLogDao().getImportedEntry("transmtf", "patch-remove"))
        assertEquals("PATCH_OFF", log.applicationType)
        assertEquals(DoseInstructionKind.NOOP.name, log.doseInstructionKind)
        assertNull(log.medicineUuid)
        assertEquals(MedicationCategory.ESTRADIOL.name, log.category)
    }

    @Test
    fun sameExternalIdsFromDifferentSourceAppsStaySeparateAndUpdateBySource() = runTest {
        service.commit(
            service.buildPreview(
                transmtfJson(
                    events = """
                        { "id": "shared-dose", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 2 }
                    """.trimIndent(),
                    labs = """
                        { "id": "shared-lab", "timeH": 2, "unit": "pg/ml", "value": 120 }
                    """.trimIndent(),
                )
            ),
            now = NOW,
        )
        service.commit(
            service.buildPreview(
                oyamaJson(
                    events = """
                        { "id": "shared-dose", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 2 }
                    """.trimIndent(),
                    labs = """
                        { "id": "shared-lab", "timeH": 2, "unit": "pg/ml", "value": 140 }
                    """.trimIndent(),
                )
            ),
            now = NOW.plusSeconds(60),
        )

        assertEquals(
            setOf("transmtf", "oyama"),
            database.medicationLogDao().getEntries().map { entry -> entry.importSourceApp }.toSet(),
        )
        assertEquals(
            setOf("E|transmtf|ORAL|ESTRADIOL|mg:2", "E|oyama|ORAL|ESTRADIOL|mg:2"),
            database.medicineDao().getAll()
                .filter { medicine -> medicine.importedFromExternalTracker }
                .map { medicine -> medicine.identityKey }
                .toSet(),
        )
        assertEquals(
            setOf("transmtf", "oyama"),
            database.bloodTestDao().getPanels().map { panel -> panel.panel.importSourceApp }.toSet(),
        )
        assertEquals(
            mapOf("transmtf" to 120.0, "oyama" to 140.0),
            database.bloodTestDao().getPanels()
                .flatMap { panel -> panel.results }
                .associate { result -> checkNotNull(result.importSourceApp) to result.value },
        )

        service.commit(
            service.buildPreview(
                oyamaJson(
                    events = """
                        { "id": "shared-dose", "timeH": 3, "route": "oral", "ester": "E2", "doseMG": 2 }
                    """.trimIndent(),
                    labs = """
                        { "id": "shared-lab", "timeH": 2, "unit": "pg/ml", "value": 180 }
                    """.trimIndent(),
                )
            ),
            now = NOW.plusSeconds(120),
        )

        val transmtfLog = checkNotNull(database.medicationLogDao().getImportedEntry("transmtf", "shared-dose"))
        val oyamaLog = checkNotNull(database.medicationLogDao().getImportedEntry("oyama", "shared-dose"))
        assertEquals(3_600_000L, transmtfLog.appliedAtEpochMillis)
        assertEquals(10_800_000L, oyamaLog.appliedAtEpochMillis)

        val resultValuesBySource = database.bloodTestDao().getPanels()
            .flatMap { panel -> panel.results }
            .associate { result -> checkNotNull(result.importSourceApp) to result.value }
        assertEquals(120.0, resultValuesBySource["transmtf"] ?: 0.0, 1e-9)
        assertEquals(180.0, resultValuesBySource["oyama"] ?: 0.0, 1e-9)
    }

    @Test
    fun importedMedicineIdentitiesDoNotCollideWithNormalCatalogOrCustomMedicines() = runTest {
        database.medicineDao().insert(
            testMedicine(
                uuid = UUID.fromString("00000000-0000-0000-0000-000000000101"),
                key = MedicationKey.ESTRADIOL,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            ).toEntity()
        )
        database.medicineDao().insert(
            testCustomMedicine(
                uuid = UUID.fromString("00000000-0000-0000-0000-000000000102"),
                medicationName = "External tracker",
                category = MedicationCategory.ESTRADIOL,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            ).toEntity()
        )

        service.commit(
            service.buildPreview(
                transmtfJson(
                    events = """
                        { "id": "dose-a", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 2 }
                    """.trimIndent(),
                    labs = "",
                )
            ),
            now = NOW,
        )

        val medicines = database.medicineDao().getAll()
        assertEquals(3, medicines.size)
        assertEquals(1, medicines.count { medicine -> medicine.importedFromExternalTracker })
        assertTrue(medicines.any { medicine -> medicine.identityKey.startsWith("C|ESTRADIOL|PILL") })
        assertTrue(medicines.any { medicine -> medicine.identityKey.startsWith("X|external tracker|PILL") })
        assertTrue(medicines.any { medicine -> medicine.identityKey == "E|transmtf|ORAL|ESTRADIOL|mg:2" })
    }

    @Test
    fun userCreatedRowsAtSameTimestampAreNeverOverwritten() = runTest {
        val userPanelUuid = UUID.fromString("00000000-0000-0000-0000-000000000201")
        val userResultUuid = UUID.fromString("00000000-0000-0000-0000-000000000202")
        database.medicationLogDao().insertEntry(
            MedicationLogEntryEntity(
                uuid = "00000000-0000-0000-0000-000000000203",
                category = MedicationCategory.ESTRADIOL.name,
                medicineUuid = null,
                applicationType = MedicationApplicationType.PATCH_OFF.name,
                doseInstructionKind = DoseInstructionKind.NOOP.name,
                tabletFractionNumerator = null,
                tabletFractionDenominator = null,
                doseVolumeMl = null,
                doseWeightGrams = null,
                equivalentE2Mg = null,
                sourceGroupUuid = null,
                appliedAtEpochMillis = 3_600_000L,
                appliedAtTimeZoneId = ZoneId.systemDefault().id,
            )
        )
        database.bloodTestDao().insertPanel(
            BloodTestPanelEntity(
                uuid = userPanelUuid.toString(),
                collectedAtInstantEpochMillis = 7_200_000L,
                collectedAtTimeZoneId = ZoneId.systemDefault().id,
                notes = "user panel",
                timeSinceLastEstradiolDoseMillis = null,
                timeSinceLastTestosteroneDoseMillis = null,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            )
        )
        database.bloodTestDao().insertResults(
            listOf(
                BloodTestResultEntity(
                    uuid = userResultUuid.toString(),
                    panelUuid = userPanelUuid.toString(),
                    createdAtEpochMillis = 1L,
                    displayOrder = 0,
                    builtinAnalyteKey = "e2",
                    customAnalyteUuid = null,
                    value = 90.0,
                    unitSnapshot = "pg_ml",
                    canonicalValue = 90.0,
                )
            )
        )

        service.commit(
            service.buildPreview(
                transmtfJson(
                    events = """
                        { "id": "patch-remove", "timeH": 1, "route": "patchRemove", "ester": "E2", "doseMG": 0 }
                    """.trimIndent(),
                    labs = """
                        { "id": "lab-a", "timeH": 2, "unit": "pg/ml", "value": 120 }
                    """.trimIndent(),
                )
            ),
            now = NOW,
        )

        val logsAtSameTime = database.medicationLogDao().getEntries()
            .filter { entry -> entry.appliedAtEpochMillis == 3_600_000L }
        assertEquals(2, logsAtSameTime.size)
        assertEquals(1, logsAtSameTime.count { entry -> entry.importSourceApp == null })
        assertEquals(1, logsAtSameTime.count { entry -> entry.importSourceApp == "transmtf" })

        val panelsAtSameTime = database.bloodTestDao().getPanels()
            .filter { panel -> panel.panel.collectedAtInstantEpochMillis == 7_200_000L }
        assertEquals(2, panelsAtSameTime.size)
        assertEquals(1, panelsAtSameTime.count { panel -> panel.panel.importSourceApp == null })
        assertEquals(1, panelsAtSameTime.count { panel -> panel.panel.importSourceApp == "transmtf" })
        assertTrue(
            panelsAtSameTime.single { panel -> panel.panel.uuid == userPanelUuid.toString() }
                .results.single().importSourceApp == null
        )
    }

    @Test
    fun labsTwentyMinutesApartCreateDistinctPanels() = runTest {
        service.commit(
            service.buildPreview(
                transmtfJson(
                    events = "",
                    labs = """
                        { "id": "lab-a", "timeH": 1, "unit": "pg/ml", "value": 120 },
                        { "id": "lab-b", "timeH": 1.333333333333, "unit": "pg/ml", "value": 140 }
                    """.trimIndent(),
                )
            ),
            now = NOW,
        )

        val importedPanels = database.bloodTestDao().getImportedPanels("transmtf")
        assertEquals(2, importedPanels.size)
        assertEquals(
            setOf(3_600_000L, 4_800_000L),
            importedPanels.map { panel -> panel.panel.importPanelKey }.toSet(),
        )
        assertEquals(2, importedPanels.sumOf { panel -> panel.results.size })
    }

    @Test
    fun changedLabTimeMovesResultToNewImportedPanelAndDeletesVacatedPanel() = runTest {
        service.commit(
            service.buildPreview(
                transmtfJson(
                    events = "",
                    labs = """
                        { "id": "lab-a", "timeH": 1, "unit": "pg/ml", "value": 120 }
                    """.trimIndent(),
                )
            ),
            now = NOW,
        )
        val oldPanel = checkNotNull(database.bloodTestDao().getImportedPanel("transmtf", 3_600_000L))
        val originalResult = oldPanel.results.single()

        service.commit(
            service.buildPreview(
                transmtfJson(
                    events = "",
                    labs = """
                        { "id": "lab-a", "timeH": 2, "unit": "pg/ml", "value": 120 }
                    """.trimIndent(),
                )
            ),
            now = NOW.plusSeconds(60),
        )

        assertNull(database.bloodTestDao().getImportedPanel("transmtf", 3_600_000L))
        val newPanel = checkNotNull(database.bloodTestDao().getImportedPanel("transmtf", 7_200_000L))
        assertNotEquals(oldPanel.panel.uuid, newPanel.panel.uuid)
        assertEquals(originalResult.uuid, newPanel.results.single().uuid)
        assertEquals(newPanel.panel.uuid, newPanel.results.single().panelUuid)
    }

    @Test
    fun transactionFailureLeavesNoPartialImportedWrites() = runTest {
        val conflictingIdentity = "E|transmtf|ORAL|ESTRADIOL|mg:2"
        database.medicineDao().insert(
            malformedNormalMedicineWithIdentity(
                uuid = "00000000-0000-0000-0000-000000000301",
                identityKey = conflictingIdentity,
            )
        )

        val preview = service.buildPreview(
            transmtfJson(
                events = """
                    { "id": "dose-a", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 1 },
                    { "id": "dose-b", "timeH": 2, "route": "oral", "ester": "E2", "doseMG": 2 }
                """.trimIndent(),
                labs = """
                    { "id": "lab-a", "timeH": 3, "unit": "pg/ml", "value": 120 }
                """.trimIndent(),
            ),
            now = NOW,
        )

        val failure = runCatching { service.commit(preview, now = NOW) }.exceptionOrNull()

        checkNotNull(failure)
        assertEquals(
            listOf(conflictingIdentity),
            database.medicineDao().getAll().map { medicine -> medicine.identityKey },
        )
        assertEquals(emptyList<MedicationLogEntryEntity>(), database.medicationLogDao().getEntries())
        assertEquals(emptyList<BloodTestPanelEntity>(), database.bloodTestDao().getPanels().map { panel -> panel.panel })
    }

    private fun transmtfJson(
        events: String,
        labs: String,
    ): String {
        return """
            {
              "version": "2026.1",
              "gelProducts": [],
              "events": [${events.trim()}],
              "labResults": [${labs.trim()}]
            }
        """.trimIndent()
    }

    private fun oyamaJson(
        events: String,
        labs: String,
    ): String {
        return """
            {
              "doseTemplates": [],
              "events": [${events.trim()}],
              "labResults": [${labs.trim()}]
            }
        """.trimIndent()
    }

    private fun malformedNormalMedicineWithIdentity(
        uuid: String,
        identityKey: String,
    ): MedicineEntity {
        return MedicineEntity(
            uuid = uuid,
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
            identityKey = identityKey,
            createdAtEpochMillis = NOW.toEpochMilli(),
            updatedAtEpochMillis = NOW.toEpochMilli(),
            archivedAtEpochMillis = null,
            importedFromExternalTracker = false,
        )
    }

    private fun ExternalImportPreview.warningReasons(): List<ExternalImportWarningReason> {
        return warnings.map(ExternalImportWarning::reason)
    }

    private fun ExternalImportCommitResult.warningReasons(): List<ExternalImportWarningReason> {
        return warnings.map(ExternalImportWarning::reason)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-01T00:00:00Z")
    }
}
