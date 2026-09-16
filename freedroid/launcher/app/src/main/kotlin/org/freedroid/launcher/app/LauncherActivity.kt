package org.freedroid.launcher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import org.freedroid.launcher.core.LauncherLayout
import org.freedroid.launcher.core.LayoutPolicy

/**
 * The FreeDroid home screen.
 *
 * SKELETON. Renders nothing yet — it wires the window-size pipeline and proves
 * the boundary. Workspace, hotseat and app drawer rendering are Phase 4 work.
 *
 * NOT YET COMPILED OR RUN.
 *
 * Note the absence of any `isTablet()` check, any `smallestScreenWidthDp` read,
 * and any layout-selection resource qualifier for device class. The layout comes
 * from [LayoutPolicy] and the current window, every time — including after a
 * configuration change, which is why [applyLayout] is called from both
 * [onCreate] and [onConfigurationChanged].
 */
class LauncherActivity : ComponentActivity() {

    private var currentLayout: LauncherLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLayout()
    }

    /**
     * The activity declares `android:configChanges` for size-affecting changes,
     * so it is not recreated on rotation, fold, or window resize. That makes
     * re-deriving the layout here mandatory rather than optional.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayout()
    }

    private fun applyLayout() {
        val geometry = WindowGeometryAdapter.currentGeometry(this)
        val layout = LayoutPolicy.decide(geometry)

        if (layout == currentLayout) return  // nothing to re-lay-out
        currentLayout = layout

        // TODO(phase-4): render workspace, hotseat and app drawer from `layout`.
    }

    /**
     * A launcher must not exit on Back — it is the bottom of the task stack.
     * Phase 4 replaces this with a predictive-back handler that closes the app
     * drawer or folders first.
     */
    @Deprecated("Replace with OnBackPressedCallback in Phase 4")
    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        // Intentionally does not call super: Back on the home screen is a no-op.
    }
}
