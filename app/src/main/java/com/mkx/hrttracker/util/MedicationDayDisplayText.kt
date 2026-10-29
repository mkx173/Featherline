package com.mkx.hrttracker.util

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

/**
 * Formats [dateTime]'s time and, when its medication day is not [dayDate], appends
 * the signed day offset: `09:00 (+1)`. With the day ending at 04:00, a time between
 * midnight and 04:00 belongs to the night before, so it gets no offset there.
 */
fun medicationDayTimeText(
    context: Context,
    dateTime: LocalDateTime,
    dayDate: LocalDate,
    boundary: MedicationDayBoundary,
    timeFormatter: DateTimeFormatter,
): String {
    val time = dateTime.format(timeFormatter)
    val dayOffset = ChronoUnit.DAYS.between(dayDate, medicationDay(dateTime, boundary))
    if (dayOffset == 0L) return time
    val offsetText = context.getString(
        if (dayOffset > 0) {
            R.string.plan_schedule_entry_logged_day_offset_later
        } else {
            R.string.plan_schedule_entry_logged_day_offset_earlier
        },
        dayOffset.absoluteValue.toInt(),
    )
    return "$time ($offsetText)"
}

/**
 * A dose time listed under a medication day. With the day ending at 04:00, a time
 * between midnight and 04:00 leads with the night owl moon, so a lone 01:00 under
 * Today reads as tonight rather than this morning.
 */
@Composable
fun MedicationDayTimeLabel(
    text: String,
    isPastMidnight: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    PastMidnightMarkedText(
        text = if (isPastMidnight) "$PAST_MIDNIGHT_MARK $text" else text,
        style = style,
        color = color,
        modifier = modifier,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
    )
}

/** Stands in a string for the night owl moon, as in a plan's schedule summary; see PastMidnightMarkedText. */
const val PAST_MIDNIGHT_MARK = '\u263E'

/**
 * Draws each [PAST_MIDNIGHT_MARK] in [text] as the night owl moon. The moon sits inside
 * the text, keeping its baseline and wrapping, and reads aloud as "After midnight".
 */
@Composable
fun PastMidnightMarkedText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    if (PAST_MIDNIGHT_MARK !in text) {
        Text(
            text = text,
            style = style,
            color = color,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            modifier = modifier,
        )
        return
    }
    val description = stringResource(R.string.medication_day_past_midnight)
    // 1 dp under the text's size: a full em read heavier than the digits beside it.
    val iconSize = with(LocalDensity.current) { (style.fontSize.toDp() - 1.dp).toSp() }
    Text(
        text = buildAnnotatedString {
            text.split(PAST_MIDNIGHT_MARK).forEachIndexed { index, part ->
                if (index > 0) appendInlineContent(PAST_MIDNIGHT_ICON_ID, description)
                append(part)
            }
        },
        inlineContent = mapOf(
            PAST_MIDNIGHT_ICON_ID to InlineTextContent(
                Placeholder(iconSize, iconSize, PlaceholderVerticalAlign.TextCenter),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_moon_stars),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        ),
        style = style,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        modifier = modifier,
    )
}

private const val PAST_MIDNIGHT_ICON_ID = "past_midnight"
