package com.cyberrick.phoneinspector.rules

import com.cyberrick.phoneinspector.core.model.FlowRecord
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class NetworkFactsTest {
    private val t0 = 1_792_584_000_000L
    private val known = KnownServices(setOf("whatsapp.net", "spotify.com", "googleapis.com"))

    private fun dns(
        pkg: String,
        host: String,
        at: Long,
    ) = FlowRecord(uid = 1, packageName = pkg, host = host, protocol = "udp", observedAt = at, api = "t", collectedAt = t0)

    private fun counter(
        pkg: String,
        bytes: Long,
    ) = FlowRecord(
        uid = 1,
        packageName = pkg,
        host = null,
        protocol = "counter",
        observedAt = t0,
        bytesSent = bytes,
        api = "t",
        collectedAt = t0,
    )

    @Test
    fun `hosts are split into known and unrecognized by suffix on a label boundary`() {
        val flows =
            listOf(
                dns("com.a", "g.whatsapp.net", t0),
                dns("com.a", "api.spotify.com", t0),
                dns("com.a", "c2.metrics-collector.xyz", t0),
                dns("com.a", "notspotify.com", t0),
            )

        val f = NetworkFacts.forPackage("com.a", flows, known)

        assertThat(f.knownHosts).containsExactly("g.whatsapp.net", "api.spotify.com")
        assertThat(f.unrecognizedHosts).containsExactly("c2.metrics-collector.xyz", "notspotify.com")
    }

    @Test
    fun `a fixed sixty second series to an unrecognized host is a beacon with its interval`() {
        val flows = (0 until 10).map { dns("com.a", "c2.metrics-collector.xyz", t0 + it * 60_000L) }

        val f = NetworkFacts.forPackage("com.a", flows, known)

        assertThat(f.beacon).isNotNull()
        assertThat(f.beacon!!.host).isEqualTo("c2.metrics-collector.xyz")
        assertThat(f.beacon!!.intervalSeconds).isWithin(2).of(60)
        assertThat(f.beacon!!.count).isEqualTo(10)
    }

    @Test
    fun `a jittered series with coefficient of variation above the threshold is not a beacon`() {
        val gaps = listOf(137L, 137L, 17L, 137L, 137L, 20L, 150L)
        var t = t0
        val flows = mutableListOf(dns("com.a", "x.example", t))
        gaps.forEach {
            t += it * 1000
            flows += dns("com.a", "x.example", t)
        }

        assertThat(NetworkFacts.forPackage("com.a", flows, known).beacon).isNull()
    }

    @Test
    fun `fewer than five lookups never make a beacon and regular lookups to a known host do not either`() {
        val few = (0 until 4).map { dns("com.a", "x.example", t0 + it * 60_000L) }
        val knownRegular = (0 until 10).map { dns("com.a", "api.spotify.com", t0 + it * 60_000L) }

        assertThat(NetworkFacts.forPackage("com.a", few, known).beacon).isNull()
        assertThat(NetworkFacts.forPackage("com.a", knownRegular, known).beacon).isNull()
    }

    @Test
    fun `bytes sent over one megabyte with no lookups is raw ip suspect`() {
        assertThat(NetworkFacts.forPackage("com.a", listOf(counter("com.a", 2_097_152)), known).rawIpSuspect).isTrue()
        assertThat(NetworkFacts.forPackage("com.a", listOf(counter("com.a", 500_000)), known).rawIpSuspect).isFalse()
        assertThat(
            NetworkFacts.forPackage("com.a", listOf(counter("com.a", 2_097_152), dns("com.a", "api.spotify.com", t0)), known).rawIpSuspect,
        ).isFalse()
    }

    @Test
    fun `bundled known services asset parses and recognises the big names`() {
        val asset = KnownServices.parse(File(System.getProperty("user.dir") ?: ".", "src/main/assets/${KnownServices.ASSET}").readText())

        assertThat(asset.suffixes.size).isAtLeast(60)
        listOf("mtalk.google.com", "play-fe.googleapis.com", "g.whatsapp.net", "cdn.samsungcloud.com", "e.crashlytics.com").forEach {
            assertThat(asset.isKnown(it)).isTrue()
        }
        assertThat(asset.isKnown("c2.metrics-collector.xyz")).isFalse()
    }
}
