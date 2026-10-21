package com.cyberrick.phoneinspector.probe

import android.content.Context
import android.util.Log
import com.cyberrick.phoneinspector.collect.AndroidControlSource
import com.cyberrick.phoneinspector.collect.AndroidPackageSource
import com.cyberrick.phoneinspector.collect.AndroidPermissionSource
import com.cyberrick.phoneinspector.collect.AndroidPersistenceSource
import com.cyberrick.phoneinspector.collect.AppsCollector
import com.cyberrick.phoneinspector.collect.ControlCollector
import com.cyberrick.phoneinspector.collect.PermissionExpectations
import com.cyberrick.phoneinspector.collect.PermissionsCollector
import com.cyberrick.phoneinspector.collect.PersistenceCollector
import com.cyberrick.phoneinspector.collect.SystemPackages
import com.cyberrick.phoneinspector.core.model.ControlKind
import com.cyberrick.phoneinspector.core.model.InstallSource
import com.cyberrick.phoneinspector.core.model.SensitiveGroup

private const val TAG = "CPIProbe"

/** Runs real collectors on the device and summarises to counts only, for the per phase hardware gates. */
class CollectorProbe(
    private val context: Context,
) {
    fun apps(): List<ProbeResult> {
        val systemList =
            context.assets
                .open(
                    SystemPackages.ASSET,
                ).bufferedReader(Charsets.UTF_8)
                .use { SystemPackages.parse(it.readText()) }
        val start = System.nanoTime()
        val result = AppsCollector(AndroidPackageSource(context), systemList).collect()
        val ms = (System.nanoTime() - start) / 1_000_000
        val r = result.records
        // Installer package ids are system components, not the owner's apps. Logcat only, never a file.
        r
            .filter { !it.isSystem }
            .groupingBy { it.installerPackage ?: "null" }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .forEach { Log.i(TAG, "installer ${it.key} -> ${it.value} packages") }

        fun row(
            name: String,
            count: Int,
            note: String,
        ) = ProbeResult(api = "AppsCollector.$name", count = count, fieldPresent = true, elapsedMs = ms, note = note)
        return listOf(
            row("records", r.size, "status ${result.check.status}"),
            row("system", r.count { it.isSystem }, "isSystem"),
            row("launcher", r.count { it.hasLauncherIcon }, "hasLauncherIcon"),
            row("hidden", r.count { it.hidden }, "non system without launcher icon"),
            row("hiddenPlay", r.count { it.hidden && it.installSource == InstallSource.PLAY_STORE }, "hidden, Play installed"),
            row("hiddenVendor", r.count { it.hidden && it.installSource == InstallSource.VENDOR_STORE }, "hidden, vendor store"),
            row("hiddenSideloaded", r.count { it.hidden && it.installSource == InstallSource.SIDELOADED }, "hidden, sideloaded"),
            row("hiddenUnknown", r.count { it.hidden && it.installSource == InstallSource.UNKNOWN }, "hidden, unknown source"),
            row("disguised", r.count { it.disguised }, "system style label, not Play, not on system list"),
            row("playStore", r.count { it.installSource == InstallSource.PLAY_STORE }, "installSource"),
            row("vendorStore", r.count { it.installSource == InstallSource.VENDOR_STORE }, "installSource"),
            row("sideloaded", r.count { it.installSource == InstallSource.SIDELOADED }, "installSource"),
            row("unknownSource", r.count { it.installSource == InstallSource.UNKNOWN }, "installSource, non system"),
            row("withCerts", r.count { it.signingCertSha256.isNotEmpty() }, "signing cert hashes present"),
            row("withCategory", r.count { it.category != null }, "category mapped"),
        )
    }

    fun control(): List<ProbeResult> {
        val start = System.nanoTime()
        val result = ControlCollector(AndroidControlSource(context)).collect()
        val ms = (System.nanoTime() - start) / 1_000_000

        fun row(
            name: String,
            count: Int,
            note: String,
        ) = ProbeResult(api = "ControlCollector.$name", count = count, fieldPresent = true, elapsedMs = ms, note = note)
        return listOf(row("records", result.records.size, "status ${result.check.status}; reason ${result.check.reason ?: "none"}")) +
            ControlKind.entries.map { kind -> row(kind.name, result.records.count { it.kind == kind }, "records of this kind") } +
            row(
                "withInstallBound",
                result.records.count {
                    it.sinceAtLatest < result.records.first().collectedAt
                },
                "sinceAtLatest earlier than collection",
            )
    }

    fun permissions(): List<ProbeResult> {
        val expectations =
            context.assets.open(PermissionExpectations.ASSET).bufferedReader(Charsets.UTF_8).use {
                PermissionExpectations.parse(it.readText())
            }
        val start = System.nanoTime()
        val result = PermissionsCollector(AndroidPermissionSource(context), expectations).collect()
        val ms = (System.nanoTime() - start) / 1_000_000
        val r = result.records

        fun row(
            name: String,
            count: Int,
            note: String,
        ) = ProbeResult(api = "PermissionsCollector.$name", count = count, fieldPresent = true, elapsedMs = ms, note = note)
        return listOf(
            row("records", r.size, "status ${result.check.status}"),
            row("apps", r.map { it.packageName }.distinct().size, "distinct packages"),
            row("uncertain", r.count { it.uncertain }, "flags null"),
        ) +
            SensitiveGroup.entries.flatMap { g ->
                listOf(
                    row("${g.name}.granted", r.count { it.group == g && it.granted }, "granted"),
                    row(
                        "${g.name}.unexpected",
                        r.count { it.group == g && it.unexpected },
                        "granted outside expected categories, non system",
                    ),
                )
            }
    }

    fun persistence(): List<ProbeResult> {
        val start = System.nanoTime()
        val result = PersistenceCollector(AndroidPersistenceSource(context)).collect()
        val ms = (System.nanoTime() - start) / 1_000_000
        val r = result.records

        fun row(
            name: String,
            count: Int,
            note: String,
        ) = ProbeResult(api = "PersistenceCollector.$name", count = count, fieldPresent = true, elapsedMs = ms, note = note)
        return listOf(
            row("records", r.size, "status ${result.check.status}"),
            row("bootReceiver", r.count { it.bootReceiver }, "relaunch on boot"),
            row("withForegroundService", r.count { it.foregroundServiceMinutes7d > 0 }, "any foreground service time in 7 d"),
            row("over6h", r.count { it.foregroundServiceMinutes7d > PersistenceCollector.HEAVY_MINUTES }, "more than 6 h in 7 d"),
            row("backgroundHeavy", r.count { it.backgroundHeavy }, "over 6 h, non system, outside always on categories"),
            row("droppedEvents", r.sumOf { it.droppedEvents }, "unmatched stop events"),
            row("maxMinutes", r.maxOfOrNull { it.foregroundServiceMinutes7d }?.toInt() ?: 0, "largest 7 d total"),
        )
    }
}
