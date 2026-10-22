package com.cyberrick.phoneinspector.collect

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipFile

/** Reads other apps' installed package files. Read only: a package's public source path is world readable by design. */
class AndroidApkSource(
    context: Context,
) : ApkSource {
    private val pm: PackageManager = context.packageManager

    override fun nonSystemApks(): List<ApkRef> =
        pm.getInstalledPackages(0).mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            if (app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return@mapNotNull null
            val path = app.publicSourceDir ?: app.sourceDir ?: return@mapNotNull null
            ApkRef(info.packageName, path, !app.splitPublicSourceDirs.isNullOrEmpty())
        }

    override fun open(ref: ApkRef): InputStream? =
        try {
            FileInputStream(ref.path)
        } catch (e: Exception) {
            null
        }
}

/** The classes*.dex entries of each non system app's base APK, read one at a time and capped so a giant app cannot exhaust memory. */
class AndroidDexSource(
    context: Context,
    private val maxDexBytes: Long = MAX_DEX_BYTES,
) : DexSource {
    private val apks = AndroidApkSource(context)
    private val paths: Map<String, String> by lazy { apks.nonSystemApks().associate { it.packageName to it.path } }

    override fun nonSystemPackages(): List<String> = paths.keys.sorted()

    override fun dexFiles(packageName: String): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        forEachDex(packageName) {
            out += it.readBytes()
            true
        }
        return out
    }

    /** Streams each DEX entry straight out of the archive; nothing is buffered whole and the archive closes even on an early stop. */
    override fun forEachDex(
        packageName: String,
        block: (InputStream) -> Boolean,
    ) {
        val path = paths[packageName] ?: return
        ZipFile(path).use { zip ->
            val entries =
                zip
                    .entries()
                    .asSequence()
                    .filter {
                        DEX_NAME.matches(
                            it.name,
                        ) &&
                            it.size in 1..maxDexBytes
                    }.sortedBy { it.name }
                    .toList()
            for (entry in entries) {
                val go = zip.getInputStream(entry).use { block(it) }
                if (!go) return
            }
        }
    }

    companion object {
        const val MAX_DEX_BYTES = 256L * 1024 * 1024
        private val DEX_NAME = Regex("""classes\d*\.dex""")
    }
}
