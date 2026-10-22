package com.cyberrick.phoneinspector.keep

import com.cyberrick.phoneinspector.rules.Severity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** An app the owner marked as theirs, with the rules that were firing and the worst severity they accepted. */
@Serializable
data class KeptApp(
    val packageName: String,
    val ruleIds: List<String>,
    val worst: Severity = Severity.MEDIUM,
    val keptAt: Long,
)

/** The kept list, one JSON file in the app's private storage. Nothing here leaves the phone. */
class KeepStore(
    dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val file = File(dir, FILE)

    fun all(): List<KeptApp> =
        if (file.exists()) {
            try {
                json.decodeFromString(Doc.serializer(), file.readText(Charsets.UTF_8)).kept
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

    fun keep(
        packageName: String,
        ruleIds: List<String>,
        worst: Severity = Severity.MEDIUM,
    ) {
        val others = all().filterNot { it.packageName == packageName }
        write(others + KeptApp(packageName, ruleIds.distinct(), worst, clock()))
    }

    fun unkeep(packageName: String) = write(all().filterNot { it.packageName == packageName })

    private fun write(kept: List<KeptApp>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(Doc.serializer(), Doc(kept.sortedBy { it.packageName })), Charsets.UTF_8)
    }

    @Serializable
    private data class Doc(
        val kept: List<KeptApp> = emptyList(),
    )

    companion object {
        const val FILE = "kept_apps.json"
        private val json =
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            }
    }
}
