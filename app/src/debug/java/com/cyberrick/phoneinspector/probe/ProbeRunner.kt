package com.cyberrick.phoneinspector.probe

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.system.OsConstants
import android.view.accessibility.AccessibilityManager
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Phase 2: call every data source PROJECT.md plans to use and record counts, field presence and timing.
 * Nothing here writes to the phone. Nothing here records the identity of any package.
 *
 * MissingPermission is suppressed on purpose: ACCESS_NETWORK_STATE is not on the PROJECT.md allowlist,
 * and the probe exists to record what the ConnectivityManager calls do without it.
 */
@SuppressLint("MissingPermission")
class ProbeRunner(
    private val context: Context,
) {
    private val pm: PackageManager = context.packageManager

    fun device(): ProbeDevice =
        ProbeDevice(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
        )

    fun runAll(): List<ProbeResult> =
        listOf(
            installedPackages(),
            installSource(),
            signingCertificates(),
            launcherActivities(),
            activeAdmins(),
            accessibilitySetting(),
            accessibilityManager(),
            notificationListeners(),
            permissionFlags(),
            appCategory(),
            bootReceivers(),
            usageAccessGranted(),
            usageEvents(),
            usageStats(),
            networkStats(),
            networkStatsMobile(),
            connectionOwnerUid(),
            connectionOwnerUidLoopbackTcp(),
            privateDns(),
            vpnPrepared(),
        )

    private fun installedPackages() =
        probe("PackageManager.getInstalledPackages") {
            val list = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            list.size to list.all { it.firstInstallTime > 0 }
        }

    private fun installSource() =
        probe("PackageManager.getInstallSourceInfo", note = "count = packages with a known installer") {
            val names = pm.getInstalledPackages(0).map { it.packageName }
            val known = names.count { installerOf(it) != null }
            known to (known > 0)
        }

    @Suppress("DEPRECATION")
    private fun installerOf(packageName: String): String? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                pm.getInstallerPackageName(packageName)
            }
        }.getOrNull()

    private fun signingCertificates() =
        probe("PackageManager.GET_SIGNING_CERTIFICATES", note = "count = packages with signingInfo") {
            val list = pm.getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES)
            list.count { it.signingInfo != null } to list.isNotEmpty()
        }

    private fun launcherActivities() =
        probe("PackageManager.queryIntentActivities(MAIN,LAUNCHER)") {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0)
            list.size to list.isNotEmpty()
        }

    private fun activeAdmins() =
        probe("DevicePolicyManager.getActiveAdmins", note = "null list is recorded as count 0, field present no") {
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            val admins = dpm.activeAdmins
            (admins?.size ?: 0) to (admins != null)
        }

    private fun accessibilitySetting() =
        probe("Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES", note = "count = colon separated entries") {
            val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val entries = raw?.split(':')?.filter { it.isNotBlank() } ?: emptyList()
            entries.size to (raw != null)
        }

    private fun accessibilityManager() =
        probe("AccessibilityManager.getEnabledAccessibilityServiceList") {
            val am = context.getSystemService(AccessibilityManager::class.java)
            val list = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            list.size to true
        }

    private fun notificationListeners() =
        probe("Settings.Secure.enabled_notification_listeners", note = "count = colon separated entries") {
            val raw = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            val entries = raw?.split(':')?.filter { it.isNotBlank() } ?: emptyList()
            entries.size to (raw != null)
        }

    private fun permissionFlags() =
        probe("PackageInfo.requestedPermissionsFlags", note = "count = packages where flags are non null") {
            val list = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val withFlags = list.count { it.requestedPermissionsFlags != null }
            withFlags to (withFlags > 0)
        }

    private fun appCategory() =
        probe("ApplicationInfo.category", note = "count = packages with a defined category") {
            val list = pm.getInstalledApplications(0)
            list.count { it.category != ApplicationInfo.CATEGORY_UNDEFINED } to list.isNotEmpty()
        }

    private fun bootReceivers() =
        probe("PackageManager.queryBroadcastReceivers(BOOT_COMPLETED)") {
            val list = pm.queryBroadcastReceivers(Intent(Intent.ACTION_BOOT_COMPLETED), 0)
            list.size to true
        }

    private fun usageAccessGranted() =
        probe("AppOpsManager GET_USAGE_STATS for this app", note = "count 1 = granted") {
            val granted = hasUsageAccess()
            (if (granted) 1 else 0) to granted
        }

    private fun usageEvents() =
        probe("UsageStatsManager.queryEvents last 24h") {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - DAY_MS, end)
            var n = 0
            val e =
                android.app.usage.UsageEvents
                    .Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                n++
            }
            n to (n > 0)
        }

    private fun usageStats() =
        probe("UsageStatsManager.queryUsageStats last 7d") {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            val end = System.currentTimeMillis()
            val list = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 7 * DAY_MS, end)
            list.size to list.isNotEmpty()
        }

    private fun networkStats() =
        probe("NetworkStatsManager.querySummary(WIFI) last 24h", note = "count = uid buckets") {
            val nsm = context.getSystemService(NetworkStatsManager::class.java)
            val end = System.currentTimeMillis()
            val stats = nsm.querySummary(ConnectivityManager.TYPE_WIFI, null, end - DAY_MS, end)
            var n = 0
            val bucket =
                android.app.usage.NetworkStats
                    .Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                n++
            }
            stats.close()
            n to (n > 0)
        }

    private fun networkStatsMobile() =
        probe("NetworkStatsManager.querySummary(MOBILE) last 24h", note = "count = uid buckets") {
            val nsm = context.getSystemService(NetworkStatsManager::class.java)
            val end = System.currentTimeMillis()
            val stats = nsm.querySummary(ConnectivityManager.TYPE_MOBILE, null, end - DAY_MS, end)
            var n = 0
            val bucket =
                android.app.usage.NetworkStats
                    .Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                n++
            }
            stats.close()
            n to (n > 0)
        }

    private fun connectionOwnerUid() =
        probe(
            "ConnectivityManager.getConnectionOwnerUid(UDP)",
            note = "count 1 = resolved to own uid on a connected but silent UDP socket",
        ) {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            DatagramSocket().use { socket ->
                socket.connect(InetSocketAddress("10.255.255.1", 53))
                val local = socket.localSocketAddress as InetSocketAddress
                val remote = InetSocketAddress(socket.inetAddress, socket.port)
                val uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote)
                (if (uid == Process.myUid()) 1 else 0) to (uid != Process.INVALID_UID)
            }
        }

    private fun connectionOwnerUidLoopbackTcp() =
        probe(
            "ConnectivityManager.getConnectionOwnerUid(TCP loopback)",
            note = "count 1 = resolved to own uid on a real loopback connection",
        ) {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                Socket(InetAddress.getLoopbackAddress(), server.localPort).use { client ->
                    server.accept().use {
                        val local = client.localSocketAddress as InetSocketAddress
                        val remote = client.remoteSocketAddress as InetSocketAddress
                        val uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_TCP, local, remote)
                        (if (uid == Process.myUid()) 1 else 0) to (uid != Process.INVALID_UID)
                    }
                }
            }
        }

    private fun privateDns() =
        probe("LinkProperties.isPrivateDnsActive", note = "count 1 = private DNS active on the default network") {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val lp = cm.activeNetwork?.let { cm.getLinkProperties(it) }
            val active = lp?.isPrivateDnsActive == true
            (if (active) 1 else 0) to (lp != null)
        }

    private fun vpnPrepared() =
        probe("VpnService.prepare", note = "count 1 = consent already granted, 0 = consent dialog needed") {
            val needsConsent = VpnService.prepare(context) != null
            (if (needsConsent) 0 else 1) to true
        }

    fun vpnTransportActive(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        return caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun probe(
        api: String,
        note: String? = null,
        body: () -> Pair<Int, Boolean>,
    ): ProbeResult {
        val start = System.nanoTime()
        return try {
            val (count, present) = body()
            ProbeResult(api = api, count = count, fieldPresent = present, elapsedMs = elapsedSince(start), note = note)
        } catch (e: Exception) {
            ProbeResult(api = api, elapsedMs = elapsedSince(start), error = "${e.javaClass.name}: ${e.message}", note = note)
        }
    }

    private fun elapsedSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
