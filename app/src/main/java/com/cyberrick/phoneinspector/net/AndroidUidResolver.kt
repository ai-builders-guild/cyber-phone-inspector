package com.cyberrick.phoneinspector.net

import android.content.Context
import android.net.ConnectivityManager
import android.system.OsConstants
import java.net.InetSocketAddress

/** Attribution through ConnectivityManager. Only works while this app is the active VPN (Phase 2 finding). */
class AndroidUidResolver(
    context: Context,
) : UidResolver {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val pm = context.packageManager
    private val cache = HashMap<Int, String?>()

    override fun ownerUid(
        srcAddr: String,
        srcPort: Int,
        dstAddr: String,
        dstPort: Int,
    ): Int =
        try {
            cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, InetSocketAddress(srcAddr, srcPort), InetSocketAddress(dstAddr, dstPort))
        } catch (e: SecurityException) {
            -1
        }

    /** Shared uids map to several packages; the first is reported and the rest are visible in the inventory. */
    override fun packageFor(uid: Int): String? = cache.getOrPut(uid) { pm.getPackagesForUid(uid)?.sorted()?.firstOrNull() }
}
