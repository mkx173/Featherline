package com.mkx.hrttracker.di

import com.mkx.hrttracker.data.repository.PkCalibrationRuntimePolicy
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
    fun providePkCalibrationRuntimePolicy(): PkCalibrationRuntimePolicy =
        PkCalibrationRuntimePolicy.Default
}
