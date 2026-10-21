package com.cyberrick.phoneinspector.net

/** What the UI shows about the network monitor. Published by the service. */
sealed interface MonitorState {
    data object Idle : MonitorState

    data class Running(
        val startedAt: Long,
        val endsAt: Long,
        val lookups: Int,
    ) : MonitorState

    data class Refused(
        val message: String,
    ) : MonitorState

    data class Finished(
        val result: MonitorResult,
    ) : MonitorState
}
