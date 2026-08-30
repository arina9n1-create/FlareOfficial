package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.VynAvatar
import com.example.ui.theme.*
import com.example.ui.viewmodel.SocialViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class AdminMainSection {
    SUPER_ADMIN_ROLES,  // 👑 Super Admin: User & Role Management (RBAC)
    STORAGE_CLEAN,      // 🧹 Super Admin: Storage & Database Clean Center
    MONETIZATION,       // 💎 Creator Monetization (Applications, Revenue Pool, Content Earnings, Creator Payouts)
    CONTENT_MODERATION, // 🛡️ Content Moderation (Delete/Edit platform posts & reels)
    REWARDS,            // 🎁 Refer & Reward Center (Coins Rules, Rates & Conversion, Gateways, Coin Payouts)
    OVERVIEW            // 📊 System Analytics & Status
}

enum class AdminRewardSubTab {
    RULES_CONFIG,       // 🎯 Daily Tasks & Referral Rules
    CONVERSION_RATES,   // 💵 Coin Exchange Rates & BDT Conversion
    GATEWAYS,           // 💳 Payment Gateways (bKash, Nagad, Rocket, Recharge, Binance)
    PAYOUT_REQUESTS     // 💸 Coin Withdrawal Requests
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: SocialViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentRole by viewModel.currentUserRole.collectAsState()
    val currentUser by viewModel.currentUserEntity.collectAsState()
    val canAccess = viewModel.canAccessAdminPanel()
    val isSuperAdmin = viewModel.isSuperAdmin()

    val allUsers by viewModel.allUsers.collectAsState()
    val currentConfig by viewModel.adminConfig.collectAsState()
    val withdrawals by viewModel.withdrawals.collectAsState()
    val tasks by viewModel.rewardTasks.collectAsState()
    val wallet by viewModel.rewardWallet.collectAsState()
    val monetizationApps by viewModel.monetizationApplications.collectAsState()
    val earningsWallet by viewModel.earningsWallet.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val stories by viewModel.stories.collectAsState()
    val reels by viewModel.reels.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val notifications by viewModel.notifications.collectAsState()

    // Sections this staff member is actually allowed to see (keeps tabs & body in sync)
    val visibleSections = remember(currentRole, isSuperAdmin, currentUser) {
        buildList {
            if (isSuperAdmin || viewModel.canManageUsers()) add(AdminMainSection.SUPER_ADMIN_ROLES)
            if (isSuperAdmin || viewModel.canCleanStorage()) add(AdminMainSection.STORAGE_CLEAN)
            if (viewModel.canManageMonetization()) add(AdminMainSection.MONETIZATION)
            if (viewModel.canModerateContent()) add(AdminMainSection.CONTENT_MODERATION)
            if (viewModel.canManageRewards()) add(AdminMainSection.REWARDS)
            add(AdminMainSection.OVERVIEW)
        }
    }

    // Land on the first section this role can actually use (fixes blank-screen for e.g. Moderator)
    var selectedSection by remember {
        mutableStateOf(
            when {
                isSuperAdmin || viewModel.canManageUsers() -> AdminMainSection.SUPER_ADMIN_ROLES
                viewModel.canManageMonetization() -> AdminMainSection.MONETIZATION
                viewModel.canModerateContent() -> AdminMainSection.CONTENT_MODERATION
                viewModel.canManageRewards() -> AdminMainSection.REWARDS
                else -> AdminMainSection.OVERVIEW
            }
        )
    }
    var selectedRewardSubTab by remember { mutableStateOf(AdminRewardSubTab.RULES_CONFIG) }

    // Dialog state for user editing
    var editingUser by remember { mutableStateOf<AppUserEntity?>(null) }
    var showWipeConfirmation by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<AppUserEntity?>(null) }
    var userToBan by remember { mutableStateOf<AppUserEntity?>(null) }

    // Pull all withdrawal requests + remote wallet refunds from Supabase on open
    LaunchedEffect(Unit) {
        viewModel.refreshRewardServerData(currentUser?.handle ?: "")
    }

    // Safety: if the current role loses access to the selected section, fall back
    LaunchedEffect(visibleSections) {
        if (selectedSection !in visibleSections) selectedSection = visibleSections.first()
    }

    BackHandler {
        onBack()
    }

    if (!canAccess) {
        // Access Denied Screen for regular users
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Access Restricted") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFF4757).copy(alpha = 0.15f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = Color(0xFFFF4757),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Text(
                            text = "Admin Access Required",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "You do not have permission to view or manage the Admin Control Panel. Contact the Super Admin to grant you staff privileges.",
                            fontSize = 14.sp,
                            color = VynTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                        ) {
                            Text("Go Back to App", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        return
    }

    val pendingMonetizationCount = remember(monetizationApps) {
        monetizationApps.count { it.status == "PENDING" }
    }
    val pendingCoinPayoutCount = remember(withdrawals) {
        withdrawals.count { it.status == "PENDING" }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isSuperAdmin) "👑 Super Admin Panel" else "🛡️ Staff Control Panel",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(currentRole.badgeColorHex).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = currentRole.displayName.uppercase(),
                                    color = Color(currentRole.badgeColorHex),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isSuperAdmin) "Full authority over users, roles, storage & monetization" else "Role: ${currentRole.displayName}",
                            fontSize = 11.sp,
                            color = VynTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("admin_back_button")
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
        ) {
            // Main Section Selector Tabs
            ScrollableTabRow(
                selectedTabIndex = visibleSections.indexOf(selectedSection).coerceAtLeast(0),
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                contentColor = Color(0xFF6C5CE7),
                divider = {}
            ) {
                if (isSuperAdmin || viewModel.canManageUsers()) {
                    Tab(
                        selected = selectedSection == AdminMainSection.SUPER_ADMIN_ROLES,
                        onClick = { selectedSection = AdminMainSection.SUPER_ADMIN_ROLES },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("👑 Roles & Users", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFFD700).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        "${allUsers.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB8860B),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    )
                }

                if (isSuperAdmin || viewModel.canCleanStorage()) {
                    Tab(
                        selected = selectedSection == AdminMainSection.STORAGE_CLEAN,
                        onClick = { selectedSection = AdminMainSection.STORAGE_CLEAN },
                        text = {
                            Text("🧹 Storage Clean", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    )
                }

                if (viewModel.canManageMonetization()) {
                    Tab(
                        selected = selectedSection == AdminMainSection.MONETIZATION,
                        onClick = { selectedSection = AdminMainSection.MONETIZATION },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("💎 Monetization", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                if (pendingMonetizationCount > 0) {
                                    Badge(containerColor = Color(0xFFFF4757)) {
                                        Text("$pendingMonetizationCount", color = Color.White, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    )
                }

                if (AdminMainSection.CONTENT_MODERATION in visibleSections) {
                    Tab(
                        selected = selectedSection == AdminMainSection.CONTENT_MODERATION,
                        onClick = { selectedSection = AdminMainSection.CONTENT_MODERATION },
                        text = {
                            Text("🛡️ Moderate", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    )
                }

                if (viewModel.canManageRewards()) {
                    Tab(
                        selected = selectedSection == AdminMainSection.REWARDS,
                        onClick = { selectedSection = AdminMainSection.REWARDS },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("🎁 Rewards", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                if (pendingCoinPayoutCount > 0) {
                                    Badge(containerColor = Color(0xFFFF4757)) {
                                        Text("$pendingCoinPayoutCount", color = Color.White, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    )
                }

                Tab(
                    selected = selectedSection == AdminMainSection.OVERVIEW,
                    onClick = { selectedSection = AdminMainSection.OVERVIEW },
                    text = {
                        Text("📊 Status", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                )
            }

            // Section Content Body
            Crossfade(targetState = selectedSection, label = "admin_section_crossfade") { section ->
                when (section) {
                    AdminMainSection.SUPER_ADMIN_ROLES -> {
                        SuperAdminRolesView(
                            allUsers = allUsers,
                            currentUser = currentUser,
                            isSuperAdmin = isSuperAdmin,
                            onEditUser = { editingUser = it },
                            onDeleteUser = { uid -> userToDelete = allUsers.firstOrNull { it.uid == uid } },
                            onToggleBan = { user ->
                                if (user.isBanned) {
                                    viewModel.unbanUser(user.uid)
                                } else {
                                    userToBan = user
                                }
                            }
                        )
                    }

                    AdminMainSection.STORAGE_CLEAN -> {
                        StorageCleanView(
                            postsCount = posts.size,
                            storiesCount = stories.size,
                            chatCount = chatMessages.size,
                            notifCount = notifications.size,
                            usersCount = allUsers.size,
                            onCleanPosts = {
                                viewModel.cleanAllPosts()
                                Toast.makeText(context, "All posts & comments cleaned", Toast.LENGTH_SHORT).show()
                            },
                            onCleanChats = {
                                viewModel.cleanAllChats()
                                Toast.makeText(context, "All chat messages cleared", Toast.LENGTH_SHORT).show()
                            },
                            onCleanStories = {
                                viewModel.cleanAllStories()
                                Toast.makeText(context, "All stories cleared", Toast.LENGTH_SHORT).show()
                            },
                            onCleanNotifications = {
                                viewModel.cleanAllNotifications()
                                Toast.makeText(context, "All notifications cleared", Toast.LENGTH_SHORT).show()
                            },
                            onCleanupBroken = {
                                viewModel.cleanupCorruptedPosts()
                                Toast.makeText(context, "Broken posts cleanup triggered", Toast.LENGTH_SHORT).show()
                            },
                            onWipeAll = { showWipeConfirmation = true }
                        )
                    }

                    AdminMainSection.MONETIZATION -> {
                        // Full monetization control center (Overview, Revenue Periods, Content
                        // Earnings, Creator Reports, Revenue Settings, Ad Units, Applications,
                        // Wallet & Transactions) from AdminMonetizationSection.kt
                        AdminMonetizationSection(viewModel = viewModel)
                    }

                    AdminMainSection.CONTENT_MODERATION -> {
                        ContentModerationView(
                            posts = posts,
                            reels = reels,
                            onDeletePost = { post ->
                                viewModel.deletePost(post)
                                Toast.makeText(context, "Post deleted from platform", Toast.LENGTH_SHORT).show()
                            },
                            onDeleteReel = { reel ->
                                viewModel.deleteReel(reel)
                                Toast.makeText(context, "Reel deleted from platform", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    AdminMainSection.REWARDS -> {
                        AdminRewardsSectionView(
                            viewModel = viewModel,
                            selectedSubTab = selectedRewardSubTab,
                            onSelectSubTab = { selectedRewardSubTab = it },
                            currentConfig = currentConfig,
                            withdrawals = withdrawals,
                            tasks = tasks
                        )
                    }

                    AdminMainSection.OVERVIEW -> {
                        AdminOverviewView(
                            usersCount = allUsers.size,
                            postsCount = posts.size,
                            reelsCount = reels.size,
                            coinsOut = withdrawals.sumOf { it.creditsUsed },
                            pendingWithdrawals = withdrawals.count { it.status == "PENDING" },
                            monetizationAppsCount = monetizationApps.size
                        )
                    }
                }
            }
        }
    }

    // Role & Permissions Editor Dialog
    editingUser?.let { user ->
        RolePermissionEditDialog(
            user = user,
            isSuperAdmin = isSuperAdmin,
            onDismiss = { editingUser = null },
            onSave = { updatedRole, canManageUsers, canDeletePosts, canEditPosts, canModerateComments, canManageChats, canManageMonetization, canManageRewards, canCleanStorage ->
                viewModel.updateUserPermissions(
                    uid = user.uid,
                    newRole = updatedRole,
                    canManageUsers = canManageUsers,
                    canDeletePosts = canDeletePosts,
                    canEditPosts = canEditPosts,
                    canModerateComments = canModerateComments,
                    canManageChats = canManageChats,
                    canManageMonetization = canManageMonetization,
                    canManageRewards = canManageRewards,
                    canCleanStorage = canCleanStorage
                )
                editingUser = null
            }
        )
    }

    // Delete User Confirmation Dialog
    userToDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF4757)) },
            title = { Text("Delete User Account?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will permanently delete @${user.handle} (${user.name}) from the platform. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUserAccount(user.uid)
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
                ) {
                    Text("Yes, Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Ban Reason Dialog
    userToBan?.let { user ->
        var banReason by remember(user.uid) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { userToBan = null },
            icon = { Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFF4757)) },
            title = { Text("Suspend @${user.handle}?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("The user will be banned from the platform. Please provide a reason:")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = banReason,
                        onValueChange = { banReason = it },
                        label = { Text("Ban reason") },
                        placeholder = { Text("e.g. Community guidelines violation") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.banUser(user.uid, banReason.ifBlank { "Policy violation" })
                        userToBan = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
                ) {
                    Text("Suspend User", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToBan = null }) { Text("Cancel") }
            }
        )
    }

    // Wipe Database Confirmation Dialog
    if (showWipeConfirmation) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmation = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = "Warning", tint = Color(0xFFFF4757)) },
            title = { Text("⚠️ Total Clean Slate Wipe", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will permanently delete all posts, comments, stories, chat logs, and notifications. Only registered user accounts and their assigned roles will be preserved.\n\nAre you sure you want to proceed?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.wipeAllSystemData()
                        showWipeConfirmation = false
                        Toast.makeText(context, "System data wiped to clean state successfully!", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
                ) {
                    Text("Yes, Wipe All Data", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// 1. SUPER ADMIN: USER & ROLE MANAGEMENT (RBAC)
// -------------------------------------------------------------
@Composable
fun SuperAdminRolesView(
    allUsers: List<AppUserEntity>,
    currentUser: AppUserEntity?,
    isSuperAdmin: Boolean,
    onEditUser: (AppUserEntity) -> Unit,
    onDeleteUser: (String) -> Unit,
    onToggleBan: (AppUserEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val admins = allUsers.filter { UserRole.fromString(it.role) != UserRole.USER }
    val members = allUsers.filter { UserRole.fromString(it.role) == UserRole.USER }

    val filteredAdmins = admins.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.handle.contains(searchQuery, ignoreCase = true)
    }
    val filteredMembers = members.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.handle.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Super Admin Info Banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFD700).copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("👑", fontSize = 26.sp)
                    Column {
                        Text(
                            text = "Super Admin Power & Hierarchy",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "First registered user is automatically Super Admin. Super Admin can assign roles: Super Admin, Admin, Manager, Moderator, or Member with custom granular permissions.",
                            fontSize = 12.sp,
                            color = VynTextSecondary
                        )
                    }
                }
            }
        }

        item {
            // Modern Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                placeholder = { Text("Search users by name or @handle...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
            )
        }

        // --- PLATFORM ADMINS & STAFF SECTION ---
        if (filteredAdmins.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Platform Admins & Staff (${filteredAdmins.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "High Privilege",
                        fontSize = 11.sp,
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            items(filteredAdmins, key = { it.uid }) { user ->
                UserRoleCard(
                    user = user,
                    currentUser = currentUser,
                    isSuperAdmin = isSuperAdmin,
                    onEditUser = onEditUser,
                    onDeleteUser = onDeleteUser,
                    onToggleBan = onToggleBan
                )
            }
        }

        // --- REGISTERED MEMBERS / SEARCH RESULTS SECTION ---
        if (filteredMembers.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "Registered Users (${filteredMembers.size})" else "Search Results (${filteredMembers.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Standard Access",
                        fontSize = 11.sp,
                        color = Color(0xFF00B894),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            items(filteredMembers, key = { it.uid }) { user ->
                UserRoleCard(
                    user = user,
                    currentUser = currentUser,
                    isSuperAdmin = isSuperAdmin,
                    onEditUser = onEditUser,
                    onDeleteUser = onDeleteUser,
                    onToggleBan = onToggleBan
                )
            }
        } else if (searchQuery.isNotEmpty() && filteredAdmins.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = VynTextSecondary
                        )
                        Text("No users found matching '$searchQuery'", color = VynTextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun UserRoleCard(
    user: AppUserEntity,
    currentUser: AppUserEntity?,
    isSuperAdmin: Boolean,
    onEditUser: (AppUserEntity) -> Unit,
    onDeleteUser: (String) -> Unit,
    onToggleBan: (AppUserEntity) -> Unit
) {
    val userRole = UserRole.fromString(user.role)
    val isTargetSuperAdmin = userRole == UserRole.SUPER_ADMIN
    val isSelf = currentUser?.uid == user.uid

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (user.isBanned) Color(0xFFFF4757).copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VynAvatar(
                    avatarType = user.avatarType,
                    storagePath = user.avatarPath,
                    size = 44.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = user.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isSelf) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF6C5CE7).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "YOU",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6C5CE7),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "@${user.handle} · ${user.email}",
                        fontSize = 12.sp,
                        color = VynTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Role Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(userRole.badgeColorHex).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(userRole.badgeColorHex).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(userRole.iconEmoji, fontSize = 12.sp)
                        Text(
                            userRole.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(userRole.badgeColorHex)
                        )
                    }
                }
            }

            // Permissions summary tag row
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (user.isBanned) {
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFF4757).copy(alpha = 0.2f)) {
                        Text("🚫 BANNED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF4757), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (userRole == UserRole.SUPER_ADMIN) {
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFFD700).copy(alpha = 0.2f)) {
                        Text("FULL ACCESS 👑", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8860B), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else {
                    if (user.canManageUsers) PermTag("👥 Users")
                    if (user.canDeletePosts) PermTag("🗑️ Posts")
                    if (user.canModerateComments) PermTag("💬 Mod")
                    if (user.canManageMonetization) PermTag("💎 Mon")
                    if (user.canManageRewards) PermTag("🎁 Coins")
                    if (user.canCleanStorage) PermTag("🧹 Storage")
                }
            }

            // Action Buttons
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSuperAdmin || (currentUser?.canManageUsers == true && !isTargetSuperAdmin)) {
                    Button(
                        onClick = { onEditUser(user) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Change Role & Perms", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (isSuperAdmin && !isSelf) {
                    OutlinedButton(
                        onClick = { onToggleBan(user) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (user.isBanned) Color(0xFF00B894) else Color(0xFFFF4757)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(if (user.isBanned) "Unban" else "Ban", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { onDeleteUser(user.uid) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF4757), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PermTag(label: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

// -------------------------------------------------------------
// ROLE & PERMISSIONS EDIT DIALOG
// -------------------------------------------------------------
@Composable
fun RolePermissionEditDialog(
    user: AppUserEntity,
    isSuperAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        role: UserRole,
        canManageUsers: Boolean,
        canDeletePosts: Boolean,
        canEditPosts: Boolean,
        canModerateComments: Boolean,
        canManageChats: Boolean,
        canManageMonetization: Boolean,
        canManageRewards: Boolean,
        canCleanStorage: Boolean
    ) -> Unit
) {
    var selectedRole by remember { mutableStateOf(UserRole.fromString(user.role)) }
    var canManageUsers by remember { mutableStateOf(user.canManageUsers) }
    var canDeletePosts by remember { mutableStateOf(user.canDeletePosts) }
    var canEditPosts by remember { mutableStateOf(user.canEditPosts) }
    var canModerateComments by remember { mutableStateOf(user.canModerateComments) }
    var canManageChats by remember { mutableStateOf(user.canManageChats) }
    var canManageMonetization by remember { mutableStateOf(user.canManageMonetization) }
    var canManageRewards by remember { mutableStateOf(user.canManageRewards) }
    var canCleanStorage by remember { mutableStateOf(user.canCleanStorage) }

    // Auto-update default permission presets when role changes
    fun applyPreset(role: UserRole) {
        selectedRole = role
        when (role) {
            UserRole.SUPER_ADMIN -> {
                canManageUsers = true
                canDeletePosts = true
                canEditPosts = true
                canModerateComments = true
                canManageChats = true
                canManageMonetization = true
                canManageRewards = true
                canCleanStorage = true
            }
            UserRole.ADMIN -> {
                canManageUsers = true
                canDeletePosts = true
                canEditPosts = true
                canModerateComments = true
                canManageChats = true
                canManageMonetization = true
                canManageRewards = true
                canCleanStorage = false
            }
            UserRole.MANAGER -> {
                canManageUsers = false
                canDeletePosts = true
                canEditPosts = false
                canModerateComments = true
                canManageChats = true
                canManageMonetization = true
                canManageRewards = true
                canCleanStorage = false
            }
            UserRole.MODERATOR -> {
                canManageUsers = false
                canDeletePosts = true
                canEditPosts = false
                canModerateComments = true
                canManageChats = true
                canManageMonetization = false
                canManageRewards = false
                canCleanStorage = false
            }
            UserRole.USER -> {
                canManageUsers = false
                canDeletePosts = false
                canEditPosts = false
                canModerateComments = false
                canManageChats = false
                canManageMonetization = false
                canManageRewards = false
                canCleanStorage = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Role & Permissions ⚙️", fontWeight = FontWeight.Bold)
                Text("@${user.handle} · ${user.name}", fontSize = 12.sp, color = VynTextSecondary)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Select Primary Role Tier", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        UserRole.values().forEach { role ->
                            // Super admin role can only be assigned by another super admin
                            if (role != UserRole.SUPER_ADMIN || isSuperAdmin) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedRole == role) Color(role.badgeColorHex).copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = if (selectedRole == role) androidx.compose.foundation.BorderStroke(1.5.dp, Color(role.badgeColorHex)) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { applyPreset(role) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(role.iconEmoji, fontSize = 18.sp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(role.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                when (role) {
                                                    UserRole.SUPER_ADMIN -> "Supreme Authority · All access & storage control"
                                                    UserRole.ADMIN -> "Full staff access · Manage users, posts, monetization"
                                                    UserRole.MANAGER -> "Manage rewards, monetization & content review"
                                                    UserRole.MODERATOR -> "Moderate comments, posts & live chat rooms"
                                                    UserRole.USER -> "Standard platform member"
                                                },
                                                fontSize = 10.sp,
                                                color = VynTextSecondary
                                            )
                                        }
                                        RadioButton(
                                            selected = selectedRole == role,
                                            onClick = { applyPreset(role) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Granular Permission Overrides", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PermSwitchRow("👥 Manage Users & Roles", canManageUsers) { canManageUsers = it }
                        PermSwitchRow("🗑️ Delete Any User's Post", canDeletePosts) { canDeletePosts = it }
                        PermSwitchRow("✏️ Edit Platform Posts", canEditPosts) { canEditPosts = it }
                        PermSwitchRow("💬 Moderate & Delete Comments", canModerateComments) { canModerateComments = it }
                        PermSwitchRow("💬 Moderate Live Multi-User Chat", canManageChats) { canManageChats = it }
                        PermSwitchRow("💎 Manage Creator Monetization", canManageMonetization) { canManageMonetization = it }
                        PermSwitchRow("🎁 Manage Rewards & Coin Rates", canManageRewards) { canManageRewards = it }
                        if (isSuperAdmin) {
                            PermSwitchRow("🧹 Wipe Storage & Database", canCleanStorage) { canCleanStorage = it }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        selectedRole,
                        canManageUsers,
                        canDeletePosts,
                        canEditPosts,
                        canModerateComments,
                        canManageChats,
                        canManageMonetization,
                        canManageRewards,
                        canCleanStorage
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PermSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(24.dp)
        )
    }
}

// -------------------------------------------------------------
// 2. SUPER ADMIN: STORAGE & DATABASE CLEAN CENTER
// -------------------------------------------------------------
@Composable
fun StorageCleanView(
    postsCount: Int,
    storiesCount: Int,
    chatCount: Int,
    notifCount: Int,
    usersCount: Int,
    onCleanPosts: () -> Unit,
    onCleanChats: () -> Unit,
    onCleanStories: () -> Unit,
    onCleanNotifications: () -> Unit,
    onCleanupBroken: () -> Unit,
    onWipeAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF00B894).copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B894).copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🧹", fontSize = 26.sp)
                    Column {
                        Text("Clean Slate Architecture", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "All pre-populated fake data and demo fixtures have been cleared. Use these controls to maintain a pristine database state.",
                            fontSize = 12.sp,
                            color = VynTextSecondary
                        )
                    }
                }
            }
        }

        item {
            Text("Database Resource Counts", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill(Modifier.weight(1f), "Posts", "$postsCount", "📝", Color(0xFF6C5CE7))
                StatPill(Modifier.weight(1f), "Stories", "$storiesCount", "📸", Color(0xFFFF7675))
                StatPill(Modifier.weight(1f), "Chats", "$chatCount", "💬", Color(0xFF0984E3))
                StatPill(Modifier.weight(1f), "Users", "$usersCount", "👥", Color(0xFFFFD700))
            }
        }

        item {
            Text("Granular Clean Operations", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        item {
            CleanActionCard(
                title = "Clean Posts & Comments",
                description = "Deletes all feed posts, likes, reposts, and comments.",
                buttonText = "Clean All Posts ($postsCount)",
                buttonColor = Color(0xFF6C5CE7),
                icon = Icons.AutoMirrored.Outlined.Article,
                onClick = onCleanPosts
            )
        }

        item {
            CleanActionCard(
                title = "Fix Broken Posts ✨",
                description = "Deep scan & remove posts with empty/white media from database and server.",
                buttonText = "Hard Cleanup",
                buttonColor = Color(0xFF00B894),
                icon = Icons.Outlined.AutoAwesome,
                onClick = onCleanupBroken
            )
        }

        item {
            CleanActionCard(
                title = "Clear Live Chat Logs",
                description = "Wipes all messages from public and direct chat rooms.",
                buttonText = "Clear Chat Logs ($chatCount)",
                buttonColor = Color(0xFF0984E3),
                icon = Icons.AutoMirrored.Outlined.Chat,
                onClick = onCleanChats
            )
        }

        item {
            CleanActionCard(
                title = "Clear Stories & Statuses",
                description = "Deletes all ephemeral user stories.",
                buttonText = "Clear Stories ($storiesCount)",
                buttonColor = Color(0xFFFF7675),
                icon = Icons.Outlined.HistoryEdu,
                onClick = onCleanStories
            )
        }

        item {
            CleanActionCard(
                title = "Clear Notifications",
                description = "Deletes all in-app notifications and alerts.",
                buttonText = "Clear Notifications ($notifCount)",
                buttonColor = Color(0xFF00B894),
                icon = Icons.Outlined.Notifications,
                onClick = onCleanNotifications
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF4757).copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF4757).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFFF4757))
                        Text("Total System Wipe (Clean Slate)", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFFFF4757))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Wipes all posts, comments, stories, chat logs, and connections at once while safely preserving registered users and Super Admin credentials.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onWipeAll,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⚠️ WIPE ALL DATA TO CLEAN STATE", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatPill(modifier: Modifier, title: String, count: String, emoji: String, color: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 16.sp)
            Text(count, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
            Text(title, fontSize = 10.sp, color = VynTextSecondary)
        }
    }
}

@Composable
fun CleanActionCard(
    title: String,
    description: String,
    buttonText: String,
    buttonColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = buttonColor.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = buttonColor, modifier = Modifier.size(20.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(description, fontSize = 11.sp, color = VynTextSecondary)
            }

            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(buttonText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// 3. CREATOR MONETIZATION SECTION VIEW
// -------------------------------------------------------------
@Composable
fun AdminMonetizationSectionView(
    viewModel: SocialViewModel,
    monetizationApps: List<MonetizationApplication>,
    earningsWallet: EarningsWallet
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Applications, 1: Revenue Pool, 2: Payouts

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = Color(0xFF6C5CE7)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Applications (${monetizationApps.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Revenue Pool", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Creator Payouts", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
        }

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (monetizationApps.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Outlined.Diamond, contentDescription = null, tint = Color(0xFF6C5CE7), modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No Monetization Applications", fontWeight = FontWeight.Bold)
                                    Text("When creators apply for monetization, their requests will appear here.", fontSize = 12.sp, color = VynTextSecondary)
                                }
                            }
                        }
                    } else {
                        items(monetizationApps) { app ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(app.userName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text("@${app.userHandle} · ${app.followerCountAtApplication} Followers · ${app.viewCountAtApplication} Views", fontSize = 12.sp, color = VynTextSecondary)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when (app.status) {
                                                "APPROVED" -> Color(0xFF00B894).copy(alpha = 0.2f)
                                                "REJECTED" -> Color(0xFFFF4757).copy(alpha = 0.2f)
                                                else -> Color(0xFFFFA502).copy(alpha = 0.2f)
                                            }
                                        ) {
                                            Text(
                                                app.status,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (app.status) {
                                                    "APPROVED" -> Color(0xFF00B894)
                                                    "REJECTED" -> Color(0xFFFF4757)
                                                    else -> Color(0xFFFFA502)
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (app.status == "PENDING") {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    viewModel.approveMonetizationApplication(app.id) { success, msg ->
                                                        Toast.makeText(context, if (success) "Application Approved 💎" else msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Approve 💎", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.rejectMonetizationApplication(app.id, "Does not meet criteria") { success, msg ->
                                                        Toast.makeText(context, if (success) "Application Rejected" else msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4757)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Reject", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF00B894).copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Creator Revenue Pool 💎", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF00B894))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Creators earn 60% of verified AdMob impressions on their reels and posts.", fontSize = 12.sp, color = VynTextSecondary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Available Pool Balance: $ ${String.format(Locale.US, "%.2f", earningsWallet.availableBalance)} USD", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
            2 -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No pending creator cashout requests.", color = VynTextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. REWARDS & COINS MANAGEMENT SECTION VIEW
// -------------------------------------------------------------
@Composable
fun AdminRewardsSectionView(
    viewModel: SocialViewModel,
    selectedSubTab: AdminRewardSubTab,
    onSelectSubTab: (AdminRewardSubTab) -> Unit,
    currentConfig: AdminConfig,
    withdrawals: List<WithdrawalRequest>,
    tasks: List<com.example.data.model.RewardTask>
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab.ordinal,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = Color(0xFFFFA502)
        ) {
            AdminRewardSubTab.values().forEach { tab ->
                Tab(
                    selected = selectedSubTab == tab,
                    onClick = { onSelectSubTab(tab) },
                    text = {
                        Text(
                            when (tab) {
                                AdminRewardSubTab.RULES_CONFIG -> "🎯 Rules"
                                AdminRewardSubTab.CONVERSION_RATES -> "💵 Rates"
                                AdminRewardSubTab.GATEWAYS -> "💳 Gateways"
                                AdminRewardSubTab.PAYOUT_REQUESTS -> "💸 Payouts (${withdrawals.count { it.status == "PENDING" }})"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                )
            }
        }

        when (selectedSubTab) {
            AdminRewardSubTab.RULES_CONFIG -> {
                AdminRewardRulesEditor(viewModel = viewModel, currentConfig = currentConfig)
            }

            AdminRewardSubTab.CONVERSION_RATES -> {
                AdminRewardRatesEditor(viewModel = viewModel, currentConfig = currentConfig)
            }

            AdminRewardSubTab.GATEWAYS -> {
                AdminRewardGatewaysEditor(viewModel = viewModel, currentConfig = currentConfig)
            }

            AdminRewardSubTab.PAYOUT_REQUESTS -> {
                AdminCoinPayoutRequestsView(viewModel = viewModel, withdrawals = withdrawals)
            }
        }
    }
}

// -------------------------------------------------------------
// 5. SYSTEM OVERVIEW & METRICS
// -------------------------------------------------------------
@Composable
fun AdminOverviewView(
    usersCount: Int,
    postsCount: Int,
    reelsCount: Int,
    coinsOut: Int,
    pendingWithdrawals: Int,
    monetizationAppsCount: Int
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF6C5CE7).copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Platform Health: All Systems Operational 🟢", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF6C5CE7))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Room SQLite Local DB + Supabase Sync Active", fontSize = 12.sp, color = VynTextSecondary)
                }
            }
        }

        item {
            Text("Ecosystem Metrics", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(Modifier.weight(1f), "Registered", "$usersCount", "👥", Color(0xFF6C5CE7))
                StatPill(Modifier.weight(1f), "Live Posts", "$postsCount", "📝", Color(0xFF00B894))
                StatPill(Modifier.weight(1f), "Reels", "$reelsCount", "🎬", Color(0xFF0984E3))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(Modifier.weight(1f), "Coins Out", "$coinsOut", "🪙", Color(0xFFFFA502))
                StatPill(Modifier.weight(1f), "Coin Payouts", "$pendingWithdrawals", "💸", if (pendingWithdrawals > 0) Color(0xFFFF4757) else Color(0xFF00B894))
                StatPill(Modifier.weight(1f), "Creator Apps", "$monetizationAppsCount", "💎", Color(0xFF6C5CE7))
            }
        }
    }
}

// -------------------------------------------------------------
// 6. CONTENT MODERATION (Posts & Reels) — delete from platform
// -------------------------------------------------------------
@Composable
fun ContentModerationView(
    posts: List<PostEntity>,
    reels: List<ReelEntity>,
    onDeletePost: (PostEntity) -> Unit,
    onDeleteReel: (ReelEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = Posts, 1 = Reels
    var pendingPostDelete by remember { mutableStateOf<PostEntity?>(null) }
    var pendingReelDelete by remember { mutableStateOf<ReelEntity?>(null) }

    val filteredPosts = remember(posts, searchQuery) {
        posts.filter {
            searchQuery.isBlank() ||
                    it.username.contains(searchQuery, ignoreCase = true) ||
                    it.userHandle.contains(searchQuery, ignoreCase = true) ||
                    it.caption.contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredReels = remember(reels, searchQuery) {
        reels.filter {
            searchQuery.isBlank() ||
                    it.author.contains(searchQuery, ignoreCase = true) ||
                    it.handle.contains(searchQuery, ignoreCase = true) ||
                    it.caption.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedFilter == 0,
                onClick = { selectedFilter = 0 },
                label = { Text("📝 Posts (${posts.size})") }
            )
            FilterChip(
                selected = selectedFilter == 1,
                onClick = { selectedFilter = 1 },
                label = { Text("🎬 Reels (${reels.size})") }
            )
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by author, handle or caption...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedFilter == 0) {
                if (filteredPosts.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No posts match.", color = VynTextSecondary, fontSize = 13.sp)
                        }
                    }
                }
                items(filteredPosts) { post ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("@${post.userHandle.ifBlank { post.username }}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    post.caption.ifBlank { "(No caption)" },
                                    fontSize = 12.sp,
                                    color = VynTextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "❤️ ${post.likesCount} · 💬 ${post.commentsCount} · ${post.timeAgo}",
                                    fontSize = 11.sp,
                                    color = VynTextSecondary
                                )
                            }
                            IconButton(onClick = { pendingPostDelete = post }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete post", tint = Color(0xFFFF4757))
                            }
                        }
                    }
                }
            } else {
                ContentModerationReelsList(reels = filteredReels, onDelete = { pendingReelDelete = it })
            }
        }
    }

    // Post delete confirmation
    pendingPostDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { pendingPostDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF4757)) },
            title = { Text("Delete this post?", fontWeight = FontWeight.Bold) },
            text = {
                Text("The post by @${post.userHandle.ifBlank { post.username }} will be removed from the server and every device. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePost(post)
                        pendingPostDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
                ) { Text("Delete Post", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingPostDelete = null }) { Text("Cancel") } }
        )
    }

    // Reel delete confirmation
    pendingReelDelete?.let { reel ->
        AlertDialog(
            onDismissRequest = { pendingReelDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF4757)) },
            title = { Text("Delete this reel?", fontWeight = FontWeight.Bold) },
            text = {
                Text("The reel by @${reel.handle.ifBlank { reel.author }} will be removed from the server and every device. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteReel(reel)
                        pendingReelDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
                ) { Text("Delete Reel", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingReelDelete = null }) { Text("Cancel") } }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.ContentModerationReelsList(
    reels: List<ReelEntity>,
    onDelete: (ReelEntity) -> Unit
) {
    if (reels.isEmpty()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No reels match.", color = VynTextSecondary, fontSize = 13.sp)
            }
        }
    }
    items(reels) { reel ->
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("@${reel.handle.ifBlank { reel.author }}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        reel.caption.ifBlank { "(No caption)" },
                        fontSize = 12.sp,
                        color = VynTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("❤️ ${reel.likesCount} · 💬 ${reel.commentsCount}", fontSize = 11.sp, color = VynTextSecondary)
                }
                IconButton(onClick = { onDelete(reel) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete reel", tint = Color(0xFFFF4757))
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 7. REWARD EDITORS — fully editable Rules / Rates / Gateways
// -------------------------------------------------------------
@Composable
fun AdminOutlinedNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(9)) },
        label = { Text(label) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        ),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    )
}

@Composable
fun AdminRewardRulesEditor(
    viewModel: SocialViewModel,
    currentConfig: AdminConfig
) {
    val context = LocalContext.current

    var watchReels by remember(currentConfig) { mutableStateOf(currentConfig.dailyTaskRequiredWatchReels.toString()) }
    var uploadReels by remember(currentConfig) { mutableStateOf(currentConfig.dailyTaskRequiredUploadReels.toString()) }
    var dailyReward by remember(currentConfig) { mutableStateOf(currentConfig.dailyTaskRewardCredits.toString()) }
    var referralBonus by remember(currentConfig) { mutableStateOf(currentConfig.referralBonusCredits.toString()) }
    var referralDays by remember(currentConfig) { mutableStateOf(currentConfig.referralRequiredDailyTaskDays.toString()) }
    var creditsPerReel by remember(currentConfig) { mutableStateOf(currentConfig.creditsPerReel.toString()) }
    var watchSeconds by remember(currentConfig) { mutableStateOf(currentConfig.requiredReelWatchSeconds.toString()) }
    var noticeMessage by remember(currentConfig) { mutableStateOf(currentConfig.noticeMessage) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("🎯 Daily Tasks & Referral Rules", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminOutlinedNumberField("Reels to watch / day", watchReels, { watchReels = it }, Modifier.weight(1f))
                AdminOutlinedNumberField("Reels to upload / day", uploadReels, { uploadReels = it }, Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminOutlinedNumberField("Daily reward (coins)", dailyReward, { dailyReward = it }, Modifier.weight(1f))
                AdminOutlinedNumberField("Reel reward (coins)", creditsPerReel, { creditsPerReel = it }, Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminOutlinedNumberField("Referral bonus (coins)", referralBonus, { referralBonus = it }, Modifier.weight(1f))
                AdminOutlinedNumberField("Referral task days", referralDays, { referralDays = it }, Modifier.weight(1f))
            }
        }
        item {
            AdminOutlinedNumberField("Required watch seconds / reel", watchSeconds, { watchSeconds = it }, Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(
                value = noticeMessage,
                onValueChange = { noticeMessage = it },
                label = { Text("🎁 Notice Banner (shown on Reward screen)") },
                minLines = 2,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    val newConfig = currentConfig.copy(
                        dailyTaskRequiredWatchReels = watchReels.toIntOrNull()?.coerceAtLeast(1) ?: currentConfig.dailyTaskRequiredWatchReels,
                        dailyTaskRequiredUploadReels = uploadReels.toIntOrNull()?.coerceAtLeast(0) ?: currentConfig.dailyTaskRequiredUploadReels,
                        dailyTaskRewardCredits = dailyReward.toIntOrNull()?.coerceAtLeast(0) ?: currentConfig.dailyTaskRewardCredits,
                        referralBonusCredits = referralBonus.toIntOrNull()?.coerceAtLeast(0) ?: currentConfig.referralBonusCredits,
                        referralRequiredDailyTaskDays = referralDays.toIntOrNull()?.coerceAtLeast(1) ?: currentConfig.referralRequiredDailyTaskDays,
                        creditsPerReel = creditsPerReel.toIntOrNull()?.coerceAtLeast(0) ?: currentConfig.creditsPerReel,
                        requiredReelWatchSeconds = watchSeconds.toIntOrNull()?.coerceIn(3, 600) ?: currentConfig.requiredReelWatchSeconds,
                        noticeMessage = noticeMessage
                    )
                    viewModel.updateAdminConfig(newConfig)
                    Toast.makeText(context, "Reward rules saved & synced ✅", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA502)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💾 Save Rules", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AdminRewardRatesEditor(
    viewModel: SocialViewModel,
    currentConfig: AdminConfig
) {
    val context = LocalContext.current

    var creditsPerDollar by remember(currentConfig) { mutableStateOf(currentConfig.creditsPerDollar.toString()) }
    var usdToBdtRate by remember(currentConfig) { mutableStateOf(currentConfig.usdToBdtRate.toString()) }
    var minWithdrawal by remember(currentConfig) { mutableStateOf(currentConfig.minWithdrawalUSD.toString()) }

    val parsedCredits = creditsPerDollar.toIntOrNull() ?: currentConfig.creditsPerDollar
    val parsedRate = usdToBdtRate.toDoubleOrNull() ?: currentConfig.usdToBdtRate

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("💵 Coin Exchange Rates & Conversion", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        item {
            AdminOutlinedNumberField("Coins per $1 USD", creditsPerDollar, { creditsPerDollar = it }, Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(
                value = usdToBdtRate,
                onValueChange = { input -> usdToBdtRate = input.filter { it.isDigit() || it == '.' }.take(8) },
                label = { Text("USD → BDT Rate") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                ),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = minWithdrawal,
                onValueChange = { input -> minWithdrawal = input.filter { it.isDigit() || it == '.' }.take(8) },
                label = { Text("Minimum Withdrawal ($ USD)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                ),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA502).copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Live Preview", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFFA502))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$parsedCredits Coins = $1.00 USD = ৳${String.format(Locale.US, "%.2f", parsedRate)} BDT", fontSize = 13.sp)
                    Text(
                        "1 Coin = ৳${if (parsedCredits > 0) String.format(Locale.US, "%.4f", parsedRate / parsedCredits) else "0"} BDT",
                        fontSize = 12.sp,
                        color = VynTextSecondary
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    val newConfig = currentConfig.copy(
                        creditsPerDollar = parsedCredits.coerceAtLeast(1),
                        usdToBdtRate = parsedRate.coerceIn(1.0, 1000.0),
                        minWithdrawalUSD = minWithdrawal.toDoubleOrNull()?.coerceAtLeast(0.1) ?: currentConfig.minWithdrawalUSD
                    )
                    viewModel.updateAdminConfig(newConfig)
                    Toast.makeText(context, "Exchange rates saved & synced ✅", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA502)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💾 Save Rates", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AdminRewardGatewaysEditor(
    viewModel: SocialViewModel,
    currentConfig: AdminConfig
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("💳 Payment Gateways", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Users can only withdraw through ENABLED gateways.", fontSize = 12.sp, color = VynTextSecondary)
        }
        item {
            AdminGatewayRow("bKash (বিকাশ)", currentConfig.isBkashEnabled) { enabled ->
                viewModel.updateAdminConfig(currentConfig.copy(isBkashEnabled = enabled))
            }
        }
        item {
            AdminGatewayRow("Nagad (নগদ)", currentConfig.isNagadEnabled) { enabled ->
                viewModel.updateAdminConfig(currentConfig.copy(isNagadEnabled = enabled))
            }
        }
        item {
            AdminGatewayRow("Rocket (রকেট)", currentConfig.isRocketEnabled) { enabled ->
                viewModel.updateAdminConfig(currentConfig.copy(isRocketEnabled = enabled))
            }
        }
        item {
            AdminGatewayRow("Binance USDT", currentConfig.isBinanceEnabled) { enabled ->
                viewModel.updateAdminConfig(currentConfig.copy(isBinanceEnabled = enabled))
            }
        }
        item {
            AdminGatewayRow("PayPal (Global)", currentConfig.isPaypalEnabled) { enabled ->
                viewModel.updateAdminConfig(currentConfig.copy(isPaypalEnabled = enabled))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Custom Methods", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                TextButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFFFA502))
                    Text("Add", fontWeight = FontWeight.Bold, color = Color(0xFFFFA502))
                }
            }
        }

        items(currentConfig.customPaymentMethods) { method ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(method.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "${method.type} · Min: $${String.format(Locale.US, "%.2f", method.minWithdrawalUSD)}",
                            fontSize = 11.sp,
                            color = VynTextSecondary
                        )
                    }
                    Switch(
                        checked = method.isEnabled,
                        onCheckedChange = { viewModel.togglePaymentMethod(method.id) }
                    )
                    IconButton(onClick = { viewModel.deletePaymentMethod(method.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete method", tint = Color(0xFFFF4757))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AdminAddPaymentMethodDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { method ->
                viewModel.addPaymentMethod(method)
                showAddDialog = false
                Toast.makeText(context, "Payment method added ✅", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun AdminGatewayRow(
    name: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Payment, contentDescription = null, tint = Color(0xFF00B894))
            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (enabled) Color(0xFF00B894).copy(alpha = 0.2f) else Color(0xFFFF4757).copy(alpha = 0.2f)
            ) {
                Text(
                    if (enabled) "ACTIVE" else "OFF",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color(0xFF00B894) else Color(0xFFFF4757),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Switch(checked = enabled, onCheckedChange = { onToggle(it) })
        }
    }
}

@Composable
fun AdminAddPaymentMethodDialog(
    onDismiss: () -> Unit,
    onAdd: (com.example.data.model.PaymentMethodItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var minUsd by remember { mutableStateOf("1.0") }
    var instructions by remember { mutableStateOf("Enter your active account / wallet number") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Payment Method", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Method name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minUsd,
                    onValueChange = { input -> minUsd = input.filter { it.isDigit() || it == '.' }.take(8) },
                    label = { Text("Minimum withdrawal ($)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("User instructions") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(
                            com.example.data.model.PaymentMethodItem(
                                id = "",
                                name = name.trim(),
                                type = "CUSTOM",
                                iconType = "generic",
                                minWithdrawalUSD = minUsd.toDoubleOrNull() ?: 1.0,
                                instructions = instructions
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Add", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// -------------------------------------------------------------
// 8. COIN PAYOUT REQUESTS (server-synced approve/reject with refund)
// -------------------------------------------------------------
@Composable
fun AdminCoinPayoutRequestsView(
    viewModel: SocialViewModel,
    withdrawals: List<WithdrawalRequest>
) {
    val context = LocalContext.current
    var pendingApprove by remember { mutableStateOf<WithdrawalRequest?>(null) }
    var pendingReject by remember { mutableStateOf<WithdrawalRequest?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val handleResult: (Boolean, String) -> Unit = { ok, message ->
        isBusy = false
        Toast.makeText(context, message, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("💸 Coin Withdrawal Requests", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "${withdrawals.count { it.status == "PENDING" }} pending · ${withdrawals.size} total",
                            fontSize = 12.sp,
                            color = VynTextSecondary
                        )
                    }
                    TextButton(onClick = { isBusy = true; viewModel.refreshRewardServerData("") }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFFFA502))
                        Text("Refresh", fontWeight = FontWeight.Bold, color = Color(0xFFFFA502), fontSize = 12.sp)
                    }
                }
            }

            if (withdrawals.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No coin withdrawal requests found.\nTap Refresh to load requests from the server.",
                            color = VynTextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                AdminCoinPayoutList(
                    withdrawals = withdrawals,
                    isBusy = isBusy,
                    onApprove = { pendingApprove = it },
                    onReject = { pendingReject = it }
                )
            }
        }

        if (isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFFA502))
            }
        }
    }

    AdminCoinPayoutDialogs(
        pendingApprove = pendingApprove,
        pendingReject = pendingReject,
        onDismissApprove = { pendingApprove = null },
        onDismissReject = { pendingReject = null },
        onConfirmApprove = { req ->
            isBusy = true
            pendingApprove = null
            viewModel.updateWithdrawal(req.id, "PAID", "Approved & paid by Admin", handleResult)
        },
        onConfirmReject = { req, reason ->
            isBusy = true
            pendingReject = null
            viewModel.updateWithdrawal(req.id, "REJECTED", reason, handleResult)
        }
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.AdminCoinPayoutList(
    withdrawals: List<WithdrawalRequest>,
    isBusy: Boolean,
    onApprove: (WithdrawalRequest) -> Unit,
    onReject: (WithdrawalRequest) -> Unit
) {
    items(withdrawals) { req ->
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${req.creditsUsed} Coins ($ ${String.format(Locale.US, "%.2f", req.amountUSD)} · ৳${String.format(Locale.US, "%.0f", req.amountBDT)})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (req.status) {
                            "PAID" -> Color(0xFF00B894).copy(alpha = 0.2f)
                            "REJECTED" -> Color(0xFFFF4757).copy(alpha = 0.2f)
                            else -> Color(0xFFFFA502).copy(alpha = 0.2f)
                        }
                    ) {
                        Text(
                            req.status,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (req.status) {
                                "PAID" -> Color(0xFF00B894)
                                "REJECTED" -> Color(0xFFFF4757)
                                else -> Color(0xFFFFA502)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Gateway: ${req.method} · Account: ${req.accountNumber}", fontSize = 12.sp, color = VynTextSecondary)
                Text("User: @${req.userHandle} · ${req.userEmail} · ${req.requestDate}", fontSize = 12.sp, color = VynTextSecondary)
                if (req.transactionNote.isNotBlank()) {
                    Text("Note: ${req.transactionNote}", fontSize = 11.sp, color = VynTextSecondary)
                }

                if (req.status == "PENDING") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onApprove(req) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Approve Payout", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onReject(req) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4757)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reject", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminCoinPayoutDialogs(
    pendingApprove: WithdrawalRequest?,
    pendingReject: WithdrawalRequest?,
    onDismissApprove: () -> Unit,
    onDismissReject: () -> Unit,
    onConfirmApprove: (WithdrawalRequest) -> Unit,
    onConfirmReject: (WithdrawalRequest, String) -> Unit
) {
    // Approve confirmation
    pendingApprove?.let { req ->
        AlertDialog(
            onDismissRequest = onDismissApprove,
            icon = { Icon(Icons.Default.Payment, contentDescription = null, tint = Color(0xFF00B894)) },
            title = { Text("Approve & Mark as Paid?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Send ${req.creditsUsed} Coins ($ ${String.format(Locale.US, "%.2f", req.amountUSD)}) to @${req.userHandle} via ${req.method} (${req.accountNumber})?\n\nOnly approve after you have actually transferred the money.")
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmApprove(req) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894))
                ) { Text("Yes, Approve", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = onDismissApprove) { Text("Cancel") } }
        )
    }

    // Reject dialog with reason + automatic coin refund
    pendingReject?.let { req ->
        var rejectReason by remember(req.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismissReject,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF4757)) },
            title = { Text("Reject Withdrawal?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("${req.creditsUsed} Coins will be automatically refunded to @${req.userHandle}'s wallet.")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text("Rejection reason") },
                        placeholder = { Text("e.g. Invalid account details") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmReject(req, rejectReason.ifBlank { "Rejected by Admin" }) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
                ) { Text("Reject & Refund", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = onDismissReject) { Text("Cancel") } }
        )
    }
}
