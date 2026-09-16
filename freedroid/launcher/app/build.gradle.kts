// FreeDroid Launcher — Android application module (launcher-app).
//
// NOT BUILDABLE IN THE CURRENT DEVELOPMENT CONTAINER: it requires the Android
// SDK, which is not installed (see docs/development/ENVIRONMENT_AUDIT.md).
// settings.gradle.kts omits this module when no SDK is present, so `gradle build`
// still works here and builds :core alone.
//
// NOTHING IN THIS MODULE HAS BEEN COMPILED OR RUN. Treat it as unverified.

plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.freedroid.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.freedroid.launcher"
        // minSdk 33 gives us POST_NOTIFICATIONS-era behaviour and predictive back
        // without carrying compatibility paths FreeDroid will never ship.
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // NO signingConfig. Platform signing happens in the AOSP build, and
            // release keys never live in this repository.
            // See docs/security/SECURITY_MODEL.md section 9.
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The adaptive layout policy, search, sorting and catalogue. This is the only
    // source of layout and filtering decisions; :app renders what it returns.
    implementation(project(":core"))

    // Arrives transitively via activity-compose; declared explicitly so its
    // version is pinned rather than resolved transitively.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling.preview)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
