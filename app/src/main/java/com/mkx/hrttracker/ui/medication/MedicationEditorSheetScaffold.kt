package com.mkx.hrttracker.ui.medication

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.MedicalDisclaimerKind
import com.mkx.hrttracker.ui.components.MedicalDisclaimerText

/**
 * Non-text focus anchor for the current editor sheet, used to dismiss the IME.
 *
 * On API 26 `clearFocus()` inside the sheet window makes the platform re-grant
 * focus to the first focusable text field (jumping back to it instead of
 * dismissing). Field IME actions move focus to this anchor instead, which keeps
 * a focused target so no reassignment happens while still hiding the keyboard.
 * Null outside a sheet, where callers fall back to plain `clearFocus()`.
 */
internal val LocalSheetDismissFocusRequester = compositionLocalOf<FocusRequester?> { null }

// ---------------------------------------------------------------------------
// Scaffold
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicationEditorSheetScaffold(
    modifier: Modifier = Modifier,
    title: String,
    sheetState: SheetState,
    confirmButtonText: String,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    fillAvailableHeight: Boolean,
    isSaving: Boolean,
    destructiveButtonText: String? = null,
    onDestructiveAction: (() -> Unit)? = null,
    disclaimerKinds: List<MedicalDisclaimerKind> = emptyList(),
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val navigationBarBottomPadding = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.consumeWindowInsets(WindowInsets.navigationBars),
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.systemBars.only(WindowInsetsSides.Top) },
    ) {
        // On API 26 the ModalBottomSheet window forces focus onto the first
        // focusable text field: it auto-opens the IME on entry, and when a
        // field's IME action clears focus the window re-grants focus to that
        // first field instead of dismissing. Parking focus on this non-text
        // anchor absorbs both — requesting it on entry stops the auto-open, and
        // field IME actions route here (via LocalSheetDismissFocusRequester) so
        // dismissing keeps a focused target and never jumps back. Newer API
        // levels don't reassign focus this way, so the anchor stays unfocused.
        val dismissFocusAnchor = remember { FocusRequester() }
        LaunchedEffect(Unit) { dismissFocusAnchor.requestFocus() }

        Column(
            modifier = Modifier
                .then(
                    if (fillAvailableHeight) Modifier.fillMaxSize()
                    else Modifier,
                )
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = dimensionResource(R.dimen.padding_large),
                    end = dimensionResource(R.dimen.padding_large),
                    bottom = dimensionResource(R.dimen.padding_large) + navigationBarBottomPadding,
                ),
        ) {
            val columnScope = this
            Spacer(
                // Decorative dismiss anchor: focusable for the IME workaround
                // but kept out of the a11y/traversal tree so TalkBack and
                // keyboard navigation don't land on an empty stop.
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .focusRequester(dismissFocusAnchor)
                    .focusable(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
                HrtFilledTonalButton(
                    text = stringResource(R.string.cancel),
                    onClick = onCloseClick,
                    compact = true
                )
            }

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

            CompositionLocalProvider(LocalSheetDismissFocusRequester provides dismissFocusAnchor) {
                with(columnScope) { content() }
            }

            if (disclaimerKinds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                MedicalDisclaimerText(kinds = disclaimerKinds)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Buttons stay visually enabled while a save is in flight; the
            // click handlers no-op so a second tap can't fire a duplicate save
            // / delete. The sheet dismissal lock keeps the buttons in view
            // until ROOM finishes.
            val hasDestructiveAction = destructiveButtonText != null && onDestructiveAction != null
            if (hasDestructiveAction) {
                val destructiveAction = checkNotNull(onDestructiveAction)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimensionResource(R.dimen.padding_xsmall)),
                    horizontalArrangement = Arrangement.spacedBy(
                        dimensionResource(R.dimen.padding_small),
                    ),
                ) {
                    HrtFilledTonalButton(
                        text = destructiveButtonText,
                        onClick = { if (!isSaving) destructiveAction() },
                        modifier = Modifier.weight(1f),
                    )
                    HrtButton(
                        text = confirmButtonText,
                        onClick = { if (!isSaving) onConfirm() },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                HrtButton(
                    text = confirmButtonText,
                    onClick = { if (!isSaving) onConfirm() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimensionResource(R.dimen.padding_xsmall)),
                )
            }
        }
    }
}
