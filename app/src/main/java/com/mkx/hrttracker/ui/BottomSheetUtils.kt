package com.mkx.hrttracker.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.WeakHashMap

@OptIn(ExperimentalMaterial3Api::class)
private val bottomSheetHideJobs = WeakHashMap<SheetState, Job>()

@OptIn(ExperimentalMaterial3Api::class)
fun hideBottomSheet(
    scope: CoroutineScope,
    sheetState: SheetState,
    onHidden: () -> Unit
) {
    if (!sheetState.isVisible) {
        onHidden()
        return
    }
    bottomSheetHideJobs[sheetState]?.takeIf(Job::isActive)?.let {
        return
    }
    val hideJob = scope.launch(start = CoroutineStart.LAZY) { sheetState.hide() }
    bottomSheetHideJobs[sheetState] = hideJob
    hideJob.invokeOnCompletion { cause ->
        if (bottomSheetHideJobs[sheetState] == hideJob) {
            bottomSheetHideJobs.remove(sheetState)
        }
        if (cause is CancellationException || !sheetState.isVisible) {
            onHidden()
        }
    }
    hideJob.start()
}
