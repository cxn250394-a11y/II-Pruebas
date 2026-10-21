package mx.charly.canal

import android.test.InstrumentationTestCase

/** Real Android runtime, no account credentials and no production Telegram login. */
@Suppress("DEPRECATION")
class DeviceTest : InstrumentationTestCase() {
    fun testVaultRoundTripAndDatabaseKey() {
        val v = Vault(instrumentation.targetContext)
        v.put("instrumentation-synthetic", "synthetic-not-a-secret")
        assertEquals("synthetic-not-a-secret", v.get("instrumentation-synthetic"))
        assertEquals(32, v.databaseKey().size)
        assertTrue(v.databaseKey().contentEquals(v.databaseKey()))
    }
}
