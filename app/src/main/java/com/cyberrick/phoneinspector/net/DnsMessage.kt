package com.cyberrick.phoneinspector.net

class DnsParseError(
    message: String,
) : Exception(message)

/**
 * Minimal DNS wire format reader: header, first question, and A, AAAA and CNAME answers. Name compression is
 * followed with a bounded pointer count so a hostile packet cannot loop. Nothing here writes to the network.
 */
data class DnsMessage(
    val id: Int,
    val isResponse: Boolean,
    val question: Question?,
    val answerAddresses: List<String>,
    val cnames: List<String>,
) {
    data class Question(
        val name: String,
        val type: Int,
    )

    companion object {
        private const val HEADER = 12
        private const val MAX_POINTERS = 16
        private const val TYPE_A = 1
        private const val TYPE_CNAME = 5
        private const val TYPE_AAAA = 28

        fun parse(bytes: ByteArray): DnsMessage {
            if (bytes.size < HEADER) throw DnsParseError("message shorter than a header (${bytes.size} bytes)")
            val id = u16(bytes, 0)
            val flags = u16(bytes, 2)
            val qd = u16(bytes, 4)
            val an = u16(bytes, 6)
            var offset = HEADER
            var question: Question? = null
            repeat(qd) { i ->
                val (name, next) = readName(bytes, offset)
                need(bytes, next, 4)
                if (i == 0) question = Question(name, u16(bytes, next))
                offset = next + 4
            }
            val addresses = mutableListOf<String>()
            val cnames = mutableListOf<String>()
            repeat(an) {
                val (_, next) = readName(bytes, offset)
                need(bytes, next, 10)
                val type = u16(bytes, next)
                val rdLen = u16(bytes, next + 8)
                val rdata = next + 10
                need(bytes, rdata, rdLen)
                when (type) {
                    TYPE_A -> if (rdLen == 4) addresses += (0 until 4).joinToString(".") { (bytes[rdata + it].toInt() and 0xFF).toString() }
                    TYPE_AAAA -> if (rdLen == 16) addresses += ipv6(bytes, rdata)
                    TYPE_CNAME -> cnames += readName(bytes, rdata).first
                }
                offset = rdata + rdLen
            }
            return DnsMessage(id, flags and 0x8000 != 0, question, addresses, cnames)
        }

        /** Copy with the transaction id replaced; the rest of the message is untouched. */
        fun withId(
            bytes: ByteArray,
            id: Int,
        ): ByteArray =
            bytes.copyOf().also {
                if (it.size < 2) throw DnsParseError("too short to carry an id")
                it[0] = (id shr 8).toByte()
                it[1] = id.toByte()
            }

        /** Returns (name, offset just past the name as it appears at [start]). */
        private fun readName(
            bytes: ByteArray,
            start: Int,
        ): Pair<String, Int> {
            val labels = mutableListOf<String>()
            var pos = start
            var end = -1
            var pointers = 0
            while (true) {
                need(bytes, pos, 1)
                val len = bytes[pos].toInt() and 0xFF
                when {
                    len == 0 -> {
                        if (end < 0) end = pos + 1
                        return labels.joinToString(".") to end
                    }
                    len and 0xC0 == 0xC0 -> {
                        need(bytes, pos, 2)
                        val target = ((len and 0x3F) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
                        if (++pointers > MAX_POINTERS ||
                            target >= pos
                        ) {
                            throw DnsParseError("compression pointer loop or forward pointer at $pos")
                        }
                        if (end < 0) end = pos + 2
                        pos = target
                    }
                    len > 63 -> throw DnsParseError("label length $len at $pos")
                    else -> {
                        need(bytes, pos + 1, len)
                        labels += String(bytes, pos + 1, len, Charsets.ISO_8859_1)
                        pos += 1 + len
                    }
                }
            }
        }

        private fun ipv6(
            b: ByteArray,
            at: Int,
        ): String {
            val groups = (0 until 8).map { u16(b, at + it * 2) }
            // Longest run of zero groups becomes "::", as inet_ntop does.
            var bestStart = -1
            var bestLen = 0
            var i = 0
            while (i < 8) {
                if (groups[i] == 0) {
                    var j = i
                    while (j < 8 && groups[j] == 0) j++
                    if (j - i > bestLen) {
                        bestStart = i
                        bestLen = j - i
                    }
                    i = j
                } else {
                    i++
                }
            }
            if (bestLen < 2) return groups.joinToString(":") { it.toString(16) }
            val head = groups.subList(0, bestStart).joinToString(":") { it.toString(16) }
            val tail = groups.subList(bestStart + bestLen, 8).joinToString(":") { it.toString(16) }
            return "$head::$tail"
        }

        private fun u16(
            b: ByteArray,
            at: Int,
        ): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

        private fun need(
            b: ByteArray,
            at: Int,
            len: Int,
        ) {
            if (at < 0 || at + len > b.size) throw DnsParseError("truncated at $at, need $len, have ${b.size}")
        }
    }
}
