package com.focussessions.app.sync

import com.focussessions.app.data.local.database.entities.FocusHistoryEntity
import com.focussessions.app.data.local.database.entities.TaskEntity
import com.focussessions.app.testutils.FakeFirestoreService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FirestoreSyncServiceTest {

    private lateinit var fakeFirestoreService: FakeFirestoreService

    @Before
    fun setUp() {
        fakeFirestoreService = FakeFirestoreService()
    }

    @Test
    fun `uploadFocusSession writes to users focus_sessions collection`() = runTest {
        val session = FocusHistoryEntity(
            sessionId = 1001L,
            startTime = 1700000000000L,
            endTime = 1700001500000L,
            focusDuration = 25,
            breakDuration = 5,
            completedCycles = 1,
            sessionTitle = "Deep Work",
            dateEpochDay = 20000L,
            dayOfWeek = 1,
            startHour = 9,
            wasCompleted = true,
            actualFocusMinutes = 25,
            actualBreakMinutes = 5
        )

        val result = fakeFirestoreService.uploadFocusSession(session)
        assertTrue(result.isSuccess)
        assertEquals(1, fakeFirestoreService.uploadedSessions.size)
        val uploaded = fakeFirestoreService.uploadedSessions["1001"]
        assertNotNull(uploaded)
        assertEquals("Deep Work", uploaded?.sessionTitle)
        assertEquals(25, uploaded?.actualFocusMinutes)
    }

    @Test
    fun `uploadTask and deleteTask write and delete from users tasks collection`() = runTest {
        val task = TaskEntity(
            id = 50L,
            title = "Write Unit Tests",
            isCompleted = false,
            createdAt = 1700000000000L
        )

        val uploadResult = fakeFirestoreService.uploadTask(task)
        assertTrue(uploadResult.isSuccess)
        assertEquals(1, fakeFirestoreService.uploadedTasks.size)
        assertEquals("Write Unit Tests", fakeFirestoreService.uploadedTasks["50"]?.title)

        val deleteResult = fakeFirestoreService.deleteTask(50L)
        assertTrue(deleteResult.isSuccess)
        assertTrue(fakeFirestoreService.deletedTaskIds.contains(50L))
        assertNull(fakeFirestoreService.uploadedTasks["50"])
    }

    @Test
    fun `uploadSettings writes to users settings collection`() = runTest {
        val prefsMap = mapOf(
            "focusDurationMinutes" to 30,
            "dailyGoalMinutes" to 180,
            "autoStartBreaks" to true
        )

        val result = fakeFirestoreService.uploadSettings(prefsMap)
        assertTrue(result.isSuccess)
        assertEquals(30, fakeFirestoreService.uploadedSettings["focusDurationMinutes"])
        assertEquals(180, fakeFirestoreService.uploadedSettings["dailyGoalMinutes"])
        assertEquals(true, fakeFirestoreService.uploadedSettings["autoStartBreaks"])
    }

    @Test
    fun `uploadRaw and deleteRaw handle generic batch ops`() = runTest {
        val payload = mapOf("taskId" to "10", "title" to "Raw Task", "isCompleted" to true)
        val rawUploadRes = fakeFirestoreService.uploadRaw("tasks", "10", payload)
        assertTrue(rawUploadRes.isSuccess)

        val uploadedMap = fakeFirestoreService.rawUploads["tasks"]?.get("10")
        assertNotNull(uploadedMap)
        assertEquals("Raw Task", uploadedMap?.get("title"))

        val rawDeleteRes = fakeFirestoreService.deleteRaw("tasks", "10")
        assertTrue(rawDeleteRes.isSuccess)
        assertTrue(fakeFirestoreService.rawDeletions.contains("tasks" to "10"))
    }

    @Test
    fun `downloadAndMergeFocusSessions and downloadAndMergeTasks return correct counts`() = runTest {
        val session = FocusHistoryEntity(
            sessionId = 1L,
            startTime = 1000L,
            endTime = 2000L,
            focusDuration = 25,
            breakDuration = 5,
            completedCycles = 1,
            dateEpochDay = 100L,
            dayOfWeek = 1,
            startHour = 10,
            wasCompleted = true,
            actualFocusMinutes = 25,
            actualBreakMinutes = 5
        )
        fakeFirestoreService.uploadFocusSession(session)

        val task = TaskEntity(id = 2L, title = "Merged Task")
        fakeFirestoreService.uploadTask(task)

        val sessionsCount = fakeFirestoreService.downloadAndMergeFocusSessions().getOrNull()
        assertEquals(1, sessionsCount)

        val tasksCount = fakeFirestoreService.downloadAndMergeTasks().getOrNull()
        assertEquals(1, tasksCount)
    }

    @Test
    fun `all service operations return failure when user is not signed in`() = runTest {
        fakeFirestoreService.isUserSignedIn = false

        val task = TaskEntity(id = 1L, title = "Unauth Task")
        val uploadRes = fakeFirestoreService.uploadTask(task)
        assertTrue(uploadRes.isFailure)
        assertEquals("User is not signed in", uploadRes.exceptionOrNull()?.message)

        val sessionRes = fakeFirestoreService.downloadAndMergeFocusSessions()
        assertTrue(sessionRes.isFailure)
    }

    @Test
    fun `all service operations return failure when firestore throws exception`() = runTest {
        fakeFirestoreService.shouldFail = true
        fakeFirestoreService.failureException = RuntimeException("Firestore Network Timeout")

        val task = TaskEntity(id = 1L, title = "Fail Task")
        val uploadRes = fakeFirestoreService.uploadTask(task)
        assertTrue(uploadRes.isFailure)
        assertEquals("Firestore Network Timeout", uploadRes.exceptionOrNull()?.message)
    }
}
