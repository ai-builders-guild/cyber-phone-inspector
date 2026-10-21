package com.cyberrick.phoneinspector.collect

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PersistenceCollectorTest {
    /** 2026-11-02T12:00:00Z, the day after the US fall back transition. */
    private val now = 1_793_448_000_000L
    private val day = 86_400_000L
    private val hour = 3_600_000L
    private val minute = 60_000L

    private fun boot(
        pkg: String,
        receiver: Boolean = false,
        permission: Boolean = false,
        category: String? = null,
        system: Boolean = false,
    ) = RawBootInfo(pkg, receiver, permission, category, system)

    private fun start(
        pkg: String,
        at: Long,
    ) = UsageEvent(pkg, UsageEventType.FOREGROUND_SERVICE_START, at)

    private fun stop(
        pkg: String,
        at: Long,
    ) = UsageEvent(pkg, UsageEventType.FOREGROUND_SERVICE_STOP, at)

    private fun collect(
        boots: List<RawBootInfo>,
        events: List<UsageEvent>? = emptyList(),
        usageAccess: Boolean = true,
        failure: Exception? = null,
    ) = PersistenceCollector(FakePersistenceSource(boots, events, usageAccess, failure)) { now }.collect()

    @Test
    fun `boot receiver is true from a receiver or from the permission`() {
        val result = collect(listOf(boot("a.receiver", receiver = true), boot("a.permission", permission = true), boot("a.neither")))

        val byPkg = result.records.associateBy { it.packageName }
        assertThat(byPkg.getValue("a.receiver").bootReceiver).isTrue()
        assertThat(byPkg.getValue("a.permission").bootReceiver).isTrue()
        assertThat(byPkg.getValue("a.neither").bootReceiver).isFalse()
    }

    @Test
    fun `foreground service minutes are summed from start stop pairs in the last seven days`() {
        val events =
            listOf(
                start("a.app", now - 2 * day),
                stop("a.app", now - 2 * day + 90 * minute),
                start("a.app", now - day),
                stop(
                    "a.app",
                    now - day + 30 * minute,
                ),
            )

        val record = collect(listOf(boot("a.app")), events).records.single()

        assertThat(record.foregroundServiceMinutes7d).isEqualTo(120)
        assertThat(record.lastForegroundServiceStartAt).isEqualTo(now - day)
    }

    @Test
    fun `a start before the window is clipped to the window start`() {
        val windowStart = now - 7 * day
        val events = listOf(start("a.app", windowStart - 5 * hour), stop("a.app", windowStart + hour))

        assertThat(collect(listOf(boot("a.app")), events).records.single().foregroundServiceMinutes7d).isEqualTo(60)
    }

    @Test
    fun `events are queried with a lookback before the window so earlier starts are not orphaned`() {
        val source = FakePersistenceSource(listOf(boot("a.app")), emptyList(), usageAccess = true)

        PersistenceCollector(source) { now }.collect()

        assertThat(source.queriedFrom).isEqualTo(now - PersistenceCollector.WINDOW_MS - PersistenceCollector.LOOKBACK_MS)
        assertThat(source.queriedTo).isEqualTo(now)
    }

    @Test
    fun `two overlapping services in one package count elapsed time once and drop nothing`() {
        val events =
            listOf(
                start("a.app", now - 60 * minute),
                start("a.app", now - 50 * minute),
                stop("a.app", now - 30 * minute),
                stop("a.app", now - 10 * minute),
            )

        val record = collect(listOf(boot("a.app")), events).records.single()

        assertThat(record.foregroundServiceMinutes7d).isEqualTo(50)
        assertThat(record.droppedEvents).isEqualTo(0)
    }

    @Test
    fun `a stop without a start is ignored and counted as dropped`() {
        val events = listOf(stop("a.app", now - hour), start("a.app", now - 30 * minute), stop("a.app", now - 10 * minute))

        val record = collect(listOf(boot("a.app")), events).records.single()

        assertThat(record.foregroundServiceMinutes7d).isEqualTo(20)
        assertThat(record.droppedEvents).isEqualTo(1)
    }

    @Test
    fun `a start still running counts until now`() {
        val record = collect(listOf(boot("a.app")), listOf(start("a.app", now - 45 * minute))).records.single()

        assertThat(record.foregroundServiceMinutes7d).isEqualTo(45)
    }

    @Test
    fun `an interval spanning the US fall back transition sums wall clock elapsed minutes`() {
        // 2026-11-01T05:00:00Z is 01:00 EDT; 2026-11-01T08:00:00Z is 03:00 EST. Local clocks show 02:00 twice; elapsed is 180 min.
        val fallBackStart = 1_793_250_000_000L
        val events = listOf(start("a.app", fallBackStart), stop("a.app", fallBackStart + 3 * hour))

        assertThat(collect(listOf(boot("a.app")), events).records.single().foregroundServiceMinutes7d).isEqualTo(180)
    }

    @Test
    fun `beacon app with boot receiver and constant service is background heavy`() {
        val events = (0 until 7).flatMap { d -> listOf(start("a.beacon", now - (d + 1) * day), stop("a.beacon", now - d * day - minute)) }

        val record = collect(listOf(boot("a.beacon", receiver = true, category = "NEWS")), events).records.single()

        assertThat(record.bootReceiver).isTrue()
        assertThat(record.foregroundServiceMinutes7d).isGreaterThan(360)
        assertThat(record.backgroundHeavy).isTrue()
    }

    @Test
    fun `music app with long foreground service time is not background heavy`() {
        val events = listOf(start("a.music", now - 20 * hour), stop("a.music", now - hour))

        val record = collect(listOf(boot("a.music", receiver = true, category = "AUDIO")), events).records.single()

        assertThat(record.foregroundServiceMinutes7d).isEqualTo(19 * 60)
        assertThat(record.backgroundHeavy).isFalse()
    }

    @Test
    fun `clean phone has no background heavy apps`() {
        val boots =
            listOf(
                boot("com.spotify.music", receiver = true, category = "AUDIO"),
                boot("com.whatsapp", receiver = true, category = "SOCIAL"),
            )
        val events =
            listOf(
                start("com.spotify.music", now - 16 * hour),
                stop("com.spotify.music", now - hour),
                start(
                    "com.whatsapp",
                    now - hour,
                ),
                stop("com.whatsapp", now - 20 * minute),
            )

        assertThat(collect(boots, events).records.none { it.backgroundHeavy }).isTrue()
    }

    @Test
    fun `usage access not granted yields NOT_CHECKED naming the settings action`() {
        val result = collect(listOf(boot("a.app", receiver = true)), events = null, usageAccess = false)

        assertThat(result.records).isEmpty()
        assertThat(result.check.check).isEqualTo(CheckKind.PERSISTENCE)
        assertThat(result.check.status).isEqualTo(CheckStatus.NOT_CHECKED)
        assertThat(result.check.reason).contains("Usage access")
        assertThat(result.check.reason).contains(PersistenceCollector.USAGE_ACCESS_SETTINGS_ACTION)
    }

    @Test
    fun `a source failure yields NOT_CHECKED with the reason`() {
        val result = collect(emptyList(), failure = IllegalStateException("boom"))

        assertThat(result.check.status).isEqualTo(CheckStatus.NOT_CHECKED)
        assertThat(result.check.reason).contains("boom")
    }

    @Test
    fun `records are sorted by package and carry provenance`() {
        val records = collect(listOf(boot("b.app"), boot("a.app"))).records

        assertThat(records.map { it.packageName }).isInOrder()
        records.forEach {
            assertThat(it.api).isEqualTo(PersistenceCollector.API)
            assertThat(it.collectedAt).isEqualTo(now)
        }
    }
}

class FakePersistenceSource(
    private val boots: List<RawBootInfo>,
    private val events: List<UsageEvent>?,
    private val usageAccess: Boolean,
    private val failure: Exception? = null,
) : PersistenceSource {
    override fun usageAccessGranted(): Boolean = usageAccess

    override fun bootInfo(): List<RawBootInfo> {
        failure?.let { throw it }
        return boots
    }

    var queriedFrom: Long? = null
    var queriedTo: Long? = null

    override fun foregroundServiceEvents(
        fromMs: Long,
        toMs: Long,
    ): List<UsageEvent> {
        queriedFrom = fromMs
        queriedTo = toMs
        return events.orEmpty().filter { it.timestamp in fromMs..toMs }
    }
}
