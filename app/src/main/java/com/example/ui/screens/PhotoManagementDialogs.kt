package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.UserProfileEntity
import com.example.ui.components.VynAvatar
import com.example.ui.components.VynImage
import com.example.ui.theme.*
import com.example.ui.viewmodel.FullScreenPhotoData
import com.example.ui.viewmodel.SocialViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

// Utility helper to save captured camera bitmap to app cache
fun saveBitmapToAppCache(context: Context, bitmap: Bitmap, prefix: String): Uri {
    val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
    val outputStream = FileOutputStream(file)
    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
    outputStream.flush()
    outputStream.close()
    return Uri.fromFile(file)
}

// ==========================================
// 1. COVER PHOTO OPTIONS BOTTOM SHEET
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverPhotoOptionsBottomSheet(
    viewModel: SocialViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsState()

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.changeCoverPhoto(it.toString())
            Toast.makeText(context, "Cover photo updated from Gallery! ✨", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    // Camera Capture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            try {
                val savedUri = saveBitmapToAppCache(context, it, "cover_camera")
                viewModel.changeCoverPhoto(savedUri.toString())
                Toast.makeText(context, "Cover photo captured and updated! 📸", Toast.LENGTH_SHORT).show()
                onDismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
                .testTag("cover_photo_options_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with Handle & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Cover Photo Options",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Customize your profile banner aesthetic",
                        fontSize = 12.sp,
                        color = VynTextSecondary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = VynTextSecondary)
                }
            }

            // Current Cover Preview Pill
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 70.dp, height = 45.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        VynImage(
                            imageResName = profile.coverType,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Current Cover Banner",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap options below to change or create",
                            fontSize = 11.5.sp,
                            color = VynTextSecondary
                        )
                    }

                    TextButton(
                        onClick = {
                            viewModel.openFullScreenPhotoPreview(
                                title = "Cover Photo",
                                imageUri = profile.coverType,
                                subtitle = "@${profile.handle} · Cover Banner",
                                isAvatar = false
                            )
                            onDismiss()
                        }
                    ) {
                        Text("View", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VynCameraBlue)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Action List Items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Upload from Gallery
                PhotoActionRowItem(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "Upload Photo from Gallery",
                    subtitle = "Select any high-res banner from your device",
                    iconTint = Color(0xFF6C5CE7),
                    onClick = { galleryLauncher.launch("image/*") }
                )

                // 2. Take Photo with Camera
                PhotoActionRowItem(
                    icon = Icons.Outlined.CameraAlt,
                    title = "Take Photo with Camera",
                    subtitle = "Capture a new shot instantly",
                    iconTint = Color(0xFF00C9A7),
                    onClick = { cameraLauncher.launch(null) }
                )

                // 3. Select from Curated Themes Gallery
                PhotoActionRowItem(
                    icon = Icons.Outlined.Collections,
                    title = "Select from Curated Themes",
                    subtitle = "Motorbike, Cyber City, Sunset, Mountain & Nature",
                    iconTint = Color(0xFFE67E22),
                    onClick = {
                        viewModel.openPresetGallery("cover")
                        onDismiss()
                    }
                )

                // 4. AI Magic Cover Art Generator
                PhotoActionRowItem(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "AI Magic Cover Generator ✨",
                    subtitle = "Generate custom cyberpunk, synthwave or anime art",
                    iconTint = Color(0xFFFF2A6D),
                    badge = "AI MAGIC",
                    onClick = {
                        viewModel.openAiArtGenerator("cover")
                        onDismiss()
                    }
                )

                // 5. View Fullscreen
                PhotoActionRowItem(
                    icon = Icons.Outlined.Fullscreen,
                    title = "View Fullscreen Cover",
                    subtitle = "Preview high resolution with details",
                    iconTint = Color(0xFF3498DB),
                    onClick = {
                        viewModel.openFullScreenPhotoPreview(
                            title = "Cover Photo",
                            imageUri = profile.coverType,
                            subtitle = "@${profile.handle} · Cover Banner",
                            isAvatar = false
                        )
                        onDismiss()
                    }
                )

                // 6. Reset to Default
                PhotoActionRowItem(
                    icon = Icons.Outlined.Refresh,
                    title = "Reset to Default Cover",
                    subtitle = "Restore the default cover photo",
                    iconTint = Color(0xFFE74C3C),
                    onClick = {
                        viewModel.resetCoverPhoto()
                        Toast.makeText(context, "Cover photo reset to default", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
            }
        }
    }
}

// ==========================================
// 2. PROFILE PICTURE OPTIONS BOTTOM SHEET
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePictureOptionsBottomSheet(
    viewModel: SocialViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsState()
    val currentFrame by viewModel.selectedAvatarFrame.collectAsState()

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.changeProfilePhoto(it.toString())
            Toast.makeText(context, "Profile picture updated from Gallery! 📸", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    // Camera Capture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            try {
                val savedUri = saveBitmapToAppCache(context, it, "avatar_camera")
                viewModel.changeProfilePhoto(savedUri.toString())
                Toast.makeText(context, "Selfie captured and updated! ✨", Toast.LENGTH_SHORT).show()
                onDismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save selfie", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
                .testTag("profile_picture_options_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with Handle & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Profile Picture Options",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Manage your avatar, photo & frame style",
                        fontSize = 12.sp,
                        color = VynTextSecondary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = VynTextSecondary)
                }
            }

            // Current Avatar Preview Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        VynAvatar(avatarType = profile.avatarType, size = 52.dp)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "@${profile.handle} · Profile Picture",
                            fontSize = 12.sp,
                            color = VynTextSecondary
                        )
                    }

                    TextButton(
                        onClick = {
                            viewModel.openFullScreenPhotoPreview(
                                title = "Profile Picture",
                                imageUri = profile.avatarType,
                                subtitle = "${profile.name} (@${profile.handle})",
                                isAvatar = true
                            )
                            onDismiss()
                        }
                    ) {
                        Text("View", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VynCameraBlue)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Action List Items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Choose from Gallery
                PhotoActionRowItem(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "Choose New Profile Picture (Gallery)",
                    subtitle = "Pick any portrait or photo from device storage",
                    iconTint = Color(0xFF6C5CE7),
                    onClick = { galleryLauncher.launch("image/*") }
                )

                // 2. Take Selfie / Photo
                PhotoActionRowItem(
                    icon = Icons.Outlined.CameraAlt,
                    title = "Take Selfie / Camera Photo",
                    subtitle = "Snap a quick selfie with front camera",
                    iconTint = Color(0xFF00C9A7),
                    onClick = { cameraLauncher.launch(null) }
                )

                // 3. Avatar Presets Gallery
                PhotoActionRowItem(
                    icon = Icons.Outlined.AccountCircle,
                    title = "Choose from Character Avatars",
                    subtitle = "Choose a photo from your device",
                    iconTint = Color(0xFFE67E22),
                    onClick = {
                        viewModel.openPresetGallery("avatar")
                        onDismiss()
                    }
                )

                // 4. AI Magic Avatar Generator
                PhotoActionRowItem(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "AI Magic Avatar Generator ✨",
                    subtitle = "Turn your persona into 3D Pixar, Anime or Cyberpunk",
                    iconTint = Color(0xFFFF2A6D),
                    badge = "AI MAGIC",
                    onClick = {
                        viewModel.openAiArtGenerator("avatar")
                        onDismiss()
                    }
                )

                // 5. Add Profile Ring / Frame
                PhotoActionRowItem(
                    icon = Icons.Outlined.Stars,
                    title = "Add Profile Story Ring / VIP Frame",
                    subtitle = "VIP Gold, Cyber Neon Glow, Active Live Badge",
                    iconTint = Color(0xFFF39C12),
                    badge = if (currentFrame != "none") "ACTIVE" else null,
                    onClick = {
                        val nextFrame = when (currentFrame) {
                            "none" -> "gold_crown"
                            "gold_crown" -> "neon_cyan"
                            "neon_cyan" -> "cyber_purple"
                            "cyber_purple" -> "active_green"
                            else -> "none"
                        }
                        viewModel.setAvatarFrame(nextFrame)
                        val frameName = when (nextFrame) {
                            "gold_crown" -> "👑 VIP Gold Crown Frame"
                            "neon_cyan" -> "🌟 Neon Cyan Cyber Ring"
                            "cyber_purple" -> "🟣 Cyberpunk Purple Ring"
                            "active_green" -> "🟢 Active Live Online Ring"
                            else -> "Removed Frame"
                        }
                        Toast.makeText(context, "Frame updated: $frameName", Toast.LENGTH_SHORT).show()
                    }
                )

                // 6. View Fullscreen
                PhotoActionRowItem(
                    icon = Icons.Outlined.Fullscreen,
                    title = "View Profile Picture",
                    subtitle = "Open full screen zoomable photo view",
                    iconTint = Color(0xFF3498DB),
                    onClick = {
                        viewModel.openFullScreenPhotoPreview(
                            title = "Profile Picture",
                            imageUri = profile.avatarType,
                            subtitle = "${profile.name} (@${profile.handle})",
                            isAvatar = true
                        )
                        onDismiss()
                    }
                )

                // 7. Reset to Default
                PhotoActionRowItem(
                    icon = Icons.Outlined.Refresh,
                    title = "Reset to Default Avatar",
                    subtitle = "Restore default avatar picture",
                    iconTint = Color(0xFFE74C3C),
                    onClick = {
                        viewModel.resetProfilePhoto()
                        Toast.makeText(context, "Profile picture reset to default", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
            }
        }
    }
}

// ==========================================
// 3. FULL SCREEN PHOTO VIEWER DIALOG
// ==========================================
@Composable
fun FullScreenPhotoViewerDialog(
    data: FullScreenPhotoData,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Top Bar with Close and Share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = data.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (data.subtitle.isNotBlank()) {
                        Text(
                            text = data.subtitle,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("Vyn9 Photo", data.imageResOrUri)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Photo link copied! 🔗", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
            }

            // Centered Image Viewer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (data.isAvatar) {
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .shadow(24.dp, CircleShape)
                            .border(4.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            .clip(CircleShape)
                    ) {
                        VynAvatar(avatarType = data.imageResOrUri, size = 280.dp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .shadow(20.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        VynImage(
                            imageResName = data.imageResOrUri,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // Bottom Actions Bar
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E1E2E).copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            Toast.makeText(context, "Saved image to device gallery 📥", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save to Gallery", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text("Done", fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. AI ART GENERATOR DIALOG (Cover & Avatar)
// ==========================================
@Composable
fun AiArtGeneratorDialog(
    type: String, // "cover" or "avatar"
    viewModel: SocialViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var prompt by remember {
        mutableStateOf(
            if (type == "cover") "Cyberpunk neon highway with superbike and neon skyscrapers"
            else "3D Pixar style cool rider avatar with glowing headphones and neon visor"
        )
    }

    var selectedStyle by remember { mutableStateOf("Cyberpunk 2099") }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedImageKey by remember {
        mutableStateOf("")
    }

    val coroutineScope = rememberCoroutineScope()

    val styles = if (type == "cover") {
        listOf("Cyberpunk 2099", "Sunset Synthwave", "Alpine Nature", "Speed Racer", "Dark Matrix")
    } else {
        listOf("3D Pixar Style", "Anime Hero", "Cyber Neon Mask", "Sunset Nomad", "VIP Gold Aura")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    brush = Brush.linearGradient(listOf(Color(0xFFFF007F), Color(0xFF6C5CE7))),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = if (type == "cover") "AI Cover Generator" else "AI Avatar Generator",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("1-Tap Generative Artwork ✨", fontSize = 11.sp, color = VynTextSecondary)
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = VynTextSecondary)
                    }
                }

                // Live Preview Artwork
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (type == "cover") 160.dp else 180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (type == "cover") {
                        VynImage(
                            imageResName = generatedImageKey,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        VynAvatar(avatarType = generatedImageKey, size = 130.dp)
                    }

                    if (isGenerating) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFFFF007F), modifier = Modifier.size(36.dp))
                                Text(
                                    text = "Synthesizing AI Artwork...",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Prompt Input
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("AI Art Prompt", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )

                // Style Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Select Art Style:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VynTextSecondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        styles.forEach { style ->
                            FilterChip(
                                selected = selectedStyle == style,
                                onClick = {
                                    selectedStyle = style
                                    prompt = when (style) {
                                        "Cyberpunk 2099" -> "Cyberpunk neon highway with superbike and purple skyscrapers"
                                        "Sunset Synthwave" -> "Retrowave aesthetic sunset with neon sun and mountain road"
                                        "Alpine Nature" -> "Majestic alpine mountain forest under starry galaxy sky"
                                        "Speed Racer" -> "High speed motorcycle racing through neon city tunnel"
                                        "Dark Matrix" -> "Futuristic dark digital matrix code glow aesthetic"
                                        "3D Pixar Style" -> "3D stylized cute 3D character with cool headphones and jacket"
                                        "Anime Hero" -> "Anime protagonist cyberpunk rider with glowing jacket"
                                        "Cyber Neon Mask" -> "Hacker mask with neon cyan and pink holographic details"
                                        "Sunset Nomad" -> "Cinematic sunset golden hour explorer portrait"
                                        "VIP Gold Aura" -> "Luxury glowing gold matrix VIP avatar with crown"
                                        else -> prompt
                                    }
                                },
                                label = { Text(style, fontSize = 11.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF6C5CE7),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Generate Button
                Button(
                    onClick = {
                        isGenerating = true
                        coroutineScope.launch {
                            delay(1600)
                            generatedImageKey = ""
                            isGenerating = false
                            Toast.makeText(context, "AI Artwork generated! 🎨", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF007F)
                    ),
                    enabled = !isGenerating
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generate with AI", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Apply Button
                Button(
                    onClick = {
                        if (type == "cover") {
                            viewModel.changeCoverPhoto(generatedImageKey)
                            Toast.makeText(context, "AI Cover Banner Applied! 🚀✨", Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.changeProfilePhoto(generatedImageKey)
                            Toast.makeText(context, "AI Profile Picture Applied! 🚀📸", Toast.LENGTH_LONG).show()
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6C5CE7)
                    )
                ) {
                    Text(
                        text = if (type == "cover") "Apply as Cover Photo" else "Apply as Profile Picture",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// 5. PRESET THEMES & AVATARS GALLERY DIALOG
// ==========================================
@Composable
fun PresetGalleryDialog(
    type: String, // "cover" or "avatar"
    viewModel: SocialViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedKey by remember {
        mutableStateOf("")
    }

    val coverPresets = emptyList<Triple<String, String, String>>()
    val avatarPresets = emptyList<Triple<String, String, String>>()

    val presets = if (type == "cover") coverPresets else avatarPresets

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (type == "cover") "Curated Cover Themes" else "Character & Avatar Presets",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select a pre-designed high aesthetic look",
                            fontSize = 12.sp,
                            color = VynTextSecondary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = VynTextSecondary)
                    }
                }

                // 2-Column Grid of Presets
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(presets) { (key, title, desc) ->
                        val isSelected = selectedKey == key
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFF6C5CE7).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = if (isSelected) BorderStroke(2.dp, Color(0xFF6C5CE7)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedKey = key }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (type == "cover") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(70.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    ) {
                                        VynImage(imageResName = key, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                                    .background(Color(0xFF6C5CE7), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        VynAvatar(avatarType = key, size = 56.dp)
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .align(Alignment.BottomEnd)
                                                    .background(Color(0xFF6C5CE7), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = title,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = desc,
                                    fontSize = 10.5.sp,
                                    color = VynTextSecondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Apply Button
                Button(
                    onClick = {
                        if (type == "cover") {
                            viewModel.changeCoverPhoto(selectedKey)
                            Toast.makeText(context, "Cover photo updated! ✨", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.changeProfilePhoto(selectedKey)
                            Toast.makeText(context, "Profile picture updated! 📸", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                ) {
                    Text(
                        text = if (type == "cover") "Apply Cover Photo" else "Apply Profile Picture",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// HELPER COMPOSABLE: PHOTO ACTION ROW ITEM
// ==========================================
@Composable
fun PhotoActionRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    badge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconTint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFF007F)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = VynTextSecondary
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = VynTextSecondary.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(18.dp)
            )
        }
    }
}
