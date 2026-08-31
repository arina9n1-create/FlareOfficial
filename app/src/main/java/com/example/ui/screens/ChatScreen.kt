package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessageEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: SocialViewModel,
    modifier: Modifier = Modifier
) {
    val isInChatThread by viewModel.isInChatThread.collectAsState()
    val activeCallState by viewModel.activeCallState.collectAsState()
    val incomingCall by viewModel.incomingCall.collectAsState()
    val showNoteCreatorDialog by viewModel.showNoteCreatorDialog.collectAsState()
    val showNewMessageDialog by viewModel.showNewMessageDialog.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("instagram_chat_screen_root")
    ) {
        Crossfade(
            targetState = isInChatThread,
            label = "instagram_dm_transition",
            animationSpec = tween(durationMillis = 250)
        ) { inThread ->
            if (inThread) {
                InstagramConversationScreen(viewModel = viewModel)
            } else {
                InstagramDirectInboxScreen(viewModel = viewModel)
            }
        }

        // Modern High-Definition Video & Audio Call Overlay (real WebRTC renderers)
        activeCallState?.let { call ->
            ModernInstagramCallOverlay(
                call = call,
                onEndCall = { viewModel.endCall() },
                onToggleMute = { viewModel.toggleCallMute() },
                onToggleCamera = { viewModel.toggleCallCamera() },
                onToggleSpeaker = { viewModel.toggleCallSpeaker() },
                onFlipCamera = { viewModel.flipCallCamera() },
                onToggleScreenShare = { viewModel.toggleScreenSharing() },
                localRenderer = viewModel.webRtcCallManager.localVideoRenderer,
                remoteRenderer = viewModel.webRtcCallManager.remoteVideoRenderer
            )
        }

        // Incoming Call Dialog
        incomingCall?.let { signal ->
            AlertDialog(
                onDismissRequest = { viewModel.rejectIncomingCall() },
                title = {
                    Text(
                        text = "Incoming ${if (signal.callType == "VIDEO") "Video" else "Audio"} Call",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VynAvatar(avatarType = signal.callerAvatar, size = 64.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = signal.callerName.ifBlank { "@${signal.callerHandle}" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${signal.callerHandle}",
                                            style = MaterialTheme.typography.bodyMedium,
                            color = VynTextSecondary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.acceptIncomingCall() }) {
                        Text("Accept", color = InstagramPink)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.rejectIncomingCall() }) {
                        Text("Decline", color = VynTextSecondary)
                    }
                }
            )
        }

        // Instagram Note Creator Sheet
        if (showNoteCreatorDialog) {
            InstagramNoteCreatorSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.toggleNoteCreator(false) }
            )
        }

        // Instagram New Message / Search Dialog
        if (showNewMessageDialog) {
            InstagramNewMessageSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.toggleNewMessageDialog(false) }
            )
        }
    }
}

/**
 * -------------------------------------------------------------
 * 1. INSTAGRAM DIRECT INBOX SCREEN
 * -------------------------------------------------------------
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramDirectInboxScreen(
    viewModel: SocialViewModel,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.profile.collectAsState()
    val availableRooms by viewModel.availableRooms.collectAsState()
    val notesList by viewModel.notesList.collectAsState()
    val searchQuery by viewModel.directSearchQuery.collectAsState()
    val selectedTab by viewModel.directInboxTab.collectAsState()

    var showVynWorld by remember { mutableStateOf(false) }
    var selectedActionRoom by remember { mutableStateOf<LiveChatRoom?>(null) }

    val filteredRooms = remember(availableRooms, searchQuery, selectedTab) {
        availableRooms.filter { room ->
            val matchesSearch = room.title.contains(searchQuery, ignoreCase = true) ||
                    room.subtitle.contains(searchQuery, ignoreCase = true)
            val matchesTab = when (selectedTab) {
                "CHANNELS" -> room.type == "GLOBAL" || room.type == "COMMUNITY"
                "REQUESTS" -> room.unreadCount > 0
                else -> true
            }
            matchesSearch && matchesTab
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("instagram_inbox_view")
    ) {
        // --- TOP BAR (Instagram Direct Style) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Username with dropdown arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.toggleNoteCreator(true) }
            ) {
                Text(
                                        text = profile.handle.ifBlank { "account" },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Switch Account",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(InstagramOrange, CircleShape)
                )
            }

            // Right Action Icons: Notes / Video Call / Edit Message
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        profile.handle.takeIf { it.isNotBlank() }?.let {
                            viewModel.startCall(profile.handle.ifBlank { profile.name }, it, isVideo = true)
                        }
                    },
                    modifier = Modifier.testTag("ig_video_call_inbox_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Videocam,
                        contentDescription = "Video Call",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleNewMessageDialog(true) },
                    modifier = Modifier.testTag("ig_new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "New Message",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // --- SEARCH BAR (Instagram Pill) ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = VynTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setDirectSearchQuery(it) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search messages, channels & notes...",
                                fontSize = 14.sp,
                                color = VynTextSecondary
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.setDirectSearchQuery("") },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = VynTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("instagram_inbox_list")
        ) {
            // --- 1. INSTAGRAM NOTES TRAY ---
            item {
                Column(modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(notesList, key = { it.id }) { note ->
                            InstagramNoteItem(
                                note = note,
                                onClick = {
                                    if (note.isMe) {
                                        viewModel.toggleNoteCreator(true)
                                    } else {
                                        viewModel.openDirectThread("dm_${note.handle}")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // --- 2. INBOX FILTER TABS ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "PRIMARY" to "Primary",
                        "VYN_WORLD" to "Vyn World",
                        "CHANNELS" to "Channels",
                        "REQUESTS" to "Requests (${availableRooms.sumOf { it.unreadCount }})"
                    ).forEach { (key, label) ->
                        val isSelected = selectedTab == key && key != "VYN_WORLD"
                        Column(
                            modifier = Modifier
                                .clickable {
                                    if (key == "VYN_WORLD") {
                                        showVynWorld = true
                                    } else {
                                        viewModel.setDirectInboxTab(key)
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onBackground else VynTextSecondary
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(2.dp)
                                        .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            // --- 3. PINNED BROADCAST / GLOBAL LOUNGE CARD ---
            item {
                InstagramBroadcastChannelCard(
                    onClick = { viewModel.openDirectThread("global_live") }
                )
            }

            // --- 4. CONVERSATION THREADS ---
            items(filteredRooms, key = { it.id }) { room ->
                InstagramConversationListItem(
                    room = room,
                    onClick = { viewModel.openDirectThread(room.id) },
                    onLongClick = { selectedActionRoom = room },
                    onCameraClick = { viewModel.openDirectThread(room.id) },
                    onProfileClick = {
                        viewModel.viewUserProfile(viewModel.directPartnerHandle(room.id))
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Context Action Bottom Sheet on Long-Press
    selectedActionRoom?.let { room ->
        ChatActionBottomSheet(
            room = room,
            onDismiss = { selectedActionRoom = null },
            viewModel = viewModel
        )
    }

    // VYN WORLD overlay — phone-number based communication system (separate from Primary)
    if (showVynWorld) {
        val vynWorldViewModel: com.example.ui.viewmodel.VynWorldViewModel =
            androidx.lifecycle.viewmodel.compose.viewModel()
        com.example.ui.screens.VynWorldScreen(
            viewModel = vynWorldViewModel,
            onDismiss = { showVynWorld = false }
        )
    }
}

/**
 * -------------------------------------------------------------
 * 2. INSTAGRAM NOTE ITEM (Bubble with avatar)
 * -------------------------------------------------------------
 */
@Composable
fun InstagramNoteItem(
    note: InstagramNote,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable { onClick() }
    ) {
        // Thought bubble on top with FIXED uniform height & size so avatar never gets pushed or resized!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(bottom = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = note.noteText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (note.musicTrack != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(top = 1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = InstagramPink,
                                modifier = Modifier.size(9.dp)
                            )
                            Text(
                                text = note.musicTrack,
                                fontSize = 8.sp,
                                color = InstagramPink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Circular Avatar with fixed size 60.dp
        val borderBrush = if (note.isMe) {
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
        } else {
            Brush.linearGradient(listOf(InstagramDeepPurple, InstagramPink, InstagramYellow))
        }

        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .border(2.dp, borderBrush, CircleShape)
                    .padding(2.5.dp),
                contentAlignment = Alignment.Center
            ) {
                VynAvatar(avatarType = note.avatarType, size = 55.dp)
            }

            if (note.isMe) {
                Box(
                    modifier = Modifier
                        .size(19.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Note",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (note.isMe) "Your note" else note.name.split(" ").firstOrNull() ?: note.handle,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * -------------------------------------------------------------
 * 3. BROADCAST CHANNEL CARD (Instagram Channels / Global)
 * -------------------------------------------------------------
 */
@Composable
fun InstagramBroadcastChannelCard(
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        Brush.linearGradient(listOf(InstagramDeepPurple, InstagramPink, InstagramYellow)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🌍 Global Live Broadcast",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = InstagramBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "Join the live community stream ⚡",
                    fontSize = 12.sp,
                    color = VynTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = InstagramBlue.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "LIVE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = InstagramBlue,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * -------------------------------------------------------------
 * 4. INSTAGRAM CONVERSATION ROW ITEM (With Long Press support & Indicators)
 * -------------------------------------------------------------
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InstagramConversationListItem(
    room: LiveChatRoom,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCameraClick: () -> Unit,
    onProfileClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("ig_thread_item_${room.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Avatar with Instagram Story ring or Live status
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.clickable { onProfileClick() }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .then(
                            if (room.hasStory) {
                                Modifier
                                    .border(
                                        2.dp,
                                        Brush.linearGradient(listOf(InstagramDeepPurple, InstagramPink, InstagramYellow)),
                                        CircleShape
                                    )
                                    .padding(2.5.dp)
                            } else Modifier
                        )
                ) {
                    VynAvatar(avatarType = room.avatarType, size = 52.dp)
                }

                // Green Active Now dot
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(Color(0xFF00E676), CircleShape)
                        .border(2.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (room.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = InstagramBlue,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Text(
                        text = room.title,
                        fontSize = 15.sp,
                        fontWeight = if (room.unreadCount > 0) FontWeight.Black else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (room.verified) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = InstagramBlue,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    if (room.isMuted) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = "Muted",
                            tint = VynTextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = room.subtitle,
                        fontSize = 13.sp,
                        fontWeight = if (room.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (room.unreadCount > 0) MaterialTheme.colorScheme.onBackground else VynTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "· ${room.lastMessageTime}",
                        fontSize = 12.sp,
                        color = VynTextSecondary
                    )
                }
            }
        }

        // Camera Quick Action Icon Button or Unread dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (room.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(InstagramBlue, CircleShape)
                )
            }

            IconButton(
                onClick = onCameraClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = "Snap Camera",
                    tint = VynTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * -------------------------------------------------------------
 * 4.1. CHAT ACTION BOTTOM SHEET (Context Menu for Chat list items)
 * -------------------------------------------------------------
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatActionBottomSheet(
    room: LiveChatRoom,
    onDismiss: () -> Unit,
    viewModel: SocialViewModel
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header with user details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                VynAvatar(avatarType = room.avatarType, size = 52.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (room.isMuted) "Notifications Muted 🔕" else room.subtitle,
                        fontSize = 13.sp,
                        color = VynTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 1. Pin / Unpin
            ChatActionRowItem(
                icon = if (room.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                title = if (room.isPinned) "Unpin chat" else "Pin to top",
                tint = if (room.isPinned) InstagramBlue else MaterialTheme.colorScheme.onSurface,
                onClick = {
                    viewModel.togglePinChat(room.id)
                    onDismiss()
                }
            )

            // 2. Mute / Unmute Notifications
            ChatActionRowItem(
                icon = if (room.isMuted) Icons.Default.NotificationsOff else Icons.Outlined.NotificationsNone,
                title = if (room.isMuted) "Unmute notifications" else "Mute notifications",
                tint = if (room.isMuted) Color(0xFFFFA000) else MaterialTheme.colorScheme.onSurface,
                onClick = {
                    viewModel.toggleMuteChat(room.id)
                    onDismiss()
                }
            )

            // 3. Mark as Unread / Read
            ChatActionRowItem(
                icon = if (room.unreadCount > 0) Icons.Outlined.Drafts else Icons.Outlined.MarkEmailUnread,
                title = if (room.unreadCount > 0) "Mark as read" else "Mark as unread",
                onClick = {
                    if (room.unreadCount > 0) viewModel.markChatRead(room.id) else viewModel.markChatUnread(room.id)
                    onDismiss()
                }
            )

            // 4. Archive / Unarchive
            ChatActionRowItem(
                icon = if (room.isArchived) Icons.Default.Unarchive else Icons.Outlined.Archive,
                title = if (room.isArchived) "Unarchive chat" else "Archive chat",
                onClick = {
                    viewModel.toggleArchiveChat(room.id)
                    onDismiss()
                }
            )

            // 5. Add to Favorites
            ChatActionRowItem(
                icon = if (room.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                title = if (room.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (room.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface,
                onClick = {
                    viewModel.toggleFavoriteChat(room.id)
                    onDismiss()
                }
            )

            // 6. View Profile
            ChatActionRowItem(
                icon = Icons.Outlined.Person,
                title = "View profile",
                onClick = {
                    viewModel.openChatPartner(room.title)
                    onDismiss()
                }
            )

            // 7. Clear History
            ChatActionRowItem(
                icon = Icons.Outlined.CleaningServices,
                title = "Clear chat history",
                onClick = {
                    viewModel.clearRoomMessagesById(room.id)
                    onDismiss()
                }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // 8. Delete Conversation
            ChatActionRowItem(
                icon = Icons.Outlined.Delete,
                title = "Delete conversation",
                tint = MaterialTheme.colorScheme.error,
                onClick = {
                    viewModel.deleteChatRoom(room.id)
                    onDismiss()
                }
            )

            // 9. Block / Report
            ChatActionRowItem(
                icon = Icons.Outlined.Block,
                title = "Block & Report",
                tint = MaterialTheme.colorScheme.error,
                onClick = {
                    viewModel.deleteChatRoom(room.id)
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ChatActionRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = tint
        )
    }
}

/**
 * -------------------------------------------------------------
 * 5. INSTAGRAM CONVERSATION SCREEN (1-on-1 / Group Thread)
 * -------------------------------------------------------------
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramConversationScreen(
    viewModel: SocialViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.chatMessages.collectAsState()
    val activeRoomId by viewModel.activeRoomId.collectAsState()
    val availableRooms by viewModel.availableRooms.collectAsState()
    val typingStatus by viewModel.typingStatus.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val showChatDetailsSheet by viewModel.showChatDetailsSheet.collectAsState()
    val roomTranslationSettings by viewModel.roomTranslationSettings.collectAsState()
    val chatTheme by viewModel.chatTheme.collectAsState()

    val currentRoom = availableRooms.find { it.id == activeRoomId } ?: availableRooms.firstOrNull()
    if (currentRoom == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No chat rooms available", color = VynTextSecondary)
        }
        return
    }
    val currentRoomTranslation = roomTranslationSettings[activeRoomId] ?: com.example.ui.viewmodel.ChatTranslationSettings(outgoingToEnglish = false, incomingToBangla = false)
    val isAnyTranslationOn = currentRoomTranslation.outgoingToEnglish || currentRoomTranslation.incomingToBangla

    var inputText by remember { mutableStateOf("") }
    var showMediaPicker by remember { mutableStateOf(false) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var showQuickEmojis by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(activeRoomId, messages.size) {
        viewModel.markActiveRoomAsSeen()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            // We use imePadding() to push the input bar up with the keyboard.
            // No navigationBarsPadding() here because it's already handled by the bottom composer's surface if needed.
            .imePadding()
            .testTag("instagram_conversation_screen")
    ) {
        // --- TOP BAR (Instagram DM Conversation Header) ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back Button + Avatar + Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { viewModel.closeDirectThread() },
                        modifier = Modifier.testTag("ig_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier.clickable { 
                            viewModel.viewUserProfile(viewModel.directPartnerHandle(currentRoom.id))
                        }
                    ) {
                        VynAvatar(avatarType = currentRoom.avatarType, size = 38.dp)
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF00E676), CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { 
                                viewModel.viewUserProfile(viewModel.directPartnerHandle(currentRoom.id))
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = currentRoom.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentRoom.verified) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = InstagramBlue,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Text(
                            text = "Active now",
                            fontSize = 11.sp,
                            color = VynTextSecondary
                        )
                    }
                }

                // Top Action Icons: Audio Call, Video Call, Details Info ("i" button)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            viewModel.startCall(
                                currentRoom.title,
                                currentRoom.avatarType,
                                isVideo = false,
                                partnerHandle = viewModel.directPartnerHandle(currentRoom.id).takeIf { it.isNotBlank() }
                            )
                        },
                        modifier = Modifier.testTag("ig_audio_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Call,
                            contentDescription = "Audio Call",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.startCall(
                                currentRoom.title,
                                currentRoom.avatarType,
                                isVideo = true,
                                partnerHandle = viewModel.directPartnerHandle(currentRoom.id).takeIf { it.isNotBlank() }
                            )
                        },
                        modifier = Modifier.testTag("ig_video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Videocam,
                            contentDescription = "Video Call",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleChatDetailsSheet(true) },
                        modifier = Modifier.testTag("ig_room_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Details & Settings",
                            tint = if (isAnyTranslationOn) InstagramPink else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

        // --- MESSAGES STREAM ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Instagram Top Profile Header Card
            item {
                InstagramProfileHeaderCard(
                    room = currentRoom,
                    onViewProfile = { viewModel.setTab(MainTab.PROFILE) }
                )
            }

            // Message Bubbles
            items(messages, key = { it.id }) { msg ->
                InstagramMessageBubble(
                    message = msg,
                    chatTheme = chatTheme,
                    onDoubleTapHeart = {
                        viewModel.sendChatMessage("❤️")
                    }
                )
            }
        }

        // --- TYPING STATUS ---
        AnimatedVisibility(
            visible = typingStatus != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = InstagramPurple
                )
                Text(
                    text = typingStatus ?: "",
                    fontSize = 12.sp,
                    color = InstagramPurple,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }

        // --- QUICK REACTION EMOJIS ---
        if (showQuickEmojis) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("❤️", "🔥", "😂", "👏", "🎉", "💯", "🚀", "💎").forEach { emoji ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.sendChatMessage(emoji) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = emoji, fontSize = 17.sp)
                        }
                    }
                }
            }
        }

        // --- PER-CHAT TRANSLATION ACTIVE STATUS BANNER ---
        AnimatedVisibility(
            visible = isAnyTranslationOn,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Translation Active for this Chat",
                            tint = InstagramPink,
                            modifier = Modifier.size(15.dp)
                        )
                        val bannerText = when {
                            currentRoomTranslation.outgoingToEnglish && currentRoomTranslation.incomingToBangla ->
                                "অনুবাদ চালু: বাংলা/Banglish ➔ English | English ➔ বাংলা"
                            currentRoomTranslation.outgoingToEnglish ->
                                "অনুবাদ চালু: পাঠানো মেসেজ ➔ English"
                            else ->
                                "অনুবাদ চালু: অন্যের মেসেজ ➔ বাংলা"
                        }
                        Text(
                            text = bannerText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "চ্যাট সেটিংস",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = InstagramBlue,
                        modifier = Modifier
                            .clickable { viewModel.toggleChatDetailsSheet(true) }
                            .padding(4.dp)
                    )
                }
            }
        }

        // --- INSTAGRAM BOTTOM COMPOSER BAR ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Blue Camera snap button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(InstagramBlue, CircleShape)
                        .clip(CircleShape)
                        .clickable { showMediaPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Camera",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Capsule Input Container
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                                .testTag("ig_message_input_field"),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            decorationBox = { innerTextField ->
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = if (isRecordingAudio) "Recording voice note..." else "Message...",
                                        fontSize = 14.sp,
                                        color = VynTextSecondary
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (inputText.isBlank()) {
                            IconButton(
                                onClick = {
                                    isRecordingAudio = !isRecordingAudio
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isRecordingAudio) Icons.Default.GraphicEq else Icons.Outlined.Mic,
                                    contentDescription = "Voice Note",
                                    tint = if (isRecordingAudio) InstagramPink else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Image gallery button
                            IconButton(
                                onClick = { showMediaPicker = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = "Gallery",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Heart Sticker Button
                            IconButton(
                                onClick = { viewModel.sendChatMessage("❤️") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Send Heart",
                                    tint = InstagramPink,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            // Animated vibrant Instagram Send button
                            Text(
                                text = "Send",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = InstagramBlue,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.sendChatMessage(inputText)
                                        inputText = ""
                                    }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                                    .testTag("ig_send_button")
                            )
                        }
                    }
                }
            }
        }
    }

    // Media Picker Dialog
    if (showMediaPicker) {
        AlertDialog(
            onDismissRequest = { showMediaPicker = false },
            title = { Text("Share to Direct Chat 📸") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose an image from your gallery to send to ${currentRoom.title}:")
                    val galleryLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let {
                            viewModel.sendChatMessage(
                                text = "Shared a photo 📸",
                                mediaUrl = it.toString(),
                                mediaType = "image"
                            )
                        }
                        showMediaPicker = false
                    }
                    Button(onClick = { galleryLauncher.launch("image/*") }) {
                        Text("Choose from Gallery")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMediaPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Instagram Chat Details & Settings Sheet ("i" button)
    if (showChatDetailsSheet) {
        InstagramChatDetailsSheet(
            room = currentRoom,
            viewModel = viewModel,
            onDismiss = { viewModel.toggleChatDetailsSheet(false) },
            onOpenProfile = {
                viewModel.toggleChatDetailsSheet(false)
                viewModel.setTab(MainTab.PROFILE)
            }
        )
    }
}

/**
 * -------------------------------------------------------------
 * 6. INSTAGRAM CHAT PROFILE HEADER CARD
 * -------------------------------------------------------------
 */
@Composable
fun InstagramProfileHeaderCard(
    room: LiveChatRoom,
    onViewProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .border(
                    2.5.dp,
                    Brush.linearGradient(listOf(InstagramDeepPurple, InstagramPink, InstagramYellow)),
                    CircleShape
                )
                .padding(3.dp)
        ) {
            VynAvatar(avatarType = room.avatarType, size = 80.dp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = room.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (room.verified) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = "Verified",
                    tint = InstagramBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Text(
            text = "${room.title.lowercase().replace(" ", "_")} · Instagram",
            fontSize = 13.sp,
            color = VynTextSecondary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "You follow each other",
            fontSize = 12.sp,
            color = VynTextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onViewProfile,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text("View Profile", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * -------------------------------------------------------------
 * 7. INSTAGRAM MESSAGE BUBBLE (Gradient, Pill Styling & Translation)
 * -------------------------------------------------------------
 */
@Composable
fun InstagramMessageBubble(
    message: ChatMessageEntity,
    chatTheme: String = "Classic Instagram",
    onDoubleTapHeart: () -> Unit
) {
    val isMe = message.isFromMe
    var showOriginal by remember { mutableStateOf(false) }

    val myBubbleGradient = when (chatTheme) {
        "Cyber Glow" -> listOf(Color(0xFF6C5CE7), Color(0xFF00CEC9))
        "Sunset Peach" -> listOf(Color(0xFFFF7675), Color(0xFFFAB1A0))
        "Emerald Mint" -> listOf(Color(0xFF00B894), Color(0xFF55EFC4))
        else -> listOf(InstagramPurple, InstagramPink)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            VynAvatar(avatarType = message.senderAvatar, storagePath = message.senderAvatarPath, size = 28.dp)
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 285.dp)
        ) {
            if (!isMe && message.roomId == "global_live") {
                Text(
                    text = message.senderName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VynTextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isMe) 18.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 18.dp
                    ),
                    color = if (isMe) Color.Transparent else InstagramDarkBubble.copy(alpha = 0.85f),
                    modifier = Modifier
                        .then(
                            if (isMe) Modifier.background(
                                Brush.linearGradient(myBubbleGradient),
                                RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = 18.dp,
                                    bottomEnd = 4.dp
                                )
                            ) else Modifier
                        )
                        .clickable { onDoubleTapHeart() }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                        // Image attachment if available
                        if (message.mediaType == "image" && !message.mediaUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .padding(bottom = 6.dp)
                            ) {
                                VynImage(
                                    imageResName = message.mediaUrl.orEmpty(),
                                    storagePath = message.storagePath,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        // Voice message player
                        if (message.mediaType == "audio") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Audio",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(6.dp, 16.dp, 10.dp, 20.dp, 8.dp, 14.dp, 6.dp).forEach { h ->
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(h)
                                                .background(Color.White.copy(alpha = 0.9f), CircleShape)
                                        )
                                    }
                                }
                                Text(
                                    text = String.format("%d:%02d", message.audioDurationSec / 60, message.audioDurationSec % 60),
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }

                        // Translation Badge Header inside Bubble
                        if (message.isTranslated) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .padding(bottom = 3.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showOriginal = !showOriginal }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = "Translated",
                                    tint = if (isMe) Color.White.copy(alpha = 0.85f) else InstagramPink,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (message.translationLang == "BN") "অনূদিত (বাংলা)" else "Auto-Translated (EN)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMe) Color.White.copy(alpha = 0.9f) else InstagramPink
                                )
                            }
                        }

                        // Main Message Text Content
                        if (message.messageText.isNotBlank()) {
                            Text(
                                text = message.messageText,
                                fontSize = 14.sp,
                                color = Color.White,
                                lineHeight = 19.sp
                            )
                        }

                        // Expandable Original Text Pill
                        if (message.isTranslated && message.originalText.isNotBlank()) {
                            AnimatedVisibility(
                                visible = showOriginal,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.28f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                        Text(
                                            text = "Original: \"${message.originalText}\"",
                                            fontSize = 11.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = Color.White.copy(alpha = 0.85f),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Heart reaction badge attached to corner of bubble
                if (message.reactions.contains("❤️") || message.messageText == "❤️") {
                    Box(
                        modifier = Modifier
                            .offset(x = 6.dp, y = 6.dp)
                            .background(MaterialTheme.colorScheme.background, CircleShape)
                            .padding(2.dp)
                    ) {
                        Text(text = "❤️", fontSize = 12.sp)
                    }
                }
            }

                // Seen / Timestamp
                val displayTime = remember(message.timestamp) {
                    com.example.util.TimeUtils.getRelativeTime(message.timestamp)
                }
                Text(
                    text = if (isMe) {
                        if (message.isRead) "Seen · $displayTime" else "Sent · $displayTime"
                    } else displayTime,
                    fontSize = 10.sp,
                    color = VynTextSecondary,
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                )
        }
    }
}

/**
 * -------------------------------------------------------------
 * 8. INSTAGRAM VIDEO / AUDIO CALL OVERLAY
 * -------------------------------------------------------------
 */
@Composable
fun InstagramCallOverlay(
    call: CallState,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit
) {
    ModernInstagramCallOverlay(
        call = call,
        onEndCall = onEndCall,
        onToggleMute = onToggleMute,
        onToggleCamera = onToggleCamera,
        onToggleSpeaker = {},
        onFlipCamera = {},
        onToggleScreenShare = {}
    )
}

/**
 * -------------------------------------------------------------
 * 9. INSTAGRAM NOTE CREATOR SHEET
 * -------------------------------------------------------------
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramNoteCreatorSheet(
    viewModel: SocialViewModel,
    onDismiss: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    var noteText by remember { mutableStateOf("") }
    var selectedMusic by remember { mutableStateOf<String?>("Rider Vibe 🎧") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = "New Note",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            viewModel.updateMyNote(noteText, selectedMusic)
                        }
                    },
                    enabled = noteText.isNotBlank(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InstagramBlue)
                ) {
                    Text("Share", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Note Bubble Preview above Avatar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp,
                modifier = Modifier.widthIn(max = 240.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = noteText.ifBlank { "Share what's on your mind..." },
                        fontSize = 14.sp,
                        color = if (noteText.isBlank()) VynTextSecondary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    if (selectedMusic != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = InstagramPink,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = selectedMusic ?: "",
                                fontSize = 10.sp,
                                color = InstagramPink
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            VynAvatar(avatarType = profile.avatarType, size = 76.dp)

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = noteText,
                onValueChange = { if (it.length <= 60) noteText = it },
                placeholder = { Text("Share a thought (up to 60 characters)...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Music Selection Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Rider Vibe 🎧", "Aesthetic 🌅", "Coding Beats ⚡").forEach { track ->
                    val isSelected = selectedMusic == track
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) InstagramPink.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) BorderStroke(1.dp, InstagramPink) else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedMusic = if (isSelected) null else track }
                    ) {
                        Text(
                            text = track,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) InstagramPink else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * -------------------------------------------------------------
 * 10. INSTAGRAM NEW MESSAGE SHEET
 * -------------------------------------------------------------
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramNewMessageSheet(
    viewModel: SocialViewModel,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val searchResults by viewModel.userSearchResults.collectAsState()
    val searchLoading by viewModel.userSearchLoading.collectAsState()
    val searchError by viewModel.userSearchError.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "New Message ✉️",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setUserSearchQuery(it)
                },
                placeholder = { Text("Search people by name or handle...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (query.isBlank()) "Suggested Friends" else "Search Results",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = VynTextSecondary
                )
                if (searchLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }

            searchError?.let { msg ->
                Text(
                    text = msg,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(searchResults, key = { it.uid }) { member ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                viewModel.openDirectThread("dm_${member.handle}")
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                VynAvatar(avatarType = member.avatarType, size = 42.dp)
                                Column {
                                    Text(
                                        text = member.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "@${member.handle}",
                                        fontSize = 12.sp,
                                        color = VynTextSecondary
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    onDismiss()
                                    viewModel.openDirectThread("dm_${member.handle}")
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = InstagramBlue),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                            ) {
                                Text("Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
