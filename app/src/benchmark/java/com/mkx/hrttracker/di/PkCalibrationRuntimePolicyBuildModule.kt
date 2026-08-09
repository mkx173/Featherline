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
 * Benchmark build type (release-equivalent, D2): identical to src/release —
 * both engine fail-closed gates stay shut so macrobenchmarks measure the
 * exact population UI that ships.
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
