package com.mkx.hrttracker.ui.medication

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.MedicalDisclaimerKind
import com.mkx.hrttracker.ui.components.MedicalDisclaimerText

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

            content()

            if (disclaimerKinds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
                MedicalDisclaimerText(kinds = disclaimerKinds)
            }

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

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
