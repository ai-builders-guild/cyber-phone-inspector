package com.cyberrick.phoneinspector.collect

import java.io.ByteArrayOutputStream

/** Builds the smallest DEX like byte array whose string pool holds the given strings (header + string ids + data). */
object SyntheticDex {
    fun build(strings: List<String>): ByteArray {
        val header = ByteArray(0x70)
        "dex\n035\u0000".toByteArray(Charsets.US_ASCII).copyInto(header, 0)
        val idsOff = 0x70
        val dataOff = idsOff + strings.size * 4
        val data = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        strings.forEachIndexed { i, s ->
            offsets[i] = dataOff + data.size()
            val bytes = s.toByteArray(Charsets.UTF_8)
            var len = s.length
            while (len >= 0x80) {
                data.write((len and 0x7F) or 0x80)
                len = len shr 7
            }
            data.write(len)
            data.write(bytes)
            data.write(0)
        }
        putU32(header, 0x38, strings.size)
        putU32(header, 0x3C, idsOff)
        val ids = ByteArray(strings.size * 4)
        offsets.forEachIndexed { i, o -> putU32(ids, i * 4, o) }
        return header + ids + data.toByteArray()
    }

    private fun putU32(
        b: ByteArray,
        at: Int,
        v: Int,
    ) {
        b[at] = v.toByte()
        b[at + 1] = (v shr 8).toByte()
        b[at + 2] = (v shr 16).toByte()
        b[at + 3] = (v shr 24).toByte()
    }
}
