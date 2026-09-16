// FreeDroid Launcher — standalone Gradle project.
//
// Two modules, deliberately separated:
//
//   :core  pure Kotlin/JVM. NO Android dependency. Holds the adaptive layout
//          policy. Builds and tests anywhere a JDK exists, including the
//          current development container.
//
//   :app   the Android launcher itself (launcher-app). Requires the Android
//          SDK, which is NOT installed in the current development container, so
//          this module is authored but not built here.
//
//   :uitest instrumented UI tests (launcher-test). Requires the SDK to compile
//          AND a device or emulator to run. Neither is available here.
//
// The split is architectural, not a workaround. Because :core cannot see the
// Android API surface, it cannot branch on device category — the rule in
// docs/architecture/OVERVIEW.md section 5.4 is enforced by the dependency graph
// rather than by code review.

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "freedroid-launcher"

include(":core")

// :app is included only when an Android SDK is present. Including it
// unconditionally would break `gradle build` in the development container with
// a confusing SDK error rather than an honest "not available here".
val androidSdkDir: String? = System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: file("local.properties")
        .takeIf { it.exists() }
        ?.readLines()
        ?.firstOrNull { it.startsWith("sdk.dir=") }
        ?.substringAfter("=")

if (androidSdkDir != null && file(androidSdkDir).isDirectory) {
    include(":app")
    include(":uitest")
    logger.lifecycle("Android SDK found at $androidSdkDir - :app and :uitest included.")
} else {
    logger.lifecycle(
        "Android SDK not found - :app and :uitest SKIPPED. " +
        "Only :core will build. Set ANDROID_HOME to build the launcher itself."
    )
}
