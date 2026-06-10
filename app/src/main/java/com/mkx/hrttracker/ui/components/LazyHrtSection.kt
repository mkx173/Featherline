package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.mkx.hrttracker.R

/** Recorder for [hrtSection] rows. Mirrors [HrtSectionScope] but emits real lazy items. */
interface LazyHrtSectionScope {
    fun item(key: String, contentType: Any? = "hrt-section-row", content: @Composable () -> Unit)
}

private class LazyHrtSectionEntry(
    val key: String,
    val contentType: Any?,
    val content: @Composable () -> Unit,
)

private class LazyHrtSectionScopeImpl : LazyHrtSectionScope {
    val entries = mutableListOf<LazyHrtSectionEntry>()

    override fun item(key: String, contentType: Any?, content: @Composable () -> Unit) {
        entries += LazyHrtSectionEntry(key, contentType, content)
    }
}

/**
 * LazyColumn equivalent of [HrtSection]: emits the header and each row as separate
 * keyed lazy items so a long section no longer composes/measures as one item (the
 * scroll-jank fix). Preserves [HrtSection]'s contract -- a leading
 * [R.dimen.list_segment_gap] before every row except the first, and each row's
 * [SegmentPosition] via [LocalSegmentPosition] so rows omit index/count.
 *
 * Does NOT support animated rows; animated sections keep using [HrtSection].
 *
 * @param key stable prefix for this section; the header item uses "$key-header".
 * @param header optional header slot; call [HrtSectionHeader] here. A LazyListScope
 *   param cannot resolve string resources, so the title is resolved in this slot's
 *   composable scope instead of a `title: String` parameter.
 */
fun LazyListScope.hrtSection(
    key: String,
    header: (@Composable () -> Unit)? = null,
    headerContentType: Any? = "hrt-section-header",
    content: LazyHrtSectionScope.() -> Unit,
) {
    val entries = LazyHrtSectionScopeImpl().apply(content).entries
    val count = entries.size

    if (header != null) {
        item(key = "$key-header", contentType = headerContentType) { header() }
    }

    entries.forEachIndexed { index, entry ->
        item(key = entry.key, contentType = entry.contentType) {
            Column {
                if (index > 0) {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
                }
                CompositionLocalProvider(
                    LocalSegmentPosition provides SegmentPosition(index, count)
                ) {
                    entry.content()
                }
            }
        }
    }
}
