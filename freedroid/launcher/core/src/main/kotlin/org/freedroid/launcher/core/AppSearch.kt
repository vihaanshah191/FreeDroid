package org.freedroid.launcher.core

/**
 * How well an entry matched a query. Ordinal order is rank order: earlier is better.
 */
public enum class MatchQuality {
    /** The whole label equals the query. */
    EXACT,

    /** The label starts with the query: "ma" -> "Maps". */
    PREFIX,

    /** Some word in the label starts with the query: "map" -> "Google Maps". */
    WORD_PREFIX,

    /** The query matches the words' initials: "gm" -> "Google Maps". */
    INITIALS,

    /** The label contains the query anywhere: "oogle" -> "Google". */
    SUBSTRING,

    /** The query's characters appear in order, not necessarily adjacent: "gmal" -> "Gmail". */
    SUBSEQUENCE,
}

/** An entry that matched, with the quality of the match. */
public data class SearchResult(
    val entry: AppEntry,
    val quality: MatchQuality,
)

/**
 * Local application search.
 *
 * Pure and platform-agnostic, so ranking behaviour is unit-testable without a
 * device — which is the point of keeping it out of the UI layer.
 *
 * Ranking is deliberately simple and fully deterministic. Ties break by label
 * length, then normalised label, then [AppEntry.key], so the drawer never
 * reorders itself between identical queries. Non-deterministic ordering looks
 * like a rendering bug and is miserable to reproduce.
 */
public object AppSearcher {

    /**
     * Matches [query] against [entries].
     *
     * A blank query returns every entry, in the order given: an empty search box
     * in an app drawer means "show everything", not "show nothing". A non-blank
     * query with no matches returns an empty list, which is what drives the
     * drawer's empty state.
     */
    public fun search(query: String, entries: List<AppEntry>): List<SearchResult> {
        val normalizedQuery = TextNormalizer.normalize(query)
        if (normalizedQuery.isEmpty()) {
            return entries.map { SearchResult(it, MatchQuality.EXACT) }
        }

        return entries
            .mapNotNull { entry ->
                match(normalizedQuery, entry)?.let { SearchResult(entry, it) }
            }
            .sortedWith(RESULT_ORDER)
    }

    /**
     * Quality of the best match between an already-normalised [normalizedQuery]
     * and [entry], or null if it does not match at all.
     */
    public fun match(normalizedQuery: String, entry: AppEntry): MatchQuality? {
        if (normalizedQuery.isEmpty()) return MatchQuality.EXACT
        val label = entry.normalizedLabel

        return when {
            label == normalizedQuery -> MatchQuality.EXACT
            label.startsWith(normalizedQuery) -> MatchQuality.PREFIX
            entry.labelWords.any { it.startsWith(normalizedQuery) } -> MatchQuality.WORD_PREFIX
            // Only meaningful for multi-word labels; a single-word label's
            // "initials" is just its first letter, which would match far too much.
            entry.labelWords.size > 1 &&
                entry.labelInitials.startsWith(normalizedQuery) -> MatchQuality.INITIALS
            label.contains(normalizedQuery) -> MatchQuality.SUBSTRING
            isSubsequence(normalizedQuery, label) -> MatchQuality.SUBSEQUENCE
            else -> null
        }
    }

    /** True when every character of [query] appears in [target] in order. */
    internal fun isSubsequence(query: String, target: String): Boolean {
        if (query.isEmpty()) return true
        if (query.length > target.length) return false

        var queryIndex = 0
        for (character in target) {
            if (character == query[queryIndex]) {
                queryIndex++
                if (queryIndex == query.length) return true
            }
        }
        return false
    }

    private val RESULT_ORDER: Comparator<SearchResult> =
        compareBy<SearchResult> { it.quality.ordinal }
            // A shorter label containing the query is usually the one meant:
            // "Maps" should outrank "Maps Go Navigation" for "maps".
            .thenBy { it.entry.normalizedLabel.length }
            .thenBy { it.entry.normalizedLabel }
            .thenBy { it.entry.key }
}
