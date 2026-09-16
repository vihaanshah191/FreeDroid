package org.freedroid.launcher.core

/** Where the hotseat (the persistent row of favourite apps) is anchored. */
public enum class HotseatPosition {
    /** Along the bottom edge. Reachable with a thumb on a narrow window. */
    BOTTOM,

    /** Along the inline start edge as a rail. Used when the window is wide enough
     *  that a bottom hotseat would waste horizontal space and stretch too far. */
    SIDE,
}

/** How the launcher presents its primary surfaces. */
public enum class PaneMode {
    /** One surface at a time: workspace, or app drawer, or search. */
    SINGLE,

    /** Two surfaces side by side, e.g. workspace with a persistent app drawer. */
    DUAL,
}

/** A workspace or drawer grid, in cells. */
public data class GridSpec(
    val columns: Int,
    val rows: Int,
) {
    init {
        require(columns > 0) { "columns must be positive, was $columns" }
        require(rows > 0) { "rows must be positive, was $rows" }
    }

    public val cellCount: Int get() = columns * rows
}

/**
 * The complete layout decision for one window size.
 *
 * Produced only by [LayoutPolicy.decide]. Deliberately a plain value: it is
 * trivially comparable in tests, and it carries no reference to any Android
 * object, so the decision can be made and asserted without a device.
 */
public data class LauncherLayout(
    val hotseatPosition: HotseatPosition,
    val hotseatCapacity: Int,
    val paneMode: PaneMode,
    val workspaceGrid: GridSpec,
    val appDrawerColumns: Int,
    /** Whether the app drawer stays visible alongside the workspace. */
    val persistentAppDrawer: Boolean,
    /** Whether to reserve space for the at-a-glance/search widget row. */
    val showSearchRow: Boolean,
)
