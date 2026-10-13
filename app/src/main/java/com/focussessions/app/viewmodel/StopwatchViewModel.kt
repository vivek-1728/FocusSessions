package com.focussessions.app.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focussessions.app.service.MusicManager
import com.focussessions.app.stopwatch.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StopwatchViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val app = application

    // ── Service binding ───────────────────────────────────────────────────────
    private var service: StopwatchService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, b: IBinder) {
            service = (b as StopwatchService.SwBinder).get()
            bound   = true
            viewModelScope.launch {
                service!!.state.collect { sw ->
                    _uiState.update { it.copy(stopwatch = sw) }
                }
            }
            service!!.onCompleted = { onStopwatchComplete() }
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; bound = false }
    }

    private val _uiState = MutableStateFlow(StopwatchViewUiState())
    val uiState: StateFlow<StopwatchViewUiState> = _uiState.asStateFlow()

    val musicManager = MusicManager(app.applicationContext)

    init {
        app.bindService(
            Intent(app, StopwatchService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        viewModelScope.launch {
            musicManager.trackList.collect { l -> _uiState.update { it.copy(trackList = l) } }
        }
        viewModelScope.launch {
            musicManager.isPlaying.collect { p -> _uiState.update { it.copy(isMusicPlaying = p) } }
        }
        viewModelScope.launch {
            musicManager.currentTrack.collect { t -> _uiState.update { it.copy(currentTrack = t) } }
        }
        viewModelScope.launch {
            val tracks = musicManager.loadLocalTracks()
            if (_uiState.value.selectedCompletionUri.isEmpty() && tracks.isNotEmpty()) {
                val defaultTrack = tracks.first()
                _uiState.update { it.copy(selectedCompletionUri = defaultTrack.uri, currentTrack = defaultTrack) }
            }
        }
    }

    // ── Stopwatch controls ────────────────────────────────────────────────────
    fun start() {
        val sw      = _uiState.value.stopwatch
        val totalMs = sw.totalDurationSeconds * 1000L
        if (totalMs <= 0L) return
        val svc = service ?: run {
            Intent(app, StopwatchService::class.java).also {
                it.action = StopwatchService.ACTION_START
                it.putExtra(StopwatchService.EXTRA_DURATION, totalMs)
                app.startService(it)
            }; return
        }
        svc.startEngine(totalMs)
    }

    fun pause()  { service?.pause() }
    fun resume() { service?.resume() }
    fun reset()  { service?.reset(); musicManager.stop() }

    fun setHours(h: Int)   { _uiState.update { it.copy(stopwatch = it.stopwatch.copy(hours   = h)) } }
    fun setMinutes(m: Int) { _uiState.update { it.copy(stopwatch = it.stopwatch.copy(minutes = m)) } }
    fun setSeconds(s: Int) { _uiState.update { it.copy(stopwatch = it.stopwatch.copy(seconds = s)) } }

    // ── Music ─────────────────────────────────────────────────────────────────
    fun loadTracks() {
        viewModelScope.launch {
            val tracks = musicManager.loadLocalTracks()
            if (_uiState.value.selectedCompletionUri.isEmpty() && tracks.isNotEmpty()) {
                val defaultTrack = tracks.first()
                _uiState.update { it.copy(selectedCompletionUri = defaultTrack.uri, currentTrack = defaultTrack) }
            }
        }
    }

    fun selectCompletionTrack(uri: String) {
        _uiState.update { it.copy(selectedCompletionUri = uri) }
        val track = _uiState.value.trackList.find { it.uri == uri }
        if (track != null) {
            musicManager.selectTrack(track)
        } else {
            musicManager.prepareAndPlay(uri)
        }
    }

    fun toggleMusic() {
        if (_uiState.value.isMusicPlaying) musicManager.pause() else musicManager.play()
    }

    fun stopMusic() { musicManager.stop() }

    internal fun onStopwatchComplete() {
        val uri = _uiState.value.selectedCompletionUri.ifEmpty { "DEFAULT_ALARM" }
        musicManager.setVolume(1f)
        musicManager.prepareAndPlay(uri)
        _uiState.update { it.copy(showCompletionOverlay = true, isMusicPlaying = true) }
    }

    fun dismissCompletion() {
        musicManager.stop()
        _uiState.update { it.copy(showCompletionOverlay = false) }
    }

    override fun onCleared() {
        super.onCleared()
        if (bound) { app.unbindService(connection); bound = false }
        musicManager.release()
    }
}

data class StopwatchViewUiState(
    val stopwatch: StopwatchUiState              = StopwatchUiState(),
    val isMusicPlaying: Boolean                  = false,
    val currentTrack: com.focussessions.app.domain.model.MusicTrack? = null,
    val trackList: List<com.focussessions.app.domain.model.MusicTrack> = emptyList(),
    val selectedCompletionUri: String            = "",
    val showCompletionOverlay: Boolean           = false
)
