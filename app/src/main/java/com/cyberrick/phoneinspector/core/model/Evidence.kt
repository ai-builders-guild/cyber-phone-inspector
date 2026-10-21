package com.cyberrick.phoneinspector.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

/** Thrown when evidence JSON cannot be read. Names the source and, when known, the field. */
class EvidenceParseError(
    val sourceName: String,
    val field: String?,
    cause: Throwable,
) : Exception("Cannot parse evidence from $sourceName${field?.let { " at field $it" } ?: ""}: ${cause.message}", cause)

/**
 * The envelope every collector writes into and every rule reads from. Immutable; [fromJson] returns
 * a normalized copy with duplicate package ids flagged.
 */
@Serializable
data class Evidence(
    val schemaVersion: Int = 1,
    val scenario: String? = null,
    val device: DeviceInfo,
    val collectedAt: Long,
    val checks: List<CheckResult>,
    val apps: List<AppRecord> = emptyList(),
    val controls: List<ControlRecord> = emptyList(),
    val permissions: List<PermissionRecord> = emptyList(),
    val persistence: List<PersistenceRecord> = emptyList(),
    val flows: List<FlowRecord> = emptyList(),
    val iocSnapshotDate: String? = null,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    /** Returns a copy where every app sharing a package id with another is marked [AppRecord.duplicate]. */
    fun withDuplicatesFlagged(): Evidence {
        val counts = apps.groupingBy { it.packageName }.eachCount()
        val flagged = apps.map { app -> app.copy(duplicate = (counts[app.packageName] ?: 0) > 1) }
        return if (flagged == apps) this else copy(apps = flagged)
    }

    companion object {
        private val json =
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = false
            }

        fun fromJson(
            sourceName: String,
            text: String,
        ): Evidence =
            try {
                json.decodeFromString(serializer(), text).withDuplicatesFlagged()
            } catch (e: SerializationException) {
                throw EvidenceParseError(sourceName, fieldFromMessage(e.message), e)
            } catch (e: IllegalArgumentException) {
                throw EvidenceParseError(sourceName, fieldFromMessage(e.message), e)
            }

        fun load(file: File): Evidence = fromJson(file.name, file.readText(Charsets.UTF_8))

        /** kotlinx reports "... at path: $.apps[0].firstInstallAt"; keep the last segment as the field name. */
        private fun fieldFromMessage(message: String?): String? =
            message
                ?.let { Regex("""path: \$[^\s]*""").find(it)?.value }
                ?.substringAfterLast('.')
                ?.substringBefore('[')
                ?.takeIf { it.isNotEmpty() && it != "$" }
    }
}
