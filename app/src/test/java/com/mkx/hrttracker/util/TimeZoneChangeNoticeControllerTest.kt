package com.mkx.hrttracker.util

import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TimeZoneChangeNoticeControllerTest {
    @Test
    fun noticeEmitsWhenCurrentZoneChangesWithoutLifecycleCycle() = runTest {
        val settingsState = MutableStateFlow(SettingsState(lastSeenTimeZoneId = "UTC"))
        val settingsRepository = settingsRepository(settingsState)
        val appTimeSource = FakeAppTimeSource(
            initialMinute = LocalDateTime.of(2026, 4, 25, 12, 0),
            initialZone = ZoneId.of("UTC"),
        )
        val controller = TimeZoneChangeNoticeController(
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
            scope = controllerScope(),
        )

        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.notice.collect {}
        }
        advanceUntilIdle()

        appTimeSource.setCurrentZone(ZoneId.of("Asia/Tokyo"))
        advanceUntilIdle()

        assertEquals(
            TimeZoneChangeNotice(previousZoneId = "UTC", currentZoneId = "Asia/Tokyo"),
            controller.notice.value,
        )
        collector.cancel()
    }

    @Test
    fun dismissSuppressesCurrentChangeAndAcknowledgesCurrentZone() = runTest {
        val settingsState = MutableStateFlow(SettingsState(lastSeenTimeZoneId = "UTC"))
        val settingsRepository = settingsRepository(settingsState)
        val appTimeSource = FakeAppTimeSource(
            initialMinute = LocalDateTime.of(2026, 4, 25, 12, 0),
            initialZone = ZoneId.of("Asia/Tokyo"),
        )
        coEvery { settingsRepository.acknowledgeTimeZone("Asia/Tokyo") } returns Unit
        val controller = TimeZoneChangeNoticeController(
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
            scope = controllerScope(),
        )

        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.notice.collect {}
        }
        advanceUntilIdle()

        assertEquals(
            TimeZoneChangeNotice(previousZoneId = "UTC", currentZoneId = "Asia/Tokyo"),
            controller.notice.value,
        )

        controller.dismiss()
        advanceUntilIdle()

        assertNull(controller.notice.value)
        coVerify(exactly = 1) { settingsRepository.acknowledgeTimeZone("Asia/Tokyo") }
        collector.cancel()
    }

    @Test
    fun foregroundWithNullStoredZoneAcknowledgesWithoutBanner() = runTest {
        val currentZone = ZoneId.systemDefault().id
        val settingsState = MutableStateFlow(SettingsState(lastSeenTimeZoneId = null))
        val settingsRepository = settingsRepository(settingsState)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(lastSeenTimeZoneId = null)
        coEvery { settingsRepository.acknowledgeTimeZone(any()) } returns Unit
        val controller = TimeZoneChangeNoticeController(
            settingsRepository = settingsRepository,
            appTimeSource = FakeAppTimeSource(
                initialMinute = LocalDateTime.of(2026, 4, 25, 12, 0),
                initialZone = ZoneId.systemDefault(),
            ),
            scope = controllerScope(),
        )

        controller.onAppForegrounded()
        advanceUntilIdle()

        assertNull(controller.notice.value)
        coVerify(exactly = 1) { settingsRepository.acknowledgeTimeZone(currentZone) }
    }

    @Test
    fun noticeDoesNotCollectZoneWhenUnsubscribed() = runTest {
        val settingsState = MutableStateFlow(SettingsState(lastSeenTimeZoneId = "UTC"))
        val settingsRepository = settingsRepository(settingsState)
        val appTimeSource = MutableZoneAppTimeSource(ZoneId.of("UTC"))
        val controller = TimeZoneChangeNoticeController(
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
            scope = controllerScope(),
        )

        appTimeSource.mutableCurrentZone.value = ZoneId.of("Asia/Tokyo")
        advanceUntilIdle()

        assertNull(controller.notice.value)
        assertEquals(
            TimeZoneChangeNotice(previousZoneId = "UTC", currentZoneId = "Asia/Tokyo"),
            controller.notice.first { it != null },
        )
    }

    @Test
    fun dismissedChangeShowsAgainWhenARestoreBringsTheSameZonesBack() = runTest {
        val settingsState = MutableStateFlow(SettingsState(lastSeenTimeZoneId = "UTC"))
        val settingsRepository = settingsRepository(settingsState)
        coEvery { settingsRepository.acknowledgeTimeZone(any()) } answers {
            settingsState.value = settingsState.value.copy(lastSeenTimeZoneId = firstArg())
        }
        val controller = TimeZoneChangeNoticeController(
            settingsRepository = settingsRepository,
            appTimeSource = FakeAppTimeSource(
                initialMinute = LocalDateTime.of(2026, 4, 25, 12, 0),
                initialZone = ZoneId.of("Asia/Tokyo"),
            ),
            scope = controllerScope(),
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.notice.collect {}
        }
        controller.dismiss()
        advanceUntilIdle()
        assertNull(controller.notice.value)

        // A backup taken in UTC is restored while the device is still in Tokyo.
        settingsState.value = SettingsState(lastSeenTimeZoneId = "UTC")
        advanceUntilIdle()

        assertEquals(
            "a restore into the dismissed zones must show the banner again",
            TimeZoneChangeNotice(previousZoneId = "UTC", currentZoneId = "Asia/Tokyo"),
            controller.notice.value,
        )
        collector.cancel()
    }

    @Test
    fun dismissHidesOnlyTheDismissedChangeWhileItsAcknowledgementIsInFlight() = runTest {
        val settingsState = MutableStateFlow(SettingsState(lastSeenTimeZoneId = "UTC"))
        val settingsRepository = settingsRepository(settingsState)
        coEvery { settingsRepository.acknowledgeTimeZone(any()) } coAnswers { awaitCancellation() }
        val appTimeSource = FakeAppTimeSource(
            initialMinute = LocalDateTime.of(2026, 4, 25, 12, 0),
            initialZone = ZoneId.of("Asia/Tokyo"),
        )
        val controller = TimeZoneChangeNoticeController(
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
            scope = controllerScope(),
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.notice.collect {}
        }
        controller.dismiss()
        advanceUntilIdle()
        assertNull(controller.notice.value)

        appTimeSource.setCurrentZone(ZoneId.of("Europe/London"))
        advanceUntilIdle()

        assertEquals(
            "a dismissed UTC -> Tokyo change must not hide UTC -> London",
            TimeZoneChangeNotice(previousZoneId = "UTC", currentZoneId = "Europe/London"),
            controller.notice.value,
        )
        collector.cancel()
    }

    @Test
    fun failedDismissShowsTheBannerAgainWithoutCrashing() = runTest {
        val uncaught = mutableListOf<Throwable>()
        val scope = appLikeScope(uncaught)
        val settingsState = MutableStateFlow(SettingsState(lastSeenTimeZoneId = "UTC"))
        val settingsRepository = settingsRepository(settingsState)
        coEvery { settingsRepository.acknowledgeTimeZone(any()) } throws IOException("disk full")
        val controller = TimeZoneChangeNoticeController(
            settingsRepository = settingsRepository,
            appTimeSource = FakeAppTimeSource(
                initialMinute = LocalDateTime.of(2026, 4, 25, 12, 0),
                initialZone = ZoneId.of("Asia/Tokyo"),
            ),
            scope = scope,
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.notice.collect {}
        }

        controller.dismiss()
        advanceUntilIdle()

        assertTrue("a settings write failure must not reach the app scope: $uncaught", uncaught.isEmpty())
        assertEquals(
            "nothing was stored, so the banner must come back",
            TimeZoneChangeNotice(previousZoneId = "UTC", currentZoneId = "Asia/Tokyo"),
            controller.notice.value,
        )
        collector.cancel()
        scope.cancel()
    }

    @Test
    fun failedForegroundReadDoesNotCrash() = runTest {
        val uncaught = mutableListOf<Throwable>()
        val scope = appLikeScope(uncaught)
        val settingsRepository = settingsRepository(MutableStateFlow(SettingsState()))
        coEvery { settingsRepository.getCurrentSettings() } throws IOException("disk unreadable")
        val controller = TimeZoneChangeNoticeController(
            settingsRepository = settingsRepository,
            appTimeSource = FakeAppTimeSource(
                initialMinute = LocalDateTime.of(2026, 4, 25, 12, 0),
                initialZone = ZoneId.systemDefault(),
            ),
            scope = scope,
        )

        controller.onAppForegrounded()
        advanceUntilIdle()

        assertTrue("a settings read failure must not reach the app scope: $uncaught", uncaught.isEmpty())
        scope.cancel()
    }

    private fun settingsRepository(
        settingsState: StateFlow<SettingsState>,
    ): SettingsRepository {
        return mockk {
            every { this@mockk.settingsState } returns settingsState
            coEvery { getCurrentSettings() } returns settingsState.value
            coEvery { acknowledgeTimeZone(any()) } returns Unit
        }
    }

    private fun TestScope.controllerScope(): CoroutineScope {
        return CoroutineScope(
            backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
        )
    }

    // AppScope has a SupervisorJob and no handler: whatever reaches the handler here would
    // terminate the process.
    private fun TestScope.appLikeScope(uncaught: MutableList<Throwable>): CoroutineScope {
        return CoroutineScope(
            SupervisorJob() +
                UnconfinedTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, error -> uncaught += error }
        )
    }

    private class MutableZoneAppTimeSource(
        initialZone: ZoneId,
    ) : AppTimeSource {
        private val initialMinute = LocalDateTime.of(2026, 4, 25, 12, 0)
        override val currentSnapshot: StateFlow<AppTimeSnapshot> = MutableStateFlow(
            AppTimeSnapshot(
                minute = initialMinute,
                zone = initialZone,
            )
        )
        override val currentMinute: StateFlow<LocalDateTime> = MutableStateFlow(initialMinute)
        val mutableCurrentZone = MutableStateFlow(initialZone)
        override val currentZone: StateFlow<ZoneId> = mutableCurrentZone

        override fun now(): Instant {
            return initialMinute.atZone(mutableCurrentZone.value).toInstant()
        }

        override fun refresh() = Unit
    }
}
