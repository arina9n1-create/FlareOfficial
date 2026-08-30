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
import com.example.ui.theme.VynButtonBg
import com.example.ui.theme.VynTextSecondary
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
                    tint = VynTextSecondary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = VynTextSecondary)
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
                        containerColor = VynButtonBg,
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
                        tint = VynTextSecondary,
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
                        color = VynTextSecondary
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
                    color = VynTextSecondary
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
                    color = VynTextSecondary
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
                    VynAvatar(avatarType = friend.avatarType, storagePath = friend.avatarPath, size = 52.dp)
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
                        color = VynTextSecondary,
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
                            color = VynTextSecondary
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
                            .background(VynButtonBg)
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
                        .background(VynButtonBg)
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
                VynAvatar(avatarType = friend.avatarType, size = 56.dp)
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
                        color = VynTextSecondary
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
                subtitle = "Transfer Vyn coins & gifts to friend",
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
                    color = VynTextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendProfilePreviewBottomSheet(
    friend: FriendEntity,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
    onWave: () -> Unit,
    onSendGift: () -> Unit,
    onFollowToggle: () -> Unit,
    onOptionsClick: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .navigationBarsPadding()
        ) {
            // Cover Photo & Avatar Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                VynImage(
                    imageResName = friend.coverImageRes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentScale = ContentScale.Crop
                )

                // Back / Close
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
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

                // Avatar Positioned Over Cover
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        VynAvatar(avatarType = friend.avatarType, size = 76.dp)
                    }
                    if (friend.isOnline) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Color(0xFF2ECC71))
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }
            }

            // User Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = friend.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (friend.isFriend) {
                                Text(text = "🤝", fontSize = 18.sp)
                            }
                            if (friend.isCloseFriend) {
                                Text(text = "⭐", fontSize = 16.sp)
                            }
                        }
                        Text(
                            text = "@${friend.handle} · ${friend.location}",
                            fontSize = 13.sp,
                            color = VynTextSecondary
                        )
                    }

                    IconButton(
                        onClick = onOptionsClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(VynButtonBg)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Bio
                if (friend.bio.isNotBlank()) {
                    Text(
                        text = friend.bio,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }

                // Friendship Meta
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "${friend.mutualFriendsCount} Mutual Friends · ${friend.friendshipDate}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = VynTextSecondary
                    )
                }

                // Facebook-Style Action Buttons Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
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
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Message", fontWeight = FontWeight.Bold)
                    }

                    // Secondary Action: Wave / Poke
                    FilledTonalButton(
                        onClick = {
                            onWave()
                            Toast.makeText(context, "You waved to ${friend.name}! 👋", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Wave 👋", fontWeight = FontWeight.Bold)
                    }

                    // Gift Action
                    FilledTonalButton(
                        onClick = onSendGift,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF2ECC71).copy(alpha = 0.15f),
                            contentColor = Color(0xFF27AE60)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Gift 🎁", fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "No photos yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }
        }
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
                    text = "Transfer Vyn Reward Credits as a gift to @${friend.handle}. They can redeem it for real payouts and bonuses!",
                    fontSize = 13.sp,
                    color = VynTextSecondary
                )

                Text(
                    text = "Choose Amount (Vyn Credits):",
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
                            color = if (isSelected) MaterialTheme.colorScheme.primary else VynButtonBg,
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
