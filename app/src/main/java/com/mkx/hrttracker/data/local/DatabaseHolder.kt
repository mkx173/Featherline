package com.mkx.hrttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.mkx.hrttracker.util.AppDiagnosticsLogger
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
    private val diagnosticsLogger: AppDiagnosticsLogger,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _databaseFlow = MutableStateFlow<HrtTrackerDatabase?>(null)
    val databaseFlow: StateFlow<HrtTrackerDatabase?> = _databaseFlow.asStateFlow()

    // Terminal open-failure signal. warmUp swallows a build/open exception (so it can't crash
    // startup), which would otherwise leave databaseFlow null forever and spin the Milestones
    // screen on a fresh install with no snapshot. Flips true so the journal's seeded flow can
    // fall through to an empty journal exactly when the open genuinely can't succeed.
    private val _openFailed = MutableStateFlow(false)
    val openFailed: StateFlow<Boolean> = _openFailed.asStateFlow()

    fun get(): HrtTrackerDatabase {
        _databaseFlow.value?.let { return it }

        return synchronized(this) {
            _databaseFlow.value ?: buildDatabase().also { _databaseFlow.value = it }
        }
    }

    fun warmUp() {
        scope.launch {
            runCatching {
                openAndSignal()
            }.onFailure { throwable ->
                // Swallowed by contract (warm-up must never crash startup), but recorded so
                // the failure is diagnosable; openAndSignal already flipped openFailed.
                diagnosticsLogger.warning(TAG, "database_warmup_failed", throwable)
            }
        }
    }

    // Blocking build + open shared by EVERY warm-up path (the async warmUp above, the
    // in-app StartupPreloader). Centralized so openFailed flips no matter which path hit
    // the failure — a flag set only by warmUp would leave the journal's seeded flow
    // spinning forever when the in-app cold start was the one that failed. Success clears
    // the flag so a later retry (e.g. passphrase newly available) recovers. Throws the
    // underlying failure for the caller to log/handle.
    fun openAndSignal(): HrtTrackerDatabase {
        try {
            val database = synchronized(this) {
                _databaseFlow.value ?: buildDatabase().also { _databaseFlow.value = it }
            }
            database.openHelper.writableDatabase
            _openFailed.value = false
            return database
        } catch (error: Exception) {
            _openFailed.value = true
            throw error
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
        ensureSqlCipherLoaded()
        val openHelperFactory =
            SupportOpenHelperFactory(databasePassphraseProvider.getPassphrase())

        val builder: RoomDatabase.Builder<HrtTrackerDatabase> = Room.databaseBuilder(
            context,
            HrtTrackerDatabase::class.java,
            DATABASE_NAME
        )
            .openHelperFactory(openHelperFactory)
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
            )
        return builder.build()
    }

    private companion object {
        private const val TAG = "DatabaseHolder"
        private const val DATABASE_NAME = "hrt_tracker.db"
        private val SQL_CIPHER_LOAD_LOCK = Any()

        @Volatile
        private var sqlCipherLoaded = false

        fun ensureSqlCipherLoaded() {
            if (sqlCipherLoaded) {
                return
            }
            synchronized(SQL_CIPHER_LOAD_LOCK) {
                if (!sqlCipherLoaded) {
                    System.loadLibrary("sqlcipher")
                    sqlCipherLoaded = true
                }
            }
        }
    }
}
