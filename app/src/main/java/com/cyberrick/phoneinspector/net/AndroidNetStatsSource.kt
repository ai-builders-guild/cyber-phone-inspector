package com.cyberrick.phoneinspector.net

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager

/** Per uid sent bytes from NetworkStatsManager for both transports. Needs usage access; returns nothing without it. */
class AndroidNetStatsSource(
    context: Context,
) : NetStatsSource {
    private val nsm = context.getSystemService(NetworkStatsManager::class.java)

    @Suppress("DEPRECATION") // TYPE_WIFI and TYPE_MOBILE are the documented selectors for querySummary
    override fun buckets(fromMs: Long, toMs: Long): List<NetStatsBucket> =
        listOf(ConnectivityManager.TYPE_WIFI to "wifi", ConnectivityManager.TYPE_MOBILE to "mobile").flatMap { (type, name) ->
            try {
                val out = mutableListOf<NetStatsBucket>()
                val stats = nsm.querySummary(type, null, fromMs, toMs)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    out += NetStatsBucket(bucket.uid, name, bucket.txBytes)
                }
                stats.close()
                out
            } catch (e: SecurityException) {
                emptyList()
            }
        }
}
