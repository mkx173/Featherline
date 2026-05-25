package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Transaction-scoped helper for every stock-column write. Callers must wrap
 * invocations in their own `databaseHolder.withTransaction { database -> ... }`
 * block so the stock mutation commits atomically with the caller's other
 * writes (log insert/delete, archive, preparation edit, etc.).
 *
 * Never opens its own transaction; never invokes HomeSnapshotRepository.
 */
@Singleton
internal class MedicineStockMutator @Inject constructor() {

    /**
     * Resolves how much stock should actually be deducted for an inserted log,
     * applies that mutation to the medicine row, and returns the values the
     * caller should stamp onto the new log row.
     *
     * Returns null when no stamp applies: tracking off, PATCH_OFF medicine, or
     * medicine row not found.
     */
    suspend fun resolveDeductionForInsert(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        requestedDose: Double,
        now: Instant = Instant.now(),
    ): StockDeductionStamp? {
        val dao = database.medicineDao()
        val entity = dao.getByUuid(medicineUuid.toString()) ?: return null
        val prepType = MedicinePreparationType.fromStorageValue(entity.preparationType)
        if (!entity.trackingEnabled || prepType == MedicinePreparationType.PATCH_OFF) {
            return null
        }

        val nowMs = now.toEpochMilli()

        if (!prepType.isContainerTopology()) {
            val remaining = entity.stockUnitsRemaining ?: 0.0
            val actuallyDeducted = minOf(requestedDose, remaining).coerceAtLeast(0.0)
            val newRemaining = remaining - actuallyDeducted
            dao.updateStockFields(
                uuid = entity.uuid,
                trackingEnabled = true,
                stockUnitsRemaining = newRemaining,
                stockUnitsLastTotal = entity.stockUnitsLastTotal,
                openContainerAmount = null,
                warnAtDaysRemaining = entity.warnAtDaysRemaining,
                stockGeneration = entity.stockGeneration,
                updatedAtEpochMillis = nowMs,
            )
            return StockDeductionStamp(
                deductionUnits = actuallyDeducted,
                generation = entity.stockGeneration,
            )
        }

        return resolveContainerDeductionForInsert(dao, entity, requestedDose, nowMs)
    }

    /**
     * Applied at log-delete time. Reads the medicine row, evaluates the three
     * refund gates from design section 3.6, and applies the refund if all pass.
     */
    suspend fun applyRefundForDelete(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        log: MedicationLogStockSnapshot,
        now: Instant = Instant.now(),
    ) {
        val deduction = log.deductionUnits ?: return
        val logGeneration = log.generation ?: return

        val dao = database.medicineDao()
        val entity = dao.getByUuid(medicineUuid.toString()) ?: return
        if (logGeneration != entity.stockGeneration || !entity.trackingEnabled) {
            return
        }

        val prepType = MedicinePreparationType.fromStorageValue(entity.preparationType)
        val nowMs = now.toEpochMilli()

        if (prepType.isContainerTopology()) {
            val containerSize = entity.containerSizeOrNull() ?: return
            val newOpen = minOf(
                containerSize,
                (entity.openContainerAmount ?: 0.0) + deduction,
            )
            dao.updateStockFields(
                uuid = entity.uuid,
                trackingEnabled = true,
                stockUnitsRemaining = entity.stockUnitsRemaining,
                stockUnitsLastTotal = entity.stockUnitsLastTotal,
                openContainerAmount = newOpen,
                warnAtDaysRemaining = entity.warnAtDaysRemaining,
                stockGeneration = entity.stockGeneration,
                updatedAtEpochMillis = nowMs,
            )
        } else {
            val newRemaining = (entity.stockUnitsRemaining ?: 0.0) + deduction
            dao.updateStockFields(
                uuid = entity.uuid,
                trackingEnabled = true,
                stockUnitsRemaining = newRemaining,
                stockUnitsLastTotal = entity.stockUnitsLastTotal,
                openContainerAmount = null,
                warnAtDaysRemaining = entity.warnAtDaysRemaining,
                stockGeneration = entity.stockGeneration,
                updatedAtEpochMillis = nowMs,
            )
        }
    }

    /** Bumps stockGeneration, clears stock columns, and resets warnAtDaysRemaining. */
    suspend fun clearStockOnArchive(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        now: Instant = Instant.now(),
    ) {
        clearStockInternal(
            database = database,
            medicineUuid = medicineUuid,
            resetWarnAtDays = true,
            now = now,
        )
    }

    /** Bumps stockGeneration and clears stock columns; warnAtDaysRemaining is preserved. */
    suspend fun clearStockOnPreparationEdit(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        now: Instant = Instant.now(),
    ) {
        clearStockInternal(
            database = database,
            medicineUuid = medicineUuid,
            resetWarnAtDays = false,
            now = now,
        )
    }

    /** Sets trackingEnabled=true, writes initial values, and bumps stockGeneration. */
    suspend fun enableTracking(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        initialUnitsRemaining: Double?,
        initialOpenContainerAmount: Double?,
        initialUnitsLastTotal: Double?,
        now: Instant = Instant.now(),
    ) {
        val dao = database.medicineDao()
        val entity = dao.getByUuid(medicineUuid.toString()) ?: return
        dao.updateStockFields(
            uuid = entity.uuid,
            trackingEnabled = true,
            stockUnitsRemaining = initialUnitsRemaining,
            stockUnitsLastTotal = initialUnitsLastTotal,
            openContainerAmount = initialOpenContainerAmount,
            warnAtDaysRemaining = entity.warnAtDaysRemaining,
            stockGeneration = entity.stockGeneration + 1L,
            updatedAtEpochMillis = now.toEpochMilli(),
        )
    }

    /** Sets trackingEnabled=false. Preserves values. Does not bump generation. */
    suspend fun disableTracking(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        now: Instant = Instant.now(),
    ) {
        val dao = database.medicineDao()
        val entity = dao.getByUuid(medicineUuid.toString()) ?: return
        dao.updateStockFields(
            uuid = entity.uuid,
            trackingEnabled = false,
            stockUnitsRemaining = entity.stockUnitsRemaining,
            stockUnitsLastTotal = entity.stockUnitsLastTotal,
            openContainerAmount = entity.openContainerAmount,
            warnAtDaysRemaining = entity.warnAtDaysRemaining,
            stockGeneration = entity.stockGeneration,
            updatedAtEpochMillis = now.toEpochMilli(),
        )
    }

    /** Recount: bumps stockGeneration to invalidate pre-Recount markers. */
    suspend fun applyRecount(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        recount: StockRecount,
        now: Instant = Instant.now(),
    ) {
        val dao = database.medicineDao()
        val entity = dao.getByUuid(medicineUuid.toString()) ?: return
        val prepType = MedicinePreparationType.fromStorageValue(entity.preparationType)

        val newOpen: Double?
        val newLastTotal: Double?
        if (prepType.isContainerTopology()) {
            val containerSize = entity.containerSizeOrNull()
            newOpen = recount.openContainerAmount?.let { amount ->
                val nonNegativeAmount = maxOf(0.0, amount)
                containerSize?.let { minOf(it, nonNegativeAmount) } ?: nonNegativeAmount
            }
            newLastTotal = null
        } else {
            newOpen = null
            newLastTotal = recount.unitsLastTotal ?: recount.unitsRemaining
        }

        dao.updateStockFields(
            uuid = entity.uuid,
            trackingEnabled = true,
            stockUnitsRemaining = maxOf(0.0, recount.unitsRemaining),
            stockUnitsLastTotal = newLastTotal,
            openContainerAmount = newOpen,
            warnAtDaysRemaining = entity.warnAtDaysRemaining,
            stockGeneration = entity.stockGeneration + 1L,
            updatedAtEpochMillis = now.toEpochMilli(),
        )
    }

    /** Received: incremental top-up. No generation bump. */
    suspend fun applyReceived(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        received: StockReceived,
        now: Instant = Instant.now(),
    ) {
        val dao = database.medicineDao()
        val entity = dao.getByUuid(medicineUuid.toString()) ?: return
        val prepType = MedicinePreparationType.fromStorageValue(entity.preparationType)
        val nowMs = now.toEpochMilli()

        if (prepType.isContainerTopology()) {
            val containerSize = entity.containerSizeOrNull()
            val newSealed = (entity.stockUnitsRemaining ?: 0.0) + received.unitsReceived
            val newOpen = received.openContainerAmount?.let { amount ->
                val nonNegativeAmount = maxOf(0.0, amount)
                containerSize?.let { minOf(it, nonNegativeAmount) } ?: nonNegativeAmount
            } ?: entity.openContainerAmount
            dao.updateStockFields(
                uuid = entity.uuid,
                trackingEnabled = true,
                stockUnitsRemaining = newSealed,
                stockUnitsLastTotal = null,
                openContainerAmount = newOpen,
                warnAtDaysRemaining = entity.warnAtDaysRemaining,
                stockGeneration = entity.stockGeneration,
                updatedAtEpochMillis = nowMs,
            )
        } else {
            val newRemaining = (entity.stockUnitsRemaining ?: 0.0) + received.unitsReceived
            dao.updateStockFields(
                uuid = entity.uuid,
                trackingEnabled = true,
                stockUnitsRemaining = newRemaining,
                stockUnitsLastTotal = newRemaining,
                openContainerAmount = null,
                warnAtDaysRemaining = entity.warnAtDaysRemaining,
                stockGeneration = entity.stockGeneration,
                updatedAtEpochMillis = nowMs,
            )
        }
    }

    /** Discard: incremental reduction. No generation bump. */
    suspend fun applyDiscard(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        discard: StockDiscard,
        now: Instant = Instant.now(),
    ) {
        val dao = database.medicineDao()
        val entity = dao.getByUuid(medicineUuid.toString()) ?: return
        val nowMs = now.toEpochMilli()

        val newRemaining: Double?
        val newOpen: Double?
        when (discard) {
            is StockDiscard.FromPool -> {
                val units = discard.units.coerceAtLeast(0.0)
                newRemaining = maxOf(0.0, (entity.stockUnitsRemaining ?: 0.0) - units)
                newOpen = null
            }

            is StockDiscard.FromOpenContainer -> {
                val amount = discard.amount.coerceAtLeast(0.0)
                newRemaining = entity.stockUnitsRemaining
                newOpen = maxOf(0.0, (entity.openContainerAmount ?: 0.0) - amount)
            }

            is StockDiscard.FromSealed -> {
                val units = discard.units.coerceAtLeast(0.0)
                newRemaining = maxOf(0.0, (entity.stockUnitsRemaining ?: 0.0) - units)
                newOpen = entity.openContainerAmount
            }
        }

        dao.updateStockFields(
            uuid = entity.uuid,
            trackingEnabled = true,
            stockUnitsRemaining = newRemaining,
            stockUnitsLastTotal = entity.stockUnitsLastTotal,
            openContainerAmount = newOpen,
            warnAtDaysRemaining = entity.warnAtDaysRemaining,
            stockGeneration = entity.stockGeneration,
            updatedAtEpochMillis = nowMs,
        )
    }

    private suspend fun resolveContainerDeductionForInsert(
        dao: MedicineDao,
        entity: MedicineEntity,
        requestedDose: Double,
        nowMs: Long,
    ): StockDeductionStamp {
        val containerSize = entity.containerSizeOrNull() ?: return StockDeductionStamp(
            deductionUnits = 0.0,
            generation = entity.stockGeneration,
        )
        val open = entity.openContainerAmount ?: 0.0
        val sealed = entity.stockUnitsRemaining ?: 0.0
        val dose = requestedDose.coerceAtLeast(0.0)

        val newSealed: Double
        val newOpen: Double
        val actuallyDeducted: Double

        when {
            hasSufficientOpenAmount(open = open, dose = dose) -> {
                newSealed = sealed
                newOpen = (open - dose).zeroIfTiny().coerceAtLeast(0.0)
                actuallyDeducted = dose
            }

            sealed >= 1.0 -> {
                newSealed = sealed - 1.0
                newOpen = maxOf(0.0, containerSize - dose)
                actuallyDeducted = open + minOf(dose, containerSize)
            }

            else -> {
                newSealed = sealed
                newOpen = 0.0
                actuallyDeducted = open
            }
        }

        dao.updateStockFields(
            uuid = entity.uuid,
            trackingEnabled = true,
            stockUnitsRemaining = newSealed,
            stockUnitsLastTotal = entity.stockUnitsLastTotal,
            openContainerAmount = newOpen,
            warnAtDaysRemaining = entity.warnAtDaysRemaining,
            stockGeneration = entity.stockGeneration,
            updatedAtEpochMillis = nowMs,
        )
        return StockDeductionStamp(
            deductionUnits = actuallyDeducted,
            generation = entity.stockGeneration,
        )
    }

    private fun hasSufficientOpenAmount(open: Double, dose: Double): Boolean {
        if (dose <= 0.0 || open >= dose) return true
        return open > FLOAT_EPSILON && dose - open <= FLOAT_EPSILON
    }

    private suspend fun clearStockInternal(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        resetWarnAtDays: Boolean,
        now: Instant,
    ) {
        val dao = database.medicineDao()
        val entity = dao.getByUuid(medicineUuid.toString()) ?: return
        dao.updateStockFields(
            uuid = entity.uuid,
            trackingEnabled = false,
            stockUnitsRemaining = null,
            stockUnitsLastTotal = null,
            openContainerAmount = null,
            warnAtDaysRemaining = if (resetWarnAtDays) {
                DEFAULT_WARN_AT_DAYS
            } else {
                entity.warnAtDaysRemaining
            },
            stockGeneration = entity.stockGeneration + 1L,
            updatedAtEpochMillis = now.toEpochMilli(),
        )
    }

    private fun Double.zeroIfTiny(): Double {
        return if (abs(this) <= FLOAT_EPSILON) 0.0 else this
    }

    internal companion object {
        const val DEFAULT_WARN_AT_DAYS: Int = 14

        private const val FLOAT_EPSILON = 1e-9
    }
}

internal fun MedicinePreparationType.isContainerTopology(): Boolean {
    return this == MedicinePreparationType.INJECTION_MULTI_USE_VIAL ||
        this == MedicinePreparationType.GEL_CONTAINER
}

internal fun MedicineEntity.containerSizeOrNull(): Double? {
    return when (MedicinePreparationType.fromStorageValue(preparationType)) {
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> vialVolumeMl
        MedicinePreparationType.GEL_CONTAINER -> containerWeightGrams
        else -> null
    }
}
