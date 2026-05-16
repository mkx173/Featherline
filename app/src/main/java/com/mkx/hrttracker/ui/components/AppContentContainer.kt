package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

/**
 * Maximum width applied to routed-screen body content. Wider screens (tablets,
 * landscape phones) center the body inside this cap; the top app bar stays full-width.
 */
val AppContentMaxWidth: Dp = 640.dp

/**
 * Centers [content] horizontally inside a box capped at [AppContentMaxWidth]. Pass the
 * containing `Scaffold`'s `innerPadding` via the [modifier] parameter (e.g.
 * `modifier = Modifier.padding(innerPadding)`). The top app bar stays at the screen's
 * full width — only the body is constrained.
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
@Preview(name = "AppContentContainer - tablet", widthDp = 1024, heightDp = 600, showBackground = true)
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
