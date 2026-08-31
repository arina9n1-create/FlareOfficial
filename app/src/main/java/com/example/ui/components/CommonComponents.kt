package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainTab

/**
 * Vyn9 brand logo + title (monogram badge, VYN + glowing 9 pill, sparkle).
 * Shared by VynTopBar and the Messages inbox header so the design stays identical.
 */
@Composable
fun VynBrandTitle(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.testTag("app_logo_title")
    ) {
        // Sleek Monogram Icon Badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6C5CE7),
                            Color(0xFFFF007F),
                            Color(0xFF00F2FE)
                        )
                    )
                )
                .padding(1.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "V",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF6C5CE7),
                                Color(0xFFFF007F)
                            )
                        )
                    )
                )
            }
        }

        // Brand Typography with Cyber-Capsule "9"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "VYN",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Glowing Number 9 Pill Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFF007F),
                                Color(0xFF6C5CE7)
                            )
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 0.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "9",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            // Social Sparkle
            Text(
                text = "✦",
                fontSize = 8.sp,
                color = Color(0xFF00F2FE),
                modifier = Modifier.padding(start = 1.dp, bottom = 6.dp)
            )
        }
    }
}

@Composable
fun VynTopBar(
    title: String = "Vyn9",
    showBack: Boolean = false,
    onBackClick: () -> Unit = {},
    showSettings: Boolean = false,
    onSettingsClick: () -> Unit = {},
    notificationCount: String = "9+",
    onNotificationClick: () -> Unit = {},
    onRewardClick: () -> Unit = {},
    onPlusClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    val transition = updateTransition(targetState = isVisible, label = "top_bar_transition")
    val offsetY by transition.animateDp(
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
        label = "offset_y"
    ) { visible ->
        if (visible) 0.dp else (-100).dp
    }

    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300) },
        label = "alpha"
    ) { visible ->
        if (visible) 1f else 0f
    }

    val scale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
        label = "scale"
    ) { visible ->
        if (visible) 1f else 0.95f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = offsetY)
            .graphicsLayer { 
                this.alpha = alpha 
                this.scaleX = scale
                this.scaleY = scale
            }
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showBack) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("top_bar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Beautiful Unique Brand Logo & Title Design (shared composable)
                VynBrandTitle()
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Reward / Earn Badge
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF6C5CE7).copy(alpha = 0.12f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onRewardClick)
                        .testTag("top_bar_reward_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = "Earn",
                            tint = Color(0xFFE67E22),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Earn 💰",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = Color(0xFF6C5CE7)
                        )
                    }
                }

                if (showSettings) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("top_bar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Modern Redesigned Notification Icon with Animation & Glowing Badge
                ModernNotificationIconButton(
                    notificationCount = notificationCount,
                    onClick = onNotificationClick,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun ModernNotificationIconButton(
    notificationCount: String = "9+",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasUnread = notificationCount.isNotBlank() && notificationCount != "0"

    // Continuous smooth aurora rotation
    val infiniteTransition = rememberInfiniteTransition(label = "aurora_anim")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_shift"
    )

    // Radar beacon pulse wave
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_pulse"
    )

    val radarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_alpha"
    )

    // Spark icon gentle tilt & pulse
    val sparkScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spark_scale"
    )

    Box(
        modifier = modifier
            .testTag("notifications_heart_button")
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Outer Radar Energy Wave (Pulsing Halo when unread)
        if (hasUnread) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(radarPulse)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF007F).copy(alpha = radarAlpha),
                                Color(0xFF8A2BE2).copy(alpha = radarAlpha * 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Futuristic Dynamic Island Capsule
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shadowElevation = if (hasUnread) 4.dp else 1.dp,
            border = BorderStroke(
                width = if (hasUnread) 1.5.dp else 1.dp,
                brush = if (hasUnread) {
                    Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFFF007F), // Neon Pink
                            Color(0xFF8A2BE2), // Electric Purple
                            Color(0xFF00F2FE), // Cyan
                            Color(0xFFFFB300), // Amber Glow
                            Color(0xFFFF007F)  // Neon Pink loop
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    )
                }
            ),
            modifier = Modifier.height(35.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF6C5CE7).copy(alpha = if (hasUnread) 0.15f else 0.05f),
                                Color(0xFFFF2A6D).copy(alpha = if (hasUnread) 0.12f else 0.03f)
                            )
                        )
                    )
                    .padding(horizontal = if (hasUnread) 8.dp else 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Futuristic Glowing Core Icon (Electric Spark & Pulse)
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasUnread) Icons.Default.AutoAwesome else Icons.Outlined.AutoAwesome,
                        contentDescription = "Notifications",
                        tint = if (hasUnread) Color(0xFFFF2A6D) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(17.dp)
                            .scale(sparkScale)
                            .rotate(if (hasUnread) (gradientShift * 0.1f) else 0f)
                    )
                }

                // Cyber Live Badge with Dynamic Counter
                if (hasUnread) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFF007F),
                                        Color(0xFF7928CA)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            // Mini blinking green/cyan live activity dot
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(Color(0xFF00FFC6), CircleShape)
                            )

                            Text(
                                text = notificationCount,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.2.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VynAvatar(
    avatarType: String,
    storagePath: String? = null,
    size: Dp = 44.dp,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    hasStoryRing: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val resolvedUrl = remember(avatarType, storagePath) {
        com.example.util.MediaStorageResolver.resolve(storagePath ?: avatarType)
    }
    
    val isUriOrUrl = resolvedUrl.startsWith("content://") ||
            resolvedUrl.startsWith("file://") ||
            resolvedUrl.startsWith("http://") ||
            resolvedUrl.startsWith("https://")

    val boxModifier = modifier
        .size(size)
        .then(
            if (hasStoryRing) {
                Modifier
                    .background(
                        brush = Brush.linearGradient(
                            listOf(VynStoryGradientStart, VynStoryGradientEnd)
                        ),
                        shape = CircleShape
                    )
                    .padding(2.5.dp)
            } else if (borderWidth > 0.dp) {
                Modifier.border(borderWidth, borderColor, CircleShape)
            } else Modifier
        )
        .clip(CircleShape)
        .then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )

    if (isUriOrUrl) {
        // The surfaceVariant base keeps the avatar visible while the remote image
        // streams in, and the error slot falls back to the generic person glyph so a
        // failed/blocked download (e.g. HTTP 401 from a private storage bucket) can
        // never render as an invisible white circle.
        SubcomposeAsyncImage(
            model = resolvedUrl,
            contentDescription = "User Avatar",
            contentScale = ContentScale.Crop,
            modifier = boxModifier.background(MaterialTheme.colorScheme.surfaceVariant),
            loading = {
                Box(modifier = Modifier.fillMaxSize())
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxSize(0.6f)
                    )
                }
            }
        )
    } else {
        // No hardcoded avatars. Display a generic letter avatar or placeholder background.
        Box(
            modifier = boxModifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxSize(0.6f)
            )
        }
    }
}

@Composable
fun VynImage(
    imageResName: String,
    storagePath: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val resolvedUrl = remember(imageResName, storagePath) {
        com.example.util.MediaStorageResolver.resolve(storagePath ?: imageResName)
    }

    if (resolvedUrl.startsWith("content://") || 
        resolvedUrl.startsWith("file://") || 
        resolvedUrl.startsWith("http://") || 
        resolvedUrl.startsWith("https://")) {
        SubcomposeAsyncImage(
            model = resolvedUrl,
            contentDescription = "Post media image",
            contentScale = contentScale,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            loading = {
                Box(modifier = Modifier.fillMaxSize())
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        )
    } else {
        // No hardcoded image resources.
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun VynBottomNavBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onPlusClick: () -> Unit = {},
    unreadMessageCount: Int = 0,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    val isChatActive = currentTab == MainTab.CHAT

    val transition = updateTransition(targetState = isVisible, label = "bottom_bar_transition")
    val offsetY by transition.animateDp(
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
        label = "offset_y"
    ) { visible ->
        if (visible) 0.dp else 100.dp
    }

    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300) },
        label = "alpha"
    ) { visible ->
        if (visible) 1f else 0f
    }

    val scale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
        label = "scale"
    ) { visible ->
        if (visible) 1f else 0.95f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = offsetY)
            .graphicsLayer { 
                this.alpha = alpha 
                this.scaleX = scale
                this.scaleY = scale
            }
            .navigationBarsPadding()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Home
                IconButton(
                    onClick = { onTabSelected(MainTab.HOME) },
                    modifier = Modifier.testTag("nav_home_tab")
                ) {
                    Icon(
                        imageVector = if (currentTab == MainTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = "Home",
                        tint = if (currentTab == MainTab.HOME) MaterialTheme.colorScheme.onSurface else VynTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 2. Search / Explore
                IconButton(
                    onClick = { onTabSelected(MainTab.SEARCH) },
                    modifier = Modifier.testTag("nav_search_tab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (currentTab == MainTab.SEARCH) MaterialTheme.colorScheme.onSurface else VynTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 3. Unique & Beautiful Center Message / Chat Action Button
                Box(
                    modifier = Modifier
                        .testTag("nav_message_center_tab")
                        .clickable { onTabSelected(MainTab.CHAT) },
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Glowing / Gradient Pill Container
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .width(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                brush = if (isChatActive) {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF6C5CE7), // Vyn Indigo
                                            Color(0xFFFF007F), // Neon Pink
                                            Color(0xFF00F2FE)  // Electric Cyan
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            )
                            .padding(if (isChatActive) 1.5.dp else 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    if (isChatActive) {
                                        MaterialTheme.colorScheme.surface
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isChatActive) Icons.Filled.Forum else Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "Messages",
                                tint = if (isChatActive) {
                                    Color(0xFFFF007F)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Floating Unread Badge on the Message Icon
                    if (unreadMessageCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-2).dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFF007F), Color(0xFF6C5CE7))
                                    )
                                )
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                .padding(horizontal = 4.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = if (unreadMessageCount > 9) "9+" else "$unreadMessageCount",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // 4. Reels / Video
                IconButton(
                    onClick = { onTabSelected(MainTab.REELS) },
                    modifier = Modifier.testTag("nav_reels_tab")
                ) {
                    Icon(
                        imageVector = if (currentTab == MainTab.REELS) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                        contentDescription = "Reels",
                        tint = if (currentTab == MainTab.REELS) MaterialTheme.colorScheme.onSurface else VynTextSecondary,
                        modifier = Modifier.size(23.dp)
                    )
                }

                // 5. Profile avatar
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (currentTab == MainTab.PROFILE) 2.dp else 1.dp,
                            color = if (currentTab == MainTab.PROFILE) MaterialTheme.colorScheme.onSurface else Color.LightGray,
                            shape = CircleShape
                        )
                        .clickable { onTabSelected(MainTab.PROFILE) }
                        .testTag("nav_profile_tab"),
                    contentAlignment = Alignment.Center
                ) {
                    VynAvatar(
                        avatarType = "default",
                        size = 24.dp
                    )
                }
            }
        }
    }
}

@Composable
fun ReelsBottomBar(
    onCommentClick: () -> Unit,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = Color.Transparent
        ) {
            // High-Contrast Glassmorphic Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .shadow(12.dp, RoundedCornerShape(28.dp))
                    .background(
                        color = Color.Black.copy(alpha = 0.75f), // Even darker for better visibility
                        shape = RoundedCornerShape(28.dp)
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.15f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clickable { onCommentClick() }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Add a comment...",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = InstagramBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
