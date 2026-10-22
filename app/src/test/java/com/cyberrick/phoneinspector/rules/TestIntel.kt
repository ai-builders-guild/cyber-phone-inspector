package com.cyberrick.phoneinspector.rules

import com.cyberrick.phoneinspector.collect.TrackerCatalog
import java.io.File

/** The bundled tracker catalog plus test only malware and host entries, so the fixtures can exercise the v1.1 rules. */
object TestIntel {
    const val TEST_HASH = "abababababababababababababababababababababababababababababababab"

    fun load(moduleDir: File): ThreatIntel =
        ThreatIntel(
            malware = MalwareHashes(mapOf(TEST_HASH to MalwareHashes.Entry("Cerberus", listOf("banker", "android")))),
            hosts = MaliciousHosts(domains = setOf("drop.bad.example"), ips = setOf("203.0.113.9")),
            trackers = TrackerCatalog.parse(File(moduleDir, "src/main/assets/${TrackerCatalog.ASSET}").readText(Charsets.UTF_8)),
        )
}
