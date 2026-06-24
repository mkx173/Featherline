package com.mkx.hrttracker.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView

// Scrolls the *whole* element this is attached to into view when a descendant text field asks
// to be revealed (on focus / cursor move). A BasicTextField's built-in bring-into-view only
// reveals the cursor line, so any chrome below it — the editor's Cancel/Save/Delete buttons —
// can stay hidden behind the IME. Intercepting the request and substituting the node's own
// bounds lifts the entire editing surface above the keyboard. Pair with `imePadding()` on the
// scroll container so there is room to scroll into.
internal fun Modifier.bringWholeFieldIntoView(): Modifier =
    this.then(WholeFieldBringIntoViewElement)

private object WholeFieldBringIntoViewElement :
    ModifierNodeElement<WholeFieldBringIntoViewNode>() {
    override fun create(): WholeFieldBringIntoViewNode = WholeFieldBringIntoViewNode()

    override fun update(node: WholeFieldBringIntoViewNode) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = javaClass.hashCode()

    override fun InspectorInfo.inspectableProperties() {
        name = "bringWholeFieldIntoView"
    }
}

private class WholeFieldBringIntoViewNode :
    Modifier.Node(),
    BringIntoViewModifierNode,
    LayoutAwareModifierNode {
    override val shouldAutoInvalidate: Boolean = false

    private var hasBeenPlaced = false

    override fun onPlaced(coordinates: LayoutCoordinates) {
        hasBeenPlaced = true
    }

    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?,
    ) {
        fun localRect(): Rect? {
            if (!isAttached || !hasBeenPlaced) return null

            val layoutCoordinates = requireLayoutCoordinates()
            val attachedChildCoordinates = childCoordinates.takeIf(LayoutCoordinates::isAttached)
                ?: return null
            val childRect = boundsProvider() ?: return null
            return layoutCoordinates.localRectOf(attachedChildCoordinates, childRect)
        }

        bringIntoView {
            localRect()?.let {
                val layoutCoordinates = requireLayoutCoordinates()
                Rect(
                    0f,
                    0f,
                    layoutCoordinates.size.width.toFloat(),
                    layoutCoordinates.size.height.toFloat(),
                )
            }
        }
    }
}

private fun LayoutCoordinates.localRectOf(
    sourceCoordinates: LayoutCoordinates,
    rect: Rect,
): Rect {
    val localRect = localBoundingBoxOf(sourceCoordinates, clipBounds = false)
    return rect.translate(localRect.topLeft)
}
