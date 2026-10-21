package com.cyberrick.phoneinspector.rules

import com.cyberrick.phoneinspector.core.model.CheckKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Rules are data. The engine is generic. */
@Serializable
data class Rule(
    val id: String,
    /** The check (report section) this finding belongs to. */
    val check: CheckKind,
    /** Orders findings of equal severity; lower first. The top finding supplies the report's single next step. */
    val priority: Int = 100,
    val severity: Severity,
    val bucket: Bucket,
    /** Plain language, may contain {package}. */
    val reason: String,
    val remediation: String,
    /** Which record kinds back this finding: app, control, permission, persistence, flow. */
    val evidence: List<String>,
    val `when`: JsonElement,
) {
    val condition: Condition by lazy { Condition.fromElement(`when`) }
}

@Serializable
data class RuleSet(
    val version: Int = 1,
    val description: String = "",
    val rules: List<Rule>,
) {
    companion object {
        const val ASSET = "rules.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): RuleSet {
            val set = json.decodeFromString(serializer(), text)
            set.rules.forEach { it.condition } // fail at load time, not at scan time
            return set
        }
    }
}
