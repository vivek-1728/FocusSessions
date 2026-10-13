package com.focussessions.app.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focussessions.app.auth.AuthManager
import com.focussessions.app.auth.AuthResult
import com.focussessions.app.auth.UserProfile
import com.focussessions.app.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val user: UserProfile?      = null,
    val isLoading: Boolean      = false,
    val error: String           = "",
    val isSignedIn: Boolean     = false,
    val legacySignInIntent: Intent? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                _state.update { it.copy(user = user, isSignedIn = user != null) }
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = "") }
            when (val result = authManager.signInWithGoogle(context)) {
                is AuthResult.Success   -> {
                    _state.update { it.copy(isLoading = false) }
                    syncManager.fullSync()
                }
                is AuthResult.LegacyFallback -> {
                    _state.update { it.copy(isLoading = false, legacySignInIntent = result.intent) }
                }
                is AuthResult.Error     -> _state.update { it.copy(isLoading = false, error = result.message) }
                is AuthResult.Cancelled -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun handleLegacySignInResult(data: Intent?) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, legacySignInIntent = null) }
            when (val result = authManager.handleLegacySignInResult(data)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(isLoading = false) }
                    syncManager.fullSync()
                }
                is AuthResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            _state.update { AuthUiState() }
        }
    }

    fun dismissError() = _state.update { it.copy(error = "") }
}
