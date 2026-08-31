package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NotificationEntity
import com.example.ui.components.VynAvatar
import com.example.ui.theme.*
import com.example.ui.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    viewModel: SocialViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Handle system back button
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val notifications by viewModel.notifications.collectAsState()
    val followingHandles by viewModel.followingHandles.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }

    val filteredList = remember(notifications, selectedFilter, searchQuery) {
        notifications.filter { item ->
            val matchesFilter = when (selectedFilter) {
                "Unread" -> !item.isRead
                "Likes" -> item.actionText.contains("liked", ignoreCase = true) || item.actionText.contains("reposted", ignoreCase = true)
                "Comments" -> item.actionText.contains("comment", ignoreCase = true) || item.actionText.contains("replied", ignoreCase = true)
                "Rewards" -> item.actionText.contains("credit", ignoreCase = true) || item.actionText.contains("earned", ignoreCase = true) || item.actionText.contains("reward", ignoreCase = true) || item.actionText.contains("payout", ignoreCase = true) || item.actionText.contains("recharge", ignoreCase = true)
                "Follows" -> item.actionText.contains("following", ignoreCase = true) || item.actionText.contains("followed", ignoreCase = true)
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    item.username.contains(searchQuery, ignoreCase = true) ||
                    item.actionText.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    // Split into Today/Recent and Earlier
    val recentNotifications = remember(filteredList) {
        filteredList.filter { it.timeAgo.contains("m") || it.timeAgo.contains("h") || it.timeAgo.contains("now", ignoreCase = true) || it.timeAgo.contains("Today", ignoreCase = true) }
    }
    val earlierNotifications = remember(filteredList) {
        filteredList.filter { !recentNotifications.contains(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("notification_full_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Notifications",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                        if (unreadCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFF2A6D),
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                Text(
                                    text = "$unreadCount new",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("notification_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (notifications.isNotEmpty()) {
                        if (unreadCount > 0) {
                            IconButton(
                                onClick = {
                                    viewModel.markAllNotificationsRead()
                                    Toast.makeText(context, "All marked as read ✨", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("mark_all_read_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Mark all as read",
                                    tint = Color(0xFF6C5CE7)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("clear_all_notifications_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = VynTextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Pills Row
            val filterTabs = listOf("All", "Unread", "Likes", "Comments", "Rewards", "Follows")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterTabs.take(3).forEach { tab ->
                    val isSelected = selectedFilter == tab
                    val count = when (tab) {
                        "Unread" -> unreadCount
                        else -> 0
                    }
                    FilterPill(
                        text = tab,
                        isSelected = isSelected,
                        badgeCount = count,
                        onClick = { selectedFilter = tab },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterTabs.drop(3).forEach { tab ->
                    val isSelected = selectedFilter == tab
                    FilterPill(
                        text = when (tab) {
                            "Likes" -> "Likes ❤️"
                            "Comments" -> "Comments 💬"
                            "Rewards" -> "Rewards 💰"
                            "Follows" -> "Follows 👥"
                            else -> tab
                        },
                        isSelected = isSelected,
                        onClick = { selectedFilter = tab },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredList.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF6C5CE7).copy(alpha = 0.1f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = Color(0xFF6C5CE7),
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }

                        Text(
                            text = if (selectedFilter == "Unread") "All Caught Up! ✨" else "No Notifications Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = if (selectedFilter == "Unread")
                                "You have viewed all recent updates, likes, and rewards."
                            else
                                "When people like your posts, send messages, or you earn reward credits, they will appear here.",
                            fontSize = 13.sp,
                            color = VynTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )

                        if (selectedFilter != "All") {
                            OutlinedButton(
                                onClick = { selectedFilter = "All" },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Show All Notifications")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
                ) {
                    if (recentNotifications.isNotEmpty()) {
                        item {
                            Text(
                                text = "New & Today",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = VynTextSecondary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(recentNotifications, key = { it.id }) { notif ->
                            NotificationCardItem(
                                notification = notif,
                                isFollowingBack = followingHandles.contains(notif.username.lowercase().trimStart('@')),
                                onFollowBack = { viewModel.followBackFromNotification(notif.username) },
                                onItemClick = {
                                    if (!notif.isRead) viewModel.markNotificationRead(notif.id)
                                },
                                onDeleteClick = {
                                    viewModel.deleteNotification(notif.id)
                                    Toast.makeText(context, "Notification removed", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    if (earlierNotifications.isNotEmpty()) {
                        item {
                            Text(
                                text = "Earlier",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = VynTextSecondary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }
                        items(earlierNotifications, key = { it.id }) { notif ->
                            NotificationCardItem(
                                notification = notif,
                                isFollowingBack = followingHandles.contains(notif.username.lowercase().trimStart('@')),
                                onFollowBack = { viewModel.followBackFromNotification(notif.username) },
                                onItemClick = {
                                    if (!notif.isRead) viewModel.markNotificationRead(notif.id)
                                },
                                onDeleteClick = {
                                    viewModel.deleteNotification(notif.id)
                                    Toast.makeText(context, "Notification removed", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear all notifications?") },
            text = { Text("This will remove all notification history from your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllNotifications()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "Notifications cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FilterPill(
    text: String,
    isSelected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF6C5CE7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (!isSelected) BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null,
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (badgeCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) Color.White else Color(0xFFFF2A6D),
                    modifier = Modifier.size(16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$badgeCount",
                            color = if (isSelected) Color(0xFF6C5CE7) else Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCardItem(
    notification: NotificationEntity,
    isFollowingBack: Boolean = true,
    onFollowBack: () -> Unit = {},
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isLike = notification.actionText.contains("liked", ignoreCase = true) || notification.actionText.contains("reposted", ignoreCase = true)
    val isCredit = notification.actionText.contains("credit", ignoreCase = true) || notification.actionText.contains("earned", ignoreCase = true) || notification.actionText.contains("reward", ignoreCase = true) || notification.actionText.contains("payout", ignoreCase = true)
    val isComment = notification.actionText.contains("comment", ignoreCase = true) || notification.actionText.contains("replied", ignoreCase = true)
    val isFollow = notification.actionText.contains("following", ignoreCase = true) || notification.actionText.contains("followed", ignoreCase = true)
    // "X started following you — Follow back! 🤝" → show Follow Back button.
    // Self-log entries ("followed you back…", "in your following list") are excluded.
    val isFollowBackCandidate = isFollow &&
            notification.actionText.contains("you", ignoreCase = true) &&
            !notification.actionText.contains("back", ignoreCase = true) &&
            !notification.actionText.contains("your", ignoreCase = true)

    val badgeIcon = when {
        isLike -> Icons.Default.Favorite
        isCredit -> Icons.Default.MonetizationOn
        isComment -> Icons.Default.ChatBubble
        isFollow -> Icons.Default.PersonAdd
        else -> Icons.Default.Notifications
    }

    val badgeBg = when {
        isLike -> Color(0xFFFF2A6D)
        isCredit -> Color(0xFF27AE60)
        isComment -> Color(0xFF3498DB)
        isFollow -> Color(0xFF6C5CE7)
        else -> Color(0xFFFF9F43)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (!notification.isRead) {
            Color(0xFF6C5CE7).copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = if (!notification.isRead) {
            BorderStroke(1.dp, Color(0xFF6C5CE7).copy(alpha = 0.25f))
        } else {
            BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .testTag("notification_item_${notification.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar with Status Badge
            Box(modifier = Modifier.size(46.dp)) {
                VynAvatar(
                    avatarType = notification.avatarType,
                    size = 44.dp
                )

                Surface(
                    shape = CircleShape,
                    color = badgeBg,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            // Notification Content Body
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (notification.username.startsWith("@")) notification.username else "@${notification.username}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = notification.timeAgo,
                        fontSize = 11.sp,
                        color = VynTextSecondary
                    )
                }

                Text(
                    text = notification.actionText,
                    fontSize = 12.5.sp,
                    color = if (!notification.isRead) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontWeight = if (!notification.isRead) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Special reward tag if applicable
                if (isCredit) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF27AE60).copy(alpha = 0.12f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "💰 Reward Credited to Balance",
                            color = Color(0xFF27AE60),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Follow Back action — one tap turns the follower into a Friend 🤝
                if (isFollowBackCandidate) {
                    Button(
                        onClick = onFollowBack,
                        enabled = !isFollowingBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6C5CE7),
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = VynTextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (isFollowingBack) "Friends 🤝" else "Follow Back 🤝",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Action / Delete icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!notification.isRead) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(Color(0xFFFF2A6D), CircleShape)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Dismiss",
                        tint = VynTextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
