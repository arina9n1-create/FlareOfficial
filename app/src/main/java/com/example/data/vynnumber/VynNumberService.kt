package com.example.data.VynNumber

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
 * VYN NUMBER networking layer.
 *
 * IMPORTANT SESSION ISOLATION: VYN NUMBER uses its OWN Supabase phone-auth session
 * (created via OTP). That session is persisted in a SEPARATE SharedPreferences file
 * ("vyn_number_prefs") so it NEVER overwrites the main Vyn9 account session stored in
 * "vyn9_auth_prefs". Logging into VYN NUMBER can therefore never sign the user out of
 * their Vyn9 account, and vice versa.
 *
 * The same phone number always produces the same Supabase phone-auth user, so the
 * same number resolves to the same persistent VYN NUMBER identity on any device or
 * Vyn9 account — after successful OTP verification only.
 */
class VynNumberService(private val context: Context) {

    companion object {
        private const val TAG = "VynNumberService"
        private const val PREFS = "vyn_number_prefs"
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
    // OTP / phone-auth session
    // ---------------------------------------------------------------------------

    /** Sends an SMS OTP. Creates the Supabase phone-auth user if it doesn't exist yet. */
    suspend fun sendOtp(rawPhone: String): Result<String> = withContext(Dispatchers.IO) {
        val phone = normalizePhone(rawPhone)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid phone number"))
        try {
            val payload = JSONObject().put("phone", phone).put("create_user", true)
            val request = Request.Builder()
                .url("${Backend.URL}/auth/v1/otp")
                .headers(Headers.Builder().add("apikey", Backend.KEY).build())
                .post(payload.toString().toRequestBody(JSON))
                .build()
            execute(request)
            Result.success(phone)
        } catch (e: Exception) {
            Log.e(TAG, "sendOtp failed", e)
            Result.failure(e)
        }
    }

    /** Verifies the OTP and persists the VYN NUMBER phone session. */
    suspend fun verifyOtp(rawPhone: String, code: String): Result<String> = withContext(Dispatchers.IO) {
        val phone = normalizePhone(rawPhone)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid phone number"))
        try {
            val payload = JSONObject()
                .put("type", "sms")
                .put("phone", phone)
                .put("token", code.trim())
            val request = Request.Builder()
                .url("${Backend.URL}/auth/v1/verify")
                .headers(Headers.Builder().add("apikey", Backend.KEY).build())
                .post(payload.toString().toRequestBody(JSON))
                .build()
            val obj = JSONObject(execute(request))
            val accessToken = obj.optString("access_token", "")
            if (accessToken.isBlank()) {
                val msg = obj.optString("msg").ifBlank {
                    obj.optString("error_description", "Invalid verification code")
                }
                return@withContext Result.failure(Exception(msg))
            }
            persistSession(obj, phone)
            Result.success(phone)
        } catch (e: Exception) {
            Log.e(TAG, "verifyOtp failed", e)
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

    /** Wipes only the VYN NUMBER session (never touches the Vyn9 account session). */
    fun clearSession() {
        prefs.edit().clear().apply()
    }

    /**
     * Returns a usable phone-session access token: refreshes it when expired.
     * Null means the user must re-verify with OTP.
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
            Log.e(TAG, "VYN NUMBER session refresh failed", e)
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
            ?: throw Exception("VYN NUMBER session expired — please verify your number again")
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
        Result.success(JSONObject(rpc("vn_activate_identity", JSONObject())))
    } catch (e: Exception) {
        Log.e(TAG, "activateIdentity failed", e)
        Result.failure(e)
    }

    /** Checks whether a normalized number is active on VYN NUMBER (no private data exposed). */
    suspend fun searchNumber(rawPhone: String): Result<JSONObject> = try {
        Result.success(JSONObject(rpc("vn_search_number", JSONObject().put("p_phone", rawPhone))))
    } catch (e: Exception) {
        Log.e(TAG, "searchNumber failed", e)
        Result.failure(e)
    }

    /** Opens (or returns the existing) 1:1 conversation with a peer phone. */
    suspend fun openConversation(rawPhone: String): Result<JSONObject> = try {
        Result.success(JSONObject(rpc("vn_open_conversation", JSONObject().put("p_peer_phone", rawPhone))))
    } catch (e: Exception) {
        Log.e(TAG, "openConversation failed", e)
        Result.failure(e)
    }

    suspend fun inbox(): Result<JSONArray> = try {
        Result.success(JSONArray(rpc("vn_inbox", JSONObject())))
    } catch (e: Exception) {
        Log.e(TAG, "inbox failed", e)
        Result.failure(e)
    }

    suspend fun messages(conversationId: String): Result<JSONArray> = try {
        Result.success(JSONArray(rpc("vn_messages", JSONObject().put("p_conversation_id", conversationId))))
    } catch (e: Exception) {
        Log.e(TAG, "messages failed", e)
        Result.failure(e)
    }

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
        Result.success(JSONObject(rpc("vn_send_message", payload)))
    } catch (e: Exception) {
        Log.e(TAG, "sendMessage failed", e)
        Result.failure(e)
    }

    suspend fun markRead(conversationId: String): Result<Unit> = try {
        rpc("vn_mark_read", JSONObject().put("p_conversation_id", conversationId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "markRead failed", e)
        Result.failure(e)
    }

    suspend fun updateProfile(displayName: String): Result<JSONObject> = try {
        Result.success(JSONObject(rpc("vn_update_profile", JSONObject().put("p_display_name", displayName))))
    } catch (e: Exception) {
        Log.e(TAG, "updateProfile failed", e)
        Result.failure(e)
    }

    /** Blocking OkHttp call — every caller already runs on Dispatchers.IO. */
    private fun execute(request: Request): String {
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${body.take(300)}")
        }
        return body
    }
}


