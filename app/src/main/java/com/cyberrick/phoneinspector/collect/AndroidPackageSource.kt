package com.cyberrick.phoneinspector.collect

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** PackageManager adapter. Read only. Exercised on devices in the probe and instrumented tests, not on the JVM. */
class AndroidPackageSource(
    context: Context,
) : PackageSource {
    private val pm: PackageManager = context.packageManager

    override fun installedPackages(): List<RawPackage> = pm.getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES).map { toRaw(it) }

    override fun launcherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    }

    @Suppress("DEPRECATION") // getInstallerPackageName is the only option below API 30
    override fun installerOf(packageName: String): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            pm.getInstallerPackageName(packageName)
        }

    private fun toRaw(info: PackageInfo): RawPackage {
        val app = info.applicationInfo
        val flags = app?.flags ?: 0
        val isSystem = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return RawPackage(
            packageName = info.packageName,
            label = app?.loadLabel(pm)?.toString() ?: info.packageName,
            versionName = info.versionName,
            versionCode = info.longVersionCode,
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
            isSystem = isSystem,
            enabled = app?.enabled ?: true,
            category = app?.category,
            signingCertSha256 = certHashes(info),
            installerPackage = null,
        )
    }

    private fun certHashes(info: PackageInfo): List<String> {
        val signing = info.signingInfo ?: return emptyList()
        val signatures = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return signatures.orEmpty().map { sig ->
            val digest = MessageDigest.getInstance("SHA-256") // not thread safe; one per call
            "sha256:" + digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
