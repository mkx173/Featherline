package com.mkx.hrttracker.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes

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
        val context = initializedContext()
        val showToast = {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showToast()
        } else {
            mainHandler.post(showToast)
        }
    }

    // Show a string resource, in the app's chosen language.
    fun showMessageRes(@StringRes messageRes: Int) {
        showMessage(initializedContext().withAppLanguage().getString(messageRes))
    }

    private fun initializedContext(): Context {
        check(::appContext.isInitialized) {
            "ToastManager is not initialized. Call ToastManager.init(context) first."
        }
        return appContext
    }
}
