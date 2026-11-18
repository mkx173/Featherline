package com.mkx.hrttracker

import android.app.UiModeManager
import android.os.Build
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HrtTrackerApplicationTest {
    @Test
    fun followAppLanguage_reregistersTheChannelAndRepostsTheReminders_onEachChangeOnly() = runTest {
        val notificationManager = mockk<ReminderNotificationManager>(relaxed = true)
        val scheduler = mockk<MedicationReminderScheduler>(relaxed = true)

        // The start language, then the picker or Android's setting changing it.
        followAppLanguage(
            languages = flowOf(
                AppLanguageOption.ENGLISH,
                AppLanguageOption.ENGLISH,
                AppLanguageOption.SIMPLIFIED_CHINESE,
            ),
            // The last run stored the start language, in the form AppCompat gives.
            lastLanguageTag = { "en-US,zh-Hans-CN" },
            reminderNotificationManager = notificationManager,
            medicationReminderScheduler = { scheduler },
            diagnosticsLogger = mockk(relaxed = true),
        )

        verify(exactly = 1) { notificationManager.createNotificationChannel(any()) }
        verify { notificationManager.createNotificationChannel(AppLanguageOption.SIMPLIFIED_CHINESE.languageTag) }
        coVerify(exactly = 1) { scheduler.refreshDoseReminderNotifications() }
    }

    @Test
    fun followAppLanguage_repostsTheReminders_whenTheAppStartsInALanguageChangedWhileItWasDead() = runTest {
        val notificationManager = mockk<ReminderNotificationManager>(relaxed = true)
        val scheduler = mockk<MedicationReminderScheduler>(relaxed = true)

        followAppLanguage(
            languages = flowOf(AppLanguageOption.CANTONESE),
            lastLanguageTag = { "zh-Hans" },
            reminderNotificationManager = notificationManager,
            medicationReminderScheduler = { scheduler },
            diagnosticsLogger = mockk(relaxed = true),
        )

        verify(exactly = 1) { notificationManager.createNotificationChannel(AppLanguageOption.CANTONESE.languageTag) }
        coVerify(exactly = 1) { scheduler.refreshDoseReminderNotifications() }
    }

    @Test
    fun followAppLanguage_skipsTheStartLanguage_withNoLastOne() = runTest {
        val notificationManager = mockk<ReminderNotificationManager>(relaxed = true)
        val scheduler = mockk<MedicationReminderScheduler>(relaxed = true)

        followAppLanguage(
            languages = flowOf(AppLanguageOption.CANTONESE),
            lastLanguageTag = { null },
            reminderNotificationManager = notificationManager,
            medicationReminderScheduler = { scheduler },
            diagnosticsLogger = mockk(relaxed = true),
        )

        verify(exactly = 0) { notificationManager.createNotificationChannel(any()) }
        coVerify(exactly = 0) { scheduler.refreshDoseReminderNotifications() }
    }

    @Test
    fun onAppForeground_rereadsTheAppLanguage() {
        // Android's per-app setting can change it with no configuration change.
        val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
            every { isAppLanguageKnown } returns true
        }

        onAppForeground(mockk(relaxed = true), settingsRepository)

        verify(exactly = 1) { settingsRepository.refreshAppLanguageOption() }
    }

    @Test
    fun applyApplicationNightModeIfAvailable_belowS_doesNotCallApi31Method() {
        val uiModeManager = mockk<UiModeManager>(relaxed = true)

        applyApplicationNightModeIfAvailable(
            uiModeManager = uiModeManager,
            applicationNightMode = UiModeManager.MODE_NIGHT_YES,
            sdkInt = 30,
        )

        verify(exactly = 0) { uiModeManager.setApplicationNightMode(any()) }
    }

    @Test
    fun applyApplicationNightModeIfAvailable_fromS_callsApi31Method() {
        val uiModeManager = mockk<UiModeManager>(relaxed = true)

        applyApplicationNightModeIfAvailable(
            uiModeManager = uiModeManager,
            applicationNightMode = UiModeManager.MODE_NIGHT_YES,
            sdkInt = Build.VERSION_CODES.S,
        )

        verify(exactly = 1) {
            uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
        }
    }
}
