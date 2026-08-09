package com.mkx.hrttracker.di

import com.mkx.hrttracker.data.repository.PkCalibrationRuntimePolicyProvider
import com.mkx.hrttracker.data.repository.ProductionUnavailablePkCalibrationRuntimePolicyProvider
import com.mkx.hrttracker.model.pk.PkCalibrationModelIdentityProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Release build type: both engine fail-closed gates stay shut — the runtime
 * policy is permanently unavailable and no model identity is approved, so the
 * population UI is all that can ever render (Phase-2 plan D2; unchanged by
 * Phase 3.3, which opens these gates in the debug source set only).
 */
@Module
@InstallIn(SingletonComponent::class)
object PkCalibrationRuntimePolicyBuildModule {
    @Provides
    @Singleton
    fun providePkCalibrationModelIdentityProvider(): PkCalibrationModelIdentityProvider {
        return PkCalibrationModelIdentityProvider.Unavailable
    }

    @Provides
    @Singleton
    fun providePkCalibrationRuntimePolicyProvider(
        provider: ProductionUnavailablePkCalibrationRuntimePolicyProvider,
    ): PkCalibrationRuntimePolicyProvider = provider
}
