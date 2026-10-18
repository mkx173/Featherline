package com.mkx.hrttracker.di

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** The process lifecycle's state: the app is in the foreground while it is at least STARTED. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProcessLifecycleState

@Module
@InstallIn(SingletonComponent::class)
object AppCoroutineModule {
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher {
        return Dispatchers.Default
    }

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    // Updated on the main thread as the lifecycle event is dispatched, so a
    // Main.immediate collector runs before the first frame after a return.
    @Provides
    @ProcessLifecycleState
    fun provideProcessLifecycleState(): StateFlow<Lifecycle.State> {
        return ProcessLifecycleOwner.get().lifecycle.currentStateFlow
    }
}
