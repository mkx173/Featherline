package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentedListItemShapes(
    index: Int,
    count: Int,
    cornerShape: CornerBasedShape = MaterialTheme.shapes.large,
    pressedShape: Shape? = null,
): ListItemShapes {
    val defaultShapes = if (pressedShape != null) {
        ListItemDefaults.shapes(pressedShape = pressedShape)
    } else {
        ListItemDefaults.shapes()
    }

    return remember(index, count, defaultShapes, cornerShape) {
        val defaultBaseShape = defaultShapes.shape

        if (defaultBaseShape !is CornerBasedShape) {
            return@remember defaultShapes
        }

        when {
            count <= 1 -> {
                defaultShapes.copy(shape = cornerShape)
            }

            index == 0 -> {
                defaultShapes.copy(
                    shape = defaultBaseShape.copy(
                        topStart = cornerShape.topStart,
                        topEnd = cornerShape.topEnd,
                    )
                )
            }

            index == count - 1 -> {
                defaultShapes.copy(
                    shape = defaultBaseShape.copy(
                        bottomStart = cornerShape.bottomStart,
                        bottomEnd = cornerShape.bottomEnd,
                    )
                )
            }

            else -> {
                defaultShapes
            }
        }
    }
}
