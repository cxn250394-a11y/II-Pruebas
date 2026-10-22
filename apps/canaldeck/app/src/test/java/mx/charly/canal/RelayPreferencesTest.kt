package mx.charly.canal

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RelayPreferencesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val account = 91004L
    private val source = -10041L
    private val destination = -10042L
    private fun prefs() = RelayPreferences(context, account)

    @After fun clean() {
        listOf(account, account + 1, account + 90).forEach { context.getSharedPreferences(RelayPreferences.fileName(it), Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test fun defaultOffUnboundAndNoImplicitReplayBaseline() {
        val p = prefs()
        assertFalse(p.enabled)
        assertEquals(0L, p.destinationChatId())
        assertTrue(p.sources().isEmpty())
        assertNull(p.sourcePolicy(source))
        assertEquals(Long.MAX_VALUE, p.sourceBaseline(source))
        assertEquals(Long.MAX_VALUE, p.enabledAt(source))
        assertFalse(p.changeEnabled(true))
    }

    @Test fun genericResellerCannotInheritUnicoAllowlist() {
        val p = prefs()
        assertFalse(p.selectSource(source, PickWatchMode.RESELLER, 100, 1000))
        assertFalse(p.selectSource(source, PickWatchMode.DIRECT, 100, 1000))
        assertFalse(p.selectSourcePolicy(source, RelaySourcePolicy.MAZDITA_LEGACY, 100, 1000))
        assertFalse(p.selectSourcePolicy(source, RelaySourcePolicy.DIRECT_OTHER, 100, 1000))
        assertTrue(p.sources().isEmpty())
    }

    @Test fun policiesPersistByExactAccountAndId() {
        val p = prefs()
        assertTrue(p.selectSourcePolicy(source, RelaySourcePolicy.UNICOREY_MEX, 100, 1000))
        assertTrue(p.selectSourcePolicy(source - 1, RelaySourcePolicy.UNICOREY_FOREIGN, 200, 1000))
        assertEquals(RelaySourcePolicy.UNICOREY_MEX, prefs().sourcePolicy(source))
        assertEquals(RelaySourcePolicy.UNICOREY_FOREIGN, prefs().sourcePolicy(source - 1))
        assertEquals(PickWatchMode.RESELLER, prefs().sourceRole(source))
        assertNull(prefs().sourcePolicy(source - 2))
        assertTrue(RelayPreferences(context, account + 1).sources().isEmpty())
        assertEquals(0L, RelayPreferences(context, account + 1).destinationChatId())
    }

    @Test fun sourceCanNeverEqualDestinationInEitherSelectionOrder() {
        val p = prefs()
        assertTrue(p.selectDestination(destination))
        assertFalse(p.selectSourcePolicy(destination, RelaySourcePolicy.FANO_DIRECT, 50, 1000))
        assertTrue(p.selectSourcePolicy(source, RelaySourcePolicy.FANO_DIRECT, 50, 1000))
        assertFalse(p.selectDestination(source))
        assertEquals(destination, p.destinationChatId())
    }

    @Test fun stopAndReopenKeepChoicesAndMonotonicBaselines() {
        val p = prefs()
        assertTrue(p.selectDestination(destination))
        assertTrue(p.selectSourcePolicy(source, RelaySourcePolicy.FANO_DIRECT, 100, 1000))
        assertTrue(p.changeEnabled(true))
        assertTrue(p.enabled)
        assertTrue(p.stop())
        val reopen = prefs()
        assertFalse(reopen.enabled)
        assertEquals(destination, reopen.destinationChatId())
        assertEquals(100L, reopen.sourceBaseline(source))
        assertEquals(1000L, reopen.enabledAt(source))
        assertTrue(reopen.selectSource(source, PickWatchMode.OFF, 0, 1001))
        assertEquals(100L, reopen.sourceBaseline(source))
        assertTrue(reopen.selectSourcePolicy(source, RelaySourcePolicy.FANO_DIRECT, 50, 999))
        assertEquals(100L, reopen.sourceBaseline(source))
        assertEquals(1000L, reopen.enabledAt(source))
    }

    @Test fun changingDestinationOrPolicyTurnsUserOutputOff() {
        val p = prefs()
        p.selectDestination(destination)
        p.selectSourcePolicy(source, RelaySourcePolicy.FANO_DIRECT, 1, 1000)
        assertTrue(p.changeEnabled(true))
        assertTrue(p.selectDestination(destination - 1))
        assertFalse(p.enabled)
        p.enabled = true
        assertTrue(p.enabled)
        p.selectSourcePolicy(source, RelaySourcePolicy.UNICOREY_POPULAR, 2, 1001)
        assertFalse(p.enabled)
        assertEquals(RelaySourcePolicy.UNICOREY_POPULAR, p.sourcePolicy(source))
        assertFalse(p.selectSource(source, PickWatchMode.DIRECT, 3, 1002))
    }

    @Test fun allFourUnicoFamiliesStayDistinctAndNoTitleIsAnIdentity() {
        val p = prefs()
        val families = listOf(RelaySourcePolicy.UNICOREY_MEX, RelaySourcePolicy.UNICOREY_FOREIGN,
            RelaySourcePolicy.UNICOREY_POPULAR, RelaySourcePolicy.UNICOREY_KINGS)
        families.forEachIndexed { index, policy -> assertTrue(p.selectSourcePolicy(source - index, policy, 1, 1000)) }
        assertEquals(4, p.sources().size)
        families.forEachIndexed { index, policy -> assertEquals(policy, prefs().sourcePolicy(source - index)) }
        assertNull(p.sourcePolicy(source - 99))
    }

    @Test fun invalidSelectionFailsClosed() {
        val p = prefs()
        assertFalse(p.selectDestination(0))
        assertFalse(p.selectSourcePolicy(0, RelaySourcePolicy.FANO_DIRECT, 1, 1000))
        assertFalse(p.selectSourcePolicy(source, RelaySourcePolicy.FANO_DIRECT, -1, 1000))
        assertFalse(p.selectSourcePolicy(source, RelaySourcePolicy.FANO_DIRECT, 1, 0))
        assertThrows(IllegalArgumentException::class.java) { RelayPreferences(context, 0) }
    }

    @Test fun failedPersistenceClosesEveryWrapperForTheAccount() {
        val isolated = account + 90
        val normal = RelayPreferences(context, isolated)
        assertTrue(normal.selectDestination(destination))
        assertTrue(normal.selectSourcePolicy(source, RelaySourcePolicy.FANO_DIRECT, 100, 1000))
        val failing = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val actual = super.getSharedPreferences(name, mode)
                return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
                    arrayOf(SharedPreferences::class.java)) { _, method, args ->
                    if (method.name != "edit") method.invoke(actual, *(args ?: emptyArray())) else {
                        val editor = actual.edit()
                        lateinit var proxy: SharedPreferences.Editor
                        proxy = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                            arrayOf(SharedPreferences.Editor::class.java)) { _, editMethod, editArgs ->
                            val result = editMethod.invoke(editor, *(editArgs ?: emptyArray()))
                            // Model a failed commit that already published changes in memory.
                            if (editMethod.name == "commit") false
                            else if (editMethod.returnType == SharedPreferences.Editor::class.java) proxy else result
                        } as SharedPreferences.Editor
                        proxy
                    }
                } as SharedPreferences
            }
        }
        val broken = RelayPreferences(failing, isolated)
        assertFalse(broken.changeEnabled(true))
        assertFalse(broken.enabled)
        assertFalse(normal.enabled)
        val recreated = RelayPreferences(context, isolated)
        assertFalse(recreated.enabled)
        assertEquals(0L, recreated.destinationChatId())
        assertTrue(recreated.sources().isEmpty())
        assertFalse(recreated.selectDestination(destination - 1))
    }
}
