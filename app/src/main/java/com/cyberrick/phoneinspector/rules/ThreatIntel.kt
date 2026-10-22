package com.cyberrick.phoneinspector.rules

import com.cyberrick.phoneinspector.collect.TrackerCatalog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Known Android malware by base APK hash (MalwareBazaar). */
data class MalwareHashes(
    val entries: Map<String, Entry>,
    val generatedAt: String = "",
    val skipped: String? = null,
) {
    data class Entry(
        val family: String,
        val tags: List<String>,
    )

    fun lookup(sha256: String?): Entry? = sha256?.lowercase()?.let { entries[it] }

    @Serializable
    private data class Doc(
        val generatedAt: String = "",
        val skipped: String? = null,
        val hashes: Map<String, EntryDoc> = emptyMap(),
    )

    @Serializable
    private data class EntryDoc(
        val family: String = "unknown",
        val tags: List<String> = emptyList(),
    )

    companion object {
        const val ASSET = "feeds/malware_hashes.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): MalwareHashes {
            val doc = json.decodeFromString(Doc.serializer(), text)
            return MalwareHashes(
                doc.hashes
                    .mapKeys {
                        it.key.lowercase()
                    }.mapValues { Entry(it.value.family, it.value.tags) },
                doc.generatedAt,
                doc.skipped,
            )
        }
    }
}

/** Malware distribution and command hosts (URLhaus, ThreatFox). Domain match is suffix on a label boundary. */
data class MaliciousHosts(
    val domains: Set<String>,
    val ips: Set<String>,
    val generatedAt: String = "",
    val skipped: String? = null,
) {
    private val lower = domains.map { it.lowercase() }.toSet()

    fun domainHit(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h in lower) return true
        var dot = h.indexOf('.')
        while (dot >= 0) {
            if (h.substring(dot + 1) in lower) return true
            dot = h.indexOf('.', dot + 1)
        }
        return false
    }

    fun ipHit(ip: String): Boolean = ip in ips

    @Serializable
    private data class Doc(
        val generatedAt: String = "",
        val skipped: String? = null,
        val domains: List<String> = emptyList(),
        val ips: List<String> = emptyList(),
    )

    companion object {
        const val ASSET = "feeds/malicious_hosts.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): MaliciousHosts {
            val doc = json.decodeFromString(Doc.serializer(), text)
            return MaliciousHosts(doc.domains.toSet(), doc.ips.toSet(), doc.generatedAt, doc.skipped)
        }
    }
}

/** Everything the rules know beyond the phone's own evidence. */
data class ThreatIntel(
    val malware: MalwareHashes,
    val hosts: MaliciousHosts,
    val trackers: TrackerCatalog,
) {
    companion object {
        val EMPTY = ThreatIntel(MalwareHashes(emptyMap()), MaliciousHosts(emptySet(), emptySet()), TrackerCatalog(emptyList()))
    }
}
