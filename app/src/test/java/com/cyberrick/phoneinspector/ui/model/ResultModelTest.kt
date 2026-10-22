package com.cyberrick.phoneinspector.ui.model

import com.cyberrick.phoneinspector.core.model.AppRecord
import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.ControlKind
import com.cyberrick.phoneinspector.core.model.ControlRecord
import com.cyberrick.phoneinspector.core.model.DeviceInfo
import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.core.model.FlowRecord
import com.cyberrick.phoneinspector.core.model.InstallSource
import com.cyberrick.phoneinspector.core.model.PermissionRecord
import com.cyberrick.phoneinspector.core.model.PersistenceRecord
import com.cyberrick.phoneinspector.core.model.SensitiveGroup
import com.cyberrick.phoneinspector.rules.Bucket
import com.cyberrick.phoneinspector.rules.Finding
import com.cyberrick.phoneinspector.rules.Labels
import com.cyberrick.phoneinspector.rules.Severity
import com.cyberrick.phoneinspector.scan.ScanResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

class ResultModelTest {
    private val now = 1_792_584_000_000L

    private fun app(
        pkg: String,
        label: String = pkg,
        system: Boolean = false,
    ) = AppRecord(
        packageName = pkg,
        label = label,
        firstInstallAt = now - 20L * 86_400_000,
        lastUpdateAt = now,
        installSource = if (system) InstallSource.SYSTEM else InstallSource.PLAY_STORE,
        isSystem = system,
        hasLauncherIcon = true,
        api = "t",
        collectedAt = now,
    )

    private fun f(
        pkg: String,
        sev: Severity,
        rule: String,
        label: String = Labels.REVIEW,
        card: String = "Card $rule.",
        priority: Int = 100,
    ) = Finding(
        ruleId = rule,
        check = CheckKind.APPS,
        packageName = pkg,
        severity = sev,
        priority = priority,
        bucket = Bucket.WORTH_REMOVING,
        summary = "summary $rule",
        reason = "r",
        remediation = "m",
        evidenceRef = emptyList(),
        label = label,
        cardText = card,
    )

    private fun evidence(
        apps: List<AppRecord>,
        network: CheckStatus = CheckStatus.CHECKED,
    ) = Evidence(
        device = DeviceInfo("t", "t", "16", 36),
        collectedAt = now,
        checks = CheckKind.entries.map { CheckResult(it, if (it == CheckKind.NETWORK) network else CheckStatus.CHECKED) },
        apps = apps,
    )

    private fun result(
        apps: List<AppRecord>,
        findings: List<Finding>,
    ) = ScanResult(evidence(apps), findings, "VERDICT: x\n")

    @Test
    fun `cards group findings per app, worst first, with the worst finding's label and card text`() {
        val r =
            result(
                listOf(app("com.a", "Alpha"), app("com.b", "Beta"), app("com.c", "Gamma")),
                listOf(
                    f("com.b", Severity.MEDIUM, "m1"),
                    f("com.a", Severity.MEDIUM, "m2", priority = 50),
                    f("com.a", Severity.HIGH, "h1", label = Labels.HIDDEN, card = "No icon."),
                    f("com.c", Severity.LOW, "l1"),
                ),
            )

        val cards = ResultModel.cards(r)

        assertThat(cards.map { it.packageName }).containsExactly("com.a", "com.b").inOrder()
        assertThat(cards[0].severity).isEqualTo(Severity.HIGH)
        assertThat(cards[0].cardLabel).isEqualTo(Labels.HIDDEN)
        assertThat(cards[0].cardText).isEqualTo("No icon.")
        assertThat(cards[0].label).isEqualTo("Alpha")
        assertThat(cards[0].ruleIds).containsExactly("h1", "m2").inOrder()
        assertThat(ResultModel.fineCount(r)).isEqualTo(1)
    }

    @Test
    fun `headline counts apps by worst severity and reads clean when nothing is above low`() {
        val two =
            result(
                listOf(app("com.a"), app("com.b"), app("com.c")),
                listOf(
                    f("com.a", Severity.HIGH, "h"),
                    f("com.a", Severity.HIGH, "h2"),
                    f("com.b", Severity.HIGH, "h"),
                    f("com.c", Severity.MEDIUM, "m"),
                ),
            )
        val review = result(listOf(app("com.c")), listOf(f("com.c", Severity.MEDIUM, "m")))
        val clean = result(listOf(app("com.c")), listOf(f("com.c", Severity.LOW, "l")))

        assertThat(ResultModel.headline(two).title).isEqualTo("2 threats found")
        assertThat(ResultModel.headline(two).subtitle).contains("1 to review")
        assertThat(ResultModel.headline(review).let { it.kind to it.title }).isEqualTo(HeadlineKind.REVIEW to "1 thing to review")
        assertThat(ResultModel.headline(clean).let { it.kind to it.title }).isEqualTo(HeadlineKind.CLEAN to "Nothing found")
    }

    @Test
    fun `facts say what it reads, where it sends, since when and how it runs`() {
        val e =
            evidence(listOf(app("com.a"))).copy(
                controls = listOf(ControlRecord(ControlKind.ACCESSIBILITY, "com.a", null, now - 40L * 86_400_000, "t", now)),
                permissions = listOf(PermissionRecord("com.a", SensitiveGroup.MICROPHONE, true, true, api = "t", collectedAt = now)),
                persistence =
                    listOf(
                        PersistenceRecord("com.a", bootReceiver = true, foregroundServiceMinutes7d = 4260, api = "t", collectedAt = now),
                    ),
                flows = listOf(FlowRecord(1, "com.a", "c2.example", observedAt = now, api = "t", collectedAt = now)),
            )

        val facts = ResultModel.facts("com.a", e, ZoneId.of("UTC"))

        assertThat(facts.canRead).isEqualTo("everything on your screen, microphone")
        assertThat(facts.sendsTo).isEqualTo("c2.example")
        assertThat(facts.since).isEqualTo("at latest 11 Sep 2026")
        assertThat(facts.runs).isEqualTo("at boot, 71 h in the background this week")
    }

    @Test
    fun `facts never go blank`() {
        val e = evidence(listOf(app("com.a")), network = CheckStatus.NOT_CHECKED)

        val facts = ResultModel.facts("com.a", e, ZoneId.of("UTC"))

        assertThat(facts.canRead).isEqualTo("nothing beyond its own data")
        assertThat(facts.sendsTo).isEqualTo("not watched yet")
        assertThat(facts.runs).isEqualTo("only when you open it")
    }
}
