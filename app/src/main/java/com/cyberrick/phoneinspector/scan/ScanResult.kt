package com.cyberrick.phoneinspector.scan

import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.keep.KeptApp
import com.cyberrick.phoneinspector.rules.Finding
import com.cyberrick.phoneinspector.score.SafetyScore

/** Everything a finished scan produces. Pure data; the UI and the exporter read it. Findings already have the kept list applied. */
data class ScanResult(
    val evidence: Evidence,
    val findings: List<Finding>,
    val report: String,
    val score: SafetyScore = SafetyScore.of(findings, evidence.checks),
    val kept: List<KeptApp> = emptyList(),
) {
    val verdictLine: String get() = report.lineSequence().firstOrNull { it.startsWith("VERDICT: ") } ?: "VERDICT: unknown"
}
