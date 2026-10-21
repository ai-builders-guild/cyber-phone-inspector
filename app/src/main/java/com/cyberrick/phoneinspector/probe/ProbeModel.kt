package com.cyberrick.phoneinspector.probe

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Device facts that are not identifying: make, model, OS version. Never a serial or IMEI. */
@Serializable
data class ProbeDevice(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
)

/**
 * One API probed, summarised to numbers and booleans only.
 * [note] is free text but may never name a package, so the report stays shareable.
 */
@Serializable
data class ProbeResult(
    val api: String,
    val count: Int? = null,
    val fieldPresent: Boolean? = null,
    val elapsedMs: Long? = null,
    val error: String? = null,
    val note: String? = null,
) {
    init {
        require(note == null || !PACKAGE_LIKE.containsMatchIn(note)) {
            "note must not contain a package id: $api"
        }
    }

    val errorClass: String?
        get() = error?.substringBefore(':')?.trim()

    private companion object {
        /** Two or more dotted lowercase segments is the shape of an Android package id. */
        val PACKAGE_LIKE = Regex("""\b[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+\b""")
    }
}

@Serializable
data class ProbeRun(
    val device: ProbeDevice,
    val results: List<ProbeResult>,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json =
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }

        fun fromJson(text: String): ProbeRun = json.decodeFromString(serializer(), text)
    }
}
