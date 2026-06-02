package com.mkx.hrttracker.util

import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Test
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
