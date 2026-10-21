package com.cyberrick.phoneinspector.collect

import com.cyberrick.phoneinspector.core.model.SensitiveGroup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Which app categories plausibly need which sensitive permission. A heuristic by design: anything outside it is
 * "unexpected", which the report words as "looks odd", never as "malicious".
 */
data class PermissionExpectations(
    val byGroup: Map<SensitiveGroup, Set<String>>,
    /** Package id prefixes that are expected regardless of category (well known first party apps). */
    val familyPrefixes: Set<String>,
) {
    fun isExpected(
        group: SensitiveGroup,
        category: String?,
        packageName: String,
    ): Boolean = familyPrefixes.any { packageName.startsWith(it) } || (category != null && category in byGroup[group].orEmpty())

    @Serializable
    private data class Doc(
        val description: String = "",
        val byGroup: Map<String, List<String>>,
        val familyPrefixes: List<String> = emptyList(),
    )

    companion object {
        const val ASSET = "permission_expectations.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): PermissionExpectations {
            val doc = json.decodeFromString(Doc.serializer(), text)
            return PermissionExpectations(
                byGroup = doc.byGroup.map { (k, v) -> SensitiveGroup.valueOf(k) to v.toSet() }.toMap(),
                familyPrefixes = doc.familyPrefixes.toSet(),
            )
        }
    }
}
