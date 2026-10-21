package com.cyberrick.phoneinspector.net

/** Hand assembled packets for the parser tests. Hex keeps the layout visible. */
object TestBytes {
    fun hex(s: String): ByteArray =
        s
            .replace(Regex("[^0-9a-fA-F]"), "")
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

    /** Standard query id 0x1234, RD, one question: example.com A IN. */
    val DNS_QUERY_EXAMPLE_A: ByteArray =
        hex("1234 0100 0001 0000 0000 0000" + " 07 6578616d706c65 03 636f6d 00" + " 0001 0001")

    /** Response id 0x1234: question example.com A, answer uses a pointer to offset 12, A 93.184.216.34 ttl 300. */
    val DNS_RESPONSE_EXAMPLE_A: ByteArray =
        hex(
            "1234 8180 0001 0001 0000 0000" +
                " 07 6578616d706c65 03 636f6d 00 0001 0001" +
                " c00c 0001 0001 0000012c 0004 5db8d822",
        )

    /** Response: www.example.com CNAME example.com (pointer), then example.com AAAA 2606:2800:220:1:248:1893:25c8:1946. */
    val DNS_RESPONSE_CNAME_AAAA: ByteArray =
        hex(
            "abcd 8180 0001 0002 0000 0000" +
                " 03 777777 07 6578616d706c65 03 636f6d 00 001c 0001" +
                " c00c 0005 0001 00000e10 0002 c010" +
                " c010 001c 0001 00000e10 0010 26062800022000010248189325c81946",
        )

    /** A name whose pointer points at itself: must be rejected, never loop. */
    val DNS_LABEL_LOOP: ByteArray = hex("0001 0100 0001 0000 0000 0000" + " c00c" + " 0001 0001")

    /** Header says one question but the bytes stop after the header. */
    val DNS_TRUNCATED: ByteArray = hex("0002 0100 0001 0000 0000 0000")

    /** IPv4 + UDP from 10.77.77.2:40000 to 10.77.77.1:53 carrying DNS_QUERY_EXAMPLE_A. Checksums are computed in the builder tests. */
    fun udpPacket(
        src: String,
        srcPort: Int,
        dst: String,
        dstPort: Int,
        payload: ByteArray,
    ): ByteArray = IpPacket.buildUdp(src, srcPort, dst, dstPort, payload)
}
