package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.FlareAvatar
import com.example.ui.components.FlareImage
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.SocialViewModel

/**
 * Full-screen "Create" composer (opened from the bottom + button).
 *
 * FlareOfficial-style creation flow with a big flexible media preview, clean
 * segmented type tabs (Photo / Video / Reel / Story), custom cover selection
 * for video content, audio picker, caption with counter, and a gradient
 * primary Share action. On publish the user is taken to the feed where the
 * new content is immediately visible (REELS tab for videos, HOME otherwise).
 *
 * Only the create/upload flow is changed — everything else is untouched.
 */
@Composable
fun CreatePostBottomSheet(
    viewModel: SocialViewModel,
    onDismiss: () -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("photo") } // photo | video | reel | story
    var mediaUri by remember { mutableStateOf<String?>(null) }
    var thumbMode by remember { mutableStateOf("auto") } // auto | custom
    var customThumbUri by remember { mutableStateOf<String?>(null) }
    var selectedMusic by remember { mutableStateOf("Original Audio — My Voice 🎙️") }
    var isSharing by remember { mutableStateOf(false) }
    var audioExpanded by remember { mutableStateOf(false) }

    val isVideoType = selectedType == "video" || selectedType == "reel"
    val profile by viewModel.profile.collectAsState()

    // Live upload progress (0-100) shown as a scrim while a reel/video publishes.
    val uploadProgress by viewModel.reelUploadProgress.collectAsState()
    var wasUploading by remember { mutableStateOf(false) }
    LaunchedEffect(uploadProgress) {
        if (uploadProgress != null) {
            wasUploading = true
        } else if (wasUploading) {
            // Upload finished — show the fresh reel in the Reels feed.
            wasUploading = false
            viewModel.closeCreatePostSheet()
            viewModel.setTab(MainTab.REELS)
        }
    }

    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            mediaUri = it.toString()
            customThumbUri = null
            thumbMode = "auto"
        }
    }

    val thumbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            customThumbUri = it.toString()
            thumbMode = "custom"
        }
    }

    fun pickMime(): String = if (isVideoType) "*/*" else "image/*"

    fun shareLabel(): String = when (selectedType) {
        "story" -> "Add to Story"
        "video" -> "Share Video"
        "reel" -> "Share Reel"
        else -> "Share Photo"
    }

    fun selectType(t: String) {
        val willBeVideo = t == "video" || t == "reel"
        if (willBeVideo != isVideoType) {
            // Media type changed — drop the stale pick so the preview matches.
            mediaUri = null
            customThumbUri = null
            thumbMode = "auto"
        }
        selectedType = t
    }

    fun doShare() {
        if (isSharing) return
        isSharing = true
        when (selectedType) {
            "photo" -> viewModel.createPost(caption, "post", mediaUri.orEmpty())
            "story" -> {
                val m = mediaUri
                if (m == null) return
                viewModel.addStory(m, caption)
            }
            "video", "reel" -> {
                val m = mediaUri
                if (m == null) return
                // Stay on the composer: the upload % scrim becomes visible and,
                // when publishing finishes, we drop into the Reels feed.
                viewModel.uploadReel(caption, selectedMusic, m, customThumbUri ?: "")
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxSize()
                .testTag("create_post_bottom_sheet")
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
// ── Top app bar ──────────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "Create",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { doShare() },
                        enabled = !isSharing,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                HorizontalDivider(thickness = 0.8.dp, color = FlareBorder)

                // Scrollable body — everything below the top bar fits and scrolls
                // on any screen size instead of overflowing.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
// ── Media preview (hero area) ────────────────────────────────────────────
                val media = mediaUri
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(FlareOffWhite)
                        .testTag("create_media_preview")
                ) {
                    when {
                        media == null -> CreateEmptyPreview(
                            isVideoType = isVideoType,
                            selectedType = selectedType,
                            onPick = { mediaLauncher.launch(pickMime()) }
                        )
                        isVideoType -> CreateVideoPreview(
                            media = media,
                            showCustomCover = customThumbUri != null && thumbMode == "custom",
                            customThumbUri = customThumbUri,
                            onPick = { mediaLauncher.launch(pickMime()) }
                        )
                        else -> CreatePhotoPreview(
                            media = media,
                            onPick = { mediaLauncher.launch(pickMime()) }
                        )
                    }
                }
// ── Type tabs ────────────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TypePill("Photo", Icons.Default.PhotoLibrary, selectedType == "photo") { selectType("photo") }
                    TypePill("Video", Icons.Default.VideoLibrary, selectedType == "video") { selectType("video") }
                    TypePill("Reel", Icons.Filled.PlayArrow, selectedType == "reel") { selectType("reel") }
                    TypePill("Story", Icons.Outlined.Image, selectedType == "story") { selectType("story") }
                }
// ── Caption ──────────────────────────────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = FlareOffWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlareAvatar(
                                avatarType = profile.avatarType,
                                storagePath = profile.avatarPath,
                                size = 34.dp
                            )
                            Text(
                                profile.handle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FlareBlack,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        TextField(
                            value = caption,
                            onValueChange = { if (it.length <= 300) caption = it },
                            placeholder = {
                                Text(
                                    when (selectedType) {
                                        "story" -> "Share a moment... (optional)"
                                        "video", "reel" -> "Write a caption & hashtags..."
                                        else -> "What's on your mind?"
                                    },
                                    fontSize = 13.sp,
                                    color = FlareTextSecondary
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 70.dp)
                                .testTag("create_post_caption_input")
                        )
                        Text(
                            "${caption.length}/300",
                            fontSize = 11.sp,
                            color = FlareTextSecondary,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
// ── Options ──────────────────────────────────────────────────────────────
                if (isVideoType) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = FlareOffWhite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = FlareCameraBlue, modifier = Modifier.size(18.dp))
                                    Text("Cover", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FlareBlack, modifier = Modifier.padding(start = 8.dp))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = thumbMode == "auto",
                                        onClick = { thumbMode = "auto" },
                                        label = { Text("Auto", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = FlareBlack,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                    FilterChip(
                                        selected = thumbMode == "custom",
                                        onClick = { thumbMode = "custom"; thumbLauncher.launch("image/*") },
                                        label = { Text("Custom", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = FlareBlack,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                            val thumb = customThumbUri
                            if (thumb != null && thumbMode == "custom") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FlareImage(
                                        imageResName = thumb,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                                    )
                                    Text(
                                        "This image will be the cover",
                                        fontSize = 12.sp,
                                        color = FlareTextSecondary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 10.dp)
                                    )
                                    Text(
                                        "Change",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlareCameraBlue,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { thumbLauncher.launch("image/*") }
                                            .padding(6.dp)
                                    )
                                }
                            } else {
                                Text(
                                    if (thumbMode == "custom") "Pick a cover image to replace the auto frame"
                                    else "A frame from your video is used automatically",
                                    fontSize = 12.sp,
                                    color = FlareTextSecondary
                                )
                            }
                            HorizontalDivider(thickness = 0.8.dp, color = FlareBorder)
                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { audioExpanded = true },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = FlareCameraBlue, modifier = Modifier.size(18.dp))
                                    Text(
                                        selectedMusic,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FlareBlack,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                    )
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = FlareTextSecondary, modifier = Modifier.size(18.dp))
                                }
                                DropdownMenu(expanded = audioExpanded, onDismissRequest = { audioExpanded = false }) {
                                    listOf(
                                        "Original Audio — My Voice 🎙️",
                                        "Trending Beats — Viral Sound 🔥",
                                        "Acoustic Melodies — Chill Vibes 🎸",
                                        "Lo-Fi Night Ride — Midnight Beat 🌙",
                                        "EDM Bass Drop — High Energy ⚡"
                                    ).forEach { sound ->
                                        DropdownMenuItem(
                                            text = { Text(sound, fontSize = 13.sp) },
                                            onClick = {
                                                selectedMusic = sound
                                                audioExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(FlareOffWhite)
                            .clickable { mediaLauncher.launch(pickMime()) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = FlareCameraBlue, modifier = Modifier.size(20.dp))
                        Text(
                            if (mediaUri == null) "Choose from Gallery" else "Choose a different photo",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FlareBlack,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                        )
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = FlareTextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
// ── Primary share action ─────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Button(
                        onClick = { doShare() },
                        enabled = !isSharing,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("publish_post_button")
                    ) {
                        Icon(
                            imageVector = if (isVideoType) Icons.Filled.PlayArrow else Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(shareLabel(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        when (selectedType) {
                            "video", "reel" -> "You'll see your reel in the Reels feed right after sharing"
                            "story" -> "Your story appears at the top of the Home feed"
                            else -> "Your photo appears in the Home feed right after sharing"
                        },
                        fontSize = 11.sp,
                        color = FlareTextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                } // ── end scrollable body ──
            }

            // Full-screen upload scrim with live % while a reel/video publishes.
            uploadProgress?.let { progress ->
                UploadScrim(progress = progress)
            }
            }
        }
    }
}
@Composable
private fun TypePill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) FlareBlack else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { onClick() }
            .testTag("create_type_${title.lowercase()}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else FlareTextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 5.dp)
            )
        }
    }
}

@Composable
private fun CreateEmptyPreview(
    isVideoType: Boolean,
    selectedType: String,
    onPick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .clickable { onPick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = FlareCameraBlue.copy(alpha = 0.12f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = if (isVideoType) Icons.Default.VideoLibrary else Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = FlareCameraBlue,
                modifier = Modifier.size(42.dp)
            )
        }
        Text(
            when (selectedType) {
                "story" -> "Share a photo to your story"
                "video", "reel" -> "Select a video for your reel"
                else -> "Select a photo to share"
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = FlareBlack,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            "Tap anywhere or use the button below",
            fontSize = 12.sp,
            color = FlareTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
        Button(
            onClick = onPick,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = FlareCameraBlue),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
            modifier = Modifier
                .padding(top = 18.dp)
                .testTag("create_choose_media_button")
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (isVideoType) "Choose Video" else "Choose from Gallery",
                fontWeight = FontWeight.Bold, fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun CreateVideoPreview(
    media: String,
    showCustomCover: Boolean,
    customThumbUri: String?,
    onPick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (showCustomCover && customThumbUri != null) {
            FlareImage(imageResName = customThumbUri, modifier = Modifier.fillMaxSize())
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (!showCustomCover) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.92f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(84.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = FlareBlack,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                fileName(media),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clickable { onPick() }
        ) {
            Text(
                "Change",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun CreatePhotoPreview(
    media: String,
    onPick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        FlareImage(imageResName = media, modifier = Modifier.fillMaxSize())
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clickable { onPick() }
        ) {
            Text(
                "Change",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private fun fileName(uri: String): String {
    val lastSlash = uri.lastIndexOf('/')
    val base = if (lastSlash >= 0) uri.substring(lastSlash + 1) else uri
    return if (base.length > 22) base.take(22) + "…" else base
}

/**
 * Full-screen scrim shown while a reel/video is publishing, with the live
 * upload percentage (same progress source as the Reels feed overlay).
 */
@Composable
private fun UploadScrim(progress: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                CircularProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 6.dp
                )
                Text(
                    "Uploading… $progress%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlareBlack,
                    modifier = Modifier.padding(top = 16.dp)
                )
                LinearProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                )
                Text(
                    "Please keep the app open until it finishes",
                    fontSize = 12.sp,
                    color = FlareTextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}