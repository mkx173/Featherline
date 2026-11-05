package com.mkx.hrttracker.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dev.chrisbanes.haze.HazeState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Whether an editor has changes it would lose, and the leave waiting on the user to discard them.
 * Each editor's ViewModel reports its own edits for as long as it lives ([track]), so an editor
 * that isn't composed, under a page opened from it (the plan editor's medicine picker), still
 * asks when that page leaves for a tab, also after the activity is recreated.
 *
 * A leave waits only while there is something to discard: once no editor has unsaved changes,
 * such as when the one asking saves and finishes, it is dropped. A discard that leaves marks the
 * editors it left, whose ViewModels live on, still reporting their edits, while they animate out:
 * they no longer count or ask. A leave that didn't navigate marks nothing.
 */
@Stable
@ActivityRetainedScoped
class UnsavedChangesState @Inject constructor() {
    private val dirtyEditors = mutableStateMapOf<Any, Boolean>()
    private val discardedEditors = mutableStateMapOf<Any, Unit>()

    // The composed dialog hosts, the last one showing the dialog: onboarding's over the app's.
    internal val dialogHosts = mutableStateListOf<Any>()

    /** The leave [UnsavedChangesDialog] asks about, null while none waits. */
    var pendingLeave: (() -> Boolean)? by mutableStateOf(null)
        private set

    val hasUnsavedChanges: Boolean
        get() = dirtyEditors.values.any { it }

    /** Reports [editor]'s [dirty] edits until the ViewModel is cleared. */
    fun track(editor: ViewModel, dirty: StateFlow<Boolean>) {
        editor.viewModelScope.launch { dirty.collect { update(editor, it) } }
        editor.addCloseable { remove(editor) }
    }

    /**
     * Runs [action] at once, or once the user discards the unsaved changes. [action] returns
     * whether it left, which a leave that can't navigate yet (mid-transition) doesn't.
     */
    fun leave(action: () -> Boolean) {
        if (hasUnsavedChanges) pendingLeave = action else action()
    }

    /** Drops [action], unless another leave has replaced it since its dialog showed. */
    fun keepEditing(action: () -> Boolean) {
        if (pendingLeave === action) pendingLeave = null
    }

    /** Drops the waiting leave, whose navigation goes with the composition that asked. */
    fun cancelLeave() {
        pendingLeave = null
    }

    fun discard() {
        val action = pendingLeave ?: return
        pendingLeave = null
        if (!action()) return
        dirtyEditors.filterValues { dirty -> dirty }.keys.forEach(::stopTracking)
    }

    internal fun asks(editor: Any): Boolean = dirtyEditors[editor] == true

    /** [editor]'s edits no longer count, though it lives on. */
    internal fun stopTracking(editor: Any) {
        discardedEditors[editor] = Unit
        dirtyEditors[editor] = false
        dropLeaveWithNothingToDiscard()
    }

    internal fun update(editor: Any, dirty: Boolean) {
        if (editor in discardedEditors) return
        dirtyEditors[editor] = dirty
        dropLeaveWithNothingToDiscard()
    }

    internal fun remove(editor: Any) {
        dirtyEditors.remove(editor)
        discardedEditors.remove(editor)
        dropLeaveWithNothingToDiscard()
    }

    private fun dropLeaveWithNothingToDiscard() {
        if (!hasUnsavedChanges) pendingLeave = null
    }
}

// The default instance keeps previews, tests, and compositions outside the app shell working.
val LocalUnsavedChanges = staticCompositionLocalOf { UnsavedChangesState() }

/**
 * For an editor whose ViewModel [UnsavedChangesState.track]s it: asks before system back leaves
 * it while it has unsaved changes, and returns [onBack] asking the same, for its top bar. Both
 * ask only while the editor is resumed, the current page. Mid-transition, as the medicine picker
 * pops back to it, system back does nothing rather than reach the NavHost, which would pop the
 * editor without asking; nor does either while a write is in flight (the editor's save), which
 * leaves the editor itself when it's done.
 *
 * @param untilDisposed for an editor outside the back stack, such as onboarding's, whose
 *   ViewModel outlives it: its edits stop counting once it leaves the composition.
 */
@Composable
fun unsavedChangesBack(
    editor: ViewModel,
    onBack: () -> Unit,
    untilDisposed: Boolean = false,
): () -> Unit {
    val state = LocalUnsavedChanges.current
    val navigationLock = LocalNavigationLock.current
    if (untilDisposed) {
        val activity = LocalActivity.current
        DisposableEffect(state, editor) {
            onDispose {
                // Recreated, the activity composes the same editor again.
                if (activity?.isChangingConfigurations != true) state.stopTracking(editor)
            }
        }
    }
    val counts = state.asks(editor)
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
        .isAtLeast(Lifecycle.State.RESUMED)
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
                else -> state.leave {
                    latestOnBack()
                    true
                }
            }
        }
    }
    BackHandler(enabled = counts) { back() }
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
private fun UnsavedChangesDialog(state: UnsavedChangesState, action: () -> Boolean) {
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
