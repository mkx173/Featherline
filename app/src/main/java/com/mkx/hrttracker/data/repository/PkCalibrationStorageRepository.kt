package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.E2CalibrationMetadataEntity
import com.mkx.hrttracker.data.local.PK_CALIBRATION_DISPLAY_SINGLETON_ID
import com.mkx.hrttracker.data.local.PkCalibrationDisplayArtifactEntity
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.pk.CanonicalDigest
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.PkCalibrationAcceptanceRecord
import com.mkx.hrttracker.model.pk.PersistedPkCalibrationDisplay
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.isStableAsciiIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
     * Writes review metadata only while [expectedGeneration] is still Home's current durable
     * input generation. Normal invalid targets fail preflight, and a stale generation exits,
     * before entering Home's mutation. The transactional target check is repeated only as a
     * safety net for an out-of-contract concurrent/direct database change after preflight; that
     * late failure occurs after the existing Home mutation has incremented its generation.
     */
    suspend fun saveMetadataIfCurrent(
        metadata: E2CalibrationMetadata,
        expectedGeneration: Long,
    ): Boolean {
        require(expectedGeneration >= 0L)
        requireBuiltInE2Target(metadata)
        return when (
            homeSnapshotRepository.runHomeDataMutationIfGenerationCurrent(
                expectedCurrentGeneration = expectedGeneration,
            ) {
                upsertValidatedMetadata(metadata)
            }
        ) {
            is HomeDataConditionalMutationResult.Published -> true
            is HomeDataConditionalMutationResult.Stale -> false
        }
    }

    private suspend fun upsertValidatedMetadata(metadata: E2CalibrationMetadata) {
        databaseHolder.withTransaction { database ->
            val dao = database.pkCalibrationDao()
            // Repeat the preflight check in the write transaction so a target that changes
            // while waiting for Home's mutation lock cannot receive calibration metadata.
            requireBuiltInE2Target(
                resultId = metadata.resultId,
                builtinAnalyteKey = dao.getBuiltinAnalyteKey(metadata.resultId.toString()),
            )
            dao.upsertMetadata(metadata.toEntity())
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

    /**
     * Capture before reading every input used to derive a display artifact. The Home
     * generation closes races with local Home-data writes, but it is not a substitute for
     * the canonical whole-fit input digest whose payload/schema is not yet declared.
     */
    suspend fun captureHomeDataGeneration(): Long {
        return homeSnapshotRepository.captureCurrentHomeDataGeneration()
    }

    /**
     * Calibration invalidates from Home's existing coherence generation. This is deliberately
     * not a second cache version or a calibration-specific compare-and-swap token.
     */
    fun observeHomeDataGeneration(): Flow<Long> {
        return homeSnapshotRepository.observeCurrentHomeDataGeneration()
    }

    /**
     * Publishes only if no Home-data mutation has occurred since [expectedInputGeneration]
     * was captured. On a match, the artifact is stamped with the newly incremented generation
     * and the existing synchronous Home clear/rebuild contract completes before this returns.
     */
    suspend fun publishDisplayArtifactIfCurrent(
        artifact: PersistedPkCalibrationDisplay,
        expectedInputGeneration: Long,
    ): Boolean {
        return when (
            homeSnapshotRepository.runHomeDataMutationIfGenerationCurrent(
                expectedCurrentGeneration = expectedInputGeneration,
            ) { newGeneration ->
                databaseHolder.get().pkCalibrationDao().upsertDisplayArtifact(
                    artifact.toEntity(homeSnapshotGeneration = newGeneration)
                )
            }
        ) {
            is HomeDataConditionalMutationResult.Published -> true
            is HomeDataConditionalMutationResult.Stale -> false
        }
    }

    /**
     * Reads and validates the whole row under one Room transaction. A malformed
     * or stale row is deleted in that same transaction so a concurrent newer
     * writer cannot be removed by a read-then-delete race.
     */
    suspend fun readDisplayArtifact(
        expectedCalibrationModelVersion: String,
        expectedResultInputDigest: CanonicalDigest,
    ): PersistedPkCalibrationDisplay? {
        require(expectedCalibrationModelVersion.isNotBlank())
        return databaseHolder.withTransaction { database ->
            val dao = database.pkCalibrationDao()
            val stored = dao.getDisplayArtifact() ?: return@withTransaction null
            val decoded = stored.toModel()
            if (decoded == null ||
                decoded.calibrationModelVersion != expectedCalibrationModelVersion ||
                decoded.resultInputDigest != expectedResultInputDigest
            ) {
                dao.deleteDisplayArtifact()
                null
            } else {
                decoded
            }
        }
    }

    suspend fun clearDisplayArtifact() {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.get().pkCalibrationDao().deleteDisplayArtifact()
        }
    }
}

internal fun E2CalibrationMetadata.toEntity(): E2CalibrationMetadataEntity {
    return E2CalibrationMetadataEntity(
        resultUuid = resultId.toString(),
        disposition = disposition.name,
        acceptedModelVersion = acceptedRecord?.calibrationModelVersion,
        acceptedSourceValueBits = acceptedRecord?.sourceValueBits,
        acceptedCollectedAtEpochMillis = acceptedRecord?.collectedAtEpochMillis,
        updatedAtEpochMillis = updatedAt.toEpochMilli(),
    )
}

internal fun E2CalibrationMetadataEntity.toModel(): E2CalibrationMetadata? {
    val parsedDisposition = runCatching { E2CalibrationDisposition.valueOf(disposition) }
        .getOrNull() ?: return null
    val hasAnyRecordField = acceptedModelVersion != null ||
        acceptedSourceValueBits != null || acceptedCollectedAtEpochMillis != null
    val record = if (hasAnyRecordField) {
        PkCalibrationAcceptanceRecord.create(
            calibrationModelVersion = acceptedModelVersion ?: return null,
            sourceValueBits = acceptedSourceValueBits ?: return null,
            collectedAtEpochMillis = acceptedCollectedAtEpochMillis ?: return null,
        ) ?: return null
    } else {
        null
    }
    return E2CalibrationMetadata.create(
        resultId = runCatching { java.util.UUID.fromString(resultUuid) }.getOrNull() ?: return null,
        disposition = parsedDisposition,
        acceptedRecord = record,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun PersistedPkCalibrationDisplay.toEntity(
    homeSnapshotGeneration: Long,
): PkCalibrationDisplayArtifactEntity {
    require(homeSnapshotGeneration >= 0L)
    return PkCalibrationDisplayArtifactEntity(
        singletonId = PK_CALIBRATION_DISPLAY_SINGLETON_ID,
        homeSnapshotGeneration = homeSnapshotGeneration,
        schema = schema,
        calibrationModelVersion = calibrationModelVersion,
        resultInputDigestSchema = resultInputDigest.schema,
        resultInputDigestAlgorithm = resultInputDigest.algorithm,
        resultInputDigestHexLower = resultInputDigest.hexLower,
        promotedRouteStableIds = promotedRoutes.joinToString(",", transform =
            PkCalibrationRoute::stableId),
        injectionLogScale = displayRouteLogScaleByRoute[PkCalibrationRoute.INJECTION],
        patchLogScale = displayRouteLogScaleByRoute[PkCalibrationRoute.PATCH],
        gelLogScale = displayRouteLogScaleByRoute[PkCalibrationRoute.GEL],
        oralLogScale = displayRouteLogScaleByRoute[PkCalibrationRoute.ORAL],
        sublingualLogScale = displayRouteLogScaleByRoute[PkCalibrationRoute.SUBLINGUAL],
    )
}

internal fun PkCalibrationDisplayArtifactEntity.toModel(): PersistedPkCalibrationDisplay? {
    if (singletonId != PK_CALIBRATION_DISPLAY_SINGLETON_ID) return null
    val promotedRoutes = if (promotedRouteStableIds.isEmpty()) {
        emptyList()
    } else {
        promotedRouteStableIds.split(',').map { stableId ->
            PkCalibrationRoute.fromStableId(stableId) ?: return null
        }
    }
    val betas = linkedMapOf<PkCalibrationRoute, Double>()
    injectionLogScale?.let { betas[PkCalibrationRoute.INJECTION] = it }
    patchLogScale?.let { betas[PkCalibrationRoute.PATCH] = it }
    gelLogScale?.let { betas[PkCalibrationRoute.GEL] = it }
    oralLogScale?.let { betas[PkCalibrationRoute.ORAL] = it }
    sublingualLogScale?.let { betas[PkCalibrationRoute.SUBLINGUAL] = it }
    val digest = CanonicalDigest.create(
        schema = resultInputDigestSchema,
        algorithm = resultInputDigestAlgorithm,
        hexLower = resultInputDigestHexLower,
    ) ?: return null
    return PersistedPkCalibrationDisplay.create(
        schema = schema,
        calibrationModelVersion = calibrationModelVersion,
        resultInputDigest = digest,
        promotedRoutes = promotedRoutes,
        displayRouteLogScaleByRoute = betas,
    )
}

/** Fail-closed Home projection of the durable singleton row. */
internal fun PkCalibrationDisplayArtifactEntity.toHomeCalibrationDisplay(
    expectedHomeSnapshotGeneration: Long,
    expectedCalibrationModelVersion: String,
): PersistedPkCalibrationDisplay? {
    if (expectedHomeSnapshotGeneration < 0L ||
        homeSnapshotGeneration != expectedHomeSnapshotGeneration ||
        !expectedCalibrationModelVersion.isStableAsciiIdentity()
    ) {
        return null
    }
    return toModel()?.takeIf { display ->
        display.calibrationModelVersion == expectedCalibrationModelVersion
    }
}
