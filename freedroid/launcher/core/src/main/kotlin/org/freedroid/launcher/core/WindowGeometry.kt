package org.freedroid.launcher.core

/**
 * The size of the window the launcher has been given, in density-independent pixels.
 *
 * This is deliberately the *window*, not the display. On a tablet in split-screen
 * the window is a fraction of the display; on a foldable it changes size mid-session
 * without the device changing at all. Every layout decision in this module is a
 * function of this value and nothing else.
 *
 * There is no device identity here on purpose — no model, no "is tablet" flag, no
 * screen diagonal. Callers cannot supply one, so policy cannot depend on one.
 */
public data class WindowGeometry(
    val widthDp: Int,
    val heightDp: Int,
) {
    init {
        require(widthDp > 0) { "widthDp must be positive, was $widthDp" }
        require(heightDp > 0) { "heightDp must be positive, was $heightDp" }
    }

    /** True when the window is wider than it is tall. Not a proxy for device orientation. */
    public val isLandscape: Boolean get() = widthDp > heightDp

    public val widthSizeClass: WindowWidthSizeClass get() = WindowWidthSizeClass.fromDp(widthDp)

    public val heightSizeClass: WindowHeightSizeClass get() = WindowHeightSizeClass.fromDp(heightDp)

    public companion object {
        /**
         * Named geometries used in tests and documentation. These describe *window sizes
         * that occur in practice*, not devices — `TABLET_LANDSCAPE` is the window a tablet
         * gives a full-screen app, and the same tablet in split-screen produces
         * [PHONE_PORTRAIT]-like geometry instead.
         */
        public val PHONE_PORTRAIT: WindowGeometry = WindowGeometry(411, 891)
        public val PHONE_LANDSCAPE: WindowGeometry = WindowGeometry(891, 411)
        public val FOLDABLE_CLOSED: WindowGeometry = WindowGeometry(360, 816)
        public val FOLDABLE_OPEN: WindowGeometry = WindowGeometry(674, 841)
        public val TABLET_PORTRAIT: WindowGeometry = WindowGeometry(800, 1280)
        public val TABLET_LANDSCAPE: WindowGeometry = WindowGeometry(1280, 800)
        public val SPLIT_SCREEN_HALF: WindowGeometry = WindowGeometry(411, 400)
        public val DESKTOP: WindowGeometry = WindowGeometry(1920, 1080)
    }
}
