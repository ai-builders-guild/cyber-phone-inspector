package com.cyberrick.phoneinspector

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.report.ReportExporter
import com.cyberrick.phoneinspector.scan.ScanPipeline
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PipelineAndExportTest {
    @Before
    fun grant() = TestShell.grantUsageAccess(true)

    @Test
    fun pipelineProducesFiveCheckStatusesAndAVerdict() {
        val result = runBlocking { ScanPipeline(TestShell.context).run() }

        assertThat(result.evidence.checks.map { it.check }).containsExactlyElementsIn(CheckKind.entries)
        assertThat(result.evidence.apps.size).isGreaterThan(10)
        assertThat(result.report).contains("VERDICT: ")
        assertThat(result.verdictLine).startsWith("VERDICT: ")
    }

    @Test
    fun exportWritesReadableFilesToDownloadsAndBuildsAShareIntent() {
        val exporter = ReportExporter(TestShell.context)
        val report = "# test\nVERDICT: clean\n"

        val exported = exporter.exportToDownloads(report, "{}", "androidtest")
        val readBack =
            TestShell.context.contentResolver
                .openInputStream(
                    exported.reportUri,
                )!!
                .use { String(it.readBytes(), Charsets.UTF_8) }
        val share = exporter.shareIntent(report, "{}", "androidtest")

        assertThat(readBack).isEqualTo(report)
        assertThat(share.action).isEqualTo(Intent.ACTION_SEND_MULTIPLE)
        assertThat(share.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)).hasSize(2)
    }
}
