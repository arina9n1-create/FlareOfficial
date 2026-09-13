package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.LiveChatRoom
import com.example.ui.viewmodel.SocialViewModel
import com.example.util.ChatTranslationEngine
import kotlinx.coroutines.launch

/**
 * -------------------------------------------------------------
 * FLAREOFFICIAL CHAT DETAILS & SETTINGS BOTTOM SHEET
 * -------------------------------------------------------------
 * Accessible via the "i" (info) button on the chat top bar.
 * Features:
 * - Live AI Bidirectional Translation (বাংলা/Banglish ⇄ English)
 * - Chat Themes & Customizations
 * - Disappearing / Vanishing Messages
 * - End-to-End Encryption & Security
 * - Mute & Notification Controls
 * - Clear Chat History & Block Controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlareOfficialChatDetailsSheet(
    room: LiveChatRoom,
    viewModel: SocialViewModel,
    onDismiss: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val roomTranslationSettings by viewModel.roomTranslationSettings.collectAsState()
    val chatTheme by viewModel.chatTheme.collectAsState()
    val disappearingDuration by viewModel.disappearingDuration.collectAsState()

    val currentRoomTranslation = roomTranslationSettings[room.id] ?: com.example.ui.viewmodel.ChatTranslationSettings(outgoingToEnglish = false, incomingToBangla = false)
    val isAnyTranslationOn = currentRoomTranslation.outgoingToEnglish || currentRoomTranslation.incomingToBangla

    var showClearDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var testInputText by remember { mutableStateOf("") }
    var testTranslatedText by remember { mutableStateOf("") }
    var isTestingTranslation by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("ig_chat_details_bottom_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. TOP HEADER & PROFILE CARD
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .border(
                                2.5.dp,
                                Brush.linearGradient(listOf(FlareOfficialDeepPurple, FlareOfficialPink, FlareOfficialYellow)),
                                CircleShape
                            )
                            .padding(4.dp)
                    ) {
                        FlareAvatar(avatarType = room.avatarType, size = 84.dp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = room.title,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (room.verified) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = FlareOfficialBlue,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Text(
                        text = if (room.type == "GLOBAL") "Public Lounge · Community" else "@${room.title.lowercase().replace(" ", "_")} · FlareOfficial",
                        fontSize = 13.sp,
                        color = FlareTextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Action Buttons Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        QuickDetailAction(
                            icon = Icons.Outlined.Call,
                            label = "Audio",
                            onClick = {
                                onDismiss()
                                viewModel.startCall(
                                    room.title,
                                    room.avatarType,
                                    isVideo = false,
                                    partnerHandle = room.id.removePrefix("dm_").takeIf { it != room.id }
                                )
                            }
                        )
                        QuickDetailAction(
                            icon = Icons.Outlined.Videocam,
                            label = "Video",
                            onClick = {
                                onDismiss()
                                viewModel.startCall(
                                    room.title,
                                    room.avatarType,
                                    isVideo = true,
                                    partnerHandle = room.id.removePrefix("dm_").takeIf { it != room.id }
                                )
                            }
                        )
                        QuickDetailAction(
                            icon = Icons.Outlined.Person,
                            label = "Profile",
                            onClick = {
                                onDismiss()
                                viewModel.viewUserProfile(viewModel.directPartnerHandle(room.id))
                            }
                        )
                        QuickDetailAction(
                            icon = if (room.isMuted) Icons.Filled.NotificationsOff else Icons.Outlined.Notifications,
                            label = if (room.isMuted) "Unmute" else "Mute",
                            onClick = {
                                viewModel.toggleMuteChat(room.id)
                                Toast.makeText(context, if (room.isMuted) "Notifications unmuted" else "Chat muted", Toast.LENGTH_SHORT).show()
                            }
                        )
                        QuickDetailAction(
                            icon = if (room.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            label = if (room.isPinned) "Unpin" else "Pin",
                            onClick = {
                                viewModel.togglePinChat(room.id)
                                Toast.makeText(context, if (room.isPinned) "Chat unpinned" else "Chat pinned to top 📌", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // 2. 🌐 AI AUTO-TRANSLATE (PER-CHAT ONLY: বাংলা/BANGLISH ⇄ ENGLISH)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAnyTranslationOn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (isAnyTranslationOn) Brush.linearGradient(listOf(FlareOfficialPurple, FlareOfficialPink, FlareOfficialYellow))
                        else Brush.linearGradient(listOf(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ig_auto_translate_settings_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(
                                            if (isAnyTranslationOn) Brush.linearGradient(listOf(FlareOfficialPurple, FlareOfficialPink))
                                            else Brush.linearGradient(listOf(Color.Gray, Color.DarkGray)),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "Translate",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Chat Translation Settings 🌐",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "শুধুমাত্র ${room.title} এর সাথে চ্যাটে প্রযোজ্য",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isAnyTranslationOn) FlareOfficialPink else FlareTextSecondary
                                    )
                                }
                            }

                            // Quick Reset to Default (OFF) / Toggle All
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isAnyTranslationOn) FlareOfficialPink.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                modifier = Modifier.clickable {
                                    val nextVal = !isAnyTranslationOn
                                    viewModel.toggleRoomTranslation(room.id, nextVal)
                                    Toast.makeText(
                                        context,
                                        if (nextVal) "Translation enabled for this chat" else "Default (No translation) restored",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Text(
                                    text = if (isAnyTranslationOn) "Reset Default" else "Default: OFF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAnyTranslationOn) FlareOfficialPink else FlareTextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "🔒 এই সেটিংসটি শুধুমাত্র এই চ্যাটের জন্যই কাজ করবে। অন্য কোনো চ্যাটে কোনো অনুবাদ হবে না এবং ডিফল্ট টেক্সট থাকবে।",
                            fontSize = 11.5.sp,
                            color = FlareTextSecondary,
                            lineHeight = 16.sp
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // 1. OUTGOING TRANSLATION SWITCH (Bangla/Banglish -> English)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "আমার পাঠানো মেসেজ ➔ English",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "আপনি বাংলা বা বাংলিশে লিখলে এনার কাছে ইংরেজিতে মেসেজ যাবে।",
                                        fontSize = 11.5.sp,
                                        color = FlareTextSecondary,
                                        lineHeight = 15.sp
                                    )
                                }

                                Switch(
                                    checked = currentRoomTranslation.outgoingToEnglish,
                                    onCheckedChange = {
                                        viewModel.setRoomOutgoingTranslation(room.id, it)
                                        Toast.makeText(
                                            context,
                                            if (it) "Outgoing Translation: Bangla ➔ English ON" else "Outgoing Translation: OFF (Default)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.testTag("ig_outgoing_translate_switch")
                                )
                            }
                        }

                        // 2. INCOMING TRANSLATION SWITCH (English -> Bangla)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.SouthWest, contentDescription = null, tint = FlareOfficialBlue, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "অন্যের মেসেজ ➔ বাংলা অনুবাদ",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "এই ব্যক্তি ইংরেজিতে মেসেজ দিলে আপনি বাংলায় দেখতে পাবেন।",
                                        fontSize = 11.5.sp,
                                        color = FlareTextSecondary,
                                        lineHeight = 15.sp
                                    )
                                }

                                Switch(
                                    checked = currentRoomTranslation.incomingToBangla,
                                    onCheckedChange = {
                                        viewModel.setRoomIncomingTranslation(room.id, it)
                                        Toast.makeText(
                                            context,
                                            if (it) "Incoming Translation: English ➔ Bangla ON" else "Incoming Translation: OFF (Default)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.testTag("ig_incoming_translate_switch")
                                )
                            }
                        }

                        // Live Interactive Test Box
                        AnimatedVisibility(visible = isAnyTranslationOn) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🧪 লাইভ টেস্ট করুন (Type Bangla/Banglish):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FlareOfficialPurple)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = testInputText,
                                        onValueChange = {
                                            testInputText = it
                                            coroutineScope.launch {
                                                isTestingTranslation = true
                                                val res = ChatTranslationEngine.translateOutgoingToEnglish(it)
                                                testTranslatedText = res.translatedText
                                                isTestingTranslation = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("e.g. kalke dekha korbo") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("➔ ইংরেজি ফলাফল:", fontSize = 11.sp, color = FlareTextSecondary)
                                    Text(
                                        text = if (isTestingTranslation) "Translating..." else testTranslatedText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E676)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. 🎨 CHAT CUSTOMIZATION & THEME
            item {
                SectionCard(title = "Chat Customization") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Theme / Gradient", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Select gradient for your message bubbles", fontSize = 12.sp, color = FlareTextSecondary)
                            }
                            Text(
                                text = chatTheme,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlareOfficialPink
                            )
                        }

                        // Theme Selection Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Classic FlareOfficial" to listOf(FlareOfficialPurple, FlareOfficialPink),
                                "Cyber Glow" to listOf(Color(0xFF6C5CE7), Color(0xFF00CEC9)),
                                "Sunset Peach" to listOf(Color(0xFFFF7675), Color(0xFFFAB1A0)),
                                "Emerald Mint" to listOf(Color(0xFF00B894), Color(0xFF55EFC4))
                            ).forEach { (tName, colors) ->
                                val isSelected = chatTheme == tName
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Brush.linearGradient(colors))
                                        .border(
                                            if (isSelected) 2.5.dp else 0.dp,
                                            if (isSelected) Color.White else Color.Transparent,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            viewModel.setChatTheme(tName)
                                            Toast.makeText(context, "Theme set to $tName", Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Default Quick Reaction Emoji
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Quick Reaction Emoji", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Double tap reaction for messages", fontSize = 12.sp, color = FlareTextSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("❤️", "🔥", "😂", "👏").forEach { emoji ->
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clickable {
                                                Toast.makeText(context, "Default reaction set to $emoji", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(emoji, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. 🔒 PRIVACY, SAFETY & VANISHING MODE
            item {
                SectionCard(title = "Privacy & Safety") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Vanishing Mode / Disappearing Messages
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Disappearing Messages", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("New messages disappear after chosen time", fontSize = 12.sp, color = FlareTextSecondary)
                                }
                            }
                            TextButton(onClick = {
                                val next = when (disappearingDuration) {
                                    "Off" -> "24 Hours"
                                    "24 Hours" -> "7 Days"
                                    else -> "Off"
                                }
                                viewModel.setDisappearingDuration(next)
                                Toast.makeText(context, "Disappearing messages: $next", Toast.LENGTH_SHORT).show()
                            }) {
                                Text(disappearingDuration, fontWeight = FontWeight.Bold, color = FlareOfficialBlue)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // End-to-End Encryption
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null, tint = Color(0xFF00E676))
                            Column {
                                Text("End-to-End Encrypted", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Messages and calls are secured with 256-bit AES encryption.", fontSize = 12.sp, color = FlareTextSecondary)
                            }
                        }
                    }
                }
            }

            // 5. 📁 SHARED MEDIA & LINKS
            item {
                SectionCard(title = "Shared Media & Files") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Photos & Videos", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("See all", fontSize = 12.sp, color = FlareOfficialBlue, modifier = Modifier.clickable {})
                        }
                        Text("No shared media yet.", fontSize = 13.sp, color = FlareTextSecondary)
                    }
                }
            }

            // 6. ⚠️ MANAGEMENT & DANGER ZONE
            item {
                SectionCard(title = "Manage Conversation") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Clear Chat
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showClearDialog = true }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            Text("Clear Chat History", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Block User
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBlockDialog = true }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Outlined.Block, contentDescription = null, tint = Color(0xFFFF5252))
                            Text("Block ${room.title}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFF5252))
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Report Chat
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Toast.makeText(context, "Thank you. Conversation reported to moderation team.", Toast.LENGTH_LONG).show()
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Outlined.ReportProblem, contentDescription = null, tint = Color(0xFFFF5252))
                            Text("Report Scam or Problem", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFF5252))
                        }
                    }
                }
            }
        }
    }

    // Clear Chat Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Chat History?") },
            text = { Text("This will permanently delete all messages and media from this conversation.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearCurrentRoomMessages()
                        showClearDialog = false
                        Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Block Confirmation Dialog
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Block ${room.title}?") },
            text = { Text("They won't be able to send you messages or find your profile on FlareOfficial.") },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockDialog = false
                        onDismiss()
                        val partner = viewModel.directPartnerHandle(room.id)
                        if (partner.isNotBlank()) {
                            viewModel.blockUserByHandle(partner)
                        }
                        Toast.makeText(context, "${room.title} has been blocked", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = FlareTextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun QuickDetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
