package com.cyberrick.phoneinspector.ui.model

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.ControlKind
import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.core.model.SensitiveGroup
import com.cyberrick.phoneinspector.rules.Finding
import com.cyberrick.phoneinspector.rules.Labels
import com.cyberrick.phoneinspector.rules.Severity
import com.cyberrick.phoneinspector.scan.ScanResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One card on the result screen: an app and its worst finding, in the person's words. Pure data, built once per scan. */
data class AppCard(
    val packageName: String,
    val label: String,
    val severity: Severity,
    val cardLabel: String,
    val cardText: String,
    val findings: List<Finding>,
    val isSystem: Boolean,
) {
    val ruleIds: List<String> get() = findings.map { it.ruleId }
}

/** The four facts on the detail screen. Each is a short phrase; "unknown" or "not watched" is said, never left blank. */
data class AppFacts(
    val canRead: String,
    val sendsTo: String,
    val since: String,
    val runs: String,
)

enum class HeadlineKind { THREATS, REVIEW, CLEAN }

data class Headline(
    val kind: HeadlineKind,
    val title: String,
    val subtitle: String,
)

/** Builds everything the screens show from a [ScanResult]. No Android types, so the mapping is tested on the JVM. */
object ResultModel {
    private val day: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

    /** Cards for apps whose worst finding is high or medium, worst first, then by priority and name. Lows live in the full report. */
    fun cards(result: ScanResult): List<AppCard> {
        val apps = result.evidence.apps.associateBy { it.packageName }
        return result.findings
            .filter { it.packageName != null && it.severity != Severity.LOW }
            .groupBy { it.packageName!! }
            .map { (pkg, fs) ->
                val sorted = fs.sortedWith(compareBy({ -it.severity.rank }, { it.priority }, { it.ruleId }))
                val worst = sorted.first()
                val app = apps[pkg]
                AppCard(
                    packageName = pkg,
                    label = app?.label?.takeIf { it.isNotBlank() } ?: pkg,
                    severity = worst.severity,
                    cardLabel = worst.label.ifBlank { Labels.REVIEW },
                    cardText = worst.cardText.ifBlank { worst.summary.replaceFirstChar { it.uppercase() } + "." },
                    findings = sorted,
                    isSystem = app?.isSystem ?: false,
                )
            }.sortedWith(compareBy({ -it.severity.rank }, { it.findings.first().priority }, { it.label.lowercase() }, { it.packageName }))
    }

    fun headline(result: ScanResult): Headline {
        val threats = result.score.threatApps
        val reviews = result.score.reviewApps
        return when {
            threats > 0 ->
                Headline(
                    HeadlineKind.THREATS,
                    if (threats == 1) "1 threat found" else "$threats threats found",
                    if (reviews > 0) "Plus $reviews to review. Start with the threats." else "Remove it, then protect your accounts.",
                )
            reviews > 0 ->
                Headline(
                    HeadlineKind.REVIEW,
                    if (reviews == 1) "1 thing to review" else "$reviews things to review",
                    "Nothing is known to be stealing from this phone. Each one needs a look.",
                )
            else -> Headline(HeadlineKind.CLEAN, "Nothing found", "No app on this phone matched a threat pattern or a published list.")
        }
    }

    /** How many apps had nothing above a low, for the "N apps are fine" line. */
    fun fineCount(result: ScanResult): Int {
        val flagged =
            result.findings
                .filter { it.severity != Severity.LOW }
                .mapNotNull { it.packageName }
                .toSet()
        return result.evidence.apps.count { it.packageName !in flagged }
    }

    fun facts(
        packageName: String,
        evidence: Evidence,
        zone: ZoneId = ZoneId.systemDefault(),
    ): AppFacts {
        val controls =
            evidence.controls
                .filter { it.packageName == packageName }
                .map { it.kind }
                .toSet()
        val granted =
            evidence.permissions
                .filter { it.packageName == packageName && it.granted }
                .map { it.group }
                .toSet()
        val reads = mutableListOf<String>()
        if (ControlKind.ACCESSIBILITY in controls) reads += "everything on your screen"
        if (ControlKind.NOTIFICATION_LISTENER in controls) reads += "every notification"
        if (ControlKind.DEVICE_ADMIN in controls || ControlKind.DEVICE_OWNER in controls) reads += "device controls (can lock or wipe)"
        if (SensitiveGroup.LOCATION_BACKGROUND in
            granted
        ) {
            reads += "location, even in the background"
        } else if (SensitiveGroup.LOCATION in granted) {
            reads += "location while open"
        }
        if (SensitiveGroup.MICROPHONE in granted) reads += "microphone"
        if (SensitiveGroup.CAMERA in granted) reads += "camera"
        val app = evidence.apps.firstOrNull { it.packageName == packageName }
        if (app != null && app.trackers.isNotEmpty()) reads += "${app.trackers.size} tracker kits inside"

        val network = evidence.checks.firstOrNull { it.check == CheckKind.NETWORK }
        val hosts =
            evidence.flows
                .filter { it.packageName == packageName }
                .mapNotNull { it.host }
                .distinct()
        val sends =
            when {
                network?.status == CheckStatus.NOT_CHECKED -> "not watched yet"
                hosts.isEmpty() -> "nothing seen during the watch"
                hosts.size <= 3 -> hosts.joinToString(", ")
                else -> hosts.take(3).joinToString(", ") + " and ${hosts.size - 3} more"
            }

        val earliest =
            (
                evidence.apps.filter { it.packageName == packageName }.map { it.firstInstallAt } +
                    evidence.controls.filter { it.packageName == packageName }.map { it.sinceAtLatest }
            ).minOrNull()
        val since = earliest?.let { "at latest " + day.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "unknown"

        val persist = evidence.persistence.firstOrNull { it.packageName == packageName }
        val runs =
            buildList {
                if (persist?.bootReceiver == true) add("at boot")
                if (persist != null &&
                    persist.foregroundServiceMinutes7d > 0
                ) {
                    add("${persist.foregroundServiceMinutes7d / 60} h in the background this week")
                }
                if (evidence.checks.firstOrNull { it.check == CheckKind.PERSISTENCE }?.status ==
                    CheckStatus.NOT_CHECKED
                ) {
                    add("not checked (needs usage access)")
                }
            }.ifEmpty { listOf("only when you open it") }

        return AppFacts(
            canRead = reads.ifEmpty { listOf("nothing beyond its own data") }.joinToString(", "),
            sendsTo = sends,
            since = since,
            runs = runs.joinToString(", "),
        )
    }

    /** The after removal checklist. State is local to the screen. */
    val PROTECT_STEPS =
        listOf(
            "Change your email and bank passwords from another device",
            "Turn on two step sign in for Google or Samsung",
            "Check recent card and bank statements",
            "Watch for sign in alerts this week",
            "Tell your bank if anything looks wrong",
        )
}
