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
