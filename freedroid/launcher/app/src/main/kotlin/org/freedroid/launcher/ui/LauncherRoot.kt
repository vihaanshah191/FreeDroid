package org.freedroid.launcher.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.freedroid.launcher.core.LauncherLayout
import org.freedroid.launcher.core.LayoutPolicy
import org.freedroid.launcher.core.WindowGeometry

/**
 * Root of the launcher UI, and the single place the adaptive layout is decided.
 *
 * [BoxWithConstraints] reports the size of the space this composable actually
 * occupies — the app's window, not the display. That is the correct input, and
 * it is also self-maintaining: a split-screen resize, a fold, or a free-form
 * window drag re-runs the constraints and the layout follows, with no
 * configuration-change plumbing and no listener to forget to unregister.
 *
 * There is no device check anywhere in this tree. There is nothing to check
 * with: [LayoutPolicy] lives in `:core`, which cannot see the Android API
 * surface at all.
 *
 * NOT YET COMPILED OR RUN.
 */
@Composable
internal fun LauncherRoot(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    state.message?.let { message ->
        LaunchedEffect(message.id) {
            snackbarHostState.showSnackbar(message.text)
            viewModel.consumeMessage()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // maxWidth/maxHeight are already density-independent, so this is a
        // direct translation with no display metrics involved.
        val geometry = WindowGeometry(
            widthDp = maxWidth.value.toInt().coerceAtLeast(1),
            heightDp = maxHeight.value.toInt().coerceAtLeast(1),
        )
        val layout: LauncherLayout = LayoutPolicy.decide(geometry)

        HomeScreen(
            state = state,
            layout = layout,
            iconCache = viewModel.iconCache,
            onQueryChange = viewModel::onQueryChanged,
            onClearQuery = viewModel::clearQuery,
            onLaunch = viewModel::launch,
        )

        SnackbarHost(hostState = snackbarHostState)
    }
}
