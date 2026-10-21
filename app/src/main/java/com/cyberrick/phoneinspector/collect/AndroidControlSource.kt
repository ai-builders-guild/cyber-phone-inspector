package com.cyberrick.phoneinspector.collect

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** Read only adapter for the Deep control collector. Exercised on devices via the probe. */
class AndroidControlSource(
    private val context: Context,
) : ControlSource {
    private val dpm: DevicePolicyManager by lazy { context.getSystemService(DevicePolicyManager::class.java) }

    override fun activeAdmins(): List<String>? = dpm.activeAdmins?.map { it.flattenToString() }

    override fun isDeviceOwner(packageName: String): Boolean = dpm.isDeviceOwnerApp(packageName)

    override fun isProfileOwner(packageName: String): Boolean = dpm.isProfileOwnerApp(packageName)

    override fun enabledAccessibilityServicesSetting(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    override fun enabledAccessibilityServicesFromManager(): List<String> =
        context
            .getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapNotNull { it.resolveInfo?.serviceInfo?.let { si -> "${si.packageName}/${si.name}" } }

    override fun enabledNotificationListenersSetting(): String? =
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")

    override fun firstInstallTime(packageName: String): Long? =
        try {
            context.packageManager.getPackageInfo(packageName, 0).firstInstallTime
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
}
