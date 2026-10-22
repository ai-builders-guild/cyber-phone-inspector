package com.cyberrick.phoneinspector.feeds

import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** HTTPS only, one host only, bounded size and time. Anything else is refused before a socket is opened. */
class HttpsFetcher(
    private val allowedHost: String = ALLOWED_HOST,
    private val timeoutMs: Int = 20_000,
) : Fetcher {
    fun allowed(url: String): Boolean {
        val u =
            try {
                URL(url)
            } catch (e: Exception) {
                return false
            }
        return u.protocol == "https" && u.host.equals(allowedHost, ignoreCase = true)
    }

    override fun get(url: String): ByteArray {
        if (!allowed(url)) throw IllegalArgumentException("refused address")
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IllegalStateException("HTTP ${connection.responseCode}")
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > FeedRefresher.MAX_BYTES) throw IllegalStateException("too large")
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val ALLOWED_HOST = "raw.githubusercontent.com"
    }
}
