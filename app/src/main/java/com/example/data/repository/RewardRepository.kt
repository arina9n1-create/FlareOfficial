package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.*
import com.example.data.remote.RewardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class RewardRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("flareofficial_rewards_prefs", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val rewardManager: RewardManager = RewardManager(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    // ---- Verification Badge (paid, fee set by Super Admin) ----
    private val _hasVerificationBadge = MutableStateFlow(false)
    val hasVerificationBadge: StateFlow<Boolean> = _hasVerificationBadge.asStateFlow()

    init {
        _hasVerificationBadge.value = prefs.getBoolean("user_verification_badge_active", false)
    }

    fun setVerificationBadge(active: Boolean) {
        prefs.edit().putBoolean("user_verification_badge_active", active).apply()
        _hasVerificationBadge.value = active
    }

    /** Activates the badge by paying the Super-Admin fee from the user's wallet (server RPC). */
    suspend fun purchaseVerificationBadge(handle: String): Result<org.json.JSONObject> =
        rewardManager.purchaseVerificationBadge(handle)

    private val _selectedCurrency = MutableStateFlow(AppCurrency.USD)
    val selectedCurrency: StateFlow<AppCurrency> = _selectedCurrency.asStateFlow()

    private val _adminConfig = MutableStateFlow(AdminConfig())
    val adminConfig: StateFlow<AdminConfig> = _adminConfig.asStateFlow()

    private val _referrals = MutableStateFlow<List<ReferralRecord>>(emptyList())
    val referrals: StateFlow<List<ReferralRecord>> = _referrals.asStateFlow()

    private val _wallet = MutableStateFlow(UserRewardWallet())
    val wallet: StateFlow<UserRewardWallet> = _wallet.asStateFlow()

    private val _tasks = MutableStateFlow<List<RewardTask>>(emptyList())
    val tasks: StateFlow<List<RewardTask>> = _tasks.asStateFlow()

    private val _withdrawals = MutableStateFlow<List<WithdrawalRequest>>(emptyList())
    val withdrawals: StateFlow<List<WithdrawalRequest>> = _withdrawals.asStateFlow()

    private val _totalPlatformCredits = MutableStateFlow(0)
    val totalPlatformCredits: StateFlow<Int> = _totalPlatformCredits.asStateFlow()

    // ---- Professional wallet state (server-authoritative via RPCs) ----
    private val _walletSummary = MutableStateFlow(WalletSummary())
    val walletSummary: StateFlow<WalletSummary> = _walletSummary.asStateFlow()
    private val _walletTransactions = MutableStateFlow<List<WalletTransaction>>(emptyList())
    val walletTransactions: StateFlow<List<WalletTransaction>> = _walletTransactions.asStateFlow()
    private val _platformOverview = MutableStateFlow(PlatformWalletOverview())
    val platformOverview: StateFlow<PlatformWalletOverview> = _platformOverview.asStateFlow()
    private val _userEarnings = MutableStateFlow<List<UserEarningsOverview>>(emptyList())
    val userEarnings: StateFlow<List<UserEarningsOverview>> = _userEarnings.asStateFlow()
    private val _auditLogs = MutableStateFlow<List<WalletAuditLog>>(emptyList())
    val auditLogs: StateFlow<List<WalletAuditLog>> = _auditLogs.asStateFlow()
    private val _fraudFlags = MutableStateFlow<List<WalletFraudFlag>>(emptyList())
    val fraudFlags: StateFlow<List<WalletFraudFlag>> = _fraudFlags.asStateFlow()

    init {
        _selectedCurrency.value = loadCurrency()
        _adminConfig.value = loadAdminConfig()
        _referrals.value = loadReferrals()
        _wallet.value = loadWallet()
        _tasks.value = loadTasks()
        _withdrawals.value = loadWithdrawals()
        checkDailyLogin()
    }

    private fun loadCurrency(): AppCurrency {
        val code = prefs.getString("user_selected_currency", AppCurrency.USD.name) ?: AppCurrency.USD.name
        return try {
            AppCurrency.valueOf(code)
        } catch (e: Exception) {
            AppCurrency.USD
        }
    }

    fun setCurrency(currency: AppCurrency) {
        prefs.edit().putString("user_selected_currency", currency.name).apply()
        _selectedCurrency.value = currency
    }

    fun toggleCurrency(): AppCurrency {
        val newCurr = if (_selectedCurrency.value == AppCurrency.USD) AppCurrency.BDT else AppCurrency.USD
        setCurrency(newCurr)
        return newCurr
    }

    private fun loadWallet(): UserRewardWallet {
        val today = dateFormat.format(Date())
        val savedDate = prefs.getString("wallet_last_date", today) ?: today
        var todayWatched = prefs.getInt("wallet_today_reels", 0)
        var todayUploaded = prefs.getInt("wallet_today_uploaded", 0)
        if (savedDate != today) {
            todayWatched = 0
            todayUploaded = 0
            prefs.edit()
                .putString("wallet_last_date", today)
                .putInt("wallet_today_reels", 0)
                .putInt("wallet_today_uploaded", 0)
                .apply()
        }

        val refCode = prefs.getString("wallet_ref_code", null) ?: generateRefCode()
        if (!prefs.contains("wallet_ref_code")) {
            prefs.edit().putString("wallet_ref_code", refCode).apply()
        }

        val refList = _referrals.value
        val pendingCount = refList.count { it.status == "PENDING" || it.status == "COMPLETED" }
        val pendingEarn = refList.filter { it.status == "PENDING" || it.status == "COMPLETED" }.sumOf { it.bonusCredits }

        return UserRewardWallet(
            totalCredits = prefs.getInt("wallet_total_credits", 0),
            todayReelsWatched = todayWatched,
            todayReelsUploaded = todayUploaded,
            totalReelsWatched = prefs.getInt("wallet_total_reels", 0),
            totalReelsUploaded = prefs.getInt("wallet_total_uploaded", 0),
            referralCode = refCode,
            referredUsersCount = prefs.getInt("wallet_ref_count", 0),
            referralEarnings = prefs.getInt("wallet_ref_earnings", 0),
            pendingReferralEarnings = pendingEarn,
            pendingReferralsCount = pendingCount,
            totalDailyTasksCompletedDays = prefs.getInt("wallet_daily_tasks_days", 0),
            accountAgeDays = prefs.getInt("wallet_account_days", 0),
            currentStreakDay = prefs.getInt("wallet_streak_day", 0),
            lastLoginDate = savedDate,
            referredByCode = prefs.getString("wallet_referred_by_code", "") ?: ""
        )
    }

    private fun saveWallet(wallet: UserRewardWallet) {
        prefs.edit().apply {
            putInt("wallet_total_credits", wallet.totalCredits)
            putInt("wallet_today_reels", wallet.todayReelsWatched)
            putInt("wallet_today_uploaded", wallet.todayReelsUploaded)
            putInt("wallet_total_reels", wallet.totalReelsWatched)
            putInt("wallet_total_uploaded", wallet.totalReelsUploaded)
            putString("wallet_ref_code", wallet.referralCode)
            putInt("wallet_ref_count", wallet.referredUsersCount)
            putInt("wallet_ref_earnings", wallet.referralEarnings)
            putInt("wallet_daily_tasks_days", wallet.totalDailyTasksCompletedDays)
            putInt("wallet_account_days", wallet.accountAgeDays)
            putInt("wallet_streak_day", wallet.currentStreakDay)
            putString("wallet_last_date", wallet.lastLoginDate)
            putString("wallet_referred_by_code", wallet.referredByCode)
            apply()
        }
        _wallet.value = wallet
    }

    private fun generateRefCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val randomPart = (1..5).map { chars.random() }.joinToString("")
        return "FLARE$randomPart"
    }

    private fun loadAdminConfig(): AdminConfig {
        val methods = loadPaymentMethods()
        return AdminConfig(
            dailyTaskRequiredWatchReels = prefs.getInt("admin_daily_watch_reels", 10),
            dailyTaskRequiredUploadReels = prefs.getInt("admin_daily_upload_reels", 5),
            dailyTaskRewardCredits = prefs.getInt("admin_daily_reward_credits", 200),
            referralBonusCredits = prefs.getInt("admin_ref_bonus", 500),
            referralRequiredDailyTaskDays = prefs.getInt("admin_ref_required_days", 2),
            creditsPerReel = prefs.getInt("admin_credits_per_reel", 2),
            requiredReelWatchSeconds = prefs.getInt("admin_watch_seconds", 10),
            creditsPerDollar = prefs.getInt("admin_credits_per_dollar", 2000),
            usdToBdtRate = prefs.getFloat("admin_usd_to_bdt_rate", 120.0f).toDouble(),
            minWithdrawalUSD = prefs.getFloat("admin_min_withdraw", 1.0f).toDouble(),
            verificationBadgeFeeUSD = prefs.getFloat("admin_verification_badge_fee", 4.99f).toDouble(),
            isBkashEnabled = prefs.getBoolean("admin_bkash_enabled", true),
            isNagadEnabled = prefs.getBoolean("admin_nagad_enabled", true),
            isRocketEnabled = prefs.getBoolean("admin_rocket_enabled", true),
            isBinanceEnabled = prefs.getBoolean("admin_binance_enabled", true),
            isPaypalEnabled = prefs.getBoolean("admin_paypal_enabled", true),
            customPaymentMethods = methods,
            noticeMessage = prefs.getString("admin_notice", "Payments are processed within 12-24 hours. Watch reels, upload content & invite friends to earn real money!") ?: ""
        )
    }

    private fun loadPaymentMethods(): List<PaymentMethodItem> {
        val count = prefs.getInt("payment_methods_count", -1)
        if (count == -1) {
            val defaults = defaultPaymentMethodsList()
            savePaymentMethods(defaults)
            return defaults
        }
        val list = mutableListOf<PaymentMethodItem>()
        for (i in 0 until count) {
            val id = prefs.getString("pm_${i}_id", "pm_$i") ?: "pm_$i"
            val name = prefs.getString("pm_${i}_name", "Payment Method") ?: "Payment Method"
            val type = prefs.getString("pm_${i}_type", "MOBILE_BANKING") ?: "MOBILE_BANKING"
            val icon = prefs.getString("pm_${i}_icon", "generic") ?: "generic"
            val minUSD = prefs.getFloat("pm_${i}_min_usd", 1.0f).toDouble()
            val instructions = prefs.getString("pm_${i}_instructions", "Enter account number") ?: "Enter account number"
            val isEnabled = prefs.getBoolean("pm_${i}_enabled", true)
            list.add(PaymentMethodItem(id, name, type, icon, minUSD, instructions, isEnabled))
        }
        if (list.none { it.id == "pm_recharge" || it.type == "MOBILE_RECHARGE" }) {
            val recharge = defaultPaymentMethodsList().first()
            list.add(0, recharge)
            savePaymentMethods(list)
        }
        return if (list.isEmpty()) defaultPaymentMethodsList() else list
    }

    private fun savePaymentMethods(list: List<PaymentMethodItem>) {
        prefs.edit().apply {
            putInt("payment_methods_count", list.size)
            list.forEachIndexed { i, pm ->
                putString("pm_${i}_id", pm.id)
                putString("pm_${i}_name", pm.name)
                putString("pm_${i}_type", pm.type)
                putString("pm_${i}_icon", pm.iconType)
                putFloat("pm_${i}_min_usd", pm.minWithdrawalUSD.toFloat())
                putString("pm_${i}_instructions", pm.instructions)
                putBoolean("pm_${i}_enabled", pm.isEnabled)
            }
            apply()
        }
    }

    private fun loadReferrals(): List<ReferralRecord> {
        val count = prefs.getInt("referrals_list_count", -1)
        if (count == -1) {
            val initial = emptyList<ReferralRecord>()
            saveReferrals(initial)
            return initial
        }
        val list = mutableListOf<ReferralRecord>()
        for (i in 0 until count) {
            val id = prefs.getString("ref_${i}_id", "") ?: ""
            val handle = prefs.getString("ref_${i}_handle", "") ?: ""
            val name = prefs.getString("ref_${i}_name", "") ?: ""
            val bonus = prefs.getInt("ref_${i}_bonus", 0)
            val completedDays = prefs.getInt("ref_${i}_completed_days", 0)
            val reqDays = prefs.getInt("ref_${i}_req_days", 0)
            val status = prefs.getString("ref_${i}_status", "PENDING") ?: "PENDING"
            val date = prefs.getString("ref_${i}_date", "") ?: ""
            val ts = prefs.getLong("ref_${i}_ts", System.currentTimeMillis())
            list.add(ReferralRecord(id, handle, name, bonus, completedDays, reqDays, status, date, ts))
        }
        return list
    }

    private fun saveReferrals(list: List<ReferralRecord>) {
        prefs.edit().apply {
            putInt("referrals_list_count", list.size)
            list.forEachIndexed { i, r ->
                putString("ref_${i}_id", r.id)
                putString("ref_${i}_handle", r.refereeHandle)
                putString("ref_${i}_name", r.refereeName)
                putInt("ref_${i}_bonus", r.bonusCredits)
                putInt("ref_${i}_completed_days", r.dailyTasksCompleted)
                putInt("ref_${i}_req_days", r.requiredDays)
                putString("ref_${i}_status", r.status)
                putString("ref_${i}_date", r.date)
                putLong("ref_${i}_ts", r.timestamp)
            }
            apply()
        }
        _referrals.value = list
    }

    fun updateAdminConfig(newConfig: AdminConfig) {
        prefs.edit().apply {
            putInt("admin_daily_watch_reels", newConfig.dailyTaskRequiredWatchReels)
            putInt("admin_daily_upload_reels", newConfig.dailyTaskRequiredUploadReels)
            putInt("admin_daily_reward_credits", newConfig.dailyTaskRewardCredits)
            putInt("admin_ref_bonus", newConfig.referralBonusCredits)
            putInt("admin_ref_required_days", newConfig.referralRequiredDailyTaskDays)
            putInt("admin_credits_per_reel", newConfig.creditsPerReel)
            putInt("admin_watch_seconds", newConfig.requiredReelWatchSeconds)
            putInt("admin_credits_per_dollar", newConfig.creditsPerDollar)
            putFloat("admin_usd_to_bdt_rate", newConfig.usdToBdtRate.toFloat())
            putFloat("admin_min_withdraw", newConfig.minWithdrawalUSD.toFloat())
            putFloat("admin_verification_badge_fee", newConfig.verificationBadgeFeeUSD.toFloat())
            putBoolean("admin_bkash_enabled", newConfig.isBkashEnabled)
            putBoolean("admin_nagad_enabled", newConfig.isNagadEnabled)
            putBoolean("admin_rocket_enabled", newConfig.isRocketEnabled)
            putBoolean("admin_binance_enabled", newConfig.isBinanceEnabled)
            putBoolean("admin_paypal_enabled", newConfig.isPaypalEnabled)
            putString("admin_notice", newConfig.noticeMessage)
            apply()
        }
        savePaymentMethods(newConfig.customPaymentMethods)
        _adminConfig.value = newConfig
        _tasks.value = loadTasks()
        scope.launch {
            rewardManager.syncAdminConfig(newConfig)
        }
    }

    fun addPaymentMethod(method: PaymentMethodItem) {
        val currentMethods = _adminConfig.value.customPaymentMethods.toMutableList()
        val uniqueId = if (method.id.isBlank()) "pm_${System.currentTimeMillis()}" else method.id
        currentMethods.add(method.copy(id = uniqueId))
        val updatedConfig = _adminConfig.value.copy(customPaymentMethods = currentMethods)
        updateAdminConfig(updatedConfig)
    }

    fun updatePaymentMethod(method: PaymentMethodItem) {
        val updatedMethods = _adminConfig.value.customPaymentMethods.map {
            if (it.id == method.id) method else it
        }
        val updatedConfig = _adminConfig.value.copy(customPaymentMethods = updatedMethods)
        updateAdminConfig(updatedConfig)
    }

    fun deletePaymentMethod(methodId: String) {
        val updatedMethods = _adminConfig.value.customPaymentMethods.filter { it.id != methodId }
        val updatedConfig = _adminConfig.value.copy(customPaymentMethods = updatedMethods)
        updateAdminConfig(updatedConfig)
    }

    fun togglePaymentMethod(methodId: String) {
        val updatedMethods = _adminConfig.value.customPaymentMethods.map {
            if (it.id == methodId) it.copy(isEnabled = !it.isEnabled) else it
        }
        val updatedConfig = _adminConfig.value.copy(customPaymentMethods = updatedMethods)
        updateAdminConfig(updatedConfig)
    }

    private fun checkDailyLogin() {
        val today = dateFormat.format(Date())
        val current = _wallet.value
        if (current.lastLoginDate != today) {
            val nextDay = if (current.currentStreakDay >= 7) 1 else current.currentStreakDay + 1
            val nextAge = current.accountAgeDays + 1
            val updated = current.copy(
                accountAgeDays = nextAge,
                currentStreakDay = nextDay,
                lastLoginDate = today,
                todayReelsWatched = 0,
                todayReelsUploaded = 0
            )
            saveWallet(updated)
        }
    }

    fun syncUserLogin(userHandle: String, email: String) {
        scope.launch {
            val remoteWallet = rewardManager.recordDailyLogin(userHandle, email)
            if (remoteWallet != null) {
                saveWallet(remoteWallet)
            }
        }
    }

    suspend fun handleNewUserRegistration(
        name: String,
        email: String,
        userHandle: String,
        referralCode: String?
    ): Result<UserRewardWallet> {
        val result = rewardManager.processNewUserSignup(
            name = name,
            email = email,
            userHandle = userHandle,
            enteredReferralCode = referralCode,
            referralBonusAmount = _adminConfig.value.referralBonusCredits
        )
        if (result.isSuccess) {
            val wallet = result.getOrNull()
            if (wallet != null) {
                saveWallet(wallet)
                _tasks.value = loadTasks()
            }
        }
        return result
    }

    /**
     * Clean task list: ONLY the daily task to watch X reels & upload Y reels to earn Z credits.
     */
    private fun loadTasks(): List<RewardTask> {
        val today = dateFormat.format(Date())
        val config = _adminConfig.value
        val wallet = _wallet.value
        val taskList = mutableListOf<RewardTask>()

        val isMasterClaimed = prefs.getBoolean("claimed_daily_master_$today", false)
        val isMasterCompleted = wallet.todayReelsWatched >= config.dailyTaskRequiredWatchReels &&
                wallet.todayReelsUploaded >= config.dailyTaskRequiredUploadReels

        // 1. Primary Unified Daily Task (10 Reels Watched + 5 Reels Uploaded = 200 Credits)
        taskList.add(
            RewardTask(
                id = "daily_reels_master",
                title = "Daily Task: Watch ${config.dailyTaskRequiredWatchReels} Reels & Upload ${config.dailyTaskRequiredUploadReels} Reels",
                description = "Watch ${config.dailyTaskRequiredWatchReels} reels (${wallet.todayReelsWatched}/${config.dailyTaskRequiredWatchReels}) & upload ${config.dailyTaskRequiredUploadReels} reels (${wallet.todayReelsUploaded}/${config.dailyTaskRequiredUploadReels}) today",
                rewardCredits = config.dailyTaskRewardCredits,
                requiredCount = config.dailyTaskRequiredWatchReels + config.dailyTaskRequiredUploadReels,
                currentCount = minOf(wallet.todayReelsWatched, config.dailyTaskRequiredWatchReels) + minOf(wallet.todayReelsUploaded, config.dailyTaskRequiredUploadReels),
                isCompleted = isMasterCompleted,
                isClaimed = isMasterClaimed,
                dayNumber = 0,
                isDailyTask = true,
                actionType = "WATCH_REELS"
            )
        )

        // 2. Sub-Task 1: Watch Reels Progress
        taskList.add(
            RewardTask(
                id = "daily_watch_reels_step",
                title = "1. Watch ${config.dailyTaskRequiredWatchReels} Short Reels",
                description = "Enjoy entertaining video reels for ${config.requiredReelWatchSeconds}s each (+${config.creditsPerReel} credits/reel)",
                rewardCredits = 0,
                requiredCount = config.dailyTaskRequiredWatchReels,
                currentCount = wallet.todayReelsWatched,
                isCompleted = wallet.todayReelsWatched >= config.dailyTaskRequiredWatchReels,
                isClaimed = wallet.todayReelsWatched >= config.dailyTaskRequiredWatchReels,
                dayNumber = 0,
                isDailyTask = true,
                actionType = "WATCH_REELS"
            )
        )

        // 3. Sub-Task 2: Upload Reels Progress
        taskList.add(
            RewardTask(
                id = "daily_upload_reels_step",
                title = "2. Upload ${config.dailyTaskRequiredUploadReels} Creator Reels",
                description = "Share video clips or stories with the community",
                rewardCredits = 0,
                requiredCount = config.dailyTaskRequiredUploadReels,
                currentCount = wallet.todayReelsUploaded,
                isCompleted = wallet.todayReelsUploaded >= config.dailyTaskRequiredUploadReels,
                isClaimed = wallet.todayReelsUploaded >= config.dailyTaskRequiredUploadReels,
                dayNumber = 0,
                isDailyTask = true,
                actionType = "CREATE_POST"
            )
        )

        return taskList
    }

    fun addReelWatchCredit(userHandle: String = "", watchSeconds: Int = 10): Int {
        val config = _adminConfig.value
        val current = _wallet.value
        val updatedToday = current.todayReelsWatched + 1
        val updatedTotal = current.totalReelsWatched + 1
        val updatedCredits = current.totalCredits + config.creditsPerReel

        val updatedWallet = current.copy(
            totalCredits = updatedCredits,
            todayReelsWatched = updatedToday,
            totalReelsWatched = updatedTotal
        )
        saveWallet(updatedWallet)
        _tasks.value = loadTasks()

        scope.launch {
            rewardManager.recordReelWatch(
                userHandle = userHandle,
                watchDurationSeconds = watchSeconds,
                creditsEarned = config.creditsPerReel
            )
        }
        return config.creditsPerReel
    }

    fun recordReelUpload(userHandle: String = "") {
        val current = _wallet.value
        val updatedTodayUpload = current.todayReelsUploaded + 1
        val updatedTotalUpload = current.totalReelsUploaded + 1

        val updatedWallet = current.copy(
            todayReelsUploaded = updatedTodayUpload,
            totalReelsUploaded = updatedTotalUpload
        )
        saveWallet(updatedWallet)
        _tasks.value = loadTasks()

        scope.launch {
            rewardManager.recordPostCreated(userHandle, rewardCredits = 0)
        }
    }

    fun claimTaskReward(taskId: String): Int {
        val task = _tasks.value.find { it.id == taskId } ?: return 0
        if (!task.isCompleted || task.isClaimed) return 0

        val today = dateFormat.format(Date())
        prefs.edit().putBoolean("claimed_${taskId}_$today", true).apply()
        if (taskId == "daily_reels_master") {
            prefs.edit().putBoolean("claimed_daily_master_$today", true).apply()
        }

        val current = _wallet.value
        val newCredits = current.totalCredits + task.rewardCredits
        val updatedDailyDays = current.totalDailyTasksCompletedDays + 1

        saveWallet(current.copy(
            totalCredits = newCredits,
            totalDailyTasksCompletedDays = updatedDailyDays
        ))

        _tasks.value = loadTasks()
        return task.rewardCredits
    }

    fun completeTaskByType(userHandle: String = "", type: String) {
        when (type) {
            "CREATE_POST", "UPLOAD_REEL" -> {
                recordReelUpload(userHandle)
            }
        }
        _tasks.value = loadTasks()
    }

    /**
     * Referral System:
     * When user enters a friend's referral code, a PENDING referral is created for the referrer.
     * The referrer receives the reward (500 credits) only after the referred friend
     * completes their Daily Task for 2 days.
     */
    fun applyReferral(code: String, refereeHandle: String = "", refereeName: String = ""): Result<String> {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) {
            return Result.failure(Exception("Please enter a valid referral code."))
        }
        if (cleanCode == _wallet.value.referralCode.uppercase()) {
            return Result.failure(Exception("You cannot use your own referral code!"))
        }

        val config = _adminConfig.value
        val currentReferrals = _referrals.value.toMutableList()

        // Create new Pending Referral Record for the Referrer
        val newRecord = ReferralRecord(
            id = "ref_${System.currentTimeMillis()}",
            refereeHandle = refereeHandle,
            refereeName = refereeName,
            bonusCredits = config.referralBonusCredits,
            dailyTasksCompleted = 0,
            requiredDays = config.referralRequiredDailyTaskDays,
            status = "PENDING",
            date = dateFormat.format(Date()),
            timestamp = System.currentTimeMillis()
        )

        currentReferrals.add(0, newRecord)
        saveReferrals(currentReferrals)

        val current = _wallet.value
        saveWallet(current.copy(
            referredByCode = cleanCode,
            pendingReferralsCount = currentReferrals.count { it.status == "PENDING" || it.status == "COMPLETED" },
            pendingReferralEarnings = currentReferrals.filter { it.status == "PENDING" || it.status == "COMPLETED" }.sumOf { it.bonusCredits }
        ))

        return Result.success("Referral code confirmed! Your referrer will get +${config.referralBonusCredits} credits once you complete 2 days of Daily Tasks.")
    }

    /**
     * Simulate / Record daily task completion for a referred user.
     * When completed days reaches requiredDays (e.g. 2 days), status transitions from PENDING -> COMPLETED (Ready to claim).
     */
    fun simulateRefereeDailyTask(referralId: String): String {
        val list = _referrals.value.toMutableList()
        val index = list.indexOfFirst { it.id == referralId }
        if (index == -1) return "Referral record not found"

        val ref = list[index]
        if (ref.status == "CLAIMED") return "Already claimed"

        val nextCompleted = ref.dailyTasksCompleted + 1
        val newStatus = if (nextCompleted >= ref.requiredDays) "COMPLETED" else "PENDING"

        list[index] = ref.copy(
            dailyTasksCompleted = nextCompleted,
            status = newStatus
        )
        saveReferrals(list)

        val current = _wallet.value
        saveWallet(current.copy(
            pendingReferralsCount = list.count { it.status == "PENDING" || it.status == "COMPLETED" },
            pendingReferralEarnings = list.filter { it.status == "PENDING" || it.status == "COMPLETED" }.sumOf { it.bonusCredits }
        ))

        return if (newStatus == "COMPLETED") {
            "🎉 2/2 Days Daily Tasks Completed! You can now claim +${ref.bonusCredits} Credits!"
        } else {
            "Day $nextCompleted/${ref.requiredDays} Daily Task recorded for @${ref.refereeHandle}."
        }
    }

    /**
     * Claim reward for a referral that has completed the 2-day daily task requirement.
     */
    fun claimReferralReward(referralId: String): Int {
        val list = _referrals.value.toMutableList()
        val index = list.indexOfFirst { it.id == referralId }
        if (index == -1) return 0

        val ref = list[index]
        if (ref.status != "COMPLETED" || ref.dailyTasksCompleted < ref.requiredDays) {
            return 0
        }

        list[index] = ref.copy(status = "CLAIMED")
        saveReferrals(list)

        val current = _wallet.value
        val newTotal = current.totalCredits + ref.bonusCredits
        val newEarned = current.referralEarnings + ref.bonusCredits
        val newCount = current.referredUsersCount + 1

        saveWallet(current.copy(
            totalCredits = newTotal,
            referralEarnings = newEarned,
            referredUsersCount = newCount,
            pendingReferralsCount = list.count { it.status == "PENDING" || it.status == "COMPLETED" },
            pendingReferralEarnings = list.filter { it.status == "PENDING" || it.status == "COMPLETED" }.sumOf { it.bonusCredits }
        ))

        return ref.bonusCredits
    }

    private fun loadWithdrawals(): List<WithdrawalRequest> {
        val count = prefs.getInt("withdrawals_count", 0)
        val list = mutableListOf<WithdrawalRequest>()
        for (i in 0 until count) {
            val id = prefs.getString("w_${i}_id", "") ?: ""
            val handle = prefs.getString("w_${i}_handle", "") ?: ""
            val email = prefs.getString("w_${i}_email", "") ?: ""
            val method = prefs.getString("w_${i}_method", "") ?: ""
            val acc = prefs.getString("w_${i}_acc", "") ?: ""
            val credits = prefs.getInt("w_${i}_credits", 0)
            val usd = prefs.getFloat("w_${i}_usd", 0f).toDouble()
            val bdt = prefs.getFloat("w_${i}_bdt", (usd * 120.0).toFloat()).toDouble()
            val status = prefs.getString("w_${i}_status", "PENDING") ?: "PENDING"
            val date = prefs.getString("w_${i}_date", "") ?: ""
            val note = prefs.getString("w_${i}_note", "") ?: ""
            list.add(WithdrawalRequest(id, handle, email, method, acc, credits, usd, bdt, status, date, note))
        }
        return list
    }

    private fun saveWithdrawals(list: List<WithdrawalRequest>) {
        prefs.edit().apply {
            putInt("withdrawals_count", list.size)
            list.forEachIndexed { i, req ->
                putString("w_${i}_id", req.id)
                putString("w_${i}_handle", req.userHandle)
                putString("w_${i}_email", req.userEmail)
                putString("w_${i}_method", req.method)
                putString("w_${i}_acc", req.accountNumber)
                putInt("w_${i}_credits", req.creditsUsed)
                putFloat("w_${i}_usd", req.amountUSD.toFloat())
                putFloat("w_${i}_bdt", req.amountBDT.toFloat())
                putString("w_${i}_status", req.status)
                putString("w_${i}_date", req.requestDate)
                putString("w_${i}_note", req.transactionNote)
            }
            apply()
        }
        _withdrawals.value = list
    }

    fun submitWithdrawal(handle: String, email: String, method: String, account: String, credits: Int, usd: Double): Result<String> {
        val current = _wallet.value
        if (current.totalCredits < credits) {
            return Result.failure(Exception("Insufficient credits balance!"))
        }

        val bdtAmount = usd * _adminConfig.value.usdToBdtRate
        val newRequest = WithdrawalRequest(
            id = "TXN-${System.currentTimeMillis() % 1000000}",
            userHandle = handle,
            userEmail = email,
            method = method,
            accountNumber = account,
            creditsUsed = credits,
            amountUSD = usd,
            amountBDT = bdtAmount,
            status = "PENDING",
            requestDate = dateFormat.format(Date()),
            transactionNote = "Verification in progress"
        )

        val updatedList = listOf(newRequest) + _withdrawals.value
        saveWithdrawals(updatedList)

        // Deduct credits
        saveWallet(current.copy(totalCredits = current.totalCredits - credits))

        scope.launch {
            rewardManager.syncWithdrawal(newRequest)
        }

        return Result.success(newRequest.id)
    }

    /**
     * Admin approve/reject. Updates the status LOCALLY and syncs it to Supabase so the
     * requester (and every other admin device) sees the change.
     */
    suspend fun updateWithdrawalStatus(requestId: String, newStatus: String, adminNote: String): Result<Unit> = withContext(Dispatchers.IO) {
        val target = _withdrawals.value.firstOrNull { it.id == requestId }
            ?: return@withContext Result.failure(Exception("Withdrawal request not found"))

        // 1. Update remote first (authoritative) — local fallback happens regardless.
        val remoteOk = rewardManager.updateWithdrawalRemote(requestId, newStatus, adminNote)

        // 2. Update local cache
        val updated = _withdrawals.value.map { req ->
            if (req.id == requestId) {
                req.copy(status = newStatus, transactionNote = adminNote)
            } else {
                req
            }
        }
        saveWithdrawals(updated)

        if (!remoteOk) {
            return@withContext Result.failure(Exception("Status saved locally but server sync failed"))
        }

        // 3. Refund coins back to the requester when a PENDING request is rejected
        if (newStatus == "REJECTED" && target.status == "PENDING" && target.creditsUsed > 0) {
            refundRequester(target.userHandle, target.creditsUsed)
        }

        Result.success(Unit)
    }

    /**
     * Refunds coins to the withdrawal requester. Remote wallet is updated through the
     * refund_withdrawal RPC; if the requester is the currently signed-in user of THIS
     * device, their local wallet is topped up too.
     */
    suspend fun refundRequester(userHandle: String, credits: Int, localUserHandle: String = "") = withContext(Dispatchers.IO) {
        if (userHandle.isBlank() || credits <= 0) return@withContext
        rewardManager.refundWithdrawalCredits(userHandle, credits)
        if (localUserHandle.isNotBlank() && localUserHandle.equals(userHandle, ignoreCase = true)) {
            val current = _wallet.value
            saveWallet(current.copy(totalCredits = current.totalCredits + credits))
        }
    }

    /** Pulls ALL withdrawal requests from Supabase and merges them with the local cache. */
    suspend fun refreshWithdrawals(): Int = withContext(Dispatchers.IO) {
        val remote = rewardManager.fetchWithdrawals()
        
        // Also refresh the total platform balance while we are at it
        _totalPlatformCredits.value = rewardManager.fetchTotalPlatformBalance()

        if (remote.isEmpty()) return@withContext _withdrawals.value.size
        val merged = (remote + _withdrawals.value.filter { local ->
            remote.none { it.id == local.id }
        }).sortedByDescending { it.timestamp }
        saveWithdrawals(merged)
        merged.size
    }

    /**
     * Pulls the signed-in user's remote wallet and adopts the higher credit balance.
     * This is how rejected-withdrawal refunds (written server-side) reach the user.
     */
    suspend fun refreshWalletFromRemote(userHandle: String) = withContext(Dispatchers.IO) {
        if (userHandle.isBlank()) return@withContext
        val remote = rewardManager.fetchRemoteWallet(userHandle) ?: return@withContext
        val local = _wallet.value
        if (remote.totalCredits > local.totalCredits) {
            saveWallet(local.copy(totalCredits = remote.totalCredits))
        }
    }

    fun deductCredits(amount: Int) {
        val current = _wallet.value
        val newCredits = maxOf(0, current.totalCredits - amount)
        saveWallet(current.copy(totalCredits = newCredits))
    }

    fun addCredits(amount: Int) {
        val current = _wallet.value
        val newCredits = current.totalCredits + amount
        saveWallet(current.copy(totalCredits = newCredits))
    }

    fun addAdminCustomTask(title: String, desc: String, rewardCredits: Int) {
        val count = prefs.getInt("custom_tasks_count", 0)
        val id = "custom_task_$count"
        prefs.edit().apply {
            putString("${id}_title", title)
            putString("${id}_desc", desc)
            putInt("${id}_reward", rewardCredits)
            putBoolean("${id}_claimed", false)
            putInt("custom_tasks_count", count + 1)
            apply()
        }
        _tasks.value = loadTasks()
    }

    // ==================================================================
    // PROFESSIONAL WALLET — server-authoritative operations
    // ==================================================================

    /** Pulls the authenticated user's wallet summary from the server. */
    suspend fun refreshWalletSummary(userHandle: String): WalletSummary = withContext(Dispatchers.IO) {
        if (userHandle.isBlank()) return@withContext _walletSummary.value
        _walletSummary.value = _walletSummary.value.copy(loading = true, error = "")
        val result = rewardManager.fetchWalletSummary(userHandle)
        _walletSummary.value = if (result.loading || result.error.isNotEmpty()) {
            result.copy(loading = false, error = result.error)
        } else {
            result.copy(loading = false)
        }
        _walletSummary.value
    }

    /** Redeems eligible reward credits into the withdrawable wallet (server-validated, no double redeem). */
    suspend fun redeemCredits(userHandle: String, source: String): Result<String> = withContext(Dispatchers.IO) {
        if (userHandle.isBlank()) return@withContext Result.failure(Exception("Not signed in"))
        val result = rewardManager.redeemRewardCredits(userHandle, source)
        if (result.isSuccess) {
            refreshWalletSummary(userHandle)
            // Refresh the local legacy wallet total too so the rest of the app stays consistent.
            val s = _walletSummary.value
            saveWallet(_wallet.value.copy(totalCredits = s.withdrawableCredits))
        }
        result.map { "Redeemed ${it.withdrawableCredits} coins into your wallet" }
    }

    /** Server-validated withdrawal request (funds reserved atomically). */
    suspend fun requestWithdrawalV2(
        handle: String,
        email: String,
        method: String,
        account: String,
        credits: Int,
        usd: Double,
        bdt: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        val clientRef = "WD-${System.currentTimeMillis()}-${(1000..9999).random()}"
        val result = rewardManager.requestWalletWithdrawal(handle, email, method, account, credits, usd, bdt, clientRef)
        if (result.isSuccess) {
            refreshWithdrawals()
            refreshWalletSummary(handle)
        }
        result
    }

    /** Pulls the authenticated user's transaction history. */
    suspend fun refreshWalletTransactions(userHandle: String? = null): List<WalletTransaction> = withContext(Dispatchers.IO) {
        val result = rewardManager.fetchWalletTransactions(userHandle)
        _walletTransactions.value = result
        result
    }

    /** Admin: refresh platform-wide wallet overview. */
    suspend fun refreshPlatformOverview(): PlatformWalletOverview = withContext(Dispatchers.IO) {
        _platformOverview.value = _platformOverview.value.copy(loading = true, error = "")
        val result = rewardManager.fetchAdminWalletOverview()
        _platformOverview.value = result.copy(loading = false)
        result
    }

    /** Admin: refresh per-user earnings overview. */
    suspend fun refreshUserEarnings(): List<UserEarningsOverview> = withContext(Dispatchers.IO) {
        val result = rewardManager.fetchUserEarningsOverview()
        _userEarnings.value = result
        result
    }

    /** Admin: refresh audit log. */
    suspend fun refreshAuditLogs(): List<WalletAuditLog> = withContext(Dispatchers.IO) {
        val result = rewardManager.fetchAdminAuditLog()
        _auditLogs.value = result
        result
    }

    /** Admin: refresh fraud flags. */
    suspend fun refreshFraudFlags(includeResolved: Boolean = false): List<WalletFraudFlag> = withContext(Dispatchers.IO) {
        val result = rewardManager.fetchFraudFlags(includeResolved)
        _fraudFlags.value = result
        result
    }

    /** Super Admin: run fraud detection. */
    suspend fun runFraudDetection(): List<WalletFraudFlag> = withContext(Dispatchers.IO) {
        val result = rewardManager.detectSuspiciousActivity()
        _fraudFlags.value = result
        result
    }

    /** Super Admin: manual wallet adjustment (mandatory reason, audited). */
    suspend fun adminAdjustWallet(target: String, amount: Int, reason: String, type: String): Result<String> =
        rewardManager.adminAdjustWallet(target, amount, reason, type)

    /** Staff: reject a withdrawal with atomically-refunded balance. */
    suspend fun adminRejectWithdrawalV2(id: String, reason: String): Result<String> {
        val result = rewardManager.adminRejectWithdrawalV2(id, reason)
        if (result.isSuccess) refreshWithdrawals()
        return result
    }

    /** Super Admin: resolve a fraud flag. */
    suspend fun adminResolveFraudFlag(flagId: String, reason: String): Result<String> {
        val result = rewardManager.adminResolveFraudFlag(flagId, reason)
        if (result.isSuccess) refreshFraudFlags()
        return result
    }
}
