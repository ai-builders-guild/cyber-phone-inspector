package com.cyberrick.phoneinspector.actions

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * The only file in the app that may reference Android's uninstall intent (GuardrailTest enforces this).
 * It asks the system to show its own confirmation; the app never removes a package itself. Everything else here
 * is a read: is the package installed, what blocks its removal, which Settings page to open.
 */
class RemoveAction(
    private val context: Context,
) {
    private val pm: PackageManager = context.packageManager

    /** Android's own uninstall confirmation for [packageName]; the result tells the caller whether the person confirmed. */
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, "package:$packageName".toUri()).putExtra(Intent.EXTRA_RETURN_RESULT, true)

    fun isInstalled(packageName: String): Boolean =
        try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    fun blockerFor(packageName: String): RemovalBlocker? {
        val app =
            try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                return null
            }
        if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) return RemovalBlocker.SYSTEM_APP
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm.activeAdmins.orEmpty().any { it.packageName == packageName }) return RemovalBlocker.DEVICE_ADMIN
        val accessibility = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        if (accessibility
                .split(
                    ':',
                ).any { ComponentName.unflattenFromString(it)?.packageName == packageName }
        ) {
            return RemovalBlocker.ACCESSIBILITY
        }
        val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        if (listeners.split(':').any {
                ComponentName.unflattenFromString(it)?.packageName == packageName
            }
        ) {
            return RemovalBlocker.NOTIFICATION_LISTENER
        }
        return null
    }

    /** The Settings screen that clears [blocker] on this device, falling back to the app's info page. */
    fun settingsIntent(
        packageName: String,
        blocker: RemovalBlocker,
    ): Intent {
        val resolver = SettingsLinks.Resolver { action -> pm.resolveActivity(Intent(action), 0) != null }
        val action = SettingsLinks.choose(blocker, resolver)
        val intent = Intent(action)
        if (action == SettingsLinks.APP_INFO) intent.data = "package:$packageName".toUri()
        if (action == SettingsLinks.NOTIFICATION_LISTENER_DETAIL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val component =
                Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty().split(':').firstOrNull {
                    ComponentName.unflattenFromString(it)?.packageName ==
                        packageName
                }
            component?.let { intent.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, it) }
        }
        return intent
    }
}
