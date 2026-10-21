package com.cyberrick.phoneinspector.report

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Writes REPORT.md and evidence.json to the phone's Downloads folder (app scoped, no storage permission on API 29+)
 * and builds a share sheet intent. The app never sends the files anywhere itself.
 */
class ReportExporter(
    private val context: Context,
) {
    data class Exported(
        val reportUri: Uri,
        val evidenceUri: Uri,
    )

    fun exportToDownloads(
        report: String,
        evidenceJson: String,
        stamp: String,
    ): Exported =
        Exported(
            reportUri = writeDownload("CPI_REPORT_$stamp.md", "text/markdown", report),
            evidenceUri = writeDownload("CPI_EVIDENCE_$stamp.json", "application/json", evidenceJson),
        )

    fun shareIntent(
        report: String,
        evidenceJson: String,
        stamp: String,
    ): Intent {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val files =
            listOf(
                File(dir, "CPI_REPORT_$stamp.md").apply { writeText(report, Charsets.UTF_8) },
                File(dir, "CPI_EVIDENCE_$stamp.json").apply { writeText(evidenceJson, Charsets.UTF_8) },
            )
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writeDownload(
        name: String,
        mime: String,
        content: String,
    ): Uri {
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_DIR")
            }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Downloads insert refused for $name")
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) } ?: error("cannot open $uri")
        return uri
    }

    companion object {
        const val DOWNLOAD_DIR = "CyberPhoneInspector"
    }
}
