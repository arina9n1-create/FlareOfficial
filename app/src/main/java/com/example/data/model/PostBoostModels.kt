package com.example.data.model

import java.util.UUID

/**
 * Post & Content Boosting Campaign Model
 */
data class PostBoostCampaign(
    val id: String = "BOOST-${UUID.randomUUID().toString().take(6).uppercase()}",
    val postId: Long,
    val postTitle: String,
    val postImageRes: String,
    val creatorHandle: String = "",
    val budgetBdt: Double = 500.0,
    val budgetUsd: Double = 4.16,
    val durationDays: Int = 3,
    val targetAudience: String = "All Bangladesh (18-35)",
    val goal: String = "Reach & Engagement",
    val status: String = "ACTIVE", // ACTIVE, COMPLETED, PAUSED
    val impressionsDelivered: Int = 1240,
    val estimatedReach: String = "8,500 - 25,000",
    val clicksDelivered: Int = 186,
    val paymentGateway: String = "bKash Merchant",
    val transactionId: String = "TRX-BK-${System.currentTimeMillis().toString().takeLast(6)}",
    val createdAt: Long = System.currentTimeMillis()
)
