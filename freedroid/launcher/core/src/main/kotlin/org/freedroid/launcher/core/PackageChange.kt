package org.freedroid.launcher.core

/**
 * A change to the set of installed applications.
 *
 * Modelled as data so that the launcher's reaction to package events is a pure
 * function and can be tested without installing anything. The Android layer
 * translates `LauncherApps.Callback` into these; everything downstream is
 * platform-agnostic.
 */
public sealed interface PackageChange {

    public val packageName: String

    /** A package became available and contributes [entries] launchable activities. */
    public data class Added(
        override val packageName: String,
        val entries: List<AppEntry>,
    ) : PackageChange {
        init { requireConsistent(packageName, entries) }
    }

    /**
     * A package was replaced. [entries] is the complete new set for the package,
     * not a delta.
     *
     * Replacing wholesale matters: an update can add or remove launcher
     * activities, and merging would leave stale entries pointing at activities
     * that no longer exist — which then fail at launch time.
     */
    public data class Updated(
        override val packageName: String,
        val entries: List<AppEntry>,
    ) : PackageChange {
        init { requireConsistent(packageName, entries) }
    }

    /** A package was uninstalled. */
    public data class Removed(
        override val packageName: String,
    ) : PackageChange

    /**
     * A package is temporarily unreachable — external storage unmounted, a
     * profile stopped, or the package suspended.
     *
     * Distinct from [Removed] because it is expected to come back. Both drop the
     * entries from the catalogue, but only [Unavailable] implies the launcher
     * should expect a matching [Added] later rather than treating it as a
     * permanent uninstall.
     */
    public data class Unavailable(
        override val packageName: String,
    ) : PackageChange

    public companion object {
        private fun requireConsistent(packageName: String, entries: List<AppEntry>) {
            require(packageName.isNotBlank()) { "packageName must not be blank" }
            val foreign = entries.filter { it.packageName != packageName }
            require(foreign.isEmpty()) {
                "entries must all belong to $packageName, found ${foreign.map { it.packageName }.distinct()}"
            }
        }
    }
}
