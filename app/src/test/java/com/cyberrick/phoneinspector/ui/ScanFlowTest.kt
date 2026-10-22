package com.cyberrick.phoneinspector.ui

import com.cyberrick.phoneinspector.actions.RemovalBlocker
import com.cyberrick.phoneinspector.actions.RemovalState
import com.cyberrick.phoneinspector.core.model.AppRecord
import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.DeviceInfo
import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.core.model.InstallSource
import com.cyberrick.phoneinspector.keep.KeepStore
import com.cyberrick.phoneinspector.keep.KeepTransform
import com.cyberrick.phoneinspector.rules.Bucket
import com.cyberrick.phoneinspector.rules.Finding
import com.cyberrick.phoneinspector.rules.Labels
import com.cyberrick.phoneinspector.rules.Severity
import com.cyberrick.phoneinspector.scan.ScanResult
import com.cyberrick.phoneinspector.score.SafetyScore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The screen stack, the removal queue and keep, driven on the JVM with a fake scanner whose findings the test chooses. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanFlowTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMain() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private val now = 1_792_584_000_000L

    private fun app(pkg: String) =
        AppRecord(
            packageName = pkg,
            label = pkg.substringAfterLast('.'),
            firstInstallAt = now,
            lastUpdateAt = now,
            installSource = InstallSource.PLAY_STORE,
            isSystem = false,
            hasLauncherIcon = true,
            api = "t",
            collectedAt = now,
        )

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
        label = Labels.HIDDEN,
        cardText = "c",
    )

    private fun result(
        installed: List<String>,
        findings: List<Finding>,
    ): ScanResult {
        val e =
            Evidence(
                device = DeviceInfo("t", "t", "16", 36),
                collectedAt = now,
                checks =
                    CheckKind.entries.map {
                        CheckResult(it, CheckStatus.CHECKED)
                    },
                apps = installed.map { app(it) },
            )
        return ScanResult(e, findings, "VERDICT: x\n", SafetyScore.of(findings, e.checks))
    }

    /** A fake phone: the scanner reports whatever is still installed. */
    private class Phone(
        var installed: MutableList<String>,
        val findings: List<Finding>,
    ) {
        var scans = 0

        fun scan(): ScanResult {
            scans++
            val fs = findings.filter { it.packageName in installed }
            val e =
                Evidence(
                    device = DeviceInfo("t", "t", "16", 36),
                    collectedAt = 1L,
                    checks =
                        CheckKind.entries.map {
                            CheckResult(it, CheckStatus.CHECKED)
                        },
                    apps =
                        installed.map {
                            AppRecord(
                                packageName = it,
                                label = it,
                                firstInstallAt = 1L,
                                lastUpdateAt = 1L,
                                installSource = InstallSource.PLAY_STORE,
                                isSystem = false,
                                hasLauncherIcon = true,
                                api = "t",
                                collectedAt = 1L,
                            )
                        },
                )
            return ScanResult(e, fs, "VERDICT: x\n", SafetyScore.of(fs, e.checks))
        }
    }

    @Test
    fun `scan lands on result, a card opens detail and back returns`() =
        runTest(dispatcher) {
            val phone = Phone(mutableListOf("com.a", "com.b"), listOf(f("com.a", Severity.HIGH, "h")))
            val vm = ScanViewModel(scanner = { phone.scan() })

            vm.start()
            advanceUntilIdle()
            assertThat(vm.screen.value).isEqualTo(Screen.Result)
            vm.open(vm.cards().single())
            assertThat(vm.screen.value).isEqualTo(Screen.Detail("com.a"))
            vm.back()
            assertThat(vm.screen.value).isEqualTo(Screen.Result)
        }

    @Test
    fun `removing a threat asks for the dialog, a gone package rescans and lands on After`() =
        runTest(dispatcher) {
            val phone = Phone(mutableListOf("com.a", "com.b"), listOf(f("com.a", Severity.HIGH, "h")))
            val vm = ScanViewModel(scanner = { phone.scan() })
            val events = mutableListOf<UiEvent>()
            val collector = launch { vm.events.toList(events) }
            vm.start()
            advanceUntilIdle()

            vm.requestRemoval("com.a", blocker = null)
            advanceUntilIdle()
            assertThat(events).containsExactly(UiEvent.LaunchUninstall("com.a"))
            phone.installed.remove("com.a")
            vm.afterDialog("com.a", installed = false, blocker = null, label = "Alpha")
            advanceUntilIdle()

            assertThat(phone.scans).isEqualTo(2)
            assertThat(vm.screen.value).isEqualTo(Screen.After(threatsGone = true))
            assertThat(vm.notice.value).isEqualTo("Alpha is gone.")
            assertThat(vm.cards()).isEmpty()
            vm.back()
            assertThat(vm.screen.value).isEqualTo(Screen.Result)
            collector.cancel()
        }

    @Test
    fun `a cancelled dialog reads still installed and does not rescan`() =
        runTest(dispatcher) {
            val phone = Phone(mutableListOf("com.a"), listOf(f("com.a", Severity.HIGH, "h")))
            val vm = ScanViewModel(scanner = { phone.scan() })
            vm.start()
            advanceUntilIdle()

            vm.requestRemoval("com.a", null)
            vm.afterDialog("com.a", installed = true, blocker = null)
            advanceUntilIdle()

            assertThat(vm.removal.value["com.a"]).isInstanceOf(RemovalState.StillInstalled::class.java)
            assertThat(phone.scans).isEqualTo(1)
            assertThat(vm.screen.value).isEqualTo(Screen.Result)
        }

    @Test
    fun `remove all highs runs one dialog after another and then rescans once`() =
        runTest(dispatcher) {
            val phone =
                Phone(
                    mutableListOf("com.a", "com.b", "com.c"),
                    listOf(f("com.a", Severity.HIGH, "h"), f("com.b", Severity.HIGH, "h"), f("com.c", Severity.MEDIUM, "m")),
                )
            val vm = ScanViewModel(scanner = { phone.scan() })
            val events = mutableListOf<UiEvent>()
            val collector = launch { vm.events.toList(events) }
            vm.start()
            advanceUntilIdle()

            vm.removeAllHighs { null }
            advanceUntilIdle()
            phone.installed.remove("com.a")
            vm.afterDialog("com.a", installed = false, blocker = null)
            advanceUntilIdle()
            assertThat(events.map { (it as UiEvent.LaunchUninstall).packageName }).containsExactly("com.a", "com.b").inOrder()
            assertThat(phone.scans).isEqualTo(1)
            phone.installed.remove("com.b")
            vm.afterDialog("com.b", installed = false, blocker = null)
            advanceUntilIdle()

            assertThat(phone.scans).isEqualTo(2)
            assertThat(vm.screen.value).isEqualTo(Screen.After(threatsGone = true))
            assertThat(vm.cards().map { it.packageName }).containsExactly("com.c")
            collector.cancel()
        }

    @Test
    fun `an admin holder walks the two step path and the uninstall step opens once Settings is done`() =
        runTest(dispatcher) {
            val phone = Phone(mutableListOf("com.a"), listOf(f("com.a", Severity.HIGH, "h")))
            val vm = ScanViewModel(scanner = { phone.scan() })
            val events = mutableListOf<UiEvent>()
            val collector = launch { vm.events.toList(events) }
            vm.start()
            advanceUntilIdle()

            vm.requestRemoval("com.a", RemovalBlocker.DEVICE_ADMIN)
            assertThat(vm.removal.value["com.a"]).isEqualTo(RemovalState.NeedsSettings("com.a", RemovalBlocker.DEVICE_ADMIN))
            vm.openSettings("com.a", RemovalBlocker.DEVICE_ADMIN)
            advanceUntilIdle()
            assertThat(events).containsExactly(UiEvent.OpenSettings("com.a", RemovalBlocker.DEVICE_ADMIN))
            vm.afterSettings("com.a", RemovalBlocker.DEVICE_ADMIN)
            assertThat(vm.removal.value["com.a"]).isInstanceOf(RemovalState.NeedsSettings::class.java)
            vm.afterSettings("com.a", null)
            assertThat(vm.removal.value["com.a"]).isEqualTo(RemovalState.Idle)

            vm.requestRemoval("com.a", null)
            advanceUntilIdle()
            assertThat(events.last()).isEqualTo(UiEvent.LaunchUninstall("com.a"))
            collector.cancel()
        }

    @Test
    fun `keep turns the card into a note without a rescan and un keep brings it back`() =
        runTest(dispatcher) {
            val phone = Phone(mutableListOf("com.a"), listOf(f("com.a", Severity.MEDIUM, "m")))
            val store = KeepStore(tmp.root) { now }
            val keeper =
                object : Keeper {
                    override fun keep(
                        packageName: String,
                        ruleIds: List<String>,
                        worst: Severity,
                    ) = store.keep(packageName, ruleIds, worst)

                    override fun unkeep(packageName: String) = store.unkeep(packageName)

                    override fun keptCount(): Int = store.all().size

                    override fun rekeep(result: ScanResult): ScanResult {
                        val kept = store.all()
                        val fs = KeepTransform.apply(result.rawFindings, kept)
                        return result.copy(findings = fs, score = SafetyScore.of(fs, result.evidence.checks), kept = kept)
                    }
                }
            val vm = ScanViewModel(scanner = { phone.scan() }, keeper = keeper)
            vm.start()
            advanceUntilIdle()
            assertThat(vm.result!!.score.value).isEqualTo(90)

            vm.keep(vm.cards().single())

            assertThat(phone.scans).isEqualTo(1)
            assertThat(vm.cards()).isEmpty()
            assertThat(vm.result!!.score.value).isEqualTo(100)
            assertThat(
                vm.result!!
                    .findings
                    .single()
                    .ruleId,
            ).isEqualTo(KeepTransform.KEPT_RULE)
            assertThat(vm.keptCount.value).isEqualTo(1)
            assertThat(vm.screen.value).isEqualTo(Screen.Result)

            vm.unkeep("com.a")

            assertThat(vm.cards().single().packageName).isEqualTo("com.a")
            assertThat(vm.keptCount.value).isEqualTo(0)
        }

    @Test
    fun `cancel returns home and a plain result is not the after screen`() =
        runTest(dispatcher) {
            val phone = Phone(mutableListOf("com.a"), emptyList())
            val vm = ScanViewModel(scanner = { phone.scan() })
            vm.start()
            advanceUntilIdle()
            assertThat(vm.screen.value).isEqualTo(Screen.Result)
            vm.reset()
            assertThat(vm.screen.value).isEqualTo(Screen.Home)
            val unused = result(listOf("com.a"), emptyList())
            assertThat(unused.score.value).isEqualTo(100)
        }
}
