package com.example.data.remote

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
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Everything the RTC engine needs for one Agora channel/session. */
data class AgoraTokenInfo(
    val appId: String,
    val token: String,
    val channelName: String,
    val uid: Int,
    val expiresAt: Long
)

/**
 * Client for the `generate-agora-token` Supabase Edge Function.
 *
 * The AGORA_APP_CERTIFICATE never reaches the app: the edge function signs a
 * short-lived RTC token server-side and returns only {token, appId, channel,
 * uid}. Reuses the existing FlareOfficial Supabase session for auth.
 */
class AgoraTokenService(private val context: Context) {

    companion object {
        private const val TAG = "AgoraTokenService"
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

    /** True when the stored Supabase JWT is expired (or expiring within 30 s). */
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

    /** Returns a fresh access token, transparently refreshing the session when needed. */
    private suspend fun ensureToken(): String? = withContext(Dispatchers.IO) {
        val stored = accessToken() ?: return@withContext null
        if (!isTokenExpired(stored)) return@withContext stored
        try {
            // Reuse the existing, battle-tested refresh logic.
            SupabaseService(context).refreshAuthSession()
            accessToken()
        } catch (e: Throwable) {
            Log.w(TAG, "Token refresh failed; will surface an auth error", e)
            stored
        }
    }

    /**
     * Requests an Agora RTC token for [channelName] + [uid] from the Edge Function.
     * If the session is missing/expired the call reports a friendly failure.
     */
    suspend fun fetchToken(
        channelName: String,
        uid: Int = 0,
        expireInSeconds: Int = 3600
    ): Result<AgoraTokenInfo> = withContext(Dispatchers.IO) {
        try {
            val accessToken = ensureToken()
                ?: throw IOException("You need to sign in to start a call.")
            val headers = Headers.Builder()
                .add("apikey", Backend.KEY)
                .add("Authorization", "Bearer $accessToken")
                .build()
            val payload = JSONObject()
                .put("channelName", channelName.take(64))
                .put("uid", uid)
                .put("expireInSeconds", expireInSeconds)
            val request = Request.Builder()
                .url("${Backend.URL}/functions/v1/generate-agora-token")
                .headers(headers)
                .post(payload.toString().toRequestBody(JSON))
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${extractErrorMessage(body)}")
            }
            val obj = JSONObject(body)
            val token = obj.optString("token", "").takeIf { it.isNotBlank() }
                ?: throw IOException("Agora returned an empty token")
            Result.success(
                AgoraTokenInfo(
                    appId = obj.optString("appId", ""),
                    token = token,
                    channelName = obj.optString("channelName", channelName),
                    uid = obj.optInt("uid", uid),
                    expiresAt = obj.optLong("expiresAt", 0L)
                )
            )
        } catch (e: Throwable) {
            Log.e(TAG, "fetchToken failed", e)
            Result.failure(e)
        }
    }

    /** Pulls a human-readable message from a Supabase/Edge Function error body. */
    private fun extractErrorMessage(body: String): String = try {
        val obj = JSONObject(body)
        obj.optString("error", "")
            .takeIf { it.isNotBlank() }
            ?: body.take(200)
    } catch (e: Exception) {
        body.take(200)
    }
}