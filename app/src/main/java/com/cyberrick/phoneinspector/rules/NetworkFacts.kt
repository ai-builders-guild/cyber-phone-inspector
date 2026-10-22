package com.cyberrick.phoneinspector.rules

import com.cyberrick.phoneinspector.core.model.FlowRecord
import kotlin.math.sqrt

/** Per app view of the monitor's flows: which hosts, whether any series looks like a beacon, bytes with no lookups. */
data class NetworkFacts(
    val knownHosts: List<String>,
    val unrecognizedHosts: List<String>,
    val beacon: Beacon?,
    val bytesSent: Long,
    val lookups: Int,
) {
    data class Beacon(
        val host: String,
        val intervalSeconds: Int,
        val count: Int,
    )

    val rawIpSuspect: Boolean get() = lookups == 0 && bytesSent > RAW_IP_BYTES

    companion object {
        const val RAW_IP_BYTES = 1_048_576L
        const val BEACON_MIN_LOOKUPS = 5
        const val BEACON_MAX_CV = 0.15

        fun forPackage(
            packageName: String,
            flows: List<FlowRecord>,
            known: KnownServices,
        ): NetworkFacts {
            val mine = flows.filter { it.packageName == packageName }
            val dns = mine.filter { it.host != null }
            val hosts = dns.mapNotNull { it.host }.distinct()
            val (knownHosts, unknownHosts) = hosts.partition { known.isKnown(it) }
            val beacon =
                dns
                    .groupBy { it.host!! }
                    .filterKeys { it in unknownHosts }
                    .mapNotNull { (host, series) -> beaconOf(host, series.map { it.observedAt }.sorted()) }
                    .maxByOrNull { it.count }
            return NetworkFacts(
                knownHosts = knownHosts,
                unrecognizedHosts = unknownHosts,
                beacon = beacon,
                bytesSent = mine.filter { it.protocol == "counter" }.sumOf { it.bytesSent ?: 0L },
                lookups = dns.size,
            )
        }

        /** A beacon is at least five lookups whose gaps vary by less than 15 percent around their mean. */
        private fun beaconOf(
            host: String,
            times: List<Long>,
        ): Beacon? {
            if (times.size < BEACON_MIN_LOOKUPS) return null
            val gaps = times.zipWithNext { a, b -> (b - a).toDouble() }
            val mean = gaps.average()
            if (mean <= 0) return null
            val sd = sqrt(gaps.sumOf { (it - mean) * (it - mean) } / gaps.size)
            if (sd / mean > BEACON_MAX_CV) return null
            return Beacon(host, (mean / 1000).toInt(), times.size)
        }
    }
}
