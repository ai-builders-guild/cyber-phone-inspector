package com.cyberrick.phoneinspector.net

import com.cyberrick.phoneinspector.core.model.FlowRecord
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MonitorPartsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 1_792_584_000_000L

    @Test
    fun `net stats sum sent bytes per uid across transports and attribute packages`() {
        val source =
            FakeNetStatsSource(
                listOf(
                    NetStatsBucket(uid = 10001, transport = "wifi", bytesSent = 1000),
                    NetStatsBucket(uid = 10001, transport = "mobile", bytesSent = 500),
                    NetStatsBucket(uid = 10002, transport = "wifi", bytesSent = 0),
                    NetStatsBucket(uid = 10003, transport = "mobile", bytesSent = 42),
                ),
            )
        val uids = FakeUidResolver(emptyMap(), mapOf(10001 to "com.a", 10003 to "com.c"))

        val records = NetStatsCollector(source, uids) { now }.collect(fromMs = now - 600_000, toMs = now)

        assertThat(records.map { it.uid to it.bytesSent }).containsExactly(10001 to 1500L, 10003 to 42L)
        assertThat(records.single { it.uid == 10001 }.packageName).isEqualTo("com.a")
        assertThat(records.all { it.protocol == "counter" && it.host == null && it.api == NetStatsCollector.API }).isTrue()
    }

    @Test
    fun `flow store round trips a monitor result and reports freshness`() {
        val store = FlowStore(tmp.root) { now }
        val flows =
            listOf(FlowRecord(uid = 1, packageName = "com.a", host = "x.example", observedAt = now - 1000, api = "t", collectedAt = now))
        val result =
            MonitorResult(
                startedAt = now - 600_000,
                endedAt = now,
                windowMinutes = 10,
                flows = flows,
                lookups = 1,
                medianLatencyMs = 12,
                droppedMalformed = 0,
                resetsSent = 1,
            )

        store.save(result)
        val loaded = store.load()

        assertThat(loaded).isEqualTo(result)
        assertThat(store.isFresh(maxAgeMs = 24 * 3_600_000L)).isTrue()
    }

    @Test
    fun `flow store is empty when nothing was saved and stale after the max age`() {
        val store = FlowStore(tmp.root) { now }
        assertThat(store.load()).isNull()
        assertThat(store.isFresh(1)).isFalse()

        store.save(
            MonitorResult(
                startedAt = now - 2 * 86_400_000L,
                endedAt = now - 2 * 86_400_000L + 1,
                windowMinutes = 1,
                flows = emptyList(),
                lookups = 0,
                medianLatencyMs = 0,
                droppedMalformed = 0,
                resetsSent = 0,
            ),
        )

        assertThat(store.isFresh(maxAgeMs = 24 * 3_600_000L)).isFalse()
    }

    @Test
    fun `strict private dns refuses the monitor and automatic or off allows it`() {
        // Strict mode whose TLS validation has not (yet) succeeded is still strict: Android hard fails DNS rather than fall back.
        assertThat(
            PrivateDnsPolicy.decide(active = false, serverName = "dns.google"),
        ).isInstanceOf(PrivateDnsPolicy.Decision.RefuseStrict::class.java)
        assertThat(
            PrivateDnsPolicy.decide(active = true, serverName = "dns.google"),
        ).isInstanceOf(PrivateDnsPolicy.Decision.RefuseStrict::class.java)
        assertThat(PrivateDnsPolicy.decide(active = true, serverName = null)).isEqualTo(PrivateDnsPolicy.Decision.Allow)
        assertThat(PrivateDnsPolicy.decide(active = false, serverName = null)).isEqualTo(PrivateDnsPolicy.Decision.Allow)
        assertThat(
            (PrivateDnsPolicy.decide(active = true, serverName = "dns.google") as PrivateDnsPolicy.Decision.RefuseStrict).message,
        ).contains("Private DNS")
    }

    @Test
    fun `window minutes clamp to 1 through 60 with a default of 10`() {
        assertThat(MonitorWindow.clamp(null)).isEqualTo(10)
        assertThat(MonitorWindow.clamp(0)).isEqualTo(1)
        assertThat(MonitorWindow.clamp(25)).isEqualTo(25)
        assertThat(MonitorWindow.clamp(600)).isEqualTo(60)
    }

    @Test
    fun `median latency of an empty or odd or even list`() {
        assertThat(MonitorWindow.median(emptyList())).isEqualTo(0)
        assertThat(MonitorWindow.median(listOf(5L, 1L, 9L))).isEqualTo(5)
        assertThat(MonitorWindow.median(listOf(1L, 2L, 3L, 10L))).isEqualTo(2)
    }
}

class FakeNetStatsSource(
    private val buckets: List<NetStatsBucket>,
) : NetStatsSource {
    override fun buckets(
        fromMs: Long,
        toMs: Long,
    ): List<NetStatsBucket> = buckets
}
