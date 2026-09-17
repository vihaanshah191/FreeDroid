// FreeDroid Launcher — instrumented UI tests (launcher-test).
//
// NOT RUNNABLE IN THE CURRENT DEVELOPMENT CONTAINER. These are Android
// instrumented tests: they need the Android SDK to compile AND a device or
// emulator to run. The container has neither — no SDK, and no /dev/kvm for an
// emulator (see docs/development/ENVIRONMENT_AUDIT.md).
//
// The tests here are written but have NEVER BEEN EXECUTED. They are not
// evidence of anything, and TESTING.md records them as blocked.
//
// MODULE TYPE: com.android.test, not com.android.library.
//
// A library module cannot depend on an application module — AGP rejects
// `implementation(project(":app"))` where :app applies com.android.application.
// com.android.test is the supported way to keep instrumented tests in their own
// module: it declares the app under test via targetProjectPath, builds a
// separate test APK, and instruments the target. Its own sources live in
// src/main, because for a test module the main source set IS the test code.

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.freedroid.launcher.uitest"
    compileSdk = 36

    // The application these tests instrument.
    targetProjectPath = ":app"

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
    // A com.android.test module uses ordinary `implementation`: its main source
    // set is the instrumentation, so there is no separate androidTest variant.
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    // The tests build Compose trees of their own (Box, Modifier.size, ...), so
    // they need the Compose runtime and foundation, not only the test harness.
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.test.junit4)
    implementation(libs.compose.ui.test.manifest)
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.espresso.core)
}
