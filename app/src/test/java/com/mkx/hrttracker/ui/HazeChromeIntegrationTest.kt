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

    private companion object {
        private val TOP_APP_BAR_CALL = Regex("""\b(?:CenterAlignedTopAppBar|TopAppBar)\(""")
    }
}
