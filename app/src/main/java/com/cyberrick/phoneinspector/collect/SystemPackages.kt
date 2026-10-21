package com.cyberrick.phoneinspector.collect

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The bundled list of packages that legitimately carry system style names. */
object SystemPackages {
    const val ASSET = "system_packages.json"

    @Serializable
    private data class Doc(
        val description: String = "",
        val generatedAt: String = "",
        val packages: List<String>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Set<String> = json.decodeFromString(Doc.serializer(), text).packages.toSet()
}
