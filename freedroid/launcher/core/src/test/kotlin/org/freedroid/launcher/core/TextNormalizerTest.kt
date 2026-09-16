package org.freedroid.launcher.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextNormalizerTest {

    @Test
    fun `normalisation lowercases`() {
        assertEquals("settings", TextNormalizer.normalize("Settings"))
        assertEquals("settings", TextNormalizer.normalize("SETTINGS"))
    }

    @Test
    fun `normalisation strips accents`() {
        assertEquals("cafe", TextNormalizer.normalize("Café"))
        assertEquals("uber", TextNormalizer.normalize("Über"))
        assertEquals("naive", TextNormalizer.normalize("naïve"))
    }

    /**
     * Kotlin's lowercase() is locale-invariant. If it were locale-sensitive, a
     * Turkish locale would map "I" to a dotless i and silently break search for
     * those users - a bug that would never show up in testing elsewhere.
     */
    @Test
    fun `normalisation is locale-invariant for dotted I`() {
        assertEquals("instagram", TextNormalizer.normalize("Instagram"))
        assertEquals("i", TextNormalizer.normalize("I"))
    }

    @Test
    fun `normalisation collapses and trims whitespace`() {
        assertEquals("google maps", TextNormalizer.normalize("  Google   Maps  "))
        assertEquals("a b", TextNormalizer.normalize("A\t\nB"))
    }

    @Test
    fun `words splits on common separators`() {
        assertEquals(listOf("google", "maps"), TextNormalizer.words("google maps"))
        assertEquals(listOf("f", "droid"), TextNormalizer.words("f-droid"))
        assertEquals(listOf("my", "app"), TextNormalizer.words("my_app"))
        assertEquals(listOf("a", "b", "c"), TextNormalizer.words("a.b/c"))
    }

    @Test
    fun `initials take the first letter of each word`() {
        assertEquals("gm", TextNormalizer.initials("google maps"))
        assertEquals("fd", TextNormalizer.initials("f-droid"))
        assertEquals("s", TextNormalizer.initials("settings"))
    }

    @Test
    fun `empty and blank input normalise to empty`() {
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("   "))
        assertTrue(TextNormalizer.words("").isEmpty())
        assertEquals("", TextNormalizer.initials(""))
    }
}
