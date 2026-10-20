package com.charly.alertcenter

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** All data stays in private app storage; never written to external storage. */
data class AlertEntry(
    val id: Long = 0,
    val packageId: String,
    val androidUserId: Int,
    val title: String,
    val body: String,
    val postedAt: Long,
    val receivedAt: Long,
    val priority: Boolean = false
)

class AlertDatabase(context: Context) : SQLiteOpenHelper(context.applicationContext, "alerts_v1.db", null, 1) {
    companion object {
        private const val MAX_ROWS = 2000
        private const val KEEP_MS = 14L * 24L * 60L * 60L * 1000L
        private const val DEDUP_MS = 120000L
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE alerts (
              _id INTEGER PRIMARY KEY AUTOINCREMENT,
              package_id TEXT NOT NULL,
              android_user_id INTEGER NOT NULL,
              notif_key TEXT NOT NULL,
              title TEXT NOT NULL,
              body TEXT NOT NULL,
              posted_at INTEGER NOT NULL,
              received_at INTEGER NOT NULL,
              priority INTEGER NOT NULL DEFAULT 0,
              fingerprint TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_recent ON alerts(received_at DESC, _id DESC)")
        db.execSQL("CREATE INDEX idx_duplicate ON alerts(fingerprint, received_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // New versions must use migrations, never silently drop private user history.
        if (oldVersion != newVersion) error("Missing migration from $oldVersion to $newVersion")
    }

    /** Returns false if a near-identical update was already captured. */
    @Synchronized fun insert(
        packageId: String,
        userId: Int,
        notificationKey: String,
        title: String,
        body: String,
        postedAt: Long,
        priority: Boolean
    ): Boolean {
        val sensitive = AlertRules.isLikelySensitive(title, body)
        val safeTitle = if (sensitive) "[Notificación sensible]" else AlertRules.clip(title, 300)
        val safeBody = if (sensitive) "[Contenido sensible oculto]" else AlertRules.clip(body, 1800)
        if (safeTitle.isBlank() && safeBody.isBlank()) return false
        val now = System.currentTimeMillis()
        val fingerprint = AlertRules.fingerprint(packageId, userId, safeTitle, safeBody, notificationKey)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery(
                "SELECT 1 FROM alerts WHERE fingerprint=? AND received_at>=? LIMIT 1",
                arrayOf(fingerprint, (now - DEDUP_MS).toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    db.setTransactionSuccessful()
                    return false
                }
            }
            val values = ContentValues().apply {
                put("package_id", packageId)
                put("android_user_id", userId)
                put("notif_key", AlertRules.clip(notificationKey, 500))
                put("title", safeTitle)
                put("body", safeBody)
                put("posted_at", postedAt)
                put("received_at", now)
                put("priority", if (priority) 1 else 0)
                put("fingerprint", fingerprint)
            }
            db.insertOrThrow("alerts", null, values)
            prune(db, now)
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized fun recent(limit: Int = 200): List<AlertEntry> {
        val items = mutableListOf<AlertEntry>()
        readableDatabase.query(
            "alerts",
            arrayOf("_id", "package_id", "android_user_id", "title", "body", "posted_at", "received_at", "priority"),
            null, null, null, null,
            "received_at DESC, _id DESC", limit.coerceIn(1, 500).toString()
        ).use { c ->
            while (c.moveToNext()) {
                items.add(AlertEntry(
                    id = c.getLong(0),
                    packageId = c.getString(1),
                    androidUserId = c.getInt(2),
                    title = c.getString(3),
                    body = c.getString(4),
                    postedAt = c.getLong(5),
                    receivedAt = c.getLong(6),
                    priority = c.getInt(7) == 1
                ))
            }
        }
        return items
    }

    @Synchronized fun deleteAll() {
        writableDatabase.delete("alerts", null, null)
    }

    @Synchronized fun purgeExpired() {
        writableDatabase.delete("alerts", "received_at<?", arrayOf((System.currentTimeMillis() - KEEP_MS).toString()))
    }

    private fun prune(db: SQLiteDatabase, now: Long) {
        db.delete("alerts", "received_at<?", arrayOf((now - KEEP_MS).toString()))
        db.execSQL("DELETE FROM alerts WHERE _id NOT IN (SELECT _id FROM alerts ORDER BY received_at DESC, _id DESC LIMIT $MAX_ROWS)")
    }
}
