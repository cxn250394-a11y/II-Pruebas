package com.charly.alertaslab

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.charly.alertcenter.AlertRules
import com.charly.alertcenter.LabAlert
import com.charly.alertcenter.LabEngine
import java.io.Closeable

/** Separate app ID + database. It cannot read or overwrite the v0.1 installation. */
class LabStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "alerts_lab.db", null, 1), Closeable {
    companion object {
        val writeLock = Any()
        const val MAX_ROWS = 2000
        const val KEEP_MS = 14L * 24 * 60 * 60 * 1000
    }
    // Explicit interface is required on older Android versions, not just the compile SDK.
    override fun close() { super.close() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE alerts (
            _id INTEGER PRIMARY KEY AUTOINCREMENT, event_key TEXT NOT NULL UNIQUE,
            source TEXT NOT NULL, profile INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL,
            posted INTEGER NOT NULL, received INTEGER NOT NULL, message_at INTEGER NOT NULL DEFAULT 0,
            is_read INTEGER NOT NULL DEFAULT 0, favorite INTEGER NOT NULL DEFAULT 0
        )""")
        db.execSQL("CREATE INDEX alerts_recent ON alerts(received DESC, _id DESC)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Explicit migration required: $oldVersion -> $newVersion")
    }
    fun insert(source: String, profile: Int, notificationKey: String, title: String, body: String,
               postedAt: Long, receivedAt: Long, messageAt: Long = 0, occurrence: Int = 0): Boolean = synchronized(writeLock) {
        if (title.isBlank() && body.isBlank()) return@synchronized false
        val secret = AlertRules.isLikelySensitive(title, body)
        val safeTitle = if (secret) "[Notificación sensible]" else AlertRules.clip(title, 300)
        val safeBody = if (secret) "[Contenido sensible oculto]" else AlertRules.clip(body, 1800)
        val event = LabEngine.eventKey(source, profile, notificationKey, safeTitle, safeBody,
            if (messageAt > 0) messageAt else postedAt, occurrence)
        val values = ContentValues().apply {
            put("event_key", event); put("source", source); put("profile", profile)
            put("title", safeTitle); put("body", safeBody); put("posted", postedAt)
            put("received", receivedAt); put("message_at", messageAt)
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insertWithOnConflict("alerts", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            prune(db, receivedAt)
            db.setTransactionSuccessful()
            id != -1L
        } finally { db.endTransaction() }
    }
    fun all(now: Long = System.currentTimeMillis()): List<LabAlert> {
        val result = mutableListOf<LabAlert>()
        readableDatabase.query("alerts", arrayOf("_id", "source", "profile", "title", "body", "posted", "received", "message_at", "is_read", "favorite"),
            "received>=?", arrayOf((now - KEEP_MS).toString()), null, null, "received DESC, _id DESC", MAX_ROWS.toString()).use { c ->
            while (c.moveToNext()) result += LabAlert(c.getLong(0), c.getString(1), c.getInt(2), c.getString(3), c.getString(4),
                c.getLong(5), c.getLong(6), c.getLong(7), c.getInt(8) == 1, c.getInt(9) == 1)
        }
        return result
    }
    fun markRead(id: Long, value: Boolean = true) = updateFlag(id, "is_read", value)
    fun favorite(id: Long, value: Boolean) = updateFlag(id, "favorite", value)
    private fun updateFlag(id: Long, column: String, value: Boolean) = synchronized(writeLock) {
        if (id > 0) writableDatabase.update("alerts", ContentValues().apply { put(column, if (value) 1 else 0) }, "_id=?", arrayOf(id.toString()))
        Unit
    }
    fun clear() = synchronized(writeLock) { writableDatabase.delete("alerts", null, null); Unit }
    fun purge(now: Long = System.currentTimeMillis()) = synchronized(writeLock) { prune(writableDatabase, now) }
    private fun prune(db: SQLiteDatabase, now: Long) {
        db.delete("alerts", "received<?", arrayOf((now - KEEP_MS).toString()))
        db.execSQL("DELETE FROM alerts WHERE _id NOT IN (SELECT _id FROM alerts ORDER BY received DESC, _id DESC LIMIT $MAX_ROWS)")
    }
}
