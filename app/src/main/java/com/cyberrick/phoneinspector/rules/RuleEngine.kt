package com.cyberrick.phoneinspector.rules

import com.cyberrick.phoneinspector.core.model.Evidence

/** Evaluates every rule against every package's facts. Deterministic: sorted by severity, package, rule. */
class RuleEngine(
    private val rules: RuleSet,
    private val ioc: IocMatcher,
    private val known: KnownServices = KnownServices(emptySet()),
    private val intel: ThreatIntel = ThreatIntel.EMPTY,
) {
    fun evaluate(evidence: Evidence): List<Finding> {
        val facts = Facts.perPackage(evidence, ioc, known, intel)
        return facts
            .flatMap { (pkg, f) ->
                rules.rules.filter { it.condition.evaluate(f) }.map { rule -> finding(rule, pkg, evidence, f) }
            }.sortedWith(compareBy({ -it.severity.rank }, { it.priority }, { it.packageName }, { it.ruleId }))
    }

    private fun finding(
        rule: Rule,
        pkg: String,
        evidence: Evidence,
        facts: Map<String, Any?>,
    ): Finding =
        Finding(
            ruleId = rule.id,
            check = rule.check,
            packageName = pkg,
            severity = rule.severity,
            priority = rule.priority,
            bucket = rule.bucket,
            summary = rule.summary,
            reason = fill(rule.reason, pkg, facts),
            remediation = fill(rule.remediation, pkg, facts),
            evidenceRef = rule.evidence.flatMap { refs(it, pkg, evidence) }.ifEmpty { listOf("package:$pkg") },
            label = if (rule.severity == Severity.LOW) Labels.NOTE else rule.label,
            cardText = fill(rule.cardText, pkg, facts),
        )

    /** The placeholders rules may use; anything else is left as written. */
    private fun fill(
        template: String,
        pkg: String,
        facts: Map<String, Any?>,
    ): String =
        template
            .replace("{package}", pkg)
            .replace("{iocSources}", facts["iocSources"]?.toString().orEmpty())
            .replace("{beaconHost}", facts["net.beaconHost"]?.toString().orEmpty())
            .replace("{beaconInterval}", facts["net.beaconInterval"]?.toString().orEmpty())
            .replace("{bytesSentMb}", facts["net.bytesSentMb"]?.toString().orEmpty())
            .replace("{unrecognizedList}", facts["net.unrecognizedList"]?.toString().orEmpty())
            .replace("{malwareFamily}", facts["malwareFamily"]?.toString().orEmpty())
            .replace("{listedHost}", facts["maliciousHostName"]?.toString().orEmpty())
            .replace("{trackerCount}", facts["trackerCount"]?.toString().orEmpty())
            .replace("{trackerKits}", facts["trackerKits"]?.toString().orEmpty())
            .replace("{adTrackerCount}", facts["adTrackerCount"]?.toString().orEmpty())
            .replace("{trackerList}", facts["trackerList"]?.toString().orEmpty())

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
