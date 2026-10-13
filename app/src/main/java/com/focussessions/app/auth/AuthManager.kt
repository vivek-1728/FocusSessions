package com.focussessions.app.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.*
import androidx.credentials.exceptions.*
import com.focussessions.app.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    object Success                          : AuthResult()
    data class Error(val message: String)   : AuthResult()
    object Cancelled                        : AuthResult()
    data class LegacyFallback(val intent: Intent) : AuthResult()
}

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    val isLoggedIn: Boolean get() = auth.currentUser != null

    init {
        // Rehydrate from cached Firebase session on startup
        auth.currentUser?.let { firebaseUser ->
            _currentUser.value = firebaseUser.toUserProfile()
        }
        // Listen for auth state changes (token refresh, sign-out from other screen, etc.)
        auth.addAuthStateListener { fa ->
            if (fa.currentUser == null) _currentUser.value = null
        }
    }

    // ── Sign In ───────────────────────────────────────────────────────────────

    suspend fun signInWithGoogle(activityContext: Context): AuthResult {
        val activity = activityContext.findActivity() ?: activityContext
        return try {
            val credentialManager = CredentialManager.create(activity)

            // Tier 1: Authorized accounts only
            val tier1Option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .build()

            // Tier 2: All accounts
            val tier2Option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            // Tier 3: Legacy/Fallback Google Sign-In bottom sheet
            val tier3Option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(tier1Option)
                .addCredentialOption(tier2Option)
                .addCredentialOption(tier3Option)
                .build()

            val result     = credentialManager.getCredential(activity, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {

                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                signInWithFirebase(googleIdTokenCredential.idToken)
            } else {
                AuthResult.Error("Unsupported credential type: ${credential.type}")
            }

        } catch (e: GetCredentialCancellationException) {
            AuthResult.Cancelled
        } catch (e: GetCredentialException) {
            // Tier 4: Legacy Fallback — Return the intent for ViewModel to launch
            AuthResult.LegacyFallback(getLegacySignInIntent(activity))
        } catch (e: Exception) {
            AuthResult.Error("Sign-in failed: ${e.message}")
        }
    }

    fun getLegacySignInIntent(context: Context): Intent {
        val activity = context.findActivity() ?: context
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(activity, gso).signInIntent
    }

    suspend fun handleLegacySignInResult(data: Intent?): AuthResult {
        return try {
            val task    = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken ?: return AuthResult.Error("No ID token from legacy sign-in")
            signInWithFirebase(idToken)
        } catch (e: ApiException) {
            val detail = when (e.statusCode) {
                10 -> "Developer Error (Code 10): Check your SHA-1 fingerprint and Web Client ID in Firebase Console."
                12500 -> "Sign-in failed (Code 12500): Check network or Google Cloud / Firebase OAuth configuration."
                else -> "Legacy sign-in error (code ${e.statusCode}): ${e.message}"
            }
            AuthResult.Error(detail)
        } catch (e: Exception) {
            AuthResult.Error("Legacy sign-in failed: ${e.message}")
        }
    }

    private suspend fun signInWithFirebase(idToken: String): AuthResult {
        return try {
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult         = auth.signInWithCredential(firebaseCredential).await()
            val user               = authResult.user ?: return AuthResult.Error("Firebase sign-in returned null user")

            val profile = user.toUserProfile()
            upsertProfileInFirestore(profile)
            _currentUser.value = profile
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error("Firebase auth failed: ${e.message}")
        }
    }

    // ── Sign Out ──────────────────────────────────────────────────────────────

    suspend fun signOut() {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) { }
        auth.signOut()
        _currentUser.value = null
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    suspend fun refreshProfile() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val doc = firestore.collection("users").document(uid)
                .collection("profile").document("data")
                .get().await()
            if (doc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val map = doc.data as? Map<String, Any> ?: return
                _currentUser.value = UserProfile.fromFirestoreMap(map)
            }
        } catch (_: Exception) { }
    }

    suspend fun updateProfileStats(totalFocusMinutes: Int, streak: Int) {
        val uid = auth.currentUser?.uid ?: return
        val updates = mapOf(
            "totalFocusMinutes" to totalFocusMinutes,
            "currentStreak"     to streak,
            "updatedAt"         to System.currentTimeMillis()
        )
        try {
            firestore.collection("users").document(uid)
                .collection("profile").document("data")
                .update(updates).await()
            _currentUser.value = _currentUser.value?.copy(
                totalFocusMinutes = totalFocusMinutes,
                currentStreak     = streak
            )
        } catch (_: Exception) { }
    }

    private suspend fun upsertProfileInFirestore(profile: UserProfile) {
        try {
            firestore.collection("users").document(profile.uid)
                .collection("profile").document("data")
                .set(profile.toFirestoreMap()).await()
        } catch (_: Exception) { }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun FirebaseUser.toUserProfile() = UserProfile(
        uid         = uid,
        displayName = displayName ?: "",
        email       = email ?: "",
        photoUrl    = photoUrl?.toString() ?: ""
    )

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is android.content.ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
}
