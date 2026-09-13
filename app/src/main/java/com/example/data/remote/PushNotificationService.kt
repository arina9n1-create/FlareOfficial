package com.example.data.remote

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for the SECURED `send-push-notification` Supabase Edge Function.
 *
 * Security contract (server-enforced, see the Edge Function):
 *  - The app NEVER sends raw FCM tokens, notification titles, channel ids, or
 *    arbitrary data maps. The Edge Function derives every payload server-side
 *    from verified database rows (call_history / chat_messages / follows).
 *  - CALL  → requires the callId of a call_history row the signed-in user
 *    actually placed (caller_handle match + RINGING/IN_PROGRESS status).
 *  - CHAT  → allowed only when a conversation or follow relationship exists.
 *  - FOLLOW → allowed only when the caller really follows the recipient.
 *  - ADMIN broadcasts are sent by trusted backends, never by this client.
 * No Agora tokens are ever carried in push payloads — the callee fetches its
 * own short-lived RTC token from `generate-agora-token` when it answers.
 */
class PushNotificationService(private val context: Context) {

    companion object {
        private const val TAG = "PushNotificationService"
        private const val PREFS = "flareofficial_auth_prefs"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun accessToken(): String? =
        prefs().getString("access_token", null)?.takeIf { it.isNotBlank() }

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

    /** Fresh access token; transparently refreshes an expired Supabase session. */
    private suspend fun ensureToken(): String? = withContext(Dispatchers.IO) {
        val stored = accessToken() ?: return@withContext null
        if (!isTokenExpired(stored)) return@withContext stored
        try {
            SupabaseService(context).refreshAuthSession()
            accessToken()
        } catch (e: Throwable) {
            Log.w(TAG, "Token refresh failed", e)
            stored
        }
    }

    /** Incoming-call ring: type=CALL + verified receiver uid + the live Agora channel. */
    suspend fun notifyCall(receiverUid: String, callId: String, channelName: String): Result<Unit> = post(
        JSONObject()
            .put("type", "CALL")
            .put("userId", receiverUid)
            .put("callId", callId)
            .put("channelName", channelName)
    )

    /** Chat alert: type=CHAT + recipient uid + message text (title is server-derived). */
    suspend fun notifyChat(receiverUid: String, body: String): Result<Unit> = post(
        JSONObject()
            .put("type", "CHAT")
            .put("userId", receiverUid)
            .put("body", body.take(1024))
    )

    /** Follow alert: type=FOLLOW + recipient uid — the server verifies the follow row. */
    suspend fun notifyFollow(receiverUid: String): Result<Unit> = post(
        JSONObject()
            .put("type", "FOLLOW")
            .put("userId", receiverUid)
    )

    private suspend fun post(payload: JSONObject): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = ensureToken()
                ?: throw IOException("Not signed in; cannot request push delivery")
            val headers = Headers.Builder()
                .add("apikey", Backend.KEY)
                .add("Authorization", "Bearer $accessToken")
                .build()
            val request = Request.Builder()
                .url("${Backend.URL}/functions/v1/send-push-notification")
                .headers(headers)
                .post(payload.toString().toRequestBody(JSON))
                .build()
            val pushType = payload.optString("type", "?")
            Log.e(TAG, "PUSH_NOTIFICATION_SEND_STARTED type=$pushType payload=$payload")
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "PUSH_NOTIFICATION_SEND_FAILED HTTP ${response.code}: ${extractErrorMessage(body)}")
                throw IOException("HTTP ${response.code}: ${extractErrorMessage(body)}")
            }
            Log.e(TAG, "PUSH_NOTIFICATION_SEND_SUCCESS type=$pushType")
            Result.success(Unit)
        } catch (e: Throwable) {
            // Push delivery is strictly best-effort: never surface into UI flows.
            Log.e(TAG, "PUSH_NOTIFICATION_SEND_EXCEPTION: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun extractErrorMessage(body: String): String = try {
        JSONObject(body).optString("error", "").takeIf { it.isNotBlank() } ?: body.take(200)
    } catch (e: Exception) {
        body.take(200)
    }
}