package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.PostEntity
import com.example.data.model.UserProfileEntity
import com.example.ui.components.FriendsSectionView
import com.example.ui.components.VynAvatar
import com.example.ui.components.VynImage
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.ProfileSubTab
import com.example.ui.viewmodel.SocialViewModel

@Composable
fun ProfileScreen(
    viewModel: SocialViewModel,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.profile.collectAsState()
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val allReels by viewModel.reels.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val savedPosts by viewModel.savedPosts.collectAsState()
    val repostedPosts by viewModel.repostedPosts.collectAsState()
    val subTab by viewModel.profileSubTab.collectAsState()
    val avatarFrame by viewModel.selectedAvatarFrame.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val userPosts = remember(posts, profile.handle, profile.name) {
        posts.filter {
            it.userHandle.equals(profile.handle, ignoreCase = true) ||
            it.username.equals(profile.name, ignoreCase = true)
        }
    }

    val userReels = remember(allReels, profile.handle, profile.name) {
        allReels.filter {
            it.handle.equals(profile.handle, ignoreCase = true) ||
            it.author.equals(profile.name, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen_content"),
        contentPadding = PaddingValues(top = 70.dp, bottom = 90.dp)
    ) {
        // 1. Cover Photo & Avatar Header Section
        item {
            ProfileHeaderSection(
                profile = profile,
                frameStyle = avatarFrame,
                onEditCover = { viewModel.openCoverPhotoOptions() },
                onEditAvatar = { viewModel.openAvatarOptions() },
                onRemoveCover = { viewModel.removeCoverPhoto() },
                onRemoveAvatar = { viewModel.removeProfilePhoto() },
                onDeleteCover = { viewModel.deleteCoverPhoto() },
                onDeleteAvatar = { viewModel.deleteProfilePhoto() }
            )
        }

        // 2. User Bio & Stats Section
        item {
            ProfileDetailsSection(
                profile = profile.copy(
                    postsCount = maxOf(profile.postsCount, userPosts.size),
                    friendsCount = maxOf(profile.friendsCount, friends.size)
                ),
                role = currentUserRole,
                showIdentity = false,
                onEditProfileClick = { viewModel.openEditProfile() },
                onShareProfileClick = { viewModel.openShareProfile() },
                onCreateClick = { viewModel.openCreatePostSheet() },
                onFriendsClick = { viewModel.setProfileSubTab(ProfileSubTab.FRIENDS) }
            )
        }

        // 2.2 Super Admin / Staff Quick Access Banner (Only shown if authorized)
        if (viewModel.canAccessAdminPanel()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(currentUserRole.badgeColorHex).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(currentUserRole.badgeColorHex).copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { viewModel.openAdminScreen() }
                        .testTag("profile_admin_panel_shortcut")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(currentUserRole.iconEmoji, fontSize = 16.sp)
                            Column {
                                Text(
                                    text = if (viewModel.isSuperAdmin()) "Super Admin Control Panel 👑" else "Staff Control Panel 🛡️",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Manage users, permissions, storage & payouts",
                                    fontSize = 11.sp,
                                    color = VynTextSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = Color(currentUserRole.badgeColorHex),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        // 2.5 Creator Monetization Quick Access Banner
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF6C5CE7).copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { viewModel.openMonetizationScreen() }
                    .testTag("profile_monetization_shortcut")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Diamond,
                            contentDescription = "Monetization",
                            tint = Color(0xFF6C5CE7),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Creator Monetization & Wallet 💎",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF6C5CE7)
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = Color(0xFF6C5CE7).copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // 3. Tab Row (Grid, Video, Friends, Repost, Bookmark)
        item {
            ProfileSubTabRow(
                selectedTab = subTab,
                onTabSelect = { viewModel.setProfileSubTab(it) }
            )
        }

        // 4. Content based on active tab
        when (subTab) {
            ProfileSubTab.GRID -> {
                if (userPosts.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Outlined.GridOn,
                            title = "No Posts Yet",
                            subtitle = "Photos and updates you share will appear here.",
                            actionButtonText = "+ Create New Post",
                            onActionClick = { viewModel.openCreatePostSheet() }
                        )
                    }
                } else {
                    items(userPosts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onLikeClick = { viewModel.toggleLike(post) },
                            onCommentClick = { viewModel.openComments(post) },
                            onRepostClick = { viewModel.toggleRepost(post) },
                            onShareClick = { viewModel.sharePost(context, post) },
                            onSaveClick = { viewModel.toggleSave(post) },
                            onDeleteClick = { viewModel.deletePost(post) }
                        )
                    }
                }
            }
            ProfileSubTab.REELS -> {
                if (userReels.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Outlined.VideoLibrary,
                            title = "No Reels Yet",
                            subtitle = "Capture and share short vertical video clips.",
                            actionButtonText = "+ Create Reel",
                            onActionClick = { viewModel.setTab(MainTab.REELS) }
                        )
                    }
                } else {
                    items(userReels, key = { it.id }) { reel ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { viewModel.setTab(MainTab.REELS) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp, 80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    VynImage(
                                        imageResName = reel.imageRes.ifBlank { reel.videoUrl.orEmpty() },
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = reel.caption.ifBlank { "Reel by @${reel.handle}" },
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("❤️ ${reel.likesCount} likes", fontSize = 12.sp, color = VynTextSecondary)
                                        Text("💬 ${reel.commentsCount} comments", fontSize = 12.sp, color = VynTextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            ProfileSubTab.FRIENDS -> {
                item {
                    FriendsSectionView(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            ProfileSubTab.REPOSTS -> {
                if (repostedPosts.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Default.Repeat,
                            title = "No Reposts",
                            subtitle = "Posts you repost to your profile will show up here."
                        )
                    }
                } else {
                    items(repostedPosts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onLikeClick = { viewModel.toggleLike(post) },
                            onCommentClick = { viewModel.openComments(post) },
                            onRepostClick = { viewModel.toggleRepost(post) },
                            onShareClick = { viewModel.sharePost(context, post) },
                            onSaveClick = { viewModel.toggleSave(post) },
                            onDeleteClick = { viewModel.deletePost(post) }
                        )
                    }
                }
            }
            ProfileSubTab.SAVED -> {
                if (savedPosts.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Outlined.BookmarkBorder,
                            title = "No Saved Posts",
                            subtitle = "Save photos and videos to view them again later."
                        )
                    }
                } else {
                    items(savedPosts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onLikeClick = { viewModel.toggleLike(post) },
                            onCommentClick = { viewModel.openComments(post) },
                            onRepostClick = { viewModel.toggleRepost(post) },
                            onShareClick = { viewModel.sharePost(context, post) },
                            onSaveClick = { viewModel.toggleSave(post) },
                            onDeleteClick = { viewModel.deletePost(post) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileHeaderSection(
    profile: UserProfileEntity,
    onEditCover: () -> Unit,
    onEditAvatar: () -> Unit,
    onRemoveCover: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onDeleteCover: () -> Unit,
    onDeleteAvatar: () -> Unit,
    frameStyle: String = "none",
    modifier: Modifier = Modifier
) {
    var coverMenuExpanded by remember { mutableStateOf(false) }
    var avatarMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        // Dynamic Cover Photo Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clickable(onClick = onEditCover)
        ) {
            VynImage(
                imageResName = profile.coverType,
                storagePath = profile.coverPath,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("profile_cover_image"),
                contentScale = ContentScale.Crop
            )

            // Strong bottom fade keeps the identity readable on light cover photos.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.45f to Color.Transparent,
                                0.78f to Color.Black.copy(alpha = 0.42f),
                                1f to Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
            )
        }

        // Camera button on cover photo (MOVED TO 3-DOT MENU)

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 4.dp)
        ) {
            IconButton(onClick = { coverMenuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Cover photo options", tint = Color.White)
            }
            DropdownMenu(
                expanded = coverMenuExpanded,
                onDismissRequest = { coverMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Edit Cover") 
                        }
                    },
                    onClick = {
                        coverMenuExpanded = false
                        onEditCover()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Remove Cover", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        coverMenuExpanded = false
                        onRemoveCover()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Cover Permanently", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        coverMenuExpanded = false
                        onDeleteCover()
                    }
                )
            }
        }

        // Circular Profile Avatar (overlapping bottom left)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Frame Ring Container
            val frameBrush = when (frameStyle) {
                "gold_crown" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFF8C00)))
                "neon_cyan" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF00F2FE), Color(0xFF4FACFE)))
                "cyber_purple" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFF007F), Color(0xFF7928CA)))
                "active_green" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2ECC71), Color(0xFF27AE60)))
                else -> null
            }

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .then(
                        if (frameBrush != null) {
                            Modifier
                                .background(brush = frameBrush, shape = CircleShape)
                                .padding(3.5.dp)
                        } else Modifier
                    )
                    .clip(CircleShape)
                    .clickable(onClick = onEditAvatar)
            ) {
                VynAvatar(
                    avatarType = profile.avatarType,
                    storagePath = profile.avatarPath,
                    size = 110.dp,
                    borderWidth = if (frameBrush == null) 3.5.dp else 0.dp,
                    borderColor = MaterialTheme.colorScheme.background,
                    hasStoryRing = false,
                    onClick = onEditAvatar,
                    modifier = Modifier.testTag("profile_avatar_image")
                )
            }
            Text(
                text = profile.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = LocalTextStyle.current.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.75f),
                        blurRadius = 4f
                    )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("profile_header_display_name")
            )
            Text(
                text = "@${profile.handle}",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.9f),
                style = LocalTextStyle.current.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.75f),
                        blurRadius = 3f
                    )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("profile_header_handle_text")
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 126.dp, bottom = 68.dp)
        ) {
                IconButton(onClick = { avatarMenuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Profile photo options", tint = Color.White)
                }
                DropdownMenu(
                    expanded = avatarMenuExpanded,
                    onDismissRequest = { avatarMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Profile Photo") },
                        onClick = {
                            avatarMenuExpanded = false
                            onEditAvatar()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove Profile Photo", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            avatarMenuExpanded = false
                            onRemoveAvatar()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Profile Photo Permanently", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            avatarMenuExpanded = false
                            onDeleteAvatar()
                        }
                    )
                }
        }

            // Blue Camera badge on profile picture
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 96.dp, bottom = 60.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(VynCameraBlue)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .clickable(onClick = onEditAvatar)
                    .testTag("change_avatar_badge_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Change Profile Picture",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

    }
}

@Composable
fun ProfileDetailsSection(
    profile: UserProfileEntity,
    role: com.example.data.model.UserRole = com.example.data.model.UserRole.USER,
    showIdentity: Boolean = true,
    onEditProfileClick: () -> Unit,
    onShareProfileClick: () -> Unit,
    onCreateClick: () -> Unit,
    onFriendsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (showIdentity) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = profile.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("profile_display_name")
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(role.badgeColorHex).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(role.badgeColorHex).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(role.iconEmoji, fontSize = 11.sp)
                        Text(
                            role.displayName.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(role.badgeColorHex)
                        )
                    }
                }
            }

            Text(
                text = "@${profile.handle}",
                fontSize = 14.sp,
                color = VynTextSecondary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .testTag("profile_handle_text")
            )
        }

        // Bio
        if (profile.bio.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("profile_bio_text")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Notes,
                    contentDescription = "Bio",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 2.dp)
                )
                Text(
                    text = profile.bio,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Location
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationCity,
                contentDescription = "Location",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = profile.location,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("profile_location_text")
            )
        }

        // Stats Row: Posts, Friends 🤝, Followers, Following
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            ProfileStatItem(count = profile.postsCount.toString(), label = "Posts", modifier = Modifier.weight(1f))
            ProfileStatItem(
                count = profile.friendsCount.toString(),
                label = "Friends 🤝",
                onClick = onFriendsClick,
                modifier = Modifier.weight(1f)
            )
            ProfileStatItem(count = profile.followersCount.toString(), label = "Followers", modifier = Modifier.weight(1f))
            ProfileStatItem(count = profile.followingCount.toString(), label = "Following", modifier = Modifier.weight(1f))
        }

        // Action Buttons: Edit Profile, Share Profile, and Create Content (+)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onEditProfileClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VynButtonBg,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("edit_profile_button"),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "Edit Profile",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onShareProfileClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VynButtonBg,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("share_profile_button"),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "Share Profile",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Create (+) Button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onCreateClick)
                    .testTag("profile_create_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Post or Story",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileStatItem(
    count: String,
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = count,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else VynTextSecondary,
            fontWeight = if (onClick != null) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun ProfileSubTabRow(
    selectedTab: ProfileSubTab,
    onTabSelect: (ProfileSubTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        ProfileTabButton(
            icon = Icons.Outlined.GridOn,
            isSelected = selectedTab == ProfileSubTab.GRID,
            onClick = { onTabSelect(ProfileSubTab.GRID) },
            testTag = "profile_tab_grid"
        )
        ProfileTabButton(
            icon = Icons.Outlined.VideoLibrary,
            isSelected = selectedTab == ProfileSubTab.REELS,
            onClick = { onTabSelect(ProfileSubTab.REELS) },
            testTag = "profile_tab_reels"
        )
        ProfileTabButton(
            icon = Icons.Default.PeopleAlt,
            isSelected = selectedTab == ProfileSubTab.FRIENDS,
            onClick = { onTabSelect(ProfileSubTab.FRIENDS) },
            testTag = "profile_tab_friends"
        )
        ProfileTabButton(
            icon = Icons.Default.Repeat,
            isSelected = selectedTab == ProfileSubTab.REPOSTS,
            onClick = { onTabSelect(ProfileSubTab.REPOSTS) },
            testTag = "profile_tab_reposts"
        )
        ProfileTabButton(
            icon = Icons.Outlined.BookmarkBorder,
            isSelected = selectedTab == ProfileSubTab.SAVED,
            onClick = { onTabSelect(ProfileSubTab.SAVED) },
            testTag = "profile_tab_saved"
        )
    }
}

@Composable
fun ProfileTabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 24.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.onBackground else VynTextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(2.dp)
                .background(if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent)
        )
    }
}

@Composable
fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VynTextSecondary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = VynTextSecondary
        )
        if (actionButtonText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = actionButtonText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
