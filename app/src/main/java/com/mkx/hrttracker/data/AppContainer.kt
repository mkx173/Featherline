package com.mkx.hrttracker.data

import android.content.Context
import com.mkx.hrttracker.data.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import kotlin.getValue

interface AppContainer {
    val settingsRepository: SettingsRepository
}

abstract class BaseAppContainer(private val context: Context) : AppContainer {
    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context)
    }
}

class DefaultAppContainer(context: Context) : BaseAppContainer(context) {
}