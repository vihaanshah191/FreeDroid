package org.freedroid.launcher.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boundary conditions for the app-drawer grid.
 *
 * The drawer's column count must come from the adaptive policy, never from a
 * device check. These tests pin the exact dp at which the count changes, so a
 * later refactor cannot quietly reintroduce "tablet gets 8, phone gets 4".
 */
class DrawerBreakpointTest {

    private fun columnsAt(widthDp: Int, heightDp: Int = 900): Int =
        LayoutPolicy.decide(WindowGeometry(widthDp, heightDp)).appDrawerColumns

    @Test
    fun `drawer columns change exactly at the width breakpoints`() {
        // COMPACT, right up to the boundary
        assertEquals(4, columnsAt(320))
        assertEquals(4, columnsAt(599))
        // MEDIUM begins at 600
        assertEquals(6, columnsAt(600))
        assertEquals(6, columnsAt(839))
        // EXPANDED begins at 840
        assertEquals(8, columnsAt(840))
        assertEquals(8, columnsAt(1920))
    }

    @Test
    fun `hotseat capacity changes at the same boundaries`() {
        fun capacityAt(w: Int) = LayoutPolicy.decide(WindowGeometry(w, 900)).hotseatCapacity

        assertEquals(4, capacityAt(599))
        assertEquals(6, capacityAt(600))
        assertEquals(6, capacityAt(839))
        assertEquals(8, capacityAt(840))
    }

    @Test
    fun `workspace columns change at the same boundaries`() {
        fun colsAt(w: Int) = LayoutPolicy.decide(WindowGeometry(w, 900)).workspaceGrid.columns

        assertEquals(4, colsAt(599))
        assertEquals(6, colsAt(600))
        assertEquals(6, colsAt(839))
        assertEquals(8, colsAt(840))
    }

    @Test
    fun `workspace rows change exactly at the height breakpoints`() {
        fun rowsAt(h: Int) = LayoutPolicy.decide(WindowGeometry(600, h)).workspaceGrid.rows

        assertEquals(3, rowsAt(479))
        assertEquals(5, rowsAt(480))
        assertEquals(5, rowsAt(899))
        assertEquals(6, rowsAt(900))
    }

    @Test
    fun `search row appears exactly at its height threshold`() {
        fun shows(h: Int) = LayoutPolicy.decide(WindowGeometry(600, h)).showSearchRow

        assertTrue(!shows(479))
        assertTrue(shows(480))
    }

    /**
     * Drawer columns are a step function of width alone. A one-dp change must
     * never alter the count anywhere except at the two breakpoints - that would
     * mean the drawer reflows while a window is being resized.
     */
    @Test
    fun `column count changes at exactly two widths across the whole range`() {
        val transitions = (201..2000).filter { columnsAt(it) != columnsAt(it - 1) }
        assertEquals(listOf(600, 840), transitions)
    }

    /** Drawer width must not depend on height, or a keyboard opening would reflow the grid. */
    @Test
    fun `drawer columns are independent of window height`() {
        listOf(200, 400, 480, 900, 1400, 2000).forEach { height ->
            assertEquals(4, columnsAt(411, height), "height $height changed compact drawer columns")
            assertEquals(8, columnsAt(1280, height), "height $height changed expanded drawer columns")
        }
    }
}
