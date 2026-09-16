package org.freedroid.launcher.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        // Typography is left at the Material default so it scales with the
        // user's font-size setting. Fixed sp values here would break that.
        content = content,
    )
}
