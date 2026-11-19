package com.mkx.hrttracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.mkx.hrttracker.util.appLanguageLocale
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** SettingsRepository.turnRemindersOffUnless on a file DataStore. */
class SettingsRepositoryTurnRemindersOffTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val remindersEnabledKey = booleanPreferencesKey("reminders_enabled")
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { appLanguageLocale() } returns Locale.ENGLISH
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        unmockkAll()
    }

    private fun newStore(): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        val file = tmpFolder.newFile("settings_${scopes.size}.preferences_pb").also { it.delete() }
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    // Unset reads as on.
    @Test
    fun anUnsetSwitch_withoutAccess_isTurnedOff() = runBlocking {
        val store = newStore()
        val repository = SettingsRepository(store)

        assertTrue(repository.turnRemindersOffUnless { false })
        assertEquals(false, store.data.first()[remindersEnabledKey])
    }

    @Test
    fun aSwitchOn_withAccess_staysOn() = runBlocking {
        val store = newStore()
        val repository = SettingsRepository(store)
        repository.setRemindersEnabled(true)

        assertFalse(repository.turnRemindersOffUnless { true })
        assertEquals(true, store.data.first()[remindersEnabledKey])
    }

    @Test
    fun aSwitchAlreadyOff_doesNotAskForAccess() = runBlocking {
        val store = newStore()
        val repository = SettingsRepository(store)
        repository.setRemindersEnabled(false)
        val asked = AtomicInteger()

        assertFalse(repository.turnRemindersOffUnless { asked.incrementAndGet(); false })
        assertEquals(0, asked.get())
    }

    // The check is inside the write: a switch-on started while it runs lands after the off.
    @Test
    fun aSwitchOnStartedWhileAccessIsChecked_landsAfterTheOff() = runBlocking {
        val store = newStore()
        val repository = SettingsRepository(store)
        repository.setRemindersEnabled(true)
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        val off = async(Dispatchers.Default) {
            repository.turnRemindersOffUnless {
                inside.countDown()
                release.await(10, TimeUnit.SECONDS)
                false
            }
        }
        assertTrue(inside.await(10, TimeUnit.SECONDS))

        val on = launch(Dispatchers.Default) { repository.setRemindersEnabled(true) }
        delay(300)
        assertTrue("the switch-on waits for the off", on.isActive)
        release.countDown()

        assertTrue(off.await())
        on.join()
        assertEquals(true, store.data.first()[remindersEnabledKey])
    }
}
