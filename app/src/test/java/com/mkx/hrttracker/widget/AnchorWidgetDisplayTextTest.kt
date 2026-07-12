package com.mkx.hrttracker.widget

import android.content.Context
import android.content.res.Configuration
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.TrackedDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnchorWidgetDisplayTextTest {
    private val appContext: Context
        get() = RuntimeEnvironment.getApplication().applicationContext

    @Test
    fun buildAnchorWidgetDisplayText_bakesLocalizedAnchorStrings() {
        val anchor = TrackedDate(
            id = "anchor-1",
            name = "Start",
            icon = AnchorIcon.STAR,
            date = LocalDate.of(2026, 7, 1),
            palette = null,
            pinnedOrder = null,
        )
        val dateText = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.SIMPLIFIED_CHINESE)
            .format(anchor.date)

        val displayText = buildAnchorWidgetDisplayText(
            context = localizedContext(Locale.SIMPLIFIED_CHINESE),
            anchor = anchor,
            today = LocalDate.of(2026, 7, 5),
        )

        assertEquals(
            AnchorWidgetDisplayText.Loaded(
                anchor = anchor,
                directionLine = "开始于 $dateText",
                dateText = dateText,
                daysText = "4 天",
            ),
            displayText,
        )
    }

    // The widget's count line must match the in-app dayCountLabel wording exactly:
    // "Today" on the anchor date, "in N days" for a future one, "N days" past.
    @Test
    fun buildAnchorWidgetDisplayText_matchesInAppDayCountWording() {
        val anchor = anchor(date = LocalDate.of(2026, 7, 5))

        val onDay = buildAnchorWidgetDisplayText(appContext, anchor, today = LocalDate.of(2026, 7, 5))
        val future = buildAnchorWidgetDisplayText(appContext, anchor, today = LocalDate.of(2026, 7, 2))
        val past = buildAnchorWidgetDisplayText(appContext, anchor, today = LocalDate.of(2026, 7, 6))

        assertEquals("Today", (onDay as AnchorWidgetDisplayText.Loaded).daysText)
        assertEquals("in 3 days", (future as AnchorWidgetDisplayText.Loaded).daysText)
        assertEquals("1 day", (past as AnchorWidgetDisplayText.Loaded).daysText)
    }

    // The direction line drops its prefix only when even the widest possible prefixed
    // date would clip: generous width keeps it, near-zero width can never fit.
    @Test
    fun anchorDirectionLineFits_switchesOnAvailableWidth() {
        assertEquals(
            true,
            anchorDirectionLineFits(appContext, sampleYear = 2026, fontSizePx = 16f, availableWidthPx = 10_000f),
        )
        assertEquals(
            false,
            anchorDirectionLineFits(appContext, sampleYear = 2026, fontSizePx = 16f, availableWidthPx = 1f),
        )
    }

    private fun anchor(date: LocalDate) = TrackedDate(
        id = "anchor-1",
        name = "Start",
        icon = AnchorIcon.STAR,
        date = date,
        palette = null,
        pinnedOrder = null,
    )

    @Test
    fun buildAnchorWidgetDisplayText_bakesLocalizedEmptyMessage() {
        val displayText = buildAnchorWidgetDisplayText(
            context = localizedContext(Locale.SIMPLIFIED_CHINESE),
            anchor = null,
            today = LocalDate.of(2026, 7, 5),
        )

        assertEquals(AnchorWidgetDisplayText.Empty(message = "请选择一个日期"), displayText)
    }

    private fun localizedContext(locale: Locale): Context =
        appContext.createConfigurationContext(
            Configuration(appContext.resources.configuration).apply { setLocale(locale) }
        )
}
