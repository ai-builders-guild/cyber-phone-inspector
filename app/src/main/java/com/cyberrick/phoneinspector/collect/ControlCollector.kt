package com.cyberrick.phoneinspector.collect

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.ControlKind
import com.cyberrick.phoneinspector.core.model.ControlRecord

/**
 * Deep control check: who holds device admin, owner, work profile, accessibility and notification listener power.
 * Grant time is not exposed by Android, so [ControlRecord.sinceAtLatest] is the package's install time, or the
 * collection time when even that is unknown.
 */
class ControlCollector(
    private val source: ControlSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun collect(): CollectorResult<ControlRecord> {
        val collectedAt = clock()
        return try {
            val unparseable = mutableListOf<String>()
            val records =
                (adminRecords(collectedAt) + accessibilityRecords(collectedAt, unparseable) + listenerRecords(collectedAt, unparseable))
                    .distinctBy { it.kind to it.component }
                    .sortedWith(compareBy({ it.kind.name }, { it.packageName }, { it.component }))
            val check =
                if (unparseable.isEmpty()) {
                    CheckResult(CheckKind.CONTROL, CheckStatus.CHECKED)
                } else {
                    CheckResult(CheckKind.CONTROL, CheckStatus.PARTIAL, "${unparseable.size} unparseable settings entries ignored")
                }
            CollectorResult(records, check)
        } catch (e: Exception) {
            CollectorResult(emptyList(), CheckResult(CheckKind.CONTROL, CheckStatus.NOT_CHECKED, "${e.javaClass.simpleName}: ${e.message}"))
        }
    }

    private fun adminRecords(collectedAt: Long): List<ControlRecord> =
        source.activeAdmins().orEmpty().mapNotNull { parseComponent(it) }.flatMap { (pkg, component) ->
            val since = sinceAtLatest(pkg, collectedAt)
            buildList {
                add(ControlRecord(ControlKind.DEVICE_ADMIN, pkg, component, since, API_ADMINS, collectedAt))
                if (source.isDeviceOwner(pkg)) add(ControlRecord(ControlKind.DEVICE_OWNER, pkg, component, since, API_ADMINS, collectedAt))
                if (source.isProfileOwner(
                        pkg,
                    )
                ) {
                    add(ControlRecord(ControlKind.PROFILE_OWNER, pkg, component, since, API_ADMINS, collectedAt))
                }
            }
        }

    private fun accessibilityRecords(
        collectedAt: Long,
        unparseable: MutableList<String>,
    ): List<ControlRecord> {
        val fromSetting = parseSetting(source.enabledAccessibilityServicesSetting(), unparseable)
        val fromManager = source.enabledAccessibilityServicesFromManager().mapNotNull { parseComponent(it) }
        return (fromSetting + fromManager).distinct().map { (pkg, component) ->
            ControlRecord(ControlKind.ACCESSIBILITY, pkg, component, sinceAtLatest(pkg, collectedAt), API_ACCESSIBILITY, collectedAt)
        }
    }

    private fun listenerRecords(
        collectedAt: Long,
        unparseable: MutableList<String>,
    ): List<ControlRecord> =
        parseSetting(source.enabledNotificationListenersSetting(), unparseable).map { (pkg, component) ->
            ControlRecord(ControlKind.NOTIFICATION_LISTENER, pkg, component, sinceAtLatest(pkg, collectedAt), API_LISTENERS, collectedAt)
        }

    private fun sinceAtLatest(
        packageName: String,
        collectedAt: Long,
    ): Long = runCatching { source.firstInstallTime(packageName) }.getOrNull() ?: collectedAt

    /** Blank entries ("::", trailing colon) are not errors; an entry without a slash is. */
    private fun parseSetting(
        raw: String?,
        unparseable: MutableList<String>,
    ): List<Pair<String, String>> =
        raw
            .orEmpty()
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                parseComponent(entry) ?: run {
                    unparseable += entry
                    null
                }
            }

    companion object {
        const val API_ADMINS = "DevicePolicyManager.getActiveAdmins"
        const val API_ACCESSIBILITY = "Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES+AccessibilityManager"
        const val API_LISTENERS = "Settings.Secure.enabled_notification_listeners"

        /** "pkg/.Cls" or "pkg/full.Cls" → (pkg, "pkg/full.Cls"); null when malformed. Mirrors ComponentName.unflattenFromString. */
        fun parseComponent(flat: String): Pair<String, String>? {
            val slash = flat.indexOf('/')
            if (slash <= 0 || slash == flat.length - 1) return null
            val pkg = flat.substring(0, slash)
            val cls = flat.substring(slash + 1).let { if (it.startsWith(".")) pkg + it else it }
            if (pkg.isBlank() || cls.isBlank() || pkg.contains(' ')) return null
            return pkg to "$pkg/$cls"
        }
    }
}
