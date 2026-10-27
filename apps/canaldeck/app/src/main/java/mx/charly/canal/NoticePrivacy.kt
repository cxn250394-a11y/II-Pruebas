package mx.charly.canal

/** The absence of protection metadata is not permission to show message text. */
object NoticePrivacy {
    const val REDACTED = "Mensaje nuevo · abrir CanalDeck"
    fun allowText(enabled: Boolean, chatKnown: Boolean, protectionPending: Boolean,
                  chatProtected: Boolean, secretChat: Boolean,
                  messageSavable: Boolean, selfDestruct: Boolean): Boolean =
        enabled && chatKnown && !protectionPending && !chatProtected && !secretChat &&
            messageSavable && !selfDestruct

    fun appendRecent(previous: List<String>, raw: String, allowText: Boolean): List<String> {
        val retained = if (allowText) previous else emptyList()
        return (retained + if (allowText) raw.take(220) else REDACTED).takeLast(5)
    }
}

/** A stale GetChat reply must not release a newer protection change. Main-thread confined. */
class PrivacyFence {
    private val versions = mutableMapOf<Long, Long>()
    private val expected = mutableMapOf<Long, Boolean>()
    private val pending = mutableSetOf<Long>()
    private var revision = 0L
    fun changed(chat: Long, protectedValue: Boolean) {
        revision++
        versions[chat] = revision
        expected[chat] = protectedValue
        pending.add(chat)
    }
    /** A positive or contradictory observation is restrictive until independently refreshed. */
    fun invalidate(chat: Long) {
        revision++
        versions[chat] = revision
        // Once protection has been observed, a conflicting false response cannot
        // reopen text without a later explicit TDLib protection update.
        expected[chat] = true
        pending.add(chat)
    }
    fun version(chat: Long): Long = versions[chat] ?: 0L
    fun snapshot(): Long = revision
    fun accepts(chat: Long, snapshot: Long, protectedValue: Boolean): Boolean =
        version(chat) <= snapshot && (!expected.containsKey(chat) || expected[chat] == protectedValue)
    fun confirmed(chat: Long, snapshot: Long, protectedValue: Boolean) {
        if (accepts(chat, snapshot, protectedValue)) {
            expected[chat] = protectedValue
            pending.remove(chat)
        }
    }
    fun isPending(chat: Long): Boolean = chat in pending
    fun observeProtected(chat: Long, snapshot: Long, previouslyProtected: Boolean): Boolean {
        if (!accepts(chat, snapshot, true) || (!previouslyProtected && !isPending(chat))) {
            invalidate(chat)
            return true
        }
        return false
    }
    fun <T> store(chat: Long, snapshot: Long, protectedValue: Boolean, value: T,
                  target: MutableMap<Long, T>, confirmsProtection: Boolean = false): Boolean {
        if (!accepts(chat, snapshot, protectedValue)) return false
        target[chat] = value
        if (confirmsProtection) confirmed(chat, snapshot, protectedValue)
        return true
    }
}
