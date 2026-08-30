package com.example.data.model

import java.util.UUID

/**
 * Monetization Settings configured by Administrator in Admin Panel
 */
data class MonetizationSettings(
    val enableMonetization: Boolean = true,
    val enableMonetizationApplication: Boolean = true,
    val enableContentMonetization: Boolean = true,
    val enablePostMonetization: Boolean = true,
    val enableVideoMonetization: Boolean = true,
    val enableReelsMonetization: Boolean = true,
    val minimumFollowers: Int = 1000,
    val minimumViews: Int = 10000,
    val enableEarningsWallet: Boolean = true,
    val enableAddFund: Boolean = true,
    val enableWithdraw: Boolean = true,
    val minimumAddFund: Double = 5.0,
    val maximumAddFund: Double = 1000.0,
    val minimumWithdrawal: Double = 10.0,
    val maximumWithdrawal: Double = 5000.0,
    // Phase 2: Revenue Allocation & Settings
    val creatorRevenueShare: Double = 50.0,   // % of AdMob revenue given to creator pool (e.g. 50%)
    val platformRevenueShare: Double = 50.0,  // % kept by platform (must sum to 100%)
    val postRevenuePoolShare: Double = 30.0,  // % of Creator Pool allocated to Posts (30%)
    val videoRevenuePoolShare: Double = 40.0, // % of Creator Pool allocated to Videos (40%)
    val reelsRevenuePoolShare: Double = 30.0, // % of Creator Pool allocated to Reels (30%)
    val allowEstimatedEarnings: Boolean = true,
    val autoFinalizeRevenue: Boolean = false,
    val revenueCurrency: String = "USD",
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "admin"
)

/**
 * User's Monetization Status and Profile
 */
data class UserMonetizationProfile(
    val userId: String = "",
    val userHandle: String = "",
    val userName: String = "",
    val monetizationStatus: String = "NOT_MONETIZED", // "NOT_MONETIZED", "PENDING", "APPROVED", "REJECTED"
    val applicationStatus: String = "NOT_APPLIED",   // "NOT_APPLIED", "PENDING", "APPROVED", "REJECTED"
    val approvedAt: Long? = null,
    val rejectedAt: Long? = null,
    val rejectionReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Application submitted by User to apply for Monetization
 */
data class MonetizationApplication(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val userHandle: String,
    val userName: String,
    val userAvatarType: String = "default",
    val followerCountAtApplication: Int,
    val viewCountAtApplication: Int,
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED"
    val rejectionReason: String = "",
    val reviewedBy: String? = null,
    val reviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Real-money Earnings Wallet for creators
 * IMPORTANT: Add Fund money does NOT increase Lifetime Earnings.
 * Lifetime Earnings represents only money earned through Vyn9 monetization systems.
 */
data class EarningsWallet(
    val userId: String = "",
    val availableBalance: Double = 0.00,
    val lifetimeEarnings: Double = 0.00,
    val totalAddedFunds: Double = 0.00,
    val totalWithdrawn: Double = 0.00,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Monetization Financial Transaction Record
 */
data class MonetizationTransaction(
    val id: String = UUID.randomUUID().toString().take(8).uppercase(),
    val userId: String = "",
    val userHandle: String = "",
    val type: String, // "CREATOR_EARNING", "ADD_FUND", "WITHDRAWAL", "REFUND", "ADJUSTMENT"
    val amount: Double,
    val status: String = "COMPLETED", // "COMPLETED", "PENDING", "FAILED", "REJECTED"
    val referenceId: String = "TXN-${System.currentTimeMillis().toString().takeLast(6)}",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Revenue Period System (e.g. 2026-08-01 -> 2026-08-31)
 */
data class RevenuePeriod(
    val id: String, // e.g. "PERIOD-2026-08"
    val name: String = "August 2026 Revenue Cycle",
    val startDate: String = "2026-08-01",
    val endDate: String = "2026-08-31",
    val status: String = "OPEN", // "OPEN", "IMPORTED", "CALCULATING", "CALCULATED", "FINALIZED", "ADJUSTED"
    val totalAdMobRevenue: Double = 0.0,
    val totalCreatorPool: Double = 0.0,
    val totalPlatformRevenue: Double = 0.0,
    val postPool: Double = 0.0,
    val videoPool: Double = 0.0,
    val reelsPool: Double = 0.0,
    val totalEligibleImpressions: Long = 0,
    val postImpressions: Long = 0,
    val videoImpressions: Long = 0,
    val reelsImpressions: Long = 0,
    val currency: String = "USD",
    val importedAt: Long? = null,
    val finalizedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * AdMob Revenue Report imported from server-side AdMob reporting data
 */
data class AdMobRevenueReport(
    val id: String = UUID.randomUUID().toString(),
    val revenuePeriodId: String,
    val reportDate: String,
    val appId: String = "ca-app-pub-vyn9-prod",
    val adUnitId: String,
    val adFormat: String, // "NATIVE", "BANNER", "INTERSTITIAL", "REWARDED"
    val countryCode: String = "GLOBAL",
    val impressions: Long = 0,
    val estimatedEarnings: Double = 0.0,
    val currency: String = "USD",
    val source: String = "ADMOB_REPORTING_API",
    val importedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Configurable AdMob Ad Unit Mapping to Vyn9 Placements & Content Types
 */
data class AdMobAdUnitMapping(
    val id: String = UUID.randomUUID().toString(),
    val adUnitId: String,
    val placement: String, // "CONTENT_FEED_NATIVE", "CONTENT_FEED_BANNER", "VIDEO_NATIVE", "VIDEO_BANNER", "REELS_NATIVE", "REELS_BANNER"
    val contentType: String, // "POST", "VIDEO", "REEL"
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Content Ad Impression Attribution Record
 * Captured strictly on valid AdMob impression events
 */
data class ContentAdImpression(
    val id: String = UUID.randomUUID().toString(),
    val contentId: String,
    val creatorId: String,
    val contentType: String, // "POST", "VIDEO", "REEL"
    val adUnitId: String,
    val placement: String,
    val sessionId: String = UUID.randomUUID().toString().take(8),
    val impressionReference: String = "IMP-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
    val occurredAt: Long = System.currentTimeMillis(),
    val dateBucket: String = "2026-08",
    val countryCode: String = "GLOBAL",
    val isValid: Boolean = true,
    val rejectionReason: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Content-Level Revenue Attribution
 * Calculated from: AdMob Revenue Pool × Content Attribution Share
 */
data class ContentEarning(
    val id: String = UUID.randomUUID().toString(),
    val revenuePeriodId: String,
    val contentId: String,
    val creatorId: String,
    val userHandle: String = "",
    val contentType: String, // "POST", "VIDEO", "REEL"
    val contentTitle: String = "Content Title",
    val contentThumbnailRes: String = "default",
    val contentViews: Long = 0,
    val eligibleImpressions: Long = 0,
    val totalTypeImpressions: Long = 0,
    val attributionShare: Double = 0.0, // e.g. 0.0542 (5.42%)
    val allocatedRevenue: Double = 0.0, // Gross pool allocated before creator split
    val creatorShare: Double = 0.0, // Net creator earning (at least 4 decimals internally)
    val platformShare: Double = 0.0,
    val status: String = "ESTIMATED", // "ESTIMATED", "FINALIZED", "ADJUSTED", "PAID"
    val currency: String = "USD",
    val calculatedAt: Long = System.currentTimeMillis(),
    val finalizedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Revenue Adjustment Record for AdMob audit changes
 */
data class RevenueAdjustment(
    val id: String = UUID.randomUUID().toString(),
    val revenuePeriodId: String,
    val contentEarningId: String,
    val creatorId: String,
    val previousAmount: Double,
    val adjustmentAmount: Double,
    val newAmount: Double,
    val reason: String,
    val source: String = "ADMOB_AUDIT",
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "admin"
)

/**
 * Attribution Diagnostics & Fraud Protection Overview
 */
data class AttributionDiagnostics(
    val totalEvents: Long = 0,
    val validEvents: Long = 0,
    val rejectedEvents: Long = 0,
    val duplicateEvents: Long = 0,
    val suspiciousEvents: Long = 0,
    val queuedOfflineEvents: Int = 0,
    val lastSyncTime: Long = System.currentTimeMillis()
)

/**
 * Creator Earnings Summary Aggregate
 */
data class CreatorEarningsSummary(
    val userId: String,
    val userHandle: String,
    val userName: String,
    val postsEarnings: Double = 0.0,
    val videosEarnings: Double = 0.0,
    val reelsEarnings: Double = 0.0,
    val estimatedEarnings: Double = 0.0,
    val finalizedEarnings: Double = 0.0,
    val availableBalance: Double = 0.0,
    val lifetimeEarnings: Double = 0.0,
    val totalWithdrawn: Double = 0.0,
    val totalImpressions: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

