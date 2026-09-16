package org.freedroid.launcher.core

/** Orders the app drawer can present entries in. */
public enum class SortOrder {
    /** A-Z by display label. The default, and what users expect to be able to scan. */
    ALPHABETICAL,

    /** Z-A. */
    REVERSE_ALPHABETICAL,

    /** Grouped by package, then by label. Useful for packages with several activities. */
    BY_PACKAGE,
}

/**
 * Sorts application entries.
 *
 * Separate from search so the drawer's resting order and its filtered order are
 * independently testable. Every comparator here is total and deterministic: a
 * comparator with ties resolved arbitrarily produces a drawer that shuffles
 * between refreshes.
 */
public object AppSorter {

    public fun sort(entries: List<AppEntry>, order: SortOrder): List<AppEntry> =
        entries.sortedWith(comparatorFor(order))

    public fun comparatorFor(order: SortOrder): Comparator<AppEntry> = when (order) {
        SortOrder.ALPHABETICAL -> ALPHABETICAL
        SortOrder.REVERSE_ALPHABETICAL -> REVERSE_ALPHABETICAL
        SortOrder.BY_PACKAGE -> BY_PACKAGE
    }

    /**
     * Compares on the normalised label so accents and case do not scatter related
     * entries, then falls through to [AppEntry.key] so the order is total.
     */
    private val ALPHABETICAL: Comparator<AppEntry> =
        compareBy<AppEntry> { it.normalizedLabel }.thenBy { it.key }

    /**
     * Reverses only the label comparison. The key tiebreak stays ascending so
     * two entries with the same label keep a stable relative order in both
     * directions rather than swapping.
     */
    private val REVERSE_ALPHABETICAL: Comparator<AppEntry> =
        compareByDescending<AppEntry> { it.normalizedLabel }.thenBy { it.key }

    private val BY_PACKAGE: Comparator<AppEntry> =
        compareBy<AppEntry> { it.packageName }
            .thenBy { it.normalizedLabel }
            .thenBy { it.key }
}
