package com.mkx.hrttracker.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.mkx.hrttracker.R
import dev.chrisbanes.haze.HazeState

/**
 * Whether an editor has changes it would lose, and the leave waiting on the user to discard them.
 * Kept per back-stack entry, not per composition, so an editor under a page opened from it (the
 * plan editor's medicine picker) still asks when that page leaves for a tab. An editor hosted
 * outside the back stack (onboarding's) is kept for its composition instead.
 *
 * A leave waits only while there is something to discard: once no editor has unsaved changes,
 * such as when the one asking saves and finishes, it is dropped. Discarding marks the editors it
 * leaves, which stay composed, still reporting their edits, while they animate out: they no
 * longer count or ask.
 */
@Stable
class UnsavedChangesState {
    private val dirtyEntries = mutableStateMapOf<Any, Boolean>()
    private val discardedEntries = mutableStateMapOf<Any, Unit>()

    // The composed dialog hosts, the last one showing the dialog: onboarding's over the app's.
    internal val dialogHosts = mutableStateListOf<Any>()

    /** The leave [UnsavedChangesDialog] asks about, null while none waits. */
    var pendingLeave: (() -> Unit)? by mutableStateOf(null)
        private set

    val hasUnsavedChanges: Boolean
        get() = dirtyEntries.values.any { it }

    /** Runs [action] at once, or once the user discards the unsaved changes. */
    fun leave(action: () -> Unit) {
        if (hasUnsavedChanges) pendingLeave = action else action()
    }

    /** Drops [action], unless another leave has replaced it since its dialog showed. */
    fun keepEditing(action: () -> Unit) {
        if (pendingLeave === action) pendingLeave = null
    }

    fun discard() {
        val action = pendingLeave ?: return
        pendingLeave = null
        dirtyEntries.filterValues { dirty -> dirty }.keys.forEach { owner ->
            discardedEntries[owner] = Unit
            dirtyEntries[owner] = false
        }
        action()
    }

    internal fun isDiscarded(owner: Any): Boolean = owner in discardedEntries

    /** [owner] is a back-stack entry, dropped with it, or a key [remove]d by its holder. */
    internal fun update(owner: Any, dirty: Boolean) {
        if (owner in discardedEntries) return
        if (owner is LifecycleOwner && owner !in dirtyEntries) {
            // Dropped with its entry, whether or not the editor is composed by then.
            owner.lifecycle.addObserver(
                object : LifecycleEventObserver {
                    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                        if (event == Lifecycle.Event.ON_DESTROY) {
                            source.lifecycle.removeObserver(this)
                            remove(owner)
                        }
                    }
                }
            )
        }
        dirtyEntries[owner] = dirty
        dropLeaveWithNothingToDiscard()
    }

    internal fun remove(owner: Any) {
        dirtyEntries.remove(owner)
        discardedEntries.remove(owner)
        dropLeaveWithNothingToDiscard()
    }

    private fun dropLeaveWithNothingToDiscard() {
        if (!hasUnsavedChanges) pendingLeave = null
    }
}

// The default instance keeps previews, tests, and compositions outside the app shell working.
val LocalUnsavedChanges = staticCompositionLocalOf { UnsavedChangesState() }

/**
 * For an editor: marks it as having unsaved changes while [dirty], asks before system back
 * leaves it then, and returns [onBack] asking the same, for its top bar. With unsaved changes,
 * both ask only while the editor is resumed, the current page, not while a page opened from it
 * is pushing in, and do nothing while a write is in flight (the editor's save), which leaves the
 * editor itself when it's done.
 *
 * @param untilDisposed for an editor outside the back stack, such as onboarding's: kept while
 *   composed, rather than while its back-stack entry lives.
 */
@Composable
fun unsavedChangesBack(
    dirty: Boolean,
    onBack: () -> Unit,
    untilDisposed: Boolean = false,
): () -> Unit {
    val state = LocalUnsavedChanges.current
    val navigationLock = LocalNavigationLock.current
    val entry = LocalLifecycleOwner.current
    val owner: Any = if (untilDisposed) remember { Any() } else entry
    SideEffect { state.update(owner, dirty) }
    if (untilDisposed) {
        DisposableEffect(state, owner) { onDispose { state.remove(owner) } }
    }
    val counts = dirty && !state.isDiscarded(owner)
    val resumed = entry.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    val latestOnBack by rememberUpdatedState(onBack)
    val latestCounts by rememberUpdatedState(counts)
    val latestResumed by rememberUpdatedState(resumed)
    val back = remember(state, navigationLock) {
        {
            when {
                !latestCounts -> latestOnBack()
                // Mid-transition, the back belongs to the page coming in; saving, the save
                // leaves the editor itself once it's written.
                !latestResumed || navigationLock.isBusy -> Unit
                else -> state.leave { latestOnBack() }
            }
        }
    }
    BackHandler(enabled = counts && resumed) { back() }
    return back
}

/**
 * Asks whether to discard unsaved changes before [UnsavedChangesState.pendingLeave] runs, blurring
 * [hazeState]'s source. Hosted by the app and by onboarding over it; only the last composed host
 * shows it, so onboarding's blurs onboarding rather than the app hidden beneath.
 */
@Composable
fun UnsavedChangesDialog(
    hazeState: HazeState?,
    state: UnsavedChangesState = LocalUnsavedChanges.current,
) {
    val host = remember { Any() }
    DisposableEffect(state, host) {
        state.dialogHosts += host
        onDispose { state.dialogHosts -= host }
    }
    if (state.dialogHosts.lastOrNull() !== host) return
    val action = state.pendingLeave ?: return
    CompositionLocalProvider(LocalChromeHazeState provides hazeState) {
        // One dialog per leave: a Home deep link closes open modals and asks again, and the new
        // dialog must outlive the old one's dismissal rather than be closed with it.
        key(action) { UnsavedChangesDialog(state, action) }
    }
}

@Composable
private fun UnsavedChangesDialog(state: UnsavedChangesState, action: () -> Unit) {
    HazeAlertDialog(
        onDismissRequest = { state.keepEditing(action) },
        title = { Text(text = stringResource(R.string.unsaved_changes_title)) },
        text = { Text(text = stringResource(R.string.unsaved_changes_confirmation)) },
        confirmButton = {
            TextButton(onClick = state::discard) {
                Text(
                    text = stringResource(R.string.unsaved_changes_discard),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { state.keepEditing(action) }) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}
