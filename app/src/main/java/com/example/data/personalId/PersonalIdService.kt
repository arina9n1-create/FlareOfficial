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
import android.net.Uri
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileOutputStream

import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Personal ID networking layer.
 *
 * PRIVACY MODEL:
 *   * Personal ID is a separate searchable identity namespace from the normal
 *     FlareOfficial account. Searching inside Personal ID NEVER returns normal FlareOfficial
 *     usernames, IDs, phone, email, or the internal account UUID.
 *   * The service authenticates with the EXISTING FlareOfficial account session
 *     ("flareofficial_auth_prefs") — there is no second login. The backend maps
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

    /** True when the caller is logged into FlareOfficial (so their session can be used). */
    fun isSignedIn(): Boolean {
        val token = accessToken() ?: return false
        return token.isNotBlank() && !isTokenExpired(token)
    }

    // --------------------------------------------------------------------------
    // Session token (existing FlareOfficial account session — never stored here)
    // --------------------------------------------------------------------------

    private fun prefs() = context.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE)

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
            ?: throw Exception("Please log in to FlareOfficial first")
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

    private fun authHeaders(token: String): Headers = Headers.Builder()
        .add("apikey", Backend.KEY)
        .add("Authorization", "Bearer $token")
        .build()

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

    /** Logs in to one of THIS account's Personal IDs (must be bound to this FlareOfficial account). */
    suspend fun loginPersonalId(username: String): Result<JSONObject> = try {
        Result.success(JSONObject(rpc("pn_login", JSONObject().put("p_username", username))))
    } catch (e: Exception) {
        Log.e(TAG, "pn_login failed", e)
        Result.failure(e)
    }

    /** Logs out of the active Personal ID ONLY — the FlareOfficial account session is untouched. */
    suspend fun logoutPersonalId(): Result<Unit> =
        unitRpc("pn_logout", JSONObject())

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

    suspend fun sendMessage(
        conversationId: String,
        text: String,
        replyToId: String? = null,
        mediaUrl: String = "",
        mediaType: String = "",
        mediaName: String = ""
    ): Result<JSONObject> = try {
        Result.success(JSONObject(rpc("pn_send_message_v2", JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_message_text", text)
            .put("p_reply_to_id", replyToId ?: JSONObject.NULL)
            .put("p_media_url", mediaUrl)
            .put("p_media_type", mediaType)
            .put("p_media_name", mediaName))))
    } catch (e: Exception) {
        Log.e(TAG, "pn_send_message_v2 failed", e)
        Result.failure(e)
    }

    suspend fun editMessage(conversationId: String, messageId: String, text: String): Result<Unit> =
        unitRpc("pn_edit_message", JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_message_id", messageId)
            .put("p_message_text", text))

    suspend fun toggleReaction(conversationId: String, messageId: String, emoji: String): Result<Unit> =
        unitRpc("pn_toggle_reaction", JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_message_id", messageId)
            .put("p_emoji", emoji))

    suspend fun setPinned(conversationId: String, messageId: String, pinned: Boolean): Result<Unit> =
        unitRpc("pn_set_message_pinned", JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_message_id", messageId)
            .put("p_pinned", pinned))

    suspend fun conversationSettings(conversationId: String): Result<JSONObject> = try {
        Result.success(JSONObject(rpc("pn_conversation_settings", JSONObject()
            .put("p_conversation_id", conversationId))))
    } catch (e: Exception) {
        Log.e(TAG, "pn_conversation_settings failed", e)
        Result.failure(e)
    }

    suspend fun updateConversationSettings(
        conversationId: String,
        muted: Boolean,
        archived: Boolean
    ): Result<Unit> = unitRpc("pn_update_conversation_settings", JSONObject()
        .put("p_conversation_id", conversationId)
        .put("p_muted", muted)
        .put("p_archived", archived))

    suspend fun updatePrivacy(readReceipts: Boolean): Result<Unit> =
        unitRpc("pn_update_privacy", JSONObject().put("p_read_receipts", readReceipts))

    suspend fun touchPresence(): Result<Unit> = unitRpc("pn_touch_presence", JSONObject())

    suspend fun clearHistory(conversationId: String): Result<Unit> =
        unitRpc("pn_clear_history", JSONObject().put("p_conversation_id", conversationId))

    // Block / unblock a Personal ID username (personal_id_blocks via SECURITY DEFINER RPCs).
    suspend fun toggleBlock(username: String, blocked: Boolean): Result<Unit> =
        unitRpc("pn_toggle_block", JSONObject()
            .put("p_username", username)
            .put("p_blocked", blocked))

    suspend fun blockedList(): Result<List<String>> = try {
        val arr = JSONArray(rpc("pn_blocked_list", JSONObject()))
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        Result.success(out)
    } catch (e: Exception) {
        Log.e(TAG, "pn_blocked_list failed", e)
        Result.failure(e)
    }

    suspend fun uploadMedia(
        conversationId: String,
        uri: Uri,
        displayName: String,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = ensureToken() ?: throw IOException("Please sign in to FlareOfficial first")
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80)
                .ifBlank { "attachment" }
            val path = "$conversationId/${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}_$safeName"
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open selected file")
            val body = object : RequestBody() {
                override fun contentType() = mimeType.ifBlank { "application/octet-stream" }.toMediaType()
                override fun writeTo(sink: BufferedSink) {
                    stream.use { input -> sink.write(input.readBytes()) }
                }
            }
            val request = Request.Builder()
                .url("${Backend.URL}/storage/v1/object/personal-id-media/$path")
                .headers(authHeaders(token).newBuilder().add("x-upsert", "false").build())
                .post(body)
                .build()
            execute(request)
            Result.success(path)
        } catch (e: Exception) {
            Log.e(TAG, "Personal ID media upload failed", e)
            Result.failure(e)
        }
    }

    suspend fun downloadMedia(storagePath: String, suggestedName: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val token = ensureToken() ?: throw IOException("Please sign in to FlareOfficial first")
                val request = Request.Builder()
                    .url("${Backend.URL}/storage/v1/object/authenticated/personal-id-media/$storagePath")
                    .headers(authHeaders(token))
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw IOException("HTTP ${response.code}: ${extractErrorMessage(body)}")
                }
                val safeName = suggestedName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .ifBlank { "attachment" }
                val file = File(context.cacheDir, "pid_${System.currentTimeMillis()}_$safeName")
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                } ?: throw IOException("Empty attachment")
                Result.success(file)
            } catch (e: Exception) {
                Log.e(TAG, "Personal ID media download failed", e)
                Result.failure(e)
            }
        }

    private suspend fun unitRpc(name: String, params: JSONObject): Result<Unit> = try {
        rpc(name, params)
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "$name failed", e)
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