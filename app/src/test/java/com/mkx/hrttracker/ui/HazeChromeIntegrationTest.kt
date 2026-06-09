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
                    if (!text.contains(".hazeChrome(")) add("hazeChrome modifier")
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
                    if (!text.contains(".hazeChrome(enabled = topAppBarHazeEnabled(scrollBehavior))")) {
                        add("Haze blur is not gated by topAppBarHazeEnabled(scrollBehavior)")
                    }
                    if (
                        text.contains("val scrollState = rememberScrollState()") &&
                        text.contains("TopAppBarDefaults.pinnedScrollBehavior(") &&
                        !text.contains("scrollState = scrollState")
                    ) {
                        add("ScrollState-backed top app bar is not using pinnedScrollBehavior(scrollState = ...)")
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
            "Haze top app bars should use Material3's default scrolled threshold.",
            hazeChromeText.contains("TopAppBarScrolledOverlapThreshold = 0.01f") &&
                    hazeChromeText.contains(
                        "scrollBehavior.state.overlappedFraction > TopAppBarScrolledOverlapThreshold"
                    ),
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
    }
}
