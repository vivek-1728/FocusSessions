package com.focussessions.app.testutils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focussessions.app.connectivity.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake implementation of NetworkMonitor for unit tests.
 * Allows toggling online/offline network states deterministically.
 */
class FakeNetworkMonitor(
    context: Context = ApplicationProvider.getApplicationContext(),
    initialOnline: Boolean = true
) : NetworkMonitor(context) {

    private val _isOnline = MutableStateFlow(initialOnline)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }

    override fun isCurrentlyOnline(): Boolean = _isOnline.value
}
