package com.mkx.hrttracker.util

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AppDiagnosticsLogger @Inject constructor() {
    open fun info(tag: String, message: String) {
        runCatching {
            Log.i(tag, message)
        }
    }

    open fun warning(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        runCatching {
            if (throwable == null) {
                Log.w(tag, message)
            } else {
                Log.w(tag, message, throwable)
            }
        }
    }
}
