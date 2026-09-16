// FreeDroid Launcher — Android application module.
//
// NOT BUILDABLE IN THE CURRENT DEVELOPMENT CONTAINER: it requires the Android
// SDK, which is not installed (see docs/development/ENVIRONMENT_AUDIT.md).
// settings.gradle.kts omits this module when no SDK is present, so `gradle build`
// still works here and builds :core alone.
//
// Nothing in this module has been compiled or run. Treat it as unverified.

plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.freedroid.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.freedroid.launcher"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // NO signingConfig here. Platform signing happens in the AOSP build,
            // and release keys never live in this repository.
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
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The layout policy. This is the only place the decision logic comes from;
    // :app adapts platform types to it and renders the result.
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.window)
}
