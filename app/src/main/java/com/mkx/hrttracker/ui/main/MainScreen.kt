package com.mkx.hrttracker.ui.main

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.ui.calibration.calibrationAllowedUnitsFor
import com.mkx.hrttracker.ui.calibration.calibrationUnitLabel
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import java.time.LocalDateTime
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
    onQuickLogDoseClick: (UUID, UUID?, LocalDateTime, MedicationDetails, Int) -> Unit =
        { _, _, _, _, _ -> },
    onEntryClick: (Set<UUID>) -> Unit = { },
    onAddEntryClick: () -> Unit = { },
    viewModel: MainViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        lazyListState = listState,
        state = topAppBarState
    )
    val initialScrollToTopSignal = remember { scrollToTopSignal }

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal != initialScrollToTopSignal) {
            listState.animateScrollToItem(0)
            topAppBarState.contentOffset = 0f
            topAppBarState.heightOffset = 0f
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    listState.animateScrollToItem(0)
                },
                title = {
                    val title = stringResource(R.string.tab_main)
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title, amount = (-2).dp),
                    )
                },
                actions = {
                    IconButton(onClick = onAddEntryClick) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.fab_add_entry),
                        )
                    }
                    HomeMoreOptionsMenu(
                        selectedUnit = uiState.homeE2DisplayUnit,
                        onUnitSelected = viewModel::setHomeE2DisplayUnit,
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        MainContent(
            uiState = uiState,
            listState = listState,
            onQuickLogDoseClick = onQuickLogDoseClick,
            onEntryClick = onEntryClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeMoreOptionsMenu(
    selectedUnit: BloodUnitKey,
    onUnitSelected: (BloodUnitKey) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var unitSubmenuExpanded by rememberSaveable { mutableStateOf(false) }

    fun dismissMenus() {
        expanded = false
        unitSubmenuExpanded = false
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.main_more_options),
            )
        }

        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = ::dismissMenus,
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(0, 1),
            ) {
                Box {
                    val itemInteractionSource = remember { MutableInteractionSource() }
                    val itemHovered by itemInteractionSource.collectIsHoveredAsState()
                    val submenuExpanded = unitSubmenuExpanded || itemHovered
                    val selectUnitText = stringResource(R.string.main_display_unit)

                    DropdownMenuItem(
                        interactionSource = itemInteractionSource,
                        onClick = { unitSubmenuExpanded = !unitSubmenuExpanded },
                        text = { Text(text = selectUnitText, modifier = Modifier.cjkTextOffset(selectUnitText)) },
                        shape = MaterialTheme.shapes.medium,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(MenuDefaults.TrailingIconSize),
                            )
                        },
                    )

                    DropdownMenuPopup(
                        popupPositionProvider =
                            MenuDefaults.rememberDropdownMenuPopupPositionProvider(
                                MenuAnchorPosition.End
                            ),
                        expanded = submenuExpanded,
                        onDismissRequest = { unitSubmenuExpanded = false },
                        properties = PopupProperties(focusable = false),
                    ) {
                        HomeDisplayUnitSubmenu(
                            selectedUnit = selectedUnit,
                            onUnitSelected = { unit ->
                                onUnitSelected(unit)
                                dismissMenus()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeDisplayUnitSubmenu(
    selectedUnit: BloodUnitKey,
    onUnitSelected: (BloodUnitKey) -> Unit,
) {
    val units = calibrationAllowedUnitsFor(BloodAnalyteKey.E2)

    DropdownMenuGroup(
        shapes = MenuDefaults.groupShape(0, 1),
    ) {
        units.forEachIndexed { index, unit ->
            DropdownMenuItem(
                selected = selectedUnit == unit,
                onClick = { onUnitSelected(unit) },
                text = { Text(text = calibrationUnitLabel(unit)) },
                shapes = MenuDefaults.itemShape(index, units.size),
                selectedLeadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                    )
                },
            )
        }
    }
}
