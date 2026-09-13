package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.notification.FlareFirebaseMessagingService
import com.example.data.notification.NotificationHelper
import com.example.data.notification.PushDiagnostics
import com.example.data.notification.PushDiagnosticCheck
import com.example.data.notification.PushDiagnosticReport
import kotlinx.coroutines.launch

/**
 * Expandable diagnostic card (Settings → Notifications & Alerts) that checks
 * every link of the push-delivery chain live on the device and offers one-tap
 * fixes for each broken link. Purpose: replace blind logcat debugging when
 * notifications do not arrive after the app is cleared from Recents.
 */
@Composable
fun PushDiagnosticCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<PushDiagnosticReport?>(null) }

    fun runChecks() {
        running = true
        scope.launch {
            report = try {
                PushDiagnostics.collect(context)
            } catch (t: Throwable) {
                null
            }
            running = false
        }
    }

    LaunchedEffect(expanded) {
        if (expanded && report == null && !running) runChecks()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFF37474F), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (report?.failing == 0) Icons.Outlined.NotificationsActive else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Push Delivery Diagnostic",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = when {
                            running -> "Checking the delivery chain…"
                            report == null -> "Tap to verify why notifications may not arrive"
                            report!!.failing == 0 -> "All ${report!!.checks.size} checks passed ✅"
                            else -> "${report!!.failing} of ${report!!.checks.size} checks failed ❌"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            if (expanded) {
                report?.checks?.forEach { check ->
                    DiagnosticRow(check) { runChecks() }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { runChecks() },
                        enabled = !running
                    ) {
                        Text(if (running) "Checking…" else "Re-check")
                    }
                    Button(onClick = {
                        NotificationHelper.showGeneralNotification(
                            context,
                            "Test notification 🔔",
                            "If you can see this, the app CAN show notifications on this device."
                        )
                        Toast.makeText(context, "Test notification sent", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Test notification")
                    }
                }

                TextButton(onClick = {
                    val opened = PushDiagnostics.openOemAutostartSettings(context)
                    Toast.makeText(
                        context,
                        if (opened) "Opened your phone's background/autostart settings"
                        else "No manufacturer settings found on this phone",
                        Toast.LENGTH_LONG
                    ).show()
                }) {
                    Text("Open OEM autostart / battery manager (Vivo, OPPO, MIUI…)")
                }

                Text(
                    text = "Note: on Vivo/OPPO/MIUI also enable Autostart for this app and set Battery → 'High background power consumption' to Allow, otherwise the phone blocks push delivery itself.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRow(check: PushDiagnosticCheck, onFixApplied: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (check.passed) "✅" else "❌",
            fontSize = 15.sp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = check.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                text = check.detail,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!check.passed) {
            val activity = PushDiagnostics.findActivity(context)
            val fix: (() -> Unit)? = when (check.id) {
                "permission", "channels" -> ({
                    PushDiagnostics.openAppNotificationSettings(context)
                })
                "battery" -> activity?.let { a -> ({
                    com.example.data.notification.BatteryOptimizationHelper.showOptOutPrompt(a)
                }) }
                "local_token", "server_token" -> ({
                    FlareFirebaseMessagingService.fetchAndUploadToken(context)
                    Toast.makeText(context, "Re-registering token… press Re-check in a few seconds", Toast.LENGTH_LONG).show()
                })
                else -> null
            }
            if (fix != null) {
                TextButton(onClick = fix) { Text("Fix") }
            }
        }
    }
}