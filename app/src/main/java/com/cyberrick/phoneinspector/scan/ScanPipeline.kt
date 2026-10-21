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
import com.cyberrick.phoneinspector.rules.Finding
import com.cyberrick.phoneinspector.rules.IocMatcher
import com.cyberrick.phoneinspector.rules.IocSet
import com.cyberrick.phoneinspector.rules.RuleEngine
import com.cyberrick.phoneinspector.rules.RuleSet
import java.time.ZoneId

/** Runs the four inventory collectors, the rule engine and the renderer on a device. Network flows are supplied by the monitor (Phase 11). */
class ScanPipeline(
    private val context: Context,
) {
    data class Result(
        val evidence: Evidence,
        val findings: List<Finding>,
        val report: String,
    )

    private fun asset(name: String): String =
        context.assets
            .open(name)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    fun run(
        flows: List<FlowRecord> = emptyList(),
        networkStatus: CheckResult = CheckResult(CheckKind.NETWORK, CheckStatus.NOT_CHECKED, "network monitor not run"),
        touched: List<String> = listOf(ScanTouched.INVENTORY, ScanTouched.USAGE_ACCESS),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result {
        val systemPackages = SystemPackages.parse(asset(SystemPackages.ASSET))
        val expectations = PermissionExpectations.parse(asset(PermissionExpectations.ASSET))
        val ioc = IocSet.parse(asset("iocs/${IocSet.ASSET}"))
        val rules = RuleSet.parse(asset(RuleSet.ASSET))

        val apps = AppsCollector(AndroidPackageSource(context), systemPackages).collect()
        val controls = ControlCollector(AndroidControlSource(context)).collect()
        val permissions = PermissionsCollector(AndroidPermissionSource(context), expectations).collect()
        val persistence = PersistenceCollector(AndroidPersistenceSource(context)).collect()

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
        val report = ReportRenderer.render(ReportInput(evidence, findings, touched, zone))
        return Result(evidence, findings, report)
    }
}
