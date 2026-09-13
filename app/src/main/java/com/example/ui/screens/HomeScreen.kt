package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PostEntity
import com.example.data.model.StoryEntity
import com.example.ui.components.AspectFitMediaImage
import com.example.ui.components.FlareAvatar
import com.example.ui.components.FlareImage
import com.example.ui.theme.*
import com.example.ui.viewmodel.SocialViewModel

@Composable
fun HomeScreen(
    viewModel: SocialViewModel,
    modifier: Modifier = Modifier
) {
    val posts by viewModel.posts.collectAsState()
    val stories by viewModel.stories.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val boostCampaigns by viewModel.boostCampaigns.collectAsState()
    val postToBoost by viewModel.showPostBoostDialogForPost.collectAsState()
    val wallet by viewModel.earningsWallet.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Content flows edge-to-edge UNDER the translucent bottom bar; the bottom
    // padding adapts to the system navigation mode (gesture vs 3-button) so the
    // last item always clears the bar on every screen size.
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Top inset auto-detects the status bar height on every device (gesture or
    // 3-button navigation), so the feed starts right under the floating top bar.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Jump to specific post if requested
    val scrollToPostId by viewModel.scrollToPostId.collectAsState()
    LaunchedEffect(scrollToPostId, posts.size) {
        val targetId = scrollToPostId
        if (targetId != null && posts.isNotEmpty()) {
            val targetIdx = posts.indexOfFirst { it.id == targetId }
            if (targetIdx >= 0) {
                // Scroll with offset to account for the top bar
                scrollState.animateScrollToItem(targetIdx + 1) // +1 for the StoriesTray item
                viewModel.onPostScrollHandled()
            }
        }
    }

    LazyColumn(
        state = scrollState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_feed_list"),
        contentPadding = PaddingValues(top = topInset + 52.dp, bottom = bottomInset + 96.dp)
    ) {
        // 1. Stories Tray
        item {
            StoriesTray(
                stories = stories,
                ownAvatarType = profile.avatarType,
                ownAvatarPath = profile.avatarPath,
                onAddStory = { viewModel.openCreatePostSheet() },
                onStoryClick = { story -> viewModel.openStory(story) }
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )
        }

        // 2. Posts Feed
        if (posts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No posts in your feed yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Be the first to share an update or photo from your device!",
                            fontSize = 13.sp,
                            color = FlareTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.openCreatePostSheet() },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Create Post")
                            }
                        }
                    }
                }
            }
        } else {
            items(posts, key = { it.id }) { post ->
                LaunchedEffect(post.id) {
                    viewModel.recordContentAdImpression(
                        contentId = post.id.toString(),
                        creatorId = post.userHandle,
                        contentType = "POST",
                        placement = "CONTENT_FEED_NATIVE"
                    )
                }
                val isBoosted = boostCampaigns.any { it.postId == post.id && it.status == "ACTIVE" }
                PostCard(
                    post = post,
                    isBoosted = isBoosted,
                    onLikeClick = { viewModel.toggleLike(post) },
                    onCommentClick = { viewModel.openComments(post) },
                    onRepostClick = { viewModel.toggleRepost(post) },
                    onShareClick = { viewModel.sharePost(context, post) },
                    onSaveClick = { viewModel.toggleSave(post) },
                    onBoostClick = { viewModel.openPostBoost(post) },
                    onDeleteClick = { viewModel.deletePost(post) },
                    onProfileClick = { viewModel.viewUserProfile(post.userHandle) },
                    onMediaClick = {
                        if (post.isReelPost) {
                            viewModel.openReel(post.remoteId)
                        } else {
                            viewModel.openFullScreenPhotoPreview(
                                title = post.username,
                                imageUri = com.example.util.MediaStorageResolver.resolve(post.postImageRes, post.storagePath),
                                subtitle = post.caption
                            )
                        }
                    }
                )
            }
        }
    }

    // Post Boost Dialog
    postToBoost?.let { p ->
        com.example.ui.components.PostBoostDialog(
            post = p,
            walletBalanceUsd = wallet.availableBalance,
            onDismiss = { viewModel.closePostBoost() },
            onBoostConfirmed = { campaign, payWithWallet, gateway ->
                viewModel.createPostBoostCampaign(campaign, payWithWallet, gateway?.displayName) { success, msg ->
                    android.widget.Toast.makeText(viewModel.getApplication(), msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun StoriesTray(
    stories: List<StoryEntity>,
    ownAvatarType: String = "default",
    ownAvatarPath: String? = null,
    onAddStory: () -> Unit,
    onStoryClick: (StoryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Your Story" item redesigned as a stylish Card
        item {
            Card(
                modifier = Modifier
                    .width(100.dp)
                    .height(150.dp)
                    .clickable(onClick = onAddStory)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .testTag("add_story_button"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            FlareAvatar(
                                avatarType = ownAvatarType,
                                storagePath = ownAvatarPath,
                                size = 56.dp
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(FlareOfficialPink)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Add Story",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Other stories as Preview Cards (users can see a preview without clicking)
        items(stories, key = { it.id }) { story ->
            Card(
                modifier = Modifier
                    .width(100.dp)
                    .height(150.dp)
                    .clickable { onStoryClick(story) }
                    .border(1.5.dp, FlareOfficialPink.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .testTag("story_item_${story.id}"),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // PREVIEW: The actual content of the story shown as background
                    FlareImage(
                        imageResName = story.imageRes,
                        storagePath = story.storagePath,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Darkening gradient at bottom for text readability and premium look
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                    startY = 200f
                                )
                            )
                    )
                    
                    // User Avatar at top-left to identify the story owner
                    Box(modifier = Modifier.padding(8.dp)) {
                        FlareAvatar(
                            avatarType = story.userAvatarType,
                            storagePath = story.userAvatarPath,
                            size = 32.dp,
                            borderWidth = 2.dp,
                            borderColor = FlareOfficialPink
                        )
                    }
                    
                    // Username at bottom
                    Text(
                        text = story.username,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PostCard(
    post: PostEntity,
    isBoosted: Boolean = false,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onRepostClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBoostClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onMediaClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val displayTime = remember(post.timestamp) { 
        com.example.util.TimeUtils.getRelativeTime(post.timestamp) 
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .testTag("post_card_${post.id}")
    ) {
        // User Info Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable { onProfileClick() }
            ) {
                FlareAvatar(
                    avatarType = post.userAvatarType,
                    storagePath = post.userAvatarPath,
                    size = 40.dp,
                    borderWidth = 1.dp,
                    borderColor = MaterialTheme.colorScheme.outline
                )

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = post.username,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "@${post.userHandle}",
                            fontSize = 12.sp,
                            color = FlareTextSecondary
                        )
                        if (isBoosted) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = FlareOfficialPink.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Sponsored ⚡",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FlareOfficialPink,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (isBoosted) {
                        Text(
                            text = "Promoted campaign active",
                            fontSize = 10.sp,
                            color = Color(0xFF00B894)
                        )
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { isMenuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Post Options",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = FlareOfficialPink, modifier = Modifier.size(18.dp))
                                Text("Boost Post ⚡", fontWeight = FontWeight.Bold, color = FlareOfficialPink)
                            }
                        },
                        onClick = {
                            onBoostClick()
                            isMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(if (post.isSaved) "Remove from Saved" else "Save Post")
                            }
                        },
                        onClick = {
                            onSaveClick()
                            isMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Post") },
                        onClick = {
                            onEditClick()
                            isMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Post", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onDeleteClick()
                            isMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share Link") },
                        onClick = {
                            onShareClick()
                            isMenuExpanded = false
                        }
                    )
                }
            }
        }

        // Action text is supplied by the authenticated user's content.
        if (post.actionText.isNotBlank()) {
            Text(
                text = post.actionText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // --- CAPTION ABOVE MEDIA ---
        if (post.caption.isNotBlank()) {
            Text(
                text = post.caption,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Main Image Post — preserves the original aspect ratio (no cropping).
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onMediaClick() }
        ) {
            AspectFitMediaImage(
                imageResName = post.postImageRes,
                // Reel posts keep the VIDEO object key in storagePath; the display
                // media is the thumbnail (postImageRes). Do NOT pass the video path 
                // as storagePath here, or it will try to load the MP4 as an image.
                storagePath = if (post.isReelPost) null else post.storagePath,
                modifier = Modifier.fillMaxWidth(),
                maxHeight = 360.dp
            )
            
            if (post.isReelPost) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reel",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        // Actions Bar (Heart, Comment, Repost, Share, Bookmark)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Heart / Like
                val scale by animateFloatAsState(
                    targetValue = if (post.isLiked) 1.2f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "like_anim"
                )
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier
                        .size(36.dp)
                        .scale(scale)
                        .testTag("post_like_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (post.isLiked) FlareHeartRed else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Comment
                IconButton(
                    onClick = onCommentClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("post_comment_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Repost
                IconButton(
                    onClick = onRepostClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("post_repost_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repost",
                        tint = if (post.isReposted) FlareCameraBlue else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Share / Send
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("post_share_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Save / Bookmark
            IconButton(
                onClick = onSaveClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("post_bookmark_button_${post.id}")
            ) {
                Icon(
                    imageVector = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Bookmark",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Likes count & Timestamp
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${post.likesCount} likes",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (post.commentsCount > 0) {
                Text(
                    text = "View all ${post.commentsCount} comments",
                    fontSize = 14.sp,
                    color = FlareTextSecondary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { onCommentClick() }
                )
            }

            if (post.repostsCount > 0) {
                Text(
                    text = "${post.repostsCount} reposts",
                    fontSize = 13.sp,
                    color = FlareTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = displayTime,
                fontSize = 12.sp,
                color = FlareTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
