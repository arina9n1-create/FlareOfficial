package com.example.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.screens.IncomingCallActivity
import java.util.concurrent.atomic.AtomicInteger

object NotificationHelper {

    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_CALLS = "calls"

    /**
     * Dedicated channel for the SYSTEM-displayed incoming-call push (sent by
     * Google Play Services while the app process is dead/swiped away). Unlike
     * [CHANNEL_CALLS] — which stays silent because CallRingingService plays
     * its own ringtone while the process is alive — this channel carries the
     * default ringtone so a missed-process call actually rings.
     */
    const val CHANNEL_CALLS_PUSH = "calls_push"

    const val EXTRA_TARGET_SCREEN = "extra_target_screen"
    const val EXTRA_CHAT_HANDLE = "extra_chat_handle"
    const val EXTRA_CHAT_NAME = "extra_chat_name"
    const val EXTRA_CHAT_AVATAR = "extra_chat_avatar"
    const val EXTRA_CALLER_HANDLE = "extra_caller_handle"
    const val EXTRA_AGORA_CHANNEL = "extra_agora_channel"

    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CALLER_NAME = "extra_caller_name"
    const val EXTRA_AGORA_TOKEN = "extra_agora_token"
    const val EXTRA_CALL_TYPE = "extra_call_type"

    const val SCREEN_CHAT = "screen_chat"
    const val SCREEN_NOTIFICATIONS = "screen_notifications"
    const val SCREEN_REWARDS = "screen_rewards"
    const val SCREEN_ADMIN = "screen_admin"
    const val SCREEN_INCOMING_CALL = "screen_incoming_call"
    const val SCREEN_GENERAL = "screen_general"

    private val notificationIdGenerator = AtomicInteger(1000)

    /**
     * Guard used by every local notification path. Instead of silently dropping
     * a notification when POST_NOTIFICATIONS permission is missing, it logs the
     * specific reason so background push failures are diagnosable. Returns true
     * only when a system notification may actually be shown.
     */
    private fun canNotify(context: Context, tag: String): Boolean {
        if (!hasNotificationPermission(context)) {
            Log.w("NotificationHelper", "$tag dropped: POST_NOTIFICATIONS permission is not granted")
            return false
        }
        return true
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val msgChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat and direct messages"
                enableLights(true)
                lightColor = Color.BLUE
                setShowBadge(true)
            }

            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming voice and video calls"
                setSound(null, null) 
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            val callPushChannel = NotificationChannel(
                CHANNEL_CALLS_PUSH,
                "Incoming Calls (app closed)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Rings when an incoming call arrives while the app is closed or in background"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                enableLights(true)
                lightColor = Color.BLUE
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            manager.createNotificationChannels(listOf(msgChannel, callChannel, callPushChannel))
        }
    }

    fun showChatNotification(
        context: Context,
        senderHandle: String,
        senderName: String,
        messageText: String,
        avatarType: String = "default"
    ) {
        if (!canNotify(context, "showChatNotification")) {
            Log.w("NotificationHelper", "showChatNotification blocked (from $senderHandle)")
            return
        }
        // Respect the user's in-app "Direct Messages" / "Pause All" toggles
        // (Settings → Notifications & Alerts). Previously these switches were
        // never enforced anywhere.
        if (!NotificationPreferences.isDirectMessagesEnabled(context)) {
            Log.w("NotificationHelper", "showChatNotification dropped: Direct Messages notifications disabled in app settings")
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_CHAT)
            putExtra(EXTRA_CHAT_HANDLE, senderHandle)
            putExtra(EXTRA_CHAT_NAME, senderName)
            putExtra(EXTRA_CHAT_AVATAR, avatarType)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, senderHandle.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(Math.abs(senderHandle.hashCode()), builder.build())
        } catch (e: SecurityException) {
            Log.w("NotificationHelper", "showChatNotification notify failed: ${e.message}")
        }
    }

    fun showRewardNotification(context: Context, title: String, message: String, isPositive: Boolean = true) {
        if (!canNotify(context, "showRewardNotification")) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_REWARDS)
        }
        val pendingIntent = PendingIntent.getActivity(context, notificationIdGenerator.incrementAndGet(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        try { NotificationManagerCompat.from(context).notify(notificationIdGenerator.incrementAndGet(), builder.build()) } catch (e: SecurityException) {
            Log.w("NotificationHelper", "showRewardNotification notify failed: ${e.message}")
        }
    }

    fun showSocialNotification(context: Context, title: String, message: String, avatarType: String = "default") {
        if (!canNotify(context, "showSocialNotification")) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_NOTIFICATIONS)
        }
        val pendingIntent = PendingIntent.getActivity(context, notificationIdGenerator.incrementAndGet(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        try { NotificationManagerCompat.from(context).notify(notificationIdGenerator.incrementAndGet(), builder.build()) } catch (e: SecurityException) {
            Log.w("NotificationHelper", "showSocialNotification notify failed: ${e.message}")
        }
    }

    fun showAdminNotification(context: Context, title: String, message: String) {
        if (!canNotify(context, "showAdminNotification")) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_NOTIFICATIONS)
        }
        val pendingIntent = PendingIntent.getActivity(context, notificationIdGenerator.incrementAndGet(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        try { NotificationManagerCompat.from(context).notify(notificationIdGenerator.incrementAndGet(), builder.build()) } catch (e: SecurityException) {
            Log.w("NotificationHelper", "showAdminNotification notify failed: ${e.message}")
        }
    }

    fun showGeneralNotification(context: Context, title: String, message: String) {
        if (!canNotify(context, "showGeneralNotification")) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_NOTIFICATIONS)
        }
        val pendingIntent = PendingIntent.getActivity(context, notificationIdGenerator.incrementAndGet(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        try { NotificationManagerCompat.from(context).notify(notificationIdGenerator.incrementAndGet(), builder.build()) } catch (e: SecurityException) {
            Log.w("NotificationHelper", "showGeneralNotification notify failed: ${e.message}")
        }
    }

    fun getIncomingCallNotification(
        context: Context,
        callerName: String,
        callType: String,
        callId: String,
        agoraChannel: String,
        callerHandle: String = ""
    ): Notification {
        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_CALL_TYPE, callType)
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_AGORA_CHANNEL, agoraChannel)
            putExtra(EXTRA_CALLER_HANDLE, callerHandle)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action
        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_INCOMING_CALL)
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_AGORA_CHANNEL, agoraChannel)
            putExtra(EXTRA_CALL_TYPE, callType)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_CALLER_HANDLE, callerHandle)
            action = "ACCEPT_CALL"
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context, 1, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Action
        val declineIntent = Intent(context, CallRingingService::class.java).apply {
            action = "DECLINE_CALL"
            putExtra(EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getService(
            context, 2, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        return NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming $callType Call")
            .setContentText("$callerName is calling you")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setSound(ringtoneUri)
            .setVibrate(longArrayOf(0, 500, 500, 500))
            .addAction(R.drawable.ic_launcher_foreground, "Decline", declinePendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Accept", acceptPendingIntent)
            .build()
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }
}
