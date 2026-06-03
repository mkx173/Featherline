package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationLogDao
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

class MedicineStockMutatorTest {

    private val database: HrtTrackerDatabase = mockk()
    private val medicineDao: MedicineDao = mockk(relaxed = true)
    private val logDao: MedicationLogDao = mockk(relaxed = true)

    private lateinit var mutator: MedicineStockMutator

    private val medicineUuid: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val logUuid: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val fixedNow: Instant = Instant.ofEpochMilli(1_000_000L)

    @Before
    fun setUp() {
        every { database.medicineDao() } returns medicineDao
        every { database.medicationLogDao() } returns logDao
        mutator = MedicineStockMutator()
    }

    /** Pool medicine (PILL): tracking on, 30 tablets, generation 1. */
    private fun pillRow(
        trackingEnabled: Boolean = true,
        stockUnitsRemaining: Double? = 30.0,
        stockUnitsLastTotal: Double? = 30.0,
        warnAtDaysRemaining: Int = 14,
        stockGeneration: Long = 1L,
        archivedAt: Long? = null,
    ): MedicineEntity {
        val key = MedicationKey.ESTRADIOL
        val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
        return MedicineEntity(
            uuid = medicineUuid.toString(),
            selectionKind = "CATALOG",
            medicationKey = key.name,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = key.category.name,
            preparationType = preparation.type.name,
            strengthMgPerTablet = preparation.strengthMgPerTablet,
            strengthMgPerVial = null,
            concentrationMgPerMl = null,
            vialVolumeMl = null,
            concentrationPercent = null,
            sachetWeightGrams = null,
            containerWeightGrams = null,
            patchTotalMg = null,
            patchReleaseRateMcgPerDay = null,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(key, preparation),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = archivedAt,
            displayDoseUnit = MedicineDisplayDoseUnit.MG.name,
            trackingEnabled = trackingEnabled,
            stockUnitsRemaining = stockUnitsRemaining,
            stockUnitsLastTotal = stockUnitsLastTotal,
            openContainerAmount = null,
            warnAtDaysRemaining = warnAtDaysRemaining,
            stockGeneration = stockGeneration,
        )
    }

    /** Container medicine (multi-use vial): tracking on, 2 sealed, 0.5 mL open, generation 1. */
    private fun vialRow(
        trackingEnabled: Boolean = true,
        sealed: Double? = 2.0,
        open: Double? = 0.5,
        vialVolume: Double = 1.0,
        warnAtDaysRemaining: Int = 14,
        stockGeneration: Long = 1L,
        lastTotal: Double? = null,
    ): MedicineEntity {
        val key = MedicationKey.ESTRADIOL_VALERATE
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 5.0,
            vialVolumeMl = vialVolume,
        )
        return MedicineEntity(
            uuid = medicineUuid.toString(),
            selectionKind = "CATALOG",
            medicationKey = key.name,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = key.category.name,
            preparationType = preparation.type.name,
            strengthMgPerTablet = null,
            strengthMgPerVial = null,
            concentrationMgPerMl = preparation.concentrationMgPerMl,
            vialVolumeMl = preparation.vialVolumeMl,
            concentrationPercent = null,
            sachetWeightGrams = null,
            containerWeightGrams = null,
            patchTotalMg = null,
            patchReleaseRateMcgPerDay = null,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(key, preparation),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = null,
            displayDoseUnit = MedicineDisplayDoseUnit.MG.name,
            trackingEnabled = trackingEnabled,
            stockUnitsRemaining = sealed,
            stockUnitsLastTotal = lastTotal,
            openContainerAmount = open,
            warnAtDaysRemaining = warnAtDaysRemaining,
            stockGeneration = stockGeneration,
        )
    }

    /** Container medicine (gel bottle): tracking on, 1 sealed, 20 g open, generation 1. */
    private fun gelContainerRow(
        trackingEnabled: Boolean = true,
        sealed: Double? = 1.0,
        open: Double? = 20.0,
        containerWeight: Double = 80.0,
        warnAtDaysRemaining: Int = 14,
        stockGeneration: Long = 1L,
    ): MedicineEntity {
        val key = MedicationKey.ESTRADIOL
        val preparation = MedicinePreparation.GelContainer(
            concentrationPercent = 0.06,
            containerWeightGrams = containerWeight,
        )
        return MedicineEntity(
            uuid = medicineUuid.toString(),
            selectionKind = "CATALOG",
            medicationKey = key.name,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = key.category.name,
            preparationType = preparation.type.name,
            strengthMgPerTablet = null,
            strengthMgPerVial = null,
            concentrationMgPerMl = null,
            vialVolumeMl = null,
            concentrationPercent = preparation.concentrationPercent,
            sachetWeightGrams = null,
            containerWeightGrams = preparation.containerWeightGrams,
            patchTotalMg = null,
            patchReleaseRateMcgPerDay = null,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(key, preparation),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = null,
            displayDoseUnit = MedicineDisplayDoseUnit.MG.name,
            trackingEnabled = trackingEnabled,
            stockUnitsRemaining = sealed,
            stockUnitsLastTotal = null,
            openContainerAmount = open,
            warnAtDaysRemaining = warnAtDaysRemaining,
            stockGeneration = stockGeneration,
        )
    }

    @Test
    fun resolveDeduction_pool_simpleDecrement() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow(stockUnitsRemaining = 30.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 1.0,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 29.0,
                stockUnitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_pool_clampAtZero_keepsStockAtZero() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow(stockUnitsRemaining = 0.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 1.0,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 0.0,
                stockUnitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_pool_nullRemaining_treatsAsEmptyStock() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow(stockUnitsRemaining = null)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 1.0,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 0.0,
                stockUnitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_pool_partialClamp_clampsRemainingAtZero() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow(stockUnitsRemaining = 0.5)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 2.0,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 0.0,
                stockUnitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_trackingDisabled_doesNotWrite() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow(trackingEnabled = false)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 1.0,
            now = fixedNow,
        )

        coVerify(exactly = 0) {
            medicineDao.updateStockFields(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun resolveDeduction_medicineMissing_doesNotWrite() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns null

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 1.0,
            now = fixedNow,
        )

        coVerify(exactly = 0) {
            medicineDao.updateStockFields(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun resolveDeduction_patchOff_doesNotWrite() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow().copy(preparationType = "PATCH_OFF", trackingEnabled = true)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 0.0,
            now = fixedNow,
        )

        coVerify(exactly = 0) {
            medicineDao.updateStockFields(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun resolveDeduction_container_case1_simpleDecrement() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 2.0, open = 0.5, vialVolume = 1.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 0.4,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 2.0,
                stockUnitsLastTotal = null,
                openContainerAmount = match { amount -> abs(amount - 0.1) < 1e-9 },
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_container_exactDrainPromotesNextContainerInSameWrite() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 2.0, open = 0.25, vialVolume = 1.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 0.25,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 1.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 1.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_container_exactDrainWithNoSealedStaysOut() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 0.0, open = 0.25, vialVolume = 1.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 0.25,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 0.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 0.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_container_openLessThanDoseKeepsAcceptedDregLossBehavior() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 1.0, open = 0.1, vialVolume = 10.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 0.25,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 0.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 9.75,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    // Regression guard for the crack's total-preserving invariant. computeTotalStock
    // (MedicineStockRepository) reads totalStockUnits as open + sealed * capacity and
    // feeds it into OUT/LOW state and runway; a crack that moved units would silently
    // shift stock classification and days-remaining. Assert the crack actually fires
    // here (not a no-op that trivially preserves the total), then that no units moved.
    @Test
    fun normalizeOpenContainer_crackPreservesTotalStock() {
        val capacity = 10.0
        val openBefore: Double? = 0.0
        val sealedBefore = 3.0

        val (openAfter, sealedAfter) = normalizeOpenContainer(
            open = openBefore,
            sealed = sealedBefore,
            capacity = capacity,
        )

        val crackedOpen = requireNotNull(openAfter)
        assertEquals(capacity, crackedOpen, 1e-9)
        assertEquals(sealedBefore - 1.0, sealedAfter, 1e-9)

        val totalBefore = (openBefore ?: 0.0) + sealedBefore * capacity
        val totalAfter = crackedOpen + sealedAfter * capacity
        assertEquals(totalBefore, totalAfter, 1e-9)
    }

    @Test
    fun resolveDeduction_container_case2_autoPromote_partialResidueDiscarded() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 2.0, open = 0.2, vialVolume = 1.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 0.5,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 1.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 0.5,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_container_case2_doseExceedsContainerSize() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 2.0, open = 0.5, vialVolume = 1.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 1.5,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 0.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 1.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_container_case2_doseExceedsOpenPlusOneContainer_drainsThenEagerlyOpensLastSealed() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 2.0, open = 0.2, vialVolume = 1.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 1.5,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 0.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 1.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_container_case1_treatsTinyFloatingResidueAsSufficientOpen() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 2.0, open = 0.09999999999999998, vialVolume = 1.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 0.1,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 1.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 1.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_container_case2_gelContainerUsesContainerWeight() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            gelContainerRow(sealed = 1.0, open = 20.0, containerWeight = 80.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 25.0,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 0.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 55.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_container_tinyDoseWithNoStockLeavesOpenAtZero() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 0.0, open = 0.0, vialVolume = 1.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 0.0000000005,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 0.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 0.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_container_case3_outOfStock_consumesRemainingOpen() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 0.0, open = 0.3, vialVolume = 1.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 0.5,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 0.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 0.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun resolveDeduction_container_case3_emptyOpenAndZeroSealed_keepsOpenAtZero() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 0.0, open = 0.0, vialVolume = 1.0)

        mutator.resolveDeductionForInsert(
            database = database,
            medicineUuid = medicineUuid,
            requestedDose = 0.5,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 0.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 0.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun clearOnArchive_resetsAllFieldsAndBumpsGeneration() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow(stockGeneration = 7L)

        mutator.clearStockOnArchive(database, medicineUuid, fixedNow)

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = false,
                stockUnitsRemaining = null,
                stockUnitsLastTotal = null,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                stockGeneration = 8L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun clearOnPreparationEdit_preservesWarnAtDaysRemaining() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow(stockGeneration = 3L, warnAtDaysRemaining = 21)

        mutator.clearStockOnPreparationEdit(database, medicineUuid, fixedNow)

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = false,
                stockUnitsRemaining = null,
                stockUnitsLastTotal = null,
                openContainerAmount = null,
                warnAtDaysRemaining = 21,
                stockGeneration = 4L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun clearOnArchive_medicineMissing_noWrite() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns null

        mutator.clearStockOnArchive(database, medicineUuid, fixedNow)

        coVerify(exactly = 0) {
            medicineDao.updateStockFields(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun enableTracking_writesInitialValuesAndBumpsGeneration() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow(
                trackingEnabled = false,
                stockUnitsRemaining = null,
                stockUnitsLastTotal = null,
                stockGeneration = 0L,
            )

        mutator.enableTracking(
            database = database,
            medicineUuid = medicineUuid,
            initialUnitsRemaining = 30.0,
            initialOpenContainerAmount = null,
            initialUnitsLastTotal = 30.0,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 30.0,
                stockUnitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun enableTracking_preservesWarnAtDaysRemainingFromExistingRow() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow(trackingEnabled = false, warnAtDaysRemaining = 21, stockGeneration = 2L)

        mutator.enableTracking(
            database = database,
            medicineUuid = medicineUuid,
            initialUnitsRemaining = 60.0,
            initialOpenContainerAmount = null,
            initialUnitsLastTotal = 60.0,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 60.0,
                stockUnitsLastTotal = 60.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 21,
                stockGeneration = 3L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun enableTracking_writesInitialOpenContainerAmount() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(
                trackingEnabled = false,
                sealed = null,
                open = null,
                stockGeneration = 4L,
            )

        mutator.enableTracking(
            database = database,
            medicineUuid = medicineUuid,
            initialUnitsRemaining = 2.0,
            initialOpenContainerAmount = 0.4,
            initialUnitsLastTotal = null,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 2.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 0.4,
                warnAtDaysRemaining = 14,
                stockGeneration = 5L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun enableTracking_containerWithoutOpenPromotesFirstContainer() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(
                trackingEnabled = false,
                sealed = null,
                open = null,
                vialVolume = 5.0,
                stockGeneration = 4L,
            )

        mutator.enableTracking(
            database = database,
            medicineUuid = medicineUuid,
            initialUnitsRemaining = 10.0,
            initialOpenContainerAmount = null,
            initialUnitsLastTotal = 10.0,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 9.0,
                stockUnitsLastTotal = 9.0,
                openContainerAmount = 5.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 5L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun disableTracking_clearsPoolStockAndBumpsGeneration() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow(
                trackingEnabled = true,
                stockUnitsRemaining = 25.0,
                stockUnitsLastTotal = 30.0,
                warnAtDaysRemaining = 21,
                stockGeneration = 5L,
            )

        mutator.disableTracking(database, medicineUuid, fixedNow)

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = false,
                stockUnitsRemaining = null,
                stockUnitsLastTotal = null,
                openContainerAmount = null,
                warnAtDaysRemaining = 21,
                stockGeneration = 6L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun disableTracking_clearsOpenContainerStockAndBumpsGeneration() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(
                trackingEnabled = true,
                sealed = 2.0,
                open = 0.4,
                warnAtDaysRemaining = 21,
                stockGeneration = 5L,
            )

        mutator.disableTracking(database, medicineUuid, fixedNow)

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = false,
                stockUnitsRemaining = null,
                stockUnitsLastTotal = null,
                openContainerAmount = null,
                warnAtDaysRemaining = 21,
                stockGeneration = 6L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun recount_pool_snapsLastTotalToCurrentAndBumpsGeneration() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow(stockUnitsRemaining = 5.0, stockUnitsLastTotal = 30.0, stockGeneration = 1L)

        mutator.applyRecount(
            database = database,
            medicineUuid = medicineUuid,
            recount = StockRecount(unitsRemaining = 20.0),
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 20.0,
                stockUnitsLastTotal = 20.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                stockGeneration = 2L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun recount_container_snapsSealedLastTotalAndPreservesOpen() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 2.0, open = 0.5, vialVolume = 1.0)

        mutator.applyRecount(
            database = database,
            medicineUuid = medicineUuid,
            recount = StockRecount(unitsRemaining = 3.0),
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 3.0,
                stockUnitsLastTotal = 3.0,
                openContainerAmount = 0.5,
                warnAtDaysRemaining = 14,
                stockGeneration = 2L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun recount_container_emptyOpenPromotesFirstCountedContainer() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 2.0, open = 0.0, vialVolume = 1.0)

        mutator.applyRecount(
            database = database,
            medicineUuid = medicineUuid,
            recount = StockRecount(unitsRemaining = 3.0),
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 2.0,
                stockUnitsLastTotal = 2.0,
                openContainerAmount = 1.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 2L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun recount_medicineMissing_noWrite() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns null

        mutator.applyRecount(
            database = database,
            medicineUuid = medicineUuid,
            recount = StockRecount(unitsRemaining = 20.0),
            now = fixedNow,
        )

        coVerify(exactly = 0) {
            medicineDao.updateStockFields(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun received_pool_addsToRemainingAndResetsLastTotalToCurrentAfter() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            pillRow(stockUnitsRemaining = 5.0, stockUnitsLastTotal = 30.0, stockGeneration = 4L)

        mutator.applyReceived(
            database = database,
            medicineUuid = medicineUuid,
            received = StockReceived(unitsReceived = 60.0),
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 65.0,
                stockUnitsLastTotal = 65.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                stockGeneration = 4L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun received_container_addsToSealedAndPreservesOpen() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 1.0, open = 0.4, vialVolume = 1.0, stockGeneration = 3L)

        mutator.applyReceived(
            database = database,
            medicineUuid = medicineUuid,
            received = StockReceived(unitsReceived = 5.0),
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 6.0,
                stockUnitsLastTotal = 6.0,
                openContainerAmount = 0.4,
                warnAtDaysRemaining = 14,
                stockGeneration = 3L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun received_container_promotesFirstContainerWhenNoOpenExists() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 2.0, open = null, vialVolume = 1.0)

        mutator.applyReceived(
            database = database,
            medicineUuid = medicineUuid,
            received = StockReceived(unitsReceived = 3.0),
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 4.0,
                stockUnitsLastTotal = 4.0,
                openContainerAmount = 1.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun received_container_emptyOpenPromotesAfterAddingSealedStock() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 2.0, open = 0.0, vialVolume = 1.0)

        mutator.applyReceived(
            database = database,
            medicineUuid = medicineUuid,
            received = StockReceived(unitsReceived = 3.0),
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 4.0,
                stockUnitsLastTotal = 4.0,
                openContainerAmount = 1.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun received_medicineMissing_noWrite() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns null

        mutator.applyReceived(
            database = database,
            medicineUuid = medicineUuid,
            received = StockReceived(unitsReceived = 2.0),
            now = fixedNow,
        )

        coVerify(exactly = 0) {
            medicineDao.updateStockFields(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun setOpenContainerAmount_clampsToContainerSizeAndPreservesEverythingElse() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 4.0, open = 0.3, vialVolume = 1.0, stockGeneration = 7L)

        mutator.applySetOpenContainerAmount(
            database = database,
            medicineUuid = medicineUuid,
            amount = 2.5,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 4.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 1.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 7L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun setOpenContainerAmount_clampsNegativeToZero() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 2.0, open = 0.4, vialVolume = 1.0)

        mutator.applySetOpenContainerAmount(
            database = database,
            medicineUuid = medicineUuid,
            amount = -0.5,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 1.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 1.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun setOpenContainerAmount_zeroWithSealedPromotesNextContainer() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 2.0, open = 0.4, vialVolume = 1.0)

        mutator.applySetOpenContainerAmount(
            database = database,
            medicineUuid = medicineUuid,
            amount = 0.0,
            now = fixedNow,
        )

        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 1.0,
                stockUnitsLastTotal = null,
                openContainerAmount = 1.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun setOpenContainerAmount_positiveAmountOnLegacyEmptyOpenCracksSealedWithoutInflatingTotal() = runTest {
        // Legacy/imported row: empty open with sealed stock. The read layer shows
        // it already cracked (2 sealed + a full open), so the edit dialog hands us
        // an absolute open amount relative to that healed view. The setter must
        // canonicalize the raw row first; otherwise the still-raw sealed count (3)
        // is left untouched and double-counts a container, inflating total stock.
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns
            vialRow(sealed = 3.0, open = null, vialVolume = 10.0, lastTotal = 3.0)

        mutator.applySetOpenContainerAmount(
            database = database,
            medicineUuid = medicineUuid,
            amount = 5.0,
            now = fixedNow,
        )

        // Cracked: 3 sealed -> 2 sealed + the edited 5 mL open (total 25, not 35),
        // and the gauge denominator drops with it to stay consistent with reads.
        coVerify {
            medicineDao.updateStockFields(
                uuid = medicineUuid.toString(),
                trackingEnabled = true,
                stockUnitsRemaining = 2.0,
                stockUnitsLastTotal = 2.0,
                openContainerAmount = 5.0,
                warnAtDaysRemaining = 14,
                stockGeneration = 1L,
                updatedAtEpochMillis = fixedNow.toEpochMilli(),
            )
        }
    }

    @Test
    fun setOpenContainerAmount_medicineMissing_noWrite() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns null

        mutator.applySetOpenContainerAmount(
            database = database,
            medicineUuid = medicineUuid,
            amount = 0.5,
            now = fixedNow,
        )

        coVerify(exactly = 0) {
            medicineDao.updateStockFields(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

}
