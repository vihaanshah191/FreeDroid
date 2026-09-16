package org.freedroid.launcher.ui

import android.graphics.Rect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.unit.dp
import org.freedroid.launcher.R
import org.freedroid.launcher.core.AppEntry
import org.freedroid.launcher.core.HotseatPosition
import org.freedroid.launcher.core.LauncherLayout
import org.freedroid.launcher.data.IconCache

/**
 * The home screen: wallpaper behind, app drawer in front, hotseat placed by the
 * adaptive policy.
 *
 * The background is transparent on purpose. `windowShowWallpaper` in the theme
 * makes the system composite the user's wallpaper behind the window, which needs
 * no permission at all — see [WallpaperBackground].
 *
 * This first version presents the drawer as the primary surface. Workspace
 * pages, folders and widgets are Phase 4.
 *
 * NOT YET COMPILED OR RUN.
 */
@Composable
internal fun HomeScreen(
    state: LauncherUiState,
    layout: LauncherLayout,
    iconCache: IconCache,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onLaunch: (AppEntry, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    WallpaperBackground(modifier = modifier.fillMaxSize()) {
        // safeDrawingPadding keeps content clear of the status bar, navigation
        // bar, cutouts and the IME without hard-coding any inset values.
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            when (layout.hotseatPosition) {
                HotseatPosition.BOTTOM -> Column(modifier = Modifier.fillMaxSize()) {
                    AppDrawer(
                        state = state,
                        layout = layout,
                        iconCache = iconCache,
                        onQueryChange = onQueryChange,
                        onClearQuery = onClearQuery,
                        onLaunch = onLaunch,
                        modifier = Modifier.weight(1f),
                    )
                    Hotseat(
                        entries = state.catalog.entries.take(layout.hotseatCapacity),
                        iconCache = iconCache,
                        onLaunch = onLaunch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                HotseatPosition.SIDE -> Row(modifier = Modifier.fillMaxSize()) {
                    HotseatRail(
                        entries = state.catalog.entries.take(layout.hotseatCapacity),
                        iconCache = iconCache,
                        onLaunch = onLaunch,
                        modifier = Modifier.width(RAIL_WIDTH),
                    )
                    AppDrawer(
                        state = state,
                        layout = layout,
                        iconCache = iconCache,
                        onQueryChange = onQueryChange,
                        onClearQuery = onClearQuery,
                        onLaunch = onLaunch,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Wallpaper backdrop.
 *
 * The window is transparent and `android:windowShowWallpaper="true"` in the
 * theme makes the system draw the live wallpaper behind it. This is the correct
 * approach for a launcher and requires **no permission**.
 *
 * Reading the wallpaper bitmap directly via `WallpaperManager.getDrawable()` is
 * deliberately avoided: on modern Android it is permission-gated and increasingly
 * restricted, and a launcher does not need the pixels — it needs them drawn
 * behind it, which the window flag already achieves.
 *
 * The scrim below is the fallback. If no wallpaper is set, or a device does not
 * honour the flag, the surface colour shows through at low alpha so text stays
 * legible instead of rendering on an unpredictable background.
 */
@Composable
private fun WallpaperBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = SCRIM_ALPHA),
            content = {},
        )
        content()
    }
}

@Composable
private fun Hotseat(
    entries: List<AppEntry>,
    iconCache: IconCache,
    onLaunch: (AppEntry, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    Row(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { isTraversalGroup = true },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            AppTile(
                entry = entry,
                iconCache = iconCache,
                onLaunch = onLaunch,
                showLabel = false,
            )
        }
    }
}

@Composable
private fun HotseatRail(
    entries: List<AppEntry>,
    iconCache: IconCache,
    onLaunch: (AppEntry, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .semantics { isTraversalGroup = true },
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEach { entry ->
            AppTile(
                entry = entry,
                iconCache = iconCache,
                onLaunch = onLaunch,
                showLabel = false,
            )
        }
    }
}

/** Shown while the first catalogue load is in flight. */
@Composable
internal fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.drawer_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val RAIL_WIDTH = 88.dp

/** Low enough to let the wallpaper through, high enough to keep labels legible. */
private const val SCRIM_ALPHA = 0.35f
