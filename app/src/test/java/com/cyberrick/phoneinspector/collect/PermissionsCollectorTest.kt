package com.cyberrick.phoneinspector.collect

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.SensitiveGroup
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PermissionsCollectorTest {
    private val now = 1_792_411_200_000L
    private val granted = PermissionsCollector.FLAG_GRANTED
    private val expectations =
        PermissionExpectations(
            byGroup =
                mapOf(
                    SensitiveGroup.LOCATION to setOf("MAPS", "SOCIAL"),
                    SensitiveGroup.LOCATION_BACKGROUND to setOf("MAPS"),
                    SensitiveGroup.MICROPHONE to setOf("SOCIAL", "AUDIO"),
                    SensitiveGroup.CAMERA to setOf("SOCIAL", "IMAGE"),
                ),
            familyPrefixes = setOf("com.google.android.apps.maps"),
        )

    private fun raw(
        pkg: String,
        requested: List<String> = emptyList(),
        flags: List<Int>? = requested.map { granted },
        category: String? = null,
        system: Boolean = false,
    ) = RawPackagePermissions(pkg, requested, flags, category, system)

    private fun collect(
        vararg packages: RawPackagePermissions,
        source: FakePermissionSource? = null,
    ) = PermissionsCollector(source ?: FakePermissionSource(packages.toList()), expectations) { now }.collect()

    @Test
    fun `maps app with background location is granted and expected`() {
        val maps = raw("com.mapco.maps", listOf(FINE, BACKGROUND), category = "MAPS")

        val records = collect(maps).records.associateBy { it.group }

        assertThat(records.getValue(SensitiveGroup.LOCATION_BACKGROUND).granted).isTrue()
        assertThat(records.getValue(SensitiveGroup.LOCATION_BACKGROUND).unexpected).isFalse()
        assertThat(records.getValue(SensitiveGroup.LOCATION).unexpected).isFalse()
    }

    @Test
    fun `calculator with microphone is granted and unexpected`() {
        val calc = raw("com.quickcalc.pro", listOf(RECORD_AUDIO), category = "PRODUCTIVITY")

        val mic = collect(calc).records.single { it.group == SensitiveGroup.MICROPHONE }

        assertThat(mic.granted).isTrue()
        assertThat(mic.unexpected).isTrue()
    }

    @Test
    fun `an app with zero requested permissions still yields a record per group`() {
        val records = collect(raw("com.nothing.requested")).records

        assertThat(records.map { it.group }).containsExactlyElementsIn(SensitiveGroup.entries)
        records.forEach {
            assertThat(it.requested).isFalse()
            assertThat(it.granted).isFalse()
            assertThat(it.unexpected).isFalse()
        }
    }

    @Test
    fun `requested but not granted is never unexpected`() {
        val app = raw("com.asks.camera", listOf(CAMERA), flags = listOf(0), category = "PRODUCTIVITY")

        val cam = collect(app).records.single { it.group == SensitiveGroup.CAMERA }

        assertThat(cam.requested).isTrue()
        assertThat(cam.granted).isFalse()
        assertThat(cam.unexpected).isFalse()
    }

    @Test
    fun `null permission flags mean not granted and uncertain`() {
        val app = raw("com.old.api", listOf(RECORD_AUDIO), flags = null, category = "PRODUCTIVITY")

        val mic = collect(app).records.single { it.group == SensitiveGroup.MICROPHONE }

        assertThat(mic.requested).isTrue()
        assertThat(mic.granted).isFalse()
        assertThat(mic.uncertain).isTrue()
        assertThat(mic.unexpected).isFalse()
    }

    @Test
    fun `known package family is expected even without a category`() {
        val maps = raw("com.google.android.apps.maps", listOf(FINE, BACKGROUND), category = null)

        val bg = collect(maps).records.single { it.group == SensitiveGroup.LOCATION_BACKGROUND }

        assertThat(bg.unexpected).isFalse()
    }

    @Test
    fun `system apps are never unexpected`() {
        val dialer = raw("com.android.phone", listOf(RECORD_AUDIO, CAMERA), category = null, system = true)

        assertThat(collect(dialer).records.none { it.unexpected }).isTrue()
    }

    @Test
    fun `the app label is never consulted`() {
        val labelledMaps = raw("com.fake.maps", listOf(BACKGROUND), category = null)

        val bg = collect(labelledMaps).records.single { it.group == SensitiveGroup.LOCATION_BACKGROUND }

        assertThat(bg.unexpected).isTrue()
    }

    @Test
    fun `coarse location alone counts as location`() {
        val app = raw("com.weather.app", listOf(COARSE), category = "NEWS")

        val loc = collect(app).records.single { it.group == SensitiveGroup.LOCATION }

        assertThat(loc.requested).isTrue()
        assertThat(loc.granted).isTrue()
        assertThat(loc.unexpected).isTrue()
    }

    @Test
    fun `records are sorted by package then group and carry provenance`() {
        val records = collect(raw("com.b"), raw("com.a")).records

        assertThat(records.map { it.packageName + " " + it.group.ordinal }).isInOrder()
        records.forEach {
            assertThat(it.api).isEqualTo(PermissionsCollector.API)
            assertThat(it.collectedAt).isEqualTo(now)
        }
    }

    @Test
    fun `inventory failure yields NOT_CHECKED with the reason`() {
        val result = collect(source = FakePermissionSource(emptyList(), failure = SecurityException("denied")))

        assertThat(result.records).isEmpty()
        assertThat(result.check.check).isEqualTo(CheckKind.PERMISSIONS)
        assertThat(result.check.status).isEqualTo(CheckStatus.NOT_CHECKED)
        assertThat(result.check.reason).contains("denied")
    }

    @Test
    fun `samsung and google first party packages are expected by the bundled asset`() {
        val text = java.io.File(System.getProperty("user.dir") ?: ".", "src/main/assets/${PermissionExpectations.ASSET}").readText()
        val bundled = PermissionExpectations.parse(text)

        assertThat(bundled.isExpected(SensitiveGroup.MICROPHONE, null, "com.sec.android.app.voicenote")).isTrue()
        assertThat(bundled.isExpected(SensitiveGroup.CAMERA, null, "com.samsung.android.ardrawing")).isTrue()
        assertThat(bundled.isExpected(SensitiveGroup.MICROPHONE, null, "com.random.vendor.app")).isFalse()
    }

    @Test
    fun `bundled expectations asset parses with all four groups`() {
        val text = java.io.File(System.getProperty("user.dir") ?: ".", "src/main/assets/${PermissionExpectations.ASSET}").readText()

        val parsed = PermissionExpectations.parse(text)

        assertThat(parsed.byGroup.keys).containsExactlyElementsIn(SensitiveGroup.entries)
        assertThat(parsed.byGroup.getValue(SensitiveGroup.LOCATION_BACKGROUND)).contains("MAPS")
    }

    private companion object {
        const val FINE = "android.permission.ACCESS_FINE_LOCATION"
        const val COARSE = "android.permission.ACCESS_COARSE_LOCATION"
        const val BACKGROUND = "android.permission.ACCESS_BACKGROUND_LOCATION"
        const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        const val CAMERA = "android.permission.CAMERA"
    }
}

class FakePermissionSource(
    private val packages: List<RawPackagePermissions>,
    private val failure: Exception? = null,
) : PermissionSource {
    override fun packagePermissions(): List<RawPackagePermissions> {
        failure?.let { throw it }
        return packages
    }
}
