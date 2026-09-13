package com.example.data.notification

import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.data.remote.SupabaseService

/**
 * One link of the push-delivery chain. `passed == false` means pushes CANNOT
 * arrive because of this specific problem.
 */
data class PushDiagnosticCheck(
    val id: String,
    val title: String,
    val passed: Boolean,
    val detail: String
)

data class PushDiagnosticReport(
    val checks: List<PushDiagnosticCheck>,
    val failing: Int
)

/**
 * Collects the state of EVERY link in the push-delivery chain so a broken
 * notification pipeline can be diagnosed on-device, without adb/logcat.
 */
object PushDiagnostics {

    private const val TAG = "PushDiagnostics"

    suspend fun collect(context: Context): PushDiagnosticReport {
        val checks = mutableListOf<PushDiagnosticCheck>()

        // 1. POST_NOTIFICATIONS permission ------------------------------------
        val hasPermission = NotificationHelper.hasNotificationPermission(context)
        checks += PushDiagnosticCheck(
            id = "permission",
            title = "Notification permission",
            passed = hasPermission,
            detail = if (hasPermission)
                "Granted — Android is allowed to display this app's notifications."
            else
                "DENIED — Android hides EVERY notification, even if the server sends it. Tap Fix and allow it."
        )

        // 2. App + channel enabled --------------------------------------------
        val appNotificationsEnabled =
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        var channelsOk = appNotificationsEnabled
        var channelDetail = if (appNotificationsEnabled)
            "App-level notifications are on."
        else
            "App-level notifications are OFF in system settings. Tap Fix."
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val messages = nm.getNotificationChannel(NotificationHelper.CHANNEL_MESSAGES)
            val calls = nm.getNotificationChannel(NotificationHelper.CHANNEL_CALLS)
            val channelsEnabled = messages != null && messages.importance != NotificationManager.IMPORTANCE_NONE &&
                    calls != null && calls.importance != NotificationManager.IMPORTANCE_NONE
            channelsOk = channelsOk && channelsEnabled
            if (!channelsEnabled) {
                channelDetail = "Channel 'Messages' or 'Calls' is disabled in system settings. Tap Fix → Channels."
            }
        }
        checks += PushDiagnosticCheck(
            id = "channels",
            title = "Notification channels enabled",
            passed = channelsOk,
            detail = channelDetail
        )

        // 3. Battery optimization ---------------------------------------------
        val ignoring = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        checks += PushDiagnosticCheck(
            id = "battery",
            title = "Battery optimization bypassed",
            passed = ignoring,
            detail = if (ignoring)
                "Yes — the OS is less likely to freeze/kill the app process in background."
            else
                "Not bypassed — some phones (Vivo/OPPO/MIUI) freeze the app after it is swiped away. Tap Fix and allow."
        )

        // 4. FCM token locally --------------------------------------------------
        val localToken = NotificationPreferences.getFcmToken(context)
        checks += PushDiagnosticCheck(
            id = "local_token",
            title = "FCM token on this device",
            passed = !localToken.isNullOrBlank(),
            detail = if (localToken != null)
                "Present (${localToken.take(10)}…) — Firebase assigned a registration token."
            else
                "MISSING — Firebase never issued a token (check google-services.json / Play Services)."
        )

        // 5. Token registered on the server ------------------------------------
        // Refresh the stored session first so an expired access token cannot
        // produce a misleading "could not verify" result.
        try {
            SupabaseService(context).refreshAuthSession()
        } catch (_: Throwable) {
            // Probe below will report the real state.
        }
        val (rowCount, updatedAt) = try {
            SupabaseService(context).checkDeviceTokenRegistered()
        } catch (t: Throwable) {
            Log.e(TAG, "checkDeviceTokenRegistered threw", t)
            -1 to null
        }
        checks += PushDiagnosticCheck(
            id = "server_token",
            title = "Token registered on server",
            passed = rowCount > 0,
            detail = when {
                rowCount > 0 ->
                    "Yes — $rowCount device row(s) on the server${if (updatedAt.isNullOrBlank()) "" else ", updated $updatedAt"}. The server CAN send to this phone."
                rowCount == 0 ->
                    "NO ROW on the server — the sender has no target, so push is impossible. Tap Fix to re-register, then Re-check."
                else ->
                    "Could not verify (not signed in, offline, or server error). Re-check after signing in."
            }
        )

        // 6. In-app master pause -----------------------------------------------
        val paused = NotificationPreferences.isPauseAll(context)
        checks += PushDiagnosticCheck(
            id = "inapp_pause",
            title = "In-app 'Pause All' setting",
            passed = !paused,
            detail = if (!paused)
                "In-app notification settings are not muting anything."
            else
                "'Pause All' is ON in the toggles above — every notification is muted inside the app. Turn it off to receive alerts."
        )

        return PushDiagnosticReport(checks = checks, failing = checks.count { !it.passed })
    }

    /** Unwraps a Context until it reaches the hosting Activity, if any. */
    fun findActivity(context: Context): Activity? {
        var ctx: Context = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /** Opens this app's system notification settings (permission + channels). */
    fun openAppNotificationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "APP_NOTIFICATION_SETTINGS failed: ${t.message}")
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (t2: Throwable) {
                Log.e(TAG, "APPLICATION_DETAILS_SETTINGS failed", t2)
            }
        }
    }

    /**
     * Best-effort jump to the OEM's background/autostart manager (Vivo iManager,
     * OPPO, MIUI, Huawei). Returns true if a manufacturer screen was opened.
     */
    fun openOemAutostartSettings(context: Context): Boolean {
        val candidates = listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
        )
        for (component in candidates) {
            try {
                context.startActivity(
                    Intent().setComponent(component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            } catch (_: Throwable) {
                // Not installed on this ROM — try the next one.
            }
        }
        return false
    }
}