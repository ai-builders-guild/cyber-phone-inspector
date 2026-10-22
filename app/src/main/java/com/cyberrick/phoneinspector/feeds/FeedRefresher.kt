package com.cyberrick.phoneinspector.feeds

import com.cyberrick.phoneinspector.collect.TrackerCatalog
import com.cyberrick.phoneinspector.rules.IocSet
import com.cyberrick.phoneinspector.rules.MaliciousHosts
import com.cyberrick.phoneinspector.rules.MalwareHashes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Fetches one address and returns its bytes, or throws. The Android one speaks HTTPS to the feeds repo only. */
fun interface Fetcher {
    fun get(url: String): ByteArray
}

/** One list on the phone: its file name, how to prove a download is that list, and the date inside it. */
enum class FeedFile(
    val fileName: String,
    val title: String,
) {
    SPYWARE("spyware.json", "Spyware and stalkerware (MVT index)"),
    TRACKERS("trackers.json", "Trackers (Exodus Privacy)"),
    MALWARE_HASHES("malware_hashes.json", "Malware hashes (MalwareBazaar)"),
    MALICIOUS_HOSTS("malicious_hosts.json", "Malicious hosts (URLhaus, ThreatFox)"),
    ;

    /** Throws when the text is not this list. */
    fun validate(text: String) {
        when (this) {
            SPYWARE -> require(IocSet.parse(text).packages.isNotEmpty()) { "no packages" }
            TRACKERS -> require(TrackerCatalog.parse(text).trackers.isNotEmpty()) { "no trackers" }
            MALWARE_HASHES -> MalwareHashes.parse(text)
            MALICIOUS_HOSTS -> MaliciousHosts.parse(text)
        }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** The generatedAt field of any list, or null. */
        fun dateOf(text: String): String? =
            try {
                json
                    .parseToJsonElement(text)
                    .jsonObject["generatedAt"]
                    ?.jsonPrimitive
                    ?.content
                    ?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
    }
}

data class RefreshResult(
    val updated: List<FeedFile>,
    val unchanged: List<FeedFile>,
    val rejected: Map<FeedFile, String>,
) {
    val ok: Boolean get() = rejected.isEmpty()
}

/**
 * Replaces the lists under the app's private feeds directory from the public feeds repository. Each download must
 * parse as its list and must not be much smaller than the copy it replaces (a truncated or empty file is refused and
 * the previous file kept). The replace is atomic: write to a temp file, then rename. The address is fixed.
 */
class FeedRefresher(
    private val dir: File,
    private val fetcher: Fetcher,
    private val base: String = BASE,
    private val bundled: (FeedFile) -> String? = { null },
) {
    fun refresh(): RefreshResult {
        dir.mkdirs()
        val updated = mutableListOf<FeedFile>()
        val unchanged = mutableListOf<FeedFile>()
        val rejected = linkedMapOf<FeedFile, String>()
        for (feed in FeedFile.entries) {
            val target = File(dir, feed.fileName)
            val current = if (target.isFile) target.readText(Charsets.UTF_8) else bundled(feed)
            val text =
                try {
                    val bytes = fetcher.get(base + feed.fileName)
                    if (bytes.size > MAX_BYTES) throw IllegalStateException("too large (${bytes.size} bytes)")
                    String(bytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    rejected[feed] = "download failed: ${e.message ?: e.javaClass.simpleName}"
                    continue
                }
            try {
                feed.validate(text)
            } catch (e: Exception) {
                rejected[feed] = "not a valid list: ${e.message ?: e.javaClass.simpleName}"
                continue
            }
            if (current != null && text.length < current.length / 2 && FeedFile.dateOf(current) != null) {
                rejected[feed] = "much smaller than the current list (${text.length} vs ${current.length} chars)"
                continue
            }
            if (current == text) {
                unchanged += feed
                continue
            }
            val tmp = File(dir, feed.fileName + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                tmp.delete()
                rejected[feed] = "could not replace the file"
                continue
            }
            updated += feed
        }
        return RefreshResult(updated, unchanged, rejected)
    }

    companion object {
        /** The only address the app ever downloads from: the public data repository's raw files. */
        const val BASE = "https://raw.githubusercontent.com/CyberRick-AI/cyber-phone-inspector-feeds/main/feeds/"
        const val MAX_BYTES = 24 * 1024 * 1024
    }
}
