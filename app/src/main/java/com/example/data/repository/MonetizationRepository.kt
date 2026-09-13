package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

class MonetizationRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("flareofficial_monetization_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    // -------------------------------------------------------------
    // STATE FLOWS
    // -------------------------------------------------------------
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<MonetizationSettings> = _settings.asStateFlow()

    private val _userProfile = MutableStateFlow(loadUserProfile())
    val userProfile: StateFlow<UserMonetizationProfile> = _userProfile.asStateFlow()

    private val _applications = MutableStateFlow(loadApplications())
    val applications: StateFlow<List<MonetizationApplication>> = _applications.asStateFlow()

    private val _wallet = MutableStateFlow(loadWallet())
    val wallet: StateFlow<EarningsWallet> = _wallet.asStateFlow()

    private val _transactions = MutableStateFlow(loadTransactions())
    val transactions: StateFlow<List<MonetizationTransaction>> = _transactions.asStateFlow()

    // Phase 2: Revenue Attribution State Flows
    private val _revenuePeriods = MutableStateFlow(loadRevenuePeriods())
    val revenuePeriods: StateFlow<List<RevenuePeriod>> = _revenuePeriods.asStateFlow()

    private val _adMobReports = MutableStateFlow(loadAdMobReports())
    val adMobReports: StateFlow<List<AdMobRevenueReport>> = _adMobReports.asStateFlow()

    private val _adUnitMappings = MutableStateFlow(loadAdUnitMappings())
    val adUnitMappings: StateFlow<List<AdMobAdUnitMapping>> = _adUnitMappings.asStateFlow()

    private val _contentImpressions = MutableStateFlow(loadContentImpressions())
    val contentImpressions: StateFlow<List<ContentAdImpression>> = _contentImpressions.asStateFlow()

    private val _contentEarnings = MutableStateFlow(loadContentEarnings())
    val contentEarnings: StateFlow<List<ContentEarning>> = _contentEarnings.asStateFlow()

    private val _revenueAdjustments = MutableStateFlow(loadRevenueAdjustments())
    val revenueAdjustments: StateFlow<List<RevenueAdjustment>> = _revenueAdjustments.asStateFlow()

    private val _attributionDiagnostics = MutableStateFlow(loadAttributionDiagnostics())
    val attributionDiagnostics: StateFlow<AttributionDiagnostics> = _attributionDiagnostics.asStateFlow()

    private val _boostCampaigns = MutableStateFlow(loadBoostCampaigns())
    val boostCampaigns: StateFlow<List<PostBoostCampaign>> = _boostCampaigns.asStateFlow()

    init {
        // (No remote Firestore client is bundled in this build.)
    }

    // -------------------------------------------------------------
    // 1. SETTINGS MANAGEMENT
    // -------------------------------------------------------------
    private fun loadSettings(): MonetizationSettings {
        return MonetizationSettings(
            enableMonetization = prefs.getBoolean("cfg_enable_monetization", true),
            enableMonetizationApplication = prefs.getBoolean("cfg_enable_app", true),
            enableContentMonetization = prefs.getBoolean("cfg_enable_content", true),
            enablePostMonetization = prefs.getBoolean("cfg_enable_post", true),
            enableVideoMonetization = prefs.getBoolean("cfg_enable_video", true),
            enableReelsMonetization = prefs.getBoolean("cfg_enable_reels", true),
            minimumFollowers = prefs.getInt("cfg_min_followers", 1000),
            minimumViews = prefs.getInt("cfg_min_views", 10000),
            enableEarningsWallet = prefs.getBoolean("cfg_enable_wallet", true),
            enableAddFund = prefs.getBoolean("cfg_enable_add_fund", true),
            enableWithdraw = prefs.getBoolean("cfg_enable_withdraw", true),
            minimumAddFund = prefs.getFloat("cfg_min_add_fund", 5.0f).toDouble(),
            maximumAddFund = prefs.getFloat("cfg_max_add_fund", 1000.0f).toDouble(),
            minimumWithdrawal = prefs.getFloat("cfg_min_withdraw", 10.0f).toDouble(),
            maximumWithdrawal = prefs.getFloat("cfg_max_withdraw", 5000.0f).toDouble(),
            creatorRevenueShare = prefs.getFloat("cfg_creator_share", 50.0f).toDouble(),
            platformRevenueShare = prefs.getFloat("cfg_platform_share", 50.0f).toDouble(),
            postRevenuePoolShare = prefs.getFloat("cfg_post_pool_share", 30.0f).toDouble(),
            videoRevenuePoolShare = prefs.getFloat("cfg_video_pool_share", 40.0f).toDouble(),
            reelsRevenuePoolShare = prefs.getFloat("cfg_reels_pool_share", 30.0f).toDouble(),
            allowEstimatedEarnings = prefs.getBoolean("cfg_allow_est_earn", true),
            autoFinalizeRevenue = prefs.getBoolean("cfg_auto_finalize", false),
            revenueCurrency = prefs.getString("cfg_rev_currency", "USD") ?: "USD",
            updatedAt = prefs.getLong("cfg_updated_at", System.currentTimeMillis()),
            updatedBy = prefs.getString("cfg_updated_by", "admin") ?: "admin"
        )
    }

    fun updateSettings(newSettings: MonetizationSettings, onResult: ((Boolean) -> Unit)? = null) {
        // Validate Revenue Shares: Creator + Platform = 100%
        val totalRevenueShare = BigDecimal(newSettings.creatorRevenueShare.toString())
            .add(BigDecimal(newSettings.platformRevenueShare.toString()))
            .setScale(2, RoundingMode.HALF_UP).toDouble()

        if (totalRevenueShare != 100.0) {
            Log.w("MonetizationRepo", "Validation error: Creator ($newSettings.creatorRevenueShare%) + Platform ($newSettings.platformRevenueShare%) must equal 100%")
            onResult?.invoke(false)
            return
        }

        // Validate Content Pools: Post + Video + Reels = 100%
        val totalPoolShare = BigDecimal(newSettings.postRevenuePoolShare.toString())
            .add(BigDecimal(newSettings.videoRevenuePoolShare.toString()))
            .add(BigDecimal(newSettings.reelsRevenuePoolShare.toString()))
            .setScale(2, RoundingMode.HALF_UP).toDouble()

        if (totalPoolShare != 100.0) {
            Log.w("MonetizationRepo", "Validation error: Post + Video + Reels pools must equal 100%")
            onResult?.invoke(false)
            return
        }

        prefs.edit()
            .putBoolean("cfg_enable_monetization", newSettings.enableMonetization)
            .putBoolean("cfg_enable_app", newSettings.enableMonetizationApplication)
            .putBoolean("cfg_enable_content", newSettings.enableContentMonetization)
            .putBoolean("cfg_enable_post", newSettings.enablePostMonetization)
            .putBoolean("cfg_enable_video", newSettings.enableVideoMonetization)
            .putBoolean("cfg_enable_reels", newSettings.enableReelsMonetization)
            .putInt("cfg_min_followers", newSettings.minimumFollowers)
            .putInt("cfg_min_views", newSettings.minimumViews)
            .putBoolean("cfg_enable_wallet", newSettings.enableEarningsWallet)
            .putBoolean("cfg_enable_add_fund", newSettings.enableAddFund)
            .putBoolean("cfg_enable_withdraw", newSettings.enableWithdraw)
            .putFloat("cfg_min_add_fund", newSettings.minimumAddFund.toFloat())
            .putFloat("cfg_max_add_fund", newSettings.maximumAddFund.toFloat())
            .putFloat("cfg_min_withdraw", newSettings.minimumWithdrawal.toFloat())
            .putFloat("cfg_max_withdraw", newSettings.maximumWithdrawal.toFloat())
            .putFloat("cfg_creator_share", newSettings.creatorRevenueShare.toFloat())
            .putFloat("cfg_platform_share", newSettings.platformRevenueShare.toFloat())
            .putFloat("cfg_post_pool_share", newSettings.postRevenuePoolShare.toFloat())
            .putFloat("cfg_video_pool_share", newSettings.videoRevenuePoolShare.toFloat())
            .putFloat("cfg_reels_pool_share", newSettings.reelsRevenuePoolShare.toFloat())
            .putBoolean("cfg_allow_est_earn", newSettings.allowEstimatedEarnings)
            .putBoolean("cfg_auto_finalize", newSettings.autoFinalizeRevenue)
            .putString("cfg_rev_currency", newSettings.revenueCurrency)
            .putLong("cfg_updated_at", System.currentTimeMillis())
            .putString("cfg_updated_by", newSettings.updatedBy)
            .apply()

        _settings.value = newSettings.copy(updatedAt = System.currentTimeMillis())

        // Settings updated locally
        onResult?.invoke(true)
    }

    // -------------------------------------------------------------
    // 2. USER PROFILE & APPLICATIONS
    // -------------------------------------------------------------
    private fun loadUserProfile(): UserMonetizationProfile {
        return UserMonetizationProfile(
            userId = prefs.getString("prof_user_id", "") ?: "",
            userHandle = prefs.getString("prof_handle", "") ?: "",
            userName = prefs.getString("prof_name", "") ?: "",
            monetizationStatus = prefs.getString("prof_monetization_status", "NOT_MONETIZED") ?: "NOT_MONETIZED",
            applicationStatus = prefs.getString("prof_app_status", "NOT_APPLIED") ?: "NOT_APPLIED",
            approvedAt = if (prefs.contains("prof_approved_at")) prefs.getLong("prof_approved_at", 0L) else null,
            rejectedAt = if (prefs.contains("prof_rejected_at")) prefs.getLong("prof_rejected_at", 0L) else null,
            rejectionReason = prefs.getString("prof_rejection_reason", "") ?: ""
        )
    }

    private fun saveUserProfile(profile: UserMonetizationProfile) {
        prefs.edit()
            .putString("prof_user_id", profile.userId)
            .putString("prof_handle", profile.userHandle)
            .putString("prof_name", profile.userName)
            .putString("prof_monetization_status", profile.monetizationStatus)
            .putString("prof_app_status", profile.applicationStatus)
            .putString("prof_rejection_reason", profile.rejectionReason)
            .apply()

        if (profile.approvedAt != null) {
            prefs.edit().putLong("prof_approved_at", profile.approvedAt).apply()
        }
        if (profile.rejectedAt != null) {
            prefs.edit().putLong("prof_rejected_at", profile.rejectedAt).apply()
        }

        _userProfile.value = profile
    }

    private fun loadApplications(): List<MonetizationApplication> {
        val raw = prefs.getString("monetization_applications_json", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<MonetizationApplication>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    MonetizationApplication(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        userId = obj.optString("userId", ""),
                        userHandle = obj.optString("userHandle", ""),
                        userName = obj.optString("userName", ""),
                        userAvatarType = obj.optString("userAvatarType", "default"),
                        followerCountAtApplication = obj.optInt("followerCountAtApplication", 0),
                        viewCountAtApplication = obj.optInt("viewCountAtApplication", 0),
                        status = obj.optString("status", "PENDING"),
                        rejectionReason = obj.optString("rejectionReason", ""),
                        reviewedBy = if (obj.has("reviewedBy")) obj.optString("reviewedBy") else null,
                        reviewedAt = if (obj.has("reviewedAt")) obj.optLong("reviewedAt") else null,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveApplications(list: List<MonetizationApplication>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { app ->
                val obj = JSONObject()
                obj.put("id", app.id)
                obj.put("userId", app.userId)
                obj.put("userHandle", app.userHandle)
                obj.put("userName", app.userName)
                obj.put("userAvatarType", app.userAvatarType)
                obj.put("followerCountAtApplication", app.followerCountAtApplication)
                obj.put("viewCountAtApplication", app.viewCountAtApplication)
                obj.put("status", app.status)
                obj.put("rejectionReason", app.rejectionReason)
                if (app.reviewedBy != null) obj.put("reviewedBy", app.reviewedBy)
                if (app.reviewedAt != null) obj.put("reviewedAt", app.reviewedAt)
                obj.put("createdAt", app.createdAt)
                jsonArray.put(obj)
            }
            prefs.edit().putString("monetization_applications_json", jsonArray.toString()).apply()
            _applications.value = list
        } catch (e: Exception) {
            Log.e("MonetizationRepo", "Error saving applications: ${e.message}")
        }
    }

    fun submitApplication(
        userId: String,
        userHandle: String,
        userName: String,
        userAvatarType: String,
        currentFollowers: Int,
        currentViews: Int,
        onResult: (Boolean, String) -> Unit
    ) {
        val currentSettings = _settings.value
        if (!currentSettings.enableMonetization) {
            onResult(false, "Monetization is currently disabled by administrator.")
            return
        }

        if (!currentSettings.enableMonetizationApplication) {
            onResult(false, "Monetization applications are temporarily closed.")
            return
        }

        val currentProfile = _userProfile.value
        if (currentProfile.applicationStatus == "PENDING") {
            onResult(false, "You already have a pending application under review.")
            return
        }

        if (currentProfile.monetizationStatus == "APPROVED") {
            onResult(false, "Your account is already Monetization Approved!")
            return
        }

        // Strict criteria check ONLY followers & views
        if (currentFollowers < currentSettings.minimumFollowers) {
            onResult(false, "Minimum ${currentSettings.minimumFollowers} followers required to apply.")
            return
        }

        if (currentViews < currentSettings.minimumViews) {
            onResult(false, "Minimum ${currentSettings.minimumViews} views required to apply.")
            return
        }

        val newApp = MonetizationApplication(
            id = "APP-${System.currentTimeMillis().toString().takeLast(6)}",
            userId = userId,
            userHandle = userHandle,
            userName = userName,
            userAvatarType = userAvatarType,
            followerCountAtApplication = currentFollowers,
            viewCountAtApplication = currentViews,
            status = "PENDING",
            createdAt = System.currentTimeMillis()
        )

        val updatedList = listOf(newApp) + _applications.value.filter { it.userId != userId || it.status != "PENDING" }
        saveApplications(updatedList)

        val updatedProfile = currentProfile.copy(
            userId = userId,
            userHandle = userHandle,
            userName = userName,
            applicationStatus = "PENDING",
            monetizationStatus = "PENDING",
            rejectionReason = "",
            updatedAt = System.currentTimeMillis()
        )
        saveUserProfile(updatedProfile)

        onResult(true, "Application submitted successfully! Our team will review your account.")
    }

    fun approveApplication(
        applicationId: String,
        adminHandle: String = "admin",
        onResult: (Boolean, String) -> Unit
    ) {
        val app = _applications.value.find { it.id == applicationId }
        if (app == null) {
            onResult(false, "Application not found.")
            return
        }

        val updatedApp = app.copy(
            status = "APPROVED",
            reviewedBy = adminHandle,
            reviewedAt = System.currentTimeMillis(),
            rejectionReason = ""
        )

        val updatedList = _applications.value.map { if (it.id == applicationId) updatedApp else it }
        saveApplications(updatedList)

        if (_userProfile.value.userHandle.equals(app.userHandle, ignoreCase = true) || _userProfile.value.userId == app.userId) {
            val updatedProfile = _userProfile.value.copy(
                monetizationStatus = "APPROVED",
                applicationStatus = "APPROVED",
                approvedAt = System.currentTimeMillis(),
                rejectionReason = "",
                updatedAt = System.currentTimeMillis()
            )
            saveUserProfile(updatedProfile)
        }

        onResult(true, "Monetization application approved for @${app.userHandle}!")
    }

    fun rejectApplication(
        applicationId: String,
        reason: String,
        adminHandle: String = "admin",
        onResult: (Boolean, String) -> Unit
    ) {
        val app = _applications.value.find { it.id == applicationId }
        if (app == null) {
            onResult(false, "Application not found.")
            return
        }

        val updatedApp = app.copy(
            status = "REJECTED",
            rejectionReason = reason.ifBlank { "Eligibility or guidelines criteria not met." },
            reviewedBy = adminHandle,
            reviewedAt = System.currentTimeMillis()
        )

        val updatedList = _applications.value.map { if (it.id == applicationId) updatedApp else it }
        saveApplications(updatedList)

        if (_userProfile.value.userHandle.equals(app.userHandle, ignoreCase = true) || _userProfile.value.userId == app.userId) {
            val updatedProfile = _userProfile.value.copy(
                monetizationStatus = "NOT_MONETIZED",
                applicationStatus = "REJECTED",
                rejectedAt = System.currentTimeMillis(),
                rejectionReason = updatedApp.rejectionReason,
                updatedAt = System.currentTimeMillis()
            )
            saveUserProfile(updatedProfile)
        }

        onResult(true, "Application rejected for @${app.userHandle}.")
    }

    // -------------------------------------------------------------
    // 3. EARNINGS WALLET & TRANSACTIONS
    // -------------------------------------------------------------
    private fun loadWallet(): EarningsWallet {
        return EarningsWallet(
            userId = prefs.getString("wallet_uid", "") ?: "",
            availableBalance = prefs.getFloat("wallet_avail_bal", 0.00f).toDouble(),
            lifetimeEarnings = prefs.getFloat("wallet_lifetime_earn", 0.00f).toDouble(),
            totalAddedFunds = prefs.getFloat("wallet_total_added", 0.00f).toDouble(),
            totalWithdrawn = prefs.getFloat("wallet_total_withdrawn", 0.00f).toDouble(),
            updatedAt = prefs.getLong("wallet_updated_at", System.currentTimeMillis())
        )
    }

    private fun saveWallet(wallet: EarningsWallet) {
        prefs.edit()
            .putString("wallet_uid", wallet.userId)
            .putFloat("wallet_avail_bal", wallet.availableBalance.toFloat())
            .putFloat("wallet_lifetime_earn", wallet.lifetimeEarnings.toFloat())
            .putFloat("wallet_total_added", wallet.totalAddedFunds.toFloat())
            .putFloat("wallet_total_withdrawn", wallet.totalWithdrawn.toFloat())
            .putLong("wallet_updated_at", wallet.updatedAt)
            .apply()

        _wallet.value = wallet
    }

    private fun loadTransactions(): List<MonetizationTransaction> {
        val raw = prefs.getString("monetization_txns_json", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<MonetizationTransaction>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    MonetizationTransaction(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        userId = obj.optString("userId", ""),
                        userHandle = obj.optString("userHandle", ""),
                        type = obj.optString("type", "CREATOR_EARNING"),
                        amount = obj.optDouble("amount", 0.0),
                        status = obj.optString("status", "COMPLETED"),
                        referenceId = obj.optString("referenceId", ""),
                        description = obj.optString("description", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveTransactions(list: List<MonetizationTransaction>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { txn ->
                val obj = JSONObject()
                obj.put("id", txn.id)
                obj.put("userId", txn.userId)
                obj.put("userHandle", txn.userHandle)
                obj.put("type", txn.type)
                obj.put("amount", txn.amount)
                obj.put("status", txn.status)
                obj.put("referenceId", txn.referenceId)
                obj.put("description", txn.description)
                obj.put("createdAt", txn.createdAt)
                jsonArray.put(obj)
            }
            prefs.edit().putString("monetization_txns_json", jsonArray.toString()).apply()
            _transactions.value = list
        } catch (e: Exception) {
            Log.e("MonetizationRepo", "Error saving transactions: ${e.message}")
        }
    }

    fun addFunds(
        amount: Double,
        userHandle: String,
        paymentMethod: String = "bKash Merchant",
        trxId: String = "DEP-${System.currentTimeMillis().toString().takeLast(6)}",
        onResult: (Boolean, String) -> Unit
    ) {
        val currentSettings = _settings.value
        if (!currentSettings.enableEarningsWallet || !currentSettings.enableAddFund) {
            onResult(false, "Add Fund feature is currently disabled.")
            return
        }

        if (amount < 0.5) {
            onResult(false, "Minimum deposit amount is $0.50 (৳60)")
            return
        }

        if (amount > currentSettings.maximumAddFund) {
            onResult(false, "Maximum deposit amount is $${String.format(Locale.US, "%.2f", currentSettings.maximumAddFund)}")
            return
        }

        val currentWallet = _wallet.value
        val updatedWallet = currentWallet.copy(
            availableBalance = currentWallet.availableBalance + amount,
            totalAddedFunds = currentWallet.totalAddedFunds + amount,
            // CRITICAL: Lifetime earnings is NOT incremented by deposits!
            updatedAt = System.currentTimeMillis()
        )
        saveWallet(updatedWallet)

        val newTxn = MonetizationTransaction(
            id = "DEP-${System.currentTimeMillis().toString().takeLast(6)}",
            userId = currentWallet.userId,
            userHandle = userHandle,
            type = "ADD_FUND",
            amount = amount,
            status = "COMPLETED",
            referenceId = trxId,
            description = "Deposit via $paymentMethod ($trxId)",
            createdAt = System.currentTimeMillis()
        )
        saveTransactions(listOf(newTxn) + _transactions.value)

        onResult(true, "Successfully added $${String.format(Locale.US, "%.2f", amount)} via $paymentMethod!")
    }

    fun addFunds(amount: Double, userHandle: String, onResult: (Boolean, String) -> Unit) {
        addFunds(amount, userHandle, "bKash Merchant", "DEP-${System.currentTimeMillis().toString().takeLast(6)}", onResult)
    }

    fun createPostBoostCampaign(
        campaign: PostBoostCampaign,
        payWithWallet: Boolean,
        gatewayMethod: String?,
        userHandle: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (payWithWallet) {
            val currentWallet = _wallet.value
            if (currentWallet.availableBalance < campaign.budgetUsd) {
                onResult(false, "Insufficient wallet balance. Please add funds or choose direct mobile payment.")
                return
            }
            val updatedWallet = currentWallet.copy(
                availableBalance = currentWallet.availableBalance - campaign.budgetUsd,
                updatedAt = System.currentTimeMillis()
            )
            saveWallet(updatedWallet)

            val newTxn = MonetizationTransaction(
                id = "BOOST-${System.currentTimeMillis().toString().takeLast(6)}",
                userId = currentWallet.userId,
                userHandle = userHandle,
                type = "ADJUSTMENT",
                amount = campaign.budgetUsd,
                status = "COMPLETED",
                referenceId = campaign.id,
                description = "Post Boost #${campaign.postId} (${campaign.durationDays}d Campaign)",
                createdAt = System.currentTimeMillis()
            )
            saveTransactions(listOf(newTxn) + _transactions.value)
        }

        val updatedList = listOf(campaign) + _boostCampaigns.value
        saveBoostCampaigns(updatedList)
        onResult(true, "Campaign launched successfully! Your post is now sponsored.")
    }

    private fun loadBoostCampaigns(): List<PostBoostCampaign> {
        val json = prefs.getString("flareofficial_boost_campaigns", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<PostBoostCampaign>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PostBoostCampaign(
                        id = obj.optString("id", ""),
                        postId = obj.optLong("postId", 0L),
                        postTitle = obj.optString("postTitle", ""),
                        postImageRes = obj.optString("postImageRes", ""),
                        creatorHandle = obj.optString("creatorHandle", ""),
                        budgetBdt = obj.optDouble("budgetBdt", 0.0),
                        budgetUsd = obj.optDouble("budgetUsd", 0.0),
                        durationDays = obj.optInt("durationDays", 0),
                        targetAudience = obj.optString("targetAudience", ""),
                        goal = obj.optString("goal", ""),
                        status = obj.optString("status", ""),
                        impressionsDelivered = obj.optInt("impressionsDelivered", 0),
                        estimatedReach = obj.optString("estimatedReach", ""),
                        clicksDelivered = obj.optInt("clicksDelivered", 0),
                        paymentGateway = obj.optString("paymentGateway", ""),
                        transactionId = obj.optString("transactionId", ""),
                        createdAt = obj.optLong("createdAt", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveBoostCampaigns(list: List<PostBoostCampaign>) {
        try {
            val array = JSONArray()
            list.forEach { c ->
                val obj = JSONObject().apply {
                    put("id", c.id)
                    put("postId", c.postId)
                    put("postTitle", c.postTitle)
                    put("postImageRes", c.postImageRes)
                    put("creatorHandle", c.creatorHandle)
                    put("budgetBdt", c.budgetBdt)
                    put("budgetUsd", c.budgetUsd)
                    put("durationDays", c.durationDays)
                    put("targetAudience", c.targetAudience)
                    put("goal", c.goal)
                    put("status", c.status)
                    put("impressionsDelivered", c.impressionsDelivered)
                    put("estimatedReach", c.estimatedReach)
                    put("clicksDelivered", c.clicksDelivered)
                    put("paymentGateway", c.paymentGateway)
                    put("transactionId", c.transactionId)
                    put("createdAt", c.createdAt)
                }
                array.put(obj)
            }
            prefs.edit().putString("flareofficial_boost_campaigns", array.toString()).apply()
            _boostCampaigns.value = list
        } catch (e: Exception) {
            Log.e("MonetizationRepo", "Error saving boost campaigns: ${e.message}")
        }
    }

    fun requestWithdrawal(amount: Double, paymentMethod: String, accountDetails: String, userHandle: String, onResult: (Boolean, String) -> Unit) {
        val currentSettings = _settings.value
        if (!currentSettings.enableEarningsWallet || !currentSettings.enableWithdraw) {
            onResult(false, "Withdrawals are currently paused by administrator.")
            return
        }

        if (amount < currentSettings.minimumWithdrawal) {
            onResult(false, "Minimum withdrawal is $${String.format(Locale.US, "%.2f", currentSettings.minimumWithdrawal)}")
            return
        }

        if (amount > currentSettings.maximumWithdrawal) {
            onResult(false, "Maximum withdrawal is $${String.format(Locale.US, "%.2f", currentSettings.maximumWithdrawal)}")
            return
        }

        val currentWallet = _wallet.value
        if (currentWallet.availableBalance < amount) {
            onResult(false, "Insufficient balance. Available: $${String.format(Locale.US, "%.2f", currentWallet.availableBalance)}")
            return
        }

        val updatedWallet = currentWallet.copy(
            availableBalance = currentWallet.availableBalance - amount,
            totalWithdrawn = currentWallet.totalWithdrawn + amount,
            // CRITICAL: Lifetime earnings is NOT decremented by withdrawals!
            updatedAt = System.currentTimeMillis()
        )
        saveWallet(updatedWallet)

        val newTxn = MonetizationTransaction(
            id = "WTH-${System.currentTimeMillis().toString().takeLast(6)}",
            userId = currentWallet.userId,
            userHandle = userHandle,
            type = "WITHDRAWAL",
            amount = amount,
            status = "PENDING",
            referenceId = "WTH-$paymentMethod-${System.currentTimeMillis().toString().takeLast(4)}",
            description = "Withdrawal request to $paymentMethod ($accountDetails)",
            createdAt = System.currentTimeMillis()
        )
        saveTransactions(listOf(newTxn) + _transactions.value)

        onResult(true, "Withdrawal request of $${String.format(Locale.US, "%.2f", amount)} submitted successfully!")
    }

    fun pauseBoostCampaign(campaignId: String, onResult: (Boolean, String) -> Unit) {
        val current = _boostCampaigns.value
        val index = current.indexOfFirst { it.id == campaignId }
        if (index == -1) {
            onResult(false, "Campaign not found.")
            return
        }
        val updated = current.toMutableList()
        updated[index] = updated[index].copy(status = "PAUSED")
        saveBoostCampaigns(updated)
        onResult(true, "Boost campaign paused.")
    }

    fun resumeBoostCampaign(campaignId: String, onResult: (Boolean, String) -> Unit) {
        val current = _boostCampaigns.value
        val index = current.indexOfFirst { it.id == campaignId }
        if (index == -1) {
            onResult(false, "Campaign not found.")
            return
        }
        val updated = current.toMutableList()
        updated[index] = updated[index].copy(status = "ACTIVE")
        saveBoostCampaigns(updated)
        onResult(true, "Boost campaign resumed!")
    }

    fun cancelBoostCampaign(campaignId: String, onResult: (Boolean, String) -> Unit) {
        val current = _boostCampaigns.value
        val index = current.indexOfFirst { it.id == campaignId }
        if (index == -1) {
            onResult(false, "Campaign not found.")
            return
        }
        val target = current[index]
        val updated = current.toMutableList()
        updated[index] = updated[index].copy(status = "CANCELLED")
        saveBoostCampaigns(updated)
        onResult(true, "Campaign cancelled.")
    }

    fun approveWithdrawal(transactionId: String, payoutRef: String, onResult: (Boolean, String) -> Unit) {
        val currentTxns = _transactions.value
        val index = currentTxns.indexOfFirst { it.id == transactionId && it.type == "WITHDRAWAL" }
        if (index == -1) {
            onResult(false, "Withdrawal transaction not found.")
            return
        }
        val updatedTxns = currentTxns.toMutableList()
        val target = updatedTxns[index]
        updatedTxns[index] = target.copy(
            status = "COMPLETED",
            referenceId = if (payoutRef.isNotBlank()) payoutRef else target.referenceId,
            description = target.description + " • Approved by Admin"
        )
        saveTransactions(updatedTxns)
        onResult(true, "Withdrawal approved and marked as COMPLETED!")
    }

    fun rejectWithdrawal(transactionId: String, reason: String, onResult: (Boolean, String) -> Unit) {
        val currentTxns = _transactions.value
        val index = currentTxns.indexOfFirst { it.id == transactionId && it.type == "WITHDRAWAL" }
        if (index == -1) {
            onResult(false, "Withdrawal transaction not found.")
            return
        }
        val target = currentTxns[index]
        val updatedTxns = currentTxns.toMutableList()
        updatedTxns[index] = target.copy(
            status = "REJECTED",
            description = target.description + " • Rejected: $reason"
        )
        saveTransactions(updatedTxns)

        // Refund the amount back to the user's available balance
        val currentWallet = _wallet.value
        val updatedWallet = currentWallet.copy(
            availableBalance = currentWallet.availableBalance + target.amount,
            totalWithdrawn = maxOf(0.0, currentWallet.totalWithdrawn - target.amount),
            updatedAt = System.currentTimeMillis()
        )
        saveWallet(updatedWallet)

        // Create a refund transaction record
        val refundTxn = MonetizationTransaction(
            id = "REF-${System.currentTimeMillis().toString().takeLast(6)}",
            userId = target.userId,
            userHandle = target.userHandle,
            type = "REFUND",
            amount = target.amount,
            status = "COMPLETED",
            referenceId = "REF-${target.id}",
            description = "Refund for rejected withdrawal #${target.id} ($reason)",
            createdAt = System.currentTimeMillis()
        )
        saveTransactions(listOf(refundTxn) + _transactions.value)

        onResult(true, "Withdrawal rejected and $${String.format(Locale.US, "%.2f", target.amount)} refunded to user wallet.")
    }

    // -------------------------------------------------------------
    // 4. PHASE 2: ADMOB REVENUE & PERIOD ATTRIBUTION ENGINE
    // -------------------------------------------------------------
    private fun loadRevenuePeriods(): List<RevenuePeriod> {
        val raw = prefs.getString("revenue_periods_json", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<RevenuePeriod>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    RevenuePeriod(
                        id = obj.optString("id"),
                        name = obj.optString("name", "Revenue Period"),
                        startDate = obj.optString("startDate", "2026-08-01"),
                        endDate = obj.optString("endDate", "2026-08-31"),
                        status = obj.optString("status", "OPEN"),
                        totalAdMobRevenue = obj.optDouble("totalAdMobRevenue", 0.0),
                        totalCreatorPool = obj.optDouble("totalCreatorPool", 0.0),
                        totalPlatformRevenue = obj.optDouble("totalPlatformRevenue", 0.0),
                        postPool = obj.optDouble("postPool", 0.0),
                        videoPool = obj.optDouble("videoPool", 0.0),
                        reelsPool = obj.optDouble("reelsPool", 0.0),
                        totalEligibleImpressions = obj.optLong("totalEligibleImpressions", 0L),
                        postImpressions = obj.optLong("postImpressions", 0L),
                        videoImpressions = obj.optLong("videoImpressions", 0L),
                        reelsImpressions = obj.optLong("reelsImpressions", 0L),
                        currency = obj.optString("currency", "USD"),
                        importedAt = if (obj.has("importedAt")) obj.optLong("importedAt") else null,
                        finalizedAt = if (obj.has("finalizedAt")) obj.optLong("finalizedAt") else null,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRevenuePeriods(list: List<RevenuePeriod>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { p ->
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("startDate", p.startDate)
                obj.put("endDate", p.endDate)
                obj.put("status", p.status)
                obj.put("totalAdMobRevenue", p.totalAdMobRevenue)
                obj.put("totalCreatorPool", p.totalCreatorPool)
                obj.put("totalPlatformRevenue", p.totalPlatformRevenue)
                obj.put("postPool", p.postPool)
                obj.put("videoPool", p.videoPool)
                obj.put("reelsPool", p.reelsPool)
                obj.put("totalEligibleImpressions", p.totalEligibleImpressions)
                obj.put("postImpressions", p.postImpressions)
                obj.put("videoImpressions", p.videoImpressions)
                obj.put("reelsImpressions", p.reelsImpressions)
                obj.put("currency", p.currency)
                if (p.importedAt != null) obj.put("importedAt", p.importedAt)
                if (p.finalizedAt != null) obj.put("finalizedAt", p.finalizedAt)
                obj.put("createdAt", p.createdAt)
                jsonArray.put(obj)
            }
            prefs.edit().putString("revenue_periods_json", jsonArray.toString()).apply()
            _revenuePeriods.value = list
        } catch (e: Exception) {
            Log.e("MonetizationRepo", "Error saving revenue periods: ${e.message}")
        }
    }

    private fun loadAdMobReports(): List<AdMobRevenueReport> {
        val raw = prefs.getString("admob_reports_json", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<AdMobRevenueReport>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    AdMobRevenueReport(
                        id = obj.optString("id"),
                        revenuePeriodId = obj.optString("revenuePeriodId"),
                        reportDate = obj.optString("reportDate"),
                        appId = obj.optString("appId", "ca-app-pub-flareofficial-prod"),
                        adUnitId = obj.optString("adUnitId"),
                        adFormat = obj.optString("adFormat", "NATIVE"),
                        countryCode = obj.optString("countryCode", "GLOBAL"),
                        impressions = obj.optLong("impressions", 0L),
                        estimatedEarnings = obj.optDouble("estimatedEarnings", 0.0),
                        currency = obj.optString("currency", "USD"),
                        source = obj.optString("source", "ADMOB_REPORTING_API"),
                        importedAt = obj.optLong("importedAt", System.currentTimeMillis()),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAdMobReports(list: List<AdMobRevenueReport>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { r ->
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("revenuePeriodId", r.revenuePeriodId)
                obj.put("reportDate", r.reportDate)
                obj.put("appId", r.appId)
                obj.put("adUnitId", r.adUnitId)
                obj.put("adFormat", r.adFormat)
                obj.put("countryCode", r.countryCode)
                obj.put("impressions", r.impressions)
                obj.put("estimatedEarnings", r.estimatedEarnings)
                obj.put("currency", r.currency)
                obj.put("source", r.source)
                obj.put("importedAt", r.importedAt)
                obj.put("createdAt", r.createdAt)
                jsonArray.put(obj)
            }
            prefs.edit().putString("admob_reports_json", jsonArray.toString()).apply()
            _adMobReports.value = list
        } catch (e: Exception) {
            Log.e("MonetizationRepo", "Error saving AdMob reports: ${e.message}")
        }
    }

    private fun loadAdUnitMappings(): List<AdMobAdUnitMapping> {
        val raw = prefs.getString("admob_mappings_json", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<AdMobAdUnitMapping>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    AdMobAdUnitMapping(
                        id = obj.optString("id"),
                        adUnitId = obj.optString("adUnitId"),
                        placement = obj.optString("placement"),
                        contentType = obj.optString("contentType", "POST"),
                        enabled = obj.optBoolean("enabled", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAdUnitMappings(list: List<AdMobAdUnitMapping>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { m ->
                val obj = JSONObject()
                obj.put("id", m.id)
                obj.put("adUnitId", m.adUnitId)
                obj.put("placement", m.placement)
                obj.put("contentType", m.contentType)
                obj.put("enabled", m.enabled)
                obj.put("createdAt", m.createdAt)
                obj.put("updatedAt", m.updatedAt)
                jsonArray.put(obj)
            }
            prefs.edit().putString("admob_mappings_json", jsonArray.toString()).apply()
            _adUnitMappings.value = list
        } catch (e: Exception) {
            Log.e("MonetizationRepo", "Error saving Ad Unit mappings: ${e.message}")
        }
    }

    private fun loadContentImpressions(): List<ContentAdImpression> {
        val raw = prefs.getString("content_impressions_json", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<ContentAdImpression>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ContentAdImpression(
                        id = obj.optString("id"),
                        contentId = obj.optString("contentId"),
                        creatorId = obj.optString("creatorId"),
                        contentType = obj.optString("contentType", "POST"),
                        adUnitId = obj.optString("adUnitId"),
                        placement = obj.optString("placement"),
                        sessionId = obj.optString("sessionId"),
                        impressionReference = obj.optString("impressionReference"),
                        occurredAt = obj.optLong("occurredAt", System.currentTimeMillis()),
                        dateBucket = obj.optString("dateBucket", "2026-08"),
                        countryCode = obj.optString("countryCode", "GLOBAL"),
                        isValid = obj.optBoolean("isValid", true),
                        rejectionReason = obj.optString("rejectionReason", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveContentImpressions(list: List<ContentAdImpression>) {
        try {
            // Keep last 1000 impressions in client memory/cache
            val trimmed = list.take(1000)
            val jsonArray = JSONArray()
            trimmed.forEach { imp ->
                val obj = JSONObject()
                obj.put("id", imp.id)
                obj.put("contentId", imp.contentId)
                obj.put("creatorId", imp.creatorId)
                obj.put("contentType", imp.contentType)
                obj.put("adUnitId", imp.adUnitId)
                obj.put("placement", imp.placement)
                obj.put("sessionId", imp.sessionId)
                obj.put("impressionReference", imp.impressionReference)
                obj.put("occurredAt", imp.occurredAt)
                obj.put("dateBucket", imp.dateBucket)
                obj.put("countryCode", imp.countryCode)
                obj.put("isValid", imp.isValid)
                obj.put("rejectionReason", imp.rejectionReason)
                obj.put("createdAt", imp.createdAt)
                jsonArray.put(obj)
            }
            prefs.edit().putString("content_impressions_json", jsonArray.toString()).apply()
            _contentImpressions.value = list
        } catch (e: Exception) {
            Log.e("MonetizationRepo", "Error saving content impressions: ${e.message}")
        }
    }

    private fun loadContentEarnings(): List<ContentEarning> {
        val raw = prefs.getString("content_earnings_json", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<ContentEarning>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ContentEarning(
                        id = obj.optString("id"),
                        revenuePeriodId = obj.optString("revenuePeriodId"),
                        contentId = obj.optString("contentId"),
                        creatorId = obj.optString("creatorId"),
                        userHandle = obj.optString("userHandle", ""),
                        contentType = obj.optString("contentType", "POST"),
                        contentTitle = obj.optString("contentTitle", "Content"),
                        contentThumbnailRes = obj.optString("contentThumbnailRes", "default"),
                        contentViews = obj.optLong("contentViews", 0L),
                        eligibleImpressions = obj.optLong("eligibleImpressions", 0L),
                        totalTypeImpressions = obj.optLong("totalTypeImpressions", 0L),
                        attributionShare = obj.optDouble("attributionShare", 0.0),
                        allocatedRevenue = obj.optDouble("allocatedRevenue", 0.0),
                        creatorShare = obj.optDouble("creatorShare", 0.0),
                        platformShare = obj.optDouble("platformShare", 0.0),
                        status = obj.optString("status", "ESTIMATED"),
                        currency = obj.optString("currency", "USD"),
                        calculatedAt = obj.optLong("calculatedAt", System.currentTimeMillis()),
                        finalizedAt = if (obj.has("finalizedAt")) obj.optLong("finalizedAt") else null,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveContentEarnings(list: List<ContentEarning>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { earn ->
                val obj = JSONObject()
                obj.put("id", earn.id)
                obj.put("revenuePeriodId", earn.revenuePeriodId)
                obj.put("contentId", earn.contentId)
                obj.put("creatorId", earn.creatorId)
                obj.put("userHandle", earn.userHandle)
                obj.put("contentType", earn.contentType)
                obj.put("contentTitle", earn.contentTitle)
                obj.put("contentThumbnailRes", earn.contentThumbnailRes)
                obj.put("contentViews", earn.contentViews)
                obj.put("eligibleImpressions", earn.eligibleImpressions)
                obj.put("totalTypeImpressions", earn.totalTypeImpressions)
                obj.put("attributionShare", earn.attributionShare)
                obj.put("allocatedRevenue", earn.allocatedRevenue)
                obj.put("creatorShare", earn.creatorShare)
                obj.put("platformShare", earn.platformShare)
                obj.put("status", earn.status)
                obj.put("currency", earn.currency)
                obj.put("calculatedAt", earn.calculatedAt)
                if (earn.finalizedAt != null) obj.put("finalizedAt", earn.finalizedAt)
                obj.put("createdAt", earn.createdAt)
                obj.put("updatedAt", earn.updatedAt)
                jsonArray.put(obj)
            }
            prefs.edit().putString("content_earnings_json", jsonArray.toString()).apply()
            _contentEarnings.value = list
        } catch (e: Exception) {
            Log.e("MonetizationRepo", "Error saving content earnings: ${e.message}")
        }
    }

    private fun loadRevenueAdjustments(): List<RevenueAdjustment> {
        val raw = prefs.getString("revenue_adjustments_json", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<RevenueAdjustment>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    RevenueAdjustment(
                        id = obj.optString("id"),
                        revenuePeriodId = obj.optString("revenuePeriodId"),
                        contentEarningId = obj.optString("contentEarningId"),
                        creatorId = obj.optString("creatorId"),
                        previousAmount = obj.optDouble("previousAmount", 0.0),
                        adjustmentAmount = obj.optDouble("adjustmentAmount", 0.0),
                        newAmount = obj.optDouble("newAmount", 0.0),
                        reason = obj.optString("reason", ""),
                        source = obj.optString("source", "ADMOB_AUDIT"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        createdBy = obj.optString("createdBy", "admin")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRevenueAdjustments(list: List<RevenueAdjustment>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { adj ->
                val obj = JSONObject()
                obj.put("id", adj.id)
                obj.put("revenuePeriodId", adj.revenuePeriodId)
                obj.put("contentEarningId", adj.contentEarningId)
                obj.put("creatorId", adj.creatorId)
                obj.put("previousAmount", adj.previousAmount)
                obj.put("adjustmentAmount", adj.adjustmentAmount)
                obj.put("newAmount", adj.newAmount)
                obj.put("reason", adj.reason)
                obj.put("source", adj.source)
                obj.put("createdAt", adj.createdAt)
                obj.put("createdBy", adj.createdBy)
                jsonArray.put(obj)
            }
            prefs.edit().putString("revenue_adjustments_json", jsonArray.toString()).apply()
            _revenueAdjustments.value = list
        } catch (e: Exception) {
            Log.e("MonetizationRepo", "Error saving revenue adjustments: ${e.message}")
        }
    }

    private fun loadAttributionDiagnostics(): AttributionDiagnostics {
        return AttributionDiagnostics(
            totalEvents = prefs.getLong("diag_total_events", 0L),
            validEvents = prefs.getLong("diag_valid_events", 0L),
            rejectedEvents = prefs.getLong("diag_rejected_events", 0L),
            duplicateEvents = prefs.getLong("diag_dup_events", 0L),
            suspiciousEvents = prefs.getLong("diag_susp_events", 0L),
            queuedOfflineEvents = 0,
            lastSyncTime = prefs.getLong("diag_last_sync", System.currentTimeMillis())
        )
    }

    private fun saveAttributionDiagnostics(diag: AttributionDiagnostics) {
        prefs.edit()
            .putLong("diag_total_events", diag.totalEvents)
            .putLong("diag_valid_events", diag.validEvents)
            .putLong("diag_rejected_events", diag.rejectedEvents)
            .putLong("diag_dup_events", diag.duplicateEvents)
            .putLong("diag_susp_events", diag.suspiciousEvents)
            .putLong("diag_last_sync", diag.lastSyncTime)
            .apply()
        _attributionDiagnostics.value = diag
    }

    /**
     * Record Attribution Event safely
     */
    fun recordAttributionEvent(
        impression: ContentAdImpression?,
        isValid: Boolean,
        reason: String = ""
    ): Boolean {
        val currentDiag = _attributionDiagnostics.value
        if (!isValid || impression == null) {
            val isDup = reason.contains("DUPLICATE", ignoreCase = true)
            val updatedDiag = currentDiag.copy(
                totalEvents = currentDiag.totalEvents + 1,
                rejectedEvents = currentDiag.rejectedEvents + (if (!isDup) 1 else 0),
                duplicateEvents = currentDiag.duplicateEvents + (if (isDup) 1 else 0),
                lastSyncTime = System.currentTimeMillis()
            )
            saveAttributionDiagnostics(updatedDiag)
            return true
        }

        // Check if event ID or reference already exists
        if (_contentImpressions.value.any { it.id == impression.id || it.impressionReference == impression.impressionReference }) {
            val updatedDiag = currentDiag.copy(
                totalEvents = currentDiag.totalEvents + 1,
                duplicateEvents = currentDiag.duplicateEvents + 1,
                lastSyncTime = System.currentTimeMillis()
            )
            saveAttributionDiagnostics(updatedDiag)
            return true
        }

        // Add valid impression
        val updatedList = listOf(impression) + _contentImpressions.value
        saveContentImpressions(updatedList)

        val updatedDiag = currentDiag.copy(
            totalEvents = currentDiag.totalEvents + 1,
            validEvents = currentDiag.validEvents + 1,
            lastSyncTime = System.currentTimeMillis()
        )
        saveAttributionDiagnostics(updatedDiag)

        // Push to Firestore in background
        return true
    }

    /**
     * Ad Unit Mapping Save / Update
     */
    fun saveAdUnitMapping(mapping: AdMobAdUnitMapping, onResult: ((Boolean) -> Unit)? = null) {
        val existing = _adUnitMappings.value.filter { it.id != mapping.id }
        val updated = listOf(mapping) + existing
        saveAdUnitMappings(updated)
        onResult?.invoke(true)
    }

    /**
     * Create a new Revenue Period
     */
    fun createRevenuePeriod(
        id: String,
        startDate: String,
        endDate: String,
        name: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (id.isBlank() || startDate.isBlank() || endDate.isBlank()) {
            onResult(false, "Please provide valid Period ID and Dates.")
            return
        }

        if (_revenuePeriods.value.any { it.id.equals(id, ignoreCase = true) }) {
            onResult(false, "A revenue period with ID '$id' already exists.")
            return
        }

        val newPeriod = RevenuePeriod(
            id = id.trim().uppercase(),
            name = name.ifBlank { "Revenue Cycle $startDate to $endDate" },
            startDate = startDate.trim(),
            endDate = endDate.trim(),
            status = "OPEN",
            createdAt = System.currentTimeMillis()
        )

        saveRevenuePeriods(listOf(newPeriod) + _revenuePeriods.value)
        onResult(true, "Revenue Period '${newPeriod.id}' created successfully.")
    }

    /**
     * Import official AdMob Revenue Reports for a Revenue Period
     */
    fun importAdMobRevenueReports(
        periodId: String,
        reports: List<AdMobRevenueReport>,
        onResult: (Boolean, String) -> Unit
    ) {
        val period = _revenuePeriods.value.find { it.id == periodId }
        if (period == null) {
            onResult(false, "Revenue Period '$periodId' not found.")
            return
        }

        if (period.status == "FINALIZED") {
            onResult(false, "Cannot import revenue into an already FINALIZED period.")
            return
        }

        if (reports.isEmpty()) {
            onResult(false, "No revenue report records provided for import.")
            return
        }

        // Deduplicate reports by (revenuePeriodId, reportDate, adUnitId, adFormat, countryCode)
        val existingReports = _adMobReports.value.filter { it.revenuePeriodId != periodId }.toMutableList()
        val uniqueIncoming = reports.distinctBy { "${it.revenuePeriodId}_${it.reportDate}_${it.adUnitId}_${it.adFormat}_${it.countryCode}" }
        existingReports.addAll(uniqueIncoming)
        saveAdMobReports(existingReports)

        // Calculate total imported AdMob revenue for this period
        val totalRevenue = uniqueIncoming.sumOf { it.estimatedEarnings }
        val totalImpressions = uniqueIncoming.sumOf { it.impressions }

        val updatedPeriod = period.copy(
            status = "IMPORTED",
            totalAdMobRevenue = BigDecimal(totalRevenue.toString()).setScale(4, RoundingMode.HALF_UP).toDouble(),
            totalEligibleImpressions = totalImpressions,
            importedAt = System.currentTimeMillis()
        )

        saveRevenuePeriods(_revenuePeriods.value.map { if (it.id == periodId) updatedPeriod else it })
        onResult(true, "Successfully imported ${uniqueIncoming.size} AdMob reporting metrics ($${String.format(Locale.US, "%.2f", totalRevenue)} total revenue).")
    }

    /**
     * CORE CALCULATION: Content Revenue Attribution Engine
     * Formula:
     * Creator Revenue Pool = Total AdMob Revenue × Creator Share % (e.g. 50%)
     * Platform Revenue = Total AdMob Revenue - Creator Revenue Pool
     * Post Pool = Creator Pool × Post Pool % (e.g. 30%)
     * Video Pool = Creator Pool × Video Pool % (e.g. 40%)
     * Reels Pool = Creator Pool × Reels Pool % (e.g. 30%)
     *
     * Within each content type:
     * Content Attribution Share = Content Valid Ad Impressions / Total Valid Impressions for that Content Type
     * Content Gross Creator Earning = Type Pool × Content Attribution Share
     */
    fun calculateRevenueAttribution(periodId: String, onResult: (Boolean, String) -> Unit) {
        val period = _revenuePeriods.value.find { it.id == periodId }
        if (period == null) {
            onResult(false, "Revenue Period '$periodId' not found.")
            return
        }

        if (period.status == "FINALIZED") {
            onResult(false, "This Revenue Period is already FINALIZED.")
            return
        }

        val reports = _adMobReports.value.filter { it.revenuePeriodId == periodId }
        val totalAdMobRevenue = reports.sumOf { it.estimatedEarnings }
        if (totalAdMobRevenue <= 0.0) {
            onResult(false, "No AdMob revenue imported for this period ($0.00). Import AdMob reports first.")
            return
        }

        val settings = _settings.value

        // Validate percentage shares
        val creatorSharePct = settings.creatorRevenueShare / 100.0
        val platformSharePct = settings.platformRevenueShare / 100.0
        val postPoolSharePct = settings.postRevenuePoolShare / 100.0
        val videoPoolSharePct = settings.videoRevenuePoolShare / 100.0
        val reelsPoolSharePct = settings.reelsRevenuePoolShare / 100.0

        val totalCreatorPool = BigDecimal(totalAdMobRevenue.toString()).multiply(BigDecimal(creatorSharePct.toString())).setScale(4, RoundingMode.HALF_UP).toDouble()
        val totalPlatformRevenue = BigDecimal(totalAdMobRevenue.toString()).subtract(BigDecimal(totalCreatorPool.toString())).setScale(4, RoundingMode.HALF_UP).toDouble()

        val postPool = BigDecimal(totalCreatorPool.toString()).multiply(BigDecimal(postPoolSharePct.toString())).setScale(4, RoundingMode.HALF_UP).toDouble()
        val videoPool = BigDecimal(totalCreatorPool.toString()).multiply(BigDecimal(videoPoolSharePct.toString())).setScale(4, RoundingMode.HALF_UP).toDouble()
        val reelsPool = BigDecimal(totalCreatorPool.toString()).multiply(BigDecimal(reelsPoolSharePct.toString())).setScale(4, RoundingMode.HALF_UP).toDouble()

        // Gather only valid impressions recorded by real ad callbacks.
        val rawImpressions = _contentImpressions.value.filter { it.isValid }
        val catalogItems = mutableListOf<ContentCatalogItem>()
        rawImpressions.groupBy { it.contentId }.forEach { (contentId, impressions) ->
            val first = impressions.first()
            catalogItems.add(
                ContentCatalogItem(
                    contentId = contentId,
                    creatorId = first.creatorId,
                    userHandle = first.creatorId,
                    contentType = first.contentType,
                    title = contentId,
                    thumbnailRes = "",
                    views = impressions.size.toLong(),
                    baseImpressions = impressions.size.toLong()
                )
            )
        }

        // Count impressions per content type
        val postsTotalImp = catalogItems.filter { it.contentType == "POST" }.sumOf { it.baseImpressions }
        val videoTotalImp = catalogItems.filter { it.contentType == "VIDEO" }.sumOf { it.baseImpressions }
        val reelsTotalImp = catalogItems.filter { it.contentType == "REEL" }.sumOf { it.baseImpressions }
        val grandTotalImp = postsTotalImp + videoTotalImp + reelsTotalImp

        val calculatedEarnings = mutableListOf<ContentEarning>()

        for (item in catalogItems) {
            val typeTotal = when (item.contentType) {
                "POST" -> postsTotalImp
                "VIDEO" -> videoTotalImp
                "REEL" -> reelsTotalImp
                else -> grandTotalImp
            }

            val typePool = when (item.contentType) {
                "POST" -> postPool
                "VIDEO" -> videoPool
                "REEL" -> reelsPool
                else -> totalCreatorPool
            }

            val share = if (typeTotal > 0) item.baseImpressions.toDouble() / typeTotal.toDouble() else 0.0
            val grossAllocated = BigDecimal((typePool * share).toString()).setScale(4, RoundingMode.HALF_UP).toDouble()
            val netCreator = grossAllocated
            val platformAttributed = BigDecimal(((totalPlatformRevenue / 3.0) * share).toString()).setScale(4, RoundingMode.HALF_UP).toDouble()

            calculatedEarnings.add(
                ContentEarning(
                    id = "EARN-${periodId}-${item.contentId}",
                    revenuePeriodId = periodId,
                    contentId = item.contentId,
                    creatorId = item.creatorId,
                    userHandle = item.userHandle,
                    contentType = item.contentType,
                    contentTitle = item.title,
                    contentThumbnailRes = item.thumbnailRes,
                    contentViews = item.views,
                    eligibleImpressions = item.baseImpressions,
                    totalTypeImpressions = typeTotal,
                    attributionShare = BigDecimal(share.toString()).setScale(6, RoundingMode.HALF_UP).toDouble(),
                    allocatedRevenue = grossAllocated,
                    creatorShare = netCreator,
                    platformShare = platformAttributed,
                    status = "ESTIMATED",
                    currency = settings.revenueCurrency,
                    calculatedAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // Replace earnings for this period
        val otherEarnings = _contentEarnings.value.filter { it.revenuePeriodId != periodId }
        saveContentEarnings(calculatedEarnings + otherEarnings)

        val updatedPeriod = period.copy(
            status = "CALCULATED",
            totalCreatorPool = totalCreatorPool,
            totalPlatformRevenue = totalPlatformRevenue,
            postPool = postPool,
            videoPool = videoPool,
            reelsPool = reelsPool,
            totalEligibleImpressions = grandTotalImp,
            postImpressions = postsTotalImp,
            videoImpressions = videoTotalImp,
            reelsImpressions = reelsTotalImp
        )

        saveRevenuePeriods(_revenuePeriods.value.map { if (it.id == periodId) updatedPeriod else it })

        onResult(
            true,
            "Calculated revenue attribution: $${String.format(Locale.US, "%.2f", totalCreatorPool)} Creator Pool allocated across ${calculatedEarnings.size} content items."
        )
    }

    /**
     * FINALIZE REVENUE PERIOD & ATOMICALLY CREDIT CREATOR WALLETS
     * Idempotent: Never double-credits creator wallets if repeated.
     */
    fun finalizeRevenuePeriod(
        periodId: String,
        adminHandle: String = "admin",
        onResult: (Boolean, String) -> Unit
    ) {
        val period = _revenuePeriods.value.find { it.id == periodId }
        if (period == null) {
            onResult(false, "Revenue Period '$periodId' not found.")
            return
        }

        if (period.status == "FINALIZED") {
            onResult(false, "Revenue Period '$periodId' is already FINALIZED.")
            return
        }

        val periodEarnings = _contentEarnings.value.filter { it.revenuePeriodId == periodId }
        if (periodEarnings.isEmpty()) {
            onResult(false, "No calculated content earnings found for this period. Run calculation first.")
            return
        }

        val now = System.currentTimeMillis()

        // 1. Mark period as FINALIZED
        val updatedPeriod = period.copy(
            status = "FINALIZED",
            finalizedAt = now
        )

        // 2. Mark all content earnings as FINALIZED
        val updatedContentEarnings = _contentEarnings.value.map { earn ->
            if (earn.revenuePeriodId == periodId) {
                earn.copy(
                    status = "FINALIZED",
                    finalizedAt = now,
                    updatedAt = now
                )
            } else earn
        }
        saveContentEarnings(updatedContentEarnings)

        // 3. Atomically Credit Creator Wallets with idempotency check
        val activeUserHandle = _userProfile.value.userHandle
        var activeUserCredit = 0.0
        val newTransactions = mutableListOf<MonetizationTransaction>()

        periodEarnings.forEach { earning ->
            val txnReferenceId = "CEARN-${earning.id}"
            val alreadyCredited = _transactions.value.any { it.referenceId == txnReferenceId }

            if (!alreadyCredited && earning.creatorShare > 0.0) {
                val txn = MonetizationTransaction(
                    id = "TXN-${System.currentTimeMillis().toString().takeLast(6)}-${earning.contentId.takeLast(4)}",
                    userId = earning.creatorId,
                    userHandle = earning.userHandle,
                    type = "CREATOR_EARNING",
                    amount = earning.creatorShare,
                    status = "COMPLETED",
                    referenceId = txnReferenceId,
                    description = "AdMob Creator Earning for ${earning.contentType} (${earning.contentTitle.take(30)})",
                    createdAt = now
                )
                newTransactions.add(txn)

                if (earning.userHandle.equals(activeUserHandle, ignoreCase = true) || earning.creatorId == _wallet.value.userId) {
                    activeUserCredit += earning.creatorShare
                }
            }
        }

        if (newTransactions.isNotEmpty()) {
            saveTransactions(newTransactions + _transactions.value)
        }

        // 4. Update Current Active User's Wallet
        if (activeUserCredit > 0.0) {
            val curWallet = _wallet.value
            val newAvailable = BigDecimal((curWallet.availableBalance + activeUserCredit).toString()).setScale(2, RoundingMode.HALF_UP).toDouble()
            val newLifetime = BigDecimal((curWallet.lifetimeEarnings + activeUserCredit).toString()).setScale(2, RoundingMode.HALF_UP).toDouble()

            val updatedWallet = curWallet.copy(
                availableBalance = newAvailable,
                lifetimeEarnings = newLifetime,
                updatedAt = now
            )
            saveWallet(updatedWallet)
        }

        saveRevenuePeriods(_revenuePeriods.value.map { if (it.id == periodId) updatedPeriod else it })

        onResult(
            true,
            "Revenue Period '$periodId' FINALIZED successfully! Credited $${String.format(Locale.US, "%.2f", period.totalCreatorPool)} to creator wallets."
        )
    }

    /**
     * Apply AdMob Audit Adjustment
     */
    fun applyRevenueAdjustment(
        periodId: String,
        contentEarningId: String,
        adjustmentAmount: Double,
        reason: String,
        adminHandle: String = "admin",
        onResult: (Boolean, String) -> Unit
    ) {
        val earning = _contentEarnings.value.find { it.id == contentEarningId }
        if (earning == null) {
            onResult(false, "Content earning record not found.")
            return
        }

        val previousAmount = earning.creatorShare
        val newAmount = BigDecimal((previousAmount + adjustmentAmount).toString()).setScale(4, RoundingMode.HALF_UP).toDouble()
        val now = System.currentTimeMillis()

        val adjustment = RevenueAdjustment(
            id = "ADJ-${now.toString().takeLast(6)}",
            revenuePeriodId = periodId,
            contentEarningId = contentEarningId,
            creatorId = earning.creatorId,
            previousAmount = previousAmount,
            adjustmentAmount = adjustmentAmount,
            newAmount = newAmount,
            reason = reason.ifBlank { "AdMob invalid traffic / reporting reconciliation" },
            source = "ADMOB_AUDIT",
            createdAt = now,
            createdBy = adminHandle
        )

        saveRevenueAdjustments(listOf(adjustment) + _revenueAdjustments.value)

        // Update earning record
        val updatedEarnings = _contentEarnings.value.map {
            if (it.id == contentEarningId) {
                it.copy(creatorShare = newAmount, status = "ADJUSTED", updatedAt = now)
            } else it
        }
        saveContentEarnings(updatedEarnings)

        // Update wallet
        val curWallet = _wallet.value
        if (earning.userHandle.equals(curWallet.userId, ignoreCase = true) || earning.creatorId == curWallet.userId) {
            val newAvailable = BigDecimal((curWallet.availableBalance + adjustmentAmount).toString()).setScale(2, RoundingMode.HALF_UP).toDouble()
            val newLifetime = if (adjustmentAmount > 0) {
                BigDecimal((curWallet.lifetimeEarnings + adjustmentAmount).toString()).setScale(2, RoundingMode.HALF_UP).toDouble()
            } else curWallet.lifetimeEarnings

            saveWallet(curWallet.copy(availableBalance = newAvailable, lifetimeEarnings = newLifetime, updatedAt = now))

            val adjTxn = MonetizationTransaction(
                id = "ADJ-TXN-${now.toString().takeLast(6)}",
                userId = curWallet.userId,
                userHandle = earning.userHandle,
                type = "ADJUSTMENT",
                amount = adjustmentAmount,
                status = "COMPLETED",
                referenceId = adjustment.id,
                description = "Revenue Adjustment: $reason",
                createdAt = now
            )
            saveTransactions(listOf(adjTxn) + _transactions.value)
        }

        onResult(true, "Adjustment of $${String.format(Locale.US, "%.2f", adjustmentAmount)} applied successfully.")
    }

    /**
     * User-Side Query: Get Content Earnings for a specific Creator
     */
    fun getUserContentEarnings(userHandle: String): List<ContentEarning> {
        val target = userHandle.lowercase()
        return _contentEarnings.value.filter { it.userHandle.lowercase() == target || it.creatorId.lowercase() == target }
    }

    /**
     * Creator Summaries for Admin and User Dashboard
     */
    fun getCreatorSummaries(): List<CreatorEarningsSummary> {
        val allEarnings = _contentEarnings.value
        val creators = allEarnings.map { it.userHandle }.distinct()
        val activeHandle = _userProfile.value.userHandle

        val list = mutableListOf<CreatorEarningsSummary>()
        val allTargetHandles = (creators + listOf(activeHandle)).filter { it.isNotBlank() }.distinct()

        for (handle in allTargetHandles) {
            val userEarnings = allEarnings.filter { it.userHandle.equals(handle, ignoreCase = true) }
            val postEarn = userEarnings.filter { it.contentType == "POST" }.sumOf { it.creatorShare }
            val videoEarn = userEarnings.filter { it.contentType == "VIDEO" }.sumOf { it.creatorShare }
            val reelsEarn = userEarnings.filter { it.contentType == "REEL" }.sumOf { it.creatorShare }

            val estimatedTotal = userEarnings.filter { it.status == "ESTIMATED" }.sumOf { it.creatorShare }
            val finalizedTotal = userEarnings.filter { it.status == "FINALIZED" || it.status == "ADJUSTED" || it.status == "PAID" }.sumOf { it.creatorShare }
            val impressions = userEarnings.sumOf { it.eligibleImpressions }

            val isCurrent = handle.equals(activeHandle, ignoreCase = true)
            val walletBal = if (isCurrent) _wallet.value.availableBalance else finalizedTotal * 0.8
            val lifetimeEarn = if (isCurrent) _wallet.value.lifetimeEarnings else finalizedTotal
            val withdrawn = if (isCurrent) _wallet.value.totalWithdrawn else 0.0

            list.add(
                CreatorEarningsSummary(
                    userId = handle,
                    userHandle = handle,
                    userName = handle.replaceFirstChar { it.uppercase() },
                    postsEarnings = postEarn,
                    videosEarnings = videoEarn,
                    reelsEarnings = reelsEarn,
                    estimatedEarnings = estimatedTotal,
                    finalizedEarnings = finalizedTotal,
                    availableBalance = walletBal,
                    lifetimeEarnings = lifetimeEarn,
                    totalWithdrawn = withdrawn,
                    totalImpressions = impressions
                )
            )
        }
        return list
    }

        // -------------------------------------------------------------
    // NOTE: A Firebase Firestore client is NOT bundled in this build.
    // Remote synchronization is handled by the Supabase backend
    // (see SupabaseService). This method is retained as a no-op
    // placeholder for any future remote-sync provider integration.
    // -------------------------------------------------------------
    private fun syncFromFirestore() {
        // No-op: no Firestore dependency is present in this build.
    }
}

private data class ContentCatalogItem(
    val contentId: String,
    val creatorId: String,
    val userHandle: String,
    val contentType: String,
    val title: String,
    val thumbnailRes: String,
    val views: Long,
    val baseImpressions: Long
)
