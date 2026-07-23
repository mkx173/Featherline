package com.mkx.hrttracker.di

import com.mkx.hrttracker.data.repository.PkCalibrationCurrentEvaluationContextProvider
import com.mkx.hrttracker.data.repository.PkCalibrationLiveRepository
import com.mkx.hrttracker.data.repository.PkCalibrationRuntimePolicyProvider
import com.mkx.hrttracker.data.repository.ProductionUnavailablePkCalibrationRuntimePolicyProvider
import com.mkx.hrttracker.model.pk.PkCalibrationModelIdentityProvider
import com.mkx.hrttracker.ui.pkcalibrationdebug.DefaultPkCalibrationDebugScenarioSource
import com.mkx.hrttracker.ui.pkcalibrationdebug.GuardedPkCalibrationDebugReviewActionHandler
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugLiveSnapshotProvider
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugReviewActionHandler
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugScenarioSource
import com.mkx.hrttracker.ui.pkcalibrationdebug.RepositoryPkCalibrationDebugLiveSnapshotProvider
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugGate
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugViewModelConfig
import com.mkx.hrttracker.data.repository.PkCalibrationReviewActionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

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
    fun providePkCalibrationDebugLiveSnapshotProvider(
        provider: RepositoryPkCalibrationDebugLiveSnapshotProvider,
    ): PkCalibrationDebugLiveSnapshotProvider = provider

    @Provides
    @Singleton
    fun providePkCalibrationDebugScenarioSource(
        provider: PkCalibrationDebugLiveSnapshotProvider,
    ): PkCalibrationDebugScenarioSource = DefaultPkCalibrationDebugScenarioSource(provider)

    @Provides
    @Singleton
    fun providePkCalibrationDebugReviewActionHandler(
        service: PkCalibrationReviewActionService,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ): PkCalibrationDebugReviewActionHandler = GuardedPkCalibrationDebugReviewActionHandler(
        service = service,
        defaultDispatcher = defaultDispatcher,
    )

    @Provides
    fun providePkCalibrationDebugViewModelConfig(): PkCalibrationDebugViewModelConfig =
        PkCalibrationDebugViewModelConfig(
            debugGate = PkCalibrationDebugGate.Build,
            autoLoadLive = true,
        )
}
