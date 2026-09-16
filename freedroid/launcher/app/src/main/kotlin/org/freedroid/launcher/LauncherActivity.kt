package org.freedroid.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import org.freedroid.launcher.ui.FreeDroidLauncherTheme
import org.freedroid.launcher.ui.LauncherRoot
import org.freedroid.launcher.ui.LauncherViewModel

/**
 * The FreeDroid home screen.
 *
 * Deliberately thin: it sets up the window and hands off to Compose. All layout
 * decisions live in `:core`, all data access in `org.freedroid.launcher.data`.
 *
 * There is no `isTablet()` here, no `smallestScreenWidthDp` read, and no
 * layout-selection resource qualifier keyed to device class. The layout is
 * derived from the current window inside [LauncherRoot], every composition.
 *
 * The activity declares `android:configChanges` for size-affecting changes, so
 * it is not recreated on rotation, fold or resize. Compose observes the new
 * constraints and re-lays out, which is both faster and free of the state loss
 * that recreation causes.
 *
 * NOT YET COMPILED OR RUN.
 */
class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars so the wallpaper is visible edge to edge.
        // Content insets are handled by safeDrawingPadding in the Compose tree.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            FreeDroidLauncherTheme {
                val viewModel: LauncherViewModel = viewModel()
                LauncherRoot(viewModel = viewModel)
            }
        }
    }
}
