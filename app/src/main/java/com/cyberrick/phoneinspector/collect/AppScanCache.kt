package com.cyberrick.phoneinspector.collect

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Remembers each package's APK hash and tracker scan keyed by its version and update time, so a rescan only reads
 * apps that changed. One JSON file in private storage. The hash never depends on a list, so it is reused as long as
 * the package is unchanged; the tracker result is reused only while the tracker catalog is the same one.
 */
class AppScanCache(
    dir: File,
) {
    @Serializable
    data class Entry(
        val sha256: String? = null,
        val catalogDate: String = "",
        val trackers: List<String> = emptyList(),
    )

    @Serializable
    private data class Doc(
        val entries: Map<String, Entry> = emptyMap(),
    )

    private val file = File(dir, FILE)
    private var doc: Doc = load()

    fun key(
        packageName: String,
        versionCode: Long,
        lastUpdateAt: Long,
    ): String = "$packageName|$versionCode|$lastUpdateAt"

    fun hash(key: String): String? = doc.entries[key]?.sha256

    /** Cached tracker names, or null when the package was not scanned with this catalog at this version. */
    fun trackers(
        key: String,
        catalogDate: String,
    ): List<String>? = doc.entries[key]?.takeIf { it.catalogDate == catalogDate }?.trackers

    /** Replaces the cache; the caller passes only the packages still installed. */
    fun put(entries: Map<String, Entry>) {
        doc = Doc(entries)
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Doc.serializer(), doc), Charsets.UTF_8)
        } catch (e: Exception) {
            // a cache that cannot be written only costs time on the next scan
        }
    }

    private fun load(): Doc =
        try {
            if (file.isFile) json.decodeFromString(Doc.serializer(), file.readText(Charsets.UTF_8)) else Doc()
        } catch (e: Exception) {
            Doc()
        }

    companion object {
        const val FILE = "app_scan_cache.json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
