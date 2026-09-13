package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

enum class UserRole(
    val roleKey: String,
    val displayName: String,
    val rankLevel: Int,
    val iconEmoji: String,
    val badgeColorHex: Long
) {
    SUPER_ADMIN("SUPER_ADMIN", "Super Admin", 100, "👑", 0xFFFFD700),
    ADMIN("ADMIN", "Admin", 80, "🛡️", 0xFF6C5CE7),
    MANAGER("MANAGER", "Manager", 60, "💼", 0xFF00B894),
    MODERATOR("MODERATOR", "Moderator", 40, "⚖️", 0xFF0984E3),
    USER("USER", "User", 10, "👤", 0xFF888888);

    companion object {
        fun fromString(role: String?): UserRole {
            return when (role?.uppercase()) {
                "SUPER_ADMIN", "SUPERADMIN" -> SUPER_ADMIN
                "ADMIN" -> ADMIN
                "MANAGER" -> MANAGER
                "MODERATOR" -> MODERATOR
                else -> USER
            }
        }
    }
}

@Entity(tableName = "app_users")
data class AppUserEntity(
    @PrimaryKey val uid: String = "",
    @ColumnInfo(defaultValue = "") val name: String = "",
    @ColumnInfo(defaultValue = "") val handle: String = "",
    @ColumnInfo(defaultValue = "") val email: String = "",
    @ColumnInfo(defaultValue = "USER") val role: String = "USER", // "SUPER_ADMIN", "ADMIN", "MANAGER", "MODERATOR", "USER"
    @ColumnInfo(defaultValue = "default") val avatarType: String = "default",
    @ColumnInfo(defaultValue = "default") val coverType: String = "default",
    @ColumnInfo(defaultValue = "") val bio: String = "",
    @ColumnInfo(defaultValue = "") val location: String = "",
    @ColumnInfo(defaultValue = "0") val isBanned: Boolean = false,
    @ColumnInfo(defaultValue = "") val banReason: String = "",
    // Granular permissions
    @ColumnInfo(defaultValue = "0") val canManageUsers: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canDeletePosts: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canEditPosts: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canModerateComments: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canManageChats: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canManageMonetization: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canManageRewards: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canManageRewardRules: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canManageRewardRates: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canManageRewardGateways: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canProcessPayouts: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canCleanStorage: Boolean = false,
    // Granular moderation permissions (server-authoritative — enforced by RLS + SECURITY DEFINER RPCs)
    @ColumnInfo(defaultValue = "0") val canViewReports: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canReviewReports: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canGiveWarning: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canDeleteReel: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canDeleteVideo: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canSuspendUser: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canBanUser: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canRemoveWarning: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canViewWarningHistory: Boolean = false,
    @ColumnInfo(defaultValue = "0") val canViewActivityLog: Boolean = false,
    @ColumnInfo(defaultValue = "1") val isPublic: Boolean = true,
    @ColumnInfo(defaultValue = "0") val registeredAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "NULL") val avatarPath: String? = null,
    @ColumnInfo(defaultValue = "NULL") val coverPath: String? = null,
    @ColumnInfo(defaultValue = "cloudflare_r2") val avatarProvider: String = "cloudflare_r2",
    @ColumnInfo(defaultValue = "cloudflare_r2") val coverProvider: String = "cloudflare_r2",
    @ColumnInfo(defaultValue = "NULL") val avatarMime: String? = null,
    @ColumnInfo(defaultValue = "NULL") val coverMime: String? = null,
    @ColumnInfo(defaultValue = "0") val avatarSize: Long = 0,
    @ColumnInfo(defaultValue = "0") val coverSize: Long = 0
)

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val remoteId: String = "",
    @ColumnInfo(defaultValue = "") val username: String = "",
    @ColumnInfo(defaultValue = "") val userHandle: String = "",
    @ColumnInfo(defaultValue = "default") val userAvatarType: String = "default",
    @ColumnInfo(defaultValue = "NULL") val userAvatarPath: String? = null,
    @ColumnInfo(defaultValue = "") val actionText: String = "",
    @ColumnInfo(defaultValue = "") val postImageRes: String = "",
    @ColumnInfo(defaultValue = "NULL") val storagePath: String? = null,
    @ColumnInfo(defaultValue = "NULL") val thumbnailPath: String? = null,
    @ColumnInfo(defaultValue = "") val caption: String = "",
    @ColumnInfo(defaultValue = "0") val likesCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val isLiked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isSaved: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isReposted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val repostsCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val commentsCount: Int = 0,
    @ColumnInfo(defaultValue = "1") val isPublic: Boolean = true,
    @ColumnInfo(defaultValue = "0") val isReelPost: Boolean = false,
    @ColumnInfo(defaultValue = "") val timeAgo: String = "Just now",
    @ColumnInfo(defaultValue = "0") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "cloudflare_r2") val storageProvider: String = "cloudflare_r2",
    @ColumnInfo(defaultValue = "NULL") val mimeType: String? = null,
    @ColumnInfo(defaultValue = "0") val fileSize: Long = 0
)

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val remoteId: String = "",
    @ColumnInfo(defaultValue = "0") val postId: Long = 0,
    @ColumnInfo(defaultValue = "") val username: String = "",
    @ColumnInfo(defaultValue = "default") val userAvatarType: String = "default",
    @ColumnInfo(defaultValue = "NULL") val userAvatarPath: String? = null,
    @ColumnInfo(defaultValue = "") val text: String = "",
    @ColumnInfo(defaultValue = "Just now") val timeAgo: String = "Just now",
    @ColumnInfo(defaultValue = "0") val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "") val uid: String = "",
    // Profile data is derived from the authenticated Supabase user/profile. These are
    // empty placeholders only and must be replaced by real server data before display.
    @ColumnInfo(defaultValue = "") val name: String = "",
    @ColumnInfo(defaultValue = "") val handle: String = "",
    @ColumnInfo(defaultValue = "") val bio: String = "",
    @ColumnInfo(defaultValue = "") val location: String = "",
    @ColumnInfo(defaultValue = "0") val postsCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val friendsCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val followersCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val followingCount: Int = 0,
    @ColumnInfo(defaultValue = "default") val avatarType: String = "default",
    @ColumnInfo(defaultValue = "default") val coverType: String = "default",
    @ColumnInfo(defaultValue = "NULL") val avatarPath: String? = null,
    @ColumnInfo(defaultValue = "NULL") val coverPath: String? = null,
    @ColumnInfo(defaultValue = "1") val isPublic: Boolean = true,
    @ColumnInfo(defaultValue = "cloudflare_r2") val avatarProvider: String = "cloudflare_r2",
    @ColumnInfo(defaultValue = "cloudflare_r2") val coverProvider: String = "cloudflare_r2",
    @ColumnInfo(defaultValue = "NULL") val avatarMime: String? = null,
    @ColumnInfo(defaultValue = "NULL") val coverMime: String? = null,
    @ColumnInfo(defaultValue = "0") val avatarSize: Long = 0,
    @ColumnInfo(defaultValue = "0") val coverSize: Long = 0
)

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val id: String = "",
    @ColumnInfo(defaultValue = "") val name: String = "",
    @ColumnInfo(defaultValue = "") val handle: String = "",
    @ColumnInfo(defaultValue = "default") val avatarType: String = "default",
    @ColumnInfo(defaultValue = "default") val coverImageRes: String = "default",
    @ColumnInfo(defaultValue = "NULL") val avatarPath: String? = null,
    @ColumnInfo(defaultValue = "NULL") val coverPath: String? = null,
    @ColumnInfo(defaultValue = "cloudflare_r2") val avatarProvider: String = "cloudflare_r2",
    @ColumnInfo(defaultValue = "cloudflare_r2") val coverProvider: String = "cloudflare_r2",
    @ColumnInfo(defaultValue = "") val bio: String = "",
    @ColumnInfo(defaultValue = "") val location: String = "",
    @ColumnInfo(defaultValue = "1") val isFollowing: Boolean = true,
    @ColumnInfo(defaultValue = "1") val isFollower: Boolean = true,
    @ColumnInfo(defaultValue = "1") val isFriend: Boolean = true,
    @ColumnInfo(defaultValue = "0") val isCloseFriend: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isMuted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isBlocked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val mutualFriendsCount: Int = 0,
    @ColumnInfo(defaultValue = "") val friendshipDate: String = "",
    @ColumnInfo(defaultValue = "1") val isOnline: Boolean = true,
    @ColumnInfo(defaultValue = "") val lastActive: String = "",
    @ColumnInfo(defaultValue = "0") val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val username: String = "",
    @ColumnInfo(defaultValue = "default") val userAvatarType: String = "default",
    @ColumnInfo(defaultValue = "NULL") val userAvatarPath: String? = null,
    @ColumnInfo(defaultValue = "") val imageRes: String = "",
    @ColumnInfo(defaultValue = "NULL") val storagePath: String? = null,
    @ColumnInfo(defaultValue = "") val caption: String = "",
    @ColumnInfo(defaultValue = "0") val isOwn: Boolean = false,
    @ColumnInfo(defaultValue = "0") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "cloudflare_r2") val storageProvider: String = "cloudflare_r2",
    @ColumnInfo(defaultValue = "NULL") val mimeType: String? = null,
    @ColumnInfo(defaultValue = "0") val fileSize: Long = 0
)

@Entity(
    tableName = "chat_messages",
    indices = [androidx.room.Index(value = ["remoteId"], unique = true)]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val remoteId: String = "",
    @ColumnInfo(defaultValue = "global_live") val roomId: String = "global_live",
    @ColumnInfo(defaultValue = "") val senderName: String = "",
    @ColumnInfo(defaultValue = "") val senderHandle: String = "",
    @ColumnInfo(defaultValue = "") val receiverHandle: String = "",
    @ColumnInfo(defaultValue = "default") val senderAvatar: String = "default",
    @ColumnInfo(defaultValue = "NULL") val senderAvatarPath: String? = null,
    @ColumnInfo(defaultValue = "") val messageText: String = "",
    @ColumnInfo(defaultValue = "") val originalText: String = "",
    @ColumnInfo(defaultValue = "0") val isTranslated: Boolean = false,
    @ColumnInfo(defaultValue = "") val translationLang: String = "", // e.g., "EN", "BN"
    @ColumnInfo(defaultValue = "NULL") val mediaUrl: String? = null,
    @ColumnInfo(defaultValue = "NULL") val storagePath: String? = null,
    @ColumnInfo(defaultValue = "cloudflare_r2") val storageProvider: String = "cloudflare_r2",
    @ColumnInfo(defaultValue = "text") val mediaType: String = "text", // "text", "image", "audio", "system"
    @ColumnInfo(defaultValue = "Just now") val time: String = "Just now",
    @ColumnInfo(defaultValue = "0") val isFromMe: Boolean = false,
    // Messages must NOT default to read. Read state is per-user (see chat_message_reads
    // or read_by_handles on the remote record) and only the recipient that actually views
    // a message marks it read.
    @ColumnInfo(defaultValue = "0") val isRead: Boolean = false,
    @ColumnInfo(defaultValue = "") val reactions: String = "",
    @ColumnInfo(defaultValue = "0") val audioDurationSec: Int = 0,
    @ColumnInfo(defaultValue = "0") val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val username: String = "",
    @ColumnInfo(defaultValue = "") val recipientHandle: String = "",
    @ColumnInfo(defaultValue = "default") val avatarType: String = "default",
    @ColumnInfo(defaultValue = "") val actionText: String = "",
    @ColumnInfo(defaultValue = "Just now") val timeAgo: String = "Just now",
    @ColumnInfo(defaultValue = "0") val isRead: Boolean = false,
    @ColumnInfo(defaultValue = "0") val timestamp: Long = System.currentTimeMillis()
)

data class CallSignalEntity(
    val id: String = "",
    val callerHandle: String = "",
    val callerName: String = "",
    val callerAvatar: String = "default",
    val receiverHandle: String = "",
    val callType: String = "VIDEO", // "VIDEO" or "AUDIO"
    val status: String = "OFFERING", // "OFFERING", "RINGING", "ACCEPTED", "REJECTED", "ENDED"
    val sdp: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reels")
data class ReelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val remoteId: String = "",
    @ColumnInfo(defaultValue = "") val author: String = "",
    @ColumnInfo(defaultValue = "") val handle: String = "",
    @ColumnInfo(defaultValue = "default") val avatarType: String = "default",
    @ColumnInfo(defaultValue = "NULL") val userAvatarPath: String? = null,
    @ColumnInfo(defaultValue = "") val caption: String = "",
    @ColumnInfo(defaultValue = "Original Audio") val music: String = "Original Audio",
    @ColumnInfo(defaultValue = "") val imageRes: String = "",
    @ColumnInfo(defaultValue = "NULL") val videoUrl: String? = null,
    @ColumnInfo(defaultValue = "NULL") val storagePath: String? = null,
    @ColumnInfo(defaultValue = "NULL") val thumbnailPath: String? = null,
    @ColumnInfo(defaultValue = "") val location: String = "",
    @ColumnInfo(defaultValue = "NULL") val effectName: String? = null,
    @ColumnInfo(defaultValue = "0") val likesCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val commentsCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val sharesCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val durationSecs: Int = 0,
    @ColumnInfo(defaultValue = "0") val isLiked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isSaved: Boolean = false,
    @ColumnInfo(defaultValue = "1") val isPublic: Boolean = true,
    @ColumnInfo(defaultValue = "0") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "cloudflare_r2") val storageProvider: String = "cloudflare_r2",
    @ColumnInfo(defaultValue = "NULL") val mimeType: String? = null,
    @ColumnInfo(defaultValue = "0") val fileSize: Long = 0
)
// =============================================================================
// MODERATION DOMAIN MODELS (not Room entities — always fetched from Supabase)
// =============================================================================

data class ModerationReport(
    val id: String = "",
    val contentType: String = "POST",      // POST, REEL, VIDEO, USER
    val contentId: String = "",
    val contentPreview: String = "",
    val targetHandle: String = "",
    val targetUid: String = "",
    val reporterHandle: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "PENDING",        // PENDING, REVIEWED, DISMISSED, ACTION_TAKEN
    val reportCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val reviewedBy: String = "",
    val resolvedAt: Long = 0
)

data class ModerationWarning(
    val id: String = "",
    val userId: String = "",
    val userHandle: String = "",
    val reason: String = "",
    val warnedBy: String = "",
    val contentType: String = "",
    val contentId: String = "",
    val reportId: String = "",
    val status: String = "ACTIVE",         // ACTIVE, REMOVED
    val createdAt: Long = System.currentTimeMillis(),
    val removedBy: String = "",
    val removedAt: Long = 0
)

data class ModerationActivityItem(
    val id: String = "",
    val actorHandle: String = "",
    val action: String = "",
    val targetHandle: String = "",
    val targetType: String = "",
    val contentId: String = "",
    val reportId: String = "",
    val reason: String = "",
    val meta: String = "{}",
    val createdAt: Long = System.currentTimeMillis()
)

/** Simple value wrapper returned by a moderation RPC call (success flag + message). */
data class ModerationActionResult(
    val success: Boolean,
    val message: String
)

