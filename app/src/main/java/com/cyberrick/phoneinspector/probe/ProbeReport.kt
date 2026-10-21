package com.cyberrick.phoneinspector.probe

/** Renders a probe run as the markdown section that goes into PROBE_RESULTS.md. */
object ProbeReport {
    fun render(
        device: ProbeDevice,
        results: List<ProbeResult>,
    ): String =
        buildString {
            appendLine("### ${device.manufacturer} ${device.model}, Android ${device.androidRelease} (API ${device.sdkInt})")
            appendLine()
            appendLine("| API | Count | Field present | Elapsed ms | Error | Note |")
            appendLine("|---|---|---|---|---|---|")
            results.forEach { appendLine(row(it)) }
        }

    private fun row(r: ProbeResult): String {
        val count = if (r.error != null) "error" else r.count?.toString() ?: "-"
        val present = if (r.error != null) "-" else r.fieldPresent?.let { if (it) "yes" else "no" } ?: "-"
        val elapsed = if (r.error != null) "-" else r.elapsedMs?.toString() ?: "-"
        return "| ${r.api} | $count | $present | $elapsed | ${r.errorClass ?: "-"} | ${r.note ?: "-"} |"
    }
}
