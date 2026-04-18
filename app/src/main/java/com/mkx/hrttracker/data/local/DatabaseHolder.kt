package com.mkx.hrttracker.data.local

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseHolder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val databasePassphraseProvider: DatabasePassphraseProvider,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _databaseFlow = MutableStateFlow<HrtTrackerDatabase?>(null)
    val databaseFlow: StateFlow<HrtTrackerDatabase?> = _databaseFlow.asStateFlow()

    private var generation = 0L

    fun get(): HrtTrackerDatabase {
        _databaseFlow.value?.let { return it }

        return synchronized(this) {
            _databaseFlow.value ?: buildDatabase().also { _databaseFlow.value = it }
        }
    }

    fun close() {
        val databaseToClose = synchronized(this) {
            generation++
            val currentDatabase = _databaseFlow.value ?: return@synchronized null
            _databaseFlow.value = null
            currentDatabase
        }

        databaseToClose?.close()
    }

    fun warmUp() {
        val generationAtLaunch = synchronized(this) { generation }
        scope.launch {
            runCatching {
                val databaseToWarm = synchronized(this@DatabaseHolder) {
                    if (generation != generationAtLaunch) {
                        return@runCatching
                    }

                    _databaseFlow.value ?: buildDatabase().also { _databaseFlow.value = it }
                }

                databaseToWarm.openHelper.writableDatabase

                val staleDatabase = synchronized(this@DatabaseHolder) {
                    if (generation == generationAtLaunch || _databaseFlow.value !== databaseToWarm) {
                        null
                    } else {
                        _databaseFlow.value = null
                        databaseToWarm
                    }
                }

                staleDatabase?.close()
            }
        }
    }

    private fun buildDatabase(): HrtTrackerDatabase {
        val openHelperFactory =
            SupportOpenHelperFactory(databasePassphraseProvider.getPassphrase())

        return Room.databaseBuilder(
            context,
            HrtTrackerDatabase::class.java,
            DATABASE_NAME
        )
            .openHelperFactory(openHelperFactory)
            .build()
    }

    private companion object {
        private const val DATABASE_NAME = "hrt_tracker.db"
    }
}
