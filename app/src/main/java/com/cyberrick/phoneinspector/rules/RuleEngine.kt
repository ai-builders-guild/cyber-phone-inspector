package com.cyberrick.phoneinspector.rules

import com.cyberrick.phoneinspector.core.model.Evidence

/** Evaluates every rule against every package's facts. Deterministic: sorted by severity, package, rule. */
class RuleEngine(
    private val rules: RuleSet,
    private val ioc: IocMatcher,
) {
    fun evaluate(evidence: Evidence): List<Finding> {
        val facts = Facts.perPackage(evidence, ioc)
        return facts
            .flatMap { (pkg, f) ->
                rules.rules.filter { it.condition.evaluate(f) }.map { rule -> finding(rule, pkg, evidence) }
            }.sortedWith(compareBy({ -it.severity.rank }, { it.packageName }, { it.ruleId }))
    }

    private fun finding(
        rule: Rule,
        pkg: String,
        evidence: Evidence,
    ): Finding =
        Finding(
            ruleId = rule.id,
            packageName = pkg,
            severity = rule.severity,
            bucket = rule.bucket,
            reason = rule.reason.replace("{package}", pkg),
            remediation = rule.remediation.replace("{package}", pkg),
            evidenceRef = rule.evidence.flatMap { refs(it, pkg, evidence) }.ifEmpty { listOf("package:$pkg") },
        )

    private fun refs(
        kind: String,
        pkg: String,
        e: Evidence,
    ): List<String> =
        when (kind) {
            "app" ->
                e.apps
                    .filter { it.packageName == pkg }
                    .map { "app:$pkg" }
                    .distinct()
            "control" -> e.controls.filter { it.packageName == pkg }.map { "control:${it.kind}:$pkg" }
            "permission" -> e.permissions.filter { it.packageName == pkg && it.granted }.map { "permission:${it.group}:$pkg" }
            "persistence" -> e.persistence.filter { it.packageName == pkg }.map { "persistence:$pkg" }
            "flow" ->
                e.flows
                    .filter { it.packageName == pkg }
                    .mapNotNull { it.host }
                    .distinct()
                    .take(5)
                    .map { "flow:$it:$pkg" }
            else -> emptyList()
        }
}
