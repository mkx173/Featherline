package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationScopeInputValidationResult
import com.mkx.hrttracker.model.pk.PkCalibrationScopeInputValidator
import com.mkx.hrttracker.model.pk.PkCalibrationValidatedScopeInput
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class PkCalibrationReviewActionRejection {
    CURRENT_INPUT_UNAVAILABLE,
    INPUT_GENERATION_CHANGED,
    CALIBRATION_NOT_READY,
    SCOPE_NOT_CURRENT,
    LAB_NOT_AUTHORIZED_E2,
    NOT_CURRENT_UNREVIEWED_OUTLIER,
    NOT_CURRENTLY_EXCLUDED,
}

sealed interface PkCalibrationReviewActionResult {
    data class Applied(val metadata: E2CalibrationMetadata) :
        PkCalibrationReviewActionResult

    data class Rejected(val reason: PkCalibrationReviewActionRejection) :
        PkCalibrationReviewActionResult
}

/**
 * Persists the three v9 review transitions against the current generation-bound evaluation
 * context. The context provider rejects a stale Home generation or runtime policy before this
 * service runs, so input, metadata, identity policy, config, and scope remain one snapshot.
 *
 * The repository owns the Room transaction and the final built-in-E2 check. A
 * Keep records the acceptance context (model version, value bits, collection
 * time) produced by the same current computation that named the row as an
 * unreviewed outlier. There is intentionally no compare-and-swap: a later
 * semantic edit makes that stored record stale and the evidence adapter
 * consequently treats the acceptance as AUTO.
 */
@Singleton
class PkCalibrationReviewActionService @Inject constructor(
    private val storageRepository: PkCalibrationStorageRepository,
    private val currentContextProvider: PkCalibrationCurrentEvaluationContextProvider,
    private val clock: Clock,
) {
    suspend fun keepForAdjustment(resultId: UUID): PkCalibrationReviewActionResult {
        val current = currentContext() ?: return rejected(
            PkCalibrationReviewActionRejection.CURRENT_INPUT_UNAVAILABLE
        )
        val evaluation = PkCalibrationEngine.evaluate(
            input = current.input,
            metadata = current.metadata,
            identityPolicy = current.identityPolicy,
            config = current.config,
            attestationProvider = current.attestationProvider,
        )
        val evidence = evaluation.readyEvidence ?: return rejected(
            PkCalibrationReviewActionRejection.CALIBRATION_NOT_READY
        )
        if (evidence.canonicalInput.authorizedLabs.none { lab -> lab.resultId == resultId }) {
            return rejected(PkCalibrationReviewActionRejection.LAB_NOT_AUTHORIZED_E2)
        }
        val isCurrentOutlier = evaluation.result.routeResults.any { routeResult ->
            resultId in routeResult.unreviewedOutlierLabIds
        }
        if (!isCurrentOutlier) {
            return rejected(
                PkCalibrationReviewActionRejection.NOT_CURRENT_UNREVIEWED_OUTLIER
            )
        }
        val record = evidence.canonicalInput.acceptanceRecordFor(resultId)
            ?: return rejected(PkCalibrationReviewActionRejection.LAB_NOT_AUTHORIZED_E2)
        val metadata = requireNotNull(
            E2CalibrationMetadata.create(
                resultId = resultId,
                disposition = E2CalibrationDisposition.ACCEPTED,
                acceptedRecord = record,
                updatedAt = Instant.now(clock),
            )
        )
        return persist(metadata, current.inputGeneration)
    }

    suspend fun exclude(resultId: UUID): PkCalibrationReviewActionResult {
        val current = currentContext() ?: return rejected(
            PkCalibrationReviewActionRejection.CURRENT_INPUT_UNAVAILABLE
        )
        val scope = validateCurrentScope(current) ?: return rejected(
            PkCalibrationReviewActionRejection.SCOPE_NOT_CURRENT
        )
        if (scope.authorizedLabs.none { lab -> lab.resultId == resultId }) {
            return rejected(PkCalibrationReviewActionRejection.LAB_NOT_AUTHORIZED_E2)
        }
        val metadata = requireNotNull(
            E2CalibrationMetadata.create(
                resultId = resultId,
                disposition = E2CalibrationDisposition.EXCLUDED,
                acceptedRecord = null,
                updatedAt = Instant.now(clock),
            )
        )
        return persist(metadata, current.inputGeneration)
    }

    suspend fun reinclude(resultId: UUID): PkCalibrationReviewActionResult {
        val current = currentContext() ?: return rejected(
            PkCalibrationReviewActionRejection.CURRENT_INPUT_UNAVAILABLE
        )
        val scope = validateCurrentScope(current) ?: return rejected(
            PkCalibrationReviewActionRejection.SCOPE_NOT_CURRENT
        )
        if (scope.authorizedLabs.none { lab -> lab.resultId == resultId }) {
            return rejected(PkCalibrationReviewActionRejection.LAB_NOT_AUTHORIZED_E2)
        }
        val stored = current.metadata.singleOrNull { item -> item.resultId == resultId }
        if (stored?.disposition != E2CalibrationDisposition.EXCLUDED) {
            return rejected(PkCalibrationReviewActionRejection.NOT_CURRENTLY_EXCLUDED)
        }
        val metadata = requireNotNull(
            E2CalibrationMetadata.create(
                resultId = resultId,
                disposition = E2CalibrationDisposition.AUTO,
                acceptedRecord = null,
                updatedAt = Instant.now(clock),
            )
        )
        return persist(metadata, current.inputGeneration)
    }

    private suspend fun currentContext(): PkCalibrationEvaluationContext? {
        return try {
            currentContextProvider.currentEvaluationContext()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    private fun validateCurrentScope(
        context: PkCalibrationEvaluationContext,
    ): PkCalibrationValidatedScopeInput? {
        val input = context.input
        return when (
            val validation = PkCalibrationScopeInputValidator.validate(
                labs = input.labs,
                medicationEvents = input.medicationEvents,
                forwardTimeOriginEpochMillis = input.forwardTimeOriginEpochMillis,
                resolvedCurrentWeightKg = input.resolvedCurrentWeightKg,
                identityPolicy = context.identityPolicy,
                config = context.config,
                attestationProvider = context.attestationProvider,
                forwardModelVersion = input.forwardModelVersion,
                calibrationModelVersion = input.calibrationModelVersion,
            )
        ) {
            is PkCalibrationScopeInputValidationResult.Failed -> null
            is PkCalibrationScopeInputValidationResult.Ready -> validation.value
        }
    }

    private suspend fun persist(
        metadata: E2CalibrationMetadata,
        expectedGeneration: Long,
    ): PkCalibrationReviewActionResult {
        return try {
            if (storageRepository.saveMetadataIfCurrent(metadata, expectedGeneration)) {
                PkCalibrationReviewActionResult.Applied(metadata)
            } else {
                rejected(PkCalibrationReviewActionRejection.INPUT_GENERATION_CHANGED)
            }
        } catch (_: PkCalibrationMetadataTargetNotAuthorizedException) {
            rejected(PkCalibrationReviewActionRejection.LAB_NOT_AUTHORIZED_E2)
        }
    }

    private fun rejected(
        reason: PkCalibrationReviewActionRejection,
    ): PkCalibrationReviewActionResult.Rejected {
        return PkCalibrationReviewActionResult.Rejected(reason)
    }
}
