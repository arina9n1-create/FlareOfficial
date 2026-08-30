package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.PostEntity
import com.example.ui.components.VynAvatar
import com.example.ui.components.VynImage
import com.example.ui.theme.VynButtonBg
import com.example.ui.theme.VynTextSecondary
import com.example.ui.viewmodel.SocialViewModel

@Composable
fun ExploreScreen(
    viewModel: SocialViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.userSearchResults.collectAsState()
    val searchLoading by viewModel.userSearchLoading.collectAsState()
    val searchError by viewModel.userSearchError.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val connections by viewModel.allConnections.collectAsState()
    var selectedPhoto by remember { mutableStateOf<PostEntity?>(null) }

    // Real trending hashtags derived from the local feed (posts), most frequent first.
    val trendingTags = remember(posts) {
        val counted = mutableMapOf<String, Int>()
        for (post in posts) {
            val normalized = "#" + post.caption
            Regex("#\\p{L}[\\p{L}\\p{N}_]*").findAll(normalized).forEach { m ->
                val tag = m.value.lowercase()
                counted[tag] = (counted[tag] ?: 0) + 1
            }
        }
        counted.entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }
            .ifEmpty { listOf("#vyn9", "#friends", "#newpost") }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("explore_screen_list"),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.setUserSearchQuery(it)
                },
                placeholder = { Text("Search friends, places, hashtags...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("explore_search_field"),
                singleLine = true
            )
        }

        // Trending Tags
        item {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trendingTags) { tag ->
                    SuggestionChip(
                        onClick = {
                            searchQuery = tag
                            viewModel.setUserSearchQuery(tag)
                        },
                        label = { Text(tag, fontWeight = FontWeight.Medium) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = VynButtonBg
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // Suggested people to follow
        item {
            val suggestions = connections.filter { !it.isFollowing }

            if (suggestions.isNotEmpty()) {
                Text(
                    text = "Suggested For You ✦ Follow Back to become Friends 🤝",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                suggestions.forEach { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openFriendProfile(friend) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box {
                                VynAvatar(avatarType = friend.avatarType, storagePath = friend.avatarPath, size = 46.dp)
                                if (friend.isOnline) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2ECC71))
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = friend.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = if (friend.isFollower) "Follows you · ${friend.mutualFriendsCount} mutual friends" else "@${friend.handle} · ${friend.bio}",
                                    fontSize = 12.sp,
                                    color = if (friend.isFollower) Color(0xFFE67E22) else VynTextSecondary,
                                    maxLines = 1
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.toggleFollow(friend.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = if (friend.isFollower) "Follow Back 🤝" else "Follow",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (searchQuery.isNotBlank()) {
            val normalizedQuery = searchQuery.removePrefix("#").trim()
            val matchingPosts = posts.filter { post ->
                post.caption.contains(normalizedQuery, ignoreCase = true) ||
                        post.actionText.contains(normalizedQuery, ignoreCase = true) ||
                        post.username.contains(normalizedQuery, ignoreCase = true) ||
                        post.userHandle.contains(normalizedQuery, ignoreCase = true)
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "People",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (searchLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            searchError?.let { msg ->
                item {
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            if (searchResults.isEmpty()) {
                item {
                    Text(
                        text = "No people found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                    )
                }
            } else {
                items(searchResults, key = { it.uid }) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openDirectChatWithUser(user) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VynAvatar(avatarType = user.avatarType, storagePath = user.avatarPath, size = 46.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("@${user.handle}", fontSize = 12.sp, color = VynTextSecondary)
                            if (user.bio.isNotBlank()) {
                                Text(user.bio, fontSize = 12.sp, color = VynTextSecondary, maxLines = 1)
                            }
                        }
                        TextButton(onClick = { viewModel.openDirectChatWithUser(user) }) {
                            Text("Chat")
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Posts",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            if (matchingPosts.isEmpty()) {
                item {
                    Text(
                        text = "No matching posts",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                    )
                }
            } else {
                items(matchingPosts, key = { it.remoteId.ifBlank { "post_${it.id}" } }) { post ->
                    val hasThumb = post.postImageRes.isNotBlank() &&
                            post.postImageRes != "default" &&
                            !post.postImageRes.startsWith("img_")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = hasThumb) { selectedPhoto = post }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (hasThumb) {
                            VynImage(
                                imageResName = post.postImageRes,
                                storagePath = post.storagePath,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        Column {
                            Text(post.username, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("@${post.userHandle}", fontSize = 12.sp, color = VynTextSecondary)
                            if (post.caption.isNotBlank()) {
                                Text(post.caption, fontSize = 13.sp, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }

        // Explore Photos Grid
        item {
            Text(
                text = "Explore Trends",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        val explorePhotos = posts.filter {
            it.postImageRes.isNotBlank() &&
                    it.postImageRes != "default" &&
                    !it.postImageRes.startsWith("img_")
        }
        if (explorePhotos.isEmpty()) {
            item {
                Text(
                    text = "No public photos yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                )
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    explorePhotos.take(30).chunked(2).forEach { rowPosts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPosts.forEach { post ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedPhoto = post },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    VynImage(
                                        imageResName = post.postImageRes,
                                        storagePath = post.storagePath,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                    )
                                }
                            }
                            if (rowPosts.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // Full-screen photo viewer (tap anywhere to close)
    selectedPhoto?.let { photo ->
        Dialog(onDismissRequest = { selectedPhoto = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { selectedPhoto = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    VynImage(
                        imageResName = photo.postImageRes,
                        storagePath = photo.storagePath,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = photo.username,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (photo.caption.isNotBlank()) {
                        Text(
                            text = photo.caption,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestedUserRow(
    name: String,
    handle: String,
    avatar: String,
    avatarPath: String? = null,
    bio: String,
    modifier: Modifier = Modifier
) {
    var isFollowing by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VynAvatar(avatarType = avatar, storagePath = avatarPath, size = 44.dp)
            Column {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "@$handle · $bio",
                    fontSize = 12.sp,
                    color = VynTextSecondary
                )
            }
        }

        Button(
            onClick = { isFollowing = !isFollowing },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowing) VynButtonBg else MaterialTheme.colorScheme.primary,
                contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                text = if (isFollowing) "Following" else "Follow",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
