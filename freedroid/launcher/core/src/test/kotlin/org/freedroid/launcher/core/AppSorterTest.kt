package org.freedroid.launcher.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AppSorterTest {

    @Test
    fun `alphabetical order ignores case`() {
        val entries = listOf(
            TestApps.entry("zebra"), TestApps.entry("Apple"), TestApps.entry("mango"),
        )
        assertContentEquals(
            listOf("Apple", "mango", "zebra"),
            AppSorter.sort(entries, SortOrder.ALPHABETICAL).map { it.label },
        )
    }

    /** Accented labels must sort next to their unaccented neighbours, not after Z. */
    @Test
    fun `alphabetical order ignores accents`() {
        val entries = listOf(
            TestApps.entry("Zoo"), TestApps.entry("École"), TestApps.entry("Apple"),
        )
        assertContentEquals(
            listOf("Apple", "École", "Zoo"),
            AppSorter.sort(entries, SortOrder.ALPHABETICAL).map { it.label },
        )
    }

    @Test
    fun `reverse alphabetical inverts the label order`() {
        val sorted = AppSorter.sort(TestApps.ALL, SortOrder.ALPHABETICAL).map { it.label }
        val reversed = AppSorter.sort(TestApps.ALL, SortOrder.REVERSE_ALPHABETICAL).map { it.label }

        assertContentEquals(sorted.reversed(), reversed)
    }

    @Test
    fun `by-package groups entries of the same package together`() {
        val entries = listOf(
            TestApps.entry("Beta", packageName = "com.a", activityName = "com.a.B"),
            TestApps.entry("Gamma", packageName = "com.b", activityName = "com.b.G"),
            TestApps.entry("Alpha", packageName = "com.a", activityName = "com.a.A"),
        )
        val sorted = AppSorter.sort(entries, SortOrder.BY_PACKAGE)

        assertContentEquals(listOf("com.a", "com.a", "com.b"), sorted.map { it.packageName })
        assertContentEquals(listOf("Alpha", "Beta", "Gamma"), sorted.map { it.label })
    }

    /**
     * A comparator that leaves ties unresolved produces a drawer that reshuffles
     * between refreshes, which reads as a rendering bug.
     */
    @Test
    fun `sorting is stable and total for duplicate labels`() {
        val entries = listOf(
            TestApps.entry("Notes", packageName = "com.c"),
            TestApps.entry("Notes", packageName = "com.a"),
            TestApps.entry("Notes", packageName = "com.b"),
        )
        val first = AppSorter.sort(entries, SortOrder.ALPHABETICAL).map { it.packageName }

        assertContentEquals(listOf("com.a", "com.b", "com.c"), first)
        repeat(20) {
            assertContentEquals(first, AppSorter.sort(entries.shuffled(), SortOrder.ALPHABETICAL).map { it.packageName })
        }
    }

    @Test
    fun `every sort order handles an empty list`() {
        SortOrder.entries.forEach { order ->
            assertEquals(emptyList(), AppSorter.sort(emptyList(), order))
        }
    }

    @Test
    fun `sorting does not mutate the input`() {
        val original = TestApps.ALL.toList()
        AppSorter.sort(original, SortOrder.REVERSE_ALPHABETICAL)
        assertContentEquals(TestApps.ALL, original)
    }
}
