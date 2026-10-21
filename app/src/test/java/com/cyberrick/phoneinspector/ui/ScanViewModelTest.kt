package com.cyberrick.phoneinspector.ui

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.DeviceInfo
import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.scan.ScanResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMain() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private val result =
        ScanResult(
            evidence =
                Evidence(
                    device = DeviceInfo("t", "t", "16", 36),
                    collectedAt = 1L,
                    checks =
                        CheckKind.entries.map {
                            CheckResult(it, CheckStatus.CHECKED)
                        },
                ),
            findings = emptyList(),
            report = "# r\nVERDICT: clean\n",
        )

    @Test
    fun `starts idle then runs then completes with the result`() =
        runTest(dispatcher) {
            val vm =
                ScanViewModel(scanner = { onProgress ->
                    CheckKind.entries.forEach(onProgress)
                    result
                })

            assertThat(vm.state.value).isEqualTo(ScanState.Idle)
            vm.start()
            advanceUntilIdle()

            assertThat(vm.state.value).isEqualTo(ScanState.Done(result))
        }

    @Test
    fun `progress reports each check as it completes`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val vm =
                ScanViewModel(scanner = { onProgress ->
                    onProgress(CheckKind.APPS)
                    onProgress(CheckKind.CONTROL)
                    gate.await()
                    result
                })

            vm.start()
            advanceUntilIdle()

            assertThat(vm.state.value).isEqualTo(ScanState.Running(setOf(CheckKind.APPS, CheckKind.CONTROL)))
            gate.complete(Unit)
            advanceUntilIdle()
            assertThat(vm.state.value).isInstanceOf(ScanState.Done::class.java)
        }

    @Test
    fun `a second start while running does not restart the scan`() =
        runTest(dispatcher) {
            var calls = 0
            val gate = CompletableDeferred<Unit>()
            val vm =
                ScanViewModel(scanner = {
                    calls++
                    gate.await()
                    result
                })

            vm.start()
            advanceUntilIdle()
            vm.start()
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            assertThat(calls).isEqualTo(1)
        }

    @Test
    fun `cancel returns to idle and the scanner is cancelled`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val vm =
                ScanViewModel(scanner = {
                    gate.await()
                    result
                })

            vm.start()
            advanceUntilIdle()
            vm.cancel()
            advanceUntilIdle()

            assertThat(vm.state.value).isEqualTo(ScanState.Idle)
            assertThat(gate.isCancelled || !gate.isCompleted).isTrue()
        }

    @Test
    fun `a scanner failure becomes Failed with the message and never a crash`() =
        runTest(dispatcher) {
            val vm = ScanViewModel(scanner = { throw IllegalStateException("collector exploded") })

            vm.start()
            advanceUntilIdle()

            assertThat(vm.state.value).isEqualTo(ScanState.Failed("IllegalStateException: collector exploded"))
        }

    @Test
    fun `reset after done returns to idle`() =
        runTest(dispatcher) {
            val vm = ScanViewModel(scanner = { result })

            vm.start()
            advanceUntilIdle()
            vm.reset()

            assertThat(vm.state.value).isEqualTo(ScanState.Idle)
        }
}
