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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PostEntity
import com.example.data.model.StoryEntity
import com.example.ui.components.VynAvatar
import com.example.ui.components.VynImage
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_feed_list"),
        contentPadding = PaddingValues(top = 70.dp, bottom = 90.dp)
    ) {
        // 1. Stories Tray
        item {
            StoriesTray(
                stories = stories,
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
                            color = VynTextSecondary,
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
                    onProfileClick = { viewModel.viewUserProfile(post.userHandle) }
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
    onAddStory: () -> Unit,
    onStoryClick: (StoryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Your Story" item with plus icon
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .width(72.dp)
                    .clickable(onClick = onAddStory)
                    .testTag("add_story_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(VynButtonBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Story",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = "Your Story",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        // Other stories
        items(stories, key = { it.id }) { story ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .width(72.dp)
                    .clickable { onStoryClick(story) }
                    .testTag("story_item_${story.id}")
            ) {
                VynAvatar(
                    avatarType = story.userAvatarType,
                    size = 68.dp,
                    hasStoryRing = true
                )
                Text(
                    text = story.username,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
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
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val displayTime = remember(post.timestamp) { 
        com.example.util.TimeUtils.getRelativeTime(post.timestamp) 
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
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
                VynAvatar(
                    avatarType = post.userAvatarType,
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
                            color = VynTextSecondary
                        )
                        if (isBoosted) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = InstagramPink.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Sponsored ⚡",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = InstagramPink,
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
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = InstagramPink, modifier = Modifier.size(18.dp))
                                Text("Boost Post ⚡", fontWeight = FontWeight.Bold, color = InstagramPink)
                            }
                        },
                        onClick = {
                            onBoostClick()
                            isMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Save Post") },
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

        // Main Image Post
        VynImage(
            imageResName = post.postImageRes,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentScale = ContentScale.Crop
        )

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
                        tint = if (post.isLiked) VynHeartRed else MaterialTheme.colorScheme.onSurface,
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
                        tint = if (post.isReposted) VynCameraBlue else MaterialTheme.colorScheme.onSurface,
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
                    color = VynTextSecondary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { onCommentClick() }
                )
            }

            if (post.repostsCount > 0) {
                Text(
                    text = "${post.repostsCount} reposts",
                    fontSize = 13.sp,
                    color = VynTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (post.caption.isNotBlank()) {
                Text(
                    text = post.caption,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = displayTime,
                fontSize = 12.sp,
                color = VynTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
