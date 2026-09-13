package com.example.data.repository

import com.example.data.db.SocialDao
import com.example.data.model.*
import com.example.data.remote.SupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SocialRepository(
    private val dao: SocialDao,
    private val supabaseService: SupabaseService,
    private val appContext: android.content.Context
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val pushService by lazy {
        com.example.data.remote.PushNotificationService(appContext)
    }



    val allPosts: Flow<List<PostEntity>> = dao.getAllPosts()
    val allReels: Flow<List<ReelEntity>> = dao.getAllReels()
    val userProfile: Flow<UserProfileEntity?> = dao.getUserProfile()
    val allStories: Flow<List<StoryEntity>> = dao.getAllStories()
    val savedPosts: Flow<List<PostEntity>> = dao.getSavedPosts()
    val repostedPosts: Flow<List<PostEntity>> = dao.getRepostedPosts()
    val chatMessages: Flow<List<ChatMessageEntity>> = dao.getChatMessages()
    val notifications: Flow<List<NotificationEntity>> = dao.getNotifications()
    val allConnections: Flow<List<FriendEntity>> = dao.getAllConnections()
    val friends: Flow<List<FriendEntity>> = dao.getFriends()

    val allUsers: Flow<List<AppUserEntity>> = dao.getAllUsers()

    fun getUserPosts(handle: String): Flow<List<PostEntity>> = dao.getUserPosts(handle)
    fun getComments(postId: Long): Flow<List<CommentEntity>> = dao.getCommentsForPost(postId)

    // ---- Follow graph state (uid -> follower/following sets, synced from the `follows` table) ----
    data class FollowGraph(val followers: Set<String> = emptySet(), val following: Set<String> = emptySet())

    private val _followState = MutableStateFlow(FollowGraph())
    val followState: StateFlow<FollowGraph> = _followState.asStateFlow()

    suspend fun refreshFollowState(myUid: String) {
        if (myUid.isBlank()) return
        try {
            val (followers, following) = supabaseService.fetchFollowState(myUid)
            _followState.value = FollowGraph(followers = followers, following = following)

            // Reconcile cached friend rows so profile sheets / lists show correct flags.
            friends.first().forEach { f ->
                val amFollowing = following.contains(f.id)
                val followsMe = followers.contains(f.id)
                if (amFollowing != f.isFollowing || followsMe != f.isFollower) {
                    dao.updateFollowStatus(id = f.id, isFollowing = amFollowing, isFriend = amFollowing && followsMe)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "refreshFollowState failed", e)
        }
    }

    // ---- Realtime presence ----------------------------------------------------

    /** Heartbeat so other users see ME as online. */
    suspend fun touchMyPresence() = supabaseService.touchSocialPresence()

    /** handle(lowercased) -> last_seen epoch millis. */
    suspend fun fetchPresence(handles: List<String>): Map<String, Long> =
        supabaseService.fetchPresence(handles)

    /** Persists online flags onto cached friend rows so all UI observes them reactively. */
    suspend fun applyPresence(lastSeenByHandle: Map<String, Long>) {
        if (lastSeenByHandle.isEmpty()) return
        val now = System.currentTimeMillis()
        try {
            friends.first().forEach { f ->
                val online = com.example.util.Presence.isOnline(lastSeenByHandle[f.handle.lowercase().trim()], now)
                if (online != f.isOnline) {
                    dao.updateFriendOnline(f.handle, online)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "applyPresence failed", e)
        }
    }

    /** Returns the local friend row for a searched/known user, creating it on first contact. */
    suspend fun ensureFriendFromUser(user: AppUserEntity): FriendEntity {
        dao.getFriendById(user.uid)?.let { return it }
        val graph = _followState.value
        val friend = FriendEntity(
            id = user.uid,
            name = user.name,
            handle = user.handle,
            avatarType = user.avatarType,
            coverImageRes = user.coverType,
            avatarPath = user.avatarPath,
            coverPath = user.coverPath,
            avatarProvider = user.avatarProvider,
            coverProvider = user.coverProvider,
            bio = user.bio,
            location = user.location,
            isFollowing = graph.following.contains(user.uid),
            isFollower = graph.followers.contains(user.uid),
            isFriend = graph.following.contains(user.uid) && graph.followers.contains(user.uid),
            isOnline = false,
            timestamp = System.currentTimeMillis()
        )
        dao.insertFriend(friend)
        return friend
    }

    /** Resolves a handle to a friend row (local cache -> remote search), creating it if needed. */
    suspend fun ensureFriendByHandle(handle: String): FriendEntity? {
        friends.first().find { it.handle.equals(handle, ignoreCase = true) }?.let { return it }
        val user = allUsers.first().find { it.handle.equals(handle, ignoreCase = true) }
            ?: runCatching { searchUsersRemote(handle).firstOrNull() }.getOrNull()
            ?: return null
        return ensureFriendFromUser(user)
    }

    suspend fun initDefaultDataIfNeeded() {
        // No local seed data. All content is loaded from Supabase (the source of truth).
    }

    /**
     * SUPER_ADMIN only. Deletes every account EXCEPT the account whose @handle
     * is [ceoHandle]. Permanently removes the real auth accounts (not just the
     * local cache), then re-syncs the local user list so the UI reflects the
     * remote truth.
     */
    suspend fun deleteAllUsersExcept(ceoHandle: String): Int {
        val deleted = supabaseService.deleteAllUsersExcept(ceoHandle).getOrThrow()
        // Wait briefly for Supabase background purging to complete before re-fetching
        kotlinx.coroutines.delay(1500)
        // Refresh the local cache from the server so the deleted accounts vanish.
        runCatching { supabaseService.fetchAllUsers() }
            .getOrElse { emptyList() }
            .let { if (it.isNotEmpty()) syncUsersFromSupabase(it) }
        return deleted
    }

    suspend fun registerOrSyncUser(
        uid: String,
        name: String,
        email: String
    ): AppUserEntity {
        // 1. Try to fetch the latest profile from the server FIRST.
        // This ensures we get any staff roles/permissions assigned by admins.
        val remoteUser = supabaseService.fetchUserByUid(uid).getOrNull()
        if (remoteUser != null) {
            dao.insertUser(remoteUser)
            updateProfileCache(remoteUser)
            return remoteUser
        }

        val existing = dao.getUserByUid(uid)

        if (existing != null) {
            // CRITICAL FIX: Do NOT overwrite the cached name/email from the login session
            // if we already have a local row. This prevents "profile revert" when navigating
            // away from the edit screen. The authoritative source is either the latest edit
            // or the full fetchAllUsers sync.
            //
            // BUT the display cache (user_profile row) can still be MISSING — e.g. after a
            // logout/login where Room kept the app_users row. Without it the profile page
            // shows no name/handle/cover and posts resolve against a blank handle.
            val cached = userProfile.first()
            if (cached == null || cached.uid != existing.uid) {
                updateProfileCache(existing)
            }
            return existing
        }

        // 2. If no server profile exists, create a new one.
        val newUser = AppUserEntity(
            uid = uid,
            name = name.ifBlank { email.substringBefore("@") },
            handle = email.substringBefore("@").replace(".", "_").lowercase(),
            email = email,
            role = UserRole.USER.roleKey,
            avatarType = "default",
            coverType = "default",
            bio = "",
            location = "",
            isBanned = false,
            banReason = "",
            canManageUsers = false,
            canDeletePosts = false,
            canEditPosts = false,
            canModerateComments = false,
            canManageChats = false,
            canManageMonetization = false,
            canManageRewards = false,
            canCleanStorage = false,
            isPublic = true,
            registeredAt = System.currentTimeMillis(),
            avatarProvider = "cloudflare_r2",
            coverProvider = "cloudflare_r2"
        )

        // Server persists the profile first; the local row is only cached once the server confirms.
        supabaseService.syncUser(newUser).getOrThrow()
        dao.insertUser(newUser)

        // Initialize or update user profile entity (display cache only).
        updateProfileCache(newUser)
        return newUser
    }

    private suspend fun updateProfileCache(user: AppUserEntity) {
        dao.insertOrUpdateProfile(
            UserProfileEntity(
                id = 1,
                uid = user.uid,
                name = user.name,
                handle = user.handle,
                bio = user.bio,
                location = user.location,
                avatarType = user.avatarType,
                coverType = user.coverType,
                avatarPath = user.avatarPath,
                coverPath = user.coverPath,
                isPublic = user.isPublic,
                avatarProvider = user.avatarProvider,
                coverProvider = user.coverProvider,
                avatarMime = user.avatarMime,
                coverMime = user.coverMime,
                avatarSize = user.avatarSize,
                coverSize = user.coverSize
            )
        )
    }

    suspend fun getUserByUid(uid: String): AppUserEntity? {
        return dao.getUserByUid(uid)
    }

    suspend fun updateUserRoleAndPermissions(
        uid: String,
        newRole: UserRole,
        canManageUsers: Boolean? = null,
        canDeletePosts: Boolean? = null,
        canEditPosts: Boolean? = null,
        canModerateComments: Boolean? = null,
        canManageChats: Boolean? = null,
        canManageMonetization: Boolean? = null,
        canManageRewards: Boolean? = null,
        canManageRewardRules: Boolean? = null,
        canManageRewardRates: Boolean? = null,
        canManageRewardGateways: Boolean? = null,
        canProcessPayouts: Boolean? = null,
        canCleanStorage: Boolean? = null,
        canViewReports: Boolean? = null,
        canReviewReports: Boolean? = null,
        canGiveWarning: Boolean? = null,
        canDeleteReel: Boolean? = null,
        canDeleteVideo: Boolean? = null,
        canSuspendUser: Boolean? = null,
        canBanUser: Boolean? = null,
        canRemoveWarning: Boolean? = null,
        canViewWarningHistory: Boolean? = null,
        canViewActivityLog: Boolean? = null
    ) {
        val existing = dao.getUserByUid(uid) ?: return
        
        // 1. Update Supabase first
        val result = supabaseService.updateUserRoleAndPermissionsRemote(
            uid = uid,
            role = newRole.roleKey,
            canManageUsers = canManageUsers ?: existing.canManageUsers,
            canDeletePosts = canDeletePosts ?: existing.canDeletePosts,
            canEditPosts = canEditPosts ?: existing.canEditPosts,
            canModerateComments = canModerateComments ?: existing.canModerateComments,
            canManageChats = canManageChats ?: existing.canManageChats,
            canManageMonetization = canManageMonetization ?: existing.canManageMonetization,
            canManageRewards = canManageRewards ?: existing.canManageRewards,
            canManageRewardRules = canManageRewardRules ?: existing.canManageRewardRules,
            canManageRewardRates = canManageRewardRates ?: existing.canManageRewardRates,
            canManageRewardGateways = canManageRewardGateways ?: existing.canManageRewardGateways,
            canProcessPayouts = canProcessPayouts ?: existing.canProcessPayouts,
            canCleanStorage = canCleanStorage ?: existing.canCleanStorage,
            canViewReports = canViewReports ?: existing.canViewReports,
            canReviewReports = canReviewReports ?: existing.canReviewReports,
            canGiveWarning = canGiveWarning ?: existing.canGiveWarning,
            canDeleteReel = canDeleteReel ?: existing.canDeleteReel,
            canDeleteVideo = canDeleteVideo ?: existing.canDeleteVideo,
            canSuspendUser = canSuspendUser ?: existing.canSuspendUser,
            canBanUser = canBanUser ?: existing.canBanUser,
            canRemoveWarning = canRemoveWarning ?: existing.canRemoveWarning,
            canViewWarningHistory = canViewWarningHistory ?: existing.canViewWarningHistory,
            canViewActivityLog = canViewActivityLog ?: existing.canViewActivityLog
        )

        // 2. If server update succeeds, update local cache
        if (result.isSuccess) {
            dao.updateUserRoleAndPermissions(
                uid = uid,
                role = newRole.roleKey,
                canManageUsers = canManageUsers ?: existing.canManageUsers,
                canDeletePosts = canDeletePosts ?: existing.canDeletePosts,
                canEditPosts = canEditPosts ?: existing.canEditPosts,
                canModerateComments = canModerateComments ?: existing.canModerateComments,
                canManageChats = canManageChats ?: existing.canManageChats,
                canManageMonetization = canManageMonetization ?: existing.canManageMonetization,
                canManageRewards = canManageRewards ?: existing.canManageRewards,
                canManageRewardRules = canManageRewardRules ?: existing.canManageRewardRules,
                canManageRewardRates = canManageRewardRates ?: existing.canManageRewardRates,
                canManageRewardGateways = canManageRewardGateways ?: existing.canManageRewardGateways,
                canProcessPayouts = canProcessPayouts ?: existing.canProcessPayouts,
                canCleanStorage = canCleanStorage ?: existing.canCleanStorage,
                canViewReports = canViewReports ?: existing.canViewReports,
                canReviewReports = canReviewReports ?: existing.canReviewReports,
                canGiveWarning = canGiveWarning ?: existing.canGiveWarning,
                canDeleteReel = canDeleteReel ?: existing.canDeleteReel,
                canDeleteVideo = canDeleteVideo ?: existing.canDeleteVideo,
                canSuspendUser = canSuspendUser ?: existing.canSuspendUser,
                canBanUser = canBanUser ?: existing.canBanUser,
                canRemoveWarning = canRemoveWarning ?: existing.canRemoveWarning,
                canViewWarningHistory = canViewWarningHistory ?: existing.canViewWarningHistory,
                canViewActivityLog = canViewActivityLog ?: existing.canViewActivityLog
            )
            android.util.Log.d("SocialRepository", "Role/Perms updated successfully for uid=$uid")
        } else {
            val error = result.exceptionOrNull()?.message ?: "Unknown error"
            android.util.Log.e("SocialRepository", "Failed to update role: $error")
            throw Exception("Server rejected role change: $error")
        }
    }

    suspend fun setUserBanStatus(uid: String, isBanned: Boolean, reason: String = "") {
        // 1. Update Supabase
        val result = supabaseService.updateUserBanStatusRemote(uid, isBanned, reason)

        // 2. Update Local
        if (result.isSuccess) {
            dao.updateUserBanStatus(uid, isBanned, reason)
            android.util.Log.d("SocialRepository", "Ban status updated for uid=$uid")
        } else {
            val error = result.exceptionOrNull()?.message ?: "Unknown error"
            android.util.Log.e("SocialRepository", "Failed to update ban status: $error")
            throw Exception("Server rejected ban status change: $error")
        }
    }

    suspend fun deleteUserAccount(uid: String) {
        // Delete the remote account FIRST so a server failure surfaces instead of being hidden,
        // and only clear the local row after the remote deletion actually succeeded.
        supabaseService.deleteUser(uid).getOrThrow()
        dao.deleteUserByUid(uid)
    }

    // --- SYSTEM STORAGE CLEANUP (SUPER ADMIN POWER) ---
    suspend fun addReelComment(reelId: Long, text: String, profile: UserProfileEntity, reelOwnerHandle: String = "") {
        val comment = CommentEntity(
            postId = reelId,
            username = profile.handle,
            userAvatarType = profile.avatarType,
            userAvatarPath = profile.avatarPath,
            text = text,
            timeAgo = "Just now",
        )
        val created = supabaseService.addComment(comment, reelId.toString(), profile.uid).getOrThrow()
        dao.insertComment(created.copy(postId = reelId))

        if (reelOwnerHandle.isNotBlank() && reelOwnerHandle != profile.handle) {
            val notif = NotificationEntity(
                username = profile.handle,
                recipientHandle = reelOwnerHandle,
                avatarType = profile.avatarType,
                actionText = "commented on your reel: $text",
                timeAgo = "Just now",
                isRead = false,
                timestamp = System.currentTimeMillis()
            )
            runCatching { supabaseService.sendNotification(notif) }
        }
    }

    suspend fun cleanAllPosts() {
        dao.deleteAllPosts()
        dao.deleteAllComments()
    }

    suspend fun cleanAllChats() {
        dao.clearAllChatMessages()
    }

    suspend fun cleanAllNotifications() {
        dao.clearAllNotifications()
    }

    suspend fun cleanAllStories() {
        dao.deleteAllStories()
    }

    suspend fun cleanAllConnections() {
        dao.deleteAllFriends()
    }

    suspend fun wipeAllDataExceptUsers() {
        dao.deleteAllPosts()
        dao.deleteAllComments()
        dao.deleteAllStories()
        dao.clearAllChatMessages()
        dao.clearAllNotifications()
        dao.deleteAllFriends()
        
        // Hard command: Reset all user media to default
        dao.resetAllUserMedia()
        runCatching { supabaseService.wipeAllUserMediaRemote() }
    }

    suspend fun toggleLike(post: PostEntity, currentUid: String) {
        if (currentUid.isBlank()) throw Exception("Authentication required to like")
        val newLiked = !post.isLiked
        val remotePostId = post.remoteId.toLongOrNull() ?: throw Exception("Post has no remote ID")

        // 1. Update Supabase first
        if (post.isReelPost) {
            supabaseService.toggleReelLike(remotePostId, currentUid, newLiked).getOrThrow()
        } else {
            supabaseService.togglePostLike(remotePostId, currentUid, newLiked).getOrThrow()
        }
        
        // 2. Local Room update reflects confirmed state
        val updated = post.copy(
            isLiked = newLiked,
            likesCount = if (newLiked) post.likesCount + 1 else (post.likesCount - 1).coerceAtLeast(0)
        )
        dao.updatePost(updated)
    }

    suspend fun toggleSave(post: PostEntity, currentUid: String) {
        if (currentUid.isBlank()) throw Exception("Authentication required to save")
        val newSaved = !post.isSaved
        val remotePostId = post.remoteId.toLongOrNull() ?: throw Exception("Post has no remote ID")
        
        // 1. Update Supabase first
        if (post.isReelPost) {
            supabaseService.toggleReelSave(remotePostId, currentUid, newSaved).getOrThrow()
        } else {
            supabaseService.togglePostSave(remotePostId, currentUid, newSaved).getOrThrow()
        }
        
        // 2. Local Room update
        dao.updatePost(post.copy(isSaved = newSaved))
    }

    suspend fun toggleRepost(post: PostEntity, currentUid: String) {
        if (currentUid.isBlank()) throw Exception("Authentication required to repost")
        val newRepost = !post.isReposted
        val remotePostId = post.remoteId.toLongOrNull() ?: throw Exception("Post has no remote ID")
        
        // 1. Update Supabase first
        if (post.isReelPost) {
            supabaseService.toggleReelRepost(remotePostId, currentUid, newRepost).getOrThrow()
        } else {
            supabaseService.togglePostRepost(remotePostId, currentUid, newRepost).getOrThrow()
        }
        
        // 2. Local Room update
        val newCount = if (newRepost) post.repostsCount + 1 else (post.repostsCount - 1).coerceAtLeast(0)
        dao.updatePost(post.copy(isReposted = newRepost, repostsCount = newCount))
    }

    suspend fun addComment(postId: Long, text: String, profile: UserProfileEntity, ownerHandle: String = "") {
        val post = dao.getPostById(postId) ?: return
        val remoteKey = post.remoteId.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Post has no remote ID")
        val comment = CommentEntity(
            postId = postId,
            username = profile.handle,
            userAvatarType = profile.avatarType,
            userAvatarPath = profile.avatarPath,
            text = text,
            timeAgo = "Just now"
        )
        // Remote first: comment is only cached locally once Supabase returns its assigned id.
        // Server-side triggers now handle comment count increments automatically and accurately.
        val created = supabaseService.addComment(comment, remoteKey, profile.uid).getOrThrow()
        dao.insertComment(created.copy(postId = postId))

        if (ownerHandle.isNotBlank() && ownerHandle != profile.handle) {
            val notif = NotificationEntity(
                username = profile.handle,
                recipientHandle = ownerHandle,
                avatarType = profile.avatarType,
                actionText = "commented on your post: $text",
                timeAgo = "Just now",
                isRead = false,
                timestamp = System.currentTimeMillis()
            )
            runCatching { supabaseService.sendNotification(notif) }
        }
    }

    suspend fun createPost(
        profile: UserProfileEntity,
        caption: String,
        actionType: String,
        imageRes: String,
        storagePath: String? = null,
        mimeType: String? = null,
        fileSize: Long = 0,
        provider: String = "cloudflare_r2"
    ) {
        val actionText = when (actionType) {
            "cover" -> "${profile.handle} updated their cover photo"
            "profile" -> "${profile.handle} updated their profile picture · Just now"
            else -> ""
        }
        val post = PostEntity(
            username = profile.name,
            userHandle = profile.handle,
            userAvatarType = com.example.util.MediaStorageResolver.toStorableKey(profile.avatarType),
            userAvatarPath = com.example.util.MediaStorageResolver.toStorableKey(profile.avatarPath),
            actionText = actionText,
            postImageRes = com.example.util.MediaStorageResolver.toStorableKey(imageRes),
            storagePath = com.example.util.MediaStorageResolver.toStorableKey(storagePath),
            caption = caption,
            likesCount = 0,
            isLiked = false,
            isSaved = false,
            isReposted = false,
            repostsCount = 0,
            commentsCount = 0,
            isPublic = true,
            timeAgo = "Just now",
            timestamp = System.currentTimeMillis(),
            storageProvider = provider,
            mimeType = mimeType,
            fileSize = fileSize
        )
        // Source of truth is Supabase: create remotely first, cache locally only on success.
        val created = supabaseService.createPost(post.copy(id = 0)).getOrThrow()
        val cached = created.copy(id = 0)
        dao.insertPost(cached)
        dao.insertOrUpdateProfile(profile.copy(postsCount = profile.postsCount + 1))

        // The success notification is only inserted after the backend actually persisted the post.
        val notif = NotificationEntity(
            username = profile.handle,
            recipientHandle = profile.handle,
            avatarType = profile.avatarType,
            actionText = "Your post was published successfully.",
            timeAgo = "Just now",
            isRead = false
        )
        dao.insertNotification(notif)
        runCatching { supabaseService.sendNotification(notif) }
    }

    suspend fun deletePost(postId: Long) {
        val post = dao.getPostById(postId) ?: return
        val remoteKey = post.remoteId.takeIf { it.isNotBlank() }
        
        // 1. HARD DELETE every R2 object that belongs to this post.
        val targets = listOfNotNull(post.postImageRes, post.storagePath, post.thumbnailPath)
        for (target in targets) {
            if (target.isNotBlank() && target != "default") {
                android.util.Log.d("SocialRepository", "Hard-deleting R2 post media: $target")
                runCatching { supabaseService.deleteMediaFromR2(target).getOrThrow() }
            }
        }

        // 2. Delete Supabase DB reference.
        if (remoteKey != null) {
            android.util.Log.d("SocialRepository", "Deleting Supabase row for post: $remoteKey")
            runCatching { supabaseService.deletePost(remoteKey) }
        }

        // 3. Finally, clear local cache.
        dao.deletePost(postId)
    }

    // --- REELS OPERATIONS & SYNC ---
    suspend fun createReel(
        profile: UserProfileEntity,
        caption: String,
        music: String,
        imageRes: String,
        videoUrl: String? = null,
        storagePath: String? = null,
        thumbnailPath: String? = null,
        location: String = "",
        durationSecs: Int = 0,
        mimeType: String? = null,
        fileSize: Long = 0,
        provider: String = "cloudflare_r2"
    ): Long {
        val reel = ReelEntity(
            author = profile.name,
            handle = profile.handle,
            avatarType = com.example.util.MediaStorageResolver.toStorableKey(profile.avatarType),
            userAvatarPath = com.example.util.MediaStorageResolver.toStorableKey(profile.avatarPath),
            caption = caption,
            music = music.ifBlank { "Original Audio" },
            imageRes = com.example.util.MediaStorageResolver.toStorableKey(imageRes.ifBlank { "" }),
            videoUrl = com.example.util.MediaStorageResolver.toStorableKey(videoUrl),
            storagePath = com.example.util.MediaStorageResolver.toStorableKey(storagePath),
            thumbnailPath = com.example.util.MediaStorageResolver.toStorableKey(thumbnailPath),
            location = location,
            likesCount = 0,
            commentsCount = 0,
            sharesCount = 0,
            durationSecs = durationSecs,
            isLiked = false,
            isSaved = false,
            timestamp = System.currentTimeMillis(),
            storageProvider = provider,
            mimeType = mimeType,
            fileSize = fileSize
        )
        // Remote first: cache the reel only after Supabase persists it and returns its remote id.
        val created = supabaseService.createReel(reel.copy(id = 0)).getOrThrow()
        val cached = created.copy(id = 0)
        dao.insertReel(cached)

        // ALSO SHARE AS A POST ON HOME FEED & PROFILE
        val reelPost = PostEntity(
            remoteId = cached.remoteId,
            username = profile.name,
            userHandle = profile.handle,
            userAvatarType = profile.avatarType,
            userAvatarPath = profile.avatarPath,
            actionText = "${profile.handle} shared a new reel",
            postImageRes = cached.imageRes,
            storagePath = cached.storagePath,
            thumbnailPath = cached.thumbnailPath,
            caption = caption,
            likesCount = 0,
            isLiked = false,
            isSaved = false,
            isReposted = false,
            repostsCount = 0,
            commentsCount = 0,
            isPublic = true,
            isReelPost = true,
            timeAgo = "Just now",
            timestamp = cached.timestamp,
            storageProvider = cached.storageProvider,
            mimeType = cached.mimeType,
            fileSize = cached.fileSize
        )
        // Persist the post link too
        runCatching { 
            val createdPost = supabaseService.createPost(reelPost.copy(id = 0)).getOrThrow()
            dao.insertPost(createdPost.copy(id = 0))
        }

        return cached.id
    }

    suspend fun toggleReelLike(reel: ReelEntity, currentUid: String) {
        if (currentUid.isBlank()) throw Exception("Authentication required to like")
        val newLiked = !reel.isLiked
        val remoteReelId = reel.remoteId.toLongOrNull() ?: reel.id.takeIf { it > 0 }
            ?: throw Exception("Reel has no remote ID")

        // 1. Update Supabase first
        supabaseService.toggleReelLike(remoteReelId, currentUid, newLiked).getOrThrow()

        // 2. Update local confirmed state
        val newCount = if (newLiked) reel.likesCount + 1 else (reel.likesCount - 1).coerceAtLeast(0)
        dao.updateReelLike(reel.id, newLiked, newCount)
    }

    suspend fun toggleReelSave(reel: ReelEntity, currentUid: String) {
        if (currentUid.isBlank()) throw Exception("Authentication required to save")
        val newSaved = !reel.isSaved
        val remoteReelId = reel.remoteId.toLongOrNull() ?: reel.id.takeIf { it > 0 }
            ?: throw Exception("Reel has no remote ID")

        // 1. Update Supabase first
        supabaseService.toggleReelSave(remoteReelId, currentUid, newSaved).getOrThrow()

        // 2. Update local state
        dao.updateReelSave(reel.id, newSaved)
    }

    suspend fun toggleReelRepost(reel: ReelEntity, currentUid: String) {
        if (currentUid.isBlank()) throw Exception("Authentication required to repost")
        val remoteReelId = reel.remoteId.toLongOrNull() ?: reel.id.takeIf { it > 0 }
            ?: throw Exception("Reel has no remote ID")
        supabaseService.toggleReelRepost(remoteReelId, currentUid, true).getOrThrow()
    }

    suspend fun deleteReel(id: Long) {
        val reel = dao.getReelById(id) ?: return
        val remoteKey = reel.remoteId.takeIf { it.isNotBlank() }

        // 1. HARD DELETE every R2 object that belongs to this reel.
        //    Try the stable storage paths first, then fall back to URLs.
        //    Each target is attempted independently so one failure never
        //    blocks the rest of the cleanup.
        val r2Targets = mutableListOf<String?>()
        r2Targets += reel.storagePath
        r2Targets += reel.thumbnailPath
        r2Targets += reel.videoUrl?.takeIf { it.startsWith("http") }
        r2Targets += reel.imageRes.takeIf { it.startsWith("http") }
        for (target in r2Targets.filter { !it.isNullOrBlank() }) {
            val t = target!!.trim()
            try {
                android.util.Log.d("SocialRepository", "Hard-deleting R2 object for reel $id: $t")
                supabaseService.deleteMediaFromR2(t).getOrThrow()
            } catch (e: Exception) {
                android.util.Log.w("SocialRepository", "R2 delete attempt failed for reel $id ($t): ${e.message}")
            }
        }

        // 2. HARD DELETE the remote DB row (never leaves a ghost reel behind).
        if (remoteKey != null) {
            try {
                supabaseService.deleteReel(remoteKey)
            } catch (e: Exception) {
                android.util.Log.w("SocialRepository", "Remote reel row delete failed for $remoteKey: ${e.message}")
            }
        }

        // 3. ALWAYS clear the local cache last.
        dao.deleteReel(id)
    }

    suspend fun updateReel(reel: ReelEntity): Result<Unit> {
        dao.updateReel(reel)
        return if (reel.remoteId.isNotBlank()) {
            supabaseService.updateReelRemote(reel.remoteId, reel.caption, reel.music)
        } else {
            Result.success(Unit)
        }
    }

    suspend fun syncReelsFromSupabase(reels: List<ReelEntity>, currentUid: String = "") {
        val likedReels = if (currentUid.isNotBlank()) {
            supabaseService.fetchUserLikedReels(currentUid)
        } else emptySet()

        val localized = reels.filter { 
            !com.example.util.MediaStorageResolver.isBrokenLegacyB2(it.videoUrl, it.storagePath) 
        }.map { reel -> 
            reel.copy(
                isLiked = reel.remoteId.toLongOrNull() in likedReels
            )
        }
        val remoteIds = localized.map { it.remoteId }.filter { it.isNotBlank() }.toSet()
        
        // NEVER wipe the local cache when the remote list is empty: an empty
        // result almost always means the fetch FAILED (network hiccup, expired
        // token, temporary permission error), not that every reel was deleted.
        // Wiping here is what made the Reels feed go permanently white/blank.
        if (localized.isNotEmpty()) {
            val reelSnapshot = dao.getReelsSnapshot()
            // Drop reels already duplicated in the cache (same remoteId), keep first.
            reelSnapshot.groupBy { it.remoteId }.filter { it.key.isNotBlank() }.forEach { (_, rows) ->
                rows.drop(1).forEach { dao.deleteReel(it.id) }
            }
            reelSnapshot.forEach { cached ->
                if (cached.remoteId.isNotBlank() && cached.remoteId !in remoteIds) {
                    dao.deleteReel(cached.id)
                }
            }
            android.util.Log.d("SocialRepository", "Caching ${localized.size} Supabase reels")
            // Reuse local ids for rows we already cached so REPLACE updates in place
            // (inserting with id=0 would duplicate every reel on each sync).
            val localByRemoteId = dao.getReelsSnapshot().associateBy { it.remoteId }
            val deduped = localized.map { r ->
                localByRemoteId[r.remoteId]?.let { r.copy(id = it.id) } ?: r
            }.distinctBy { it.remoteId.ifBlank { "local_${it.id}" } }
            dao.insertReels(deduped)
        } else {
            android.util.Log.w("SocialRepository", "Reel sync skipped: remote fetch returned 0 reels (kept local cache)")
        }
    }

    suspend fun refreshReelsFromSupabase() {
        val reels = supabaseService.fetchReels()
        if (reels.isNotEmpty()) {
            dao.deleteAllReels()
            dao.insertReels(reels)
        }
    }

    suspend fun syncPostsFromSupabase(posts: List<PostEntity>, currentUid: String = "") {
        val likedPosts = if (currentUid.isNotBlank()) supabaseService.fetchUserLikedPosts(currentUid) else emptySet()
        val repostedPosts = if (currentUid.isNotBlank()) supabaseService.fetchUserRepostedPosts(currentUid) else emptySet()
        val savedPosts = if (currentUid.isNotBlank()) supabaseService.fetchUserSavedPosts(currentUid) else emptySet()

        val validPosts = posts.filter { post ->
            val legacyMedia = post.postImageRes.trim()
            val isBroken = com.example.util.MediaStorageResolver.isBrokenLegacyB2(post.postImageRes, post.storagePath)
            
            val hasLegacyMedia = legacyMedia.isNotEmpty() && 
                    !legacyMedia.equals("default", ignoreCase = true) && 
                    !legacyMedia.equals("null", ignoreCase = true)
            val hasStableMedia = !post.storagePath.isNullOrBlank()
            
            (hasLegacyMedia || hasStableMedia) && !isBroken
        }.map { post ->
            val rId = post.remoteId.toLongOrNull()
            post.copy(
                isLiked = rId in likedPosts,
                isReposted = rId in repostedPosts,
                isSaved = rId in savedPosts
            )
        }

        val remoteIds = validPosts.map { it.remoteId }.filter { it.isNotBlank() }.toSet()
        val snapshot = dao.getPostsSnapshot()
        // First drop duplicates already in the cache (same remoteId cached multiple
        // times by earlier syncs) — keep the first occurrence of each remoteId.
        snapshot.groupBy { it.remoteId }.filter { it.key.isNotBlank() }.forEach { (rid, rows) ->
            rows.drop(1).forEach { dao.deletePost(it.id) }
        }
        snapshot.forEach { cached ->
            if (cached.remoteId.isNotBlank() && cached.remoteId !in remoteIds) {
                dao.deletePost(cached.id)
            }
        }
        if (validPosts.isNotEmpty()) {
            android.util.Log.d("SocialRepository", "Caching ${validPosts.size} Supabase posts")
            // Reuse local ids for rows we already cached so REPLACE updates in place.
            // Without this, inserting with id=0 auto-generates a NEW row for every
            // remote post on every sync → duplicates pile up in the feed.
            val localByRemoteId = dao.getPostsSnapshot().associateBy { it.remoteId }
            val deduped = validPosts.map { p ->
                localByRemoteId[p.remoteId]?.let { p.copy(id = it.id) } ?: p
            }.distinctBy { it.remoteId.ifBlank { "local_${it.id}" } }
            dao.insertPosts(deduped)
        }
    }

    suspend fun refreshComments(post: PostEntity) {
        val remoteKey = post.remoteId.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Post has no remote ID")
        val comments = supabaseService.fetchComments(remoteKey)
        dao.deleteCommentsForPost(post.id)
        comments.forEach { dao.insertComment(it.copy(postId = post.id)) }
    }

    suspend fun deleteComment(comment: CommentEntity) {
        val remoteKey = comment.remoteId.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Comment has no remote ID")
        supabaseService.deleteComment(remoteKey)
        dao.deleteComment(comment.id)
    }

    suspend fun deleteStory(story: StoryEntity) {
        if (!story.isOwn) throw IllegalStateException("Cannot delete another user's story")
        
        // 1. HARD DELETE every R2 object that belongs to this story.
        val targets = listOfNotNull(story.imageRes, story.storagePath)
        for (target in targets) {
            if (target.isNotBlank() && target != "default") {
                android.util.Log.d("SocialRepository", "Hard-deleting R2 story media: $target")
                runCatching { supabaseService.deleteMediaFromR2(target).getOrThrow() }
            }
        }

        // 2. Delete Supabase row
        runCatching { supabaseService.deleteStory(story.id.toString()) }

        // 3. Clear local
        dao.deleteStory(story.id)
    }

    suspend fun syncStoriesFromSupabase(stories: List<StoryEntity>) {
        if (stories.isNotEmpty()) {
            android.util.Log.d("SocialRepository", "Caching ${stories.size} Supabase stories")
            dao.insertStories(stories)
        }

    }

    suspend fun clearCachedContent() {
        dao.deleteAllPosts()
        dao.deleteAllReels()
        dao.deleteAllStories()
    }

    suspend fun removeEmptyMediaPostsFromCache() {
        dao.deleteEmptyMediaPosts()
        // Also try to clean up on the server
        runCatching { supabaseService.cleanupCorruptedPostsRemote() }
    }

    suspend fun syncChatMessagesFromSupabase(messages: List<ChatMessageEntity>): List<ChatMessageEntity> {
        if (messages.isEmpty()) return messages
        // Only insert messages that don't exist locally (remoteId check).
        // Room REPLACE strategy is now backed by a UNIQUE index on remoteId, but
        // explicit filtering prevents unnecessary Flow emissions in the ViewModel.
        android.util.Log.d("SocialRepository", "Syncing ${messages.size} chat messages to Room")
        dao.insertChatMessages(messages)
        return messages
    }

    /**
     * Repairs the local cache: legacy DM rows were stored under inconsistent room ids
     * ("dm_<partner>", "dm_<a>__<b>" in any order, even blank-partner rooms), which made the
     * inbox show one separate "new chat" per id and split one conversation into many. Rewires
     * every DM room into its canonical "dm_<sorted handles>" form.
     */
    suspend fun healLegacyChatRooms(currentHandle: String) {
        if (currentHandle.isBlank()) return
        val myHandle = currentHandle.lowercase().trim()
        val rooms = dao.getDistinctChatRoomIds().filter { it.startsWith("dm_") }
        for (oldRoom in rooms) {
            // Find ALL messages in this room to resolve the real partner
            val sample = dao.getChatMessagesForRoom(oldRoom).first().firstOrNull() ?: continue
            val partner = sample.senderHandle.takeIf { !it.equals(myHandle, ignoreCase = true) }
                ?: sample.receiverHandle.takeIf { !it.equals(myHandle, ignoreCase = true) }
                ?: oldRoom.removePrefix("dm_").split("__").firstOrNull { it.isNotBlank() && !it.equals(myHandle, ignoreCase = true) }
                ?: continue
            
            val canonical = "dm_" + listOf(myHandle, partner.lowercase().trim())
                .sorted()
                .joinToString("__")
            
            if (canonical != oldRoom) {
                android.util.Log.d("SocialRepository", "Healing legacy chat room: $oldRoom -> $canonical")
                dao.rewireChatRoom(oldRoom, canonical)
            }
        }
    }

    suspend fun syncUsersFromSupabase(users: List<AppUserEntity>) {
        if (users.isNotEmpty()) {
            android.util.Log.d("SocialRepository", "Syncing ${users.size} users to Room (Full Replacement)")
            
            // CRITICAL: To ensure users deleted on the server disappear from the app,
            // we must treat the fetched list as the source of truth and clear the local table.
            dao.deleteAllUsers()
            dao.insertUsers(users)

            val cachedProfile = userProfile.first()
            val remoteProfile = users.firstOrNull { it.uid == cachedProfile?.uid }
            if (remoteProfile != null && cachedProfile != null) {
                dao.insertOrUpdateProfile(
                    cachedProfile.copy(
                        name = remoteProfile.name,
                        handle = remoteProfile.handle,
                        bio = remoteProfile.bio,
                        location = remoteProfile.location,
                        avatarType = remoteProfile.avatarType,
                        coverType = remoteProfile.coverType,
                        isPublic = remoteProfile.isPublic
                    )
                )
            }
        }
    }
    suspend fun searchUsersRemote(query: String): List<AppUserEntity> {
        // Use real backend search instead of client-side filtering
        return supabaseService.searchUsers(query)
    }

    suspend fun markChatMessagesAsSeen(roomId: String, userId: String = "", senderHandle: String = "") {
        // Marking as seen must only happen when the user actually opens the thread. The local Room
        // rows for received messages are flipped, and a per-user read receipt is sent to Supabase.
        dao.markRoomMessagesRead(roomId)
        if (userId.isNotBlank() && senderHandle.isNotBlank()) {
            runCatching { supabaseService.markChatMessagesReadForUser(roomId, userId, senderHandle) }
        }
    }

    suspend fun clearAllPosts() {
        dao.deleteAllPosts()
    }

    fun getChatMessagesForRoom(roomId: String): Flow<List<ChatMessageEntity>> {
        return dao.getChatMessagesForRoom(roomId)
    }

    suspend fun sendChatMessage(
        roomId: String,
        text: String,
        profile: UserProfileEntity,
        mediaUrl: String? = null,
        storagePath: String? = null,
        mediaType: String = "text",
        audioDurationSec: Int = 0,
        originalText: String = "",
        isTranslated: Boolean = false,
        translationLang: String = "",
        receiverHandle: String = "",
        storageProvider: String = "cloudflare_r2"
    ) {
        val entity = ChatMessageEntity(
            roomId = roomId,
            senderName = profile.name,
            senderHandle = profile.handle,
            receiverHandle = receiverHandle,
            senderAvatar = com.example.util.MediaStorageResolver.toStorableKey(profile.avatarType),
            senderAvatarPath = com.example.util.MediaStorageResolver.toStorableKey(profile.avatarPath),
            messageText = text,
            originalText = originalText,
            isTranslated = isTranslated,
            translationLang = translationLang,
            mediaUrl = com.example.util.MediaStorageResolver.toStorableKey(mediaUrl),
            storagePath = com.example.util.MediaStorageResolver.toStorableKey(storagePath),
            storageProvider = storageProvider,
            mediaType = mediaType,
            time = "Just now",
            isFromMe = true,
            // New outgoing messages are NOT flagged read for the recipient.
            isRead = false,
            audioDurationSec = audioDurationSec,
            timestamp = System.currentTimeMillis()
        )
        // Remote first: cache only after Supabase persists the message and returns its remote id.
        val created = supabaseService.sendChatMessage(entity.copy(id = 0)).getOrThrow()
        dao.insertChatMessage(created.copy(id = 0))
    }

    suspend fun insertIncomingChatMessage(message: ChatMessageEntity) {
        dao.insertChatMessage(message)
    }

    suspend fun clearRoomMessages(roomId: String) {
        dao.clearChatMessagesForRoom(roomId)
    }

    suspend fun updateProfile(profile: UserProfileEntity) {
        // Server first: the local cache must not claim an update that Supabase rejected.
        supabaseService.patchUserProfile(
            uid = profile.uid,
            avatarType = profile.avatarType,
            coverType = profile.coverType,
            avatarPath = profile.avatarPath,
            coverPath = profile.coverPath,
            name = profile.name,
            handle = profile.handle,
            bio = profile.bio,
            location = profile.location,
            isPublic = profile.isPublic,
            avatarProvider = "cloudflare_r2",
            coverProvider = "cloudflare_r2"
        ).getOrThrow()
        dao.insertOrUpdateProfile(profile)
        dao.getUserByUid(profile.uid)?.let { user ->
            dao.updateUser(
                user.copy(
                    name = profile.name,
                    handle = profile.handle,
                    avatarType = profile.avatarType,
                    coverType = profile.coverType,
                    avatarPath = profile.avatarPath,
                    coverPath = profile.coverPath,
                    bio = profile.bio,
                    location = profile.location,
                    isPublic = profile.isPublic,
                    registeredAt = user.registeredAt,
                    avatarProvider = "cloudflare_r2",
                    coverProvider = "cloudflare_r2"
                )
            )
        }
    }

    suspend fun deleteProfileMedia(profile: UserProfileEntity, deleteAvatar: Boolean) {
        val mediaUrl = if (deleteAvatar) profile.avatarType else profile.coverType
        val mediaPath = if (deleteAvatar) profile.avatarPath else profile.coverPath

        // 1. Hard delete all related R2 objects
        val targets = listOfNotNull(mediaUrl, mediaPath)
        for (target in targets) {
            if (target.isNotBlank() && target != "default") {
                android.util.Log.d("SocialRepository", "Hard-deleting R2 profile media: $target")
                runCatching { supabaseService.deleteMediaFromR2(target).getOrThrow() }
            }
        }

        val actionPattern = if (deleteAvatar) "%profile picture%" else "%cover photo%"
        dao.getUserPostsByAction(profile.handle, actionPattern).forEach { post ->
            val remoteKey = post.remoteId.takeIf { it.isNotBlank() }
            
            // Delete post media from R2
            val pTargets = listOfNotNull(post.postImageRes, post.storagePath, post.thumbnailPath)
            for (t in pTargets) {
                if (t.isNotBlank() && t != "default") {
                    runCatching { supabaseService.deleteMediaFromR2(t).getOrThrow() }
                }
            }
            
            if (remoteKey != null) runCatching { supabaseService.deletePost(remoteKey) }
            dao.deletePost(post.id)
        }

        updateProfile(
            if (deleteAvatar) profile.copy(avatarType = "default", avatarPath = null)
            else profile.copy(coverType = "default", coverPath = null)
        )
    }

    suspend fun uploadMedia(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        uploadType: String, // "profile", "cover", "post", "reel", "story", "chat"
        onProgress: ((Int) -> Unit)? = null
    ): Result<MediaUploadResult> {
        val resolvedType = if (uploadType == "post") {
            if (mimeType.startsWith("video/")) "post_video" else "post_image"
        } else uploadType

        android.util.Log.d("SocialRepository", "Uploading $resolvedType: $fileName")
        val res = supabaseService.uploadMediaToR2(bytes, fileName, mimeType, resolvedType, onProgress)
        return res.map { json ->
            val url = json.optString("url", "")
            // Check all possible return keys
            val storagePath = json.optString("key", "")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("path", "")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("storage_path", "")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: com.example.util.MediaStorageResolver.toStorableKey(url)
            
            val thumbnailPath = json.optString("thumbnail_path")
                .takeIf { it != "null" && it.isNotBlank() }
                ?.let { com.example.util.MediaStorageResolver.toStorableKey(it) }
            
            if (url.isBlank() && storagePath.isBlank()) {
                android.util.Log.e("SocialRepository", "R2 Upload failed. Response: $json")
                throw Exception("Upload failed: Server did not return a valid path")
            }
            
            MediaUploadResult(
                url = url, 
                storagePath = storagePath, 
                thumbnailPath = thumbnailPath,
                mimeType = json.optString("type").takeIf { it.isNotBlank() },
                fileSize = json.optLong("size", 0),
                provider = "cloudflare_r2"
            )
        }
    }


    suspend fun addStory(
        imageRes: String, 
        storagePath: String?, 
        caption: String, 
        profile: UserProfileEntity,
        mimeType: String? = null,
        fileSize: Long = 0,
        provider: String = "cloudflare_r2"
    ) {
        val story = StoryEntity(
            username = profile.handle,
            userAvatarType = com.example.util.MediaStorageResolver.toStorableKey(profile.avatarType),
            userAvatarPath = com.example.util.MediaStorageResolver.toStorableKey(profile.avatarPath),
            imageRes = com.example.util.MediaStorageResolver.toStorableKey(imageRes),
            storagePath = com.example.util.MediaStorageResolver.toStorableKey(storagePath),
            caption = caption,
            isOwn = true,
            timestamp = System.currentTimeMillis(),
            storageProvider = provider,
            mimeType = mimeType,
            fileSize = fileSize
        )
        val created = supabaseService.createStory(story).getOrThrow()
        dao.insertStory(created)
    }

    suspend fun syncNotificationsFromSupabase(notifications: List<NotificationEntity>) {
        if (notifications.isNotEmpty()) {
            dao.insertNotifications(notifications)
        }
    }

    suspend fun markNotificationsRead() {
        dao.markNotificationsAsRead()
    }

    suspend fun markNotificationRead(id: Long) {
        // Find remoteId if available, or use the local id if it's the same
        dao.markNotificationAsRead(id)
        runCatching { supabaseService.markNotificationReadRemote(id) }
    }

    suspend fun deleteNotification(id: Long) {
        dao.deleteNotification(id)
    }

    suspend fun clearAllNotifications() {
        dao.clearAllNotifications()
    }

    suspend fun addNotification(username: String, avatarType: String, actionText: String, recipient: String = "") {
        val notif = NotificationEntity(
            username = username,
            recipientHandle = recipient,
            avatarType = avatarType,
            actionText = actionText,
            timeAgo = "Just now",
            isRead = false,
            timestamp = System.currentTimeMillis()
        )
        val id = dao.insertNotification(notif)
        scope.launch {
            supabaseService.sendNotification(notif.copy(id = id))
        }
    }

    // Mutual Follow and Friends Operations
    suspend fun toggleFollow(friendId: String, currentProfile: UserProfileEntity) {
        val existing = dao.getFriendById(friendId) ?: return
        val newFollowing = !existing.isFollowing
        val newIsFriend = newFollowing && existing.isFollower

        // Remote first: persist the follow relationship before touching the local cache. The `follows`
        // table uses stable user identifiers (uid or handle), never device-local row ids.
        supabaseService.upsertFollow(
            followerUid = currentProfile.uid,
            followingUid = existing.id,
            isFollowing = newFollowing
        )

        dao.updateFollowStatus(
            id = friendId,
            isFollowing = newFollowing,
            isFriend = newIsFriend
        )

        // Keep the cached follow graph in sync for instant UI feedback.
        val graph = _followState.value
        _followState.value = if (newFollowing) graph.copy(following = graph.following + friendId)
                             else graph.copy(following = graph.following - friendId)

        if (newIsFriend) {
            addNotification(
                username = existing.handle,
                avatarType = existing.avatarType,
                                actionText = "followed you back! You are now Friends 🤝🎉"
            )
        } else if (newFollowing) {
            addNotification(
                username = existing.handle,
                avatarType = existing.avatarType,
                actionText = "is now in your following list ✨"
            )
        }

        // Notify the followed user on the server so THEY receive a notification.
        if (newFollowing) {
            supabaseService.sendFollowNotification(
                targetHandle = existing.handle,
                actionText = if (newIsFriend) "followed you back — you are now Friends 🤝🎉"
                             else "started following you 🤝"
            )

            // Push to the followed user's other devices via the secured Edge
            // Function (it re-verifies the follow row server-side). Fire-and-forget.
            scope.launch {
                pushService.notifyFollow(existing.id).onFailure {
                    android.util.Log.w("SocialRepository", "Follow push skipped", it)
                }
            }
        }

        val updatedFollowers = if (newIsFriend) currentProfile.followersCount else currentProfile.followersCount
        val updatedFollowing = if (newFollowing) currentProfile.followingCount + 1 else maxOf(0, currentProfile.followingCount - 1)
        val updatedFriends = if (newIsFriend) currentProfile.friendsCount + 1 else if (!newFollowing && existing.isFriend) maxOf(0, currentProfile.friendsCount - 1) else currentProfile.friendsCount

        dao.insertOrUpdateProfile(
            currentProfile.copy(
                friendsCount = updatedFriends,
                followingCount = updatedFollowing,
                followersCount = updatedFollowers
            )
        )
    }

    suspend fun unfriend(friendId: String, currentProfile: UserProfileEntity) {
        val existing = dao.getFriendById(friendId) ?: return
        supabaseService.upsertFollow(
            followerUid = currentProfile.uid,
            followingUid = existing.id,
            isFollowing = false
        )
        dao.updateFollowStatus(id = friendId, isFollowing = false, isFriend = false)
        addNotification(
            username = existing.handle,
            avatarType = existing.avatarType,
            actionText = "was removed from your friends list"
        )
        dao.insertOrUpdateProfile(
            currentProfile.copy(
                friendsCount = maxOf(0, currentProfile.friendsCount - 1),
                followingCount = maxOf(0, currentProfile.followingCount - 1)
            )
        )
    }

    suspend fun toggleCloseFriend(friendId: String) {
        val existing = dao.getFriendById(friendId) ?: return
        val updated = !existing.isCloseFriend
        dao.updateCloseFriendStatus(friendId, updated)
        addNotification(
            username = existing.handle,
            avatarType = existing.avatarType,
            actionText = if (updated) "added to your Close Friends ⭐" else "removed from Close Friends"
        )
    }

    suspend fun toggleMute(friendId: String) {
        val existing = dao.getFriendById(friendId) ?: return
        dao.updateMuteStatus(friendId, !existing.isMuted)
    }

    suspend fun blockUser(friendId: String, currentProfile: UserProfileEntity) {
        val existing = dao.getFriendById(friendId) ?: return
        dao.updateBlockStatus(friendId, true)
        addNotification(
            username = existing.handle,
            avatarType = existing.avatarType,
            actionText = "was blocked ⛔"
        )
        if (existing.isFriend) {
            dao.insertOrUpdateProfile(
                currentProfile.copy(friendsCount = maxOf(0, currentProfile.friendsCount - 1))
            )
        }
    }

    suspend fun sendPokeOrWave(friendId: String, currentProfile: UserProfileEntity) {
        val existing = dao.getFriendById(friendId) ?: return
        val dmRoomId = "dm_${existing.handle}"
        sendChatMessage(
            roomId = dmRoomId,
            text = "👋 Waved to you!",
            profile = currentProfile
        )
        addNotification(
            username = existing.handle,
            avatarType = existing.avatarType,
            actionText = "received your Wave 👋"
        )
    }

    suspend fun sendGiftCredits(friendId: String, amount: Int, currentProfile: UserProfileEntity) {
        val existing = dao.getFriendById(friendId) ?: return
        val dmRoomId = "dm_${existing.handle}"
        sendChatMessage(
            roomId = dmRoomId,
            text = "🎁 Sent you $amount Flare Reward Credits! 💰",
            profile = currentProfile
        )
        addNotification(
            username = existing.handle,
            avatarType = existing.avatarType,
            actionText = "was gifted $amount Credits 🎁💰"
        )
    }
}