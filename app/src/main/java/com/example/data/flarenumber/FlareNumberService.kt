package com.example.data.flarenumber

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.remote.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * FLARE NUMBER networking layer.
 *
 * IMPORTANT SESSION ISOLATION: FLARE NUMBER uses its OWN Supabase auth session
 * (created via number + password — the number maps to a hidden deterministic
 * email, so no SMS/OTP or SMS provider is ever needed). That session is persisted
 * in a SEPARATE SharedPreferences file
 * ("flare_number_prefs") so it NEVER overwrites the main FlareOfficial account session stored in
 * "flareofficial_auth_prefs". Logging into FLARE NUMBER can therefore never sign the user out of
 * their FlareOfficial account, and vice versa.
 *
 * The same phone number always maps to the same hidden auth email, so the
 * same number resolves to the same persistent FLARE NUMBER identity on any device
 * or FlareOfficial account — after successful number + password authentication only.
 */
class FlareNumberService(private val context: Context) {

    companion object {
        private const val TAG = "FlareNumberService"
        private const val PREFS = "flare_number_prefs"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    // ---------------------------------------------------------------------------
    // Phone normalization — must match the server-side vn_normalize_phone exactly.
    // ---------------------------------------------------------------------------
    /** Normalizes to E.164 (BD default like the server): 01712... -> +8801712... */
    fun normalizePhone(raw: String): String? {
        val d = raw.filter { it.isDigit() }
        if (d.length < 7) return null
        var digits = d
        if (digits.startsWith("00")) digits = digits.substring(2)
        if (digits.startsWith("0")) digits = "880" + digits.substring(1)
        return "+$digits"
    }

    /** Loose validation for the UI (length sanity on the normalized form). */
    fun isValidPhone(raw: String): Boolean {
        val n = normalizePhone(raw) ?: return false
        return n.length in 9..17
    }

    // ---------------------------------------------------------------------------
    // Auth session (number + password, backed by Supabase email+password auth).
    // We deliberately do NOT use Supabase phone auth — hosted projects require a
    // paid SMS provider for it. Instead the number maps to a deterministic hidden
    // email ("flare.<digits>@flare-number.app") and we use the free email provider.
    // "Confirm email" must be OFF so signup returns the session with no mail sent.
    // ---------------------------------------------------------------------------

    /** Hidden deterministic email for a phone number (digits only, no '+'). */
    private fun pseudoEmail(normalizedPhone: String): String {
        val digits = normalizedPhone.filter { it.isDigit() }
        return "flare.$digits@flare-number.app"
    }

    /** Creates a new FLARE NUMBER account with number + password (no SMS/email). */
    suspend fun signUpWithPhone(rawPhone: String, password: String): Result<String> =
        phoneAuth(isSignUp = true, rawPhone = rawPhone, password = password)

    /** Logs in to an existing FLARE NUMBER with number + password. */
    suspend fun signInWithPhone(rawPhone: String, password: String): Result<String> =
        phoneAuth(isSignUp = false, rawPhone = rawPhone, password = password)

    private suspend fun phoneAuth(
        isSignUp: Boolean,
        rawPhone: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val phone = normalizePhone(rawPhone)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid phone number"))
        if (password.length < 6) {
            return@withContext Result.failure(
                IllegalArgumentException("Password must be at least 6 characters")
            )
        }
        try {
            val url = if (isSignUp) "${Backend.URL}/auth/v1/signup"
                      else "${Backend.URL}/auth/v1/token?grant_type=password"
            val payload = JSONObject()
                .put("email", pseudoEmail(phone))
                .put("password", password)
            val request = Request.Builder()
                .url(url)
                .headers(Headers.Builder().add("apikey", Backend.KEY).build())
                .post(payload.toString().toRequestBody(JSON))
                .build()
            val obj = JSONObject(execute(request))
            val accessToken = obj.optString("access_token", "")
            if (accessToken.isBlank()) {
                val msg = obj.optString("msg").ifBlank {
                    obj.optString("error_description", "Authentication failed")
                }.ifBlank { obj.optString("message", "Authentication failed") }
                return@withContext Result.failure(Exception(msg))
            }
            persistSession(obj, phone)
            Result.success(phone)
        } catch (e: Exception) {
            Log.e(TAG, "phoneAuth(signUp=$isSignUp) failed", e)
            Result.failure(e)
        }
    }

    private fun persistSession(authResponse: JSONObject, phone: String) {
        prefs.edit()
            .putString("access_token", authResponse.optString("access_token", ""))
            .putString("refresh_token", authResponse.optString("refresh_token", ""))
            .putString("session_uid", authResponse.optJSONObject("user")?.optString("id") ?: "")
            .putString("phone", phone)
            .putBoolean("session_valid", true)
            .apply()
    }

    /** True when a stored phone session exists and its JWT is not expired. */
    fun hasActiveSession(): Boolean {
        val token = prefs.getString("access_token", null) ?: return false
        return token.isNotBlank() && !isTokenExpired(token)
    }

    fun savedPhone(): String = prefs.getString("phone", "") ?: ""

    /** Wipes only the FLARE NUMBER session (never touches the FlareOfficial account session). */
    fun clearSession() {
        prefs.edit().clear().apply()
    }

    /** Saves a nickname for the user's own FLARE number (local preference only). */
    fun saveSelfNickname(nickname: String) {
        prefs.edit().putString("self_nickname", nickname).apply()
    }

        /** Loads the nickname set for the user's own FLARE number (empty if none set). */
    fun loadSelfNickname(): String = prefs.getString("self_nickname", "") ?: ""

    // ---------------------------------------------------------------------------
    // Auto-reply configuration (local preference — persisted on the device only,
    // never uploaded to the server)
    // ---------------------------------------------------------------------------

    /** Local auto-reply configuration for the FLARE NUMBER SMS feature. */
    data class AutoReplyConfig(
        val isEnabled: Boolean = false,
        val primaryMessage: String = "",
        val replyCount: Int = 3,
        val replyDelayMs: Long = 2000L,
        val repeatOnReply: Boolean = true
    )

    /** Persists the auto-reply configuration (device-local only). */
    fun saveAutoReplyConfig(config: AutoReplyConfig) {
        prefs.edit()
            .putBoolean("ar_enabled", config.isEnabled)
            .putString("ar_primary_msg", config.primaryMessage)
            .putInt("ar_reply_count", config.replyCount)
            .putLong("ar_delay_ms", config.replyDelayMs)
            .putBoolean("ar_repeat", config.repeatOnReply)
            .apply()
    }

    /** Loads the previously-persisted auto-reply configuration (defaults if none). */
    fun loadAutoReplyConfig(): AutoReplyConfig = AutoReplyConfig(
        isEnabled = prefs.getBoolean("ar_enabled", false),
        primaryMessage = prefs.getString("ar_primary_msg", "") ?: "",
        replyCount = prefs.getInt("ar_reply_count", 3),
        replyDelayMs = prefs.getLong("ar_delay_ms", 2000L),
        repeatOnReply = prefs.getBoolean("ar_repeat", true)
    )

    /**
     * Returns a usable phone-session access token: refreshes it when expired.
     * Null means the user must re-authenticate with their number + password.
     */
    suspend fun ensureSessionToken(): String? = withContext(Dispatchers.IO) {
        val token = prefs.getString("access_token", null)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        if (!isTokenExpired(token)) return@withContext token
        val refreshToken = prefs.getString("refresh_token", null)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        try {
            val payload = JSONObject().put("refresh_token", refreshToken)
            val request = Request.Builder()
                .url("${Backend.URL}/auth/v1/token?grant_type=refresh_token")
                .headers(Headers.Builder().add("apikey", Backend.KEY).build())
                .post(payload.toString().toRequestBody(JSON))
                .build()
            val obj = JSONObject(execute(request))
            val newToken = obj.optString("access_token", "")
            if (newToken.isBlank()) return@withContext null
            prefs.edit()
                .putString("access_token", newToken)
                .putString("refresh_token", obj.optString("refresh_token", refreshToken))
                .apply()
            newToken
        } catch (e: Exception) {
            Log.e(TAG, "FLARE NUMBER session refresh failed", e)
            null
        }
    }

    private fun isTokenExpired(token: String): Boolean = try {
        val parts = token.split(".")
        if (parts.size < 2) true else {
            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            val exp = JSONObject(payload).optLong("exp", 0L)
            exp > 0 && exp - 30 <= System.currentTimeMillis() / 1000
        }
    } catch (e: Exception) { true }

    // ---------------------------------------------------------------------------
    // PostgREST RPCs (phone-session token required)
    // ---------------------------------------------------------------------------

    private suspend fun rpc(name: String, payload: JSONObject): String = withContext(Dispatchers.IO) {
        val token = ensureSessionToken()
            ?: throw Exception("FLARE NUMBER session expired — please verify your number again")
        val request = Request.Builder()
            .url("${Backend.URL}/rest/v1/rpc/$name")
            .headers(vwHeaders(token))
            .post(payload.toString().toRequestBody(JSON))
            .build()
        execute(request)
    }

    private fun vwHeaders(token: String): Headers = Headers.Builder()
        .add("apikey", Backend.KEY)
        .add("Authorization", "Bearer $token")
        .build()

    /** Activates (first time) or loads (every later time) the identity of this phone. */
    suspend fun activateIdentity(): Result<JSONObject> = try {
        Result.success(JSONObject(rpc("flare_activate_identity", JSONObject())))
    } catch (e: Exception) {
        Log.e(TAG, "activateIdentity failed", e)
        Result.failure(e)
    }

    /** Checks whether a normalized number is active on FLARE NUMBER (no private data exposed). */
    suspend fun searchNumber(rawPhone: String): Result<JSONObject> = try {
        val phone = normalizePhone(rawPhone) ?: return Result.failure(Exception("Invalid phone number"))
        Result.success(JSONObject(rpc("flare_search_number", JSONObject().put("p_phone", phone))))
    } catch (e: Exception) {
        Log.e(TAG, "searchNumber failed", e)
        Result.failure(e)
    }

    /** Opens (or returns the existing) 1:1 conversation with a peer phone. */
    suspend fun openConversation(rawPhone: String): Result<JSONObject> = try {
        val phone = normalizePhone(rawPhone) ?: return Result.failure(Exception("Invalid phone number"))
        Result.success(JSONObject(rpc("flare_open_conversation", JSONObject().put("p_peer_phone", phone))))
    } catch (e: Exception) {
        Log.e(TAG, "openConversation failed", e)
        Result.failure(e)
    }

    suspend fun inbox(): Result<JSONArray> = try {
        Result.success(JSONArray(rpc("flare_inbox", JSONObject())))
    } catch (e: Exception) {
        Log.e(TAG, "inbox failed", e)
        Result.failure(e)
    }

    suspend fun messages(conversationId: String): Result<JSONArray> = try {
        Result.success(JSONArray(rpc("flare_messages", JSONObject().put("p_conversation_id", conversationId))))
    } catch (e: Exception) {
        Log.e(TAG, "messages failed", e)
        Result.failure(e)
    }

    // ---- Realtime presence ----

    /** Heartbeat: marks THIS Flare Number identity as seen right now. */
    suspend fun touchPresence(): Result<Unit> = try {
        rpc("flare_touch_presence", JSONObject())
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Peer presence for a conversation. Result value = peer last_seen epoch millis (null when unknown). */
    suspend fun conversationPresence(conversationId: String): Result<Long?> = try {
        val o = JSONObject(rpc("flare_conversation_presence", JSONObject().put("p_conversation_id", conversationId)))
        val iso = o.optString("peer_last_seen", "").takeIf { it.isNotBlank() && it != "null" }
        Result.success(iso?.let { parseIsoMillis(it) })
    } catch (e: Exception) {
        Log.e(TAG, "conversationPresence failed", e)
        Result.failure(e)
    }

    /** Parses Supabase ISO-8601 timestamps to epoch millis. */
    private fun parseIsoMillis(iso: String): Long? = try {
        val clean = iso.replace("Z", "+00:00")
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        val datePart = clean.substringBefore("T")
        val timePart = clean.substringAfter("T").removeSuffix("+00:00")
        val (y, mo, d) = datePart.split("-").map { it.toInt() }
        val (h, mi, secPart) = timePart.split(":").map { it.toDouble() }.let { Triple(it[0].toInt(), it[1].toInt(), it[2]) }
        cal.set(y, mo - 1, d, h, mi, secPart.toInt())
        cal.set(java.util.Calendar.MILLISECOND, ((secPart % 1) * 1000).toInt())
        cal.timeInMillis
    } catch (e: Exception) { null }

    suspend fun sendMessage(
        conversationId: String,
        text: String,
        mediaUrl: String = "",
        mediaType: String = ""
    ): Result<JSONObject> = try {
        val payload = JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_message_text", text)
            .put("p_media_url", mediaUrl)
            .put("p_media_type", mediaType)
        Result.success(JSONObject(rpc("flare_send_message", payload)))
    } catch (e: Exception) {
        Log.e(TAG, "sendMessage failed", e)
        Result.failure(e)
    }

    /** Deletes (unsends) one of the caller's own messages in a conversation. */
    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> = try {
        val payload = JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_message_id", messageId)
        rpc("flare_delete_message", payload)
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "deleteMessage failed", e)
        Result.failure(e)
    }

    suspend fun markRead(conversationId: String): Result<Unit> = try {
        rpc("flare_mark_read", JSONObject().put("p_conversation_id", conversationId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "markRead failed", e)
        Result.failure(e)
    }

    // ---------------------------------------------------------------------------
    // Chat settings (pin, mute, archive, favorite, block)
    // ---------------------------------------------------------------------------

    suspend fun togglePin(conversationId: String): Result<Unit> = try {
        rpc("flare_toggle_pin", JSONObject().put("p_conversation_id", conversationId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "togglePin failed", e)
        Result.failure(e)
    }

    suspend fun toggleMute(conversationId: String): Result<Unit> = try {
        rpc("flare_toggle_mute", JSONObject().put("p_conversation_id", conversationId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "toggleMute failed", e)
        Result.failure(e)
    }

    suspend fun toggleArchive(conversationId: String): Result<Unit> = try {
        rpc("flare_toggle_archive", JSONObject().put("p_conversation_id", conversationId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "toggleArchive failed", e)
        Result.failure(e)
    }

    suspend fun toggleFavorite(conversationId: String): Result<Unit> = try {
        rpc("flare_toggle_favorite", JSONObject().put("p_conversation_id", conversationId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "toggleFavorite failed", e)
        Result.failure(e)
    }

    suspend fun markUnread(conversationId: String): Result<Unit> = try {
        rpc("flare_mark_unread", JSONObject().put("p_conversation_id", conversationId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "markUnread failed", e)
        Result.failure(e)
    }

    suspend fun clearHistory(conversationId: String): Result<Unit> = try {
        rpc("flare_clear_history", JSONObject().put("p_conversation_id", conversationId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "clearHistory failed", e)
        Result.failure(e)
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> = try {
        rpc("flare_delete_conversation", JSONObject().put("p_conversation_id", conversationId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "deleteConversation failed", e)
        Result.failure(e)
    }

    suspend fun blockUser(peerPhone: String): Result<Unit> = try {
        rpc("flare_block_user", JSONObject().put("p_peer_phone", peerPhone))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "blockUser failed", e)
        Result.failure(e)
    }

    suspend fun reportUser(peerPhone: String, reason: String): Result<Unit> = try {
        rpc("flare_report_user", JSONObject().put("p_peer_phone", peerPhone).put("p_reason", reason))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "reportUser failed", e)
        Result.failure(e)
    }

    suspend fun updateProfile(displayName: String): Result<JSONObject> = try {
        Result.success(JSONObject(rpc("flare_update_profile", JSONObject().put("p_display_name", displayName))))
    } catch (e: Exception) {
        Log.e(TAG, "updateProfile failed", e)
        Result.failure(e)
    }

    /** Blocking OkHttp call — every caller already runs on Dispatchers.IO. */
    private fun execute(request: Request): String {
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${extractErrorMessage(body)}")
        }
        return body
    }

    /**
     * Pulls a human-readable message out of a Supabase/PostgREST error body.
     * Shapes handled: {"msg":...}, {"error_description":...}, {"message":...},
     * {"error":...}, {"hint":...} — falls back to a short raw snippet.
     */
    private fun extractErrorMessage(body: String): String = try {
        val obj = JSONObject(body)
        listOf("msg", "error_description", "message", "error", "hint")
            .firstNotNullOfOrNull { key -> obj.optString(key, "").takeIf { it.isNotBlank() } }
            ?: body.take(200)
    } catch (e: Exception) {
        body.take(200)
    }
}
