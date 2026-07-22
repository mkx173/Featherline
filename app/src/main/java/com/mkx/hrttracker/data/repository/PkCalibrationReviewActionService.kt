package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationIdentityPolicy
import com.mkx.hrttracker.model.pk.PkCalibrationInputSnapshot
import com.mkx.hrttracker.model.pk.PkCalibrationConfig
import com.mkx.hrttracker.model.pk.PkCalibrationScopeDecisionProvider
import com.mkx.hrttracker.model.pk.PkCalibrationScopeInputValidationResult
import com.mkx.hrttracker.model.pk.PkCalibrationScopeInputValidator
import com.mkx.hrttracker.model.pk.PkCalibrationValidatedScopeInput
import java.time.Clock
import java.time.Instant
import java.util.UUID

fun interface PkCalibrationCurrentInputProvider {
    suspend fun currentInput(): PkCalibrationInputSnapshot?
}

enum class PkCalibrationReviewActionRejection {
    CURRENT_INPUT_UNAVAILABLE,
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
 * Persists the three v9 review transitions against a fresh semantic-input read.
 *
 * The repository owns the Room transaction and the final built-in-E2 check. A
 * Keep records the digest produced by the same current computation that named
 * the row as an unreviewed outlier. There is intentionally no compare-and-swap:
 * a later semantic edit makes that stored digest stale and the evidence adapter
 * consequently treats the acceptance as AUTO.
 */
class PkCalibrationReviewActionService(
    private val storageRepository: PkCalibrationStorageRepository,
    private val currentInputProvider: PkCalibrationCurrentInputProvider,
    private val identityPolicy: PkCalibrationIdentityPolicy,
    private val config: PkCalibrationConfig,
    private val scopeDecisionProvider: PkCalibrationScopeDecisionProvider,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun keepForAdjustment(resultId: UUID): PkCalibrationReviewActionResult {
        val current = currentContext() ?: return rejected(
            PkCalibrationReviewActionRejection.CURRENT_INPUT_UNAVAILABLE
        )
        val evaluation = PkCalibrationEngine.evaluate(
            input = current.input,
            metadata = current.metadata,
            identityPolicy = identityPolicy,
            config = config,
            scopeDecisionProvider = scopeDecisionProvider,
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
        val digest = evidence.canonicalInput.reviewDigestFor(resultId)
            ?: return rejected(PkCalibrationReviewActionRejection.LAB_NOT_AUTHORIZED_E2)
        val metadata = requireNotNull(
            E2CalibrationMetadata.create(
                resultId = resultId,
                disposition = E2CalibrationDisposition.ACCEPTED,
                acceptedReviewDigest = digest,
                updatedAt = Instant.now(clock),
            )
        )
        return persist(metadata)
    }

    suspend fun exclude(resultId: UUID): PkCalibrationReviewActionResult {
        val current = currentContext() ?: return rejected(
            PkCalibrationReviewActionRejection.CURRENT_INPUT_UNAVAILABLE
        )
        val scope = validateCurrentScope(current.input) ?: return rejected(
            PkCalibrationReviewActionRejection.SCOPE_NOT_CURRENT
        )
        if (scope.authorizedLabs.none { lab -> lab.resultId == resultId }) {
            return rejected(PkCalibrationReviewActionRejection.LAB_NOT_AUTHORIZED_E2)
        }
        val metadata = requireNotNull(
            E2CalibrationMetadata.create(
                resultId = resultId,
                disposition = E2CalibrationDisposition.EXCLUDED,
                acceptedReviewDigest = null,
                updatedAt = Instant.now(clock),
            )
        )
        return persist(metadata)
    }

    suspend fun reinclude(resultId: UUID): PkCalibrationReviewActionResult {
        val current = currentContext() ?: return rejected(
            PkCalibrationReviewActionRejection.CURRENT_INPUT_UNAVAILABLE
        )
        val scope = validateCurrentScope(current.input) ?: return rejected(
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
                acceptedReviewDigest = null,
                updatedAt = Instant.now(clock),
            )
        )
        return persist(metadata)
    }

    private suspend fun currentContext(): CurrentContext? {
        val input = runCatching { currentInputProvider.currentInput() }.getOrNull()
            ?: return null
        val metadata = storageRepository.getAllMetadata()
        return CurrentContext(input, metadata)
    }

    private fun validateCurrentScope(
        input: PkCalibrationInputSnapshot,
    ): PkCalibrationValidatedScopeInput? {
        return when (
            val validation = PkCalibrationScopeInputValidator.validate(
                labs = input.labs,
                medicationEvents = input.medicationEvents,
                forwardTimeOriginEpochMillis = input.forwardTimeOriginEpochMillis,
                resolvedCurrentWeightKg = input.resolvedCurrentWeightKg,
                identityPolicy = identityPolicy,
                config = config,
                scopeDecisionProvider = scopeDecisionProvider,
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
        } catch (_: IllegalArgumentException) {
            rejected(PkCalibrationReviewActionRejection.LAB_NOT_AUTHORIZED_E2)
        }
    }

    private data class CurrentContext(
        val input: PkCalibrationInputSnapshot,
        val metadata: List<E2CalibrationMetadata>,
    )

    private fun rejected(
        reason: PkCalibrationReviewActionRejection,
    ): PkCalibrationReviewActionResult.Rejected {
        return PkCalibrationReviewActionResult.Rejected(reason)
    }
}
