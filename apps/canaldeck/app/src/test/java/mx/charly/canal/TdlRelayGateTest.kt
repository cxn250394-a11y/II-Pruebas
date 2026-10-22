package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

class TdlRelayGateTest {
    @Test fun compiledFuseRejectsEvenExplicitUserIntent() {
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
        assertFalse(RelayRealGate.isOpen(true))
        assertFalse(RelayRealGate.isOpen(false))
        assertThrows(IllegalStateException::class.java) { RelayRealGate.requireOpen(true) }
    }
}
