package com.cyberrick.phoneinspector.probe

import android.content.Context
import android.util.Log
import com.cyberrick.phoneinspector.collect.AndroidPackageSource
import com.cyberrick.phoneinspector.collect.AppsCollector
import com.cyberrick.phoneinspector.collect.SystemPackages
import com.cyberrick.phoneinspector.core.model.InstallSource

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
}
