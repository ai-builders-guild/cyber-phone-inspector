package com.cyberrick.phoneinspector.net

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DnsRelayTest {
    private var now = 1_792_584_000_000L
    private val uids =
        FakeUidResolver(
            mapOf(40000 to 10123, 40001 to 10456),
            mapOf(
                10123 to "com.android.chrome",
                10456 to "com.example.other",
            ),
        )
    private val relay = DnsRelay(uids, resolverAddress = "10.77.77.1") { now }

    private fun query(
        srcPort: Int,
        dns: ByteArray = TestBytes.DNS_QUERY_EXAMPLE_A,
    ) = TestBytes.udpPacket("10.77.77.2", srcPort, "10.77.77.1", 53, dns)

    @Test
    fun `a dns query from the tun is forwarded with a fresh upstream id`() {
        val action = relay.onTunPacket(query(40000))

        val fwd = action as DnsRelay.Action.Forward
        assertThat(DnsMessage.parse(fwd.payload).id).isNotEqualTo(0x1234)
        assertThat(DnsMessage.parse(fwd.payload).question?.name).isEqualTo("example.com")
    }

    @Test
    fun `an upstream response is rewritten to the original id and written back to the client`() {
        val fwd = relay.onTunPacket(query(40000)) as DnsRelay.Action.Forward
        val upstreamId = DnsMessage.parse(fwd.payload).id
        val response = DnsMessage.withId(TestBytes.DNS_RESPONSE_EXAMPLE_A, upstreamId)
        now += 37

        val out = relay.onUpstreamResponse(response)

        val back = IpPacket.parse(out.tunPacket!!) as IpPacket.Udp
        assertThat(back.dst).isEqualTo("10.77.77.2")
        assertThat(back.dstPort).isEqualTo(40000)
        assertThat(back.srcPort).isEqualTo(53)
        assertThat(DnsMessage.parse(back.payload).id).isEqualTo(0x1234)
        assertThat(IpPacket.udpChecksumIsValid(out.tunPacket!!)).isTrue()
    }

    @Test
    fun `a completed lookup becomes a flow record attributed to the app with latency`() {
        val fwd = relay.onTunPacket(query(40000)) as DnsRelay.Action.Forward
        now += 37
        val out = relay.onUpstreamResponse(DnsMessage.withId(TestBytes.DNS_RESPONSE_EXAMPLE_A, DnsMessage.parse(fwd.payload).id))

        val flow = out.flow!!
        assertThat(flow.uid).isEqualTo(10123)
        assertThat(flow.packageName).isEqualTo("com.android.chrome")
        assertThat(flow.host).isEqualTo("example.com")
        assertThat(flow.answerIps).containsExactly("93.184.216.34")
        assertThat(flow.remotePort).isEqualTo(53)
        assertThat(flow.protocol).isEqualTo("udp")
        assertThat(flow.observedAt).isEqualTo(1_792_584_000_000L)
        assertThat(flow.api).isEqualTo(DnsRelay.API)
        assertThat(relay.stats.latenciesMs).containsExactly(37L)
    }

    @Test
    fun `two clients using the same dns id do not collide`() {
        val a = relay.onTunPacket(query(40000)) as DnsRelay.Action.Forward
        val b = relay.onTunPacket(query(40001)) as DnsRelay.Action.Forward

        assertThat(DnsMessage.parse(a.payload).id).isNotEqualTo(DnsMessage.parse(b.payload).id)
        val outB = relay.onUpstreamResponse(DnsMessage.withId(TestBytes.DNS_RESPONSE_EXAMPLE_A, DnsMessage.parse(b.payload).id))
        assertThat((IpPacket.parse(outB.tunPacket!!) as IpPacket.Udp).dstPort).isEqualTo(40001)
        assertThat(outB.flow?.packageName).isEqualTo("com.example.other")
    }

    @Test
    fun `an unattributed query is still relayed and recorded with uid minus one`() {
        val fwd = relay.onTunPacket(query(40999)) as DnsRelay.Action.Forward
        val out = relay.onUpstreamResponse(DnsMessage.withId(TestBytes.DNS_RESPONSE_EXAMPLE_A, DnsMessage.parse(fwd.payload).id))

        assertThat(out.flow?.uid).isEqualTo(-1)
        assertThat(out.flow?.packageName).isNull()
    }

    @Test
    fun `a tcp syn to the resolver gets a reset so dns over tls probing fails fast`() {
        val syn = IpPacket.buildTcpSyn("10.77.77.2", 50000, "10.77.77.1", 853, seq = 7L)

        val action = relay.onTunPacket(syn)

        val rst = IpPacket.parse((action as DnsRelay.Action.WriteToTun).packet) as IpPacket.Tcp
        assertThat(rst.rst).isTrue()
        assertThat(relay.stats.resetsSent).isEqualTo(1)
    }

    @Test
    fun `malformed dns unknown responses and foreign packets are dropped and counted never thrown`() {
        val garbage = TestBytes.udpPacket("10.77.77.2", 40000, "10.77.77.1", 53, TestBytes.DNS_LABEL_LOOP)
        val notForUs = TestBytes.udpPacket("10.77.77.2", 40000, "1.1.1.1", 53, TestBytes.DNS_QUERY_EXAMPLE_A)

        assertThat(relay.onTunPacket(garbage)).isEqualTo(DnsRelay.Action.Drop)
        assertThat(relay.onTunPacket(notForUs)).isEqualTo(DnsRelay.Action.Drop)
        assertThat(relay.onTunPacket(ByteArray(3))).isEqualTo(DnsRelay.Action.Drop)
        val unknown = relay.onUpstreamResponse(DnsMessage.withId(TestBytes.DNS_RESPONSE_EXAMPLE_A, 0x0F0F))
        assertThat(unknown.tunPacket).isNull()
        assertThat(relay.onUpstreamResponse(ByteArray(4)).tunPacket).isNull()

        assertThat(relay.stats.droppedMalformed).isEqualTo(3)
        assertThat(relay.stats.droppedUnknown).isEqualTo(1)
        assertThat(relay.stats.droppedForeign).isEqualTo(1)
    }

    @Test
    fun `pending queries older than the timeout are expired`() {
        relay.onTunPacket(query(40000))
        now += DnsRelay.PENDING_TIMEOUT_MS + 1

        relay.expirePending()

        assertThat(relay.stats.expired).isEqualTo(1)
        assertThat(relay.pendingCount).isEqualTo(0)
    }
}

class FakeUidResolver(
    private val uidByClientPort: Map<Int, Int>,
    private val packageByUid: Map<Int, String>,
) : UidResolver {
    override fun ownerUid(
        srcAddr: String,
        srcPort: Int,
        dstAddr: String,
        dstPort: Int,
    ): Int = uidByClientPort[srcPort] ?: -1

    override fun packageFor(uid: Int): String? = packageByUid[uid]
}
