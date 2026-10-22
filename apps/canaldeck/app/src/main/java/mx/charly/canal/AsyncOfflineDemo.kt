package mx.charly.canal

import android.content.Context
import java.io.Closeable

/** A separate, explicit rehearsal. It never accepts a live account, client, media or chat. */
class AsyncOfflineDemo(context: Context) : Closeable {
    enum class Step { IDLE, PARTIAL_HOLD, HEADER_WAITING, QUICK_ACK_WAITING, MEDIA_WAITING, ONE_PHOTO_WAITING, COMPLETE, UNKNOWN }

    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var database = AsyncRelayDatabase(this.context, DATABASE_NAME)
    private var engine: AsyncRelayEngine? = null
    private var transport: FakeAsyncRelayTransport? = null
    private var fixture: Fixture? = null
    private var now = 0L
    var snapshot: AsyncRelaySnapshot? = null
        private set
    var step = Step.IDLE
        private set
    var reopened = false
        private set
    var newRequestsAfterReopen = 0
        private set
    val requestCount: Int get() = transport?.requests?.size ?: snapshot?.requests?.size ?: 0
    val confirmedPhotos: Int get() = snapshot?.requests?.lastOrNull { it.stage == AsyncRelayStage.MEDIA }?.finalMessageIds?.size ?: 0

    init { restore() }

    /** A new identity is committed first; prior COMPLETE/UNKNOWN rows are never reset. */
    fun newScenario(): Boolean {
        stopScenario()
        val sequence = synchronized(SEQUENCE_LOCK) {
            val old = preferences.getLong("sequence", 0)
            if (old !in 0..999_999_999L) return false
            val next = old + 1
            if (!preferences.edit().putLong("sequence", next).putLong("last-sequence", next).commit()) return false
            next
        }
        now = System.currentTimeMillis().coerceAtLeast(1_800_000_000_000L)
        val f = Fixture(sequence, now)
        val fake = FakeAsyncRelayTransport(f.fresh.copy(batch = f.fullBatch.copy(messages = f.fullBatch.messages.take(1))))
        val runner = AsyncRelayEngine(database, fake) { now }
        fixture = f
        transport = fake
        engine = runner
        snapshot = runner.start(f.intent, f.manifest)
        reopened = false
        newRequestsAfterReopen = 0
        step = Step.PARTIAL_HOLD
        return snapshot == null && fake.requests.isEmpty()
    }

    /** Each owner tap supplies one synthetic update; quick ACK never manufactures final success. */
    fun advance(): Boolean {
        val f = fixture ?: return false
        val runner = engine ?: return false
        val before = step
        when (step) {
            Step.PARTIAL_HOLD -> {
                transport?.freshState = f.fresh
                snapshot = runner.start(f.intent, f.manifest)
                if (snapshot?.phase != AsyncRelayPhase.HEADER_WAITING) { updateStep(); return false }
                step = Step.HEADER_WAITING
            }
            Step.HEADER_WAITING -> {
                val temporaryId = snapshot?.requests?.singleOrNull()?.temporaryMessageIds?.singleOrNull() ?: return false
                snapshot = runner.onUpdate(f.intent.key, AsyncRelayUpdate(f.scope, AsyncRelayUpdateKind.ACKNOWLEDGED, temporaryId))
                if (snapshot?.phase != AsyncRelayPhase.HEADER_WAITING || requestCount != 1) { updateStep(); return false }
                step = Step.QUICK_ACK_WAITING
            }
            Step.QUICK_ACK_WAITING -> {
                val temporaryId = snapshot?.requests?.singleOrNull()?.temporaryMessageIds?.singleOrNull() ?: return false
                snapshot = runner.onUpdate(f.intent.key, f.success(temporaryId, 1))
                if (snapshot?.phase != AsyncRelayPhase.MEDIA_WAITING) { updateStep(); return false }
                step = Step.MEDIA_WAITING
            }
            Step.MEDIA_WAITING, Step.ONE_PHOTO_WAITING -> {
                val request = snapshot?.requests?.lastOrNull() ?: return false
                val temporaryId = request.temporaryMessageIds.firstOrNull { it !in request.finalMessageIds } ?: return false
                snapshot = runner.onUpdate(f.intent.key, f.success(temporaryId, 2 + confirmedPhotos))
                step = when (snapshot?.phase) {
                    AsyncRelayPhase.COMPLETE -> Step.COMPLETE
                    AsyncRelayPhase.MEDIA_WAITING -> Step.ONE_PHOTO_WAITING
                    else -> { updateStep(); return false }
                }
            }
            else -> return false
        }
        return step != before
    }

    fun stopScenario() {
        val key = snapshot?.key ?: return
        snapshot = engine?.stop(key, "OFFLINE_DEMO_CUT") ?: database.read(key)
        updateStep()
    }

    /** Drops all transient context, reopens SQLite, and recovers pending rows without dispatch. */
    fun reopen() {
        database.close()
        database = AsyncRelayDatabase(context, DATABASE_NAME)
        engine = null
        transport = null
        fixture = null
        restore()
        reopened = true
        newRequestsAfterReopen = 0 // This path has no transport and makes no request.
    }

    private fun restore() {
        database.recoverPending(ACCOUNT_ID)
        val sequence = preferences.getLong("last-sequence", 0)
        snapshot = if (sequence in 1..1_000_000_000L) database.read(key(sequence)) else null
        updateStep()
    }

    private fun updateStep() {
        step = when (snapshot?.phase) {
            AsyncRelayPhase.COMPLETE -> Step.COMPLETE
            AsyncRelayPhase.UNKNOWN -> Step.UNKNOWN
            AsyncRelayPhase.HEADER_WAITING -> Step.HEADER_WAITING
            AsyncRelayPhase.MEDIA_WAITING -> if (confirmedPhotos > 0) Step.ONE_PHOTO_WAITING else Step.MEDIA_WAITING
            null -> Step.IDLE
        }
    }

    override fun close() { database.close() }

    private class Fixture(sequence: Long, now: Long) {
        private val firstId = 10_000L + sequence * 10
        val scope = AsyncRelayScope(ACCOUNT_ID, "offline-demo-$sequence", DESTINATION_ID)
        private val messages = listOf(firstId, firstId + 1).mapIndexed { index, id ->
            RelayMessage(ACCOUNT_ID, SOURCE_ID, id, now / 1000 - 1,
                if (index == 0) "Pick ficticio: equipo de ejemplo, más de 1.5 goles." else "", RelayContentKind.PHOTO,
                albumId = albumId(sequence), canBeForwarded = true)
        }
        val fullBatch = RelayBatch(messages, synthetic = true)
        val manifest = AlbumCompletenessEvidence.FakeManifest(ACCOUNT_ID, SOURCE_ID, albumId(sequence), messages.map { it.messageId })
        private val config = RelayConfig(ACCOUNT_ID, DESTINATION_ID, true,
            listOf(RelaySourceBinding(ACCOUNT_ID, SOURCE_ID, "Canal ficticio · ensayo", RelaySourcePolicy.FANO_DIRECT, true)))
        val fresh = AsyncRelayFreshState(scope, fullBatch, config,
            RelayPermissions(ACCOUNT_ID, SOURCE_ID, DESTINATION_ID, true, true,
                messageForwardable = messages.associate { it.messageId to true },
                messageProtected = messages.associate { it.messageId to false }))
        val intent = AsyncRelayIntent(key(sequence), scope, messages.map { it.messageId }, true, now)
        fun success(temporaryId: Long, index: Int) = AsyncRelayUpdate(scope, AsyncRelayUpdateKind.SUCCEEDED,
            temporaryId, firstId + 100 + index, finalIsOutgoing = true, finalHasSendingState = false)
    }

    companion object {
        const val DATABASE_NAME = "canaldeck-async-offline-demo.db"
        const val PREFERENCES_NAME = "canaldeck-async-offline-demo"
        private const val ACCOUNT_ID = 900_000_001L
        private const val SOURCE_ID = -900_000_101L
        private const val DESTINATION_ID = -900_000_201L
        private val SEQUENCE_LOCK = Any()
        private fun albumId(sequence: Long) = 10_009L + sequence * 10
        private fun key(sequence: Long) = RelayKey(ACCOUNT_ID, SOURCE_ID, "a:${albumId(sequence)}")
    }
}
