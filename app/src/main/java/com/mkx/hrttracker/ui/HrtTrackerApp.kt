package com.mkx.hrttracker.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.reminder.PostLogStockWarning
import com.mkx.hrttracker.ui.navigation.HrtTrackerNavHost
import com.mkx.hrttracker.ui.navigation.Screen
import com.mkx.hrttracker.util.medicineDisplayName
import kotlinx.coroutines.launch

@Composable
fun HrtTrackerApp(
    navController: NavHostController,
    homeDeepLinkSignal: Int,
    highlightEffectsEnabled: Boolean,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        HrtTrackerNavHost(
            navController = navController,
            homeDeepLinkSignal = homeDeepLinkSignal,
            highlightEffectsEnabled = highlightEffectsEnabled,
            showPostLogStockWarning = { warning ->
                val message = postLogStockWarningSnackbarMessage(warning, context)
                val actionLabel = context.getString(R.string.stock_snackbar_action_view)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = actionLabel,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        navController.navigate(postLogStockWarningDestination(warning)) {
                            launchSingleTop = true
                        }
                    }
                }
            },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding(),
        )
    }
}

// Single-medicine warnings deep-link to that medicine's detail page (where
// stock is managed); multi-medicine warnings open the Medicines manager list.
internal fun postLogStockWarningDestination(warning: PostLogStockWarning): String {
    return when (warning) {
        is PostLogStockWarning.Single ->
            Screen.MedicineDetail.createRoute(warning.medicine.uuid.toString())
        is PostLogStockWarning.Many ->
            Screen.Medicines.createRoute()
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
        is PostLogStockWarning.Many -> {
            when (warning.state) {
                MedicineStockState.OUT -> context.resources.getQuantityString(
                    R.plurals.stock_toast_out_multiple,
                    warning.count,
                    warning.count,
                )
                MedicineStockState.IMMINENT -> context.resources.getQuantityString(
                    R.plurals.stock_toast_imminent_multiple,
                    warning.count,
                    warning.count,
                )
                MedicineStockState.USER_LOW -> context.resources.getQuantityString(
                    R.plurals.stock_toast_user_low_multiple,
                    warning.count,
                    warning.count,
                )
                MedicineStockState.HEALTHY,
                MedicineStockState.UNTRACKED,
                MedicineStockState.NO_RUNWAY -> unsupportedPostLogStockWarningState(warning.state)
            }
        }
    }
}

private fun unsupportedPostLogStockWarningState(state: MedicineStockState): Nothing {
    error("Unsupported post-log stock warning state: $state")
}
