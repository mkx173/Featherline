package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.UserProfileDao
import com.mkx.hrttracker.data.local.UserProfileEntity
import com.mkx.hrttracker.model.personalization.WeightUnit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

class UserProfileRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val dao: UserProfileDao = mockk(relaxed = true)

    private lateinit var repository: UserProfileRepository

    @Before
    fun setUp() {
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        every { databaseHolder.get() } returns database
        every { database.userProfileDao() } returns dao

        val testScope = CoroutineScope(StandardTestDispatcher())
        repository = UserProfileRepository(databaseHolder, testScope)
    }

    @Test
    fun setWeight_kg_stores_value_unchanged_as_kg() = runTest {
        val captured = slot<UserProfileEntity>()
        coEvery { dao.upsertProfile(capture(captured)) } returns Unit

        repository.setWeight(
            originalValue = 72.5,
            originalUnit = WeightUnit.KILOGRAMS,
            now = Instant.ofEpochMilli(1_700_000_000_000L)
        )

        assertEquals(72.5, captured.captured.weightKg!!, 1e-9)
        assertEquals(72.5, captured.captured.weightOriginalValue!!, 1e-9)
        assertEquals("KILOGRAMS", captured.captured.weightOriginalUnit)
        assertEquals(1_700_000_000_000L, captured.captured.updatedAtEpochMillis)
        assertEquals(UserProfileEntity.SINGLETON_ID, captured.captured.id)
    }

    @Test
    fun setWeight_lb_converts_to_kg_using_standard_factor() = runTest {
        val captured = slot<UserProfileEntity>()
        coEvery { dao.upsertProfile(capture(captured)) } returns Unit

        repository.setWeight(
            originalValue = 160.0,
            originalUnit = WeightUnit.POUNDS,
            now = Instant.ofEpochMilli(0L)
        )

        val expectedKg = 160.0 * 0.45359237
        assert(abs(captured.captured.weightKg!! - expectedKg) < 1e-9)
        assertEquals(160.0, captured.captured.weightOriginalValue!!, 1e-9)
        assertEquals("POUNDS", captured.captured.weightOriginalUnit)
    }

    @Test
    fun clearWeight_persists_all_null_fields() = runTest {
        val captured = slot<UserProfileEntity>()
        coEvery { dao.upsertProfile(capture(captured)) } returns Unit

        repository.clearWeight(now = Instant.ofEpochMilli(42L))

        assertNull(captured.captured.weightKg)
        assertNull(captured.captured.weightOriginalValue)
        assertNull(captured.captured.weightOriginalUnit)
        assertEquals(42L, captured.captured.updatedAtEpochMillis)
        coVerify(exactly = 1) { dao.upsertProfile(any()) }
    }
}
