package com.mkx.hrttracker.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import com.mkx.hrttracker.R

/**
 * Whether an editor in the back stack has changes it would lose, and the leave waiting on the
 * user to discard them. Kept per back-stack entry, not per composition, so an editor under a page
 * opened from it (the plan editor's medicine picker) still asks when that page leaves for a tab.
 */
@Stable
class UnsavedChangesState {
    private val dirtyEntries = mutableStateMapOf<LifecycleOwner, Boolean>()

    /** The leave [UnsavedChangesDialog] asks about, null while none waits. */
    var pendingLeave: (() -> Unit)? by mutableStateOf(null)
        private set

    val hasUnsavedChanges: Boolean
        get() = dirtyEntries.values.any { it }

    /** Runs [action] at once, or once the user discards the unsaved changes. */
    fun leave(action: () -> Unit) {
        if (hasUnsavedChanges) pendingLeave = action else action()
    }

    fun keepEditing() {
        pendingLeave = null
    }

    fun discard() {
        val action = pendingLeave
        pendingLeave = null
        action?.invoke()
    }

    internal fun update(entry: LifecycleOwner, dirty: Boolean) {
        if (entry !in dirtyEntries) {
            // Dropped with its entry, whether or not the editor is composed by then.
            entry.lifecycle.addObserver(
                object : LifecycleEventObserver {
                    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                        if (event == Lifecycle.Event.ON_DESTROY) {
                            source.lifecycle.removeObserver(this)
                            dirtyEntries.remove(entry)
                        }
                    }
                }
            )
        }
        dirtyEntries[entry] = dirty
    }
}

// The default instance keeps previews, tests, and compositions outside the app shell working.
val LocalUnsavedChanges = staticCompositionLocalOf { UnsavedChangesState() }

/**
 * For an editor's route: marks it as having unsaved changes while [dirty], asks before system
 * back leaves it then, and returns [onBack] asking the same, for its top bar.
 */
@Composable
fun unsavedChangesBack(dirty: Boolean, onBack: () -> Unit): () -> Unit {
    val state = LocalUnsavedChanges.current
    val entry = LocalLifecycleOwner.current
    SideEffect { state.update(entry, dirty) }
    val latestOnBack by rememberUpdatedState(onBack)
    BackHandler(enabled = dirty) { state.leave { latestOnBack() } }
    return remember(state) { { state.leave { latestOnBack() } } }
}

/** Asks whether to discard unsaved changes before [UnsavedChangesState.pendingLeave] runs. */
@Composable
fun UnsavedChangesDialog(state: UnsavedChangesState = LocalUnsavedChanges.current) {
    if (state.pendingLeave == null) return
    HazeAlertDialog(
        onDismissRequest = state::keepEditing,
        title = { Text(text = stringResource(R.string.unsaved_changes_title)) },
        text = { Text(text = stringResource(R.string.unsaved_changes_message)) },
        confirmButton = {
            TextButton(onClick = state::discard) {
                Text(
                    text = stringResource(R.string.unsaved_changes_discard),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = state::keepEditing) {
                Text(text = stringResource(R.string.unsaved_changes_keep_editing))
            }
        },
    )
}
