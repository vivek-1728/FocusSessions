package com.focussessions.app.service

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.provider.MediaStore
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.focussessions.app.domain.model.MusicTrack
import com.focussessions.app.timer.SessionPhase
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class MusicServiceTest {

    private lateinit var context: Context
    private lateinit var musicManager: MusicManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        musicManager = MusicManager(context)
    }

    // ── 1. MediaStore Scan Tests ──────────────────────────────────────────────

    @Test
    fun `loadLocalTracks parses results correctly from MediaStore cursor`() {
        val spyContext = spyk(context)
        val contentResolver = mockk<ContentResolver>()
        every { spyContext.contentResolver } returns contentResolver

        val columns = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        val cursor = MatrixCursor(columns).apply {
            addRow(arrayOf<Any>(101L, "Lofi Beat", "Chill Artist", 180000L, "/path/to/beat1.mp3"))
            addRow(arrayOf<Any>(102L, "Focus Flow", "Ambient Artist", 240000L, "/path/to/beat2.mp3"))
        }

        every {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                any(), any(), any(), any()
            )
        } returns cursor

        val manager = MusicManager(spyContext)
        val tracks = manager.loadLocalTracks()

        val mediaStoreTracks = tracks.filter { it.id > 0 }
        assertEquals(2, mediaStoreTracks.size)
        assertEquals(101L, mediaStoreTracks[0].id)
        assertEquals("Lofi Beat", mediaStoreTracks[0].title)
        assertEquals("Chill Artist", mediaStoreTracks[0].artist)
        assertEquals(180000L, mediaStoreTracks[0].duration)

        assertEquals(102L, mediaStoreTracks[1].id)
        assertEquals("Focus Flow", mediaStoreTracks[1].title)
    }

    @Test
    fun `loadLocalTracks includes default alarm ring when cursor is empty`() {
        val spyContext = spyk(context)
        val contentResolver = mockk<ContentResolver>()
        every { spyContext.contentResolver } returns contentResolver

        val columns = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        val emptyCursor = MatrixCursor(columns)

        every {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                any(), any(), any(), any()
            )
        } returns emptyCursor

        val manager = MusicManager(spyContext)
        val tracks = manager.loadLocalTracks()

        assertTrue(tracks.any { it.uri == "DEFAULT_ALARM" })
    }

    @Test
    fun `loadLocalTracks handles missing permission and query exception gracefully`() {
        val spyContext = spyk(context)
        val contentResolver = mockk<ContentResolver>()
        every { spyContext.contentResolver } returns contentResolver

        every {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                any(), any(), any(), any()
            )
        } throws SecurityException("Permission denied: READ_MEDIA_AUDIO")

        val manager = MusicManager(spyContext)
        val tracks = manager.loadLocalTracks()

        assertTrue(tracks.any { it.uri == "DEFAULT_ALARM" })
    }

    // ── 2. Playback Control Tests ─────────────────────────────────────────────

    @Test
    fun `playback controls update isPlaying and currentTrack state flows`() = runTest {
        val spyContext = spyk(context)
        val contentResolver = mockk<ContentResolver>()
        every { spyContext.contentResolver } returns contentResolver
        val columns = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA
        )
        val cursor = MatrixCursor(columns).apply {
            addRow(arrayOf<Any>(1L, "Track 1", "Artist 1", 120000L, "/path/1"))
            addRow(arrayOf<Any>(2L, "Track 2", "Artist 2", 180000L, "/path/2"))
        }
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor

        musicManager = MusicManager(spyContext)
        val loadedTracks = musicManager.loadLocalTracks()
        val track2 = loadedTracks.find { it.id == 2L }!!

        assertFalse(musicManager.isPlaying.value)

        // Select Track 2
        musicManager.selectTrack(track2)
        assertEquals("Track 2", musicManager.currentTrack.value?.title)
        assertTrue(musicManager.isPlaying.value)

        // Pause
        musicManager.pause()
        assertFalse(musicManager.isPlaying.value)

        // Play
        musicManager.play()
        assertTrue(musicManager.isPlaying.value)

        // Stop & Release
        musicManager.stop()
        assertFalse(musicManager.isPlaying.value)

        musicManager.release()
        assertFalse(musicManager.isPlaying.value)
    }

    @Test
    fun `formatDuration formats milliseconds correctly`() {
        assertEquals("0:00", musicManager.formatDuration(0L))
        assertEquals("1:05", musicManager.formatDuration(65000L))
        assertEquals("3:00", musicManager.formatDuration(180000L))
    }

    // ── 3. Focus Session Auto-Play & Break Behavior Tests ─────────────────────

    @Test
    fun `music pauses on focus phase start and plays on break phase start`() {
        val mockManager = mockk<MusicManager>(relaxed = true)

        var onMusicStartBreak: (() -> Unit)? = null
        var onMusicStopFocus: (() -> Unit)? = null

        val handlePhaseChanged = { phase: SessionPhase ->
            when (phase) {
                SessionPhase.FOCUS -> onMusicStopFocus?.invoke()
                SessionPhase.BREAK -> onMusicStartBreak?.invoke()
                SessionPhase.COMPLETED -> onMusicStopFocus?.invoke()
                else -> {}
            }
        }

        onMusicStartBreak = { mockManager.play() }
        onMusicStopFocus = { mockManager.pause() }

        // 1. Focus phase starts -> music paused
        handlePhaseChanged(SessionPhase.FOCUS)
        verify(exactly = 1) { mockManager.pause() }

        // 2. Break phase starts -> music auto-plays
        handlePhaseChanged(SessionPhase.BREAK)
        verify(exactly = 1) { mockManager.play() }

        // 3. Session completes -> music paused / stopped
        handlePhaseChanged(SessionPhase.COMPLETED)
        verify(exactly = 2) { mockManager.pause() }
    }

    // ── 4. MusicService MediaSession Notification Controls Test ──────────────

    @Test
    fun `MusicService creates and exposes MediaSession on create`() {
        val controller: ServiceController<MusicService> = Robolectric.buildService(MusicService::class.java)
        val service = controller.create().get()

        val mockControllerInfo = mockk<MediaSession.ControllerInfo>(relaxed = true)
        val session = service.onGetSession(mockControllerInfo)

        assertNotNull(session)
        controller.destroy()
    }
}
