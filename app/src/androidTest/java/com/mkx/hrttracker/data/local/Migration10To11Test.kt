package com.mkx.hrttracker.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration10To11Test {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HrtTrackerDatabase::class.java,
        emptyList(),
        cipherFactory(),
    )

    @get:Rule
    val testName: TestName = TestName()

    private val testDb get() = "migration-test-db-${testName.methodName}"

    @Before
    fun loadSqlCipher() {
        System.loadLibrary("sqlcipher")
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(testDb)
    }

    @Test
    fun migrate10To11_weightedRowGetsNullMeasuredAt() {
        migrateWithProfile(weightKg = "62.4", originalValue = "62.4", ::assertMeasuredAtNull)
    }

    @Test
    fun migrate10To11_clearedRowGetsNullMeasuredAt() {
        migrateWithProfile(weightKg = "NULL", originalValue = "NULL", ::assertMeasuredAtNull)
    }

    // Every pre-v11 weight was typed, so none has a Health Connect measured time.
    private fun assertMeasuredAtNull(db: SupportSQLiteDatabase) {
        db.query("SELECT weightMeasuredAtEpochMillis, updatedAtEpochMillis FROM user_profile")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals(UPDATED_AT, cursor.getLong(1))
            }
    }

    private fun migrateWithProfile(
        weightKg: String,
        originalValue: String,
        assertMigrated: (SupportSQLiteDatabase) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)

        migrationHelper.createDatabase(testDb, 10).use { version10 ->
            version10.execSQL(
                """
                INSERT INTO user_profile (
                    id, weightKg, weightOriginalValue, weightOriginalUnit, updatedAtEpochMillis
                ) VALUES ('default', $weightKg, $originalValue, 'KILOGRAMS', $UPDATED_AT)
                """.trimIndent()
            )
        }

        migrationHelper.runMigrationsAndValidate(testDb, 11, true, MIGRATION_10_11)
            .use(assertMigrated)

        // Reopen through Room too, so its runtime identity-hash check runs with
        // the SQLCipher factory used by production.
        val roomDatabase = Room.databaseBuilder(context, HrtTrackerDatabase::class.java, testDb)
            .openHelperFactory(cipherFactory())
            .addMigrations(MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()
        migrationHelper.closeWhenFinished(roomDatabase)
        assertMigrated(roomDatabase.openHelper.writableDatabase)
    }

    private companion object {
        const val UPDATED_AT = 1_700_000_000_000L

        fun cipherFactory(): SupportOpenHelperFactory {
            return SupportOpenHelperFactory(
                "migration-test-passphrase".toByteArray(Charsets.UTF_8)
            )
        }
    }
}
