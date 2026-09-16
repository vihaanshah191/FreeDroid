package org.freedroid.launcher.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaunchRecoveryTest {

    private val entry = TestApps.GMAIL

    @Test
    fun `success needs no recovery`() {
        assertEquals(RecoveryAction.NONE, LaunchRecovery.actionFor(LaunchOutcome.Success))
        assertFalse(LaunchRecovery.invalidatesCatalog(LaunchOutcome.Success))
    }

    /**
     * Offering the user an icon whose activity no longer exists proves the
     * snapshot is stale, so the catalogue is rebuilt rather than left to drift.
     */
    @Test
    fun `a missing activity invalidates the catalogue`() {
        val outcome = LaunchOutcome.ActivityNotFound(entry)

        assertEquals(RecoveryAction.REFRESH_CATALOG, LaunchRecovery.actionFor(outcome))
        assertTrue(LaunchRecovery.invalidatesCatalog(outcome))
    }

    /** An unavailable package is expected back, so the entry stays and the user is told. */
    @Test
    fun `an unavailable package notifies without invalidating`() {
        val outcome = LaunchOutcome.PackageUnavailable(entry)

        assertEquals(RecoveryAction.NOTIFY_USER, LaunchRecovery.actionFor(outcome))
        assertFalse(LaunchRecovery.invalidatesCatalog(outcome))
    }

    /**
     * A refused launch is Android enforcing its own rules. The launcher reports it
     * and stops - it holds no privileged permission and must not try to escalate.
     */
    @Test
    fun `permission denial is reported, never worked around`() {
        assertEquals(
            RecoveryAction.NOTIFY_USER,
            LaunchRecovery.actionFor(LaunchOutcome.PermissionDenied(entry)),
        )
    }

    @Test
    fun `an unclassified failure notifies the user`() {
        assertEquals(
            RecoveryAction.NOTIFY_USER,
            LaunchRecovery.actionFor(LaunchOutcome.Failed(entry, "transaction too large")),
        )
    }

    /** A silent failure is the worst outcome: the user taps and nothing happens. */
    @Test
    fun `no failure outcome is silently ignored`() {
        val failures = listOf(
            LaunchOutcome.ActivityNotFound(entry),
            LaunchOutcome.PackageUnavailable(entry),
            LaunchOutcome.PermissionDenied(entry),
            LaunchOutcome.Failed(entry, "boom"),
        )
        failures.forEach {
            assertTrue(
                LaunchRecovery.actionFor(it) != RecoveryAction.NONE,
                "$it would fail silently",
            )
        }
    }
}
