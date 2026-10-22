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
    /** Findings before the kept list was applied, so a keep or un keep re renders without a rescan. */
    val rawFindings: List<Finding> = findings,
    /** The "what this scan touched" lines without the kept app lines. */
    val touched: List<String> = emptyList(),
) {
    val verdictLine: String get() = report.lineSequence().firstOrNull { it.startsWith("VERDICT: ") } ?: "VERDICT: unknown"
}
