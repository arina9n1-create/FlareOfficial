package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthUserState
import com.example.data.db.AppDatabase
import com.example.data.model.*
import com.example.data.remote.SupabaseService
import com.example.data.repository.SocialRepository
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class MainTab {
    HOME,
    SEARCH,
    CHAT,
    REELS,
    PROFILE
}

enum class ProfileSubTab {
    GRID,
    REELS,
    FRIENDS,
    REPOSTS,
    SAVED
}

enum class SettingsPage {
    MAIN,
    PERSONAL_INFO,
    PRIVACY_SECURITY,
    NOTIFICATIONS,
    ACCOUNT_SYNC,
    HELP_SUPPORT,
    TERMS_PRIVACY,
    ABOUT_APP
}

data class LiveChatRoom(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: String, // "GLOBAL", "COMMUNITY", "DM"
    val avatarType: String,
    val onlineCount: Int = 0,
    val unreadCount: Int = 0,
    val verified: Boolean = false,
    val lastMessageTime: String = "Just now",
    val hasStory: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val isFavorite: Boolean = false
)

data class InstagramNote(
    val id: String,
    val name: String,
    val handle: String,
    val avatarType: String,
    val noteText: String,
    val musicTrack: String? = null,
    val isMe: Boolean = false
)

data class CallState(
    val partnerName: String,
    val partnerAvatar: String,
    val isVideo: Boolean,
    val isMuted: Boolean = false,
    val isCameraOn: Boolean = true,
    val isSpeakerOn: Boolean = true,
    val isFrontCamera: Boolean = true,
    val isScreenSharing: Boolean = false,
    val durationSec: Int = 0,
    val status: String = "Connected",
    val connectionQuality: String = "HD · 1080p 60fps",
    val audioVolume: Float = 0.65f,
    val callId: String = "",
    val remoteHandle: String = "",
    val isOutgoing: Boolean = true
)

data class OnlineMember(
    val id: String,
    val name: String,
    val handle: String,
    val avatarType: String,
    val avatarPath: String? = null,
    val status: String = "Active now",
    val isVerified: Boolean = false,
    val isOnline: Boolean = true
)

data class ChatTranslationSettings(
    val outgoingToEnglish: Boolean = false, // When user types in Bangla/Banglish, translates to English for recipient
    val incomingToBangla: Boolean = false   // When recipient types in English, translates to Bangla for user
)

class SocialViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SocialRepository
    private val supabaseService: SupabaseService = SupabaseService(application)
    private val authRepository: AuthRepository = AuthRepository(application)

    // New Repositories

    private val rewardRepository: com.example.data.repository.RewardRepository = com.example.data.repository.RewardRepository(application)
    private val monetizationRepository: com.example.data.repository.MonetizationRepository = com.example.data.repository.MonetizationRepository(application)

    val userAuthState: StateFlow<AuthUserState> = authRepository.userState

    val rewardWallet = rewardRepository.wallet
    val adminConfig = rewardRepository.adminConfig
    val rewardTasks = rewardRepository.tasks
    val withdrawals = rewardRepository.withdrawals
    private val _autoSyncEnabled = MutableStateFlow(false)
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()
    val referrals = rewardRepository.referrals
    val selectedCurrency = rewardRepository.selectedCurrency

    // Native Monetization States
    val monetizationSettings = monetizationRepository.settings
    val userMonetizationProfile = monetizationRepository.userProfile
    val monetizationApplications = monetizationRepository.applications
    val earningsWallet = monetizationRepository.wallet
    val monetizationTransactions = monetizationRepository.transactions

    // Phase 2: Revenue Attribution State Flows & Manager
    val adAttributionManager: com.example.data.repository.AdAttributionManager = com.example.data.repository.AdAttributionManager(application, monetizationRepository)
    val revenuePeriods = monetizationRepository.revenuePeriods
    val adMobReports = monetizationRepository.adMobReports
    val adUnitMappings = monetizationRepository.adUnitMappings
    val contentImpressions = monetizationRepository.contentImpressions
    val contentEarnings = monetizationRepository.contentEarnings
    val revenueAdjustments = monetizationRepository.revenueAdjustments
    val attributionDiagnostics = monetizationRepository.attributionDiagnostics
    val boostCampaigns = monetizationRepository.boostCampaigns

    private val _showPostBoostDialogForPost = MutableStateFlow<PostEntity?>(null)
    val showPostBoostDialogForPost: StateFlow<PostEntity?> = _showPostBoostDialogForPost.asStateFlow()

    private val _showMonetizationScreen = MutableStateFlow(false)
    val showMonetizationScreen: StateFlow<Boolean> = _showMonetizationScreen.asStateFlow()

    private val _showRewardScreen = MutableStateFlow(false)
    val showRewardScreen: StateFlow<Boolean> = _showRewardScreen.asStateFlow()

    private val _showAdminScreen = MutableStateFlow(false)
    val showAdminScreen: StateFlow<Boolean> = _showAdminScreen.asStateFlow()

    val posts: StateFlow<List<PostEntity>>
    val profile: StateFlow<UserProfileEntity>
    val stories: StateFlow<List<StoryEntity>>
    val savedPosts: StateFlow<List<PostEntity>>
    val repostedPosts: StateFlow<List<PostEntity>>
    val chatMessages: StateFlow<List<ChatMessageEntity>>
    val notifications: StateFlow<List<NotificationEntity>>
    val allConnections: StateFlow<List<FriendEntity>>
    val friends: StateFlow<List<FriendEntity>>
    val allUsers: StateFlow<List<AppUserEntity>>
    val allReels: StateFlow<List<ReelEntity>>
    val reels: StateFlow<List<ReelEntity>> get() = allReels

    /** Reel upload progress: null = idle, 0-100 = percent uploaded. */
    private val _reelUploadProgress = MutableStateFlow<Int?>(null)
    val reelUploadProgress: StateFlow<Int?> = _reelUploadProgress.asStateFlow()

    private val _currentUserEntity = MutableStateFlow<AppUserEntity?>(null)
    val currentUserEntity: StateFlow<AppUserEntity?> = _currentUserEntity.asStateFlow()

    val currentUserRole: StateFlow<UserRole> = _currentUserEntity.map { entity ->
        UserRole.fromString(entity?.role)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UserRole.USER
    )

    // Friends Interaction States
    private val _selectedFriendDetail = MutableStateFlow<FriendEntity?>(null)
    val selectedFriendDetail: StateFlow<FriendEntity?> = _selectedFriendDetail.asStateFlow()

    private val _friendOptionsTarget = MutableStateFlow<FriendEntity?>(null)
    val friendOptionsTarget: StateFlow<FriendEntity?> = _friendOptionsTarget.asStateFlow()

    private val _showGiftDialogForFriend = MutableStateFlow<FriendEntity?>(null)
    val showGiftDialogForFriend: StateFlow<FriendEntity?> = _showGiftDialogForFriend.asStateFlow()

    private val _showFriendsListFullScreen = MutableStateFlow(false)
    val showFriendsListFullScreen: StateFlow<Boolean> = _showFriendsListFullScreen.asStateFlow()

    // Live Multi-User Chat States
    private val _activeRoomId = MutableStateFlow("")
    val activeRoomId: StateFlow<String> = _activeRoomId.asStateFlow()

    private val _typingStatus = MutableStateFlow<String?>(null)
    val typingStatus: StateFlow<String?> = _typingStatus.asStateFlow()

    private val _showOnlineMembersSheet = MutableStateFlow(false)
    val showOnlineMembersSheet: StateFlow<Boolean> = _showOnlineMembersSheet.asStateFlow()

    private val _showChatDetailsSheet = MutableStateFlow(false)
    val showChatDetailsSheet: StateFlow<Boolean> = _showChatDetailsSheet.asStateFlow()

    // Per-Chat Translation Settings (Defaults to OFF / Original for all rooms unless user toggles for a specific chat)
    private val _roomTranslationSettings = MutableStateFlow<Map<String, ChatTranslationSettings>>(emptyMap())
    val roomTranslationSettings: StateFlow<Map<String, ChatTranslationSettings>> = _roomTranslationSettings.asStateFlow()

    private val _chatTheme = MutableStateFlow("Classic Instagram")
    val chatTheme: StateFlow<String> = _chatTheme.asStateFlow()

    private val _disappearingDuration = MutableStateFlow("Off")
    val disappearingDuration: StateFlow<String> = _disappearingDuration.asStateFlow()

    private val _userSearchQuery = MutableStateFlow("")
    val userSearchQuery: StateFlow<String> = _userSearchQuery.asStateFlow()
    private val _userSearchLoading = MutableStateFlow(false)
    val userSearchLoading: StateFlow<Boolean> = _userSearchLoading.asStateFlow()
    private val _userSearchError = MutableStateFlow<String?>(null)
    val userSearchError: StateFlow<String?> = _userSearchError.asStateFlow()
    val onlineMembers: StateFlow<List<OnlineMember>>
    val userSearchResults: StateFlow<List<AppUserEntity>>
    private val _availableRooms = MutableStateFlow<List<LiveChatRoom>>(emptyList())
    val availableRooms: StateFlow<List<LiveChatRoom>> = _availableRooms.asStateFlow()

    private val _isInChatThread = MutableStateFlow(false)
    val isInChatThread: StateFlow<Boolean> = _isInChatThread.asStateFlow()

    /** True when the VYN NUMBER or Personal ID full-screen chat overlay is open. */
    private val _chatOverlayOpen = MutableStateFlow(false)
    val chatOverlayOpen: StateFlow<Boolean> = _chatOverlayOpen.asStateFlow()

    /** Called by ChatScreen when a VYN NUMBER / Personal ID overlay opens or closes. */
    fun setChatOverlayOpen(open: Boolean) {
        _chatOverlayOpen.value = open
    }

    private val _directSearchQuery = MutableStateFlow("")
    val directSearchQuery: StateFlow<String> = _directSearchQuery.asStateFlow()

    private val _directInboxTab = MutableStateFlow("PRIMARY") // "PRIMARY", "GENERAL", "CHANNELS", "REQUESTS"
    val directInboxTab: StateFlow<String> = _directInboxTab.asStateFlow()

    private val _notesList = MutableStateFlow<List<InstagramNote>>(emptyList())
    val notesList: StateFlow<List<InstagramNote>> = _notesList.asStateFlow()

    private val _activeCallState = MutableStateFlow<CallState?>(null)
    val activeCallState: StateFlow<CallState?> = _activeCallState.asStateFlow()

    private val _incomingCall = MutableStateFlow<CallSignalEntity?>(null)
    val incomingCall: StateFlow<CallSignalEntity?> = _incomingCall.asStateFlow()

    val webRtcCallManager: com.example.media.WebRtcCallManager =
        com.example.media.WebRtcCallManager(getApplication())

    private var currentCallPollJob: kotlinx.coroutines.Job? = null

    private val _showNoteCreatorDialog = MutableStateFlow(false)
    val showNoteCreatorDialog: StateFlow<Boolean> = _showNoteCreatorDialog.asStateFlow()

    private val _showNewMessageDialog = MutableStateFlow(false)
    val showNewMessageDialog: StateFlow<Boolean> = _showNewMessageDialog.asStateFlow()

    private val _currentTab = MutableStateFlow(MainTab.HOME)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    private val _profileSubTab = MutableStateFlow(ProfileSubTab.GRID)
    val profileSubTab: StateFlow<ProfileSubTab> = _profileSubTab.asStateFlow()

    // Sheet / Dialog states
    private val _showCreatePostSheet = MutableStateFlow(false)
    val showCreatePostSheet: StateFlow<Boolean> = _showCreatePostSheet.asStateFlow()

    private val _showEditProfileDialog = MutableStateFlow(false)
    val showEditProfileDialog: StateFlow<Boolean> = _showEditProfileDialog.asStateFlow()

    private val _showShareProfileDialog = MutableStateFlow(false)
    val showShareProfileDialog: StateFlow<Boolean> = _showShareProfileDialog.asStateFlow()

    private val _activeCommentPost = MutableStateFlow<PostEntity?>(null)
    val activeCommentPost: StateFlow<PostEntity?> = _activeCommentPost.asStateFlow()

    private val _activeStory = MutableStateFlow<StoryEntity?>(null)
    val activeStory: StateFlow<StoryEntity?> = _activeStory.asStateFlow()

    private val _showNotifications = MutableStateFlow(false)
    val showNotifications: StateFlow<Boolean> = _showNotifications.asStateFlow()

    private val _activeReelPage = MutableStateFlow(0)
    val activeReelPage: StateFlow<Int> = _activeReelPage.asStateFlow()

    private val _triggerReelComments = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerReelComments = _triggerReelComments.asSharedFlow()

    fun setActiveReelPage(page: Int) {
        _activeReelPage.value = page
    }

    fun openActiveReelComments() {
        _triggerReelComments.tryEmit(Unit)
    }

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _settingsPage = MutableStateFlow(SettingsPage.MAIN)
    val settingsPage: StateFlow<SettingsPage> = _settingsPage.asStateFlow()

    private val _activeChatPartner = MutableStateFlow<String?>(null)
    val activeChatPartner: StateFlow<String?> = _activeChatPartner.asStateFlow()

    // Cover Photo & Profile Picture State Management
    private val _showCoverPhotoOptions = MutableStateFlow(false)
    val showCoverPhotoOptions: StateFlow<Boolean> = _showCoverPhotoOptions.asStateFlow()

    private val _showAvatarOptions = MutableStateFlow(false)
    val showAvatarOptions: StateFlow<Boolean> = _showAvatarOptions.asStateFlow()

    private val _fullScreenPhotoPreview = MutableStateFlow<FullScreenPhotoData?>(null)
    val fullScreenPhotoPreview: StateFlow<FullScreenPhotoData?> = _fullScreenPhotoPreview.asStateFlow()

    private val _showAiArtGenerator = MutableStateFlow<String?>(null) // "cover" or "avatar"
    val showAiArtGenerator: StateFlow<String?> = _showAiArtGenerator.asStateFlow()

    private val _showPresetGallery = MutableStateFlow<String?>(null) // "cover" or "avatar"
    val showPresetGallery: StateFlow<String?> = _showPresetGallery.asStateFlow()

    private val _selectedAvatarFrame = MutableStateFlow("none")
    val selectedAvatarFrame: StateFlow<String> = _selectedAvatarFrame.asStateFlow()

    fun shareProfile(context: android.content.Context, handle: String) {
        com.example.util.ShareUtils.shareProfile(context, handle)
    }

    fun sharePost(context: android.content.Context, post: PostEntity) {
        if (post.remoteId.isBlank()) return
        com.example.util.ShareUtils.sharePost(context, post.remoteId, post.caption)
    }

    fun shareReel(context: android.content.Context, reel: ReelEntity) {
        if (reel.remoteId.isBlank()) return
        com.example.util.ShareUtils.shareReel(context, reel.remoteId, reel.caption)
    }

    fun handleDeepLink(uri: android.net.Uri?) {
        if (uri == null) return
        val path = uri.path ?: return
        android.util.Log.d("SocialViewModel", "Handling deep link: $uri")

        viewModelScope.launch {
            when {
                path.startsWith("/@") -> {
                    val handle = path.removePrefix("/@")
                    if (handle.isNotBlank()) {
                        // Check if user is public or it's me
                        val user = repository.allUsers.first().find { it.handle.equals(handle, ignoreCase = true) }
                            ?: repository.searchUsersRemote(handle).firstOrNull()
                        
                        if (user != null) {
                            if (user.isPublic || user.uid == userAuthState.value.uid) {
                                viewUserProfile(handle)
                            } else {
                                android.widget.Toast.makeText(getApplication(), "This profile is private", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } else {
                            android.widget.Toast.makeText(getApplication(), "User not found", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
                path.startsWith("/post/") -> {
                    val postId = path.removePrefix("/post/")
                    if (postId.isNotBlank()) {
                        val post = posts.value.find { it.remoteId == postId }
                        if (post != null) {
                            if (post.isPublic || post.userHandle == profile.value.handle) {
                                openComments(post)
                                setTab(MainTab.HOME)
                            } else {
                                android.widget.Toast.makeText(getApplication(), "This post is private", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } else {
                            android.widget.Toast.makeText(getApplication(), "Post not found or unavailable", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
                path.startsWith("/reel/") -> {
                    val reelId = path.removePrefix("/reel/")
                    if (reelId.isNotBlank()) {
                        val reelIndex = allReels.value.indexOfFirst { it.remoteId == reelId }
                        if (reelIndex != -1) {
                            val reel = allReels.value[reelIndex]
                            if (reel.isPublic || reel.handle == profile.value.handle) {
                                setTab(MainTab.REELS)
                                setActiveReelPage(reelIndex)
                            } else {
                                android.widget.Toast.makeText(getApplication(), "This reel is private", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } else {
                            android.widget.Toast.makeText(getApplication(), "Reel not found or unavailable", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
                path.startsWith("/video/") -> {
                    // Mapping video to reel for now as it's the primary video content
                    val videoId = path.removePrefix("/video/")
                    if (videoId.isNotBlank()) {
                        val reelIndex = allReels.value.indexOfFirst { it.remoteId == videoId }
                        if (reelIndex != -1) {
                            setTab(MainTab.REELS)
                            setActiveReelPage(reelIndex)
                        } else {
                            android.widget.Toast.makeText(getApplication(), "Video not found", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    init {
        android.util.Log.d("SocialViewModel", "Starting Production Init")
        val database = AppDatabase.getDatabase(application)
        val dao = database.socialDao()
        repository = SocialRepository(dao, supabaseService)


        posts = repository.allPosts.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        profile = repository.userProfile.map {
            it ?: UserProfileEntity()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserProfileEntity()
        )

        stories = repository.allStories.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        savedPosts = repository.savedPosts.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        repostedPosts = repository.repostedPosts.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        chatMessages = _activeRoomId.flatMapLatest { roomId ->
            repository.getChatMessagesForRoom(roomId)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        notifications = repository.notifications.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allConnections = repository.allConnections.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        friends = repository.friends.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allUsers = repository.allUsers.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allReels = repository.allReels.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        onlineMembers = allUsers.map { users ->
            users.filter { it.uid != _currentUserEntity.value?.uid }.map { user ->
                OnlineMember(
                    id = user.uid,
                    name = user.name,
                    handle = user.handle,
                    avatarType = user.avatarType,
                    avatarPath = user.avatarPath,
                    status = "Active now",
                    isVerified = user.role == "SUPER_ADMIN",
                    isOnline = true
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
        userSearchResults = _userSearchQuery
            .debounce(400)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    _userSearchLoading.value = false
                    _userSearchError.value = null
                    allUsers.map { users -> users.take(10) }
                } else {
                    flow {
                        val local = allUsers.value.filter {
                            it.name.contains(query, ignoreCase = true) || it.handle.contains(query, ignoreCase = true)
                        }
                        emit(local)

                        _userSearchLoading.value = true
                        _userSearchError.value = null
                        try {
                            val remote = repository.searchUsersRemote(query)
                            val remoteAsEntities = remote.map { prof ->
                                AppUserEntity(
                                    uid = prof.uid,
                                    name = prof.name,
                                    handle = prof.handle,
                                    email = "",
                                    avatarType = prof.avatarType,
                                    bio = prof.bio,
                                    location = prof.location
                                )
                            }
                            val combined = (allUsers.value.filter {
                                it.name.contains(query, ignoreCase = true) || it.handle.contains(query, ignoreCase = true)
                            } + remoteAsEntities).distinctBy { it.handle }
                            emit(combined)
                        } catch (e: Exception) {
                            // Don't flag network errors when the flow was merely cancelled by a new query.
                            if (e !is kotlinx.coroutines.CancellationException) {
                                _userSearchError.value = "Search is offline — showing nearby people"
                            }
                        } finally {
                            _userSearchLoading.value = false
                        }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        combine(allUsers, repository.chatMessages) { users, messages ->
            val myHandle = profile.value.handle.lowercase().trim()
            if (myHandle.isBlank()) return@combine emptyList<LiveChatRoom>()

            // 1. Group ALL cached messages by their canonical DM room or group ID.
            // This collapses legacy/fragmented threads into one entry per conversation.
            val normalizedMessages = normalizeIncomingMessages(messages)

            val latestMessagesByRoom = normalizedMessages
                .groupBy { it.roomId }
                .mapValues { (_, msgs) -> msgs.maxByOrNull { it.timestamp } }

            val roomIds = latestMessagesByRoom.keys.toMutableList()

            roomIds.mapNotNull { roomId ->
                if (roomId.startsWith("dm_")) {
                    val partner = directPartnerHandle(roomId)
                    if (partner.isBlank()) return@mapNotNull null
                    
                    val user = users.find { it.handle.equals(partner, ignoreCase = true) }
                    val latest = latestMessagesByRoom[roomId]
                    
                    LiveChatRoom(
                        id = roomId,
                        title = user?.name ?: partner,
                        subtitle = latest?.messageText?.take(35) ?: "@$partner",
                        type = "DM",
                        avatarType = user?.avatarType ?: "default",
                        verified = user?.role == "SUPER_ADMIN",
                        lastMessageTime = latest?.time ?: "Just now",
                        unreadCount = normalizedMessages.count { it.roomId == roomId && !it.isRead && !it.isFromMe }
                    )
                } else {
                    null // Ignore malformed rooms
                }
            }
        }.onEach { rooms ->
            _availableRooms.value = rooms
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            android.util.Log.d("SocialViewModel", "Initializing Supabase Connection strictly...")
            
            // 0. Explicit Connectivity Test
            var isConnected = false
            try {
                isConnected = supabaseService.checkConnection()
                if (!isConnected) {
                    android.util.Log.e("SocialViewModel", "SUPABASE IS NOT REACHABLE!")
                    // Show toast for feedback
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "Backend not reachable. Check internet.", android.widget.Toast.LENGTH_LONG).show()
                    }
                } else {
                    android.util.Log.d("SocialViewModel", "SUPABASE CONNECTED SUCCESSFULLY")
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Connectivity test crashed: ${e.message}")
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Connection Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }

            // 1. Ensure we have a fresh session BEFORE doing anything else
            // If disconnected, this might fail or use cached session.
            authRepository.ensureFreshSession()
            
            // 2. Sync core data with the now-valid (or correctly anonymous) headers
            if (isConnected) {
                try {
                    val currentUid = authRepository.userState.value.uid
                    val postsList = supabaseService.fetchPosts()
                    if (postsList.isNotEmpty()) {
                        android.util.Log.d("SocialViewModel", "Fetched ${postsList.size} posts from Supabase")
                        // ONLY clear cache if we actually have data to replace it with
                        repository.clearCachedContent()
                        repository.syncPostsFromSupabase(postsList, currentUid)
                    }
                    
                    val storiesList = supabaseService.fetchStories()
                    if (storiesList.isNotEmpty()) {
                        repository.syncStoriesFromSupabase(storiesList)
                    }
                    
                    val reelsList = supabaseService.fetchReels()
                    if (reelsList.isNotEmpty()) {
                        repository.syncReelsFromSupabase(reelsList, currentUid)
                    }
                    
                    val users = supabaseService.fetchAllUsers()
                    if (users.isNotEmpty()) {
                        repository.syncUsersFromSupabase(users)
                    }
                    
                    // Final hard cleanup of any leftover broken data
                    repository.removeEmptyMediaPostsFromCache()
                    
                } catch (e: Exception) {
                    android.util.Log.e("SocialViewModel", "Supabase sync failed: ${e.message}", e)
                    if (e.message?.contains("401") == true || e.message?.contains("UNAUTHORIZED") == true) {
                        android.util.Log.e("SocialViewModel", "Forcing logout due to 401 Error")
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            authRepository.signOut()
                            android.widget.Toast.makeText(getApplication(), "Session expired. Please log in again.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
else {
                android.util.Log.w("SocialViewModel", "Skipping initial sync due to connection failure. Using local cache.")
            }

            // 3. Keep current active user in sync
            userAuthState.collectLatest { currentAuth ->
                if (currentAuth.isLoggedIn && currentAuth.email.isNotBlank()) {
                    try {
                        val synced = repository.registerOrSyncUser(
                            uid = currentAuth.uid,
                            name = currentAuth.displayName,
                            email = currentAuth.email
                        )
                        _currentUserEntity.value = synced
                    } catch (e: Throwable) {
                        android.util.Log.e("SocialViewModel", "Error syncing current user", e)
                    }
                }
            }
        }

        // The selected-room listener is attached by selectChatRoom(). Keeping one cancellable
        // listener avoids collecting the old room forever when the user changes rooms.
        viewModelScope.launch {
            attachSupabaseRoomListener(_activeRoomId.value)
        }
        viewModelScope.launch {
            supabaseService.observeUsersRealtime().collect { users ->
                repository.syncUsersFromSupabase(users)
            }
        }
        viewModelScope.launch {
            supabaseService.observePostsRealtime().collect { posts ->
                repository.syncPostsFromSupabase(posts, userAuthState.value.uid)
            }
        }
        viewModelScope.launch {
            supabaseService.observeReelsRealtime().collect { reels ->
                repository.syncReelsFromSupabase(reels, userAuthState.value.uid)
            }
        }

        viewModelScope.launch {
            profile.map { it.handle }.distinctUntilChanged().collectLatest { myHandle ->
                if (myHandle.isBlank()) return@collectLatest
                supabaseService.observeNotificationsRealtime(myHandle).collect { notifs ->
                    repository.syncNotificationsFromSupabase(notifs)
                }
            }
        }

        // Follow graph sync: refresh my followers/following whenever login identity changes.
        viewModelScope.launch {
            profile.map { it.uid }.distinctUntilChanged().collectLatest { myUid ->
                if (myUid.isBlank()) return@collectLatest
                repository.refreshFollowState(myUid)
            }
        }

        // Real WebRTC call signalling: react to incoming OFFERING signals and to ICE state changes.
        webRtcCallManager.setConnectionStateListener { state ->
            val current = _activeCallState.value
            if (current == null) return@setConnectionStateListener
            when (state) {
                "Connected" -> _activeCallState.value = current.copy(status = "Connected")
                "Failed" -> {
                    _activeCallState.value = current.copy(status = "Call failed")
                    currentCallPollJob?.cancel()
                    webRtcCallManager.endCall()
                    viewModelScope.launch {
                        delay(300)
                        _activeCallState.value = null
                    }
                }
                else -> {}
            }
        }
        viewModelScope.launch {
            profile.map { it.handle }.distinctUntilChanged().collectLatest { myHandle ->
                if (myHandle.isBlank()) {
                    _incomingCall.value = null
                    return@collectLatest
                }
                supabaseService.observeCallSignalsRealtime(myHandle).collect { signal ->
                    if (signal != null) {
                        // Ghost-call guard: never ring for stale OFFERING rows (callee was
                        // offline). Mark them MISSED so they never surface again.
                        val isStale = System.currentTimeMillis() - signal.timestamp >
                            com.example.data.remote.SupabaseService.RING_WINDOW_MS
                        if (isStale) {
                            runCatching { supabaseService.updateCallSignalStatus(signal.id, "MISSED") }
                            return@collect
                        }
                        // A NEW incoming id always replaces whatever was queued; the old check
                        // suppressed genuinely new calls whenever any stale state existed, which
                        // made calls appear to "auto accept" into the wrong session.
                        val busy = _activeCallState.value != null
                        val isNewIncoming = _incomingCall.value?.id != signal.id
                        if (!busy && isNewIncoming) {
                            _incomingCall.value = signal
                        }
                    }
                }
            }
        }

        // Global DM delivery: poll for any direct message involving me so messages arrive
        // (and notify) even when no chat thread is open. Room remains the UI source of truth.
        viewModelScope.launch {
            profile.map { it.handle }.distinctUntilChanged().collectLatest { myHandle ->
                if (myHandle.isBlank()) return@collectLatest
                // Repair any legacy/broken cached room ids ONCE per handle change so old
                // fragmented conversations merge into their canonical thread.
                runCatching { repository.healLegacyChatRooms(myHandle) }
                val notifiedRemoteIds = mutableSetOf<String>()
                supabaseService.observeMyDirectMessagesRealtime(myHandle).collect { messages ->
                    val synced = repository.syncChatMessagesFromSupabase(normalizeIncomingMessages(messages))
                    for (message in synced) {
                        val isMine = message.senderHandle.equals(myHandle, ignoreCase = true)
                        val key = message.remoteId.ifBlank { "${message.roomId}_${message.timestamp}_${message.senderHandle}" }
                        if (isMine || !notifiedRemoteIds.add(key)) continue
                        val viewingThisRoom = _currentTab.value == MainTab.CHAT &&
                            _isInChatThread.value && _activeRoomId.value == message.roomId
                        if (!viewingThisRoom) {
                            com.example.data.notification.NotificationHelper.showChatNotification(
                                context = getApplication(),
                                senderHandle = message.senderHandle,
                                senderName = message.senderName,
                                messageText = message.messageText,
                                avatarType = message.senderAvatar
                            )
                        }
                    }
                }
            }
        }
    }


    fun signUp(name: String, email: String, pass: String, referralCode: String? = null, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = authRepository.signUpWithEmail(name, email, pass)
                if (res.isSuccess) {
                    val authUser = res.getOrNull()
                    val uid = authUser?.uid ?: throw IllegalStateException("SignUp successful but UID is missing")
                    val registeredUser = repository.registerOrSyncUser(
                        uid = uid,
                        name = name,
                        email = email
                    )
                    _currentUserEntity.value = registeredUser
                    val handle = registeredUser.handle

                    // Process Firestore & local referral bonus tracking
                    rewardRepository.handleNewUserRegistration(
                        name = name,
                        email = email,
                        userHandle = handle,
                        referralCode = referralCode?.takeIf { it.isNotBlank() }
                    )

                    onSuccess()
                } else {
                    onError(res.exceptionOrNull()?.localizedMessage ?: "Failed to sign up")
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "SignUp Error", e)
                onError(e.localizedMessage ?: "Connection error during SignUp")
            }
        }
    }

    fun signIn(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = authRepository.signInWithEmail(email, pass)
                if (res.isSuccess) {
                    val user = res.getOrNull()
                    val uid = user?.uid ?: throw IllegalStateException("SignIn successful but UID is missing")
                    val registeredUser = repository.registerOrSyncUser(
                        uid = uid,
                        name = user?.displayName ?: email.substringBefore("@"),
                        email = email
                    )
                    _currentUserEntity.value = registeredUser
                    val handle = registeredUser.handle
                    // Sync daily activity & streak in Firestore
                    rewardRepository.syncUserLogin(handle, email)

                    onSuccess()
                } else {
                    onError(res.exceptionOrNull()?.localizedMessage ?: "Failed to sign in")
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "SignIn Error", e)
                onError(e.localizedMessage ?: "Connection error during SignIn")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _currentUserEntity.value = null
        }
    }

    // --- Role-Based Access Control (RBAC) Permissions & Actions ---
    fun isSuperAdmin(): Boolean {
        return currentUserRole.value == UserRole.SUPER_ADMIN
    }

    fun canAccessAdminPanel(): Boolean {
        val role = currentUserRole.value
        val entity = _currentUserEntity.value
        return role == UserRole.SUPER_ADMIN ||
                role == UserRole.ADMIN ||
                role == UserRole.MANAGER ||
                role == UserRole.MODERATOR ||
                entity?.canManageUsers == true ||
                entity?.canManageMonetization == true ||
                entity?.canManageRewards == true
    }

    fun canDeletePost(post: PostEntity): Boolean {
        // Super Admin can delete anything
        if (currentUserRole.value == UserRole.SUPER_ADMIN) return true
        
        val isOwn = post.userHandle.equals(profile.value.handle, ignoreCase = true) ||
                post.username.equals(profile.value.name, ignoreCase = true)
        if (isOwn) return true

        val role = currentUserRole.value
        val entity = _currentUserEntity.value
        return role == UserRole.ADMIN ||
                role == UserRole.MANAGER ||
                role == UserRole.MODERATOR ||
                entity?.canDeletePosts == true
    }

    fun canEditPost(post: PostEntity): Boolean {
        val isOwn = post.userHandle.equals(profile.value.handle, ignoreCase = true) ||
                post.username.equals(profile.value.name, ignoreCase = true)
        if (isOwn) return true

        val role = currentUserRole.value
        val entity = _currentUserEntity.value
        return role == UserRole.SUPER_ADMIN ||
                role == UserRole.ADMIN ||
                entity?.canEditPosts == true
    }

    fun deletePost(post: PostEntity) {
        viewModelScope.launch {
            try {
                // HARD DELETE: Repository now handles B2 + Supabase + Room cleanup
                repository.deletePost(post.id)
                android.widget.Toast.makeText(getApplication(), "Post deleted ✨", android.widget.Toast.LENGTH_SHORT).show()
                
                // If it was an "empty" post row, trigger global cleanup as a safety net
                if (post.postImageRes.isBlank() || post.postImageRes == "default") {
                    repository.removeEmptyMediaPostsFromCache()
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Hard Delete Post Error", e)
                android.widget.Toast.makeText(getApplication(), "Delete failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * One-time cleanup of corrupted posts that have no image/video.
     */
    fun cleanupCorruptedPosts() {
        viewModelScope.launch {
            try {
                repository.removeEmptyMediaPostsFromCache()
                android.util.Log.d("SocialViewModel", "Cleanup of empty posts completed.")
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Cleanup error", e)
            }
        }
    }

    fun deleteReel(reel: ReelEntity) {
        viewModelScope.launch {
            runCatching { repository.deleteReel(reel.id) }
                .onFailure {
                    android.util.Log.e("SocialViewModel", "Reel delete failed", it)
                    android.widget.Toast.makeText(
                        getApplication(),
                        "Reel delete failed: ${it.message ?: "remote operation failed"}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    fun deleteStory(story: StoryEntity) {
        viewModelScope.launch {
            runCatching { repository.deleteStory(story) }
                .onFailure { android.util.Log.e("SocialViewModel", "Story delete failed", it) }
        }
    }

    fun removeProfilePhoto() {
        viewModelScope.launch {
            runCatching { repository.updateProfile(profile.value.copy(avatarType = "default")) }
                .onFailure { android.util.Log.e("SocialViewModel", "Profile photo removal failed", it) }
        }
    }

    fun deleteProfilePhoto() {
        viewModelScope.launch {
            try {
                repository.deleteProfileMedia(profile.value, deleteAvatar = true)
                android.widget.Toast.makeText(getApplication(), "Profile photo deleted ✨", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Profile photo delete failed", e)
                android.widget.Toast.makeText(getApplication(), "Delete failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun removeCoverPhoto() {
        viewModelScope.launch {
            runCatching { repository.updateProfile(profile.value.copy(coverType = "default")) }
                .onFailure { android.util.Log.e("SocialViewModel", "Cover photo removal failed", it) }
        }
    }

    fun deleteCoverPhoto() {
        viewModelScope.launch {
            try {
                repository.deleteProfileMedia(profile.value, deleteAvatar = false)
                android.widget.Toast.makeText(getApplication(), "Cover photo deleted ✨", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Cover photo delete failed", e)
                android.widget.Toast.makeText(getApplication(), "Delete failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun canModerateComments(): Boolean {
        val role = currentUserRole.value
        val entity = _currentUserEntity.value
        return role == UserRole.SUPER_ADMIN ||
                role == UserRole.ADMIN ||
                role == UserRole.MANAGER ||
                role == UserRole.MODERATOR ||
                entity?.canModerateComments == true
    }

    fun canManageUsers(): Boolean {
        val role = currentUserRole.value
        val entity = _currentUserEntity.value
        return role == UserRole.SUPER_ADMIN ||
                role == UserRole.ADMIN ||
                entity?.canManageUsers == true
    }

    fun canManageMonetization(): Boolean {
        val role = currentUserRole.value
        val entity = _currentUserEntity.value
        return role == UserRole.SUPER_ADMIN ||
                role == UserRole.ADMIN ||
                role == UserRole.MANAGER ||
                entity?.canManageMonetization == true
    }

    fun canManageRewards(): Boolean {
        val role = currentUserRole.value
        val entity = _currentUserEntity.value
        return role == UserRole.SUPER_ADMIN ||
                role == UserRole.ADMIN ||
                role == UserRole.MANAGER ||
                entity?.canManageRewards == true
    }

    fun canCleanStorage(): Boolean {
        val role = currentUserRole.value
        val entity = _currentUserEntity.value
        return role == UserRole.SUPER_ADMIN || entity?.canCleanStorage == true
    }

    /** True when this staff member may browse/delete platform content (posts & reels). */
    fun canModerateContent(): Boolean {
        val role = currentUserRole.value
        val entity = _currentUserEntity.value
        return role == UserRole.SUPER_ADMIN ||
                role == UserRole.ADMIN ||
                role == UserRole.MANAGER ||
                role == UserRole.MODERATOR ||
                entity?.canDeletePosts == true
    }

    fun updateUserRole(uid: String, newRole: UserRole) {
        viewModelScope.launch {
            try {
                repository.updateUserRoleAndPermissions(uid, newRole)
                android.widget.Toast.makeText(getApplication(), "Role updated successfully ✨", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Update role failed", e)
                android.widget.Toast.makeText(getApplication(), "Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun updateUserPermissions(
        uid: String,
        newRole: UserRole,
        canManageUsers: Boolean,
        canDeletePosts: Boolean,
        canEditPosts: Boolean,
        canModerateComments: Boolean,
        canManageChats: Boolean,
        canManageMonetization: Boolean,
        canManageRewards: Boolean,
        canCleanStorage: Boolean
    ) {
        viewModelScope.launch {
            try {
                repository.updateUserRoleAndPermissions(
                    uid = uid,
                    newRole = newRole,
                    canManageUsers = canManageUsers,
                    canDeletePosts = canDeletePosts,
                    canEditPosts = canEditPosts,
                    canModerateComments = canModerateComments,
                    canManageChats = canManageChats,
                    canManageMonetization = canManageMonetization,
                    canManageRewards = canManageRewards,
                    canCleanStorage = canCleanStorage
                )
                android.widget.Toast.makeText(getApplication(), "Permissions saved successfully! ⚙️", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Update perms failed", e)
                android.widget.Toast.makeText(getApplication(), "Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun banUser(uid: String, reason: String = "Policy violation") {
        viewModelScope.launch {
            try {
                repository.setUserBanStatus(uid, true, reason)
                android.widget.Toast.makeText(getApplication(), "User banned", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Ban failed", e)
                android.widget.Toast.makeText(getApplication(), "Ban failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun unbanUser(uid: String) {
        viewModelScope.launch {
            try {
                repository.setUserBanStatus(uid, false, "")
                android.widget.Toast.makeText(getApplication(), "User unbanned", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Unban failed", e)
                android.widget.Toast.makeText(getApplication(), "Unban failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteUserAccount(uid: String) {
        viewModelScope.launch {
            try {
                repository.deleteUserAccount(uid)
                android.widget.Toast.makeText(getApplication(), "User account deleted", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Delete user failed", e)
                android.widget.Toast.makeText(getApplication(), "Delete failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // System Data Cleanup (Super Admin full control)
    fun cleanAllPosts() {
        viewModelScope.launch {
            repository.cleanAllPosts()
        }
    }

    fun cleanAllChats() {
        viewModelScope.launch {
            repository.cleanAllChats()
        }
    }

    fun cleanAllNotifications() {
        viewModelScope.launch {
            repository.cleanAllNotifications()
        }
    }

    fun cleanAllStories() {
        viewModelScope.launch {
            repository.cleanAllStories()
        }
    }

    fun wipeAllSystemData() {
        viewModelScope.launch {
            repository.wipeAllDataExceptUsers()
        }
    }

    fun setTab(tab: MainTab) {
        _currentTab.value = tab
    }

    fun setProfileSubTab(subTab: ProfileSubTab) {
        _profileSubTab.value = subTab
    }

    fun toggleLike(post: PostEntity) {
        viewModelScope.launch {
            try {
                val uid = userAuthState.value.uid
                if (uid.isBlank()) {
                    android.widget.Toast.makeText(getApplication(), "Please login to like posts", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                android.util.Log.d("SocialViewModel", "Toggling like for post: ${post.remoteId} by user: $uid")
                repository.toggleLike(post, uid)
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Like Error for post ${post.remoteId}", e)
                val msg = e.message ?: "network error"
                android.widget.Toast.makeText(getApplication(), "Like failed: $msg", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun toggleReelLike(reel: ReelEntity) {
        viewModelScope.launch {
            try {
                val uid = userAuthState.value.uid
                if (uid.isBlank()) {
                    android.widget.Toast.makeText(getApplication(), "Please login to like reels", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                android.util.Log.d("SocialViewModel", "Toggling like for reel: ${reel.remoteId} by user: $uid")
                repository.toggleReelLike(reel, uid)
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Reel Like Error for reel ${reel.remoteId}", e)
                val msg = e.message ?: "network error"
                android.widget.Toast.makeText(getApplication(), "Like failed: $msg", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun addReelComment(reelId: Long, text: String) {
        viewModelScope.launch {
            repository.addReelComment(reelId, text, profile.value)
        }
    }

    fun toggleSave(post: PostEntity) {
        viewModelScope.launch {
            try {
                val uid = userAuthState.value.uid
                if (uid.isBlank()) {
                    android.widget.Toast.makeText(getApplication(), "Please login to save posts", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                repository.toggleSave(post, uid)
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Save Error", e)
                android.widget.Toast.makeText(getApplication(), "Save failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun toggleRepost(post: PostEntity) {
        viewModelScope.launch {
            try {
                val uid = userAuthState.value.uid
                if (uid.isBlank()) {
                    android.widget.Toast.makeText(getApplication(), "Please login to repost", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                repository.toggleRepost(post, uid)
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Repost Error", e)
                android.widget.Toast.makeText(getApplication(), "Repost failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openComments(post: PostEntity) {
        _activeCommentPost.value = post
        viewModelScope.launch {
            runCatching { repository.refreshComments(post) }
                .onFailure { android.util.Log.e("SocialViewModel", "Comment refresh failed", it) }
        }
    }

    fun closeComments() {
        _activeCommentPost.value = null
    }

    fun getCommentsForPost(postId: Long): Flow<List<CommentEntity>> {
        return repository.getComments(postId)
    }

    fun addComment(postId: Long, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val uid = userAuthState.value.uid
                if (uid.isBlank()) {
                    android.widget.Toast.makeText(getApplication(), "Please login to comment", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                android.util.Log.d("SocialViewModel", "Adding comment to post $postId by $uid: $text")
                repository.addComment(postId, text.trim(), profile.value)
                android.widget.Toast.makeText(getApplication(), "Comment posted! 💬", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Comment Error for post $postId", e)
                val msg = e.message ?: "network error"
                android.widget.Toast.makeText(getApplication(), "Comment failed: $msg", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openStory(story: StoryEntity) {
        _activeStory.value = story
    }

    fun closeStory() {
        _activeStory.value = null
    }

    fun openCreatePostSheet() {
        _showCreatePostSheet.value = true
    }

    fun closeCreatePostSheet() {
        _showCreatePostSheet.value = false
    }

    fun openEditProfile() {
        _settingsPage.value = SettingsPage.PERSONAL_INFO
        _showSettings.value = true
        _showEditProfileDialog.value = false
    }

    fun closeEditProfile() {
        _showEditProfileDialog.value = false
    }

    fun openShareProfile() {
        _showShareProfileDialog.value = true
    }

    fun closeShareProfile() {
        _showShareProfileDialog.value = false
    }

    fun openNotifications() {
        _showNotifications.value = true
    }

    fun viewUserProfile(handle: String) {
        if (handle.equals(profile.value.handle, ignoreCase = true)) {
            setTab(MainTab.PROFILE)
            return
        }
        viewModelScope.launch {
            try {
                val user = repository.allUsers.first().find { it.handle.equals(handle, ignoreCase = true) }
                    ?: repository.searchUsersRemote(handle).firstOrNull()
                if (user != null) {
                    // Persist the friend row (with live follow flags) so the profile
                    // sheet's Follow / Follow Back button actually works.
                    val friend = repository.ensureFriendFromUser(user)
                    _selectedFriendDetail.value = friend
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Profile Navigation Error", e)
            }
        }
    }

    fun blockUserByHandle(handle: String) {
        viewModelScope.launch {
            try {
                val user = repository.allUsers.first().find { it.handle.equals(handle, ignoreCase = true) }
                if (user != null) {
                    repository.blockUser(user.uid, profile.value)
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Block User Error", e)
            }
        }
    }

    fun closeNotifications() {
        _showNotifications.value = false
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markNotificationsRead()
        }
    }

    fun markNotificationRead(id: Long) {
        viewModelScope.launch {
            repository.markNotificationRead(id)
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            repository.deleteNotification(id)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
        }
    }

    // Friends & Social Graph Actions
    fun toggleFollow(friendId: String) {
        viewModelScope.launch {
            repository.toggleFollow(friendId, profile.value)
        }
    }

    // ---- Follow system: search-follow, follow-back, state ----

    val followState get() = repository.followState

    // Handles I currently follow (reactive; used by the notification Follow Back buttons).
    val followingHandles: StateFlow<Set<String>> = repository.friends
        .map { list -> list.filter { it.isFollowing }.map { it.handle.lowercase() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun refreshFollowState() {
        viewModelScope.launch {
            repository.refreshFollowState(profile.value.uid)
        }
    }

    /** Follow a user found via search — creates the local friend row first if needed. */
    fun followUserFromSearch(user: AppUserEntity) {
        if (user.uid.isBlank() || user.uid == profile.value.uid) return
        viewModelScope.launch {
            try {
                val friend = repository.ensureFriendFromUser(user)
                if (!friend.isFollowing) {
                    repository.toggleFollow(friend.id, profile.value)
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "followUserFromSearch failed", e)
            }
        }
    }

    /** Follow back a user who followed me (from a notification). Makes us Friends 🤝. */
    fun followBackFromNotification(handle: String) {
        if (handle.equals(profile.value.handle, ignoreCase = true)) return
        viewModelScope.launch {
            try {
                val friend = repository.ensureFriendByHandle(handle) ?: return@launch
                if (!friend.isFollowing) {
                    repository.toggleFollow(friend.id, profile.value)
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "followBackFromNotification failed", e)
            }
        }
    }

    fun unfriend(friendId: String) {
        viewModelScope.launch {
            repository.unfriend(friendId, profile.value)
            _friendOptionsTarget.value = null
            if (_selectedFriendDetail.value?.id == friendId) {
                _selectedFriendDetail.value = null
            }
        }
    }

    fun toggleCloseFriend(friendId: String) {
        viewModelScope.launch {
            repository.toggleCloseFriend(friendId)
            _friendOptionsTarget.value = null
        }
    }

    fun toggleMute(friendId: String) {
        viewModelScope.launch {
            repository.toggleMute(friendId)
            _friendOptionsTarget.value = null
        }
    }

    fun blockUser(friendId: String) {
        viewModelScope.launch {
            repository.blockUser(friendId, profile.value)
            _friendOptionsTarget.value = null
            if (_selectedFriendDetail.value?.id == friendId) {
                _selectedFriendDetail.value = null
            }
        }
    }

    fun sendPokeOrWave(friendId: String) {
        viewModelScope.launch {
            repository.sendPokeOrWave(friendId, profile.value)
        }
    }

    fun sendGiftCredits(friendId: String, amount: Int) {
        viewModelScope.launch {
            repository.sendGiftCredits(friendId, amount, profile.value)
            rewardRepository.deductCredits(amount)
            _showGiftDialogForFriend.value = null
        }
    }

    fun openFriendOptions(friend: FriendEntity) {
        _friendOptionsTarget.value = friend
    }

    fun closeFriendOptions() {
        _friendOptionsTarget.value = null
    }

    fun openFriendProfile(friend: FriendEntity) {
        _selectedFriendDetail.value = friend
        _friendOptionsTarget.value = null
    }

    fun closeFriendProfile() {
        _selectedFriendDetail.value = null
    }

    fun openGiftDialog(friend: FriendEntity) {
        _showGiftDialogForFriend.value = friend
        _friendOptionsTarget.value = null
    }

    fun closeGiftDialog() {
        _showGiftDialogForFriend.value = null
    }

    fun openFriendsList() {
        _showFriendsListFullScreen.value = true
    }

    fun closeFriendsList() {
        _showFriendsListFullScreen.value = false
    }

    fun openDirectChatWithFriend(friend: FriendEntity) {
        _friendOptionsTarget.value = null
        _selectedFriendDetail.value = null
        _showFriendsListFullScreen.value = false
        openDirectThread("dm_${friend.handle}")
        _currentTab.value = MainTab.CHAT
    }

    fun openSettings(page: SettingsPage = SettingsPage.MAIN) {
        _settingsPage.value = page
        _showSettings.value = true
    }

    fun setSettingsPage(page: SettingsPage) {
        _settingsPage.value = page
    }

    fun closeSettings() {
        _showSettings.value = false
        _settingsPage.value = SettingsPage.MAIN
    }

    fun updateFullProfile(
        name: String,
        handle: String,
        bio: String,
        location: String,
        avatarType: String = profile.value.avatarType,
        coverType: String = profile.value.coverType,
        onComplete: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                // The handle is IMMUTABLE: always reuse the currently-stored server handle so no
                // UI input can ever change it. The (read-only) `handle` argument is intentionally
                // ignored here; only Name, Bio, Location, Avatar and Cover are updated.
                repository.updateProfile(
                    profile.value.copy(
                        name = name.trim().ifBlank { profile.value.name },
                        handle = profile.value.handle,
                        bio = bio.trim(),
                        location = location.trim(),
                        avatarType = avatarType,
                        coverType = coverType
                    )
                )
                onComplete?.invoke()
            } catch (e: Throwable) {
                android.util.Log.e("SocialViewModel", "Profile update failed", e)
                onError?.invoke(e.message ?: "Profile update failed")
            }
        }
    }

    fun selectChatRoom(roomId: String) {
        _activeRoomId.value = roomId
        attachSupabaseRoomListener(roomId)
    }

    private var supabaseChatJob: kotlinx.coroutines.Job? = null

    private fun attachSupabaseRoomListener(roomId: String) {
        supabaseChatJob?.cancel()
        supabaseChatJob = viewModelScope.launch {
            try {
                supabaseService.observeChatRealtime(roomId, profile.value.handle).collect { messages ->
                    for (message in normalizeIncomingMessages(messages)) {
                        // Avoid duplicates if already received via sync or send
                        repository.syncChatMessagesFromSupabase(listOf(message))

                        // Push notification if user is outside this chat
                        val myHandle = profile.value.handle
                        val isMe = message.senderHandle.equals(myHandle, ignoreCase = true)

                        if (!isMe && (_currentTab.value != MainTab.CHAT || !_isInChatThread.value || _activeRoomId.value != roomId)) {
                            com.example.data.notification.NotificationHelper.showChatNotification(
                                context = getApplication(),
                                senderHandle = message.senderHandle,
                                senderName = message.senderName,
                                messageText = message.messageText,
                                avatarType = message.senderAvatar
                            )
                        }
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("SocialViewModel", "Error attaching Supabase listener", e)
            }
        }
    }

    fun setUserSearchQuery(query: String) {
        _userSearchQuery.value = query
    }

    fun openDirectChatWithUser(user: AppUserEntity) {
        _isInChatThread.value = true
        _currentTab.value = MainTab.CHAT
        openDirectThread("dm_${user.handle}")
    }

    fun toggleOnlineMembersSheet(show: Boolean) {        _showOnlineMembersSheet.value = show
    }

    fun toggleChatDetailsSheet(show: Boolean) {
        _showChatDetailsSheet.value = show
    }

    fun getRoomTranslation(roomId: String): ChatTranslationSettings {
        return _roomTranslationSettings.value[roomId] ?: ChatTranslationSettings(outgoingToEnglish = false, incomingToBangla = false)
    }

    fun setRoomOutgoingTranslation(roomId: String, enabled: Boolean) {
        val current = getRoomTranslation(roomId)
        _roomTranslationSettings.value = _roomTranslationSettings.value + (roomId to current.copy(outgoingToEnglish = enabled))
    }

    fun setRoomIncomingTranslation(roomId: String, enabled: Boolean) {
        val current = getRoomTranslation(roomId)
        _roomTranslationSettings.value = _roomTranslationSettings.value + (roomId to current.copy(incomingToBangla = enabled))
    }

    fun toggleRoomTranslation(roomId: String, enabled: Boolean) {
        _roomTranslationSettings.value = _roomTranslationSettings.value + (roomId to ChatTranslationSettings(outgoingToEnglish = enabled, incomingToBangla = enabled))
    }

    fun setChatTheme(theme: String) {
        _chatTheme.value = theme
    }

    fun setDisappearingDuration(duration: String) {
        _disappearingDuration.value = duration
    }

    fun openDirectThread(roomId: String) {
        selectChatRoom(canonicalDmRoom(roomId))
        _isInChatThread.value = true
    }

    private fun canonicalDmRoom(roomId: String): String {
        if (!roomId.startsWith("dm_")) return roomId
        val myHandle = profile.value.handle.lowercase().trim()
        if (myHandle.isBlank()) return roomId

        // Extract potential handles from room ID
        val raw = roomId.removePrefix("dm_")
        // Handle both "dm_partner" and "dm_a__b"
        val participants = if (raw.contains("__")) raw.split("__") else listOf(raw)
        
        // Partner is the one that isn't me.
        var partner = participants
            .map { it.trim().lowercase() }
            .firstOrNull { it.isNotBlank() && it != myHandle }
            ?: participants.firstOrNull { it.isNotBlank() }?.lowercase() ?: ""

        if (partner.isBlank() || partner == myHandle) {
            // If it's still blank or it's a self-chat, we can't reliably canonicalize 
            // without message metadata. Return as is for now.
            return roomId
        }
        
        // Final canonical form: dm_<sorted_alpha_handles>
        return "dm_" + listOf(myHandle, partner).sorted().joinToString("__")
    }

    /**
     * Normalizes messages arriving from Supabase before they touch the local cache:
     * every DM is re-keyed to its canonical room so one conversation can never split into
     * multiple inbox entries.
     */
    private fun normalizeIncomingMessages(messages: List<ChatMessageEntity>): List<ChatMessageEntity> {
        val myHandle = profile.value.handle.lowercase().trim()
        if (myHandle.isBlank()) return messages
        return messages.mapNotNull { msg ->
            if (!msg.roomId.startsWith("dm_")) return@mapNotNull msg
            
            // 1. Resolve the partner handle from ANY available source
            val partner = msg.senderHandle.takeIf { it.isNotBlank() && !it.equals(myHandle, ignoreCase = true) }
                ?: msg.receiverHandle.takeIf { it.isNotBlank() && !it.equals(myHandle, ignoreCase = true) }
                ?: run {
                    val raw = msg.roomId.removePrefix("dm_")
                    val parts = if (raw.contains("__")) raw.split("__") else listOf(raw)
                    parts.firstOrNull { it.isNotBlank() && !it.equals(myHandle, ignoreCase = true) }
                } ?: return@mapNotNull null // Drop unidentifiable messages
            
            val canonical = "dm_" + listOf(myHandle, partner.lowercase().trim()).sorted().joinToString("__")
            msg.copy(roomId = canonical)
        }
    }

    fun directPartnerHandle(roomId: String): String {
        if (!roomId.startsWith("dm_")) return ""
        val myHandle = profile.value.handle.lowercase().trim()
        val participants = roomId.removePrefix("dm_").split("__")
        return participants.firstOrNull { it.isNotBlank() && !it.equals(myHandle, ignoreCase = true) }.orEmpty()
    }

    fun closeDirectThread() {
        _isInChatThread.value = false
    }

    fun setDirectSearchQuery(query: String) {
        _directSearchQuery.value = query
    }

    fun setDirectInboxTab(tab: String) {
        _directInboxTab.value = tab
    }

    fun toggleNoteCreator(show: Boolean) {
        _showNoteCreatorDialog.value = show
    }

    fun toggleNewMessageDialog(show: Boolean) {
        _showNewMessageDialog.value = show
    }

    fun updateMyNote(noteText: String, musicTrack: String?) {
        val updated = _notesList.value.toMutableList()
        val index = updated.indexOfFirst { it.isMe }
        val newNote = InstagramNote(
            id = "my_note",
            name = profile.value.name,
            handle = profile.value.handle,
            avatarType = profile.value.avatarType,
            noteText = noteText,
            musicTrack = musicTrack,
            isMe = true
        )
        if (index != -1) {
            updated[index] = newNote
        } else {
            updated.add(0, newNote)
        }
        _notesList.value = updated
        _showNoteCreatorDialog.value = false
    }

    fun startCall(
        partnerName: String,
        partnerAvatar: String,
        isVideo: Boolean,
        partnerHandle: String? = null
    ) {
        viewModelScope.launch {
            val myHandle = profile.value.handle.ifBlank { return@launch }
            val receiverRaw = (partnerHandle ?: partnerName).takeIf { it.isNotBlank() } ?: return@launch
            // Canonical DM room ids ("dm_a__b") leak into the caller path from some screens as
            // "a__b". Resolve the real partner handle; otherwise the signal targets a handle
            // that belongs to no user and the callee never sees the call.
            val receiver = if (receiverRaw.contains("__")) {
                receiverRaw.split("__")
                    .firstOrNull { it.isNotBlank() && !it.equals(myHandle, ignoreCase = true) }
                    ?: receiverRaw
            } else {
                receiverRaw
            }
            if (receiver.equals(myHandle, ignoreCase = true)) {
                android.widget.Toast.makeText(
                    getApplication(), "You can't call yourself 😅", android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            try {
                val callId = "call_${System.currentTimeMillis()}"
                val offerSdp = webRtcCallManager.createOutgoingOffer(isVideo, useFrontCamera = true)
                    ?: throw IllegalStateException("Could not create the call offer")
                supabaseService.sendCallSignal(
                    CallSignalEntity(
                        id = callId,
                        callerHandle = myHandle,
                        callerName = profile.value.name.ifBlank { myHandle },
                        callerAvatar = profile.value.avatarType,
                        receiverHandle = receiver,
                        callType = if (isVideo) "VIDEO" else "AUDIO",
                        status = "OFFERING",
                        sdp = offerSdp,
                        timestamp = System.currentTimeMillis()
                    )
                )
                _activeCallState.value = CallState(
                    partnerName = partnerName,
                    partnerAvatar = partnerAvatar,
                    isVideo = isVideo,
                    isMuted = false,
                    isCameraOn = isVideo,
                    isSpeakerOn = true,
                    isFrontCamera = true,
                    isScreenSharing = false,
                    durationSec = 0,
                    status = "Calling…",
                    connectionQuality = if (isVideo) "HD · 1080p 60fps" else "Crystal Clear Audio · 48kHz",
                    callId = callId,
                    remoteHandle = receiver,
                    isOutgoing = true
                )
                monitorOutgoingCall(callId)
            } catch (e: Throwable) {
                android.util.Log.e("SocialViewModel", "Start call failed", e)
                endCall()
                android.widget.Toast.makeText(getApplication(), "Call failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun toggleCallMute() {
        val current = _activeCallState.value ?: return
        val newMuted = !current.isMuted
        webRtcCallManager.toggleMute(newMuted)
        _activeCallState.value = current.copy(isMuted = newMuted)
    }

    fun toggleCallCamera() {
        val current = _activeCallState.value ?: return
        val newCamera = !current.isCameraOn
        webRtcCallManager.toggleCamera(newCamera)
        _activeCallState.value = current.copy(isCameraOn = newCamera)
    }

    fun toggleCallSpeaker() {
        val current = _activeCallState.value ?: return
        val newSpeaker = !current.isSpeakerOn
        webRtcCallManager.toggleSpeaker(newSpeaker)
        _activeCallState.value = current.copy(isSpeakerOn = newSpeaker)
    }

    fun flipCallCamera() {
        val current = _activeCallState.value ?: return
        webRtcCallManager.switchCamera()
        _activeCallState.value = current.copy(isFrontCamera = !current.isFrontCamera)
    }

    fun toggleScreenSharing() {
        val current = _activeCallState.value ?: return
        // Screen capture requires MediaProjection permission and is out of scope for calls;
        // the toggle is kept for UI only.
        _activeCallState.value = current.copy(isScreenSharing = !current.isScreenSharing)
    }

    fun endCall() {
        currentCallPollJob?.cancel()
        currentCallPollJob = null
        val current = _activeCallState.value
        current?.callId?.takeIf { it.isNotBlank() }?.let { callId ->
            viewModelScope.launch {
                runCatching { supabaseService.updateCallSignalStatus(callId, "ENDED") }
            }
        }
        webRtcCallManager.endCall()
        _activeCallState.value = null
        _incomingCall.value = null
    }

    /** Caller side: poll the call row until the callee accepts (answer SDP) or ends the call. */
    private fun monitorOutgoingCall(callId: String) {
        currentCallPollJob?.cancel()
        currentCallPollJob = viewModelScope.launch {
            val ringDeadline = System.currentTimeMillis() +
                com.example.data.remote.SupabaseService.RING_WINDOW_MS
            var ringingOut = true
            while (isActive && _activeCallState.value?.callId == callId) {
                try {
                    // No answer within the ring window: end the fake "ringing forever" state and
                    // mark the row MISSED so the callee's device never rings for it later.
                    if (ringingOut && System.currentTimeMillis() > ringDeadline) {
                        ringingOut = false
                        runCatching { supabaseService.updateCallSignalStatus(callId, "MISSED") }
                        updateCallStatusIfActive(callId, "No answer")
                        delay(600)
                        endCallInternal(callId)
                        break
                    }
                    val signal = supabaseService.fetchCallSignalById(callId)
                    when (signal?.status) {
                        "ACCEPTED" -> {
                            ringingOut = false
                            val sdp = signal.sdp
                            if (!sdp.isNullOrBlank()) {
                                webRtcCallManager.applyRemoteAnswer(sdp) { ok ->
                                    updateCallStatusIfActive(callId, if (ok) "Connecting…" else "Call error")
                                }
                            } else {
                                updateCallStatusIfActive(callId, "Connecting…")
                            }
                            delay(1500)
                        }

                        "REJECTED", "ENDED", "MISSED" -> {
                            updateCallStatusIfActive(callId, "Call ended")
                            delay(300)
                            endCallInternal(callId)
                            break
                        }

                        else -> delay(1800)
                    }
                } catch (_: Throwable) {
                    delay(1800)
                }
            }
        }
    }

    private fun updateCallStatusIfActive(callId: String, status: String) {
        val current = _activeCallState.value
        if (current != null && current.callId == callId) {
            _activeCallState.value = current.copy(status = status)
        }
    }

    private fun endCallInternal(callId: String) {
        currentCallPollJob?.cancel()
        webRtcCallManager.endCall()
        runCatching { _activeCallState.value = null }
        _incomingCall.value = null
    }

    fun acceptIncomingCall() {
        viewModelScope.launch {
            val signal = _incomingCall.value ?: return@launch
            // A call can only be accepted while it is still ringing. Stale signals (device was
            // asleep) would negotiate a WebRTC answer into a dead call — the fake "auto accept".
            if (System.currentTimeMillis() - signal.timestamp >
                com.example.data.remote.SupabaseService.RING_WINDOW_MS
            ) {
                runCatching { supabaseService.updateCallSignalStatus(signal.id, "MISSED") }
                _incomingCall.value = null
                android.widget.Toast.makeText(
                    getApplication(), "Call expired", android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            try {
                val answerSdp = webRtcCallManager.receiveIncoming(
                    isVideo = signal.callType == "VIDEO",
                    remoteOfferSdp = signal.sdp
                ) ?: throw IllegalStateException("Could not create the call answer")
                supabaseService.updateCallSignalWithAnswer(signal.id, "ACCEPTED", answerSdp)
                _incomingCall.value = null
                _activeCallState.value = CallState(
                    partnerName = signal.callerName,
                    partnerAvatar = signal.callerAvatar,
                    isVideo = signal.callType == "VIDEO",
                    isMuted = false,
                    isCameraOn = signal.callType == "VIDEO",
                    isSpeakerOn = true,
                    isFrontCamera = true,
                    isScreenSharing = false,
                    durationSec = 0,
                    status = "Connecting…",
                    connectionQuality = if (signal.callType == "VIDEO") "HD · 1080p 60fps" else "Crystal Clear Audio · 48kHz",
                    callId = signal.id,
                    remoteHandle = signal.callerHandle,
                    isOutgoing = false
                )
            } catch (e: Throwable) {
                android.util.Log.e("SocialViewModel", "Accept call failed", e)
                _incomingCall.value = null
                android.widget.Toast.makeText(getApplication(), "Could not accept call: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun rejectIncomingCall() {
        viewModelScope.launch {
            val signal = _incomingCall.value
            if (signal != null) {
                runCatching { supabaseService.updateCallSignalStatus(signal.id, "REJECTED") }
            }
            _incomingCall.value = null
        }
    }

    fun openChatPartner(name: String) {
        _activeChatPartner.value = name
        val matchedRoom = _availableRooms.value.find { it.title.contains(name, ignoreCase = true) }
        if (matchedRoom != null) {
            openDirectThread(matchedRoom.id)
        }
    }

    fun sendChatMessage(
        text: String,
        mediaUrl: String? = null,
        mediaType: String = "text",
        audioDurationSec: Int = 0
    ) {
        if (text.isBlank() && mediaUrl == null) return
        val currentRoom = canonicalDmRoom(_activeRoomId.value)
        viewModelScope.launch {
            try {
                var finalText = text.trim()
                var originalText = ""
                var isTranslated = false
                var translationLang = ""

                val roomSettings = getRoomTranslation(currentRoom)

                if (roomSettings.outgoingToEnglish && finalText.isNotBlank() && mediaType == "text") {
                    val translationResult = com.example.util.ChatTranslationEngine.translateOutgoingToEnglish(finalText)
                    if (translationResult.isTranslated) {
                        originalText = finalText
                        finalText = translationResult.translatedText
                        isTranslated = true
                        translationLang = "EN"
                    }
                }

                var finalMediaUrl = mediaUrl
                if (mediaUrl != null && (mediaUrl.startsWith("content://") || mediaUrl.startsWith("file://"))) {
                    requireUploadSession()
                    val uri = android.net.Uri.parse(mediaUrl)
                    val mimeType = getApplication<Application>().contentResolver.getType(uri) ?: "image/jpeg"
                    
                    val bytes = if (mimeType.startsWith("image")) {
                        com.example.util.MediaUtils.compressImage(getApplication(), uri)
                    } else {
                        getApplication<Application>().contentResolver.openInputStream(uri)?.readBytes()
                    } ?: throw IllegalStateException("Could not read or compress selected media")

                    val extension = mimeType.substringAfter('/', "bin").replace("+", "_")
                    val upload = repository.uploadMedia(
                        bytes = bytes,
                        fileName = "chat_${System.currentTimeMillis()}.$extension",
                        mimeType = mimeType,
                        uploadType = "media"
                    )
                    finalMediaUrl = upload.getOrThrow().url

                    repository.sendChatMessage(
                        roomId = currentRoom,
                        text = finalText,
                        profile = profile.value,
                        receiverHandle = directPartnerHandle(currentRoom),
                        mediaUrl = finalMediaUrl,
                        mediaType = mediaType,
                        audioDurationSec = audioDurationSec,
                        originalText = originalText,
                        isTranslated = isTranslated,
                        translationLang = translationLang
                    )
                } else {
                    repository.sendChatMessage(
                        roomId = currentRoom,
                        text = finalText,
                        profile = profile.value,
                        receiverHandle = directPartnerHandle(currentRoom),
                        mediaUrl = finalMediaUrl,
                        mediaType = mediaType,
                        audioDurationSec = audioDurationSec,
                        originalText = originalText,
                        isTranslated = isTranslated,
                        translationLang = translationLang
                    )
                }
                rewardRepository.completeTaskByType(profile.value.handle, "CHAT_MESSAGE")
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Send Chat Error", e)
                // Never fail silently — the user must know the message did NOT reach the server.
                android.widget.Toast.makeText(
                    getApplication(),
                    "Message failed to send: ${e.message ?: "network error"}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun clearCurrentRoomMessages() {
        val currentRoom = _activeRoomId.value
        if (currentRoom.isBlank()) return
        viewModelScope.launch {
            try {
                repository.clearRoomMessages(currentRoom)
                supabaseService.deleteChatRoomMessages(currentRoom).getOrThrow()
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Clear history failed", e)
            }
        }
    }

    fun clearRoomMessagesById(roomId: String) {
        viewModelScope.launch {
            try {
                repository.clearRoomMessages(roomId)
                // Add remote deletion call
                supabaseService.deleteChatRoomMessages(roomId).getOrThrow()
                android.widget.Toast.makeText(getApplication(), "History cleared", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Clear History Error", e)
            }
        }
    }

    fun togglePinChat(roomId: String) {
        val updated = _availableRooms.value.map { room ->
            if (room.id == roomId) room.copy(isPinned = !room.isPinned) else room
        }.sortedWith(compareByDescending<LiveChatRoom> { it.isPinned }.thenByDescending { it.unreadCount })
        _availableRooms.value = updated
    }

    fun toggleMuteChat(roomId: String) {
        _availableRooms.value = _availableRooms.value.map { room ->
            if (room.id == roomId) room.copy(isMuted = !room.isMuted) else room
        }
    }

    fun toggleArchiveChat(roomId: String) {
        _availableRooms.value = _availableRooms.value.map { room ->
            if (room.id == roomId) room.copy(isArchived = !room.isArchived) else room
        }
    }

    fun toggleFavoriteChat(roomId: String) {
        _availableRooms.value = _availableRooms.value.map { room ->
            if (room.id == roomId) room.copy(isFavorite = !room.isFavorite) else room
        }
    }

    fun markChatUnread(roomId: String) {
        _availableRooms.value = _availableRooms.value.map { room ->
            if (room.id == roomId) room.copy(unreadCount = if (room.unreadCount > 0) room.unreadCount else 1) else room
        }
    }

    fun markChatRead(roomId: String) {
        _availableRooms.value = _availableRooms.value.map { room ->
            if (room.id == roomId) room.copy(unreadCount = 0) else room
        }
    }

    fun deleteChatRoom(roomId: String) {
        viewModelScope.launch {
            try {
                repository.clearRoomMessages(roomId)
                supabaseService.deleteChatRoomMessages(roomId).getOrThrow()
                _availableRooms.value = _availableRooms.value.filter { it.id != roomId }
                if (_activeRoomId.value == roomId) {
                    _isInChatThread.value = false
                    _activeRoomId.value = ""
                }
                android.widget.Toast.makeText(getApplication(), "Chat deleted", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Delete Chat Error", e)
            }
        }
    }

    private suspend fun requireUploadSession() {
        val session = authRepository.ensureFreshSession()
        if (!session.isLoggedIn || session.uid.isBlank()) {
            throw IllegalStateException("Please sign in before uploading media")
        }
    }

    fun createPost(caption: String, actionType: String, imageUriOrRes: String) {
        viewModelScope.launch {
            try {
                var finalMediaUrl = imageUriOrRes

                // If a real gallery image is provided, upload it to B2 first and use the returned
                // URL. Text-only posts are allowed (finalMediaUrl stays blank), so publishing with
                // just a caption works from the + button.
                if (imageUriOrRes.startsWith("content://") || imageUriOrRes.startsWith("file://")) {
                    requireUploadSession()
                    val uri = android.net.Uri.parse(imageUriOrRes)
                    
                    // COMPRESSION: Use MediaUtils to compress the post image
                    val bytes = com.example.util.MediaUtils.compressImage(getApplication(), uri)
                        ?: throw Exception("Could not compress post image")

                    val fileName = "post_${System.currentTimeMillis()}.jpg"
                    val mimeType = "image/jpeg"

                    val uploadRes = repository.uploadMedia(bytes, fileName, mimeType, "post")
                    finalMediaUrl = uploadRes.getOrThrow().url

                    repository.createPost(profile.value, caption, actionType, finalMediaUrl)
                } else {
                    repository.createPost(profile.value, caption, actionType, finalMediaUrl)
                }
                rewardRepository.completeTaskByType(profile.value.handle, "CREATE_POST")
                _showCreatePostSheet.value = false
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Create Post Error", e)
                android.widget.Toast.makeText(getApplication(), "Post upload failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun updateProfile(name: String, bio: String, location: String, isPublic: Boolean = profile.value.isPublic) {
        viewModelScope.launch {
            repository.updateProfile(
                profile.value.copy(
                    name = name,
                    bio = bio,
                    location = location,
                    isPublic = isPublic
                )
            )
            _showEditProfileDialog.value = false
        }
    }

    fun changeCoverPhoto(uriString: String) {
        viewModelScope.launch {
            try {
                requireUploadSession()
                if (uriString.isBlank() || uriString.startsWith("img_")) {
                    android.util.Log.w("SocialViewModel", "Ignoring non-Uri cover photo: $uriString")
                    return@launch
                }

                val uri = android.net.Uri.parse(uriString)
                val bytes = com.example.util.MediaUtils.compressImage(getApplication(), uri)
                    ?: throw Exception("Could not compress cover image")
                
                val fileName = "cover_${System.currentTimeMillis()}.jpg"
                val mimeType = "image/jpeg"
                
                val uploadRes = repository.uploadMedia(bytes, fileName, mimeType, "cover")
                val uploadResult = uploadRes.getOrThrow()
                val b2Url = uploadResult.url
                val storagePath = uploadResult.storagePath

                val oldCover = profile.value.coverType
                // B2 Delete order: 1. Delete old B2 object first (if it's not default)
                if (oldCover.isNotBlank() && !oldCover.equals("default", ignoreCase = true) && oldCover != b2Url) {
                    supabaseService.deleteMediaFromB2(oldCover).getOrThrow()
                }

                // 2. Only if B2 delete succeeds (or is skipped), update the database reference
                repository.updateProfile(profile.value.copy(coverType = b2Url, coverPath = storagePath))
                repository.createPost(
                    profile = profile.value,
                    caption = "Updated cover photo ✨",
                    actionType = "cover",
                    imageRes = b2Url,
                    storagePath = storagePath
                )
                _showCreatePostSheet.value = false
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Cover Photo Update Error", e)
                android.widget.Toast.makeText(getApplication(), "Cover upload failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun changeProfilePhoto(uriString: String) {
        viewModelScope.launch {
            try {
                requireUploadSession()
                if (uriString.isBlank() || uriString.startsWith("img_")) {
                    android.util.Log.w("SocialViewModel", "Ignoring non-Uri profile photo: $uriString")
                    return@launch
                }

                val uri = android.net.Uri.parse(uriString)
                val bytes = com.example.util.MediaUtils.compressImage(getApplication(), uri)
                    ?: throw Exception("Could not compress profile image")
                
                val fileName = "avatar_${System.currentTimeMillis()}.jpg"
                val mimeType = "image/jpeg"
                
                val uploadRes = repository.uploadMedia(bytes, fileName, mimeType, "profile")
                val uploadResult = uploadRes.getOrThrow()
                val b2Url = uploadResult.url
                val storagePath = uploadResult.storagePath

                val oldAvatar = profile.value.avatarType
                // B2 Delete order: 1. Delete old B2 object first
                if (oldAvatar.isNotBlank() && !oldAvatar.equals("default", ignoreCase = true) && oldAvatar != b2Url) {
                    supabaseService.deleteMediaFromB2(oldAvatar).getOrThrow()
                }

                // 2. Update database reference only on success
                repository.updateProfile(profile.value.copy(avatarType = b2Url, avatarPath = storagePath))
                repository.createPost(
                    profile = profile.value,
                    caption = "Updated profile picture 📸",
                    actionType = "profile",
                    imageRes = b2Url,
                    storagePath = storagePath
                )
                _showCreatePostSheet.value = false
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Profile Photo Update Error", e)
                android.widget.Toast.makeText(getApplication(), "Profile upload failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun addStory(imageUriString: String, caption: String) {
        viewModelScope.launch {
            try {
                requireUploadSession()
                if (!imageUriString.startsWith("content://") && !imageUriString.startsWith("file://")) {
                    throw IllegalArgumentException("Please choose a real gallery image")
                }
                val uri = android.net.Uri.parse(imageUriString)
                val bytes = com.example.util.MediaUtils.compressImage(getApplication(), uri)
                    ?: throw Exception("Could not compress story image")
                val mimeType = "image/jpeg"
                val upload = repository.uploadMedia(bytes, "story_${System.currentTimeMillis()}.jpg", mimeType, "story")
                val res = upload.getOrThrow()
                repository.addStory(res.url, null, caption, profile.value)
                _showCreatePostSheet.value = false
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Story Upload Error", e)
                android.widget.Toast.makeText(getApplication(), "Story upload failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun clearAllPosts() {
        viewModelScope.launch {
            repository.clearAllPosts()
        }
    }

    // Reward & Currency System Methods
    fun openRewardScreen() {
        _showRewardScreen.value = true
    }

    fun closeRewardScreen() {
        _showRewardScreen.value = false
    }

    fun setCurrency(currency: AppCurrency) {
        rewardRepository.setCurrency(currency)
    }

    fun toggleCurrency(): AppCurrency {
        return rewardRepository.toggleCurrency()
    }

    fun formatMoney(usdAmount: Double, forceCurrency: AppCurrency? = null): String {
        val curr = forceCurrency ?: selectedCurrency.value
        val rate = adminConfig.value.usdToBdtRate
        return if (curr == AppCurrency.BDT) {
            "৳ ${String.format(java.util.Locale.US, "%.2f", usdAmount * rate)}"
        } else {
            "$ ${String.format(java.util.Locale.US, "%.2f", usdAmount)}"
        }
    }

    fun formatCredits(credits: Int, forceCurrency: AppCurrency? = null): String {
        val creditsPerDollar = adminConfig.value.creditsPerDollar.takeIf { it > 0 } ?: 2000
        val usd = credits.toDouble() / creditsPerDollar
        return formatMoney(usd, forceCurrency)
    }

    fun addPaymentMethod(method: PaymentMethodItem) {
        rewardRepository.addPaymentMethod(method)
    }

    fun updatePaymentMethod(method: PaymentMethodItem) {
        rewardRepository.updatePaymentMethod(method)
    }

    fun deletePaymentMethod(methodId: String) {
        rewardRepository.deletePaymentMethod(methodId)
    }

    fun togglePaymentMethod(methodId: String) {
        rewardRepository.togglePaymentMethod(methodId)
    }

    fun completeTask(taskType: String) {
        viewModelScope.launch {
            rewardRepository.completeTaskByType(profile.value.handle, taskType)
        }
    }

    fun openAdminScreen() {
        _showAdminScreen.value = true
    }

    fun closeAdminScreen() {
        _showAdminScreen.value = false
    }

    fun watchReelEarnCredit(watchDurationSeconds: Int = 10): Int {
        return rewardRepository.addReelWatchCredit(
            userHandle = profile.value.handle,
            watchSeconds = watchDurationSeconds
        )
    }

    fun recordReelUploaded() {
        rewardRepository.recordReelUpload(profile.value.handle)
    }

    fun uploadReel(caption: String, music: String, videoUriString: String) {
        viewModelScope.launch {
            try {
                requireUploadSession()
                _reelUploadProgress.value = 3
                if (!videoUriString.startsWith("content://") && !videoUriString.startsWith("file://")) {
                    throw IllegalArgumentException("Please choose a real gallery video")
                }
                
                val uri = android.net.Uri.parse(videoUriString)
                
                // 1. Generate and COMPRESS thumbnail from video
                var thumbnailB2Url = ""
                var thumbnailPath: String? = null
                try {
                    val mmr = android.media.MediaMetadataRetriever()
                    mmr.setDataSource(getApplication(), uri)
                    val bitmap = mmr.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    mmr.release()
                    
                    bitmap?.let {
                        val out = java.io.ByteArrayOutputStream()
                        it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                        val thumbRes = repository.uploadMedia(out.toByteArray(), "thumb_${System.currentTimeMillis()}.jpg", "image/jpeg", "media")
                        val res = thumbRes.getOrThrow()
                        thumbnailB2Url = res.url
                        thumbnailPath = res.storagePath
                        it.recycle()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SocialViewModel", "Thumbnail generation failed", e)
                }

                _reelUploadProgress.value = 10

                // 2. Upload VIDEO as a new file
                val videoBytes = getApplication<Application>().contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw Exception("Could not read reel video bytes")
                
                val videoFileName = "reel_${System.currentTimeMillis()}.mp4"
                val videoMimeType = getApplication<Application>().contentResolver.getType(uri) ?: "video/mp4"
                
                val uploadRes = repository.uploadMedia(
                    bytes = videoBytes, 
                    fileName = videoFileName, 
                    mimeType = videoMimeType, 
                    uploadType = "reel",
                    onProgress = { pct ->
                        _reelUploadProgress.value = 10 + (pct * 80) / 100
                    }
                )
                val uploadResult = uploadRes.getOrThrow()
                val finalVideoUrl = uploadResult.url
                val videoStoragePath = uploadResult.storagePath

                _reelUploadProgress.value = 92

                repository.createReel(
                    profile = profile.value,
                    caption = caption,
                    music = music,
                    imageRes = thumbnailB2Url,
                    videoUrl = finalVideoUrl,
                    storagePath = videoStoragePath,
                    thumbnailPath = thumbnailPath
                )
                
                _reelUploadProgress.value = 95
                recordReelUploaded()
                repository.refreshReelsFromSupabase()
                _reelUploadProgress.value = 100
                delay(1000)
                _reelUploadProgress.value = null
            } catch (e: Exception) {
                _reelUploadProgress.value = null
                android.util.Log.e("SocialViewModel", "Reel Upload Error", e)
                android.widget.Toast.makeText(getApplication(), "Reel upload failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun claimReward(taskId: String): Int {
        return rewardRepository.claimTaskReward(taskId)
    }

    fun applyReferralCode(code: String, onResult: (Boolean, String) -> Unit) {
        val result = rewardRepository.applyReferral(
            code = code,
            refereeHandle = profile.value.handle.ifBlank { throw IllegalStateException("Cannot apply referral without handle") },
            refereeName = profile.value.name,
        )
        if (result.isSuccess) {
            onResult(true, result.getOrNull() ?: "Referral code activated!")
        } else {
            onResult(false, result.exceptionOrNull()?.localizedMessage ?: "Invalid code")
        }
    }

    fun simulateRefereeDailyTask(referralId: String): String {
        return rewardRepository.simulateRefereeDailyTask(referralId)
    }

    fun claimReferralReward(referralId: String): Int {
        return rewardRepository.claimReferralReward(referralId)
    }

    fun requestWithdrawal(method: String, account: String, credits: Int, usd: Double, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val result = rewardRepository.submitWithdrawal(
            handle = profile.value.handle,
            email = userAuthState.value.email,
            method = method,
            account = account,
            credits = credits,
            usd = usd
        )
        if (result.isSuccess) {
            onSuccess(result.getOrNull() ?: "")
        } else {
            onError(result.exceptionOrNull()?.localizedMessage ?: "Failed to submit request")
        }
    }

    fun updateAdminConfig(newConfig: com.example.data.model.AdminConfig) {
        rewardRepository.updateAdminConfig(newConfig)
    }

    fun updateWithdrawal(id: String, status: String, note: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val target = rewardRepository.withdrawals.value.firstOrNull { it.id == id }
                val result = rewardRepository.updateWithdrawalStatus(id, status, note)
                result.fold(
                    onSuccess = {
                        // On rejection, refund coins to the requester (remote wallet + own device if applicable)
                        if (status == "REJECTED" && target != null && target.creditsUsed > 0) {
                            rewardRepository.refundRequester(target.userHandle, target.creditsUsed, profile.value.handle)
                        }
                        onResult(true, if (status == "REJECTED") "Withdrawal rejected & coins refunded" else "Withdrawal $status")
                    },
                    onFailure = { onResult(false, it.message ?: "Failed to update withdrawal") }
                )
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to update withdrawal")
            }
        }
    }

    /** Pulls all withdrawal requests + the signed-in user's remote wallet (refund merge) from Supabase. */
    fun refreshRewardServerData(handle: String) {
        viewModelScope.launch {
            try {
                rewardRepository.refreshWithdrawals()
                if (handle.isNotBlank()) rewardRepository.refreshWalletFromRemote(handle)
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Reward server refresh failed", e)
            }
        }
    }

    fun addAdminTask(title: String, desc: String, reward: Int) {
        rewardRepository.addAdminCustomTask(title, desc, reward)
    }

    // Cover Photo & Profile Picture Helper Methods
    fun openCoverPhotoOptions() {
        _showCoverPhotoOptions.value = true
    }

    fun closeCoverPhotoOptions() {
        _showCoverPhotoOptions.value = false
    }

    fun openAvatarOptions() {
        _showAvatarOptions.value = true
    }

    fun closeAvatarOptions() {
        _showAvatarOptions.value = false
    }

    fun openFullScreenPhotoPreview(title: String, imageUri: String, subtitle: String = "", isAvatar: Boolean = false) {
        _fullScreenPhotoPreview.value = FullScreenPhotoData(title, imageUri, subtitle, isAvatar)
    }

    fun closeFullScreenPhotoPreview() {
        _fullScreenPhotoPreview.value = null
    }

    fun openAiArtGenerator(type: String) {
        _showCoverPhotoOptions.value = false
        _showAvatarOptions.value = false
        _showAiArtGenerator.value = type
    }

    fun closeAiArtGenerator() {
        _showAiArtGenerator.value = null
    }

    fun openPresetGallery(type: String) {
        _showCoverPhotoOptions.value = false
        _showAvatarOptions.value = false
        _showPresetGallery.value = type
    }

    fun closePresetGallery() {
        _showPresetGallery.value = null
    }

    fun setAvatarFrame(frame: String) {
        _selectedAvatarFrame.value = frame
    }

    fun resetCoverPhoto() {
        viewModelScope.launch {
            try {
                val oldCover = profile.value.coverType
                // 1. Delete B2 object first
                if (oldCover.isNotBlank() && !oldCover.equals("default", ignoreCase = true)) {
                    supabaseService.deleteMediaFromB2(oldCover).getOrThrow()
                }
                // 2. Update DB reference only if B2 delete succeeds
                repository.updateProfile(profile.value.copy(coverType = "default"))
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Reset Cover B2 Delete Error", e)
                android.widget.Toast.makeText(getApplication(), "Could not delete from storage: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        _showCoverPhotoOptions.value = false
    }

    fun resetProfilePhoto() {
        viewModelScope.launch {
            try {
                val oldAvatar = profile.value.avatarType
                // 1. Delete B2 object first
                if (oldAvatar.isNotBlank() && !oldAvatar.equals("default", ignoreCase = true)) {
                    supabaseService.deleteMediaFromB2(oldAvatar).getOrThrow()
                }
                // 2. Update DB reference only if B2 delete succeeds
                repository.updateProfile(profile.value.copy(avatarType = "default"))
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Reset Profile B2 Delete Error", e)
                android.widget.Toast.makeText(getApplication(), "Could not delete from storage: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        _showAvatarOptions.value = false
    }

    // Monetization Actions
    fun openMonetizationScreen() {
        _showMonetizationScreen.value = true
    }

    fun closeMonetizationScreen() {
        _showMonetizationScreen.value = false
    }

    fun updateMonetizationSettings(newSettings: MonetizationSettings, onResult: ((Boolean) -> Unit)? = null) {
        monetizationRepository.updateSettings(newSettings, onResult)
    }

    fun submitMonetizationApplication(onResult: (Boolean, String) -> Unit) {
        val prof = profile.value
        val totalViews = (prof.postsCount * 2500) + (prof.followersCount * 120) + 1500
        monetizationRepository.submitApplication(
            userId = prof.handle.ifBlank { "" },
            userHandle = prof.handle.ifBlank { "" },
            userName = prof.name.ifBlank { "" },
            userAvatarType = prof.avatarType,
            currentFollowers = prof.followersCount,
            currentViews = totalViews,
            onResult = onResult
        )
    }

    fun approveMonetizationApplication(applicationId: String, onResult: (Boolean, String) -> Unit) {
        monetizationRepository.approveApplication(applicationId, "admin", onResult)
    }

    fun rejectMonetizationApplication(applicationId: String, reason: String, onResult: (Boolean, String) -> Unit) {
        monetizationRepository.rejectApplication(applicationId, reason, "admin", onResult)
    }

    fun addMonetizationFunds(
        amount: Double,
        gateway: String = "bKash Merchant",
        trxId: String = "DEP-${System.currentTimeMillis().toString().takeLast(6)}",
        onResult: (Boolean, String) -> Unit
    ) {
        val handle = profile.value.handle.ifBlank { "" }
        monetizationRepository.addFunds(amount, handle, gateway, trxId, onResult)
    }

    fun addMonetizationFunds(amount: Double, onResult: (Boolean, String) -> Unit) {
        addMonetizationFunds(amount, "bKash Merchant", "DEP-${System.currentTimeMillis().toString().takeLast(6)}", onResult)
    }

    fun openPostBoost(post: PostEntity) {
        _showPostBoostDialogForPost.value = post
    }

    fun closePostBoost() {
        _showPostBoostDialogForPost.value = null
    }

    fun createPostBoostCampaign(
        campaign: PostBoostCampaign,
        payWithWallet: Boolean,
        gatewayMethod: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        val handle = profile.value.handle.ifBlank { "" }
        monetizationRepository.createPostBoostCampaign(campaign, payWithWallet, gatewayMethod, handle) { success, msg ->
            if (success) {
                // Send positive push notification
                com.example.data.notification.NotificationHelper.showRewardNotification(
                    context = getApplication(),
                    title = "Post Boost Active! ⚡",
                    message = "Your campaign for ${campaign.postTitle} is now running across ${campaign.targetAudience}!",
                    isPositive = true
                )
            }
            onResult(success, msg)
        }
    }

    fun requestMonetizationWithdrawal(amount: Double, method: String, account: String, onResult: (Boolean, String) -> Unit) {
        val handle = profile.value.handle.ifBlank { "" }
        monetizationRepository.requestWithdrawal(amount, method, account, handle, onResult)
    }

    fun pauseBoostCampaign(campaignId: String, onResult: (Boolean, String) -> Unit) {
        monetizationRepository.pauseBoostCampaign(campaignId, onResult)
    }

    fun resumeBoostCampaign(campaignId: String, onResult: (Boolean, String) -> Unit) {
        monetizationRepository.resumeBoostCampaign(campaignId, onResult)
    }

    fun cancelBoostCampaign(campaignId: String, onResult: (Boolean, String) -> Unit) {
        monetizationRepository.cancelBoostCampaign(campaignId, onResult)
    }

    fun approveMonetizationWithdrawal(transactionId: String, payoutRef: String, onResult: (Boolean, String) -> Unit) {
        monetizationRepository.approveWithdrawal(transactionId, payoutRef, onResult)
    }

    fun rejectMonetizationWithdrawal(transactionId: String, reason: String, onResult: (Boolean, String) -> Unit) {
        monetizationRepository.rejectWithdrawal(transactionId, reason, onResult)
    }

    // Phase 2: Revenue Attribution Actions
    fun createRevenuePeriod(id: String, startDate: String, endDate: String, name: String, onResult: (Boolean, String) -> Unit) {
        monetizationRepository.createRevenuePeriod(id, startDate, endDate, name, onResult)
    }

    fun importAdMobRevenueReports(periodId: String, reports: List<AdMobRevenueReport>, onResult: (Boolean, String) -> Unit) {
        monetizationRepository.importAdMobRevenueReports(periodId, reports, onResult)
    }

    fun calculateRevenueAttribution(periodId: String, onResult: (Boolean, String) -> Unit) {
        monetizationRepository.calculateRevenueAttribution(periodId, onResult)
    }

    fun finalizeRevenuePeriod(periodId: String, onResult: (Boolean, String) -> Unit) {
        monetizationRepository.finalizeRevenuePeriod(periodId, "admin", onResult)
    }

    fun applyRevenueAdjustment(periodId: String, contentEarningId: String, adjustmentAmount: Double, reason: String, onResult: (Boolean, String) -> Unit) {
        monetizationRepository.applyRevenueAdjustment(periodId, contentEarningId, adjustmentAmount, reason, "admin", onResult)
    }

    fun saveAdUnitMapping(mapping: AdMobAdUnitMapping, onResult: ((Boolean) -> Unit)? = null) {
        monetizationRepository.saveAdUnitMapping(mapping, onResult)
    }

    fun getCreatorSummaries(): List<CreatorEarningsSummary> {
        return monetizationRepository.getCreatorSummaries()
    }

    fun getUserContentEarnings(handle: String): List<ContentEarning> {
        return monetizationRepository.getUserContentEarnings(handle)
    }

    fun recordContentAdImpression(contentId: String, creatorId: String, contentType: String, placement: String) {
        adAttributionManager.recordContentAdImpression(contentId, creatorId, contentType, placement)
    }

    // ==========================================
    // NOTIFICATION HELPERS & TEST TRIGGERS
    // ==========================================
    fun setAutoSyncEnabled(enabled: Boolean) {
        _autoSyncEnabled.value = enabled
    }

    fun markActiveRoomAsSeen() {
        val roomId = _activeRoomId.value
        if (roomId.isBlank()) return
        viewModelScope.launch {
            repository.markChatMessagesAsSeen(roomId, profile.value.uid, profile.value.handle)
        }
    }


}

data class FullScreenPhotoData(
    val title: String,
    val imageResOrUri: String,
    val subtitle: String = "",
    val isAvatar: Boolean = false
)