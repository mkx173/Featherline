package com.mkx.hrttracker.data.repository

import android.app.KeyguardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.model.settings.SecuritySettingsState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The app's own settings store, through SettingsRepository(context): its file, its corruption
 * handler's database and lock checks, and one store per process for repositories built at once.
 *
 * The store is cached per process (per Robolectric sandbox), so this is the only test that may
 * use it: a test that built it first would leave this one reading that test's store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsRepositoryAppStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aCorruptSettingsFileOverADatabase_comesBackLocked_inOneStoreForEveryRepository() {
        // A phone with a screen lock, so the app lock can authenticate.
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        context.getDatabasePath("hrt_tracker.db").apply { parentFile?.mkdirs() }.writeText("encrypted database")
        File(context.filesDir, "datastore/settings.preferences_pb")
            .apply { parentFile?.mkdirs() }
            .writeBytes(byteArrayOf(0x0A, 0xFF.toByte()))

        // Several repositories built at once, as several entry points could.
        val threads = 8
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        val built = (1..threads).map {
            pool.submit<SettingsRepository> {
                start.await()
                SettingsRepository(context)
            }
        }
        start.countDown()
        val repositories = built.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        runBlocking {
            withTimeout(10_000) {
                // Every read succeeds: two stores on one file would throw "multiple DataStores".
                repositories.forEach { repository ->
                    val security = repository.securitySettingsState.first() as SecuritySettingsState.Loaded
                    assertTrue("the app lock reads on", security.screenLockProtectionEnabled)
                    assertTrue("Hide screen content reads on", security.hideScreenContentEnabled)
                }
                repositories.first().setOnboardingCompleted(true)
                assertEquals(true, repositories.last().onboardingCompleted.first { it })
            }
        }
    }
}
