package com.mkx.hrttracker.util

import android.util.Log
import com.mkx.hrttracker.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AppDiagnosticsLogger @Inject constructor(
    private val logStore: AppDiagnosticsLogStore,
) {
    private var enabled: Boolean = BuildConfig.DEBUG

    constructor() : this(AppDiagnosticsLogStore.disabled())

    internal constructor(
        enabled: Boolean,
        logStore: AppDiagnosticsLogStore = AppDiagnosticsLogStore.disabled(),
    ) : this(logStore) {
        this.enabled = enabled
    }

    open fun info(tag: String, message: String) {
        if (!enabled) {
            return
        }
        logStore.record(AppDiagnosticsLogLevel.INFO, tag, message, null)
        runCatching {
            Log.i(tag, message)
        }
    }

    open fun warning(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (!enabled) {
            return
        }
        logStore.record(AppDiagnosticsLogLevel.WARNING, tag, message, throwable)
        runCatching {
            if (throwable == null) {
                Log.w(tag, message)
            } else {
                Log.w(tag, message, throwable)
            }
        }
    }
}
