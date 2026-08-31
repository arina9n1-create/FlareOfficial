package com.example.ui.screens

import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import com.example.data.VynNumber.VynNumberService
import com.example.ui.theme.InstagramOrange
import com.example.ui.theme.VynTextSecondary
import com.example.ui.viewmodel.VynNumberViewModel
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.ContextCompat
import com.example.ui.viewmodel.VynNumberChatItem
import com.example.ui.viewmodel.VynNumberMessage
import com.example.ui.viewmodel.VynNumberStep
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * VYN NUMBER — phone-number-based communication system.
 *
 * Completely separate from Primary (Vyn9 account) chats: identity, conversations
 * and messages live in their own tables with a dedicated phone-auth session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VynNumberScreen(
    viewModel: VynNumberViewModel,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val step by viewModel.step.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.dismissError()
        }
    }

    val activity = LocalContext.current as? Activity
    BackHandler(enabled = onBack != null || onDismiss != null || step is VynNumberStep.Chat) {
        val s = step
        when {
            s is VynNumberStep.Chat -> viewModel.backFromChat()
            onDismiss != null      -> onDismiss()
            onBack != null         -> onBack()
            activity != null       -> activity.finish()
        }
    }

    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        when (val s = step) {
            is VynNumberStep.Setup -> VynNumberAuthScreen(viewModel, loading)
            is VynNumberStep.Home   -> VynNumberHome(viewModel, s)
            is VynNumberStep.Chat   -> VynNumberConversation(viewModel, s)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = androidx.compose.ui.Modifier.align(Alignment.BottomCenter)
        )
    }
}

/* =============================================================================
   SCREEN 1 — AUTH (SIGN UP / LOG IN WITH NUMBER + PASSWORD)
   ========================================================================== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VynNumberAuthScreen(viewModel: VynNumberViewModel, loading: Boolean) {
    var isSignUp by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp).padding(bottom = 24.dp)
        )
        Text(
            text = "VYN NUMBER",
            fontSize = 28.sp, fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (isSignUp) "Create your VYN NUMBER\nPhone number + password"
                   else "Log in to your VYN NUMBER",
            fontSize = 15.sp, color = VynTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 28.dp)
        )

        OutlinedTextField(
            value = raw,
            onValueChange = { if (it.length <= 15) raw = it },
            label = { Text("Phone number") },
            placeholder = { Text("+880XXXXXXXXXX") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            enabled = !loading,
            isError = raw.isNotBlank() && !viewModel.isValidPhone(raw),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )
        if (raw.isNotBlank() && !viewModel.isValidPhone(raw)) {
            Text(
                "Enter a valid phone number",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Start)
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { if (it.length <= 72) password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !loading,
            isError = password.isNotBlank() && password.length < 6,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )
        if (password.isNotBlank() && password.length < 6) {
            Text(
                "Password must be at least 6 characters",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Start)
            )
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (isSignUp) viewModel.signUp(raw, password) else viewModel.login(raw, password)
            },
            enabled = !loading && raw.isNotBlank() && password.length >= 6 && viewModel.isValidPhone(raw),
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (loading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (isSignUp) "Create account" else "Log in",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
        }

        TextButton(
            onClick = { isSignUp = !isSignUp },
            enabled = !loading,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = if (isSignUp) "Already have a VYN NUMBER? Log in"
                       else "New here? Create an account",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/* SCREEN 3 — HOME */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VynNumberHome(viewModel: VynNumberViewModel, home: VynNumberStep.Home) {
    val chats by viewModel.chats.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val chatsLoading by viewModel.chatsLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showContacts by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("VYN NUMBER", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = VynTextSecondary)
                Text(text = home.phone, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
            TextButton(onClick = { viewModel.signOutVynNumber() }) {
                Icon(Icons.Default.Edit, "Change", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = VynTextSecondary, modifier = Modifier.size(20.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { if (it.length <= 15) searchQuery = it },
                singleLine = true,
                placeholder = { Text("Search by phone number...", fontSize = 14.sp, color = VynTextSecondary) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
            )
            if (searchQuery.isNotBlank()) {
                IconButton(onClick = { viewModel.searchNumber(searchQuery) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Check number", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }

        searchResult?.let { res ->
            val isActive = res.optBoolean("active", false)
            val foundPhone = res.optString("phone", "")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable(enabled = isActive) { if (isActive) viewModel.openChatWith(foundPhone) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Person else Icons.Default.PersonOff,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else VynTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isActive) "$foundPhone is on VYN NUMBER" else "This number is not active on VYN NUMBER.",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(text = if (isActive) "Tap to start chatting" else "Try another number", fontSize = 12.sp, color = VynTextSecondary)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Chats", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { showContacts = true }.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Contacts, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(text = "Contacts", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
        when {
            chatsLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            chats.isEmpty() -> {
                Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Forum, null, tint = VynTextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(text = "No VYN NUMBER chats yet.", fontSize = 14.sp, color = VynTextSecondary, textAlign = TextAlign.Center)
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(chats, key = { it.conversationId }) { item ->
                        VynNumberChatListItem(item) { viewModel.openExistingChat(item) }
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
    if (showContacts) VynNumberContactsPicker(viewModel = viewModel, onDismiss = { showContacts = false })
}

@Composable
private fun VynNumberChatListItem(item: VynNumberChatItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (item.peerName.ifBlank { item.peerPhone }).take(1).uppercase(),
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = item.peerName.ifBlank { item.peerPhone }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = item.lastPreview.ifBlank { "No messages yet" }, fontSize = 13.sp, color = VynTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            item.lastAt?.let { Text(text = formatVwTime(it), fontSize = 11.sp, color = VynTextSecondary) }
            if (item.unreadCount > 0) {
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(text = item.unreadCount.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

private fun formatVwTime(millis: Long?): String = millis?.let { ms ->
    try {
        val now = System.currentTimeMillis()
        val diff = now - ms
        when {
            diff < 60000 -> "now"
            diff < 86400000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
            diff < 172800000 -> "Yesterday"
            else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(ms))
        }
    } catch (e: Exception) { "" }
} ?: ""

/**
 * DEVICE CONTACTS picker — PRIVACY-FIRST:
 *  * reads contacts ONLY after READ_CONTACTS permission (platform dialog),
 *  * everything stays LOCAL: nothing uploaded, no background sync,
 *  * tapping a number does a single-number lookup via the same StateFlow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VynNumberContactsPicker(viewModel: VynNumberViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var permissionDenied by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchResult by viewModel.searchResult.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contacts = viewModel.loadDeviceContacts()
        } else {
            permissionDenied = true
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            contacts = viewModel.loadDeviceContacts()
        } else {
            permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Contacts", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = VynTextSecondary, modifier = Modifier.size(20.dp))
                    }
                }

                if (permissionDenied) {
                    Text(
                        "Contacts permission denied. Grant it in system settings to pick a contact's number.",
                        fontSize = 13.sp, color = VynTextSecondary
                    )
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { if (it.length <= 20) query = it },
                        singleLine = true,
                        label = { Text("Search contacts...") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(Modifier.height(8.dp))
                    if (contacts.isEmpty()) {
                        Text("Loading contacts…", fontSize = 13.sp, color = VynTextSecondary)
                    } else {
                        val filtered = contacts.filter {
                            it.first.contains(query, ignoreCase = true) || it.second.contains(query)
                        }
                        LazyColumn(Modifier.height(300.dp)) {
                            items(filtered, key = { it.second + it.first }) { (name, number) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            viewModel.openChatWith(number)
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Person, null, tint = VynTextSecondary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(number, fontSize = 12.sp, color = VynTextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* SCREEN 4 — CONVERSATION (mirrors Primary InstagramConversationScreen) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VynNumberConversation(viewModel: VynNumberViewModel, step: VynNumberStep.Chat) {
    val messages by viewModel.messages.collectAsState()
    val msgsLoading by viewModel.msgsLoading.collectAsState()
    val me = viewModel.myIdentityId
    val sending by viewModel.sending.collectAsState()

    var input by remember { mutableStateOf("") }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var pendingDeleteMessage by remember { mutableStateOf<VynNumberMessage?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-detect screen size for responsive layout
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isCompact = screenWidthDp < 360
    val isLarge = screenWidthDp >= 600

    // Responsive sizing
    val horizontalPadding = if (isCompact) 8.dp else if (isLarge) 24.dp else 16.dp
    val bubbleMaxWidth = if (isCompact) 260.dp else if (isLarge) 400.dp else 300.dp
    val avatarSize = if (isCompact) 36.dp else 42.dp
    val headerFontSize = if (isCompact) 14.sp else 16.sp

    LaunchedEffect(step.conversationId) {
        viewModel.markRead(step.conversationId)
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Root VynNumberScreen Box already applies statusBarsPadding(); no double here.
            .imePadding()
    ) {
        // Header — Instagram DM style (responsive)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = { viewModel.backFromChat() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                // Avatar with online indicator
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (step.peerName.ifBlank { step.peerPhone }).take(1).uppercase(),
                            fontSize = (avatarSize.value * 0.45f).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFF00E676), CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = step.peerName.ifBlank { step.peerPhone },
                        fontSize = headerFontSize, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = "Active now", fontSize = 12.sp, color = VynTextSecondary)
                }
                // Call buttons (Instagram style)
                IconButton(onClick = { /* Audio call */ }) {
                    Icon(Icons.Default.Call, "Audio Call", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { /* Video call */ }) {
                    Icon(Icons.Default.Videocam, "Video Call", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                // Settings menu
                IconButton(onClick = { showSettingsSheet = true }) {
                    Icon(Icons.Default.MoreVert, "Chat settings", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
                }
            }
        }
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
            thickness = 1.dp
        )

        // Messages stream (responsive)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (msgsLoading) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxSize().padding(bottom = 120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Forum, null,
                            tint = VynTextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No messages yet.",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text("Say hi 👋", fontSize = 13.sp, color = VynTextSecondary)
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    VynNumberMessageBubble(
                        msg = msg,
                        myIdentityId = me,
                        maxBubbleWidth = bubbleMaxWidth,
                        onLongPress = {
                            if (msg.senderIdentityId == me) pendingDeleteMessage = msg
                        }
                    )
                }
            }
        }

        // Input bar — Instagram style (responsive)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Camera button (Instagram style)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clip(CircleShape)
                        .clickable { /* Media picker */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, "Camera", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                // Text input
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { if (it.length <= 1000) input = it },
                            placeholder = { Text("Message...", fontSize = 14.sp, color = VynTextSecondary) },
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f),
                            singleLine = false,
                            maxLines = 4
                        )
                        if (input.trim().isBlank()) {
                            IconButton(
                                onClick = { scope.launch { viewModel.sendText("❤️") } },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Favorite, "Send Heart", tint = Color(0xFFFF3040), modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                // Send button
                if (input.trim().isNotBlank()) {
                    Text(
                        text = "Send",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(enabled = !sending) {
                                viewModel.sendText(input.trim())
                                input = ""
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // Chat settings bottom sheet
    if (showSettingsSheet) {
        VynNumberChatSettingsSheet(
            conversationId = step.conversationId,
            peerPhone = step.peerPhone,
            peerName = step.peerName,
            viewModel = viewModel,
            onDismiss = { showSettingsSheet = false }
        )
    }

    // Delete message confirmation dialog (long-press on your own message)
    pendingDeleteMessage?.let { m ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMessage = null },
            title = { Text("Delete message?", fontWeight = FontWeight.Bold) },
            text = { Text("You can only delete your own messages. It will be removed for everyone in this chat.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(m.id)
                    pendingDeleteMessage = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMessage = null }) { Text("Cancel") }
            }
        )
    }
}

/** A single VYN NUMBER message bubble — Instagram DM style (responsive). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VynNumberMessageBubble(
    msg: VynNumberMessage,
    myIdentityId: String,
    maxBubbleWidth: Dp = 300.dp,
    onLongPress: () -> Unit = {}
) {
    val isMine = msg.senderIdentityId == myIdentityId
    val align = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor: Brush = if (isMine) {
        Brush.linearGradient(listOf(Color(0xFF833AB4), Color(0xFFE1306C), Color(0xFFFF6938)))
    } else {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant))
    }
    val textColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
        contentAlignment = align
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = maxBubbleWidth)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (isMine) 18.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 18.dp
                ),
                color = if (isMine) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                modifier = if (isMine) Modifier.background(
                    brush = bubbleColor,
                    shape = RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = 18.dp, bottomEnd = 4.dp
                    )
                ) else Modifier
            ) {
                Text(
                    text = msg.text, fontSize = 15.sp, color = textColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VynNumberChatSettingsSheet(
    conversationId: String,
    peerPhone: String,
    peerName: String,
    viewModel: VynNumberViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
                    .width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )
            Text("Chat Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            SettingsRow(Icons.Default.PushPin, "Pin chat", { viewModel.togglePin(conversationId); onDismiss() })
            SettingsRow(Icons.Default.Star, "Favorite", { viewModel.toggleFavorite(conversationId); onDismiss() })
            SettingsRow(Icons.Default.VolumeOff, "Mute notifications", { viewModel.toggleMute(conversationId); onDismiss() })
            SettingsRow(Icons.Default.Archive, "Archive chat", { viewModel.toggleArchive(conversationId); onDismiss() })
            SettingsRow(Icons.Default.MarkEmailUnread, "Mark as unread", { viewModel.markUnread(conversationId); onDismiss() })
            SettingsRow(Icons.Default.DeleteSweep, "Clear history", { viewModel.clearHistory(conversationId); onDismiss() }, MaterialTheme.colorScheme.error)
            SettingsRow(Icons.Default.DeleteForever, "Delete conversation", { viewModel.deleteConversation(conversationId); onDismiss() }, MaterialTheme.colorScheme.error)
            SettingsRow(Icons.Default.Block, "Block $peerPhone", { viewModel.blockUser(peerPhone); onDismiss() }, MaterialTheme.colorScheme.error)
            SettingsRow(Icons.Default.Flag, "Report", { onDismiss() })
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(text = label, fontSize = 15.sp, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VynNumberReportSheet(
    peerPhone: String,
    viewModel: VynNumberViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reasons = listOf(
        "Spam or scam", "Harassment or bullying", "Hate speech or symbols",
        "Violence or dangerous content", "Nudity or sexual content",
        "False information", "Other"
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
                    .width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )
            Text("Report $peerPhone", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            reasons.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text(text = reason, fontSize = 15.sp)
                }
            }
        }
    }
}

