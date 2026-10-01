package com.mkx.hrttracker.testing

import com.mkx.hrttracker.data.backup.BackupCrypto
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.widget.WidgetEntryPoint
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Fixture access for instrumented E2E tests; all bindings are production bindings. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface E2eEntryPoint : WidgetEntryPoint {
    fun databaseHolder(): DatabaseHolder
    fun medicineRepository(): MedicineRepository
    fun reminderScheduler(): MedicationReminderScheduler
    fun backupExportService(): BackupExportService
    fun backupRestoreService(): BackupRestoreService
    fun backupCrypto(): BackupCrypto
}
