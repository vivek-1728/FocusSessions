package com.focussessions.app.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focussessions.app.data.local.database.entities.DailyProgressEntity
import com.focussessions.app.data.repository.FocusSessionRepository
import com.focussessions.app.data.repository.TaskRepository
import com.focussessions.app.data.repository.UserPreferencesRepository
import com.focussessions.app.domain.model.*
import com.focussessions.app.service.MusicManager
import com.focussessions.app.service.TimerService
import com.focussessions.app.timer.CycleState
import com.focussessions.app.timer.SessionPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ── UI state ──────────────────────────────────────────────────────────────────

data class FocusUiState(
    // Cycle engine
    val cycleState: CycleState              = CycleState(),

    // DB / prefs
    val tasks: List<Task>                   = emptyList(),
    val todayProgress: DailyProgressEntity? = null,
    val preferences: UserPreferences        = UserPreferences(),

    // Music
    val isPlaying: Boolean                  = false,
    val currentTrack: MusicTrack?           = null,
    val trackList: List<MusicTrack>         = emptyList(),

    // Dialogs
    val showMusicPicker: Boolean            = false,
    val showSettings: Boolean               = false,
    val showTargetPicker: Boolean           = false,

    // Completion overlay
    val showCompletionScreen: Boolean       = false,
    val completedCycleState: CycleState?    = null
)

@HiltViewModel
class FocusViewModel @Inject constructor(
    application: Application,
    private val taskRepository: TaskRepository,
    private val sessionRepository: FocusSessionRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val syncManager: com.focussessions.app.sync.SyncManager
) : AndroidViewModel(application) {

    private val app = application

    // ── Service binding ───────────────────────────────────────────────────────

    private var timerService: TimerService? = null
    private var serviceBound                = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as TimerService.TimerBinder).getService()
            timerService = svc
            serviceBound = true

            // Stream CycleState from service → UI
            viewModelScope.launch {
                svc.cycleState.collect { cycle ->
                    _uiState.update { it.copy(cycleState = cycle) }
                }
            }

            // Music callbacks wired here so service stays context-free
            svc.onMusicStartBreak = { startMusicForBreak() }
            svc.onMusicStopFocus  = { musicManager.pause() }

            // Minute-by-minute live progress update across DB and Firebase
            svc.onProgressTick = { sessionId, liveMin, liveSec, liveBreakMin ->
                viewModelScope.launch {
                    sessionRepository.updateLiveProgress(sessionId, liveMin, liveSec, liveBreakMin)
                    syncManager.onLiveProgressChanged(sessionId)
                }
            }

            // Cycle completion
            svc.onCycleComplete = { finalState ->
                viewModelScope.launch {
                    sessionRepository.recordSession(
                        finalState.targetFocusMinutes,
                        true
                    )
                    updateStreak()
                    _uiState.update {
                        it.copy(
                            showCompletionScreen = true,
                            completedCycleState  = finalState
                        )
                    }
                }
            }

            // Configure service with saved prefs
            viewModelScope.launch {
                val p = prefsRepository.preferences.first()
                svc.configure(p.focusDurationMinutes, p.shortBreakMinutes, p.dailyGoalMinutes)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            timerService = null
            serviceBound = false
        }
    }

    // ── Music ─────────────────────────────────────────────────────────────────

    val musicManager = MusicManager(application.applicationContext)

    // ── UI state ──────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    init {
        bindTimerService()
        observeData()
    }

    private fun bindTimerService() {
        Intent(app, TimerService::class.java).also { intent ->
            app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            taskRepository.activeTasks.collect { tasks ->
                _uiState.update { it.copy(tasks = tasks) }
            }
        }
        viewModelScope.launch {
            sessionRepository.getTodayProgress().collect { p ->
                _uiState.update { it.copy(todayProgress = p) }
            }
        }
        viewModelScope.launch {
            prefsRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
                timerService?.configure(
                    prefs.focusDurationMinutes,
                    prefs.shortBreakMinutes,
                    prefs.dailyGoalMinutes
                )
            }
        }
        viewModelScope.launch {
            musicManager.isPlaying.collect { p -> _uiState.update { it.copy(isPlaying = p) } }
        }
        viewModelScope.launch {
            musicManager.currentTrack.collect { t -> _uiState.update { it.copy(currentTrack = t) } }
        }
        viewModelScope.launch {
            musicManager.trackList.collect { l -> _uiState.update { it.copy(trackList = l) } }
        }
    }

    // ── Cycle control ─────────────────────────────────────────────────────────

    /**
     * Start a fresh continuous focus cycle.
     * Uses the target stored in preferences (dailyGoalMinutes).
     */
    fun startCycle(overrideTargetMinutes: Int? = null) {
        val prefs       = _uiState.value.preferences
        val target      = overrideTargetMinutes ?: prefs.dailyGoalMinutes
        val focusMin    = prefs.focusDurationMinutes
        val breakMin    = prefs.shortBreakMinutes

        val svc = timerService ?: run {
            // Service not yet bound — start via intent
            Intent(app, TimerService::class.java).apply {
                action = TimerService.ACTION_START_CYCLE
                putExtra(TimerService.EXTRA_TARGET_MIN, target)
                putExtra(TimerService.EXTRA_FOCUS_MIN,  focusMin)
                putExtra(TimerService.EXTRA_BREAK_MIN,  breakMin)
            }.also { app.startService(it) }
            return
        }

        // Music is OFF at start of cycle — focus phase first
        musicManager.pause()
        _uiState.update { it.copy(showCompletionScreen = false, completedCycleState = null) }

        svc.startCycle(target, focusMin, breakMin)
    }

    fun pause()  { timerService?.pause();  musicManager.pause() }
    fun resume() {
        val phase = _uiState.value.cycleState.phase
        timerService?.resume()
        // Only restart music if we are resuming a break
        if (phase == SessionPhase.BREAK) {
            val prefs = _uiState.value.preferences
            if (prefs.selectedMusicUri.isNotEmpty()) musicManager.play()
        }
    }

    fun skip()  { timerService?.skip() }
    fun reset() { timerService?.reset(); musicManager.stop() }

    // ── Target picker ─────────────────────────────────────────────────────────

    fun showTargetPicker() { _uiState.update { it.copy(showTargetPicker = true) } }
    fun hideTargetPicker() { _uiState.update { it.copy(showTargetPicker = false) } }

    fun startCycleWithTarget(targetMinutes: Int) {
        viewModelScope.launch { prefsRepository.setDailyGoal(targetMinutes) }
        hideTargetPicker()
        startCycle(targetMinutes)
    }

    // ── Completion overlay ────────────────────────────────────────────────────

    fun dismissCompletionScreen() {
        _uiState.update { it.copy(showCompletionScreen = false, completedCycleState = null) }
    }

    // ── Task actions ──────────────────────────────────────────────────────────

    fun addTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { taskRepository.addTask(title.trim()) }
    }
    fun toggleTask(task: Task) { viewModelScope.launch { taskRepository.toggleTask(task) } }
    fun deleteTask(id: Long)   { viewModelScope.launch { taskRepository.deleteTask(id) } }

    // ── Preferences ───────────────────────────────────────────────────────────

    fun setFocusDuration(minutes: Int) {
        viewModelScope.launch { prefsRepository.setFocusDuration(minutes) }
    }
    fun setShortBreak(minutes: Int) {
        viewModelScope.launch { prefsRepository.setShortBreak(minutes) }
    }
    fun setLongBreak(minutes: Int) {
        viewModelScope.launch { prefsRepository.setLongBreak(minutes) }
    }
    fun setDailyGoal(minutes: Int) {
        viewModelScope.launch { prefsRepository.setDailyGoal(minutes.coerceIn(30, 1080)) }
    }
    fun setMusicEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setMusicEnabled(enabled) }
        if (!enabled) musicManager.stop()
    }
    fun setAutoStartBreaks(e: Boolean) {
        viewModelScope.launch { prefsRepository.setAutoStartBreaks(e) }
    }

    // ── Music ─────────────────────────────────────────────────────────────────

    fun showMusicPicker() {
        viewModelScope.launch { musicManager.loadLocalTracks() }
        _uiState.update { it.copy(showMusicPicker = true) }
    }
    fun hideMusicPicker()  { _uiState.update { it.copy(showMusicPicker = false) } }
    fun showSettings()     { _uiState.update { it.copy(showSettings = true) } }
    fun hideSettings()     { _uiState.update { it.copy(showSettings = false) } }

    fun playTrack(track: MusicTrack) {
        viewModelScope.launch { prefsRepository.setSelectedMusicUri(track.uri) }
        viewModelScope.launch { prefsRepository.setMusicEnabled(true) }
        _uiState.update { it.copy(currentTrack = track) }

        // Only play immediately if currently in a break
        val phase = _uiState.value.cycleState.phase
        if (phase == SessionPhase.BREAK && _uiState.value.cycleState.isRunning) {
            musicManager.selectTrack(track)
        }
    }

    fun toggleMusicPlayback() {
        val phase = _uiState.value.cycleState.phase
        if (phase == SessionPhase.FOCUS) { musicManager.pause(); return }
        if (_uiState.value.isPlaying) musicManager.pause() else musicManager.play()
    }

    fun nextTrack()     = musicManager.nextTrack()
    fun previousTrack() = musicManager.previousTrack()

    private fun startMusicForBreak() {
        val prefs = _uiState.value.preferences
        if (!prefs.musicEnabled) return
        when {
            prefs.selectedMusicUri.isNotEmpty() ->
                musicManager.prepareAndPlay(prefs.selectedMusicUri)
            _uiState.value.trackList.isNotEmpty() ->
                musicManager.selectTrack(_uiState.value.trackList.first())
        }
    }

    // ── Streak ────────────────────────────────────────────────────────────────

    private suspend fun updateStreak() {
        val prefs     = prefsRepository.preferences.first()
        val today     = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()
        val newStreak = when (prefs.lastActiveDate) {
            today     -> prefs.streakCount
            yesterday -> prefs.streakCount + 1
            else      -> 1
        }
        prefsRepository.setStreakCount(newStreak)
        prefsRepository.setLastActiveDate(today)
    }

    override fun onCleared() {
        super.onCleared()
        if (serviceBound) { app.unbindService(serviceConnection); serviceBound = false }
        musicManager.release()
    }
}
