package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.E2CalibrationMetadataEntity
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal class PkCalibrationMetadataTargetNotAuthorizedException(
    resultId: UUID,
) : IllegalArgumentException(
    "Calibration metadata can only be stored for a built-in E2 result: $resultId"
)

@Singleton
class PkCalibrationStorageRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotRepository: HomeSnapshotRepository,
) {
    suspend fun getAllMetadata(): List<E2CalibrationMetadata> {
        return databaseHolder.get().pkCalibrationDao().getAllMetadata().map { entity ->
            checkNotNull(entity.toModel()) { "Stored E2 calibration metadata is invalid." }
        }
    }

    fun observeAllMetadata(): Flow<List<E2CalibrationMetadata>> {
        return databaseHolder.get().pkCalibrationDao().observeAllMetadata().map { entities ->
            entities.map { entity ->
                checkNotNull(entity.toModel()) { "Stored E2 calibration metadata is invalid." }
            }
        }
    }

    /**
     * Writes review metadata through Home's mutation sequence so the Home
     * generation bumps and the live evaluation re-runs. The built-in-E2
     * target check is repeated inside the write transaction so a target
     * that changes while waiting for Home's mutation lock cannot receive
     * calibration metadata.
     */
    suspend fun saveMetadata(metadata: E2CalibrationMetadata) {
        requireBuiltInE2Target(metadata)
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val dao = database.pkCalibrationDao()
                requireBuiltInE2Target(
                    resultId = metadata.resultId,
                    builtinAnalyteKey = dao.getBuiltinAnalyteKey(metadata.resultId.toString()),
                )
                dao.upsertMetadata(metadata.toEntity())
            }
        }
    }

    private suspend fun requireBuiltInE2Target(metadata: E2CalibrationMetadata) {
        requireBuiltInE2Target(
            resultId = metadata.resultId,
            builtinAnalyteKey = databaseHolder.get()
                .pkCalibrationDao()
                .getBuiltinAnalyteKey(metadata.resultId.toString()),
        )
    }

    private fun requireBuiltInE2Target(
        resultId: UUID,
        builtinAnalyteKey: String?,
    ) {
        if (builtinAnalyteKey != BloodAnalyteKey.E2.storageValue) {
            throw PkCalibrationMetadataTargetNotAuthorizedException(resultId)
        }
    }

    /** Capture before reading every input of one evaluation; closes races with Home-data writes. */
    suspend fun captureHomeDataGeneration(): Long {
        return homeSnapshotRepository.captureCurrentHomeDataGeneration()
    }

    /**
     * Generated-at of the last written Home snapshot. It changes on every
     * rebuild: after a Home-data mutation has committed (so a calibration read
     * keyed on it always sees the new data) and on the snapshot-only rebuilds
     * (date change, projection expiry, chart window), so the live evaluation
     * follows the same window Home draws.
     */
    fun observeHomeSnapshotWrites(): Flow<Long> {
        return homeSnapshotRepository.observeHomeSnapshot()
            .mapNotNull { snapshot -> snapshot?.generatedAtEpochMillis }
            .distinctUntilChanged()
    }
}

internal fun E2CalibrationMetadata.toEntity(): E2CalibrationMetadataEntity {
    return E2CalibrationMetadataEntity(
        resultUuid = resultId.toString(),
        disposition = disposition.name,
        updatedAtEpochMillis = updatedAt.toEpochMilli(),
    )
}

internal fun E2CalibrationMetadataEntity.toModel(): E2CalibrationMetadata? {
    val parsedDisposition = runCatching { E2CalibrationDisposition.valueOf(disposition) }
        .getOrNull() ?: return null
    return E2CalibrationMetadata(
        resultId = runCatching { UUID.fromString(resultUuid) }.getOrNull() ?: return null,
        disposition = parsedDisposition,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}
