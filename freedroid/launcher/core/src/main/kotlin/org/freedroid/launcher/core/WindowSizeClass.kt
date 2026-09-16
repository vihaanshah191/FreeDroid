package org.freedroid.launcher.core

/**
 * Width breakpoints, matching the values Android and Jetpack WindowManager use.
 *
 * Mirrored here rather than depending on `androidx.window` so that this module
 * stays free of Android dependencies. [org.freedroid.launcher.core] must not be
 * able to observe device category, and the cheapest way to guarantee that is for
 * the Android API surface to be absent from its classpath entirely.
 *
 * The [app] module converts `androidx.window.core.layout.WindowSizeClass` into
 * [WindowGeometry]; the breakpoints below must stay in step with it. `LayoutPolicyTest`
 * asserts the boundary values so drift fails a test rather than shipping.
 */
public enum class WindowWidthSizeClass {
    /** Below 600dp. A phone in portrait, or any app in a narrow split-screen pane. */
    COMPACT,

    /** 600dp up to 840dp. A large phone in landscape, a small tablet, an open foldable. */
    MEDIUM,

    /** 840dp and above. A tablet in landscape, a desktop window, an external display. */
    EXPANDED,
    ;

    public companion object {
        public const val MEDIUM_MIN_DP: Int = 600
        public const val EXPANDED_MIN_DP: Int = 840

        public fun fromDp(widthDp: Int): WindowWidthSizeClass = when {
            widthDp < MEDIUM_MIN_DP -> COMPACT
            widthDp < EXPANDED_MIN_DP -> MEDIUM
            else -> EXPANDED
        }
    }
}

/** Height breakpoints, matching Jetpack WindowManager. See [WindowWidthSizeClass]. */
public enum class WindowHeightSizeClass {
    /** Below 480dp. A phone in landscape, or a short split-screen pane. */
    COMPACT,

    /** 480dp up to 900dp. Most phones in portrait. */
    MEDIUM,

    /** 900dp and above. Tablets in portrait. */
    EXPANDED,
    ;

    public companion object {
        public const val MEDIUM_MIN_DP: Int = 480
        public const val EXPANDED_MIN_DP: Int = 900

        public fun fromDp(heightDp: Int): WindowHeightSizeClass = when {
            heightDp < MEDIUM_MIN_DP -> COMPACT
            heightDp < EXPANDED_MIN_DP -> MEDIUM
            else -> EXPANDED
        }
    }
}
