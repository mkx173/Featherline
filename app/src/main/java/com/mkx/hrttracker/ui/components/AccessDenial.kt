package com.mkx.hrttracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A switch's denied access: the reminders switch's notifications, or the weight sheet's Health
 * Connect access. Set by a denial, it shows the switch's message to Android's or Health Connect's
 * settings, every time the screen comes back, until access returns or the app restarts: it lives
 * in memory, in [AccessDenials].
 */
class AccessDenial {
    var isDenied by mutableStateOf(false)

    /** The message opened the settings screen; [RETURNED] once the screen resumes after it. */
    private var trip = Trip.NONE

    /** The message opened the settings screen that grants access. */
    fun onOpenSettings() {
        trip = Trip.OPENED
    }

    internal fun onResume() {
        if (trip == Trip.OPENED) trip = Trip.RETURNED
    }

    internal fun onPause() {
        if (trip == Trip.RETURNED) trip = Trip.NONE
    }

    /** Access is back: the denial ends. True only on the return from the message's settings trip. */
    internal fun onAccessBack(): Boolean {
        if (!isDenied) return false
        isDenied = false
        val fromTrip = trip != Trip.NONE
        trip = Trip.NONE
        return fromTrip
    }

    private enum class Trip { NONE, OPENED, RETURNED }
}

/** The app's switch denials, for the process's life: a restart clears them. */
@Singleton
class AccessDenials @Inject constructor() {
    val notifications = AccessDenial()
    val healthConnect = AccessDenial()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccessDenialsEntryPoint {
    fun accessDenials(): AccessDenials
}

@Composable
fun rememberAccessDenials(): AccessDenials {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        EntryPointAccessors.fromApplication(applicationContext, AccessDenialsEntryPoint::class.java)
            .accessDenials()
    }
}

/**
 * Follows [denial] on this screen: access back on the return from the message's settings screen
 * runs [onAccessAllowed] (the switch turns on), as the user asked for it. Access back any other
 * way, a grant made some other day, ends the denial and turns nothing on: the switch can be
 * tapped again.
 */
@Composable
fun FollowAccessDenial(
    denial: AccessDenial,
    hasAccess: Boolean,
    onAccessAllowed: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, denial) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> denial.onResume()
                Lifecycle.Event.ON_PAUSE -> denial.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val latestOnAccessAllowed by rememberUpdatedState(onAccessAllowed)
    LaunchedEffect(denial, hasAccess) {
        if (hasAccess && denial.onAccessBack()) latestOnAccessAllowed()
    }
}
