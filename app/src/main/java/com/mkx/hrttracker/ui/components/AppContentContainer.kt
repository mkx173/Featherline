package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

/**
 * Maximum width applied to routed-screen body content. Wider screens (tablets,
 * landscape phones) center the body inside this cap; the top app bar stays full-width.
 */
val AppContentMaxWidth: Dp = 640.dp

/**
 * Bottom padding the body content should apply inside its scrollable region so the
 * last item can scroll past the system navigation gesture bar in tablet layouts.
 *
 * The navigation host computes the effective value once and provides it here:
 * - On compact layouts (bottom `NavigationBar` covers the gesture bar): `0.dp`.
 * - On medium / expanded layouts (rail / drawer on the side): the real
 *   `WindowInsets.navigationBars` bottom inset.
 *
 * Body content reads this via [appContentPaddingValues] and adds it to its
 * `LazyColumn.contentPadding` or the `padding(...)` modifier that sits *inside* its
 * `verticalScroll(...)` chain — so the bottom padding scrolls with content rather
 * than reserving a static frame at the bottom of the viewport.
 */
val LocalAppContentBottomInset = compositionLocalOf { 0.dp }

/**
 * Standard [PaddingValues] for a routed screen's scrollable body — applied as
 * `LazyColumn.contentPadding` or via `Modifier.padding(...)` inside a
 * `verticalScroll(...)` chain.
 *
 * Adds [LocalAppContentBottomInset] to the bottom so the last item scrolls past the
 * system navigation gesture bar on tablet layouts. On compact layouts the local
 * resolves to `0.dp`, so this returns the plain padding values unchanged.
 *
 * @param horizontal padding applied to both `start` and `end`. Defaults to
 *   `R.dimen.padding_medium`.
 * @param top padding applied to the top. Defaults to `R.dimen.padding_medium`.
 * @param bottom base padding applied to the bottom *before* adding the gesture-bar
 *   inset. Defaults to `R.dimen.padding_medium`.
 */
@Composable
fun appContentPaddingValues(
    horizontal: Dp = dimensionResource(R.dimen.padding_medium),
    top: Dp = dimensionResource(R.dimen.padding_medium),
    bottom: Dp = dimensionResource(R.dimen.padding_medium),
): PaddingValues {
    val bottomInset = LocalAppContentBottomInset.current
    return PaddingValues(
        start = horizontal,
        top = top,
        end = horizontal,
        bottom = bottom + bottomInset,
    )
}

/**
 * Centers [content] horizontally inside a box capped at [AppContentMaxWidth]. Pass the
 * containing `Scaffold`'s `innerPadding` via the [modifier] parameter (e.g.
 * `modifier = Modifier.padding(innerPadding)`). The top app bar stays at the screen's
 * full width — only the body is constrained.
 *
 * Body content is responsible for honoring [LocalAppContentBottomInset] inside its
 * scrollable region — see that local's KDoc.
 */
@Composable
fun AppContentContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = AppContentMaxWidth)
                .fillMaxSize(),
            content = content,
        )
    }
}

@Preview(name = "AppContentContainer - phone", widthDp = 420, heightDp = 600, showBackground = true)
@Preview(
    name = "AppContentContainer - tablet",
    widthDp = 1024,
    heightDp = 600,
    showBackground = true
)
@Composable
private fun AppContentContainerPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            AppContentContainer {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {}
            }
        }
    }
}
