package com.focussessions.app.data.mappers

import com.focussessions.app.auth.UserProfile
import com.focussessions.app.data.local.database.entities.*
import com.focussessions.app.domain.model.*
import com.focussessions.app.sync.ConflictResolver
import com.focussessions.app.sync.fromJsonString
import com.focussessions.app.sync.toJsonString
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ModelsAndMappersTest {

    // ── UserProfile Firestore Map Round-Trip ──────────────────────────────────

    @Test
    fun `UserProfile serializes to and from Firestore map with complete accuracy`() {
        val profile = UserProfile(
            uid = "user_abc_123",
            displayName = "Jane Doe",
            email = "jane@example.com",
            photoUrl = "https://example.com/avatar.png",
            lastSyncTime = 1700000000000L,
            totalFocusMinutes = 450,
            currentStreak = 14,
            createdAt = 1600000000000L
        )

        val firestoreMap = profile.toFirestoreMap()
        val rehydrated = UserProfile.fromFirestoreMap(firestoreMap)

        assertEquals(profile.uid, rehydrated.uid)
        assertEquals(profile.displayName, rehydrated.displayName)
        assertEquals(profile.email, rehydrated.email)
        assertEquals(profile.photoUrl, rehydrated.photoUrl)
        assertEquals(profile.lastSyncTime, rehydrated.lastSyncTime)
        assertEquals(profile.totalFocusMinutes, rehydrated.totalFocusMinutes)
        assertEquals(profile.currentStreak, rehydrated.currentStreak)
        assertEquals(profile.createdAt, rehydrated.createdAt)
    }

    @Test
    fun `UserProfile handles missing, null or numeric type variations from Firestore`() {
        val partialMap = mapOf<String, Any>(
            "uid" to "user_xyz",
            "displayName" to "Bob",
            "lastSyncTime" to 1000000000000L, // Long
            "totalFocusMinutes" to 30 // Int
        )

        val profile = UserProfile.fromFirestoreMap(partialMap)

        assertEquals("user_xyz", profile.uid)
        assertEquals("Bob", profile.displayName)
        assertEquals("", profile.email)
        assertEquals("", profile.photoUrl)
        assertEquals(1000000000000L, profile.lastSyncTime)
        assertEquals(30, profile.totalFocusMinutes)
        assertEquals(0, profile.currentStreak)
        assertTrue(profile.createdAt > 0)
    }

    // ── TaskEntity & Task Domain Mapping ─────────────────────────────────────

    @Test
    fun `TaskEntity and Task domain mapping round-trip retains timestamps and values`() {
        val epochMillis = 1680000000000L
        val localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())

        val taskEntity = TaskEntity(
            id = 42L,
            title = "Refactor Database",
            isCompleted = true,
            sessionId = 101L,
            createdAt = epochMillis
        )

        val taskDomain = Task(
            id = taskEntity.id,
            title = taskEntity.title,
            isCompleted = taskEntity.isCompleted,
            sessionId = taskEntity.sessionId,
            createdAt = localDateTime
        )

        assertEquals(taskEntity.id, taskDomain.id)
        assertEquals(taskEntity.title, taskDomain.title)
        assertEquals(taskEntity.isCompleted, taskDomain.isCompleted)
        assertEquals(taskEntity.sessionId, taskDomain.sessionId)
        assertEquals(localDateTime, taskDomain.createdAt)
    }

    // ── JSON Helpers Round-Trip for Sync Entities ─────────────────────────────

    @Test
    fun `TaskEntity JSON string serialization and deserialization retains data`() {
        val entity = TaskEntity(
            id = 99L,
            title = "Review PR",
            isCompleted = true,
            createdAt = 1710000000000L
        )

        val jsonString = entity.toJsonString()
        val parsedMap = jsonString.fromJsonString()

        assertEquals(99L, (parsedMap["taskId"] as Number).toLong())
        assertEquals("Review PR", parsedMap["title"])
        assertEquals(true, parsedMap["isCompleted"])
        assertEquals(1710000000000L, (parsedMap["createdAt"] as Number).toLong())
    }

    @Test
    fun `FocusHistoryEntity JSON serialization and deserialization retains data`() {
        val history = FocusHistoryEntity(
            sessionId = 15L,
            startTime = 1700000000000L,
            endTime = 1700001500000L,
            focusDuration = 25,
            breakDuration = 5,
            completedCycles = 1,
            sessionTitle = "Deep Work Session",
            dateEpochDay = 20000L,
            dayOfWeek = 3,
            startHour = 14,
            wasCompleted = true,
            actualFocusMinutes = 25,
            actualBreakMinutes = 5
        )

        val jsonString = history.toJsonString()
        val parsedMap = jsonString.fromJsonString()

        assertEquals(15L, (parsedMap["sessionId"] as Number).toLong())
        assertEquals("Deep Work Session", parsedMap["title"])
        assertEquals(25, (parsedMap["focusDuration"] as Number).toInt())
        assertEquals(5, (parsedMap["breakDuration"] as Number).toInt())
        assertEquals(1, (parsedMap["completedCycles"] as Number).toInt())
        assertEquals(true, parsedMap["wasCompleted"])
        assertEquals(20000L, (parsedMap["dateEpochDay"] as Number).toLong())
        assertEquals(3, (parsedMap["dayOfWeek"] as Number).toInt())
        assertEquals(14, (parsedMap["startHour"] as Number).toInt())
    }

    @Test
    fun `Map toJsonString and fromJsonString handles numbers, strings, booleans and empty maps`() {
        val originalMap = mapOf<String, Any>(
            "name" to "Focus",
            "duration" to 25L,
            "score" to 4.5,
            "active" to true,
            "disabled" to false
        )

        val json = originalMap.toJsonString()
        val restored = json.fromJsonString()

        assertEquals("Focus", restored["name"])
        assertEquals(25L, (restored["duration"] as Number).toLong())
        assertEquals(4.5, (restored["score"] as Number).toDouble(), 0.001)
        assertEquals(true, restored["active"])
        assertEquals(false, restored["disabled"])

        val emptyJson = emptyMap<String, Any>().toJsonString()
        val restoredEmpty = emptyJson.fromJsonString()
        assertTrue(restoredEmpty.isEmpty())
    }

    // ── ConflictResolver Tests ───────────────────────────────────────────────

    @Test
    fun `ConflictResolver correctly picks newer timestamp or handles merge`() {
        val local = mapOf<String, Any?>("updatedAt" to 2000L, "val" to "local_data")
        val remote = mapOf<String, Any?>("updatedAt" to 1000L, "val" to "remote_data")

        val resultLocal = ConflictResolver.resolve(local, remote)
        assertEquals(ConflictResolver.Winner.LOCAL, resultLocal.side)
        assertEquals("local_data", resultLocal.winner["val"])

        val remoteNewer = mapOf<String, Any?>("updatedAt" to 3000L, "val" to "remote_newer")
        val resultRemote = ConflictResolver.resolve(local, remoteNewer)
        assertEquals(ConflictResolver.Winner.REMOTE, resultRemote.side)
        assertEquals("remote_newer", resultRemote.winner["val"])

        val merged = ConflictResolver.merge(base = local, override = mapOf("extra" to "added"))
        assertEquals("local_data", merged["val"])
        assertEquals("added", merged["extra"])
    }

    // ── Domain Model Defaults ────────────────────────────────────────────────

    @Test
    fun `Domain model constructors initialize with expected defaults`() {
        val defaultTask = Task()
        assertEquals(0L, defaultTask.id)
        assertEquals("", defaultTask.title)
        assertFalse(defaultTask.isCompleted)

        val defaultSession = FocusSession()
        assertEquals(25, defaultSession.durationMinutes)
        assertTrue(defaultSession.wasCompleted)

        val defaultProgress = DailyProgress()
        assertEquals(LocalDate.now(), defaultProgress.date)
        assertEquals(0, defaultProgress.focusMinutes)

        val musicTrack = MusicTrack(1L, "Rain Sounds", "Nature", 180000L, "asset:///rain.mp3")
        assertEquals(1L, musicTrack.id)
        assertEquals("Rain Sounds", musicTrack.title)
        assertEquals("Nature", musicTrack.artist)
        assertEquals(180000L, musicTrack.duration)
        assertEquals("asset:///rain.mp3", musicTrack.uri)
    }
}
