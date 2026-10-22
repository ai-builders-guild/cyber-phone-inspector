package com.cyberrick.phoneinspector.keep

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.rules.Bucket
import com.cyberrick.phoneinspector.rules.Finding
import com.cyberrick.phoneinspector.rules.Severity
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KeepTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 1_792_584_000_000L

    private fun f(
        pkg: String,
        sev: Severity,
        rule: String,
    ) = Finding(
        ruleId = rule,
        check = CheckKind.APPS,
        packageName = pkg,
        severity = sev,
        bucket = Bucket.WORTH_REMOVING,
        summary = "s",
        reason = "r",
        remediation = "m",
        evidenceRef = emptyList(),
    )

    @Test
    fun `keeping an app turns its kept findings into one low note and leaves others alone`() {
        val store = KeepStore(tmp.root) { now }
        store.keep("com.family", listOf("ioc_match_visible_store"))
        val findings =
            listOf(f("com.family", Severity.MEDIUM, "ioc_match_visible_store"), f("com.other", Severity.MEDIUM, "device_admin_no_profile"))

        val out = KeepTransform.apply(findings, store.all())

        assertThat(out.map { it.ruleId to it.packageName }).containsExactly(
            KeepTransform.KEPT_RULE to "com.family",
            "device_admin_no_profile" to "com.other",
        )
        val kept = out.single { it.ruleId == KeepTransform.KEPT_RULE }
        assertThat(kept.severity).isEqualTo(Severity.LOW)
        assertThat(kept.bucket).isEqualTo(Bucket.SAFE)
        assertThat(kept.reason).contains("2026-10-21")
    }

    @Test
    fun `a new or stronger finding on a kept app comes back`() {
        val store = KeepStore(tmp.root) { now }
        store.keep("com.family", listOf("ioc_match_visible_store"))
        val stronger = listOf(f("com.family", Severity.HIGH, "ioc_match_visible_store"))
        val different =
            listOf(
                f("com.family", Severity.MEDIUM, "ioc_match_visible_store"),
                f("com.family", Severity.MEDIUM, "beacon_unrecognized_host"),
            )

        assertThat(KeepTransform.apply(stronger, store.all()).single().severity).isEqualTo(Severity.HIGH)
        assertThat(
            KeepTransform.apply(different, store.all()).map {
                it.ruleId
            },
        ).containsExactly(KeepTransform.KEPT_RULE, "beacon_unrecognized_host")
    }

    @Test
    fun `store round trips and unkeep removes`() {
        val store = KeepStore(tmp.root) { now }
        store.keep("com.a", listOf("x"))
        store.keep("com.b", listOf("y"))

        assertThat(KeepStore(tmp.root) { now }.all().map { it.packageName }).containsExactly("com.a", "com.b")
        store.unkeep("com.a")
        assertThat(KeepStore(tmp.root) { now }.all().map { it.packageName }).containsExactly("com.b")
    }

    @Test
    fun `touched lines name every kept app with its date`() {
        val store = KeepStore(tmp.root) { now }
        store.keep("com.family", listOf("x"))

        val lines = KeepTransform.touchedLines(store.all(), mapOf("com.family" to "Findmykids"))

        assertThat(lines).containsExactly("You marked Findmykids (com.family) as yours on 2026-10-21; its findings are not counted.")
    }
}
