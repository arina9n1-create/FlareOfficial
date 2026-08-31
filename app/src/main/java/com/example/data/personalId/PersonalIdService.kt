package com.example.data.personalId

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
 * Personal ID networking layer.
 *
 * PRIVACY MODEL:
 *   * Personal ID is a separate searchable identity namespace from the normal
 *     Vyn9 account. Searching inside Personal ID NEVER returns normal Vyn9
 *     usernames, IDs, phone, email, or the internal account UUID.
 *   * The service authenticates with the EXISTING Vyn9 account session
 *     ("vyn9_auth_prefs") — there is no second login. The backend maps
 *     auth.uid() -> personal_ids.user_id internally, but that mapping is never
 *     exposed through RPCs (pn_search returns only username + avatar_url).
 *   * All operations go through SECURITY DEFINER RPCs; direct table access is
 *     revoked.
 */
class PersonalIdService(private val context: Context) {

    companion object {
        private const val TAG = "PersonalIdService"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // --------------------------------------------------------------------------
    // Username helpers
    // --------------------------------------------------------------------------

    /** Lowercase, validates 3-20 chars, starts with a letter, letters/digits/underscore. */
    fun normalizeUsername(raw: String): String? {
        val u = raw.trim().lowercase()
        return if (Regex("^[a-z][a-z0-9_]{2,19}$").matches(u)) u else null
    }

    fun isValidUsername(raw: String): Boolean = normalizeUsername(raw) != null

    /** True when the caller is logged into Vyn9 (so their session can be used). */
    fun isSignedIn(): Boolean {
        val token = accessToken() ?: return false
        return token.isNotBlank() && !isTokenExpired(token)
    }

    // --------------------------------------------------------------------------
    // Session token (existing Vyn9 account session — never stored here)
    // --------------------------------------------------------------------------

    private fun prefs() = context.getSharedPreferences("vyn9_auth_prefs", Context.MODE_PRIVATE)

    private fun accessToken(): String? =
        prefs().getString("access_token", null)?.takeIf { it.isNotBlank() }

    private suspend fun ensureToken(): String? = withContext(Dispatchers.IO) {
        var token = accessToken() ?: return@withContext null
        if (!isTokenExpired(token)) return@withContext token
        val refresh = prefs().getString("refresh_token", null)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        try {
            val payload = JSONObject().put("refresh_token", refresh)
            val req = Request.Builder()
                .url("${Backend.URL}/auth/v1/token?grant_type=refresh_token")
                .headers(Headers.Builder().add("apikey", Backend.KEY).build())
                .post(payload.toString().toRequestBody(JSON))
                .build()
            val obj = JSONObject(execute(req))
            val newToken = obj.optString("access_token", "")
            if (newToken.isBlank()) return@withContext null
            prefs().edit()
                .putString("access_token", newToken)
                .putString("refresh_token", obj.optString("refresh_token", refresh))
                .apply()
            newToken
        } catch (e: Exception) {
            Log.e(TAG, "token refresh failed", e)
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
// --------------------------------------------------------------------------
    // RPCs
    // --------------------------------------------------------------------------

    private suspend fun rpc(name: String, payload: JSONObject): String = withContext(Dispatchers.IO) {
        val token = ensureToken()
            ?: throw Exception("Please log in to Vyn9 first")
        val req = Request.Builder()
            .url("${Backend.URL}/rest/v1/rpc/$name")
            .headers(Headers.Builder()
                .add("apikey", Backend.KEY)
                .add("Authorization", "Bearer $token")
                .build())
            .post(payload.toString().toRequestBody(JSON))
            .build()
        execute(req)
    }

    /** Current caller's Personal ID (null when not created yet). */
    suspend fun me(): Result<JSONObject> = try {
        val obj = JSONObject(rpc("pn_me", JSONObject()))
        Result.success(obj)
    } catch (e: Exception) {
        Log.e(TAG, "pn_me failed", e)
        Result.failure(e)
    }

    suspend fun create(username: String): Result<JSONObject> = try {
        Result.success(JSONObject(rpc("pn_create", JSONObject().put("p_username", username))))
    } catch (e: Exception) {
        Log.e(TAG, "pn_create failed", e)
        Result.failure(e)
    }

    suspend fun search(query: String): Result<JSONArray> = try {
        Result.success(JSONArray(rpc("pn_search", JSONObject().put("p_query", query))))
    } catch (e: Exception) {
        Log.e(TAG, "pn_search failed", e)
        Result.failure(e)
    }

    suspend fun openConversation(username: String): Result<JSONObject> = try {
        Result.success(JSONObject(rpc("pn_open_conversation", JSONObject().put("p_username", username))))
    } catch (e: Exception) {
        Log.e(TAG, "pn_open_conversation failed", e)
        Result.failure(e)
    }

    suspend fun inbox(): Result<JSONArray> = try {
        Result.success(JSONArray(rpc("pn_inbox", JSONObject())))
    } catch (e: Exception) {
        Log.e(TAG, "pn_inbox failed", e)
        Result.failure(e)
    }

    suspend fun messages(conversationId: String): Result<JSONArray> = try {
        Result.success(JSONArray(rpc("pn_messages", JSONObject().put("p_conversation_id", conversationId))))
    } catch (e: Exception) {
        Log.e(TAG, "pn_messages failed", e)
        Result.failure(e)
    }

    suspend fun sendMessage(conversationId: String, text: String): Result<JSONObject> = try {
        Result.success(JSONObject(rpc("pn_send_message", JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_message_text", text))))
    } catch (e: Exception) {
        Log.e(TAG, "pn_send_message failed", e)
        Result.failure(e)
    }

    suspend fun markRead(conversationId: String): Result<Unit> = try {
        rpc("pn_mark_read", JSONObject().put("p_conversation_id", conversationId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "pn_mark_read failed", e)
        Result.failure(e)
    }

    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> = try {
        rpc("pn_delete_message", JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_message_id", messageId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "pn_delete_message failed", e)
        Result.failure(e)
    }
// --- Call signaling ---

    suspend fun callInitiate(conversationId: String, callType: String): Result<JSONObject> = try {
        Result.success(JSONObject(rpc("pn_call_initiate", JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_call_type", callType))))
    } catch (e: Exception) {
        Log.e(TAG, "pn_call_initiate failed", e)
        Result.failure(e)
    }

    suspend fun callPoll(conversationId: String): Result<JSONArray> = try {
        Result.success(JSONArray(rpc("pn_call_poll", JSONObject().put("p_conversation_id", conversationId))))
    } catch (e: Exception) {
        Log.e(TAG, "pn_call_poll failed", e)
        Result.failure(e)
    }

    suspend fun callUpdate(callId: String, sdp: String? = null, status: String? = null): Result<Unit> = try {
        rpc("pn_call_update", JSONObject()
            .put("p_call_id", callId)
            .put("p_sdp", sdp ?: JSONObject.NULL)
            .put("p_status", status ?: JSONObject.NULL))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "pn_call_update failed", e)
        Result.failure(e)
    }

    suspend fun callAnswer(callId: String, sdp: String): Result<Unit> = try {
        rpc("pn_call_answer", JSONObject()
            .put("p_call_id", callId)
            .put("p_sdp", sdp))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "pn_call_answer failed", e)
        Result.failure(e)
    }

    suspend fun callReject(callId: String): Result<Unit> = try {
        rpc("pn_call_reject", JSONObject().put("p_call_id", callId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "pn_call_reject failed", e)
        Result.failure(e)
    }

    suspend fun callEnd(callId: String): Result<Unit> = try {
        rpc("pn_call_end", JSONObject().put("p_call_id", callId))
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "pn_call_end failed", e)
        Result.failure(e)
    }

    // --------------------------------------------------------------------------

    private fun execute(request: Request): String {
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${extractErrorMessage(body)}")
        }
        return body
    }

    private fun extractErrorMessage(body: String): String = try {
        val obj = JSONObject(body)
        listOf("msg", "error_description", "message", "error", "hint")
            .firstNotNullOfOrNull { key -> obj.optString(key, "").takeIf { it.isNotBlank() } }
            ?: body.take(200)
    } catch (e: Exception) {
        body.take(200)
    }
}