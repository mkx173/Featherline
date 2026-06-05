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

// In-app post-log stock warnings are rendered by the NavHost snackbar. Manual
// logging still produces Single in practice, but batch-add can produce Many, so
// these helpers accept the full sealed warning type.
// Single deep-links to that medicine's detail page (where stock is managed);
// Many routes to the medicines list. Destinations are rooted under the caller's
// current top-level tab so the highlighted tab stays put and the back stack
// doesn't accumulate cross-tab entries.
internal fun postLogStockWarningDestination(
    warning: PostLogStockWarning,
    topLevelParentRoute: String,
): String {
    return when (warning) {
        is PostLogStockWarning.Single -> Screen.MedicineDetail.createRoute(
            medicineId = warning.medicine.uuid.toString(),
            topLevelParentRoute = topLevelParentRoute,
        )
        is PostLogStockWarning.Many -> Screen.Medicines.createRoute(
            topLevelParentRoute = topLevelParentRoute,
        )
    }
}

internal fun postLogStockWarningSnackbarMessage(
    warning: PostLogStockWarning,
    context: Context,
): String {
    return when (warning) {
        is PostLogStockWarning.Single -> {
            val displayName = medicineDisplayName(warning.medicine, context)
            when (warning.state) {
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
        is PostLogStockWarning.Many -> context.resources.getQuantityString(
            R.plurals.stock_toast_many_attention,
            warning.count,
            warning.count,
        )
    }
}

private fun unsupportedPostLogStockWarningState(state: MedicineStockState): Nothing {
    error("Unsupported post-log stock warning state: $state")
}
