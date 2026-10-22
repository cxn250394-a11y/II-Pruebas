package mx.charly.canal

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SinglePhotoConsentTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val account = 970071L
    private val source = -701L
    private val destination = -702L
    private fun prefs() = RelayPreferences(context, account)
    private fun route(): RelayPreferences = prefs().also {
        assertTrue(it.selectDestination(destination))
        assertTrue(it.selectSourcePolicy(source, RelaySourcePolicy.FANO_DIRECT, 100, 1000))
    }
    @After fun cleanup() {
        listOf(account, account + 1).forEach {
            context.getSharedPreferences(RelayPreferences.fileName(it), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @Test fun consentDefaultsOffAndRequiresBoundFanoAndDestination() {
        val p = prefs()
        assertFalse(p.singlePhotoOnlyConsent(source))
        assertFalse(p.setSinglePhotoOnlyConsent(source, true))
        assertTrue(p.selectSourcePolicy(source, RelaySourcePolicy.FANO_DIRECT, 100, 1000))
        assertFalse(p.setSinglePhotoOnlyConsent(source, true))
        assertTrue(p.selectDestination(destination))
        assertFalse(p.singlePhotoOnlyConsent(source))
        assertTrue(p.setSinglePhotoOnlyConsent(source, true))
        assertTrue(p.singlePhotoOnlyConsent(source))
        assertFalse(RelayPreferences(context, account + 1).singlePhotoOnlyConsent(source))
    }
    @Test fun photoGrantChangesRevisionAndClosesOutputWithoutOpeningCompiledFuse() {
        val p = route()
        assertTrue(p.changeEnabled(true))
        val before = p.revision()
        assertTrue(p.setSinglePhotoOnlyConsent(source, true))
        assertEquals(before + 1, prefs().revision())
        assertFalse(p.enabled)
        assertTrue(p.singlePhotoOnlyConsent(source))
        assertFalse(RelayRealGate.isOpen(true))
    }
    @Test fun destinationPolicyAndStopNeverCarryPhotoConsentForward() {
        val p = route()
        assertTrue(p.setSinglePhotoOnlyConsent(source, true))
        assertTrue(p.selectDestination(destination - 1))
        assertFalse(p.singlePhotoOnlyConsent(source))
        assertTrue(p.setSinglePhotoOnlyConsent(source, true))
        assertTrue(p.selectSourcePolicy(source, RelaySourcePolicy.FANO_DIRECT, 101, 1001))
        assertFalse(p.singlePhotoOnlyConsent(source))
        assertTrue(p.setSinglePhotoOnlyConsent(source, true))
        assertTrue(p.stop())
        assertFalse(p.singlePhotoOnlyConsent(source))
        assertEquals(101L, p.sourceBaseline(source))
        assertEquals(destination - 1, p.destinationChatId())
    }
    @Test fun unicoreyAndUnboundSourcesCannotInheritPhotoOnlyPermission() {
        val p = route()
        assertTrue(p.setSinglePhotoOnlyConsent(source, true))
        assertTrue(p.selectSourcePolicy(source, RelaySourcePolicy.UNICOREY_MEX, 100, 1000))
        assertFalse(p.singlePhotoOnlyConsent(source))
        assertFalse(p.setSinglePhotoOnlyConsent(source, true))
        assertFalse(p.setSinglePhotoOnlyConsent(source - 99, true))
    }
    @Test fun offAndBackOnCannotReviveTheOldPhotoGrant() {
        val p = route()
        assertTrue(p.setSinglePhotoOnlyConsent(source, true))
        val before = p.revision()
        assertTrue(p.changeEnabled(false))
        assertTrue(p.changeEnabled(true))
        assertFalse(p.singlePhotoOnlyConsent(source))
        assertEquals(before + 2, p.revision())
    }
}
