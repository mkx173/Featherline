package com.mkx.hrttracker.di

import com.mkx.hrttracker.data.repository.PkCalibrationCurrentEvaluationContextProvider
import com.mkx.hrttracker.data.repository.PkCalibrationLiveRepository
import com.mkx.hrttracker.data.repository.PkCalibrationRuntimePolicyProvider
import com.mkx.hrttracker.data.repository.ProductionUnavailablePkCalibrationRuntimePolicyProvider
import com.mkx.hrttracker.model.pk.PkCalibrationModelIdentityProvider
import com.mkx.hrttracker.ui.pkcalibrationdebug.DefaultPkCalibrationDebugScenarioSource
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugScenarioSource
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugGate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PkCalibrationModule {
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

    @Provides
    @Singleton
    fun providePkCalibrationCurrentEvaluationContextProvider(
        repository: PkCalibrationLiveRepository,
    ): PkCalibrationCurrentEvaluationContextProvider = repository

    @Provides
    @Singleton
    fun providePkCalibrationDebugScenarioSource(): PkCalibrationDebugScenarioSource =
        DefaultPkCalibrationDebugScenarioSource()

    @Provides
    @Singleton
    fun providePkCalibrationDebugGate(): PkCalibrationDebugGate = PkCalibrationDebugGate.Build
}
