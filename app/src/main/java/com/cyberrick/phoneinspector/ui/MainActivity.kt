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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.cyberrick.phoneinspector.collect.AndroidPersistenceSource
import com.cyberrick.phoneinspector.net.DnsTunnelService
import com.cyberrick.phoneinspector.net.MonitorWindow
import com.cyberrick.phoneinspector.report.ReportExporter
import com.cyberrick.phoneinspector.scan.ScanPipeline
import com.cyberrick.phoneinspector.scan.ScanResult
import com.cyberrick.phoneinspector.service.ScanService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val viewModel: ScanViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val pipeline = ScanPipeline(applicationContext)
                return ScanViewModel(scanner = { onProgress -> pipeline.run(onProgress) }) as T
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consentSeen = prefs().getBoolean(PREF_CONSENT_SEEN, false)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.state.collectAsState()
                    val monitor by DnsTunnelService.stateFlow.collectAsState()
                    when {
                        !consentSeen ->
                            ConsentScreen(onContinue = {
                                prefs().edit { putBoolean(PREF_CONSENT_SEEN, true) }
                                consentSeen =
                                    true
                            })
                        state is ScanState.Idle ->
                            HomeScreen(
                                usageAccessGranted = usageAccess,
                                onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                                onStartScan = { requestNotificationsThenScan() },
                                monitor = monitor,
                                onStartMonitor = { startMonitor() },
                                onStopMonitor = { DnsTunnelService.stop(this) },
                            )
                        state is ScanState.Running ->
                            ScanningScreen(completed = (state as ScanState.Running).completed, onCancel = {
                                viewModel.cancel()
                                ScanService.stop(this)
                            })
                        state is ScanState.Done ->
                            ReportScreen(
                                result = (state as ScanState.Done).result,
                                onShare = { share((state as ScanState.Done).result) },
                                onSave = { save((state as ScanState.Done).result) },
                                onScanAgain = { viewModel.reset() },
                            )
                        state is ScanState.Failed ->
                            FailedScreen(
                                message = (state as ScanState.Failed).message,
                                onRetry = { viewModel.reset() },
                            )
                    }
                    if (state !is ScanState.Running) ScanService.stop(this)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        usageAccess = AndroidPersistenceSource(this).usageAccessGranted()
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
