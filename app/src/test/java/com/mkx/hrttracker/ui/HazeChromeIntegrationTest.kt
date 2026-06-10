package com.mkx.hrttracker.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HazeChromeIntegrationTest {
    @Test
    fun top_app_bars_use_haze_chrome_and_transparent_haze_colors() {
        val uiDir = File("src/main/java/com/mkx/hrttracker/ui")
        require(uiDir.isDirectory) {
            "Expected UI source directory at ${uiDir.absolutePath}; unit tests must run with " +
                    "the app module as the working directory."
        }

        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> TOP_APP_BAR_CALL.containsMatchIn(file.readText()) }
            .mapNotNull { file ->
                val text = file.readText()
                val missing = buildList {
                    if (!text.contains(".hazeTopAppBar(")) add("hazeTopAppBar modifier")
                    if (!text.contains("hazeTopAppBarColors()")) add("transparent haze colors")
                }
                if (missing.isEmpty()) {
                    null
                } else {
                    "${file.relativeTo(uiDir)} missing ${missing.joinToString(" and ")}"
                }
            }
            .toList()

        assertTrue(
            "Every TopAppBar/CenterAlignedTopAppBar should render with Haze chrome:\n" +
                    offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun top_app_bar_haze_transparent_colors_preserve_theme_rgb_channels() {
        val hazeChromeText =
            File("src/main/java/com/mkx/hrttracker/ui/components/HazeChrome.kt").readText()

        assertTrue(
            "Top app bar Haze colors should keep the default theme RGB channels and only " +
                    "clear alpha. Animating from Color.Transparent uses transparent black, " +
                    "which can flash black when blur is toggled off.",
            hazeChromeText.contains("copy(alpha = 0f)") &&
                    !hazeChromeText.contains("containerColor = Color.Transparent") &&
                    !hazeChromeText.contains("scrolledContainerColor = Color.Transparent"),
        )
    }

    @Test
    fun top_app_bars_reset_material_color_animation_when_haze_setting_changes() {
        val uiDir = File("src/main/java/com/mkx/hrttracker/ui")
        require(uiDir.isDirectory) {
            "Expected UI source directory at ${uiDir.absolutePath}; unit tests must run with " +
                    "the app module as the working directory."
        }

        val hazeChromeText =
            File("src/main/java/com/mkx/hrttracker/ui/components/HazeChrome.kt").readText()
        assertTrue(
            "Haze top app bars need a keyed wrapper because Material3 animates the " +
                    "container color internally. Without resetting that animation on blur " +
                    "setting changes, on -> off can animate up from transparent after the " +
                    "Haze effect has been removed.",
            hazeChromeText.contains("fun HazeTopAppBarColorReset(") &&
                    hazeChromeText.contains("key(LocalHazeBlurEnabled.current)"),
        )

        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> TOP_APP_BAR_CALL.containsMatchIn(file.readText()) }
            .mapNotNull { file ->
                val text = file.readText()
                if (text.contains("HazeTopAppBarColorReset {")) {
                    null
                } else {
                    "${file.relativeTo(uiDir)} missing HazeTopAppBarColorReset wrapper"
                }
            }
            .toList()

        assertTrue(
            "Every Haze TopAppBar/CenterAlignedTopAppBar should reset its Material " +
                    "container color animation when the blur setting changes:\n" +
                    offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun top_app_bar_blur_follows_default_scrolled_overlap_state() {
        val uiDir = File("src/main/java/com/mkx/hrttracker/ui")
        require(uiDir.isDirectory) {
            "Expected UI source directory at ${uiDir.absolutePath}; unit tests must run with " +
                    "the app module as the working directory."
        }

        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> TOP_APP_BAR_CALL.containsMatchIn(file.readText()) }
            .mapNotNull { file ->
                val text = file.readText()
                val missing = buildList {
                    if (!text.contains(".hazeTopAppBar(scrollBehavior)")) {
                        add("Haze blur does not track the scroll overlap via hazeTopAppBar(scrollBehavior)")
                    }
                    if (text.contains("TopAppBarDefaults.pinnedScrollBehavior(")) {
                        add(
                            "pinned scroll behavior bypasses pinnedTopAppBarScrollBehavior, " +
                                    "losing the content-offset reset when content shrinks back " +
                                    "to its start"
                        )
                    }
                    if (text.contains("contentOffset = 0f")) {
                        add(
                            "manually zeroes TopAppBarState.contentOffset, snapping the " +
                                    "overlap-driven bar chrome off in one frame; " +
                                    "pinnedTopAppBarScrollBehavior settles stale offsets " +
                                    "with an eased fade instead"
                        )
                    }
                    if (
                        text.contains("val scrollState = rememberScrollState()") &&
                        text.contains("pinnedTopAppBarScrollBehavior(") &&
                        !text.contains("scrollState = scrollState")
                    ) {
                        add("ScrollState-backed top app bar is not using pinnedTopAppBarScrollBehavior(scrollState = ...)")
                    }
                }
                if (missing.isEmpty()) {
                    null
                } else {
                    "${file.relativeTo(uiDir)}: ${missing.joinToString("; ")}"
                }
            }
            .toList()

        val hazeChromeText =
            File("src/main/java/com/mkx/hrttracker/ui/components/HazeChrome.kt").readText()
        assertTrue(
            "Haze top app bars should use Material3's default scrolled threshold and fade " +
                    "their chrome with the actual overlap (blur opacity when blur is on, a " +
                    "container-color lerp when off) so fast flings never show unstyled " +
                    "content behind the bar.",
            hazeChromeText.contains("TopAppBarScrolledOverlapThreshold = 0.01f") &&
                    hazeChromeText.contains(
                        "scrollBehavior.state.overlappedFraction > TopAppBarScrolledOverlapThreshold"
                    ) &&
                    hazeChromeText.contains(
                        "alpha = { topAppBarOverlapAlpha(scrollBehavior.state.overlappedFraction) }"
                    ) &&
                    hazeChromeText.contains("fun topAppBarOverlapAlpha(") &&
                    hazeChromeText.contains("topAppBarOverlapAlpha(scrollBehavior.state.overlappedFraction),"),
        )
        assertTrue(
            "Top app bar blur should remain disabled until content actually scrolls:\n" +
                    offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun top_app_bar_backdrop_content_draws_behind_top_chrome() {
        val uiDir = File("src/main/java/com/mkx/hrttracker/ui")
        val contentContainerFile =
            File("src/main/java/com/mkx/hrttracker/ui/components/AppContentContainer.kt")
        val navigationScaffoldFile =
            File("src/main/java/com/mkx/hrttracker/ui/navigation/EdgeToEdgeNavigationSuiteScaffold.kt")

        assertTrue(
            "AppContentContainer should expose routed body content as the Haze source.",
            contentContainerFile.readText().contains("hazeSourceArea(LocalChromeHazeState.current)"),
        )
        assertTrue(
            "The route-level scaffold must not reuse the top-app-bar Haze source; top " +
                    "chrome needs AppContentContainer's per-screen body source so it does " +
                    "not become a child of the same Haze source it samples.",
            !navigationScaffoldFile.readText().contains("hazeSourceArea(LocalChromeHazeState.current)"),
        )
        val navHostText =
            File("src/main/java/com/mkx/hrttracker/ui/navigation/HrtTrackerNavHost.kt").readText()
        assertTrue(
            "Top app bars need per-destination Haze state. A single top chrome state around " +
                    "the whole NavHost lets outgoing and incoming pages register source layers " +
                    "at the same time, which can make the top blur sample stale transition content.",
            navHostText.contains("RoutedTopChromeHazeProvider") &&
                    navHostText.contains("val routeTopChromeHazeState = rememberChromeHazeState()") &&
                    navHostText.contains("LocalChromeHazeState provides routeTopChromeHazeState") &&
                    !navHostText.contains("val topChromeHazeState = rememberChromeHazeState()"),
        )
        assertTrue(
            "Every NavHost destination should be wrapped in RoutedTopChromeHazeProvider.",
            Regex("""\bcomposable\(""").findAll(navHostText).count() ==
                    Regex("""RoutedTopChromeHazeProvider \{""").findAll(navHostText).count(),
        )

        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> TOP_APP_BAR_CALL.containsMatchIn(file.readText()) }
            .mapNotNull { file ->
                val text = file.readText()
                val missing = buildList {
                    if (text.contains("AppContentContainer(modifier = Modifier.padding(innerPadding))")) {
                        add("body viewport still starts below the top app bar")
                    }
                    if (text.contains("appContentPaddingValues()") &&
                        !text.contains("appContentPaddingValuesBehindTopAppBar(innerPadding)")
                    ) {
                        add("scrollable content padding does not include top app bar inset")
                    }
                }
                if (missing.isEmpty()) {
                    null
                } else {
                    "${file.relativeTo(uiDir)}: ${missing.joinToString("; ")}"
                }
            }
            .toList()

        assertTrue(
            "Haze top app bars need routed content to draw behind the top chrome:\n" +
                    offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun bottom_navigation_bar_uses_haze_chrome_and_transparent_haze_colors() {
        val scaffoldFile =
            File("src/main/java/com/mkx/hrttracker/ui/navigation/EdgeToEdgeNavigationSuiteScaffold.kt")
        val navHostFile =
            File("src/main/java/com/mkx/hrttracker/ui/navigation/HrtTrackerNavHost.kt")
        val text = scaffoldFile.readText()
        val navHostText = navHostFile.readText()

        assertTrue(
            "Bottom navigation scaffold should apply the Haze chrome modifier.",
            text.contains(".hazeChrome("),
        )
        assertTrue(
            "Bottom navigation scaffold should make navigation containers transparent for Haze.",
            text.contains("hazeNavigationSuiteColors()"),
        )
        assertTrue(
            "Bottom navigation chrome needs a stable route-level Haze source so page " +
                    "transitions update the blur from the composed NavHost frame instead of " +
                    "detaching and reattaching per-screen source layers.",
            text.contains("navigationChromeHazeState: HazeState?") &&
                    text.contains(".hazeSourceArea(navigationChromeHazeState)") &&
                    text.contains(".hazeChrome(navigationChromeHazeState") &&
                    navHostText.contains("val navigationChromeHazeState = rememberChromeHazeState()") &&
                    navHostText.contains("navigationChromeHazeState = navigationChromeHazeState"),
        )
    }

    @Test
    fun bottom_sheets_use_haze_chrome_and_transparent_haze_container_colors() {
        val uiDir = File("src/main/java/com/mkx/hrttracker/ui")
        require(uiDir.isDirectory) {
            "Expected UI source directory at ${uiDir.absolutePath}; unit tests must run with " +
                    "the app module as the working directory."
        }

        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> BOTTOM_SHEET_CALL.containsMatchIn(file.readText()) }
            .mapNotNull { file ->
                val text = file.readText()
                val missing = buildList {
                    if (!text.contains("HazeBottomSheetSurface(")) {
                        add("inner HazeBottomSheetSurface")
                    }
                    if (!text.contains("dragHandle = null")) {
                        add("default drag handle disabled for inner Haze surface")
                    }
                    if (!text.contains("hazeBottomSheetContainerColor()")) {
                        add("transparent Haze container color")
                    }
                }
                if (missing.isEmpty()) {
                    null
                } else {
                    "${file.relativeTo(uiDir)} missing ${missing.joinToString(" and ")}"
                }
            }
            .toList()

        assertTrue(
            "Every ModalBottomSheet should render with Haze chrome:\n" +
                    offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun bottom_sheet_blur_follows_app_wide_haze_setting() {
        val hazeChromeText =
            File("src/main/java/com/mkx/hrttracker/ui/components/HazeChrome.kt").readText()

        assertTrue(
            "Bottom sheet Haze should be provided by shared helpers so the same " +
                    "LocalHazeBlurEnabled switch controls sheet blur and other chrome.",
            hazeChromeText.contains("fun Modifier.hazeBottomSheet(") &&
                    hazeChromeText.contains("fun HazeBottomSheetSurface(") &&
                    hazeChromeText.contains("fun hazeBottomSheetContainerColor(") &&
                    hazeChromeText.contains("LocalHazeBlurEnabled.current") &&
                    hazeChromeText.contains("BottomSheetDefaults.ContainerColor") &&
                    hazeChromeText.contains("copy(alpha = 0f)"),
        )
    }

    @Test
    fun dialogs_use_haze_chrome_and_translucent_haze_container_colors() {
        val uiDir = File("src/main/java/com/mkx/hrttracker/ui")
        require(uiDir.isDirectory) {
            "Expected UI source directory at ${uiDir.absolutePath}; unit tests must run with " +
                    "the app module as the working directory."
        }

        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { file -> file.relativeTo(uiDir).invariantSeparatorsPath == "components/HazeChrome.kt" }
            .mapNotNull { file ->
                val text = file.readText()
                val missing = buildList {
                    if (DIRECT_MATERIAL_DIALOG_IMPORT.containsMatchIn(text)) {
                        add("direct Material3 dialog import")
                    }
                    if (DIALOG_CALL.containsMatchIn(text)) {
                        add("direct Material3 dialog call")
                    }
                }
                if (missing.isEmpty()) {
                    null
                } else {
                    "${file.relativeTo(uiDir)}: ${missing.joinToString("; ")}"
                }
            }
            .toList()

        assertTrue(
            "Every Material3 dialog should render through the shared Haze " +
                    "dialog wrappers:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )

        val hazeChromeText =
            File("src/main/java/com/mkx/hrttracker/ui/components/HazeChrome.kt").readText()

        assertTrue(
            "Dialog Haze should be provided by shared helpers so LocalHazeBlurEnabled " +
                    "controls dialog blur and disabled blur falls back to Material3 colors.",
            hazeChromeText.contains("fun HazeAlertDialog(") &&
                    hazeChromeText.contains("fun HazeBasicAlertDialog(") &&
                    hazeChromeText.contains("fun HazeDatePickerDialog(") &&
                    hazeChromeText.contains("fun HazeTimePickerDialog(") &&
                    hazeChromeText.contains("fun Modifier.hazeDialog(") &&
                    hazeChromeText.contains("shape: Shape") &&
                    hazeChromeText.contains(".clip(shape)") &&
                    hazeChromeText.contains("blurredEdgeTreatment = BlurredEdgeTreatment(shape)") &&
                    hazeChromeText.contains("fun hazeDialogContainerColor(") &&
                    hazeChromeText.contains("fun hazeDatePickerColors(") &&
                    hazeChromeText.contains("LocalHazeBlurEnabled.current") &&
                    hazeChromeText.contains("LocalChromeHazeState.current != null") &&
                    hazeChromeText.contains("copy(alpha = 0.2f)") &&
                    hazeChromeText.contains("forceInvalidateOnPreDraw = true") &&
                    hazeChromeText.contains("AlertDialogDefaults.containerColor"),
        )

        val materialPickerText =
            File("src/main/java/com/mkx/hrttracker/ui/components/MaterialPickerDialogs.kt")
                .readText()
        val planBatchAddText =
            File("src/main/java/com/mkx/hrttracker/ui/plan/PlanBatchAddScreen.kt").readText()

        assertTrue(
            "DatePicker and DateRangePicker draw their own container background, so picker " +
                    "content must receive the same translucent Haze date picker colors as " +
                    "the outer DatePickerDialog surface.",
            materialPickerText.contains("val colors = hazeDatePickerColors()") &&
                    materialPickerText.contains("DatePicker(state = datePickerState, colors = colors)") &&
                    planBatchAddText.contains("val colors = hazeDatePickerColors()") &&
                    planBatchAddText.contains("DateRangePicker(") &&
                    planBatchAddText.contains("colors = colors"),
        )
    }

    @Test
    fun bottom_sheet_haze_surface_owns_content_window_insets() {
        val uiDir = File("src/main/java/com/mkx/hrttracker/ui")
        require(uiDir.isDirectory) {
            "Expected UI source directory at ${uiDir.absolutePath}; unit tests must run with " +
                    "the app module as the working directory."
        }

        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> BOTTOM_SHEET_CALL.containsMatchIn(file.readText()) }
            .mapNotNull { file ->
                val text = file.readText()
                if (text.contains("contentWindowInsets = { hazeBottomSheetContentWindowInsets() }")) {
                    null
                } else {
                    "${file.relativeTo(uiDir)} lets Material3 pad outside the Haze surface"
                }
            }
            .toList()

        assertTrue(
            "Haze bottom sheets should make the Material3 content slot edge-to-edge and " +
                    "apply modal content insets inside HazeBottomSheetSurface so the haze " +
                    "background extends behind the status bar:\n" +
                    offenders.joinToString("\n"),
            offenders.isEmpty(),
        )

        val hazeChromeText =
            File("src/main/java/com/mkx/hrttracker/ui/components/HazeChrome.kt").readText()

        assertTrue(
            "HazeBottomSheetSurface should apply Material3 modal content insets after its " +
                    "haze/background modifiers, while ModalBottomSheet receives zero outer " +
                    "content insets.",
            hazeChromeText.contains(
                "contentWindowInsets: @Composable () -> WindowInsets = " +
                        "{ BottomSheetDefaults.modalWindowInsets }"
            ) &&
                    hazeChromeText.contains(".windowInsetsPadding(contentWindowInsets())") &&
                    hazeChromeText.contains("fun hazeBottomSheetContentWindowInsets()") &&
                    hazeChromeText.contains("WindowInsets(0, 0, 0, 0)"),
        )
    }

    @Test
    fun onboarding_bottom_sheets_receive_app_wide_haze_setting_and_source() {
        val mainActivityText =
            File("src/main/java/com/mkx/hrttracker/MainActivity.kt").readText()

        assertTrue(
            "Onboarding is hosted outside HrtTrackerNavHost, so the app shell must " +
                    "provide the same haze setting and a source layer for onboarding sheets.",
            mainActivityText.contains(
                "val appHazeBlurEnabled = effectiveHazeBlurEnabled(settingsState.hazeBlurEnabled)"
            ) &&
                    mainActivityText.contains("LocalHazeBlurEnabled provides appHazeBlurEnabled") &&
                    mainActivityText.contains(
                        "val onboardingChromeHazeState = rememberChromeHazeState()"
                    ) &&
                    mainActivityText.contains(
                        "LocalChromeHazeState provides onboardingChromeHazeState"
                    ) &&
                    mainActivityText.contains(".hazeSourceArea(onboardingChromeHazeState)"),
        )
    }

    @Test
    fun log_entry_bottom_sheet_receives_navigation_haze_setting_and_source() {
        val navHostText =
            File("src/main/java/com/mkx/hrttracker/ui/navigation/HrtTrackerNavHost.kt").readText()

        assertTrue(
            "The log entry editor is hosted by HrtTrackerNavHost rather than a route. It must " +
                    "stay inside the app-wide haze setting provider and receive the navigation " +
                    "Haze state whose source wraps the routed home/plan/history content.",
            navHostText.contains("LocalHazeBlurEnabled provides hazeBlurEnabled") &&
                    navHostText.contains("navigationChromeHazeState = navigationChromeHazeState") &&
                    navHostText.contains(
                        "LocalChromeHazeState provides navigationChromeHazeState"
                    ) &&
                    navHostText.contains("MedicationLogEntryScreen("),
        )
    }

    @Test
    fun haze_blur_is_controlled_by_app_wide_platform_gated_setting() {
        val hazeChromeText =
            File("src/main/java/com/mkx/hrttracker/ui/components/HazeChrome.kt").readText()
        val navHostText =
            File("src/main/java/com/mkx/hrttracker/ui/navigation/HrtTrackerNavHost.kt").readText()
        val mainActivityText =
            File("src/main/java/com/mkx/hrttracker/MainActivity.kt").readText()
        val settingsScreenText =
            File("src/main/java/com/mkx/hrttracker/ui/settings/SettingsScreen.kt").readText()

        assertTrue(
            "Haze needs a central composition local so every chrome/source modifier can be " +
                    "disabled from one app-wide setting.",
            hazeChromeText.contains("LocalHazeBlurEnabled") &&
                    hazeChromeText.contains("effectiveHazeBlurEnabled(") &&
                    hazeChromeText.contains("Build.VERSION_CODES.S") &&
                    hazeChromeText.contains("hazeTopAppBarColors(") &&
                    hazeChromeText.contains("hazeNavigationSuiteColors("),
        )
        assertTrue(
            "The NavHost should publish the effective value from SettingsState, with the " +
                    "Android 12+ platform gate applied before it reaches screen chrome.",
            mainActivityText.contains("settingsRepository.settingsState.collectAsStateWithLifecycle()") &&
                    mainActivityText.contains("settingsState = settingsState") &&
                    navHostText.contains("effectiveHazeBlurEnabled(") &&
                    navHostText.contains("settingsState.hazeBlurEnabled") &&
                    navHostText.contains("LocalHazeBlurEnabled provides"),
        )
        assertTrue(
            "Settings should expose the haze switch only through an Android 12+ gate.",
            settingsScreenText.contains("shouldShowHazeBlurToggle(") &&
                    settingsScreenText.contains("Build.VERSION_CODES.S") &&
                    settingsScreenText.contains("onHazeBlurEnabledChange") &&
                    settingsScreenText.contains("settingsState.hazeBlurEnabled"),
        )
    }

    private companion object {
        private val TOP_APP_BAR_CALL = Regex("""\b(?:CenterAlignedTopAppBar|TopAppBar)\(""")
        private val BOTTOM_SHEET_CALL = Regex("""\bModalBottomSheet\(""")
        private val DIALOG_CALL =
            Regex("""\b(?:AlertDialog|BasicAlertDialog|DatePickerDialog|TimePickerDialog)\(""")
        private val DIRECT_MATERIAL_DIALOG_IMPORT =
            Regex(
                """import androidx\.compose\.material3\.""" +
                        """(?:AlertDialog|BasicAlertDialog|DatePickerDialog|TimePickerDialog)\b"""
            )
    }
}
