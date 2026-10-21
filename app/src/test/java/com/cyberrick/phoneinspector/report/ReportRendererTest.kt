package com.cyberrick.phoneinspector.report

import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.rules.IocMatcher
import com.cyberrick.phoneinspector.rules.IocSet
import com.cyberrick.phoneinspector.rules.RuleEngine
import com.cyberrick.phoneinspector.rules.RuleSet
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File
import java.time.ZoneId

class ReportRendererTest {
    private val moduleDir = File(System.getProperty("user.dir") ?: ".")
    private val fixtureDir = File(moduleDir, "src/test/resources/fixtures")
    private val goldenDir = File(moduleDir, "src/test/resources/golden")
    private val update = System.getProperty("cpi.updateGolden") == "true"
    private val zone: ZoneId = ZoneId.of("America/New_York")

    private val engine: RuleEngine by lazy {
        val rules = RuleSet.parse(File(moduleDir, "src/main/assets/${RuleSet.ASSET}").readText(Charsets.UTF_8))
        val shipped = IocSet.parse(File(moduleDir, "src/main/assets/iocs/${IocSet.ASSET}").readText(Charsets.UTF_8))
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

    private fun fixture(prefix: String) = fixtureDir.listFiles { f -> f.name.startsWith(prefix) }!!.single()

    private fun render(file: File): String {
        val evidence = Evidence.load(file)
        return ReportRenderer.render(ReportInput(evidence, engine.evaluate(evidence), ScanTouched.DEFAULT, zone))
    }

    @Test
    fun `golden report for every fixture is byte identical`() {
        val fixtures = fixtureDir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        assertThat(fixtures.size).isAtLeast(16)
        val mismatches = mutableListOf<String>()
        fixtures.forEach { f ->
            val text = render(f)
            val golden = File(goldenDir, f.nameWithoutExtension + ".md")
            if (update) {
                goldenDir.mkdirs()
                golden.writeText(text, Charsets.UTF_8)
            } else if (!golden.exists() || golden.readText(Charsets.UTF_8) != text) {
                mismatches += f.name
            }
        }
        assertWithMessage("fixtures whose report differs from src/test/resources/golden").that(mismatches).isEmpty()
    }

    @Test
    fun `the brief's end with block appears in order`() {
        val md = render(fixture("03_"))
        val markers =
            listOf("VERDICT: ", "## Safe", "## Worth removing", "## Needs a professional", "## The single most important thing to do next")

        val positions = markers.map { md.indexOf(it) }

        assertThat(positions.all { it >= 0 }).isTrue()
        assertThat(positions).isInOrder()
    }

    @Test
    fun `every flag carries the five fields from the brief in order`() {
        val md = render(fixture("09_"))
        val fields = listOf("What it is:", "What it can reach:", "How long it has been there:", "Where it sends data:", "Severity:")

        val block = md.substringAfter("com.cerberus.antitheft").substringBefore("\n\n")
        val positions = fields.map { block.indexOf(it) }

        assertThat(positions.all { it >= 0 }).isTrue()
        assertThat(positions).isInOrder()
    }

    @Test
    fun `clean fixture says clean with empty removal and professional lists`() {
        val md = render(fixture("01_"))

        assertThat(md).contains("VERDICT: clean")
        assertThat(md).doesNotContain("VERDICT: compromised")
        assertThat(md.section("## Worth removing")).contains("- none")
        assertThat(md.section("## Needs a professional")).contains("- none")
        assertThat(
            md.section("## The single most important thing to do next"),
        ).contains("Nothing to do. Rescan after installing anything new.")
    }

    @Test
    fun `stalkerware fixture says compromised and the next step is the high finding's remediation`() {
        val md = render(fixture("03_"))

        assertThat(md).contains("VERDICT: compromised")
        assertThat(md.section("## Needs a professional")).contains("com.sysconfig.service")
        assertThat(md.section("## The single most important thing to do next")).contains("Preserve the phone as evidence")
    }

    @Test
    fun `medium only fixture is clean with the worth removing list populated`() {
        val md = render(fixture("05_"))

        assertThat(md).contains("VERDICT: clean")
        assertThat(md.section("## Worth removing")).contains("com.update.core")
    }

    @Test
    fun `a not checked check renders NOT CHECKED with the reason and the verdict says it is not covered`() {
        val md = render(fixture("12_"))

        assertThat(md.section("## Persistence")).contains("NOT CHECKED")
        assertThat(md.section("## Persistence")).contains("Usage access not granted")
        assertThat(md.section("## Verdict")).contains("does not cover")
        assertThat(md.section("## Verdict")).contains("Persistence")
    }

    @Test
    fun `where it sends data lists observed hosts or says no network observed`() {
        val stalkerware = render(fixture("03_"))
        val antitheft = render(fixture("09_"))

        assertThat(stalkerware).contains("Where it sends data: upd.srv-telemetry.top")
        assertThat(antitheft).contains("Where it sends data: no network observed")
    }

    @Test
    fun `how long uses the install date as an at latest bound`() {
        val md = render(fixture("09_"))

        assertThat(md).containsMatch("How long it has been there: since at latest 2026-09-\\d\\d")
    }

    @Test
    fun `what it can reach lists the powers the evidence shows`() {
        val md = render(fixture("03_"))

        val reach = md.substringAfter("What it can reach:").substringBefore("\n")
        assertThat(reach).contains("everything on screen")
        assertThat(reach).contains("microphone")
        assertThat(reach).contains("location in the background")
        assertThat(reach).contains("starts at boot")
    }

    @Test
    fun `header has device, utc and local scan time and snapshot date`() {
        val md = render(fixture("01_"))

        assertThat(md).contains("Google Pixel 9, Android 16 (API 36)")
        assertThat(md).contains("2026-10-21T12:00:00Z")
        assertThat(md).contains("2026-10-21 08:00 EDT")
        assertThat(md).contains("Indicator snapshot: 2026-10-21")
    }

    @Test
    fun `safe list carries low notes and a count of apps with nothing flagged`() {
        val md = render(fixture("08_"))

        assertThat(md.section("## Safe")).contains("org.fdroid.example.reader")
        assertThat(md.section("## Safe")).containsMatch("\\d+ other apps? had nothing flagged")
    }

    @Test
    fun `the cannot see footer is present and last`() {
        val md = render(fixture("01_"))

        assertThat(md.trimEnd()).endsWith(ReportRenderer.CANNOT_SEE_FOOTER.trimEnd())
        assertThat(md).contains("Rooted")
    }

    private fun String.section(heading: String): String = substringAfter("$heading\n").substringBefore("\n## ")
}
