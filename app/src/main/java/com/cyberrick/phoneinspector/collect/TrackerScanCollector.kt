package com.cyberrick.phoneinspector.collect

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream

/** The Exodus tracker catalog: names, categories and class name prefixes to look for inside an app. */
data class TrackerCatalog(
    val trackers: List<Tracker>,
    val generatedAt: String = "",
) {
    data class Tracker(
        val name: String,
        val categories: List<String>,
        val signatures: List<String>,
    )

    /** Signatures as DEX type descriptor prefixes ("com.zeta.ads." → "Lcom/zeta/ads/"), bucketed by their first two labels. */
    val byBucket: Map<String, List<Pair<String, Tracker>>> =
        trackers
            .flatMap { t -> t.signatures.map { sig -> descriptorPrefix(sig) to t } }
            .groupBy { bucket(it.first) }

    val categoriesOf: Map<String, List<String>> = trackers.associate { it.name to it.categories }

    @Serializable
    private data class Doc(
        val generatedAt: String = "",
        val source: String = "",
        val trackers: List<TrackerDoc>,
    )

    @Serializable
    private data class TrackerDoc(
        val name: String,
        val categories: List<String> = emptyList(),
        val signatures: List<String> = emptyList(),
    )

    companion object {
        const val ASSET = "feeds/trackers.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): TrackerCatalog {
            val doc = json.decodeFromString(Doc.serializer(), text)
            return TrackerCatalog(
                doc.trackers.filter { it.signatures.isNotEmpty() }.map { Tracker(it.name, it.categories, it.signatures) },
                doc.generatedAt,
            )
        }

        fun descriptorPrefix(signature: String): String =
            "L" + signature.trimEnd('.').replace('.', '/') + if (signature.endsWith(".")) "/" else ""

        /** "Lcom/zeta/ads/..." → "Lcom/zeta" */
        fun bucket(descriptor: String): String = descriptor.split('/').take(2).joinToString("/")
    }
}

interface DexSource {
    fun nonSystemPackages(): List<String>

    /** The classes*.dex entries of the package's base APK, or empty when unreadable. Throws on a corrupt archive. */
    fun dexFiles(packageName: String): List<ByteArray>

    /**
     * Visits the package's DEX files as streams, one at a time, so no DEX is ever held whole in memory; [block] returns
     * false to stop early. The default wraps [dexFiles]; the Android source streams straight out of the archive.
     */
    fun forEachDex(
        packageName: String,
        block: (InputStream) -> Boolean,
    ) {
        for (dex in dexFiles(packageName)) if (!block(ByteArrayInputStream(dex))) return
    }
}

data class TrackerScanResult(
    val trackers: Map<String, List<String>>,
    val budgetHit: Set<String>,
    val unreadable: Int,
)

/**
 * Finds tracker kits inside apps by matching class descriptors in each DEX string pool against the Exodus
 * signatures. Bounded by a per app time budget so a huge app cannot stall the scan; over budget is reported, not hidden.
 */
class TrackerScanCollector(
    private val source: DexSource,
    private val catalog: TrackerCatalog,
    private val perAppBudgetMs: Long = DEFAULT_BUDGET_MS,
    private val clock: () -> Long = System::nanoTime,
) {
    fun collect(): TrackerScanResult {
        val out = LinkedHashMap<String, List<String>>()
        val budgetHit = mutableSetOf<String>()
        var unreadable = 0
        for (pkg in source.nonSystemPackages()) {
            val found = LinkedHashSet<String>()
            val deadline = clock() + perAppBudgetMs * 1_000_000
            var over = false
            var corrupt = 0
            try {
                source.forEachDex(pkg) { dex ->
                    try {
                        DexStrings.forEach(dex) { s ->
                            if (clock() > deadline) {
                                over = true
                                return@forEach false
                            }
                            if (s.startsWith("L")) {
                                catalog.byBucket[TrackerCatalog.bucket(s)]?.forEach { (prefix, tracker) ->
                                    if (s.startsWith(prefix)) found += tracker.name
                                }
                            }
                            true
                        }
                    } catch (e: DexStrings.DexError) {
                        corrupt++
                    } catch (e: EOFException) {
                        corrupt++
                    }
                    !over
                }
            } catch (e: Exception) {
                unreadable++
                out[pkg] = emptyList()
                continue
            }
            unreadable += corrupt
            if (over) budgetHit += pkg
            out[pkg] = catalog.trackers.map { it.name }.filter { it in found }
        }
        return TrackerScanResult(out, budgetHit, unreadable)
    }

    companion object {
        const val DEFAULT_BUDGET_MS = 1_500L
    }
}

/**
 * Reads the string pool of a DEX file from a stream in one forward pass: header, string id table, then each string's
 * data in ascending offset order (how d8 and dx lay them out). Peak memory is the id table, a few hundred KB, never
 * the DEX itself.
 */
object DexStrings {
    class DexError(
        message: String,
    ) : Exception(message)

    private const val HEADER_SIZE = 0x70
    private const val STRING_IDS_SIZE = 0x38
    private const val STRING_IDS_OFF = 0x3C
    private const val MAX_STRINGS = 2_000_000

    fun read(dex: ByteArray): List<String> {
        val out = ArrayList<String>()
        forEach(ByteArrayInputStream(dex)) {
            out += it
            true
        }
        return out
    }

    /** Streams the string pool; [block] returns false to stop. Throws [DexError] on anything that is not a readable DEX. */
    fun forEach(
        input: InputStream,
        block: (String) -> Boolean,
    ) {
        val stream = Cursor(input.buffered(64 * 1024))
        val header = ByteArray(HEADER_SIZE)
        stream.readFully(header)
        if (!(header[0] == 'd'.code.toByte() && header[1] == 'e'.code.toByte() && header[2] == 'x'.code.toByte())) {
            throw DexError(
                "not a dex file",
            )
        }
        val count = u32(header, STRING_IDS_SIZE)
        val idsOff = u32(header, STRING_IDS_OFF)
        if (count < 0 || count > MAX_STRINGS || idsOff < HEADER_SIZE) throw DexError("string table out of range")
        if (count == 0) return
        stream.skipTo(idsOff.toLong())
        val table = ByteArray(count * 4)
        stream.readFully(table)
        val offsets = LongArray(count) { u32(table, it * 4).toLong() and 0xFFFF_FFFFL }
        offsets.sort()
        val data = ByteArray(256)
        for (at in offsets) {
            if (at < stream.position) throw DexError("string data out of order")
            stream.skipTo(at)
            var shift = 0
            while (true) {
                val v = stream.readByte()
                if (v and 0x80 == 0) break
                shift += 7
                if (shift > 35) throw DexError("bad uleb128")
            }
            var n = 0
            var buf = data
            while (true) {
                val b = stream.readByte()
                if (b == 0) break
                if (n == buf.size) buf = buf.copyOf(buf.size * 2)
                buf[n++] = b.toByte()
            }
            if (!block(String(buf, 0, n, Charsets.UTF_8))) return
        }
    }

    private class Cursor(
        private val input: InputStream,
    ) {
        var position: Long = 0
            private set

        fun readByte(): Int {
            val v = input.read()
            if (v < 0) throw DexError("truncated dex")
            position++
            return v
        }

        fun readFully(buf: ByteArray) {
            var read = 0
            while (read < buf.size) {
                val n = input.read(buf, read, buf.size - read)
                if (n < 0) throw DexError("truncated dex")
                read += n
            }
            position += buf.size
        }

        fun skipTo(target: Long) {
            if (target < position) throw DexError("backward seek")
            var left = target - position
            while (left > 0) {
                val n = input.skip(left)
                if (n <= 0) {
                    if (input.read() < 0) throw EOFException("truncated dex")
                    left--
                } else {
                    left -= n
                }
            }
            position = target
        }
    }

    private fun u32(
        b: ByteArray,
        at: Int,
    ): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16) or
            ((b[at + 3].toInt() and 0xFF) shl 24)
}
