package com.mkx.hrttracker.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The reconfigure activity is exported and tolerates INVALID_APPWIDGET_ID, so the
// provider lookup can come back null (or any unknown class) on malformed launches.
// The spec requires falling back to the MEDIUM preview in that case rather than
// crashing or stranding the UI — only an explicit large-receiver provider gets the
// large preview.
class WidgetConfigSizeResolutionTest {

    @Test
    fun nullProviderFallsBackToMedium() {
        assertTrue(isMediumWidgetProvider(null))
    }

    @Test
    fun mediumReceiverResolvesMedium() {
        assertTrue(isMediumWidgetProvider(HrtWidgetMediumReceiver::class.java.name))
    }

    @Test
    fun largeReceiverResolvesLarge() {
        assertFalse(isMediumWidgetProvider(HrtWidgetLargeReceiver::class.java.name))
    }

    @Test
    fun unknownProviderFallsBackToMedium() {
        assertTrue(isMediumWidgetProvider("com.unknown.SomeOtherReceiver"))
    }
}
