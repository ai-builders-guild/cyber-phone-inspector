package com.cyberrick.phoneinspector.net

import com.cyberrick.phoneinspector.core.model.FlowRecord

/**
 * The tunnel's brain, pure and single threaded by contract (the service serialises calls). Queries from the tun
 * get a fresh upstream id so two apps sharing an id cannot collide; responses are matched, restored to the client's
 * id, wrapped in a packet back to the client, and recorded as a [FlowRecord] attributed to the owning app.
 */
class DnsRelay(
    private val uids: UidResolver,
    private val resolverAddress: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed class Action {
        data class Forward(
            val payload: ByteArray,
        ) : Action()

        data class WriteToTun(
            val packet: ByteArray,
        ) : Action()

        data object Drop : Action()
    }

    data class Out(
        val tunPacket: ByteArray?,
        val flow: FlowRecord?,
    )

    class Stats {
        var droppedMalformed = 0
        var droppedUnknown = 0
        var droppedForeign = 0
        var expired = 0
        var resetsSent = 0
        var flows = 0
        val latenciesMs = mutableListOf<Long>()
    }

    private data class Pending(
        val clientAddr: String,
        val clientPort: Int,
        val originalId: Int,
        val uid: Int,
        val host: String,
        val sentAt: Long,
    )

    val stats = Stats()
    private val pending = LinkedHashMap<Int, Pending>()
    private var nextId = 1
    val pendingCount: Int get() = pending.size

    fun onTunPacket(packet: ByteArray): Action {
        return when (val ip = IpPacket.parse(packet)) {
            is IpPacket.Udp -> {
                if (ip.dst != resolverAddress || ip.dstPort != DNS_PORT) {
                    stats.droppedForeign++
                    return Action.Drop
                }
                val query =
                    try {
                        DnsMessage.parse(ip.payload)
                    } catch (e: DnsParseError) {
                        stats.droppedMalformed++
                        return Action.Drop
                    }
                val host =
                    query.question?.name ?: run {
                        stats.droppedMalformed++
                        return Action.Drop
                    }
                val uid = uids.ownerUid(ip.src, ip.srcPort, ip.dst, ip.dstPort)
                val upstreamId = allocateId()
                pending[upstreamId] = Pending(ip.src, ip.srcPort, query.id, uid, host, clock())
                Action.Forward(DnsMessage.withId(ip.payload, upstreamId))
            }
            is IpPacket.Tcp -> {
                if (ip.dst == resolverAddress && ip.syn && !ip.ack) {
                    stats.resetsSent++
                    Action.WriteToTun(IpPacket.buildTcpReset(ip))
                } else {
                    stats.droppedForeign++
                    Action.Drop
                }
            }
            IpPacket.Other -> {
                stats.droppedMalformed++
                Action.Drop
            }
        }
    }

    fun onUpstreamResponse(payload: ByteArray): Out {
        val response =
            try {
                DnsMessage.parse(payload)
            } catch (e: DnsParseError) {
                stats.droppedMalformed++
                return Out(null, null)
            }
        val p =
            pending.remove(response.id) ?: run {
                stats.droppedUnknown++
                return Out(null, null)
            }
        val now = clock()
        stats.latenciesMs += now - p.sentAt
        stats.flows++
        val packet = IpPacket.buildUdp(resolverAddress, DNS_PORT, p.clientAddr, p.clientPort, DnsMessage.withId(payload, p.originalId))
        val flow =
            FlowRecord(
                uid = p.uid,
                packageName = if (p.uid >= 0) uids.packageFor(p.uid) else null,
                host = p.host,
                answerIps = response.answerAddresses,
                remotePort = DNS_PORT,
                protocol = "udp",
                observedAt = p.sentAt,
                api = API,
                collectedAt = now,
            )
        return Out(packet, flow)
    }

    /** Drops queries that never got an answer so the pending map cannot grow without bound. */
    fun expirePending() {
        val cutoff = clock() - PENDING_TIMEOUT_MS
        val stale = pending.filterValues { it.sentAt < cutoff }.keys
        stale.forEach { pending.remove(it) }
        stats.expired += stale.size
    }

    private fun allocateId(): Int {
        repeat(0xFFFF) {
            val id = nextId
            nextId = if (nextId >= 0xFFFF) 1 else nextId + 1
            if (id !in pending) return id
        }
        error("no free DNS ids: ${pending.size} pending")
    }

    companion object {
        const val API = "DnsTunnelService"
        const val DNS_PORT = 53
        const val PENDING_TIMEOUT_MS = 5_000L
    }
}
