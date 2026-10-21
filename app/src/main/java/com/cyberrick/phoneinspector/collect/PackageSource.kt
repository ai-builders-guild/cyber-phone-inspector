package com.cyberrick.phoneinspector.collect

/** Raw facts about one installed package, as the platform reports them. No interpretation here. */
data class RawPackage(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isSystem: Boolean,
    val enabled: Boolean,
    val category: Int?,
    val signingCertSha256: List<String>,
    /** Installer as the platform knows it; null when unknown. May be ignored by [PackageSource.installerOf]. */
    val installerPackage: String?,
)

/** The slice of PackageManager the Apps collector needs. Implemented by the Android adapter and by test fakes. */
interface PackageSource {
    fun installedPackages(): List<RawPackage>

    fun launcherPackages(): Set<String>

    /** May throw for a package that vanished between calls; the collector records UNKNOWN and continues. */
    fun installerOf(packageName: String): String?
}
