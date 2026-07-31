package com.mkx.hrttracker.ui.settings

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.cloudsync.CloudConflictResolution
import com.mkx.hrttracker.cloudsync.CloudDriveAuthorization
import com.mkx.hrttracker.cloudsync.CloudSyncCoordinator
import com.mkx.hrttracker.cloudsync.CloudSyncInterval
import com.mkx.hrttracker.cloudsync.CloudSyncPreferences
import com.mkx.hrttracker.cloudsync.CloudSyncResult
import com.mkx.hrttracker.cloudsync.CloudSyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    private val coordinator: CloudSyncCoordinator,
    private val preferences: CloudSyncPreferences,
) : ViewModel() {
    private val inProgress = MutableStateFlow(false)
    private val events = MutableSharedFlow<CloudSyncUiEvent>(extraBufferCapacity = 4)

    val uiState = combine(
        preferences.state,
        inProgress,
    ) { storedState, syncing ->
        CloudSyncUiState(
            available = coordinator.isAvailable,
            enabled = storedState.enabled,
            interval = storedState.interval,
            status = storedState.status,
            lastSyncAt = storedState.lastSyncAt,
            lastError = storedState.lastError,
            inProgress = syncing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CloudSyncUiState(available = coordinator.isAvailable),
    )

    val uiEvents = events.asSharedFlow()

    fun enable(password: String) = launchOperation {
        emitResult(coordinator.enable(password))
    }

    fun disable() = launchOperation {
        coordinator.disconnect()
        events.emit(CloudSyncUiEvent.Disconnected)
    }

    fun setInterval(interval: CloudSyncInterval) {
        viewModelScope.launch { coordinator.setInterval(interval) }
    }

    fun syncNow() = launchOperation {
        emitResult(coordinator.syncNow())
    }

    fun resolveConflict(resolution: CloudConflictResolution) = launchOperation {
        emitResult(coordinator.resolveConflict(resolution))
    }

    fun completeAuthorization(data: Intent?) = launchOperation {
        when (val authorization = coordinator.completeAuthorization(data)) {
            is CloudDriveAuthorization.Authorized -> emitResult(coordinator.syncNow())
            is CloudDriveAuthorization.RequiresUserAction -> {
                events.emit(CloudSyncUiEvent.LaunchAuthorization(authorization.pendingIntent))
            }
            CloudDriveAuthorization.Unavailable -> events.emit(CloudSyncUiEvent.Unavailable)
            is CloudDriveAuthorization.Failed -> events.emit(
                CloudSyncUiEvent.Failure(authorization.error)
            )
        }
    }

    private fun launchOperation(block: suspend () -> Unit) {
        if (inProgress.value) return
        inProgress.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                events.emit(CloudSyncUiEvent.Failure(error))
            } finally {
                inProgress.value = false
            }
        }
    }

    private suspend fun emitResult(result: CloudSyncResult) {
        when (result) {
            CloudSyncResult.Disabled -> Unit
            CloudSyncResult.Unavailable -> events.emit(CloudSyncUiEvent.Unavailable)
            CloudSyncResult.UpToDate -> events.emit(CloudSyncUiEvent.UpToDate)
            CloudSyncResult.Uploaded -> events.emit(CloudSyncUiEvent.Uploaded)
            CloudSyncResult.Downloaded -> events.emit(CloudSyncUiEvent.Downloaded)
            CloudSyncResult.Conflict -> events.emit(CloudSyncUiEvent.Conflict)
            is CloudSyncResult.NeedsAuthorization -> {
                events.emit(CloudSyncUiEvent.LaunchAuthorization(result.pendingIntent))
            }
            is CloudSyncResult.Failed -> events.emit(CloudSyncUiEvent.Failure(result.error))
        }
    }
}

data class CloudSyncUiState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val interval: CloudSyncInterval = CloudSyncInterval.DAILY,
    val status: CloudSyncStatus = CloudSyncStatus.DISCONNECTED,
    val lastSyncAt: Instant? = null,
    val lastError: String? = null,
    val inProgress: Boolean = false,
)

sealed interface CloudSyncUiEvent {
    data class LaunchAuthorization(val pendingIntent: PendingIntent) : CloudSyncUiEvent
    data object Uploaded : CloudSyncUiEvent
    data object Downloaded : CloudSyncUiEvent
    data object UpToDate : CloudSyncUiEvent
    data object Conflict : CloudSyncUiEvent
    data object Disconnected : CloudSyncUiEvent
    data object Unavailable : CloudSyncUiEvent
    data class Failure(val error: Throwable) : CloudSyncUiEvent
}
