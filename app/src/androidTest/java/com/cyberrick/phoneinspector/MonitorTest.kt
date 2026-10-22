package com.cyberrick.phoneinspector

import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyberrick.phoneinspector.net.DnsTunnelService
import com.cyberrick.phoneinspector.net.FlowStore
import com.cyberrick.phoneinspector.net.MonitorState
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonitorTest {
    /** VPN consent is the user's; the test runs only where it was already granted (the emulator from earlier runs). */
    @Test
    fun monitorStartsRunsAndStopsLeavingASavedResult() {
        assumeTrue("VPN consent not granted on this device", VpnService.prepare(TestShell.context) == null)
        TestShell.grantUsageAccess(true)

        DnsTunnelService.start(TestShell.context, 1)
        waitFor(10_000) { DnsTunnelService.stateFlow.value is MonitorState.Running }
        assertThat(DnsTunnelService.stateFlow.value).isInstanceOf(MonitorState.Running::class.java)
        Thread.sleep(3_000)
        DnsTunnelService.stop(TestShell.context)
        waitFor(10_000) { DnsTunnelService.stateFlow.value is MonitorState.Finished }

        val saved = FlowStore(TestShell.context.filesDir).load()
        assertThat(saved).isNotNull()
        assertThat(saved!!.endedAt).isAtLeast(saved.startedAt)
    }

    private fun waitFor(
        timeoutMs: Long,
        condition: () -> Boolean,
    ) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end && !condition()) Thread.sleep(200)
    }
}
