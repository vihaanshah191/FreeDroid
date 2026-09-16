package org.freedroid.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.freedroid.launcher.core.AppCatalog
import org.freedroid.launcher.core.AppEntry
import org.freedroid.launcher.core.PackageChange

/**
 * Discovers launchable applications through Android's public package APIs.
 *
 * Nothing is hard-coded and nothing privileged is used. [LauncherApps] is the
 * supported API for exactly this purpose: it enumerates activities that declare
 * `CATEGORY_LAUNCHER`, across every user profile the caller can see, and reports
 * changes. It needs no permission beyond being the sort of app that asks.
 *
 * This class is the only thing in the launcher that talks to the package
 * manager. Everything downstream consumes [AppCatalog] and [PackageChange] from
 * `:core`, which is why package-event handling is unit-tested off-device.
 *
 * NOT YET COMPILED OR RUN.
 */
internal class PackageAppSource(context: Context) {

    private val appContext = context.applicationContext
    private val launcherApps =
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager =
        appContext.getSystemService(Context.USER_SERVICE) as UserManager

    /**
     * Enumerates every launchable activity across all visible profiles.
     *
     * Runs on [Dispatchers.IO]: this is a binder round trip per profile and can
     * take tens of milliseconds on a device with many apps. Never call it on the
     * main thread.
     */
    suspend fun loadCatalog(): AppCatalog = withContext(Dispatchers.IO) {
        val entries = profiles().flatMap { user -> entriesForUser(user) }
        AppCatalog.of(entries)
    }

    /**
     * A cold flow of package changes, one per platform callback.
     *
     * Emissions are already translated into `:core` types, so the collector never
     * touches an Android class. Registration and unregistration are tied to
     * collection via [callbackFlow]'s `awaitClose`, so a cancelled collector
     * cannot leak the platform callback. No external scope is needed or taken:
     * the flow's own lifetime is the callback's lifetime.
     */
    fun packageChanges(): Flow<PackageChange> = callbackFlow {
        val callback = object : LauncherApps.Callback() {

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                trySend(PackageChange.Added(packageName, entriesForPackage(packageName, user)))
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                trySend(PackageChange.Removed(packageName))
            }

            /**
             * An update can add or remove launcher activities, so the complete new
             * set is re-queried rather than merged. AppCatalog.apply replaces the
             * package wholesale for the same reason.
             */
            override fun onPackageChanged(packageName: String, user: UserHandle) {
                val entries = entriesForPackage(packageName, user)
                if (entries.isEmpty()) {
                    // The package still exists but no longer offers a launcher
                    // activity - correct to drop it from the drawer.
                    trySend(PackageChange.Removed(packageName))
                } else {
                    trySend(PackageChange.Updated(packageName, entries))
                }
            }

            /** Fired when external storage mounts, or a profile becomes available. */
            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                packageNames.forEach { name ->
                    trySend(PackageChange.Added(name, entriesForPackage(name, user)))
                }
            }

            /** Fired when external storage unmounts, or a profile stops. */
            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                packageNames.forEach { trySend(PackageChange.Unavailable(it)) }
            }

            override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) {
                packageNames.forEach { trySend(PackageChange.Unavailable(it)) }
            }

            override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) {
                packageNames.forEach { name ->
                    trySend(PackageChange.Added(name, entriesForPackage(name, user)))
                }
            }
        }

        launcherApps.registerCallback(callback)
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    private fun profiles(): List<UserHandle> =
        runCatching { userManager.userProfiles }
            .getOrElse {
                Log.w(TAG, "Could not enumerate user profiles; falling back to current user", it)
                listOf(Process.myUserHandle())
            }

    private fun entriesForUser(user: UserHandle): List<AppEntry> =
        queryActivities(packageName = null, user = user)

    private fun entriesForPackage(packageName: String, user: UserHandle): List<AppEntry> =
        queryActivities(packageName = packageName, user = user)

    /**
     * A package can disappear between the callback firing and this query running,
     * so failures here are expected rather than exceptional. Returning an empty
     * list lets the catalogue drop the package, which is the correct outcome.
     */
    private fun queryActivities(packageName: String?, user: UserHandle): List<AppEntry> =
        runCatching {
            val serial = userManager.getSerialNumberForUser(user).toInt()
            launcherApps.getActivityList(packageName, user).map { activity ->
                AppEntry(
                    packageName = activity.applicationInfo.packageName,
                    activityName = activity.componentName.className,
                    label = activity.label?.toString().orEmpty()
                        .ifBlank { activity.applicationInfo.packageName },
                    userSerial = serial,
                )
            }
        }.getOrElse { error ->
            Log.w(TAG, "Activity query failed for ${packageName ?: "all packages"}", error)
            emptyList()
        }

    private companion object {
        const val TAG = "FreeDroidAppSource"
    }
}
