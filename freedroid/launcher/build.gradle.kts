// Root build file.
//
// Deliberately declares NO plugins, not even with `apply false`.
//
// Declaring the Kotlin plugin here puts it on the shared buildscript classpath
// with an unresolved version, and :app's versioned request for
// `org.jetbrains.kotlin.android` then fails with "already on the classpath with
// an unknown version". Declaring AGP here instead would make every :core-only
// build resolve the Android Gradle Plugin, which is pointless in an environment
// with no SDK. Each module declaring what it needs avoids both.

tasks.register("freedroidInfo") {
    group = "freedroid"
    description = "Report which modules can be built in this environment."
    val modulePaths = subprojects.map { it.path }
    doLast {
        println("FreeDroid Launcher")
        println("  modules present: " + modulePaths.joinToString(", "))
        println("  :app and :uitest require the Android SDK (ANDROID_HOME or local.properties sdk.dir)")
    }
}
