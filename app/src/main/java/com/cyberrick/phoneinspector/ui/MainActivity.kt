package com.cyberrick.phoneinspector.ui

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.cyberrick.phoneinspector.actions.RemovalState
import com.cyberrick.phoneinspector.actions.RemoveAction
import com.cyberrick.phoneinspector.collect.AndroidPersistenceSource
import com.cyberrick.phoneinspector.keep.KeepStore
import com.cyberrick.phoneinspector.net.DnsTunnelService
import com.cyberrick.phoneinspector.net.MonitorState
import com.cyberrick.phoneinspector.net.MonitorWindow
import com.cyberrick.phoneinspector.report.ReportExporter
import com.cyberrick.phoneinspector.rules.Severity
import com.cyberrick.phoneinspector.scan.ScanPipeline
import com.cyberrick.phoneinspector.scan.ScanResult
import com.cyberrick.phoneinspector.service.ScanService
import com.cyberrick.phoneinspector.ui.model.ResultModel
import com.cyberrick.phoneinspector.ui.theme.CpiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val remove by lazy { RemoveAction(this) }
    private var pendingUninstall: String? = null
    private var pendingSettings: String? = null

    private val viewModel: ScanViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val pipeline = ScanPipeline(applicationContext)
                val store = KeepStore(applicationContext.filesDir)
                val keeper =
                    object : Keeper {
                        override fun keep(
                            packageName: String,
                            ruleIds: List<String>,
                            worst: Severity,
                        ) = store.keep(packageName, ruleIds, worst)

                        override fun unkeep(packageName: String) = store.unkeep(packageName)

                        override fun keptCount(): Int = store.all().size

                        override fun rekeep(result: ScanResult): ScanResult = pipeline.rekeep(result)
                    }
                return ScanViewModel(scanner = { onProgress -> pipeline.run(onProgress) }, keeper = keeper) as T
            }
        }
    }

    private var consentSeen by mutableStateOf(false)
    private var usageAccess by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startScan() }
    private val vpnConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) DnsTunnelService.start(this, MonitorWindow.DEFAULT_MINUTES)
        }

    /** Android's own uninstall confirmation. Whatever it returns, the package list is the truth. */
    private val uninstallDialog =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val pkg = pendingUninstall ?: return@registerForActivityResult
            pendingUninstall = null
            val label =
                viewModel.result
                    ?.evidence
                    ?.apps
                    ?.firstOrNull { it.packageName == pkg }
                    ?.label ?: pkg
            viewModel.afterDialog(pkg, remove.isInstalled(pkg), remove.blockerFor(pkg), label)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consentSeen = prefs().getBoolean(PREF_CONSENT_SEEN, false)
        setContent {
            CpiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.state.collectAsState()
                    val screen by viewModel.screen.collectAsState()
                    val monitor by DnsTunnelService.stateFlow.collectAsState()
                    val removal by viewModel.removal.collectAsState()
                    val notice by viewModel.notice.collectAsState()
                    val keptCount by viewModel.keptCount.collectAsState()

                    LaunchedEffect(Unit) {
                        viewModel.events.collect { event ->
                            when (event) {
                                is UiEvent.LaunchUninstall -> {
                                    pendingUninstall = event.packageName
                                    uninstallDialog.launch(remove.uninstallIntent(event.packageName))
                                }
                                is UiEvent.OpenSettings -> {
                                    pendingSettings = event.packageName
                                    startActivity(remove.settingsIntent(event.packageName, event.blocker))
                                }
                            }
                        }
                    }
                    // The watch finishing rescans on its own so the data flow segment fills.
                    LaunchedEffect(monitor) {
                        if (monitor is MonitorState.Finished && state is ScanState.Done) viewModel.start()
                    }

                    Screens(state, screen, monitor, removal, notice, keptCount)
                    if (state !is ScanState.Running) ScanService.stop(this)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Screens(
        state: ScanState,
        screen: Screen,
        monitor: MonitorState,
        removal: Map<String, RemovalState>,
        notice: String?,
        keptCount: Int,
    ) {
        val result = (state as? ScanState.Done)?.result
        when {
            screen is Screen.Consent ->
                ConsentSheet(
                    continueText =
                        if (consentSeen) {
                            getString(
                                com.cyberrick.phoneinspector.R.string.detail_back,
                            )
                        } else {
                            getString(com.cyberrick.phoneinspector.R.string.sheet_continue)
                        },
                    onContinue = {
                        if (consentSeen) {
                            viewModel.back()
                        } else {
                            prefs().edit { putBoolean(PREF_CONSENT_SEEN, true) }
                            consentSeen = true
                            viewModel.back()
                            requestNotificationsThenScan()
                        }
                    },
                    onBack = { viewModel.back() },
                )
            state is ScanState.Running ->
                ScanningScreen(
                    completed = state.completed,
                    usageAccessGranted = usageAccess,
                    onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onCancel = {
                        viewModel.cancel()
                        ScanService.stop(this)
                    },
                )
            state is ScanState.Failed -> FailedScreen(message = state.message, onRetry = { viewModel.reset() })
            screen is Screen.Kept ->
                KeptScreen(
                    kept = KeepStore(filesDir).all(),
                    labels =
                        result
                            ?.evidence
                            ?.apps
                            ?.associate { it.packageName to it.label }
                            .orEmpty(),
                    onUnkeep = { viewModel.unkeep(it) },
                    onBack = { viewModel.back() },
                )
            result == null || screen is Screen.Home ->
                LaunchScreen(
                    keptCount = keptCount,
                    onScan = { if (consentSeen) requestNotificationsThenScan() else viewModel.showConsent() },
                    onWhatItTouches = { viewModel.showConsent() },
                    onKept = { viewModel.showKept() },
                )
            screen is Screen.Detail -> {
                val card = ResultModel.cards(result).firstOrNull { it.packageName == screen.packageName }
                if (card == null) {
                    viewModel.back()
                } else {
                    val cards = ResultModel.cards(result)
                    DetailScreen(
                        card = card,
                        facts = ResultModel.facts(card.packageName, result.evidence),
                        position = "${cards.indexOf(card) + 1} of ${cards.size}",
                        removal = removal[card.packageName] ?: RemovalState.Idle,
                        kept = result.kept.any { it.packageName == card.packageName },
                        onRemove = { viewModel.requestRemoval(card.packageName, remove.blockerFor(card.packageName)) },
                        onOpenSettings = {
                            val blocker = remove.blockerFor(card.packageName)
                            if (blocker !=
                                null
                            ) {
                                viewModel.openSettings(card.packageName, blocker)
                            } else {
                                viewModel.afterSettings(card.packageName, null)
                            }
                        },
                        onKeep = { viewModel.keep(card) },
                        onUnkeep = { viewModel.unkeep(card.packageName) },
                        onBack = { viewModel.back() },
                    )
                }
            }
            screen is Screen.After ->
                AfterScreen(
                    score = result.score.value,
                    threatsGone = screen.threatsGone,
                    onDone = { viewModel.back() },
                )
            screen is Screen.Report ->
                ReportScreen(result = result, onShare = {
                    share(result)
                }, onSave = { save(result) }, onBack = { viewModel.back() })
            else ->
                ResultScreen(
                    result = result,
                    cards = ResultModel.cards(result),
                    monitor = monitor,
                    usageAccessGranted = usageAccess,
                    notice = notice,
                    onCard = { viewModel.open(it) },
                    onRemoveAll = { viewModel.removeAllHighs { remove.blockerFor(it) } },
                    onStartWatch = { startMonitor() },
                    onStopWatch = { DnsTunnelService.stop(this) },
                    onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onReport = { viewModel.showReport() },
                    onKept = { viewModel.showKept() },
                    onRescan = { requestNotificationsThenScan() },
                )
        }
    }

    override fun onResume() {
        super.onResume()
        usageAccess = AndroidPersistenceSource(this).usageAccessGranted()
        pendingSettings?.let { pkg ->
            pendingSettings = null
            viewModel.afterSettings(pkg, remove.blockerFor(pkg))
        }
    }

    private fun requestNotificationsThenScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startScan()
        }
    }

    /** The system VPN consent dialog is Android's; the app only asks. */
    private fun startMonitor() {
        val intent = VpnService.prepare(this)
        if (intent == null) DnsTunnelService.start(this, MonitorWindow.DEFAULT_MINUTES) else vpnConsent.launch(intent)
    }

    private fun startScan() {
        ScanService.start(this)
        viewModel.start()
    }

    private fun stamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    private fun share(result: ScanResult) {
        startActivity(Intent.createChooser(ReportExporter(this).shareIntent(result.report, result.evidence.toJson(), stamp()), null))
    }

    private fun save(result: ScanResult) {
        lifecycleScope.launch {
            val message =
                try {
                    withContext(
                        Dispatchers.IO,
                    ) { ReportExporter(this@MainActivity).exportToDownloads(result.report, result.evidence.toJson(), stamp()) }
                    getString(com.cyberrick.phoneinspector.R.string.saved_to_downloads, ReportExporter.DOWNLOAD_DIR)
                } catch (e: Exception) {
                    getString(com.cyberrick.phoneinspector.R.string.save_failed, e.message ?: e.javaClass.simpleName)
                }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun prefs() = getSharedPreferences("cpi", MODE_PRIVATE)

    private companion object {
        const val PREF_CONSENT_SEEN = "consent_seen"
    }
}
