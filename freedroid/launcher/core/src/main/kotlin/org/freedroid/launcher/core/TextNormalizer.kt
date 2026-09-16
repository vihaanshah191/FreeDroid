package org.freedroid.launcher.core

import java.text.Normalizer

/**
 * Normalises application labels and search queries into a comparable form.
 *
 * Uses `java.text.Normalizer`, which is JDK API present on Android since API 9,
 * so this stays free of Android dependencies.
 *
 * Normalisation is: decompose accents, drop combining marks, lowercase, collapse
 * whitespace. That makes "Café" and "cafe" match, which users expect and which
 * a naive `equals` would get wrong.
 */
public object TextNormalizer {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")
    private val WORD_SEPARATORS = Regex("[\\s\\-_./:]+")

    /** Lowercased, accent-stripped, whitespace-collapsed form of [raw]. */
    public fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            // lowercase() is locale-invariant in Kotlin, so a Turkish locale
            // cannot turn "I" into a dotless i and break matching.
            .lowercase()
            .replace(WHITESPACE, " ")
            .trim()

    /** Splits an already-normalised string into words for prefix and initial matching. */
    public fun words(normalized: String): List<String> =
        normalized.split(WORD_SEPARATORS).filter { it.isNotEmpty() }

    /** First letter of each word: "google maps" -> "gm". */
    public fun initials(normalized: String): String =
        words(normalized).map { it.first() }.joinToString("")
}
