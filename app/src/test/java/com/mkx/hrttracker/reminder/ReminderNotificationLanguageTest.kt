package com.mkx.hrttracker.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

/**
 * A process an alarm woke: no activity, so AppCompat reports no app language. On Android 12 the
 * application context is in the system's, and the reminder follows the stored app language.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ReminderNotificationLanguageTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val settingsRepository: SettingsRepository = mockk {
        every { appLanguageTag() } returns "zh-Hans"
    }
    private val manager = ReminderNotificationManager(context, settingsRepository)

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun aReminderPostedWithNoActivity_isInTheStoredAppLanguage() {
        val chinese = context.localizedTo(Locale.forLanguageTag("zh-Hans"))
        assertNotEquals(
            context.getString(R.string.reminder_notification_single_title),
            chinese.getString(R.string.reminder_notification_single_title),
        )

        manager.showDoseReminderNotification(bundle)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val posted = notificationManager.activeNotifications.single().notification
        assertEquals(
            chinese.getString(R.string.reminder_notification_single_title),
            posted.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString(),
        )
        assertEquals(
            chinese.getString(R.string.reminder_notification_channel_name),
            notificationManager.getNotificationChannel(REMINDER_CHANNEL_ID).name.toString(),
        )
    }

    /**
     * From API 33 the framework keeps the application context in the app language (here
     * zh-Hans, as qualifiers), so a tag stored before the user changed it in system settings
     * (here en) is ignored. On SDK 34, which the other Robolectric tests load already: one more
     * SDK's sandbox runs the whole suite out of its heap.
     */
    @Test
    @Config(sdk = [34], qualifiers = "b+zh+Hans")
    fun fromApi33_aReminderPostedWithNoActivity_followsTheApplicationContext() = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { temporaryFolder.newFile("settings.preferences_pb") },
        )
        dataStore.edit { preferences -> preferences[stringPreferencesKey("app_language_tag")] = "en" }
        val settings = SettingsRepository(dataStore)
        settings.getCurrentSettings()

        assertNull(settings.appLanguageTag())
        ReminderNotificationManager(context, settings).showDoseReminderNotification(bundle)

        val posted = context.getSystemService(NotificationManager::class.java).activeNotifications.single()
        assertEquals(
            context.getString(R.string.reminder_notification_single_title),
            posted.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString(),
        )
        assertNotEquals(
            context.localizedTo(Locale.ENGLISH).getString(R.string.reminder_notification_single_title),
            context.getString(R.string.reminder_notification_single_title),
        )
    }

    private val bundle = MedicationReminderBundle(
        scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
        items = listOf(
            MedicationReminderBundleItem(
                slot = MedicationReminderSlot(
                    groupUuid = UUID.fromString("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"),
                    scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
                    scheduleTimeUuid = UUID.fromString("9f8e7d6c-5b4a-3928-1706-050403020100"),
                ),
                groupName = "Estradiol",
                medications = listOf(testMedicationGroupMedication()),
            )
        ),
    )

    private fun Context.localizedTo(locale: Locale): Context =
        createConfigurationContext(Configuration(resources.configuration).apply { setLocale(locale) })
}
