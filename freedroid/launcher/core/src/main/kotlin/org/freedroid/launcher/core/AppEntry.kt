package org.freedroid.launcher.core

/**
 * One launchable activity, as the launcher sees it.
 *
 * Deliberately a plain value with no Android types: no `ComponentName`, no
 * `Drawable`, no `UserHandle`, no `PackageManager` reference. Icons are resolved
 * separately by the Android layer and cached there — holding a bitmap here would
 * make the catalogue expensive to copy and impossible to test off-device.
 *
 * An application can contribute more than one entry: a package may declare
 * several `LAUNCHER` activities, and the same package appears once per Android
 * user profile. [key] identifies an entry uniquely across both.
 */
public data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    /** Serial number of the Android user profile this entry belongs to. */
    val userSerial: Int = 0,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(activityName.isNotBlank()) { "activityName must not be blank" }
    }

    /** Stable identity across package updates, multiple activities, and user profiles. */
    public val key: String get() = "$packageName/$activityName#$userSerial"

    /**
     * Precomputed so filtering does not renormalise every label on every
     * keystroke. Lazy rather than eager so building a catalogue stays cheap, and
     * excluded from [equals] because it is derived.
     */
    public val normalizedLabel: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TextNormalizer.normalize(label)
    }

    internal val labelWords: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TextNormalizer.words(normalizedLabel)
    }

    internal val labelInitials: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TextNormalizer.initials(normalizedLabel)
    }
}
