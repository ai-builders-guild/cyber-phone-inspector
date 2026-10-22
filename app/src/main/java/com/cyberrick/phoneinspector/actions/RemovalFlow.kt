package com.cyberrick.phoneinspector.actions

/** Why an app cannot simply be uninstalled right now, and the Settings step that clears it. */
enum class RemovalBlocker {
    DEVICE_ADMIN,
    ACCESSIBILITY,
    NOTIFICATION_LISTENER,
    SYSTEM_APP,
    ;

    fun stepText(appLabel: String): String =
        when (this) {
            DEVICE_ADMIN -> "Find $appLabel in the Device admin apps list and switch it off, then come back."
            ACCESSIBILITY -> "Find $appLabel under Accessibility, open it, and switch it off, then come back."
            NOTIFICATION_LISTENER -> "Switch off notification access for $appLabel, then come back."
            SYSTEM_APP -> "$appLabel is part of the phone's system and cannot be uninstalled. It can be disabled in Settings."
        }
}

sealed interface RemovalState {
    data object Idle : RemovalState

    /** Android's uninstall confirmation is being shown for this package. */
    data class Requested(
        val packageName: String,
    ) : RemovalState

    /** A Settings switch must be flipped by the person first. */
    data class NeedsSettings(
        val packageName: String,
        val blocker: RemovalBlocker,
    ) : RemovalState

    data class NotRemovable(
        val packageName: String,
        val blocker: RemovalBlocker,
    ) : RemovalState

    data class Gone(
        val packageName: String,
    ) : RemovalState

    data class StillInstalled(
        val packageName: String,
        val reason: String,
    ) : RemovalState
}

/** The removal state machine. Pure: the dialog, the package check and the blocker lookup are facts passed in. */
object RemovalFlow {
    const val CANCELLED = "the uninstall was cancelled or refused"

    fun onRequest(
        packageName: String,
        blocker: RemovalBlocker?,
    ): RemovalState =
        when (blocker) {
            null -> RemovalState.Requested(packageName)
            RemovalBlocker.SYSTEM_APP -> RemovalState.NotRemovable(packageName, blocker)
            else -> RemovalState.NeedsSettings(packageName, blocker)
        }

    fun afterDialog(
        packageName: String,
        installed: Boolean,
        blocker: RemovalBlocker?,
    ): RemovalState =
        when {
            !installed -> RemovalState.Gone(packageName)
            blocker != null && blocker != RemovalBlocker.SYSTEM_APP -> RemovalState.NeedsSettings(packageName, blocker)
            else -> RemovalState.StillInstalled(packageName, CANCELLED)
        }

    fun afterSettings(
        packageName: String,
        blocker: RemovalBlocker?,
    ): RemovalState = onRequest(packageName, blocker)
}
