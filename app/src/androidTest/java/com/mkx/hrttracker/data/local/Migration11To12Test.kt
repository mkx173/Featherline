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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration11To12Test {
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
    fun migrate11To12_existingPlanKeepsMidnight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)

        migrationHelper.createDatabase(testDb, 11).use { version11 ->
            version11.execSQL(
                """
                INSERT INTO medication_groups (
                    uuid, name, colorKey, notificationsEnabled, scheduleType,
                    scheduleInterval, scheduleSinceEpochDay, createdAtEpochMillis,
                    updatedAtEpochMillis, includePastScheduledSlots
                ) VALUES (
                    '$GROUP_UUID', 'Evening', 'ROSE', 0, 'DAILY',
                    1, 19000, $UPDATED_AT, $UPDATED_AT, 1
                )
                """.trimIndent()
            )
        }

        migrationHelper.runMigrationsAndValidate(testDb, 12, true, MIGRATION_11_12)
            .use { db ->
                assertColumn(
                    db = db,
                    table = "medication_groups",
                    column = "dayEndsAtFourAm",
                    type = "INTEGER",
                    notNull = true,
                    defaultValue = "0",
                )
                assertSeededPlanAtMidnight(db)
            }

        // Reopen through Room too, so its runtime identity-hash check runs with
        // the SQLCipher factory used by production.
        val roomDatabase = Room.databaseBuilder(context, HrtTrackerDatabase::class.java, testDb)
            .openHelperFactory(cipherFactory())
            .addMigrations(MIGRATION_11_12)
            .allowMainThreadQueries()
            .build()
        migrationHelper.closeWhenFinished(roomDatabase)
        assertSeededPlanAtMidnight(roomDatabase.openHelper.writableDatabase)
    }

    // Every pre-v12 plan ended its day at midnight.
    private fun assertSeededPlanAtMidnight(db: SupportSQLiteDatabase) {
        db.query("SELECT dayEndsAtFourAm, updatedAtEpochMillis FROM medication_groups")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertEquals(UPDATED_AT, cursor.getLong(1))
            }
    }

    private fun assertColumn(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
        type: String,
        notNull: Boolean,
        defaultValue: String,
    ) {
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            val defaultValueIndex = cursor.getColumnIndexOrThrow("dflt_value")

            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    assertEquals(type, cursor.getString(typeIndex))
                    assertEquals(if (notNull) 1 else 0, cursor.getInt(notNullIndex))
                    assertEquals(defaultValue, cursor.getString(defaultValueIndex))
                    return
                }
            }
        }

        fail("Missing column $table.$column")
    }

    private companion object {
        const val GROUP_UUID = "00000000-0000-0000-0000-000000000012"
        const val UPDATED_AT = 1_700_000_000_000L

        fun cipherFactory(): SupportOpenHelperFactory {
            return SupportOpenHelperFactory(
                "migration-test-passphrase".toByteArray(Charsets.UTF_8)
            )
        }
    }
}
