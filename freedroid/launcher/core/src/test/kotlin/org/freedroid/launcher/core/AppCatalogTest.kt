package org.freedroid.launcher.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppCatalogTest {

    private val catalog = AppCatalog.of(TestApps.ALL)

    @Test
    fun `catalogue is alphabetically ordered on construction`() {
        val labels = catalog.entries.map { it.normalizedLabel }
        assertContentEquals(labels.sorted(), labels)
    }

    @Test
    fun `empty catalogue behaves`() {
        assertTrue(AppCatalog.EMPTY.isEmpty())
        assertEquals(0, AppCatalog.EMPTY.size)
        assertTrue(AppCatalog.EMPTY.search("anything").isEmpty())
        assertNull(AppCatalog.EMPTY.findByKey("nope"))
    }

    // -- package lifecycle ---------------------------------------------------

    @Test
    fun `application added appears in the catalogue`() {
        val newApp = TestApps.entry("Podcasts", packageName = "org.example.podcasts")
        val updated = catalog.apply(PackageChange.Added("org.example.podcasts", listOf(newApp)))

        assertEquals(catalog.size + 1, updated.size)
        assertTrue(updated.containsPackage("org.example.podcasts"))
        assertTrue(updated.search("podcasts").isNotEmpty())
    }

    @Test
    fun `application removed disappears from the catalogue`() {
        val updated = catalog.apply(PackageChange.Removed(TestApps.GMAIL.packageName))

        assertFalse(updated.containsPackage(TestApps.GMAIL.packageName))
        assertEquals(catalog.size - 1, updated.size)
        assertTrue(updated.search("gmail").isEmpty())
    }

    /**
     * The case a merge-based implementation gets wrong: an update that drops a
     * launcher activity must drop the entry, or the drawer keeps an icon that
     * fails when tapped.
     */
    @Test
    fun `application updated replaces its entries wholesale`() {
        val pkg = "com.example.suite"
        val before = AppCatalog.of(
            listOf(
                TestApps.entry("Suite Docs", packageName = pkg, activityName = "$pkg.DocsActivity"),
                TestApps.entry("Suite Sheets", packageName = pkg, activityName = "$pkg.SheetsActivity"),
            ),
        )
        assertEquals(2, before.size)

        // The update removes the Sheets activity and renames Docs.
        val after = before.apply(
            PackageChange.Updated(
                pkg,
                listOf(TestApps.entry("Suite Documents", packageName = pkg, activityName = "$pkg.DocsActivity")),
            ),
        )

        assertEquals(1, after.size)
        assertEquals("Suite Documents", after.entries.single().label)
        assertTrue(after.entriesOf(pkg).none { it.activityName.endsWith("SheetsActivity") })
    }

    @Test
    fun `an update that adds an activity keeps both`() {
        val pkg = "com.example.suite"
        val before = AppCatalog.of(
            listOf(TestApps.entry("Suite", packageName = pkg, activityName = "$pkg.A")),
        )
        val after = before.apply(
            PackageChange.Updated(
                pkg,
                listOf(
                    TestApps.entry("Suite", packageName = pkg, activityName = "$pkg.A"),
                    TestApps.entry("Suite Editor", packageName = pkg, activityName = "$pkg.B"),
                ),
            ),
        )
        assertEquals(2, after.size)
    }

    @Test
    fun `unavailable package is dropped like a removal`() {
        val updated = catalog.apply(PackageChange.Unavailable(TestApps.FILES.packageName))

        assertFalse(updated.containsPackage(TestApps.FILES.packageName))
        assertEquals(catalog.size - 1, updated.size)
    }

    /** An unavailable package is expected back; re-adding must restore it cleanly. */
    @Test
    fun `an unavailable package can come back`() {
        val pkg = TestApps.FILES.packageName
        val gone = catalog.apply(PackageChange.Unavailable(pkg))
        val back = gone.apply(PackageChange.Added(pkg, listOf(TestApps.FILES)))

        assertEquals(catalog.size, back.size)
        assertTrue(back.containsPackage(pkg))
    }

    @Test
    fun `removing an unknown package is a no-op and returns the same instance`() {
        val updated = catalog.apply(PackageChange.Removed("com.does.not.exist"))
        assertSame(catalog, updated, "an unchanged catalogue should not allocate a new snapshot")
    }

    @Test
    fun `changes apply in sequence`() {
        val newApp = TestApps.entry("Weather", packageName = "org.example.weather")
        val result = catalog.applyAll(
            listOf(
                PackageChange.Added("org.example.weather", listOf(newApp)),
                PackageChange.Removed(TestApps.CLOCK.packageName),
                PackageChange.Removed("org.example.weather"),
            ),
        )

        assertFalse(result.containsPackage("org.example.weather"))
        assertFalse(result.containsPackage(TestApps.CLOCK.packageName))
        assertEquals(catalog.size - 1, result.size)
    }

    // -- immutability --------------------------------------------------------

    /** Immutability is what lets binder-thread events and main-thread reads coexist without a lock. */
    @Test
    fun `applying a change never mutates the original`() {
        val sizeBefore = catalog.size
        val labelsBefore = catalog.entries.map { it.label }

        catalog.apply(PackageChange.Removed(TestApps.GMAIL.packageName))
        catalog.apply(PackageChange.Added("x.y.z", listOf(TestApps.entry("New", packageName = "x.y.z"))))

        assertEquals(sizeBefore, catalog.size)
        assertContentEquals(labelsBefore, catalog.entries.map { it.label })
    }

    @Test
    fun `equal catalogues compare equal regardless of construction order`() {
        assertEquals(AppCatalog.of(TestApps.ALL), AppCatalog.of(TestApps.ALL.reversed()))
        assertEquals(AppCatalog.of(TestApps.ALL).hashCode(), AppCatalog.of(TestApps.ALL.reversed()).hashCode())
    }

    @Test
    fun `duplicate keys collapse to one entry`() {
        val duplicated = TestApps.ALL + TestApps.ALL
        assertEquals(TestApps.ALL.size, AppCatalog.of(duplicated).size)
    }

    @Test
    fun `the same package in two user profiles yields two entries`() {
        val work = TestApps.GMAIL.copy(userSerial = 10)
        val both = AppCatalog.of(listOf(TestApps.GMAIL, work))

        assertEquals(2, both.size)
        assertEquals(2, both.entriesOf(TestApps.GMAIL.packageName).size)
    }

    // -- validation ----------------------------------------------------------

    @Test
    fun `a change carrying entries from another package is rejected`() {
        val error = runCatching {
            PackageChange.Added("com.a", listOf(TestApps.entry("X", packageName = "com.b")))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException, "expected rejection, got $error")
    }

    @Test
    fun `search delegates to the searcher`() {
        assertEquals("Maps", catalog.search("maps").first().entry.label)
        assertTrue(catalog.search("zzzz").isEmpty())
    }
}
