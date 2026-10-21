package com.cyberrick.phoneinspector.collect

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process

/** Read only adapter for the Persistence collector. Usage events need the usage access special permission. */
class AndroidPersistenceSource(
    private val context: Context,
) : PersistenceSource {
    private val pm: PackageManager = context.packageManager

    @Suppress("DEPRECATION") // unsafeCheckOpNoThrow is the variant that works for the app's own op on API 29
    override fun usageAccessGranted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    override fun bootInfo(): List<RawBootInfo> {
        val receivers = pm.queryBroadcastReceivers(Intent(Intent.ACTION_BOOT_COMPLETED), 0).map { it.activityInfo.packageName }.toSet()
        return pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).map { info ->
            val app = info.applicationInfo
            val flags = app?.flags ?: 0
            RawBootInfo(
                packageName = info.packageName,
                hasBootReceiver = info.packageName in receivers,
                requestsBootPermission = info.requestedPermissions?.contains(BOOT_PERMISSION) == true,
                category = AppsCollector.categoryName(app?.category),
                isSystem = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            )
        }
    }

    override fun foregroundServiceEvents(
        fromMs: Long,
        toMs: Long,
    ): List<UsageEvent> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(fromMs, toMs)
        val out = mutableListOf<UsageEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type =
                when (e.eventType) {
                    UsageEvents.Event.FOREGROUND_SERVICE_START -> UsageEventType.FOREGROUND_SERVICE_START
                    UsageEvents.Event.FOREGROUND_SERVICE_STOP -> UsageEventType.FOREGROUND_SERVICE_STOP
                    else -> null
                }
            if (type != null) out += UsageEvent(e.packageName, type, e.timeStamp)
        }
        return out
    }

    private companion object {
        const val BOOT_PERMISSION = "android.permission.RECEIVE_BOOT_COMPLETED"
    }
}
