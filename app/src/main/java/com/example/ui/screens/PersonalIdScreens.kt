package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Personal ID — a private, separate identity namespace on top of the FlareOfficial account.
 * Never shows the FlareOfficial username / account id / phone / email.
 */
@Composable
fun PersonalIdScreen(
    viewModel: PersonalIdViewModel,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null
) {
    val step by viewModel.step.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val activeCall by viewModel.activeCall.collectAsState()
    val incomingCall by viewModel.incomingCall.collectAsState()
    val localRenderer by viewModel.agora.localVideoRenderer.collectAsState()
    val remoteRenderer by viewModel.agora.remoteVideoRenderer.collectAsState()

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
            is PidStep.Home -> PidHomeScreen(viewModel, s.username, onSignOut)
            is PidStep.Chat -> PidConversationScreen(viewModel, s)
        }

        // Real WebRTC call overlay (reuses the same HD call room as Primary).
        activeCall?.let { call ->
            com.example.ui.components.ModernFlareOfficialCallOverlay(
                call = call,
                onEndCall = { viewModel.endCall() },
                onToggleMute = { viewModel.toggleMute() },
                onToggleCamera = { viewModel.toggleCamera() },
                onToggleSpeaker = { viewModel.toggleSpeaker() },
                onFlipCamera = { viewModel.flipCamera() },
                onToggleScreenShare = { viewModel.toggleScreenSharing() },
                localRenderer = localRenderer,
                remoteRenderer = remoteRenderer
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
                            color = FlareTextSecondary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.acceptIncomingCall() }) {
                        Text("Accept", color = FlareOfficialPink, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.rejectIncomingCall() }) {
                        Text("Decline", color = FlareTextSecondary)
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

@Composable
private fun PidCreateScreen(viewModel: PersonalIdViewModel, loading: Boolean) {
    var raw by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
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
                .background(Brush.linearGradient(listOf(FlareOfficialPink, FlareOfficialOrange, FlareOfficialYellow)))
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
            text = if (isLogin) {
                "Sign in with your Personal ID\nusername to continue"
            } else {
                "A private, separate identity\nthat never shows your FlareOfficial account"
            },
            fontSize = 15.sp, color = FlareTextSecondary,
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
            onClick = {
                if (isLogin) viewModel.login(raw) else viewModel.create(raw)
            },
            enabled = !loading && normalized != null,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (isLogin) "Log in" else "Create Personal ID",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        TextButton(
            onClick = { isLogin = !isLogin; raw = "" },
            enabled = !loading,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = if (isLogin) "Need an account? Create one" else "Already have a Personal ID? Log in",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = if (isLogin) {
                "Use your Personal ID username to log in.\nCreate a new one if you do not have an account yet."
            } else {
                "One Personal ID per account.\nYour FlareOfficial username is never shown or searched."
            },
            fontSize = 12.sp, color = FlareTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
private fun PidHomeScreen(viewModel: PersonalIdViewModel, username: String, onSignOut: (() -> Unit)? = null) {
    var query by remember { mutableStateOf("") }
    val search by viewModel.search.collectAsState()
    val chats by viewModel.chats.collectAsState()
    val chatsLoading by viewModel.chatsLoading.collectAsState()
    val blocked by viewModel.blocked.collectAsState()
    val settings by viewModel.conversationSettings.collectAsState()
    val pinnedConvs by viewModel.pinnedConvs.collectAsState()
    val hiddenConvs by viewModel.hiddenConvs.collectAsState()
    var actionItem by remember { mutableStateOf<PidChatItem?>(null) }
    var pendingClearConvId by remember { mutableStateOf<String?>(null) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val visibleChats = chats.filter { !it.isArchived && it.peerUsername !in blocked }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Personal ID", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FlareTextSecondary)
                Text(
                    "@$username",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            // SWAPPED: Refresh is now where Info was.
            IconButton(onClick = { viewModel.refreshInbox() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
            }
            // SWAPPED: Settings (Info) is now where Refresh was.
            IconButton(onClick = { showInfoSheet = true }) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Personal ID settings",
                    tint = MaterialTheme.colorScheme.primary
                )
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
                            "No Personal ID found for \"$query\"",
                            fontSize = 14.sp,
                            color = FlareTextSecondary,
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
                if (visibleChats.isEmpty() && !chatsLoading) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ChatBubble, contentDescription = null, tint = FlareTextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
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
                                color = FlareTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                items(visibleChats, key = { it.conversationId }) { chat ->
                    PidChatRow(chat, onClick = { viewModel.openExistingChat(chat) }, onLongClick = { actionItem = chat })
                }
                if (chats.any { it.isArchived }) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showInfoSheet = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Archive, contentDescription = null, tint = FlareTextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Archived chats — view in settings",
                                fontSize = 13.sp,
                                color = FlareTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    actionItem?.let { item ->
        PidChatListActionSheet(
            item = item,
            onDismiss = { actionItem = null },
            onToggleMute = {
                actionItem = null
                viewModel.toggleListMute(item.conversationId, item.isMuted, item.isArchived)
            },
            onToggleArchive = {
                actionItem = null
                viewModel.toggleListArchive(item.conversationId, item.isMuted, item.isArchived)
            },
            onMarkRead = {
                actionItem = null
                viewModel.markListRead(item.conversationId)
            },
            onClearHistory = {
                actionItem = null
                pendingClearConvId = item.conversationId
            },
            isBlocked = item.peerUsername in blocked,
            onToggleBlock = {
                actionItem = null
                viewModel.toggleBlock(item.peerUsername, item.peerUsername in blocked)
            }
        )
    }

    pendingClearConvId?.let { convId ->
        AlertDialog(
            onDismissRequest = { pendingClearConvId = null },
            title = { Text("Clear chat history?", fontWeight = FontWeight.Bold) },
            text = { Text("This removes all messages in this conversation. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingClearConvId = null
                    viewModel.clearHistory(convId)
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearConvId = null }) { Text("Cancel") }
            }
        )
    }

    if (showInfoSheet) {
        PidInfoSettingsSheet(
            archivedChats = chats.filter { it.isArchived },
            blocked = blocked,
            readReceipts = settings.readReceipts,
            onDismiss = { showInfoSheet = false },
            onRestore = { chat ->
                viewModel.toggleListArchive(chat.conversationId, chat.isMuted, chat.isArchived)
            },
            onOpenChat = { chat ->
                showInfoSheet = false
                viewModel.openExistingChat(chat)
            },
            onToggleReadReceipts = { viewModel.updateReadReceipts(!settings.readReceipts) },
            onToggleBlock = { username, currentlyBlocked ->
                viewModel.toggleBlock(username, currentlyBlocked)
            },
            onLogout = {
                showInfoSheet = false
                showLogoutConfirm = true
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log out?", fontWeight = FontWeight.Bold) },
            text = { Text("You will be signed out of your FlareOfficial account and Personal ID.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onSignOut?.invoke()
                }) { Text("Log out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PidInfoSettingsSheet(
    archivedChats: List<PidChatItem>,
    blocked: Set<String>,
    readReceipts: Boolean,
    onDismiss: () -> Unit,
    onRestore: (PidChatItem) -> Unit,
    onOpenChat: (PidChatItem) -> Unit,
    onToggleReadReceipts: () -> Unit,
    onToggleBlock: (String, Boolean) -> Unit,
    onLogout: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Personal ID settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))

            Text("ARCHIVED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary)
            Spacer(Modifier.height(6.dp))
            if (archivedChats.isEmpty()) {
                Text("No archived chats", fontSize = 14.sp, color = FlareTextSecondary)
            } else {
                archivedChats.forEach { chat ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(FlareOfficialPink, FlareOfficialPurple))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("@" + chat.peerUsername.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "@${chat.peerUsername}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { onOpenChat(chat) }) { Text("Open") }
                        TextButton(onClick = { onRestore(chat) }) {
                            Text("Restore", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            PidInfoPrivacyBlockedLogout(readReceipts, blocked, onToggleReadReceipts, onToggleBlock, onLogout)
        }
    }
}

@Composable
private fun PidInfoPrivacyBlockedLogout(
    readReceipts: Boolean,
    blocked: Set<String>,
    onToggleReadReceipts: () -> Unit,
    onToggleBlock: (String, Boolean) -> Unit,
    onLogout: () -> Unit
) {
    Text("PRIVACY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary)
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.FactCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Read receipts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Show others when you've read their messages", fontSize = 12.sp, color = FlareTextSecondary)
        }
        Switch(checked = readReceipts, onCheckedChange = { onToggleReadReceipts() })
    }
    Spacer(Modifier.height(14.dp))
    Text("BLOCKED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary)
    Spacer(Modifier.height(4.dp))
    if (blocked.isEmpty()) {
        Text("No blocked Personal IDs", fontSize = 14.sp, color = FlareTextSecondary)
    } else {
        blocked.forEach { username ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("@" + username.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "@$username",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onToggleBlock(username, true) }) {
                    Text("Unblock", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("ACCOUNT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FlareTextSecondary)
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLogout() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Log out", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            Text("Sign out of your FlareOfficial account", fontSize = 12.sp, color = FlareTextSecondary)
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
            Icon(Icons.Default.Search, contentDescription = null, tint = FlareTextSecondary, modifier = Modifier.size(20.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search Personal ID...", fontSize = 14.sp, color = FlareTextSecondary)
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = FlareTextSecondary, modifier = Modifier.size(16.dp))
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
                .background(Brush.linearGradient(listOf(FlareOfficialPink, FlareOfficialPurple))),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PidChatRow(chat: PidChatItem, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(FlareOfficialPink, FlareOfficialPurple))),
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
                    val displayTime = remember(it) { com.example.util.TimeUtils.getRelativeTime(it) }
                    Text(displayTime, fontSize = 12.sp, color = FlareTextSecondary)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.lastPreview.ifEmpty { "Say hi..." },
                    fontSize = 14.sp,
                    color = if (chat.unreadCount > 0) MaterialTheme.colorScheme.onBackground else FlareTextSecondary,
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
                            .background(FlareOfficialPink),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PidChatListActionSheet(
    item: PidChatItem,
    isBlocked: Boolean = false,
    onDismiss: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleArchive: () -> Unit,
    onMarkRead: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleBlock: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(FlareOfficialPink, FlareOfficialPurple))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("@".plus(item.peerUsername).take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("@${item.peerUsername}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (item.unreadCount > 0) "$item.unreadCount unread — ${item.lastPreview.ifBlank { "tap to open" }}"
                        else item.lastPreview.ifBlank { "Personal ID conversation" },
                        fontSize = 12.sp,
                        color = FlareTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            Spacer(Modifier.height(6.dp))

            PidSettingsRow(
                icon = if (item.isMuted) Icons.Default.NotificationsOff else Icons.Default.NotificationsNone,
                title = if (item.isMuted) "Unmute notifications" else "Mute notifications",
                subtitle = if (item.isMuted) "Start receiving message alerts" else "Stop receiving message alerts",
                onClick = onToggleMute
            )
            PidSettingsRow(
                icon = if (item.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                title = if (item.isArchived) "Unarchive chat" else "Archive chat",
                subtitle = if (item.isArchived) "Move this chat back to the inbox" else "Move this chat to the archive",
                onClick = onToggleArchive
            )
            if (item.unreadCount > 0) {
                PidSettingsRow(
                    icon = Icons.Default.MarkEmailUnread,
                    title = "Mark as read",
                    subtitle = "Clear the unread badge for this chat",
                    onClick = onMarkRead
                )
            }
            PidSettingsRow(
                icon = Icons.Default.Block,
                title = if (isBlocked) "Unblock @${item.peerUsername}" else "Block @${item.peerUsername}",
                subtitle = if (isBlocked) "They will be able to message you again" else "They will no longer be able to message you",
                tint = MaterialTheme.colorScheme.error,
                onClick = onToggleBlock
            )
            PidSettingsRow(
                icon = Icons.Default.DeleteSweep,
                title = "Clear chat history",
                subtitle = "Delete all messages in this chat",
                tint = MaterialTheme.colorScheme.error,
                onClick = onClearHistory
            )
            Spacer(Modifier.height(22.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PidConversationScreen(viewModel: PersonalIdViewModel, step: PidStep.Chat) {
    val messages by viewModel.messages.collectAsState()
    val sending by viewModel.sending.collectAsState()
    val mediaBusy by viewModel.mediaBusy.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()
    val settings by viewModel.conversationSettings.collectAsState()
    var input by remember { mutableStateOf("") }
    var actionsTarget by remember { mutableStateOf<PidMessage?>(null) }
    var pendingClear by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var pending by remember { mutableStateOf<List<PersonalIdViewModel.MediaPick>>(emptyList()) }
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            pending = uris.mapNotNull { uri ->
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val name = context.contentResolver.query(
                    uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else "attachment" } ?: "attachment"
                PersonalIdViewModel.MediaPick(uri, name ?: "attachment", mime)
            }
        }
    }

    var recorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var recFile by remember { mutableStateOf<java.io.File?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recStartedAt by remember { mutableLongStateOf(0L) }
    var recorderError by remember { mutableStateOf(false) }
    var recRequestStart by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) recRequestStart = true }
    LaunchedEffect(recorderError) {
        if (recorderError) {
            android.widget.Toast.makeText(context, "Could not start recording", android.widget.Toast.LENGTH_SHORT).show()
            recorderError = false
        }
    }

    fun startRecording() {
        try {
            val file = java.io.File(context.cacheDir, "pid_voice_${System.currentTimeMillis()}.m4a")
            val r = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }
            r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recFile = file
            recStartedAt = System.currentTimeMillis()
            recording = true
        } catch (e: Exception) {
            android.util.Log.e("PidChat", "voice record failed", e)
            recording = false
            recorderError = true
        }
    }

    fun stopRecording(send: Boolean) {
        val r = recorder
        val file = recFile
        recording = false
        recorder = null
        recFile = null
        if (r == null || file == null) return
        try { r.stop() } catch (e: Exception) { android.util.Log.e("PidChat", "record stop failed", e) }
        try { r.release() } catch (_: Exception) {}
        if (send && file.exists() && file.length() > 0) {
            val durationSec = ((System.currentTimeMillis() - recStartedAt) / 1000).toInt().coerceAtLeast(1)
            viewModel.sendMediaBatch(
                step.conversationId,
                listOf(
                    PersonalIdViewModel.MediaPick(
                        Uri.fromFile(file),
                        "voice_${durationSec}s.m4a",
                        "audio/mp4"
                    )
                )
            )
        } else {
            file.delete()
        }
    }

    fun onMicClicked() {
        if (recording) { stopRecording(send = true); return }
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(recRequestStart) {
        if (recRequestStart) {
            recRequestStart = false
            startRecording()
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (recording) stopRecording(send = false)
        }
    }

    fun sendPending() {
        val items = pending
        if (items.isEmpty()) return
        viewModel.sendMediaBatch(step.conversationId, items, input.trim())
        pending = emptyList()
        input = ""
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                        when {
                            com.example.util.Presence.isOnline(settings.peerLastSeenMs) -> "Active now"
                            settings.peerLastSeenMs != null -> com.example.util.Presence.label(settings.peerLastSeenMs)
                            else -> "Offline"
                        },
                        fontSize = 11.sp,
                        color = if (com.example.util.Presence.isOnline(settings.peerLastSeenMs)) FlareOfficialPink else FlareTextSecondary
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { viewModel.backFromChat() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
            },
            actions = {
                // ACTIONS BLOCK: spacing reduced, icons closer together.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp) // Minimum gap
                ) {
                    IconButton(onClick = { viewModel.startCall(step.conversationId, isVideo = false) }) {
                        Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = { viewModel.startCall(step.conversationId, isVideo = true) }) {
                        Icon(Icons.Outlined.Videocam, contentDescription = "Video Call", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(26.dp))
                    }
                    // "i" (Info) button — opens the Personal ID chat settings sheet.
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Chat settings", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

        // SMART ADJUSTING CONTAINER: weights and window insets for gesture/ime stability.
        Column(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
        ) {
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
                                color = FlareTextSecondary
                            )
                        }
                    }
                }
                items(messages, key = { it.id }) { msg ->
                    PidMessageBubble(
                        msg = msg,
                        viewModel = viewModel,
                        onLongClick = { actionsTarget = msg },
                        onReactionPicked = { emoji ->
                            viewModel.toggleReaction(step.conversationId, msg.id, emoji)
                        }
                    )
                }
            }

            if (pending.isNotEmpty() && !mediaBusy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${pending.size} selected",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { pending = emptyList() }) { Text("Cancel") }
                    TextButton(onClick = { sendPending() }) {
                        Text("Send", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (mediaBusy) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Processing $uploadProgress%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (replyingTo != null || editingMessage != null) {
                val label = when {
                    editingMessage != null -> "Editing message"
                    replyingTo?.isMine == true -> "Replying to yourself"
                    else -> "Replying to @${step.peerUsername}"
                }
                val preview = (editingMessage ?: replyingTo)?.text ?: ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(FlareOfficialPink)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FlareOfficialPink)
                        Text(
                            preview.ifBlank { "attachment" },
                            fontSize = 13.sp,
                            color = FlareTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { viewModel.cancelComposerAction() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = FlareTextSecondary)
                    }
                }
            }

            // INPUT AREA: uses navigationBarsPadding + imePadding for gesture stability.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 6.dp, end = 6.dp, top = 3.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = input,
                    onValueChange = { if (it.length <= 2000) input = it },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = {
                        val t = input.trim()
                        if (t.isNotEmpty() && !sending && !mediaBusy) {
                            viewModel.sendMessage(step.conversationId, t)
                            input = ""
                        }
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { inner ->
                        if (input.isEmpty()) Text("Message @${step.peerUsername}...", fontSize = 15.sp, color = FlareTextSecondary)
                        inner()
                    }
                )
                IconButton(
                    onClick = { if (!mediaBusy) onMicClicked() },
                    enabled = !mediaBusy,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (recording) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = if (recording) FlareOfficialPink else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = { attachmentLauncher.launch("*/*") },
                    enabled = !mediaBusy && !recording,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Attach", tint = if (mediaBusy) FlareTextSecondary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(enabled = !mediaBusy && !recording) {
                            val t = input.trim()
                            if (t.isNotEmpty()) {
                                viewModel.sendMessage(step.conversationId, t)
                                input = ""
                            } else {
                                viewModel.sendMessage(step.conversationId, "❤️")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // When the user has typed an SMS — show a real Send (paper-plane) icon;
                    // when empty — show the quick heart reaction button.

                    if (input.isNotBlank()) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Send heart",
                            tint = FlareOfficialPink,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

    actionsTarget?.let { msg ->
        ModalBottomSheet(onDismissRequest = { actionsTarget = null }) {
            val emojis = listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDD25")
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("React", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FlareTextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    emojis.forEach { e ->
                        val selected = msg.reactions.any { it.emoji == e && it.reactedByMe }
                        Text(
                            e,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { viewModel.toggleReaction(step.conversationId, msg.id, e); actionsTarget = null }
                                .padding(8.dp)
                        )
                    }
                }
                Text(
                    "Reply",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { actionsTarget = null; viewModel.beginReply(msg) }
                        .padding(vertical = 12.dp)
                )
                if (msg.isMine && msg.mediaUrl.isBlank()) {
                    Text(
                        "Edit",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                actionsTarget = null
                                input = msg.text
                                viewModel.beginEdit(msg)
                            }
                            .padding(vertical = 12.dp)
                    )
                }
                if (msg.isMine) {
                    Text(
                        if (msg.isPinned) "Unpin message" else "Pin message",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { actionsTarget = null; viewModel.setPinned(step.conversationId, msg.id, !msg.isPinned) }
                            .padding(vertical = 12.dp)
                    )
                    Text(
                        "Delete message",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { actionsTarget = null; viewModel.deleteMessage(step.conversationId, msg.id) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
    }

    if (pendingClear) {
        AlertDialog(
            onDismissRequest = { pendingClear = false },
            title = { Text("Clear chat history?", fontWeight = FontWeight.Bold) },
            text = { Text("This removes this conversation's messages for everyone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { pendingClear = false; viewModel.clearHistory(step.conversationId) }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = false }) { Text("Cancel") }
            }
        )
    }

    if (showSettingsSheet) {
        PidConversationSettingsSheet(
            peerUsername = step.peerUsername,
            settings = settings,
            onDismiss = { showSettingsSheet = false },
            onToggleMuted = {
                showSettingsSheet = false
                viewModel.updateMuted(step.conversationId, !settings.muted)
            },
            onToggleArchived = {
                showSettingsSheet = false
                viewModel.updateArchived(step.conversationId, !settings.archived)
            },
            onToggleReadReceipts = {
                showSettingsSheet = false
                viewModel.updateReadReceipts(!settings.readReceipts)
            },
            onClearHistory = {
                showSettingsSheet = false
                pendingClear = true
            },
            onToggleBlock = {
                showSettingsSheet = false
                viewModel.toggleBlock(step.peerUsername, viewModel.blocked.value.contains(step.peerUsername))
            },
            isBlocked = viewModel.blocked.collectAsState().value.contains(step.peerUsername)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PidConversationSettingsSheet(
    peerUsername: String,
    settings: PidConversationSettings,
    isBlocked: Boolean = false,
    onDismiss: () -> Unit,
    onToggleMuted: () -> Unit,
    onToggleArchived: () -> Unit,
    onToggleReadReceipts: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleBlock: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(FlareOfficialPink.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "@".plus(peerUsername).take(2).uppercase(),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlareOfficialPink
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "@$peerUsername",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Personal ID chat settings",
                        fontSize = 12.sp,
                        color = FlareTextSecondary
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            Spacer(Modifier.height(6.dp))

            PidSettingsRow(
                icon = Icons.Default.NotificationsOff,
                title = if (settings.muted) "Unmute notifications" else "Mute notifications",
                subtitle = if (settings.muted) "Start receiving message alerts" else "Stop receiving message alerts",
                onClick = onToggleMuted
            )
            PidSettingsRow(
                icon = if (settings.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                title = if (settings.archived) "Unarchive chat" else "Archive chat",
                subtitle = if (settings.archived) "Move this chat back to the inbox" else "Move this chat to the archive",
                onClick = onToggleArchived
            )
            PidSettingsRow(
                icon = Icons.Default.DoneAll,
                title = if (settings.readReceipts) "Turn off read receipts" else "Turn on read receipts",
                subtitle = if (settings.readReceipts) "Stop sharing when you've read messages" else "Share when you've read messages",
                onClick = onToggleReadReceipts
            )
            PidSettingsRow(
                icon = Icons.Default.Block,
                title = if (isBlocked) "Unblock @$peerUsername" else "Block @$peerUsername",
                subtitle = if (isBlocked) "They will be able to message you again" else "They will no longer be able to message you",
                tint = MaterialTheme.colorScheme.error,
                onClick = onToggleBlock
            )
            PidSettingsRow(
                icon = Icons.Default.DeleteSweep,
                title = "Clear chat history",
                subtitle = "Delete all messages in this chat",
                tint = MaterialTheme.colorScheme.error,
                onClick = onClearHistory
            )
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun PidSettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(subtitle, fontSize = 12.sp, color = FlareTextSecondary)
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = FlareTextSecondary, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PidMessageBubble(
    msg: PidMessage,
    viewModel: PersonalIdViewModel,
    onLongClick: () -> Unit,
    onReactionPicked: (String) -> Unit
) {
    val bubbleColor = if (msg.isMine) {
        Brush.linearGradient(listOf(Color(0xFF6A1B9A), Color(0xFFAD1457)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF2A2A2E), Color(0xFF3A3A3E)))
    }
    val showReactions = msg.reactions.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (msg.isMine) Alignment.End else Alignment.Start
    ) {
            if (msg.isPinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pinned message", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(2.dp))
            }
            Box(
                modifier = Modifier
                    .widthIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * 0.78f))
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
                Column {
                    if (!msg.replyToId.isNullOrBlank() || msg.replyText.isNotBlank()) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.22f))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Column {
                                Text(
                                    if (msg.replyIsMine) "Replying to yourself" else "Replying to message",
                                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f)
                                )
                                Text(msg.replyText.ifBlank { "attachment" }, fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    if (msg.mediaType == "image") {
                        var isExpanded by remember { mutableStateOf(false) }
                        val imageUrl = run {
                            val p = msg.mediaUrl.trim()
                            if (p.startsWith("http://", true) || p.startsWith("https://", true)) p
                            else com.example.data.remote.Backend.URL +
                                "/storage/v1/object/authenticated/personal-id-media/" +
                                java.net.URLEncoder.encode(p, "UTF-8").replace("+", "%20")
                        }
                        Box(
                            Modifier
                                .animateContentSize()
                                .then(if (isExpanded) Modifier.fillMaxWidth().heightIn(max = 500.dp) else Modifier.size(200.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable { isExpanded = !isExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            coil.compose.AsyncImage(
                                model = imageUrl,
                                contentDescription = "Attachment",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = if (isExpanded) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
else if (msg.mediaType == "audio" && msg.mediaUrl.isNotBlank()) {
                        PidVoicePlayer(viewModel, msg)
                        Spacer(Modifier.height(4.dp))
                    } else if (msg.mediaUrl.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(msg.mediaName.ifBlank { "Attachment" }, fontSize = 13.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(msg.mediaType, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    if (msg.text.isNotBlank()) {
                        Text(text = msg.text, fontSize = 15.sp, color = Color.White, maxLines = 200, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val displayTime = remember(msg.createdAtMs) {
                            if (msg.createdAtMs != null) com.example.util.TimeUtils.getRelativeTime(msg.createdAtMs) else "just now"
                        }
                        Text(
                            text = buildString {
                                append(displayTime)
                                if (msg.editedAtMs != null) append("  edited")
                                if (msg.isMine) append(if (msg.isRead) "  read" else "  sent")
                            },
                            fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f)
                        )
                        if (showReactions) {
                            Spacer(Modifier.width(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                msg.reactions.forEach { r ->
                                    Text(
                                        "${r.emoji} ${r.count}",
                                        fontSize = 11.sp,
                                        color = if (r.reactedByMe) Color(0xFFFFE082) else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.25f))
                                            .clickable { onReactionPicked(r.emoji) }
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }


@Composable
private fun PidVoicePlayer(viewModel: PersonalIdViewModel, msg: com.example.ui.viewmodel.PidMessage) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var player by remember(msg.id) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var playing by remember(msg.id) { mutableStateOf(false) }
    var prepared by remember(msg.id) { mutableStateOf(false) }
    var posMs by remember(msg.id) { mutableStateOf(0) }
    var durMs by remember(msg.id) { mutableStateOf(0) }
    var loading by remember(msg.id) { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.runtime.DisposableEffect(msg.id) {
        onDispose {
            player?.let { p -> try { if (p.isPlaying) p.stop(); p.release() } catch (_: Exception) {} }
        }
    }

    fun fmt(ms: Int): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(min = 140.dp, max = 240.dp)
    ) {
        IconButton(
            onClick = {
                val p = player
                when {
                    loading -> {}
                    p != null && playing -> {
                        try { p.pause() } catch (_: Exception) {}
                        playing = false
                    }
                    p != null && prepared -> {
                        try { p.start() } catch (_: Exception) {}
                        playing = true
                    }
                    else -> {
                        loading = true
                        scope.launch {
                            viewModel.downloadMedia(msg)
                                .onSuccess { file ->
                                    val np = android.media.MediaPlayer()
                                    try {
                                        np.setDataSource(file.absolutePath)
                                        np.setOnPreparedListener { mp ->
                                            prepared = true
                                            loading = false
                                            durMs = mp.duration
                                            mp.start()
                                            playing = true
                                        }
                                        np.setOnCompletionListener {
                                            playing = false
                                            posMs = 0
                                            try { it.seekTo(0) } catch (_: Exception) {}
                                        }
                                        np.setOnErrorListener { _, _, _ ->
                                            playing = false; loading = false; true
                                        }
                                        np.prepareAsync()
                                        player = np
                                    } catch (e: Exception) {
                                        loading = false
                                        try { np.release() } catch (_: Exception) {}
                                        android.widget.Toast.makeText(context, "Could not play voice message", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .onFailure {
                                    loading = false
                                    android.widget.Toast.makeText(context, "Could not download voice message", android.widget.Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }
            }
        ) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                playing -> Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White)
                else -> Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
            }
        }
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Slider(
                value = if (durMs > 0) posMs.toFloat() / durMs else 0f,
                onValueChange = { frac ->
                    val p = player
                    if (p != null && prepared && durMs > 0) {
                        posMs = (frac * durMs).toInt()
                        try { p.seekTo(posMs) } catch (_: Exception) {}
                    }
                },
                enabled = prepared,
                modifier = Modifier.fillMaxWidth().height(26.dp),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                )
            )
            Text(
                text = "${fmt(posMs)} / ${fmt(if (durMs > 0) durMs else 0)}",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }

    if (playing) {
        LaunchedEffect(msg.id) {
            while (true) {
                val p = player
                if (p != null) {
                    try { posMs = p.currentPosition } catch (_: Exception) {}
                }
                kotlinx.coroutines.delay(200)
            }
        }
    }
}
