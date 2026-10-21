package com.cyberrick.phoneinspector.rules

import com.cyberrick.phoneinspector.core.model.Evidence
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

/**
 * Phase 8 gate: for every fixture, the findings must equal the committed expected file exactly.
 * Regenerate with `./gradlew :app:testDebugUnitTest -Pcpi.updateGolden=true`, then review the diff by eye.
 */
class GoldenFindingsTest {
    private val moduleDir = File(System.getProperty("user.dir") ?: ".")
    private val fixtureDir = File(moduleDir, "src/test/resources/fixtures")
    private val expectedDir = File(moduleDir, "src/test/resources/expected")
    private val update = System.getProperty("cpi.updateGolden") == "true"
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    private val engine: RuleEngine by lazy {
        val rules = RuleSet.parse(File(moduleDir, "src/main/assets/${RuleSet.ASSET}").readText(Charsets.UTF_8))
        val shipped = IocSet.parse(File(moduleDir, "src/main/assets/iocs/${IocSet.ASSET}").readText(Charsets.UTF_8))
        // The test only package id is added here and never ships (IocMatcherTest proves it).
        RuleEngine(
            rules,
            IocMatcher(
                shipped.copy(
                    packages = shipped.packages + "com.example.ioc.match",
                    packageSources =
                        shipped.packageSources + ("com.example.ioc.match" to listOf("Test only list")),
                ),
            ),
        )
    }

    private fun fixtures() = fixtureDir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }

    @Test
    fun `findings for every fixture match the expected file exactly`() {
        val fixtures = fixtures()
        assertThat(fixtures.size).isAtLeast(16)
        val mismatches = mutableListOf<String>()
        fixtures.forEach { f ->
            val findings = engine.evaluate(Evidence.load(f))
            val text = json.encodeToString(ListSerializer(Finding.serializer()), findings) + "\n"
            val expected = File(expectedDir, f.name)
            if (update) {
                expectedDir.mkdirs()
                expected.writeText(text, Charsets.UTF_8)
            } else if (!expected.exists() || expected.readText(Charsets.UTF_8) != text) {
                mismatches += f.name
            }
        }
        assertWithMessage("fixtures whose findings differ from src/test/resources/expected").that(mismatches).isEmpty()
    }

    @Test
    fun `the two clean fixtures have no medium or high findings`() {
        listOf("01_clean_pixel.json", "02_clean_samsung_with_work_profile.json").forEach { name ->
            val findings = engine.evaluate(Evidence.load(File(fixtureDir, name)))
            assertWithMessage(name).that(findings.filter { it.severity != Severity.LOW }).isEmpty()
        }
    }

    @Test
    fun `the maps fixture has nothing above low`() {
        val findings = engine.evaluate(Evidence.load(File(fixtureDir, "06_maps_background_location_legit.json")))

        assertThat(findings.filter { it.severity != Severity.LOW }).isEmpty()
    }

    @Test
    fun `the stalkerware and ioc fixtures have at least one high`() {
        listOf("03_hidden_stalkerware_accessibility.json", "04_ioc_package_match.json").forEach { name ->
            val findings = engine.evaluate(Evidence.load(File(fixtureDir, name)))
            assertWithMessage(name).that(findings.any { it.severity == Severity.HIGH }).isTrue()
        }
    }

    @Test
    fun `two thousand packages evaluate under one second`() {
        val e = Evidence.load(File(fixtureDir, "14_two_thousand_packages.json"))
        engine.evaluate(e)

        val start = System.nanoTime()
        engine.evaluate(e)
        val ms = (System.nanoTime() - start) / 1_000_000

        assertThat(ms).isLessThan(1000)
    }
}
