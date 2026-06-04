package com.mkx.hrttracker.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

val LocalCjkTextOffsetEnabled = compositionLocalOf { false }

private val cjkTextOffsetScripts = setOf(
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL,
)

internal fun CharSequence.containsCjkCharacters(): Boolean {
    var index = 0
    while (index < length) {
        val codePoint = Character.codePointAt(this, index)
        if (Character.UnicodeScript.of(codePoint) in cjkTextOffsetScripts) {
            return true
        }
        index += Character.charCount(codePoint)
    }
    return false
}

internal fun Modifier.cjkTextOffset(
    text: CharSequence,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || !text.containsCjkCharacters()) {
        return this
    }
    return composed {
        if (!LocalCjkTextOffsetEnabled.current) {
            Modifier
        } else {
            Modifier.graphicsLayer {
                translationY = (-1).dp.toPx()
            }
        }
    }
}

internal fun Modifier.cjkTextOffset(
    locale: Locale,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || locale.language != Locale.CHINESE.language) {
        return this
    }
    return composed {
        if (!LocalCjkTextOffsetEnabled.current) {
            Modifier
        } else {
            Modifier.graphicsLayer {
                translationY = (-1).dp.toPx()
            }
        }
    }
}
