package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.VynAvatar
import com.example.ui.components.VynImage
import com.example.ui.theme.*
import com.example.ui.viewmodel.SocialViewModel
import com.example.data.remote.SupabaseService
import com.example.data.model.ReelEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen overlay shown while a reel is uploading.
 * Shows a live percentage ring, the current stage and a progress bar.
 */
@Composable
fun ReelUploadOverlay(progress: Int) {
    val stage = when {
        progress >= 100 -> "Reel published! 🎉"
        progress >= 95 -> "Refreshing your reels…"
        progress >= 92 -> "Saving reel…"
        else -> "Uploading your reel…"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(110.dp),
                    strokeWidth = 8.dp,
                    color = Color(0xFFE91E63),
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
                Text(
                    text = "$progress%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = stage,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (progress < 92) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFFE91E63),
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Keep the app open — large videos take a moment ✨",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}


data class ReelComment(
    val id: String,
    val author: String,
    val handle: String,
    val avatarType: String,
    val userAvatarPath: String? = null,
    val text: String,
    val timeAgo: String,
    var likes: Int = 0,
    var isLiked: Boolean = false
)

data class InstagramReelData(
    val id: Int,
    val author: String,
    val handle: String,
    val avatarType: String,
    val userAvatarPath: String? = null,
    val caption: String,
    val music: String,
    val imageRes: String,
    val videoUrl: String? = null,
    val storagePath: String? = null,
    val thumbnailPath: String? = null,
    val location: String = "",
    val effectName: String? = null,
    var likes: Int,
    var commentsCount: Int,
    var sharesCount: Int = 0,
    var isLiked: Boolean = false,
    var isSaved: Boolean = false,
    var isFollowing: Boolean = false,
    val comments: MutableList<ReelComment> = mutableListOf(),
    val remoteId: String = ""
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(
    viewModel: SocialViewModel,
    modifier: Modifier = Modifier
) {
    val adminConfig by viewModel.adminConfig.collectAsState()
    val wallet by viewModel.rewardWallet.collectAsState()
    val profile by viewModel.profile.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    // Real reels are loaded from Supabase (through the Room cache).
    val remoteReels by viewModel.reels.collectAsState()
    val remoteReelsData = remember(remoteReels) {
        mutableStateListOf(
            *remoteReels
                .filter { it.imageRes.isNotBlank() || !it.videoUrl.isNullOrBlank() } // Filter out broken reels
                .map { reel ->
                InstagramReelData(
                    id = reel.id.toInt(),
                    author = reel.author.ifBlank { reel.handle },
                    handle = reel.handle,
                    avatarType = reel.avatarType,
                    userAvatarPath = reel.userAvatarPath,
                    caption = reel.caption,
                    music = reel.music,
                    imageRes = reel.imageRes,
                    videoUrl = reel.videoUrl?.takeIf { it.isNotBlank() },
                    storagePath = reel.storagePath,
                    thumbnailPath = reel.thumbnailPath,
                    location = reel.location,
                    effectName = reel.effectName,
                    likes = reel.likesCount,
                    commentsCount = reel.commentsCount,
                    sharesCount = reel.sharesCount,
                    isLiked = reel.isLiked,
                    isSaved = reel.isSaved,
                    remoteId = reel.remoteId
                )
            }.toTypedArray()
        )
    }

    val pagerState = rememberPagerState(pageCount = { remoteReelsData.size })
    val coroutineScope = rememberCoroutineScope()

    // Background pre-caching for upcoming and previous video reels
    LaunchedEffect(pagerState.currentPage, remoteReelsData.size) {
        val nextIdx = pagerState.currentPage + 1
        if (nextIdx < remoteReelsData.size) {
            val videoRef = remoteReelsData[nextIdx].videoUrl
            val resolved = com.example.util.MediaStorageResolver.resolve(videoRef)
            if (resolved.isNotBlank()) {
                com.example.media.player.ExoPlayerCacheManager.preloadVideo(context, resolved)
            }
        }
        val prevIdx = pagerState.currentPage - 1
        if (prevIdx >= 0) {
            val videoRef = remoteReelsData[prevIdx].videoUrl
            val resolved = com.example.util.MediaStorageResolver.resolve(videoRef)
            if (resolved.isNotBlank()) {
                com.example.media.player.ExoPlayerCacheManager.preloadVideo(context, resolved)
            }
        }
    }

    var isMuted by remember { mutableStateOf(false) }
    var showMuteIndicator by remember { mutableStateOf(false) }
    var activeCommentsReel by remember { mutableStateOf<InstagramReelData?>(null) }
    var activeShareReel by remember { mutableStateOf<InstagramReelData?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setActiveReelPage(pagerState.currentPage)
    }

    LaunchedEffect(Unit) {
        viewModel.triggerReelComments.collect {
            val idx = pagerState.currentPage
            if (idx >= 0 && idx < remoteReelsData.size) {
                activeCommentsReel = remoteReelsData[idx]
            }
        }
    }

    // Earning timer logic for currently watched reel
    var currentProgress by remember(pagerState.currentPage) { mutableFloatStateOf(0f) }
    var showRewardToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }

    val targetSeconds = remember(adminConfig.requiredReelWatchSeconds) {
        maxOf(3, adminConfig.requiredReelWatchSeconds)
    }

    // Video watch & reward progression
    LaunchedEffect(pagerState.currentPage, targetSeconds) {
        val activeReel = remoteReelsData.getOrNull(pagerState.currentPage)
        if (activeReel != null) {
            viewModel.recordContentAdImpression(
                contentId = "reel_${activeReel.id}",
                creatorId = activeReel.handle,
                contentType = "REEL",
                placement = "REELS_NATIVE"
            )
        }

        currentProgress = 0f
        val stepMs = 150L
        val totalSteps = (targetSeconds * 1000L) / stepMs

        for (i in 1..totalSteps) {
            delay(stepMs)
            currentProgress = (i.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
        }

        // Award credit when watched required duration
        val credited = viewModel.watchReelEarnCredit(targetSeconds)
        toastMessage = "+$credited Coins Added to Wallet! 🪙"
        showRewardToast = true
        delay(2500)
        showRewardToast = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("instagram_reels_screen_root")
    ) {
        // --- 0. REEL UPLOAD PROGRESS OVERLAY ---
        val reelUploadProgress by viewModel.reelUploadProgress.collectAsState()
        reelUploadProgress?.let { progress ->
            ReelUploadOverlay(progress = progress)
        }

        // --- 1. VERTICAL PAGER (Instagram Reels Vertical Scroll) ---
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { remoteReelsData[it].id }
        ) { page ->
            val reel = remoteReelsData[page]
            val isCurrentPage = pagerState.currentPage == page

            InstagramReelPageItem(
                reel = reel,
                isCurrentPage = isCurrentPage,
                isMuted = isMuted,
                onToggleMute = {
                    isMuted = !isMuted
                    showMuteIndicator = true
                    coroutineScope.launch {
                        delay(1200)
                        showMuteIndicator = false
                    }
                },
                onLikeClicked = {
                    reel.isLiked = !reel.isLiked
                    reel.likes += if (reel.isLiked) 1 else -1
                    viewModel.toggleReelLike(
                        ReelEntity(
                            id = reel.id.toLong(),
                            author = reel.author,
                            handle = reel.handle,
                            avatarType = reel.avatarType,
                            caption = reel.caption,
                            music = reel.music,
                            imageRes = reel.imageRes,
                            videoUrl = reel.videoUrl,
                            location = reel.location,
                            likesCount = reel.likes,
                            commentsCount = reel.commentsCount,
                            sharesCount = reel.sharesCount,
                            isLiked = reel.isLiked,
                            isSaved = reel.isSaved
                        )
                    )
                },
                onDoubleTapLike = {
                    if (!reel.isLiked) {
                        reel.isLiked = true
                        reel.likes += 1
                        viewModel.toggleReelLike(
                            ReelEntity(
                                id = reel.id.toLong(),
                                author = reel.author,
                                handle = reel.handle,
                                avatarType = reel.avatarType,
                                caption = reel.caption,
                                music = reel.music,
                                imageRes = reel.imageRes,
                                videoUrl = reel.videoUrl,
                                location = reel.location,
                                likesCount = reel.likes,
                                commentsCount = reel.commentsCount,
                                sharesCount = reel.sharesCount,
                                isLiked = true,
                                isSaved = reel.isSaved
                            )
                        )
                    }
                },
                onCommentClicked = { activeCommentsReel = reel },
                onShareClicked = { activeShareReel = reel },
                onSaveClicked = { reel.isSaved = !reel.isSaved },
                onFollowClicked = { reel.isFollowing = !reel.isFollowing },
                onOpenProfile = { viewModel.setTab(com.example.ui.viewmodel.MainTab.PROFILE) },
                onEditClicked = {
                    android.widget.Toast.makeText(context, "Edit feature coming soon! ✨", android.widget.Toast.LENGTH_SHORT).show()
                },
                onDeleteClicked = {
                    remoteReels.firstOrNull { it.id.toInt() == reel.id }?.let { target ->
                        viewModel.deleteReel(target)
                        android.widget.Toast.makeText(context, "Reel deleted", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // --- 2. TOP FLOATING INSTAGRAM REELS HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Reels" Title with dropdown arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Reels",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Right Actions: Camera / Create, Sound Mute, Live Coin Watcher
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Live Watch Earning Progress Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { viewModel.openRewardScreen() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { currentProgress },
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFFFFD700),
                                trackColor = Color.White.copy(alpha = 0.2f),
                                strokeWidth = 2.5.dp
                            )
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = "Coin",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        Text(
                            text = "${wallet.totalCredits} 🪙",
                            color = Color(0xFFFFEAA7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Camera Upload Icon
                IconButton(
                    onClick = { showCreateSheet = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .testTag("ig_reels_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = "Create Reel",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // --- 3. MUTE / UNMUTE CENTER BADGE INDICATOR ---
        AnimatedVisibility(
            visible = showMuteIndicator,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.7f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Muted" else "Unmuted",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // --- 4. REWARD TOAST POPUP ---
        AnimatedVisibility(
            visible = showRewardToast,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -50 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -50 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 60.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF27AE60),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Celebration, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(
                        text = toastMessage,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // --- 5. BOTTOM SEEK PROGRESS BAR (Instagram Reel Line) ---
        LinearProgressIndicator(
            progress = { currentProgress },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(2.5.dp),
            color = Color.White.copy(alpha = 0.85f),
            trackColor = Color.White.copy(alpha = 0.15f)
        )

        // --- 6. INSTAGRAM COMMENTS BOTTOM SHEET ---
        activeCommentsReel?.let { reel ->
            InstagramReelCommentsSheet(
                reel = reel,
                myProfile = profile,
                onDismiss = { activeCommentsReel = null },
                onAddComment = { text ->
                    viewModel.addReelComment(reel.id.toLong(), text)
                    val newComment = ReelComment(
                        id = "c_${System.currentTimeMillis()}",
                        author = profile.name,
                        handle = profile.handle,
                        avatarType = profile.avatarType,
                        userAvatarPath = profile.avatarPath,
                        text = text,
                        timeAgo = "Just now",
                        likes = 0
                    )
                    reel.comments.add(0, newComment)
                    reel.commentsCount += 1
                }
            )
        }

        // --- 7. INSTAGRAM SHARE SHEET ---
        activeShareReel?.let { reel ->
            InstagramReelShareSheet(
                reel = reel,
                onDismiss = { activeShareReel = null },
                onShareExternal = {
                    val entity = remoteReels.find { it.id.toInt() == reel.id }
                    if (entity != null) {
                        viewModel.shareReel(context, entity)
                    }
                    activeShareReel = null
                },
                onSendDirect = { friendName ->
                    viewModel.sendChatMessage("Shared a reel by @${reel.handle} 🎬")
                    activeShareReel = null
                    toastMessage = "Sent to $friendName ✈️"
                    showRewardToast = true
                    coroutineScope.launch {
                        delay(2000)
                        showRewardToast = false
                    }
                }
            )
        }

        // --- 8. CREATE REEL BOTTOM SHEET ---
        if (showCreateSheet) {
            CreateReelBottomSheet(
                onDismiss = { showCreateSheet = false },
                onPublish = { newCaption, newMusic, newImageUri ->
                    showCreateSheet = false
                    coroutineScope.launch {
                        pagerState.scrollToPage(0)
                    }

                    // The reel is persisted remotely first; the list refreshes from the Room cache
                    // only after Supabase confirms it. No optimistic local-only reel is injected.
                    // We use uploadReel which generates thumbnails and handles progress.
                    viewModel.uploadReel(
                        caption = newCaption,
                        music = newMusic,
                        videoUriString = newImageUri
                    )
                }
            )
        }
    }
}

/**
 * -------------------------------------------------------------
 * INDIVIDUAL INSTAGRAM REEL PAGE
 * -------------------------------------------------------------
 */
@Composable
fun InstagramReelPageItem(
    reel: InstagramReelData,
    isCurrentPage: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onLikeClicked: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onCommentClicked: () -> Unit,
    onShareClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onFollowClicked: () -> Unit,
    onOpenProfile: () -> Unit,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var showPlayPauseIcon by remember { mutableStateOf(false) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    var isCaptionExpanded by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Smooth subtle video zoom simulation
    val infiniteTransition = rememberInfiniteTransition(label = "reel_video_zoom")
    val videoScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isCurrentPage && isPlaying) 1.05f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reel_zoom_anim"
    )

    // Continuous Vinyl Disc Rotation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_disc_rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onDoubleTapLike()
                        showDoubleTapHeart = true
                        coroutineScope.launch {
                            delay(900)
                            showDoubleTapHeart = false
                        }
                    },
                    onTap = {
                        isPlaying = !isPlaying
                        showPlayPauseIcon = true
                        coroutineScope.launch {
                            delay(800)
                            showPlayPauseIcon = false
                        }
                    }
                )
            }
            .testTag("ig_reel_page_${reel.id}")
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp, end = 10.dp)
        ) {
            IconButton(onClick = { isMenuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Reel options", tint = Color.White)
            }
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit Reel") },
                    onClick = { isMenuExpanded = false; onEditClicked() }
                )
                DropdownMenuItem(
                    text = { Text("Delete Reel", color = MaterialTheme.colorScheme.error) },
                    onClick = { isMenuExpanded = false; onDeleteClicked() }
                )
            }
        }

        // --- HARDWARE ACCELERATED EXOPLAYER WITH CLOUD CACHING ---
        com.example.media.player.ReelsVideoPlayerView(
            videoUrl = reel.videoUrl,
            thumbnailRes = reel.imageRes,
            isCurrentPage = isCurrentPage,
            isPlaying = isPlaying,
            isMuted = isMuted,
            modifier = Modifier.fillMaxSize()
        )

        // --- INSTAGRAM SHADOW GRADIENTS ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        ),
                        startY = 0f,
                        endY = 1800f
                    )
                )
        )

        // --- DOUBLE-TAP EXPLOSIVE HEART ANIMATION ---
        AnimatedVisibility(
            visible = showDoubleTapHeart,
            enter = scaleIn(initialScale = 0.2f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 1.3f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = InstagramPink,
                modifier = Modifier.size(110.dp)
            )
        }

        // --- PLAY / PAUSE CENTRAL ICON ---
        AnimatedVisibility(
            visible = showPlayPauseIcon,
            enter = scaleIn(initialScale = 0.5f) + fadeIn(),
            exit = scaleOut(targetScale = 1.2f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        // --- RIGHT-SIDE INSTAGRAM ACTION STACK ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. LIKE HEART
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onLikeClicked,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = if (reel.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (reel.isLiked) InstagramPink else Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = formatCount(reel.likes),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 2. COMMENTS BUBBLE
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onCommentClicked,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = formatCount(reel.commentsCount),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 3. SHARE / SEND PAPER PLANE
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onShareClicked,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = formatCount(reel.sharesCount),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 4. BOOKMARK / SAVE
            IconButton(
                onClick = onSaveClicked,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = if (reel.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (reel.isSaved) Color(0xFFFFD700) else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // 5. ROTATING VINYL DISC WITH FLOATING MUSIC NOTES
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clickable { onToggleMute() },
                contentAlignment = Alignment.Center
            ) {
                // Vinyl outer border
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF262626))
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .rotate(if (isPlaying) rotationAngle else 0f)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VynAvatar(avatarType = reel.avatarType, storagePath = reel.userAvatarPath, size = 30.dp)
                }
            }
        }

        // --- BOTTOM-LEFT CREATOR & CAPTION DETAILS ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, bottom = 64.dp, end = 76.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Creator Handle, Avatar & Follow Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(
                            1.5.dp,
                            Brush.linearGradient(listOf(InstagramDeepPurple, InstagramPink, InstagramYellow)),
                            CircleShape
                        )
                        .padding(2.dp)
                        .clickable { onOpenProfile() }
                ) {
                    VynAvatar(avatarType = reel.avatarType, storagePath = reel.userAvatarPath, size = 34.dp)
                }

                Text(
                    text = reel.author,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onOpenProfile() }
                )

                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = "Verified",
                    tint = InstagramBlue,
                    modifier = Modifier.size(14.dp)
                )

                // Follow Button Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (reel.isFollowing) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { onFollowClicked() }
                ) {
                    Text(
                        text = if (reel.isFollowing) "Following" else "Follow",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            // Caption (with expand "...more")
            Text(
                text = reel.caption,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = if (isCaptionExpanded) 6 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable { isCaptionExpanded = !isCaptionExpanded }
                    .padding(vertical = 2.dp)
            )

            // Location & Filter Tag (if any)
            if (reel.location.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = reel.location,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                    if (reel.effectName != null) {
                        Text(text = "· ✨ ${reel.effectName}", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }
            }

            // Audio / Soundtrack Ticker
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.35f),
                modifier = Modifier.clickable { onToggleMute() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Audio",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = reel.music,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * -------------------------------------------------------------
 * INSTAGRAM REEL COMMENTS BOTTOM SHEET
 * -------------------------------------------------------------
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramReelCommentsSheet(
    reel: InstagramReelData,
    myProfile: com.example.data.model.UserProfileEntity,
    onDismiss: () -> Unit,
    onAddComment: (String) -> Unit
) {
    var commentText by remember { mutableStateOf("") }
    val quickEmojis = listOf("❤️", "🔥", "👏", "😂", "😍", "🙌", "💯", "✨")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .navigationBarsPadding()
        ) {
            // Header
            Text(
                text = "Comments",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 10.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            // Comments List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(reel.comments, key = { it.id }) { comment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VynAvatar(avatarType = comment.avatarType, storagePath = comment.userAvatarPath, size = 36.dp)

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = comment.author,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val relTime = remember(comment.id) {
                                        // This assumes ReelComment has a timestamp or we can derive it.
                                        // Since ReelComment doesn't have it, we'll keep timeAgo for now 
                                        // unless we update ReelComment model.
                                        comment.timeAgo 
                                    }
                                    Text(
                                        text = relTime,
                                        fontSize = 11.sp,
                                        color = VynTextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = comment.text,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Reply",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VynTextSecondary,
                                    modifier = Modifier.clickable {
                                        commentText = "@${comment.handle} "
                                    }
                                )
                            }
                        }

                        // Comment Heart Like
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                comment.isLiked = !comment.isLiked
                                comment.likes += if (comment.isLiked) 1 else -1
                            }
                        ) {
                            Icon(
                                imageVector = if (comment.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (comment.isLiked) InstagramPink else VynTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            if (comment.likes > 0) {
                                Text(
                                    text = "${comment.likes}",
                                    fontSize = 10.sp,
                                    color = VynTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Quick Emoji Pill Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickEmojis.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .clickable { commentText += emoji }
                            .padding(4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            // Comment Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VynAvatar(
                    avatarType = myProfile.avatarType,
                    storagePath = myProfile.avatarPath,
                    size = 36.dp
                )

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            decorationBox = { innerTextField ->
                                if (commentText.isEmpty()) {
                                    Text(
                                        text = "Add a comment for @${reel.handle}...",
                                        fontSize = 13.sp,
                                        color = VynTextSecondary
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (commentText.isNotBlank()) {
                            Text(
                                text = "Post",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = InstagramBlue,
                                modifier = Modifier
                                    .clickable {
                                        onAddComment(commentText)
                                        commentText = ""
                                    }
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * -------------------------------------------------------------
 * INSTAGRAM REEL SHARE SHEET
 * -------------------------------------------------------------
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramReelShareSheet(
    reel: InstagramReelData,
    onDismiss: () -> Unit,
    onShareExternal: () -> Unit = {},
    onSendDirect: (String) -> Unit
) {
    val friendsList = emptyList<Pair<String, String>>()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Share Reel",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Direct Friends Horizontal Row
            Text("Send in Direct Message:", fontSize = 13.sp, color = VynTextSecondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                friendsList.forEach { (name, avatar) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onSendDirect(name) }
                            .width(68.dp)
                    ) {
                        VynAvatar(avatarType = avatar, size = 52.dp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name.split(" ").first(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Send",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = InstagramBlue
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            // Action options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onDismiss() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Story", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Add to story", fontSize = 11.sp)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onDismiss() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Copy Link", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Copy link", fontSize = 11.sp)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onShareExternal() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Share via...", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Number formatter (e.g. 1420 -> 1.4K)
 */
fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000f)
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000f)
        else -> count.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReelBottomSheet(
    onDismiss: () -> Unit,
    onPublish: (caption: String, music: String, imageUri: String) -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var selectedMusic by remember { mutableStateOf("Trending Beats - Viral Sound 🔥") }
    var selectedImage by remember { mutableStateOf("") }
    var customUriString by remember { mutableStateOf<String?>(null) }

    val videoOrImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            customUriString = it.toString()
            selectedImage = it.toString()
        }
    }

    val soundOptions = listOf(
        "Trending Beats - Viral Sound 🔥",
        "Acoustic Melodies - Chill Vibes 🎸",
        "Original Audio - My Voice 🎙️",
        "Lo-Fi Night Ride - Midnight Beat 🌙",
        "EDM Bass Drop - High Energy ⚡"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .testTag("create_reel_bottom_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Upload Instagram Reel 🎥",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = Color(0xFFFFD700).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "+50 Coins Bonus",
                        color = Color(0xFFD35400),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Media picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Video / Cover:", fontSize = 13.sp, color = VynTextSecondary)
                OutlinedButton(
                    onClick = { videoOrImageLauncher.launch("*/*") },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = "Gallery", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Choose from Phone", fontSize = 12.sp)
                }
            }

            // Thumbnail options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (customUriString != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedImage = customUriString!! }
                    ) {
                        VynImage(
                            imageResName = customUriString!!,
                            modifier = Modifier
                                .height(80.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (selectedImage == customUriString) {
                                        Modifier.background(InstagramBlue).padding(2.dp)
                                    } else Modifier
                                )
                        )
                        Text("Phone File", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // Soundtrack / Audio Selection
            Text("Select Audio Soundtrack:", fontSize = 13.sp, color = VynTextSecondary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                soundOptions.forEach { sound ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedMusic = sound },
                        color = if (selectedMusic == sound) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedMusic == sound) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selectedMusic == sound) MaterialTheme.colorScheme.primary else VynTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                sound,
                                fontSize = 13.sp,
                                fontWeight = if (selectedMusic == sound) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                placeholder = { Text("Write a caption and hashtags (#reels #vyn9 #viral)...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .testTag("create_reel_caption_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    val finalCaption = caption.ifBlank { "Check out my new Reel! 🚀 #vyn9 #reels" }
                    onPublish(finalCaption, selectedMusic, selectedImage)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("publish_reel_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Share Reel & Earn 50 🪙", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
