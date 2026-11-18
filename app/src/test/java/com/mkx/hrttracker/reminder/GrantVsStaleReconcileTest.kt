package com.mkx.hrttracker.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.HomeE2Estimate
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.ui.settings.SettingsViewModel
import com.mkx.hrttracker.util.AppLockSecurityManager
import com.mkx.hrttracker.util.appLanguageLocale
import com.mkx.hrttracker.widget.WidgetAppearance
import com.mkx.hrttracker.widget.WidgetAppearanceRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A reconcile pass that saw no notification access must not turn reminders off over the grant's
 * own switch-on, which can land between the pass's snapshot and its write. Real
 * SettingsViewModel, ReminderCapabilityReconciler and SettingsRepository on a file DataStore;
 * only the capability checks and the scheduler are faked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantVsStaleReconcileTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val remindersEnabledKey = booleanPreferencesKey("reminders_enabled")
    private val context: Context = mockk()
    private val granted = AtomicBoolean(false)
    private val scheduler: MedicationReminderScheduler = mockk(relaxed = true)
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { appLanguageLocale() } returns Locale.ENGLISH
        mockkStatic("com.mkx.hrttracker.reminder.NotificationPermissionsKt")
        every { canPostNotifications(context) } answers { granted.get() }
        every { canScheduleExactAlarms(context) } returns true
        coEvery { scheduler.rescheduleAll(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        Dispatchers.resetMain()
        unmockkAll()
    }

    /** One read of the store waits at [hold]: the reconciler's getCurrentSettings(). */
    private class GatedDataStore(private val real: DataStore<Preferences>) : DataStore<Preferences> {
        val hold = AtomicReference<CompletableDeferred<Unit>?>(null)
        val held = CompletableDeferred<Unit>()
        override val data: Flow<Preferences> = flow {
            hold.getAndSet(null)?.let { gate ->
                held.complete(Unit)
                gate.await()
            }
            emitAll(real.data)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) =
            real.updateData(transform)
    }

    // One-shot reads: DataStore 1.2.1's data flow, started while a write is between its version
    // bump and its cache update, emits the old value and then drops that write's update.
    private suspend fun DataStore<Preferences>.awaitRemindersOn(timeoutMillis: Long): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            while (data.first()[remindersEnabledKey] != true) delay(10)
            true
        } ?: false

    private fun newStore(): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        val file = tmpFolder.newFile("settings_${scopes.size}.preferences_pb").also { it.delete() }
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    private fun newReconciler(repository: SettingsRepository) = ReminderCapabilityReconciler(
        context = context,
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also(scopes::add),
        settingsRepository = repository,
        medicationReminderScheduler = scheduler,
        diagnosticsLogger = mockk(relaxed = true),
    )

    private fun newViewModel(repository: SettingsRepository): SettingsViewModel {
        val userProfileRepository: UserProfileRepository = mockk { every { observeProfile() } returns flowOf(null) }
        val bloodTestRepository: BloodTestRepository = mockk {
            coEvery { getPanels() } returns emptyList()
            coEvery { preloadActiveCustomAnalytes() } returns emptyList()
        }
        val e2EstimateRepository: E2EstimateRepository = mockk {
            every { latestHome } returns MutableStateFlow<HomeE2Estimate?>(null)
        }
        val appLock: AppLockSecurityManager = mockk(relaxed = true) {
            every { availabilityErrorMessageRes() } returns null
        }
        val backupExportService: BackupExportService = mockk {
            coEvery { discardAbandonedPreparedBackups() } just Runs
        }
        val widgetAppearanceRepository: WidgetAppearanceRepository = mockk {
            every { effectiveFor(null) } returns flowOf(WidgetAppearance.Default)
        }
        return SettingsViewModel(
            applicationContext = context,
            settingsRepository = repository,
            userProfileRepository = userProfileRepository,
            bloodTestRepository = bloodTestRepository,
            appLockSecurityManager = appLock,
            medicationReminderScheduler = scheduler,
            backupExportService = backupExportService,
            backupRestoreService = mockk(),
            externalImportService = mockk(),
            diagnosticsExportService = mockk(),
            widgetAppearanceRepository = widgetAppearanceRepository,
            e2EstimateRepository = e2EstimateRepository,
            weightSyncCoordinator = mockk(relaxed = true),
            healthConnectCapabilityReconciler = mockk(relaxed = true),
        )
    }

    // The pass is held in its settings read while the grant lands; it then sees access in its write.
    @Test
    fun aDeniedPassStillInItsSettingsRead_whenTheGrantIsStored_leavesRemindersOn() = runBlocking {
        val real = newStore()
        val store = GatedDataStore(real)
        val repository = SettingsRepository(store)
        repository.setRemindersEnabled(false)
        val reconciler = newReconciler(repository)
        val viewModel = newViewModel(repository)
        // settingsState's own collection of the store has started: the hold below is the pass's.
        withTimeout(5_000) { repository.lastRunAppLanguageTag() }

        // The first pass of the process, from MainActivity.onResume: sees no access, then reads.
        val gate = CompletableDeferred<Unit>().also(store.hold::set)
        val stalePass = launch(Dispatchers.Default) { reconciler.reconcile("main_activity_on_resume") }
        withTimeout(5_000) { store.held.await() }
        assertFalse(reconciler.state.value.hasNotificationAccess)

        // SettingsScreen.kt:275-279, the launcher's callback for a grant.
        granted.set(true)
        reconciler.requestReconcile("settings_notification_permission_result")
        viewModel.setRemindersEnabled(true)
        assertTrue("the grant's write landed", real.awaitRemindersOn(5_000))

        gate.complete(Unit)
        stalePass.join()
        reconciler.reconcile("drain") // FIFO mutex: behind the queued grant-result pass

        assertTrue(reconciler.state.value.hasNotificationAccess)
        assertEquals(
            "access is granted and the user switched reminders on",
            true,
            real.data.first()[remindersEnabledKey],
        )
    }

    // Control: the same steps with the first pass done before the grant.
    @Test
    fun aDeniedPassThatFinishedBeforeTheGrant_leavesRemindersOn() = runBlocking {
        val real = newStore()
        val repository = SettingsRepository(real)
        repository.setRemindersEnabled(false)
        val reconciler = newReconciler(repository)
        val viewModel = newViewModel(repository)

        reconciler.reconcile("main_activity_on_resume")
        granted.set(true)
        reconciler.requestReconcile("settings_notification_permission_result")
        viewModel.setRemindersEnabled(true)
        assertTrue("the grant's write landed", real.awaitRemindersOn(5_000))
        reconciler.reconcile("drain")

        assertEquals(true, real.data.first()[remindersEnabledKey])
    }
}
