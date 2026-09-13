package com.example.ui.screens

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.data.notification.NotificationHelper
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SocialViewModel

class IncomingCallActivity : ComponentActivity() {

    private lateinit var socialViewModel: SocialViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get SocialViewModel to handle accept/decline
        socialViewModel = ViewModelProvider(this)[SocialViewModel::class.java]

        // Ensure activity shows over lockscreen and turns screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Extras use NotificationHelper constant keys (legacy bare keys kept as fallback)
        val callerName = intent.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME)
            ?: intent.getStringExtra("caller_name") ?: "Unknown Caller"
        val callType = intent.getStringExtra(NotificationHelper.EXTRA_CALL_TYPE)
            ?: intent.getStringExtra("call_type") ?: "AUDIO"
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID)
            ?: intent.getStringExtra("call_id") ?: ""
        val agoraChannel = intent.getStringExtra(NotificationHelper.EXTRA_AGORA_CHANNEL)
            ?: intent.getStringExtra("agora_channel") ?: ""
        val callerHandle = intent.getStringExtra(NotificationHelper.EXTRA_CALLER_HANDLE)
            ?: intent.getStringExtra("caller_handle") ?: ""

        setContent {
            MyApplicationTheme {
                IncomingCallScreen(
                    callerName = callerName,
                    callType = callType,
                    onAccept = {
                        // Actually accept the call via SocialViewModel
                        socialViewModel.acceptIncomingCall(
                            explicitCallId = callId,
                            explicitAgoraChannel = agoraChannel,
                            explicitCallType = callType,
                            explicitCallerName = callerName,
                            explicitCallerHandle = callerHandle
                        )
                        finish()
                    },
                    onDecline = {
                        // Actually reject the call via SocialViewModel
                        socialViewModel.rejectIncomingCall()
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun IncomingCallScreen(
    callerName: String,
    callType: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = callerName, style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text(text = "Incoming $callType Call", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(100.dp))
            Row {
                Button(onClick = onDecline, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("Decline")
                }
                Spacer(modifier = Modifier.width(32.dp))
                Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = Color.Green)) {
                    Text("Accept")
                }
            }
        }
    }
}
