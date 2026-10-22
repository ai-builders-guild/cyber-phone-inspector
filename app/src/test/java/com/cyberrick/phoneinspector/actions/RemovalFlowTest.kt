package com.cyberrick.phoneinspector.actions

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The removal state machine is pure; the Android pieces (dialog, package check) are injected as facts. */
class RemovalFlowTest {
    @Test
    fun `a request with no blocker leads to the system dialog`() {
        val next = RemovalFlow.onRequest("com.x", blocker = null)

        assertThat(next).isEqualTo(RemovalState.Requested("com.x"))
    }

    @Test
    fun `a request with an active device admin leads to the two step path`() {
        val next = RemovalFlow.onRequest("com.x", blocker = RemovalBlocker.DEVICE_ADMIN)

        assertThat(next).isEqualTo(RemovalState.NeedsSettings("com.x", RemovalBlocker.DEVICE_ADMIN))
    }

    @Test
    fun `system packages cannot be removed and say so`() {
        val next = RemovalFlow.onRequest("com.android.systemui", blocker = RemovalBlocker.SYSTEM_APP)

        assertThat(next).isEqualTo(RemovalState.NotRemovable("com.android.systemui", RemovalBlocker.SYSTEM_APP))
    }

    @Test
    fun `after the dialog the package being gone is Gone and still present is StillInstalled with the reason`() {
        assertThat(RemovalFlow.afterDialog("com.x", installed = false, blocker = null)).isEqualTo(RemovalState.Gone("com.x"))
        assertThat(
            RemovalFlow.afterDialog("com.x", installed = true, blocker = null),
        ).isEqualTo(RemovalState.StillInstalled("com.x", "the uninstall was cancelled or refused"))
        assertThat(RemovalFlow.afterDialog("com.x", installed = true, blocker = RemovalBlocker.ACCESSIBILITY))
            .isEqualTo(RemovalState.NeedsSettings("com.x", RemovalBlocker.ACCESSIBILITY))
    }

    @Test
    fun `returning from settings with the blocker cleared moves on to the dialog`() {
        assertThat(RemovalFlow.afterSettings("com.x", blocker = null)).isEqualTo(RemovalState.Requested("com.x"))
        assertThat(
            RemovalFlow.afterSettings("com.x", blocker = RemovalBlocker.DEVICE_ADMIN),
        ).isEqualTo(RemovalState.NeedsSettings("com.x", RemovalBlocker.DEVICE_ADMIN))
    }

    @Test
    fun `every blocker has a plain language step and a settings target`() {
        RemovalBlocker.entries.filter { it != RemovalBlocker.SYSTEM_APP }.forEach {
            assertThat(it.stepText("Anti Theft")).contains("Anti Theft")
            assertThat(SettingsLinks.targetFor(it)).isNotNull()
        }
    }

    @Test
    fun `settings targets fall back to the app info page when the preferred action is unavailable`() {
        val resolver = SettingsLinks.Resolver { action -> action == SettingsLinks.APP_INFO }

        val chosen = SettingsLinks.choose(RemovalBlocker.DEVICE_ADMIN, resolver)

        assertThat(chosen).isEqualTo(SettingsLinks.APP_INFO)
    }
}
