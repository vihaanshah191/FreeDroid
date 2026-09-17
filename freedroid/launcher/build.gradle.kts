// Root build file.
//
// Declares the Kotlin plugins with `apply false` so each is resolved ONCE, here,
// and the subprojects apply the already-resolved plugin.
//
// Getting this wrong is easy in both directions:
//
//   - Declaring nothing means :core and :app each load the Kotlin plugin
//     independently. Gradle warns "The Kotlin Gradle plugin was loaded multiple
//     times in different subprojects, which is not supported and may break the
//     build", and it happens even when the versions are identical.
//
//   - Declaring only kotlin-jvm here is worse: kotlin-jvm and kotlin-android are
//     different plugin IDs backed by the SAME artifact, so :app's versioned
//     request for kotlin-android fails with "already on the classpath with an
//     unknown version". Every Kotlin plugin ID in use must be declared.
//
// AGP must be declared here too. kotlin-android resolves KotlinAndroidTarget
// against com.android.build.gradle.api.BaseVariant at apply time, so if AGP is
// absent from the classpath where kotlin-android is resolved, applying it in
// :app fails with "Could not generate a decorated class for type
// KotlinAndroidTarget". Declaring kotlin-android at the root without AGP is
// therefore not an option.
//
// This does put AGP on the buildscript classpath of every build, including a
// :core-only one. That costs a dependency resolution, but it does NOT require
// an Android SDK: resolving the plugin and applying it to a project are
// different things, and only the latter needs an SDK. The no-SDK path is
// verified, not assumed - `gradle :core:test` still works with ANDROID_HOME
// unset, because settings.gradle.kts omits :app and :uitest entirely.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.app) apply false
    alias(libs.plugins.android.test) apply false
}

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
