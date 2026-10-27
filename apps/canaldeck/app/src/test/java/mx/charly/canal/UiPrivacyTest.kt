package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

class UiPrivacyTest {
    @Test fun unknownPendingProtectedAndSecretChatsFailClosed() {
        assertFalse(UiPrivacy.safeChat(false, false, false, false))
        assertFalse(UiPrivacy.safeChat(true, true, false, false))
        assertFalse(UiPrivacy.safeChat(true, false, true, false))
        assertFalse(UiPrivacy.safeChat(true, false, false, true))
        assertTrue(UiPrivacy.safeChat(true, false, false, false))
    }
    @Test fun ocrRequiresSavableNonSelfDestructingContentAndCurrentPrivacy() {
        assertTrue(UiPrivacy.safeMedia(true, false, false, false, true, false))
        assertFalse(UiPrivacy.safeMedia(false, false, false, false, true, false))
        assertFalse(UiPrivacy.safeMedia(true, true, false, false, true, false))
        assertFalse(UiPrivacy.safeMedia(true, false, true, false, true, false))
        assertFalse(UiPrivacy.safeMedia(true, false, false, true, true, false))
        assertFalse(UiPrivacy.safeMedia(true, false, false, false, false, false))
        assertFalse(UiPrivacy.safeMedia(true, false, false, false, true, true))
    }
    @Test fun backupIsBoundToTheOriginalSessionAndAccount() {
        val origin=Any(); val other=Any()
        assertTrue(BackupSessionGuard.matches(origin, 42, origin, 42, true))
        assertFalse(BackupSessionGuard.matches(origin, 42, other, 42, true))
        assertFalse(BackupSessionGuard.matches(origin, 42, origin, 43, true))
        assertFalse(BackupSessionGuard.matches(origin, 42, origin, 42, false))
        assertFalse(BackupSessionGuard.matches(null, 0, null, 0, true))
    }
    @Test fun pausedViewRejectsReplacementSessionWithSameAccountIdOnResume() {
        val beforePause=Any(); val replacement=Any(); val account=42L
        assertTrue(BackupSessionGuard.matches(beforePause, account, beforePause, account, true))
        // The activity was paused while the session instance was replaced.
        assertFalse(BackupSessionGuard.matches(beforePause, account, replacement, account, true))
    }
    @Test fun actionFromNewInstanceCannotUseOldViewOwnerEvenWithSameAccount() {
        val viewOwner=Any(); val replacement=Any(); val account=42L
        assertFalse(AccountActionGuard.matches(replacement, viewOwner, account, replacement, account, true))
        assertFalse(AccountActionGuard.matches(viewOwner, viewOwner, account, replacement, account, true))
        assertTrue(AccountActionGuard.matches(replacement, replacement, account, replacement, account, true))
    }
}
