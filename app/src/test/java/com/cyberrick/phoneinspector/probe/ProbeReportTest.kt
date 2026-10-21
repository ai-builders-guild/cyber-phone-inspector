package com.cyberrick.phoneinspector.probe

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The probe report is the Phase 2 deliverable. It must carry counts and field presence only,
 * never the identity of anything on Rick's phone (PROJECT.md privacy rules).
 */
class ProbeReportTest {
    private val device = ProbeDevice(manufacturer = "Acme", model = "Phone 9", androidRelease = "15", sdkInt = 35)

    @Test
    fun `renders one markdown row per probe with api name count field presence and elapsed`() {
        val results =
            listOf(
                ProbeResult(api = "PackageManager.getInstalledPackages", count = 212, fieldPresent = true, elapsedMs = 41),
            )

        val md = ProbeReport.render(device, results)

        assertThat(md).contains("| PackageManager.getInstalledPackages | 212 | yes | 41 |")
    }

    @Test
    fun `renders a probe that threw as an error row with the exception class only`() {
        val results =
            listOf(
                ProbeResult(
                    api = "DevicePolicyManager.getActiveAdmins",
                    error = "java.lang.SecurityException: Permission Denial: com.evil.app",
                ),
            )

        val md = ProbeReport.render(device, results)

        assertThat(md).contains("| DevicePolicyManager.getActiveAdmins | error | - | - | java.lang.SecurityException |")
        assertThat(md).doesNotContain("com.evil.app")
    }

    @Test
    fun `report header names the device and android version`() {
        val md = ProbeReport.render(device, emptyList())

        assertThat(md).contains("Acme Phone 9")
        assertThat(md).contains("Android 15 (API 35)")
    }

    @Test
    fun `a sample value that looks like a package id is rejected at construction`() {
        val attempt =
            runCatching {
                ProbeResult(api = "x", count = 1, fieldPresent = true, elapsedMs = 1, note = "first=com.whatsapp")
            }

        assertThat(attempt.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `json round trip is lossless`() {
        val original =
            ProbeRun(
                device = device,
                results = listOf(ProbeResult(api = "a", count = 0, fieldPresent = false, elapsedMs = 3, note = "empty list is fine")),
            )

        val restored = ProbeRun.fromJson(original.toJson())

        assertThat(restored).isEqualTo(original)
    }
}
