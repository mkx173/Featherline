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
                daysText = "4 天",
            ),
            displayText,
        )
    }

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
