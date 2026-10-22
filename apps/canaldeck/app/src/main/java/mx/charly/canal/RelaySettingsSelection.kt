package mx.charly.canal

/** A transient row from the existing account's TDLib chat list, never a guessed identity. */
data class RelayLoadedChat(val chatId: Long, val title: String, val latestMessageId: Long)

/** Shared by the private settings screen and its Android tests. Titles never authorize a route. */
class RelaySettingsSelection(private val preferences: RelayPreferences) {
    fun selectDestination(loadedAccountId: Long, loaded: List<RelayLoadedChat>, chatId: Long): Boolean {
        if (loadedAccountId != preferences.accountId || loaded.none { it.chatId == chatId && chatId != 0L }) return false
        return preferences.selectDestination(chatId)
    }

    fun selectSource(
        loadedAccountId: Long,
        loaded: List<RelayLoadedChat>,
        chatId: Long,
        policy: RelaySourcePolicy,
        nowUnix: Long
    ): Boolean {
        if (loadedAccountId != preferences.accountId || policy !in allowedPolicies) return false
        val source = loaded.singleOrNull { it.chatId == chatId && chatId != 0L } ?: return false
        return preferences.selectSourcePolicy(chatId, policy, source.latestMessageId, nowUnix)
    }

    companion object {
        val allowedPolicies = listOf(
            RelaySourcePolicy.FANO_DIRECT,
            RelaySourcePolicy.UNICOREY_MEX,
            RelaySourcePolicy.UNICOREY_FOREIGN,
            RelaySourcePolicy.UNICOREY_POPULAR,
            RelaySourcePolicy.UNICOREY_KINGS
        )
        fun label(policy: RelaySourcePolicy): String = when (policy) {
            RelaySourcePolicy.FANO_DIRECT -> "Fano Futbol · canal directo"
            RelaySourcePolicy.UNICOREY_MEX -> "UnicoRey · Mexicanos"
            RelaySourcePolicy.UNICOREY_FOREIGN -> "UnicoRey · Extranjeros"
            RelaySourcePolicy.UNICOREY_POPULAR -> "UnicoRey · Populares"
            RelaySourcePolicy.UNICOREY_KINGS -> "UnicoRey · Los Reyes"
            else -> "Fuente no autorizada para este puente"
        }
    }
}
