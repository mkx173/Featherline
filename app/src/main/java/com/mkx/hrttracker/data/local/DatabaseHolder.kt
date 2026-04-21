package com.mkx.hrttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    private fun buildDatabase(): HrtTrackerDatabase {
        val openHelperFactory =
            SupportOpenHelperFactory(databasePassphraseProvider.getPassphrase())

        return Room.databaseBuilder(
            context,
            HrtTrackerDatabase::class.java,
            DATABASE_NAME
        )
            .openHelperFactory(openHelperFactory)
            .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
            .build()
    }

    private companion object {
        private const val DATABASE_NAME = "hrt_tracker.db"

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE medication_groups
                    ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 0
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_profile (
                        id TEXT NOT NULL PRIMARY KEY,
                        weightKg REAL,
                        weightOriginalValue REAL,
                        weightOriginalUnit TEXT,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
