// Root build file. No plugins applied at the root; each module declares its own.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register("freedroidInfo") {
    group = "freedroid"
    description = "Report which modules can be built in this environment."
    doLast {
        println("FreeDroid Launcher")
        println("  modules present: " + subprojects.joinToString(", ") { it.path })
        println("  :app requires the Android SDK (ANDROID_HOME or local.properties sdk.dir)")
    }
}
