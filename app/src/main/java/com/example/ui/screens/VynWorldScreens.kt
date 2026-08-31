package com.example.ui.screens

import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.vynworld.VynWorldService
import com.example.ui.theme.InstagramOrange
import com.example.ui.theme.VynTextSecondary
import com.example.ui.viewmodel.VynWorldViewModel
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
import com.example.ui.viewmodel.VynWorldChatItem
import com.example.ui.viewmodel.VynWorldMessage
import com.example.ui.viewmodel.VynWorldStep
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * VYN WORLD — phone-number-based communication system.
 *
 * Completely separate from Primary (Vyn9 account) chats: identity, conversations
 * and messages live in their own tables with a dedicated phone-auth session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VynWorldScreen(
    viewModel: VynWorldViewModel,
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
    BackHandler(enabled = onBack != null || onDismiss != null || step is VynWorldStep.Chat) {
        val s = step
        when {
            s is VynWorldStep.Chat -> viewModel.backFromChat()
            onDismiss != null      -> onDismiss()
            onBack != null         -> onBack()
            activity != null       -> activity.finish()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val s = step) {
            is VynWorldStep.Setup -> VynWorldPhoneEntry(viewModel, loading)
            is VynWorldStep.Otp   -> VynWorldOtpEntry(viewModel, s, loading)
            is VynWorldStep.Home   -> VynWorldHome(viewModel, s)
            is VynWorldStep.Chat   -> VynWorldConversation(viewModel, s)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = androidx.compose.ui.Modifier.align(Alignment.BottomCenter)
        )
    }
}

/* =============================================================================
   SCREEN 1 — PHONE NUMBER ENTRY
   ========================================================================== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VynWorldPhoneEntry(viewModel: VynWorldViewModel, loading: Boolean) {
    var raw by remember { mutableStateOf("") }

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
            text = "Vyn World",
            fontSize = 28.sp, fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Enter your phone number to verify",
            fontSize = 15.sp, color = VynTextSecondary,
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
        Button(
            onClick = { viewModel.requestOtp(raw) },
            enabled = !loading && raw.isNotBlank() && viewModel.isValidPhone(raw),
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
            Text("Send OTP", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* =============================================================================
   SCREEN 2 — OTP VERIFICATION
   ========================================================================== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VynWorldOtpEntry(viewModel: VynWorldViewModel, step: VynWorldStep.Otp, loading: Boolean) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Verify ${step.phone}",
            fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Enter the 6-digit code sent via SMS",
            fontSize = 14.sp, color = VynTextSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it.filter { c -> c.isDigit() } },
            label = { Text("OTP code") },
            placeholder = { Text("000000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !loading,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )

        if (step.resendInSec > 0) {
            Text(text = "Resend in ${step.resendInSec}s", fontSize = 13.sp, color = VynTextSecondary)
        } else {
            TextButton(
                onClick = { viewModel.resendOtp() },
                enabled = !loading,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text("Resend code", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { viewModel.verifyOtp(code.trim()) },
            enabled = !loading && code.length == 6,
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
            Text("Verify", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        TextButton(
            onClick = { viewModel.editPhoneNumber() },
            enabled = !loading,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Use a different number", fontSize = 12.sp, color = VynTextSecondary)
        }
    }
}

/* SCREEN 3 — HOME */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VynWorldHome(viewModel: VynWorldViewModel, home: VynWorldStep.Home) {
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
                Text("Vyn World", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = VynTextSecondary)
                Text(text = home.phone, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
            TextButton(onClick = { viewModel.signOutVynWorld() }) {
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
                    Icon(Icons.Default.ArrowForward, "Check number", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
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
                        text = if (isActive) "$foundPhone is on Vyn World" else "This number is not active on Vyn World.",
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
                    Text(text = "No Vyn World chats yet.", fontSize = 14.sp, color = VynTextSecondary, textAlign = TextAlign.Center)
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(chats, key = { it.conversationId }) { item ->
                        VynWorldChatListItem(item) { viewModel.openExistingChat(item) }
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
    if (showContacts) VynWorldContactsPicker(viewModel = viewModel, onDismiss = { showContacts = false })
}

@Composable
private fun VynWorldChatListItem(item: VynWorldChatItem, onClick: () -> Unit) {
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
private fun VynWorldContactsPicker(viewModel: VynWorldViewModel, onDismiss: () -> Unit) {
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

/* SCREEN 4 — CONVERSATION */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VynWorldConversation(viewModel: VynWorldViewModel, step: VynWorldStep.Chat) {
    val messages by viewModel.messages.collectAsState()
    val msgsLoading by viewModel.msgsLoading.collectAsState()
    val me = viewModel.myIdentityId

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

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
            .imePadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.backFromChat() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = step.peerName.ifBlank { step.peerPhone },
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = step.peerPhone, fontSize = 12.sp, color = VynTextSecondary)
            }
        }
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
            thickness = 1.dp
        )

        // Messages list
        if (msgsLoading) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (messages.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No messages yet.", fontSize = 13.sp, color = VynTextSecondary)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    VynWorldMessageBubble(msg, myIdentityId = me)
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 1000) input = it },
                placeholder = { Text("Message...", fontSize = 14.sp, color = VynTextSecondary) },
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 4
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        viewModel.sendText(text)
                        input = ""
                    }
                },
                enabled = !viewModel.sending.value && input.trim().isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (input.trim().isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send, "Send",
                    tint = if (input.trim().isNotBlank()) MaterialTheme.colorScheme.onPrimary
                           else VynTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** A single Vyn World message bubble. */
@Composable
private fun VynWorldMessageBubble(msg: VynWorldMessage, myIdentityId: String) {
    val isMine = msg.senderIdentityId == myIdentityId
    val align = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), contentAlignment = align) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 14.dp, topEnd = 14.dp,
                    bottomStart = if (isMine) 14.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 14.dp
                ),
                color = bubbleColor
            ) {
                Text(
                    text = msg.text, fontSize = 14.sp, color = textColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Text(
                text = formatVwTime(msg.createdAtMs), fontSize = 10.sp,
                color = VynTextSecondary, modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

