package com.mkx.hrttracker.di

import com.mkx.hrttracker.ui.pkcalibrationdebug.DefaultPkCalibrationDebugScenarioSource
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugScenarioSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PkCalibrationDebugModule {
    @Provides
    @Singleton
    fun providePkCalibrationDebugScenarioSource(): PkCalibrationDebugScenarioSource =
        DefaultPkCalibrationDebugScenarioSource()
}
