package com.cyberrick.phoneinspector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cyberrick.phoneinspector.R
import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.scan.ScanResult

private val checkLabels =
    mapOf(
        CheckKind.APPS to R.string.check_apps,
        CheckKind.CONTROL to R.string.check_control,
        CheckKind.PERMISSIONS to R.string.check_permissions,
        CheckKind.PERSISTENCE to R.string.check_persistence,
        CheckKind.NETWORK to R.string.check_network,
    )

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun Heading(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
}

@Composable
fun ConsentScreen(onContinue: () -> Unit) {
    Page {
        Heading(stringResource(R.string.consent_title))
        Text(stringResource(R.string.consent_body))
        Text(stringResource(R.string.consent_usage))
        Text(stringResource(R.string.consent_vpn))
        Text(stringResource(R.string.consent_readonly), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.consent_continue)) }
    }
}

@Composable
fun HomeScreen(
    usageAccessGranted: Boolean,
    onOpenUsageAccess: () -> Unit,
    onStartScan: () -> Unit,
) {
    Page {
        Heading(stringResource(R.string.app_name))
        Text(stringResource(R.string.home_body))
        Text(
            text = if (usageAccessGranted) stringResource(R.string.usage_access_granted) else stringResource(R.string.usage_access_missing),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!usageAccessGranted) {
            OutlinedButton(
                onClick = onOpenUsageAccess,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.open_usage_access)) }
        }
        Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_scan)) }
        Text(stringResource(R.string.home_network_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ScanningScreen(
    completed: Set<CheckKind>,
    onCancel: () -> Unit,
) {
    Page {
        Heading(stringResource(R.string.scanning_title))
        LinearProgressIndicator(progress = { completed.size / CheckKind.entries.size.toFloat() }, modifier = Modifier.fillMaxWidth())
        CheckKind.entries.forEach { check ->
            val done = check in completed
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(checkLabels.getValue(check)))
                Text(if (done) stringResource(R.string.check_done) else stringResource(R.string.check_pending))
            }
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
fun ReportScreen(
    result: ScanResult,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onScanAgain: () -> Unit,
) {
    Page {
        Heading(result.verdictLine)
        Text(
            stringResource(
                R.string.report_counts,
                result.findings.count { it.severity.name == "HIGH" },
                result.findings.count {
                    it.severity.name ==
                        "MEDIUM"
                },
                result.findings.count { it.severity.name == "LOW" },
            ),
        )
        Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_report)) }
        OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_report)) }
        OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_again)) }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = result.report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun FailedScreen(
    message: String,
    onRetry: () -> Unit,
) {
    Page {
        Heading(stringResource(R.string.failed_title))
        Text(message)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.try_again)) }
    }
}
