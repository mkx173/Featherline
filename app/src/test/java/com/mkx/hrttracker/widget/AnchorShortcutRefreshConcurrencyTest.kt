package com.mkx.hrttracker.widget

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.TrackedDate
import dagger.hilt.android.EntryPointAccessors
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * The journal observer and the date receiver both refresh the pinned anchor shortcuts.
 * A refresh plans from the journal it read and the launcher's pins it read after it, so
 * one that read the journal before a delete must not apply that plan over the refresh
 * the delete itself triggered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnchorShortcutRefreshConcurrencyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val journalRepository: JournalRepository = mockk()

    // The launcher's pinned shortcuts: id to enabled.
    private val pins = ConcurrentHashMap<String, Boolean>()

    @Before
    fun setUp() {
        mockkStatic(EntryPointAccessors::class)
        val entryPoint: WidgetEntryPoint = mockk {
            every { journalRepository() } returns journalRepository
        }
        every { EntryPointAccessors.fromApplication(any(), WidgetEntryPoint::class.java) } returns entryPoint
        mockkStatic(ShortcutManagerCompat::class)
        every { ShortcutManagerCompat.getShortcuts(any(), ShortcutManagerCompat.FLAG_MATCH_PINNED) } answers {
            pins.map { (shortcutId, enabled) ->
                mockk<ShortcutInfoCompat> {
                    every { id } returns shortcutId
                    every { isEnabled } returns enabled
                }
            }
        }
        every { ShortcutManagerCompat.enableShortcuts(any(), any()) } answers {
            secondArg<List<ShortcutInfoCompat>>().forEach { shortcut -> pins[shortcut.id] = true }
        }
        every { ShortcutManagerCompat.disableShortcuts(any(), any(), any()) } answers {
            secondArg<List<String>>().forEach { shortcutId -> pins[shortcutId] = false }
        }
        every { ShortcutManagerCompat.updateShortcuts(any(), any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun aRefreshThatReadTheJournalBeforeADelete_neverReEnablesTheDeletedAnchorsShortcut() = runTest {
        val anchor = TrackedDate(
            id = "a1",
            name = "HRT start",
            icon = AnchorIcon.entries.first(),
            date = LocalDate.of(2025, 3, 14),
            palette = null,
            pinnedOrder = 0,
        )
        pins["anchor:a1"] = true
        var journal = listOf(anchor)
        val staleRead = CompletableDeferred<Unit>()
        val releaseStale = CompletableDeferred<Unit>()
        var reads = 0
        coEvery { journalRepository.awaitTrackedDatesOrNull(any()) } coAnswers {
            val read = journal
            if (reads++ == 0) {
                staleRead.complete(Unit)
                releaseStale.await()
            }
            read
        }

        // The date receiver's refresh reads the journal while the anchor is still there...
        val receiverRefresh = launch { AnchorShortcutManager.refreshAll(context) }
        staleRead.await()
        // ...the anchor is deleted, and the observer's refresh for the delete runs as far as
        // it can before the receiver's refresh goes on.
        journal = emptyList()
        val observerRefresh = launch { AnchorShortcutManager.refreshAll(context) }
        advanceUntilIdle()
        releaseStale.complete(Unit)
        joinAll(receiverRefresh, observerRefresh)

        assertEquals(
            "The deleted anchor's shortcut was re-enabled by a refresh that planned from " +
                "the journal it read before the delete",
            false,
            pins["anchor:a1"],
        )
    }
}
