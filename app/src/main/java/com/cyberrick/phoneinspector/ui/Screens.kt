package com.cyberrick.phoneinspector.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberrick.phoneinspector.R
import com.cyberrick.phoneinspector.actions.RemovalState
import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.feeds.FeedFile
import com.cyberrick.phoneinspector.keep.KeptApp
import com.cyberrick.phoneinspector.net.MonitorState
import com.cyberrick.phoneinspector.rules.Severity
import com.cyberrick.phoneinspector.scan.ScanResult
import com.cyberrick.phoneinspector.ui.model.AppCard
import com.cyberrick.phoneinspector.ui.model.AppFacts
import com.cyberrick.phoneinspector.ui.model.HeadlineKind
import com.cyberrick.phoneinspector.ui.model.ResultModel
import com.cyberrick.phoneinspector.ui.theme.Cpi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val checkLabels =
    mapOf(
        CheckKind.APPS to "Apps and what is inside them",
        CheckKind.CONTROL to "Who has deep control",
        CheckKind.PERMISSIONS to "What can hear or watch you",
        CheckKind.PERSISTENCE to "What runs all the time",
        CheckKind.NETWORK to "Where your data goes",
    )

/** Every screen: full bleed background, inset for the system bars, 20 dp gutters, scrolling column. */
@Composable
fun Page(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Cpi.Background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: String? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("‹ " + stringResource(R.string.detail_back), color = Cpi.Muted) }
        } else {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Cpi.Muted)
        }
        Spacer(modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing.uppercase(), style = MaterialTheme.typography.labelSmall, color = Cpi.Muted)
    }
}

@Composable
fun LaunchScreen(
    keptCount: Int,
    listsDated: String?,
    onScan: () -> Unit,
    onWhatItTouches: () -> Unit,
    onKept: () -> Unit,
    onLists: () -> Unit,
) {
    Page {
        TopBar(stringResource(R.string.app_name))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScoreRing(score = null, caption = stringResource(R.string.ring_not_scanned), modifier = Modifier.padding(top = 12.dp))
        }
        Text(
            stringResource(R.string.launch_question),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        MutedText(stringResource(R.string.launch_body), centered = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        PrimaryButton(stringResource(R.string.scan_now), onClick = onScan)
        GhostButton(stringResource(R.string.what_it_touches), onClick = onWhatItTouches)
        ListsLink(listsDated, onLists)
        if (keptCount > 0) {
            TextButton(onClick = onKept, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.kept_link, keptCount), color = Cpi.Muted)
            }
        }
    }
}

/** The first run sheet, shown once on the first Scan tap, and again from "What a scan touches". */
@Composable
fun ConsentSheet(
    continueText: String,
    onContinue: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    if (onBack != null) BackHandler(onBack = onBack)
    Page {
        TopBar(stringResource(R.string.sheet_title), onBack = onBack)
        SectionHeading(stringResource(R.string.sheet_title))
        Text(stringResource(R.string.sheet_body), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.sheet_lists), style = MaterialTheme.typography.bodyLarge)
        MutedText(stringResource(R.string.sheet_usage))
        MutedText(stringResource(R.string.sheet_watch))
        MutedText(stringResource(R.string.consent_readonly))
        PrimaryButton(continueText, onClick = onContinue)
        MutedText(stringResource(R.string.sheet_credits))
    }
}

@Composable
fun ScanningScreen(
    completed: Set<CheckKind>,
    usageAccessGranted: Boolean,
    onOpenUsageAccess: () -> Unit,
    onCancel: () -> Unit,
) {
    val total = CheckKind.entries.size
    val done = completed.size
    Page {
        TopBar(stringResource(R.string.scanning_title))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScoreRing(
                score = (done * 100 / total),
                centre = "${done * 100 / total}%",
                caption = stringResource(R.string.scanning_checks, done, total),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        CheckKind.entries.forEachIndexed { i, check ->
            val isDone = check in completed
            val needsPermission = check == CheckKind.PERSISTENCE && !usageAccessGranted
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(if (isDone) Cpi.Accent else Cpi.Outline),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isDone) "✓" else "${i + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDone) Cpi.OnAccent else Cpi.Text,
                    )
                }
                Text(
                    checkLabels.getValue(check) +
                        if (needsPermission) " (" + stringResource(R.string.scanning_needs_permission) + ")" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDone) Cpi.Text else Cpi.Muted,
                )
            }
        }
        if (!usageAccessGranted) GhostButton(stringResource(R.string.grant_usage_access), onClick = onOpenUsageAccess)
        if (CheckKind.APPS !in completed ||
            done < total
        ) {
            MutedText(stringResource(R.string.scanning_inside), centered = true, modifier = Modifier.fillMaxWidth())
        }
        GhostButton(stringResource(R.string.cancel), onClick = onCancel)
    }
}

@Composable
fun ResultScreen(
    result: ScanResult,
    cards: List<AppCard>,
    monitor: MonitorState,
    usageAccessGranted: Boolean,
    notice: String?,
    onCard: (AppCard) -> Unit,
    onRemoveAll: () -> Unit,
    onStartWatch: () -> Unit,
    onStopWatch: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onReport: () -> Unit,
    onKept: () -> Unit,
    onRescan: () -> Unit,
    onLists: () -> Unit = {},
) {
    val headline = ResultModel.headline(result)
    val score = result.score
    val highs = cards.count { it.severity == Severity.HIGH }
    Page {
        TopBar("Result", trailing = if (result.kept.isNotEmpty()) "${result.kept.size} kept" else null)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ScoreRing(
                score = score.value,
                size = 96.dp,
                capped = score.cappedBy.isNotEmpty(),
                dotted = score.watchNotRun,
                caption = stringResource(R.string.ring_safety),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    headline.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color =
                        when (headline.kind) {
                            HeadlineKind.THREATS -> Cpi.HighText
                            HeadlineKind.REVIEW -> Cpi.MediumText
                            HeadlineKind.CLEAN -> Cpi.Accent
                        },
                )
                MutedText(headline.subtitle)
            }
        }
        if (notice != null) {
            Text(
                notice,
                style = MaterialTheme.typography.bodyMedium,
                color = Cpi.Accent,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            notice
                    },
            )
        }
        if (score.cappedBy.isNotEmpty()) {
            MutedText(pluralStringResource(R.plurals.capped_note, score.cappedBy.size, score.cappedBy.size))
            if (!usageAccessGranted) GhostButton(stringResource(R.string.grant_usage_access), onClick = onOpenUsageAccess)
        }
        if (highs > 0) {
            PrimaryButton(
                pluralStringResource(R.plurals.remove_threats, highs, highs),
                onClick = onRemoveAll,
                danger = true,
            )
        }
        cards.forEach { card -> ThreatCard(card, onClick = { onCard(card) }) }
        WatchCard(monitor = monitor, watched = !score.watchNotRun, onStart = onStartWatch, onStop = onStopWatch)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Cpi.Surface)
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                pluralStringResource(R.plurals.apps_fine, ResultModel.fineCount(result), ResultModel.fineCount(result)),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onReport) { Text(stringResource(R.string.see_full_report), color = Cpi.Accent) }
        }
        if (result.kept.isNotEmpty()) GhostButton(stringResource(R.string.kept_link, result.kept.size), onClick = onKept)
        GhostButton(stringResource(R.string.rescan), onClick = onRescan)
        ListsLink(result.listsDated.ifBlank { null }, onLists)
    }
}

@Composable
private fun ListsLink(
    dated: String?,
    onLists: () -> Unit,
) {
    TextButton(onClick = onLists, modifier = Modifier.fillMaxWidth()) {
        Text(
            (if (dated != null) stringResource(R.string.lists_link, dated) else stringResource(R.string.lists_link_unknown)) + " ›",
            color = Cpi.Muted,
        )
    }
}

@Composable
fun ListsScreen(
    dates: Map<FeedFile, String?>,
    refresh: RefreshUi,
    weekly: Boolean,
    onRefresh: () -> Unit,
    onWeekly: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Page {
        TopBar(stringResource(R.string.lists_title), onBack = onBack)
        SectionHeading(stringResource(R.string.lists_title))
        MutedText(stringResource(R.string.lists_body))
        FeedFile.entries.forEach { f ->
            val date = dates[f]
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cpi.Surface)
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(f.title, style = MaterialTheme.typography.titleMedium)
                MutedText(if (date != null) stringResource(R.string.list_dated, date) else stringResource(R.string.list_not_included))
            }
        }
        when (refresh) {
            RefreshUi.Idle -> Unit
            RefreshUi.Running -> MutedText(stringResource(R.string.refreshing))
            is RefreshUi.Failed -> Text(refresh.message, color = Cpi.MediumText, style = MaterialTheme.typography.bodyMedium)
            is RefreshUi.Done -> {
                if (refresh.result.updated.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.refresh_updated,
                            refresh.result.updated.joinToString(", ") {
                                it.title
                            },
                        ),
                        color = Cpi.Accent,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (refresh.result.ok) {
                    Text(stringResource(R.string.refresh_done), color = Cpi.Accent, style = MaterialTheme.typography.bodyMedium)
                }
                refresh.result.rejected.forEach { (f, why) ->
                    Text(
                        stringResource(R.string.refresh_rejected, f.title, why),
                        color = Cpi.MediumText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        PrimaryButton(stringResource(R.string.refresh_now), onClick = onRefresh, enabled = refresh !is RefreshUi.Running)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.weekly_refresh), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = weekly, onCheckedChange = onWeekly)
        }
    }
}

@Composable
private fun WatchCard(
    monitor: MonitorState,
    watched: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Cpi.Surface)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.watch_card_title), style = MaterialTheme.typography.titleMedium)
        when (monitor) {
            is MonitorState.Running -> {
                MutedText(
                    pluralStringResource(
                        R.plurals.watch_card_running,
                        monitor.lookups,
                        monitor.lookups,
                        (
                            (monitor.endsAt - System.currentTimeMillis()) /
                                60_000
                        ).coerceAtLeast(0).toInt(),
                    ),
                )
                GhostButton(stringResource(R.string.watch_stop), onClick = onStop)
            }
            is MonitorState.Refused -> {
                MutedText(stringResource(R.string.monitor_refused, monitor.message))
                GhostButton(stringResource(R.string.watch_start), onClick = onStart)
            }
            else -> {
                MutedText(if (watched) stringResource(R.string.watch_card_done) else stringResource(R.string.watch_card_body))
                GhostButton(stringResource(R.string.watch_start), onClick = onStart)
            }
        }
    }
}

@Composable
fun DetailScreen(
    card: AppCard,
    facts: AppFacts,
    position: String,
    removal: RemovalState,
    kept: Boolean,
    onRemove: () -> Unit,
    onOpenSettings: () -> Unit,
    onKeep: () -> Unit,
    onUnkeep: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val worst = card.findings.first()
    Page {
        TopBar(card.label, onBack = onBack, trailing = position)
        LabelPill("${card.cardLabel} · ${card.severity.name.lowercase()}", card.severity)
        Text(card.label, style = MaterialTheme.typography.headlineMedium)
        MutedText(card.packageName)
        Text(card.cardText, style = MaterialTheme.typography.bodyLarge)
        Column(modifier = Modifier.fillMaxWidth()) {
            FactRow(stringResource(R.string.detail_can_read), facts.canRead)
            FactRow(stringResource(R.string.detail_sends_to), facts.sendsTo)
            FactRow(stringResource(R.string.detail_since), facts.since)
            FactRow(stringResource(R.string.detail_runs), facts.runs)
        }
        Text(stringResource(R.string.detail_why), style = MaterialTheme.typography.titleMedium)
        card.findings.forEach { f ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cpi.Surface)
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    f.summary.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    color = severityColor(f.severity),
                )
                MutedText(f.reason)
            }
        }
        if (kept) {
            // A kept app only has a card when something new fired since it was kept.
            Text(stringResource(R.string.kept_new_finding), style = MaterialTheme.typography.bodyLarge)
            PrimaryButton(stringResource(R.string.keep_again), onClick = onKeep)
            GhostButton(stringResource(R.string.unkeep_it), onClick = onUnkeep)
        } else {
            RemovalBlock(card, removal, onRemove, onOpenSettings)
            if (worst.severity == Severity.HIGH) MutedText(stringResource(R.string.stalkerware_line))
            GhostButton(stringResource(R.string.keep_it), onClick = onKeep)
        }
    }
}

@Composable
private fun RemovalBlock(
    card: AppCard,
    removal: RemovalState,
    onRemove: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (removal) {
        is RemovalState.NeedsSettings -> {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Cpi.Surface)
                        .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.two_step_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.two_step_one), style = MaterialTheme.typography.bodyLarge)
                MutedText(removal.blocker.stepText(card.label))
                GhostButton(stringResource(R.string.open_settings), onClick = onOpenSettings)
                Text(stringResource(R.string.two_step_two), style = MaterialTheme.typography.bodyLarge, color = Cpi.Muted)
                PrimaryButton(stringResource(R.string.remove_this_app), onClick = onRemove, enabled = false)
            }
        }
        is RemovalState.NotRemovable -> {
            MutedText(stringResource(R.string.not_removable))
            GhostButton(stringResource(R.string.open_app_info), onClick = onOpenSettings)
        }
        else -> {
            if (removal is RemovalState.StillInstalled) {
                Text(
                    stringResource(R.string.still_installed, removal.reason),
                    color = Cpi.MediumText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            PrimaryButton(stringResource(R.string.remove_this_app), onClick = onRemove, danger = card.severity == Severity.HIGH)
            MutedText(stringResource(R.string.remove_note))
        }
    }
}

@Composable
fun AfterScreen(
    score: Int,
    threatsGone: Boolean,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    var ticked by rememberSaveable { mutableStateOf(setOf<Int>()) }
    Page {
        TopBar("Removed")
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScoreRing(score = score, caption = stringResource(R.string.ring_safety), modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            if (threatsGone) stringResource(R.string.after_threats_gone) else stringResource(R.string.after_review_done),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        MutedText(
            if (threatsGone) stringResource(R.string.after_body) else stringResource(R.string.after_body_light),
            centered = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (threatsGone) {
            ResultModel.PROTECT_STEPS.forEachIndexed { i, step ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = i in ticked, onCheckedChange = { on -> ticked = if (on) ticked + i else ticked - i })
                    Text(step, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        PrimaryButton(stringResource(R.string.done), onClick = onDone)
    }
}

@Composable
fun KeptScreen(
    kept: List<KeptApp>,
    labels: Map<String, String>,
    onUnkeep: (String) -> Unit,
    onBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    BackHandler(onBack = onBack)
    val day = DateTimeFormatter.ofPattern("d MMM yyyy")
    Page {
        TopBar(stringResource(R.string.kept_title), onBack = onBack)
        SectionHeading(stringResource(R.string.kept_title))
        if (kept.isEmpty()) MutedText(stringResource(R.string.kept_empty))
        kept.forEach { k ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Cpi.Surface)
                        .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(labels[k.packageName] ?: k.packageName, style = MaterialTheme.typography.titleMedium)
                MutedText(k.packageName)
                MutedText(stringResource(R.string.kept_since, day.format(Instant.ofEpochMilli(k.keptAt).atZone(zone))))
                GhostButton(stringResource(R.string.unkeep_it), onClick = { onUnkeep(k.packageName) })
            }
        }
    }
}

@Composable
fun ReportScreen(
    result: ScanResult,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Page {
        TopBar(stringResource(R.string.full_report), onBack = onBack)
        SectionHeading(stringResource(R.string.full_report))
        MutedText(stringResource(R.string.report_intro))
        Text(result.verdictLine, style = MaterialTheme.typography.titleMedium)
        PrimaryButton(stringResource(R.string.share_report), onClick = onShare)
        GhostButton(stringResource(R.string.save_report), onClick = onSave)
        Text(text = result.report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun FailedScreen(
    message: String,
    onRetry: () -> Unit,
) {
    Page {
        TopBar(stringResource(R.string.failed_title))
        SectionHeading(stringResource(R.string.failed_title))
        MutedText(message)
        PrimaryButton(stringResource(R.string.try_again), onClick = onRetry)
    }
}
