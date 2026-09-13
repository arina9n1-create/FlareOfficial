package com.example.data.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.example.data.remote.SupabaseService
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FlareFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d(TAG, "FCM token refreshed")

        NotificationPreferences.setFcmToken(
            applicationContext,
            token
        )

        saveTokenToBackend(
            applicationContext,
            token
        )
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data

        Log.d(
            TAG,
            "FCM_MESSAGE_RECEIVED data=$data"
        )

        val type = data["type"]?.trim()?.lowercase()
            ?: data[NotificationHelper.EXTRA_TARGET_SCREEN]?.trim()?.lowercase()
            ?: ""

        // =========================================================
        // INCOMING CALL
        // =========================================================

        if (type == "call" || type == NotificationHelper.SCREEN_INCOMING_CALL) {

            val callerName = data[NotificationHelper.EXTRA_CALLER_NAME]
                ?: data["caller_name"]
                ?: "Flare Caller"

            val callType = data[NotificationHelper.EXTRA_CALL_TYPE]
                ?: data["call_type"]
                ?: "AUDIO"

            val callId = data[NotificationHelper.EXTRA_CALL_ID]
                ?: data["call_id"]
                ?.trim()
                ?: ""

            val agoraChannel = data[NotificationHelper.EXTRA_AGORA_CHANNEL]
                ?: data["agora_channel"]
                ?.trim()
                ?: ""

            Log.d(
                TAG,
                "INCOMING_CALL_PAYLOAD " +
                        "callId=$callId " +
                        "channel=$agoraChannel " +
                        "caller=$callerName " +
                        "type=$callType"
            )

            if (callId.isBlank() || agoraChannel.isBlank()) {
                Log.e(TAG, "INCOMING_CALL_REJECTED: Missing required fields")
                return
            }

            val callerHandle = data[NotificationHelper.EXTRA_CALLER_HANDLE]
                ?: data["caller_handle"]
                ?: ""

            val serviceIntent = Intent(applicationContext, CallRingingService::class.java).apply {
                putExtra(NotificationHelper.EXTRA_CALLER_NAME, callerName)
                putExtra(NotificationHelper.EXTRA_CALL_TYPE, callType)
                putExtra(NotificationHelper.EXTRA_CALL_ID, callId)
                putExtra(NotificationHelper.EXTRA_AGORA_CHANNEL, agoraChannel)
                putExtra(NotificationHelper.EXTRA_CALLER_HANDLE, callerHandle)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start CallRingingService", t)
            }
            return
        }

        // =========================================================
        // FOLLOW (foreground: FCM does not auto-display notification
        // payloads when the app process is alive, so render here)
        // =========================================================

        if (type == "follow") {

            val title = remoteMessage.notification?.title
                ?: "New follower"

            val text = remoteMessage.notification?.body
                ?: data["body"]
                ?: "Someone started following you 🤝"

            Log.d(
                TAG,
                "FOLLOW_NOTIFICATION title=$title"
            )

            NotificationHelper.showSocialNotification(
                applicationContext,
                title,
                text
            )

            return
        }

        // =========================================================
        // ADMIN broadcast (same foreground-only concern as FOLLOW)
        // =========================================================

        if (type == "admin") {

            val title = remoteMessage.notification?.title
                ?: "FlareOfficial announcement"

            val text = remoteMessage.notification?.body
                ?: data["body"]
                ?: ""

            Log.d(
                TAG,
                "ADMIN_NOTIFICATION title=$title"
            )

            NotificationHelper.showAdminNotification(
                applicationContext,
                title,
                text
            )

            return
        }

        // =========================================================
        // MISSED CALL fallback (sent by the server ~20s after a ring
        // that was never answered — e.g. the OEM froze the process and
        // the live ring UI never showed; a plain notification for the
        // user, rendered here only when the app is in foreground)
        // =========================================================

        if (type == "missed_call") {

            val title = remoteMessage.notification?.title
                ?: "Missed call"

            val text = remoteMessage.notification?.body
                ?: data["body"]
                ?: ""

            Log.d(
                TAG,
                "MISSED_CALL_NOTIFICATION"
            )

            NotificationHelper.showSocialNotification(
                applicationContext,
                title,
                text
            )

            return
        }

        // =========================================================
        // CHAT / MESSAGE
        // =========================================================

        val body = data["body"]
            ?: remoteMessage.notification?.body
            ?: "You have a new message"

        val senderHandle = data[NotificationHelper.EXTRA_CHAT_HANDLE]
            ?: data["sender_handle"]
            ?: ""

        val senderName = data[NotificationHelper.EXTRA_CHAT_NAME]
            ?: data["sender_name"]
            ?.takeIf { it.isNotBlank() }
            ?: senderHandle

        Log.d(TAG, "DISPLAYING_NOTIFICATION type=$type sender=$senderHandle")

        when (type) {
            "chat", NotificationHelper.SCREEN_CHAT -> {
                NotificationHelper.showChatNotification(applicationContext, senderHandle, senderName, body)
            }
            "follow", NotificationHelper.SCREEN_NOTIFICATIONS -> {
                NotificationHelper.showSocialNotification(applicationContext, senderName, body)
            }
            "admin", NotificationHelper.SCREEN_ADMIN -> {
                NotificationHelper.showAdminNotification(applicationContext, senderName, body)
            }
            else -> {
                // If it's a known target screen, show general notification
                if (data.containsKey(NotificationHelper.EXTRA_TARGET_SCREEN)) {
                    NotificationHelper.showGeneralNotification(applicationContext, senderName, body)
                }
            }
        }
    }

    companion object {

        private const val TAG = "FlareFcmService"

        /**
         * Fetch the current FCM token and upload it to Supabase.
         */
        fun fetchAndUploadToken(context: Context) {

            FirebaseMessaging
                .getInstance()
                .token
                .addOnCompleteListener { task ->

                    if (!task.isSuccessful) {

                        Log.e(
                            TAG,
                            "Unable to get FCM token",
                            task.exception
                        )

                        return@addOnCompleteListener
                    }

                    val token = task.result

                    if (token.isNullOrBlank()) {

                        Log.e(
                            TAG,
                            "FCM token is empty"
                        )

                        return@addOnCompleteListener
                    }

                    Log.d(
                        TAG,
                        "FCM token obtained successfully"
                    )

                    NotificationPreferences.setFcmToken(
                        context,
                        token
                    )

                    saveTokenToBackend(
                        context,
                        token
                    )
                }
        }

        /**
         * Save FCM token in Supabase device_tokens.
         */
        private fun saveTokenToBackend(
            context: Context,
            token: String
        ) {

            CoroutineScope(
                Dispatchers.IO + SupervisorJob()
            ).launch {

                try {

                    val deviceId =
                        Settings.Secure.getString(
                            context.contentResolver,
                            Settings.Secure.ANDROID_ID
                        )

                    if (deviceId.isNullOrBlank()) {

                        Log.e(
                            TAG,
                            "ANDROID_ID is empty; cannot save FCM token"
                        )

                        return@launch
                    }

                    // The token can be refreshed BEFORE the user finishes
                    // signing in; saveDeviceToken() bails out when there is no
                    // session yet. Retry a few times so a login that is still
                    // in flight does not leave device_tokens empty (an empty
                    // table = the server has nothing to send to = no push).
                    var saved = false
                    var attempt = 0
                    while (attempt < 3 && !saved) {
                        attempt++

                        Log.d(
                            TAG,
                            "Uploading FCM token to backend (attempt $attempt)"
                        )

                        saved = SupabaseService(context)
                            .saveDeviceToken(
                                deviceId,
                                token
                            )

                        if (!saved && attempt < 3) {

                            Log.w(
                                TAG,
                                "FCM token upload attempt $attempt failed; retrying in 4s"
                            )

                            delay(4_000L)
                        }
                    }

                    if (saved) {

                        Log.d(
                            TAG,
                            "FCM token uploaded successfully"
                        )

                    } else {

                        Log.e(
                            TAG,
                            "Failed to upload FCM token after $attempt attempts"
                        )
                    }

                } catch (t: Throwable) {

                    Log.e(
                        TAG,
                        "Failed to upload FCM token",
                        t
                    )
                }
            }
        }
    }
}


/**
 * Foreground service responsible for keeping the incoming-call
 * notification alive while the user decides to accept/decline.
 */
class CallRingingService : Service() {

    private var stopJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()

        Log.d(
            TAG,
            "CallRingingService created"
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent == null) {

            Log.e(
                TAG,
                "CallRingingService started with null intent"
            )

            stopSelf(startId)
            return START_NOT_STICKY
        }

        val action = intent.action
        if (action == "DECLINE_CALL") {
            Log.d(TAG, "Call declined via notification action; stopping service")
            val callIdForDecline = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID) ?: ""
            if (callIdForDecline.isNotBlank()) {
                // Background update to REJECTED status so the caller gets a signal
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        SupabaseService(applicationContext).updateCallSignalStatus(callIdForDecline, "REJECTED")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update call status to REJECTED", e)
                    }
                }
            }
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == "STOP_SERVICE") {
            Log.d(TAG, "Stopping CallRingingService via STOP_SERVICE action")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        val callerName =
            intent.getStringExtra(
                NotificationHelper.EXTRA_CALLER_NAME
            ) ?: "Unknown"

        val callType =
            intent.getStringExtra(
                NotificationHelper.EXTRA_CALL_TYPE
            ) ?: "AUDIO"

        val callId =
            intent.getStringExtra(
                NotificationHelper.EXTRA_CALL_ID
            )?.trim()
                ?: ""

        val agoraChannel =
            intent.getStringExtra(
                NotificationHelper.EXTRA_AGORA_CHANNEL
            )?.trim()
                ?: ""

        val callerHandle =
            intent.getStringExtra(
                NotificationHelper.EXTRA_CALLER_HANDLE
            ) ?: ""

        Log.d(
            TAG,
            "RINGING_CALL " +
                    "caller=$callerName " +
                    "type=$callType " +
                    "callId=$callId " +
                    "agoraChannel=$agoraChannel"
        )

        // =========================================================
        // VALIDATION
        // =========================================================

        if (callId.isBlank()) {

            Log.e(
                TAG,
                "Cannot show call: callId is empty"
            )

            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (agoraChannel.isBlank()) {

            Log.e(
                TAG,
                "Cannot show call: agoraChannel is empty"
            )

            stopSelf(startId)
            return START_NOT_STICKY
        }

        // IMPORTANT:
        // Never convert callId into an Agora channel.
        if (callId == agoraChannel) {

            Log.w(
                TAG,
                "callId == agoraChannel. " +
                        "Verify call_history.channel_name."
            )
        }

        // =========================================================
        // INCOMING CALL NOTIFICATION
        // =========================================================

        try {

            val notification =
                NotificationHelper.getIncomingCallNotification(
                    this,
                    callerName,
                    callType,
                    callId,
                    agoraChannel,
                    callerHandle
                )

            startForeground(
                NOTIFICATION_ID,
                notification
            )

            Log.d(
                TAG,
                "Incoming call foreground notification started"
            )

        } catch (t: Throwable) {

            Log.e(
                TAG,
                "Failed to start foreground notification",
                t
            )

            stopSelf(startId)
            return START_NOT_STICKY
        }

        // =========================================================
        // AUTO STOP
        // =========================================================

        stopJob?.cancel()

        stopJob = CoroutineScope(
            Dispatchers.Main.immediate
        ).launch {

            delay(AUTO_STOP_DELAY_MS)

            Log.d(
                TAG,
                "Incoming call timeout reached; stopping service"
            )

            stopSelf()
        }

        /*
         * IMPORTANT:
         *
         * Do NOT join Agora here.
         *
         * The Agora join should happen only after the user
         * presses ACCEPT, using the exact agoraChannel received
         * from FCM.
         *
         * Expected accept flow:
         *
         * AGORA|$agoraChannel
         *
         * -> AgoraCallManager.receiveIncoming(...)
         */

        return START_NOT_STICKY
    }

    override fun onDestroy() {

        Log.d(
            TAG,
            "CallRingingService destroyed"
        )

        stopJob?.cancel()
        stopJob = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {

        private const val TAG =
            "CallRingingService"

        private const val NOTIFICATION_ID =
            9001

        private const val AUTO_STOP_DELAY_MS =
            45_000L
    }
}