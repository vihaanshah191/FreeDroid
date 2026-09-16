package org.freedroid.launcher.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSearcherTest {

    private fun labels(results: List<SearchResult>) = results.map { it.entry.label }

    // -- matching ------------------------------------------------------------

    @Test
    fun `search is case-insensitive`() {
        val lower = AppSearcher.search("settings", TestApps.ALL)
        val upper = AppSearcher.search("SETTINGS", TestApps.ALL)
        val mixed = AppSearcher.search("SeTtInGs", TestApps.ALL)

        assertEquals(labels(lower), labels(upper))
        assertEquals(labels(lower), labels(mixed))
        assertEquals("Settings", lower.first().entry.label)
    }

    @Test
    fun `search ignores accents in both query and label`() {
        assertTrue(labels(AppSearcher.search("cafe", TestApps.ALL)).contains("Café Finder"))
        assertTrue(labels(AppSearcher.search("café", TestApps.ALL)).contains("Café Finder"))
    }

    @Test
    fun `exact match outranks prefix match`() {
        val results = AppSearcher.search("maps", TestApps.ALL)

        assertEquals("Maps", results.first().entry.label)
        assertEquals(MatchQuality.EXACT, results.first().quality)
    }

    @Test
    fun `word prefix matches a later word`() {
        val quality = AppSearcher.match("maps", TestApps.GOOGLE_MAPS)
        assertEquals(MatchQuality.WORD_PREFIX, quality)
    }

    @Test
    fun `initials match multi-word labels`() {
        assertEquals(MatchQuality.INITIALS, AppSearcher.match("gm", TestApps.GOOGLE_MAPS))
        assertEquals(MatchQuality.INITIALS, AppSearcher.match("fd", TestApps.F_DROID))
    }

    /**
     * A single-word label's "initials" is just its first letter. Treating that as
     * an initials match would make every one-letter query match nearly everything.
     */
    @Test
    fun `initials do not apply to single-word labels`() {
        assertEquals(MatchQuality.PREFIX, AppSearcher.match("c", TestApps.CLOCK))
        assertEquals(MatchQuality.PREFIX, AppSearcher.match("s", TestApps.SETTINGS))
    }

    @Test
    fun `substring matches inside a word`() {
        assertEquals(MatchQuality.SUBSTRING, AppSearcher.match("lock", TestApps.CLOCK))
    }

    @Test
    fun `subsequence matches typo-tolerant input`() {
        assertEquals(MatchQuality.SUBSEQUENCE, AppSearcher.match("gml", TestApps.GMAIL))
        assertEquals(MatchQuality.SUBSEQUENCE, AppSearcher.match("clcltr", TestApps.CALCULATOR))
    }

    @Test
    fun `non-matching query returns null quality`() {
        assertNull(AppSearcher.match("zzzz", TestApps.SETTINGS))
        assertNull(AppSearcher.match("xyz", TestApps.CLOCK))
    }

    // -- ranking -------------------------------------------------------------

    @Test
    fun `results are ranked by match quality`() {
        val results = AppSearcher.search("ca", TestApps.ALL)
        val qualities = results.map { it.quality }

        assertEquals(qualities.sortedBy { it.ordinal }, qualities, "results must be rank-ordered")
    }

    /** A shorter label containing the query is usually the one the user meant. */
    @Test
    fun `shorter labels outrank longer ones at equal quality`() {
        val results = AppSearcher.search("maps go", TestApps.ALL)
        assertEquals("Maps Go Navigation", results.first().entry.label)

        val prefixMatches = AppSearcher.search("map", TestApps.ALL)
            .filter { it.quality == MatchQuality.PREFIX }
            .map { it.entry.label }
        assertEquals(listOf("Maps", "Maps Go Navigation"), prefixMatches)
    }

    @Test
    fun `ranking is deterministic across repeated searches`() {
        val first = labels(AppSearcher.search("c", TestApps.ALL))
        repeat(50) {
            assertEquals(first, labels(AppSearcher.search("c", TestApps.ALL)))
        }
    }

    /** Entries with identical labels must still order deterministically. */
    @Test
    fun `identical labels are broken by stable key order`() {
        val duplicates = listOf(
            TestApps.entry("Notes", packageName = "com.b.notes"),
            TestApps.entry("Notes", packageName = "com.a.notes"),
        )
        val results = AppSearcher.search("notes", duplicates)

        assertContentEquals(
            listOf("com.a.notes", "com.b.notes"),
            results.map { it.entry.packageName },
        )
    }

    // -- empty states --------------------------------------------------------

    /** An empty search box in a drawer means "show everything". */
    @Test
    fun `blank query returns every entry in the given order`() {
        assertEquals(TestApps.ALL.size, AppSearcher.search("", TestApps.ALL).size)
        assertEquals(TestApps.ALL.size, AppSearcher.search("   ", TestApps.ALL).size)
        assertContentEquals(
            TestApps.ALL.map { it.label },
            labels(AppSearcher.search("", TestApps.ALL)),
        )
    }

    /** This empty list is what drives the drawer's no-results state. */
    @Test
    fun `no matches returns an empty list`() {
        assertTrue(AppSearcher.search("zzzzzzz", TestApps.ALL).isEmpty())
    }

    @Test
    fun `searching an empty catalogue is safe`() {
        assertTrue(AppSearcher.search("anything", emptyList()).isEmpty())
        assertTrue(AppSearcher.search("", emptyList()).isEmpty())
    }

    // -- subsequence helper --------------------------------------------------

    @Test
    fun `subsequence helper handles edges`() {
        assertTrue(AppSearcher.isSubsequence("", "anything"))
        assertTrue(AppSearcher.isSubsequence("abc", "abc"))
        assertTrue(AppSearcher.isSubsequence("ac", "abc"))
        assertTrue(!AppSearcher.isSubsequence("ca", "abc"))
        assertTrue(!AppSearcher.isSubsequence("abcd", "abc"))
    }

    // -- performance-shaped sanity ------------------------------------------

    /**
     * Not a benchmark - a guard that filtering a realistically large drawer stays
     * far away from anything a user would perceive as lag.
     */
    @Test
    fun `filtering a large catalogue stays fast`() {
        val many = (1..2_000).map { TestApps.entry("Application $it", packageName = "com.example.app$it") }

        val start = System.nanoTime()
        repeat(20) { AppSearcher.search("app", many) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(elapsedMs < 2_000, "20 searches over 2000 apps took ${elapsedMs}ms")
    }
}
