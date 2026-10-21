package com.cyberrick.phoneinspector.collect

/** Whether a package can relaunch itself at boot, plus what the heavy background heuristic may consider. */
data class RawBootInfo(
    val packageName: String,
    val hasBootReceiver: Boolean,
    val requestsBootPermission: Boolean,
    val category: String?,
    val isSystem: Boolean,
)

enum class UsageEventType { FOREGROUND_SERVICE_START, FOREGROUND_SERVICE_STOP }

data class UsageEvent(
    val packageName: String,
    val type: UsageEventType,
    val timestamp: Long,
)

interface PersistenceSource {
    fun usageAccessGranted(): Boolean

    fun bootInfo(): List<RawBootInfo>

    /** Foreground service start and stop events in [fromMs, toMs], oldest first. Empty when none. */
    fun foregroundServiceEvents(
        fromMs: Long,
        toMs: Long,
    ): List<UsageEvent>
}
