package com.focussessions.app.service

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.focussessions.app.domain.model.MusicTrack
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).setUsePlatformDiagnostics(false).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession
}

// ── Music Manager (injected into ViewModel) ───────────────────────────────────
class MusicManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var systemRingtone: Ringtone? = null

    private val _isPlaying   = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrack = MutableStateFlow<MusicTrack?>(
        MusicTrack(-1L, "Default Alarm Ring ⏰", "System Alarm", 5000L, "DEFAULT_ALARM")
    )
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()

    private val _trackList = MutableStateFlow<List<MusicTrack>>(emptyList())
    val trackList: StateFlow<List<MusicTrack>> = _trackList.asStateFlow()

    private var currentTrackIndex = 0

    // ── Playback Control ──────────────────────────────────────────────────────
    fun prepareAndPlay(uriStr: String) {
        stopSystemRingtone()

        val parsedUri = when {
            uriStr.isEmpty() || uriStr == "default" || uriStr == "DEFAULT_ALARM" ->
                runCatching {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }.getOrNull()
            else -> runCatching { Uri.parse(uriStr) }.getOrNull()
        }

        var exoSuccess = false
        if (parsedUri != null) {
            runCatching {
                ensurePlayer()
                exoPlayer?.let { player ->
                    val mediaItem = MediaItem.fromUri(parsedUri)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playWhenReady = true
                    _isPlaying.value = true
                    exoSuccess = true
                }
            }.onFailure {
                exoSuccess = false
            }
        }

        if (!exoSuccess && parsedUri != null) {
            runCatching {
                systemRingtone = RingtoneManager.getRingtone(context, parsedUri)
                systemRingtone?.play()
                _isPlaying.value = true
            }.onFailure {
                _isPlaying.value = true
            }
        } else if (parsedUri == null) {
            _isPlaying.value = true
        }
    }

    fun play() {
        if (systemRingtone != null) {
            systemRingtone?.play()
            _isPlaying.value = true
        } else {
            exoPlayer?.let {
                it.play()
                _isPlaying.value = true
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
        stopSystemRingtone()
        _isPlaying.value = false
    }

    fun stop() {
        exoPlayer?.stop()
        stopSystemRingtone()
        _isPlaying.value = false
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        stopSystemRingtone()
        _isPlaying.value = false
    }

    private fun stopSystemRingtone() {
        runCatching {
            systemRingtone?.stop()
            systemRingtone = null
        }
    }

    fun nextTrack() {
        val tracks = _trackList.value
        if (tracks.isEmpty()) return
        currentTrackIndex = (currentTrackIndex + 1) % tracks.size
        val track = tracks[currentTrackIndex]
        _currentTrack.value = track
        prepareAndPlay(track.uri)
    }

    fun previousTrack() {
        val tracks = _trackList.value
        if (tracks.isEmpty()) return
        currentTrackIndex = (currentTrackIndex - 1 + tracks.size) % tracks.size
        val track = tracks[currentTrackIndex]
        _currentTrack.value = track
        prepareAndPlay(track.uri)
    }

    fun selectTrack(track: MusicTrack) {
        _currentTrack.value = track
        val idx = _trackList.value.indexOf(track)
        if (idx >= 0) currentTrackIndex = idx
        prepareAndPlay(track.uri)
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    // ── Library Scanning ──────────────────────────────────────────────────────
    fun loadLocalTracks(): List<MusicTrack> {
        val tracks = mutableListOf<MusicTrack>()

        // 1. Always include Default Alarm Ring
        val defaultAlarm = MusicTrack(
            id = -1L,
            title = "Default Alarm Ring ⏰",
            artist = "System Alarm",
            duration = 5000L,
            uri = "DEFAULT_ALARM"
        )
        tracks += defaultAlarm

        // 2. Query System Ringtones & Alarms
        runCatching {
            val rm = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
            }
            rm.cursor?.use { cursor ->
                var sysId = -2L
                while (cursor.moveToNext()) {
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: "Ringtone"
                    val uri = rm.getRingtoneUri(cursor.position)?.toString() ?: continue
                    tracks += MusicTrack(
                        id = sysId--,
                        title = title,
                        artist = "System Ringtone",
                        duration = 5000L,
                        uri = uri
                    )
                }
            }
        }

        // 3. Query Local Music Files from MediaStore
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                collection, projection, selection, null, sortOrder
            )?.use { cursor ->
                val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id       = cursor.getLong(idCol)
                    val uri      = Uri.withAppendedPath(collection, id.toString()).toString()
                    tracks += MusicTrack(
                        id       = id,
                        title    = cursor.getString(titleCol) ?: "Unknown Track",
                        artist   = cursor.getString(artistCol) ?: "Unknown Artist",
                        duration = cursor.getLong(durationCol),
                        uri      = uri
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _trackList.value = tracks
        if (_currentTrack.value == null && tracks.isNotEmpty()) {
            _currentTrack.value = tracks.first()
        }
        return tracks
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun ensurePlayer() {
        if (exoPlayer == null) {
            exoPlayer = runCatching {
                ExoPlayer.Builder(context)
                    .setUsePlatformDiagnostics(false)
                    .build().also { player ->
                        player.addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_ENDED) {
                                    nextTrack()
                                }
                            }
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                _isPlaying.value = isPlaying
                            }
                        })
                    }
            }.getOrNull()
        }
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min      = totalSec / 60
        val sec      = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}
