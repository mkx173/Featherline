package com.mkx.hrttracker.data.local

import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseWarmUp @Inject constructor(
    private val database: Lazy<HrtTrackerDatabase>,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun warmUp() {
        scope.launch {
            runCatching { database.get().openHelper.writableDatabase }
        }
    }
}
