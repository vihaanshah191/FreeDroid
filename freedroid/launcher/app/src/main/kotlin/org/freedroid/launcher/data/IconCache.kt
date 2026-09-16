package org.freedroid.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freedroid.launcher.core.AppEntry

/**
 * Bounded, lazily populated cache of application icons.
 *
 * Icon loading is the launcher's main performance hazard. Each icon is a binder
 * call plus drawable inflation, and a drawer with several hundred apps will
 * stutter badly if they are loaded eagerly or on the main thread. So:
 *
 *  - [load] is a suspending function on [Dispatchers.IO]; nothing decodes on the
 *    main thread.
 *  - Tiles request icons only as they scroll into view, so a large drawer costs
 *    the same as a small one at first frame.
 *  - The cache is size-bounded, because holding every icon of every installed
 *    app is a genuine memory problem on a low-RAM device.
 *
 * Deliberately not premature: there is no disk cache, no bitmap pooling and no
 * pre-scaling. Those are worth adding once there is a device to measure on.
 *
 * NOT YET COMPILED OR RUN.
 */
internal class IconCache(
    context: Context,
    maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val appContext = context.applicationContext
    private val launcherApps =
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager =
        appContext.getSystemService(Context.USER_SERVICE) as UserManager
    private val density = appContext.resources.displayMetrics.densityDpi

    private val cache = LruCache<String, Drawable>(maxEntries)

    /** Cached icon, or null if it has not been loaded yet. Safe on the main thread. */
    fun peek(entry: AppEntry): Drawable? = cache.get(entry.key)

    /**
     * Returns the icon, loading it off the main thread on first request.
     *
     * Returns null rather than throwing when an icon cannot be resolved — a
     * package can vanish mid-scroll, and a missing icon must degrade to a
     * placeholder rather than take down the drawer.
     */
    suspend fun load(entry: AppEntry): Drawable? {
        cache.get(entry.key)?.let { return it }

        return withContext(Dispatchers.IO) {
            val icon = runCatching {
                val user = resolveUser(entry.userSerial)
                launcherApps.getActivityList(entry.packageName, user)
                    .firstOrNull { it.componentName.className == entry.activityName }
                    ?.getIcon(density)
            }.getOrElse {
                Log.w(TAG, "Icon load failed for ${entry.key}", it)
                null
            }

            icon?.also { cache.put(entry.key, it) }
        }
    }

    /** Drops cached icons for a package whose contents changed. */
    fun evictPackage(packageName: String) {
        cache.snapshot().keys
            .filter { it.startsWith("$packageName/") }
            .forEach { cache.remove(it) }
    }

    fun clear(): Unit = cache.evictAll()

    private fun resolveUser(serial: Int): UserHandle =
        runCatching { userManager.getUserForSerialNumber(serial.toLong()) }
            .getOrNull() ?: Process.myUserHandle()

    private companion object {
        const val TAG = "FreeDroidIconCache"

        /**
         * Roughly a large drawer's worth of visible icons plus scroll headroom.
         * Sized by reasoning, not measurement — revisit with a profiler in Phase 4.
         */
        const val DEFAULT_MAX_ENTRIES = 256
    }
}
