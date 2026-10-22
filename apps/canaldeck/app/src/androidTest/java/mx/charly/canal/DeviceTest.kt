package mx.charly.canal

import android.test.InstrumentationTestCase
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Actual Android runtime and native TDLib, without account identity or a production login. */
@Suppress("DEPRECATION")
class DeviceTest : InstrumentationTestCase() {
    fun testVaultRoundTripAndDatabaseKey() {
        val v = Vault(instrumentation.targetContext)
        v.put("instrumentation-synthetic", "synthetic-not-a-secret")
        assertEquals("synthetic-not-a-secret", v.get("instrumentation-synthetic"))
        assertEquals(32, v.databaseKey().size)
        assertTrue(v.databaseKey().contentEquals(v.databaseKey()))
    }
    fun testNativeTdlibLoadsAndWaitsForOwnIdentity() = runBlocking {
        withTimeout(15_000) {
            val client = TdlClient.create()
            try {
                client.setLogVerbosityLevel(0)
                val result = client.getAuthorizationState()
                assertTrue("Native runtime must return a response", result is TdlResult.Success)
                assertTrue((result as TdlResult.Success).result is AuthorizationStateWaitTdlibParameters)
            } finally { client.close() }
        }
        Unit
    }
    fun testAccountScopedPreferencesOnDevice() {
        val a = Organization(instrumentation.targetContext, -101)
        val b = Organization(instrumentation.targetContext, -102)
        val id = a.createFolder("Device test ${System.nanoTime()}")!!
        a.assign(999, id)
        assertEquals(id, a.folder(999))
        assertEquals("", b.folder(999))
        assertTrue(a.renameFolder(id, "Renamed ${System.nanoTime()}"))
        assertEquals(id, a.folder(999))
        a.deleteFolder(id)
        assertEquals("", a.folder(999))
    }
}
