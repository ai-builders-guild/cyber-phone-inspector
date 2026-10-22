package com.cyberrick.phoneinspector.collect

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackerScanCollectorTest {
    private val catalog =
        TrackerCatalog(
            listOf(
                TrackerCatalog.Tracker("Zeta Ads", listOf("Advertisement"), listOf("com.zeta.ads.")),
                TrackerCatalog.Tracker("Alpha Analytics", listOf("Analytics"), listOf("com.alpha.")),
                TrackerCatalog.Tracker("Profilr", listOf("Profiling"), listOf("io.profilr.sdk")),
            ),
        )

    private fun dex(vararg classes: String) = SyntheticDex.build(classes.toList())

    private fun source(vararg apps: Pair<String, List<ByteArray>>) =
        object : DexSource {
            override fun nonSystemPackages(): List<String> = apps.map { it.first }

            override fun dexFiles(packageName: String): List<ByteArray> = apps.first { it.first == packageName }.second
        }

    @Test
    fun `finds trackers by class descriptor prefix and misses near misses`() {
        val app = "com.shop" to listOf(dex("Lcom/zeta/ads/Banner;", "Lcom/alphabet/NotAlpha;", "Lio/profilr/sdk/Core;", "Lcom/shop/Main;"))

        val result = TrackerScanCollector(source(app), catalog).collect()

        assertThat(result.trackers.getValue("com.shop")).containsExactly("Zeta Ads", "Profilr").inOrder()
        assertThat(result.budgetHit).isEmpty()
    }

    @Test
    fun `an app with no trackers yields an empty list not an absence`() {
        val result = TrackerScanCollector(source("com.clean" to listOf(dex("Lcom/clean/Main;"))), catalog).collect()

        assertThat(result.trackers).containsKey("com.clean")
        assertThat(result.trackers.getValue("com.clean")).isEmpty()
    }

    @Test
    fun `a huge string pool trips the per app budget and is reported as partial`() {
        val big = SyntheticDex.build((0 until 200_000).map { "Lcom/filler/C$it;" })
        // A fake clock that advances one millisecond per look, so the budget trips by count, never by wall time.
        var ticks = 0L
        val tiny =
            TrackerScanCollector(
                source("com.big" to listOf(big), "com.ok" to listOf(dex("Lcom/zeta/ads/X;"))),
                catalog,
                perAppBudgetMs = 1,
                clock = { ticks++ * 1_000_000 },
            )

        val result = tiny.collect()

        assertThat(result.budgetHit).contains("com.big")
        assertThat(result.trackers.getValue("com.ok")).containsExactly("Zeta Ads")
    }

    @Test
    fun `a corrupt dex is skipped and counted never thrown`() {
        val result = TrackerScanCollector(source("com.bad" to listOf(ByteArray(10) { 1 })), catalog).collect()

        assertThat(result.trackers.getValue("com.bad")).isEmpty()
        assertThat(result.unreadable).isEqualTo(1)
    }

    @Test
    fun `dex string pool reader returns the descriptors of a synthetic dex`() {
        val strings = DexStrings.read(dex("Lcom/a/B;", "Lcom/c/D;"))

        assertThat(strings).containsExactly("Lcom/a/B;", "Lcom/c/D;")
    }

    @Test
    fun `bundled tracker catalog parses with signatures and categories`() {
        val text = java.io.File(System.getProperty("user.dir") ?: ".", "src/main/assets/feeds/trackers.json").readText()

        val bundled = TrackerCatalog.parse(text)

        assertThat(bundled.trackers.size).isAtLeast(400)
        assertThat(bundled.trackers.all { it.signatures.isNotEmpty() }).isTrue()
        assertThat(bundled.trackers.any { "Advertisement" in it.categories }).isTrue()
    }
}
