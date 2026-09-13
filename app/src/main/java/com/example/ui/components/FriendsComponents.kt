package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FriendEntity
import com.example.data.model.PostEntity
import com.example.data.model.ReelEntity
import com.example.data.model.UserProfileEntity
import com.example.ui.screens.ProfileDetailsSection
import com.example.ui.screens.EmptyStateView
import com.example.ui.screens.ProfileHeaderSection
import com.example.ui.screens.ProfileSubTabRow
import com.example.ui.theme.FlareButtonBg
import com.example.ui.theme.FlareTextSecondary
import com.example.ui.viewmodel.ProfileSubTab
import com.example.ui.viewmodel.SocialViewModel

@Composable
fun FriendsSectionView(
    viewModel: SocialViewModel,
    modifier: Modifier = Modifier
) {
    val connections by viewModel.allConnections.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "MUTUAL", "CLOSE", "ONLINE", "SUGGESTIONS"
    var searchQuery by remember { mutableStateOf("") }
    var unfriendConfirmTarget by remember { mutableStateOf<FriendEntity?>(null) }
    var blockConfirmTarget by remember { mutableStateOf<FriendEntity?>(null) }

    val filteredList = remember(connections, selectedFilter, searchQuery) {
        connections.filter { friend ->
            val matchesSearch = friend.name.contains(searchQuery, ignoreCase = true) ||
                    friend.handle.contains(searchQuery, ignoreCase = true) ||
                    friend.location.contains(searchQuery, ignoreCase = true)

            if (!matchesSearch) return@filter false

            when (selectedFilter) {
                "MUTUAL" -> friend.isFriend
                "CLOSE" -> friend.isCloseFriend
                "ONLINE" -> friend.isOnline
                "SUGGESTIONS" -> !friend.isFollowing
                else -> true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("friends_section_view")
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search friends by name, handle, city...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Friends",
                    tint = FlareTextSecondary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = FlareTextSecondary)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("friends_search_input"),
            singleLine = true
        )

        // Filter Pills
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "ALL" to "👥 All Friends (${friends.size})",
                "MUTUAL" to "🤝 Mutual (${friends.count { it.isFriend }})",
                "ONLINE" to "🟢 Active Now (${connections.count { it.isOnline }})",
                "CLOSE" to "⭐ Close Friends (${connections.count { it.isCloseFriend }})",
                "SUGGESTIONS" to "✨ Follow Back (${connections.count { !it.isFollowing && it.isFollower }})"
            )

            items(filters) { (key, label) ->
                val isSelected = selectedFilter == key
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = key },
                    label = {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = FlareButtonBg,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("filter_chip_$key")
                )
            }
        }

        // Friends List / Empty State
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PeopleOutline,
                        contentDescription = null,
                        tint = FlareTextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No connections found",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Try adjusting your search or follow more people!",
                        fontSize = 13.sp,
                        color = FlareTextSecondary
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                filteredList.forEach { friend ->
                    FriendCardItem(
                        friend = friend,
                        onCardClick = { viewModel.openFriendProfile(friend) },
                        onMessageClick = { viewModel.openDirectChatWithFriend(friend) },
                        onFollowToggle = { viewModel.toggleFollow(friend.id) },
                        onOptionsClick = { viewModel.openFriendOptions(friend) }
                    )
                }
            }
        }
    }

    // Unfriend Confirmation Dialog
    unfriendConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unfriendConfirmTarget = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.PersonRemove,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Remove ${target.name} as friend?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove @${target.handle} from your friends list? You won't see their private posts and stories.",
                    fontSize = 14.sp,
                    color = FlareTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unfriend(target.id)
                        unfriendConfirmTarget = null
                        Toast.makeText(context, "Removed @${target.handle} from friends", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Unfriend", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { unfriendConfirmTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Block Confirmation Dialog
    blockConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { blockConfirmTarget = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Block ${target.name}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "@${target.handle} will no longer be able to message you, view your profile, or see your posts.",
                    fontSize = 14.sp,
                    color = FlareTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.blockUser(target.id)
                        blockConfirmTarget = null
                        Toast.makeText(context, "Blocked @${target.handle}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Block", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockConfirmTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FriendCardItem(
    friend: FriendEntity,
    onCardClick: () -> Unit,
    onMessageClick: () -> Unit,
    onFollowToggle: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("friend_card_${friend.handle}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Avatar + Online status + Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box {
                    FlareAvatar(avatarType = friend.avatarType, storagePath = friend.avatarPath, size = 52.dp)
                    // Online Badge Dot
                    if (friend.isOnline) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Color(0xFF2ECC71))
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = friend.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (friend.isCloseFriend) {
                            Text(text = "⭐", fontSize = 12.sp)
                        }
                    }

                    Text(
                        text = "@${friend.handle} · ${friend.location}",
                        fontSize = 12.sp,
                        color = FlareTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Mutual Friends / Friendship Status Tag
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (friend.isFriend) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1877F2).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Friends 🤝",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1877F2),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (friend.isFollower && !friend.isFollowing) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE67E22).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Follows you ✦",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE67E22),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "${friend.mutualFriendsCount} mutual friends",
                            fontSize = 11.sp,
                            color = FlareTextSecondary
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (friend.isFriend) {
                    // Quick Message Button (FB Style)
                    IconButton(
                        onClick = onMessageClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FlareButtonBg)
                            .testTag("friend_msg_btn_${friend.handle}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Message",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Follow Back / Follow Button
                    Button(
                        onClick = onFollowToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = if (friend.isFollower) "Follow Back 🤝" else "Follow",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 3-Dots Options Menu (Facebook Style)
                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(FlareButtonBg)
                        .testTag("friend_options_btn_${friend.handle}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookFriendActionBottomSheet(
    friend: FriendEntity,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
    onViewProfile: () -> Unit,
    onWave: () -> Unit,
    onSendGift: () -> Unit,
    onToggleCloseFriend: () -> Unit,
    onToggleMute: () -> Unit,
    onUnfriend: () -> Unit,
    onBlock: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Friend Header in Sheet
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                FlareAvatar(avatarType = friend.avatarType, size = 56.dp)
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = friend.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (friend.isFriend) {
                            Text(text = "🤝", fontSize = 16.sp)
                        }
                    }
                    Text(
                        text = "@${friend.handle} · ${friend.friendshipDate}",
                        fontSize = 13.sp,
                        color = FlareTextSecondary
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Facebook Style Action Items
            FriendOptionItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "Message @${friend.handle}",
                subtitle = "Send direct message in live chat",
                iconTint = Color(0xFF1877F2),
                onClick = onMessage
            )

            FriendOptionItem(
                icon = Icons.Default.Person,
                title = "View Profile & Timeline",
                subtitle = "See posts, stories, photos & mutuals",
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onViewProfile
            )

            FriendOptionItem(
                icon = Icons.Default.WavingHand,
                title = "Send a Wave 👋 (Poke)",
                subtitle = "Send an instant wave to say hello",
                iconTint = Color(0xFFFFA000),
                onClick = onWave
            )

            FriendOptionItem(
                icon = Icons.Default.MonetizationOn,
                title = "Send Gift / Reward Credits 🎁",
                subtitle = "Transfer Flare coins & gifts to friend",
                iconTint = Color(0xFF2ECC71),
                onClick = onSendGift
            )

            FriendOptionItem(
                icon = if (friend.isCloseFriend) Icons.Default.Star else Icons.Outlined.StarBorder,
                title = if (friend.isCloseFriend) "Remove from Close Friends" else "Add to Close Friends ⭐",
                subtitle = "Highlight them in stories and posts",
                iconTint = Color(0xFFFFB300),
                onClick = onToggleCloseFriend
            )

            FriendOptionItem(
                icon = if (friend.isMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                title = if (friend.isMuted) "Unmute @${friend.handle}" else "Mute Posts & Stories 🔕",
                subtitle = "Stop seeing their updates in feed without unfriending",
                iconTint = MaterialTheme.colorScheme.onSurface,
                onClick = onToggleMute
            )

            FriendOptionItem(
                icon = Icons.Default.Share,
                title = "Share Profile Link",
                subtitle = "Share friend's link with others",
                iconTint = MaterialTheme.colorScheme.onSurface,
                onClick = onShare
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (friend.isFriend || friend.isFollowing) {
                FriendOptionItem(
                    icon = Icons.Default.PersonRemove,
                    title = "Unfriend @${friend.handle}",
                    subtitle = "Remove from your friends list",
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = onUnfriend
                )
            }

            FriendOptionItem(
                icon = Icons.Default.Block,
                title = "Block @${friend.handle}",
                subtitle = "They won't be able to find your profile or message you",
                iconTint = MaterialTheme.colorScheme.error,
                onClick = onBlock
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun FriendOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = FlareTextSecondary
                )
            }
        }
    }
}

@Composable
private fun VisitedPostCard(post: PostEntity, context: android.content.Context) {
    var liked by remember(post.id) { mutableStateOf(post.isLiked) }
    var saved by remember(post.id) { mutableStateOf(post.isSaved) }
    val likeCount = (post.likesCount + if (liked && !post.isLiked) 1 else 0).coerceAtLeast(0)

    Column(Modifier.fillMaxWidth()) {
        // Author header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlareAvatar(
                avatarType = post.userAvatarType,
                storagePath = post.userAvatarPath,
                size = 40.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = post.username,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "@${post.userHandle} · ${post.timeAgo}",
                    fontSize = 12.sp,
                    color = FlareTextSecondary
                )
            }
            Icon(Icons.Default.MoreVert, contentDescription = null, tint = FlareTextSecondary, modifier = Modifier.size(20.dp))
        }

        // Caption — above the media
        if (post.caption.isNotBlank()) {
            Text(
                text = post.caption,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // Media — full width, aspect preserved (homepage style, no cropping)
        AspectFitMediaImage(
            imageResName = post.postImageRes,
            // Reel posts: storagePath holds the video key — render the thumbnail instead.
            storagePath = post.thumbnailPath ?: post.storagePath,
            modifier = Modifier.fillMaxWidth(),
            maxHeight = 520.dp
        )

        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { liked = !liked }, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (liked) Color(0xFFE0245E) else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = {}, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comment", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = {}, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Repeat, contentDescription = "Repost", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { saved = !saved }, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = { com.example.util.ShareUtils.sharePost(context, post.remoteId, post.caption) },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp)) {
            Text("$likeCount likes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("  ·  ${post.commentsCount} comments", fontSize = 13.sp, color = FlareTextSecondary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(top = 8.dp))
    }
}
@Composable
private fun VisitedReelCard(reel: ReelEntity, context: android.content.Context) {
    var liked by remember(reel.id) { mutableStateOf(false) }
    val likeCount = (reel.likesCount + if (liked) 1 else 0).coerceAtLeast(0)

    Column(Modifier.fillMaxWidth()) {
        // Author header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlareAvatar(
                avatarType = reel.avatarType,
                storagePath = reel.userAvatarPath,
                size = 40.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(reel.author, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Reel · @${reel.handle}", fontSize = 12.sp, color = FlareTextSecondary)
            }
            Icon(Icons.Default.MoreVert, contentDescription = null, tint = FlareTextSecondary, modifier = Modifier.size(20.dp))
        }

        // Caption — above the media
        if (reel.caption.isNotBlank()) {
            Text(
                text = reel.caption,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // Reel thumbnail (video)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            FlareImage(
                imageResName = reel.imageRes.ifBlank { "default" },
                storagePath = reel.thumbnailPath ?: reel.storagePath,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play Reel",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { liked = !liked }, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (liked) Color(0xFFE0245E) else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = {}, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comment", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(
                onClick = { com.example.util.ShareUtils.sharePost(context, reel.remoteId, reel.caption) },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Save", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp)) {
            Text("$likeCount likes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("  ·  ${reel.commentsCount} comments", fontSize = 13.sp, color = FlareTextSecondary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun FriendProfileFullScreen(
    friend: FriendEntity,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onWave: () -> Unit,
    onSendGift: () -> Unit,
    onOptionsClick: () -> Unit,
    onFollowToggle: () -> Unit = {},
    posts: List<PostEntity> = emptyList(),
    reels: List<ReelEntity> = emptyList()
) {
    val context = LocalContext.current
    var subTab by remember { mutableStateOf(ProfileSubTab.GRID) }
    var following by remember(friend.id) { mutableStateOf(friend.isFollowing) }

    // Render the visited profile EXACTLY like the owner's profile screen.
    val viewedProfile = UserProfileEntity(
        name = friend.name,
        handle = friend.handle,
        bio = friend.bio,
        location = friend.location,
        postsCount = maxOf(posts.size, friend.mutualFriendsCount),
        friendsCount = friend.mutualFriendsCount,
        followersCount = friend.mutualFriendsCount,
        followingCount = 0,
        avatarType = friend.avatarType,
        coverType = friend.coverImageRes,
        avatarPath = friend.avatarPath,
        coverPath = friend.coverPath
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .navigationBarsPadding()
        ) {
            // Cover Photo & Avatar Header — identical to the owner's profile header.
            Box {
                ProfileHeaderSection(
                    profile = viewedProfile,
                    onEditCover = {},
                    onEditAvatar = {},
                    onRemoveCover = {},
                    onRemoveAvatar = {},
                    onDeleteCover = {},
                    onDeleteAvatar = {},
                    isOwner = false
                )

                // Back / Close
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Options
                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 12.dp, end = 12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // User Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Same details & stats section as the owner's profile, with
                // Follow / Unfollow as the primary action.
                ProfileDetailsSection(
                    profile = viewedProfile.copy(postsCount = maxOf(viewedProfile.postsCount, posts.size)),
                    showIdentity = false,
                    // Total reactions across this user's posts and reels
                    likesCount = posts.sumOf { it.likesCount } + reels.sumOf { it.likesCount },
                    onEditProfileClick = {
                        following = !following
                        onFollowToggle()
                        Toast.makeText(
                            context,
                            if (following) "You followed @${friend.handle} ✅" else "You unfollowed @${friend.handle}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onShareProfileClick = {
                        com.example.util.ShareUtils.shareProfile(context, friend.handle)
                    },
                    onCreateClick = {},
                    editProfileLabel = if (following) "Following ✓" else "Follow",
                    showCreateButton = false
                )

                // Facebook-Style Action Buttons Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Primary Action: Message
                    Button(
                        onClick = onMessage,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Message", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Secondary Action: Wave / Poke
                    FilledTonalButton(
                        onClick = {
                            onWave()
                            Toast.makeText(context, "You waved to ${friend.name}! 👋", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Wave 👋", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Gift Action
                    FilledTonalButton(
                        onClick = onSendGift,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF2ECC71).copy(alpha = 0.15f),
                            contentColor = Color(0xFF27AE60)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Gift 🎁", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Sub-tab row — identical to the owner's profile screen
                ProfileSubTabRow(
                    selectedTab = subTab,
                    onTabSelect = { subTab = it }
                )

                when (subTab) {
                    ProfileSubTab.REELS -> {
                        val mediaReels = reels.filter { r ->
                            (r.imageRes.isNotBlank() && r.imageRes != "default") || !r.storagePath.isNullOrBlank() || !r.videoUrl.isNullOrBlank()
                        }
                        if (mediaReels.isEmpty()) {
                            EmptyStateView(
                                icon = Icons.Outlined.VideoLibrary,
                                title = "No Reels Yet",
                                subtitle = "@${friend.handle} hasn't shared any reels."
                            )
                        } else {
                            Column(Modifier.fillMaxWidth()) {
                                mediaReels.forEach { reel ->
                                    VisitedReelCard(reel = reel, context = context)
                                }
                            }
                        }
                    }
                    ProfileSubTab.FRIENDS -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "🤝 ${friend.mutualFriendsCount} mutual friends",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            EmptyStateView(
                                icon = Icons.Default.PeopleAlt,
                                title = "Friends",
                                subtitle = "Friends and followers of @${friend.handle} will appear here."
                            )
                        }
                    }
                    ProfileSubTab.REPOSTS -> {
                        EmptyStateView(
                            icon = Icons.Default.Repeat,
                            title = "No Reposts",
                            subtitle = "@${friend.handle} hasn't reposted anything yet."
                        )
                    }
                    ProfileSubTab.SAVED -> {
                        val mediaPosts = posts.filter { p ->
                            (p.postImageRes.isNotBlank() && p.postImageRes != "default") || !p.storagePath.isNullOrBlank()
                        }
                        if (mediaPosts.isEmpty()) {
                            EmptyStateView(
                                icon = Icons.Outlined.BookmarkBorder,
                                title = "No Saved Posts",
                                subtitle = "@${friend.handle} hasn't saved anything yet."
                            )
                        } else {
                            Column(Modifier.fillMaxWidth()) {
                                mediaPosts.forEach { post ->
                                    VisitedPostCard(post = post, context = context)
                                }
                            }
                        }
                    }
                    else -> {
                        val mediaPosts = posts.filter { p ->
                            (p.postImageRes.isNotBlank() && p.postImageRes != "default") || !p.storagePath.isNullOrBlank()
                        }
                        if (mediaPosts.isEmpty()) {
                            EmptyStateView(
                                icon = Icons.Outlined.GridOn,
                                title = "No Posts Yet",
                                subtitle = "@${friend.handle} hasn't shared any posts yet."
                            )
                        } else {
                            Column(Modifier.fillMaxWidth()) {
                                mediaPosts.forEach { post ->
                                    VisitedPostCard(post = post, context = context)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TabButtonWithCount(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$count",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onSurface else FlareTextSecondary
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else FlareTextSecondary
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(3.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(2.dp)
                )
        )
    }
}

@Composable
fun SendGiftRewardDialog(
    friend: FriendEntity,
    onDismiss: () -> Unit,
    onConfirmSend: (amount: Int) -> Unit
) {
    var selectedAmount by remember { mutableStateOf(50) }
    val amounts = listOf(25, 50, 100, 250, 500)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2ECC71).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🎁", fontSize = 28.sp)
            }
        },
        title = {
            Text(
                text = "Send Gift to ${friend.name}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Transfer Flare Reward Credits as a gift to @${friend.handle}. They can redeem it for real payouts and bonuses!",
                    fontSize = 13.sp,
                    color = FlareTextSecondary
                )

                Text(
                    text = "Choose Amount (Flare Credits):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(amounts) { amount ->
                        val isSelected = selectedAmount == amount
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else FlareButtonBg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedAmount = amount }
                        ) {
                            Text(
                                text = "💰 $amount",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmSend(selectedAmount) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71))
            ) {
                Text("Send 💰 $selectedAmount Credits", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
