package com.cyberrick.phoneinspector.net

/** Attribution: which uid owns a flow seen in the tunnel, and which package that uid is. Android backed or faked. */
interface UidResolver {
    /** -1 when the platform cannot attribute the flow. */
    fun ownerUid(
        srcAddr: String,
        srcPort: Int,
        dstAddr: String,
        dstPort: Int,
    ): Int

    fun packageFor(uid: Int): String?
}
