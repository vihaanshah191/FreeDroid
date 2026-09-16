package org.freedroid.launcher.core

/** Shared fixtures. Labels chosen to exercise accents, multi-word names and near-collisions. */
internal object TestApps {

    fun entry(
        label: String,
        packageName: String = "com.example." + label.lowercase().replace(Regex("[^a-z0-9]"), ""),
        activityName: String = "$packageName.MainActivity",
        userSerial: Int = 0,
    ): AppEntry = AppEntry(
        packageName = packageName,
        activityName = activityName,
        label = label,
        userSerial = userSerial,
    )

    val CALCULATOR = entry("Calculator")
    val CALENDAR = entry("Calendar")
    val CAMERA = entry("Camera")
    val CAFE = entry("Café Finder")
    val CLOCK = entry("Clock")
    val FILES = entry("Files")
    val GMAIL = entry("Gmail")
    val GOOGLE_MAPS = entry("Google Maps")
    val MAPS = entry("Maps")
    val MAPS_GO = entry("Maps Go Navigation")
    val SETTINGS = entry("Settings")
    val F_DROID = entry("F-Droid")

    val ALL: List<AppEntry> = listOf(
        CALCULATOR, CALENDAR, CAMERA, CAFE, CLOCK,
        FILES, GMAIL, GOOGLE_MAPS, MAPS, MAPS_GO, SETTINGS, F_DROID,
    )
}
