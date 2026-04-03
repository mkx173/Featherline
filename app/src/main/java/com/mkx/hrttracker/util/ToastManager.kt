package com.mkx.hrttracker.util

import android.content.Context
import android.widget.Toast

object ToastManager {
    // Hold the application context
    private lateinit var appContext: Context

    // Initialize with the application context. This should be called from your Application class.
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // Show a toast using the application context
    fun showMessage(message: String) {
        if (::appContext.isInitialized) {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        } else {
            // Optionally handle uninitialized context, e.g., log a warning
            throw IllegalStateException("ToastManager is not initialized. Call ToastManager.init(context) first.")
        }
    }
}
