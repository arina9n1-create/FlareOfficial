package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import com.example.data.notification.NotificationHelper
import com.example.data.notification.NotificationPreferences
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.VynButtonBg
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.SocialViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SocialViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "POST_NOTIFICATIONS permission granted")
        } else {
            Log.d("MainActivity", "POST_NOTIFICATIONS permission denied")
        }
    }

    private val requestMediaPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        Log.d("MainActivity", "Call Permissions: Camera=$cameraGranted, Audio=$audioGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize Android Notification Channels
        try {
            NotificationHelper.createNotificationChannels(this)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Error creating notification channels", e)
        }

        // 2. Request Android 13+ Notification Permission
        try {
            checkAndRequestNotificationPermission()
        } catch (e: Throwable) {
            Log.e("MainActivity", "Error requesting notification permission", e)
        }

        // 3. Handle Notification Navigation Intent
        try {
            handleNotificationIntent(intent)
            handleDeepLink(intent)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Error handling intent", e)
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.data?.let { uri ->
            viewModel.handleDeepLink(uri)
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        checkAndRequestCallPermissions()
    }

    private fun checkAndRequestCallPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }
        if (permissionsNeeded.isNotEmpty()) {
            requestMediaPermissionsLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    private fun handleNotificationIntent(targetIntent: Intent?) {
        val screen = targetIntent?.getStringExtra(NotificationHelper.EXTRA_TARGET_SCREEN) ?: return
        when (screen) {
            NotificationHelper.SCREEN_CHAT -> {
                val handle = targetIntent.getStringExtra(NotificationHelper.EXTRA_CHAT_HANDLE)?.takeIf { it.isNotBlank() }
                    ?: return
                val name = targetIntent.getStringExtra(NotificationHelper.EXTRA_CHAT_NAME)?.takeIf { it.isNotBlank() }
                    ?: handle
                viewModel.setTab(MainTab.CHAT)
                viewModel.openChatPartner(name)
            }
            NotificationHelper.SCREEN_NOTIFICATIONS -> {
                viewModel.openNotifications()
            }
            NotificationHelper.SCREEN_REWARDS -> {
                viewModel.openRewardScreen()
            }
            NotificationHelper.SCREEN_ADMIN -> {
                viewModel.openAdminScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen(
    viewModel: SocialViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authState by viewModel.userAuthState.collectAsState()

    val currentTab by viewModel.currentTab.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val showCreateSheet by viewModel.showCreatePostSheet.collectAsState()
    val showEditProfile by viewModel.showEditProfileDialog.collectAsState()
    val showShareProfile by viewModel.showShareProfileDialog.collectAsState()
    val activeCommentPost by viewModel.activeCommentPost.collectAsState()
    val activeStory by viewModel.activeStory.collectAsState()
    val showNotifications by viewModel.showNotifications.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val showRewardScreen by viewModel.showRewardScreen.collectAsState()
    val showAdminScreen by viewModel.showAdminScreen.collectAsState()
    val showMonetizationScreen by viewModel.showMonetizationScreen.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val friendOptionsTarget by viewModel.friendOptionsTarget.collectAsState()
    val selectedFriendDetail by viewModel.selectedFriendDetail.collectAsState()
    val showGiftDialogForFriend by viewModel.showGiftDialogForFriend.collectAsState()
    val showCoverPhotoOptions by viewModel.showCoverPhotoOptions.collectAsState()
    val showAvatarOptions by viewModel.showAvatarOptions.collectAsState()
    val fullScreenPhotoPreview by viewModel.fullScreenPhotoPreview.collectAsState()
    val showAiArtGenerator by viewModel.showAiArtGenerator.collectAsState()
    val showPresetGallery by viewModel.showPresetGallery.collectAsState()

    val unreadCount = remember(notifications) {
        val unread = notifications.count { !it.isRead }
        if (unread > 9) "9+" else if (unread > 0) "$unread" else "9+"
    }

    val isInChatThread by viewModel.isInChatThread.collectAsState()
    val showMainTopBar = !(currentTab == MainTab.CHAT || currentTab == MainTab.REELS || currentTab == MainTab.SEARCH)
    val showMainBottomBar = !(currentTab == MainTab.CHAT && isInChatThread)

    // Scroll-to-hide logic
    var isBarsVisible by remember { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Do not hide bars in Reels tab; keep the comment box visible
                if (currentTab == MainTab.REELS) {
                    isBarsVisible = true
                    return Offset.Zero
                }
                
                val delta = available.y
                if (delta < -15f) { // Scrolling down
                    isBarsVisible = false
                } else if (delta > 15f) { // Scrolling up
                    isBarsVisible = true
                }
                return Offset.Zero
            }
        }
    }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Dynamic System Bars Control
    LaunchedEffect(currentTab) {
        val isReels = currentTab == MainTab.REELS
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (isReels) {
                androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                androidx.activity.SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            },
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
    }

    // Unified Top-Level BackHandler
    BackHandler {
        when {
            showMonetizationScreen -> viewModel.closeMonetizationScreen()
            showRewardScreen -> viewModel.closeRewardScreen()
            showAdminScreen -> viewModel.closeAdminScreen()
            showSettings -> viewModel.closeSettings()
            showNotifications -> viewModel.closeNotifications()
            fullScreenPhotoPreview != null -> viewModel.closeFullScreenPhotoPreview()
            activeStory != null -> viewModel.closeStory()
            activeCommentPost != null -> viewModel.closeComments()
            showCreateSheet -> viewModel.closeCreatePostSheet()
            showEditProfile -> viewModel.closeEditProfile()
            showShareProfile -> viewModel.closeShareProfile()
            showGiftDialogForFriend != null -> viewModel.closeGiftDialog()
            selectedFriendDetail != null -> viewModel.closeFriendProfile()
            friendOptionsTarget != null -> viewModel.closeFriendOptions()
            showCoverPhotoOptions -> viewModel.closeCoverPhotoOptions()
            showAvatarOptions -> viewModel.closeAvatarOptions()
            showAiArtGenerator != null -> viewModel.closeAiArtGenerator()
            showPresetGallery != null -> viewModel.closePresetGallery()
            currentTab == MainTab.CHAT && isInChatThread -> viewModel.closeDirectThread()
            currentTab != MainTab.HOME -> viewModel.setTab(MainTab.HOME)
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (!authState.isLoggedIn) {
        AuthScreen(viewModel = viewModel)
    } else if (showMonetizationScreen) {
        com.example.ui.screens.MonetizationScreen(
            viewModel = viewModel,
            onBack = { viewModel.closeMonetizationScreen() }
        )
    } else if (showRewardScreen) {
        RewardScreen(
            viewModel = viewModel,
            onBack = { viewModel.closeRewardScreen() }
        )
    } else if (showAdminScreen) {
        AdminPanelScreen(
            viewModel = viewModel,
            onBack = { viewModel.closeAdminScreen() }
        )
    } else if (showSettings) {
        FullScreenSettings(
            viewModel = viewModel,
            onBack = { viewModel.closeSettings() }
        )
    } else if (showNotifications) {
        NotificationScreen(
            viewModel = viewModel,
            onBack = { viewModel.closeNotifications() }
        )
    } else {

    Box(modifier = modifier
        .fillMaxSize()
        .nestedScroll(nestedScrollConnection)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0.dp), 
            containerColor = if (currentTab == MainTab.REELS) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.background,
            topBar = {},
            bottomBar = {}
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // No padding(padding) here to allow content behind gesture navigation
                    .nestedScroll(nestedScrollConnection)
            ) {
                // 1. Main Content (Fills entire screen)
                Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                    when (tab) {
                        MainTab.HOME -> HomeScreen(viewModel = viewModel)
                        MainTab.SEARCH -> ExploreScreen(viewModel = viewModel)
                        MainTab.CHAT -> ChatScreen(viewModel = viewModel)
                        MainTab.REELS -> ReelsScreen(viewModel = viewModel)
                        MainTab.PROFILE -> ProfileScreen(viewModel = viewModel)
                    }
                }

                // 2. Floating Top Bar
                if (showMainTopBar) {
                    VynTopBar(
                        title = "Vyn9",
                        showBack = currentTab == MainTab.PROFILE,
                        onBackClick = { viewModel.setTab(MainTab.HOME) },
                        showSettings = currentTab == MainTab.PROFILE,
                        onSettingsClick = { viewModel.openSettings() },
                        notificationCount = unreadCount,
                        onNotificationClick = { viewModel.openNotifications() },
                        onRewardClick = { viewModel.openRewardScreen() },
                        onPlusClick = { viewModel.openCreatePostSheet() },
                        onChatClick = { viewModel.setTab(MainTab.CHAT) },
                        isVisible = isBarsVisible,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                // 3. Floating Bottom Bar
                if (showMainBottomBar) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        if (currentTab == MainTab.REELS) {
                            ReelsBottomBar(
                                onCommentClick = { 
                                    viewModel.openActiveReelComments()
                                },
                                isVisible = isBarsVisible
                            )
                        } else {
                            VynBottomNavBar(
                                currentTab = currentTab,
                                onTabSelected = { viewModel.setTab(it) },
                                onPlusClick = { viewModel.openCreatePostSheet() },
                                isVisible = isBarsVisible
                            )
                        }
                    }
                }

                // 4. Floating Action Button (+) for home/explore
                if (currentTab == MainTab.HOME || currentTab == MainTab.SEARCH) {
                    FloatingActionButton(
                        onClick = { viewModel.openCreatePostSheet() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = if (isBarsVisible) 80.dp else 30.dp)
                            .testTag("main_fab_add_button"),
                        containerColor = VynButtonBg,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Post or Story",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // Active Dialogs & Sheets
        if (showCreateSheet) {
            CreatePostBottomSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.closeCreatePostSheet() }
            )
        }

        if (showEditProfile) {
            EditProfileDialog(
                profile = profile,
                onDismiss = { viewModel.closeEditProfile() },
                onSave = { name, bio, location ->
                    viewModel.updateProfile(name, bio, location)
                }
            )
        }

        if (showShareProfile) {
            ShareProfileDialog(
                profile = profile,
                onDismiss = { viewModel.closeShareProfile() },
                onShareExternal = { viewModel.shareProfile(context, profile.handle) }
            )
        }

        activeCommentPost?.let { post ->
            CommentsBottomSheet(
                post = post,
                viewModel = viewModel,
                onDismiss = { viewModel.closeComments() }
            )
        }

        activeStory?.let { story ->
            StoryViewerDialog(
                story = story,
                onDismiss = { viewModel.closeStory() }
            )
        }

        // Facebook-Style Friend Action Options BottomSheet
        friendOptionsTarget?.let { friend ->
            FacebookFriendActionBottomSheet(
                friend = friend,
                onDismiss = { viewModel.closeFriendOptions() },
                onMessage = { viewModel.openDirectChatWithFriend(friend) },
                onViewProfile = { viewModel.openFriendProfile(friend) },
                onWave = { viewModel.sendPokeOrWave(friend.id) },
                onSendGift = { viewModel.openGiftDialog(friend) },
                onToggleCloseFriend = { viewModel.toggleCloseFriend(friend.id) },
                onToggleMute = { viewModel.toggleMute(friend.id) },
                onUnfriend = { viewModel.unfriend(friend.id) },
                onBlock = { viewModel.blockUser(friend.id) },
                onShare = {
                    com.example.util.ShareUtils.shareProfile(context, friend.handle)
                    viewModel.closeFriendOptions()
                }
            )
        }

        // Friend Profile Preview Sheet
        selectedFriendDetail?.let { friend ->
            FriendProfilePreviewBottomSheet(
                friend = friend,
                onDismiss = { viewModel.closeFriendProfile() },
                onMessage = { viewModel.openDirectChatWithFriend(friend) },
                onWave = { viewModel.sendPokeOrWave(friend.id) },
                onSendGift = { viewModel.openGiftDialog(friend) },
                onFollowToggle = { viewModel.toggleFollow(friend.id) },
                onOptionsClick = { viewModel.openFriendOptions(friend) }
            )
        }

        // Send Gift Dialog
        showGiftDialogForFriend?.let { friend ->
            SendGiftRewardDialog(
                friend = friend,
                onDismiss = { viewModel.closeGiftDialog() },
                onConfirmSend = { amount ->
                    viewModel.sendGiftCredits(friend.id, amount)
                    android.widget.Toast.makeText(context, "Sent $amount Credits to @${friend.handle} 🎁💰", android.widget.Toast.LENGTH_LONG).show()
                }
            )
        }

        // Cover Photo Options BottomSheet
        if (showCoverPhotoOptions) {
            CoverPhotoOptionsBottomSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.closeCoverPhotoOptions() }
            )
        }

        // Profile Picture Options BottomSheet
        if (showAvatarOptions) {
            ProfilePictureOptionsBottomSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.closeAvatarOptions() }
            )
        }

        // Full Screen Photo Viewer Dialog
        fullScreenPhotoPreview?.let { previewData ->
            FullScreenPhotoViewerDialog(
                data = previewData,
                onDismiss = { viewModel.closeFullScreenPhotoPreview() }
            )
        }

        // AI Art Generator Dialog
        showAiArtGenerator?.let { genType ->
            AiArtGeneratorDialog(
                type = genType,
                viewModel = viewModel,
                onDismiss = { viewModel.closeAiArtGenerator() }
            )
        }

        // Preset Gallery Dialog
        showPresetGallery?.let { presetType ->
            PresetGalleryDialog(
                type = presetType,
                viewModel = viewModel,
                onDismiss = { viewModel.closePresetGallery() }
            )
        }
    }
}
}
