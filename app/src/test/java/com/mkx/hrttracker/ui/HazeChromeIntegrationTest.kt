package com.mkx.hrttracker.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural checks for the haze chrome integration.
 *
 * The bulk of the haze wiring is enforced at compile time: the shared wrappers in
 * HazeChrome.kt (HazeTopAppBar, HazeModalBottomSheet, HazeAlertDialog, ...) own the
 * transparent containers, blur modifiers and accessibility affordances, and their
 * building blocks are private to that file. What's left for these tests is the part
 * the compiler can't see: that screens actually use the wrappers instead of the raw
 * Material3 components, and the cross-file CompositionLocal wiring.
 */
class HazeChromeIntegrationTest {
    @Test
    fun material_chrome_renders_through_shared_haze_wrappers() {
        val offenders = uiSourceFiles()
            .filterNot { file ->
                file.relativeTo(uiDir()).invariantSeparatorsPath == "components/HazeChrome.kt"
            }
            .mapNotNull { file ->
                val text = file.readText()
                val missing = buildList {
                    if (DIRECT_MATERIAL_CHROME_IMPORT.containsMatchIn(text)) {
                        add("direct Material3 chrome import")
                    }
                    if (DIRECT_MATERIAL_CHROME_CALL.containsMatchIn(text)) {
                        add("direct Material3 chrome call")
                    }
                }
                if (missing.isEmpty()) {
                    null
                } else {
                    "${file.relativeTo(uiDir())}: ${missing.joinToString("; ")}"
                }
            }
            .toList()

        assertTrue(
            "Top app bars, modal bottom sheets and dialogs must render through the " +
                    "shared Haze wrappers in HazeChrome.kt (HazeTopAppBar, " +
                    "HazeModalBottomSheet, HazeAlertDialog, ...); hand-assembling the " +
                    "chrome at call sites is how transparent-surface bugs slip in:\n" +
                    offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun haze_chrome_keeps_wrapper_building_blocks() {
        val hazeChromeText = hazeChromeText()

        assertTrue(
            "Haze wrappers should keep the shared building blocks so one " +
                    "LocalHazeBlurEnabled switch controls all chrome.",
            hazeChromeText.contains("fun HazeTopAppBar(") &&
                    hazeChromeText.contains("fun HazeCenterAlignedTopAppBar(") &&
                    hazeChromeText.contains("fun HazeModalBottomSheet(") &&
                    hazeChromeText.contains("fun Modifier.hazeBottomSheet(") &&
                    hazeChromeText.contains("fun HazeBottomSheetSurface(") &&
                    hazeChromeText.contains("fun hazeBottomSheetContainerColor(") &&
                    hazeChromeText.contains("LocalHazeBlurEnabled.current") &&
                    hazeChromeText.contains("BottomSheetDefaults.ContainerColor") &&
                    hazeChromeText.contains("copy(alpha = 0f)"),
        )

        assertTrue(
            "Sheet and dialog containers must only go transparent when a haze state is " +
                    "actually present; a transparent container without a blur behind it " +
                    "renders the surface invisible.",
            hazeChromeText.contains("LocalChromeHazeState.current != null"),
        )

        assertTrue(
            "Dialog Haze should be provided by shared helpers so LocalHazeBlurEnabled " +
                    "controls dialog blur and disabled blur falls back to Material3 colors.",
            hazeChromeText.contains("fun HazeAlertDialog(") &&
                    hazeChromeText.contains("fun HazeBasicAlertDialog(") &&
                    hazeChromeText.contains("fun HazeDatePickerDialog(") &&
                    hazeChromeText.contains("fun HazeTimePickerDialog(") &&
                    hazeChromeText.contains("fun Modifier.hazeDialog(") &&
                    hazeChromeText.contains(".clip(shape)") &&
                    hazeChromeText.contains("blurredEdgeTreatment = BlurredEdgeTreatment(shape)") &&
                    hazeChromeText.contains("fun hazeDialogContainerColor(") &&
                    hazeChromeText.contains("fun hazeDatePickerColors(") &&
                    hazeChromeText.contains("copy(alpha = 0.2f)") &&
                    hazeChromeText.contains("AlertDialogDefaults.containerColor"),
        )
    }

    @Test
    fun top_app_bar_haze_transparent_colors_preserve_theme_rgb_channels() {
        val hazeChromeText = hazeChromeText()

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
    fun top_app_bar_blur_follows_default_scrolled_overlap_state() {
        val offenders = uiSourceFiles()
            .filterNot { file ->
                file.relativeTo(uiDir()).invariantSeparatorsPath ==
                        "components/PinnedTopAppBarScrollBehavior.kt"
            }
            .mapNotNull { file ->
                val text = file.readText()
                val missing = buildList {
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
                    "${file.relativeTo(uiDir())}: ${missing.joinToString("; ")}"
                }
            }
            .toList()

        val hazeChromeText = hazeChromeText()
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
        val navHostText = navHostText()
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

        val offenders = uiSourceFiles()
            .filter { file -> HAZE_TOP_APP_BAR_CALL.containsMatchIn(file.readText()) }
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
                    "${file.relativeTo(uiDir())}: ${missing.joinToString("; ")}"
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
        val text = scaffoldFile.readText()
        val navHostText = navHostText()

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
    fun navhost_hosted_sheets_receive_navigation_haze_setting_and_source() {
        val navHostText = navHostText()

        assertTrue(
            "The log entry editor and the stock-nudge opt-in sheet are hosted by " +
                    "HrtTrackerNavHost rather than a route. Both must receive the navigation " +
                    "Haze state whose source wraps the routed content; composing either one " +
                    "outside the provider made its transparent container fully invisible.",
            navHostText.contains("MedicationLogEntryScreen(") &&
                    navHostText.contains("AdjustStockSheet(") &&
                    Regex("""LocalChromeHazeState provides navigationChromeHazeState""")
                        .findAll(navHostText).count() == 2,
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
    fun haze_blur_is_controlled_by_app_wide_platform_gated_setting() {
        val hazeChromeText = hazeChromeText()
        val navHostText = navHostText()
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
            "MainActivity publishes the effective value from SettingsState (with the " +
                    "Android 12+ platform gate applied) for the whole app; the NavHost must " +
                    "not re-derive or re-provide it — the duplicate provider invalidated the " +
                    "NavHost on every settings change and defaulted blur on when the " +
                    "parameter was omitted.",
            mainActivityText.contains("settingsRepository.settingsState.collectAsStateWithLifecycle()") &&
                    mainActivityText.contains("LocalHazeBlurEnabled provides appHazeBlurEnabled") &&
                    !navHostText.contains("LocalHazeBlurEnabled provides"),
        )
        assertTrue(
            "Settings should expose the haze switch only through an Android 12+ gate, " +
                    "reusing the shared isHazeBlurSupported check.",
            settingsScreenText.contains("if (isHazeBlurSupported())") &&
                    settingsScreenText.contains("onHazeBlurEnabledChange") &&
                    settingsScreenText.contains("settingsState.hazeBlurEnabled"),
        )
    }

    @Test
    fun date_pickers_receive_translucent_haze_colors() {
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

    private fun uiDir(): File {
        val uiDir = File("src/main/java/com/mkx/hrttracker/ui")
        require(uiDir.isDirectory) {
            "Expected UI source directory at ${uiDir.absolutePath}; unit tests must run with " +
                    "the app module as the working directory."
        }
        return uiDir
    }

    private fun uiSourceFiles(): Sequence<File> = uiDir().walkTopDown()
        .filter { it.isFile && it.extension == "kt" }

    private fun hazeChromeText(): String =
        File("src/main/java/com/mkx/hrttracker/ui/components/HazeChrome.kt").readText()

    private fun navHostText(): String =
        File("src/main/java/com/mkx/hrttracker/ui/navigation/HrtTrackerNavHost.kt").readText()

    private companion object {
        private val HAZE_TOP_APP_BAR_CALL =
            Regex("""\bHaze(?:CenterAligned)?TopAppBar\(""")
        private val DIRECT_MATERIAL_CHROME_CALL =
            Regex(
                """\b(?:CenterAlignedTopAppBar|TopAppBar|ModalBottomSheet|AlertDialog|""" +
                        """BasicAlertDialog|DatePickerDialog|TimePickerDialog)\("""
            )
        private val DIRECT_MATERIAL_CHROME_IMPORT =
            Regex(
                """import androidx\.compose\.material3\.""" +
                        """(?:CenterAlignedTopAppBar|TopAppBar|ModalBottomSheet|AlertDialog|""" +
                        """BasicAlertDialog|DatePickerDialog|TimePickerDialog)\b"""
            )
    }
}
