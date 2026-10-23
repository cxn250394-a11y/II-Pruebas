package mx.charly.canal

import java.net.URI

/** Only public username links. Invite, /c/, paid, protected and private links are never guessed. */
data class PublicTelegramLink(val username: String, val messageId: Long?)
object MessageLinks {
    fun parsePublic(link: String): PublicTelegramLink? {
        return try {
            if (link.length > 2048) return null
            val u = URI(link)
            if (u.scheme?.lowercase() != "https" || u.userInfo != null || u.port != -1) return null
            if (u.host?.lowercase() !in setOf("t.me", "www.t.me", "telegram.me", "www.telegram.me")) return null
            val parts = u.rawPath.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
            val path = if (parts.firstOrNull() == "s") parts.drop(1) else parts
            if (path.size !in 1..2) return null
            val name = path[0]
            if (!Regex("[A-Za-z][A-Za-z0-9_]{4,31}").matches(name)
                || name.lowercase() in setOf("share", "joinchat", "proxy", "addstickers", "c", "s", "premium")) return null
            val id = if (path.size == 2) {
                if (!Regex("[1-9][0-9]{0,17}").matches(path[1])) return null
                path[1].toLongOrNull() ?: return null
            } else null
            PublicTelegramLink(name, id)
        } catch (_: Exception) { null }
    }
}
