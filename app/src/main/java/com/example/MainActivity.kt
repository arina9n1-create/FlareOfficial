package com.example

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.example.data.notification.BatteryOptimizationHelper
import com.example.data.notification.CallRingingService
import com.example.data.notification.FlareFirebaseMessagingService
import com.example.data.notification.NotificationHelper
import com.example.data.notification.NotificationPreferences
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.*
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

        // 2.5 Fetch & upload the FCM registration token (push notifications).
        // Retries automatically on each app start until the upload succeeds.
        try {
            Log.d("MainActivity", "Initializing FCM token...")
            FlareFirebaseMessagingService.fetchAndUploadToken(this)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Error initializing FCM token", e)
        }

        // 2.6 OEM Survival: swiping the app away on Vivo/MIUI kills FCM connection.
        // Prompt once to exempt the app from battery management.
        try {
            maybeRequestBatteryOptOut()
        } catch (e: Throwable) {
            Log.e("MainActivity", "Error requesting battery opt-out", e)
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
        refreshFcmRegistration()
        maybeRequestBatteryOptOut(onResume = true)
    }

    /**
     * Re-establishes the Firebase Messaging registration whenever the app is
     * brought back into the foreground (e.g. reopened after being swiped away
     * from Recent Apps). Swiping the app away can tear down the Firebase
     * connection on some OEMs (Vivo/MIUI battery optimization); refreshing the
     * FCM token here re-registers the device so push notifications resume.
     *
     * Intent evaluation is idempotent: if there is no signed-in session the
     * registration is silently skipped, and repeated calls with the same token
     * are upserted atomically on the backend.
     */
    private fun refreshFcmRegistration() {
        // Re-request the POST_NOTIFICATIONS permission if it is still missing, so
        // a previously-denied (or wiped) grant is re-prompted on resume instead of
        // silently dropping every notification via hasNotificationPermission().
        try {
            checkAndRequestNotificationPermission()
        } catch (e: Throwable) {
            Log.e("MainActivity", "Error re-requesting notification permission", e)
        }

        // Re-establish the Firebase Messaging registration whenever the app is
        // brought back into the foreground (e.g. reopened after being swiped away
        // from Recent Apps). Swiping the app away can tear down the Firebase
        // connection on some OEMs (Vivo/MIUI battery optimization); refreshing the
        // FCM token here re-registers the device so push notifications resume.
        try {
            FlareFirebaseMessagingService.fetchAndUploadToken(this)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Error refreshing FCM token", e)
        }

        // Pull any notifications that arrived while the Firebase connection was
        // suspended, so the in-app list / unread badge reflect them immediately.
        try {
            viewModel.resumeAppSync()
        } catch (e: Throwable) {
            Log.e("MainActivity", "Error syncing missed notifications", e)
        }
    }

    private fun maybeRequestBatteryOptOut(onResume: Boolean = false) {
        Log.e("MainActivity", "Checking battery opt-out status...")
        val prefs = getSharedPreferences("flareofficial_auth_prefs", Context.MODE_PRIVATE)
        if (BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            Log.e("MainActivity", "Already ignoring battery optimizations.")
            return
        }

        // Avoid nagging the user on every resume: on cold start show the prompt
        // right away; on resume, re-ask at most once every 12 hours and only if
        // the device is STILL not ignoring battery optimizations.
        val now = System.currentTimeMillis()
        if (onResume) {
            val lastPrompt = prefs.getLong("battery_opt_last_prompt_ms", 0L)
            if (lastPrompt > 0 && now - lastPrompt < 12 * 60 * 60 * 1000L) {
                Log.e("MainActivity", "Battery prompt shown recently; skipping.")
                return
            }
        }

        Log.e("MainActivity", "Prompting user for battery opt-out in 3s...")
        prefs.edit().putLong("battery_opt_last_prompt_ms", now).apply()
        window.decorView.postDelayed({
            BatteryOptimizationHelper.showOptOutPrompt(this)
        }, 3000)
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
            NotificationHelper.SCREEN_INCOMING_CALL -> {
                // Production incoming-call push landing point.
                val callId = targetIntent.getStringExtra(NotificationHelper.EXTRA_CALL_ID) 
                    ?: targetIntent.getStringExtra("call_id") ?: ""
                val agoraChannel = targetIntent.getStringExtra(NotificationHelper.EXTRA_AGORA_CHANNEL) ?: ""
                val callType = targetIntent.getStringExtra(NotificationHelper.EXTRA_CALL_TYPE)
                val callerName = targetIntent.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME)
                val callerHandle = targetIntent.getStringExtra(NotificationHelper.EXTRA_CALLER_HANDLE)
                
                Log.d(
                    "MainActivity",
                    "Incoming call notification tapped: caller=$callerHandle, agoraChannel=$agoraChannel"
                )

                // Stop the ringing service if it is still active.
                try {
                    val stopServiceIntent = Intent(this, CallRingingService::class.java)
                    stopService(stopServiceIntent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to stop CallRingingService", e)
                }
                
                // If the user tapped Accept, trigger the accept flow in the ViewModel.
                if (targetIntent.action == "ACCEPT_CALL") {
                    viewModel.acceptIncomingCall(
                        explicitCallId = callId,
                        explicitAgoraChannel = agoraChannel,
                        explicitCallType = callType,
                        explicitCallerName = callerName,
                        explicitCallerHandle = callerHandle
                    )
                }
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
    val showWalletScreen by viewModel.showWalletScreen.collectAsState()
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

    val activeCallState by viewModel.activeCallState.collectAsState()
    val incomingCall by viewModel.incomingCall.collectAsState()
    val localRenderer by viewModel.agoraCallManager.localVideoRenderer.collectAsState()
    val remoteRenderer by viewModel.agoraCallManager.remoteVideoRenderer.collectAsState()

    // Warm the reels disk cache from ANY tab (home/profile/settings), so by the
    // time the user opens the REELS tab the first reel is already on disk and
    // starts instantly — no initial loading.
    val warmReels by viewModel.reels.collectAsState()
    LaunchedEffect(warmReels) {
        if (warmReels.isNotEmpty()) {
            val resolved = com.example.util.MediaStorageResolver.resolve(warmReels.first().videoUrl)
            if (resolved.isNotBlank()) {
                com.example.media.player.ExoPlayerCacheManager.preloadVideo(context, resolved)
            }
        }
    }

    val unreadCount = remember(notifications) {
        val unread = notifications.count { !it.isRead }
        if (unread > 9) "9+" else if (unread > 0) "$unread" else "9+"
    }

    val isInChatThread by viewModel.isInChatThread.collectAsState()
    val chatOverlayOpen by viewModel.chatOverlayOpen.collectAsState()
    val showMainTopBar = !(currentTab == MainTab.CHAT || currentTab == MainTab.REELS || currentTab == MainTab.SEARCH)
    val showMainBottomBar = !(currentTab == MainTab.CHAT && (isInChatThread || chatOverlayOpen))

    // Scroll-to-hide logic
    var isBarsVisible by remember { mutableStateOf(true) }
    // Accumulated downward scroll. The bar only starts hiding AFTER the user has
    // scrolled a meaningful distance — the first small scroll near the top of the
    // home feed just moves content under the bar without hiding it.
    var hideAccum by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Do not hide bars in Reels tab; keep the comment box visible
                if (currentTab == MainTab.REELS) {
                    isBarsVisible = true
                    hideAccum = 0f
                    return Offset.Zero
                }

                val delta = available.y
                if (delta < 0f) { // Scrolling down (content moves up)
                    hideAccum += -delta
                    if (hideAccum > 350f) { // hide only after a real scroll
                        isBarsVisible = false
                    }
                } else if (delta > 0f) { // Scrolling up — bar comes back immediately
                    hideAccum = 0f
                    isBarsVisible = true
                }
                return Offset.Zero
            }
        }
    }

    // Bar is always visible when switching tabs
    LaunchedEffect(currentTab) {
        isBarsVisible = true
        hideAccum = 0f
    }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Dynamic System Bars Control
    LaunchedEffect(currentTab, activeStory, fullScreenPhotoPreview, authState.isLoggedIn) {
        val isDarkStatusBarNeeded = (currentTab == MainTab.REELS || 
                                   activeStory != null || 
                                   fullScreenPhotoPreview != null) && authState.isLoggedIn
        
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (isDarkStatusBarNeeded) {
                androidx.activity.SystemBarStyle.dark(android.graphics.Color.BLACK)
            } else {
                androidx.activity.SystemBarStyle.light(
                    android.graphics.Color.WHITE,
                    android.graphics.Color.WHITE
                )
            },
            navigationBarStyle = if (isDarkStatusBarNeeded) {
                androidx.activity.SystemBarStyle.dark(android.graphics.Color.BLACK)
            } else {
                androidx.activity.SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            }
        )
    }

    // Unified Top-Level BackHandler
    BackHandler {
        when {
            showMonetizationScreen -> viewModel.closeMonetizationScreen()
            showWalletScreen -> viewModel.closeWalletScreen()
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

    // Warm up the media cache + token as soon as the app opens (not just when
    // the Reels tab is entered), so the first reel plays instantly.
    if (authState.isLoggedIn) {
        LaunchedEffect(authState.isLoggedIn) {
            com.example.media.player.ExoPlayerCacheManager.warmUp(context)
        }
    }

    val isReels = currentTab == MainTab.REELS
    val isDarkTheme = (isReels || activeStory != null || fullScreenPhotoPreview != null) && authState.isLoggedIn

    // Root-level IME padding: with edge-to-edge enabled, adjustResize alone cannot
    // resize the window, so every screen's content is lifted above the keyboard here.
    // Scrollable screens can then bring any focused text field above the keyboard.
    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        // --- SCREEN CONTENT ---
        if (!authState.isLoggedIn) {
            AuthScreen(viewModel = viewModel)
        } else if (showMonetizationScreen) {
            com.example.ui.screens.MonetizationScreen(
                viewModel = viewModel,
                onBack = { viewModel.closeMonetizationScreen() }
            )
        } else if (showWalletScreen) {
            com.example.ui.screens.WalletScreen(
                viewModel = viewModel,
                onBack = { viewModel.closeWalletScreen() }
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
            // Main Scaffold-based UI
            Box(modifier = modifier.fillMaxSize()) {
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
                            FlareTopBar(
                                title = "FlareOfficial",
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
                                    FlareBottomNavBar(
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
                            // Auto-detect the system navigation bar height (gesture vs
                            // 3-button navigation) so the + button clears the bottom bar
                            // on EVERY device, regardless of screen size.
                            val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            FloatingActionButton(
                                onClick = { viewModel.openCreatePostSheet() },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 20.dp, bottom = if (isBarsVisible) navBottomInset + 88.dp else navBottomInset + 28.dp)
                                    .testTag("main_fab_add_button"),
                                containerColor = FlareButtonBg,
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
            }
        }

        // --- GLOBAL OVERLAYS (Dialogs, BottomSheets) ---
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

        selectedFriendDetail?.let { friend ->
            val viewedPosts by viewModel.viewedUserPosts.collectAsState()
            val viewedReels by viewModel.viewedUserReels.collectAsState()
            FriendProfileFullScreen(
                friend = friend,
                onBack = { viewModel.closeFriendProfile() },
                onMessage = { viewModel.openDirectChatWithFriend(friend) },
                onWave = { viewModel.sendPokeOrWave(friend.id) },
                onSendGift = { viewModel.openGiftDialog(friend) },
                onOptionsClick = { viewModel.openFriendOptions(friend) },
                onFollowToggle = { viewModel.toggleFollow(friend.id) },
                posts = viewedPosts,
                reels = viewedReels
            )
        }

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

        if (showCoverPhotoOptions) {
            CoverPhotoOptionsBottomSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.closeCoverPhotoOptions() }
            )
        }

        if (showAvatarOptions) {
            ProfilePictureOptionsBottomSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.closeAvatarOptions() }
            )
        }

        fullScreenPhotoPreview?.let { previewData ->
            FullScreenPhotoViewerDialog(
                data = previewData,
                onDismiss = { viewModel.closeFullScreenPhotoPreview() }
            )
        }

        showAiArtGenerator?.let { genType ->
            AiArtGeneratorDialog(
                type = genType,
                viewModel = viewModel,
                onDismiss = { viewModel.closeAiArtGenerator() }
            )
        }

        showPresetGallery?.let { presetType ->
            PresetGalleryDialog(
                type = presetType,
                viewModel = viewModel,
                onDismiss = { viewModel.closePresetGallery() }
            )
        }

        // Modern High-Definition Video & Audio Call Overlay (global scope)
        activeCallState?.let { call ->
            ModernFlareOfficialCallOverlay(
                call = call,
                onEndCall = { viewModel.endCall() },
                onToggleMute = { viewModel.toggleCallMute() },
                onToggleCamera = { viewModel.toggleCallCamera() },
                onToggleSpeaker = { viewModel.toggleCallSpeaker() },
                onFlipCamera = { viewModel.flipCallCamera() },
                onToggleScreenShare = { viewModel.toggleScreenSharing() },
                localRenderer = localRenderer,
                remoteRenderer = remoteRenderer
            )
        }

        // Global Incoming Call Dialog
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FlareAvatar(avatarType = signal.callerAvatar, size = 64.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = signal.callerName.ifBlank { "@${signal.callerHandle}" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${signal.callerHandle}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FlareTextSecondary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.acceptIncomingCall() }) {
                        Text("Accept", color = FlareOfficialPink)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.rejectIncomingCall() }) {
                        Text("Decline", color = FlareTextSecondary)
                    }
                }
            )
        }

        // --- GLOBAL SOLID STATUS BAR SCRIM ---
        // This stays on top of EVERYTHING to ensure icons are always readable.
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(if (isDarkTheme) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White)
                .align(Alignment.TopCenter)
        )
    }
}
