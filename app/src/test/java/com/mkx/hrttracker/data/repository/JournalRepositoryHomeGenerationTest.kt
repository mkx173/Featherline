package com.mkx.hrttracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.TrackedDateEntity
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Home's hero card paints from the persisted Home snapshot at cold start, which the next
 * start trusts while its generation equals the durable one. A journal write that changes
 * the hero must therefore move the durable generation itself: if the process dies right
 * after the write, nothing else is left to tell the next start that the snapshot is stale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JournalRepositoryHomeGenerationTest {
    private val now = LocalDateTime.of(2026, 11, 8, 9, 0)
    private val zoneId = ZoneId.systemDefault()
    private val databaseHolder: DatabaseHolder = mockk()
    private lateinit var database: HrtTrackerDatabase

    // Disk, as one process leaves it and the next one reads it.
    @Volatile
    private var durableGeneration = 3L

    @Volatile
    private var persistedSnapshot: HomeSnapshotRecord? = null
    private val generationStore: HomeSnapshotGenerationStore = mockk {
        coEvery { readGeneration() } answers { durableGeneration }
        coEvery { incrementGeneration() } answers { ++durableGeneration }
    }
    private val snapshotStore: HomeSnapshotStore = mockk {
        coEvery { readSnapshot() } answers { persistedSnapshot }
        coEvery { readE2Inputs() } returns null
    }
    private val settingsRepository: SettingsRepository = mockk {
        every { homeE2ChartWindowOptionFlow } returns MutableStateFlow(HomeE2ChartWindowOption.SEVEN_DAYS)
    }

    private val hero = trackedDate("hero", name = "HRT start", pinnedOrder = 0)
    private val second = trackedDate("second", name = "Name change", pinnedOrder = 1)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HrtTrackerDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        every { databaseHolder.get() } returns database
        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        // The process dies before any run lands: none rewrites the snapshot.
        coEvery { databaseHolder.awaitOpen() } coAnswers { awaitCancellation() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addingTheFirstPinnedDate_makesTheNextStartRejectTheSnapshot() = runTest {
        assertTheNextStartRejectsTheSnapshotAfter(seed = emptyList()) {
            addTrackedDate("HRT start", "event", LocalDate.of(2025, 3, 14), null, pinned = true)
        }
    }

    @Test
    fun editingTheHero_makesTheNextStartRejectTheSnapshot() = runTest {
        assertTheNextStartRejectsTheSnapshotAfter(seed = listOf(hero, second)) {
            updateTrackedDate(hero.uuid, "Renamed", hero.iconKey, LocalDate.of(2025, 3, 14), null)
        }
    }

    @Test
    fun deletingTheHero_makesTheNextStartRejectTheSnapshot() = runTest {
        assertTheNextStartRejectsTheSnapshotAfter(seed = listOf(hero, second)) {
            deleteTrackedDate(hero.uuid)
        }
    }

    @Test
    fun unpinningTheHero_makesTheNextStartRejectTheSnapshot() = runTest {
        assertTheNextStartRejectsTheSnapshotAfter(seed = listOf(hero, second)) {
            setPinned(hero.uuid, pinned = false)
        }
    }

    @Test
    fun reorderingAnotherDateFirst_makesTheNextStartRejectTheSnapshot() = runTest {
        assertTheNextStartRejectsTheSnapshotAfter(seed = listOf(hero, second)) {
            reorderPinned(listOf(second.uuid, hero.uuid))
        }
    }

    @Test
    fun changingTheHerosBackground_makesTheNextStartRejectTheSnapshot() = runTest {
        assertTheNextStartRejectsTheSnapshotAfter(seed = listOf(hero, second)) {
            setHeroBackground(hero.uuid, HeroBackground.DateColor)
        }
    }

    /**
     * One process: Room holds [seed], whose hero the persisted snapshot shows at the durable
     * generation; [write] changes the hero, and the process dies as soon as it returns, before
     * the journal's own app-scope work runs. The next start must not load that snapshot.
     */
    private suspend fun TestScope.assertTheNextStartRejectsTheSnapshotAfter(
        seed: List<TrackedDateEntity>,
        write: suspend JournalRepository.() -> Unit,
    ) {
        database.journalDao().insertTrackedDates(seed)
        val heroBefore = database.journalDao().getFirstPinnedTrackedDate()?.toModel()
        persistedSnapshot = homeData(generation = durableGeneration).copy(homeAnchor = heroBefore)
        val journal = JournalRepository(
            databaseHolder = databaseHolder,
            clock = Clock.fixed(now.atZone(zoneId).toInstant(), zoneId),
            homeSnapshotRepository = homeSnapshotRepository(),
            anchorSnapshotStore = mockk(relaxed = true),
            // Never advanced: the process is gone before anything it launches runs.
            appScope = CoroutineScope(StandardTestDispatcher()),
        )

        journal.write()
        val heroAfter = database.journalDao().getFirstPinnedTrackedDate()?.toModel()
        assertNotEquals("The write under test must change the hero", heroBefore, heroAfter)

        val nextStart = homeSnapshotRepository().cache.filterNotNull().first()
        assertNull(
            "The next start loaded the snapshot persisted before the write, so Home shows " +
                "the hero $heroBefore instead of $heroAfter: the write left the durable " +
                "generation at $durableGeneration",
            nextStart.homeData,
        )
    }

    private fun TestScope.homeSnapshotRepository() = HomeSnapshotRepository(
        databaseHolder = databaseHolder,
        homeSnapshotStore = snapshotStore,
        homeSnapshotGenerationStore = generationStore,
        settingsRepository = settingsRepository,
        e2EstimateRepository = { mockk(relaxed = true) },
        appTimeSource = FakeAppTimeSource(now),
        appScope = backgroundScope,
        defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
        diagnosticsLogger = mockk(relaxed = true),
    )

    private fun homeData(generation: Long) = HomeSnapshotRecord(
        schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
        generation = generation,
        generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
        anchorDateEpochDay = now.toLocalDate().toEpochDay(),
        zoneId = zoneId.id,
        activeGroups = emptyList(),
        scheduleEntries = emptyList(),
        antiandrogenHistoryEntries = emptyList(),
    )

    private fun trackedDate(uuid: String, name: String, pinnedOrder: Int?) = TrackedDateEntity(
        uuid = uuid,
        name = name,
        iconKey = "event",
        dateIso = "2025-03-14",
        paletteKey = null,
        heroBackgroundKey = null,
        pinnedOrder = pinnedOrder,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )
}
