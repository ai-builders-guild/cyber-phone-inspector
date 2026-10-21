package com.cyberrick.phoneinspector.net

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IpPacketTest {
    private val payload = TestBytes.DNS_QUERY_EXAMPLE_A

    @Test
    fun `builds an ipv4 udp packet that parses back with the same fields`() {
        val bytes = IpPacket.buildUdp("10.77.77.2", 40000, "10.77.77.1", 53, payload)

        val p = IpPacket.parse(bytes) as IpPacket.Udp
        assertThat(p.src).isEqualTo("10.77.77.2")
        assertThat(p.srcPort).isEqualTo(40000)
        assertThat(p.dst).isEqualTo("10.77.77.1")
        assertThat(p.dstPort).isEqualTo(53)
        assertThat(p.payload).isEqualTo(payload)
    }

    @Test
    fun `ipv4 header and udp checksums verify to zero`() {
        val bytes = IpPacket.buildUdp("10.77.77.2", 40000, "10.77.77.1", 53, payload)

        assertThat(IpPacket.ipv4HeaderChecksumIsValid(bytes)).isTrue()
        assertThat(IpPacket.udpChecksumIsValid(bytes)).isTrue()
    }

    @Test
    fun `a corrupted byte fails the header checksum`() {
        val bytes = IpPacket.buildUdp("10.77.77.2", 40000, "10.77.77.1", 53, payload)
        bytes[8] = (bytes[8].toInt() xor 0x01).toByte() // ttl

        assertThat(IpPacket.ipv4HeaderChecksumIsValid(bytes)).isFalse()
    }

    @Test
    fun `ipv6 and non udp packets are reported as other`() {
        val ipv6 = ByteArray(40).also { it[0] = 0x60 }
        val tcp = IpPacket.buildTcpSyn("10.77.77.2", 50000, "10.77.77.1", 853, seq = 1000L)

        assertThat(IpPacket.parse(ipv6)).isInstanceOf(IpPacket.Other::class.java)
        assertThat(IpPacket.parse(tcp)).isInstanceOf(IpPacket.Tcp::class.java)
    }

    @Test
    fun `a tcp syn gets a reset that acknowledges seq plus one`() {
        val syn = IpPacket.parse(IpPacket.buildTcpSyn("10.77.77.2", 50000, "10.77.77.1", 853, seq = 1000L)) as IpPacket.Tcp

        val rst = IpPacket.parse(IpPacket.buildTcpReset(syn)) as IpPacket.Tcp

        assertThat(rst.src).isEqualTo("10.77.77.1")
        assertThat(rst.srcPort).isEqualTo(853)
        assertThat(rst.dst).isEqualTo("10.77.77.2")
        assertThat(rst.dstPort).isEqualTo(50000)
        assertThat(rst.rst).isTrue()
        assertThat(rst.ack).isTrue()
        assertThat(rst.ackNumber).isEqualTo(1001L)
    }

    @Test
    fun `short or garbage input parses as other and never throws`() {
        assertThat(IpPacket.parse(ByteArray(0))).isInstanceOf(IpPacket.Other::class.java)
        assertThat(IpPacket.parse(ByteArray(5) { 0x45 })).isInstanceOf(IpPacket.Other::class.java)
        assertThat(IpPacket.parse(ByteArray(60) { 0xFF.toByte() })).isInstanceOf(IpPacket.Other::class.java)
    }
}
