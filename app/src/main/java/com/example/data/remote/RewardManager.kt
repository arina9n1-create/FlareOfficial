package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.model.AdminConfig
import com.example.data.model.UserRewardWallet
import com.example.data.model.WithdrawalRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class RewardManager(private val context: Context? = null) {

    companion object {
        private const val TAG = "RewardManager"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun getBaseHeaders(preferMerge: Boolean = false): Headers {
        val builder = Headers.Builder()
            .add("apikey", Backend.KEY)
        val token = context?.getSharedPreferences("vyn9_auth_prefs", Context.MODE_PRIVATE)
            ?.getString("access_token", null)?.takeIf { it.isNotBlank() }
        builder.add("Authorization", "Bearer ${token ?: Backend.KEY}")
        builder.add("Content-Type", "application/json")
        if (preferMerge) {
            builder.add("Prefer", "resolution=merge-duplicates,return=representation")
        }
        return builder.build()
    }

    /** Executes a request and throws on non-2xx so a failed write is never reported as success. */
    private fun executeChecked(request: Request): String {
        val response = client.newCall(request).execute()
        return try {
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Reward backend request failed (${response.code}): ${body.take(200)}")
            }
            body
        } finally {
            response.close()
        }
    }

    fun observeUserWallet(userHandle: String): Flow<UserRewardWallet?> = flow {
        // Emit updated wallet from local/remote
        emit(null)
    }.flowOn(Dispatchers.IO)

    suspend fun syncWallet(userHandle: String, wallet: UserRewardWallet) = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/user_rewards"
            val payload = JSONObject().apply {
                put("user_handle", userHandle.lowercase())
                put("total_credits", wallet.totalCredits)
                put("today_reels_watched", wallet.todayReelsWatched)
                put("today_reels_uploaded", wallet.todayReelsUploaded)
                put("total_reels_watched", wallet.totalReelsWatched)
                put("total_reels_uploaded", wallet.totalReelsUploaded)
                put("referral_code", wallet.referralCode)
                put("referred_users_count", wallet.referredUsersCount)
                put("referral_earnings", wallet.referralEarnings)
                put("updated_at", System.currentTimeMillis())
            }
            val request = Request.Builder()
                .url(url)
                .headers(getBaseHeaders(preferMerge = true))
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            executeChecked(request)
        } catch (e: Throwable) {
            Log.e(TAG, "Error syncing user wallet to Supabase", e)
        }
    }

    suspend fun recordDailyLogin(userHandle: String, email: String): UserRewardWallet? = withContext(Dispatchers.IO) {
        try {
            null
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun processNewUserSignup(
        name: String,
        email: String,
        userHandle: String,
        enteredReferralCode: String?,
        referralBonusAmount: Int
    ): Result<UserRewardWallet> = withContext(Dispatchers.IO) {
        try {
            val wallet = UserRewardWallet(
                referralCode = userHandle.uppercase().take(6),
                referredByCode = enteredReferralCode ?: ""
            )
            Result.success(wallet)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun recordReelWatch(userHandle: String, watchDurationSeconds: Int, creditsEarned: Int) = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/reel_activities"
            val payload = JSONObject().apply {
                put("user_handle", userHandle.lowercase())
                put("duration_seconds", watchDurationSeconds)
                put("credits_earned", creditsEarned)
                put("timestamp", System.currentTimeMillis())
            }
            val request = Request.Builder()
                .url(url)
                .headers(getBaseHeaders())
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeChecked(request)
        } catch (e: Throwable) {
            Log.e(TAG, "Error recording reel watch to Supabase", e)
        }
    }

    suspend fun recordPostCreated(userHandle: String, rewardCredits: Int) = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/post_activities"
            val payload = JSONObject().apply {
                put("user_handle", userHandle.lowercase())
                put("reward_credits", rewardCredits)
                put("timestamp", System.currentTimeMillis())
            }
            val request = Request.Builder()
                .url(url)
                .headers(getBaseHeaders())
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeChecked(request)
        } catch (e: Throwable) {
            Log.e(TAG, "Error recording post activity to Supabase", e)
        }
    }

    suspend fun recordReelWatchReward(userHandle: String, secondsWatched: Int): Int = withContext(Dispatchers.IO) {
        if (secondsWatched >= 10) 5 else 0
    }

    suspend fun syncWithdrawal(request: WithdrawalRequest): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/withdrawals"
            val payload = JSONObject().apply {
                put("id", request.id)
                put("user_handle", request.userHandle)
                put("user_email", request.userEmail)
                put("method", request.method)
                put("account_number", request.accountNumber)
                put("credits_used", request.creditsUsed)
                put("amount_usd", request.amountUSD)
                put("status", request.status)
                put("request_date", request.requestDate)
                put("transaction_note", request.transactionNote)
                put("timestamp", System.currentTimeMillis())
            }
            val req = Request.Builder()
                .url(url)
                .headers(getBaseHeaders(preferMerge = true))
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Throwable) {
            Log.e(TAG, "Error syncing withdrawal to Supabase", e)
            false
        }
    }

    /** Fetches ALL withdrawal requests from Supabase (Admin view across all users). */
    suspend fun fetchWithdrawals(): List<WithdrawalRequest> = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/withdrawals?select=*&order=timestamp.desc"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val array = JSONArray(body)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                WithdrawalRequest(
                    id = obj.optString("id", ""),
                    userHandle = obj.optString("user_handle", ""),
                    userEmail = obj.optString("user_email", ""),
                    method = obj.optString("method", ""),
                    accountNumber = obj.optString("account_number", ""),
                    creditsUsed = obj.optInt("credits_used", 0),
                    amountUSD = obj.optDouble("amount_usd", 0.0),
                    amountBDT = obj.optDouble("amount_bdt", 0.0),
                    status = obj.optString("status", "PENDING"),
                    requestDate = obj.optString("request_date", ""),
                    transactionNote = obj.optString("transaction_note", ""),
                    timestamp = obj.optLong("timestamp", 0L)
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error fetching withdrawals from Supabase", e)
            emptyList()
        }
    }

    /** PATCHes a withdrawal's status + note in Supabase (Admin approve/reject). */
    suspend fun updateWithdrawalRemote(requestId: String, status: String, note: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/withdrawals?id=eq.$requestId"
            val payload = JSONObject().apply {
                put("status", status)
                put("transaction_note", note)
            }
            val req = Request.Builder()
                .url(url)
                .headers(getBaseHeaders())
                .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeChecked(req)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error updating withdrawal status in Supabase", e)
            false
        }
    }

    /** Credits coins back to a user's remote wallet (rejection refund) via the refund_withdrawal RPC. */
    suspend fun refundWithdrawalCredits(userHandle: String, credits: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/rpc/refund_withdrawal"
            val payload = JSONObject().apply {
                put("p_user_handle", userHandle.lowercase())
                put("p_credits", credits)
            }
            val req = Request.Builder()
                .url(url)
                .headers(getBaseHeaders())
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeChecked(req)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error refunding withdrawal credits in Supabase", e)
            false
        }
    }

    /** Fetches a user's remote wallet row from user_rewards (refund merge). */
    suspend fun fetchRemoteWallet(userHandle: String): UserRewardWallet? = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/user_rewards?user_handle=eq.${userHandle.lowercase()}&select=*&limit=1"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val array = JSONArray(body)
            if (array.length() == 0) return@withContext null
            val obj = array.getJSONObject(0)
            UserRewardWallet(totalCredits = obj.optInt("total_credits", 0))
        } catch (e: Throwable) {
            Log.e(TAG, "Error fetching remote wallet from Supabase", e)
            null
        }
    }

    suspend fun syncAdminConfig(config: AdminConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/admin_configs"
            val payload = JSONObject().apply {
                put("id", "global_config")
                put("credits_per_reel", config.creditsPerReel)
                put("required_reel_watch_seconds", config.requiredReelWatchSeconds)
                put("credits_per_dollar", config.creditsPerDollar)
                put("referral_bonus_credits", config.referralBonusCredits)
                put("min_withdrawal_usd", config.minWithdrawalUSD)
                put("is_bkash_enabled", config.isBkashEnabled)
                put("is_nagad_enabled", config.isNagadEnabled)
                put("is_rocket_enabled", config.isRocketEnabled)
                put("is_binance_enabled", config.isBinanceEnabled)
                put("is_paypal_enabled", config.isPaypalEnabled)
                put("notice_message", config.noticeMessage)
                put("updated_at", System.currentTimeMillis())
            }
            val req = Request.Builder()
                .url(url)
                .headers(getBaseHeaders(preferMerge = true))
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Throwable) {
            Log.e(TAG, "Error syncing admin config to Supabase", e)
            false
        }
    }
}
