package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
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
    SCOPE_NOT_CURRENT,
    LAB_NOT_AUTHORIZED_E2,
    NOT_CURRENTLY_EXCLUDED,
}

sealed interface PkCalibrationReviewActionResult {
    data class Applied(val metadata: E2CalibrationMetadata) :
        PkCalibrationReviewActionResult

    data class Rejected(val reason: PkCalibrationReviewActionRejection) :
        PkCalibrationReviewActionResult
}

/**
 * Persists the two review transitions (exclude, re-include) against the
 * current evaluation context. The repository owns the Room transaction and
 * the final built-in-E2 check; a concurrent Home-data edit simply re-runs the
 * evaluation with the new metadata (ponytail: no compare-and-swap, the write
 * is a single idempotent row keyed by result id).
 */
@Singleton
class PkCalibrationReviewActionService @Inject constructor(
    private val storageRepository: PkCalibrationStorageRepository,
    private val currentContextProvider: PkCalibrationCurrentEvaluationContextProvider,
    private val clock: Clock,
) {
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
        return persist(
            E2CalibrationMetadata(resultId, E2CalibrationDisposition.EXCLUDED, Instant.now(clock))
        )
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
        return persist(
            E2CalibrationMetadata(resultId, E2CalibrationDisposition.AUTO, Instant.now(clock))
        )
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
    ): PkCalibrationReviewActionResult {
        return try {
            storageRepository.saveMetadata(metadata)
            PkCalibrationReviewActionResult.Applied(metadata)
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
