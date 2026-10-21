package com.cyberrick.phoneinspector.collect

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.PersistenceRecord

/**
 * Persistence check: what relaunches on boot and what runs constantly. Foreground service time is summed from
 * start and stop pairs over the last seven days in epoch time, so local clock changes (DST) cannot distort it.
 * Needs usage access; without it the whole check is NOT_CHECKED, never silently clean.
 */
class PersistenceCollector(
    private val source: PersistenceSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun collect(): CollectorResult<PersistenceRecord> {
        val now = clock()
        return try {
            if (!source.usageAccessGranted()) {
                return CollectorResult(
                    emptyList(),
                    CheckResult(
                        CheckKind.PERSISTENCE,
                        CheckStatus.NOT_CHECKED,
                        "Usage access not granted; open $USAGE_ACCESS_SETTINGS_ACTION",
                    ),
                )
            }
            val windowStart = now - WINDOW_MS
            val boots = source.bootInfo()
            val eventsByPackage =
                source
                    .foregroundServiceEvents(
                        windowStart - LOOKBACK_MS,
                        now,
                    ).sortedBy { it.timestamp }
                    .groupBy { it.packageName }
            val records =
                boots
                    .sortedBy { it.packageName }
                    .map { boot -> record(boot, eventsByPackage[boot.packageName].orEmpty(), windowStart, now) }
            CollectorResult(records, CheckResult(CheckKind.PERSISTENCE, CheckStatus.CHECKED))
        } catch (e: Exception) {
            CollectorResult(
                emptyList(),
                CheckResult(CheckKind.PERSISTENCE, CheckStatus.NOT_CHECKED, "${e.javaClass.simpleName}: ${e.message}"),
            )
        }
    }

    private fun record(
        boot: RawBootInfo,
        events: List<UsageEvent>,
        windowStart: Long,
        now: Long,
    ): PersistenceRecord {
        val usage = sumForegroundService(events, windowStart, now)
        val minutes = usage.elapsedMs / 60_000
        val heavy = minutes > HEAVY_MINUTES && !boot.isSystem && boot.category !in ALWAYS_ON_CATEGORIES
        return PersistenceRecord(
            packageName = boot.packageName,
            bootReceiver = boot.hasBootReceiver || boot.requestsBootPermission,
            foregroundServiceMinutes7d = minutes,
            lastForegroundServiceStartAt = usage.lastStart,
            backgroundHeavy = heavy,
            droppedEvents = usage.dropped,
            api = API,
            collectedAt = now,
        )
    }

    private data class Usage(
        val elapsedMs: Long,
        val lastStart: Long?,
        val dropped: Int,
    )

    /**
     * Counts time while any foreground service of the package is running. A package may run several services at
     * once, so START and STOP are a counter, not a pair. A run already open at the window start is clipped to it;
     * one still open at collection time runs until now; a STOP with nothing running is dropped and counted.
     */
    private fun sumForegroundService(
        events: List<UsageEvent>,
        windowStart: Long,
        now: Long,
    ): Usage {
        var elapsed = 0L
        var running = 0
        var runStart = 0L
        var lastStart: Long? = null
        var dropped = 0
        for (event in events) {
            when (event.type) {
                UsageEventType.FOREGROUND_SERVICE_START -> {
                    if (running == 0) runStart = event.timestamp
                    running++
                    lastStart = event.timestamp
                }
                UsageEventType.FOREGROUND_SERVICE_STOP -> {
                    if (running == 0) {
                        dropped++
                    } else {
                        running--
                        if (running == 0) elapsed += (event.timestamp - maxOf(runStart, windowStart)).coerceAtLeast(0)
                    }
                }
            }
        }
        if (running > 0) elapsed += (now - maxOf(runStart, windowStart)).coerceAtLeast(0)
        return Usage(elapsed, lastStart, dropped)
    }

    companion object {
        const val API = "UsageStatsManager.queryEvents"
        const val USAGE_ACCESS_SETTINGS_ACTION = "android.settings.USAGE_ACCESS_SETTINGS"
        const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000

        /** Events are fetched this much earlier than the window so a service that started before it is clipped, not orphaned. */
        const val LOOKBACK_MS = 7L * 24 * 60 * 60 * 1000
        const val HEAVY_MINUTES = 6L * 60

        /** Kinds of app expected to run a foreground service for hours: music, messaging, accessibility, navigation. */
        val ALWAYS_ON_CATEGORIES = setOf("AUDIO", "SOCIAL", "ACCESSIBILITY", "MAPS")
    }
}
