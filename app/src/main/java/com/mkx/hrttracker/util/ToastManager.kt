package com.mkx.hrttracker.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object ToastManager {
    // Hold the application context
    private lateinit var appContext: Context
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // Initialize with the application context. This should be called from your Application class.
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // Show a toast using the application context
    fun showMessage(message: String) {
        if (::appContext.isInitialized) {
            val showToast = {
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                showToast()
            } else {
                mainHandler.post(showToast)
            }
        } else {
            // Optionally handle uninitialized context, e.g., log a warning
            throw IllegalStateException("ToastManager is not initialized. Call ToastManager.init(context) first.")
        }
    }
}
