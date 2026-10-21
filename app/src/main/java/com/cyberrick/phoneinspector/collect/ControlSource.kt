package com.cyberrick.phoneinspector.collect

/** The slice of DevicePolicyManager, Settings.Secure and AccessibilityManager the Deep control collector reads. */
interface ControlSource {
    /** Flattened component strings of active device admins; null when the platform returns null (no admins on some builds). */
    fun activeAdmins(): List<String>?

    fun isDeviceOwner(packageName: String): Boolean

    fun isProfileOwner(packageName: String): Boolean

    /** Raw colon separated ENABLED_ACCESSIBILITY_SERVICES, null when none. */
    fun enabledAccessibilityServicesSetting(): String?

    /** Flattened components from AccessibilityManager, the cross check for the raw setting. */
    fun enabledAccessibilityServicesFromManager(): List<String>

    /** Raw colon separated enabled_notification_listeners, null when none. */
    fun enabledNotificationListenersSetting(): String?

    fun firstInstallTime(packageName: String): Long?
}
