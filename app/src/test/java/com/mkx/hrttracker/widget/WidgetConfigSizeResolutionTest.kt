package com.mkx.hrttracker.widget

import org.junit.Assert.assertEquals
import org.junit.Test

// The reconfigure activity is exported and tolerates INVALID_APPWIDGET_ID, so the
// provider lookup can come back null (or any unknown class) on malformed launches.
// The spec requires falling back to the MEDIUM preview in that case rather than
// crashing or stranding the UI — only an explicit large/anchor provider gets the
// large/anchor config type.
class WidgetConfigSizeResolutionTest {

    @Test
    fun nullProviderFallsBackToMedium() {
        assertEquals(WidgetConfigType.MEDIUM, widgetConfigTypeForProvider(null))
    }

    @Test
    fun mediumReceiverResolvesMedium() {
        assertEquals(
            WidgetConfigType.MEDIUM,
            widgetConfigTypeForProvider(HrtWidgetMediumReceiver::class.java.name),
        )
    }

    @Test
    fun largeReceiverResolvesLarge() {
        assertEquals(
            WidgetConfigType.LARGE,
            widgetConfigTypeForProvider(HrtWidgetLargeReceiver::class.java.name),
        )
    }

    @Test
    fun anchorReceiverResolvesAnchor() {
        assertEquals(
            WidgetConfigType.ANCHOR,
            widgetConfigTypeForProvider(HrtAnchorWidgetReceiver::class.java.name),
        )
    }

    @Test
    fun unknownProviderFallsBackToMedium() {
        assertEquals(
            WidgetConfigType.MEDIUM,
            widgetConfigTypeForProvider("com.unknown.SomeOtherReceiver"),
        )
    }
}
