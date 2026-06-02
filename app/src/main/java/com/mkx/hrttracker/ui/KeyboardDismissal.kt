package com.mkx.hrttracker.ui

import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController

internal fun dismissInput(
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
) {
    focusManager.clearFocus(force = true)
    keyboardController?.hide()
}

internal fun dismissInputAndRun(
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    action: () -> Unit,
) {
    dismissInput(
        focusManager = focusManager,
        keyboardController = keyboardController,
    )
    action()
}

internal suspend fun dismissInputAndRunWhenHidden(
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    isInputVisible: () -> Boolean,
    awaitInputHidden: suspend () -> Unit,
    action: () -> Unit,
) {
    dismissInput(
        focusManager = focusManager,
        keyboardController = keyboardController,
    )
    while (isInputVisible()) {
        awaitInputHidden()
    }
    action()
}

internal fun inputIsVisibleOrAnimating(
    imeVisible: Boolean,
    imeBottom: Int,
    imeAnimationTargetBottom: Int,
): Boolean {
    return imeVisible || imeBottom > 0 || imeAnimationTargetBottom > 0
}
