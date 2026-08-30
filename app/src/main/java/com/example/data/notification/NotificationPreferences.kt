package com.example.data.notification

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralized preferences for notification settings, FCM token storage, and muted states.
 */
object NotificationPreferences {
    private const val PREFS_NAME = "vyn9_notification_prefs"

    private const val KEY_PAUSE_ALL = "pause_all_notifications"
    private const val KEY_LIKES_REACTIONS = "notify_likes_reactions"
    private const val KEY_COMMENTS_REPLIES = "notify_comments_replies"
    private const val KEY_NEW_FOLLOWERS = "notify_new_followers"
    private const val KEY_DIRECT_MESSAGES = "notify_direct_messages"
    private const val KEY_REWARD_ALERTS = "notify_reward_alerts"
    private const val KEY_MENTIONS_TAGS = "notify_mentions_tags"
    private const val KEY_SOUND_VIBRATION = "notify_sound_vibration"
    private const val KEY_FCM_TOKEN = "fcm_registration_token"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isPauseAll: Boolean
        get() = false
        set(value) {}

    fun isPauseAll(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_PAUSE_ALL, false)

    fun setPauseAll(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PAUSE_ALL, value).apply()
    }

    fun isLikesEnabled(context: Context): Boolean =
        !isPauseAll(context) && getPrefs(context).getBoolean(KEY_LIKES_REACTIONS, true)

    fun setLikesEnabled(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LIKES_REACTIONS, value).apply()
    }

    fun isCommentsEnabled(context: Context): Boolean =
        !isPauseAll(context) && getPrefs(context).getBoolean(KEY_COMMENTS_REPLIES, true)

    fun setCommentsEnabled(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_COMMENTS_REPLIES, value).apply()
    }

    fun isFollowersEnabled(context: Context): Boolean =
        !isPauseAll(context) && getPrefs(context).getBoolean(KEY_NEW_FOLLOWERS, true)

    fun setFollowersEnabled(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NEW_FOLLOWERS, value).apply()
    }

    fun isDirectMessagesEnabled(context: Context): Boolean =
        !isPauseAll(context) && getPrefs(context).getBoolean(KEY_DIRECT_MESSAGES, true)

    fun setDirectMessagesEnabled(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DIRECT_MESSAGES, value).apply()
    }

    fun isRewardAlertsEnabled(context: Context): Boolean =
        !isPauseAll(context) && getPrefs(context).getBoolean(KEY_REWARD_ALERTS, true)

    fun setRewardAlertsEnabled(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REWARD_ALERTS, value).apply()
    }

    fun isMentionsEnabled(context: Context): Boolean =
        !isPauseAll(context) && getPrefs(context).getBoolean(KEY_MENTIONS_TAGS, true)

    fun setMentionsEnabled(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MENTIONS_TAGS, value).apply()
    }

    fun isSoundVibrationEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_SOUND_VIBRATION, true)

    fun setSoundVibrationEnabled(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SOUND_VIBRATION, value).apply()
    }

    fun getFcmToken(context: Context): String? =
        getPrefs(context).getString(KEY_FCM_TOKEN, null)

    fun setFcmToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_FCM_TOKEN, token).apply()
    }
}
