package mx.charly.canal

import java.net.URI
import java.util.Locale

/**
 * Conservative MESSAGE-link grammar, not proof of origin, access, account or message identity.
 * The caller must obtain the value from TDLib getMessageLink for the selected account/chat/post.
 * Never fabricate a URL from IDs or treat a syntactically valid string as TDLib attestation.
 * Supported subset of https://core.telegram.org/api/links#message-links: optional topic path
 * and the exact ?single flag. Other query variants deliberately display No disponible.
 */
object TelegramMessageLinks {
    private val username = Regex("[A-Za-z][A-Za-z0-9_]{4,31}")
    private val positive = Regex("[1-9][0-9]*")
    private val reserved = setOf(
        "addemoji", "addlist", "addstickers", "addstyle", "addtheme", "auction", "auth",
        "boost", "call", "confirmphone", "contact", "giftcode", "invoice", "joinchat", "login",
        "m", "nft", "proxy", "setlanguage", "share", "socks", "web", "a", "k", "z",
        "newbot", "oauth", "bg", "msg", "iv", "s", "c", "addgifts", "telegrampassport"
    )

    fun validated(link: String?): String? {
        if (link.isNullOrEmpty() || link.length > 1024 || link.any { it.code !in 33..126 } || '%' in link) return null
        return try {
            val uri = URI(link)
            if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port != -1 || uri.rawFragment != null ||
                uri.host?.lowercase(Locale.ROOT) !in setOf("t.me", "telegram.me") ||
                uri.rawAuthority?.lowercase(Locale.ROOT) !in setOf("t.me", "telegram.me") ||
                (uri.rawQuery != null && uri.rawQuery != "single")) return null
            val path = uri.rawPath.orEmpty()
            if (!path.startsWith('/')) return null
            val parts = path.substring(1).split('/')
            val ids = if (parts.firstOrNull() == "c") {
                if (parts.size !in 3..4) return null
                parts.drop(1)
            } else {
                if (parts.size !in 2..3 || !username.matches(parts.first()) ||
                    parts.first().lowercase(Locale.ROOT) in reserved) return null
                parts.drop(1)
            }
            if (ids.any { !positive.matches(it) || it.toLongOrNull() == null }) null else link
        } catch (_: Exception) { null }
    }
}
