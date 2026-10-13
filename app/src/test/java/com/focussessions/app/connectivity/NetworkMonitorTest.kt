package com.focussessions.app.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.focussessions.app.auth.AuthManager
import com.focussessions.app.auth.UserProfile
import com.focussessions.app.data.local.database.dao.FocusHistoryDao
import com.focussessions.app.data.local.database.dao.TaskDao
import com.focussessions.app.sync.FirestoreSyncService
import com.focussessions.app.sync.SyncManager
import com.focussessions.app.sync.SyncQueueDao
import com.focussessions.app.sync.SyncQueueEntity
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class NetworkMonitorTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkMonitor: NetworkMonitor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        connectivityManager = mockk(relaxed = true)

        val spyContext = spyk(context)
        every { spyContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager

        networkMonitor = NetworkMonitor(spyContext)
    }

    @Test
    fun `isCurrentlyOnline returns false when activeNetwork is null`() {
        every { connectivityManager.activeNetwork } returns null
        assertFalse(networkMonitor.isCurrentlyOnline())
    }

    @Test
    fun `isCurrentlyOnline returns false when capabilities lack internet or validated`() {
        val mockNetwork = mockk<Network>()
        val mockCaps = mockk<NetworkCapabilities>()

        every { connectivityManager.activeNetwork } returns mockNetwork
        every { connectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCaps

        every { mockCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { mockCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false

        assertFalse(networkMonitor.isCurrentlyOnline())
    }

    @Test
    fun `isCurrentlyOnline returns true when network has internet and validated capabilities`() {
        val mockNetwork = mockk<Network>()
        val mockCaps = mockk<NetworkCapabilities>()

        every { connectivityManager.activeNetwork } returns mockNetwork
        every { connectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCaps

        every { mockCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { mockCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true

        assertTrue(networkMonitor.isCurrentlyOnline())
    }

    @Test
    fun `isOnline flow emits changes from NetworkCallback`() = runTest {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerNetworkCallback(any(), capture(callbackSlot)) } just Runs
        every { connectivityManager.activeNetwork } returns null

        networkMonitor.isOnline.test {
            assertEquals(false, awaitItem()) // initial emission

            val mockNetwork = mockk<Network>()
            val mockCaps = mockk<NetworkCapabilities>()

            // Trigger onAvailable
            callbackSlot.captured.onAvailable(mockNetwork)
            assertEquals(true, awaitItem())

            // Trigger onCapabilitiesChanged with invalid capabilities
            every { mockCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { mockCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
            callbackSlot.captured.onCapabilitiesChanged(mockNetwork, mockCaps)
            assertEquals(false, awaitItem())

            // Trigger onCapabilitiesChanged with valid capabilities
            every { mockCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
            callbackSlot.captured.onCapabilitiesChanged(mockNetwork, mockCaps)
            assertEquals(true, awaitItem())

            // Trigger onLost
            every { connectivityManager.activeNetwork } returns null
            callbackSlot.captured.onLost(mockNetwork)
            assertEquals(false, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reconnect triggers sync in SyncManager`() = runTest {
        val mockAuthManager = mockk<AuthManager>(relaxed = true)
        val mockFirestoreSync = mockk<FirestoreSyncService>(relaxed = true)
        val mockSyncQueueDao = mockk<SyncQueueDao>(relaxed = true)
        val mockHistoryDao = mockk<FocusHistoryDao>(relaxed = true)
        val mockTaskDao = mockk<TaskDao>(relaxed = true)

        val isOnlineFlow = MutableStateFlow(false)
        val spyNetworkMonitor = mockk<NetworkMonitor>(relaxed = true)
        every { spyNetworkMonitor.isOnline } returns isOnlineFlow
        every { spyNetworkMonitor.isCurrentlyOnline() } answers { isOnlineFlow.value }

        every { mockAuthManager.currentUser } returns MutableStateFlow(UserProfile(uid = "user_1"))
        every { mockAuthManager.isLoggedIn } returns true
        coEvery { mockSyncQueueDao.peek(50) } returns listOf(
            SyncQueueEntity(id = 1L, collection = "tasks", documentId = "1", operation = "UPSERT", payload = "{}")
        )
        coEvery { mockFirestoreSync.uploadRaw(any(), any(), any()) } returns Result.success(Unit)

        // Instantiate SyncManager
        SyncManager(
            context,
            mockAuthManager,
            mockFirestoreSync,
            mockSyncQueueDao,
            mockHistoryDao,
            mockTaskDao,
            spyNetworkMonitor
        )

        // Reconnect network
        isOnlineFlow.value = true

        // Verify flushQueue was triggered and uploadRaw called
        coVerify(timeout = 2000) {
            mockFirestoreSync.uploadRaw("tasks", "1", any())
        }
    }
}
