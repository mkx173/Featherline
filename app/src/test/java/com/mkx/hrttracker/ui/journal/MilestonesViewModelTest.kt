package com.mkx.hrttracker.ui.journal

import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.MilestoneUnit
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MilestonesViewModelTest {
    private val repository: JournalRepository = mockk()
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 6, 16)
    private lateinit var appTimeSource: FakeAppTimeSource

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        appTimeSource = FakeAppTimeSource(
            initialMinute = LocalDateTime.of(2026, 6, 16, 12, 0),
            initialZone = ZoneId.of("Asia/Tokyo"),
        )
        // Default to a cold warm-cache so the initial state seeds the loading placeholder;
        // tests exercising the seed override this.
        every { repository.getCachedTrackedDates() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_heroIsFirstPin_timelineHasAllDatesAndDivider() = runTest {
        val estradiol = trackedDate(
            id = "estradiol",
            name = "On estradiol",
            icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2024, 4, 1),
            palette = MedicationGroupColorKey.ROSE,
            pinnedOrder = 0,
        )
        val surgery = trackedDate(
            id = "surgery",
            name = "Surgery",
            icon = AnchorIcon.FLAG,
            date = LocalDate.of(2026, 9, 15),
            pinnedOrder = null,
        )
        stubTrackedDates(
            all = listOf(surgery, estradiol),
            pinned = listOf(estradiol),
        )

        val viewModel = MilestonesViewModel(repository, appTimeSource)
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(today, state.today)
        assertEquals("On estradiol", state.hero?.name)
        assertNotNull(state.heroNextMilestone)
        assertEquals(listOf("On estradiol"), state.pinnedTray.map { it.name })
        assertEquals(listOf("On estradiol", "Surgery"), state.timeline.map { it.anchor.name })
        assertEquals(1, state.todayDividerIndex)
        assertEquals(listOf(true, false), state.timeline.map { it.isPinned })
    }

    // Opening Milestones with an already-warm cache must render real data on the very first
    // frame instead of flashing the loading indicator until the cold Room flow lands — the
    // same seeding JournalViewModel/PlanViewModel do. Regression for the entry loading-flash.
    @Test
    fun uiState_seedsFromWarmCache_noLoadingFlashOnEntry() = runTest {
        val estradiol = trackedDate(
            id = "estradiol",
            name = "On estradiol",
            icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2024, 4, 1),
            palette = MedicationGroupColorKey.ROSE,
            pinnedOrder = 0,
        )
        val surgery = trackedDate(
            id = "surgery",
            name = "Surgery",
            icon = AnchorIcon.FLAG,
            date = LocalDate.of(2026, 9, 15),
            pinnedOrder = null,
        )
        stubTrackedDates(all = listOf(surgery, estradiol), pinned = listOf(estradiol))
        every { repository.getCachedTrackedDates() } returns listOf(surgery, estradiol)

        val viewModel = MilestonesViewModel(repository, appTimeSource)

        // Read the initial value before the cold combine advances: it must already be loaded.
        val initial = viewModel.uiState.value
        assertFalse(initial.isLoading)
        assertEquals("On estradiol", initial.hero?.name)
        assertEquals(listOf("On estradiol", "Surgery"), initial.timeline.map { it.anchor.name })
        assertEquals(1, initial.todayDividerIndex)
    }

    @Test
    fun uiState_todayDatedAnchorSortsBelowDivider() = runTest {
        val past = trackedDate(
            id = "past", name = "First injection", icon = AnchorIcon.VACCINES,
            date = LocalDate.of(2026, 3, 1), pinnedOrder = 0,
        )
        val todayAnchor = trackedDate(
            id = "today", name = "Blood test", icon = AnchorIcon.BLOODTYPE,
            date = today, pinnedOrder = null,
        )
        val future = trackedDate(
            id = "future", name = "Surgery", icon = AnchorIcon.FLAG,
            date = LocalDate.of(2026, 9, 15), pinnedOrder = null,
        )
        stubTrackedDates(all = listOf(future, todayAnchor, past), pinned = listOf(past))

        val viewModel = MilestonesViewModel(repository, appTimeSource)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            listOf("First injection", "Blood test", "Surgery"),
            state.timeline.map { it.anchor.name },
        )
        // Divider sits before the first today-or-future date — i.e. before "Blood test".
        assertEquals(1, state.todayDividerIndex)
    }

    @Test
    fun uiState_futureHeroHasNoNextMilestoneLabel() = runTest {
        val futureHero = trackedDate(
            id = "surgery",
            name = "Surgery",
            icon = AnchorIcon.FLAG,
            date = LocalDate.of(2026, 9, 15),
            pinnedOrder = 0,
        )
        stubTrackedDates(
            all = listOf(futureHero),
            pinned = listOf(futureHero),
        )

        val viewModel = MilestonesViewModel(repository, appTimeSource)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Surgery", state.hero?.name)
        assertNull(state.heroNextMilestone)
    }

    @Test
    fun uiState_derivesPinnedStateFromSingleTrackedDateSnapshotUsingPinnedOrderTies() = runTest {
        val laterDate = trackedDate(
            id = "later-date",
            name = "A later date",
            icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2026, 3, 2),
            pinnedOrder = 0,
            createdAtEpochMillis = 1,
        )
        val createdFirst = trackedDate(
            id = "created-first",
            name = "Z created first",
            icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2026, 3, 1),
            pinnedOrder = 0,
            createdAtEpochMillis = 10,
        )
        val idFirst = trackedDate(
            id = "a-id",
            name = "Z id first",
            icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2026, 3, 1),
            pinnedOrder = 0,
            createdAtEpochMillis = 20,
        )
        val idSecond = trackedDate(
            id = "b-id",
            name = "A id second",
            icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2026, 3, 1),
            pinnedOrder = 0,
            createdAtEpochMillis = 20,
        )
        val consult = trackedDate(
            id = "consult",
            name = "Consult",
            icon = AnchorIcon.EVENT,
            date = LocalDate.of(2026, 5, 1),
            pinnedOrder = null,
        )
        stubTrackedDates(
            all = listOf(laterDate, consult, idSecond, idFirst, createdFirst),
            pinned = emptyList(),
        )

        val viewModel = MilestonesViewModel(repository, appTimeSource)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Z created first", state.hero?.name)
        assertEquals(
            listOf("Z created first", "Z id first", "A id second", "A later date"),
            state.pinnedTray.map { it.name },
        )
        assertEquals(
            listOf("A id second", "Z created first", "Z id first", "A later date", "Consult"),
            state.timeline.map { it.anchor.name },
        )
        assertEquals(listOf(true, true, true, true, false), state.timeline.map { it.isPinned })
    }

    @Test
    fun uiState_updatesDateDerivedFieldsAfterLocalMidnight() = runTest {
        val estradiol = trackedDate(
            id = "estradiol",
            name = "On estradiol",
            icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2026, 3, 9),
            pinnedOrder = 0,
        )
        val labs = trackedDate(
            id = "labs",
            name = "Labs",
            icon = AnchorIcon.LABS,
            date = LocalDate.of(2026, 6, 17),
            pinnedOrder = null,
        )
        stubTrackedDates(all = listOf(estradiol, labs))
        val viewModel = MilestonesViewModel(repository, appTimeSource)
        advanceUntilIdle()

        assertEquals(today, viewModel.uiState.value.today)
        assertEquals(99L, viewModel.uiState.value.hero?.dayMagnitude)
        assertEquals(
            NextMilestoneUiState(
                remainingDays = 1,
                value = 100,
                unit = MilestoneUnit.DAYS,
            ),
            viewModel.uiState.value.heroNextMilestone,
        )
        assertEquals(1, viewModel.uiState.value.todayDividerIndex)

        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 6, 17, 0, 0))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalDate.of(2026, 6, 17), state.today)
        assertEquals(100L, state.hero?.dayMagnitude)
        assertEquals(
            NextMilestoneUiState(
                remainingDays = 0,
                value = 100,
                unit = MilestoneUnit.DAYS,
            ),
            state.heroNextMilestone,
        )
        // After midnight, "Labs" (2026-06-17) is today-dated, so it sorts below the
        // divider; only the strictly-past "On estradiol" counts toward the index.
        assertEquals(1, state.todayDividerIndex)
    }

    @Test
    fun toggleEditMode_flipsState() = runTest {
        stubTrackedDates()
        val viewModel = MilestonesViewModel(repository, appTimeSource)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEditMode)

        viewModel.toggleEditMode()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEditMode)

        viewModel.toggleEditMode()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEditMode)
    }

    @Test
    fun actions_delegateToRepository() = runTest {
        stubTrackedDates()
        val addDate = LocalDate.of(2026, 7, 1)
        val updateDate = LocalDate.of(2026, 8, 1)
        coEvery { repository.setPinned("date-1", true) } returns Unit
        coEvery { repository.reorderPinned(listOf("date-2", "date-1")) } returns Unit
        coEvery {
            repository.addTrackedDate("Labs", AnchorIcon.LABS.storageKey, addDate, "TEAL", pinned = true)
        } returns Unit
        coEvery {
            repository.updateTrackedDate(
                "date-1",
                "Updated labs",
                AnchorIcon.BLOODTYPE.storageKey,
                updateDate,
                null,
            )
        } returns Unit
        coEvery { repository.deleteTrackedDate("date-1") } returns Unit
        val viewModel = MilestonesViewModel(repository, appTimeSource)

        viewModel.setPinned("date-1", true)
        viewModel.reorderPinned(listOf("date-2", "date-1"))
        viewModel.addDate("Labs", AnchorIcon.LABS.storageKey, addDate, "TEAL", pinned = true)
        viewModel.updateDate(
            id = "date-1",
            name = "Updated labs",
            icon = AnchorIcon.BLOODTYPE.storageKey,
            date = updateDate,
            paletteKey = null,
        )
        viewModel.deleteDate("date-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setPinned("date-1", true) }
        coVerify(exactly = 1) { repository.reorderPinned(listOf("date-2", "date-1")) }
        coVerify(exactly = 1) {
            repository.addTrackedDate("Labs", AnchorIcon.LABS.storageKey, addDate, "TEAL", pinned = true)
        }
        coVerify(exactly = 1) {
            repository.updateTrackedDate(
                "date-1",
                "Updated labs",
                AnchorIcon.BLOODTYPE.storageKey,
                updateDate,
                null,
            )
        }
        coVerify(exactly = 1) { repository.deleteTrackedDate("date-1") }
    }

    // A reorder writes to the repository but the Room flow re-emits only after the
    // transaction commits. The tray must show the new order immediately so exiting
    // edit mode (which recreates the reorder state from this list) can't snap back
    // to the pre-drag order.
    @Test
    fun reorderPinned_reflectsNewOrderBeforeRepositoryFlowReemits() = runTest {
        val a = trackedDate(
            id = "a", name = "Anchor A", icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2024, 4, 1), pinnedOrder = 0,
        )
        val b = trackedDate(
            id = "b", name = "Anchor B", icon = AnchorIcon.FLAG,
            date = LocalDate.of(2024, 5, 1), pinnedOrder = 1,
        )
        val dates = MutableStateFlow(listOf(a, b))
        every { repository.observeTrackedDates() } returns dates
        every { repository.observePinnedTrackedDates() } returns flowOf(listOf(a, b))
        coEvery { repository.reorderPinned(any()) } returns Unit

        val viewModel = MilestonesViewModel(repository, appTimeSource)
        advanceUntilIdle()
        assertEquals(listOf("Anchor A", "Anchor B"), viewModel.uiState.value.pinnedTray.map { it.name })

        // The write is in flight; the Room flow has NOT re-emitted the new order.
        viewModel.reorderPinned(listOf("b", "a"))
        advanceUntilIdle()

        assertEquals(listOf("Anchor B", "Anchor A"), viewModel.uiState.value.pinnedTray.map { it.name })
        assertEquals("Anchor B", viewModel.uiState.value.hero?.name)
    }

    // Saving a hero background writes to the repository; the hero must repaint with
    // the new background immediately instead of after the Room flow re-emits.
    @Test
    fun setHeroBackground_reflectsNewBackgroundBeforeRepositoryFlowReemits() = runTest {
        val hero = trackedDate(
            id = "a", name = "Anchor A", icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2024, 4, 1), pinnedOrder = 0,
        )
        val dates = MutableStateFlow(listOf(hero))
        every { repository.observeTrackedDates() } returns dates
        every { repository.observePinnedTrackedDates() } returns flowOf(listOf(hero))
        coEvery { repository.setHeroBackground(any(), any()) } returns Unit

        val viewModel = MilestonesViewModel(repository, appTimeSource)
        advanceUntilIdle()
        assertEquals(HeroBackground.None, viewModel.uiState.value.hero?.heroBackground)

        viewModel.setHeroBackground("a", HeroBackground.DateColor)
        advanceUntilIdle()

        assertEquals(HeroBackground.DateColor, viewModel.uiState.value.hero?.heroBackground)
    }

    // The optimistic order must be discarded once the source reflects it, so a later
    // independent source change wins instead of being masked forever.
    @Test
    fun reorderPinned_optimisticOverrideClearsOnceRepositoryFlowCatchesUp() = runTest {
        val a = trackedDate(
            id = "a", name = "Anchor A", icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2024, 4, 1), pinnedOrder = 0,
        )
        val b = trackedDate(
            id = "b", name = "Anchor B", icon = AnchorIcon.FLAG,
            date = LocalDate.of(2024, 5, 1), pinnedOrder = 1,
        )
        val dates = MutableStateFlow(listOf(a, b))
        every { repository.observeTrackedDates() } returns dates
        every { repository.observePinnedTrackedDates() } returns flowOf(listOf(a, b))
        coEvery { repository.reorderPinned(any()) } returns Unit

        val viewModel = MilestonesViewModel(repository, appTimeSource)
        advanceUntilIdle()
        viewModel.reorderPinned(listOf("b", "a"))
        advanceUntilIdle()
        assertEquals(listOf("Anchor B", "Anchor A"), viewModel.uiState.value.pinnedTray.map { it.name })

        // The source catches up to the persisted order.
        dates.value = listOf(b.copy(pinnedOrder = 0), a.copy(pinnedOrder = 1))
        advanceUntilIdle()
        assertEquals(listOf("Anchor B", "Anchor A"), viewModel.uiState.value.pinnedTray.map { it.name })

        // A later independent source change must win — the override is gone.
        dates.value = listOf(a.copy(pinnedOrder = 0), b.copy(pinnedOrder = 1))
        advanceUntilIdle()
        assertEquals(listOf("Anchor A", "Anchor B"), viewModel.uiState.value.pinnedTray.map { it.name })
    }

    @Test
    fun setHeroBackground_optimisticOverrideClearsOnceRepositoryFlowCatchesUp() = runTest {
        val hero = trackedDate(
            id = "a", name = "Anchor A", icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2024, 4, 1), pinnedOrder = 0,
        )
        val dates = MutableStateFlow(listOf(hero))
        every { repository.observeTrackedDates() } returns dates
        every { repository.observePinnedTrackedDates() } returns flowOf(listOf(hero))
        coEvery { repository.setHeroBackground(any(), any()) } returns Unit

        val viewModel = MilestonesViewModel(repository, appTimeSource)
        advanceUntilIdle()
        viewModel.setHeroBackground("a", HeroBackground.None)
        advanceUntilIdle()
        assertEquals(HeroBackground.None, viewModel.uiState.value.hero?.heroBackground)

        // The source catches up, then a later independent change must win.
        dates.value = listOf(hero.copy(heroBackground = HeroBackground.None))
        advanceUntilIdle()
        dates.value = listOf(hero.copy(heroBackground = HeroBackground.DateColor))
        advanceUntilIdle()
        assertEquals(HeroBackground.DateColor, viewModel.uiState.value.hero?.heroBackground)
    }

    // A reorder can be rejected by the repository (the pinned set changed underneath,
    // e.g. a row was just unpinned), and a rejected write doesn't re-emit. The optimistic
    // override must not survive that, or it resurrects the rejected order once the set
    // matches again.
    @Test
    fun reorderPinned_overrideDoesNotResurrectAfterRepositoryRejectsStaleOrder() = runTest {
        val a = trackedDate(
            id = "a", name = "Anchor A", icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2024, 4, 1), pinnedOrder = 0,
        )
        val b = trackedDate(
            id = "b", name = "Anchor B", icon = AnchorIcon.FLAG,
            date = LocalDate.of(2024, 5, 1), pinnedOrder = 1,
        )
        val c = trackedDate(
            id = "c", name = "Anchor C", icon = AnchorIcon.EVENT,
            date = LocalDate.of(2024, 6, 1), pinnedOrder = 2,
        )
        val dates = MutableStateFlow(listOf(a, b, c))
        every { repository.observeTrackedDates() } returns dates
        every { repository.observePinnedTrackedDates() } returns flowOf(listOf(a, b, c))
        // The repository's separate warm cache lags the screen's observed flow — here it
        // never advances past the original {a,b,c}. Reconciliation must use the observed
        // source, not this cache, or the rejected order survives and later resurrects.
        every { repository.getCachedTrackedDates() } returns listOf(a, b, c)
        // The stale reorder is rejected, so the source never re-emits for this write.
        coEvery { repository.reorderPinned(any()) } returns Unit

        val viewModel = MilestonesViewModel(repository, appTimeSource)
        advanceUntilIdle()

        // The pinned set changes underneath: C is unpinned (and that emission settles).
        dates.value = listOf(a, b, c.copy(pinnedOrder = null))
        advanceUntilIdle()
        // A drag computed from the stale tray still submits the old {a,b,c} order; the
        // repository rejects it with no further emission.
        viewModel.reorderPinned(listOf("c", "b", "a"))
        advanceUntilIdle()
        assertEquals(listOf("Anchor A", "Anchor B"), viewModel.uiState.value.pinnedTray.map { it.name })

        // C is pinned again — the rejected [c,b,a] override must not come back to life.
        dates.value = listOf(a, b, c)
        advanceUntilIdle()
        assertEquals(
            listOf("Anchor A", "Anchor B", "Anchor C"),
            viewModel.uiState.value.pinnedTray.map { it.name },
        )
    }

    // Editing an existing date (name/icon/date/palette) writes to the repository; the row
    // must repaint with the edits immediately instead of after the Room flow re-emits.
    @Test
    fun updateDate_reflectsEditedFieldsBeforeRepositoryFlowReemits() = runTest {
        val original = trackedDate(
            id = "a", name = "Old name", icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2024, 4, 1), palette = MedicationGroupColorKey.ROSE,
            pinnedOrder = 0,
        )
        val dates = MutableStateFlow(listOf(original))
        every { repository.observeTrackedDates() } returns dates
        every { repository.observePinnedTrackedDates() } returns flowOf(listOf(original))
        coEvery { repository.updateTrackedDate(any(), any(), any(), any(), any()) } returns Unit

        val viewModel = MilestonesViewModel(repository, appTimeSource)
        advanceUntilIdle()
        assertEquals(MedicationGroupColorKey.ROSE, viewModel.uiState.value.hero?.palette)

        val newDate = LocalDate.of(2024, 5, 2)
        viewModel.updateDate(
            id = "a",
            name = "New name",
            icon = AnchorIcon.FLAG.storageKey,
            date = newDate,
            paletteKey = MedicationGroupColorKey.TEAL.name,
        )
        advanceUntilIdle()

        val hero = viewModel.uiState.value.hero
        assertEquals("New name", hero?.name)
        assertEquals(AnchorIcon.FLAG, hero?.icon)
        assertEquals(newDate, hero?.date)
        assertEquals(MedicationGroupColorKey.TEAL, hero?.palette)
    }

    @Test
    fun updateDate_optimisticOverrideClearsOnceRepositoryFlowCatchesUp() = runTest {
        val original = trackedDate(
            id = "a", name = "Old name", icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2024, 4, 1), palette = MedicationGroupColorKey.ROSE,
            pinnedOrder = 0,
        )
        val dates = MutableStateFlow(listOf(original))
        every { repository.observeTrackedDates() } returns dates
        every { repository.observePinnedTrackedDates() } returns flowOf(listOf(original))
        coEvery { repository.updateTrackedDate(any(), any(), any(), any(), any()) } returns Unit

        val viewModel = MilestonesViewModel(repository, appTimeSource)
        advanceUntilIdle()
        viewModel.updateDate(
            id = "a",
            name = "New name",
            icon = AnchorIcon.FLAG.storageKey,
            date = original.date,
            paletteKey = MedicationGroupColorKey.TEAL.name,
        )
        advanceUntilIdle()
        assertEquals(MedicationGroupColorKey.TEAL, viewModel.uiState.value.hero?.palette)

        // The source catches up to the edit.
        dates.value = listOf(
            original.copy(name = "New name", icon = AnchorIcon.FLAG, palette = MedicationGroupColorKey.TEAL),
        )
        advanceUntilIdle()
        // A later independent change to the same date must win — the override is gone.
        dates.value = listOf(
            original.copy(name = "External edit", icon = AnchorIcon.FLAG, palette = MedicationGroupColorKey.ROSE),
        )
        advanceUntilIdle()
        assertEquals("External edit", viewModel.uiState.value.hero?.name)
        assertEquals(MedicationGroupColorKey.ROSE, viewModel.uiState.value.hero?.palette)
    }

    private fun stubTrackedDates(
        all: List<TrackedDate> = emptyList(),
        pinned: List<TrackedDate> = emptyList(),
    ) {
        every { repository.observeTrackedDates() } returns flowOf(all)
        every { repository.observePinnedTrackedDates() } returns flowOf(pinned)
    }

    private fun trackedDate(
        id: String,
        name: String,
        icon: AnchorIcon,
        date: LocalDate,
        palette: MedicationGroupColorKey? = null,
        pinnedOrder: Int?,
        createdAtEpochMillis: Long = 0L,
    ): TrackedDate = TrackedDate(
        id = id,
        name = name,
        icon = icon,
        date = date,
        palette = palette,
        pinnedOrder = pinnedOrder,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
