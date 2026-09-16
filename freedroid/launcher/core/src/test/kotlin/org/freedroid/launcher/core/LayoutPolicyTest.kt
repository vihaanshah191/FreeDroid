package org.freedroid.launcher.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutPolicyTest {

    // -----------------------------------------------------------------------
    // The rule this module exists to enforce.
    // -----------------------------------------------------------------------

    /**
     * The test that justifies the whole design.
     *
     * A tablet running the launcher in a narrow split-screen pane must lay out
     * exactly like a phone, because what the launcher actually has is a narrow
     * window. Any policy keyed to device identity gets this wrong, and
     * split-screen is an ordinary thing users do, not an edge case.
     */
    @Test
    fun `identical window sizes produce identical layouts regardless of device`() {
        // Same window geometry; one arrives from a phone, one from a tablet in
        // split-screen. The policy cannot tell them apart, and must not.
        val phoneWindow = WindowGeometry(411, 891)
        val tabletSplitScreenWindow = WindowGeometry(411, 891)

        assertEquals(
            LayoutPolicy.decide(phoneWindow),
            LayoutPolicy.decide(tabletSplitScreenWindow),
            "Layout must be a function of window size alone",
        )
    }

    /**
     * A foldable changes window size mid-session without changing device. The
     * layout must follow the window across that transition.
     */
    @Test
    fun `unfolding changes the layout`() {
        val closed = LayoutPolicy.decide(WindowGeometry.FOLDABLE_CLOSED)
        val open = LayoutPolicy.decide(WindowGeometry.FOLDABLE_OPEN)

        assertEquals(4, closed.workspaceGrid.columns, "closed foldable is a compact window")
        assertEquals(6, open.workspaceGrid.columns, "open foldable is a medium window")
        assertTrue(open.workspaceGrid.cellCount > closed.workspaceGrid.cellCount)
    }

    /** Pure function: no hidden state, no ordering effects, safe to call per frame. */
    @Test
    fun `decide is pure`() {
        val geometry = WindowGeometry.TABLET_LANDSCAPE
        val first = LayoutPolicy.decide(geometry)
        repeat(100) { assertEquals(first, LayoutPolicy.decide(geometry)) }
    }

    // -----------------------------------------------------------------------
    // Concrete layouts per window size.
    // -----------------------------------------------------------------------

    @Test
    fun `phone portrait gets a single pane with a bottom hotseat`() {
        val layout = LayoutPolicy.decide(WindowGeometry.PHONE_PORTRAIT)

        assertEquals(HotseatPosition.BOTTOM, layout.hotseatPosition)
        assertEquals(PaneMode.SINGLE, layout.paneMode)
        assertEquals(GridSpec(columns = 4, rows = 5), layout.workspaceGrid)
        assertEquals(4, layout.appDrawerColumns)
        assertFalse(layout.persistentAppDrawer)
        assertTrue(layout.showSearchRow)
    }

    /**
     * A large phone in landscape is an EXPANDED window, and is treated exactly
     * like a tablet, because at 891dp wide that is what it is.
     *
     * This case is the clearest argument against device-category branching, and
     * it caught a wrong assumption while this test was being written: "phone" was
     * assumed to imply a narrow window, but a modern large phone rotated to
     * landscape is wider than Android's 840dp EXPANDED breakpoint. Any code that
     * had reasoned from "this is a phone" would lay out a wide window as if it
     * were narrow, and waste most of it.
     */
    @Test
    fun `a large phone in landscape is an expanded window and is treated as one`() {
        val geometry = WindowGeometry.PHONE_LANDSCAPE
        assertEquals(WindowWidthSizeClass.EXPANDED, geometry.widthSizeClass)
        assertEquals(WindowHeightSizeClass.COMPACT, geometry.heightSizeClass)

        val layout = LayoutPolicy.decide(geometry)

        // Wide and landscape, so it earns the wide-window treatment.
        assertEquals(HotseatPosition.SIDE, layout.hotseatPosition)
        assertEquals(PaneMode.DUAL, layout.paneMode)
        assertEquals(8, layout.workspaceGrid.columns)
        // But it is short, so it gets few rows and no search row.
        assertEquals(3, layout.workspaceGrid.rows, "a short window gets fewer rows")
        assertFalse(layout.showSearchRow, "411dp tall cannot afford a search row")
    }

    /**
     * The same launcher on the same phone, rotated. Width class crosses two
     * breakpoints, so the layout changes substantially - correctly.
     */
    @Test
    fun `rotating a phone crosses a width breakpoint`() {
        val portrait = LayoutPolicy.decide(WindowGeometry.PHONE_PORTRAIT)
        val landscape = LayoutPolicy.decide(WindowGeometry.PHONE_LANDSCAPE)

        assertEquals(PaneMode.SINGLE, portrait.paneMode)
        assertEquals(PaneMode.DUAL, landscape.paneMode)
        assertEquals(HotseatPosition.BOTTOM, portrait.hotseatPosition)
        assertEquals(HotseatPosition.SIDE, landscape.hotseatPosition)
    }

    @Test
    fun `tablet landscape gets a side hotseat and two panes`() {
        val layout = LayoutPolicy.decide(WindowGeometry.TABLET_LANDSCAPE)

        assertEquals(HotseatPosition.SIDE, layout.hotseatPosition)
        assertEquals(PaneMode.DUAL, layout.paneMode)
        assertEquals(8, layout.workspaceGrid.columns)
        assertEquals(8, layout.hotseatCapacity)
        assertTrue(layout.persistentAppDrawer)
    }

    @Test
    fun `tablet portrait is expanded-height but only medium-width`() {
        val layout = LayoutPolicy.decide(WindowGeometry.TABLET_PORTRAIT)

        // 800dp wide is MEDIUM. Portrait, so no side hotseat.
        assertEquals(HotseatPosition.BOTTOM, layout.hotseatPosition)
        assertEquals(PaneMode.SINGLE, layout.paneMode)
        assertEquals(GridSpec(columns = 6, rows = 6), layout.workspaceGrid)
    }

    @Test
    fun `a cramped split-screen pane drops the search row`() {
        val layout = LayoutPolicy.decide(WindowGeometry.SPLIT_SCREEN_HALF)

        assertFalse(layout.showSearchRow, "400dp tall cannot afford a search row")
        assertEquals(PaneMode.SINGLE, layout.paneMode)
        assertEquals(3, layout.workspaceGrid.rows)
    }

    @Test
    fun `desktop window gets the largest layout`() {
        val layout = LayoutPolicy.decide(WindowGeometry.DESKTOP)

        assertEquals(HotseatPosition.SIDE, layout.hotseatPosition)
        assertEquals(PaneMode.DUAL, layout.paneMode)
        assertEquals(GridSpec(columns = 8, rows = 6), layout.workspaceGrid)
    }

    // -----------------------------------------------------------------------
    // Properties that must hold across the whole input space.
    // -----------------------------------------------------------------------

    /**
     * Sweeps every plausible window size. Catches discontinuities a handful of
     * named cases would miss — a grid that collapses at one breakpoint, or a
     * crash on an unusual aspect ratio.
     */
    @Test
    fun `every plausible window size yields a usable layout`() {
        var checked = 0
        for (width in 200..2000 step 7) {
            for (height in 200..2000 step 11) {
                val layout = LayoutPolicy.decide(WindowGeometry(width, height))

                assertTrue(layout.workspaceGrid.columns in 1..12, "columns out of range at ${width}x$height")
                assertTrue(layout.workspaceGrid.rows in 1..12, "rows out of range at ${width}x$height")
                assertTrue(layout.hotseatCapacity in 1..12, "hotseat out of range at ${width}x$height")
                assertTrue(layout.appDrawerColumns in 1..12, "drawer cols out of range at ${width}x$height")
                checked++
            }
        }
        assertTrue(checked > 40_000, "expected a broad sweep, checked only $checked")
    }

    /** Growing the window must never shrink the workspace. */
    @Test
    fun `workspace capacity is monotonic in window size`() {
        val heights = listOf(400, 500, 700, 950, 1280)
        for (height in heights) {
            var previous = 0
            for (width in listOf(360, 599, 600, 839, 840, 1280, 1920)) {
                val cells = LayoutPolicy.decide(WindowGeometry(width, height)).workspaceGrid.columns
                assertTrue(
                    cells >= previous,
                    "columns shrank from $previous to $cells going wider at width=$width height=$height",
                )
                previous = cells
            }
        }
    }

    /** A dual-pane layout without room for two panes would be a layout bug. */
    @Test
    fun `dual pane only ever appears on expanded width`() {
        for (width in 200..2000 step 3) {
            val layout = LayoutPolicy.decide(WindowGeometry(width, 900))
            if (layout.paneMode == PaneMode.DUAL) {
                assertTrue(
                    width >= WindowWidthSizeClass.EXPANDED_MIN_DP,
                    "dual pane at ${width}dp is too narrow",
                )
            }
        }
    }

    /** A side hotseat in a portrait window would eat the width it is trying to use. */
    @Test
    fun `side hotseat only ever appears on wide landscape windows`() {
        for (width in 200..2000 step 13) {
            for (height in 200..2000 step 17) {
                val geometry = WindowGeometry(width, height)
                if (LayoutPolicy.decide(geometry).hotseatPosition == HotseatPosition.SIDE) {
                    assertTrue(geometry.isLandscape, "side hotseat in portrait at ${width}x$height")
                    assertTrue(width >= WindowWidthSizeClass.EXPANDED_MIN_DP)
                }
            }
        }
    }
}
