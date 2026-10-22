package mx.charly.canal

/** In-memory test double. Has no TDLib import, account session, media bytes or networking. */
class FakeRelayTdlib(
    private val permission: (Int) -> RelayPermissions,
    private val headerAck: RelayAck = RelayAck.CONFIRMED,
    private val forwardAck: RelayAck = RelayAck.CONFIRMED,
    private val failAfterHeader: Boolean = false,
    private val refreshed: (Int, RelayBatch) -> RelayBatch = { _, batch -> batch }
) : RelayTransport {
    override val synthetic = true
    val sentRealMessages: Int get() = 0
    data class Header(val destination: Long, val text: String)
    data class Forward(val destination: Long, val source: Long, val messageIds: List<Long>)
    val headers = mutableListOf<Header>()
    val forwards = mutableListOf<Forward>()
    private var permissionReads = 0
    private var refreshReads = 0
    override fun refreshBatch(batch: RelayBatch) = refreshed(refreshReads++, batch)
    override fun currentPermissions(batch: RelayBatch, config: RelayConfig) = permission(permissionReads++)
    override fun sendHeader(destinationChatId: Long, header: String): RelayAck {
        headers.add(Header(destinationChatId, header))
        if (failAfterHeader) throw IllegalStateException("Synthetic crash after request, before ACK")
        return headerAck
    }
    override fun forwardOriginals(destinationChatId: Long, sourceChatId: Long, messageIds: List<Long>): RelayAck {
        forwards.add(Forward(destinationChatId, sourceChatId, messageIds.toList()))
        return forwardAck
    }
}
