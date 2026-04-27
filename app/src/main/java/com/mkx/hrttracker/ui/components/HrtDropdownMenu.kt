package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

data class HrtDropdownMenuItem(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val supportingText: String? = null,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HrtDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<HrtDropdownMenuItem>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuGroupContentPadding,
) {
    if (items.isEmpty()) {
        return
    }

    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShapes(),
            contentPadding = contentPadding,
        ) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    onClick = {
                        onDismissRequest()
                        item.onClick()
                    },
                    text = { Text(text = item.text) },
                    shape = expressiveMenuItemShape(index = index, count = items.size),
                    enabled = item.enabled,
                    supportingText = item.supportingText?.let { supportingText ->
                        { Text(text = supportingText) }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun expressiveMenuItemShape(
    index: Int,
    count: Int,
): Shape {
    return when {
        count <= 1 -> MaterialTheme.shapes.medium
        index == 0 -> MenuDefaults.leadingItemShape
        index == count - 1 -> MenuDefaults.trailingItemShape
        else -> MenuDefaults.middleItemShape
    }
}
