package com.example.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SocialDao {
    @Query("SELECT * FROM posts WHERE (postImageRes IS NOT NULL AND trim(postImageRes) <> '' AND lower(trim(postImageRes)) NOT IN ('default', 'null')) OR (storagePath IS NOT NULL AND trim(storagePath) <> '') ORDER BY timestamp DESC")
    fun getAllPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE userHandle = :handle ORDER BY timestamp DESC")
    fun getUserPosts(handle: String): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE userHandle = :handle AND actionText LIKE :actionPattern")
    suspend fun getUserPostsByAction(handle: String, actionPattern: String): List<PostEntity>

    @Query("SELECT * FROM posts WHERE isSaved = 1 ORDER BY timestamp DESC")
    fun getSavedPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE isReposted = 1 ORDER BY timestamp DESC")
    fun getRepostedPosts(): Flow<List<PostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: PostEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<PostEntity>)

    @Update
    suspend fun updatePost(post: PostEntity)

    @Query("SELECT * FROM posts WHERE id = :id LIMIT 1")
    suspend fun getPostById(id: Long): PostEntity?

    @Query("DELETE FROM posts WHERE id = :id")
    suspend fun deletePost(id: Long)

    @Query("SELECT * FROM posts")
    suspend fun getPostsSnapshot(): List<PostEntity>

    @Query("DELETE FROM posts WHERE remoteId = :remoteId")
    suspend fun deletePostByRemoteId(remoteId: String)

    @Query("DELETE FROM posts")
    suspend fun deleteAllPosts()

    @Query("DELETE FROM posts WHERE (postImageRes IS NULL OR trim(postImageRes) = '' OR lower(trim(postImageRes)) IN ('default', 'null')) AND (storagePath IS NULL OR trim(storagePath) = '')")
    suspend fun deleteEmptyMediaPosts()

    @Query("SELECT COUNT(*) FROM posts")
    suspend fun getPostCount(): Int

    // Profile
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfileEntity)

    // Comments
    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY timestamp ASC")
    fun getCommentsForPost(postId: Long): Flow<List<CommentEntity>>

    @Query("DELETE FROM comments WHERE postId = :postId")
    suspend fun deleteCommentsForPost(postId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Query("DELETE FROM comments WHERE id = :id")
    suspend fun deleteComment(id: Long)

    // Stories
    @Query("SELECT * FROM stories ORDER BY id ASC")
    fun getAllStories(): Flow<List<StoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<StoryEntity>)

    // Chat
    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    fun getChatMessagesForRoom(roomId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getChatMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessages(messages: List<ChatMessageEntity>)

    @Query("UPDATE chat_messages SET isRead = 1 WHERE roomId = :roomId AND isFromMe = 0")
    suspend fun markRoomMessagesRead(roomId: String)

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId")
    suspend fun clearChatMessagesForRoom(roomId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllChatMessages()

    @Query("SELECT DISTINCT roomId FROM chat_messages")
    suspend fun getDistinctChatRoomIds(): List<String>

    /** Moves every cached message of a legacy/DM room id into its canonical room. */
    @Query("UPDATE chat_messages SET roomId = :newRoom WHERE roomId = :oldRoom")
    suspend fun rewireChatRoom(oldRoom: String, newRoom: String)

    // Reels
    @Query("SELECT * FROM reels ORDER BY timestamp DESC")
    fun getAllReels(): Flow<List<ReelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReels(reels: List<ReelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReel(reel: ReelEntity): Long

    @Update
    suspend fun updateReel(reel: ReelEntity)

    @Query("UPDATE reels SET isLiked = :isLiked, likesCount = :likesCount WHERE id = :id")
    suspend fun updateReelLike(id: Long, isLiked: Boolean, likesCount: Int)

    @Query("UPDATE reels SET isSaved = :isSaved WHERE id = :id")
    suspend fun updateReelSave(id: Long, isSaved: Boolean)

    @Query("SELECT * FROM reels WHERE id = :id LIMIT 1")
    suspend fun getReelById(id: Long): ReelEntity?

    @Query("SELECT * FROM reels")
    suspend fun getReelsSnapshot(): List<ReelEntity>

    @Query("DELETE FROM reels WHERE id = :id")
    suspend fun deleteReel(id: Long)

    @Query("DELETE FROM reels")
    suspend fun deleteAllReels()

    // Notifications
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getNotifications(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markNotificationsAsRead()

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: Long)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearAllNotifications()

    // Friends & Social Graph
    @Query("SELECT * FROM friends WHERE isBlocked = 0 ORDER BY isFriend DESC, timestamp DESC")
    fun getAllConnections(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE isFriend = 1 AND isBlocked = 0 ORDER BY isCloseFriend DESC, name ASC")
    fun getFriends(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE id = :id LIMIT 1")
    suspend fun getFriendById(id: String): FriendEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriends(friends: List<FriendEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: FriendEntity)

    @Update
    suspend fun updateFriend(friend: FriendEntity)

    @Query("UPDATE friends SET isFollowing = :isFollowing, isFriend = :isFriend WHERE id = :id")
    suspend fun updateFollowStatus(id: String, isFollowing: Boolean, isFriend: Boolean)

    @Query("UPDATE friends SET isCloseFriend = :isCloseFriend WHERE id = :id")
    suspend fun updateCloseFriendStatus(id: String, isCloseFriend: Boolean)

    @Query("UPDATE friends SET isMuted = :isMuted WHERE id = :id")
    suspend fun updateMuteStatus(id: String, isMuted: Boolean)

    @Query("UPDATE friends SET isOnline = :online WHERE LOWER(handle) = LOWER(:handle)")
    suspend fun updateFriendOnline(handle: String, online: Boolean)

    @Query("UPDATE friends SET isBlocked = :isBlocked, isFriend = 0, isFollowing = 0 WHERE id = :id")
    suspend fun updateBlockStatus(id: String, isBlocked: Boolean)

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteFriend(id: String)

    @Query("DELETE FROM friends")
    suspend fun deleteAllFriends()

    @Query("DELETE FROM comments")
    suspend fun deleteAllComments()

    @Query("DELETE FROM stories")
    suspend fun deleteAllStories()

    @Query("DELETE FROM stories WHERE id = :id")
    suspend fun deleteStory(id: Long)

    // App Users & RBAC
    @Query("SELECT * FROM app_users ORDER BY registeredAt ASC")
    fun getAllUsers(): Flow<List<AppUserEntity>>

    @Query("SELECT * FROM app_users WHERE uid = :uid LIMIT 1")
    suspend fun getUserByUid(uid: String): AppUserEntity?

    @Query("SELECT * FROM app_users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): AppUserEntity?

    @Query("SELECT COUNT(*) FROM app_users")
    suspend fun getUserCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: AppUserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<AppUserEntity>)

    @Update
    suspend fun updateUser(user: AppUserEntity)

    @Query("UPDATE app_users SET role = :role, canManageUsers = :canManageUsers, canDeletePosts = :canDeletePosts, canEditPosts = :canEditPosts, canModerateComments = :canModerateComments, canManageChats = :canManageChats, canManageMonetization = :canManageMonetization, canManageRewards = :canManageRewards, canManageRewardRules = :canManageRewardRules, canManageRewardRates = :canManageRewardRates, canManageRewardGateways = :canManageRewardGateways, canProcessPayouts = :canProcessPayouts, canCleanStorage = :canCleanStorage, canViewReports = :canViewReports, canReviewReports = :canReviewReports, canGiveWarning = :canGiveWarning, canDeleteReel = :canDeleteReel, canDeleteVideo = :canDeleteVideo, canSuspendUser = :canSuspendUser, canBanUser = :canBanUser, canRemoveWarning = :canRemoveWarning, canViewWarningHistory = :canViewWarningHistory, canViewActivityLog = :canViewActivityLog WHERE uid = :uid")
    suspend fun updateUserRoleAndPermissions(
        uid: String,
        role: String,
        canManageUsers: Boolean,
        canDeletePosts: Boolean,
        canEditPosts: Boolean,
        canModerateComments: Boolean,
        canManageChats: Boolean,
        canManageMonetization: Boolean,
        canManageRewards: Boolean,
        canManageRewardRules: Boolean,
        canManageRewardRates: Boolean,
        canManageRewardGateways: Boolean,
        canProcessPayouts: Boolean,
        canCleanStorage: Boolean,
        canViewReports: Boolean,
        canReviewReports: Boolean,
        canGiveWarning: Boolean,
        canDeleteReel: Boolean,
        canDeleteVideo: Boolean,
        canSuspendUser: Boolean,
        canBanUser: Boolean,
        canRemoveWarning: Boolean,
        canViewWarningHistory: Boolean,
        canViewActivityLog: Boolean
    )

    @Query("UPDATE app_users SET avatarType = 'default', coverType = 'default'")
    suspend fun resetAllUserMedia()

    @Query("UPDATE app_users SET isBanned = :isBanned, banReason = :banReason WHERE uid = :uid")
    suspend fun updateUserBanStatus(uid: String, isBanned: Boolean, banReason: String)

    @Query("DELETE FROM app_users WHERE uid = :uid")
    suspend fun deleteUserByUid(uid: String)

    @Query("DELETE FROM app_users")
    suspend fun deleteAllUsers()
}

@Database(
    entities = [
        AppUserEntity::class,
        PostEntity::class,
        CommentEntity::class,
        UserProfileEntity::class,
        StoryEntity::class,
        ChatMessageEntity::class,
        NotificationEntity::class,
        FriendEntity::class,
        ReelEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun socialDao(): SocialDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flareofficial_social_db_v4"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
