package com.cyberrick.phoneinspector.scan

import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.rules.Finding

/** Everything a finished scan produces. Pure data; the UI and the exporter read it. */
data class ScanResult(
    val evidence: Evidence,
    val findings: List<Finding>,
    val report: String,
) {
    val verdictLine: String get() = report.lineSequence().firstOrNull { it.startsWith("VERDICT: ") } ?: "VERDICT: unknown"
}
