package com.mkx.hrttracker.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.reminder.PostLogStockWarning
import com.mkx.hrttracker.ui.navigation.HrtTrackerNavHost
import com.mkx.hrttracker.ui.navigation.Screen
import com.mkx.hrttracker.util.medicineDisplayName

@Composable
fun HrtTrackerApp(
    navController: NavHostController,
    homeDeepLinkSignal: Int,
    highlightEffectsEnabled: Boolean,
) {
    // The post-log stock snackbar is hosted inside HrtTrackerNavHost so it sits
    // above the app's bottom navigation bar rather than overlapping it.
    HrtTrackerNavHost(
        navController = navController,
        homeDeepLinkSignal = homeDeepLinkSignal,
        highlightEffectsEnabled = highlightEffectsEnabled,
    )
}

// Only single-medicine warnings reach the snackbar: in-app logging saves one
// medicine at a time, so resolvePostLogStockWarning is always called with a
// single UUID. (Multi-medicine warnings only arise from the reminder action
// path, which renders its own count toasts and never uses these helpers.)
// Deep-link to that medicine's detail page (where stock is managed), rooted
// under the caller's current top-level tab so the highlighted tab stays put
// and the back stack doesn't accumulate cross-tab entries.
internal fun postLogStockWarningDestination(
    warning: PostLogStockWarning.Single,
    topLevelParentRoute: String,
): String {
    return Screen.MedicineDetail.createRoute(
        medicineId = warning.medicine.uuid.toString(),
        topLevelParentRoute = topLevelParentRoute,
    )
}

internal fun postLogStockWarningSnackbarMessage(
    warning: PostLogStockWarning.Single,
    context: Context,
): String {
    val displayName = medicineDisplayName(warning.medicine, context)
    return when (warning.state) {
        MedicineStockState.OUT -> context.getString(
            R.string.stock_toast_out_single,
            displayName,
        )
        MedicineStockState.IMMINENT -> context.getString(
            R.string.stock_toast_imminent_single,
            displayName,
        )
        MedicineStockState.USER_LOW -> context.getString(
            R.string.stock_toast_user_low_single,
            displayName,
        )
        MedicineStockState.HEALTHY,
        MedicineStockState.UNTRACKED,
        MedicineStockState.NO_RUNWAY -> unsupportedPostLogStockWarningState(warning.state)
    }
}

private fun unsupportedPostLogStockWarningState(state: MedicineStockState): Nothing {
    error("Unsupported post-log stock warning state: $state")
}
