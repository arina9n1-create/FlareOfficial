package com.example.data.repository

import com.example.data.db.SocialDao
import com.example.data.model.*
import com.example.data.remote.SupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SocialRepository(
    private val dao: SocialDao,
    private val supabaseService: SupabaseService
) {
    private val scope = CoroutineScope(Dispatchers.IO)



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

    suspend fun initDefaultDataIfNeeded() {
        // No local seed data. All content is loaded from Supabase (the source of truth).
    }

    suspend fun cleanAllUsers() {
        dao.deleteAllUsers()
        // Deletion is a privileged operation; this call will throw if RLS/backend rejects it.
        supabaseService.deleteAllUsers()
    }

    suspend fun registerOrSyncUser(
        uid: String,
        name: String,
        email: String
    ): AppUserEntity {
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
                dao.insertOrUpdateProfile(
                    UserProfileEntity(
                        id = 1,
                        uid = existing.uid,
                        name = existing.name,
                        handle = existing.handle,
                        bio = existing.bio,
                        location = existing.location,
                        avatarType = existing.avatarType,
                        coverType = existing.coverType,
                        avatarPath = existing.avatarPath,
                        coverPath = existing.coverPath,
                        isPublic = existing.isPublic
                    )
                )
            }
            return existing
        }

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
            registeredAt = System.currentTimeMillis()
        )

        // Server persists the profile first; the local row is only cached once the server confirms.
        supabaseService.syncUser(newUser).getOrThrow()
        dao.insertUser(newUser)

        // Initialize or update user profile entity (display cache only).
        dao.insertOrUpdateProfile(
            UserProfileEntity(
                id = 1,
                uid = newUser.uid,
                name = newUser.name,
                handle = newUser.handle,
                bio = newUser.bio,
                location = newUser.location,
                postsCount = 0,
                friendsCount = 0,
                followersCount = 0,
                followingCount = 0,
                avatarType = newUser.avatarType,
                coverType = newUser.coverType,
                isPublic = newUser.isPublic
            )
        )

        return newUser
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
        canCleanStorage: Boolean? = null
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
            canCleanStorage = canCleanStorage ?: existing.canCleanStorage
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
                canCleanStorage = canCleanStorage ?: existing.canCleanStorage
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
        dao.deleteUserByUid(uid)
        // Await the server result so a failed remote deletion surfaces instead of being hidden.
        supabaseService.deleteUser(uid)
    }

    // --- SYSTEM STORAGE CLEANUP (SUPER ADMIN POWER) ---
    suspend fun addReelComment(reelId: Long, text: String, profile: UserProfileEntity) {
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
        
        // 1. Update Supabase first (Authoritative multi-user like)
        supabaseService.togglePostLike(remotePostId, currentUid, newLiked).getOrThrow()
        
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
        supabaseService.togglePostSave(remotePostId, currentUid, newSaved).getOrThrow()
        
        // 2. Local Room update
        dao.updatePost(post.copy(isSaved = newSaved))
    }

    suspend fun toggleRepost(post: PostEntity, currentUid: String) {
        if (currentUid.isBlank()) throw Exception("Authentication required to repost")
        val newRepost = !post.isReposted
        val remotePostId = post.remoteId.toLongOrNull() ?: throw Exception("Post has no remote ID")
        
        // 1. Update Supabase first
        supabaseService.togglePostRepost(remotePostId, currentUid, newRepost).getOrThrow()
        
        // 2. Local Room update
        val newCount = if (newRepost) post.repostsCount + 1 else (post.repostsCount - 1).coerceAtLeast(0)
        dao.updatePost(post.copy(isReposted = newRepost, repostsCount = newCount))
    }

    suspend fun addComment(postId: Long, text: String, profile: UserProfileEntity) {
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
    }

    suspend fun createPost(
        profile: UserProfileEntity,
        caption: String,
        actionType: String,
        imageRes: String,
        storagePath: String? = null
    ) {
        val actionText = when (actionType) {
            "cover" -> "${profile.handle} updated their cover photo"
            "profile" -> "${profile.handle} updated their profile picture · Just now"
            else -> ""
        }
        val post = PostEntity(
            username = profile.name,
            userHandle = profile.handle,
            userAvatarType = profile.avatarType,
            userAvatarPath = profile.avatarPath,
            actionText = actionText,
            postImageRes = imageRes,
            storagePath = storagePath,
            caption = caption,
            likesCount = 0,
            isLiked = false,
            isSaved = false,
            isReposted = false,
            repostsCount = 0,
            commentsCount = 0,
            isPublic = true,
            timeAgo = "Just now",
            timestamp = System.currentTimeMillis()
        )
        // Source of truth is Supabase: create remotely first, cache locally only on success.
        val created = supabaseService.createPost(post.copy(id = 0)).getOrThrow()
        val cached = created.copy(id = 0)
        dao.insertPost(cached)
        dao.insertOrUpdateProfile(profile.copy(postsCount = profile.postsCount + 1))

        // The success notification is only inserted after the backend actually persisted the post.
        val notif = NotificationEntity(
            username = profile.handle,
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
        
        // 1. HARD DELETE every B2 object that belongs to this post.
        val targets = listOfNotNull(post.postImageRes, post.storagePath, post.thumbnailPath)
        for (target in targets) {
            if (target.isNotBlank() && target != "default") {
                android.util.Log.d("SocialRepository", "Hard-deleting B2 post media: $target")
                runCatching { supabaseService.deleteMediaFromB2(target).getOrThrow() }
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
        location: String = ""
    ): Long {
        val reel = ReelEntity(
            author = profile.name,
            handle = profile.handle,
            avatarType = profile.avatarType,
            userAvatarPath = profile.avatarPath,
            caption = caption,
            music = music.ifBlank { "Original Audio" },
            imageRes = imageRes.ifBlank { "" },
            videoUrl = videoUrl,
            storagePath = storagePath,
            thumbnailPath = thumbnailPath,
            location = location,
            likesCount = 0,
            commentsCount = 0,
            sharesCount = 0,
            isLiked = false,
            isSaved = false,
            timestamp = System.currentTimeMillis()
        )
        // Remote first: cache the reel only after Supabase persists it and returns its remote id.
        val created = supabaseService.createReel(reel.copy(id = 0)).getOrThrow()
        val cached = created.copy(id = 0)
        dao.insertReel(cached)
        return cached.id
    }

    suspend fun toggleReelLike(reel: ReelEntity, currentUid: String) {
        if (currentUid.isBlank()) throw Exception("Authentication required to like")
        val newLiked = !reel.isLiked
        val remoteReelId = reel.remoteId.toLongOrNull() ?: throw Exception("Reel has no remote ID")

        // 1. Update Supabase first
        supabaseService.toggleReelLike(remoteReelId, currentUid, newLiked).getOrThrow()

        // 2. Update local confirmed state
        val newCount = if (newLiked) reel.likesCount + 1 else (reel.likesCount - 1).coerceAtLeast(0)
        dao.updateReelLike(reel.id, newLiked, newCount)
    }

    suspend fun deleteReel(id: Long) {
        val reel = dao.getReelById(id) ?: return
        val remoteKey = reel.remoteId.takeIf { it.isNotBlank() }

        // 1. HARD DELETE every B2 object that belongs to this reel.
        //    Try the stable storage paths first, then fall back to URLs.
        //    Each target is attempted independently so one failure never
        //    blocks the rest of the cleanup.
        val b2Targets = mutableListOf<String?>()
        b2Targets += reel.storagePath
        b2Targets += reel.thumbnailPath
        b2Targets += reel.videoUrl?.takeIf { it.startsWith("http") }
        b2Targets += reel.imageRes.takeIf { it.startsWith("http") }
        for (target in b2Targets.filter { !it.isNullOrBlank() }) {
            val t = target!!.trim()
            try {
                android.util.Log.d("SocialRepository", "Hard-deleting B2 object for reel $id: $t")
                supabaseService.deleteMediaFromB2(t).getOrThrow()
            } catch (e: Exception) {
                android.util.Log.w("SocialRepository", "B2 delete attempt failed for reel $id ($t): ${e.message}")
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

    suspend fun syncReelsFromSupabase(reels: List<ReelEntity>, currentUid: String = "") {
        val likedReels = if (currentUid.isNotBlank()) {
            supabaseService.fetchUserLikedReels(currentUid)
        } else emptySet()

        val localized = reels.map { reel -> 
            reel.copy(
                isLiked = reel.remoteId.toLongOrNull() in likedReels
            )
        }
        val remoteIds = localized.map { it.remoteId }.filter { it.isNotBlank() }.toSet()
        
        dao.getReelsSnapshot().forEach { cached ->
            if (cached.remoteId.isNotBlank() && cached.remoteId !in remoteIds) {
                dao.deleteReel(cached.id)
            }
        }
        if (localized.isNotEmpty()) {
            android.util.Log.d("SocialRepository", "Caching ${localized.size} Supabase reels")
            dao.insertReels(localized)
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
            val hasLegacyMedia = legacyMedia.isNotEmpty() && 
                    !legacyMedia.equals("default", ignoreCase = true) && 
                    !legacyMedia.equals("null", ignoreCase = true)
            val hasStableMedia = !post.storagePath.isNullOrBlank()
            
            hasLegacyMedia || hasStableMedia
        }.map { post ->
            val rId = post.remoteId.toLongOrNull()
            post.copy(
                isLiked = rId in likedPosts,
                isReposted = rId in repostedPosts,
                isSaved = rId in savedPosts
            )
        }

        val remoteIds = validPosts.map { it.remoteId }.filter { it.isNotBlank() }.toSet()
        dao.getPostsSnapshot().forEach { cached ->
            if (cached.remoteId.isNotBlank() && cached.remoteId !in remoteIds) {
                dao.deletePost(cached.id)
            }
        }
        if (validPosts.isNotEmpty()) {
            android.util.Log.d("SocialRepository", "Caching ${validPosts.size} Supabase posts")
            dao.insertPosts(validPosts)
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
        
        // 1. HARD DELETE every B2 object that belongs to this story.
        val targets = listOfNotNull(story.imageRes, story.storagePath)
        for (target in targets) {
            if (target.isNotBlank() && target != "default") {
                android.util.Log.d("SocialRepository", "Hard-deleting B2 story media: $target")
                runCatching { supabaseService.deleteMediaFromB2(target).getOrThrow() }
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
        if (messages.isNotEmpty()) {
            android.util.Log.d("SocialRepository", "Syncing ${messages.size} chat messages to Room")
            dao.insertChatMessages(messages)
        }
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
            android.util.Log.d("SocialRepository", "Syncing ${users.size} users to Room")
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
        receiverHandle: String = ""
    ) {
        val entity = ChatMessageEntity(
            roomId = roomId,
            senderName = profile.name,
            senderHandle = profile.handle,
            receiverHandle = receiverHandle,
            senderAvatar = profile.avatarType,
            senderAvatarPath = profile.avatarPath,
            messageText = text,
            originalText = originalText,
            isTranslated = isTranslated,
            translationLang = translationLang,
            mediaUrl = mediaUrl,
            storagePath = storagePath,
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
            isPublic = profile.isPublic
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
                    registeredAt = user.registeredAt
                )
            )
        }
    }

    suspend fun deleteProfileMedia(profile: UserProfileEntity, deleteAvatar: Boolean) {
        val mediaUrl = if (deleteAvatar) profile.avatarType else profile.coverType
        val mediaPath = if (deleteAvatar) profile.avatarPath else profile.coverPath

        // 1. Hard delete all related B2 objects
        val targets = listOfNotNull(mediaUrl, mediaPath)
        for (target in targets) {
            if (target.isNotBlank() && target != "default") {
                android.util.Log.d("SocialRepository", "Hard-deleting B2 profile media: $target")
                runCatching { supabaseService.deleteMediaFromB2(target).getOrThrow() }
            }
        }

        val actionPattern = if (deleteAvatar) "%profile picture%" else "%cover photo%"
        dao.getUserPostsByAction(profile.handle, actionPattern).forEach { post ->
            val remoteKey = post.remoteId.takeIf { it.isNotBlank() }
            
            // Delete post media from B2
            val pTargets = listOfNotNull(post.postImageRes, post.storagePath, post.thumbnailPath)
            for (t in pTargets) {
                if (t.isNotBlank() && t != "default") {
                    runCatching { supabaseService.deleteMediaFromB2(t).getOrThrow() }
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
        uploadType: String, // "profile", "cover", "post", "reel", "story"
        onProgress: ((Int) -> Unit)? = null
    ): Result<MediaUploadResult> {
        android.util.Log.d("SocialRepository", "Uploading $uploadType: $fileName")
        val res = supabaseService.uploadMediaToB2(bytes, fileName, mimeType, uploadType, onProgress)
        return res.map { json ->
            val url = json.optString("url", "")
            // b2-upload returns "path"; accept legacy "storage_path" too.
            val storagePath = json.optString("storage_path", "")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("path", "")
            val thumbnailPath = json.optString("thumbnail_path").takeIf { it != "null" && it.isNotBlank() }
            
            if (url.isBlank() && storagePath.isBlank()) {
                throw Exception("B2 Upload failed: No URL or storage path returned")
            }
            MediaUploadResult(url, storagePath, thumbnailPath)
        }
    }

    suspend fun sendChatMessage(text: String, profile: UserProfileEntity) {
        sendChatMessage("global_live", text, profile)
    }

    suspend fun addStory(imageRes: String, storagePath: String?, caption: String, profile: UserProfileEntity) {
        val story = StoryEntity(
            username = profile.handle,
            userAvatarType = profile.avatarType,
            userAvatarPath = profile.avatarPath,
            imageRes = imageRes,
            storagePath = storagePath,
            caption = caption,
            isOwn = true
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

    suspend fun addNotification(username: String, avatarType: String, actionText: String) {
        val notif = NotificationEntity(
            username = username,
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
            text = "🎁 Sent you $amount Vyn Reward Credits! 💰",
            profile = currentProfile
        )
        addNotification(
            username = existing.handle,
            avatarType = existing.avatarType,
            actionText = "was gifted $amount Credits 🎁💰"
        )
    }
}