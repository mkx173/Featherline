package com.mkx.hrttracker.ui.journal

import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        assertNotNull(state.heroNextMilestoneLabel)
        assertEquals(listOf("On estradiol"), state.pinnedTray.map { it.name })
        assertEquals(listOf("On estradiol", "Surgery"), state.timeline.map { it.anchor.name })
        assertEquals(1, state.todayDividerIndex)
        assertEquals(listOf(true, false), state.timeline.map { it.isPinned })
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
        assertNull(state.heroNextMilestoneLabel)
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
        assertEquals("100 days", viewModel.uiState.value.heroNextMilestoneLabel)
        assertEquals(1, viewModel.uiState.value.todayDividerIndex)

        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 6, 17, 0, 0))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalDate.of(2026, 6, 17), state.today)
        assertEquals(100L, state.hero?.dayMagnitude)
        assertEquals("6 months", state.heroNextMilestoneLabel)
        assertEquals(2, state.todayDividerIndex)
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
            repository.addTrackedDate("Labs", AnchorIcon.LABS.storageKey, addDate, "TEAL")
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
        viewModel.addDate("Labs", AnchorIcon.LABS.storageKey, addDate, "TEAL")
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
            repository.addTrackedDate("Labs", AnchorIcon.LABS.storageKey, addDate, "TEAL")
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
