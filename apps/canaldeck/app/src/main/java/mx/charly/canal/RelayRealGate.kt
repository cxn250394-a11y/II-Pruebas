package mx.charly.canal

/** This release has a literal compiled fuse. User intent cannot substitute for it. */
object RelayRealGate {
    fun isOpen(userEnabled: Boolean): Boolean = BuildConfig.PICK_RELAY_REAL_ENABLED && userEnabled

    fun requireOpen(userEnabled: Boolean) {
        check(isOpen(userEnabled)) { "REAL_RELAY_COMPILED_OFF" }
    }
}
