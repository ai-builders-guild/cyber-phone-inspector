package com.cyberrick.phoneinspector.net

/** IPv4 packet reading and writing for the tunnel: UDP for DNS, TCP only far enough to answer a SYN with a reset. */
sealed class IpPacket {
    data class Udp(
        val src: String,
        val srcPort: Int,
        val dst: String,
        val dstPort: Int,
        val payload: ByteArray,
    ) : IpPacket()

    data class Tcp(
        val src: String,
        val srcPort: Int,
        val dst: String,
        val dstPort: Int,
        val seqNumber: Long,
        val ackNumber: Long,
        val syn: Boolean,
        val ack: Boolean,
        val rst: Boolean,
    ) : IpPacket()

    data object Other : IpPacket()

    companion object {
        private const val PROTO_TCP = 6
        private const val PROTO_UDP = 17

        fun parse(bytes: ByteArray): IpPacket {
            if (bytes.size < 20) return Other
            val version = (bytes[0].toInt() and 0xF0) shr 4
            val ihl = (bytes[0].toInt() and 0x0F) * 4
            if (version != 4 || ihl < 20 || bytes.size < ihl) return Other
            val totalLength = u16(bytes, 2)
            if (totalLength < ihl || totalLength > bytes.size) return Other
            val proto = bytes[9].toInt() and 0xFF
            val src = ipv4(bytes, 12)
            val dst = ipv4(bytes, 16)
            return when (proto) {
                PROTO_UDP -> {
                    if (totalLength < ihl + 8) return Other
                    val udpLen = u16(bytes, ihl + 4)
                    if (udpLen < 8 || ihl + udpLen > totalLength) return Other
                    Udp(src, u16(bytes, ihl), dst, u16(bytes, ihl + 2), bytes.copyOfRange(ihl + 8, ihl + udpLen))
                }
                PROTO_TCP -> {
                    if (totalLength < ihl + 20) return Other
                    val flags = bytes[ihl + 13].toInt() and 0xFF
                    Tcp(
                        src,
                        u16(bytes, ihl),
                        dst,
                        u16(bytes, ihl + 2),
                        seqNumber = u32(bytes, ihl + 4),
                        ackNumber = u32(bytes, ihl + 8),
                        syn = flags and 0x02 != 0,
                        ack = flags and 0x10 != 0,
                        rst = flags and 0x04 != 0,
                    )
                }
                else -> Other
            }
        }

        fun buildUdp(
            src: String,
            srcPort: Int,
            dst: String,
            dstPort: Int,
            payload: ByteArray,
        ): ByteArray {
            val udpLen = 8 + payload.size
            val udp = ByteArray(udpLen)
            put16(udp, 0, srcPort)
            put16(udp, 2, dstPort)
            put16(udp, 4, udpLen)
            payload.copyInto(udp, 8)
            val sum = transportChecksum(src, dst, PROTO_UDP, udp)
            put16(udp, 6, if (sum == 0) 0xFFFF else sum)
            return ipv4Header(src, dst, PROTO_UDP, udp.size) + udp
        }

        /** Test helper and probe helper: a SYN as a client would send it. */
        fun buildTcpSyn(
            src: String,
            srcPort: Int,
            dst: String,
            dstPort: Int,
            seq: Long,
        ): ByteArray = buildTcp(src, srcPort, dst, dstPort, seq = seq, ack = 0, flags = 0x02)

        /** RST+ACK answering [syn], so a DNS over TLS probe to the tunnel resolver fails immediately. */
        fun buildTcpReset(syn: Tcp): ByteArray =
            buildTcp(syn.dst, syn.dstPort, syn.src, syn.srcPort, seq = 0, ack = (syn.seqNumber + 1) and 0xFFFFFFFFL, flags = 0x14)

        private fun buildTcp(
            src: String,
            srcPort: Int,
            dst: String,
            dstPort: Int,
            seq: Long,
            ack: Long,
            flags: Int,
        ): ByteArray {
            val tcp = ByteArray(20)
            put16(tcp, 0, srcPort)
            put16(tcp, 2, dstPort)
            put32(tcp, 4, seq)
            put32(tcp, 8, ack)
            tcp[12] = (5 shl 4).toByte()
            tcp[13] = flags.toByte()
            put16(tcp, 14, 0)
            put16(tcp, 16, transportChecksum(src, dst, PROTO_TCP, tcp))
            return ipv4Header(src, dst, PROTO_TCP, tcp.size) + tcp
        }

        fun ipv4HeaderChecksumIsValid(bytes: ByteArray): Boolean {
            val ihl = (bytes[0].toInt() and 0x0F) * 4
            return onesComplementSum(bytes, 0, ihl, 0) == 0xFFFF
        }

        fun udpChecksumIsValid(bytes: ByteArray): Boolean {
            val ihl = (bytes[0].toInt() and 0x0F) * 4
            val udpLen = u16(bytes, ihl + 4)
            val pseudo = pseudoHeader(ipv4(bytes, 12), ipv4(bytes, 16), PROTO_UDP, udpLen)
            return onesComplementSum(bytes, ihl, udpLen, onesComplementSumRaw(pseudo)) == 0xFFFF
        }

        private fun ipv4Header(
            src: String,
            dst: String,
            proto: Int,
            payloadLen: Int,
        ): ByteArray {
            val h = ByteArray(20)
            h[0] = 0x45
            put16(h, 2, 20 + payloadLen)
            h[8] = 64
            h[9] = proto.toByte()
            parseIpv4(src).copyInto(h, 12)
            parseIpv4(dst).copyInto(h, 16)
            put16(h, 10, onesComplementSum(h, 0, 20, 0).inv() and 0xFFFF)
            return h
        }

        private fun pseudoHeader(
            src: String,
            dst: String,
            proto: Int,
            len: Int,
        ): ByteArray =
            ByteArray(12).also {
                parseIpv4(src).copyInto(it, 0)
                parseIpv4(dst).copyInto(it, 4)
                it[9] = proto.toByte()
                put16(it, 10, len)
            }

        private fun transportChecksum(
            src: String,
            dst: String,
            proto: Int,
            segment: ByteArray,
        ): Int =
            onesComplementSum(segment, 0, segment.size, onesComplementSumRaw(pseudoHeader(src, dst, proto, segment.size))).inv() and 0xFFFF

        /** Running ones' complement sum, folded to 16 bits. Returns the folded sum (0xFFFF when a checksummed block verifies). */
        private fun onesComplementSum(
            b: ByteArray,
            start: Int,
            len: Int,
            initial: Int,
        ): Int {
            var sum = initial
            var i = start
            val end = start + len
            while (i + 1 < end) {
                sum += ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
                i += 2
            }
            if (i < end) sum += (b[i].toInt() and 0xFF) shl 8
            while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
            return sum
        }

        private fun onesComplementSumRaw(b: ByteArray): Int = onesComplementSum(b, 0, b.size, 0)

        private fun ipv4(
            b: ByteArray,
            at: Int,
        ): String = (0 until 4).joinToString(".") { (b[at + it].toInt() and 0xFF).toString() }

        private fun parseIpv4(s: String): ByteArray {
            val parts = s.split('.')
            require(parts.size == 4) { "not an IPv4 address: $s" }
            return ByteArray(4) { parts[it].toInt().toByte() }
        }

        private fun u16(
            b: ByteArray,
            at: Int,
        ): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

        private fun u32(
            b: ByteArray,
            at: Int,
        ): Long = (u16(b, at).toLong() shl 16) or u16(b, at + 2).toLong()

        private fun put16(
            b: ByteArray,
            at: Int,
            v: Int,
        ) {
            b[at] = (v shr 8).toByte()
            b[at + 1] = v.toByte()
        }

        private fun put32(
            b: ByteArray,
            at: Int,
            v: Long,
        ) {
            put16(b, at, ((v shr 16) and 0xFFFF).toInt())
            put16(b, at + 2, (v and 0xFFFF).toInt())
        }
    }
}
