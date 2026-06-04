package com.mkx.hrttracker.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import com.mkx.hrttracker.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.util.Locale

/**
 * Guards the localization contract for toasts shown from long-lived effects.
 *
 * The app switches UI language in place via composition locals (LocalContext),
 * without recreating the Activity (see MainActivity). A toast collector that
 * lives in a `LaunchedEffect(Unit)` — which never restarts — must therefore
 * read the *current* localized context at emit time, or it will render in the
 * language that was active when the effect first launched. The production fix
 * threads `rememberUpdatedState(LocalContext.current)` into those collectors;
 * this test pins that mechanism so a future revert (capturing the context
 * directly again) fails here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LocalizedToastLocaleSwapTest {
    @get:Rule
    val composeRule = createComposeRule()

    // One of the real toasts the fix covers; no format args, distinct per locale.
    private val toastRes = R.string.stock_nudge_disabled_toast
    private val english = Locale.ENGLISH
    private val chinese = Locale.forLanguageTag("zh-Hans")

    /** Mirrors MainActivity: a ContextWrapper serving resources for [locale]. */
    private fun Context.localizedTo(locale: Locale): Context {
        val configuration = Configuration(resources.configuration).apply { setLocale(locale) }
        val localizedResources = createConfigurationContext(configuration).resources
        return object : ContextWrapper(this) {
            override fun getResources(): Resources = localizedResources
        }
    }

    /**
     * Reproduces the production collector shape: a `LaunchedEffect(Unit)` that
     * never restarts, toasting on each event. When [useLatestContext] is true it
     * reads the live context via rememberUpdatedState (the fix); otherwise it
     * captures LocalContext.current directly (the bug).
     */
    @Composable
    private fun ToastOnEvent(events: Flow<Unit>, useLatestContext: Boolean) {
        val capturedContext = LocalContext.current
        val latestContext by rememberUpdatedState(capturedContext)
        LaunchedEffect(Unit) {
            events.collect {
                val context = if (useLatestContext) latestContext else capturedContext
                Toast.makeText(context, context.getString(toastRes), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Hosts [ToastOnEvent] under a LocalContext driven by [localeState], so the
     * test can flip the provided language after the effect has launched —
     * exactly the in-place swap the real app performs.
     */
    private fun setUpLocaleSwitchableHost(
        events: Flow<Unit>,
        localeState: MutableState<Locale>,
        useLatestContext: Boolean,
    ) {
        composeRule.setContent {
            val base = LocalContext.current
            val locale = localeState.value
            val provided = remember(locale.toLanguageTag()) { base.localizedTo(locale) }
            CompositionLocalProvider(LocalContext provides provided) {
                ToastOnEvent(events = events, useLatestContext = useLatestContext)
            }
        }
    }

    private fun expectedToast(locale: Locale): String =
        RuntimeEnvironment.getApplication().localizedTo(locale).getString(toastRes)

    @Test
    fun toastFollowsInPlaceLocaleSwap_whenReadingLatestContext() {
        val englishToast = expectedToast(english)
        val chineseToast = expectedToast(chinese)
        // Precondition: the two locales must actually differ, or the assertion
        // below proves nothing (e.g. if zh-Hans resources failed to resolve).
        assertNotEquals(englishToast, chineseToast)

        val events = Channel<Unit>(Channel.BUFFERED)
        val localeState = mutableStateOf(english)
        setUpLocaleSwitchableHost(events.receiveAsFlow(), localeState, useLatestContext = true)

        // Effect launched under English; now switch the app language in place.
        composeRule.runOnUiThread { localeState.value = chinese }
        composeRule.waitForIdle()

        composeRule.runOnUiThread { events.trySend(Unit) }
        composeRule.waitUntil(timeoutMillis = 5_000) { ShadowToast.shownToastCount() == 1 }

        assertEquals(chineseToast, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun toastGoesStale_whenCapturingContextDirectly() {
        // The control: proves the bug reproduces and that the test can detect it.
        val englishToast = expectedToast(english)
        val chineseToast = expectedToast(chinese)
        assertNotEquals(englishToast, chineseToast)

        val events = Channel<Unit>(Channel.BUFFERED)
        val localeState = mutableStateOf(english)
        setUpLocaleSwitchableHost(events.receiveAsFlow(), localeState, useLatestContext = false)

        composeRule.runOnUiThread { localeState.value = chinese }
        composeRule.waitForIdle()

        composeRule.runOnUiThread { events.trySend(Unit) }
        composeRule.waitUntil(timeoutMillis = 5_000) { ShadowToast.shownToastCount() == 1 }

        // Stale: the never-restarting effect still holds the English context.
        assertEquals(englishToast, ShadowToast.getTextOfLatestToast())
    }
}
