package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResearchDebugPkCalibrationRuntimePolicyProviderTest {

    private val attestationState = MutableStateFlow<PkCalibrationAttestationState?>(null)
    private val attestationRepository: PkCalibrationAttestationRepository = mockk {
        every { state } returns attestationState
    }

    @Test
    fun policyTracksTheAttestationTriState() = runTest(UnconfinedTestDispatcher()) {
        val provider = ResearchDebugPkCalibrationRuntimePolicyProvider(
            attestationRepository = attestationRepository,
            appScope = backgroundScope,
        )

        // Store not loaded yet: unavailable, never a flashing evaluation.
        assertTrue(provider.policy.value is PkCalibrationRuntimePolicy.Unavailable)

        // UNSEEN and DECLINED both evaluate live with a null attestation, so
        // the engine lands on SCOPE_NOT_CONFIRMED and the surface keeps its
        // attestation CTA instead of disappearing.
        attestationState.value = PkCalibrationAttestationState.Unseen
        val unseen = provider.policy.value as PkCalibrationRuntimePolicy.ResearchOrTest
        assertNull(unseen.attestation)

        attestationState.value = PkCalibrationAttestationState.Declined
        val declined = provider.policy.value as PkCalibrationRuntimePolicy.ResearchOrTest
        assertNull(declined.attestation)

        attestationState.value =
            PkCalibrationAttestationState.Attested(1_700_000_000_000L)
        val attested = provider.policy.value as PkCalibrationRuntimePolicy.ResearchOrTest
        assertEquals(
            1_700_000_000_000L,
            checkNotNull(attested.attestation).attestedAtEpochMillis,
        )

        // Withdrawal takes effect on the next evaluation: attestation gone,
        // policy still live (population everywhere via SCOPE_NOT_CONFIRMED).
        attestationState.value = PkCalibrationAttestationState.Declined
        val withdrawn = provider.policy.value as PkCalibrationRuntimePolicy.ResearchOrTest
        assertNull(withdrawn.attestation)
    }

    @Test
    fun identityPolicy_unitMapCoversExactlyTheCatalogE2Units() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val provider = ResearchDebugPkCalibrationRuntimePolicyProvider(
            attestationRepository = attestationRepository,
            appScope = backgroundScope,
        )
        attestationState.value = PkCalibrationAttestationState.Unseen
        val policy = provider.policy.value as PkCalibrationRuntimePolicy.ResearchOrTest

        // Drift tripwire: every unit the catalog can persist for an E2 result
        // must resolve to a stable unit id, or a valid lab would fail closed
        // as SOURCE_DATA_INVALID on the live path.
        val catalogE2Units = BloodUnitKey.entries
            .filter { unit -> BloodTestCatalog.isUnitAllowed(BloodAnalyteKey.E2, unit) }
            .map(BloodUnitKey::storageValue)
            .toSet()
        assertEquals(
            catalogE2Units,
            policy.identityPolicy.unitIdBySourceSnapshot.keys,
        )
    }
}
