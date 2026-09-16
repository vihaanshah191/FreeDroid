package org.freedroid.launcher.core

/**
 * The result of trying to launch an application.
 *
 * Launching goes through the ordinary Android intent mechanism — the launcher
 * holds no privileged permission and gets no special treatment. That means it
 * can fail for perfectly normal reasons, and a launcher that crashes or silently
 * does nothing when a tap fails is a bad launcher. Modelling the outcomes makes
 * the recovery policy explicit and testable.
 */
public sealed interface LaunchOutcome {

    /** The activity started. */
    public data object Success : LaunchOutcome

    /**
     * The target activity no longer exists — typically the package was updated
     * or uninstalled since the catalogue was built.
     */
    public data class ActivityNotFound(val entry: AppEntry) : LaunchOutcome

    /**
     * The package is installed but not currently usable: external storage
     * unmounted, profile stopped, or the package suspended.
     */
    public data class PackageUnavailable(val entry: AppEntry) : LaunchOutcome

    /**
     * Android refused the launch. Expected for activities the launcher may not
     * start; not something to work around. The launcher must not try to escalate
     * privileges to satisfy a tap.
     */
    public data class PermissionDenied(val entry: AppEntry) : LaunchOutcome

    /** Anything else, with the platform's description preserved for logging. */
    public data class Failed(val entry: AppEntry, val reason: String) : LaunchOutcome
}

/** What the launcher should do after a failed launch. */
public enum class RecoveryAction {
    /** Nothing to do. */
    NONE,

    /** Tell the user the app could not be opened. */
    NOTIFY_USER,

    /**
     * Rebuild the catalogue from the package manager: our snapshot is provably
     * stale, because we offered the user something that no longer exists.
     */
    REFRESH_CATALOG,
}

/**
 * Maps a launch outcome to the launcher's response.
 *
 * Pure, so the recovery policy is unit-testable without provoking real launch
 * failures on a device.
 */
public object LaunchRecovery {

    public fun actionFor(outcome: LaunchOutcome): RecoveryAction = when (outcome) {
        is LaunchOutcome.Success -> RecoveryAction.NONE

        // The catalogue is demonstrably out of date: refresh it, and say so,
        // because the icon the user tapped is about to disappear.
        is LaunchOutcome.ActivityNotFound -> RecoveryAction.REFRESH_CATALOG

        // Expected to return, so keep the entry but explain the failure.
        is LaunchOutcome.PackageUnavailable -> RecoveryAction.NOTIFY_USER

        is LaunchOutcome.PermissionDenied -> RecoveryAction.NOTIFY_USER
        is LaunchOutcome.Failed -> RecoveryAction.NOTIFY_USER
    }

    /** True when the outcome proves the catalogue no longer reflects reality. */
    public fun invalidatesCatalog(outcome: LaunchOutcome): Boolean =
        actionFor(outcome) == RecoveryAction.REFRESH_CATALOG
}
