package com.mkx.hrttracker.data.local

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    @Volatile
    private var database: HrtTrackerDatabase? = null
    private var generation = 0L

    fun get(): HrtTrackerDatabase {
        database?.let { return it }

        return synchronized(this) {
            database ?: buildDatabase().also { database = it }
        }
    }

    fun close() {
        val databaseToClose = synchronized(this) {
            generation++
            val currentDatabase = database ?: return@synchronized null
            database = null
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

                    database ?: buildDatabase().also { database = it }
                }

                databaseToWarm.openHelper.writableDatabase

                val staleDatabase = synchronized(this@DatabaseHolder) {
                    if (generation == generationAtLaunch || database !== databaseToWarm) {
                        null
                    } else {
                        database = null
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
