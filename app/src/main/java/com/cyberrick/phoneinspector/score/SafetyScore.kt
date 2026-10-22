package com.cyberrick.phoneinspector.score

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.rules.Finding
import com.cyberrick.phoneinspector.rules.Severity

/**
 * The earned safety score. 100 minus 30 per app whose worst finding is high and 10 per app whose worst is medium,
 * floor 0. Lows cost nothing. A check that could not run (other than the optional network watch) caps it at 90,
 * so the number never claims more than was checked.
 */
data class SafetyScore(
    val value: Int,
    val cappedBy: List<CheckKind>,
    val watchNotRun: Boolean,
    val threatApps: Int,
    val reviewApps: Int,
) {
    companion object {
        const val HIGH_COST = 30
        const val MEDIUM_COST = 10
        const val CAP_WHEN_NOT_CHECKED = 90

        fun of(
            findings: List<Finding>,
            checks: List<CheckResult>,
        ): SafetyScore {
            val worstPerApp =
                findings
                    .groupBy { it.packageName ?: "rule:${it.ruleId}" }
                    .mapValues { (_, fs) -> fs.maxOf { it.severity.rank } }
            val threats = worstPerApp.count { it.value == Severity.HIGH.rank }
            val reviews = worstPerApp.count { it.value == Severity.MEDIUM.rank }
            val cappedBy = checks.filter { it.status == CheckStatus.NOT_CHECKED && it.check != CheckKind.NETWORK }.map { it.check }
            val watchNotRun = checks.any { it.check == CheckKind.NETWORK && it.status == CheckStatus.NOT_CHECKED }
            val earned = (100 - threats * HIGH_COST - reviews * MEDIUM_COST).coerceAtLeast(0)
            val value = if (cappedBy.isEmpty()) earned else minOf(earned, CAP_WHEN_NOT_CHECKED)
            return SafetyScore(value, cappedBy, watchNotRun, threats, reviews)
        }
    }
}
