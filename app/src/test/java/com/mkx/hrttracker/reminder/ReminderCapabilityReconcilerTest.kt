package com.mkx.hrttracker.reminder

import android.content.Context
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderCapabilityReconcilerTest {
    private val context: Context = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler = mockk()
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private val appScope = CoroutineScope(UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        mockkStatic("com.mkx.hrttracker.reminder.NotificationPermissionsKt")
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } just Runs
        coEvery { medicationReminderSnoozeScheduler.rescheduleAll(any()) } just Runs
        coEvery { medicationReminderSnoozeScheduler.clearAllSnoozes() } just Runs
        coEvery { settingsRepository.setRemindersEnabled(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun seedsInitialStateFromCurrentCapabilities() {
        givenCapabilities(notification = false, exactAlarm = false)

        val reconciler = createReconciler()

        assertEquals(
            ReminderCapabilityState(hasNotificationAccess = false, hasExactAlarmAccess = false),
            reconciler.state.value,
        )
    }

    @Test
    fun reconcile_publishesCurrentCapabilityState() = runTest {
        givenCapabilities(notification = true, exactAlarm = true)
        val reconciler = createReconciler()
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = true)

        givenCapabilities(notification = false, exactAlarm = true)
        reconciler.reconcile()

        assertEquals(
            ReminderCapabilityState(hasNotificationAccess = false, hasExactAlarmAccess = true),
            reconciler.state.value,
        )
    }

    @Test
    fun reconcile_disablesMasterRemindersWhenNotificationAccessLost() = runTest {
        givenCapabilities(notification = false, exactAlarm = true)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = true)

        val reconciler = createReconciler()
        reconciler.reconcile()

        coVerify(exactly = 1) { settingsRepository.setRemindersEnabled(false) }
        coVerify(exactly = 1) { medicationReminderSnoozeScheduler.clearAllSnoozes() }
    }

    @Test
    fun reconcile_doesNotDisableMasterRemindersWhenAlreadyDisabled() = runTest {
        givenCapabilities(notification = false, exactAlarm = true)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = false)

        val reconciler = createReconciler()
        reconciler.reconcile()

        coVerify(exactly = 0) { settingsRepository.setRemindersEnabled(any()) }
        coVerify(exactly = 0) { medicationReminderSnoozeScheduler.clearAllSnoozes() }
    }

    @Test
    fun reconcile_doesNotAutoEnableRemindersWhenAccessReturns() = runTest {
        givenCapabilities(notification = true, exactAlarm = true)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = false)

        val reconciler = createReconciler()
        reconciler.reconcile()

        coVerify(exactly = 0) { settingsRepository.setRemindersEnabled(any()) }
    }

    @Test
    fun reconcile_alwaysReschedulesEvenWhenCapabilityUnchanged() = runTest {
        givenCapabilities(notification = true, exactAlarm = true)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = true)

        val reconciler = createReconciler()
        reconciler.reconcile()
        reconciler.reconcile()

        coVerify(exactly = 2) { medicationReminderScheduler.rescheduleAll(any()) }
        coVerify(exactly = 2) { medicationReminderSnoozeScheduler.rescheduleAll(any()) }
    }

    @Test
    fun reconcile_persistsRemindersDisabledBeforeRescheduling() = runTest {
        givenCapabilities(notification = false, exactAlarm = true)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = true)

        val reconciler = createReconciler()
        reconciler.reconcile()

        coVerifyOrder {
            settingsRepository.setRemindersEnabled(false)
            medicationReminderSnoozeScheduler.clearAllSnoozes()
            medicationReminderScheduler.rescheduleAll(any())
            medicationReminderSnoozeScheduler.rescheduleAll(any())
        }
    }

    @Test
    fun reconcile_doesNotClearSnoozesWhenAccessIsGranted() = runTest {
        givenCapabilities(notification = true, exactAlarm = false)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = true)

        val reconciler = createReconciler()
        reconciler.reconcile()

        coVerify(exactly = 0) { medicationReminderSnoozeScheduler.clearAllSnoozes() }
    }

    @Test
    fun reconcile_publishesExactAlarmAccessChange() = runTest {
        givenCapabilities(notification = true, exactAlarm = true)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = true)

        val reconciler = createReconciler()
        assertTrue(reconciler.state.value.hasExactAlarmAccess)

        givenCapabilities(notification = true, exactAlarm = false)
        reconciler.reconcile()

        assertFalse(reconciler.state.value.hasExactAlarmAccess)
        assertTrue(reconciler.state.value.hasNotificationAccess)
    }

    private fun givenCapabilities(notification: Boolean, exactAlarm: Boolean) {
        io.mockk.every { canPostNotifications(context) } returns notification
        io.mockk.every { canScheduleExactAlarms(context) } returns exactAlarm
    }

    private fun createReconciler(): ReminderCapabilityReconciler {
        return ReminderCapabilityReconciler(
            context = context,
            appScope = appScope,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            diagnosticsLogger = diagnosticsLogger,
        )
    }
}
