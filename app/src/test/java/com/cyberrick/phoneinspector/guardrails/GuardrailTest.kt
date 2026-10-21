package com.cyberrick.phoneinspector.guardrails

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Mechanical enforcement of PROJECT.md's read only and privacy rules.
 * These tests read the module's own sources; they need no device.
 */
class GuardrailTest {
    private val moduleDir = File(System.getProperty("user.dir") ?: ".").absoluteFile
    private val mainDir = File(moduleDir, "src/main")
    private val manifest = File(mainDir, "AndroidManifest.xml")

    @Test
    fun `manifest declares exactly the permission allowlist`() {
        assertThat(manifest.exists()).isTrue()
        assertThat(declaredPermissions(manifest)).containsExactlyElementsIn(PERMISSION_ALLOWLIST)
    }

    @Test
    fun `no other source set manifest adds a permission`() {
        val others =
            File(moduleDir, "src")
                .listFiles { f -> f.isDirectory && f.name != "main" }
                .orEmpty()
                .map { File(it, "AndroidManifest.xml") }
                .filter { it.exists() }
        val added = others.flatMap { m -> declaredPermissions(m).map { "${m.parentFile?.name}: $it" } }
        assertThat(added).isEmpty()
    }

    private fun declaredPermissions(file: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("uses-permission")
        return (0 until nodes.length)
            .map {
                nodes
                    .item(it)
                    .attributes
                    .getNamedItem("android:name")
                    .nodeValue
            }.toSet()
    }

    @Test
    fun `main sources contain no symbol that writes to the phone`() {
        val hits =
            sourceFiles().flatMap { file ->
                FORBIDDEN_SYMBOLS.filter { file.readText().contains(it) }.map { "${file.relativeTo(moduleDir)} contains $it" }
            }
        assertThat(hits).isEmpty()
    }

    @Test
    fun `the only url literal in main sources is the pinned ioc source`() {
        val urlPattern = Regex("""https?://[^\s"'<>)]+""")
        val hits =
            sourceFiles().flatMap { file ->
                urlPattern
                    .findAll(file.readText())
                    .map { it.value }
                    .filterNot { url -> ALLOWED_URL_PREFIXES.any { url.startsWith(it) } }
                    .map { "${file.relativeTo(moduleDir)} contains $it" }
                    .toList()
            }
        assertThat(hits).isEmpty()
    }

    private fun sourceFiles(): List<File> =
        mainDir
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java", "xml") }
            .toList()

    companion object {
        val PERMISSION_ALLOWLIST =
            setOf(
                "android.permission.QUERY_ALL_PACKAGES",
                "android.permission.PACKAGE_USAGE_STATS",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                "android.permission.INTERNET",
                "android.permission.POST_NOTIFICATIONS",
                // Approved by Rick 2026-10-21 (DECISIONS.md): needed by every ConnectivityManager read in the network check.
                "android.permission.ACCESS_NETWORK_STATE",
            )

        val FORBIDDEN_SYMBOLS =
            listOf(
                "PackageInstaller",
                "ACTION_DELETE",
                "ACTION_UNINSTALL_PACKAGE",
                "killBackgroundProcesses",
                "Settings.Secure.putString",
                "Settings.Secure.putInt",
                "Settings.Global.putString",
                "Settings.Global.putInt",
                "Settings.System.putString",
                "Settings.System.putInt",
                "removeActiveAdmin",
                "setApplicationHidden",
                "setPackagesSuspended",
                "setComponentEnabledSetting",
            )

        /** The pinned MVT indicator index is the only outbound URL allowed. XML namespaces are schema ids, not endpoints. */
        val ALLOWED_URL_PREFIXES =
            listOf(
                "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/indicators.yaml",
                "http://schemas.android.com/",
            )
    }
}
