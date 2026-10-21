package com.cyberrick.phoneinspector.net

import com.cyberrick.phoneinspector.core.model.FlowRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** What one monitor run produced. Saved by the service, read by the next scan. */
@Serializable
data class MonitorResult(
    val startedAt: Long,
    val endedAt: Long,
    val windowMinutes: Int,
    val flows: List<FlowRecord>,
    val lookups: Int,
    val medianLatencyMs: Long,
    val droppedMalformed: Int,
    val resetsSent: Int,
)

/** App private file holding the latest monitor result. Atomic write via temp file and rename. */
class FlowStore(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val file = File(dir, FILE_NAME)
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun save(result: MonitorResult) {
        dir.mkdirs()
        val tmp = File(dir, "$FILE_NAME.tmp")
        tmp.writeText(json.encodeToString(MonitorResult.serializer(), result), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "cannot replace $file" }
        }
    }

    fun load(): MonitorResult? =
        if (file.exists()) {
            try {
                json.decodeFromString(MonitorResult.serializer(), file.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

    fun isFresh(maxAgeMs: Long): Boolean = load()?.let { clock() - it.endedAt <= maxAgeMs } ?: false

    companion object {
        const val FILE_NAME = "monitor_latest.json"
        const val FRESH_MS = 24 * 3_600_000L
    }
}
