package com.mkx.hrttracker.data.local

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class DatabaseMigrationsTest {
    @Test
    fun migration25To26_backfillsScheduleTimeOwnershipAndLogScheduleTimeLinks() {
        val database = mockk<SupportSQLiteDatabase>()
        val statements = mutableListOf<String>()
        val statementsWithArgs = mutableListOf<Pair<String, List<Any?>>>()
        every { database.execSQL(any<String>()) } answers {
            statements += firstArg<String>()
            Unit
        }
        every { database.execSQL(any<String>(), any<Array<Any?>>()) } answers {
            statementsWithArgs += firstArg<String>() to secondArg<Array<Any?>>().toList()
            Unit
        }
        every {
            database.query(match<String> { sql -> sql.contains("archivedAtEpochMillis") })
        } returns cursorOf(
            columns = listOf("uuid", "archivedAtEpochMillis"),
            rows = listOf(
                listOf(
                    "archived-group",
                    Instant.parse("2026-04-18T01:00:00Z").toEpochMilli(),
                )
            ),
        )
        every {
            database.query(match<String> { sql ->
                sql.contains("FROM medication_group_schedule_times AS times")
            })
        } returns cursorOf(
            columns = listOf(
                "groupUuid",
                "sortOrder",
                "hourOfDay",
                "minuteOfHour",
                "scheduleSinceEpochDay",
                "createdAtEpochMillis",
                "includePastScheduledSlots",
            ),
            rows = listOf(
                listOf(
                    "fresh-group",
                    0,
                    9,
                    0,
                    LocalDate.of(2026, 4, 1).toEpochDay(),
                    Instant.parse("2026-04-18T01:00:00Z").toEpochMilli(),
                    1,
                ),
                listOf(
                    "forward-only-group",
                    1,
                    21,
                    30,
                    LocalDate.of(2026, 4, 1).toEpochDay(),
                    Instant.parse("2026-04-18T01:00:00Z").toEpochMilli(),
                    0,
                ),
            ),
        )

        MIGRATION_25_26.migrate(database)

        val archivedUpdate = statementsWithArgs.single { (sql, _) ->
            sql.contains("UPDATE medication_groups") && sql.contains("archivedAtLocalIso")
        }
        assertEquals(
            Instant.parse("2026-04-18T01:00:00Z")
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .toString(),
            archivedUpdate.second[0],
        )
        assertEquals("archived-group", archivedUpdate.second[1])

        val scheduleInserts = statementsWithArgs.filter { (sql, _) ->
            sql.contains("INSERT INTO medication_group_schedule_times_new")
        }
        assertEquals(2, scheduleInserts.size)
        scheduleInserts.forEach { (_, args) ->
            UUID.fromString(args[0] as String)
        }
        assertEquals("fresh-group", scheduleInserts[0].second[1])
        assertEquals("2026-04-01T00:00", scheduleInserts[0].second[5])
        assertEquals("forward-only-group", scheduleInserts[1].second[1])
        assertEquals(
            Instant.parse("2026-04-18T01:00:00Z")
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .toString(),
            scheduleInserts[1].second[5],
        )
        assertTrue(statements.any { sql ->
            sql.contains("ALTER TABLE medication_log_entries ADD COLUMN scheduleTimeUuid")
        })
        assertTrue(statements.any { sql ->
            sql.contains("UPDATE medication_log_entries") &&
                sql.contains("SET scheduleTimeUuid") &&
                sql.contains("COUNT(*)")
        })
    }

    @Test
    fun migration25To26_legacySuccessorWithIncludePastTrue_setsEffectiveFromSinceStartOfDay() {
        val database = mockk<SupportSQLiteDatabase>()
        val statementsWithArgs = mutableListOf<Pair<String, List<Any?>>>()
        every { database.execSQL(any<String>()) } answers { Unit }
        every { database.execSQL(any<String>(), any<Array<Any?>>()) } answers {
            statementsWithArgs += firstArg<String>() to secondArg<Array<Any?>>().toList()
            Unit
        }
        every {
            database.query(match<String> { sql -> sql.contains("archivedAtEpochMillis") })
        } returns cursorOf(columns = listOf("uuid", "archivedAtEpochMillis"), rows = emptyList())
        every {
            database.query(match<String> { sql ->
                sql.contains("FROM medication_group_schedule_times AS times")
            })
        } returns cursorOf(
            columns = listOf(
                "groupUuid",
                "sortOrder",
                "hourOfDay",
                "minuteOfHour",
                "scheduleSinceEpochDay",
                "createdAtEpochMillis",
                "includePastScheduledSlots",
            ),
            rows = listOf(
                listOf(
                    "legacy-successor",
                    0,
                    9,
                    0,
                    LocalDate.of(2026, 4, 1).toEpochDay(),
                    Instant.parse("2026-04-18T01:00:00Z").toEpochMilli(),
                    1,
                ),
            ),
        )

        MIGRATION_25_26.migrate(database)

        val scheduleInsert = statementsWithArgs.single { (sql, _) ->
            sql.contains("INSERT INTO medication_group_schedule_times_new")
        }
        assertEquals("legacy-successor", scheduleInsert.second[1])
        assertEquals("2026-04-01T00:00", scheduleInsert.second[5])
    }

    private fun cursorOf(
        columns: List<String>,
        rows: List<List<Any?>>,
    ): Cursor {
        val cursor = mockk<Cursor>()
        var position = -1
        every { cursor.getColumnIndexOrThrow(any()) } answers {
            val columnName = firstArg<String>()
            val index = columns.indexOf(columnName)
            if (index < 0) {
                throw IllegalArgumentException("Unknown column $columnName")
            }
            index
        }
        every { cursor.moveToNext() } answers {
            position += 1
            position < rows.size
        }
        every { cursor.getString(any()) } answers {
            rows[position][firstArg<Int>()] as String
        }
        every { cursor.getLong(any()) } answers {
            when (val value = rows[position][firstArg<Int>()]) {
                is Long -> value
                is Int -> value.toLong()
                else -> error("Value $value is not a Long")
            }
        }
        every { cursor.getInt(any()) } answers {
            when (val value = rows[position][firstArg<Int>()]) {
                is Int -> value
                is Long -> value.toInt()
                else -> error("Value $value is not an Int")
            }
        }
        every { cursor.close() } returns Unit
        return cursor
    }
}
