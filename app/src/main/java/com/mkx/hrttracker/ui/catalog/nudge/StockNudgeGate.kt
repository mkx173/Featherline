package com.mkx.hrttracker.ui.catalog.nudge

import com.mkx.hrttracker.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Number of explicit X-dismissals after which the nudge auto-disables. */
const val STOCK_NUDGE_DISMISS_LIMIT = 3

/**
 * Owns the stock-tracking nudge's enable flag and the dismiss-threshold policy.
 * Only explicit X-dismissals (not the 5s timeout, not tapping the action) reach
 * [onDismissedViaX]; the 3rd one auto-disables the nudge.
 */
@Singleton
class StockNudgeGate @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    val enabled: Flow<Boolean> = settingsRepository.stockNudgeEnabledFlow

    /** Records an X-dismissal. Returns true iff this dismissal just disabled the nudge. */
    suspend fun onDismissedViaX(): Boolean {
        val count = settingsRepository.incrementStockNudgeDismissCount()
        if (count == STOCK_NUDGE_DISMISS_LIMIT) {
            settingsRepository.setStockNudgeEnabled(false)
            return true
        }
        return false
    }

    suspend fun setEnabled(enabled: Boolean) {
        settingsRepository.setStockNudgeEnabled(enabled)
    }
}
