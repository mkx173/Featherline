package com.mkx.hrttracker.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
 * A leave names the editors it would leave, and asks only while one of them has unsaved changes:
 * once none has, such as when the one asking saves and finishes, it is dropped. A discard that
 * navigates marks those editors, whose ViewModels live on, still reporting their edits, while they
 * animate out: they no longer count or ask. Any other editor, or all of them when the leave
 * didn't navigate, still asks.
 */
@Stable
@ActivityRetainedScoped
class UnsavedChangesState @Inject constructor() {
    private val dirtyEditors = mutableStateMapOf<Any, Boolean>()
    private val discardedEditors = mutableStateMapOf<Any, Unit>()

    // Hosted in the NavHost's back stack, rather than outside it like onboarding's.
    private val backStackEditors = mutableStateMapOf<Any, Unit>()

    // The composed dialog hosts, the last one showing the dialog: onboarding's over the app's.
    internal val dialogHosts = mutableStateListOf<Any>()

    /** A leave from [editors], navigating by [action], which returns whether it did. */
    class Leave internal constructor(internal val editors: Set<Any>, internal val action: () -> Boolean)

    /** The leave [UnsavedChangesDialog] asks about, null while none waits. */
    var pendingLeave: Leave? by mutableStateOf(null)
        private set

    val hasUnsavedChanges: Boolean
        get() = dirtyEditors.values.any { it }

    /** Whether a pop to the top-level pager would lose edits: the back stack's, not onboarding's. */
    val backStackHasUnsavedChanges: Boolean
        get() = backStackEditors.keys.any(::asks)

    /** Reports [editor]'s [dirty] edits until the ViewModel is cleared. */
    fun track(editor: ViewModel, dirty: StateFlow<Boolean>) {
        editor.viewModelScope.launch { dirty.collect { update(editor, it) } }
        editor.addCloseable { remove(editor) }
    }

    /**
     * Runs [action], leaving [editors], at once, or once the user discards their unsaved changes,
     * returning the leave that waits for it. [action] returns whether it navigated, which it can't
     * mid-transition.
     */
    fun leave(editors: Set<Any>, action: () -> Boolean): Leave? {
        if (editors.none(::asks)) {
            action()
            return null
        }
        return Leave(editors, action).also { pendingLeave = it }
    }

    /** A pop to the top-level pager, which leaves every editor in the back stack. */
    fun leaveBackStack(action: () -> Boolean): Leave? = leave(backStackEditors.keys.toSet(), action)

    /** Drops [leave], unless another has replaced it since its dialog showed. */
    fun keepEditing(leave: Leave) {
        if (pendingLeave === leave) pendingLeave = null
    }

    /** Drops the waiting leave, whose navigation goes with the composition that asked. */
    fun cancelLeave() {
        pendingLeave = null
    }

    fun discard() {
        val leave = pendingLeave ?: return
        pendingLeave = null
        if (leave.action()) leave.editors.forEach(::stopTracking)
    }

    internal fun asks(editor: Any): Boolean = dirtyEditors[editor] == true

    internal fun hostInBackStack(editor: Any) {
        backStackEditors[editor] = Unit
    }

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
        backStackEditors.remove(editor)
        dropLeaveWithNothingToDiscard()
    }

    private fun dropLeaveWithNothingToDiscard() {
        if (pendingLeave?.editors?.none(::asks) == true) pendingLeave = null
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
    onBack: () -> Boolean,
    untilDisposed: Boolean = false,
): () -> Unit {
    val state = LocalUnsavedChanges.current
    val navigationLock = LocalNavigationLock.current
    if (!untilDisposed) {
        SideEffect { state.hostInBackStack(editor) }
    } else {
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
    val back = remember<() -> Unit>(state, navigationLock) {
        {
            when {
                !latestCounts -> latestOnBack()
                // Mid-transition, the back belongs to the page coming in; saving, the save
                // leaves the editor itself once it's written.
                !latestResumed || navigationLock.isBusy -> Unit
                else -> state.leave(setOf(editor)) { latestOnBack() }
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
    val leave = state.pendingLeave ?: return
    CompositionLocalProvider(LocalChromeHazeState provides hazeState) {
        // One dialog per leave: a Home deep link closes open modals and asks again, and the new
        // dialog must outlive the old one's dismissal rather than be closed with it.
        key(leave) { UnsavedChangesDialog(state, leave) }
    }
}

@Composable
private fun UnsavedChangesDialog(state: UnsavedChangesState, leave: UnsavedChangesState.Leave) {
    HazeAlertDialog(
        onDismissRequest = { state.keepEditing(leave) },
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
            TextButton(onClick = { state.keepEditing(leave) }) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}
