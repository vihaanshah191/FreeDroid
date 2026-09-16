package org.freedroid.launcher.core

/**
 * Decides the launcher's layout from the size of the window it was given.
 *
 * This is the whole of the launcher's adaptive behaviour, and it is a pure
 * function: the same [WindowGeometry] always produces the same [LauncherLayout],
 * with no I/O, no clock, no platform lookup and no hidden state. That makes every
 * form factor the launcher will ever run on testable without a device.
 *
 * ## Why there is no device check here
 *
 * `docs/architecture/OVERVIEW.md` section 5.4 prohibits branching on device
 * category. This module cannot violate that rule even by accident: it has no
 * Android dependency (enforced by the `verifyNoAndroidDependencies` Gradle task),
 * so `isTablet()`, `Configuration.smallestScreenWidthDp` and display metrics are
 * not reachable from here. The only input available is the current window.
 *
 * That matters concretely. A tablet in split-screen gets a narrow window and must
 * lay out like a phone; a foldable changes window size mid-session without
 * changing device. Policy keyed to device identity is wrong in both cases, and
 * those are ordinary situations rather than edge cases.
 */
public object LayoutPolicy {

    /**
     * A window this short cannot afford a search row without crowding the grid.
     * Chosen to sit below [WindowHeightSizeClass.MEDIUM_MIN_DP] so a phone in
     * landscape keeps its workspace usable.
     */
    private const val SEARCH_ROW_MIN_HEIGHT_DP = 480

    /**
     * Below this width a side hotseat would leave too little room for the
     * workspace. Above it, a bottom hotseat stretches unhelpfully wide.
     */
    private const val SIDE_HOTSEAT_MIN_WIDTH_DP = WindowWidthSizeClass.EXPANDED_MIN_DP

    public fun decide(geometry: WindowGeometry): LauncherLayout {
        val width = geometry.widthSizeClass
        val height = geometry.heightSizeClass

        return LauncherLayout(
            hotseatPosition = hotseatPosition(geometry),
            hotseatCapacity = hotseatCapacity(width),
            paneMode = paneMode(width),
            workspaceGrid = workspaceGrid(width, height),
            appDrawerColumns = appDrawerColumns(width),
            persistentAppDrawer = width == WindowWidthSizeClass.EXPANDED,
            showSearchRow = geometry.heightDp >= SEARCH_ROW_MIN_HEIGHT_DP,
        )
    }

    /**
     * A side hotseat needs both width to spare and a landscape-ish window; a tall
     * narrow window with a side rail wastes the width it does have.
     */
    private fun hotseatPosition(geometry: WindowGeometry): HotseatPosition =
        if (geometry.widthDp >= SIDE_HOTSEAT_MIN_WIDTH_DP && geometry.isLandscape) {
            HotseatPosition.SIDE
        } else {
            HotseatPosition.BOTTOM
        }

    private fun hotseatCapacity(width: WindowWidthSizeClass): Int = when (width) {
        WindowWidthSizeClass.COMPACT -> 4
        WindowWidthSizeClass.MEDIUM -> 6
        WindowWidthSizeClass.EXPANDED -> 8
    }

    /** Two panes need real horizontal room; below EXPANDED each pane would be cramped. */
    private fun paneMode(width: WindowWidthSizeClass): PaneMode = when (width) {
        WindowWidthSizeClass.COMPACT, WindowWidthSizeClass.MEDIUM -> PaneMode.SINGLE
        WindowWidthSizeClass.EXPANDED -> PaneMode.DUAL
    }

    private fun workspaceGrid(
        width: WindowWidthSizeClass,
        height: WindowHeightSizeClass,
    ): GridSpec {
        val columns = when (width) {
            WindowWidthSizeClass.COMPACT -> 4
            WindowWidthSizeClass.MEDIUM -> 6
            WindowWidthSizeClass.EXPANDED -> 8
        }
        val rows = when (height) {
            WindowHeightSizeClass.COMPACT -> 3
            WindowHeightSizeClass.MEDIUM -> 5
            WindowHeightSizeClass.EXPANDED -> 6
        }
        return GridSpec(columns = columns, rows = rows)
    }

    private fun appDrawerColumns(width: WindowWidthSizeClass): Int = when (width) {
        WindowWidthSizeClass.COMPACT -> 4
        WindowWidthSizeClass.MEDIUM -> 6
        WindowWidthSizeClass.EXPANDED -> 8
    }
}
