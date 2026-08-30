package com.example.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * Helper to initialize notification channels, format system push notifications,
 * and dispatch heads-up alerts with deep-link navigation actions.
 */
object NotificationHelper {

    const val CHANNEL_DIRECT_MESSAGES = "vyn9_channel_direct_messages"
    const val CHANNEL_SOCIAL_ALERTS = "vyn9_channel_social_alerts"
    const val CHANNEL_REWARDS_PAYOUTS = "vyn9_channel_rewards_payouts"
    const val CHANNEL_ADMIN_ALERTS = "vyn9_channel_admin_alerts"

    const val EXTRA_TARGET_SCREEN = "extra_target_screen"
    const val EXTRA_CHAT_HANDLE = "extra_chat_handle"
    const val EXTRA_CHAT_NAME = "extra_chat_name"
    const val EXTRA_CHAT_AVATAR = "extra_chat_avatar"

    const val SCREEN_CHAT = "screen_chat"
    const val SCREEN_NOTIFICATIONS = "screen_notifications"
    const val SCREEN_REWARDS = "screen_rewards"
    const val SCREEN_ADMIN = "screen_admin"

    private val notificationIdGenerator = AtomicInteger(1000)

    /**
     * Initializes all Material 3 Notification Channels on Android 8.0+ (API 26+)
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Direct Messages & Chats Channel (High priority, Sound, Vibration, Pop-up)
            val chatChannel = NotificationChannel(
                CHANNEL_DIRECT_MESSAGES,
                "Direct Messages & Chats",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming chat messages from friends and connections"
                enableLights(true)
                lightColor = Color.parseColor("#4A90E2")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                setShowBadge(true)
            }

            // 2. Social Interactions Channel (Likes, Comments, Followers)
            val socialChannel = NotificationChannel(
                CHANNEL_SOCIAL_ALERTS,
                "Social Interactions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for likes, comments, mentions, and new followers"
                enableLights(true)
                lightColor = Color.parseColor("#E91E63")
                enableVibration(true)
                setShowBadge(true)
            }

            // 3. Rewards & Cashouts Channel (High priority)
            val rewardsChannel = NotificationChannel(
                CHANNEL_REWARDS_PAYOUTS,
                "Rewards & Cashout Payouts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Cashout status updates (bKash/Nagad), earning milestones, and bonuses"
                enableLights(true)
                lightColor = Color.parseColor("#4CAF50")
                enableVibration(true)
                setShowBadge(true)
            }

            // 4. Admin & System Broadcasts Channel
            val adminChannel = NotificationChannel(
                CHANNEL_ADMIN_ALERTS,
                "System & Admin Broadcasts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Official announcements, moderation notices, and security updates"
                enableLights(true)
                lightColor = Color.parseColor("#6C5CE7")
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(listOf(chatChannel, socialChannel, rewardsChannel, adminChannel))
        }
    }

    /**
     * Dispatches a Direct Message chat push notification.
     */
    fun showChatNotification(
        context: Context,
        senderHandle: String,
        senderName: String,
        messageText: String,
        avatarType: String = "default"
    ) {
        if (!NotificationPreferences.isDirectMessagesEnabled(context)) return
        if (!hasNotificationPermission(context)) return

        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_CHAT)
            putExtra(EXTRA_CHAT_HANDLE, senderHandle)
            putExtra(EXTRA_CHAT_NAME, senderName)
            putExtra(EXTRA_CHAT_AVATAR, avatarType)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            senderHandle.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val soundEnabled = NotificationPreferences.isSoundVibrationEnabled(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_DIRECT_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (soundEnabled) {
            builder.setSound(defaultSoundUri)
            builder.setVibrate(longArrayOf(0, 200, 100, 200))
        } else {
            builder.setSilent(true)
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            val notificationId = Math.abs(senderHandle.hashCode()) % 10000 + 100
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Dispatches a Rewards or Cashout milestone push notification.
     */
    fun showRewardNotification(
        context: Context,
        title: String,
        message: String,
        isPositive: Boolean = true
    ) {
        if (!NotificationPreferences.isRewardAlertsEnabled(context)) return
        if (!hasNotificationPermission(context)) return

        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_REWARDS)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationIdGenerator.incrementAndGet(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val soundEnabled = NotificationPreferences.isSoundVibrationEnabled(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_REWARDS_PAYOUTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (soundEnabled) {
            builder.setSound(defaultSoundUri)
        } else {
            builder.setSilent(true)
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationIdGenerator.incrementAndGet(), builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Dispatches a Social Interaction push notification (Like, Comment, Follower).
     */
    fun showSocialNotification(
        context: Context,
        title: String,
        message: String,
        avatarType: String = "default"
    ) {
        if (NotificationPreferences.isPauseAll(context)) return
        if (!hasNotificationPermission(context)) return

        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_NOTIFICATIONS)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationIdGenerator.incrementAndGet(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val soundEnabled = NotificationPreferences.isSoundVibrationEnabled(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_SOCIAL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (soundEnabled) {
            builder.setSound(defaultSoundUri)
        } else {
            builder.setSilent(true)
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationIdGenerator.incrementAndGet(), builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Dispatches an Official Admin Broadcast push notification.
     */
    fun showAdminNotification(
        context: Context,
        title: String,
        message: String
    ) {
        if (!hasNotificationPermission(context)) return

        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_NOTIFICATIONS)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationIdGenerator.incrementAndGet(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ADMIN_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationIdGenerator.incrementAndGet(), builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Checks whether POST_NOTIFICATIONS permission is granted on Android 13+ (API 33+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
