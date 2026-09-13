package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.model.AdminConfig
import com.example.data.model.PlatformWalletOverview
import com.example.data.model.UserEarningsOverview
import com.example.data.model.UserRewardWallet
import com.example.data.model.WalletAuditLog
import com.example.data.model.WalletFraudFlag
import com.example.data.model.WalletSummary
import com.example.data.model.WalletTransaction
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
        val token = context?.getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE)
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

    /** Fetches the total amount of credits currently held by ALL users in their wallets. */
    suspend fun fetchTotalPlatformBalance(): Int = withContext(Dispatchers.IO) {
        try {
            // Using a simple select on the column and summing here for now.
            // In a production app with millions of users, an RPC (Stored Procedure) 
            // would be much faster: SELECT sum(total_credits) FROM user_rewards.
            val url = "${Backend.URL}/rest/v1/user_rewards?select=total_credits"
            val body = executeChecked(Request.Builder().url(url).headers(getBaseHeaders()).get().build())
            val array = JSONArray(body)
            var total = 0
            for (i in 0 until array.length()) {
                total += array.getJSONObject(i).optInt("total_credits", 0)
            }
            total
        } catch (e: Exception) {
            Log.e(TAG, "fetchTotalPlatformBalance failed", e)
            0
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

    suspend fun updateUserBalanceRemote(userHandle: String, newBalance: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${Backend.URL}/rest/v1/user_rewards"
            val payload = JSONObject().apply {
                put("user_handle", userHandle.lowercase())
                put("total_credits", newBalance)
                put("updated_at", System.currentTimeMillis())
            }
            val request = Request.Builder()
                .url(url)
                .headers(getBaseHeaders(preferMerge = true))
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeChecked(request)
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateUserBalanceRemote failed", e)
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
                put("verification_badge_fee_usd", config.verificationBadgeFeeUSD)
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

    // ==================================================================
    // PROFESSIONAL WALLET — server-validated RPCs + reads
    // ==================================================================
    private fun parseIsoTime(iso: String): Long = try {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        fmt.parse(iso.take(19))?.time ?: 0L
    } catch (e: Exception) { 0L }

    /** Maps an OkHttp failure body to a friendly message (PostgREST error JSON). */
    private fun friendlyRpcError(message: String): String {
        return try {
            val json = JSONObject(message)
            if (json.has("message")) json.getString("message") else message.take(200)
        } catch (e: Exception) {
            message.take(200)
        }
    }

    /** Invokes a SECURITY DEFINER RPC and returns the raw JSON body. */
    private fun callRpc(name: String, payload: JSONObject): String {
        val url = "${Backend.URL}/rest/v1/rpc/$name"
        val request = Request.Builder()
            .url(url)
            .headers(getBaseHeaders())
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return executeChecked(request)
    }

    /** Loads the authenticated user's wallet summary (server-authoritative). */
    suspend fun fetchWalletSummary(userHandle: String): WalletSummary = withContext(Dispatchers.IO) {
        try {
            val body = callRpc("wallet_summary", JSONObject().apply { put("p_user_handle", userHandle.lowercase()) })
            parseWalletSummary(JSONObject(body))
        } catch (e: Throwable) {
            Log.e(TAG, "fetchWalletSummary failed", e)
            WalletSummary(error = friendlyRpcError(e.message ?: ""))
        }
    }

    private fun parseWalletSummary(o: JSONObject): WalletSummary = WalletSummary(
        userHandle = o.optString("user_handle", ""),
        withdrawableCredits = o.optInt("withdrawable_credits", 0),
        withdrawableUsd = o.optDouble("withdrawable_usd", 0.0),
        withdrawableBdt = o.optDouble("withdrawable_bdt", 0.0),
        pendingWithdrawalCredits = o.optInt("pending_withdrawal_credits", 0),
        pendingWithdrawalUsd = o.optDouble("pending_withdrawal_usd", 0.0),
        totalEarnedCredits = o.optInt("total_earned_credits", 0),
        referralCredits = o.optInt("referral_credits", 0),
        watchCredits = o.optInt("watch_credits", 0),
        challengeCredits = o.optInt("challenge_credits", 0),
        referralRedeemedCredits = o.optInt("referral_redeemed_credits", 0),
        watchRedeemedCredits = o.optInt("watch_redeemed_credits", 0),
        challengeRedeemedCredits = o.optInt("challenge_redeemed_credits", 0),
        totalCredits = o.optInt("total_credits", 0),
        creditsPerDollar = o.optInt("credits_per_dollar", 2000),
        usdToBdt = o.optDouble("usd_to_bdt", 0.005)
    )

    /** Activates the Verification Badge by paying the admin-set fee from the user's wallet. */
    suspend fun purchaseVerificationBadge(userHandle: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = callRpc("purchase_verification_badge", JSONObject().apply {
                put("p_user_handle", userHandle.lowercase())
            })
            Result.success(JSONObject(body))
        } catch (e: Throwable) {
            Log.e(TAG, "purchaseVerificationBadge failed", e)
            Result.failure(e)
        }
    }

    /** Redeems eligible reward credits (REFERRAL/WATCH/CHALLENGE) into the withdrawable wallet. */
    suspend fun redeemRewardCredits(userHandle: String, source: String): Result<WalletSummary> = withContext(Dispatchers.IO) {
        try {
            val body = callRpc("redeem_reward_credits", JSONObject().apply {
                put("p_user_handle", userHandle.lowercase())
                put("p_source", source)
            })
            val o = JSONObject(body)
            if (o.optBoolean("ok", false)) {
                Result.success(WalletSummary(withdrawableCredits = o.optInt("withdrawable_credits", 0)))
            } else {
                Result.failure(Exception(o.optString("message", "Redemption failed")))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "redeemRewardCredits failed ($source)", e)
            Result.failure(Exception(friendlyRpcError(e.message ?: "Redemption failed")))
        }
    }

    /** Requests a withdrawal; funds are reserved atomically server-side. */
    suspend fun requestWalletWithdrawal(
        userHandle: String,
        email: String,
        method: String,
        account: String,
        credits: Int,
        amountUsd: Double,
        amountBdt: Double,
        clientRef: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = callRpc("request_wallet_withdrawal", JSONObject().apply {
                put("p_user_handle", userHandle.lowercase())
                put("p_email", email)
                put("p_method", method)
                put("p_account_number", account)
                put("p_credits", credits)
                put("p_amount_usd", amountUsd)
                put("p_amount_bdt", amountBdt)
                put("p_client_ref", clientRef)
            })
            val o = JSONObject(body)
            val id = o.optString("withdrawal_id", "")
            if (o.optBoolean("ok", false) && id.isNotBlank()) Result.success(id)
            else Result.failure(Exception(o.optString("message", "Withdrawal request failed")))
        } catch (e: Throwable) {
            Log.e(TAG, "requestWalletWithdrawal failed", e)
            Result.failure(Exception(friendlyRpcError(e.message ?: "Withdrawal request failed")))
        }
    }

    /** Super Admin: manually add/remove coins with a mandatory reason (audited server-side). */
    suspend fun adminAdjustWallet(targetHandle: String, amount: Int, reason: String, type: String, source: String = "ADMIN"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = callRpc("admin_adjust_wallet", JSONObject().apply {
                put("p_target_handle", targetHandle.lowercase())
                put("p_amount", amount)
                put("p_reason", reason)
                put("p_type", type)
                put("p_source", source)
            })
            val o = JSONObject(body)
            if (o.optBoolean("ok", false)) Result.success("Adjusted by ${o.optInt("delta", 0)} coins")
            else Result.failure(Exception(o.optString("message", "Adjustment failed")))
        } catch (e: Throwable) {
            Log.e(TAG, "adminAdjustWallet failed", e)
            Result.failure(Exception(friendlyRpcError(e.message ?: "Adjustment failed")))
        }
    }

    /** Staff: reject a withdrawal with refund + reversal transaction (atomic). */
    suspend fun adminRejectWithdrawalV2(withdrawalId: String, reason: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = callRpc("admin_reject_withdrawal_v2", JSONObject().apply {
                put("p_withdrawal_id", withdrawalId)
                put("p_reason", reason)
            })
            val o = JSONObject(body)
            if (o.optBoolean("ok", false)) Result.success("Rejected & refunded")
            else Result.failure(Exception(o.optString("message", "Rejection failed")))
        } catch (e: Throwable) {
            Log.e(TAG, "adminRejectWithdrawalV2 failed", e)
            Result.failure(Exception(friendlyRpcError(e.message ?: "Rejection failed")))
        }
    }

    /** Fetch transaction history for the calling user (or all, when staff). */
    suspend fun fetchWalletTransactions(userHandle: String? = null): List<WalletTransaction> = withContext(Dispatchers.IO) {
        try {
            val body = callRpc("list_wallet_transactions", JSONObject().apply {
                if (userHandle?.isNotBlank() == true) put("p_user_handle", userHandle.lowercase())
                put("p_limit", 200)
            })
            val arr = JSONArray(body)
            List(arr.length()) { i -> arr.getJSONObject(i).let {
                WalletTransaction(
                    id = it.optString("id", ""),
                    userHandle = it.optString("user_handle", ""),
                    txType = it.optString("tx_type", ""),
                    source = it.optString("source", ""),
                    coinAmount = it.optInt("coin_amount", 0),
                    monetaryAmount = it.optDouble("monetary_amount", 0.0),
                    currency = it.optString("currency", "USD"),
                    status = it.optString("status", "COMPLETED"),
                    creditOrDebit = it.optString("credit_or_debit", "CREDIT"),
                    reference = it.optString("reference", ""),
                    createdAt = parseIsoTime(it.optString("created_at", ""))
                )
            }}
        } catch (e: Throwable) {
            Log.e(TAG, "fetchWalletTransactions failed", e)
            emptyList()
        }
    }

    /** Admin: platform-wide wallet overview. */
    suspend fun fetchAdminWalletOverview(): PlatformWalletOverview = withContext(Dispatchers.IO) {
        try {
            val body = callRpc("admin_wallet_overview", JSONObject())
            val o = JSONObject(body)
            PlatformWalletOverview(
                totalPlatformCoins = o.optInt("total_platform_coins", 0),
                totalCoinsEarnedByUsers = o.optInt("total_coins_earned_by_users", 0),
                totalCoinsRedeemed = o.optInt("total_coins_redeemed", 0),
                totalWithdrawnAmountUsd = o.optDouble("total_withdrawn_amount_usd", 0.0),
                pendingPayoutAmountUsd = o.optDouble("pending_payout_amount_usd", 0.0),
                totalPaidPayoutsUsd = o.optDouble("total_paid_payouts_usd", 0.0),
                pendingPayoutCount = o.optInt("pending_payout_count", 0),
                todayEarnedCoins = o.optInt("today_earned_coins", 0),
                yesterdayEarnedCoins = o.optInt("yesterday_earned_coins", 0),
                todayWithdrawnUsd = o.optDouble("today_withdrawn_usd", 0.0),
                yesterdayWithdrawnUsd = o.optDouble("yesterday_withdrawn_usd", 0.0)
            )
        } catch (e: Throwable) {
            Log.e(TAG, "fetchAdminWalletOverview failed", e)
            PlatformWalletOverview(error = friendlyRpcError(e.message ?: ""))
        }
    }

    /** Admin: per-user earnings overview. */
    suspend fun fetchUserEarningsOverview(): List<UserEarningsOverview> = withContext(Dispatchers.IO) {
        try {
            val body = callRpc("admin_user_earnings_overview", JSONObject())
            val arr = JSONArray(body)
            List(arr.length()) { i -> arr.getJSONObject(i).let {
                UserEarningsOverview(
                    userHandle = it.optString("user_handle", ""),
                    name = it.optString("name", ""),
                    totalEarned = it.optInt("total_earned", 0),
                    referralCredits = it.optInt("referral_credits", 0),
                    watchCredits = it.optInt("watch_credits", 0),
                    challengeCredits = it.optInt("challenge_credits", 0),
                    redeemed = it.optInt("redeemed", 0),
                    withdrawn = it.optInt("withdrawn", 0),
                    withdrawable = it.optInt("withdrawable", 0)
                )
            }}
        } catch (e: Throwable) {
            Log.e(TAG, "fetchUserEarningsOverview failed", e)
            emptyList()
        }
    }

    /** Admin: audit log of administrative wallet/reward actions. */
    suspend fun fetchAdminAuditLog(): List<WalletAuditLog> = withContext(Dispatchers.IO) {
        try {
            val body = callRpc("admin_audit_log", JSONObject().apply { put("p_limit", 200) })
            val arr = JSONArray(body)
            List(arr.length()) { i -> arr.getJSONObject(i).let {
                WalletAuditLog(
                    id = it.optString("id", ""),
                    adminHandle = it.optString("admin_handle", ""),
                    targetHandle = it.optString("target_handle", ""),
                    action = it.optString("action", ""),
                    fieldName = it.optString("field_name", ""),
                    previousValue = it.optString("previous_value", ""),
                    newValue = it.optString("new_value", ""),
                    reference = it.optString("reference", ""),
                    reason = it.optString("reason", ""),
                    createdAt = parseIsoTime(it.optString("created_at", ""))
                )
            }}
        } catch (e: Throwable) {
            Log.e(TAG, "fetchAdminAuditLog failed", e)
            emptyList()
        }
    }

    /** Admin: fraud / suspicious activity flags. */
    suspend fun fetchFraudFlags(includeResolved: Boolean = false): List<WalletFraudFlag> = withContext(Dispatchers.IO) {
        try {
            parseFraudFlags(callRpc("admin_fraud_flags", JSONObject().apply { put("p_include_resolved", includeResolved) }))
        } catch (e: Throwable) {
            Log.e(TAG, "fetchFraudFlags failed", e)
            emptyList()
        }
    }

    /** Super Admin: run fraud detection (flags only, never deletes users). */
    suspend fun detectSuspiciousActivity(): List<WalletFraudFlag> = withContext(Dispatchers.IO) {
        try {
            parseFraudFlags(callRpc("detect_suspicious_activity", JSONObject()))
        } catch (e: Throwable) {
            Log.e(TAG, "detectSuspiciousActivity failed", e)
            emptyList()
        }
    }

    private fun parseFraudFlags(body: String): List<WalletFraudFlag> {
        val arr = JSONArray(body)
        return List(arr.length()) { i -> arr.getJSONObject(i).let {
            WalletFraudFlag(
                id = it.optString("id", ""),
                userHandle = it.optString("user_handle", ""),
                flagType = it.optString("flag_type", ""),
                severity = it.optString("severity", "LOW"),
                description = it.optString("description", ""),
                resolved = it.optBoolean("resolved", false),
                resolvedBy = it.optString("resolved_by", ""),
                resolvedAt = it.optString("resolved_at", "").takeIf { it.isNotBlank() }?.let(::parseIsoTime),
                createdAt = parseIsoTime(it.optString("created_at", ""))
            )
        }}
    }

    /** Super Admin: resolve a fraud flag after manual review. */
    suspend fun adminResolveFraudFlag(flagId: String, reason: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val o = JSONObject(callRpc("admin_resolve_fraud_flag", JSONObject().apply {
                put("p_flag_id", flagId)
                put("p_reason", reason)
            }))
            if (o.optBoolean("ok", false)) Result.success("Flag resolved")
            else Result.failure(Exception(o.optString("message", "Could not resolve flag")))
        } catch (e: Throwable) {
            Log.e(TAG, "adminResolveFraudFlag failed", e)
            Result.failure(Exception(friendlyRpcError(e.message ?: "Could not resolve flag")))
        }
    }
}
