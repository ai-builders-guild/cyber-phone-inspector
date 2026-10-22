package com.cyberrick.phoneinspector.keep

import com.cyberrick.phoneinspector.rules.Bucket
import com.cyberrick.phoneinspector.rules.Finding
import com.cyberrick.phoneinspector.rules.Labels
import com.cyberrick.phoneinspector.rules.Severity
import java.time.Instant
import java.time.ZoneOffset

/**
 * Applies the kept list to a scan's findings before the report is rendered, so the screens and the report agree.
 * A kept app's accepted findings collapse to one low note that still names the app. Anything new, or stronger than
 * what was accepted, comes back in full. Nothing is ever hidden.
 */
object KeepTransform {
    const val KEPT_RULE = "kept_by_owner"

    fun apply(
        findings: List<Finding>,
        kept: List<KeptApp>,
    ): List<Finding> {
        if (kept.isEmpty()) return findings
        val byPackage = kept.associateBy { it.packageName }
        val out = ArrayList<Finding>(findings.size)
        val noted = LinkedHashSet<String>()
        for (f in findings) {
            val k = f.packageName?.let { byPackage[it] }
            if (k == null || f.ruleId !in k.ruleIds || f.severity.rank > k.worst.rank) {
                out += f
                continue
            }
            if (noted.add(k.packageName)) out += note(f, k)
        }
        return out.sortedWith(compareBy({ -it.severity.rank }, { it.priority }, { it.packageName }, { it.ruleId }))
    }

    private fun note(
        first: Finding,
        kept: KeptApp,
    ): Finding =
        Finding(
            ruleId = KEPT_RULE,
            check = first.check,
            packageName = kept.packageName,
            severity = Severity.LOW,
            priority = 900,
            bucket = Bucket.SAFE,
            summary = "kept by you",
            reason = "You marked ${kept.packageName} as yours on ${date(
                kept.keptAt,
            )}. Its earlier findings (${kept.ruleIds.joinToString(", ")}) are listed here as a note and not counted.",
            remediation = "If you no longer recognise this app, remove it from your kept list and scan again.",
            evidenceRef = first.evidenceRef,
            label = Labels.NOTE,
            cardText = "You marked this app as yours on ${date(kept.keptAt)}.",
        )

    /** Lines for the report's "what this scan touched" section. */
    fun touchedLines(
        kept: List<KeptApp>,
        labels: Map<String, String>,
    ): List<String> =
        kept.sortedBy { it.packageName }.map { k ->
            val name = labels[k.packageName]?.takeIf { it.isNotBlank() && it != k.packageName }
            val who = if (name != null) "$name (${k.packageName})" else k.packageName
            "You marked $who as yours on ${date(k.keptAt)}; its findings are not counted."
        }

    private fun date(at: Long): String =
        Instant
            .ofEpochMilli(at)
            .atOffset(ZoneOffset.UTC)
            .toLocalDate()
            .toString()
}
