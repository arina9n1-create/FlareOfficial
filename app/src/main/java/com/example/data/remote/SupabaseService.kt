package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

class SupabaseService(private val context: Context) {

    companion object {
        private const val TAG = "SupabaseService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val RING_WINDOW_MS = 45000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        // Large media uploads (reels up to 50 MB) need a generous write window;
        // OkHttp's 10 s default would abort them mid-flight on slower links.
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun getBaseHeaders(preferMerge: Boolean = false): Headers {
        val prefs = context.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE)
        var token = prefs.getString("access_token", null)?.takeIf { it.isNotBlank() }
        
        // Supabase access tokens expire after ~1 hour. Proactively refresh an
        // expired token (never on the main thread) so long-lived sessions keep
        // working for REST calls AND the r2-upload media gateway.
        if (token != null && isTokenExpired(token) && Looper.myLooper() != Looper.getMainLooper()) {
            if (performTokenRefresh() == SessionRefreshResult.REFRESHED) {
                token = prefs.getString("access_token", null)?.takeIf { it.isNotBlank() }
            }
        }
        
        val builder = Headers.Builder()
            .add("apikey", Backend.KEY)
        
        // If we have a token, use it. Otherwise, use the anon key for authorization.
        // This ensures auth.uid() is populated in RLS when logged in.
        val authValue = if (!token.isNullOrBlank()) "Bearer $token" else "Bearer ${Backend.KEY}"
        builder.add("Authorization", authValue)
        
        if (preferMerge) {
            builder.add("Prefer", "resolution=merge-duplicates,return=representation")
        }
        return builder.build()
    }

    /** True when the Supabase JWT is expired (or expiring within 30 s). */
    private fun isTokenExpired(token: String): Boolean = try {
        val parts = token.split(".")
        if (parts.size < 2) false else {
            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            val exp = JSONObject(payload).optLong("exp", 0L)
            exp > 0 && exp - 30 <= System.currentTimeMillis() / 1000
        }
    } catch (e: Exception) { false }

        private fun executeChecked(request: Request): String {
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        response.body?.close()
        // CRITICAL: consume and close the response body so the connection is
        // returned to OkHttp's pool. Without this OkHttp can leak sockets on
        // background threads and later dispatcher calls throw NullPointerException
        // (reported as "LocalTokenDeviceMaster ... ExecutorsHandler"), which is
        // exactly the NPE crash seen in token upload / push flows.

        if (response.code == 401) {
            // CRITICAL: If we get a 401, it means the current access token is invalid or expired.
            // We should notify the session manager to clear it.
            Log.e(TAG, "Supabase 401 Unauthorized: $body")
            if (body.contains("UNAUTHORIZED_ASYMMETRIC_KEY", ignoreCase = true) ||
                body.contains("JWT", ignoreCase = true) ||
                body.contains("invalid", ignoreCase = true)) {

                // Clear the potentially corrupted/expired session locally
                context.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE).edit()
                    .putBoolean("session_valid", false)
                    .remove("access_token")
                    .apply()
            }
        }

        if (!response.isSuccessful) {
            throw Exception("Supabase request failed (${response.code}): $body")
        }
        return body
    }

    /**
     * Same as [executeChecked] but retries once on a 401 by forcing a fresh token
     * refresh first. This is essential for token-upload paths ([saveDeviceToken])
     * that can fire from [FirebaseMessagingService.onNewToken] — a callback that
     * is delivered even while the app is killed/backgrounded, when the cached
     * access token may be expired and there is no UI to trigger a refresh.
     */
    private fun executeCheckedWithRetry(request: Request): String {
        try {
            return executeChecked(request)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("(401)") || msg.contains("401")) {
                Log.d(TAG, "Initial request unauthorized; attempting token refresh")
                val result = performTokenRefresh()
                if (result == SessionRefreshResult.REFRESHED) {
                    Log.d(TAG, "Token refreshed; retrying request")
                    return executeChecked(request)
                }
            }
            throw e
        }
    }

    private fun JSONObject.textOrDefault(key: String, default: String = ""): String {
        return optString(key, default).takeIf { it != "null" } ?: default
    }

    private fun parseSingleId(bodyString: String): String {
        return try {
            val array = JSONArray(bodyString)
            if (array.length() == 0) return ""
            array.optJSONObject(0)?.optString("id", "") ?: ""
        } catch (e: Exception) { "" }
    }

    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Hit the REST health check endpoint which is more reliable
            val url = "${Backend.URL}/rest/v1/"
            val request = Request.Builder()
                .url(url)
                .headers(getBaseHeaders())
                .get()
                .build()
            client.newCall(request).execute().use { 
                Log.d(TAG, "Supabase Connection test: ${it.code}")
                // 200/204 is success, 401 is also okay as it means the server is there but rejecting the key
                it.isSuccessful || it.code == 401 || it.code == 404 || it.code == 204
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Supabase Connection failed", e)
            throw e // Let the caller handle the specific exception message
        }
    }

    // Auth
    suspend fun signUpWithEmail(name: String, email: String, pass: String): Result<AppUserEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/auth/v1/signup"
            val payload = JSONObject().apply {
                put("email", email)
                put("password", pass)
                put("data", JSONObject().apply { put("full_name", name) })
            }
            val res = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            val obj = JSONObject(res)
            val user = obj.optJSONObject("user") ?: throw Exception("Auth failed: No user object in response")
            // When email confirmation is disabled Supabase returns a session right away —
            // persist it so the very first upload doesn't bounce with Unauthorized.
            obj.optJSONObject("session")?.let { persistSession(it, user) }
            Result.success(AppUserEntity(uid = user.optString("id", ""), name = name, handle = email.substringBefore("@"), email = email))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<AppUserEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/auth/v1/token?grant_type=password"
            val payload = JSONObject().apply { put("email", email); put("password", pass) }
            val res = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            val obj = JSONObject(res)
            val user = obj.optJSONObject("user") ?: throw Exception("Auth failed: No user object in response")

            // Persist access + refresh tokens so the session can be renewed hourly.
            persistSession(obj, user)
            // Cache display identity so a cold app start can render the profile
            // before the first network sync.
            context.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE).edit()
                .putString("access_token_name", user.optJSONObject("user_metadata")?.optString("full_name", "") ?: "")
                .putString("access_token_email", email)
                .apply()

            Result.success(AppUserEntity(uid = user.optString("id", ""), name = user.optJSONObject("user_metadata")?.optString("full_name", "") ?: "", handle = email.substringBefore("@"), email = email))
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Persists access + refresh tokens (and uid) from a Supabase auth response object. */
    private fun persistSession(authResponse: JSONObject, user: JSONObject) {
        val session = authResponse.optString("access_token", "")
        if (session.isBlank()) return
        context.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE).edit()
            .putString("access_token", session)
            .putString("refresh_token", authResponse.optString("refresh_token", ""))
            .putString("session_uid", user.optString("id", ""))
            .putBoolean("session_valid", true)
            .apply()
    }

    private val refreshMutex = Any()

    /**
     * Exchanges the stored refresh token for a fresh access token. Supabase access
     * tokens expire after ~1 hour; without this every request (including the
     * r2-upload media gateway) fails with Unauthorized afterwards.
     */
    suspend fun refreshAuthSession(): SessionRefreshResult = withContext(Dispatchers.IO) {
        performTokenRefresh()
    }

    private fun performTokenRefresh(): SessionRefreshResult = synchronized(refreshMutex) {
        val prefs = context.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE)
        val refreshToken = prefs.getString("refresh_token", null)?.takeIf { it.isNotBlank() }
            ?: return SessionRefreshResult.NO_CHANGE
        try {
            val payload = JSONObject().put("refresh_token", refreshToken)
            val request = Request.Builder()
                .url("${Backend.URL}/auth/v1/token?grant_type=refresh_token")
                .headers(Headers.Builder().add("apikey", Backend.KEY).build())
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val res = executeChecked(request)
            val obj = JSONObject(res)
            val user = obj.optJSONObject("user")
            prefs.edit()
                .putString("access_token", obj.optString("access_token", ""))
                .putString("refresh_token", obj.optString("refresh_token", ""))
                .putString("session_uid", user?.optString("id", "") ?: "")
                .putBoolean("session_valid", true)
                .apply()
            Log.d(TAG, "Supabase session refreshed successfully")
            SessionRefreshResult.REFRESHED
        } catch (e: Exception) {
            val rejected = e.message?.contains("Supabase request failed (400") == true ||
                e.message?.contains("invalid_grant", ignoreCase = true) == true
            if (rejected) {
                Log.e(TAG, "Supabase refresh token rejected — session is gone", e)
                SessionRefreshResult.REJECTED
            } else {
                // Transient network problem: keep the current session and retry later.
                Log.w(TAG, "Transient session refresh failure", e)
                SessionRefreshResult.NO_CHANGE
            }
        }
    }

    enum class SessionRefreshResult { REFRESHED, REJECTED, NO_CHANGE }

    suspend fun signOutRemote() = withContext(Dispatchers.IO) {
        try {
            // Revoke the session server-side so the refresh token can't be reused.
            executeChecked(
                Request.Builder()
                    .url("${Backend.URL}/auth/v1/logout?scope=global")
                    .headers(getBaseHeaders())
                    .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Remote sign-out failed (local wipe continues)", e)
        }
    }

    fun clearAuthSession() {
        context.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE).edit()
            .remove("access_token")
            .remove("refresh_token")
            .putBoolean("session_valid", false)
            .apply()
    }

    // ---------------------------------------------------------------------------
    // FCM device token registration (push notifications)
    // ---------------------------------------------------------------------------

    /**
     * Upserts this device's FCM registration token into the Supabase
     * `device_tokens` table for the currently signed-in user.
     *
     * USES ATOMIC UPSERT: A unique constraint on `device_id` (added in migration)
     * ensures that each physical device has exactly one row. If the same user
     * logs in on another device, a new row is created. If another user logs in
     * on this device, the existing row's `user_id` is updated.
     *
     * This prevents duplicate notifications and ensures tokens remain valid
     * after the app is swiped away from Recent Apps.
     */
    suspend fun saveDeviceToken(deviceId: String, fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE)
        val uid = prefs.getString("session_uid", null)?.takeIf { it.isNotBlank() }
        if (uid.isNullOrBlank()) {
            Log.w(TAG, "Cannot save FCM token: no authenticated user")
            return@withContext false
        }

        val utcNow = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        // Use a single atomic POST (Upsert) instead of DELETE then INSERT.
        // PostgREST upsert requires Prefer: resolution=merge-duplicates and
        // the on_conflict parameter if multiple constraints exist.
        return@withContext try {
            // Upsert on the ACTUAL unique constraint (device_tokens_user_id_token_key
            // => columns user_id + token). Using on_conflict=device_id made PostgREST
            // attempt a plain INSERT, which 409'd with a duplicate-key violation on
            // (user_id, token) whenever the user already had this token stored.
            val upsertUrl = "${Backend.URL}/rest/v1/device_tokens?on_conflict=user_id,token"
            Log.d(TAG, "Upserting FCM token for user $uid on device $deviceId")
            
            val payload = JSONObject().apply {
                put("user_id", uid)
                put("device_id", deviceId)
                put("token", fcmToken)
                put("updated_at", utcNow)
            }

            executeCheckedWithRetry(
                Request.Builder()
                    .url(upsertUrl)
                    .headers(getBaseHeaders(preferMerge = true))
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            )
            Log.d(TAG, "FCM token upserted successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upsert FCM device token: ${e.message}", e)
            false
        }
    }

    /**
     * Liveness probe for the push pipeline: reads this user's rows from
     * `device_tokens` with the SAME (user-level) credentials the client uses
     * everywhere else. Returns (rowCount, lastUpdatedAt); rowCount == -1 means
     * the query itself failed (network / not signed in / server error).
     */
    suspend fun checkDeviceTokenRegistered(): Pair<Int, String?> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE)
        val uid = prefs.getString("session_uid", null)?.takeIf { it.isNotBlank() }
        if (uid.isNullOrBlank()) return@withContext 0 to null

        return@withContext try {
            val url = "${Backend.URL}/rest/v1/device_tokens?user_id=eq.$uid&select=updated_at"
            val body = executeCheckedWithRetry(
                Request.Builder()
                    .url(url)
                    .headers(getBaseHeaders())
                    .get()
                    .build()
            )
            val rows = org.json.JSONArray(body)
            if (rows.length() == 0) {
                0 to null
            } else {
                val updated = rows.getJSONObject(0).optString("updated_at", "").takeIf { it.isNotBlank() }
                rows.length() to updated
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkDeviceTokenRegistered failed: ${e.message}", e)
            -1 to null
        }
    }

    /**
     * Removes a specific FCM token for the currently signed-in user from the
     * `device_tokens` table. Called during explicit logout to ensure the device
     * stops receiving notifications for that user account.
     */
    suspend fun deleteDeviceToken(fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE)
        val uid = prefs.getString("session_uid", null)?.takeIf { it.isNotBlank() }
        if (uid.isNullOrBlank()) return@withContext false

        return@withContext try {
            val deleteUrl = "${Backend.URL}/rest/v1/device_tokens?user_id=eq.$uid&token=eq.$fcmToken"
            executeCheckedWithRetry(
                Request.Builder()
                    .url(deleteUrl)
                    .headers(getBaseHeaders())
                    .delete()
                    .build()
            )
            Log.d(TAG, "FCM token deleted successfully for user $uid")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete FCM token: ${e.message}")
            false
        }
    }

    suspend fun fetchUserByUid(uid: String): Result<AppUserEntity?> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/app_users?uid=eq.$uid&select=*"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val array = JSONArray(body)
            if (array.length() == 0) return@withContext Result.success(null)
            
            val obj = array.optJSONObject(0) ?: return@withContext Result.success(null)
            Result.success(
                AppUserEntity(
                    uid = obj.textOrDefault("uid"),
                    name = obj.textOrDefault("name"),
                    handle = obj.textOrDefault("handle"),
                    email = obj.textOrDefault("email"),
                    role = obj.textOrDefault("role", "USER"),
                    avatarType = obj.textOrDefault("avatar_type", "default"),
                    coverType = obj.textOrDefault("cover_type", "default"),
                    bio = obj.textOrDefault("bio"),
                    location = obj.textOrDefault("location"),
                    isBanned = obj.optBoolean("is_banned", false),
                    banReason = obj.textOrDefault("ban_reason"),
                    canManageUsers = obj.optBoolean("can_manage_users", false),
                    canDeletePosts = obj.optBoolean("can_delete_posts", false),
                    canEditPosts = obj.optBoolean("can_edit_posts", false),
                    canModerateComments = obj.optBoolean("can_moderate_comments", false),
                    canManageChats = obj.optBoolean("can_manage_chats", false),
                    canManageMonetization = obj.optBoolean("can_manage_monetization", false),
                    canManageRewards = obj.optBoolean("can_manage_rewards", false),
                    canManageRewardRules = obj.optBoolean("can_manage_reward_rules", false),
                    canManageRewardRates = obj.optBoolean("can_manage_reward_rates", false),
                    canManageRewardGateways = obj.optBoolean("can_manage_reward_gateways", false),
                    canProcessPayouts = obj.optBoolean("can_process_payouts", false),
                    canCleanStorage = obj.optBoolean("can_clean_storage", false),
                    canViewReports = obj.optBoolean("can_view_reports", false),
                    canReviewReports = obj.optBoolean("can_review_reports", false),
                    canGiveWarning = obj.optBoolean("can_give_warning", false),
                    canDeleteReel = obj.optBoolean("can_delete_reel", false),
                    canDeleteVideo = obj.optBoolean("can_delete_video", false),
                    canSuspendUser = obj.optBoolean("can_suspend_user", false),
                    canBanUser = obj.optBoolean("can_ban_user", false),
                    canRemoveWarning = obj.optBoolean("can_remove_warning", false),
                    canViewWarningHistory = obj.optBoolean("can_view_warning_history", false),
                    canViewActivityLog = obj.optBoolean("can_view_activity_log", false),
                    isPublic = obj.optBoolean("is_public", true),
                    registeredAt = obj.optLong("registered_at", System.currentTimeMillis()),
                    avatarPath = com.example.util.MediaStorageResolver.toStorableKey(obj.optString("avatar_path")),
                    coverPath = com.example.util.MediaStorageResolver.toStorableKey(obj.optString("cover_path")),
                    avatarProvider = obj.optString("avatar_provider", "cloudflare_r2"),
                    coverProvider = obj.optString("cover_provider", "cloudflare_r2"),
                    avatarMime = obj.optString("avatar_mime").takeIf { it != "null" && it.isNotBlank() },
                    coverMime = obj.optString("cover_mime").takeIf { it != "null" && it.isNotBlank() },
                    avatarSize = obj.optLong("avatar_size", 0),
                    coverSize = obj.optLong("cover_size", 0)
                )
            )
        } catch (e: Exception) { Result.failure(e) }
    }

    // Users
    suspend fun fetchAllUsers(): List<AppUserEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/app_users?select=*"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            val list = mutableListOf<AppUserEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(AppUserEntity(
                    uid = obj.optString("uid", ""),
                    name = obj.textOrDefault("name"),
                    handle = obj.textOrDefault("handle"),
                    email = obj.textOrDefault("email"),
                    role = obj.textOrDefault("role", "USER"),
                    avatarType = obj.textOrDefault("avatar_type", "default"),
                    coverType = obj.textOrDefault("cover_type", "default"),
                    bio = obj.textOrDefault("bio"),
                    location = obj.textOrDefault("location"),
                    isBanned = obj.optBoolean("is_banned", false),
                    banReason = obj.textOrDefault("ban_reason"),
                    canManageUsers = obj.optBoolean("can_manage_users", false),
                    canDeletePosts = obj.optBoolean("can_delete_posts", false),
                    canEditPosts = obj.optBoolean("can_edit_posts", false),
                    canModerateComments = obj.optBoolean("can_moderate_comments", false),
                    canManageChats = obj.optBoolean("can_manage_chats", false),
                    canManageMonetization = obj.optBoolean("can_manage_monetization", false),
                    canManageRewards = obj.optBoolean("can_manage_rewards", false),
                    canManageRewardRules = obj.optBoolean("can_manage_reward_rules", false),
                    canManageRewardRates = obj.optBoolean("can_manage_reward_rates", false),
                    canManageRewardGateways = obj.optBoolean("can_manage_reward_gateways", false),
                    canProcessPayouts = obj.optBoolean("can_process_payouts", false),
                    canCleanStorage = obj.optBoolean("can_clean_storage", false),
                    canViewReports = obj.optBoolean("can_view_reports", false),
                    canReviewReports = obj.optBoolean("can_review_reports", false),
                    canGiveWarning = obj.optBoolean("can_give_warning", false),
                    canDeleteReel = obj.optBoolean("can_delete_reel", false),
                    canDeleteVideo = obj.optBoolean("can_delete_video", false),
                    canSuspendUser = obj.optBoolean("can_suspend_user", false),
                    canBanUser = obj.optBoolean("can_ban_user", false),
                    canRemoveWarning = obj.optBoolean("can_remove_warning", false),
                    canViewWarningHistory = obj.optBoolean("can_view_warning_history", false),
                    canViewActivityLog = obj.optBoolean("can_view_activity_log", false),
                    isPublic = obj.optBoolean("is_public", true),
                    registeredAt = obj.optLong("registered_at", System.currentTimeMillis()),
                    avatarPath = obj.optString("avatar_path").takeIf { it != "null" && it.isNotBlank() },
                    coverPath = obj.optString("cover_path").takeIf { it != "null" && it.isNotBlank() },
                    avatarProvider = obj.optString("avatar_provider", "cloudflare_r2"),
                    coverProvider = obj.optString("cover_provider", "cloudflare_r2"),
                    avatarMime = obj.optString("avatar_mime").takeIf { it != "null" && it.isNotBlank() },
                    coverMime = obj.optString("cover_mime").takeIf { it != "null" && it.isNotBlank() },
                    avatarSize = obj.optLong("avatar_size", 0),
                    coverSize = obj.optLong("cover_size", 0)
                ))
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun syncUser(user: AppUserEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/app_users"
            val payload = JSONObject().apply {
                put("uid", user.uid)
                put("name", user.name)
                put("handle", user.handle)
                put("email", user.email)
                // CRITICAL: NEVER sync role or permission flags during normal user sync.
                // These are server-authoritative and can only be changed via RPC/Admin.
                put("avatar_type", user.avatarType)
                put("cover_type", user.coverType)
                put("avatar_path", user.avatarPath)
                put("cover_path", user.coverPath)
                put("bio", user.bio)
                put("location", user.location)
                put("is_public", user.isPublic)
                put("avatar_provider", user.avatarProvider)
                put("cover_provider", user.coverProvider)
                put("avatar_mime", user.avatarMime)
                put("cover_mime", user.coverMime)
                put("avatar_size", user.avatarSize)
                put("cover_size", user.coverSize)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun patchUserProfile(
        uid: String, 
        avatarType: String?, 
        coverType: String?, 
        avatarPath: String?, 
        coverPath: String?, 
        name: String?, 
        handle: String?, 
        bio: String?, 
        location: String?, 
        isPublic: Boolean? = null,
        avatarProvider: String? = null,
        coverProvider: String? = null,
        avatarMime: String? = null,
        coverMime: String? = null,
        avatarSize: Long? = null,
        coverSize: Long? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/app_users?uid=eq.$uid"
            val payload = JSONObject().apply {
                avatarType?.let { put("avatar_type", com.example.util.MediaStorageResolver.toStorableKey(it)) }
                coverType?.let { put("cover_type", com.example.util.MediaStorageResolver.toStorableKey(it)) }
                avatarPath?.let { put("avatar_path", com.example.util.MediaStorageResolver.toStorableKey(it)) }
                coverPath?.let { put("cover_path", com.example.util.MediaStorageResolver.toStorableKey(it)) }
                name?.let { put("name", it) }
                handle?.let { put("handle", it) }
                bio?.let { put("bio", it) }
                location?.let { put("location", it) }
                isPublic?.let { put("is_public", it) }
                avatarProvider?.let { put("avatar_provider", it) }
                coverProvider?.let { put("cover_provider", it) }
                avatarMime?.let { put("avatar_mime", it) }
                coverMime?.let { put("cover_mime", it) }
                avatarSize?.let { put("avatar_size", it) }
                coverSize?.let { put("cover_size", it) }
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun searchUsers(query: String): List<AppUserEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/app_users?or=(handle.ilike.*$query*,name.ilike.*$query*)&limit=10"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                AppUserEntity(uid = obj.optString("uid", ""), name = obj.textOrDefault("name"), handle = obj.textOrDefault("handle"), email = obj.textOrDefault("email"))
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun updateUserRoleAndPermissionsRemote(
        uid: String,
        role: String,
        canManageUsers: Boolean,
        canDeletePosts: Boolean,
        canEditPosts: Boolean,
        canModerateComments: Boolean,
        canManageChats: Boolean,
        canManageMonetization: Boolean,
        canManageRewards: Boolean,
        canManageRewardRules: Boolean,
        canManageRewardRates: Boolean,
        canManageRewardGateways: Boolean,
        canProcessPayouts: Boolean,
        canCleanStorage: Boolean,
        canViewReports: Boolean,
        canReviewReports: Boolean,
        canGiveWarning: Boolean,
        canDeleteReel: Boolean,
        canDeleteVideo: Boolean,
        canSuspendUser: Boolean,
        canBanUser: Boolean,
        canRemoveWarning: Boolean,
        canViewWarningHistory: Boolean,
        canViewActivityLog: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/promote_user"
            val payload = JSONObject().apply {
                put("target_uid", uid)
                put("new_role", role)
                put("new_can_manage_users", canManageUsers)
                put("new_can_delete_posts", canDeletePosts)
                put("new_can_edit_posts", canEditPosts)
                put("new_can_moderate_comments", canModerateComments)
                put("new_can_manage_chats", canManageChats)
                put("new_can_manage_monetization", canManageMonetization)
                put("new_can_manage_rewards", canManageRewards)
                put("new_can_manage_reward_rules", canManageRewardRules)
                put("new_can_manage_reward_rates", canManageRewardRates)
                put("new_can_manage_reward_gateways", canManageRewardGateways)
                put("new_can_process_payouts", canProcessPayouts)
                put("new_can_clean_storage", canCleanStorage)
                put("new_can_view_reports", canViewReports)
                put("new_can_review_reports", canReviewReports)
                put("new_can_give_warning", canGiveWarning)
                put("new_can_delete_reel", canDeleteReel)
                put("new_can_delete_video", canDeleteVideo)
                put("new_can_suspend_user", canSuspendUser)
                put("new_can_ban_user", canBanUser)
                put("new_can_remove_warning", canRemoveWarning)
                put("new_can_view_warning_history", canViewWarningHistory)
                put("new_can_view_activity_log", canViewActivityLog)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { 
            Log.e(TAG, "promote_user RPC failed", e)
            Result.failure(e) 
        }
    }

    suspend fun updateUserBanStatusRemote(uid: String, isBanned: Boolean, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/ban_user_rpc"
            val payload = JSONObject().apply {
                put("target_uid", uid)
                put("ban_status", isBanned)
                put("reason", reason)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { 
            Log.e(TAG, "promote_user RPC failed", e)
            Result.failure(e) 
        }
    }

    suspend fun deleteUser(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/delete_user_rpc"
            val payload = JSONObject().apply { put("target_uid", uid) }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "delete_user_rpc failed", e)
            Result.failure(e)
        }
    }

    /**
     * SUPER_ADMIN only. Permanently deletes every account EXCEPT the account whose
     * @handle equals [ceoHandle]. Returns the number of accounts removed.
     * Backed by the SECURITY DEFINER RPC admin_delete_all_except_ceo (see migration
     * 20260903000000_admin_delete_fix_and_cleanup.sql).
     */
    suspend fun deleteAllUsersExcept(ceoHandle: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/admin_delete_all_except_ceo"
            val payload = JSONObject().apply { put("ceo_handle", ceoHandle) }
            val body = executeChecked(
                Request.Builder().url(url).headers(getBaseHeaders())
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
            )
            // The RPC returns a scalar INT. PostgREST may wrap it as a JSON array
            // ("[5]") or return a bare number ("5"); either is fine.
            val cleaned = body.trim().removePrefix("[").removeSuffix("]").trim()
            val count = cleaned.replace("\"", "").toIntOrNull() ?: 0
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "admin_delete_all_except_ceo failed", e)
            Result.failure(e)
        }
    }

    // Posts
    suspend fun fetchPosts(): List<PostEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/posts?select=*&order=timestamp.desc"
            val bodyString = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val array = JSONArray(bodyString)
            val list = mutableListOf<PostEntity>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(PostEntity(
                    id = obj.optLong("id", System.currentTimeMillis()),
                    remoteId = obj.optString("id", ""),
                    username = obj.textOrDefault("username"),
                    userHandle = obj.textOrDefault("user_handle"),
                    userAvatarType = com.example.util.MediaStorageResolver.toStorableKey(obj.textOrDefault("user_avatar_type", "default")),
                    userAvatarPath = com.example.util.MediaStorageResolver.toStorableKey(obj.optString("user_avatar_path")),
                    actionText = obj.textOrDefault("action_text"),
                    postImageRes = com.example.util.MediaStorageResolver.toStorableKey(obj.textOrDefault("post_image_res")),
                    storagePath = com.example.util.MediaStorageResolver.toStorableKey(obj.optString("storage_path")),
                    thumbnailPath = com.example.util.MediaStorageResolver.toStorableKey(obj.optString("thumbnail_path")),
                    caption = obj.textOrDefault("caption"),
                    likesCount = obj.optInt("likes_count", 0),
                    isLiked = obj.optBoolean("is_liked", false),
                    isSaved = obj.optBoolean("is_saved", false),
                    isReposted = obj.optBoolean("is_reposted", false),
                    repostsCount = obj.optInt("reposts_count", 0),
                    commentsCount = obj.optInt("comments_count", 0),
                    isPublic = obj.optBoolean("is_public", true),
                    isReelPost = obj.optBoolean("is_reel_post", false),
                    timeAgo = obj.textOrDefault("time_ago", "Just now"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    storageProvider = obj.optString("storage_provider", "cloudflare_r2"),
                    mimeType = obj.optString("mime_type").takeIf { it != "null" && it.isNotBlank() },
                    fileSize = obj.optLong("file_size", 0)
                ))
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createPost(post: PostEntity): Result<PostEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/posts"
            val payload = JSONObject().apply {
                put("username", post.username)
                put("user_handle", post.userHandle)
                put("user_avatar_type", com.example.util.MediaStorageResolver.toStorableKey(post.userAvatarType))
                put("user_avatar_path", com.example.util.MediaStorageResolver.toStorableKey(post.userAvatarPath))
                put("action_text", post.actionText)
                put("post_image_res", com.example.util.MediaStorageResolver.toStorableKey(post.postImageRes))
                put("storage_path", com.example.util.MediaStorageResolver.toStorableKey(post.storagePath))
                put("thumbnail_path", com.example.util.MediaStorageResolver.toStorableKey(post.thumbnailPath))
                put("caption", post.caption)
                put("likes_count", 0)
                put("is_public", post.isPublic)
                put("is_reel_post", post.isReelPost)
                put("timestamp", post.timestamp)
                put("storage_provider", post.storageProvider)
                put("mime_type", post.mimeType)
                put("file_size", post.fileSize)
            }
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(post.copy(remoteId = parseSingleId(body)))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deletePost(remoteId: String) {
        try {
            executeChecked(Request.Builder().url("${Backend.URL}/rest/v1/posts?id=eq.$remoteId").headers(getBaseHeaders()).delete().build())
        } catch (e: Exception) { Log.e(TAG, "Supabase request failed", e) }
    }

    suspend fun togglePostLike(postId: Long, userId: String, shouldLike: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/toggle_post_like"
            val payload = JSONObject().apply {
                put("p_post_id", postId)
                put("p_user_id", userId)
                put("p_should_like", shouldLike)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun togglePostRepost(postId: Long, userId: String, shouldRepost: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/toggle_post_repost"
            val payload = JSONObject().apply {
                put("p_post_id", postId)
                put("p_user_id", userId)
                put("p_should_repost", shouldRepost)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun togglePostSave(postId: Long, userId: String, shouldSave: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/toggle_post_save"
            val payload = JSONObject().apply {
                put("p_post_id", postId)
                put("p_user_id", userId)
                put("p_should_save", shouldSave)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun fetchUserLikedPosts(userId: String): Set<Long> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/post_likes?user_id=eq.$userId&select=post_id"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            val set = mutableSetOf<Long>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val postIdValue = obj.opt("post_id")
                val postId = when (postIdValue) {
                    is Number -> postIdValue.toLong()
                    is String -> postIdValue.toLongOrNull() ?: 0L
                    else -> 0L
                }
                if (postId != 0L) set.add(postId)
            }
            set
        } catch (e: Exception) { emptySet() }
    }

    suspend fun updatePostState(remoteKey: String, isSaved: Boolean, isReposted: Boolean, repostsCount: Int) {}

    suspend fun fetchUserRepostedPosts(userId: String): Set<Long> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/post_reposts?user_id=eq.$userId&select=post_id"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            val set = mutableSetOf<Long>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val pid = when (val v = obj.opt("post_id")) { is Number -> v.toLong(); is String -> v.toLongOrNull() ?: 0L; else -> 0L }
                if (pid != 0L) set.add(pid)
            }
            set
        } catch (e: Exception) { emptySet() }
    }

    suspend fun fetchUserSavedPosts(userId: String): Set<Long> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/saved_posts?user_id=eq.$userId&select=post_id"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            val set = mutableSetOf<Long>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val pid = when (val v = obj.opt("post_id")) { is Number -> v.toLong(); is String -> v.toLongOrNull() ?: 0L; else -> 0L }
                if (pid != 0L) set.add(pid)
            }
            set
        } catch (e: Exception) { emptySet() }
    }

    // Reels
    suspend fun fetchReels(): List<ReelEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/reels?select=*"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            val list = mutableListOf<ReelEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(ReelEntity(
                    id = obj.optLong("id", System.currentTimeMillis()),
                    remoteId = obj.optString("id", ""),
                    author = obj.textOrDefault("author"),
                    handle = obj.textOrDefault("handle"),
                    avatarType = com.example.util.MediaStorageResolver.toStorableKey(obj.textOrDefault("avatar_type")),
                    userAvatarPath = com.example.util.MediaStorageResolver.toStorableKey(obj.optString("user_avatar_path")),
                    caption = obj.textOrDefault("caption"),
                    music = obj.textOrDefault("music", "Original Audio"),
                    imageRes = com.example.util.MediaStorageResolver.toStorableKey(obj.textOrDefault("image_res")),
                    videoUrl = com.example.util.MediaStorageResolver.toStorableKey(obj.optString("video_url")),
                    storagePath = com.example.util.MediaStorageResolver.toStorableKey(obj.optString("storage_path")),
                    thumbnailPath = com.example.util.MediaStorageResolver.toStorableKey(obj.optString("thumbnail_path")),
                    location = obj.textOrDefault("location"),
                    effectName = obj.optString("effect_name", "").takeIf { it != "null" },
                    likesCount = obj.optInt("likes_count", 0),
                    commentsCount = obj.optInt("comments_count", 0),
                    sharesCount = obj.optInt("shares_count", 0),
                    durationSecs = obj.optInt("duration_secs", 0),
                    isLiked = obj.optBoolean("is_liked", false),
                    isSaved = obj.optBoolean("is_saved", false),
                    isPublic = obj.optBoolean("is_public", true),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    storageProvider = obj.optString("storage_provider", "cloudflare_r2"),
                    mimeType = obj.optString("mime_type").takeIf { it != "null" && it.isNotBlank() },
                    fileSize = obj.optLong("file_size", 0)
                ))
            }
            list
        } catch (e: Exception) {
            android.util.Log.e(TAG, "fetchReels FAILED — falling back to local reel cache", e)
            emptyList()
        }
    }

    suspend fun createReel(reel: ReelEntity): Result<ReelEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/reels"
            val payload = JSONObject().apply {
                put("author", reel.author)
                put("handle", reel.handle)
                put("avatar_type", com.example.util.MediaStorageResolver.toStorableKey(reel.avatarType))
                put("user_avatar_path", com.example.util.MediaStorageResolver.toStorableKey(reel.userAvatarPath))
                put("caption", reel.caption)
                put("video_url", com.example.util.MediaStorageResolver.toStorableKey(reel.videoUrl))
                put("image_res", com.example.util.MediaStorageResolver.toStorableKey(reel.imageRes))
                put("storage_path", com.example.util.MediaStorageResolver.toStorableKey(reel.storagePath))
                put("thumbnail_path", com.example.util.MediaStorageResolver.toStorableKey(reel.thumbnailPath))
                put("duration_secs", reel.durationSecs)
                put("is_public", reel.isPublic)
                put("timestamp", reel.timestamp)
                put("storage_provider", reel.storageProvider)
                put("mime_type", reel.mimeType)
                put("file_size", reel.fileSize)
                put("shares_count", 0)
            }
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(reel.copy(remoteId = parseSingleId(body)))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteReel(remoteId: String) {
        try {
            executeChecked(Request.Builder().url("${Backend.URL}/rest/v1/reels?id=eq.$remoteId").headers(getBaseHeaders()).delete().build())
        } catch (e: Exception) { Log.e(TAG, "Supabase request failed", e) }
    }

    suspend fun updateReelRemote(remoteId: String, caption: String, music: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/reels?id=eq.$remoteId"
            val payload = JSONObject().apply {
                put("caption", caption)
                put("music", music)
            }
            executeChecked(
                Request.Builder()
                    .url(url)
                    .headers(getBaseHeaders())
                    .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateReelRemote failed", e)
            Result.failure(e)
        }
    }

    suspend fun toggleReelLike(reelId: Long, userId: String, shouldLike: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/toggle_reel_like"
            val payload = JSONObject().apply {
                put("p_reel_id", reelId)
                put("p_user_id", userId)
                put("p_should_like", shouldLike)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun toggleReelRepost(reelId: Long, userId: String, shouldRepost: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/toggle_reel_repost"
            val payload = JSONObject().apply {
                put("p_reel_id", reelId)
                put("p_user_id", userId)
                put("p_should_repost", shouldRepost)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun toggleReelSave(reelId: Long, userId: String, shouldSave: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/toggle_reel_save"
            val payload = JSONObject().apply {
                put("p_reel_id", reelId)
                put("p_user_id", userId)
                put("p_should_save", shouldSave)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun fetchUserLikedReels(userId: String): Set<Long> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/reel_likes?user_id=eq.$userId&select=reel_id"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            val set = mutableSetOf<Long>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val rid = when (val v = obj.opt("reel_id")) { is Number -> v.toLong(); is String -> v.toLongOrNull() ?: 0L; else -> 0L }
                if (rid != 0L) set.add(rid)
            }
            set
        } catch (e: Exception) { emptySet() }
    }

    // Comments
    suspend fun fetchComments(postRemoteKey: String): List<CommentEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/comments?post_id=eq.$postRemoteKey&order=timestamp.asc"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val array = JSONArray(body)
            val list = mutableListOf<CommentEntity>()
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                list.add(CommentEntity(
                    id = row.optLong("id", System.currentTimeMillis()),
                    remoteId = row.optString("id", ""),
                    postId = postRemoteKey.toLongOrNull() ?: 0L,
                    username = row.textOrDefault("username"),
                    userAvatarType = row.textOrDefault("user_avatar_type", "default"),
                    userAvatarPath = row.optString("user_avatar_path").takeIf { it != "null" && it.isNotBlank() },
                    text = row.textOrDefault("text"),
                    timeAgo = row.textOrDefault("time_ago", "Just now"),
                    timestamp = row.optLong("timestamp", System.currentTimeMillis())
                ))
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun addComment(comment: CommentEntity, postRemoteKey: String, userId: String): Result<CommentEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/comments"
            val payload = JSONObject().apply {
                put("post_id", postRemoteKey)
                put("user_id", userId)
                put("username", comment.username)
                put("user_avatar_type", comment.userAvatarType)
                put("user_avatar_path", comment.userAvatarPath)
                put("text", comment.text)
                put("timestamp", comment.timestamp)
            }
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(comment.copy(remoteId = parseSingleId(body)))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteComment(remoteId: String) {
        try {
            executeChecked(Request.Builder().url("${Backend.URL}/rest/v1/comments?id=eq.$remoteId").headers(getBaseHeaders()).delete().build())
        } catch (e: Exception) { Log.e(TAG, "Supabase request failed", e) }
    }

    // Chats & Calls Realtime
    fun observeCallSignalsRealtime(handle: String): Flow<CallSignalEntity?> = flow {
        while (currentCoroutineContext().isActive) {
            try {
                val url = "${Backend.URL}/rest/v1/call_signals?receiver_handle=eq.$handle&status=eq.OFFERING&order=timestamp.desc&limit=1"
                val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    emit(CallSignalEntity(
                        id = obj.optString("id", ""),
                        callerHandle = obj.optString("caller_handle", ""),
                        callerName = obj.textOrDefault("caller_name"),
                        callerAvatar = obj.textOrDefault("caller_avatar"),
                        receiverHandle = obj.optString("receiver_handle", ""),
                        callType = obj.optString("call_type", "VIDEO"),
                        status = obj.optString("status", "OFFERING"),
                        sdp = obj.optString("sdp", ""),
                        timestamp = obj.optLong("timestamp", 0L)
                    ))
                } else emit(null)
            } catch (e: Exception) { emit(null) }
            delay(3000)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun updateCallSignalStatus(callId: String, status: String) = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/call_signals?id=eq.$callId"
            val payload = JSONObject().apply { put("status", status) }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
        } catch (e: Exception) { Log.e(TAG, "Supabase request failed", e) }
    }

    suspend fun sendCallSignal(signal: CallSignalEntity) = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/call_signals"
            val payload = JSONObject().apply {
                put("id", signal.id)
                put("caller_handle", signal.callerHandle)
                put("caller_name", signal.callerName)
                put("caller_avatar", signal.callerAvatar)
                put("receiver_handle", signal.receiverHandle)
                put("call_type", signal.callType)
                put("status", signal.status)
                put("sdp", signal.sdp)
                put("timestamp", signal.timestamp)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
        } catch (e: Exception) { Log.e(TAG, "Supabase request failed", e) }
    }

    suspend fun fetchCallSignalById(id: String): CallSignalEntity? = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/call_signals?id=eq.$id&select=*"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                CallSignalEntity(
                    id = obj.optString("id", ""), 
                    callerHandle = obj.optString("caller_handle", ""), 
                    receiverHandle = obj.optString("receiver_handle", ""), 
                    sdp = obj.optString("sdp", ""), 
                    status = obj.optString("status", "OFFERING"), 
                    timestamp = obj.optLong("timestamp", 0L)
                )
            } else null
        } catch (e: Exception) { null }
    }

    suspend fun updateCallSignalWithAnswer(callId: String, status: String, sdp: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/call_signals?id=eq.$callId"
            val payload = JSONObject().apply { put("sdp", sdp); put("status", status) }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Records a finished Agora call in the `call_history` table. */
    suspend fun insertCallHistory(
        channelName: String,
        callerHandle: String,
        receiverHandle: String,
        callType: String,
        status: String,
        durationSec: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/call_history"
            val payload = JSONObject().apply {
                put("caller_handle", callerHandle)
                put("receiver_handle", receiverHandle)
                put("call_type", callType)
                put("channel_name", channelName)
                put("status", status)
                put("started_at", java.time.Instant.ofEpochMilli(System.currentTimeMillis() - durationSec * 1000L).toString())
                put("ended_at", java.time.Instant.now().toString())
                put("duration_sec", durationSec)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Inserts an active (RINGING) `call_history` row when the caller starts dialing.
     * The send-push-notification Edge Function authorizes CALL pushes by verifying
     * this row server-side (caller_handle + receiver_handle + active status), so it
     * must exist before the ring push is requested.
     */
    suspend fun startCallHistory(
        callId: String,
        channelName: String,
        callerHandle: String,
        receiverHandle: String,
        callType: String
    ) = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/call_history"
            val payload = JSONObject().apply {
                put("caller_handle", callerHandle)
                put("receiver_handle", receiverHandle)
                put("call_type", callType)
                put("channel_name", channelName)
                put("status", "RINGING")
                put("started_at", java.time.Instant.now().toString())
                put("duration_sec", 0)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Patches the final state onto the RINGING row created by [startCallHistory].
     * Returns true when an existing row was updated; false means the caller should
     * fall back to inserting a fresh history row (e.g. the start insert failed).
     */
    suspend fun finalizeCallHistory(
        channelName: String,
        callerHandle: String,
        status: String,
        durationSec: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val encodedHandle = java.net.URLEncoder.encode(callerHandle, "UTF-8")
            val encodedChannel = java.net.URLEncoder.encode(channelName, "UTF-8")
            val url = "${Backend.URL}/rest/v1/call_history?channel_name=eq.$encodedChannel&caller_handle=eq.$encodedHandle"
            val headers = getBaseHeaders().newBuilder()
                .add("Prefer", "return=representation")
                .build()
            val payload = JSONObject().apply {
                put("status", status)
                put("ended_at", java.time.Instant.now().toString())
                put("duration_sec", durationSec)
            }
            val body = executeChecked(Request.Builder().url(url).headers(headers).patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            JSONArray(body).length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "finalizeCallHistory failed", e)
            false
        }
    }

    /** Resolves a FlareOfficial handle to its auth uid (needed for push targeting). */
    suspend fun fetchUidByHandle(handle: String): String? = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(handle, "UTF-8")
            val url = "${Backend.URL}/rest/v1/app_users?handle=ilike.$encoded&select=uid&limit=1"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            JSONArray(body).optJSONObject(0)?.optString("uid")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "fetchUidByHandle failed", e)
            null
        }
    }

    suspend fun fetchMyDirectMessages(userHandle: String, sinceTimestamp: Long): List<ChatMessageEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/chat_messages?or=(receiver_handle.eq.$userHandle,sender_handle.eq.$userHandle)&timestamp=gte.$sinceTimestamp&order=timestamp.asc"
            val bodyString = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val array = JSONArray(bodyString)
            val list = mutableListOf<ChatMessageEntity>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(ChatMessageEntity(
                    id = obj.optLong("id", System.currentTimeMillis()),
                    remoteId = obj.optString("id", ""),
                    roomId = obj.optString("room_id", ""),
                    senderName = obj.optString("sender_name", ""),
                    senderHandle = obj.optString("sender_handle", ""),
                    receiverHandle = obj.optString("receiver_handle", ""),
                    senderAvatar = obj.optString("sender_avatar", "default"),
                    senderAvatarPath = obj.optString("sender_avatar_path").takeIf { it != "null" && it.isNotBlank() },
                    messageText = obj.optString("message_text", ""),
                    mediaUrl = obj.optString("media_url").takeIf { it != "null" && it.isNotBlank() },
                    storagePath = obj.optString("storage_path").takeIf { it != "null" && it.isNotBlank() },
                    storageProvider = obj.optString("storage_provider", "cloudflare_r2"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    isFromMe = obj.optString("sender_handle", "") == userHandle,
                    time = "Just now",
                    isRead = obj.optBoolean("is_read", false),
                    reactions = obj.optString("reactions", ""),
                    audioDurationSec = obj.optInt("audio_duration_sec", 0)
                ))
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    fun observeMyDirectMessagesRealtime(userHandle: String): Flow<List<ChatMessageEntity>> = flow {
        var since = System.currentTimeMillis() - 10000
        while (currentCoroutineContext().isActive) {
            val msgs = fetchMyDirectMessages(userHandle, since)
            if (msgs.isNotEmpty()) { since = msgs.maxOf { it.timestamp } + 1; emit(msgs) }
            delay(2500)
        }
    }.flowOn(Dispatchers.IO)

    fun observeChatRealtime(roomId: String, handle: String): Flow<List<ChatMessageEntity>> = flow {
        var lastTs = System.currentTimeMillis() - 5000
        while (currentCoroutineContext().isActive) {
            try {
                // Fetch ONLY new messages in this room to avoid duplicate processing loops.
                val url = "${Backend.URL}/rest/v1/chat_messages?room_id=eq.$roomId&timestamp=gte.$lastTs&order=timestamp.asc"
                val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
                val array = JSONArray(body)
                val msgs = mutableListOf<ChatMessageEntity>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    msgs.add(ChatMessageEntity(
                        remoteId = obj.optString("id", ""),
                        roomId = roomId,
                        senderName = obj.textOrDefault("sender_name"),
                        senderHandle = obj.textOrDefault("sender_handle"),
                        senderAvatar = obj.textOrDefault("sender_avatar", "default"),
                        senderAvatarPath = obj.optString("sender_avatar_path").takeIf { it != "null" && it.isNotBlank() },
                        messageText = obj.textOrDefault("message_text"),
                        mediaUrl = obj.optString("media_url").takeIf { it != "null" && it.isNotBlank() },
                        storagePath = obj.optString("storage_path").takeIf { it != "null" && it.isNotBlank() },
                        storageProvider = obj.optString("storage_provider", "cloudflare_r2"),
                        mediaType = obj.textOrDefault("media_type", "text"),
                        audioDurationSec = obj.optInt("audio_duration_sec", 0),
                        timestamp = obj.optLong("timestamp", 0L),
                        isFromMe = obj.optString("sender_handle", "") == handle,
                        time = "Just now",
                        isRead = obj.optBoolean("is_read", false),
                        reactions = obj.optString("reactions", "")
                    ))
                }
                if (msgs.isNotEmpty()) {
                    lastTs = msgs.maxOf { it.timestamp } + 1
                    emit(msgs)
                }
            } catch (e: Exception) { Log.e(TAG, "observeChatRealtime poll failed", e) }
            delay(3000)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun markChatMessagesReadForUser(roomId: String, userId: String, senderHandle: String) = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/chat_messages?room_id=eq.$roomId&sender_handle=eq.$senderHandle"
            val payload = JSONObject().apply { put("is_read", true) }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
        } catch (e: Exception) { Log.e(TAG, "Supabase request failed", e) }
    }
    
    suspend fun sendChatMessage(message: ChatMessageEntity): Result<ChatMessageEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/chat_messages"
            val payload = JSONObject().apply {
                put("room_id", message.roomId)
                put("sender_name", message.senderName)
                put("sender_handle", message.senderHandle)
                put("receiver_handle", message.receiverHandle)
                put("sender_avatar", message.senderAvatar)
                put("sender_avatar_path", com.example.util.MediaStorageResolver.toStorableKey(message.senderAvatarPath))
                put("message_text", message.messageText)
                put("original_text", message.originalText)
                put("is_translated", message.isTranslated)
                put("translation_lang", message.translationLang)
                put("media_url", com.example.util.MediaStorageResolver.toStorableKey(message.mediaUrl))
                put("storage_path", com.example.util.MediaStorageResolver.toStorableKey(message.storagePath))
                put("storage_provider", message.storageProvider)
                put("media_type", message.mediaType)
                put("audio_duration_sec", message.audioDurationSec)
                put("is_read", message.isRead)
                put("timestamp", message.timestamp)
            }
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(message.copy(remoteId = parseSingleId(body)))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteChatRoomMessages(roomId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            executeChecked(Request.Builder().url("${Backend.URL}/rest/v1/chat_messages?room_id=eq.$roomId").headers(getBaseHeaders()).delete().build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // Notifications
    suspend fun fetchNotifications(recipientHandle: String): List<NotificationEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/notifications?recipient_handle=eq.$recipientHandle&order=timestamp.desc&limit=40"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val array = JSONArray(body)
            val list = mutableListOf<NotificationEntity>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(NotificationEntity(
                    id = obj.optLong("id", System.currentTimeMillis()),
                    username = obj.optString("username", "FlareOfficial User"),
                    recipientHandle = obj.optString("recipient_handle", ""),
                    avatarType = obj.optString("avatar_type", "default"),
                    actionText = obj.optString("action_text", ""),
                    timeAgo = obj.optString("time_ago", "Just now"),
                    isRead = obj.optBoolean("is_read", false),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                ))
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun sendNotification(notification: NotificationEntity): Result<NotificationEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/notifications"
            val payload = JSONObject().apply {
                put("username", notification.username)
                put("recipient_handle", notification.recipientHandle)
                put("avatar_type", notification.avatarType)
                put("action_text", notification.actionText)
                put("is_read", false)
                put("timestamp", notification.timestamp)
            }
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(notification.copy(isRead = false))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun markNotificationReadRemote(notifId: Long) = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/notifications?id=eq.$notifId"
            val payload = JSONObject().apply { put("is_read", true) }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
        } catch (e: Exception) { Log.e(TAG, "Supabase request failed", e) }
    }

    fun observeNotificationsRealtime(handle: String): Flow<List<NotificationEntity>> = flow {
        while (currentCoroutineContext().isActive) { emit(fetchNotifications(handle)); delay(5000) }
    }.flowOn(Dispatchers.IO)

    // Media
    suspend fun uploadMediaToR2(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        type: String,
        onProgress: ((Int) -> Unit)? = null
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/functions/v1/r2-upload"
            // The deployed r2-upload Edge Function expects a multipart form
            // with a "file" part and a "type" part.
            val fileBody = if (onProgress != null) {
                ProgressRequestBody(mimeType.toMediaType(), bytes) { pct -> onProgress(pct) }
            } else {
                bytes.toRequestBody(mimeType.toMediaType())
            }
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", type) // profile, cover, post_image, post_video, reel, story, chat
                .addFormDataPart("file", fileName, fileBody)
                .build()
            val request = Request.Builder()
                .url(url)
                .headers(getBaseHeaders())
                .post(requestBody)
                .build()
            val body = executeChecked(request)
            Result.success(JSONObject(body))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteMediaFromR2(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Accepts a raw stable path ("reels/...", "users/...", etc.), 
            // a gateway URL (.../r2-download?path=...) 
            // or a raw bucket URL (.../cloudflare.com/...).
            val trimmed = url.trim()
            if (trimmed.isBlank() || trimmed == "default" || trimmed == "null") return@withContext Result.success(Unit)

            val path = if (!trimmed.startsWith("http")) {
                // It's already a raw path
                trimmed
            } else {
                Uri.parse(trimmed).getQueryParameter("path") ?: run {
                    val candidate = trimmed.substringAfter(".r2.cloudflarestorage.com/", "")
                    candidate.takeIf { it.isNotBlank() && it != trimmed }
                }
            } ?: throw Exception("Cannot resolve R2 object path from: $url")

            android.util.Log.d(TAG, "Hard-deleting R2 path: $path")
            val payload = JSONObject().put("path", path)
            executeChecked(
                Request.Builder()
                    .url("${Backend.URL}/functions/v1/r2-delete")
                    .headers(getBaseHeaders())
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            )
            Result.success(Unit)
        } catch (e: Exception) { 
            Log.w(TAG, "R2 Hard-delete failed: ${e.message}")
            Result.failure(e) 
        }
    }

    suspend fun wipeAllUserMediaRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Calls the r2-cleanup Edge Function in 'purge' mode.
            // This is an administrative nuclear command that removes every object in R2
            // that is NOT referenced by any database row.
            val payload = JSONObject().put("mode", "purge")
            executeChecked(
                Request.Builder()
                    .url("${Backend.URL}/functions/v1/r2-cleanup")
                    .headers(getBaseHeaders())
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "wipeAllUserMediaRemote failed", e)
            Result.failure(e)
        }
    }

    suspend fun cleanupCorruptedPostsRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // RPC that cleans up posts with null/broken media references
            executeChecked(
                Request.Builder()
                    .url("${Backend.URL}/rest/v1/rpc/cleanup_corrupted_posts")
                    .headers(getBaseHeaders())
                    .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "cleanupCorruptedPostsRemote failed", e)
            Result.failure(e)
        }
    }

    // Realtime & Others (Optimized Polling)
    fun observeUsersRealtime(): Flow<List<AppUserEntity>> = flow { while(true) { emit(fetchAllUsers()); delay(12000) } }.flowOn(Dispatchers.IO)
    fun observePostsRealtime(): Flow<List<PostEntity>> = flow { while(true) { emit(fetchPosts()); delay(15000) } }.flowOn(Dispatchers.IO)
    fun observeReelsRealtime(): Flow<List<ReelEntity>> = flow { while(true) { emit(fetchReels()); delay(18000) } }.flowOn(Dispatchers.IO)
    
    suspend fun upsertFollow(followerUid: String, followingUid: String, isFollowing: Boolean) = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/follows"
            val payload = JSONObject().apply { put("follower_uid", followerUid); put("following_uid", followingUid); put("is_following", isFollowing) }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
        } catch (e: Exception) { Log.e(TAG, "Supabase request failed", e) }
    }

    // Follow graph state: (uids that follow me, uids I follow). Only active follows returned.
    suspend fun fetchFollowState(myUid: String): Pair<Set<String>, Set<String>> = withContext(Dispatchers.IO) {
        var followers: MutableSet<String> = mutableSetOf()
        var following: MutableSet<String> = mutableSetOf()
        try {
            val url = "${Backend.URL}/rest/v1/follows?following_uid=eq.$myUid&is_following=eq.true&select=follower_uid"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("follower_uid")?.takeIf { it.isNotBlank() }?.let { followers.add(it) }
            }
        } catch (e: Exception) { Log.e(TAG, "fetchFollowers failed", e) }
        try {
            val url = "${Backend.URL}/rest/v1/follows?follower_uid=eq.$myUid&is_following=eq.true&select=following_uid"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("following_uid")?.takeIf { it.isNotBlank() }?.let { following.add(it) }
            }
        } catch (e: Exception) { Log.e(TAG, "fetchFollowing failed", e) }
        followers to following
    }

    // Server-side notification to the followed user (uses vn_follow_notify SECURITY DEFINER RPC,
    // because notifications RLS blocks direct cross-recipient inserts from the client).
    // ---- Realtime presence (social app_users) ----

    /** Heartbeat: marks the logged-in social user as seen right now. */
    suspend fun touchSocialPresence() = withContext(Dispatchers.IO) {
        try {
            executeChecked(
                Request.Builder()
                    .url("${Backend.URL}/rest/v1/rpc/social_touch_presence")
                    .headers(getBaseHeaders())
                    .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            )
        } catch (e: Exception) { Log.e(TAG, "touchSocialPresence failed", e) }
    }

    /**
     * Batch presence lookup. Returns handle(lowercased) -> last_seen epoch millis.
     * Missing handles simply have no entry (treated as offline).
     */
    suspend fun fetchPresence(handles: List<String>): Map<String, Long> = withContext(Dispatchers.IO) {
        if (handles.isEmpty()) return@withContext emptyMap()
        try {
            val clean = handles.filter { it.isNotBlank() }.map { it.trim().lowercase() }.distinct()
            if (clean.isEmpty()) return@withContext emptyMap()
            val payload = org.json.JSONArray(clean).toString()
            val body = executeChecked(
                Request.Builder()
                    .url("${Backend.URL}/rest/v1/rpc/social_presence")
                    .headers(getBaseHeaders())
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            )
            val arr = JSONArray(body)
            val out = mutableMapOf<String, Long>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val h = o.optString("handle", "").lowercase()
                val ts = o.optString("last_seen", "")
                if (h.isNotBlank() && ts.isNotBlank()) {
                    out[h] = parseIsoMillis(ts) ?: continue
                }
            }
            out
        } catch (e: Exception) {
            Log.e(TAG, "fetchPresence failed", e)
            emptyMap()
        }
    }

    /**
     * Parses Supabase ISO-8601 timestamps ("2026-09-02T12:34:56.789+00:00").
     * Uses a robust approach that handles various timezone offsets and fractional seconds.
     */
    private fun parseIsoMillis(iso: String): Long? {
        if (iso.isBlank()) return null
        return try {
            // For API 24+ we can use SimpleDateFormat or try manual parse.
            // Best effort: try common ISO formats.
            val patterns = arrayOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss"
            )
            
            for (pattern in patterns) {
                try {
                    val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val date = sdf.parse(iso)
                    if (date != null) return date.time
                } catch (_: Exception) {}
            }
            
            // Fallback to Long conversion if it looks like a timestamp
            iso.toLongOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ISO timestamp: $iso", e)
            null
        }
    }

    suspend fun sendFollowNotification(targetHandle: String, actionText: String) = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/vn_follow_notify"
            val payload = JSONObject().apply {
                put("p_target_handle", targetHandle)
                put("p_action", actionText)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
        } catch (e: Exception) { Log.e(TAG, "sendFollowNotification failed", e) }
    }

    suspend fun createStory(story: StoryEntity): Result<StoryEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/stories"
            val payload = JSONObject().apply { 
                put("username", story.username)
                put("user_avatar_type", com.example.util.MediaStorageResolver.toStorableKey(story.userAvatarType))
                put("user_avatar_path", com.example.util.MediaStorageResolver.toStorableKey(story.userAvatarPath))
                put("image_res", com.example.util.MediaStorageResolver.toStorableKey(story.imageRes))
                put("storage_path", com.example.util.MediaStorageResolver.toStorableKey(story.storagePath))
                put("caption", story.caption)
                put("is_own", true) 
                put("storage_provider", story.storageProvider)
                put("mime_type", story.mimeType)
                put("file_size", story.fileSize)
            }
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(story.copy(id = JSONObject(body).optLong("id", System.currentTimeMillis())))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun fetchStories(): List<StoryEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/stories?select=*"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            val list = mutableListOf<StoryEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(StoryEntity(
                    id = obj.optLong("id", System.currentTimeMillis()), 
                    username = obj.textOrDefault("username"), 
                    userAvatarType = com.example.util.MediaStorageResolver.toStorableKey(obj.textOrDefault("user_avatar_type", "default")), 
                    userAvatarPath = com.example.util.MediaStorageResolver.toStorableKey(obj.optString("user_avatar_path")),
                    imageRes = com.example.util.MediaStorageResolver.toStorableKey(obj.textOrDefault("image_res")), 
                    storagePath = com.example.util.MediaStorageResolver.toStorableKey(obj.optString("storage_path")),
                    caption = obj.textOrDefault("caption"), 
                    isOwn = obj.optBoolean("is_own", false), 
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    storageProvider = obj.optString("storage_provider", "cloudflare_r2"),
                    mimeType = obj.optString("mime_type").takeIf { it != "null" && it.isNotBlank() },
                    fileSize = obj.optLong("file_size", 0)
                ))
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun deleteStory(id: String) {
        try {
            executeChecked(Request.Builder().url("${Backend.URL}/rest/v1/stories?id=eq.$id").headers(getBaseHeaders()).delete().build())
        } catch (e: Exception) { Log.e(TAG, "Supabase request failed", e) }
    }

    // =================================================================================
    // MODERATION SYSTEM — reports, warnings, activity log & server-side actions
    // =================================================================================

    private fun isoParam(ms: Long): String {
        if (ms <= 0) return ""
        val p = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        p.setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
        return p.format(java.util.Date(ms))
    }

    private fun parseModerationReport(obj: JSONObject): ModerationReport = ModerationReport(
        id = obj.optString("id", ""),
        contentType = obj.textOrDefault("content_type", "POST"),
        contentId = obj.textOrDefault("content_id"),
        contentPreview = obj.textOrDefault("content_preview"),
        targetHandle = obj.textOrDefault("target_handle"),
        targetUid = obj.textOrDefault("target_uid"),
        reporterHandle = obj.textOrDefault("reporter_handle"),
        reason = obj.textOrDefault("reason"),
        details = obj.textOrDefault("details"),
        status = obj.textOrDefault("status", "PENDING"),
        reportCount = obj.optInt("report_count", 1),
        createdAt = parseIsoMillis(obj.optString("created_at", "")) ?: System.currentTimeMillis(),
        reviewedBy = obj.textOrDefault("reviewed_by"),
        resolvedAt = parseIsoMillis(obj.optString("resolved_at", "")) ?: 0L
    )
suspend fun fetchModerationReports(
        statusFilter: String = "",
        fromMs: Long = 0,
        toMs: Long = 0,
        search: String = "",
        limit: Int = 50,
        offset: Int = 0
    ): List<ModerationReport> = withContext(Dispatchers.IO) {
        try {
            var url = "${Backend.URL}/rest/v1/moderation_reports?select=*&order=created_at.desc"
            if (statusFilter.isNotBlank()) url += "&status=eq.$statusFilter"
            if (fromMs > 0) url += "&created_at=gte.${isoParam(fromMs)}"
            if (toMs > 0) url += "&created_at=lte.${isoParam(toMs)}"
            if (search.isNotBlank()) url += "&or=(target_handle.ilike.*$search*,reporter_handle.ilike.*$search*,content_preview.ilike.*$search*)"
            url += "&limit=$limit&offset=$offset"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            val list = mutableListOf<ModerationReport>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(parseModerationReport(obj))
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun fetchAllModerationWarnings(limit: Int = 500, offset: Int = 0): List<ModerationWarning> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/moderation_warnings?select=*&order=created_at.desc&limit=$limit&offset=$offset"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            val list = mutableListOf<ModerationWarning>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(ModerationWarning(
                    id = obj.optString("id", ""),
                    userId = obj.textOrDefault("user_id"),
                    userHandle = obj.textOrDefault("user_handle"),
                    reason = obj.textOrDefault("reason"),
                    warnedBy = obj.textOrDefault("warned_by"),
                    contentType = obj.textOrDefault("content_type"),
                    contentId = obj.textOrDefault("content_id"),
                    reportId = obj.textOrDefault("report_id"),
                    status = obj.textOrDefault("status", "ACTIVE"),
                    createdAt = parseIsoMillis(obj.optString("created_at", "")) ?: System.currentTimeMillis(),
                    removedBy = obj.textOrDefault("removed_by"),
                    removedAt = parseIsoMillis(obj.optString("removed_at", "")) ?: 0L
                ))
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun fetchModerationActivity(fromMs: Long = 0, toMs: Long = 0, limit: Int = 150, offset: Int = 0): List<ModerationActivityItem> = withContext(Dispatchers.IO) {
        try {
            var url = "${Backend.URL}/rest/v1/moderation_activity?select=*&order=created_at.desc"
            if (fromMs > 0) url += "&created_at=gte.${isoParam(fromMs)}"
            if (toMs > 0) url += "&created_at=lte.${isoParam(toMs)}"
            url += "&limit=$limit&offset=$offset"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            val list = mutableListOf<ModerationActivityItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(ModerationActivityItem(
                    id = obj.optString("id", ""),
                    actorHandle = obj.textOrDefault("actor_handle"),
                    action = obj.textOrDefault("action"),
                    targetHandle = obj.textOrDefault("target_handle"),
                    targetType = obj.textOrDefault("target_type"),
                    contentId = obj.textOrDefault("content_id"),
                    reportId = obj.textOrDefault("report_id"),
                    reason = obj.textOrDefault("reason"),
                    meta = obj.textOrDefault("meta", "{}"),
                    createdAt = parseIsoMillis(obj.optString("created_at", "")) ?: System.currentTimeMillis()
                ))
            }
            list
        } catch (e: Exception) { emptyList() }
    }
suspend fun submitContentReport(report: ModerationReport): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/moderation_reports"
            val payload = JSONObject().apply {
                put("content_type", report.contentType)
                put("content_id", report.contentId)
                put("content_preview", report.contentPreview.substring(0, report.contentPreview.length.coerceAtMost(300)))
                put("target_handle", report.targetHandle)
                put("target_uid", report.targetUid)
                put("reporter_handle", report.reporterHandle)
                put("reason", report.reason)
                put("details", report.details)
                put("status", "PENDING")
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Runs a moderation RPC and converts errors into a friendly, safe message. */
    suspend fun runModerationRpc(name: String, payload: JSONObject): ModerationActionResult = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/$name"
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            ModerationActionResult(success = true, message = "Success")
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val friendly = if (msg.contains("Access Denied"))
                "You don't have permission to perform this action."
            else if (msg.contains("42501"))
                "You don't have permission to perform this action."
            else if (msg.contains("429"))
                "Too many requests — please try again shortly."
            else
                "The moderation action could not be completed. Please try again."
            ModerationActionResult(success = false, message = friendly)
        }
    }

    suspend fun issueWarning(userId: String, userHandle: String, reason: String, contentType: String = "", contentId: String = "", reportId: String = ""): ModerationActionResult =
        runModerationRpc("mod_issue_warning", JSONObject().apply {
            put("p_user_id", userId); put("p_user_handle", userHandle); put("p_reason", reason)
            put("p_content_type", contentType); put("p_content_id", contentId); put("p_report_id", reportId)
        })

    suspend fun removeWarning(warningId: String, reason: String = ""): ModerationActionResult =
        runModerationRpc("mod_remove_warning", JSONObject().apply { put("p_warning_id", warningId); put("p_reason", reason) })

    suspend fun moderationDeletePost(remoteId: String, reason: String = "", reportId: String = ""): ModerationActionResult =
        runModerationRpc("mod_delete_post", JSONObject().apply { put("p_remote_id", remoteId); put("p_reason", reason); put("p_report_id", reportId) })

    suspend fun moderationDeleteReel(remoteId: String, reason: String = "", reportId: String = ""): ModerationActionResult =
        runModerationRpc("mod_delete_reel", JSONObject().apply { put("p_remote_id", remoteId); put("p_reason", reason); put("p_report_id", reportId) })

    suspend fun moderationDeleteVideo(remoteId: String, reason: String = "", reportId: String = ""): ModerationActionResult =
        runModerationRpc("mod_delete_video", JSONObject().apply { put("p_remote_id", remoteId); put("p_reason", reason); put("p_report_id", reportId) })

    suspend fun reviewReport(reportId: String, status: String, reason: String = ""): ModerationActionResult =
        runModerationRpc("mod_review_report", JSONObject().apply { put("p_report_id", reportId); put("p_status", status); put("p_reason", reason) })

    suspend fun suspendUser(targetUid: String, reason: String): ModerationActionResult =
        runModerationRpc("mod_suspend_user", JSONObject().apply { put("p_target_uid", targetUid); put("p_reason", reason) })

    suspend fun banUserMod(targetUid: String, reason: String): ModerationActionResult =
        runModerationRpc("mod_ban_user", JSONObject().apply { put("p_target_uid", targetUid); put("p_reason", reason) })

    suspend fun unbanUserMod(targetUid: String): ModerationActionResult =
        runModerationRpc("mod_unban_user", JSONObject().apply { put("p_target_uid", targetUid) })

    suspend fun setModPermission(targetUid: String, perm: String, value: Boolean): ModerationActionResult =
        runModerationRpc("set_mod_permission", JSONObject().apply { put("p_target_uid", targetUid); put("p_perm", perm); put("p_value", value) })
}
