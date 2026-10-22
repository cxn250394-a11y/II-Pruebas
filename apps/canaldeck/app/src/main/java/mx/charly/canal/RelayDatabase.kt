package mx.charly.canal

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteOpenHelper

/**
 * Durable account/source IDs, state and aggregate counts only. A crash can lose an output,
 * but never clears a reservation to make it eligible for automatic retry.
 */
class RelayDatabase(context: Context, name: String = "canaldeck-relay.db") : SQLiteOpenHelper(
    context.applicationContext, name, null, 1,
    DatabaseErrorHandler { throw SQLiteDatabaseCorruptException("Relay store unavailable; preserve checkpoints and stop") }
), RelayLedger {
    init { setWriteAheadLoggingEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE baselines (account INTEGER NOT NULL, source INTEGER NOT NULL, latest INTEGER NOT NULL CHECK(latest>=0), not_before INTEGER NOT NULL CHECK(not_before>0), PRIMARY KEY(account,source))")
        db.execSQL("CREATE TABLE observed (account INTEGER NOT NULL, source INTEGER NOT NULL, unit_key TEXT NOT NULL, PRIMARY KEY(account,source,unit_key))")
        db.execSQL("CREATE TABLE deliveries (account INTEGER NOT NULL, source INTEGER NOT NULL, unit_key TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN ('PENDING','CONFIRMED','UNKNOWN')), PRIMARY KEY(account,source,unit_key))")
        db.execSQL("CREATE TABLE coverage (account INTEGER NOT NULL, source INTEGER NOT NULL, window_start INTEGER NOT NULL, metric TEXT NOT NULL, value INTEGER NOT NULL CHECK(value>=0), PRIMARY KEY(account,source,window_start,metric))")
    }

    /** No destructive migration: a future schema change requires a reviewed migration. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Relay migration $oldVersion->$newVersion is not approved; preserve checkpoints")
    }

    private fun identity(account: Long, source: Long) { require(account > 0 && source != 0L) }
    private fun valid(key: RelayKey) {
        identity(key.accountId, key.sourceChatId)
        require(Regex("[ma]:[1-9][0-9]{0,18}").matches(key.groupId))
        require(key.groupId.substring(2).toLongOrNull()?.let { it > 0L } == true)
    }
    private fun values(key: RelayKey) = ContentValues().apply {
        put("account", key.accountId); put("source", key.sourceChatId); put("unit_key", key.groupId)
    }
    private fun args(key: RelayKey) = arrayOf(key.accountId.toString(), key.sourceChatId.toString(), key.groupId)
    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        return try { block(db).also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }

    override fun checkpoint(accountId: Long, sourceChatId: Long, latestMessageId: Long, notBeforeUnix: Long) {
        identity(accountId, sourceChatId); require(latestMessageId >= 0L && notBeforeUnix > 0L)
        transaction { db ->
            val prior = baseline(db, accountId, sourceChatId)
            val values = ContentValues().apply {
                put("account", accountId); put("source", sourceChatId)
                put("latest", maxOf(prior?.latestMessageId ?: 0L, latestMessageId))
                put("not_before", maxOf(prior?.notBeforeUnix ?: 0L, notBeforeUnix))
            }
            check(db.insertWithOnConflict("baselines", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
        }
    }

    private fun baseline(db: SQLiteDatabase, accountId: Long, sourceChatId: Long): RelayBaseline? =
        db.query("baselines", arrayOf("latest", "not_before"), "account=? AND source=?",
            arrayOf(accountId.toString(), sourceChatId.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) RelayBaseline(c.getLong(0), c.getLong(1)) else null
        }

    override fun baseline(accountId: Long, sourceChatId: Long): RelayBaseline? {
        identity(accountId, sourceChatId)
        return baseline(readableDatabase, accountId, sourceChatId)
    }

    override fun acceptObserved(key: RelayKey, messageIds: List<Long>, originalUnix: Long): Boolean {
        valid(key)
        if (messageIds.isEmpty() || messageIds.any { it <= 0L } || messageIds.distinct().size != messageIds.size ||
            originalUnix <= 0L || (key.groupId.startsWith("m:") && messageIds != listOf(key.groupId.substring(2).toLong()))) return false
        return transaction { db ->
            val armed = baseline(db, key.accountId, key.sourceChatId)
            // Epoch seconds are coarse: hold the entire activation second rather than
            // replay a pre-activation post whose ID was absent from the cached chat row.
            if (armed == null || originalUnix <= armed.notBeforeUnix || messageIds.any { it <= armed.latestMessageId }) false
            else {
                // Reserve the whole album AND each member ID. Changing an album wrapper
                // can never make an already-observed message eligible for a second output.
                val aliases = (listOf(key) + messageIds.map { key.copy(groupId = "m:$it") }).distinct()
                val exists = aliases.any { alias ->
                    db.query("observed", arrayOf("unit_key"), "account=? AND source=? AND unit_key=?",
                        args(alias), null, null, null).use { it.moveToFirst() }
                }
                if (exists) false else {
                    aliases.forEach { alias -> check(db.insertOrThrow("observed", null, values(alias)) != -1L) }
                    true
                }
            }
        }
    }

    override fun claim(key: RelayKey): Boolean {
        valid(key)
        return transaction { db ->
            val v = values(key).apply { put("state", RelayDeliveryState.PENDING.name) }
            db.insertWithOnConflict("deliveries", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L
        }
    }

    override fun state(key: RelayKey): RelayDeliveryState? {
        valid(key)
        return readableDatabase.query("deliveries", arrayOf("state"), "account=? AND source=? AND unit_key=?",
            args(key), null, null, null).use { c -> if (c.moveToFirst()) RelayDeliveryState.valueOf(c.getString(0)) else null }
    }

    override fun finish(key: RelayKey, state: RelayDeliveryState) {
        valid(key); require(state != RelayDeliveryState.PENDING)
        transaction { db ->
            // UNKNOWN and CONFIRMED are terminal. Repeated finishes cannot resurrect a delivery.
            db.update("deliveries", ContentValues().apply { put("state", state.name) },
                "account=? AND source=? AND unit_key=? AND state='PENDING'", args(key))
        }
    }

    override fun recoverPending(accountId: Long): Int {
        require(accountId > 0L)
        return transaction { db -> db.update("deliveries", ContentValues().apply { put("state", "UNKNOWN") },
            "account=? AND state='PENDING'", arrayOf(accountId.toString())) }
    }

    private fun valid(key: RelayCounterKey) {
        identity(key.accountId, key.sourceChatId); require(key.windowStartUnix >= 0L)
    }

    override fun increment(key: RelayCounterKey, metric: RelayMetric, amount: Long) {
        valid(key); require(amount > 0L)
        require(metric != RelayMetric.REAL_CONFIRMED) { "v0.4 has no authorized real relay delivery" }
        transaction { db ->
            val v = ContentValues().apply {
                put("account", key.accountId); put("source", key.sourceChatId)
                put("window_start", key.windowStartUnix); put("metric", metric.name); put("value", 0L)
            }
            db.insertWithOnConflict("coverage", null, v, SQLiteDatabase.CONFLICT_IGNORE)
            db.execSQL("UPDATE coverage SET value=value+? WHERE account=? AND source=? AND window_start=? AND metric=? AND value<=?",
                arrayOf<Any>(amount, key.accountId, key.sourceChatId, key.windowStartUnix, metric.name, Long.MAX_VALUE - amount))
            db.rawQuery("SELECT changes()", null).use { c -> check(c.moveToFirst() && c.getInt(0) == 1) { "Coverage counter overflow" } }
        }
    }

    override fun counters(key: RelayCounterKey): Map<RelayMetric, Long> {
        valid(key)
        val result = RelayMetric.entries.associateWith { 0L }.toMutableMap()
        readableDatabase.query("coverage", arrayOf("metric", "value"), "account=? AND source=? AND window_start=?",
            arrayOf(key.accountId.toString(), key.sourceChatId.toString(), key.windowStartUnix.toString()), null, null, null).use { c ->
            while (c.moveToNext()) {
                val metric = RelayMetric.entries.firstOrNull { it.name == c.getString(0) }
                    ?: throw IllegalStateException("Unknown private coverage metric")
                result[metric] = c.getLong(1)
            }
        }
        return result
    }

    fun counterWindows(accountId: Long, sourceChatId: Long): List<Long> {
        identity(accountId, sourceChatId)
        return readableDatabase.query(true, "coverage", arrayOf("window_start"), "account=? AND source=?",
            arrayOf(accountId.toString(), sourceChatId.toString()), null, null, "window_start DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
    }
}
