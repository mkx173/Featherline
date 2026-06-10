package com.mkx.hrttracker.ui.components

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.mkx.hrttracker.BuildConfig
import dev.chrisbanes.haze.HazeState

/**
 * Temporary debug instrumentation for the haze blink / blank-first-frame investigation.
 *
 * All probes are debug-build no-ops in release. Correlate with the library's own
 * `HazeSource` / `HazeEffect` logcat tags (enabled via [dev.chrisbanes.haze.HazeLogger]
 * in [com.mkx.hrttracker.HrtTrackerApplication]) using the `t=` uptime stamps —
 * lines within the same frame land within ~1ms of each other.
 *
 * TODO(haze-blink): remove once the blink root cause is fixed.
 */
internal const val HAZE_BLINK_PROBE_TAG = "HazeBlinkProbe"

internal val hazeBlinkProbeEnabled: Boolean get() = BuildConfig.DEBUG

internal fun hazeBlinkProbeLog(message: () -> String) {
    if (hazeBlinkProbeEnabled) {
        Log.d(HAZE_BLINK_PROBE_TAG, "t=${SystemClock.uptimeMillis()} ${message()}")
    }
}

/**
 * Logs every draw pass of the modified node, plus a snapshot of the [HazeState] areas as
 * seen at that draw. Lets us see, on the blink frame, whether the node drew at all and
 * whether the effect had a registered source area with a live content layer at that moment.
 */
internal fun Modifier.hazeBlinkDrawProbe(label: String, state: HazeState?): Modifier {
    if (!hazeBlinkProbeEnabled) return this
    return drawWithContent {
        // Read haze state without snapshot observation so the probe does not add draw
        // invalidations of its own — extra invalidations could mask the blink under test.
        Snapshot.withoutReadObservation {
            hazeBlinkProbeLog {
                val areas = state?.areas.orEmpty()
                "draw $label state=${state?.hashCode()} " +
                    "areas=${areas.size} " +
                    areas.joinToString(prefix = "[", postfix = "]") { area ->
                        "pos=${area.positionOnScreen} size=${area.size} " +
                            "layer=${if (area.contentLayer == null) "null" else "set"}"
                    }
            }
        }
        drawContent()
    }
}

/**
 * Logs once whenever [value] changes across recompositions — used to catch the
 * top app bar's haze enablement flag flipping (which removes/re-adds the whole
 * hazeEffect node from the modifier chain).
 */
@Composable
internal fun HazeBlinkChangeProbe(label: String, value: Any?) {
    if (!hazeBlinkProbeEnabled) return
    val last = remember { arrayOfNulls<Any?>(1).also { it[0] = Unit } }
    SideEffect {
        if (last[0] != value) {
            hazeBlinkProbeLog { "$label changed: ${last[0]} -> $value" }
            last[0] = value
        }
    }
}
