package com.cyberrick.phoneinspector.core.model

import kotlinx.serialization.Serializable

/** Every evidence record says which API produced it and when (epoch millis UTC). */
interface Provenanced {
    val api: String
    val collectedAt: Long
}

@Serializable
enum class CheckKind { APPS, CONTROL, PERMISSIONS, PERSISTENCE, NETWORK }

@Serializable
enum class CheckStatus { CHECKED, PARTIAL, NOT_CHECKED }

/** Whether a check ran. A NOT_CHECKED check is unknown to the rules, never clean. */
@Serializable
data class CheckResult(
    val check: CheckKind,
    val status: CheckStatus,
    val reason: String? = null,
)

/** WEB_APP: a WebAPK (installed progressive web app); Chrome is its installer. VENDOR_STORE includes carrier and OEM module installers. */
@Serializable
enum class InstallSource { PLAY_STORE, VENDOR_STORE, WEB_APP, SYSTEM, SIDELOADED, UNKNOWN }

/** Non identifying device facts. Never a serial, IMEI, or account. */
@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val oemSkin: String? = null,
)

@Serializable
data class AppRecord(
    val packageName: String,
    val label: String,
    val versionName: String? = null,
    val versionCode: Long = 0,
    val firstInstallAt: Long,
    val lastUpdateAt: Long,
    val installerPackage: String? = null,
    val installSource: InstallSource,
    /** "sha256:<hex>" entries, and "sha1:<hex>" entries since Phase 8 because published indicators are mostly SHA-1. */
    val signingCertSha256: List<String> = emptyList(),
    val isSystem: Boolean,
    val hasLauncherIcon: Boolean,
    val enabled: Boolean = true,
    val hidden: Boolean = false,
    val disguised: Boolean = false,
    val category: String? = null,
    val duplicate: Boolean = false,
    override val api: String,
    override val collectedAt: Long,
) : Provenanced

@Serializable
enum class ControlKind { DEVICE_ADMIN, DEVICE_OWNER, PROFILE_OWNER, ACCESSIBILITY, NOTIFICATION_LISTENER }

/** [sinceAtLatest] is a bound: Android does not expose grant time, so the app's install time is used. */
@Serializable
data class ControlRecord(
    val kind: ControlKind,
    val packageName: String,
    val component: String? = null,
    val sinceAtLatest: Long,
    override val api: String,
    override val collectedAt: Long,
) : Provenanced

@Serializable
enum class SensitiveGroup { LOCATION, LOCATION_BACKGROUND, MICROPHONE, CAMERA }

/** "granted" means the permission flag is set. It never means "used". */
@Serializable
data class PermissionRecord(
    val packageName: String,
    val group: SensitiveGroup,
    val requested: Boolean,
    val granted: Boolean,
    val uncertain: Boolean = false,
    val unexpected: Boolean = false,
    override val api: String,
    override val collectedAt: Long,
) : Provenanced

@Serializable
data class PersistenceRecord(
    val packageName: String,
    val bootReceiver: Boolean,
    val foregroundServiceMinutes7d: Long = 0,
    val lastForegroundServiceStartAt: Long? = null,
    val backgroundHeavy: Boolean = false,
    val droppedEvents: Int = 0,
    override val api: String,
    override val collectedAt: Long,
) : Provenanced

/** One observed flow: a DNS lookup attributed to an app, or a byte counter bucket with no name. */
@Serializable
data class FlowRecord(
    val uid: Int,
    val packageName: String? = null,
    val host: String? = null,
    val answerIps: List<String> = emptyList(),
    val remotePort: Int? = null,
    val protocol: String = "udp",
    val observedAt: Long,
    val bytesSent: Long? = null,
    override val api: String,
    override val collectedAt: Long,
) : Provenanced
