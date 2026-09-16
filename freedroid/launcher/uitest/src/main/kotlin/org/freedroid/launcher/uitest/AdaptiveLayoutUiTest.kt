package org.freedroid.launcher.uitest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI tests for the adaptive drawer.
 *
 * STATUS: WRITTEN BUT NEVER EXECUTED.
 *
 * These require the Android SDK to compile and a device or emulator to run.
 * The current development container has neither. They are recorded as BLOCKED
 * in docs/development/TESTING.md and must not be counted as passing.
 *
 * Note what is deliberately NOT tested here: the layout decisions themselves.
 * Those are pure functions in :core and are already covered by 80 JVM tests that
 * run in milliseconds without a device. Re-testing them through the UI would be
 * slower, flakier, and would prove nothing extra. What belongs here is only what
 * genuinely needs a device: that the composables render, that the grid actually
 * reflows when the window resizes, and that semantics reach the accessibility tree.
 */
class AdaptiveLayoutUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun drawerRendersInACompactWindow() {
        composeRule.setContent {
            Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                // TODO(phase-4): host LauncherRoot with a fake catalogue once a
                // test double for PackageAppSource exists.
            }
        }
        composeRule.onNodeWithText("Search apps").assertExists()
    }

    @Test
    fun drawerReflowsWhenTheWindowIsResized() {
        // TODO(phase-4): render at 411dp, assert the compact column count, resize
        // to 1280dp, assert the expanded count. This is the one adaptive
        // behaviour a device test adds over the :core unit tests - that the grid
        // actually re-lays-out rather than merely computing a new number.
    }

    @Test
    fun everyAppTileExposesItsLabelToAccessibility() {
        // TODO(phase-4): assert each tile merges into a single node with
        // Role.Button and a contentDescription equal to the app label.
    }

    @Test
    fun searchFieldAcceptsKeyboardInputAndFiltersTheGrid() {
        // TODO(phase-4): type into the field, assert the grid filters and the
        // no-results state appears for a non-matching query.
    }
}
