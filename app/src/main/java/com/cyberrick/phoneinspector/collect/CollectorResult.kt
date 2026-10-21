package com.cyberrick.phoneinspector.collect

import com.cyberrick.phoneinspector.core.model.CheckResult

/** What every collector returns: immutable records plus the status of its check. */
data class CollectorResult<T>(
    val records: List<T>,
    val check: CheckResult,
)
