package com.cyberrick.phoneinspector.collect

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.ControlKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ControlCollectorTest {
    private val now = 1_792_411_200_000L
    private val installedAt = 1_788_220_800_000L

    private fun collect(source: FakeControlSource) = ControlCollector(source) { now }.collect()

    @Test
    fun `work profile yields one profile owner record and one admin record for the agent`() {
        val agent = "com.google.android.apps.work.clouddpc"
        val source =
            FakeControlSource(
                admins = listOf("$agent/.receivers.CloudDeviceAdminReceiver"),
                profileOwners = setOf(agent),
                installTimes = mapOf(agent to installedAt),
            )

        val result = collect(source)

        val owners = result.records.filter { it.kind == ControlKind.PROFILE_OWNER }
        assertThat(owners).hasSize(1)
        assertThat(owners.single().packageName).isEqualTo(agent)
        assertThat(
            result.records
                .filter { it.kind == ControlKind.DEVICE_ADMIN }
                .single()
                .component,
        ).isEqualTo("$agent/$agent.receivers.CloudDeviceAdminReceiver")
        assertThat(result.records.filter { it.kind == ControlKind.DEVICE_OWNER }).isEmpty()
    }

    @Test
    fun `accessibility stalkerware yields one accessibility record with install time as the bound`() {
        val source =
            FakeControlSource(
                accessibilitySetting = "com.sysconfig.service/.AccService",
                installTimes = mapOf("com.sysconfig.service" to installedAt),
            )

        val result = collect(source)

        val acc = result.records.filter { it.kind == ControlKind.ACCESSIBILITY }
        assertThat(acc).hasSize(1)
        assertThat(acc.single().packageName).isEqualTo("com.sysconfig.service")
        assertThat(acc.single().component).isEqualTo("com.sysconfig.service/com.sysconfig.service.AccService")
        assertThat(acc.single().sinceAtLatest).isEqualTo(installedAt)
        assertThat(result.check.status).isEqualTo(CheckStatus.CHECKED)
    }

    @Test
    fun `malformed settings strings parse to partial lists with a reason and never throw`() {
        val source = FakeControlSource(accessibilitySetting = "::garbage-no-slash:com.a/.X::", notificationSetting = ":")

        val result = collect(source)

        assertThat(result.records.map { it.component }).containsExactly("com.a/com.a.X")
        assertThat(result.check.status).isEqualTo(CheckStatus.PARTIAL)
        assertThat(result.check.reason).contains("1 unparseable")
    }

    @Test
    fun `empty and null settings yield no records and CHECKED`() {
        val result = collect(FakeControlSource(accessibilitySetting = "::", notificationSetting = null, admins = null))

        assertThat(result.records).isEmpty()
        assertThat(result.check.status).isEqualTo(CheckStatus.CHECKED)
    }

    @Test
    fun `notification listeners are parsed from the colon separated setting`() {
        val source = FakeControlSource(notificationSetting = "com.wear.app/.NlService:com.android.systemui/.NotifListener")

        val listeners = collect(source).records.filter { it.kind == ControlKind.NOTIFICATION_LISTENER }

        assertThat(listeners.map { it.packageName }).containsExactly("com.android.systemui", "com.wear.app")
    }

    @Test
    fun `accessibility manager entries missing from the setting are included once`() {
        val source =
            FakeControlSource(
                accessibilitySetting = "com.a/.X",
                accessibilityFromManager = listOf("com.a/com.a.X", "com.b/com.b.Y"),
            )

        val acc = collect(source).records.filter { it.kind == ControlKind.ACCESSIBILITY }

        assertThat(acc.map { it.component }).containsExactly("com.a/com.a.X", "com.b/com.b.Y")
    }

    @Test
    fun `device owner is reported as DEVICE_OWNER`() {
        val source = FakeControlSource(admins = listOf("com.mdm/.Admin"), deviceOwners = setOf("com.mdm"))

        val kinds = collect(source).records.filter { it.packageName == "com.mdm" }.map { it.kind }

        assertThat(kinds).containsExactly(ControlKind.DEVICE_ADMIN, ControlKind.DEVICE_OWNER)
    }

    @Test
    fun `unknown install time falls back to collection time as the weakest bound`() {
        val result = collect(FakeControlSource(admins = listOf("com.gone/.Admin")))

        assertThat(result.records.single().sinceAtLatest).isEqualTo(now)
    }

    @Test
    fun `when every source throws the check is NOT_CHECKED with the reason`() {
        val result = collect(FakeControlSource(failure = SecurityException("settings read denied")))

        assertThat(result.records).isEmpty()
        assertThat(result.check.check).isEqualTo(CheckKind.CONTROL)
        assertThat(result.check.status).isEqualTo(CheckStatus.NOT_CHECKED)
        assertThat(result.check.reason).contains("settings read denied")
    }

    @Test
    fun `records are sorted by kind then package and carry provenance`() {
        val source =
            FakeControlSource(
                admins = listOf("com.z/.A", "com.a/.A"),
                accessibilitySetting = "com.m/.S",
                notificationSetting = "com.n/.L",
            )

        val records = collect(source).records

        assertThat(records.map { it.kind.name + " " + it.packageName }).isInOrder()
        records.forEach {
            assertThat(it.api).isNotEmpty()
            assertThat(it.collectedAt).isEqualTo(now)
        }
    }
}

class FakeControlSource(
    private val admins: List<String>? = emptyList(),
    private val deviceOwners: Set<String> = emptySet(),
    private val profileOwners: Set<String> = emptySet(),
    private val accessibilitySetting: String? = null,
    private val accessibilityFromManager: List<String> = emptyList(),
    private val notificationSetting: String? = null,
    private val installTimes: Map<String, Long> = emptyMap(),
    private val failure: Exception? = null,
) : ControlSource {
    private fun <T> guarded(value: T): T = failure?.let { throw it } ?: value

    override fun activeAdmins(): List<String>? = guarded(admins)

    override fun isDeviceOwner(packageName: String): Boolean = guarded(packageName in deviceOwners)

    override fun isProfileOwner(packageName: String): Boolean = guarded(packageName in profileOwners)

    override fun enabledAccessibilityServicesSetting(): String? = guarded(accessibilitySetting)

    override fun enabledAccessibilityServicesFromManager(): List<String> = guarded(accessibilityFromManager)

    override fun enabledNotificationListenersSetting(): String? = guarded(notificationSetting)

    override fun firstInstallTime(packageName: String): Long? = guarded(installTimes[packageName])
}
