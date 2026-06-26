@file:Suppress(
    "INVISIBLE_REFERENCE",
    "INVISIBLE_MEMBER",
    "INVISIBLE_SETTER",
    "CANNOT_OVERRIDE_INVISIBLE_MEMBER",
    "EXPOSED_PARAMETER_TYPE",
)
@file:OptIn(ExperimentalMaterial3Api::class)

package com.mkx.hrttracker.ui.components.datepicker

/*
 * Vendored, minimal fork of Material3's private DatePicker rendering chain so the
 * year-selection overlay can paint a real haze blur instead of bleeding through the
 * translucent dialog container color. Copied from `androidx.compose.material3`
 * 1.5.0-alpha18 (Compose BOM 2026.04.01). Only the `private` functions on the
 * DatePicker -> YearPicker path are copied; every `internal` Material3 symbol is
 * referenced in place via the file-level @Suppress above (Kotlin warns this is
 * "UNSPECIFIED and WILL NOT BE PRESERVED" — re-sync on every Material3 bump).
 *
 * The ONE real change vs upstream is in `YearPicker`'s LazyVerticalGrid background
 * (see the `ponytail:` comment there). Everything else is a verbatim copy.
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.tokens.DatePickerModalTokens
import androidx.compose.material3.tokens.MotionSchemeKeyTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.isContainer
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.ui.components.LocalHazeBlurEnabled
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.max
import kotlinx.coroutines.launch

// Public Material3 symbols referenced by simple name below
// (ButtonDefaults, DatePickerColors, DatePickerDefaults, DatePickerFormatter,
// DatePickerState, DisplayMode, DividerDefaults, HorizontalDivider, Icon,
// IconButton, LocalContentColor, PlainTooltip, ProvideTextStyle, SelectableDates,
// Surface, Text, TextButton, TooltipAnchorPosition, TooltipBox, TooltipDefaults,
// rememberTooltipState) and the internal chain (BaseDatePickerStateImpl,
// DateEntryContainer, DateInputContent, DisplayModeToggleButton, Month, WeekDays,
// updateDisplayedMonth, numberOfMonthsInRange, createCalendarModel, getString,
// Strings, Icons, CalendarModel, toLocalString, formatDatePickerNavigateToYearString,
// DatePickerModeTogglePadding, DatePickerHorizontalPadding, MonthYearHeight,
// RecommendedSizeForAccessibility, the token `.value`/`.value()` extensions, and the
// DatePickerColors.year* member colors) all live in androidx.compose.material3 and
// resolve through these wildcard imports + the @file:Suppress invisible-reference hack.
import androidx.compose.material3.*
import androidx.compose.material3.internal.*

/**
 * Haze-enabled fork of Material3's [androidx.compose.material3.DatePicker].
 *
 * Identical to the upstream picker except for a [hazeState] parameter that is threaded
 * down to the year-selection overlay so it can blur the app behind the dialog and
 * opaquely cover the calendar (see [YearPicker]). Pass `null` to get the stock
 * opaque-container behavior.
 */
@Composable
fun HazeDatePicker(
    state: DatePickerState,
    modifier: Modifier = Modifier,
    dateFormatter: DatePickerFormatter = remember { DatePickerDefaults.dateFormatter() },
    colors: DatePickerColors = DatePickerDefaults.colors(),
    title: (@Composable () -> Unit)? = {
        DatePickerDefaults.DatePickerTitle(
            displayMode = state.displayMode,
            modifier = Modifier.padding(DatePickerTitlePadding),
            contentColor = colors.titleContentColor,
        )
    },
    headline: (@Composable () -> Unit)? = {
        DatePickerDefaults.DatePickerHeadline(
            selectedDateMillis = state.selectedDateMillis,
            displayMode = state.displayMode,
            dateFormatter = dateFormatter,
            modifier = Modifier.padding(DatePickerHeadlinePadding),
            contentColor = colors.headlineContentColor,
        )
    },
    showModeToggle: Boolean = true,
    focusRequester: FocusRequester? = remember { FocusRequester() },
    hazeState: HazeState? = null,
) {
    val calendarModel =
        remember(state.locale) {
            if (state is BaseDatePickerStateImpl) {
                state.calendarModel
            } else {
                createCalendarModel(state.locale)
            }
        }
    DateEntryContainer(
        modifier = modifier,
        title = title,
        headline = headline,
        modeToggleButton =
            if (showModeToggle) {
                {
                    DisplayModeToggleButton(
                        modifier = Modifier.padding(DatePickerModeTogglePadding),
                        displayMode = state.displayMode,
                        onDisplayModeChange = { displayMode -> state.displayMode = displayMode },
                        colors = colors,
                    )
                }
            } else {
                null
            },
        headlineTextStyle = DatePickerModalTokens.HeaderHeadlineFont.value,
        headerMinHeight = DatePickerModalTokens.HeaderContainerHeight,
        colors = colors,
    ) {
        SwitchableDateEntryContent(
            selectedDateMillis = state.selectedDateMillis,
            displayedMonthMillis = state.displayedMonthMillis,
            displayMode = state.displayMode,
            onDateSelectionChange = { dateInMillis -> state.selectedDateMillis = dateInMillis },
            onDisplayedMonthChange = { monthInMillis ->
                state.displayedMonthMillis = monthInMillis
            },
            calendarModel = calendarModel,
            yearRange = state.yearRange,
            dateFormatter = dateFormatter,
            selectableDates = state.selectableDates,
            colors = colors,
            focusRequester = focusRequester,
            hazeState = hazeState,
        )
    }
}

@Composable
private fun SwitchableDateEntryContent(
    selectedDateMillis: Long?,
    displayedMonthMillis: Long,
    displayMode: DisplayMode,
    onDateSelectionChange: (dateInMillis: Long?) -> Unit,
    onDisplayedMonthChange: (monthInMillis: Long) -> Unit,
    calendarModel: CalendarModel,
    yearRange: IntRange,
    dateFormatter: DatePickerFormatter,
    selectableDates: SelectableDates,
    colors: DatePickerColors,
    focusRequester: FocusRequester?,
    hazeState: HazeState?,
) {
    // Parallax effect offset that will slightly scroll in and out the navigation part of the picker
    // when the display mode changes.
    val parallaxTarget = with(LocalDensity.current) { -48.dp.roundToPx() }
    // TODO Load the motionScheme tokens from the component tokens file
    val effectsInAnimationSpec: FiniteAnimationSpec<Float> =
        MotionSchemeKeyTokens.DefaultEffects.value()
    val effectsOutAnimationSpec: FiniteAnimationSpec<Float> =
        MotionSchemeKeyTokens.FastEffects.value()
    val spatialInOutAnimationSpec: FiniteAnimationSpec<IntOffset> =
        MotionSchemeKeyTokens.DefaultSpatial.value()
    val spatialSizeAnimationSpec: FiniteAnimationSpec<IntSize> =
        MotionSchemeKeyTokens.DefaultSpatial.value()
    AnimatedContent(
        targetState = displayMode,
        modifier =
            Modifier.semantics {
                // TODO(b/347038246): replace `isContainer` with `isTraversalGroup` with new
                // pruning API.
                @Suppress("DEPRECATION")
                isContainer = true
            },
        transitionSpec = {
            // When animating the input mode, fade out the calendar picker and slide in the text
            // field from the bottom with a delay to show up after the picker is hidden.
            if (targetState == DisplayMode.Input) {
                    slideInVertically(animationSpec = spatialInOutAnimationSpec) { height ->
                        height
                    } + fadeIn(animationSpec = effectsInAnimationSpec) togetherWith
                        fadeOut(effectsOutAnimationSpec) +
                            slideOutVertically(
                                animationSpec = spatialInOutAnimationSpec,
                                targetOffsetY = { _ -> parallaxTarget },
                            )
                } else {
                    // When animating the picker mode, slide out text field and fade in calendar
                    // picker with a delay to show up after the text field is hidden.
                    slideInVertically(
                        animationSpec = spatialInOutAnimationSpec,
                        initialOffsetY = { _ -> parallaxTarget },
                    ) + fadeIn(animationSpec = effectsInAnimationSpec) togetherWith
                        slideOutVertically(
                            animationSpec = spatialInOutAnimationSpec,
                            targetOffsetY = { fullHeight -> fullHeight },
                        ) + fadeOut(animationSpec = effectsOutAnimationSpec)
                }
                .using(
                    SizeTransform(
                        clip = true,
                        sizeAnimationSpec = { _, _ -> spatialSizeAnimationSpec },
                    )
                )
        },
        label = "DatePickerDisplayModeAnimation",
    ) { mode ->
        when (mode) {
            DisplayMode.Picker ->
                DatePickerContent(
                    selectedDateMillis = selectedDateMillis,
                    displayedMonthMillis = displayedMonthMillis,
                    onDateSelectionChange = onDateSelectionChange,
                    onDisplayedMonthChange = onDisplayedMonthChange,
                    calendarModel = calendarModel,
                    yearRange = yearRange,
                    dateFormatter = dateFormatter,
                    selectableDates = selectableDates,
                    colors = colors,
                    hazeState = hazeState,
                )
            DisplayMode.Input ->
                DateInputContent(
                    selectedDateMillis = selectedDateMillis,
                    onDateSelectionChange = onDateSelectionChange,
                    calendarModel = calendarModel,
                    yearRange = yearRange,
                    dateFormatter = dateFormatter,
                    selectableDates = selectableDates,
                    colors = colors,
                    focusRequester = focusRequester,
                )
        }
    }
}

@Composable
private fun DatePickerContent(
    selectedDateMillis: Long?,
    displayedMonthMillis: Long,
    onDateSelectionChange: (dateInMillis: Long) -> Unit,
    onDisplayedMonthChange: (monthInMillis: Long) -> Unit,
    calendarModel: CalendarModel,
    yearRange: IntRange,
    dateFormatter: DatePickerFormatter,
    selectableDates: SelectableDates,
    colors: DatePickerColors,
    hazeState: HazeState?,
) {
    val displayedMonth = calendarModel.getMonth(displayedMonthMillis)
    val monthIndex = displayedMonth.indexIn(yearRange).coerceAtLeast(0)
    val monthsListState = rememberLazyListState(initialFirstVisibleItemIndex = monthIndex)

    // Scroll to the resolved displayedMonth, if needed.
    LaunchedEffect(monthIndex) {
        // The DatePicker has other actions that can trigger a scroll and update the
        // displayedMonthMillis as they do so, hence we check here for isScrollInProgress and only
        // scroll to the monthIndex when there is none in progress.
        if (
            !monthsListState.isScrollInProgress &&
                monthsListState.firstVisibleItemIndex != monthIndex
        ) {
            monthsListState.scrollToItem(monthIndex)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var yearPickerVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val (
        nextButtonFocusRequester,
        yearSelectionButtonFocusRequester,
        currentYearFocusRequester,
        dividerFocusRequester) =
        remember { FocusRequester.createRefs() }
    Column {
        MonthsNavigation(
            modifier = Modifier.padding(horizontal = DatePickerHorizontalPadding),
            nextAvailable = monthsListState.canScrollForward,
            previousAvailable = monthsListState.canScrollBackward,
            yearPickerVisible = yearPickerVisible,
            yearPickerText =
                dateFormatter.formatMonthYear(
                    monthMillis = displayedMonthMillis,
                    locale = calendarModel.locale,
                ) ?: "-",
            nextButtonModifier = Modifier.focusRequester(nextButtonFocusRequester),
            onNextClicked = {
                coroutineScope.launch {
                    try {
                        monthsListState.animateScrollToItem(
                            monthsListState.firstVisibleItemIndex + 1
                        )
                    } catch (_: IllegalArgumentException) {
                        // Ignore. This may happen if the user clicked the "next" arrow fast while
                        // the list was still animating to the next item.
                    }
                }
            },
            onPreviousClicked = {
                coroutineScope.launch {
                    try {
                        monthsListState.animateScrollToItem(
                            monthsListState.firstVisibleItemIndex - 1
                        )
                    } catch (_: IllegalArgumentException) {
                        // Ignore. This may happen if the user clicked the "previous" arrow fast
                        // while  the list was still animating to the previous item.
                    }
                }
            },
            onYearPickerButtonClicked = { yearPickerVisible = !yearPickerVisible },
            onYearPickerButtonTabPressed = {
                // Keyboard focus on the selected year when tabbed from the open year picker button.
                val moved = currentYearFocusRequester.requestFocus()
                if (!moved) {
                    // If grid is scrolled and selected year is not in view just move focus to
                    // closest year option from button.
                    focusManager.moveFocus(FocusDirection.Down)
                }
            },
            yearSelectionButtonFocusRequester = yearSelectionButtonFocusRequester,
            colors = colors,
        )

        Box {
            Column(modifier = Modifier.padding(horizontal = DatePickerHorizontalPadding)) {
                WeekDays(colors, calendarModel)
                HorizontalMonthsList(
                    lazyListState = monthsListState,
                    selectedDateMillis = selectedDateMillis,
                    onDateSelectionChange = onDateSelectionChange,
                    onDisplayedMonthChange = onDisplayedMonthChange,
                    calendarModel = calendarModel,
                    yearRange = yearRange,
                    dateFormatter = dateFormatter,
                    selectableDates = selectableDates,
                    colors = colors,
                    onReturnFocus = { nextButtonFocusRequester.requestFocus() },
                    focusManager = focusManager,
                )
            }
            // TODO Load the motionScheme tokens from the component tokens file
            val fadeInAnimationSpec: FiniteAnimationSpec<Float> =
                MotionSchemeKeyTokens.DefaultEffects.value()
            val fadeOutAnimationSpec: FiniteAnimationSpec<Float> =
                MotionSchemeKeyTokens.FastEffects.value()
            val shrinkExpandAnimationSpec: FiniteAnimationSpec<IntSize> =
                MotionSchemeKeyTokens.DefaultEffects.value()
            androidx.compose.animation.AnimatedVisibility(
                visible = yearPickerVisible,
                modifier = Modifier.clipToBounds(),
                enter =
                    expandVertically(animationSpec = shrinkExpandAnimationSpec) +
                        fadeIn(animationSpec = fadeInAnimationSpec, initialAlpha = 0.6f),
                exit =
                    shrinkVertically(animationSpec = shrinkExpandAnimationSpec) +
                        fadeOut(animationSpec = fadeOutAnimationSpec),
            ) {
                // Apply a paneTitle to make the screen reader focus on a relevant node after this
                // column is hidden and disposed.
                // TODO(b/186443263): Have the screen reader focus on a year in the list when the
                //  list is revealed.
                val yearsPaneTitle = getString(Strings.DatePickerYearPickerPaneTitle)
                Column(modifier = Modifier.semantics { paneTitle = yearsPaneTitle }) {
                    YearPicker(
                        // Keep the height the same as the monthly calendar + weekdays height, and
                        // take into account the thickness of the divider that will be composed
                        // below it.
                        modifier =
                            Modifier.requiredHeight(
                                    RecommendedSizeForAccessibility * (MaxCalendarRows + 1) -
                                        DividerDefaults.Thickness
                                )
                                .padding(horizontal = DatePickerHorizontalPadding),
                        displayedMonthMillis = displayedMonthMillis,
                        onYearSelected = { year ->
                            // Switch back to the monthly calendar and scroll to the selected year.
                            yearPickerVisible = !yearPickerVisible
                            coroutineScope.launch {
                                // Scroll to the selected year (maintaining the month of year).
                                // A LaunchEffect at the MonthsList will take care of rest and will
                                // update the state's displayedMonth to the month we scrolled to.
                                monthsListState.scrollToItem(
                                    (year - yearRange.first) * 12 + displayedMonth.month - 1
                                )
                            }
                        },
                        selectableDates = selectableDates,
                        calendarModel = calendarModel,
                        yearRange = yearRange,
                        colors = colors,
                        currentYearFocusRequester = currentYearFocusRequester,
                        onYearShiftTabPressed = {
                            // Shift + Tab should exit year selection grid and move focus backwards.
                            yearSelectionButtonFocusRequester.requestFocus()
                        },
                        onYearTabPressed = {
                            // Tab should exit year selection grid and move focus forward.
                            dividerFocusRequester.requestFocus()
                            focusManager.moveFocus(FocusDirection.Next)
                        },
                        hazeState = hazeState,
                    )
                    // Make the divider a focus target so that we can properly move keyboard focus
                    // to dismiss/confirm buttons, which we don't have access to from DatePicker.
                    // However, the divider won't ever actually have the focus stay on it, so it'll
                    // be as if it's not focusable when interacting with the picker.
                    HorizontalDivider(
                        color = colors.dividerColor,
                        modifier =
                            Modifier.focusRequester(dividerFocusRequester)
                                .onKeyEvent {
                                    if (
                                        it.key == Key.DirectionUp ||
                                            (it.isShiftPressed && it.key == Key.Tab)
                                    ) {
                                        // If focus is coming from below, move back up.
                                        focusManager.moveFocus(FocusDirection.Previous)
                                        return@onKeyEvent true
                                    } else if (it.key == Key.DirectionDown || it.key == Key.Tab) {
                                        // If focus is coming from above, move forward down.
                                        focusManager.moveFocus(FocusDirection.Next)
                                        return@onKeyEvent true
                                    }
                                    false
                                }
                                .focusTarget(),
                    )
                }
            }
        }
    }
}

/** Composes a horizontal pageable list of months. */
@Composable
private fun HorizontalMonthsList(
    lazyListState: LazyListState,
    selectedDateMillis: Long?,
    onDateSelectionChange: (dateInMillis: Long) -> Unit,
    onDisplayedMonthChange: (monthInMillis: Long) -> Unit,
    calendarModel: CalendarModel,
    yearRange: IntRange,
    dateFormatter: DatePickerFormatter,
    selectableDates: SelectableDates,
    colors: DatePickerColors,
    onReturnFocus: () -> Unit,
    focusManager: FocusManager,
) {
    val today = calendarModel.today
    val firstMonth =
        remember(yearRange) {
            calendarModel.getMonth(
                year = yearRange.first,
                month = 1, // January
            )
        }
    ProvideTextStyle(DatePickerModalTokens.DateLabelTextFont.value) {
        LazyRow(
            // Apply this to prevent the screen reader from scrolling to the next or previous month,
            // and instead, traverse outside the Month composable when swiping from a focused first
            // or last day of the month.
            modifier =
                Modifier.semantics {
                    horizontalScrollAxisRange = ScrollAxisRange(value = { 0f }, maxValue = { 0f })
                },
            state = lazyListState,
            flingBehavior = DatePickerDefaults.rememberSnapFlingBehavior(lazyListState),
        ) {
            items(numberOfMonthsInRange(yearRange)) {
                val month = calendarModel.plusMonths(from = firstMonth, addedMonthsCount = it)
                Box(modifier = Modifier.fillParentMaxWidth()) {
                    Month(
                        month = month,
                        onDateSelectionChange = onDateSelectionChange,
                        todayMillis = today.utcTimeMillis,
                        startDateMillis = selectedDateMillis,
                        endDateMillis = null,
                        rangeSelectionInfo = null,
                        dateFormatter = dateFormatter,
                        selectableDates = selectableDates,
                        colors = colors,
                        locale = calendarModel.locale,
                        lazyListState = lazyListState,
                        focusManager = focusManager,
                        onReturnFocus = onReturnFocus,
                    )
                }
            }
        }
    }

    LaunchedEffect(lazyListState) {
        updateDisplayedMonth(
            lazyListState = lazyListState,
            onDisplayedMonthChange = onDisplayedMonthChange,
            calendarModel = calendarModel,
            yearRange = yearRange,
        )
    }
}

@Composable
private fun YearPicker(
    modifier: Modifier,
    displayedMonthMillis: Long,
    onYearSelected: (year: Int) -> Unit,
    selectableDates: SelectableDates,
    calendarModel: CalendarModel,
    yearRange: IntRange,
    colors: DatePickerColors,
    currentYearFocusRequester: FocusRequester,
    onYearShiftTabPressed: () -> Unit,
    onYearTabPressed: () -> Unit,
    hazeState: HazeState?,
) {
    ProvideTextStyle(value = DatePickerModalTokens.SelectionYearLabelTextFont.value) {
        val currentYear = calendarModel.getMonth(calendarModel.today).year
        val displayedYear = calendarModel.getMonth(displayedMonthMillis).year
        val lazyGridState =
            rememberLazyGridState(
                // Set the initial index to a few years before the current year to allow quicker
                // selection of previous years.
                initialFirstVisibleItemIndex = max(0, displayedYear - yearRange.first - YearsInRow)
            )
        // ponytail: THE one real change vs the vendored upstream copy. Material3 paints the
        // year overlay with `colors.containerColor` — the same (translucent, for haze) color
        // as the dialog surface — so it bleeds through and the calendar shows behind it. When a
        // haze state is supplied and blur is active, give the grid a real haze blur instead:
        // it blurs the app behind the dialog and opaquely covers the calendar. Falls back to the
        // stock opaque background when blur is unsupported/disabled. Mirrors HazeChrome.hazeDialog.
        val blurEnabled = LocalHazeBlurEnabled.current
        val yearOverlayBlurStyle =
            HazeMaterials.regular(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        val yearGridBackground =
            if (hazeState != null && blurEnabled) {
                Modifier.hazeEffect(state = hazeState) {
                    blurEffect { this.style = yearOverlayBlurStyle }
                }
            } else {
                Modifier.background(colors.containerColor)
            }
        LazyVerticalGrid(
            columns = GridCells.Fixed(YearsInRow),
            modifier = modifier.then(yearGridBackground),
            state = lazyGridState,
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(YearsVerticalPadding),
        ) {
            items(yearRange.count()) {
                val selectedYear = it + yearRange.first
                val localizedYear = selectedYear.toLocalString(locale = calendarModel.locale)
                Year(
                    text = localizedYear,
                    modifier =
                        Modifier.requiredSize(
                                width = DatePickerModalTokens.SelectionYearContainerWidth,
                                height = DatePickerModalTokens.SelectionYearContainerHeight,
                            )
                            .onKeyEvent {
                                if (it.isShiftTab) {
                                    onYearShiftTabPressed()
                                    return@onKeyEvent true
                                }
                                if (it.isTab) {
                                    onYearTabPressed()
                                    return@onKeyEvent true
                                }
                                false
                            }
                            .then(
                                if (selectedYear == displayedYear) {
                                    Modifier.focusRequester(currentYearFocusRequester)
                                } else {
                                    Modifier
                                }
                            ),
                    selected = selectedYear == displayedYear,
                    currentYear = selectedYear == currentYear,
                    onClick = { onYearSelected(selectedYear) },
                    enabled = selectableDates.isSelectableYear(selectedYear),
                    description =
                        formatDatePickerNavigateToYearString(
                            getString(Strings.DatePickerNavigateToYearDescription),
                            localizedYear,
                        ),
                    colors = colors,
                )
            }
        }
    }
    // Keyboard focus on the selected year when the year picker opens.
    LaunchedEffect(currentYearFocusRequester) { currentYearFocusRequester.requestFocus() }
}

@Composable
private fun Year(
    text: String,
    modifier: Modifier,
    selected: Boolean,
    currentYear: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    description: String,
    colors: DatePickerColors,
) {
    val border =
        remember(currentYear, selected) {
            if (currentYear && !selected) {
                // Use the day's spec to draw a border around the current year.
                BorderStroke(
                    DatePickerModalTokens.DateTodayContainerOutlineWidth,
                    colors.todayDateBorderColor,
                )
            } else {
                null
            }
        }
    Surface(
        selected = selected,
        onClick = onClick,
        // Apply and merge semantics here. This will ensure that when scrolling the list the entire
        // Year surface is treated as one unit and holds the date semantics even when it's not
        // completely visible atm.
        modifier =
            modifier.semantics(mergeDescendants = true) {
                this.text = AnnotatedString(description)
                this.role = Role.Button
            },
        enabled = enabled,
        shape = DatePickerModalTokens.SelectionYearStateLayerShape.value,
        color = colors.yearContainerColor(selected = selected, enabled = enabled).value,
        border = border,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                // The semantics are set at the Surface level.
                modifier = Modifier.clearAndSetSemantics {},
                color =
                    colors
                        .yearContentColor(
                            currentYear = currentYear,
                            selected = selected,
                            enabled = enabled,
                        )
                        .value,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A composable that shows a year menu button and a couple of buttons that enable navigation between
 * displayed months.
 */
@Composable
private fun MonthsNavigation(
    modifier: Modifier,
    nextAvailable: Boolean,
    previousAvailable: Boolean,
    yearPickerVisible: Boolean,
    yearPickerText: String,
    nextButtonModifier: Modifier,
    onNextClicked: () -> Unit,
    onPreviousClicked: () -> Unit,
    onYearPickerButtonClicked: () -> Unit,
    onYearPickerButtonTabPressed: () -> Unit,
    yearSelectionButtonFocusRequester: FocusRequester,
    colors: DatePickerColors,
) {
    Row(
        modifier = modifier.fillMaxWidth().requiredHeight(MonthYearHeight),
        horizontalArrangement =
            if (yearPickerVisible) {
                Arrangement.Start
            } else {
                Arrangement.SpaceBetween
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A menu button for selecting a year.
        YearPickerMenuButton(
            onClick = onYearPickerButtonClicked,
            expanded = yearPickerVisible,
            modifier =
                Modifier.focusRequester(yearSelectionButtonFocusRequester).onKeyEvent {
                    if (yearPickerVisible && it.isTab) {
                        onYearPickerButtonTabPressed()
                        return@onKeyEvent true
                    }
                    false
                },
        ) {
            Text(
                text = yearPickerText,
                modifier =
                    Modifier.semantics {
                        // Make the screen reader read out updates to the menu button text as
                        // the user navigates the arrows or scrolls to change the displayed
                        // month.
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = yearPickerText
                    },
                color = colors.navigationContentColor,
            )
        }
        // Show arrows for traversing months (only visible when the year selection is off)
        if (!yearPickerVisible) {
            CompositionLocalProvider(LocalContentColor provides colors.navigationContentColor) {
                Row {
                    IconButtonWithTooltip(
                        onClick = onPreviousClicked,
                        enabled = previousAvailable,
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = getString(Strings.DatePickerSwitchToPreviousMonth),
                    )

                    IconButtonWithTooltip(
                        modifier = nextButtonModifier,
                        onClick = onNextClicked,
                        enabled = nextAvailable,
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = getString(Strings.DatePickerSwitchToNextMonth),
                    )
                }
            }
        }
    }
}

// TODO: Replace with the official MenuButton when implemented.
@Composable
private fun YearPickerMenuButton(
    onClick: () -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
        elevation = null,
        border = null,
    ) {
        content()
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription =
                if (expanded) {
                    getString(Strings.DatePickerSwitchToDaySelection)
                } else {
                    getString(Strings.DatePickerSwitchToYearSelection)
                },
            Modifier.rotate(if (expanded) 180f else 0f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconButtonWithTooltip(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

private val DatePickerTitlePadding = PaddingValues(start = 24.dp, end = 12.dp, top = 16.dp)
private val DatePickerHeadlinePadding = PaddingValues(start = 24.dp, end = 12.dp, bottom = 12.dp)

private val YearsVerticalPadding = 16.dp

private const val MaxCalendarRows = 6
private const val YearsInRow: Int = 3

private val KeyEvent.isShiftTab: Boolean
    get() = isShiftPressed && type == KeyEventType.KeyDown && key == Key.Tab
private val KeyEvent.isTab: Boolean
    get() = !isShiftPressed && type == KeyEventType.KeyDown && key == Key.Tab
