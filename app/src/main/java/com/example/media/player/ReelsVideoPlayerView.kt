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
import com.example.ui.components.FlareImage
import kotlinx.coroutines.launch

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
    storagePath: String? = null,
    thumbnailPath: String? = null,
    onBufferingStateChange: (Boolean) -> Unit = {},
    onPlaybackStateChange: (Boolean) -> Unit = {},
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isBuffering by remember { mutableStateOf(false) }
    var isCachedOnDisk by remember { mutableStateOf(false) }
    var hasRenderedFirstFrame by remember { mutableStateOf(false) }
    var hasPlaybackError by remember { mutableStateOf(false) }

    val isBroken = remember(videoUrl, storagePath) {
        com.example.util.MediaStorageResolver.isBrokenLegacyB2(videoUrl, storagePath)
    }

    if (isBroken) {
        SideEffect { onError() }
        return
    }
    
    // ... rest of the code ...

    // Manage ExoPlayer Lifecycle & Instance
    val exoPlayer = remember {
        ExoPlayerCacheManager.createOptimizedExoPlayer(context, isMuted)
    }

    // Set up Player Listener
    var retriedWithFreshToken by remember { mutableStateOf(false) }
    val retryScope = rememberCoroutineScope()
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = playbackState == Player.STATE_BUFFERING
                isBuffering = buffering
                onBufferingStateChange(buffering)
                if (playbackState == Player.STATE_READY) {
                    hasPlaybackError = false
                    val resolved = com.example.util.MediaStorageResolver.resolve(videoUrl, storagePath)
                    if (resolved.isNotBlank()) {
                        isCachedOnDisk = ExoPlayerCacheManager.isVideoCached(context, resolved)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlaybackStateChange(isPlaying)
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                hasPlaybackError = false
            }

            override fun onPlayerError(error: PlaybackException) {
                hasPlaybackError = true
                isBuffering = false
                // Auth tokens rotate hourly; a 401 mid-session is almost always a
                // stale token. Refresh once and retry before giving up.
                if (!retriedWithFreshToken) {
                    retriedWithFreshToken = true
                    retryScope.launch {
                        ExoPlayerCacheManager.ensureFreshToken(context)
                        val resolved = com.example.util.MediaStorageResolver.resolve(videoUrl, storagePath)
                        if (resolved.isNotBlank()) {
                            runCatching {
                                exoPlayer.setMediaSource(
                                    ExoPlayerCacheManager.createCachedMediaSource(context, Uri.parse(resolved))
                                )
                                exoPlayer.prepare()
                                hasPlaybackError = false
                            }
                        } else {
                            onError()
                        }
                    }
                } else {
                    onError()
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            onPlaybackStateChange(false)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Handle Media Source updates
    LaunchedEffect(videoUrl, storagePath) {
        hasRenderedFirstFrame = false
        hasPlaybackError = false
        // The r2-download media gateway needs a valid Bearer token. A stale
        // (expired ~1h) token makes every video 401 and the reel buffers forever,
        // so refresh the session first when needed.
        ExoPlayerCacheManager.ensureFreshToken(context)
        val resolved = com.example.util.MediaStorageResolver.resolve(videoUrl, storagePath)
        
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
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val hasValidVideo = (!videoUrl.isNullOrBlank() || !storagePath.isNullOrBlank()) && !hasPlaybackError

        if (hasValidVideo) {
            // AndroidView wrapping optimized PlayerView
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
            FlareImage(
                imageResName = thumbnailRes,
                storagePath = thumbnailPath,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }


    // -------------------------------------------------------------
    // NOTE: The "ExoCache" status badge was intentionally removed from the
    // overlay — disk caching still works silently in the background.
    // -------------------------------------------------------------
    // Top-left debug badge removed for a clean full-screen viewing experience.
    val _unusedCacheStatus = isCachedOnDisk // state kept for future diagnostics
    }
}
