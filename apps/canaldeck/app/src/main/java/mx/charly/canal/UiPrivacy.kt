package mx.charly.canal

/** Conservative gate for screenshots and user-initiated local OCR. */
object UiPrivacy {
    fun safeChat(known: Boolean, pending: Boolean, chatProtected: Boolean, secret: Boolean): Boolean =
        known && !pending && !chatProtected && !secret
    fun safeMedia(known: Boolean, pending: Boolean, chatProtected: Boolean, secret: Boolean,
                  messageSavable: Boolean, selfDestruct: Boolean): Boolean =
        safeChat(known, pending, chatProtected, secret) && messageSavable && !selfDestruct
}

object BackupSessionGuard {
    fun matches(origin: Any?, originId: Long, current: Any?, currentId: Long, ready: Boolean): Boolean =
        origin != null && origin === current && originId != 0L && originId == currentId && ready
}
object AccountActionGuard {
    fun matches(origin: Any?, tracked: Any?, account: Long, current: Any?, currentId: Long, ready: Boolean): Boolean =
        origin != null && origin === tracked && BackupSessionGuard.matches(origin,account,current,currentId,ready)
}
