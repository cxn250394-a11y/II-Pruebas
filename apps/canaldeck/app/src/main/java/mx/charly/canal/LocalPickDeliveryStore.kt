package mx.charly.canal

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteOpenHelper

enum class LocalPickDeliveryState { PENDING, NOTIFIED, UNKNOWN }

/** Durable local-notification identities only. No text, captions, URLs, images or notification bodies. */
class LocalPickDeliveryStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext, FILE_NAME, null, 1,
    DatabaseErrorHandler { throw SQLiteDatabaseCorruptException("Preserve local pick checkpoints; delivery stopped") }
) {
    companion object { const val FILE_NAME = "canaldeck-local-pick-delivery.db" }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE units (account INTEGER NOT NULL, chat INTEGER NOT NULL, kind TEXT NOT NULL, unit INTEGER NOT NULL, state TEXT NOT NULL CHECK(state IN ('PENDING','NOTIFIED','UNKNOWN')), PRIMARY KEY(account,chat,kind,unit))")
        db.execSQL("CREATE TABLE messages (account INTEGER NOT NULL, chat INTEGER NOT NULL, message INTEGER NOT NULL, kind TEXT NOT NULL, unit INTEGER NOT NULL, PRIMARY KEY(account,chat,message))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("No destructive local-delivery migration approved")
    }
    private fun kind(albumId: Long) = if (albumId == 0L) "message" else "album"
    private fun unit(messageId: Long, albumId: Long) = if (albumId == 0L) messageId else albumId
    private fun identity(account: Long, chat: Long, messageId: Long, albumId: Long) {
        require(account > 0L && chat != 0L && messageId > 0L && albumId >= 0L)
    }
    private fun args(account: Long, chat: Long, messageId: Long, albumId: Long) =
        arrayOf(account.toString(), chat.toString(), kind(albumId), unit(messageId, albumId).toString())

    fun known(account: Long, chat: Long, messageId: Long, albumId: Long): Boolean {
        identity(account, chat, messageId, albumId)
        val db = readableDatabase
        val message = db.rawQuery("SELECT 1 FROM messages WHERE account=? AND chat=? AND message=?",
            arrayOf(account.toString(), chat.toString(), messageId.toString())).use { it.moveToFirst() }
        return message || state(account, chat, messageId, albumId) != null
    }

    /** Unique account/chat/message and typed album identity; callback order never becomes a watermark. */
    fun claim(account: Long, chat: Long, messageId: Long, albumId: Long): Boolean {
        identity(account, chat, messageId, albumId)
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val oldMessage = db.rawQuery("SELECT 1 FROM messages WHERE account=? AND chat=? AND message=?",
                arrayOf(account.toString(), chat.toString(), messageId.toString())).use { it.moveToFirst() }
            if (oldMessage) {
                db.setTransactionSuccessful()
                false
            } else {
                val values = ContentValues().apply {
                    put("account", account); put("chat", chat); put("kind", kind(albumId)); put("unit", unit(messageId, albumId))
                    put("state", LocalPickDeliveryState.PENDING.name)
                }
                val inserted = db.insertWithOnConflict("units", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
                // Preserve every observed fragment alias of an already announced album as well.
                values.remove("state"); values.put("message", messageId)
                check(db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L)
                db.setTransactionSuccessful()
                inserted
            }
        } finally { db.endTransaction() }
    }

    fun state(account: Long, chat: Long, messageId: Long, albumId: Long): LocalPickDeliveryState? {
        identity(account, chat, messageId, albumId)
        return readableDatabase.rawQuery("SELECT state FROM units WHERE account=? AND chat=? AND kind=? AND unit=?",
            args(account, chat, messageId, albumId)).use {
            if (it.moveToFirst()) LocalPickDeliveryState.valueOf(it.getString(0)) else null
        }
    }

    fun finish(account: Long, chat: Long, messageId: Long, albumId: Long, state: LocalPickDeliveryState) {
        identity(account, chat, messageId, albumId)
        require(state != LocalPickDeliveryState.PENDING)
        writableDatabase.update("units", ContentValues().apply { put("state", state.name) },
            "account=? AND chat=? AND kind=? AND unit=? AND state='PENDING'", args(account, chat, messageId, albumId))
    }

    /** Android's notify operation is not transactional with this store: uncertain attempts never retry. */
    fun recoverPending(account: Long) {
        require(account > 0L)
        writableDatabase.update("units", ContentValues().apply { put("state", LocalPickDeliveryState.UNKNOWN.name) },
            "account=? AND state='PENDING'", arrayOf(account.toString()))
    }
}
