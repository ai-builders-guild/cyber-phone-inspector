package com.cyberrick.phoneinspector.rules

import com.cyberrick.phoneinspector.core.model.CheckKind
import kotlinx.serialization.Serializable

@Serializable
enum class Severity(
    val rank: Int,
) {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
}

/** Where the report lists the finding: the three lists from the brief. */
@Serializable
enum class Bucket { SAFE, WORTH_REMOVING, NEEDS_PROFESSIONAL }

/** One rule firing for one package. Everything the report needs, traceable to evidence records. */
@Serializable
data class Finding(
    val ruleId: String,
    val check: CheckKind,
    val packageName: String?,
    val severity: Severity,
    val priority: Int = 100,
    val bucket: Bucket,
    val summary: String,
    val reason: String,
    val remediation: String,
    val evidenceRef: List<String>,
)
