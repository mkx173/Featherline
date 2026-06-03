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

private const val FLOAT_EPSILON = 1e-9

private fun Double.zeroIfTiny(): Double {
    return if (abs(this) <= FLOAT_EPSILON) 0.0 else this
}

internal fun normalizeOpenContainer(
    open: Double?,
    sealed: Double,
    capacity: Double,
): Pair<Double?, Double> {
    val usableOpen = open?.takeIf { it.isFinite() } ?: 0.0
    if (usableOpen > FLOAT_EPSILON) return open to sealed
    if (sealed < 1.0 || !capacity.isFinite() || capacity <= 0.0) return open to sealed
    return capacity to (sealed - 1.0).coerceAtLeast(0.0).zeroIfTiny()
}

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

    /** Resolves and applies the stock deduction for an inserted live log. */
    suspend fun resolveDeductionForInsert(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        requestedDose: Double,
        now: Instant = Instant.now(),
    ) {
        val dao = database.medicineDao()
        val entity = dao.getByUuid(medicineUuid.toString()) ?: return
        val prepType = MedicinePreparationType.fromStorageValue(entity.preparationType)
        if (!entity.trackingEnabled || prepType == MedicinePreparationType.PATCH_OFF) {
            return
        }

        val nowMs = now.toEpochMilli()

        if (!prepType.isContainerTopology()) {
            val remaining = entity.stockUnitsRemaining ?: 0.0
            val actuallyDeducted = minOf(requestedDose, remaining).coerceAtLeast(0.0)
            val newRemaining = (remaining - actuallyDeducted).zeroIfTiny()
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
            return
        }

        resolveContainerDeductionForInsert(dao, entity, requestedDose, nowMs)
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
        val prepType = MedicinePreparationType.fromStorageValue(entity.preparationType)
        val stockFields = if (prepType.isContainerTopology()) {
            entity.promoteFirstContainerIfNeeded(
                sealedCount = initialUnitsRemaining,
                lastTotal = initialUnitsLastTotal,
                openAmount = initialOpenContainerAmount,
            )
        } else {
            StockFields(
                unitsRemaining = initialUnitsRemaining,
                unitsLastTotal = initialUnitsLastTotal,
                openContainerAmount = null,
            )
        }
        dao.updateStockFields(
            uuid = entity.uuid,
            trackingEnabled = true,
            stockUnitsRemaining = stockFields.unitsRemaining,
            stockUnitsLastTotal = stockFields.unitsLastTotal,
            openContainerAmount = stockFields.openContainerAmount,
            warnAtDaysRemaining = entity.warnAtDaysRemaining,
            stockGeneration = entity.stockGeneration + 1L,
            updatedAtEpochMillis = now.toEpochMilli(),
        )
    }

    /** Sets trackingEnabled=false, clears stock columns, and bumps stockGeneration. */
    suspend fun disableTracking(
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

    /** Recount: bumps stockGeneration for the new counted stock session. */
    suspend fun applyRecount(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        recount: StockRecount,
        now: Instant = Instant.now(),
    ) {
        val dao = database.medicineDao()
        val entity = dao.getByUuid(medicineUuid.toString()) ?: return
        val prepType = MedicinePreparationType.fromStorageValue(entity.preparationType)
        val stockFields = if (prepType.isContainerTopology()) {
            val recountedSealed = maxOf(0.0, recount.unitsRemaining)
            entity.promoteFirstContainerIfNeeded(
                sealedCount = recountedSealed,
                lastTotal = recountedSealed,
                openAmount = entity.openContainerAmount,
            )
        } else {
            val recountedRemaining = maxOf(0.0, recount.unitsRemaining)
            StockFields(
                unitsRemaining = recountedRemaining,
                unitsLastTotal = recountedRemaining,
                openContainerAmount = null,
            )
        }

        dao.updateStockFields(
            uuid = entity.uuid,
            trackingEnabled = true,
            stockUnitsRemaining = stockFields.unitsRemaining,
            stockUnitsLastTotal = stockFields.unitsLastTotal,
            openContainerAmount = stockFields.openContainerAmount,
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
            val newSealed = (entity.stockUnitsRemaining ?: 0.0) + received.unitsReceived
            val stockFields = entity.promoteFirstContainerIfNeeded(
                sealedCount = newSealed,
                lastTotal = newSealed,
                openAmount = entity.openContainerAmount,
            )
            dao.updateStockFields(
                uuid = entity.uuid,
                trackingEnabled = true,
                stockUnitsRemaining = stockFields.unitsRemaining,
                stockUnitsLastTotal = stockFields.unitsLastTotal,
                openContainerAmount = stockFields.openContainerAmount,
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

    private data class StockFields(
        val unitsRemaining: Double?,
        val unitsLastTotal: Double?,
        val openContainerAmount: Double?,
    )

    private fun MedicineEntity.promoteFirstContainerIfNeeded(
        sealedCount: Double?,
        lastTotal: Double?,
        openAmount: Double?,
    ): StockFields {
        val containerSize = containerSizeOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return StockFields(
                unitsRemaining = sealedCount,
                unitsLastTotal = lastTotal,
                openContainerAmount = openAmount,
            )
        val sealed = sealedCount?.takeIf { it.isFinite() } ?: 0.0
        val (normalizedOpen, normalizedSealed) = normalizeOpenContainer(
            open = openAmount,
            sealed = sealed,
            capacity = containerSize,
        )
        val cracked = normalizedSealed != sealed
        if (!cracked) {
            return StockFields(
                unitsRemaining = sealedCount,
                unitsLastTotal = lastTotal,
                openContainerAmount = openAmount,
            )
        }
        return StockFields(
            unitsRemaining = normalizedSealed.zeroIfTiny(),
            unitsLastTotal = lastTotal?.let { total ->
                if (total.isFinite()) {
                    (total - 1.0).coerceAtLeast(0.0).zeroIfTiny()
                } else {
                    total
                }
            },
            openContainerAmount = normalizedOpen,
        )
    }

    /**
     * Sets the open container amount directly. Container topology only — pool
     * preparations have no open container. Clamps to [0, containerSize]. When
     * the result lands empty and sealed stock remains, eagerly cracks one
     * sealed container so an empty open gauge never persists, decrementing the
     * sealed count to match. No generation bump: this is an incremental stock
     * edit, not a new counted session.
     */
    suspend fun applySetOpenContainerAmount(
        database: HrtTrackerDatabase,
        medicineUuid: UUID,
        amount: Double,
        now: Instant = Instant.now(),
    ) {
        val dao = database.medicineDao()
        val entity = dao.getByUuid(medicineUuid.toString()) ?: return
        val containerSize = entity.containerSizeOrNull() ?: return
        val clamped = amount.coerceIn(0.0, containerSize).zeroIfTiny()
        val sealed = entity.stockUnitsRemaining?.takeIf { it.isFinite() } ?: 0.0
        val (normalizedOpen, normalizedSealed) = normalizeOpenContainer(
            open = clamped,
            sealed = sealed,
            capacity = containerSize,
        )
        dao.updateStockFields(
            uuid = entity.uuid,
            trackingEnabled = true,
            stockUnitsRemaining = normalizedSealed.zeroIfTiny(),
            stockUnitsLastTotal = entity.stockUnitsLastTotal,
            openContainerAmount = normalizedOpen,
            warnAtDaysRemaining = entity.warnAtDaysRemaining,
            stockGeneration = entity.stockGeneration,
            updatedAtEpochMillis = now.toEpochMilli(),
        )
    }

    private suspend fun resolveContainerDeductionForInsert(
        dao: MedicineDao,
        entity: MedicineEntity,
        requestedDose: Double,
        nowMs: Long,
    ) {
        val containerSize = entity.containerSizeOrNull() ?: return
        val open = entity.openContainerAmount ?: 0.0
        val sealed = entity.stockUnitsRemaining ?: 0.0
        val dose = requestedDose.coerceAtLeast(0.0)

        val newSealed: Double
        val newOpen: Double

        when {
            hasSufficientOpenAmount(open = open, dose = dose) -> {
                newSealed = sealed
                newOpen = (open - dose).zeroIfTiny().coerceAtLeast(0.0)
            }

            sealed >= 1.0 -> {
                newSealed = sealed - 1.0
                newOpen = maxOf(0.0, containerSize - dose)
            }

            else -> {
                newSealed = sealed
                newOpen = 0.0
            }
        }

        val (normalizedOpen, normalizedSealed) = normalizeOpenContainer(
            open = newOpen.zeroIfTiny(),
            sealed = newSealed,
            capacity = containerSize,
        )

        dao.updateStockFields(
            uuid = entity.uuid,
            trackingEnabled = true,
            stockUnitsRemaining = normalizedSealed.zeroIfTiny(),
            stockUnitsLastTotal = entity.stockUnitsLastTotal,
            openContainerAmount = normalizedOpen,
            warnAtDaysRemaining = entity.warnAtDaysRemaining,
            stockGeneration = entity.stockGeneration,
            updatedAtEpochMillis = nowMs,
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

    internal companion object {
        const val DEFAULT_WARN_AT_DAYS: Int = 14
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
