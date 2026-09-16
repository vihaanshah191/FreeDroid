package org.freedroid.launcher.app

import android.content.Context
import androidx.window.layout.WindowMetricsCalculator
import org.freedroid.launcher.core.WindowGeometry

/**
 * The single boundary between Android and the launcher's layout policy.
 *
 * Everything platform-specific stops here. [org.freedroid.launcher.core] receives
 * a plain [WindowGeometry] and nothing else — no [Context], no `Configuration`,
 * no `Display`. That is what keeps the policy testable without a device and
 * unable to branch on device category.
 *
 * NOT YET COMPILED OR RUN. The Android SDK is unavailable in the current
 * development environment, so this code is unverified.
 */
internal object WindowGeometryAdapter {

    /**
     * Reads the *current window* bounds, not the display bounds.
     *
     * [WindowMetricsCalculator.computeCurrentWindowMetrics] is the correct source:
     * in split-screen it reports the app's pane rather than the screen, and on a
     * foldable it changes across a fold. Using display metrics here would
     * reintroduce exactly the device-shaped reasoning the architecture forbids.
     */
    fun currentGeometry(context: Context): WindowGeometry {
        val metrics = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(context)
        val density = context.resources.displayMetrics.density

        // Guard against a zero-size window, which can occur transiently during
        // configuration changes. WindowGeometry rejects non-positive dimensions,
        // so clamp rather than let a transient value crash the launcher.
        val widthDp = (metrics.bounds.width() / density).toInt().coerceAtLeast(1)
        val heightDp = (metrics.bounds.height() / density).toInt().coerceAtLeast(1)

        return WindowGeometry(widthDp = widthDp, heightDp = heightDp)
    }
}
