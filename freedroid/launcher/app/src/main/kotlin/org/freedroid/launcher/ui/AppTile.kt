package org.freedroid.launcher.ui

import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.freedroid.launcher.core.AppEntry
import org.freedroid.launcher.data.IconCache

/**
 * One application icon and label.
 *
 * Accessibility, deliberately not deferred:
 *  - the whole tile is one focusable target with [Role.Button], so a screen
 *    reader announces "Gmail, button" rather than reading an icon and a label
 *    as two unrelated nodes;
 *  - the icon itself is decorative (`contentDescription = null`) because the
 *    tile already carries the name;
 *  - the touch target is at least 48dp regardless of icon size, which is the
 *    documented minimum;
 *  - the label uses a Material type style, so it scales with the user's font
 *    size setting instead of being pinned to a fixed sp value.
 *
 * Performance: the icon is requested only when the tile enters composition, so a
 * drawer with hundreds of apps loads only what is visible.
 *
 * NOT YET COMPILED OR RUN.
 */
@Composable
internal fun AppTile(
    entry: AppEntry,
    iconCache: IconCache,
    onLaunch: (AppEntry, Rect?) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    // Seed from the cache so an already-loaded icon renders on the first frame
    // instead of flashing a placeholder.
    var icon by remember(entry.key) { mutableStateOf(iconCache.peek(entry)) }
    var bounds by remember(entry.key) { mutableStateOf<Rect?>(null) }

    LaunchedEffect(entry.key) {
        if (icon == null) icon = iconCache.load(entry)
    }

    Column(
        modifier = modifier
            .defaultMinSize(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onLaunch(entry, bounds) }
            .onGloballyPositioned { coordinates ->
                // Passed to the platform for the launch animation; cosmetic only.
                val rect = coordinates.boundsInWindow()
                bounds = Rect(
                    rect.left.toInt(), rect.top.toInt(),
                    rect.right.toInt(), rect.bottom.toInt(),
                )
            }
            .padding(4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = entry.label
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(
            drawable = icon,
            modifier = Modifier.size(ICON_SIZE),
        )

        if (showLabel) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Draws a platform [android.graphics.drawable.Drawable] into a Compose canvas.
 *
 * Compose has no first-class Drawable painter, and adapting one avoids
 * converting every icon to a bitmap — which would defeat adaptive and themed
 * icons and cost memory for no benefit.
 */
@Composable
private fun AppIcon(
    drawable: android.graphics.drawable.Drawable?,
    modifier: Modifier = Modifier,
) {
    if (drawable == null) {
        // Placeholder while loading, or when the icon could not be resolved.
        // A missing icon must never remove the tile: the app is still launchable
        // and the label still identifies it.
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .drawBehind { },
        )
        return
    }

    Box(
        modifier = modifier.drawBehind {
            drawIntoCanvas { canvas ->
                drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                drawable.draw(canvas.nativeCanvas)
            }
        },
    )
}

/** Android's documented minimum touch target. */
private val MIN_TOUCH_TARGET = 48.dp
private val ICON_SIZE = 48.dp
