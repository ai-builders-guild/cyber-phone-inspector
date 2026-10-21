package com.cyberrick.phoneinspector.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The bundled indicator snapshot: known stalkerware and spyware package ids, domains, signing certs, IPs. */
@Serializable
data class IocSet(
    val generatedAt: String = "",
    val sources: List<String> = emptyList(),
    val packages: Set<String>,
    val domains: Set<String>,
    /** "sha1:<hex>" or "sha256:<hex>", lower case. */
    val certs: Set<String>,
    val ips: Set<String> = emptySet(),
    /** Which published lists name each package, so the report can say "listed by ...". */
    val packageSources: Map<String, List<String>> = emptyMap(),
) {
    companion object {
        const val ASSET = "snapshot.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): IocSet = json.decodeFromString(serializer(), text)
    }
}

class IocMatcher(
    set: IocSet,
) {
    private val packages = set.packages
    private val domains = set.domains.map { it.lowercase() }.toSet()
    private val certs = set.certs.map { it.lowercase() }.toSet()
    private val ips = set.ips
    private val packageSources = set.packageSources

    fun packageHit(packageName: String): Boolean = packageName in packages

    fun certHit(certHashes: List<String>): Boolean = certHashes.any { it.lowercase() in certs }

    /** A host hits when it equals an indicator or is a subdomain of one. */
    fun domainHit(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h in domains) return true
        var dot = h.indexOf('.')
        while (dot >= 0) {
            if (h.substring(dot + 1) in domains) return true
            dot = h.indexOf('.', dot + 1)
        }
        return false
    }

    fun ipHit(ip: String): Boolean = ip in ips

    fun sourcesFor(packageName: String): List<String> = packageSources[packageName].orEmpty()
}
