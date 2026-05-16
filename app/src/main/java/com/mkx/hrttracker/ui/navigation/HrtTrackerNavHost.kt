package com.mkx.hrttracker.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.ui.calibration.CalibrationEditorScreen
import com.mkx.hrttracker.ui.calibration.CalibrationEditorViewModel
import com.mkx.hrttracker.ui.calibration.CalibrationScreen
import com.mkx.hrttracker.ui.calibration.CalibrationUnitsScreen
import com.mkx.hrttracker.ui.history.HistoryScreen
import com.mkx.hrttracker.ui.log.AddEntryEditSnapshot
import com.mkx.hrttracker.ui.log.AddEntryQuickLogRequest
import com.mkx.hrttracker.ui.log.AddEntryScreen
import com.mkx.hrttracker.ui.main.MainEditEntryRequest
import com.mkx.hrttracker.ui.main.MainScreen
import com.mkx.hrttracker.ui.plan.ArchivedMedicationGroupsScreen
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorScreen
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorViewModel
import com.mkx.hrttracker.ui.plan.PlanBatchAddScreen
import com.mkx.hrttracker.ui.plan.PlanScreen
import com.mkx.hrttracker.ui.settings.SettingsScreen
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

sealed class Screen(val route: String, @get:StringRes val label: Int) {
    data object Main : Screen("main", R.string.tab_main)
    data object Plan : Screen("plan", R.string.tab_plan)
    data object PlanBatchAdd : Screen(
        "plan_batch_add?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.plan_batch_add_title
    ) {
        const val baseRoute = "plan_batch_add"

        fun createRoute(topLevelParentRoute: String = Plan.route): String {
            return "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
        }
    }
    data object PlanArchivedGroups : Screen(
        "plan_archived_groups?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.plan_archived_groups
    ) {
        const val baseRoute = "plan_archived_groups"

        fun createRoute(topLevelParentRoute: String = Plan.route): String {
            return "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
        }
    }
    data object History : Screen(
        "history?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.tab_history
    ) {
        const val baseRoute = "history"

        fun createRoute(topLevelParentRoute: String = Plan.route): String {
            return "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
        }
    }
    data object Settings : Screen("settings", R.string.tab_settings)
    data object SettingsCalibration : Screen(
        "settings_calibration?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.settings_personalization_calibration
    ) {
        const val baseRoute = "settings_calibration"

        fun createRoute(
            topLevelParentRoute: String,
        ): String {
            return "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
        }
    }
    data object SettingsCalibrationUnits : Screen(
        "settings_calibration_units?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.settings_calibration_settings
    ) {
        const val baseRoute = "settings_calibration_units"

        fun createRoute(
            topLevelParentRoute: String,
        ): String {
            return "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
        }
    }
    data object SettingsCalibrationEntry : Screen(
        "settings_calibration_entry?" +
            "${CalibrationEditorViewModel.PANEL_ID_ARG}={${CalibrationEditorViewModel.PANEL_ID_ARG}}" +
            "&$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.settings_calibration_add_result
    ) {
        const val baseRoute = "settings_calibration_entry"

        fun createRoute(
            topLevelParentRoute: String,
            panelId: String? = null,
        ): String {
            return if (panelId == null) {
                "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
            } else {
                "$baseRoute?${CalibrationEditorViewModel.PANEL_ID_ARG}=$panelId&$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
            }
        }
    }

    data object EditMedicationGroup : Screen(
        "edit_medication_group?" +
            "${MedicationGroupEditorViewModel.GROUP_ID_ARG}={${MedicationGroupEditorViewModel.GROUP_ID_ARG}}" +
            "&$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}" +
            "&$MEDICATION_GROUP_EDITOR_SOURCE_ARG={$MEDICATION_GROUP_EDITOR_SOURCE_ARG}",
        R.string.add_medication_group
    ) {
        const val baseRoute = "edit_medication_group"

        fun createRoute(
            topLevelParentRoute: String,
            groupId: String? = null,
            source: String? = null,
        ): String {
            val queryParameters = buildList {
                groupId?.let { add("${MedicationGroupEditorViewModel.GROUP_ID_ARG}=$it") }
                add("$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute")
                source?.let { add("$MEDICATION_GROUP_EDITOR_SOURCE_ARG=$it") }
            }
            return "$baseRoute?${queryParameters.joinToString("&")}"
        }
    }

    companion object {
        fun topLevelScreenForRoute(route: String?): Screen? {
            return when (route) {
                Main.route -> Main
                Plan.route -> Plan
                Settings.route -> Settings
                else -> null
            }
        }
    }
}

internal data class NavigationItemContent(
    val screen: Screen,
    @param:DrawableRes val icon: Int,
)

internal val topLevelNavigationItems = listOf(
    NavigationItemContent(Screen.Main, R.drawable.ic_home),
    NavigationItemContent(Screen.Plan, R.drawable.ic_calendar_month),
    NavigationItemContent(Screen.Settings, R.drawable.ic_settings)
)

internal enum class TopLevelNavigationTapAction {
    NAVIGATE,
    POP_TO_TOP_LEVEL,
    SCROLL_TO_TOP,
    NONE,
}

internal enum class TopLevelRootBackAction {
    NAVIGATE_HOME,
    NONE,
}

internal fun topLevelNavigationTapAction(
    tappedScreen: Screen,
    selectedBottomScreen: Screen?,
    currentRoute: String?,
): TopLevelNavigationTapAction {
    val isOnChildOfSelectedTopLevel =
        selectedBottomScreen == tappedScreen &&
            currentRoute != null &&
            currentRoute != tappedScreen.route

    return when {
        isOnChildOfSelectedTopLevel -> TopLevelNavigationTapAction.POP_TO_TOP_LEVEL
        currentRoute == tappedScreen.route -> TopLevelNavigationTapAction.SCROLL_TO_TOP
        currentRoute != tappedScreen.route -> TopLevelNavigationTapAction.NAVIGATE
        else -> TopLevelNavigationTapAction.NONE
    }
}

internal fun topLevelNavigationReplacementPopUpToRoute(
    targetScreen: Screen,
    selectedBottomScreen: Screen?,
): String? {
    return selectedBottomScreen
        ?.takeIf { screen -> screen != targetScreen }
        ?.route
}

internal fun topLevelRootBackAction(
    selectedBottomScreen: Screen?,
    currentRoute: String?,
): TopLevelRootBackAction {
    return if (
        selectedBottomScreen != null &&
        selectedBottomScreen != Screen.Main &&
        currentRoute == selectedBottomScreen.route
    ) {
        TopLevelRootBackAction.NAVIGATE_HOME
    } else {
        TopLevelRootBackAction.NONE
    }
}

@Composable
fun HrtTrackerNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    var addEntrySheetRequest by rememberSaveable(stateSaver = AddEntrySheetRequestSaver) {
        mutableStateOf<AddEntrySheetRequest?>(null)
    }
    var mainScrollToTopSignal by remember { mutableIntStateOf(0) }
    var planScrollToTopSignal by remember { mutableIntStateOf(0) }
    var settingsScrollToTopSignal by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = currentBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val explicitParentRoute = currentBackStackEntry?.arguments?.getString(TOP_LEVEL_PARENT_ARG)

    val selectedBottomScreen =
        Screen.topLevelScreenForRoute(explicitParentRoute)
            ?: topLevelNavigationItems.firstOrNull { navItem ->
                currentDestination?.hierarchy?.any { it.route == navItem.screen.route } == true
            }?.screen
            ?: Screen.Main

    BackHandler(
        enabled = topLevelRootBackAction(
            selectedBottomScreen = selectedBottomScreen,
            currentRoute = currentRoute,
        ) == TopLevelRootBackAction.NAVIGATE_HOME
    ) {
        navController.navigateToTopLevelScreen(
            targetScreen = Screen.Main,
            selectedBottomScreen = selectedBottomScreen,
        )
    }

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            topLevelNavigationItems.forEach { navItem ->
                item(
                    selected = selectedBottomScreen == navItem.screen,
                    onClick = {
                        when (
                            topLevelNavigationTapAction(
                                tappedScreen = navItem.screen,
                                selectedBottomScreen = selectedBottomScreen,
                                currentRoute = currentRoute,
                            )
                        ) {
                            TopLevelNavigationTapAction.POP_TO_TOP_LEVEL -> {
                                navController.popBackStack(navItem.screen.route, false)
                            }

                            TopLevelNavigationTapAction.SCROLL_TO_TOP -> {
                                when (navItem.screen) {
                                    Screen.Main -> mainScrollToTopSignal++
                                    Screen.Plan -> planScrollToTopSignal++
                                    Screen.Settings -> settingsScrollToTopSignal++
                                    else -> Unit
                                }
                            }

                            TopLevelNavigationTapAction.NAVIGATE -> {
                                navController.navigateToTopLevelScreen(
                                    targetScreen = navItem.screen,
                                    selectedBottomScreen = selectedBottomScreen,
                                )
                            }

                            TopLevelNavigationTapAction.NONE -> Unit
                        }
                    },
                    icon = {
                        Icon(
                            painter = painterResource(navItem.icon),
                            contentDescription = stringResource(navItem.screen.label)
                        )
                    },
                    label = {
                        Text(text = stringResource(navItem.screen.label))
                    }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Main.route,
            enterTransition = { hrtNavHostEnterTransition(density, layoutDirection) },
            exitTransition = { hrtNavHostExitTransition(density, layoutDirection) },
            popEnterTransition = { hrtNavHostPopEnterTransition(density, layoutDirection) },
            popExitTransition = { hrtNavHostPopExitTransition(density, layoutDirection) },
        ) {
            composable(Screen.Main.route, sizeTransform = hrtSizeTransform) {
                MainScreen(
                    modifier,
                    scrollToTopSignal = mainScrollToTopSignal,
                    onEntryClick = { request ->
                        addEntrySheetRequest = AddEntrySheetRequest(
                            entryIds = request.entryUuids.map(UUID::toString),
                            editSnapshot = request.toAddEntryEditSnapshot(),
                        )
                    },
                    onAddEntryClick = {
                        addEntrySheetRequest = AddEntrySheetRequest(entryIds = emptyList())
                    },
                    onQuickLogDoseClick = { request ->
                        if (request.medicationCount > 0) {
                            addEntrySheetRequest = AddEntrySheetRequest(
                                quickLogRequest = AddEntryQuickLogRequest(
                                    groupId = request.groupUuid,
                                    scheduleTimeUuid = request.scheduleTimeUuid,
                                    scheduledFor = request.scheduledAt,
                                    medicationDetails = request.medicationDetails,
                                    medicationCount = request.medicationCount,
                                    sourceGroupName = request.sourceGroupName,
                                    sourceGroupColorKey = request.sourceGroupColorKey,
                                    sourceGroupPreviousScheduledFor = request.sourceGroupPreviousScheduledFor,
                                    sourceGroupNextScheduledFor = request.sourceGroupNextScheduledFor,
                                )
                            )
                        }
                    }
                )
            }
            composable(Screen.Plan.route, sizeTransform = hrtSizeTransform) {
                PlanScreen(
                    modifier = modifier,
                    scrollToTopSignal = planScrollToTopSignal,
                    onGroupClick = { groupId ->
                        navController.navigate(
                            Screen.EditMedicationGroup.createRoute(
                                topLevelParentRoute = Screen.Plan.route,
                                groupId = groupId.toString()
                            )
                        )
                    },
                    onEntryClick = { entryIds ->
                        addEntrySheetRequest = AddEntrySheetRequest(
                            entryIds = entryIds.map(UUID::toString)
                        )
                    },
                    onQuickLogClick = { groupId, scheduleTimeUuid, scheduledAt, medicationDetails, medicationCount ->
                        if (medicationCount > 0) {
                            addEntrySheetRequest = AddEntrySheetRequest(
                                quickLogRequest = AddEntryQuickLogRequest(
                                    groupId = groupId,
                                    scheduleTimeUuid = scheduleTimeUuid,
                                    scheduledFor = scheduledAt,
                                    medicationDetails = medicationDetails,
                                    medicationCount = medicationCount
                                )
                            )
                        }
                    },
                    onAddGroupClick = {
                        navController.navigate(
                            Screen.EditMedicationGroup.createRoute(
                                topLevelParentRoute = Screen.Plan.route
                            )
                        )
                    },
                    onHistoryClick = {
                        navController.navigate(Screen.History.createRoute(Screen.Plan.route)) {
                            launchSingleTop = true
                        }
                    },
                    onBatchAddClick = {
                        navController.navigate(Screen.PlanBatchAdd.createRoute(Screen.Plan.route)) {
                            launchSingleTop = true
                        }
                    },
                    onArchivedGroupsClick = {
                        navController.navigate(Screen.PlanArchivedGroups.createRoute(Screen.Plan.route)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = Screen.PlanBatchAdd.route,
                arguments = listOf(
                    navArgument(TOP_LEVEL_PARENT_ARG) {
                        type = NavType.StringType
                        defaultValue = Screen.Plan.route
                    }
                ),
                sizeTransform = hrtSizeTransform,
            ) {
                PlanBatchAddScreen(
                    modifier = modifier,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Screen.PlanArchivedGroups.route,
                arguments = listOf(
                    navArgument(TOP_LEVEL_PARENT_ARG) {
                        type = NavType.StringType
                        defaultValue = Screen.Plan.route
                    }
                ),
                sizeTransform = hrtSizeTransform,
            ) {
                ArchivedMedicationGroupsScreen(
                    modifier = modifier,
                    onNavigateBack = { navController.popBackStack() },
                    onGroupClick = { groupId ->
                        navController.navigate(
                            Screen.EditMedicationGroup.createRoute(
                                topLevelParentRoute = Screen.Plan.route,
                                groupId = groupId.toString(),
                                source = MEDICATION_GROUP_EDITOR_SOURCE_ARCHIVED_GROUPS,
                            )
                        )
                    }
                )
            }
            composable(
                route = Screen.History.route,
                arguments = listOf(
                    navArgument(TOP_LEVEL_PARENT_ARG) {
                        type = NavType.StringType
                        defaultValue = Screen.Plan.route
                    }
                ),
                sizeTransform = hrtSizeTransform,
            ) {
                HistoryScreen(
                    modifier = modifier,
                    onNavigateBack = { navController.popBackStack() },
                    onEntryClick = { entryIds ->
                        addEntrySheetRequest = AddEntrySheetRequest(
                            entryIds = entryIds.map(UUID::toString)
                        )
                    }
                )
            }
            composable(Screen.Settings.route, sizeTransform = hrtSizeTransform) {
                SettingsScreen(
                    modifier = modifier,
                    scrollToTopSignal = settingsScrollToTopSignal,
                    onCalibrationClick = {
                        navController.navigate(
                            Screen.SettingsCalibration.createRoute(Screen.Settings.route)
                        )
                    }
                )
            }
            composable(
                route = Screen.SettingsCalibration.route,
                arguments = listOf(
                    navArgument(TOP_LEVEL_PARENT_ARG) {
                        type = NavType.StringType
                        defaultValue = Screen.Settings.route
                    }
                ),
                sizeTransform = hrtSizeTransform,
            ) {
                CalibrationScreen(
                    modifier = modifier,
                    onNavigateBack = { navController.popBackStack() },
                    onUnitsClick = {
                        navController.navigate(
                            Screen.SettingsCalibrationUnits.createRoute(Screen.Settings.route)
                        )
                    },
                    onAddClick = {
                        navController.navigate(
                            Screen.SettingsCalibrationEntry.createRoute(Screen.Settings.route)
                        )
                    },
                    onPanelClick = { panelUuid ->
                        navController.navigate(
                            Screen.SettingsCalibrationEntry.createRoute(
                                topLevelParentRoute = Screen.Settings.route,
                                panelId = panelUuid.toString()
                            )
                        )
                    }
                )
            }
            composable(
                route = Screen.SettingsCalibrationUnits.route,
                arguments = listOf(
                    navArgument(TOP_LEVEL_PARENT_ARG) {
                        type = NavType.StringType
                        defaultValue = Screen.Settings.route
                    }
                ),
                sizeTransform = hrtSizeTransform,
            ) {
                CalibrationUnitsScreen(
                    modifier = modifier,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Screen.SettingsCalibrationEntry.route,
                arguments = listOf(
                    navArgument(CalibrationEditorViewModel.PANEL_ID_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(TOP_LEVEL_PARENT_ARG) {
                        type = NavType.StringType
                        defaultValue = Screen.Settings.route
                    }
                ),
                sizeTransform = hrtSizeTransform,
            ) {
                CalibrationEditorScreen(
                    modifier = modifier,
                    onNavigateBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.EditMedicationGroup.route,
                arguments = listOf(
                    navArgument(MedicationGroupEditorViewModel.GROUP_ID_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(TOP_LEVEL_PARENT_ARG) {
                        type = NavType.StringType
                        defaultValue = Screen.Plan.route
                    },
                    navArgument(MEDICATION_GROUP_EDITOR_SOURCE_ARG) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
                sizeTransform = hrtSizeTransform,
            ) { backStackEntry ->
                val openedFromArchivedGroupsPage =
                    backStackEntry.arguments?.getString(MEDICATION_GROUP_EDITOR_SOURCE_ARG) ==
                        MEDICATION_GROUP_EDITOR_SOURCE_ARCHIVED_GROUPS
                MedicationGroupEditorScreen(
                    modifier = modifier,
                    onNavigateBack = { navController.popBackStack() },
                    onGroupSaved = { navController.popBackStack() },
                    onGroupSavedToPlan = {
                        if (!navController.popBackStack(Screen.Plan.route, inclusive = false)) {
                            navController.navigate(Screen.Plan.route) {
                                launchSingleTop = true
                            }
                        }
                    },
                    openedFromArchivedGroupsPage = openedFromArchivedGroupsPage,
                )
            }
        }
    }

    addEntrySheetRequest?.let { request ->
        AddEntryScreen(
            entryIds = request.entryIds,
            quickLogRequest = request.quickLogRequest,
            editSnapshot = request.editSnapshot,
            onDismissRequest = { addEntrySheetRequest = null },
            onEntrySaved = { addEntrySheetRequest = null }
        )
    }
}

private fun NavHostController.navigateToTopLevelScreen(
    targetScreen: Screen,
    selectedBottomScreen: Screen?,
) {
    navigate(targetScreen.route) {
        topLevelNavigationReplacementPopUpToRoute(
            targetScreen = targetScreen,
            selectedBottomScreen = selectedBottomScreen,
        )?.let { popUpToRoute ->
            popUpTo(popUpToRoute) {
                inclusive = true
                saveState = true
            }
        }
        launchSingleTop = true
        restoreState = true
    }
}

private const val TOP_LEVEL_PARENT_ARG = "topLevelParent"
private const val MEDICATION_GROUP_EDITOR_SOURCE_ARG = "source"
private const val MEDICATION_GROUP_EDITOR_SOURCE_ARCHIVED_GROUPS = "archivedGroups"

internal data class AddEntrySheetRequest(
    val entryIds: List<String> = emptyList(),
    val quickLogRequest: AddEntryQuickLogRequest? = null,
    val editSnapshot: AddEntryEditSnapshot? = null,
)

internal val AddEntrySheetRequestSaver: Saver<AddEntrySheetRequest?, Any> = Saver(
    save = { request -> request?.let(::saveAddEntrySheetRequest) ?: SAVED_REQUEST_ABSENT },
    restore = { saved ->
        if (saved == SAVED_REQUEST_ABSENT) null else restoreAddEntrySheetRequest(saved)
    }
)

private const val SAVED_REQUEST_ABSENT = "absent"

private fun saveAddEntrySheetRequest(request: AddEntrySheetRequest): ArrayList<Any?> {
    return arrayListOf<Any?>(
        ArrayList(request.entryIds),
        request.quickLogRequest?.let(::saveQuickLogRequest),
        request.editSnapshot?.let(::saveEditSnapshot),
    )
}

@Suppress("UNCHECKED_CAST")
private fun restoreAddEntrySheetRequest(saved: Any): AddEntrySheetRequest {
    val list = saved as ArrayList<Any?>
    return AddEntrySheetRequest(
        entryIds = (list[0] as? ArrayList<String>).orEmpty().toList(),
        quickLogRequest = list[1]?.let(::restoreQuickLogRequest),
        editSnapshot = list.getOrNull(2)?.let(::restoreEditSnapshot),
    )
}

private fun MainEditEntryRequest.toAddEntryEditSnapshot(): AddEntryEditSnapshot? {
    if (snapshotEntries.isEmpty()) {
        return null
    }

    return AddEntryEditSnapshot(
        entries = snapshotEntries,
        sourceGroupName = sourceGroupName,
        sourceGroupColorKey = sourceGroupColorKey,
        sourceGroupPreviousScheduledFor = sourceGroupPreviousScheduledFor,
        sourceGroupNextScheduledFor = sourceGroupNextScheduledFor,
    )
}

private fun saveQuickLogRequest(request: AddEntryQuickLogRequest): ArrayList<Any?> {
    return arrayListOf(
        request.groupId.toString(),
        request.scheduleTimeUuid?.toString(),
        request.scheduledFor.toString(),
        saveMedicationDetails(request.medicationDetails),
        request.medicationCount,
        request.sourceGroupName,
        request.sourceGroupColorKey?.name,
        request.sourceGroupPreviousScheduledFor?.toString(),
        request.sourceGroupNextScheduledFor?.toString(),
    )
}

@Suppress("UNCHECKED_CAST")
private fun restoreQuickLogRequest(saved: Any): AddEntryQuickLogRequest {
    val list = saved as ArrayList<Any?>
    return AddEntryQuickLogRequest(
        groupId = UUID.fromString(list[0] as String),
        scheduleTimeUuid = (list[1] as? String)?.let(UUID::fromString),
        scheduledFor = LocalDateTime.parse(list[2] as String),
        medicationDetails = restoreMedicationDetails(list[3]!!),
        medicationCount = list[4] as Int,
        sourceGroupName = list.getOrNull(5) as? String,
        sourceGroupColorKey = (list.getOrNull(6) as? String)?.let(::restoreMedicationGroupColorKey),
        sourceGroupPreviousScheduledFor = (list.getOrNull(7) as? String)?.let(LocalDateTime::parse),
        sourceGroupNextScheduledFor = (list.getOrNull(8) as? String)?.let(LocalDateTime::parse),
    )
}

private fun restoreMedicationGroupColorKey(value: String): MedicationGroupColorKey? {
    return MedicationGroupColorKey.entries.firstOrNull { colorKey -> colorKey.name == value }
}

private fun saveEditSnapshot(snapshot: AddEntryEditSnapshot): ArrayList<Any?> {
    return arrayListOf(
        ArrayList(snapshot.entries.map(::saveMedicationLogEntry)),
        snapshot.sourceGroupName,
        snapshot.sourceGroupColorKey?.name,
        snapshot.sourceGroupPreviousScheduledFor?.toString(),
        snapshot.sourceGroupNextScheduledFor?.toString(),
    )
}

private fun restoreEditSnapshot(saved: Any): AddEntryEditSnapshot {
    val list = saved as ArrayList<Any?>
    return AddEntryEditSnapshot(
        entries = (list[0] as? ArrayList<*>)
            .orEmpty()
            .mapNotNull { entry -> entry?.let(::restoreMedicationLogEntry) },
        sourceGroupName = list.getOrNull(1) as? String,
        sourceGroupColorKey = (list.getOrNull(2) as? String)?.let(::restoreMedicationGroupColorKey),
        sourceGroupPreviousScheduledFor = (list.getOrNull(3) as? String)?.let(LocalDateTime::parse),
        sourceGroupNextScheduledFor = (list.getOrNull(4) as? String)?.let(LocalDateTime::parse),
    )
}

private fun saveMedicationLogEntry(entry: MedicationLogEntry): ArrayList<Any?> {
    return arrayListOf(
        entry.uuid.toString(),
        saveMedicationDetails(entry.details),
        entry.dosageMgAsEstradiol,
        entry.sourceGroupUuid?.toString(),
        entry.appliedAt.toEpochMilli(),
        entry.appliedAtTimeZoneId,
        entry.scheduledFor?.toString(),
        entry.count,
        entry.scheduleTimeUuid?.toString(),
    )
}

private fun restoreMedicationLogEntry(saved: Any): MedicationLogEntry {
    val list = saved as ArrayList<Any?>
    return MedicationLogEntry(
        uuid = UUID.fromString(list[0] as String),
        details = restoreMedicationDetails(list[1]!!),
        dosageMgAsEstradiol = list[2] as? Double,
        sourceGroupUuid = (list[3] as? String)?.let(UUID::fromString),
        appliedAt = Instant.ofEpochMilli(list[4] as Long),
        appliedAtTimeZoneId = list[5] as String,
        scheduledFor = (list[6] as? String)?.let(LocalDateTime::parse),
        count = list[7] as Int,
        scheduleTimeUuid = (list[8] as? String)?.let(UUID::fromString),
    )
}

private fun saveMedicationDetails(details: MedicationDetails): ArrayList<Any?> {
    return arrayListOf(
        details.category.name,
        details.applicationType.name,
        saveMedicationSelection(details.selection),
        saveMedicationDose(details.dose),
        details.gelApplicationArea.name,
        details.customDoseUnit.name,
    )
}

@Suppress("UNCHECKED_CAST")
private fun restoreMedicationDetails(saved: Any): MedicationDetails {
    val list = saved as ArrayList<Any?>
    return MedicationDetails(
        category = MedicationCategory.fromStorageValue(list[0] as String),
        applicationType = MedicationApplicationType.fromStorageValue(list[1] as String),
        selection = restoreMedicationSelection(list[2]!!),
        dose = restoreMedicationDose(list[3]!!),
        gelApplicationArea = MedicationGelApplicationArea.fromStorageValue(list[4] as String),
        customDoseUnit = MedicationDoseUnit.fromStorageValue(list[5] as String),
    )
}

private fun saveMedicationSelection(selection: MedicationSelection): ArrayList<Any?> {
    return when (selection) {
        is MedicationSelection.Catalog -> arrayListOf(selection.kind.name, selection.medicationKey.name)
        is MedicationSelection.Custom -> arrayListOf(selection.kind.name, selection.medicationName)
    }
}

@Suppress("UNCHECKED_CAST")
private fun restoreMedicationSelection(saved: Any): MedicationSelection {
    val list = saved as ArrayList<Any?>
    return when (MedicationSelectionKind.fromStorageValue(list[0] as String)) {
        MedicationSelectionKind.CATALOG -> MedicationSelection.Catalog(
            medicationKey = MedicationKey.fromStorageValue(list[1] as String)
                ?: error("Unknown medication key: ${list[1]}")
        )
        MedicationSelectionKind.CUSTOM -> MedicationSelection.Custom(
            medicationName = list[1] as String
        )
    }
}

private fun saveMedicationDose(dose: MedicationDose): ArrayList<Any?> {
    return when (dose) {
        is MedicationDose.MgAsMedicine -> arrayListOf(dose.kind.name, dose.valueMg)
        is MedicationDose.GelEquivalentEstradiolMg -> arrayListOf(dose.kind.name, dose.valueMg)
        is MedicationDose.GelPercentAndWeight -> arrayListOf(dose.kind.name, dose.percent, dose.weightGrams)
        is MedicationDose.PatchTotalMg -> arrayListOf(dose.kind.name, dose.valueMg)
        is MedicationDose.PatchReleaseRateMcgPerDay -> arrayListOf(dose.kind.name, dose.valueMcgPerDay)
        MedicationDose.None -> arrayListOf(dose.kind.name)
    }
}

@Suppress("UNCHECKED_CAST")
private fun restoreMedicationDose(saved: Any): MedicationDose {
    val list = saved as ArrayList<Any?>
    return when (MedicationDoseKind.fromStorageValue(list[0] as String)) {
        MedicationDoseKind.MG_AS_MEDICINE -> MedicationDose.MgAsMedicine(list[1] as Double)
        MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG ->
            MedicationDose.GelEquivalentEstradiolMg(list[1] as Double)
        MedicationDoseKind.GEL_PERCENT_AND_WEIGHT ->
            MedicationDose.GelPercentAndWeight(list[1] as Double, list[2] as Double)
        MedicationDoseKind.PATCH_TOTAL_MG -> MedicationDose.PatchTotalMg(list[1] as Double)
        MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY ->
            MedicationDose.PatchReleaseRateMcgPerDay(list[1] as Double)
        MedicationDoseKind.NONE -> MedicationDose.None
    }
}
