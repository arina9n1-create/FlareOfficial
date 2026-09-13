package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.data.remote.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class AuthUserState(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val isLoggedIn: Boolean = false
)

class AuthRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE)
    private val supabaseService = SupabaseService(context)

    private val _userState = MutableStateFlow(loadLocalUserState())
    val userState: StateFlow<AuthUserState> = _userState.asStateFlow()

    private fun loadLocalUserState(): AuthUserState {
        // A session is only restored when we have a real Supabase-issued access token AND a
        // non-synthetic uid. Stale fake sessions (uid "u_...", no token) are not treated as logged in.
        val hasToken = !(prefs.getString("access_token", "") ?: "").isBlank()
        val sessionValid = prefs.getBoolean("session_valid", false)
        val uid = prefs.getString("session_uid", "") ?: ""
        val isRealSession = hasToken && sessionValid && uid.isNotBlank()
        return if (isRealSession) {
            AuthUserState(
                uid = uid,
                displayName = prefs.getString("access_token_name", "") ?: "",
                email = prefs.getString("access_token_email", "") ?: "",
                isLoggedIn = true
            )
        } else {
            AuthUserState()
        }
    }

    // The access token itself is persisted by SupabaseService (same prefs file); the state here
    // always reflects a real authenticated session or nothing.

    suspend fun signUpWithEmail(name: String, email: String, pass: String): Result<AuthUserState> = withContext(Dispatchers.IO) {
        try {
            val result = supabaseService.signUpWithEmail(name, email, pass)
                .map { user ->
                    AuthUserState(
                        uid = user.uid,
                        displayName = user.name,
                        email = user.email,
                        isLoggedIn = true
                    )
                }
            // Only a real Supabase signup (with an issued session) sets a state; failures are
            // propagated and never converted into a fabricated local user.
            result.getOrNull()?.let { _userState.value = it }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<AuthUserState> = withContext(Dispatchers.IO) {
        try {
            // Password verification is performed by Supabase Auth server-side. A wrong password is
            // a failure — there is no fallback user creation.
            val result = supabaseService.signInWithEmail(email, pass)
                .map { user ->
                    AuthUserState(
                        uid = user.uid,
                        displayName = user.name,
                        email = user.email,
                        isLoggedIn = true
                    )
                }
            result.getOrNull()?.let { _userState.value = it }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Validates the persisted Supabase Auth session on startup. If a refresh token exists it is
     * exchanged for a fresh session so the exposed UID always comes from a LIVE Supabase Auth
     * session; a server rejection performs a real sign-out instead of pretending to be logged in.
     */
    suspend fun ensureFreshSession(): AuthUserState = withContext(Dispatchers.IO) {
        val current = _userState.value
        if (!current.isLoggedIn) return@withContext current
        when (supabaseService.refreshAuthSession()) {
            SupabaseService.SessionRefreshResult.REFRESHED -> loadLocalUserState()
            SupabaseService.SessionRefreshResult.REJECTED -> {
                // Supabase refused the refresh token: the session is genuinely gone.
                signOut()
                AuthUserState()
            }
            // Nothing to validate, or a transient network error: keep the restored state.
            else -> current
        }
    }

    suspend fun signOut() {
        // Revoke server-side FIRST while the access token is still stored, then wipe every local
        // credential. A network failure during revocation never blocks the local wipe.
        
        // 1. Remove the FCM token from the backend for this device.
        try {
            val token = com.example.data.notification.NotificationPreferences.getFcmToken(context)
            if (!token.isNullOrBlank()) {
                supabaseService.deleteDeviceToken(token)
            }
        } catch (e: Exception) {
            android.util.Log.w("AuthRepository", "Failed to delete device token on sign-out", e)
        }

        supabaseService.signOutRemote()
        prefs.edit().clear().apply()
        supabaseService.clearAuthSession()
        _userState.value = AuthUserState()
    }
}
