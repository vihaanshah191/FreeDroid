package org.freedroid.launcher.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import org.freedroid.launcher.core.AppEntry
import org.freedroid.launcher.core.LaunchOutcome

/**
 * Starts applications through Android's ordinary launch mechanism.
 *
 * The launcher holds no privileged permission and gets no special treatment:
 * [LauncherApps.startMainActivity] is the same public API any launcher uses, and
 * the platform decides whether the launch is allowed. Failures are reported, not
 * worked around — there is deliberately no fallback path that tries harder.
 *
 * NOT YET COMPILED OR RUN.
 */
internal class AppLauncher(context: Context) {

    private val appContext = context.applicationContext
    private val launcherApps =
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager =
        appContext.getSystemService(Context.USER_SERVICE) as UserManager

    /**
     * @param sourceBounds on-screen bounds of the tapped icon, used by the system
     *   for the launch animation. Purely cosmetic; null is fine.
     */
    fun launch(entry: AppEntry, sourceBounds: Rect? = null): LaunchOutcome {
        val user = resolveUser(entry.userSerial)
            ?: return LaunchOutcome.PackageUnavailable(entry)

        return try {
            launcherApps.startMainActivity(
                android.content.ComponentName(entry.packageName, entry.activityName),
                user,
                sourceBounds,
                null,
            )
            LaunchOutcome.Success
        } catch (e: ActivityNotFoundException) {
            // The catalogue is stale: the activity went away since we listed it.
            // LaunchRecovery maps this to a catalogue refresh.
            Log.w(TAG, "Activity missing for ${entry.key}", e)
            LaunchOutcome.ActivityNotFound(entry)
        } catch (e: SecurityException) {
            // Android refused. Report it; never attempt to escalate.
            Log.w(TAG, "Launch refused for ${entry.key}", e)
            LaunchOutcome.PermissionDenied(entry)
        } catch (e: IllegalStateException) {
            // Typically a stopped profile or an unmounted package.
            Log.w(TAG, "Package unavailable for ${entry.key}", e)
            LaunchOutcome.PackageUnavailable(entry)
        } catch (e: RuntimeException) {
            // A launcher must never crash because one tap failed. Anything else
            // that escapes becomes a reported failure, not a dead home screen.
            Log.e(TAG, "Unexpected launch failure for ${entry.key}", e)
            LaunchOutcome.Failed(entry, e.javaClass.simpleName + ": " + e.message)
        }
    }

    private fun resolveUser(serial: Int): UserHandle? =
        runCatching { userManager.getUserForSerialNumber(serial.toLong()) }
            .getOrNull()
            ?: Process.myUserHandle().takeIf { serial == 0 }

    private companion object {
        const val TAG = "FreeDroidAppLauncher"
    }
}
