package com.cyberrick.phoneinspector.net

object MonitorWindow {
    const val DEFAULT_MINUTES = 10
    const val MAX_MINUTES = 60

    fun clamp(minutes: Int?): Int = (minutes ?: DEFAULT_MINUTES).coerceIn(1, MAX_MINUTES)

    fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }
}
