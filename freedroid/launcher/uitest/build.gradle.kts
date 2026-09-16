// FreeDroid Launcher — instrumented UI tests (launcher-test).
//
// NOT RUNNABLE IN THE CURRENT DEVELOPMENT CONTAINER. These are Android
// instrumented tests: they need the Android SDK to compile AND a device or
// emulator to run. The container has neither — no SDK, and no /dev/kvm for an
// emulator (see docs/development/ENVIRONMENT_AUDIT.md).
//
// The tests below are written but have NEVER BEEN EXECUTED. They are not
// evidence of anything yet, and TESTING.md records them as blocked.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.freedroid.launcher.uitest"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    androidTestImplementation(project(":app"))
    androidTestImplementation(project(":core"))

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)

    debugImplementation(libs.compose.ui.test.manifest)
}
