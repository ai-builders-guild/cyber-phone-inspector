package com.cyberrick.phoneinspector.collect

import java.io.InputStream
import java.security.MessageDigest

data class ApkRef(
    val packageName: String,
    val path: String,
    val isSplit: Boolean,
)

interface ApkSource {
    fun nonSystemApks(): List<ApkRef>

    /** Null when the file cannot be opened (some OEMs restrict other apps' package files). */
    fun open(ref: ApkRef): InputStream?
}

data class ApkHashResult(
    val hashes: Map<String, String?>,
    val unreadable: Int,
    val splitPackages: Set<String>,
)

/** SHA-256 of each non system app's base APK, for the known malware list. Read only; streams, never loads a whole APK. */
class ApkHashCollector(
    private val source: ApkSource,
) {
    fun collect(): ApkHashResult {
        val hashes = LinkedHashMap<String, String?>()
        var unreadable = 0
        val splits = mutableSetOf<String>()
        for (ref in source.nonSystemApks()) {
            if (ref.isSplit) splits += ref.packageName
            val stream =
                try {
                    source.open(ref)
                } catch (e: Exception) {
                    null
                }
            if (stream == null) {
                hashes[ref.packageName] = null
                unreadable++
                continue
            }
            hashes[ref.packageName] =
                try {
                    stream.use { sha256(it) }
                } catch (e: Exception) {
                    unreadable++
                    null
                }
        }
        return ApkHashResult(hashes, unreadable, splits)
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
