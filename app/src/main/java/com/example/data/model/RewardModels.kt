package com.example.data.model

enum class AppCurrency(val symbol: String, val code: String, val displayName: String) {
    USD("$", "USD", "US Dollar ($)"),
    BDT("৳", "BDT", "Bangladeshi Taka (৳)")
}

data class PaymentMethodItem(
    val id: String,
    val name: String,
    val type: String = "MOBILE_BANKING", // "MOBILE_BANKING", "CRYPTO", "ONLINE", "BANK"
    val iconType: String = "bkash", // "bkash", "nagad", "rocket", "binance", "paypal", "bank", "generic"
    val minWithdrawalUSD: Double = 1.0,
    val instructions: String = "Enter your active account / wallet number",
    val isEnabled: Boolean = true
)

fun defaultPaymentMethodsList(): List<PaymentMethodItem> = listOf(
    PaymentMethodItem(
        id = "pm_recharge",
        name = "Mobile Recharge (মোবাইল রিচার্জ)",
        type = "MOBILE_RECHARGE",
        iconType = "recharge",
        minWithdrawalUSD = 0.20,
        instructions = "Grameenphone, Banglalink, Robi, Airtel, Teletalk & Skitto (Prepaid/Postpaid)",
        isEnabled = true
    ),
    PaymentMethodItem(
        id = "pm_bkash",
        name = "bKash (বিকাশ)",
        type = "MOBILE_BANKING",
        iconType = "bkash",
        minWithdrawalUSD = 1.0,
        instructions = "Enter 11-digit bKash Personal Number (01XXXXXXXXX)",
        isEnabled = true
    ),
    PaymentMethodItem(
        id = "pm_nagad",
        name = "Nagad (নগদ)",
        type = "MOBILE_BANKING",
        iconType = "nagad",
        minWithdrawalUSD = 1.0,
        instructions = "Enter 11-digit Nagad Personal Number (01XXXXXXXXX)",
        isEnabled = true
    ),
    PaymentMethodItem(
        id = "pm_rocket",
        name = "Rocket (রকেট)",
        type = "MOBILE_BANKING",
        iconType = "rocket",
        minWithdrawalUSD = 1.0,
        instructions = "Enter 12-digit Rocket Account Number",
        isEnabled = true
    ),
    PaymentMethodItem(
        id = "pm_binance",
        name = "Binance USDT (TRC20 / Pay ID)",
        type = "CRYPTO",
        iconType = "binance",
        minWithdrawalUSD = 2.0,
        instructions = "Enter Binance Pay ID or USDT TRC20 Address",
        isEnabled = true
    ),
    PaymentMethodItem(
        id = "pm_paypal",
        name = "PayPal (Global)",
        type = "ONLINE",
        iconType = "paypal",
        minWithdrawalUSD = 5.0,
        instructions = "Enter registered PayPal Email Address",
        isEnabled = true
    ),
    PaymentMethodItem(
        id = "pm_bank",
        name = "Bank Transfer (BD)",
        type = "BANK",
        iconType = "bank",
        minWithdrawalUSD = 10.0,
        instructions = "Enter Bank Name, Branch, Account Holder Name & Account Number",
        isEnabled = true
    )
)

data class RewardTask(
    val id: String,
    val title: String,
    val description: String,
    val rewardCredits: Int,
    val requiredCount: Int = 1,
    val currentCount: Int = 0,
    val isCompleted: Boolean = false,
    val isClaimed: Boolean = false,
    val dayNumber: Int = 1, // 1..7 for 7-day challenge, 0 for daily tasks
    val isDailyTask: Boolean = false,
    val actionType: String = "WATCH_REELS" // "LOGIN", "WATCH_REELS", "CREATE_POST", "INVITE_FRIEND", "LIKE_POSTS", "CUSTOM"
)

data class WithdrawalRequest(
    val id: String,
    val userHandle: String,
    val userEmail: String,
    val method: String, // "bKash", "Nagad", "Rocket", "Binance USDT", "PayPal", etc.
    val accountNumber: String,
    val creditsUsed: Int,
    val amountUSD: Double,
    val amountBDT: Double = 0.0,
    val status: String = "PENDING", // "PENDING", "APPROVED", "PAID", "REJECTED"
    val requestDate: String,
    val transactionNote: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class AdminConfig(
    val dailyTaskRequiredWatchReels: Int = 10,
    val dailyTaskRequiredUploadReels: Int = 5,
    val dailyTaskRewardCredits: Int = 200,
    val referralBonusCredits: Int = 500,
    val referralRequiredDailyTaskDays: Int = 2,
    val creditsPerReel: Int = 2,
    val requiredReelWatchSeconds: Int = 10,
    val creditsPerDollar: Int = 2000,
    val usdToBdtRate: Double = 120.0, // 1 USD = 120.0 BDT (Admin editable)
    val minWithdrawalUSD: Double = 1.0,
    val isBkashEnabled: Boolean = true,
    val isNagadEnabled: Boolean = true,
    val isRocketEnabled: Boolean = true,
    val isBinanceEnabled: Boolean = true,
    val isPaypalEnabled: Boolean = true,
    val customPaymentMethods: List<PaymentMethodItem> = defaultPaymentMethodsList(),
    val noticeMessage: String = "Payments are processed within 12-24 hours. Watch reels, upload content & invite friends to earn real money!"
)

data class ReferralRecord(
    val id: String,
    val refereeHandle: String,
    val refereeName: String = "",
    val bonusCredits: Int = 500,
    val dailyTasksCompleted: Int = 0,
    val requiredDays: Int = 2,
    val status: String = "PENDING", // "PENDING", "COMPLETED", "CLAIMED"
    val date: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class UserRewardWallet(
    val totalCredits: Int = 0,
    val todayReelsWatched: Int = 0,
    val todayReelsUploaded: Int = 0,
    val totalReelsWatched: Int = 0,
    val totalReelsUploaded: Int = 0,
    val referralCode: String = "VYN9WIN",
    val referredUsersCount: Int = 0,
    val referralEarnings: Int = 0,
    val pendingReferralEarnings: Int = 0,
    val pendingReferralsCount: Int = 0,
    val totalDailyTasksCompletedDays: Int = 0,
    val accountAgeDays: Int = 1,
    val currentStreakDay: Int = 1,
    val lastLoginDate: String = "",
    val referredByCode: String = ""
)

