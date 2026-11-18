package com.mkx.hrttracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.mkx.hrttracker.model.settings.SecuritySettingsState
import com.mkx.hrttracker.readSettingsOrNull
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.appLanguageLocale
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * The settings store's failure modes on the real SettingsRepository over a real Preferences
 * DataStore file, with the app's corruption handler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreFailureModesTest {
    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val storeScopes = mutableListOf<CoroutineScope>()
    private val logger = mockk<AppDiagnosticsLogger>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { appLanguageLocale() } returns Locale.ENGLISH
    }

    @After
    fun tearDown() {
        runBlocking { storeScopes.forEach { scope -> scope.coroutineContext[Job]!!.cancelAndJoin() } }
        unmockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        Dispatchers.resetMain()
    }

    /** Over an existing database, a damaged settings file comes back locked, content hidden. */
    @Test
    fun aCorruptSettingsFile_overADatabase_comesBackWithTheLockAndHideContentOn() = runBlocking {
        val file = corruptSettingsFileWithTheLockOff()

        val settings = SettingsRepository(
            settingsStore(file, databaseExists = true, appLockCanAuthenticate = true),
            logger,
        )
        val after = withTimeout(5_000) { settings.securitySettingsState.first() }

        after as SecuritySettingsState.Loaded
        assertTrue("the app lock reads on", after.screenLockProtectionEnabled)
        assertTrue("Hide screen content reads on", after.hideScreenContentEnabled)
        // Not decided, left as the handler gives them: onboarding counts as not done, and
        // reminders read as their default, on.
        assertEquals(false, settings.onboardingCompleted.first())
        assertEquals(true, settings.getCurrentSettings().remindersEnabled)
    }

    /** On a phone with no screen lock the lock would keep the user out, so only Hide content is on. */
    @Test
    fun aCorruptSettingsFile_overADatabase_onAPhoneThatCantAuthenticate_hidesContentWithoutTheLock() =
        runBlocking {
            val file = corruptSettingsFileWithTheLockOff()

            val settings = SettingsRepository(
                settingsStore(file, databaseExists = true, appLockCanAuthenticate = false),
                logger,
            )
            val after = withTimeout(5_000) { settings.securitySettingsState.first() }
                as SecuritySettingsState.Loaded

            assertFalse("the app lock reads off", after.screenLockProtectionEnabled)
            assertTrue("Hide screen content reads on", after.hideScreenContentEnabled)
        }

    @Test
    fun aCorruptSettingsFile_withNoDatabase_comesBackAsDefaults() = runBlocking {
        val file = corruptSettingsFileWithTheLockOff()

        val settings = SettingsRepository(settingsStore(file, databaseExists = false), logger)
        val after = withTimeout(5_000) { settings.securitySettingsState.first() } as SecuritySettingsState.Loaded

        assertFalse(after.screenLockProtectionEnabled)
        assertFalse(after.hideScreenContentEnabled)
    }

    /**
     * A failed read is retried, not shown as defaults: onboardingCompleted waits for the stored
     * value instead of saying false, and settingsState doesn't stay on its defaults.
     */
    @Test
    fun aFailedRead_isRetried_untilTheStoredSettingsRead() = runBlocking {
        val file = File(tmpFolder.root, "settings.preferences_pb")
        val firstScope = newStoreScope()
        SettingsRepository(settingsStore(file, databaseExists = false, scope = firstScope), logger).run {
            setOnboardingCompleted(true)
            setHideReferenceRanges(true)
        }
        firstScope.coroutineContext[Job]!!.cancelAndJoin()
        // The next start, with a file that can't be opened (a directory in its place).
        val stored = file.readBytes()
        assertTrue(file.delete() && file.mkdir())
        val settings = SettingsRepository(settingsStore(file, databaseExists = true), logger)
        val onboarding = async(Dispatchers.IO) { settings.onboardingCompleted.first() }
        // Past the first two retries (one a second), so a single retry isn't enough.
        delay(2_500)
        assertFalse("answered from a failed read", onboarding.isCompleted)
        verify(atLeast = 2) { logger.warning(any(), "settings_read_failed_retrying", any<IOException>()) }

        // The disk reads again.
        assertTrue(file.delete())
        file.writeBytes(stored)

        assertEquals(true, withTimeout(5_000) { onboarding.await() })
        assertTrue(withTimeout(5_000) { settings.settingsState.first { it.hideReferenceRanges } }.hideReferenceRanges)
    }

    /** What HrtTrackerApplication.onCreate reads with: a failed read is null, not a crash. */
    @Test
    fun aFailedRead_atApplicationStart_isNull() = runBlocking {
        val unreadable = tmpFolder.newFolder("unreadable.preferences_pb")
        val settings = SettingsRepository(settingsStore(unreadable, databaseExists = true), logger)

        assertTrue(runCatching { settings.getCurrentSettings() }.exceptionOrNull() is IOException)
        assertNull(withTimeout(5_000) { readSettingsOrNull(settings, logger) })
    }

    /**
     * Once a read has succeeded the DataStore serves every later read in the process from
     * memory, so the flows a backup export reads after getCurrentSettings() never meet a
     * failed read (review Medium 6, unreachable).
     */
    @Test
    fun afterGetCurrentSettingsSucceeds_theFlowReadsAreServedFromMemory() = runBlocking {
        val file = File(tmpFolder.root, "settings.preferences_pb")
        val settings = SettingsRepository(settingsStore(file, databaseExists = false), logger)
        settings.setOnboardingCompleted(true)
        settings.setPkCalibrationIntroSeen(true)

        withTimeout(5_000) { settings.getCurrentSettings() }
        assertTrue(file.delete() && file.mkdir())

        assertEquals(true, withTimeout(5_000) { settings.onboardingCompleted.first() })
        assertEquals(true, withTimeout(5_000) { settings.pkCalibrationIntroSeen.first() })
    }

    /** A run with the lock and Hide screen content off and onboarding done, then the file damaged. */
    private suspend fun corruptSettingsFileWithTheLockOff(): File {
        val file = File(tmpFolder.root, "settings.preferences_pb")
        val firstScope = newStoreScope()
        SettingsRepository(settingsStore(file, databaseExists = true, scope = firstScope), logger).run {
            setScreenLockProtectionEnabled(false)
            setHideScreenContentEnabled(false)
            setOnboardingCompleted(true)
            setRemindersEnabled(false)
        }
        firstScope.coroutineContext[Job]!!.cancelAndJoin()
        // A length-delimited field cut off in its length.
        file.writeBytes(byteArrayOf(0x0A, 0xFF.toByte()))
        return file
    }

    private fun newStoreScope() = CoroutineScope(Dispatchers.IO + Job()).also(storeScopes::add)

    /** The app's store (SettingsRepository's Context.dataStore), over [file]. */
    private fun settingsStore(
        file: File,
        databaseExists: Boolean,
        appLockCanAuthenticate: Boolean = true,
        scope: CoroutineScope = newStoreScope(),
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = settingsCorruptionHandler({ databaseExists }, { appLockCanAuthenticate }),
        scope = scope,
        produceFile = { file },
    )
}
