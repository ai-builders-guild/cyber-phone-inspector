package com.cyberrick.phoneinspector.report

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.ControlKind
import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.core.model.SensitiveGroup
import com.cyberrick.phoneinspector.rules.Bucket
import com.cyberrick.phoneinspector.rules.Finding
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders REPORT.md in the brief's exact contract. Pure: a function of evidence and findings. Order is fixed:
 * header, what the scan touched, one section per check with each flag's five fields, the verdict line, the three
 * lists, the single next step, and the "cannot see" footer.
 */
object ReportRenderer {
    const val CANNOT_SEE_FOOTER = """## What this scan cannot see

- Rooted or system privileged spyware that hides from the app inventory. An unprivileged app cannot detect it; a forensic examination can.
- Packages an OEM hides from the inventory. The report says when the inventory may be incomplete.
- Anything on the network side: your router, your carrier, a compromised Wi Fi, or a SIM swap.
- Your accounts: a shared Google or Samsung account, a forwarded mailbox, or a cloud backup someone else can open.
- A physical tracker (AirTag, SmartTag) or a second phone. Use the Unknown tracker alerts in Settings for those.
- What a work profile's administrator sees on the work side.
- Traffic from apps that only sent data while the monitor was not running.
- Name lookups by apps that bring their own encrypted DNS (some browsers, notably Chrome) and so never ask the phone's resolver. Their other traffic still counts in the per app byte totals.
"""

    private val sectionTitles =
        mapOf(
            CheckKind.APPS to "Apps",
            CheckKind.CONTROL to "Deep control",
            CheckKind.PERMISSIONS to "Permissions",
            CheckKind.PERSISTENCE to "Persistence",
            CheckKind.NETWORK to "Network",
        )
    private val utc = DateTimeFormatter.ISO_INSTANT
    private val day = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
    private val local = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm zzz", Locale.US)

    fun render(input: ReportInput): String {
        val e = input.evidence
        val verdict = Verdict.of(input.findings, e.checks)
        return buildString {
            header(e, input)
            section("What this scan touched") { input.touched.forEach { appendLine("- $it") } }
            CheckKind.entries.forEach { check -> checkSection(check, e, input.findings.filter { it.check == check }) }
            section("Verdict") {
                appendLine("VERDICT: ${verdict.word}")
                if (verdict.uncovered.isNotEmpty()) {
                    val names = verdict.uncovered.joinToString("; ") { "${sectionTitles.getValue(it.check)} (${it.reason ?: "not run"})" }
                    appendLine("This verdict does not cover: $names.")
                }
            }
            section("Safe") { safeList(e, input.findings) }
            section("Worth removing") { bucketList(input.findings, Bucket.WORTH_REMOVING) }
            section("Needs a professional") { bucketList(input.findings, Bucket.NEEDS_PROFESSIONAL) }
            section("The single most important thing to do next") { appendLine(verdict.nextStep) }
            append(CANNOT_SEE_FOOTER)
        }
    }

    private fun StringBuilder.header(
        e: Evidence,
        input: ReportInput,
    ) {
        appendLine("# Cyber Phone Inspector report")
        appendLine()
        appendLine("Device: ${e.device.manufacturer} ${e.device.model}, Android ${e.device.androidRelease} (API ${e.device.sdkInt})")
        val instant = Instant.ofEpochMilli(e.collectedAt)
        appendLine("Scan time: ${utc.format(instant)} (UTC), ${local.withZone(input.localZone).format(instant)} (local)")
        appendLine("Indicator snapshot: ${e.iocSnapshotDate ?: "none"}")
        appendLine("Read only: this scan changed nothing on the phone.")
        appendLine()
    }

    private fun StringBuilder.section(
        title: String,
        body: StringBuilder.() -> Unit,
    ) {
        appendLine("## $title")
        body()
        appendLine()
    }

    private fun StringBuilder.checkSection(
        check: CheckKind,
        e: Evidence,
        findings: List<Finding>,
    ) {
        section(sectionTitles.getValue(check)) {
            val status = e.checks.firstOrNull { it.check == check }
            when (status?.status) {
                null, CheckStatus.NOT_CHECKED -> appendLine("NOT CHECKED: ${status?.reason ?: "this check did not run"}")
                CheckStatus.PARTIAL -> appendLine("Partial: ${status.reason ?: "some data could not be read"}")
                CheckStatus.CHECKED -> {}
            }
            if (findings.isEmpty()) {
                if (status?.status == CheckStatus.CHECKED) appendLine("Nothing flagged.")
            } else {
                findings.forEach { flag(it, e) }
            }
        }
    }

    private fun StringBuilder.flag(
        f: Finding,
        e: Evidence,
    ) {
        val pkg = f.packageName ?: "(no package)"
        val app = e.apps.firstOrNull { it.packageName == pkg }
        appendLine()
        appendLine("### ${app?.label ?: pkg} ($pkg)")
        appendLine("- What it is: ${f.reason}")
        appendLine("- What it can reach: ${reach(pkg, e)}")
        appendLine("- How long it has been there: ${since(pkg, e)}")
        appendLine("- Where it sends data: ${sendsTo(pkg, e)}")
        appendLine("- Severity: ${f.severity.name.lowercase()}")
        appendLine("- What to do: ${f.remediation}")
    }

    private fun reach(
        pkg: String,
        e: Evidence,
    ): String {
        val powers = mutableListOf<String>()
        val controls =
            e.controls
                .filter { it.packageName == pkg }
                .map { it.kind }
                .toSet()
        if (ControlKind.ACCESSIBILITY in controls) powers += "everything on screen, and it can tap and type for you"
        if (ControlKind.DEVICE_OWNER in controls) powers += "full device management"
        if (ControlKind.PROFILE_OWNER in controls) powers += "the work profile"
        if (ControlKind.DEVICE_ADMIN in controls) powers += "it can lock or wipe the phone"
        if (ControlKind.NOTIFICATION_LISTENER in controls) powers += "every notification, including messages"
        val granted =
            e.permissions
                .filter { it.packageName == pkg && it.granted }
                .map { it.group }
                .toSet()
        if (SensitiveGroup.LOCATION_BACKGROUND in
            granted
        ) {
            powers += "location in the background"
        } else if (SensitiveGroup.LOCATION in granted) {
            powers += "location while in use"
        }
        if (SensitiveGroup.MICROPHONE in granted) powers += "microphone"
        if (SensitiveGroup.CAMERA in granted) powers += "camera"
        e.persistence.firstOrNull { it.packageName == pkg }?.let {
            if (it.bootReceiver) powers += "starts at boot"
            if (it.foregroundServiceMinutes7d > 0) powers += "ran ${it.foregroundServiceMinutes7d / 60} h in the background this week"
        }
        return if (powers.isEmpty()) "nothing beyond its own data" else powers.joinToString(", ")
    }

    private fun since(
        pkg: String,
        e: Evidence,
    ): String {
        val candidates =
            e.apps.filter { it.packageName == pkg }.map { it.firstInstallAt } +
                e.controls.filter { it.packageName == pkg }.map { it.sinceAtLatest }
        val earliest = candidates.minOrNull() ?: return "unknown"
        return "since at latest ${day.format(Instant.ofEpochMilli(earliest))}"
    }

    private fun sendsTo(
        pkg: String,
        e: Evidence,
    ): String {
        val network = e.checks.firstOrNull { it.check == CheckKind.NETWORK }
        if (network?.status == CheckStatus.NOT_CHECKED) return "network not checked"
        val hosts =
            e.flows
                .filter { it.packageName == pkg }
                .mapNotNull { it.host }
                .distinct()
        if (hosts.isEmpty()) return "no network observed"
        val shown = hosts.take(5).joinToString(", ")
        return if (hosts.size > 5) "$shown, +${hosts.size - 5} more" else shown
    }

    private fun StringBuilder.safeList(
        e: Evidence,
        findings: List<Finding>,
    ) {
        val lows = findings.filter { it.bucket == Bucket.SAFE }.groupBy { it.packageName ?: "" }
        lows.forEach { (pkg, fs) -> appendLine("- $pkg: ${fs.joinToString(" ") { it.reason }}") }
        val flagged = findings.mapNotNull { it.packageName }.toSet()
        val others =
            e.apps
                .map { it.packageName }
                .toSet()
                .count { it !in flagged }
        appendLine("- $others other ${if (others == 1) "app" else "apps"} had nothing flagged.")
    }

    private fun StringBuilder.bucketList(
        findings: List<Finding>,
        bucket: Bucket,
    ) {
        val items = findings.filter { it.bucket == bucket }.groupBy { it.packageName ?: "" }
        if (items.isEmpty()) {
            appendLine("- none")
        } else {
            items.forEach { (pkg, fs) ->
                val top = fs.maxBy { it.severity.rank }
                appendLine("- $pkg (${top.severity.name.lowercase()}): ${fs.joinToString(" ") { it.reason }}")
            }
        }
    }
}
