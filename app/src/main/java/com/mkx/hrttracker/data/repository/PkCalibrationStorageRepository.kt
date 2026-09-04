package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.E2CalibrationMetadataEntity
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PkCalibrationStorageRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotRepository: HomeSnapshotRepository,
) {
    suspend fun getAllMetadata(): List<E2CalibrationMetadata> {
        return databaseHolder.get().pkCalibrationDao().getAllMetadata()
            .map(E2CalibrationMetadataEntity::toModel)
    }

    /**
     * Writes review metadata through Home's mutation sequence so the Home
     * generation bumps and the live evaluation re-runs. The target is checked
     * inside the write transaction: only a built-in E2 result may carry
     * calibration metadata.
     */
    suspend fun saveMetadata(metadata: E2CalibrationMetadata) {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val dao = database.pkCalibrationDao()
                val analyteKey = dao.getBuiltinAnalyteKey(metadata.resultId.toString())
                require(analyteKey == BloodAnalyteKey.E2.storageValue) {
                    "Calibration metadata can only be stored for a built-in E2 result: " +
                            metadata.resultId
                }
                dao.upsertMetadata(metadata.toEntity())
            }
        }
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

internal fun E2CalibrationMetadataEntity.toModel(): E2CalibrationMetadata {
    return E2CalibrationMetadata(
        resultId = UUID.fromString(resultUuid),
        disposition = E2CalibrationDisposition.valueOf(disposition),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}
