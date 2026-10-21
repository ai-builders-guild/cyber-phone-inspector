package com.cyberrick.phoneinspector.collect

import com.cyberrick.phoneinspector.core.model.AppRecord
import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.InstallSource

/**
 * Apps check: full inventory with install source, hidden launcher and disguised system name detection.
 * Pure: everything Android lives behind [PackageSource]. Never aborts the inventory on one package.
 */
class AppsCollector(
    private val source: PackageSource,
    private val knownSystemPackages: Set<String>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun collect(): CollectorResult<AppRecord> {
        val collectedAt = clock()
        val packages =
            try {
                source.installedPackages()
            } catch (e: Exception) {
                return CollectorResult(
                    records = emptyList(),
                    check =
                        CheckResult(
                            CheckKind.APPS,
                            CheckStatus.NOT_CHECKED,
                            "Inventory failed: ${e.javaClass.simpleName}: ${e.message}",
                        ),
                )
            }
        val launchers = source.launcherPackages()
        val records = packages.map { toRecord(it, it.packageName in launchers, collectedAt) }.sortedBy { it.packageName }
        return CollectorResult(records, CheckResult(CheckKind.APPS, CheckStatus.CHECKED))
    }

    private fun toRecord(
        raw: RawPackage,
        hasLauncherIcon: Boolean,
        collectedAt: Long,
    ): AppRecord {
        val installer = if (raw.isSystem) null else runCatching { source.installerOf(raw.packageName) }.getOrNull()
        val installSource =
            installSourceOf(
                raw.packageName,
                raw.isSystem,
                installer,
                lookupFailed = !raw.isSystem && installer == null,
            )
        return AppRecord(
            packageName = raw.packageName,
            label = raw.label,
            versionName = raw.versionName,
            versionCode = raw.versionCode,
            firstInstallAt = raw.firstInstallTime,
            lastUpdateAt = raw.lastUpdateTime,
            installerPackage = installer,
            installSource = installSource,
            signingCertSha256 = raw.signingCertSha256,
            isSystem = raw.isSystem,
            hasLauncherIcon = hasLauncherIcon,
            enabled = raw.enabled,
            hidden = !raw.isSystem && !hasLauncherIcon,
            disguised = isDisguised(raw, installSource),
            category = categoryName(raw.category),
            api = API,
            collectedAt = collectedAt,
        )
    }

    private fun installSourceOf(
        packageName: String,
        isSystem: Boolean,
        installer: String?,
        lookupFailed: Boolean,
    ): InstallSource =
        when {
            isSystem -> InstallSource.SYSTEM
            packageName.startsWith(WEBAPK_PREFIX) -> InstallSource.WEB_APP
            installer == null || lookupFailed -> InstallSource.UNKNOWN
            installer == PLAY_STORE -> InstallSource.PLAY_STORE
            installer in VENDOR_STORES -> InstallSource.VENDOR_STORE
            else -> InstallSource.SIDELOADED
        }

    /**
     * A user installed app whose label reads like a system component, not on the known system list.
     * Play Store installs are exempt: Play policy forbids impersonating system apps, and names like
     * "Sync for Reddit" are common there.
     */
    private fun isDisguised(
        raw: RawPackage,
        installSource: InstallSource,
    ): Boolean =
        !raw.isSystem &&
            installSource != InstallSource.PLAY_STORE &&
            raw.packageName !in knownSystemPackages &&
            SYSTEM_STYLE_LABEL.containsMatchIn(raw.label)

    companion object {
        const val API = "PackageManager.getInstalledPackages"
        private const val PLAY_STORE = "com.android.vending"
        private const val WEBAPK_PREFIX = "org.chromium.webapk."
        private val VENDOR_STORES =
            setOf(
                "com.sec.android.app.samsungapps",
                "com.amazon.venezia",
                "com.huawei.appmarket",
                "com.xiaomi.market",
                "com.oppo.market",
                "com.heytap.market",
                "com.vivo.appstore",
                "com.oneplus.store",
                // Carrier and OEM preload or module installers seen on Rick's Galaxy S24 Ultra (Phase 4 probe).
                "com.LogiaGroup.LogiaDeck",
                "com.samsung.android.goodlock",
                "com.sec.android.preloadinstaller",
                "com.samsung.android.app.omcagent",
            )
        private val SYSTEM_STYLE_LABEL = Regex("""\b(system|update|sync|service|settings|security)\b""", RegexOption.IGNORE_CASE)

        /** ApplicationInfo.CATEGORY_* constants by value; anything else is null. */
        private val CATEGORY_NAMES =
            mapOf(
                0 to "GAME",
                1 to "AUDIO",
                2 to "VIDEO",
                3 to "IMAGE",
                4 to "SOCIAL",
                5 to "NEWS",
                6 to "MAPS",
                7 to "PRODUCTIVITY",
                8 to "ACCESSIBILITY",
            )

        fun categoryName(category: Int?): String? = category?.let { CATEGORY_NAMES[it] }
    }
}
