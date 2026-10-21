package com.cyberrick.phoneinspector.probe

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Debug only. Runs the Phase 2 probes and writes probe_run.json and probe_report.md to filesDir. */
class ProbeActivity : ComponentActivity() {
    private val runner by lazy { ProbeRunner(this) }
    private var report by mutableStateOf("Not run yet.")

    private val vpnConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) startVpnProbe() else report = "VPN consent declined.\n\n$report"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Button(onClick = { runProbes() }) { Text("Run probes") }
                    Button(
                        onClick = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    ) { Text("Open usage access settings") }
                    Button(onClick = { requestVpnConsent() }) { Text("Request VPN consent and open tun") }
                    Text(text = report, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (intent.getBooleanExtra(EXTRA_AUTORUN, false)) runProbes()
        if (intent.getBooleanExtra(EXTRA_VPN, false)) requestVpnConsent()
        when (intent.getStringExtra(EXTRA_COLLECTOR)) {
            "apps" -> runCollectorProbe { CollectorProbe(this).apps() }
            "control" -> runCollectorProbe { CollectorProbe(this).control() }
            "permissions" -> runCollectorProbe { CollectorProbe(this).permissions() }
        }
    }

    private fun runCollectorProbe(body: () -> List<ProbeResult>) {
        report = "Running collector..."
        lifecycleScope.launch {
            val md =
                withContext(Dispatchers.IO) {
                    val results = body()
                    ProbeReport.render(runner.device(), results).also { File(filesDir, COLLECTOR_FILE).writeText(it, Charsets.UTF_8) }
                }
            report = md
        }
    }

    /** Probes run off the main thread: the socket probe would otherwise hit NetworkOnMainThreadException. */
    private fun runProbes() {
        report = "Running..."
        lifecycleScope.launch {
            val md =
                withContext(Dispatchers.IO) {
                    val results = runner.runAll() + vpnResults() + vpnLingering()
                    val run = ProbeRun(runner.device(), results)
                    File(filesDir, RUN_FILE).writeText(run.toJson(), Charsets.UTF_8)
                    ProbeReport.render(run.device, run.results).also { File(filesDir, REPORT_FILE).writeText(it, Charsets.UTF_8) }
                }
            report = md
        }
    }

    private fun vpnResults(): List<ProbeResult> {
        val f = File(filesDir, ProbeVpnService.VPN_RESULT_FILE)
        return if (f.exists()) ProbeRun.fromJson(f.readText(Charsets.UTF_8)).results else emptyList()
    }

    private fun vpnLingering(): List<ProbeResult> {
        val api = "NetworkCapabilities.TRANSPORT_VPN after probe"
        val result =
            try {
                ProbeResult(
                    api = api,
                    count = if (runner.vpnTransportActive()) 1 else 0,
                    fieldPresent = true,
                    elapsedMs = 0,
                    note = "count 0 = no VPN left behind",
                )
            } catch (e: SecurityException) {
                ProbeResult(api = api, error = "${e.javaClass.name}: ${e.message}")
            }
        return listOf(result)
    }

    private fun requestVpnConsent() {
        val intent = VpnService.prepare(this)
        if (intent == null) startVpnProbe() else vpnConsent.launch(intent)
    }

    private fun startVpnProbe() {
        startService(Intent(this, ProbeVpnService::class.java))
        report = "VPN probe started. Tap Run probes in a few seconds.\n\n$report"
    }

    companion object {
        const val EXTRA_AUTORUN = "autorun"
        const val EXTRA_VPN = "vpn"
        const val EXTRA_COLLECTOR = "collector"
        const val COLLECTOR_FILE = "collector_probe.md"
        const val RUN_FILE = "probe_run.json"
        const val REPORT_FILE = "probe_report.md"
    }
}
