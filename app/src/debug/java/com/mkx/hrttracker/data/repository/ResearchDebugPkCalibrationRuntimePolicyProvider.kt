package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.PkCalibrationAttestation
import com.mkx.hrttracker.model.pk.PkCalibrationConfig
import com.mkx.hrttracker.model.pk.PkCalibrationIdentityPolicy
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkCompound
import com.mkx.hrttracker.model.pk.PkRoute
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Phase-3.3 research/debug runtime policy: the actual switch that lets a debug
 * build evaluate calibration from real data. The policy tracks the durable
 * §A2 attestation — not attested means a live SCOPE_NOT_CONFIRMED surface
 * with the attestation CTA, and withdrawal reverts every route to population
 * on the next evaluation.
 *
 * Referenced only behind the compile-time BuildConfig.DEBUG selection in
 * PkCalibrationModule, so R8 strips this class and the live path it unlocks
 * from release (and benchmark) builds — the D2 dex scan verifies.
 */
@Singleton
class ResearchDebugPkCalibrationRuntimePolicyProvider @Inject constructor(
    attestationRepository: PkCalibrationAttestationRepository,
    @AppScope appScope: CoroutineScope,
) : PkCalibrationRuntimePolicyProvider {

    override val policy: StateFlow<PkCalibrationRuntimePolicy> =
        attestationRepository.state
            .map(::policyFor)
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = PkCalibrationRuntimePolicy.Unavailable(),
            )

    private fun policyFor(
        state: PkCalibrationAttestationState?,
    ): PkCalibrationRuntimePolicy {
        // Store not loaded yet: stay unavailable rather than flashing a
        // not-attested evaluation that immediately gets replaced.
        if (state == null) return PkCalibrationRuntimePolicy.Unavailable()
        val attestation = (state as? PkCalibrationAttestationState.Attested)
            ?.let { attested -> PkCalibrationAttestation(attested.attestedAtEpochMillis) }
        return PkCalibrationRuntimePolicy.ResearchOrTest.create(
            identityPolicy = IdentityPolicy,
            config = Config,
            attestation = attestation,
            forwardModelVersion = FORWARD_MODEL_VERSION,
            calibrationModelVersion = CALIBRATION_MODEL_VERSION,
        ) ?: PkCalibrationRuntimePolicy.Unavailable()
    }

    companion object {
        const val FORWARD_MODEL_VERSION = "hrttracker:pk-forward/v1"
        const val CALIBRATION_MODEL_VERSION = "hrttracker:route-calibration/v10"

        /**
         * Research constants (user decision, 2026-08-09; recorded in the
         * phase-3 plan). Both are debug/research-only and revisitable from
         * Phase-3.4 QA telemetry before any launch sign-off:
         * - R_LOG = 0.0225 is the §A3 anchor (reproduces the v9.0 RMSE
         *   promotion gate; ~15% log-SD from published assay CV plus the
         *   collection-timing budget).
         * - D_min = 5 pg/mL of population drug-attributable E2 for a lab to
         *   count as informative.
         */
        private val Config = requireNotNull(
            PkCalibrationConfig.researchOrTest(
                drugMinInformativePgml = 5.0,
                rLog = 0.0225,
            )
        )

        private val IdentityPolicy = requireNotNull(
            PkCalibrationIdentityPolicy.researchOrTest(
                builtinE2AnalyteId = "hrttracker:analyte/e2/v1",
                targetHormoneId = "hrttracker:hormone/estradiol/v1",
                // Keys are the persisted unit snapshots; exactly the units the
                // blood-test catalog allows for E2.
                unitIdBySourceSnapshot = mapOf(
                    BloodUnitKey.PG_ML.storageValue to "hrttracker:unit/pg-ml/v1",
                    BloodUnitKey.PMOL_L.storageValue to "hrttracker:unit/pmol-l/v1",
                    BloodUnitKey.NG_DL.storageValue to "hrttracker:unit/ng-dl/v1",
                ),
                eventTypeIdByRoute = mapOf(
                    PkRoute.INJECTION to "hrttracker:event/injection-dose/v1",
                    PkRoute.PATCH_APPLY to "hrttracker:event/patch-apply/v1",
                    PkRoute.PATCH_REMOVE to "hrttracker:event/patch-remove/v1",
                    PkRoute.GEL to "hrttracker:event/gel-dose/v1",
                    PkRoute.ORAL to "hrttracker:event/oral-dose/v1",
                    PkRoute.SUBLINGUAL to "hrttracker:event/sublingual-dose/v1",
                ),
                // Must equal the canonical event-route -> calibration-route
                // stable-id mapping; the factory rejects anything else.
                routeIdByRoute = mapOf(
                    PkRoute.INJECTION to PkCalibrationRoute.INJECTION.stableId,
                    PkRoute.PATCH_APPLY to PkCalibrationRoute.PATCH.stableId,
                    PkRoute.PATCH_REMOVE to PkCalibrationRoute.PATCH.stableId,
                    PkRoute.GEL to PkCalibrationRoute.GEL.stableId,
                    PkRoute.ORAL to PkCalibrationRoute.ORAL.stableId,
                    PkRoute.SUBLINGUAL to PkCalibrationRoute.SUBLINGUAL.stableId,
                ),
                compoundIdByCompound = mapOf(
                    PkCompound.E2 to "hrttracker:compound/e2/v1",
                    PkCompound.EB to "hrttracker:compound/eb/v1",
                    PkCompound.EV to "hrttracker:compound/ev/v1",
                    PkCompound.EC to "hrttracker:compound/ec/v1",
                    PkCompound.EN to "hrttracker:compound/en/v1",
                    PkCompound.EU to "hrttracker:compound/eu/v1",
                ),
            )
        )
    }
}
