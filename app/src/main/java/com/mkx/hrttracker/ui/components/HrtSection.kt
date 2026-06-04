package com.mkx.hrttracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R

/** Position of a row within its segmented group. Drives the rounded-corner shape. */
data class SegmentPosition(val index: Int, val count: Int)

/**
 * Set by [HrtSection] for each visible row so [EditorSegmentedListItem] /
 * [PreferenceSegmentedListItem] can default their index/count. Null outside a
 * section (rows then fall back to index 0 / count 1 = a standalone card).
 */
val LocalSegmentPosition = compositionLocalOf<SegmentPosition?> { null }

/**
 * Assigns each row a [SegmentPosition] from its order among the *counted*
 * (visible) rows. Hidden rows (false) get null and are excluded from the count,
 * so adding/removing/conditionally hiding a row needs no manual renumbering.
 */
internal fun segmentPositionsFor(counted: List<Boolean>): List<SegmentPosition?> {
    val total = counted.count { it }
    var index = 0
    return counted.map { visible ->
        if (visible) SegmentPosition(index++, total) else null
    }
}

/**
 * Collapses a row's separate index/count into a single explicit position, or
 * null to inherit. Requires both or neither -- a partial override (only one set)
 * would silently mix an explicit value with an inherited one, so it fails loud.
 */
internal fun explicitSegmentPosition(index: Int?, count: Int?): SegmentPosition? {
    require((index == null) == (count == null)) {
        "Pass both index and count, or neither (got index=$index, count=$count)."
    }
    return if (index != null && count != null) SegmentPosition(index, count) else null
}

/**
 * Resolves a row's segment position: [explicitPosition] wins; otherwise inherit
 * from [LocalSegmentPosition] (set by [HrtSection]); otherwise a standalone card
 * (0 of 1). Single resolution point so rows inside an HrtSection omit index/count.
 */
@Composable
internal fun currentSegmentPosition(explicitPosition: SegmentPosition? = null): SegmentPosition =
    explicitPosition ?: LocalSegmentPosition.current ?: SegmentPosition(0, 1)

/** Test-observable hook: the resolved [SegmentPosition] a row rendered with. */
internal val SegmentPositionSemanticsKey = SemanticsPropertyKey<SegmentPosition>("SegmentPosition")

/** Publishes [position] on the node so tests can assert a row honored its inherited position. */
internal fun Modifier.segmentPositionSemantics(position: SegmentPosition): Modifier =
    semantics { this[SegmentPositionSemanticsKey] = position }

/** Recorder for [HrtSection] rows. Mirrors LazyListScope: non-composable, appends entries. */
interface HrtSectionScope {
    /** A row that is always present. */
    fun item(content: @Composable () -> Unit)

    /**
     * A row that animates in/out with [visible]. Always rendered (so it can
     * animate out), but only counted toward the segment group when visible -
     * matching the prior resolve*SectionLayout behavior, including the brief
     * corner-snap during the transition.
     */
    fun animatedItem(visible: Boolean, content: @Composable () -> Unit)
}

private class HrtSectionEntry(
    val animated: Boolean,
    val visible: Boolean,
    val content: @Composable () -> Unit,
)

private class HrtSectionScopeImpl : HrtSectionScope {
    val entries = mutableListOf<HrtSectionEntry>()

    override fun item(content: @Composable () -> Unit) {
        entries += HrtSectionEntry(animated = false, visible = true, content = content)
    }

    override fun animatedItem(visible: Boolean, content: @Composable () -> Unit) {
        entries += HrtSectionEntry(animated = true, visible = visible, content = content)
    }
}

/**
 * The canonical "section header + grouped segmented rows" container.
 *
 * Owns the header, the inter-row gap (R.dimen.list_segment_gap), and each
 * visible row's SegmentPosition (supplied via LocalSegmentPosition), so rows
 * never hand-number index/count. Declaration order is the source of truth;
 * `if (cond) item {}` adjusts the count automatically.
 *
 * @param title section header text; null renders the grouped rows with no header.
 * @param topPadding adds the standard header top padding (false for the first section).
 * @param headerTrailing optional trailing slot in the header (e.g. divider/action).
 */
@Composable
fun HrtSection(
    title: String?,
    modifier: Modifier = Modifier,
    topPadding: Boolean = true,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: HrtSectionScope.() -> Unit,
) {
    val scope = HrtSectionScopeImpl().apply(content)
    val positions = segmentPositionsFor(scope.entries.map { it.visible })
    val gap = dimensionResource(R.dimen.list_segment_gap)

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            HrtSectionHeader(text = title, topPadding = topPadding, trailing = headerTrailing)
        }
        // Gaps are leading spacers inside each row (not Arrangement.spacedBy) so a
        // collapsed animatedItem leaves no double gap - its leading spacer collapses
        // together with its AnimatedVisibility content.
        scope.entries.forEachIndexed { i, entry ->
            val position = positions[i]
            val body: @Composable () -> Unit = {
                CompositionLocalProvider(LocalSegmentPosition provides position) {
                    Column {
                        if (position != null && position.index > 0) {
                            Spacer(modifier = Modifier.height(gap))
                        }
                        entry.content()
                    }
                }
            }
            if (entry.animated) {
                AnimatedVisibility(
                    visible = entry.visible,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) { body() }
            } else {
                body()
            }
        }
    }
}

/** Section header: uppercased titleSmall on onSurfaceVariant, with an optional trailing slot. */
@Composable
fun HrtSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    topPadding: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp, top = if (topPadding) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
