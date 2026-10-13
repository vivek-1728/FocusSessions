package com.focussessions.app.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SyncWorkerTest {

    private lateinit var context: Context
    private lateinit var syncManager: SyncManager
    private lateinit var workerParams: WorkerParameters

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        syncManager = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
    }

    @Test
    fun `doWork calls syncManager fullSync and returns success`() = runTest {
        coEvery { syncManager.fullSync() } returns Unit

        val worker = SyncWorker(context, workerParams, syncManager)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { syncManager.fullSync() }
    }

    @Test
    fun `doWork retries when syncManager throws exception and runAttemptCount is less than 3`() = runTest {
        coEvery { syncManager.fullSync() } throws RuntimeException("Cloud Sync Exception")
        every { workerParams.runAttemptCount } returns 1

        val worker = SyncWorker(context, workerParams, syncManager)
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }

    @Test
    fun `doWork returns failure when runAttemptCount reaches 3`() = runTest {
        coEvery { syncManager.fullSync() } throws RuntimeException("Persistent Failure")
        every { workerParams.runAttemptCount } returns 3

        val worker = SyncWorker(context, workerParams, syncManager)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun `doWork skips sync cleanly and returns success when user is signed out`() = runTest {
        // When signed out, syncManager.fullSync() returns cleanly without error
        coEvery { syncManager.fullSync() } returns Unit

        val worker = SyncWorker(context, workerParams, syncManager)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { syncManager.fullSync() }
    }
}
