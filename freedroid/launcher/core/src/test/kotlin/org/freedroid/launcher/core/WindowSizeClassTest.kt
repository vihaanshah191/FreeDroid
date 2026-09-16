package org.freedroid.launcher.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Breakpoint boundaries, asserted exactly.
 *
 * These values are mirrored from Jetpack WindowManager. If the app module's
 * adapter and these constants ever drift apart the launcher would lay out
 * inconsistently with the rest of the system, so the boundaries are pinned here
 * rather than left implicit.
 */
class WindowSizeClassTest {

    @Test
    fun `width breakpoints are exact`() {
        assertEquals(WindowWidthSizeClass.COMPACT, WindowWidthSizeClass.fromDp(1))
        assertEquals(WindowWidthSizeClass.COMPACT, WindowWidthSizeClass.fromDp(599))
        assertEquals(WindowWidthSizeClass.MEDIUM, WindowWidthSizeClass.fromDp(600))
        assertEquals(WindowWidthSizeClass.MEDIUM, WindowWidthSizeClass.fromDp(839))
        assertEquals(WindowWidthSizeClass.EXPANDED, WindowWidthSizeClass.fromDp(840))
        assertEquals(WindowWidthSizeClass.EXPANDED, WindowWidthSizeClass.fromDp(4096))
    }

    @Test
    fun `height breakpoints are exact`() {
        assertEquals(WindowHeightSizeClass.COMPACT, WindowHeightSizeClass.fromDp(1))
        assertEquals(WindowHeightSizeClass.COMPACT, WindowHeightSizeClass.fromDp(479))
        assertEquals(WindowHeightSizeClass.MEDIUM, WindowHeightSizeClass.fromDp(480))
        assertEquals(WindowHeightSizeClass.MEDIUM, WindowHeightSizeClass.fromDp(899))
        assertEquals(WindowHeightSizeClass.EXPANDED, WindowHeightSizeClass.fromDp(900))
        assertEquals(WindowHeightSizeClass.EXPANDED, WindowHeightSizeClass.fromDp(2400))
    }

    @Test
    fun `geometry rejects non-positive dimensions`() {
        assertFailsWith<IllegalArgumentException> { WindowGeometry(0, 800) }
        assertFailsWith<IllegalArgumentException> { WindowGeometry(400, 0) }
        assertFailsWith<IllegalArgumentException> { WindowGeometry(-1, 800) }
    }

    @Test
    fun `landscape is a property of the window, not the device`() {
        assertEquals(false, WindowGeometry.PHONE_PORTRAIT.isLandscape)
        assertEquals(true, WindowGeometry.PHONE_LANDSCAPE.isLandscape)
        assertEquals(true, WindowGeometry.TABLET_LANDSCAPE.isLandscape)
        assertEquals(false, WindowGeometry.TABLET_PORTRAIT.isLandscape)
    }

    @Test
    fun `grid rejects empty dimensions`() {
        assertFailsWith<IllegalArgumentException> { GridSpec(columns = 0, rows = 5) }
        assertFailsWith<IllegalArgumentException> { GridSpec(columns = 4, rows = 0) }
    }
}
