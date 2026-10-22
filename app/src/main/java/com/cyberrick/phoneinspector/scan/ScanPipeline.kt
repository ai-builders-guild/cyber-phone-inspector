package com.cyberrick.phoneinspector.scan

import android.content.Context
import android.os.Build
import com.cyberrick.phoneinspector.collect.AndroidApkSource
import com.cyberrick.phoneinspector.collect.AndroidControlSource
import com.cyberrick.phoneinspector.collect.AndroidDexSource
import com.cyberrick.phoneinspector.collect.AndroidPackageSource
import com.cyberrick.phoneinspector.collect.AndroidPermissionSource
import com.cyberrick.phoneinspector.collect.AndroidPersistenceSource
import com.cyberrick.phoneinspector.collect.ApkHashCollector
import com.cyberrick.phoneinspector.collect.ApkRef
import com.cyberrick.phoneinspector.collect.ApkSource
import com.cyberrick.phoneinspector.collect.AppScanCache
import com.cyberrick.phoneinspector.collect.AppsCollector
import com.cyberrick.phoneinspector.collect.ControlCollector
import com.cyberrick.phoneinspector.collect.DexSource
import com.cyberrick.phoneinspector.collect.PermissionExpectations
import com.cyberrick.phoneinspector.collect.PermissionsCollector
import com.cyberrick.phoneinspector.collect.PersistenceCollector
import com.cyberrick.phoneinspector.collect.SystemPackages
import com.cyberrick.phoneinspector.collect.TrackerScanCollector
import com.cyberrick.phoneinspector.collect.TrackerScanResult
import com.cyberrick.phoneinspector.core.model.AppRecord
import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.DeviceInfo
import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.core.model.FlowRecord
import com.cyberrick.phoneinspector.feeds.FeedFiles
import com.cyberrick.phoneinspector.keep.KeepStore
import com.cyberrick.phoneinspector.keep.KeepTransform
import com.cyberrick.phoneinspector.net.FlowStore
import com.cyberrick.phoneinspector.report.ReportInput
import com.cyberrick.phoneinspector.report.ReportRenderer
import com.cyberrick.phoneinspector.report.ScanTouched
import com.cyberrick.phoneinspector.rules.IocMatcher
import com.cyberrick.phoneinspector.rules.IocSet
import com.cyberrick.phoneinspector.rules.KnownServices
import com.cyberrick.phoneinspector.rules.RuleEngine
import com.cyberrick.phoneinspector.rules.RuleSet
import com.cyberrick.phoneinspector.score.SafetyScore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Runs the four inventory collectors in parallel, then the v1.1 package hash and tracker scans over the non system
 * apps, then the rule engine (with the local threat lists), the kept list and the renderer. Structured: cancelling
 * the caller cancels every collector. Network flows come from the monitor (Phase 11).
 */
class ScanPipeline(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private fun asset(name: String): String =
        context.assets
            .open(name)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    /** The latest monitor result if it ended within [FlowStore.FRESH_MS]; otherwise the Network check is NOT_CHECKED. */
    fun latestNetwork(): Pair<List<FlowRecord>, CheckResult> {
        val store = FlowStore(context.filesDir)
        val result = store.load()
        return if (result != null && store.isFresh(FlowStore.FRESH_MS)) {
            val minutes = ((result.endedAt - result.startedAt) / 60_000).coerceAtLeast(1)
            result.flows to
                CheckResult(
                    CheckKind.NETWORK,
                    CheckStatus.CHECKED,
                    "monitor ran $minutes min, ${result.lookups} lookups, ended ${java.time.Instant.ofEpochMilli(result.endedAt)}",
                )
        } else {
            emptyList<FlowRecord>() to CheckResult(CheckKind.NETWORK, CheckStatus.NOT_CHECKED, "network monitor not run in the last 24 h")
        }
    }

    suspend fun run(
        onProgress: (CheckKind) -> Unit = {},
        network: Pair<List<FlowRecord>, CheckResult> = latestNetwork(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ScanResult =
        withContext(io) {
            val (flows, networkStatus) = network
            val systemPackages = SystemPackages.parse(asset(SystemPackages.ASSET))
            val expectations = PermissionExpectations.parse(asset(PermissionExpectations.ASSET))
            val ioc = IocSet.parse(asset("iocs/${IocSet.ASSET}"))
            val rules = RuleSet.parse(asset(RuleSet.ASSET))
            val known = KnownServices.parse(asset(KnownServices.ASSET))
            val intel = FeedFiles(context).threatIntel()
            val persistenceSource = AndroidPersistenceSource(context)

            val (apps, controls, permissions, persistence) =
                coroutineScope {
                    val a =
                        async { AppsCollector(AndroidPackageSource(context), systemPackages).collect().also { onProgress(CheckKind.APPS) } }
                    val c = async { ControlCollector(AndroidControlSource(context)).collect().also { onProgress(CheckKind.CONTROL) } }
                    val p =
                        async {
                            PermissionsCollector(
                                AndroidPermissionSource(context),
                                expectations,
                            ).collect().also { onProgress(CheckKind.PERMISSIONS) }
                        }
                    val s = async { PersistenceCollector(persistenceSource).collect().also { onProgress(CheckKind.PERSISTENCE) } }
                    Collected(a.await(), c.await(), p.await(), s.await())
                }
            // v1.1: file hash and tracker scan, non system apps only, checked against the lists on this phone.
            // Both are slow on big apps, so a package unchanged since the last scan comes from the cache.
            val cache = AppScanCache(context.filesDir)
            val keys = apps.records.associate { it.packageName to cache.key(it.packageName, it.versionCode, it.lastUpdateAt) }
            val cachedHashes = keys.mapNotNull { (pkg, key) -> cache.hash(key)?.let { pkg to it } }.toMap()
            val cachedTrackers =
                keys
                    .mapNotNull { (pkg, key) ->
                        cache.trackers(key, intel.trackers.generatedAt)?.let { pkg to it }
                    }.toMap()
            val (freshHashes, freshTrackers) =
                coroutineScope {
                    val h = async { ApkHashCollector(uncached(AndroidApkSource(context), cachedHashes.keys)).collect() }
                    // The tracker scan is CPU bound (inflate and parse), so the uncached packages are split across workers.
                    val dex = AndroidDexSource(context)
                    val todo = dex.nonSystemPackages().filter { it !in cachedTrackers }
                    val parts = todo.withIndex().groupBy({ it.index % TRACKER_WORKERS }, { it.value }).values
                    val t = parts.map { part -> async { TrackerScanCollector(only(dex, part.toSet()), intel.trackers).collect() } }
                    h.await() to
                        t.awaitAll().fold(TrackerScanResult(emptyMap(), emptySet(), 0)) { acc, r ->
                            TrackerScanResult(acc.trackers + r.trackers, acc.budgetHit + r.budgetHit, acc.unreadable + r.unreadable)
                        }
                }
            val hashes = freshHashes.copy(hashes = cachedHashes + freshHashes.hashes)
            val trackers = freshTrackers.copy(trackers = cachedTrackers + freshTrackers.trackers)
            cache.put(
                keys
                    .filterKeys { it in hashes.hashes || it in trackers.trackers }
                    .map { (pkg, key) ->
                        val scanned = pkg in trackers.trackers && pkg !in trackers.budgetHit
                        key to
                            AppScanCache.Entry(
                                hashes.hashes[pkg],
                                if (scanned) intel.trackers.generatedAt else "",
                                trackers.trackers[pkg].orEmpty(),
                            )
                    }.toMap(),
            )
            val enrichedApps =
                apps.records.map { app ->
                    enrich(app, hashes.hashes[app.packageName], trackers.trackers[app.packageName], app.packageName in trackers.budgetHit)
                }
            val appsCheck =
                if (trackers.budgetHit.isEmpty()) {
                    apps.check
                } else {
                    apps.check.copy(
                        status = if (apps.check.status == CheckStatus.CHECKED) CheckStatus.PARTIAL else apps.check.status,
                        reason =
                            listOfNotNull(
                                apps.check.reason,
                                "tracker scan ran out of time on ${trackers.budgetHit.size} apps",
                            ).joinToString("; "),
                    )
                }
            onProgress(CheckKind.NETWORK)
            val kept = KeepStore(context.filesDir).all()

            val touched =
                buildList {
                    add(ScanTouched.INVENTORY)
                    if (persistenceSource.usageAccessGranted()) add(ScanTouched.USAGE_ACCESS)
                    if (networkStatus.status != CheckStatus.NOT_CHECKED) add(ScanTouched.LOCAL_VPN)
                    add(ScanTouched.threatLists(intel))
                }
            val labels = apps.records.associate { it.packageName to it.label }
            val evidence =
                Evidence(
                    device = DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
                    collectedAt = System.currentTimeMillis(),
                    checks = listOf(appsCheck, controls.check, permissions.check, persistence.check, networkStatus),
                    apps = enrichedApps,
                    controls = controls.records,
                    permissions = permissions.records,
                    persistence = persistence.records,
                    flows = flows,
                    iocSnapshotDate = ioc.generatedAt,
                ).withDuplicatesFlagged()
            val raw = RuleEngine(rules, IocMatcher(ioc), known, intel).evaluate(evidence)
            val findings = KeepTransform.apply(raw, kept)
            val touchedAll = touched + KeepTransform.touchedLines(kept, labels)
            ScanResult(
                evidence,
                findings,
                ReportRenderer.render(ReportInput(evidence, findings, touchedAll, zone), known),
                SafetyScore.of(findings, evidence.checks),
                kept,
                rawFindings = raw,
                touched = touched,
            )
        }

    /** Re applies the kept list to a finished scan without rescanning: findings, report and score all change together. */
    fun rekeep(
        result: ScanResult,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ScanResult {
        val kept = KeepStore(context.filesDir).all()
        val known = KnownServices.parse(asset(KnownServices.ASSET))
        val labels = result.evidence.apps.associate { it.packageName to it.label }
        val findings = KeepTransform.apply(result.rawFindings, kept)
        val touched = result.touched + KeepTransform.touchedLines(kept, labels)
        return result.copy(
            findings = findings,
            report = ReportRenderer.render(ReportInput(result.evidence, findings, touched, zone), known),
            score = SafetyScore.of(findings, result.evidence.checks),
            kept = kept,
        )
    }

    /** The DEX source restricted to one worker's share of the uncached packages. */
    private fun only(
        source: DexSource,
        packages: Set<String>,
    ): DexSource =
        object : DexSource by source {
            override fun nonSystemPackages(): List<String> = source.nonSystemPackages().filter { it in packages }
        }

    private fun uncached(
        source: ApkSource,
        skip: Set<String>,
    ): ApkSource =
        object : ApkSource by source {
            override fun nonSystemApks(): List<ApkRef> = source.nonSystemApks().filter { it.packageName !in skip }
        }

    private fun enrich(
        app: AppRecord,
        sha256: String?,
        trackers: List<String>?,
        budgetHit: Boolean,
    ): AppRecord =
        if (sha256 == null &&
            trackers == null &&
            !budgetHit
        ) {
            app
        } else {
            app.copy(apkSha256 = sha256, trackers = trackers.orEmpty(), scanBudgetHit = budgetHit)
        }

    private companion object {
        const val TRACKER_WORKERS = 4
    }

    private data class Collected<A, B, C, D>(
        val a: A,
        val b: B,
        val c: C,
        val d: D,
    )
}
