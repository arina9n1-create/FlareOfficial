package com.example.media.player

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.components.VynImage

/**
 * -------------------------------------------------------------
 * High-Performance ExoPlayer Reels & Video Composable
 * -------------------------------------------------------------
 * - Hardware accelerated PlayerView with RESIZE_MODE_ZOOM
 * - Automatic cloud caching & instant disk retrieval
 * - Zero-lag preloaded playback
 * - Buffering state indicator & cache status badge
 * - Lifecycle-aware auto play/pause management
 */
@OptIn(UnstableApi::class)
@Composable
fun ReelsVideoPlayerView(
    videoUrl: String?,
    thumbnailRes: String,
    isCurrentPage: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
    onBufferingStateChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isBuffering by remember { mutableStateOf(false) }
    var isCachedOnDisk by remember { mutableStateOf(false) }
    var hasRenderedFirstFrame by remember { mutableStateOf(false) }
    var hasPlaybackError by remember { mutableStateOf(false) }

    // Check disk cache status
    LaunchedEffect(videoUrl) {
        val resolved = com.example.util.MediaStorageResolver.resolve(videoUrl)
        if (resolved.isNotBlank()) {
            isCachedOnDisk = ExoPlayerCacheManager.isVideoCached(context, resolved)
        }
    }

    // Manage ExoPlayer Lifecycle & Instance
    val exoPlayer = remember {
        ExoPlayerCacheManager.createOptimizedExoPlayer(context, isMuted)
    }

    // Set up Player Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = playbackState == Player.STATE_BUFFERING
                isBuffering = buffering
                onBufferingStateChange(buffering)
                if (playbackState == Player.STATE_READY) {
                    hasPlaybackError = false
                    val resolved = com.example.util.MediaStorageResolver.resolve(videoUrl)
                    if (resolved.isNotBlank()) {
                        isCachedOnDisk = ExoPlayerCacheManager.isVideoCached(context, resolved)
                    }
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                hasPlaybackError = false
            }

            override fun onPlayerError(error: PlaybackException) {
                hasPlaybackError = true
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Handle Media Source updates
    LaunchedEffect(videoUrl) {
        hasRenderedFirstFrame = false
        hasPlaybackError = false
        val resolved = com.example.util.MediaStorageResolver.resolve(videoUrl)
        
        if (resolved.isNotBlank()) {
            try {
                val uri = Uri.parse(resolved)
                val mediaSource = ExoPlayerCacheManager.createCachedMediaSource(context, uri)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
            } catch (_: Exception) {
                // Fallback to standard MediaItem
                val mediaItem = MediaItem.fromUri(Uri.parse(resolved))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
            }
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // Handle Play / Pause based on pager state and user interaction
    LaunchedEffect(isCurrentPage, isPlaying, hasPlaybackError) {
        if (isCurrentPage && isPlaying && !hasPlaybackError) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Handle Mute / Unmute
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Observe App Lifecycle (pause when app is backgrounded, resume when active)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isCurrentPage && isPlaying && !hasPlaybackError) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val hasValidVideo = !videoUrl.isNullOrBlank() && !hasPlaybackError

        if (hasValidVideo) {
            // AndroidView wrapping optimized PlayerView
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    if (playerView.player != exoPlayer) {
                        playerView.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Show Thumbnail placeholder while buffering first frame or if video fallback is needed
        if (!hasRenderedFirstFrame || !hasValidVideo) {
            VynImage(
                imageResName = thumbnailRes,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Buffering Indicator
        AnimatedVisibility(
            visible = isBuffering && isCurrentPage,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = Color(0xFF00B894)
                    )
                    Text(
                        text = "Buffering Stream...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Top-left Cloud Cache Status Badge (indicates ExoPlayer disk caching)
        AnimatedVisibility(
            visible = isCurrentPage && hasValidVideo,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 56.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isCachedOnDisk) Color(0xFF00B894).copy(alpha = 0.25f) else Color(0xFF0984E3).copy(alpha = 0.25f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCachedOnDisk) Color(0xFF00B894).copy(alpha = 0.6f) else Color(0xFF0984E3).copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = if (isCachedOnDisk) Color(0xFF00B894) else Color(0xFF0984E3),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isCachedOnDisk) "ExoCache: Instant 0ms" else "ExoPlayer: Caching",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
