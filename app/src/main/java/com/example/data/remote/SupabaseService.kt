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
        val prefs = context.getSharedPreferences("vyn9_auth_prefs", Context.MODE_PRIVATE)
        var token = prefs.getString("access_token", null)?.takeIf { it.isNotBlank() }
        // Supabase access tokens expire after ~1 hour. Proactively refresh an
        // expired token (never on the main thread) so long-lived sessions keep
        // working for REST calls AND the b2-upload media gateway.
        if (token != null && isTokenExpired(token) && Looper.myLooper() != Looper.getMainLooper()) {
            if (performTokenRefresh() == SessionRefreshResult.REFRESHED) {
                token = prefs.getString("access_token", null)?.takeIf { it.isNotBlank() }
            }
        }
        val builder = Headers.Builder()
            .add("apikey", Backend.KEY)
        builder.add("Authorization", "Bearer ${token ?: Backend.KEY}")
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
        
        if (response.code == 401) {
            // CRITICAL: If we get a 401, it means the current access token is invalid or expired.
            // We should notify the session manager to clear it.
            Log.e(TAG, "Supabase 401 Unauthorized: $body")
            if (body.contains("UNAUTHORIZED_ASYMMETRIC_KEY", ignoreCase = true) || 
                body.contains("JWT", ignoreCase = true) || 
                body.contains("invalid", ignoreCase = true)) {
                
                // Clear the potentially corrupted/expired session locally
                context.getSharedPreferences("vyn9_auth_prefs", Context.MODE_PRIVATE).edit()
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

    private fun JSONObject.textOrDefault(key: String, default: String = ""): String {
        return optString(key, default).takeIf { it != "null" } ?: default
    }

    private fun parseSingleId(bodyString: String): String {
        return try {
            val array = JSONArray(bodyString)
            if (array.length() == 0) return ""
            array.getJSONObject(0).optString("id", "")
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
            val user = obj.getJSONObject("user")
            // When email confirmation is disabled Supabase returns a session right away —
            // persist it so the very first upload doesn't bounce with Unauthorized.
            obj.optJSONObject("session")?.let { persistSession(it, user) }
            Result.success(AppUserEntity(uid = user.getString("id"), name = name, handle = email.substringBefore("@"), email = email))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<AppUserEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/auth/v1/token?grant_type=password"
            val payload = JSONObject().apply { put("email", email); put("password", pass) }
            val res = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            val obj = JSONObject(res)
            val user = obj.getJSONObject("user")

            // Persist access + refresh tokens so the session can be renewed hourly.
            persistSession(obj, user)
            // Cache display identity so a cold app start can render the profile
            // before the first network sync.
            context.getSharedPreferences("vyn9_auth_prefs", Context.MODE_PRIVATE).edit()
                .putString("access_token_name", user.optJSONObject("user_metadata")?.optString("full_name") ?: "")
                .putString("access_token_email", email)
                .apply()

            Result.success(AppUserEntity(uid = user.getString("id"), name = user.optJSONObject("user_metadata")?.optString("full_name") ?: "", handle = email.substringBefore("@"), email = email))
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Persists access + refresh tokens (and uid) from a Supabase auth response object. */
    private fun persistSession(authResponse: JSONObject, user: JSONObject) {
        val session = authResponse.optString("access_token", "")
        if (session.isBlank()) return
        context.getSharedPreferences("vyn9_auth_prefs", Context.MODE_PRIVATE).edit()
            .putString("access_token", session)
            .putString("refresh_token", authResponse.optString("refresh_token", ""))
            .putString("session_uid", user.getString("id"))
            .putBoolean("session_valid", true)
            .apply()
    }

    private val refreshMutex = Any()

    /**
     * Exchanges the stored refresh token for a fresh access token. Supabase access
     * tokens expire after ~1 hour; without this every request (including the
     * b2-upload media gateway) fails with Unauthorized afterwards.
     */
    suspend fun refreshAuthSession(): SessionRefreshResult = withContext(Dispatchers.IO) {
        performTokenRefresh()
    }

    private fun performTokenRefresh(): SessionRefreshResult = synchronized(refreshMutex) {
        val prefs = context.getSharedPreferences("vyn9_auth_prefs", Context.MODE_PRIVATE)
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
            prefs.edit()
                .putString("access_token", obj.getString("access_token"))
                .putString("refresh_token", obj.getString("refresh_token"))
                .putString("session_uid", obj.getJSONObject("user").getString("id"))
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
        context.getSharedPreferences("vyn9_auth_prefs", Context.MODE_PRIVATE).edit()
            .remove("access_token")
            .remove("refresh_token")
            .putBoolean("session_valid", false)
            .apply()
    }

    // Users
    suspend fun fetchAllUsers(): List<AppUserEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/app_users?select=*"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                AppUserEntity(
                    uid = obj.getString("uid"),
                    name = obj.textOrDefault("name"),
                    handle = obj.textOrDefault("handle"),
                    email = obj.textOrDefault("email"),
                    role = obj.textOrDefault("role", "USER"),
                    avatarType = obj.textOrDefault("avatar_type", "default"),
                    coverType = obj.textOrDefault("cover_type", "default"),
                    avatarPath = obj.optString("avatar_path").takeIf { it != "null" && it.isNotBlank() },
                    coverPath = obj.optString("cover_path").takeIf { it != "null" && it.isNotBlank() },
                    bio = obj.textOrDefault("bio"),
                    location = obj.textOrDefault("location"),
                    isPublic = obj.optBoolean("is_public", true)
                )
            }
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
                put("role", user.role)
                put("avatar_type", user.avatarType)
                put("cover_type", user.coverType)
                put("avatar_path", user.avatarPath)
                put("cover_path", user.coverPath)
                put("bio", user.bio)
                put("location", user.location)
                put("is_public", user.isPublic)
            }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun patchUserProfile(uid: String, avatarType: String?, coverType: String?, avatarPath: String?, coverPath: String?, name: String?, handle: String?, bio: String?, location: String?, isPublic: Boolean? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/app_users?uid=eq.$uid"
            val payload = JSONObject().apply {
                avatarType?.let { put("avatar_type", it) }
                coverType?.let { put("cover_type", it) }
                avatarPath?.let { put("avatar_path", it) }
                coverPath?.let { put("cover_path", it) }
                name?.let { put("name", it) }
                handle?.let { put("handle", it) }
                bio?.let { put("bio", it) }
                location?.let { put("location", it) }
                isPublic?.let { put("is_public", it) }
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
                AppUserEntity(uid = obj.getString("uid"), name = obj.textOrDefault("name"), handle = obj.textOrDefault("handle"), email = obj.textOrDefault("email"))
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
        canCleanStorage: Boolean
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
                put("new_can_clean_storage", canCleanStorage)
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

    suspend fun deleteUser(uid: String) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/delete_user_rpc"
            val payload = JSONObject().apply { put("target_uid", uid) }
            executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
        } catch (e: Exception) { Log.e(TAG, "Supabase request failed", e) }
    }

    suspend fun deleteAllUsers() {
        // Implementation for mass deletion if needed by Super Admin
    }

    // Posts
    suspend fun fetchPosts(): List<PostEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/posts?select=*&order=timestamp.desc"
            val bodyString = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val array = JSONArray(bodyString)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                PostEntity(
                    id = obj.optLong("id", System.currentTimeMillis()),
                    remoteId = obj.optString("id", ""),
                    username = obj.textOrDefault("username"),
                    userHandle = obj.textOrDefault("user_handle"),
                    userAvatarType = obj.textOrDefault("user_avatar_type", "default"),
                    userAvatarPath = obj.optString("user_avatar_path").takeIf { it != "null" && it.isNotBlank() },
                    actionText = obj.textOrDefault("action_text"),
                    postImageRes = obj.textOrDefault("post_image_res"),
                    storagePath = obj.optString("storage_path").takeIf { it != "null" && it.isNotBlank() },
                    thumbnailPath = obj.optString("thumbnail_path").takeIf { it != "null" && it.isNotBlank() },
                    caption = obj.textOrDefault("caption"),
                    likesCount = obj.optInt("likes_count", 0),
                    isLiked = obj.optBoolean("is_liked", false),
                    isSaved = obj.optBoolean("is_saved", false),
                    isReposted = obj.optBoolean("is_reposted", false),
                    repostsCount = obj.optInt("reposts_count", 0),
                    commentsCount = obj.optInt("comments_count", 0),
                    isPublic = obj.optBoolean("is_public", true),
                    timeAgo = obj.textOrDefault("time_ago", "Just now"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createPost(post: PostEntity): Result<PostEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/posts"
            val payload = JSONObject().apply {
                put("username", post.username)
                put("user_handle", post.userHandle)
                put("user_avatar_type", post.userAvatarType)
                put("user_avatar_path", post.userAvatarPath)
                put("action_text", post.actionText)
                put("post_image_res", post.postImageRes)
                put("storage_path", post.storagePath)
                put("thumbnail_path", post.thumbnailPath)
                put("caption", post.caption)
                put("likes_count", 0)
                put("is_public", post.isPublic)
                put("timestamp", post.timestamp)
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
            if (shouldLike) {
                val url = "${Backend.URL}/rest/v1/post_likes"
                val payload = JSONObject().apply { put("post_id", postId); put("user_id", userId) }
                executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            } else {
                val url = "${Backend.URL}/rest/v1/post_likes?post_id=eq.$postId&user_id=eq.$userId"
                executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).delete().build())
            }
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
                val obj = arr.getJSONObject(i)
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
    suspend fun togglePostRepost(postId: Long, userId: String, shouldRepost: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (shouldRepost) {
                val url = "${Backend.URL}/rest/v1/post_reposts"
                val payload = JSONObject().apply { put("post_id", postId); put("user_id", userId) }
                executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            } else {
                val url = "${Backend.URL}/rest/v1/post_reposts?post_id=eq.$postId&user_id=eq.$userId"
                executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).delete().build())
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun togglePostSave(postId: Long, userId: String, shouldSave: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (shouldSave) {
                val url = "${Backend.URL}/rest/v1/saved_posts"
                val payload = JSONObject().apply { put("post_id", postId); put("user_id", userId) }
                executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            } else {
                val url = "${Backend.URL}/rest/v1/saved_posts?post_id=eq.$postId&user_id=eq.$userId"
                executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).delete().build())
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun fetchUserRepostedPosts(userId: String): Set<Long> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/post_reposts?user_id=eq.$userId&select=post_id"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            val set = mutableSetOf<Long>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
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
                val obj = arr.getJSONObject(i)
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
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                ReelEntity(
                    id = obj.optLong("id", System.currentTimeMillis()),
                    remoteId = obj.optString("id", ""),
                    author = obj.textOrDefault("author"),
                    handle = obj.textOrDefault("handle"),
                    avatarType = obj.textOrDefault("avatar_type"),
                    userAvatarPath = obj.optString("user_avatar_path").takeIf { it != "null" && it.isNotBlank() },
                    caption = obj.textOrDefault("caption"),
                    videoUrl = obj.textOrDefault("video_url"),
                    imageRes = obj.textOrDefault("image_res"),
                    storagePath = obj.optString("storage_path").takeIf { it != "null" && it.isNotBlank() },
                    thumbnailPath = obj.optString("thumbnail_path").takeIf { it != "null" && it.isNotBlank() },
                    likesCount = obj.optInt("likes_count", 0),
                    commentsCount = obj.optInt("comments_count", 0),
                    isPublic = obj.optBoolean("is_public", true),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createReel(reel: ReelEntity): Result<ReelEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/reels"
            val payload = JSONObject().apply {
                put("author", reel.author)
                put("handle", reel.handle)
                put("avatar_type", reel.avatarType)
                put("user_avatar_path", reel.userAvatarPath)
                put("caption", reel.caption)
                put("video_url", reel.videoUrl)
                put("image_res", reel.imageRes)
                put("storage_path", reel.storagePath)
                put("thumbnail_path", reel.thumbnailPath)
                put("is_public", reel.isPublic)
                put("timestamp", reel.timestamp)
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

    suspend fun toggleReelLike(reelId: Long, userId: String, shouldLike: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (shouldLike) {
                val url = "${Backend.URL}/rest/v1/reel_likes"
                val payload = JSONObject().apply { put("reel_id", reelId); put("user_id", userId) }
                executeChecked(Request.Builder().url(url).headers(getBaseHeaders(true)).post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
            } else {
                val url = "${Backend.URL}/rest/v1/reel_likes?reel_id=eq.$reelId&user_id=eq.$userId"
                executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).delete().build())
            }
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
                val obj = arr.getJSONObject(i)
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
            List(array.length()) { index ->
                val row = array.getJSONObject(index)
                CommentEntity(
                    id = row.optLong("id", System.currentTimeMillis()),
                    remoteId = row.optString("id", ""),
                    postId = postRemoteKey.toLongOrNull() ?: 0L,
                    username = row.textOrDefault("username"),
                    userAvatarType = row.textOrDefault("user_avatar_type", "default"),
                    userAvatarPath = row.optString("user_avatar_path").takeIf { it != "null" && it.isNotBlank() },
                    text = row.textOrDefault("text"),
                    timeAgo = row.textOrDefault("time_ago", "Just now"),
                    timestamp = row.optLong("timestamp", System.currentTimeMillis())
                )
            }
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
                        id = obj.getString("id"),
                        callerHandle = obj.getString("caller_handle"),
                        callerName = obj.textOrDefault("caller_name"),
                        callerAvatar = obj.textOrDefault("caller_avatar"),
                        receiverHandle = obj.getString("receiver_handle"),
                        callType = obj.getString("call_type"),
                        status = obj.getString("status"),
                        sdp = obj.optString("sdp"),
                        timestamp = obj.getLong("timestamp")
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
                CallSignalEntity(id = obj.getString("id"), callerHandle = obj.getString("caller_handle"), receiverHandle = obj.getString("receiver_handle"), sdp = obj.optString("sdp"), status = obj.getString("status"), timestamp = obj.getLong("timestamp"))
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

    suspend fun fetchMyDirectMessages(userHandle: String, sinceTimestamp: Long): List<ChatMessageEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/chat_messages?or=(receiver_handle.eq.$userHandle,sender_handle.eq.$userHandle)&timestamp=gte.$sinceTimestamp&order=timestamp.asc"
            val bodyString = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val array = JSONArray(bodyString)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                ChatMessageEntity(
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
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    isFromMe = obj.optString("sender_handle") == userHandle,
                    time = "Just now",
                    isRead = obj.optBoolean("is_read", false)
                )
            }
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
        while (currentCoroutineContext().isActive) {
            try {
                val url = "${Backend.URL}/rest/v1/chat_messages?room_id=eq.$roomId&order=timestamp.asc"
                val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
                val array = JSONArray(body)
                emit(List(array.length()) { i ->
                    val obj = array.getJSONObject(i)
                    ChatMessageEntity(
                        remoteId = obj.getString("id"),
                        roomId = roomId,
                        senderName = obj.textOrDefault("sender_name"),
                        senderHandle = obj.textOrDefault("sender_handle"),
                        senderAvatar = obj.textOrDefault("sender_avatar", "default"),
                        senderAvatarPath = obj.optString("sender_avatar_path").takeIf { it != "null" && it.isNotBlank() },
                        messageText = obj.textOrDefault("message_text"),
                        mediaUrl = obj.optString("media_url").takeIf { it != "null" && it.isNotBlank() },
                        storagePath = obj.optString("storage_path").takeIf { it != "null" && it.isNotBlank() },
                        mediaType = obj.textOrDefault("media_type", "text"),
                        audioDurationSec = obj.optInt("audio_duration_sec", 0),
                        timestamp = obj.getLong("timestamp"),
                        isFromMe = obj.getString("sender_handle") == handle,
                        time = "Just now",
                        isRead = obj.optBoolean("is_read", false)
                    )
                })
            } catch (e: Exception) { Log.e(TAG, "Supabase request failed", e) }
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
                put("sender_avatar_path", message.senderAvatarPath)
                put("message_text", message.messageText)
                put("original_text", message.originalText)
                put("is_translated", message.isTranslated)
                put("translation_lang", message.translationLang)
                put("media_url", message.mediaUrl)
                put("storage_path", message.storagePath)
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
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                NotificationEntity(
                    id = obj.optLong("id", System.currentTimeMillis()),
                    username = obj.optString("username", "Vyn9 User"),
                    avatarType = obj.optString("avatar_type", "default"),
                    actionText = obj.optString("action_text", ""),
                    timeAgo = obj.optString("time_ago", "Just now"),
                    isRead = obj.optBoolean("is_read", false),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun sendNotification(notification: NotificationEntity): Result<NotificationEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/notifications"
            val payload = JSONObject().apply {
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
    suspend fun uploadMediaToB2(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        type: String,
        onProgress: ((Int) -> Unit)? = null
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/functions/v1/b2-upload"
            // The deployed b2-upload Edge Function expects a multipart form
            // with a "file" part and a "type" part.
            val fileBody = if (onProgress != null) {
                ProgressRequestBody(mimeType.toMediaType(), bytes) { pct -> onProgress(pct) }
            } else {
                bytes.toRequestBody(mimeType.toMediaType())
            }
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", type) // profile, cover, post, reel, story
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

    suspend fun deleteMediaFromB2(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Accepts a raw stable path ("reels/...", "users/...", etc.), 
            // a gateway URL (.../b2-download?path=...) 
            // or a raw bucket URL (.../backblazeb2.com/...).
            val trimmed = url.trim()
            if (trimmed.isBlank() || trimmed == "default" || trimmed == "null") return@withContext Result.success(Unit)

            val path = if (!trimmed.startsWith("http")) {
                // It's already a raw path
                trimmed
            } else {
                Uri.parse(trimmed).getQueryParameter("path") ?: run {
                    val candidate = trimmed.substringAfter(".backblazeb2.com/", "")
                    candidate.takeIf { it.isNotBlank() && it != trimmed }
                }
            } ?: throw Exception("Cannot resolve B2 object path from: $url")

            android.util.Log.d(TAG, "Hard-deleting B2 path: $path")
            val payload = JSONObject().put("path", path)
            executeChecked(
                Request.Builder()
                    .url("${Backend.URL}/functions/v1/b2-delete")
                    .headers(getBaseHeaders())
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            )
            Result.success(Unit)
        } catch (e: Exception) { 
            Log.w(TAG, "B2 Hard-delete failed: ${e.message}")
            Result.failure(e) 
        }
    }

    suspend fun wipeAllUserMediaRemote() {}
    suspend fun cleanupCorruptedPostsRemote() {}

    // Realtime & Others
    fun observeUsersRealtime(): Flow<List<AppUserEntity>> = flow { while(true) { emit(fetchAllUsers()); delay(10000) } }.flowOn(Dispatchers.IO)
    fun observePostsRealtime(): Flow<List<PostEntity>> = flow { while(true) { emit(fetchPosts()); delay(10000) } }.flowOn(Dispatchers.IO)
    fun observeReelsRealtime(): Flow<List<ReelEntity>> = flow { while(true) { emit(fetchReels()); delay(10000) } }.flowOn(Dispatchers.IO)
    
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
                arr.getJSONObject(i).optString("follower_uid").takeIf { it.isNotBlank() }?.let { followers.add(it) }
            }
        } catch (e: Exception) { Log.e(TAG, "fetchFollowers failed", e) }
        try {
            val url = "${Backend.URL}/rest/v1/follows?follower_uid=eq.$myUid&is_following=eq.true&select=following_uid"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                arr.getJSONObject(i).optString("following_uid").takeIf { it.isNotBlank() }?.let { following.add(it) }
            }
        } catch (e: Exception) { Log.e(TAG, "fetchFollowing failed", e) }
        followers to following
    }

    // Server-side notification to the followed user (uses vn_follow_notify SECURITY DEFINER RPC,
    // because notifications RLS blocks direct cross-recipient inserts from the client).
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
                put("user_avatar_type", story.userAvatarType)
                put("user_avatar_path", story.userAvatarPath)
                put("image_res", story.imageRes)
                put("storage_path", story.storagePath)
                put("caption", story.caption)
                put("is_own", true) 
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
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                StoryEntity(
                    id = obj.optLong("id", System.currentTimeMillis()), 
                    username = obj.textOrDefault("username"), 
                    userAvatarType = obj.textOrDefault("user_avatar_type", "default"), 
                    userAvatarPath = obj.optString("user_avatar_path").takeIf { it != "null" && it.isNotBlank() },
                    imageRes = obj.textOrDefault("image_res"), 
                    storagePath = obj.optString("storage_path").takeIf { it != "null" && it.isNotBlank() },
                    caption = obj.textOrDefault("caption"), 
                    isOwn = obj.optBoolean("is_own", false), 
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun deleteStory(id: String) {
        try {
            executeChecked(Request.Builder().url("${Backend.URL}/rest/v1/stories?id=eq.$id").headers(getBaseHeaders()).delete().build())
        } catch (e: Exception) { Log.e(TAG, "Supabase request failed", e) }
    }
}
