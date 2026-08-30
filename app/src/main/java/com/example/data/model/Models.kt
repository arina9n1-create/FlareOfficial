package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    USER("USER", "Member", 10, "👤", 0xFF888888);

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
    @PrimaryKey val uid: String,
    val name: String,
    val handle: String,
    val email: String,
    val role: String = "USER", // "SUPER_ADMIN", "ADMIN", "MANAGER", "MODERATOR", "USER"
    val avatarType: String = "default",
    val coverType: String = "default",
    val bio: String = "",
    val location: String = "",
    val isBanned: Boolean = false,
    val banReason: String = "",
    // Granular permissions
    val canManageUsers: Boolean = false,
    val canDeletePosts: Boolean = false,
    val canEditPosts: Boolean = false,
    val canModerateComments: Boolean = false,
    val canManageChats: Boolean = false,
    val canManageMonetization: Boolean = false,
    val canManageRewards: Boolean = false,
    val canCleanStorage: Boolean = false,
    val isPublic: Boolean = true,
    val registeredAt: Long = System.currentTimeMillis(),
    val avatarPath: String? = null,
    val coverPath: String? = null
)

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Globally unique remote id (UUID) issued by Supabase. The local `id` above is a
    // device-local cache key only and must NEVER be used as the remote primary key.
    val remoteId: String = "",
    val username: String,
    val userHandle: String,
    val userAvatarType: String, // avatar preset key (URI/URL or "default")
    val userAvatarPath: String? = null,
    val actionText: String = "", // e.g., "updated their cover photo", "updated their profile picture · 3d"
    val postImageRes: String, // media key: URI/URL or "default"
    val storagePath: String? = null,
    val thumbnailPath: String? = null,
    val caption: String = "",
    val likesCount: Int = 1,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isReposted: Boolean = false,
    val repostsCount: Int = 0,
    val commentsCount: Int = 0,
    val isPublic: Boolean = true,
    val timeAgo: String = "3D",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String = "",
    val postId: Long,
    val username: String,
    val userAvatarType: String,
    val userAvatarPath: String? = null,
    val text: String,
    val timeAgo: String = "Just now",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val uid: String = "",
    // Profile data is derived from the authenticated Supabase user/profile. These are
    // empty placeholders only and must be replaced by real server data before display.
    val name: String = "",
    val handle: String = "",
    val bio: String = "",
    val location: String = "",
    val postsCount: Int = 0,
    val friendsCount: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val avatarType: String = "default",
    val coverType: String = "default",
    val avatarPath: String? = null,
    val coverPath: String? = null,
    val isPublic: Boolean = true
)

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val id: String,
    val name: String,
    val handle: String,
    val avatarType: String,
    val coverImageRes: String = "default",
    val avatarPath: String? = null,
    val coverPath: String? = null,
    val bio: String = "",
    val location: String = "",
    val isFollowing: Boolean = true,
    val isFollower: Boolean = true,
    val isFriend: Boolean = true,
    val isCloseFriend: Boolean = false,
    val isMuted: Boolean = false,
    val isBlocked: Boolean = false,
    val mutualFriendsCount: Int = 0,
    val friendshipDate: String = "",
    val isOnline: Boolean = true,
    val lastActive: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val userAvatarType: String,
    val userAvatarPath: String? = null,
    val imageRes: String,
    val storagePath: String? = null,
    val caption: String = "",
    val isOwn: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String = "",
    val roomId: String = "global_live",
    val senderName: String,
    val senderHandle: String = "",
    val receiverHandle: String = "",
    val senderAvatar: String,
    val senderAvatarPath: String? = null,
    val messageText: String,
    val originalText: String = "",
    val isTranslated: Boolean = false,
    val translationLang: String = "", // e.g., "EN", "BN"
    val mediaUrl: String? = null,
    val storagePath: String? = null,
    val mediaType: String = "text", // "text", "image", "audio", "system"
    val time: String,
    val isFromMe: Boolean,
    // Messages must NOT default to read. Read state is per-user (see chat_message_reads
    // or read_by_handles on the remote record) and only the recipient that actually views
    // a message marks it read.
    val isRead: Boolean = false,
    val reactions: String = "",
    val audioDurationSec: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val avatarType: String,
    val actionText: String,
    val timeAgo: String,
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
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
    val remoteId: String = "",
    val author: String = "",
    val handle: String = "",
    val avatarType: String = "default",
    val userAvatarPath: String? = null,
    val caption: String = "",
    val music: String = "Original Audio",
    val imageRes: String = "",
    val videoUrl: String? = null,
    val storagePath: String? = null,
    val thumbnailPath: String? = null,
    val location: String = "",
    val effectName: String? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isPublic: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

