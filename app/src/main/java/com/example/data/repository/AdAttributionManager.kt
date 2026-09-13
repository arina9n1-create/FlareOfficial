package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.ContentAdImpression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized Ad Attribution Manager for FlareOfficial Native Android
 * Responsibilities:
 * - Identifies content context (Post, Video, Reel)
 * - Listens for valid AdMob impression events
 * - Prevents client duplication and rapid re-logging
 * - Manages offline queue
 * - Dispatches valid impression events to MonetizationRepository
 */
class AdAttributionManager(
    private val context: Context,
    private val monetizationRepository: MonetizationRepository
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("flareofficial_ad_attribution_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    // Deduplication tracker: contentId_placement -> lastImpressionTimestamp
    private val recentImpressions = ConcurrentHashMap<String, Long>()

    private val _queuedEventCount = MutableStateFlow(loadQueuedCount())
    val queuedEventCount: StateFlow<Int> = _queuedEventCount.asStateFlow()

    init {
        // Attempt flush on startup
        flushOfflineQueue()
    }

    private fun loadQueuedCount(): Int {
        val raw = prefs.getString("offline_attribution_queue", null) ?: return 0
        return try {
            JSONArray(raw).length()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Record a valid Ad Impression event for a specific Content item
     * Must ONLY be invoked when the Ad has actually rendered/displayed via supported AdMob callback.
     * Never for page opens, view counts, requests, or clicks.
     */
    fun recordContentAdImpression(
        contentId: String,
        creatorId: String,
        contentType: String, // "POST", "VIDEO", "REEL"
        placement: String,   // "CONTENT_FEED_NATIVE", "VIDEO_NATIVE", "REELS_NATIVE", etc.
        adUnitId: String = "",
        countryCode: String = "GLOBAL"
    ) {
        scope.launch {
            try {
                if (contentId.isBlank() || creatorId.isBlank()) {
                    Log.w(TAG, "Rejected impression: missing contentId or creatorId")
                    monetizationRepository.recordAttributionEvent(null, isValid = false, reason = "MALFORMED_IDENTIFIERS")
                    return@launch
                }

                // Anti-duplication check: 5-second cooldown per content placement
                val dedupeKey = "${contentId}_${placement}"
                val now = System.currentTimeMillis()
                val lastLogged = recentImpressions[dedupeKey] ?: 0L
                if (now - lastLogged < 5000L) {
                    Log.d(TAG, "Duplicate ad impression throttled for $dedupeKey")
                    monetizationRepository.recordAttributionEvent(null, isValid = false, reason = "DUPLICATE_THROTTLED")
                    return@launch
                }
                recentImpressions[dedupeKey] = now

                // Clean memory map if it grows too large
                if (recentImpressions.size > 200) {
                    val cutoff = now - 60000L
                    recentImpressions.entries.removeIf { it.value < cutoff }
                }

                val impression = ContentAdImpression(
                    id = UUID.randomUUID().toString(),
                    contentId = contentId,
                    creatorId = creatorId,
                    contentType = contentType.uppercase(),
                    adUnitId = adUnitId,
                    placement = placement,
                    sessionId = UUID.randomUUID().toString().take(8),
                    impressionReference = "IMP-${now}-${UUID.randomUUID().toString().take(6)}",
                    occurredAt = now,
                    dateBucket = SimpleDateBucket(now),
                    countryCode = countryCode,
                    isValid = true,
                    rejectionReason = ""
                )

                // Dispatch to repository
                val success = monetizationRepository.recordAttributionEvent(impression, isValid = true)
                if (!success) {
                    queueEventOffline(impression)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error recording ad impression: ${e.message}", e)
            }
        }
    }

    private fun SimpleDateBucket(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        return sdf.format(java.util.Date(timestamp))
    }

    private fun queueEventOffline(impression: ContentAdImpression) {
        try {
            val raw = prefs.getString("offline_attribution_queue", null)
            val jsonArray = if (raw != null) JSONArray(raw) else JSONArray()

            val obj = JSONObject().apply {
                put("id", impression.id)
                put("contentId", impression.contentId)
                put("creatorId", impression.creatorId)
                put("contentType", impression.contentType)
                put("adUnitId", impression.adUnitId)
                put("placement", impression.placement)
                put("sessionId", impression.sessionId)
                put("impressionReference", impression.impressionReference)
                put("occurredAt", impression.occurredAt)
                put("dateBucket", impression.dateBucket)
                put("countryCode", impression.countryCode)
            }
            jsonArray.put(obj)

            prefs.edit().putString("offline_attribution_queue", jsonArray.toString()).apply()
            _queuedEventCount.value = jsonArray.length()
            Log.d(TAG, "Queued offline attribution event. Total in queue: ${jsonArray.length()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue offline event: ${e.message}")
        }
    }

    fun flushOfflineQueue() {
        scope.launch {
            try {
                val raw = prefs.getString("offline_attribution_queue", null) ?: return@launch
                val jsonArray = JSONArray(raw)
                if (jsonArray.length() == 0) return@launch

                val remaining = JSONArray()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val impression = ContentAdImpression(
                        id = obj.optString("id", UUID.randomUUID().toString()),
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
                        isValid = true
                    )

                    val processed = monetizationRepository.recordAttributionEvent(impression, isValid = true)
                    if (!processed) {
                        remaining.put(obj)
                    }
                }

                prefs.edit().putString("offline_attribution_queue", remaining.toString()).apply()
                _queuedEventCount.value = remaining.length()
            } catch (e: Exception) {
                Log.e(TAG, "Failed flushing offline queue: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "AdAttributionMgr"
    }
}
