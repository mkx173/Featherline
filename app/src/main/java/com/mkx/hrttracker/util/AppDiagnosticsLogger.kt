package com.mkx.hrttracker.util

import android.util.Log
import com.mkx.hrttracker.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AppDiagnosticsLogger @Inject constructor() {
    private var enabled: Boolean = BuildConfig.DEBUG

    internal constructor(enabled: Boolean) : this() {
        this.enabled = enabled
    }

    open fun info(tag: String, message: String) {
        if (!enabled) {
            return
        }
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
        runCatching {
            if (throwable == null) {
                Log.w(tag, message)
            } else {
                Log.w(tag, message, throwable)
            }
        }
    }
}
