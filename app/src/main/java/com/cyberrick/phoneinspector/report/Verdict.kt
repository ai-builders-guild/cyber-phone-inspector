package com.cyberrick.phoneinspector.report

import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.rules.Finding
import com.cyberrick.phoneinspector.rules.Severity

/** The one verdict line and what it does not cover. Compromised only when at least one high finding exists. */
data class Verdict(
    val word: String,
    val uncovered: List<CheckResult>,
    val nextStep: String,
) {
    companion object {
        const val NOTHING_TO_DO = "Nothing to do. Rescan after installing anything new."

        fun of(
            findings: List<Finding>,
            checks: List<CheckResult>,
        ): Verdict {
            val sorted = findings.sortedWith(compareBy({ -it.severity.rank }, { it.priority }, { it.packageName }, { it.ruleId }))
            return Verdict(
                word = if (sorted.any { it.severity == Severity.HIGH }) "compromised" else "clean",
                uncovered = checks.filter { it.status == CheckStatus.NOT_CHECKED },
                nextStep = sorted.firstOrNull()?.remediation ?: NOTHING_TO_DO,
            )
        }
    }
}
