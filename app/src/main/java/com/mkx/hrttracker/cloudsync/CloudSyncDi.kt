package com.mkx.hrttracker.cloudsync

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudSyncWorkerEntryPoint {
    fun cloudSyncCoordinator(): CloudSyncCoordinator
}

@Module
@InstallIn(SingletonComponent::class)
object CloudSyncModule {
    @Provides
    @Singleton
    fun provideCloudDriveGateway(
        @ApplicationContext context: Context,
    ): CloudDriveGateway = CloudDriveGatewayFactory.create(context)
}
