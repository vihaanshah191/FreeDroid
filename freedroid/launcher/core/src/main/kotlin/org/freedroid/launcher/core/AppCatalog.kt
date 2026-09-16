package org.freedroid.launcher.core

/**
 * An immutable snapshot of the launchable applications the launcher knows about.
 *
 * Immutability is the concurrency strategy. Package events arrive on a binder
 * thread while the UI reads the catalogue on the main thread; handing out a new
 * snapshot per change means neither side needs a lock, and the UI can never
 * observe a half-applied update. It also makes every state transition a pure
 * function, so the whole of package-event handling is testable off-device.
 *
 * Entries are kept in [SortOrder.ALPHABETICAL] order so the common case — render
 * the drawer — needs no sorting at all.
 */
public class AppCatalog private constructor(
    private val byKey: Map<String, AppEntry>,
) {

    /** All known entries, alphabetically ordered. */
    public val entries: List<AppEntry> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        AppSorter.sort(byKey.values.toList(), SortOrder.ALPHABETICAL)
    }

    public val size: Int get() = byKey.size

    public fun isEmpty(): Boolean = byKey.isEmpty()

    public fun containsPackage(packageName: String): Boolean =
        byKey.values.any { it.packageName == packageName }

    public fun entriesOf(packageName: String): List<AppEntry> =
        entries.filter { it.packageName == packageName }

    public fun findByKey(key: String): AppEntry? = byKey[key]

    /** Applies one package change, returning a new catalogue. This one is never mutated. */
    public fun apply(change: PackageChange): AppCatalog {
        val updated = byKey.toMutableMap()

        when (change) {
            is PackageChange.Added -> {
                change.entries.forEach { updated[it.key] = it }
            }

            is PackageChange.Updated -> {
                // Replace wholesale: an update can drop or add launcher
                // activities, and merging would leave entries pointing at
                // activities that no longer exist.
                updated.entries.removeAll { it.value.packageName == change.packageName }
                change.entries.forEach { updated[it.key] = it }
            }

            is PackageChange.Removed,
            is PackageChange.Unavailable,
            -> {
                updated.entries.removeAll { it.value.packageName == change.packageName }
            }
        }

        // Nothing actually changed - hand back the same instance so downstream
        // equality checks can skip re-rendering.
        if (updated == byKey) return this
        return AppCatalog(updated.toMap())
    }

    public fun applyAll(changes: Iterable<PackageChange>): AppCatalog =
        changes.fold(this) { catalog, change -> catalog.apply(change) }

    /** Searches this catalogue. See [AppSearcher.search]. */
    public fun search(query: String): List<SearchResult> = AppSearcher.search(query, entries)

    public fun sortedBy(order: SortOrder): List<AppEntry> = AppSorter.sort(entries, order)

    override fun equals(other: Any?): Boolean =
        this === other || (other is AppCatalog && byKey == other.byKey)

    override fun hashCode(): Int = byKey.hashCode()

    override fun toString(): String = "AppCatalog(size=$size)"

    public companion object {
        public val EMPTY: AppCatalog = AppCatalog(emptyMap())

        /**
         * Builds a catalogue. Later entries win on duplicate [AppEntry.key], which
         * matches how a re-query after an update should behave.
         */
        public fun of(entries: Iterable<AppEntry>): AppCatalog =
            AppCatalog(entries.associateBy { it.key })
    }
}
