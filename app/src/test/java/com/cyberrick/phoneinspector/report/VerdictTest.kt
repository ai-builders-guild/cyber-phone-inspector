package com.cyberrick.phoneinspector.report

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.rules.Bucket
import com.cyberrick.phoneinspector.rules.Finding
import com.cyberrick.phoneinspector.rules.Severity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VerdictTest {
    private fun finding(severity: Severity) =
        Finding(
            ruleId = "r",
            check = CheckKind.APPS,
            packageName = "com.x",
            severity = severity,
            bucket = Bucket.SAFE,
            summary = "short",
            reason = "reason",
            remediation = "fix it",
            evidenceRef = emptyList(),
        )

    private val allChecked = CheckKind.entries.map { CheckResult(it, CheckStatus.CHECKED) }

    @Test
    fun `compromised only when at least one high`() {
        assertThat(Verdict.of(listOf(finding(Severity.MEDIUM), finding(Severity.LOW)), allChecked).word).isEqualTo("clean")
        assertThat(Verdict.of(listOf(finding(Severity.HIGH)), allChecked).word).isEqualTo("compromised")
        assertThat(Verdict.of(emptyList(), allChecked).word).isEqualTo("clean")
    }

    @Test
    fun `not checked checks are listed as uncovered`() {
        val checks =
            allChecked.map {
                if (it.check ==
                    CheckKind.NETWORK
                ) {
                    it.copy(status = CheckStatus.NOT_CHECKED, reason = "monitor not run")
                } else {
                    it
                }
            }

        val v = Verdict.of(emptyList(), checks)

        assertThat(v.word).isEqualTo("clean")
        assertThat(v.uncovered.map { it.check }).containsExactly(CheckKind.NETWORK)
    }

    @Test
    fun `next step is the top finding's remediation or the rescan advice`() {
        assertThat(Verdict.of(listOf(finding(Severity.LOW), finding(Severity.HIGH)), allChecked).nextStep).isEqualTo("fix it")
        assertThat(Verdict.of(emptyList(), allChecked).nextStep).isEqualTo(Verdict.NOTHING_TO_DO)
    }
}
