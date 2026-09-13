package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.CallState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Redesigned Sleek HD Call Overlay.
 * - Smaller, compact UI elements.
 * - Sticker-style emoji reactions.
 * - No top back button for a cleaner immersive look.
 * - All original options preserved.
 */
@Composable
fun ModernFlareOfficialCallOverlay(
    call: CallState,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    localRenderer: android.view.View? = null,
    remoteRenderer: android.view.View? = null
) {
    var callSeconds by remember { mutableStateOf(call.durationSec) }
    var isPipExpanded by remember { mutableStateOf(false) }
    var stickerReaction by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (coroutineContext.isActive) {
            delay(1000)
            callSeconds++
        }
    }

    LaunchedEffect(stickerReaction) {
        if (stickerReaction != null) {
            delay(2500)
            stickerReaction = null
        }
    }

    val formattedDuration = remember(callSeconds) {
        val mins = (callSeconds % 3600) / 60
        val secs = callSeconds % 60
        String.format("%02d:%02d", mins, secs)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08080E))
            .testTag("modern_call_overlay")
    ) {
        // 1. MAIN RENDERER (Immersive Background)
        if (call.isVideo && !call.isScreenSharing) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (remoteRenderer != null) {
                    key(remoteRenderer) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { remoteRenderer },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F1B)),
                        contentAlignment = Alignment.Center
                    ) {
                        FlareAvatar(avatarType = call.partnerAvatar, size = 100.dp)
                    }
                }
                // Subtle shading for UI visibility
                Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(0.4f), Color.Transparent))).align(Alignment.TopCenter))
                Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.5f)))).align(Alignment.BottomCenter))
            }
        } else if (call.isScreenSharing) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0F1D)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Outlined.ScreenShare, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(50.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Screen Sharing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        } else {
            // Sleek Audio Call UI
            Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1A1231), Color(0xFF05050A)))), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(180.dp).scale(pulseScale).background(FlareOfficialPurple.copy(0.06f), CircleShape))
                        Box(modifier = Modifier.size(130.dp).background(Color(0xFF151025), CircleShape).border(1.5.dp, FlareOfficialPink.copy(0.6f), CircleShape)) {
                            FlareAvatar(avatarType = call.partnerAvatar, size = 130.dp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(call.partnerName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("HD Voice · $formattedDuration", color = Color.White.copy(0.5f), fontSize = 11.sp)
                    Spacer(Modifier.height(24.dp))
                    LiveAudioVisualizerWave(Modifier.height(30.dp).width(140.dp), phase = (pulseScale * 360f))
                }
            }
        }

        // 2. SELF CAMERA PIP (Smaller & Compact)
        if (call.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 44.dp, end = 16.dp)
                    .size(if (isPipExpanded) 130.dp else 90.dp, if (isPipExpanded) 180.dp else 125.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1A1A27))
                    .border(1.dp, Color.White.copy(0.25f), RoundedCornerShape(14.dp))
                    .clickable { isPipExpanded = !isPipExpanded }
            ) {
                if (call.isCameraOn && localRenderer != null) {
                    key(localRenderer) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { localRenderer },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.VideocamOff, null, tint = Color.White.copy(0.2f), modifier = Modifier.size(24.dp))
                    }
                }
                if (call.isCameraOn) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(24.dp).background(Color.Black.copy(0.4f), CircleShape).clickable { onFlipCamera() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FlipCameraIos, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        // 3. TOP INFO (No back button, centered/compact)
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(top = 44.dp, start = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(Color(0xFF4CAF50), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (call.isVideo) "Video Call" else "Voice Call",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Text(formattedDuration, color = Color.White.copy(0.7f), fontSize = 11.sp)
        }

        // 4. STICKER REACTIONS (Front and center floating row)
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).width(44.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("❤️", "🔥", "👏", "😂", "😢", "😮").forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White.copy(0.12f), CircleShape)
                            .clickable { stickerReaction = emoji },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 16.sp)
                    }
                }
            }
        }

        // 5. BOTTOM COMPACT CONTROLS
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Secondary Actions (Speaker, Share, Flip)
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(0.35f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallOptionButton(
                    icon = if (call.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                    label = "Speaker",
                    isActive = call.isSpeakerOn,
                    onClick = onToggleSpeaker
                )
                CallOptionButton(
                    icon = if (call.isScreenSharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Outlined.ScreenShare,
                    label = "Share",
                    isActive = call.isScreenSharing,
                    onClick = onToggleScreenShare
                )
                if (call.isVideo) {
                    CallOptionButton(icon = Icons.Default.FlipCameraIos, label = "Flip", isActive = false, onClick = onFlipCamera)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Primary Actions (Mute, End, Camera)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Camera
                SmallCallButton(
                    icon = if (call.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    onClick = onToggleCamera,
                    bgColor = if (call.isCameraOn) Color.White.copy(0.15f) else Color(0xFFD32F2F)
                )

                // End Call (The only slightly larger button)
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier.size(64.dp).background(Color(0xFFD32F2F), CircleShape)
                ) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }

                // Mute
                SmallCallButton(
                    icon = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    onClick = onToggleMute,
                    bgColor = if (call.isMuted) Color(0xFFD32F2F) else Color.White.copy(0.15f)
                )
            }
        }

        // Immersive Sticker Animation
        AnimatedVisibility(
            visible = stickerReaction != null,
            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier.background(Color.Black.copy(0.5f), RoundedCornerShape(20.dp)).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stickerReaction ?: "", fontSize = 72.sp)
            }
        }
    }
}

@Composable
private fun SmallCallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    bgColor: Color
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(50.dp).background(bgColor, CircleShape)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CallOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.width(50.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(if (isActive) FlareOfficialPink else Color.White.copy(0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 9.sp, color = Color.White.copy(0.8f))
    }
}

@Composable
fun LiveAudioVisualizerWave(modifier: Modifier = Modifier, phase: Float = 0f) {
    Canvas(modifier = modifier) {
        val barCount = 10
        val spacing = size.width / barCount
        val barWidth = spacing * 0.5f
        for (i in 0 until barCount) {
            val h = (Math.sin((phase + i * 36).toDouble() * Math.PI / 180.0) * 0.5 + 0.5) * size.height
            drawRoundRect(
                color = FlareOfficialPink.copy(alpha = 0.8f),
                topLeft = Offset(i * spacing, (size.height - h.toFloat()) / 2),
                size = Size(barWidth, h.toFloat()),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }
    }
}
