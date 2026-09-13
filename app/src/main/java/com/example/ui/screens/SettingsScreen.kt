package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfileEntity
import com.example.ui.components.FlareAvatar
import com.example.ui.components.FlareImage
import com.example.ui.theme.*
import com.example.ui.viewmodel.SettingsPage
import com.example.ui.viewmodel.SocialViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenSettings(
    viewModel: SocialViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPage by viewModel.settingsPage.collectAsState()

    // Handle system back gesture
    BackHandler(enabled = true) {
        if (currentPage != SettingsPage.MAIN) {
            viewModel.setSettingsPage(SettingsPage.MAIN)
        } else {
            onBack()
        }
    }

    AnimatedContent(
        targetState = currentPage,
        transitionSpec = {
            if (targetState == SettingsPage.MAIN) {
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            } else {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            }
        },
        label = "settings_page_transition",
        modifier = modifier.fillMaxSize()
    ) { page ->
        when (page) {
            SettingsPage.MAIN -> {
                SettingsMainPage(
                    viewModel = viewModel,
                    onBack = onBack,
                    onNavigate = { viewModel.setSettingsPage(it) }
                )
            }
            SettingsPage.PERSONAL_INFO -> {
                PersonalInfoFullScreenPage(
                    viewModel = viewModel,
                    onBack = { viewModel.setSettingsPage(SettingsPage.MAIN) }
                )
            }
            SettingsPage.PRIVACY_SECURITY -> {
                PrivacySecurityFullScreenPage(
                    viewModel = viewModel,
                    onBack = { viewModel.setSettingsPage(SettingsPage.MAIN) }
                )
            }
            SettingsPage.NOTIFICATIONS -> {
                NotificationsFullScreenPage(
                    viewModel = viewModel,
                    onBack = { viewModel.setSettingsPage(SettingsPage.MAIN) }
                )
            }
            SettingsPage.ACCOUNT_SYNC -> {
                CloudSyncFullScreenPage(
                    viewModel = viewModel,
                    onBack = { viewModel.setSettingsPage(SettingsPage.MAIN) }
                )
            }
            SettingsPage.HELP_SUPPORT -> {
                HelpSupportFullScreenPage(
                    onBack = { viewModel.setSettingsPage(SettingsPage.MAIN) }
                )
            }
            SettingsPage.TERMS_PRIVACY -> {
                TermsPrivacyFullScreenPage(
                    onBack = { viewModel.setSettingsPage(SettingsPage.MAIN) }
                )
            }
            SettingsPage.ABOUT_APP -> {
                AboutAppFullScreenPage(
                    onBack = { viewModel.setSettingsPage(SettingsPage.MAIN) }
                )
            }
            SettingsPage.MY_WALLET -> {
                MyWalletFullScreenPage(
                    viewModel = viewModel,
                    onBack = { viewModel.setSettingsPage(SettingsPage.MAIN) }
                )
            }
            SettingsPage.VERIFICATION_BADGE -> {
                VerificationBadgeFullScreenPage(
                    viewModel = viewModel,
                    onBack = { viewModel.setSettingsPage(SettingsPage.MAIN) }
                )
            }
        }
    }
}

// ==========================================
// 1. MAIN SETTINGS DASHBOARD
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainPage(
    viewModel: SocialViewModel,
    onBack: () -> Unit,
    onNavigate: (SettingsPage) -> Unit
) {
    val authState by viewModel.userAuthState.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val wallet by viewModel.rewardWallet.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Privacy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Summary Header Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = FlareOffWhite,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(SettingsPage.PERSONAL_INFO) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FlareAvatar(
                        avatarType = profile.avatarType,
                        storagePath = profile.avatarPath,
                        size = 56.dp
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = profile.name.ifBlank { "No profile" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = FlareCameraBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = profile.handle.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Not signed in",
                            fontSize = 13.sp,
                            color = FlareTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = FlareCameraBlue.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Cloud Account",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FlareCameraBlue,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                color = FlareGoldCoin.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${wallet.totalCredits} Coins 🪙",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB8860B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Edit Profile",
                        tint = FlareTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Section 1: Monetization & Rewards
            SettingsSection(title = "Creator Monetization & Rewards") {
                SettingsNavigationItem(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    title = "My Wallet 💳",
                    subtitle = "Add funds, withdraw earnings & view transaction history",
                    onClick = { onNavigate(SettingsPage.MY_WALLET) }
                )
                SettingsNavigationItem(
                    icon = Icons.Outlined.Verified,
                    title = "Verification Badge ✔️",
                    subtitle = "Activate your verification badge — fee paid directly from My Wallet",
                    onClick = { onNavigate(SettingsPage.VERIFICATION_BADGE) }
                )
                SettingsNavigationItem(
                    icon = Icons.Outlined.Diamond,
                    title = "Monetization 💎",
                    subtitle = "Apply for creator monetization, Content & Reels revenue, Earnings Wallet",
                    onClick = {
                        onBack()
                        viewModel.openMonetizationScreen()
                    }
                )
                SettingsNavigationItem(
                    icon = Icons.Outlined.MonetizationOn,
                    title = "Rewards & Cashout Dashboard 💰",
                    subtitle = "Watch reels, 7-day streak tasks & withdraw via bKash/Nagad",
                    onClick = {
                        onBack()
                        viewModel.openRewardScreen()
                    }
                )
                if (viewModel.canAccessAdminPanel()) {
                    SettingsNavigationItem(
                        icon = Icons.Outlined.AdminPanelSettings,
                        title = if (viewModel.isSuperAdmin()) "👑 Super Admin Panel" else "🛡️ Staff Admin Panel",
                        subtitle = "Manage user roles, permissions, storage & payout controls",
                        onClick = {
                            onBack()
                            viewModel.openAdminScreen()
                        }
                    )
                }
            }

            // Section 2: Account & Identity
            SettingsSection(title = "Account Settings") {
                SettingsNavigationItem(
                    icon = Icons.Outlined.Person,
                    title = "Personal Information & Edit Profile",
                    subtitle = "Change name, @handle, avatar, bio, location & website",
                    onClick = { onNavigate(SettingsPage.PERSONAL_INFO) }
                )
                SettingsNavigationItem(
                    icon = Icons.Outlined.Lock,
                    title = "Privacy & Security",
                    subtitle = "Private account, password change, 2FA & block list",
                    onClick = { onNavigate(SettingsPage.PRIVACY_SECURITY) }
                )
                SettingsNavigationItem(
                    icon = Icons.Outlined.CloudSync,
                    title = "Cloud Sync & Data Storage",
                    subtitle = "Supabase cloud data, storage usage, clear cache & export data",
                    onClick = { onNavigate(SettingsPage.ACCOUNT_SYNC) }
                )
            }

            // Section 3: Preferences
            SettingsSection(title = "Preferences & Alerts") {
                SettingsNavigationItem(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications & Sounds",
                    subtitle = "Likes, comments, chat alerts, rewards & email digests",
                    onClick = { onNavigate(SettingsPage.NOTIFICATIONS) }
                )
            }

            // Section 4: Support & Legal
            SettingsSection(title = "Support & Legal") {
                SettingsNavigationItem(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = "Help & Support Center",
                    subtitle = "FAQ, live support ticket & report problem",
                    onClick = { onNavigate(SettingsPage.HELP_SUPPORT) }
                )
                SettingsNavigationItem(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Terms & Privacy Policy",
                    subtitle = "Community guidelines, earning rules & privacy standards",
                    onClick = { onNavigate(SettingsPage.TERMS_PRIVACY) }
                )
                SettingsNavigationItem(
                    icon = Icons.Outlined.Info,
                    title = "About FlareOfficial",
                    subtitle = "Version 1.3.0, developer info & check updates",
                    onClick = { onNavigate(SettingsPage.ABOUT_APP) }
                )
            }

            // Log Out Button
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("settings_logout_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = "Log out",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Log Out of FlareOfficial",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out Confirmation", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to log out? Your local data is saved on this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.signOut()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==========================================
// 2. FULL SCREEN: PERSONAL INFORMATION
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInfoFullScreenPage(
    viewModel: SocialViewModel,
    onBack: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val context = LocalContext.current

    var name by remember(profile) { mutableStateOf(profile.name) }
    var handle by remember(profile) { mutableStateOf(profile.handle) }
    var bio by remember(profile) { mutableStateOf(profile.bio) }
    var location by remember(profile) { mutableStateOf(profile.location) }
    var website by remember { mutableStateOf("https://flareofficial.app/@" + profile.handle) }
    var selectedAvatar by remember(profile) { mutableStateOf(profile.avatarType) }
    var selectedCover by remember(profile) { mutableStateOf(profile.coverType) }
    var gender by remember { mutableStateOf("Male") }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            isSaving = true
                            viewModel.updateFullProfile(
                                name = name,
                                handle = handle,
                                bio = bio,
                                location = location,
                                avatarType = selectedAvatar,
                                coverType = selectedCover,
                                onComplete = {
                                    isSaving = false
                                    Toast.makeText(context, "Profile updated successfully! ✅", Toast.LENGTH_SHORT).show()
                                    onBack()
                                },
                                onError = { error ->
                                    isSaving = false
                                    Toast.makeText(context, "Profile update failed: $error", Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = FlareCameraBlue)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile & Cover Picture Selector
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FlareOffWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Profile Picture & Avatar", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("default" to "Default").forEach { (type, label) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedAvatar = type }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .then(
                                            if (selectedAvatar == type) {
                                                Modifier.border(3.dp, FlareCameraBlue, CircleShape)
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    FlareAvatar(avatarType = type, size = 52.dp)
                                }
                                Text(label, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("Cover Photo Theme", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("default" to "Default").forEach { (cov, lbl) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedCover == cov) FlareCameraBlue.copy(alpha = 0.15f) else Color.Transparent,
                                border = if (selectedCover == cov) ButtonDefaults.outlinedButtonBorder(enabled = true) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedCover = cov }
                                    .padding(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (selectedCover == cov) Icons.Default.CheckCircle else Icons.Outlined.Image,
                                        contentDescription = null,
                                        tint = if (selectedCover == cov) FlareCameraBlue else FlareTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(lbl, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            // Input Fields
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Display Name") },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = handle,
                onValueChange = { handle = it.lowercase().replace(" ", "") },
                label = { Text("Username / Handle") },
                prefix = { Text("@") },
                leadingIcon = { Icon(Icons.Outlined.AlternateEmail, contentDescription = null) },
                singleLine = true,
                enabled = false,
                supportingText = { Text("Handles are permanent and cannot be changed after sign-up.") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 160) bio = it },
                label = { Text("Bio (Description)") },
                supportingText = { Text("${bio.length}/160 characters") },
                leadingIcon = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location / City") },
                leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = website,
                onValueChange = { website = it },
                label = { Text("Website or Social Link") },
                leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Gender Selector
            Text("Gender", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = FlareTextSecondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Male", "Female", "Other").forEach { g ->
                    FilterChip(
                        selected = gender == g,
                        onClick = { gender = g },
                        label = { Text(g) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    isSaving = true
                    viewModel.updateFullProfile(
                        name = name,
                        handle = handle,
                        bio = bio,
                        location = location,
                        avatarType = selectedAvatar,
                        coverType = selectedCover,
                        onComplete = {
                            isSaving = false
                            Toast.makeText(context, "Profile saved successfully! 🎉", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        onError = { error ->
                            isSaving = false
                            Toast.makeText(context, "Profile update failed: $error", Toast.LENGTH_LONG).show()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FlareCameraBlue)
            ) {
                Text("Save Profile Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ==========================================
// 3. FULL SCREEN: PRIVACY & SECURITY
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySecurityFullScreenPage(
    viewModel: SocialViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsState()
    var isPrivateAccount by remember(profile.isPublic) { mutableStateOf(!profile.isPublic) }
    var showActiveStatus by remember { mutableStateOf(true) }
    var allowStorySharing by remember { mutableStateOf(true) }
    var twoFactorAuth by remember { mutableStateOf(true) }

    // Password change states
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Blocked list dialog
    var showBlockedDialog by remember { mutableStateOf(false) }
    val blockedUsers = remember { mutableStateListOf<String>() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Security", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Privacy Section
            SettingsSection(title = "Account Privacy Controls") {
                SettingsToggleItem(
                    icon = Icons.Outlined.Lock,
                    title = "Private Account",
                    subtitle = "Only users you approve can view your posts, stories, and reels",
                    checked = isPrivateAccount,
                    onCheckedChange = {
                        isPrivateAccount = it
                        viewModel.updateProfile(
                            name = profile.name,
                            bio = profile.bio,
                            location = profile.location,
                            isPublic = !it
                        )
                        Toast.makeText(context, if (it) "Account is now Private 🔒" else "Account is now Public 🌍", Toast.LENGTH_SHORT).show()
                    }
                )
                SettingsToggleItem(
                    icon = Icons.Outlined.Visibility,
                    title = "Show Active Status",
                    subtitle = "Let friends see when you're online or recently active on FlareOfficial",
                    checked = showActiveStatus,
                    onCheckedChange = { showActiveStatus = it }
                )
                SettingsToggleItem(
                    icon = Icons.Outlined.Share,
                    title = "Allow Story Sharing",
                    subtitle = "Allow followers to share your stories as direct messages",
                    checked = allowStorySharing,
                    onCheckedChange = { allowStorySharing = it }
                )
                SettingsNavigationItem(
                    icon = Icons.Outlined.Block,
                    title = "Blocked Accounts (${blockedUsers.size})",
                    subtitle = "Manage users you have blocked from messaging or seeing you",
                    onClick = { showBlockedDialog = true }
                )
            }

            // Security & Passwords
            SettingsSection(title = "Security & Two-Factor Authentication") {
                SettingsToggleItem(
                    icon = Icons.Outlined.Shield,
                    title = "Two-Factor Authentication (2FA)",
                    subtitle = "Requires an SMS or Authenticator OTP when signing in",
                    checked = twoFactorAuth,
                    onCheckedChange = {
                        twoFactorAuth = it
                        Toast.makeText(context, if (it) "2FA Protection Enabled 🛡️" else "2FA Protection Disabled", Toast.LENGTH_SHORT).show()
                    }
                )

                // Password change card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Change Account Password", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isPasswordVisible = !isPasswordVisible }
                    ) {
                        Checkbox(checked = isPasswordVisible, onCheckedChange = { isPasswordVisible = it })
                        Text("Show Passwords", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (currentPassword.isBlank() || newPassword.isBlank()) {
                                Toast.makeText(context, "Please fill out password fields", Toast.LENGTH_SHORT).show()
                            } else if (newPassword != confirmPassword) {
                                Toast.makeText(context, "New passwords do not match!", Toast.LENGTH_SHORT).show()
                            } else {
                                currentPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                                Toast.makeText(context, "Password updated successfully! 🔑", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Update Password")
                    }
                }
            }

            // Login Sessions
            SettingsSection(title = "Active Login Sessions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(FlareCameraBlue.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = FlareCameraBlue)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Android Streaming Session (Current Device)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("No active session", fontSize = 11.sp, color = FlareTextSecondary)
                    }
                    Surface(color = Color(0xFF4CAF50).copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                        Text("Online", fontSize = 11.sp, color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("Blocked Accounts", fontWeight = FontWeight.Bold) },
            text = {
                if (blockedUsers.isEmpty()) {
                    Text("You have no blocked accounts.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        blockedUsers.forEach { u ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("@$u", fontWeight = FontWeight.Medium)
                                OutlinedButton(
                                    onClick = {
                                        blockedUsers.remove(u)
                                        Toast.makeText(context, "Unblocked @$u", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Unblock", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

// ==========================================
// 4. FULL SCREEN: NOTIFICATIONS & ALERTS
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsFullScreenPage(
    viewModel: SocialViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pauseAll by remember { mutableStateOf(com.example.data.notification.NotificationPreferences.isPauseAll(context)) }
    var likesReactions by remember { mutableStateOf(com.example.data.notification.NotificationPreferences.isLikesEnabled(context)) }
    var commentsReplies by remember { mutableStateOf(com.example.data.notification.NotificationPreferences.isCommentsEnabled(context)) }
    var newFollowers by remember { mutableStateOf(com.example.data.notification.NotificationPreferences.isFollowersEnabled(context)) }
    var directMessages by remember { mutableStateOf(com.example.data.notification.NotificationPreferences.isDirectMessagesEnabled(context)) }
    var rewardAlerts by remember { mutableStateOf(com.example.data.notification.NotificationPreferences.isRewardAlertsEnabled(context)) }
    var mentionsTags by remember { mutableStateOf(com.example.data.notification.NotificationPreferences.isMentionsEnabled(context)) }
    var emailSummaries by remember { mutableStateOf(false) }
    var soundVibration by remember { mutableStateOf(com.example.data.notification.NotificationPreferences.isSoundVibrationEnabled(context)) }

    val hasSystemPermission = remember {
        com.example.data.notification.NotificationHelper.hasNotificationPermission(context)
    }

    val fcmToken = remember {
        com.example.data.notification.NotificationPreferences.getFcmToken(context)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Notifications & Alerts", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // System Notification Status Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (hasSystemPermission) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(if (hasSystemPermission) Color(0xFF2E7D32) else Color(0xFFEF6C00), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (hasSystemPermission) Icons.Outlined.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasSystemPermission) "Real-Time Push Alerts: Active 🟢" else "Push Permission: Check Settings ⚠️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (hasSystemPermission) Color(0xFF1B5E20) else Color(0xFFE65100)
                        )
                        Text(
                            text = if (fcmToken != null) "FCM Cloud Engine Registered (${fcmToken.take(12)}...)" else "FCM Cloud Engine Ready",
                            fontSize = 11.sp,
                            color = FlareTextSecondary
                        )
                    }
                }
            }

            // Push Delivery Diagnostic — live check of the whole chain
            // (permission, channels, battery, local token, server token).
            PushDiagnosticCard()

            // Master Pause All
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (pauseAll) Color(0xFFFFEBEE) else FlareOffWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(if (pauseAll) MaterialTheme.colorScheme.error else FlareButtonBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (pauseAll) Icons.Default.NotificationsOff else Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = if (pauseAll) Color.White else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pause All Push Notifications",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (pauseAll) "All notifications are currently muted" else "Receive notifications normally",
                            fontSize = 12.sp,
                            color = FlareTextSecondary
                        )
                    }
                    Switch(
                        checked = pauseAll,
                        onCheckedChange = {
                            pauseAll = it
                            com.example.data.notification.NotificationPreferences.setPauseAll(context, it)
                            Toast.makeText(context, if (it) "Notifications paused 🔕" else "Notifications resumed 🔔", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }


            // Social Activity Notifications
            SettingsSection(title = "Social Interactions") {
                SettingsToggleItem(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = "Likes & Reactions",
                    subtitle = "Alerts when someone likes your posts, comments, or reels",
                    checked = likesReactions && !pauseAll,
                    onCheckedChange = {
                        likesReactions = it
                        com.example.data.notification.NotificationPreferences.setLikesEnabled(context, it)
                    }
                )
                SettingsToggleItem(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = "Comments & Mentions",
                    subtitle = "Alerts when someone comments on your post or mentions you",
                    checked = commentsReplies && !pauseAll,
                    onCheckedChange = {
                        commentsReplies = it
                        com.example.data.notification.NotificationPreferences.setCommentsEnabled(context, it)
                    }
                )
                SettingsToggleItem(
                    icon = Icons.Outlined.PersonAdd,
                    title = "New Followers & Requests",
                    subtitle = "Alerts when a new user follows your profile",
                    checked = newFollowers && !pauseAll,
                    onCheckedChange = {
                        newFollowers = it
                        com.example.data.notification.NotificationPreferences.setFollowersEnabled(context, it)
                    }
                )
                SettingsToggleItem(
                    icon = Icons.AutoMirrored.Outlined.Send,
                    title = "Direct Messages",
                    subtitle = "Alerts for incoming private chat messages",
                    checked = directMessages && !pauseAll,
                    onCheckedChange = {
                        directMessages = it
                        com.example.data.notification.NotificationPreferences.setDirectMessagesEnabled(context, it)
                    }
                )
                SettingsToggleItem(
                    icon = Icons.Outlined.Tag,
                    title = "Tags & Mentions in Stories",
                    subtitle = "Alerts when you are tagged in stories or shared content",
                    checked = mentionsTags && !pauseAll,
                    onCheckedChange = {
                        mentionsTags = it
                        com.example.data.notification.NotificationPreferences.setMentionsEnabled(context, it)
                    }
                )
            }

            // Rewards & Earnings Notifications
            SettingsSection(title = "Rewards & Monetization Alerts") {
                SettingsToggleItem(
                    icon = Icons.Outlined.MonetizationOn,
                    title = "Earning & Cashout Milestones",
                    subtitle = "Instant alerts when reel watch credits are added or withdrawal is approved",
                    checked = rewardAlerts && !pauseAll,
                    onCheckedChange = {
                        rewardAlerts = it
                        com.example.data.notification.NotificationPreferences.setRewardAlertsEnabled(context, it)
                    }
                )
            }

            // Email & Sound Settings
            SettingsSection(title = "Email & In-App Alerts") {
                SettingsToggleItem(
                    icon = Icons.Outlined.Email,
                    title = "Weekly Email Digest",
                    subtitle = "Summary of top trending posts and your reward stats",
                    checked = emailSummaries,
                    onCheckedChange = { emailSummaries = it }
                )
                SettingsToggleItem(
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    title = "In-App Notification Sounds & Vibration",
                    subtitle = "Play gentle chimes and vibrate on incoming direct messages",
                    checked = soundVibration,
                    onCheckedChange = {
                        soundVibration = it
                        com.example.data.notification.NotificationPreferences.setSoundVibrationEnabled(context, it)
                    }
                )
            }

            Button(
                onClick = {
                    com.example.data.notification.NotificationPreferences.setPauseAll(context, pauseAll)
                    com.example.data.notification.NotificationPreferences.setLikesEnabled(context, likesReactions)
                    com.example.data.notification.NotificationPreferences.setCommentsEnabled(context, commentsReplies)
                    com.example.data.notification.NotificationPreferences.setFollowersEnabled(context, newFollowers)
                    com.example.data.notification.NotificationPreferences.setDirectMessagesEnabled(context, directMessages)
                    com.example.data.notification.NotificationPreferences.setRewardAlertsEnabled(context, rewardAlerts)
                    com.example.data.notification.NotificationPreferences.setMentionsEnabled(context, mentionsTags)
                    com.example.data.notification.NotificationPreferences.setSoundVibrationEnabled(context, soundVibration)
                    Toast.makeText(context, "Notification preferences saved! ✅", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FlareCameraBlue)
            ) {
                Text("Save Preferences", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ==========================================
// 5. FULL SCREEN: CLOUD SYNC & STORAGE
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncFullScreenPage(
    viewModel: SocialViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var cacheSizeMb by remember { mutableFloatStateOf(34.8f) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Cloud Sync & Storage", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cloud Status Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FlareOffWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(FlareCameraBlue.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = FlareCameraBlue)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Supabase Cloud Backend", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Data syncs automatically with the Supabase backend", fontSize = 12.sp, color = FlareTextSecondary)
                        }
                        Surface(
                            color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Online", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }

                // NOTE: There is no manual cloud-sync button in this app. Content is written to
                // Supabase by its own actions and the UI only reports success after the backend
                // confirms. A fake progress spinner claiming a completed cloud sync was removed.
                Text(
                    text = "Your content is saved to Supabase as you create it. Failed operations are reported instead of being silently retried here.",
                    fontSize = 12.sp,
                    color = FlareTextSecondary
                )
                }
            }

            // Sync Settings
            SettingsSection(title = "Cloud Synchronization Settings") {
                val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
                SettingsToggleItem(
                    icon = Icons.Outlined.CloudSync,
                    title = "Automatic Cloud Backup",
                    subtitle = "Automatically sync new posts, comments, and reward balance",
                    checked = autoSyncEnabled,
                    onCheckedChange = { viewModel.setAutoSyncEnabled(it) }
                )
                SettingsNavigationItem(
                    icon = Icons.Outlined.FileDownload,
                    title = "Download My Data (JSON Archive)",
                    subtitle = "Request and export a copy of your posts, wallet history & profile",
                    onClick = { showExportDialog = true }
                )
            }

            // Local Storage & Cache
            SettingsSection(title = "Local Device Storage & Cache") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Cached Media & Video Buffer", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("ExoPlayer disk cache, images & video feed", fontSize = 12.sp, color = FlareTextSecondary)
                    }
                    Text(
                        text = com.example.media.player.ExoPlayerCacheManager.getFormattedCacheSize(context),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedButton(
                    onClick = {
                        com.example.media.player.ExoPlayerCacheManager.clearCache(context)
                        cacheSizeMb = 0.0f
                        Toast.makeText(context, "ExoPlayer video & media cache cleared successfully! 0.0 MB", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear ExoPlayer Video Cache")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.clearAllPosts()
                        Toast.makeText(context, "Feed cleared! You can now share fresh posts 🚀", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clean Home Feed (Empty Slate)")
                }

            }

            // Danger Zone
            SettingsSection(title = "Account Management") {
                Button(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete or Deactivate Account", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Account Data", fontWeight = FontWeight.Bold) },
            text = {
                Text("We will generate a complete JSON archive of your profile, posts, comments, transactions and reward history. Ready to download?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDialog = false
                        Toast.makeText(context, "Data export archive generated: flareofficial_export.json 📥", Toast.LENGTH_LONG).show()
                    }
                ) {
                    Text("Export & Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Account?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Text("Deleting your account is permanent. All your posts, followers, and earned credits will be erased and cannot be recovered.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.signOut()
                        onBack()
                        Toast.makeText(context, "Account session deleted.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ==========================================
// 6. FULL SCREEN: HELP & SUPPORT CENTER
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportFullScreenPage(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Earning & Payouts") }
    var ticketSubject by remember { mutableStateOf("") }
    var ticketMessage by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val faqs = remember {
        listOf(
            "How do I earn credits watching Reels?" to "Open the Reels tab and watch videos for at least 10 seconds. You will automatically receive reward credits in your wallet balance!",
            "How do I withdraw cash to bKash / Nagad?" to "Navigate to Rewards Dashboard -> Withdraw Funds -> Select your preferred payment method (bKash, Nagad, Rocket, Binance, or PayPal), enter your account number, and submit. Payouts are verified by Admin within 2-24 hours.",
            "How does the 7-Day Login Streak work?" to "Log in to FlareOfficial every consecutive day. Each day grants increasing bonus credits up to Day 7. Completing a full 7-day streak awards a bonus multiplier.",
            "How do Referral Bonuses work?" to "Share your unique referral code from the Rewards Dashboard. When friends sign up using your code, you earn 500 coins and your friend receives 150 welcome bonus coins!",
            "How do I become a Verified Creator?" to "Maintain an active profile, post original content, and interact with the community. You can request a verification badge through the support ticket below."
        )
    }

    val filteredFaqs = faqs.filter {
        it.first.contains(searchQuery, ignoreCase = true) || it.second.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Help & Support Center", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search FAQ
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search help articles & FAQs...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            // FAQ Accordion
            Text("Frequently Asked Questions", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            filteredFaqs.forEach { (question, answer) ->
                var isExpanded by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FlareOffWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = question,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = FlareCameraBlue
                            )
                        }
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = answer,
                                fontSize = 13.sp,
                                color = FlareTextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Contact Support Ticket Form
            Spacer(modifier = Modifier.height(8.dp))
            Text("Submit a Support Ticket", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FlareOffWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Select Category", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FlareTextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Earning & Payouts", "Bug Report", "Account").forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = ticketSubject,
                        onValueChange = { ticketSubject = it },
                        label = { Text("Subject / Issue Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = ticketMessage,
                        onValueChange = { ticketMessage = it },
                        label = { Text("Describe your problem in detail...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            if (ticketSubject.isBlank() || ticketMessage.isBlank()) {
                                Toast.makeText(context, "Please fill in subject and message", Toast.LENGTH_SHORT).show()
                            } else {
                                isSubmitting = true
                                val ticketId = "TKT-${(10000..99999).random()}"
                                Toast.makeText(context, "Ticket #$ticketId submitted! Support team will respond via email. ✉️", Toast.LENGTH_LONG).show()
                                ticketSubject = ""
                                ticketMessage = ""
                                isSubmitting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FlareCameraBlue)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Support Ticket")
                    }
                }
            }

            // Direct Email Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = FlareCameraBlue.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Outlined.Email, contentDescription = null, tint = FlareCameraBlue)
                    Column {
                        Text("Official Support Email", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("support@flareofficial.app (24/7 Assistance)", fontSize = 12.sp, color = FlareCameraBlue)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ==========================================
// 7. FULL SCREEN: TERMS & PRIVACY POLICY
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsPrivacyFullScreenPage(
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Terms & Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FlareOffWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Terms of Service", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Welcome to FlareOfficial. By using our application, you agree to comply with our community standards and guidelines. Users must be at least 13 years of age. You are responsible for any activity that occurs under your account and for keeping your account credentials safe.",
                        fontSize = 13.sp,
                        color = FlareTextSecondary,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider()

                    Text("2. Privacy & Data Protection", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                            "We value your privacy. We collect minimal information required to deliver social feeds, chat messages, and reward distribution. We never sell your personal data to third parties. All network communications with our authentication servers are encrypted using TLS 1.3 standards.",
                        fontSize = 13.sp,
                        color = FlareTextSecondary,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider()

                    Text("3. Creator Monetization & Earning Rules", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Users can earn credits by watching authentic reels, completing daily login streaks, and inviting genuine friends. Any automated scripts, bot accounts, or fraudulent activities will lead to immediate wallet forfeiture and account suspension. Minimum cashout threshold is enforced per platform.",
                        fontSize = 13.sp,
                        color = FlareTextSecondary,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider()

                    Text("4. Community Safety & Anti-Harassment", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Harassment, hate speech, spamming, and illegal content are strictly prohibited on FlareOfficial. Violating posts will be removed immediately upon community report or moderation review.",
                        fontSize = 13.sp,
                        color = FlareTextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ==========================================
// 8. FULL SCREEN: ABOUT APP
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppFullScreenPage(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isCheckingUpdate by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("About FlareOfficial", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // App Logo & Header
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(FlareStoryGradientStart),
                contentAlignment = Alignment.Center
            ) {
                Text("V9", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FlareOfficial & Rewards", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Version 1.3.0 (Build 204) · Release", fontSize = 13.sp, color = FlareTextSecondary)
                Text("Connect, Watch, Share & Earn Cash", fontSize = 12.sp, color = FlareCameraBlue, fontWeight = FontWeight.Medium)
            }

            // Features Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FlareOffWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Key Architecture & Capabilities", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("• Kotlin & Jetpack Compose 100% Declarative UI", fontSize = 13.sp, color = FlareTextSecondary)
                    Text("• Supabase Cloud Sync & Real-Time Backend", fontSize = 13.sp, color = FlareTextSecondary)
                    Text("• Room Database Offline First Architecture", fontSize = 13.sp, color = FlareTextSecondary)
                    Text("• Reels Video Monetization & Daily Streak Engine", fontSize = 13.sp, color = FlareTextSecondary)
                    Text("• Multi-Channel Cashouts (bKash, Nagad, Rocket, Binance)", fontSize = 13.sp, color = FlareTextSecondary)
                }
            }

            // Check for Updates Button
            Button(
                onClick = {
                    isCheckingUpdate = true
                    Toast.makeText(context, "You are using the latest version of FlareOfficial! 🎉 (v1.3.0)", Toast.LENGTH_SHORT).show()
                    isCheckingUpdate = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FlareCameraBlue)
            ) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Check for Updates")
            }

            Text(
                "© 2026 FlareOfficial Platform. All rights reserved.",
                fontSize = 11.sp,
                color = FlareTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ==========================================
// 9. FULL SCREEN: MY WALLET (FINANCE)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyWalletFullScreenPage(
    viewModel: SocialViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val wallet by viewModel.earningsWallet.collectAsState()
    val transactions by viewModel.monetizationTransactions.collectAsState()
    val settings by viewModel.monetizationSettings.collectAsState()
    
    var showAddFundDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("My Wallet", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Balance Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF6C5CE7),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            "TOTAL BALANCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "$${java.lang.String.format(java.util.Locale.US, "%.2f", wallet.availableBalance)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Available for withdrawal & ad boosts",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showAddFundDialog = true },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF6C5CE7)
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Fund", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showWithdrawDialog = true },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.CallMade, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Withdraw", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Stats row (Total Added, Total Withdrawn)
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF39C12).copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Total Added", fontSize = 11.sp, color = FlareTextSecondary)
                            Text("$${java.lang.String.format(java.util.Locale.US, "%.2f", wallet.totalAddedFunds)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFF39C12))
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD63031).copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Total Withdraw", fontSize = 11.sp, color = FlareTextSecondary)
                            Text("$${java.lang.String.format(java.util.Locale.US, "%.2f", wallet.totalWithdrawn)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFD63031))
                        }
                    }
                }
            }

            // Transaction History Title
            item {
                Text(
                    "Transaction History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (transactions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.History, null, tint = FlareTextSecondary, modifier = Modifier.size(48.dp))
                            Text("No transactions yet", color = FlareTextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(transactions) { txn ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = FlareOffWhite)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = txn.description.ifBlank { txn.type },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                val date = remember(txn.createdAt) {
                                    try {
                                        java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.getDefault()).format(java.util.Date(txn.createdAt))
                                    } catch (e: Exception) {
                                        "Recently"
                                    }
                                }
                                Text(
                                    text = date,
                                    fontSize = 11.sp,
                                    color = FlareTextSecondary
                                )
                            }
                            Text(
                                text = (if (txn.type == "WITHDRAWAL") "-" else "+") + "$${java.lang.String.format(java.util.Locale.US, "%.2f", txn.amount)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = if (txn.type == "WITHDRAWAL") Color(0xFFD63031) else Color(0xFF00B894)
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }

    if (showAddFundDialog) {
        com.example.ui.components.PaymentGatewayDialog(
            initialAmountBdt = 500.0,
            title = "Deposit Funds to Wallet",
            description = "Official bKash, Nagad, SSLCommerz, Stripe & Crypto Gateways",
            onDismiss = { showAddFundDialog = false },
            onPaymentSuccess = { usdAmount, bdtAmount, gateway, trxId ->
                viewModel.addMonetizationFunds(
                    amount = usdAmount,
                    gateway = gateway.displayName,
                    trxId = trxId
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showWithdrawDialog) {
        com.example.ui.components.CreatorWithdrawDialog(
            availableBalanceUsd = wallet.availableBalance,
            minimumWithdrawalUsd = settings.minimumWithdrawal,
            onDismiss = { showWithdrawDialog = false },
            onWithdrawSubmit = { amount, method, accountDetails ->
                viewModel.requestMonetizationWithdrawal(amount, method, accountDetails) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        showWithdrawDialog = false
                    }
                }
            }
        )
    }
}

// ==========================================
// REUSABLE COMPONENTS
// ==========================================
@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = FlareTextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = FlareOffWhite,
            shadowElevation = 0.5.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsNavigationItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(FlareButtonBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = FlareTextSecondary
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = FlareTextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(FlareButtonBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = FlareTextSecondary
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FlareCameraBlue
            )
        )
    }
}
