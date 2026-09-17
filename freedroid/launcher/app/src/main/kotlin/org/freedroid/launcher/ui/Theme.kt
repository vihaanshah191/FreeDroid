package org.freedroid.launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * FreeDroid launcher theme.
 *
 * Uses the platform's dynamic colour where available so the launcher follows the
 * user's wallpaper rather than imposing a palette. FreeDroid branding colours
 * arrive in Phase 4 through `vendor/freedroid/overlay` as RROs, which is why
 * nothing here is hard-coded to a brand value — an RRO can retheme the launcher
 * without touching this source.
 *
 * NOT YET COMPILED OR RUN.
 */
@Composable
internal fun FreeDroidLauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // minSdk is 33, so dynamic colour is always available. An earlier version
    // guarded this with SDK_INT >= S and fell back to static schemes; Lint
    // correctly flagged the check as always true, and the fallbacks were dead.
    val colorScheme =
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    MaterialTheme(
        colorScheme = colorScheme,
        // Typography is left at the Material default so it scales with the
        // user's font-size setting. Fixed sp values here would break that.
        content = content,
    )
}
