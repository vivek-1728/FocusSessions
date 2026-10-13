package com.focussessions.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focussessions.app.auth.SyncState
import com.focussessions.app.connectivity.NetworkMonitor
import com.focussessions.app.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncManager: SyncManager,
    val networkMonitor: NetworkMonitor
) : ViewModel() {

    val syncState: StateFlow<SyncState> = syncManager.syncState

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun triggerSync() {
        viewModelScope.launch { syncManager.fullSync() }
    }
}
