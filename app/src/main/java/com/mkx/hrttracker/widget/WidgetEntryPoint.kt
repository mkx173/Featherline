package com.mkx.hrttracker.widget

import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetSnapshotStore(): WidgetSnapshotStore
    fun homeSnapshotRepository(): HomeSnapshotRepository
    fun journalRepository(): JournalRepository
    fun medicationGroupRepository(): MedicationGroupRepository
    fun medicationLogRepository(): MedicationLogRepository
    fun medicineStockRepository(): MedicineStockRepository
    fun settingsRepository(): SettingsRepository
    fun widgetAppearanceRepository(): WidgetAppearanceRepository
    fun reminderNotificationManager(): ReminderNotificationManager
    fun diagnosticsLogger(): AppDiagnosticsLogger

    @AppScope
    fun appScope(): CoroutineScope
}
