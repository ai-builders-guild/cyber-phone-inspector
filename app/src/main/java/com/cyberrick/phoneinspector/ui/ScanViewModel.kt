package com.cyberrick.phoneinspector.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyberrick.phoneinspector.actions.RemovalBlocker
import com.cyberrick.phoneinspector.actions.RemovalFlow
import com.cyberrick.phoneinspector.actions.RemovalState
import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.feeds.FeedFile
import com.cyberrick.phoneinspector.feeds.RefreshResult
import com.cyberrick.phoneinspector.rules.Severity
import com.cyberrick.phoneinspector.scan.ScanResult
import com.cyberrick.phoneinspector.ui.model.AppCard
import com.cyberrick.phoneinspector.ui.model.ResultModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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

/** Which screen sits on top. Lives in the view model so rotation keeps the place. */
sealed interface Screen {
    data object Home : Screen

    data object Consent : Screen

    data object Result : Screen

    data class Detail(
        val packageName: String,
    ) : Screen

    data class After(
        val threatsGone: Boolean,
    ) : Screen

    data object Kept : Screen

    data object Report : Screen

    data object Lists : Screen
}

sealed interface RefreshUi {
    data object Idle : RefreshUi

    data object Running : RefreshUi

    data class Done(
        val result: RefreshResult,
    ) : RefreshUi

    data class Failed(
        val message: String,
    ) : RefreshUi
}

/** The lists on the phone and the one way to replace them. */
interface Refresher {
    fun dates(): Map<FeedFile, String?>

    suspend fun refresh(): RefreshResult
}

/** Things only the activity can do; the view model asks, the activity launches. */
sealed interface UiEvent {
    data class LaunchUninstall(
        val packageName: String,
    ) : UiEvent

    data class OpenSettings(
        val packageName: String,
        val blocker: RemovalBlocker,
    ) : UiEvent
}

/** What the view model needs from the phone, behind an interface so the state machine runs on the JVM. */
interface Keeper {
    fun keep(
        packageName: String,
        ruleIds: List<String>,
        worst: Severity,
    )

    fun unkeep(packageName: String)

    fun keptCount(): Int

    /** Re renders a finished result with the current kept list. */
    fun rekeep(result: ScanResult): ScanResult
}

/**
 * Owns the scan job so it survives rotation, plus the screen stack, the removal state per package and the kept list.
 * [start] is idempotent while a scan runs; [cancel] cancels the collectors through structured concurrency.
 */
class ScanViewModel(
    private val scanner: suspend (onProgress: (CheckKind) -> Unit) -> ScanResult,
    private val keeper: Keeper = NoKeeper,
    private val refresher: Refresher? = null,
) : ViewModel() {
    private val _refresh = MutableStateFlow<RefreshUi>(RefreshUi.Idle)
    val refresh: StateFlow<RefreshUi> = _refresh
    private val _listDates = MutableStateFlow(refresher?.dates().orEmpty())
    val listDates: StateFlow<Map<FeedFile, String?>> = _listDates

    val oldestListDate: String? get() =
        _listDates.value.values
            .filterNotNull()
            .minOrNull()

    fun showLists() {
        _screen.value = Screen.Lists
    }

    fun refreshLists() {
        val r = refresher ?: return
        if (_refresh.value is RefreshUi.Running) return
        _refresh.value = RefreshUi.Running
        viewModelScope.launch {
            _refresh.value =
                try {
                    RefreshUi.Done(r.refresh())
                } catch (e: Exception) {
                    RefreshUi.Failed(e.message ?: e.javaClass.simpleName)
                }
            _listDates.value = r.dates()
        }
    }

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state
    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen
    private val _removal = MutableStateFlow<Map<String, RemovalState>>(emptyMap())
    val removal: StateFlow<Map<String, RemovalState>> = _removal
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events
    private val _keptCount = MutableStateFlow(keeper.keptCount())
    val keptCount: StateFlow<Int> = _keptCount

    private var job: Job? = null
    private var removeQueue: List<String> = emptyList()
    private var removedThisRound = false

    val result: ScanResult? get() = (_state.value as? ScanState.Done)?.result

    fun cards(): List<AppCard> = result?.let { ResultModel.cards(it) }.orEmpty()

    fun start() {
        if (job?.isActive == true) return
        _state.value = ScanState.Running(emptySet())
        // A removal's "X is gone" line survives its own rescan; a scan the person starts clears it.
        if (!removedThisRound) _notice.value = null
        job =
            viewModelScope.launch {
                try {
                    val scanned =
                        scanner { check ->
                            _state.update { s -> if (s is ScanState.Running) s.copy(completed = s.completed + check) else s }
                        }
                    _state.value = ScanState.Done(scanned)
                    _removal.value = emptyMap()
                    _screen.value =
                        if (removedThisRound) {
                            removedThisRound = false
                            Screen.After(threatsGone = scanned.score.threatApps == 0)
                        } else {
                            Screen.Result
                        }
                } catch (e: CancellationException) {
                    _state.value = ScanState.Idle
                    _screen.value = Screen.Home
                    throw e
                } catch (e: Exception) {
                    _state.value = ScanState.Failed("${e.javaClass.simpleName}: ${e.message}")
                }
            }
    }

    fun cancel() {
        job?.cancel()
        job = null
        removedThisRound = false
        _state.value = ScanState.Idle
        _screen.value = Screen.Home
    }

    fun reset() {
        if (job?.isActive != true) {
            _state.value = ScanState.Idle
            _screen.value = Screen.Home
        }
    }

    // Navigation

    fun open(card: AppCard) {
        _screen.value = Screen.Detail(card.packageName)
    }

    fun showConsent() {
        _screen.value = Screen.Consent
    }

    fun showKept() {
        _screen.value = Screen.Kept
    }

    fun showReport() {
        _screen.value = Screen.Report
    }

    /** Back from any secondary screen lands on the result if there is one, else home. */
    fun back() {
        _screen.value = if (_state.value is ScanState.Done) Screen.Result else Screen.Home
    }

    // Removal

    /** Tapping Remove on a card: ask for the dialog, or the Settings step first. */
    fun requestRemoval(
        packageName: String,
        blocker: RemovalBlocker?,
    ) {
        val next = RemovalFlow.onRequest(packageName, blocker)
        _removal.update { it + (packageName to next) }
        when (next) {
            is RemovalState.Requested -> _events.tryEmit(UiEvent.LaunchUninstall(packageName))
            else -> Unit
        }
    }

    /** The "Remove N threats" button: every app with a high, one dialog after another. */
    fun removeAllHighs(blockerOf: (String) -> RemovalBlocker?) {
        val highs = cards().filter { it.severity == Severity.HIGH }.map { it.packageName }
        if (highs.isEmpty()) return
        removeQueue = highs.drop(1)
        requestRemoval(highs.first(), blockerOf(highs.first()))
    }

    fun openSettings(
        packageName: String,
        blocker: RemovalBlocker,
    ) {
        _events.tryEmit(UiEvent.OpenSettings(packageName, blocker))
    }

    /** Android's dialog closed. A gone package means a rescan on its own; the After screen follows when it finishes. */
    fun afterDialog(
        packageName: String,
        installed: Boolean,
        blocker: RemovalBlocker?,
        label: String = packageName,
    ) {
        val next = RemovalFlow.afterDialog(packageName, installed, blocker)
        _removal.update { it + (packageName to next) }
        if (next is RemovalState.Gone) {
            removedThisRound = true
            _notice.value = "$label is gone."
        }
        val queued = removeQueue.firstOrNull()
        if (queued != null) {
            removeQueue = removeQueue.drop(1)
            _events.tryEmit(UiEvent.LaunchUninstall(queued))
            _removal.update { it + (queued to RemovalState.Requested(queued)) }
            return
        }
        if (removedThisRound) start()
    }

    /** Back from Settings: re check the blocker. Clear means the Uninstall step is now available. */
    fun afterSettings(
        packageName: String,
        blocker: RemovalBlocker?,
    ) {
        val current = _removal.value[packageName]
        if (current !is RemovalState.NeedsSettings) return
        _removal.update {
            it + (
                packageName to
                    if (blocker ==
                        null
                    ) {
                        RemovalState.Idle
                    } else {
                        RemovalFlow.afterSettings(packageName, blocker)
                    }
            )
        }
    }

    // Keep

    fun keep(card: AppCard) {
        keeper.keep(card.packageName, card.ruleIds, card.severity)
        rekeep()
        _notice.value = "${card.label} is marked as yours."
        _screen.value = Screen.Result
    }

    fun unkeep(packageName: String) {
        keeper.unkeep(packageName)
        rekeep()
    }

    private fun rekeep() {
        _keptCount.value = keeper.keptCount()
        _state.update { s -> if (s is ScanState.Done) ScanState.Done(keeper.rekeep(s.result)) else s }
    }

    private object NoKeeper : Keeper {
        override fun keep(
            packageName: String,
            ruleIds: List<String>,
            worst: Severity,
        ) = Unit

        override fun unkeep(packageName: String) = Unit

        override fun keptCount(): Int = 0

        override fun rekeep(result: ScanResult): ScanResult = result
    }
}
