package mx.charly.canal

/** Apply read updates before an asynchronous chat refresh can finish. No content is retained. */
class LocalInboxReadFence {
    private val boundaries = mutableMapOf<Pair<Long, Long>, Long>()
    fun observe(accountId: Long, chatId: Long, lastReadId: Long) {
        if (accountId <= 0L || chatId == 0L || lastReadId < 0L) return
        val key = accountId to chatId
        boundaries[key] = maxOf(boundaries[key] ?: 0L, lastReadId)
    }
    fun boundary(accountId: Long, chatId: Long, cachedReadId: Long): Long =
        maxOf(boundaries[accountId to chatId] ?: 0L, cachedReadId)
}
