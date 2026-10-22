package com.cyberrick.phoneinspector.report

import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.rules.Finding
import java.time.ZoneId

/** What the report says the scan touched. The UI fills this from the consents actually granted. */
object ScanTouched {
    const val INVENTORY =
        "Read the installed app list, permissions, device admin, accessibility and notification settings. " +
            "No consent needed; nothing changed."
    const val USAGE_ACCESS =
        "Usage access: read which apps ran foreground services in the last 7 days. " +
            "You granted this; revoke it any time in Settings."
    const val LOCAL_VPN =
        "Local VPN: observed DNS lookups for the monitor window. You granted this; it stopped when the scan ended. " +
            "No traffic was redirected or stored."
    val DEFAULT = listOf(INVENTORY, USAGE_ACCESS, LOCAL_VPN)

    /** v1.1: names the local lists and their dates. Nothing about this phone was sent anywhere to check them. */
    fun threatLists(intel: com.cyberrick.phoneinspector.rules.ThreatIntel): String {
        val malware =
            intel.malware.skipped?.let { "not included ($it)" } ?: "${intel.malware.entries.size} hashes dated ${intel.malware.generatedAt}"
        val hosts =
            intel.hosts.skipped?.let { "not included ($it)" }
                ?: "${intel.hosts.domains.size + intel.hosts.ips.size} hosts dated ${intel.hosts.generatedAt}"
        return "Checked every non system app against the lists stored on this phone: " +
            "${intel.trackers.trackers.size} tracker signatures (Exodus Privacy), malware hashes (MalwareBazaar): $malware, " +
            "malicious hosts (URLhaus, ThreatFox): $hosts. Nothing about this phone was sent anywhere."
    }
}

data class ReportInput(
    val evidence: Evidence,
    val findings: List<Finding>,
    val touched: List<String>,
    val localZone: ZoneId,
)
