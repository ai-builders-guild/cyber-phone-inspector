package com.cyberrick.phoneinspector.collect

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.InstallSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppsCollectorTest {
    private val now = 1_792_411_200_000L
    private val systemList = setOf("com.android.systemui", "com.android.settings", "com.google.android.gms")

    private fun raw(
        pkg: String,
        label: String = pkg.substringAfterLast('.'),
        system: Boolean = false,
        installer: String? = if (system) null else "com.android.vending",
        category: Int? = null,
    ) = RawPackage(
        packageName = pkg,
        label = label,
        versionName = "1.0",
        versionCode = 1,
        firstInstallTime = 1_788_220_800_000L,
        lastUpdateTime = 1_788_220_800_000L,
        isSystem = system,
        enabled = true,
        category = category,
        signingCertSha256 = listOf("sha256:${pkg.hashCode()}"),
        installerPackage = installer,
    )

    private val baseline =
        listOf(
            raw("com.android.systemui", "System UI", system = true),
            raw("com.android.settings", "Settings", system = true),
            raw("com.google.android.gms", "Google Play services", system = true),
            raw("com.whatsapp", "WhatsApp", category = 4),
            raw("com.spotify.music", "Spotify"),
            raw("com.google.android.apps.maps", "Maps", category = 6),
        )
    private val baselineLaunchers = setOf("com.android.settings", "com.whatsapp", "com.spotify.music", "com.google.android.apps.maps")

    private fun collect(
        packages: List<RawPackage>,
        launchers: Set<String> = baselineLaunchers,
        source: FakePackageSource? = null,
    ) = AppsCollector(source ?: FakePackageSource(packages, launchers), systemList) { now }.collect()

    @Test
    fun `clean phone has no hidden and no disguised apps`() {
        val result = collect(baseline)

        assertThat(result.records.filter { it.hidden }).isEmpty()
        assertThat(result.records.filter { it.disguised }).isEmpty()
        assertThat(result.check.status).isEqualTo(CheckStatus.CHECKED)
    }

    @Test
    fun `sideloaded app without a launcher icon is the only hidden app`() {
        val planted = raw("com.sysconfig.service", "Sync Service", installer = "com.android.packageinstaller")

        val result = collect(baseline + planted)

        val hidden = result.records.filter { it.hidden }
        assertThat(hidden.map { it.packageName }).containsExactly("com.sysconfig.service")
        assertThat(hidden.single().hasLauncherIcon).isFalse()
    }

    @Test
    fun `system apps without launcher icons are not hidden`() {
        val result = collect(baseline)

        assertThat(result.records.single { it.packageName == "com.android.systemui" }.hidden).isFalse()
    }

    @Test
    fun `sideloaded app with a system style label is disguised`() {
        val planted = raw("com.update.core", "System Update", installer = "com.android.packageinstaller")

        val result = collect(baseline + planted, baselineLaunchers + "com.update.core")

        assertThat(result.records.single { it.packageName == "com.update.core" }.disguised).isTrue()
    }

    @Test
    fun `play installed app with a system style word is not disguised`() {
        val reddit = raw("com.laurencedawson.reddit_sync", "Sync for Reddit")

        val result = collect(baseline + reddit, baselineLaunchers + reddit.packageName)

        assertThat(result.records.single { it.packageName == reddit.packageName }.disguised).isFalse()
    }

    @Test
    fun `real system app with a system style label is not disguised`() {
        assertThat(collect(baseline).records.single { it.packageName == "com.android.systemui" }.disguised).isFalse()
    }

    @Test
    fun `installer maps to install source`() {
        val packages =
            listOf(
                raw("a.play", installer = "com.android.vending"),
                raw("a.samsung", installer = "com.sec.android.app.samsungapps"),
                raw("a.unknown", installer = null),
                raw("a.side", installer = "com.android.packageinstaller"),
                raw("a.browser", installer = "com.android.chrome"),
                raw("a.sys", system = true, installer = "com.android.vending"),
            )

        val byPkg = collect(packages, packages.map { it.packageName }.toSet()).records.associateBy { it.packageName }

        assertThat(byPkg.getValue("a.play").installSource).isEqualTo(InstallSource.PLAY_STORE)
        assertThat(byPkg.getValue("a.samsung").installSource).isEqualTo(InstallSource.VENDOR_STORE)
        assertThat(byPkg.getValue("a.unknown").installSource).isEqualTo(InstallSource.UNKNOWN)
        assertThat(byPkg.getValue("a.side").installSource).isEqualTo(InstallSource.SIDELOADED)
        assertThat(byPkg.getValue("a.browser").installSource).isEqualTo(InstallSource.SIDELOADED)
        assertThat(byPkg.getValue("a.sys").installSource).isEqualTo(InstallSource.SYSTEM)
    }

    @Test
    fun `web apks installed by chrome are web apps and carrier or oem module installers are vendor`() {
        val packages =
            listOf(
                raw("org.chromium.webapk.a1b2c3", "Some PWA", installer = "com.android.chrome"),
                raw("a.apk.from.browser", installer = "com.android.chrome"),
                raw("a.carrier.preload", installer = "com.LogiaGroup.LogiaDeck"),
                raw("a.goodlock.module", installer = "com.samsung.android.goodlock"),
            )

        val byPkg = collect(packages, packages.map { it.packageName }.toSet()).records.associateBy { it.packageName }

        assertThat(byPkg.getValue("org.chromium.webapk.a1b2c3").installSource).isEqualTo(InstallSource.WEB_APP)
        assertThat(byPkg.getValue("a.apk.from.browser").installSource).isEqualTo(InstallSource.SIDELOADED)
        assertThat(byPkg.getValue("a.carrier.preload").installSource).isEqualTo(InstallSource.VENDOR_STORE)
        assertThat(byPkg.getValue("a.goodlock.module").installSource).isEqualTo(InstallSource.VENDOR_STORE)
    }

    @Test
    fun `an installer lookup that throws keeps the record as unknown and does not abort`() {
        val source = FakePackageSource(baseline, baselineLaunchers, installerFailures = setOf("com.spotify.music"))

        val result = collect(baseline, source = source)

        assertThat(result.records).hasSize(baseline.size)
        assertThat(result.records.single { it.packageName == "com.spotify.music" }.installSource).isEqualTo(InstallSource.UNKNOWN)
        assertThat(result.check.status).isEqualTo(CheckStatus.CHECKED)
    }

    @Test
    fun `records are sorted by package and carry provenance`() {
        val result = collect(baseline)

        assertThat(result.records.map { it.packageName }).isInOrder()
        result.records.forEach {
            assertThat(it.api).isEqualTo(AppsCollector.API)
            assertThat(it.collectedAt).isEqualTo(now)
        }
    }

    @Test
    fun `category int is mapped to its name and unknown ints to null`() {
        val packages = listOf(raw("a.maps", category = 6), raw("a.none", category = -1), raw("a.weird", category = 99))

        val byPkg = collect(packages, packages.map { it.packageName }.toSet()).records.associateBy { it.packageName }

        assertThat(byPkg.getValue("a.maps").category).isEqualTo("MAPS")
        assertThat(byPkg.getValue("a.none").category).isNull()
        assertThat(byPkg.getValue("a.weird").category).isNull()
    }

    @Test
    fun `duplicate packages from the source are both kept`() {
        val dupes = baseline + raw("com.whatsapp", "WhatsApp")

        assertThat(collect(dupes).records.count { it.packageName == "com.whatsapp" }).isEqualTo(2)
    }

    @Test
    fun `an inventory failure yields NOT_CHECKED with the reason and no records`() {
        val source = FakePackageSource(baseline, baselineLaunchers, inventoryFailure = SecurityException("no QUERY_ALL_PACKAGES"))

        val result = collect(baseline, source = source)

        assertThat(result.records).isEmpty()
        assertThat(result.check.check).isEqualTo(CheckKind.APPS)
        assertThat(result.check.status).isEqualTo(CheckStatus.NOT_CHECKED)
        assertThat(result.check.reason).contains("QUERY_ALL_PACKAGES")
    }

    @Test
    fun `two thousand packages collect under 500 ms`() {
        val many = (0 until 2000).map { raw("com.synthetic.app%04d".format(it), system = it % 5 == 0) }
        val launchers = many.filter { !it.isSystem }.map { it.packageName }.toSet()
        collect(many, launchers)

        val start = System.nanoTime()
        val result = collect(many, launchers)
        val ms = (System.nanoTime() - start) / 1_000_000

        assertThat(result.records).hasSize(2000)
        assertThat(ms).isLessThan(500)
    }
}

/** Test double for [PackageSource]. Per package installer failures and a whole inventory failure are configurable. */
class FakePackageSource(
    private val packages: List<RawPackage>,
    private val launchers: Set<String>,
    private val installerFailures: Set<String> = emptySet(),
    private val inventoryFailure: Exception? = null,
) : PackageSource {
    override fun installedPackages(): List<RawPackage> {
        inventoryFailure?.let { throw it }
        return packages
    }

    override fun launcherPackages(): Set<String> = launchers

    override fun installerOf(packageName: String): String? {
        if (packageName in installerFailures) throw IllegalStateException("NameNotFound: $packageName")
        return packages.first { it.packageName == packageName }.installerPackage
    }
}
