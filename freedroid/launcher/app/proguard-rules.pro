# FreeDroid Launcher — R8/ProGuard rules for release builds.
#
# Referenced by app/build.gradle.kts. AGP fails the release build if this file
# is absent, even when empty.
#
# Deliberately minimal. Keep rules are a way to defeat shrinking, so each one
# needs a reason.

# The launcher activity is instantiated by the system from the manifest, not
# from code, so R8 cannot see the reference.
-keep class org.freedroid.launcher.LauncherActivity { *; }

# AndroidViewModel subclasses are instantiated reflectively by the default
# ViewModelProvider factory, which needs the (Application) constructor.
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# :core is pure data and policy with no reflection; it shrinks and obfuscates
# normally. No keep rules needed.

# Keep source file and line numbers for readable crash reports, but rename the
# source file attribute so it does not leak original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
