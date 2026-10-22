package com.cyberrick.phoneinspector.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Domain suffixes of services most phones talk to: platform vendors, big apps, CDNs, analytics, carriers.
 * "Known" means recognised, not endorsed; "unrecognized" means not on this list, never "malicious".
 */
data class KnownServices(
    val suffixes: Set<String>,
) {
    private val lower = suffixes.map { it.lowercase().trimStart('.') }.toSet()

    fun isKnown(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h in lower) return true
        var dot = h.indexOf('.')
        while (dot >= 0) {
            if (h.substring(dot + 1) in lower) return true
            dot = h.indexOf('.', dot + 1)
        }
        return false
    }

    @Serializable
    private data class Doc(
        val description: String = "",
        val suffixes: List<String>,
    )

    companion object {
        const val ASSET = "known_services.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): KnownServices = KnownServices(json.decodeFromString(Doc.serializer(), text).suffixes.toSet())
    }
}
