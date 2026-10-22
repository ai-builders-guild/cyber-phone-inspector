package com.cyberrick.phoneinspector.actions

/**
 * Which Settings screen clears each blocker. Android has no public action for the Device admin apps list, so the
 * AOSP Settings component is tried first and the Security page is the public fallback; accessibility has a public
 * list page. The step text names the app. When the preferred action does not resolve on a device, the app's
 * own info page is the fallback. Pure: resolution is injected.
 */
object SettingsLinks {
    const val DEVICE_ADMIN_LIST_PACKAGE = "com.android.settings"
    const val DEVICE_ADMIN_LIST_CLASS = "com.android.settings.Settings\$DeviceAdminSettingsActivity"
    const val SECURITY = "android.settings.SECURITY_SETTINGS"
    const val ACCESSIBILITY = "android.settings.ACCESSIBILITY_SETTINGS"
    const val NOTIFICATION_LISTENER_DETAIL = "android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS"
    const val APP_INFO = "android.settings.APPLICATION_DETAILS_SETTINGS"

    fun interface Resolver {
        fun canResolve(action: String): Boolean
    }

    fun targetFor(blocker: RemovalBlocker): String? =
        when (blocker) {
            RemovalBlocker.DEVICE_ADMIN -> SECURITY
            RemovalBlocker.ACCESSIBILITY -> ACCESSIBILITY
            RemovalBlocker.NOTIFICATION_LISTENER -> NOTIFICATION_LISTENER_DETAIL
            RemovalBlocker.SYSTEM_APP -> APP_INFO
        }

    fun choose(
        blocker: RemovalBlocker,
        resolver: Resolver,
    ): String {
        val preferred = targetFor(blocker) ?: APP_INFO
        return if (resolver.canResolve(preferred)) preferred else APP_INFO
    }
}
