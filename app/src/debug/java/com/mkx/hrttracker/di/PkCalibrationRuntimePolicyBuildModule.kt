package com.mkx.hrttracker.di

import com.mkx.hrttracker.data.repository.PkCalibrationRuntimePolicyProvider
import com.mkx.hrttracker.data.repository.ResearchDebugPkCalibrationRuntimePolicyProvider
import com.mkx.hrttracker.model.pk.PkCalibrationModelIdentityProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Debug build type (Phase 3.3): the two remaining fail-closed gates open —
 * the live runtime policy tracks the durable attestation, and the model
 * identity is fixed to the research calibration version. The provider class
 * itself exists only in this source set, so no other build can reach the live
 * path.
 */
@Module
@InstallIn(SingletonComponent::class)
object PkCalibrationRuntimePolicyBuildModule {
    @Provides
    @Singleton
    fun providePkCalibrationModelIdentityProvider(): PkCalibrationModelIdentityProvider {
        return PkCalibrationModelIdentityProvider.fixed(
            ResearchDebugPkCalibrationRuntimePolicyProvider.CALIBRATION_MODEL_VERSION
        )
    }

    @Provides
    @Singleton
    fun providePkCalibrationRuntimePolicyProvider(
        provider: ResearchDebugPkCalibrationRuntimePolicyProvider,
    ): PkCalibrationRuntimePolicyProvider = provider
}
