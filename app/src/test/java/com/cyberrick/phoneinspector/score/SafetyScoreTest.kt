package com.cyberrick.phoneinspector.score

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.rules.Bucket
import com.cyberrick.phoneinspector.rules.Finding
import com.cyberrick.phoneinspector.rules.Severity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafetyScoreTest {
    private fun f(
        pkg: String,
        sev: Severity,
        rule: String = "r",
    ) = Finding(
        ruleId = rule,
        check = CheckKind.APPS,
        packageName = pkg,
        severity = sev,
        bucket = Bucket.SAFE,
        summary = "s",
        reason = "r",
        remediation = "m",
        evidenceRef = emptyList(),
    )

    private val allChecked = CheckKind.entries.map { CheckResult(it, CheckStatus.CHECKED) }

    @Test
    fun `clean is 100`() {
        assertThat(SafetyScore.of(emptyList(), allChecked).value).isEqualTo(100)
    }

    @Test
    fun `one app with one high or two highs costs 30 once`() {
        assertThat(SafetyScore.of(listOf(f("a", Severity.HIGH)), allChecked).value).isEqualTo(70)
        assertThat(SafetyScore.of(listOf(f("a", Severity.HIGH, "x"), f("a", Severity.HIGH, "y")), allChecked).value).isEqualTo(70)
    }

    @Test
    fun `an app whose worst is medium costs 10 and lows cost nothing`() {
        val findings = listOf(f("a", Severity.MEDIUM), f("a", Severity.LOW), f("b", Severity.LOW), f("c", Severity.LOW))
        assertThat(SafetyScore.of(findings, allChecked).value).isEqualTo(90)
    }

    @Test
    fun `mixed phone sums per app and floors at zero`() {
        val findings =
            listOf(f("a", Severity.HIGH), f("b", Severity.HIGH), f("c", Severity.MEDIUM), f("d", Severity.MEDIUM), f("e", Severity.MEDIUM))
        assertThat(SafetyScore.of(findings, allChecked).value).isEqualTo(10)
        val many = (0 until 10).map { f("p$it", Severity.HIGH) }
        assertThat(SafetyScore.of(many, allChecked).value).isEqualTo(0)
    }

    @Test
    fun `usage access declined caps at 90 with a note and the watch not run does not cap`() {
        val persistenceOff =
            allChecked.map {
                if (it.check ==
                    CheckKind.PERSISTENCE
                ) {
                    it.copy(status = CheckStatus.NOT_CHECKED, reason = "Usage access not granted")
                } else {
                    it
                }
            }
        val watchOff =
            allChecked.map {
                if (it.check ==
                    CheckKind.NETWORK
                ) {
                    it.copy(status = CheckStatus.NOT_CHECKED, reason = "network monitor not run")
                } else {
                    it
                }
            }

        val capped = SafetyScore.of(emptyList(), persistenceOff)
        val uncapped = SafetyScore.of(emptyList(), watchOff)

        assertThat(capped.value).isEqualTo(90)
        assertThat(capped.cappedBy).containsExactly(CheckKind.PERSISTENCE)
        assertThat(uncapped.value).isEqualTo(100)
        assertThat(uncapped.cappedBy).isEmpty()
        assertThat(uncapped.watchNotRun).isTrue()
    }

    @Test
    fun `headline counts apps by their worst severity`() {
        val findings = listOf(f("a", Severity.HIGH, "x"), f("a", Severity.HIGH, "y"), f("b", Severity.MEDIUM), f("c", Severity.LOW))

        val s = SafetyScore.of(findings, allChecked)

        assertThat(s.threatApps).isEqualTo(1)
        assertThat(s.reviewApps).isEqualTo(1)
    }
}
