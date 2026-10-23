package mx.charly.canal

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Separate offline-candidate database. Persists only IDs, immutable intents, stages and
 * confirmation metadata; never a header, caption, body, link, credential or media bytes.
 * No clear/reset path: a crash or an ambiguous identity closes unresolved work permanently.
 */
class AsyncRelayDatabase(context: Context, name: String = "canaldeck-async-relay.db") : SQLiteOpenHelper(
    context.applicationContext, name, null, 1,
    DatabaseErrorHandler { throw SQLiteDatabaseCorruptException("Async relay store unavailable; preserve evidence and stop") }
), AsyncRelayLedger, java.io.Closeable {
    init { setWriteAheadLoggingEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE async_deliveries (account INTEGER NOT NULL, source INTEGER NOT NULL, unit_key TEXT NOT NULL, session_id TEXT NOT NULL, destination INTEGER NOT NULL, phase TEXT NOT NULL, version INTEGER NOT NULL CHECK(version>=0), metadata TEXT NOT NULL, PRIMARY KEY(account,source,unit_key))")
        db.execSQL("CREATE TABLE async_members (account INTEGER NOT NULL, source INTEGER NOT NULL, message_id INTEGER NOT NULL CHECK(message_id>0), owner_unit TEXT NOT NULL, PRIMARY KEY(account,source,message_id))")
        db.execSQL("CREATE TABLE async_ids (account INTEGER NOT NULL, session_id TEXT NOT NULL, destination INTEGER NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('REQUEST','TEMP','FINAL')), id_value TEXT NOT NULL, owner_source INTEGER NOT NULL, owner_unit TEXT NOT NULL, request_id TEXT NOT NULL, stage TEXT NOT NULL, PRIMARY KEY(account,session_id,destination,kind,id_value))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Async relay migration $oldVersion->$newVersion requires review; do not erase checkpoints")
    }

    private fun keyArgs(key: RelayKey) = arrayOf(key.accountId.toString(), key.sourceChatId.toString(), key.groupId)
    private fun validKey(key: RelayKey) {
        require(key.accountId > 0 && key.sourceChatId != 0L)
        require(key.groupId.matches(Regex("[ma]:[1-9][0-9]{0,18}")) && key.groupId.substring(2).toLongOrNull() != null)
    }
    private fun safe(snapshot: AsyncRelaySnapshot): Boolean {
        val id = Regex("[A-Za-z0-9_.:-]{1,128}")
        return AsyncRelayValidation.valid(snapshot) && id.matches(snapshot.intent.scope.sessionId) &&
            snapshot.requests.all { id.matches(it.requestId) } && snapshot.reason.matches(Regex("[A-Z0-9_]{0,96}"))
    }

    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        return try { block(db).also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }

    private data class Claim(val kind: String, val value: String, val request: AsyncRelayRequest)
    private data class Owner(val key: RelayKey, val requestId: String, val stage: AsyncRelayStage)
    private fun claims(snapshot: AsyncRelaySnapshot): List<Claim> = snapshot.requests.flatMap { request ->
        listOf(Claim("REQUEST", request.requestId, request)) +
            request.temporaryMessageIds.map { Claim("TEMP", it.toString(), request) } +
            request.finalMessageIds.values.map { Claim("FINAL", it.toString(), request) }
    }

    private fun claimArgs(snapshot: AsyncRelaySnapshot, claim: Claim) = arrayOf(
        snapshot.intent.scope.accountId.toString(), snapshot.intent.scope.sessionId,
        snapshot.intent.scope.destinationChatId.toString(), claim.kind, claim.value
    )

    private fun owner(db: SQLiteDatabase, snapshot: AsyncRelaySnapshot, claim: Claim): Owner? =
        db.query("async_ids", arrayOf("owner_source", "owner_unit", "request_id", "stage"),
            "account=? AND session_id=? AND destination=? AND kind=? AND id_value=?", claimArgs(snapshot, claim),
            null, null, null).use { c ->
            if (c.moveToFirst()) Owner(RelayKey(snapshot.intent.scope.accountId, c.getLong(0), c.getString(1)),
                c.getString(2), AsyncRelayStage.valueOf(c.getString(3))) else null
        }

    private fun collisions(db: SQLiteDatabase, snapshot: AsyncRelaySnapshot): Set<RelayKey> = claims(snapshot).mapNotNull { claim ->
        owner(db, snapshot, claim)?.takeUnless {
            it.key == snapshot.key && it.requestId == claim.request.requestId && it.stage == claim.request.stage
        }?.key
    }.toSet()

    private fun reserveClaims(db: SQLiteDatabase, snapshot: AsyncRelaySnapshot) {
        claims(snapshot).forEach { claim ->
            val existing = owner(db, snapshot, claim)
            if (existing != null) {
                check(existing.key == snapshot.key && existing.requestId == claim.request.requestId && existing.stage == claim.request.stage)
            } else {
                val scope = snapshot.intent.scope
                val v = ContentValues().apply {
                    put("account", scope.accountId); put("session_id", scope.sessionId); put("destination", scope.destinationChatId)
                    put("kind", claim.kind); put("id_value", claim.value); put("owner_source", snapshot.key.sourceChatId)
                    put("owner_unit", snapshot.key.groupId); put("request_id", claim.request.requestId); put("stage", claim.request.stage.name)
                }
                check(db.insertOrThrow("async_ids", null, v) != -1L)
            }
        }
    }

    private fun record(snapshot: AsyncRelaySnapshot) = ContentValues().apply {
        put("account", snapshot.key.accountId); put("source", snapshot.key.sourceChatId); put("unit_key", snapshot.key.groupId)
        put("session_id", snapshot.intent.scope.sessionId); put("destination", snapshot.intent.scope.destinationChatId)
        put("phase", snapshot.phase.name); put("version", snapshot.version); put("metadata", encode(snapshot))
    }

    override fun create(snapshot: AsyncRelaySnapshot): Boolean {
        val fresh = snapshot.detached()
        if (!safe(fresh) || fresh.version != 0L || fresh.phase != AsyncRelayPhase.HEADER_WAITING ||
            fresh.requests.size != 1 || fresh.requests.single().responseBound || fresh.requests.single().earlyUpdates.isNotEmpty()) return false
        return transaction { db ->
            if (read(db, fresh.key) != null) return@transaction false
            val alreadyObserved = fresh.intent.sourceMessageIds.any { message ->
                db.query("async_members", arrayOf("owner_unit"), "account=? AND source=? AND message_id=?",
                    arrayOf(fresh.key.accountId.toString(), fresh.key.sourceChatId.toString(), message.toString()), null, null, null).use { it.moveToFirst() }
            }
            if (alreadyObserved) return@transaction false
            check(db.insertOrThrow("async_deliveries", null, record(fresh)) != -1L)
            fresh.intent.sourceMessageIds.forEach { message ->
                val v = ContentValues().apply {
                    put("account", fresh.key.accountId); put("source", fresh.key.sourceChatId)
                    put("message_id", message); put("owner_unit", fresh.key.groupId)
                }
                check(db.insertOrThrow("async_members", null, v) != -1L)
            }
            val conflict = collisions(db, fresh)
            if (conflict.isNotEmpty()) {
                (conflict + fresh.key).forEach { makeUnknown(db, it, "SCOPED_ID_COLLISION") }
                false
            } else { reserveClaims(db, fresh); true }
        }
    }

    private fun read(db: SQLiteDatabase, key: RelayKey): AsyncRelaySnapshot? {
        validKey(key)
        return db.query("async_deliveries", arrayOf("session_id", "destination", "phase", "version", "metadata"),
            "account=? AND source=? AND unit_key=?", keyArgs(key), null, null, null).use { c ->
            if (!c.moveToFirst()) null else {
                val snapshot = decode(c.getString(4))
                check(safe(snapshot) && snapshot.key == key && snapshot.intent.scope.sessionId == c.getString(0) &&
                    snapshot.intent.scope.destinationChatId == c.getLong(1) && snapshot.phase.name == c.getString(2) &&
                    snapshot.version == c.getLong(3)) { "Async metadata integrity failed; preserve evidence and stop" }
                snapshot.detached()
            }
        }
    }

    override fun read(key: RelayKey): AsyncRelaySnapshot? = read(readableDatabase, key)

    private fun makeUnknown(db: SQLiteDatabase, key: RelayKey, reason: String): Boolean {
        val old = read(db, key) ?: return false
        if (old.terminal) return false
        val next = old.copy(phase = AsyncRelayPhase.UNKNOWN, version = old.version + 1L, reason = reason)
        check(safe(next) && AsyncRelayValidation.validTransition(old, next))
        check(db.update("async_deliveries", record(next), "account=? AND source=? AND unit_key=? AND version=?",
            keyArgs(key) + old.version.toString()) == 1)
        return true
    }

    override fun compareAndSet(expectedVersion: Long, next: AsyncRelaySnapshot): Boolean {
        val detached = next.detached()
        if (!safe(detached)) return false
        return transaction { db ->
            val old = read(db, detached.key) ?: return@transaction false
            if (old.version != expectedVersion || !AsyncRelayValidation.validTransition(old, detached)) return@transaction false
            val conflict = collisions(db, detached)
            if (conflict.isNotEmpty()) {
                // Do not leave a prior unresolved owner capable of accepting an ambiguous late ACK.
                (conflict + detached.key).forEach { makeUnknown(db, it, "SCOPED_ID_COLLISION") }
                false
            } else {
                reserveClaims(db, detached)
                check(db.update("async_deliveries", record(detached), "account=? AND source=? AND unit_key=? AND version=?",
                    keyArgs(detached.key) + expectedVersion.toString()) == 1)
                true
            }
        }
    }

    override fun recoverPending(accountId: Long, sessionId: String?): Int {
        require(accountId > 0L)
        require(sessionId == null || sessionId.matches(Regex("[A-Za-z0-9_.:-]{1,128}")))
        return transaction { db ->
            val where = "account=? AND phase NOT IN ('COMPLETE','UNKNOWN')" + if (sessionId == null) "" else " AND session_id=?"
            val args = if (sessionId == null) arrayOf(accountId.toString()) else arrayOf(accountId.toString(), sessionId)
            val keys = db.query("async_deliveries", arrayOf("source", "unit_key"), where, args, null, null, null).use { c ->
                buildList { while (c.moveToNext()) add(RelayKey(accountId, c.getLong(0), c.getString(1))) }
            }
            keys.count { makeUnknown(db, it, "PROCESS_RECOVERY_UNKNOWN") }
        }
    }

    /** Inspect before recovery mutates rows, so a crash cannot erase the need for a fence. */
    internal fun requiresProcessRecoveryFence(accountId: Long): Boolean = processRecoveryScope(accountId) != null

    internal fun processRecoveryScope(accountId: Long): AsyncRelayScope? {
        require(accountId > 0L)
        return readableDatabase.query("async_deliveries", arrayOf("phase", "metadata"),
            "account=? AND phase!='COMPLETE'", arrayOf(accountId.toString()), null, null, null).use { c ->
            var scope: AsyncRelayScope? = null
            while (c.moveToNext()) {
                val snapshot = decode(c.getString(1))
                check(safe(snapshot) && snapshot.intent.scope.accountId == accountId)
                // Even terminal UNKNOWN can hide an SDK request whose queued callback never
                // returned. A new process has lost the router's lifetime identity tombstones.
                if (snapshot.phase != AsyncRelayPhase.COMPLETE) scope = snapshot.intent.scope
            }
            scope
        }
    }

    private fun encode(snapshot: AsyncRelaySnapshot): String {
        val intent = snapshot.intent
        val scope = intent.scope
        val requests = JSONArray()
        snapshot.requests.forEach { r ->
            val finals = JSONArray()
            r.finalMessageIds.toSortedMap().forEach { (temporary, final) -> finals.put(JSONObject().put("temporary", temporary).put("final", final)) }
            val early = JSONArray()
            r.earlyUpdates.forEach { update -> early.put(JSONObject().put("kind", update.kind.name)
                .put("account", update.scope.accountId).put("session", update.scope.sessionId).put("destination", update.scope.destinationChatId)
                .put("old", update.oldMessageId).put("final", update.finalMessageId).put("outgoing", update.finalIsOutgoing)
                .put("sending", update.finalHasSendingState)) }
            requests.put(JSONObject().put("id", r.requestId).put("stage", r.stage.name)
                .put("sources", JSONArray(r.expectedSourceMessageIds)).put("deadline", r.deadlineMillis).put("bound", r.responseBound)
                .put("temporary", JSONArray(r.temporaryMessageIds)).put("finals", finals).put("early", early))
        }
        return JSONObject().put("schema", 1).put("account", intent.key.accountId).put("source", intent.key.sourceChatId)
            .put("unit", intent.key.groupId).put("session", scope.sessionId).put("destination", scope.destinationChatId)
            .put("sources", JSONArray(intent.sourceMessageIds)).put("media", intent.mediaRequired).put("created", intent.createdMillis)
            .put("timeout", intent.timeoutMillis).put("revision", intent.configurationRevision)
            .put("phase", snapshot.phase.name).put("version", snapshot.version).put("reason", snapshot.reason).put("requests", requests).toString()
    }

    private fun JSONArray.longs(): List<Long> = List(length()) { getLong(it) }
    private fun decode(text: String): AsyncRelaySnapshot {
        val root = JSONObject(text)
        check(root.getInt("schema") == 1)
        val key = RelayKey(root.getLong("account"), root.getLong("source"), root.getString("unit"))
        val scope = AsyncRelayScope(key.accountId, root.getString("session"), root.getLong("destination"))
        val intent = AsyncRelayIntent(key, scope, root.getJSONArray("sources").longs(), root.getBoolean("media"),
            root.getLong("created"), root.getLong("timeout"), root.getLong("revision"))
        val array = root.getJSONArray("requests")
        val requests = List(array.length()) { index ->
            val r = array.getJSONObject(index)
            val pairs = r.getJSONArray("finals")
            val finals = buildMap<Long, Long> {
                repeat(pairs.length()) { p ->
                    val pair = pairs.getJSONObject(p)
                    val old = pair.getLong("temporary")
                    check(!containsKey(old))
                    put(old, pair.getLong("final"))
                }
            }
            val e = r.getJSONArray("early")
            val early = List(e.length()) { p ->
                val update = e.getJSONObject(p)
                AsyncRelayUpdate(AsyncRelayScope(update.getLong("account"), update.getString("session"), update.getLong("destination")),
                    AsyncRelayUpdateKind.valueOf(update.getString("kind")), update.getLong("old"), update.getLong("final"),
                    update.getBoolean("outgoing"), update.getBoolean("sending"))
            }
            AsyncRelayRequest(r.getString("id"), AsyncRelayStage.valueOf(r.getString("stage")), r.getJSONArray("sources").longs(),
                r.getLong("deadline"), r.getBoolean("bound"), r.getJSONArray("temporary").longs(), finals, early)
        }
        return AsyncRelaySnapshot(intent, AsyncRelayPhase.valueOf(root.getString("phase")), requests, root.getLong("version"), root.getString("reason"))
    }
}
