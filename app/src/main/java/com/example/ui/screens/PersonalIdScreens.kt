package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Personal ID — a private, separate identity namespace on top of the Vyn9 account.
 * Never shows the Vyn9 username / account id / phone / email.
 */
@Composable
fun PersonalIdScreen(
    viewModel: PersonalIdViewModel,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
val step by viewModel.step.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val activeCall by viewModel.activeCall.collectAsState()
    val incomingCall by viewModel.incomingCall.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    val activity = LocalContext.current as? Activity
    BackHandler(
        enabled = onBack != null || onDismiss != null || step is PidStep.Chat
    ) {
        val s = step
        when {
            activeCall != null -> viewModel.endCall()
            incomingCall != null -> viewModel.rejectIncomingCall()
            s is PidStep.Chat -> viewModel.backFromChat()
            onDismiss != null -> onDismiss()
            onBack != null -> onBack()
            activity != null -> activity.finish()
        }
    }

    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        when (val s = step) {
            is PidStep.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PidStep.Setup -> PidCreateScreen(viewModel, loading)
            is PidStep.Home -> PidHomeScreen(viewModel, s.username)
            is PidStep.Chat -> PidConversationScreen(viewModel, s)
        }

        // Real WebRTC call overlay (reuses the same HD call room as Primary).
        activeCall?.let { call ->
            com.example.ui.components.ModernInstagramCallOverlay(
                call = call,
                onEndCall = { viewModel.endCall() },
                onToggleMute = { viewModel.toggleMute() },
                onToggleCamera = { viewModel.toggleCamera() },
                onToggleSpeaker = { viewModel.toggleSpeaker() },
                onFlipCamera = { viewModel.flipCamera() },
                onToggleScreenShare = {},
                localRenderer = viewModel.webRtc.localVideoRenderer,
                remoteRenderer = viewModel.webRtc.remoteVideoRenderer
            )
        }

        // Incoming call prompt (privacy-safe: shows only the peer Personal ID).
        incomingCall?.let { inc ->
            AlertDialog(
                onDismissRequest = { viewModel.rejectIncomingCall() },
                title = { Text("Incoming ${if (inc.callType == "VIDEO") "Video" else "Audio"} Call") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "@${inc.peerUsername}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "is calling you on Personal ID",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VynTextSecondary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.acceptIncomingCall() }) {
                        Text("Accept", color = InstagramPink, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.rejectIncomingCall() }) {
                        Text("Decline", color = VynTextSecondary)
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
/* =============================================================================
   SCREEN: CREATE PERSONAL ID
   ========================================================================== */
@Composable
private fun PidCreateScreen(viewModel: PersonalIdViewModel, loading: Boolean) {
    var raw by remember { mutableStateOf("") }
    val normalized = viewModel.normalizeUsername(raw)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (screenWidthDp < 360f) 64.dp else 80.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(InstagramPink, InstagramOrange, InstagramYellow)))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Personal ID",
            fontSize = 28.sp, fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "A private, separate identity\nthat never shows your Vyn9 account",
            fontSize = 15.sp, color = VynTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = raw,
            onValueChange = { if (it.length <= 20 && !it.contains(' ')) raw = it },
            label = { Text("@username") },
            placeholder = { Text("e.g. darkmoon") },
            singleLine = true,
            enabled = !loading,
            isError = raw.isNotBlank() && normalized == null,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Text
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )
        if (raw.isNotBlank() && normalized == null) {
            Text(
                "3-20 characters, start with a letter, letters/numbers/underscore only",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Start)
            )
        }
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { viewModel.create(raw) },
            enabled = !loading && normalized != null,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text("Create Personal ID", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = "One Personal ID per account.\nYour Vyn9 username is never shown or searched.",
            fontSize = 12.sp, color = VynTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}
/* =============================================================================
   SCREEN: PERSONAL ID HOME (header + search + chats)
   ========================================================================== */
@Composable
private fun PidHomeScreen(viewModel: PersonalIdViewModel, username: String) {
    var query by remember { mutableStateOf("") }
    val search by viewModel.search.collectAsState()
    val chats by viewModel.chats.collectAsState()
    val chatsLoading by viewModel.chatsLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Personal ID", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VynTextSecondary)
                Text(
                    "@$username",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = { viewModel.refreshInbox() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
            }
        }

        SearchBarLike(
            query = query,
            onQuery = { q -> query = q; viewModel.searchUsernames(q) },
            onClear = { query = ""; viewModel.clearSearch() }
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (query.isNotBlank()) {
                if (search.isEmpty()) {
                    item {
                        Text(
                            "No Personal ID found for “$query”",
                            fontSize = 14.sp,
                            color = VynTextSecondary,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                } else {
                    items(search, key = { it.username }) { result ->
                        PidSearchResultRow(result = result, onChat = { viewModel.openChatWith(result.username) })
                    }
                }
            } else {
                if (chatsLoading && chats.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
                if (chats.isEmpty() && !chatsLoading) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ChatBubble, contentDescription = null, tint = VynTextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No Personal ID chats yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Search a Personal ID above to start a private chat",
                                fontSize = 13.sp,
                                color = VynTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                items(chats, key = { it.conversationId }) { chat ->
                    PidChatRow(chat, onClick = { viewModel.openExistingChat(chat) })
                }
            }
        }
    }
}
@Composable
private fun SearchBarLike(query: String, onQuery: (String) -> Unit, onClear: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = VynTextSecondary, modifier = Modifier.size(20.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search Personal ID…", fontSize = 14.sp, color = VynTextSecondary)
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = VynTextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PidSearchResultRow(result: PidSearchResult, onChat: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(InstagramPink, InstagramPurple))),
            contentAlignment = Alignment.Center
        ) {
            Text("@" + result.username.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "@${result.username}",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Button(
            onClick = onChat,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Chat", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
@Composable
private fun PidChatRow(chat: PidChatItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(InstagramPink, InstagramPurple))),
            contentAlignment = Alignment.Center
        ) {
            Text("@" + chat.peerUsername.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "@${chat.peerUsername}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                chat.lastAt?.let {
                    Text(formatPidTime(it), fontSize = 12.sp, color = VynTextSecondary)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.lastPreview.ifEmpty { "Say hi…" },
                    fontSize = 14.sp,
                    color = if (chat.unreadCount > 0) MaterialTheme.colorScheme.onBackground else VynTextSecondary,
                    fontWeight = if (chat.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chat.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(InstagramPink),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(chat.unreadCount.toString().take(2), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

internal fun formatPidTime(millis: Long): String {
    return try {
        val now = System.currentTimeMillis()
        if (now - millis < 86_400_000L) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
        } else {
            SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
        }
    } catch (e: Exception) { "" }
}
/* =============================================================================
   SCREEN: PERSONAL ID CONVERSATION (header shows only @peer Personal ID)
   ========================================================================== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PidConversationScreen(viewModel: PersonalIdViewModel, step: PidStep.Chat) {
    val messages by viewModel.messages.collectAsState()
    val sending by viewModel.sending.collectAsState()
    var input by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        androidx.compose.material3.TopAppBar(
            title = {
                Column {
                    Text(
                        "@${step.peerUsername}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Personal ID conversation",
                        fontSize = 11.sp,
                        color = VynTextSecondary
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { viewModel.backFromChat() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
            },
            actions = {
                IconButton(onClick = { viewModel.startCall(step.conversationId, isVideo = false) }) {
                    Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = { viewModel.startCall(step.conversationId, isVideo = true) }) {
                    Icon(Icons.Outlined.Videocam, contentDescription = "Video Call", tint = MaterialTheme.colorScheme.onBackground)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Say hi to @${step.peerUsername}",
                            fontSize = 14.sp,
                            color = VynTextSecondary
                        )
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                PidMessageBubble(msg = msg, onLongClick = {
                    if (msg.isMine) viewModel.deleteMessage(step.conversationId, msg.id)
                })
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = input,
                onValueChange = { if (it.length <= 2000) input = it },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    if (input.isEmpty()) Text("Message @${step.peerUsername}…", fontSize = 14.sp, color = VynTextSecondary)
                    inner()
                }
            )
            if (input.isNotBlank()) {
                IconButton(
                    onClick = {
                        val t = input.trim()
                        if (t.isNotEmpty() && !sending) {
                            viewModel.sendMessage(step.conversationId, t)
                            input = ""
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(InstagramPink)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PidMessageBubble(msg: PidMessage, onLongClick: () -> Unit) {
    val bubbleColor = if (msg.isMine) {
        Brush.linearGradient(listOf(InstagramPurple, InstagramPink, InstagramOrange))
    } else {
        Brush.linearGradient(listOf(Color(0xFF2A2A2E), Color(0xFF3A3A3E)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = if (msg.isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (msg.isMine) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .combinedClickable(onClick = {}, onLongClick = onLongClick)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = if (msg.isMine) 18.dp else 4.dp,
                            bottomEnd = if (msg.isMine) 4.dp else 18.dp
                        )
                    )
                    .background(brush = bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    text = msg.text,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 200,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    msg.createdAtMs?.let { append(formatPidTime(it)) }
                    if (msg.isMine) append(if (msg.isRead) " · read" else " · sent")
                },
                fontSize = 10.sp,
                color = VynTextSecondary
            )
        }
    }
}