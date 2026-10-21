package com.cyberrick.phoneinspector.net

/**
 * Strict private DNS (a named DNS over TLS server) would break name resolution once the tunnel is the resolver,
 * because the tunnel only speaks plain DNS and strict mode never falls back. A configured server name means strict
 * mode whether or not validation has succeeded yet. Automatic mode falls back to plain DNS when the TLS probe fails.
 */
object PrivateDnsPolicy {
    sealed class Decision {
        data object Allow : Decision()

        data class RefuseStrict(
            val message: String,
        ) : Decision()
    }

    fun decide(
        active: Boolean,
        serverName: String?,
    ): Decision =
        if (!serverName.isNullOrBlank()) {
            Decision.RefuseStrict(
                "Private DNS is set to a specific provider ($serverName). The network monitor cannot run in that mode. " +
                    "Set Settings > Network > Private DNS to Automatic for the length of the monitor, then set it back.",
            )
        } else {
            Decision.Allow
        }
}
