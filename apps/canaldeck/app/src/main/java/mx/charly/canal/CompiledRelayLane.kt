package mx.charly.canal

/** Production metadata belongs behind the compiled fuse, including construction and cleanup.
 * Fake rehearsals use their own explicit stores and never enter this lane.
 */
internal class CompiledRelayLane<T : AutoCloseable>(private val factory: () -> T) : AutoCloseable {
    private var instance: T? = null
    private var retired = false

    fun with(operation: (T) -> Unit) {
        if (!BuildConfig.PICK_RELAY_REAL_ENABLED || retired) return
        try {
            val active = instance ?: factory().also { instance = it }
            operation(active)
        } catch (_: Exception) {
            close() // Never evaluate a failed or unused factory during cleanup.
        }
    }

    override fun close() {
        if (!BuildConfig.PICK_RELAY_REAL_ENABLED || retired) return
        retired = true
        val active = instance
        instance = null
        try { active?.close() } catch (_: Exception) { /* isolate this lane; preserve stores */ }
    }
}
