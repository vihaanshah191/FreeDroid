plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Target JVM 17 for Android compatibility, compiled by whatever JDK Gradle runs
// on (21 in the current container). No toolchain block: auto-provisioning would
// try to download a JDK, which is neither necessary nor desirable here.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

// ---------------------------------------------------------------------------
// Architectural guard.
//
// :core must never gain an Android dependency. Its whole purpose is to hold
// layout policy that cannot consult device category, and that guarantee comes
// from Android's API surface being absent from the classpath. If someone adds
// an Android dependency, the policy could start calling isTablet()-style APIs
// and the guarantee silently evaporates.
//
// Enforce it in the build rather than in review.
//
// Dependency coordinates are resolved into plain strings through a Provider so
// the task carries no live Gradle object into its action. Capturing a
// Configuration directly is not configuration-cache compatible.
// ---------------------------------------------------------------------------

abstract class VerifyNoAndroidDependencies : DefaultTask() {

    @get:Input
    abstract val dependencyCoordinates: ListProperty<String>

    @get:Input
    abstract val bannedGroupPrefixes: ListProperty<String>

    @TaskAction
    fun verify() {
        val banned = bannedGroupPrefixes.get()
        val offenders = dependencyCoordinates.get()
            .filter { coordinate -> banned.any { coordinate.startsWith(it) } }
            .distinct()
            .sorted()

        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(":core has acquired an Android dependency, which is prohibited.")
                    appendLine("The layout policy must stay unable to observe device category.")
                    appendLine()
                    appendLine("Offending artifacts:")
                    offenders.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("Android-facing code belongs in :app, which adapts platform types")
                    appendLine("to the pure inputs :core accepts.")
                }
            )
        }
        logger.lifecycle(
            ":core has no Android dependencies - layout policy stays platform-agnostic " +
                "(${dependencyCoordinates.get().size} artifacts checked)."
        )
    }
}

/** Resolved coordinates of a configuration, as plain serializable strings. */
fun coordinatesOf(configurationName: String): Provider<List<String>> =
    configurations.named(configurationName).flatMap { configuration ->
        configuration.incoming.artifacts.resolvedArtifacts.map { artifacts ->
            artifacts.map { it.id.componentIdentifier.displayName }
        }
    }

val verifyNoAndroidDependencies =
    tasks.register<VerifyNoAndroidDependencies>("verifyNoAndroidDependencies") {
        group = "verification"
        description = "Fail if :core acquires any Android dependency."

        dependencyCoordinates.set(
            coordinatesOf("compileClasspath").zip(coordinatesOf("runtimeClasspath")) { a, b -> a + b }
        )
        bannedGroupPrefixes.set(listOf("com.android", "androidx.", "android.arch", "com.google.android"))
    }

tasks.named("check") { dependsOn(verifyNoAndroidDependencies) }
