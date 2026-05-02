package com.mkx.hrttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
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

    fun get(): HrtTrackerDatabase {
        _databaseFlow.value?.let { return it }

        return synchronized(this) {
            _databaseFlow.value ?: buildDatabase().also { _databaseFlow.value = it }
        }
    }

    fun warmUp() {
        scope.launch {
            runCatching {
                val database = synchronized(this@DatabaseHolder) {
                    _databaseFlow.value ?: buildDatabase().also { _databaseFlow.value = it }
                }
                database.openHelper.writableDatabase
            }
        }
    }

    suspend fun <T> withTransaction(
        block: suspend (HrtTrackerDatabase) -> T,
    ): T {
        val database = get()
        return database.withTransaction {
            block(database)
        }
    }

    suspend fun runTransaction(
        block: suspend (HrtTrackerDatabase) -> Unit,
    ) {
        withTransaction(block)
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
            .addMigrations(
                MIGRATION_19_20,
                MIGRATION_20_21,
                MIGRATION_21_22,
                MIGRATION_22_23,
                MIGRATION_23_24,
                MIGRATION_24_25,
                MIGRATION_25_26,
                MIGRATION_26_27,
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    private companion object {
        private const val DATABASE_NAME = "hrt_tracker.db"
    }
}
