package com.cyberrick.phoneinspector.feeds

import android.content.Context
import com.cyberrick.phoneinspector.collect.TrackerCatalog
import com.cyberrick.phoneinspector.rules.MaliciousHosts
import com.cyberrick.phoneinspector.rules.MalwareHashes
import com.cyberrick.phoneinspector.rules.ThreatIntel
import java.io.File

/**
 * Where the threat lists live on the phone. A refreshed copy under the app's private files directory wins over the
 * copy bundled in the APK; a copy that fails to parse is ignored and the bundled one is used instead.
 */
class FeedFiles(
    private val context: Context,
) {
    private val dir = File(context.filesDir, DIR)

    fun read(asset: String): String {
        val local = File(dir, File(asset).name)
        if (local.isFile) {
            try {
                return local.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                // fall through to the bundled copy
            }
        }
        return context.assets
            .open(asset)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    fun threatIntel(): ThreatIntel =
        ThreatIntel(
            malware = parseOr(MalwareHashes.ASSET, MalwareHashes::parse) { MalwareHashes(emptyMap(), skipped = "list unreadable") },
            hosts =
                parseOr(
                    MaliciousHosts.ASSET,
                    MaliciousHosts::parse,
                ) { MaliciousHosts(emptySet(), emptySet(), skipped = "list unreadable") },
            trackers = parseOr(TrackerCatalog.ASSET, TrackerCatalog::parse) { TrackerCatalog(emptyList()) },
        )

    private fun <T> parseOr(
        asset: String,
        parse: (String) -> T,
        fallback: () -> T,
    ): T =
        try {
            parse(read(asset))
        } catch (e: Exception) {
            try {
                parse(
                    context.assets
                        .open(asset)
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() },
                )
            } catch (e2: Exception) {
                fallback()
            }
        }

    companion object {
        const val DIR = "feeds"
    }
}
