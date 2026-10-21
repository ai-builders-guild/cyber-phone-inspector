package com.cyberrick.phoneinspector.scan

import android.content.Context
import android.os.Build
import com.cyberrick.phoneinspector.collect.AndroidControlSource
import com.cyberrick.phoneinspector.collect.AndroidPackageSource
import com.cyberrick.phoneinspector.collect.AndroidPermissionSource
import com.cyberrick.phoneinspector.collect.AndroidPersistenceSource
import com.cyberrick.phoneinspector.collect.AppsCollector
import com.cyberrick.phoneinspector.collect.ControlCollector
import com.cyberrick.phoneinspector.collect.PermissionExpectations
import com.cyberrick.phoneinspector.collect.PermissionsCollector
import com.cyberrick.phoneinspector.collect.PersistenceCollector
import com.cyberrick.phoneinspector.collect.SystemPackages
import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.DeviceInfo
import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.core.model.FlowRecord
import com.cyberrick.phoneinspector.report.ReportInput
import com.cyberrick.phoneinspector.report.ReportRenderer
import com.cyberrick.phoneinspector.report.ScanTouched
import com.cyberrick.phoneinspector.rules.IocMatcher
import com.cyberrick.phoneinspector.rules.IocSet
import com.cyberrick.phoneinspector.rules.RuleEngine
import com.cyberrick.phoneinspector.rules.RuleSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Runs the four inventory collectors in parallel, then the rule engine and the renderer. Structured: cancelling
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

    suspend fun run(
        onProgress: (CheckKind) -> Unit = {},
        flows: List<FlowRecord> = emptyList(),
        networkStatus: CheckResult = CheckResult(CheckKind.NETWORK, CheckStatus.NOT_CHECKED, "network monitor not run"),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ScanResult =
        withContext(io) {
            val systemPackages = SystemPackages.parse(asset(SystemPackages.ASSET))
            val expectations = PermissionExpectations.parse(asset(PermissionExpectations.ASSET))
            val ioc = IocSet.parse(asset("iocs/${IocSet.ASSET}"))
            val rules = RuleSet.parse(asset(RuleSet.ASSET))
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
            onProgress(CheckKind.NETWORK)

            val touched =
                buildList {
                    add(ScanTouched.INVENTORY)
                    if (persistenceSource.usageAccessGranted()) add(ScanTouched.USAGE_ACCESS)
                    if (networkStatus.status != CheckStatus.NOT_CHECKED) add(ScanTouched.LOCAL_VPN)
                }
            val evidence =
                Evidence(
                    device = DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
                    collectedAt = System.currentTimeMillis(),
                    checks = listOf(apps.check, controls.check, permissions.check, persistence.check, networkStatus),
                    apps = apps.records,
                    controls = controls.records,
                    permissions = permissions.records,
                    persistence = persistence.records,
                    flows = flows,
                    iocSnapshotDate = ioc.generatedAt,
                ).withDuplicatesFlagged()
            val findings = RuleEngine(rules, IocMatcher(ioc)).evaluate(evidence)
            ScanResult(evidence, findings, ReportRenderer.render(ReportInput(evidence, findings, touched, zone)))
        }

    private data class Collected<A, B, C, D>(
        val a: A,
        val b: B,
        val c: C,
        val d: D,
    )
}
