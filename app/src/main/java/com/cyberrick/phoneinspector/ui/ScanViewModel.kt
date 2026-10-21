package com.cyberrick.phoneinspector.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.scan.ScanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ScanState {
    data object Idle : ScanState

    data class Running(
        val completed: Set<CheckKind>,
    ) : ScanState

    data class Done(
        val result: ScanResult,
    ) : ScanState

    data class Failed(
        val message: String,
    ) : ScanState
}

/**
 * Owns the scan job so it survives rotation. [start] is idempotent while a scan runs; [cancel] cancels the
 * collectors through structured concurrency. The scanner is injected so the state machine is testable on the JVM.
 */
class ScanViewModel(
    private val scanner: suspend (onProgress: (CheckKind) -> Unit) -> ScanResult,
) : ViewModel() {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        _state.value = ScanState.Running(emptySet())
        job =
            viewModelScope.launch {
                try {
                    val result =
                        scanner { check ->
                            _state.update { s -> if (s is ScanState.Running) s.copy(completed = s.completed + check) else s }
                        }
                    _state.value = ScanState.Done(result)
                } catch (e: CancellationException) {
                    _state.value = ScanState.Idle
                    throw e
                } catch (e: Exception) {
                    _state.value = ScanState.Failed("${e.javaClass.simpleName}: ${e.message}")
                }
            }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = ScanState.Idle
    }

    fun reset() {
        if (job?.isActive != true) _state.value = ScanState.Idle
    }
}
