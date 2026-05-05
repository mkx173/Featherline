package com.mkx.hrttracker.ui.main

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationDetails
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
