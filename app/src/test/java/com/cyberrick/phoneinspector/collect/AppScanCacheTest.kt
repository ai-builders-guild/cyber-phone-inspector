package com.cyberrick.phoneinspector.collect

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppScanCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `round trips, keeps the hash across catalog dates and misses on a new version`() {
        val cache = AppScanCache(tmp.root)
        val k = cache.key("com.a", 3, 100)
        cache.put(mapOf(k to AppScanCache.Entry("ab".repeat(32), "2026-10-22", listOf("AdMob"))))

        val again = AppScanCache(tmp.root)

        assertThat(again.trackers(k, "2026-10-22")).containsExactly("AdMob")
        assertThat(again.trackers(k, "2026-10-29")).isNull()
        assertThat(again.hash(k)).isEqualTo("ab".repeat(32))
        assertThat(again.hash(cache.key("com.a", 4, 100))).isNull()
    }

    @Test
    fun `a corrupt cache file reads as empty`() {
        tmp.newFile(AppScanCache.FILE).writeText("{not json")

        assertThat(AppScanCache(tmp.root).hash("x")).isNull()
    }
}
