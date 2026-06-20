package com.mkx.hrttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mkx.hrttracker.model.journal.PrideFlag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeroBackgroundPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun migration8To9_addsColumn_preservingExistingRowsAsNone() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE tracked_dates (
                            uuid TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            iconKey TEXT NOT NULL,
                            dateIso TEXT NOT NULL,
                            paletteKey TEXT,
                            pinnedOrder INTEGER,
                            createdAtEpochMillis INTEGER NOT NULL,
                            updatedAtEpochMillis INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        val db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        db.execSQL(
            "INSERT INTO tracked_dates " +
                "(uuid,name,iconKey,dateIso,paletteKey,pinnedOrder,createdAtEpochMillis,updatedAtEpochMillis) " +
                "VALUES ('id1','Started E','medication','2024-01-01',NULL,0,1000,1000)"
        )

        MIGRATION_8_9.migrate(db)

        db.query("SELECT name, heroBackgroundKey FROM tracked_dates WHERE uuid='id1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Started E", c.getString(0))
            assertTrue("legacy rows decode to None", c.isNull(1))
        }
        db.close()
    }

    @Test
    fun dao_updateHeroBackground_roundTrips() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, HrtTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.journalDao()
        dao.upsertTrackedDate(
            TrackedDateEntity(
                uuid = "id1",
                name = "Started E",
                iconKey = "medication",
                dateIso = "2024-01-01",
                paletteKey = null,
                heroBackgroundKey = null,
                pinnedOrder = 0,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
            )
        )

        dao.updateHeroBackground("id1", PrideFlag.TRANSGENDER.name, 2000L)
        val set = dao.getTrackedDates().first { it.uuid == "id1" }
        assertEquals(PrideFlag.TRANSGENDER.name, set.heroBackgroundKey)
        assertEquals(2000L, set.updatedAtEpochMillis)

        dao.updateHeroBackground("id1", null, 3000L)
        assertEquals(null, dao.getTrackedDates().first { it.uuid == "id1" }.heroBackgroundKey)
        db.close()
    }
}
