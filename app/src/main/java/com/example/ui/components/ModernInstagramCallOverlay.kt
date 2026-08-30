package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.CallState
import kotlinx.coroutines.delay

/**
 * Fullscreen HD Video & Voice Call Room with Camera feed PIP, Audio Frequency Visualizer,
 * Sound Effects, Screen Share Simulation, Speakerphone, and Live Connection Diagnostics.
 */
@Composable
fun ModernInstagramCallOverlay(
    call: CallState,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    localRenderer: org.webrtc.SurfaceViewRenderer? = null,
    remoteRenderer: org.webrtc.SurfaceViewRenderer? = null
) {
    var callSeconds by remember { mutableStateOf(call.durationSec) }
    var isPipExpanded by remember { mutableStateOf(false) }
    var showInCallChat by remember { mutableStateOf(false) }
    var quickReaction by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callSeconds++
        }
    }

    LaunchedEffect(quickReaction) {
        if (quickReaction != null) {
            delay(2200)
            quickReaction = null
        }
    }

    val formattedDuration = remember(callSeconds) {
        val hrs = callSeconds / 3600
        val mins = (callSeconds % 3600) / 60
        val secs = callSeconds % 60
        if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    // Audio frequency ripple wave animation
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (!call.isMuted) 1.15f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0C10))
            .testTag("modern_call_overlay")
    ) {
        // ==========================================
        // 1. VIDEO FEED / AUDIO BACKGROUND CANVAS
        // ==========================================
        if (call.isVideo && !call.isScreenSharing) {
            // Main Remote Video Stream View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1F1D36),
                                Color(0xFF14142B),
                                Color(0xFF0F0E17)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Background Ambient Glow
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .scale(pulseScale)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    InstagramPink.copy(alpha = 0.25f),
                                    InstagramPurple.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .border(
                                2.dp,
                                Brush.linearGradient(
                                    listOf(InstagramPink, InstagramPurple, InstagramYellow)
                                ),
                                RoundedCornerShape(32.dp)
                            )
                            .background(Color(0xFF232338)),
                        contentAlignment = Alignment.Center
                    ) {
                        VynAvatar(avatarType = call.partnerAvatar, size = 180.dp)

                        // Live audio frequency bars inside video frame
                        if (!call.isMuted) {
                            LiveAudioVisualizerWave(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                                    .height(24.dp)
                                    .width(80.dp),
                                waveColor = InstagramPink,
                                phase = wavePhase
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = call.partnerName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${call.connectionQuality} · Stable",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (call.isScreenSharing) {
            // Simulated Live Screen Share View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121824)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(260.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ScreenShare,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Screen Sharing Active",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Sharing full viewport with ${call.partnerName}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Audio Call Ambient Wave Layout
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF2A1B4E),
                                Color(0xFF140F2D),
                                Color(0xFF090615)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Expanding Audio Pulses
                for (i in 3 downTo 1) {
                    val multiScale = 1.0f + (i * 0.18f * (pulseScale - 1f) * 6f)
                    Box(
                        modifier = Modifier
                            .size((140 + i * 45).dp)
                            .scale(multiScale)
                            .background(
                                InstagramPurple.copy(alpha = 0.08f * (4 - i)),
                                CircleShape
                            )
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .border(
                                3.dp,
                                Brush.sweepGradient(listOf(InstagramPurple, InstagramPink, InstagramYellow, InstagramPurple)),
                                CircleShape
                            )
                            .background(Color(0xFF251A40)),
                        contentAlignment = Alignment.Center
                    ) {
                        VynAvatar(avatarType = call.partnerAvatar, size = 140.dp)
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = call.partnerName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Encrypted Audio Call · $formattedDuration",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    LiveAudioVisualizerWave(
                        modifier = Modifier
                            .height(36.dp)
                            .width(180.dp),
                        waveColor = InstagramPink,
                        phase = wavePhase
                    )
                }
            }
        }

        // ==========================================
        // 2. SELF CAMERA PIP (PICTURE-IN-PICTURE)
        // ==========================================
        if (call.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 16.dp)
                    .size(if (isPipExpanded) 150.dp else 110.dp, if (isPipExpanded) 200.dp else 150.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1E1E2E))
                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .clickable { isPipExpanded = !isPipExpanded }
            ) {
                if (call.isCameraOn) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF3B185F),
                                        Color(0xFFA12568),
                                        Color(0xFF22092C)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        VynAvatar(avatarType = "default", size = 80.dp)

                        // Camera flip indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .clickable { onFlipCamera() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipCameraIos,
                                contentDescription = "Flip",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF28283E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                            Text("Camera Off", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. TOP BAR (BACK, TITLE, DURATION, METRICS)
        // ==========================================
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 44.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Minimize",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (call.isVideo) "Video Call" else "Voice Call",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = formattedDuration,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }
            }

            // Quick floating emoji reactions
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("❤️", "🔥", "👏", "😂").forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { quickReaction = emoji },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 14.sp)
                    }
                }
            }
        }

        // Floating Reaction Pop animation
        quickReaction?.let { emoji ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .scale(pulseScale * 1.5f)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(text = "$emoji Sent!", fontSize = 28.sp, color = Color.White)
            }
        }

        // ==========================================
        // 4. BOTTOM CONTROLS DOCK (MATERIAL 3)
        // ==========================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 36.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Secondary row: Speaker, Screen share, Flip camera
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF181829).copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speakerphone Toggle
                    CallControlButton(
                        icon = if (call.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                        label = if (call.isSpeakerOn) "Speaker" else "Earpiece",
                        isActive = call.isSpeakerOn,
                        onClick = onToggleSpeaker
                    )

                    // Screen Share Toggle
                    CallControlButton(
                        icon = if (call.isScreenSharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Outlined.ScreenShare,
                        label = if (call.isScreenSharing) "Stop Share" else "Share Screen",
                        isActive = call.isScreenSharing,
                        onClick = onToggleScreenShare
                    )

                    // Flip Camera
                    if (call.isVideo) {
                        CallControlButton(
                            icon = Icons.Default.FlipCameraIos,
                            label = "Flip",
                            isActive = false,
                            onClick = onFlipCamera
                        )
                    }
                }
            }

            // Primary row: Camera on/off, Mute, End Call
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video Camera On / Off
                IconButton(
                    onClick = onToggleCamera,
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (call.isCameraOn) Color.White.copy(alpha = 0.22f) else Color(0xFFE53935),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (call.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Toggle Camera",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // End Call Red Button
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(68.dp)
                        .background(Color(0xFFE53935), CircleShape)
                        .testTag("ig_end_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Microphone Mute / Unmute
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (call.isMuted) Color(0xFFE53935) else Color.White.copy(alpha = 0.22f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Toggle Mic",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (isActive) InstagramPink else Color.White.copy(alpha = 0.12f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun LiveAudioVisualizerWave(
    modifier: Modifier = Modifier,
    waveColor: Color = InstagramPink,
    phase: Float = 0f
) {
    Canvas(modifier = modifier) {
        val barCount = 18
        val spacing = size.width / barCount
        val barWidth = spacing * 0.55f

        for (i in 0 until barCount) {
            val angle = (phase + i * 22f) * (Math.PI / 180f)
            val normalizedHeight = (Math.sin(angle) * 0.5 + 0.5).toFloat()
            val barHeight = (size.height * 0.25f) + (size.height * 0.75f * normalizedHeight)

            val x = i * spacing + (spacing - barWidth) / 2
            val y = (size.height - barHeight) / 2

            drawRoundRect(
                color = waveColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
