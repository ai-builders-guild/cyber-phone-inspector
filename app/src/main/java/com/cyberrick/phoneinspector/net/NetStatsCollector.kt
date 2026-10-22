package com.cyberrick.phoneinspector.net

import com.cyberrick.phoneinspector.core.model.FlowRecord

data class NetStatsBucket(
    val uid: Int,
    val transport: String,
    val bytesSent: Long,
)

interface NetStatsSource {
    fun buckets(
        fromMs: Long,
        toMs: Long,
    ): List<NetStatsBucket>
}

/** Bytes sent per app over the monitor window, both transports summed. Catches apps that send without a DNS lookup. */
class NetStatsCollector(
    private val source: NetStatsSource,
    private val uids: UidResolver,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun collect(
        fromMs: Long,
        toMs: Long,
    ): List<FlowRecord> {
        val now = clock()
        return source
            .buckets(fromMs, toMs)
            .groupBy { it.uid }
            .mapValues { (_, b) -> b.sumOf { it.bytesSent } }
            .filterValues { it > 0 }
            .toSortedMap()
            .map { (uid, bytes) ->
                FlowRecord(
                    uid = uid,
                    packageName = uids.packageFor(uid),
                    host = null,
                    protocol = "counter",
                    observedAt = fromMs,
                    bytesSent = bytes,
                    api = API,
                    collectedAt = now,
                )
            }
    }

    companion object {
        const val API = "NetworkStatsManager.querySummary"
    }
}
